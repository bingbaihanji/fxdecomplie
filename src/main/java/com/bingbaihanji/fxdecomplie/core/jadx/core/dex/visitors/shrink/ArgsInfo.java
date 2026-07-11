package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnWrapArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.TernaryInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.EmptyBitSet;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 参数信息辅助类。
 * <p>
 * 用于代码收缩（shrink）阶段记录单条指令及其寄存器参数的分布情况，
 * 并判断某条赋值指令能否安全地向下移动并内联（inline，即包装）到使用它的指令中，
 * 从而在保证语义不变的前提下减少中间变量、生成更紧凑的表达式。
 */
final class ArgsInfo {
	private final InsnNode insn;
	private final List<ArgsInfo> argsList;
	private final List<RegisterArg> args;
	private final int pos;
	/** 允许内联移动的边界位置（不能越过该位置向上移动） */
	private int inlineBorder;
	/** 若当前指令已被内联到其它指令中，则指向承载它的那条指令信息 */
	private ArgsInfo inlinedInsn;
	/** 被内联（包装）进当前指令的其它指令信息列表 */
	private @Nullable List<ArgsInfo> wrappedInsns;

	public ArgsInfo(InsnNode insn, List<ArgsInfo> argsList, int pos) {
		this.insn = insn;
		this.argsList = argsList;
		this.pos = pos;
		this.inlineBorder = pos;
		this.args = getArgs(insn);
	}

	/**
	 * 收集指令中用到的所有寄存器参数（包括三元表达式条件及嵌套包装指令中的参数）。
	 *
	 * @param insn 目标指令
	 * @return 该指令涉及的寄存器参数列表
	 */
	public static List<RegisterArg> getArgs(InsnNode insn) {
		List<RegisterArg> args = new ArrayList<>();
		addArgs(insn, args);
		return args;
	}

	private static void addArgs(InsnNode insn, List<RegisterArg> args) {
		if (insn.getType() == InsnType.TERNARY) {
			args.addAll(((TernaryInsn) insn).getCondition().getRegisterArgs());
		}
		for (InsnArg arg : insn.getArguments()) {
			if (arg.isRegister()) {
				args.add((RegisterArg) arg);
			}
		}
		for (InsnArg arg : insn.getArguments()) {
			if (arg.isInsnWrap()) {
				addArgs(((InsnWrapArg) arg).getWrapInsn(), args);
			}
		}
	}

	public InsnNode getInsn() {
		return insn;
	}

	List<RegisterArg> getArgs() {
		return args;
	}

	/**
	 * 返回当前指令所使用的寄存器编号集合（含被包装指令的寄存器）。
	 *
	 * @return 寄存器编号位集；若为空则返回空位集
	 */
	public BitSet getArgsSet() {
		if (args.isEmpty() && Utils.isEmpty(wrappedInsns)) {
			return EmptyBitSet.EMPTY;
		}
		BitSet set = new BitSet();
		fillArgsSet(set);
		return set;
	}

	private void fillArgsSet(BitSet set) {
		for (RegisterArg arg : args) {
			set.set(arg.getRegNum());
		}
		List<ArgsInfo> wrapList = wrappedInsns;
		if (wrapList != null) {
			for (ArgsInfo wrappedInsn : wrapList) {
				wrappedInsn.fillArgsSet(set);
			}
		}
	}

	/**
	 * 检查位于 {@code assignPos} 的赋值指令能否内联到当前指令中。
	 *
	 * @param assignPos 赋值指令在指令列表中的位置
	 * @param arg       当前指令中引用该赋值结果的寄存器参数
	 * @return 可内联时返回包装信息，否则返回 {@code null}
	 */
	public WrapInfo checkInline(int assignPos, RegisterArg arg) {
		if (assignPos >= inlineBorder || !canMove(assignPos, inlineBorder)) {
			return null;
		}
		inlineBorder = assignPos;
		return inline(assignPos, arg);
	}

	private boolean canMove(int from, int to) {
		ArgsInfo startInfo = argsList.get(from);
		int start = from + 1;
		if (start == to) {
			// 前一条指令或恰好位于内联边界上
			return true;
		}
		if (start > to) {
			throw new JadxRuntimeException("Invalid inline insn positions: " + start + " - " + to);
		}
		BitSet movedSet = startInfo.getArgsSet();
		if (movedSet == EmptyBitSet.EMPTY && startInfo.insn.isConstInsn()) {
			return true;
		}
		boolean canReorder = startInfo.canReorder();
		for (int i = start; i < to; i++) {
			ArgsInfo argsInfo = argsList.get(i);
			if (argsInfo.getInlinedInsn() == this) {
				continue;
			}
			InsnNode curInsn = argsInfo.insn;
			if (canReorder) {
				if (usedArgAssign(curInsn, movedSet)) {
					return false;
				}
			} else {
				if (!curInsn.canReorder() || usedArgAssign(curInsn, movedSet)) {
					return false;
				}
			}
		}
		return true;
	}

	private boolean canReorder() {
		if (!insn.canReorder()) {
			return false;
		}
		List<ArgsInfo> wrapList = wrappedInsns;
		if (wrapList != null) {
			for (ArgsInfo wrapInsn : wrapList) {
				if (!wrapInsn.canReorder()) {
					return false;
				}
			}
		}
		return true;
	}

	static boolean usedArgAssign(InsnNode insn, BitSet args) {
		if (args.isEmpty()) {
			return false;
		}
		RegisterArg result = insn.getResult();
		if (result == null) {
			return false;
		}
		return args.get(result.getRegNum());
	}

	/**
	 * 将位于 {@code assignInsnPos} 的赋值指令内联（包装）到当前指令中。
	 *
	 * @param assignInsnPos 赋值指令位置
	 * @param arg           被替换的寄存器参数
	 * @return 描述被包装指令及对应参数的 {@link WrapInfo}
	 */
	WrapInfo inline(int assignInsnPos, RegisterArg arg) {
		ArgsInfo argsInfo = argsList.get(assignInsnPos);
		argsInfo.inlinedInsn = this;
		if (wrappedInsns == null) {
			wrappedInsns = new ArrayList<>(args.size());
		}
		wrappedInsns.add(argsInfo);
		return new WrapInfo(argsInfo.insn, arg);
	}

	ArgsInfo getInlinedInsn() {
		if (inlinedInsn != null) {
			ArgsInfo parent = inlinedInsn.getInlinedInsn();
			if (parent != null) {
				inlinedInsn = parent;
			}
		}
		return inlinedInsn;
	}

	@Override
	public String toString() {
		return "ArgsInfo: |" + inlineBorder
				+ " ->" + (inlinedInsn == null ? "-" : inlinedInsn.pos)
				+ ' ' + args + " : " + insn;
	}
}
