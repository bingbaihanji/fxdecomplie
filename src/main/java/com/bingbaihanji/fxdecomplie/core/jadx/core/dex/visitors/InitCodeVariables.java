package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.PhiInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.CodeVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ssa.SSATransform;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * 代码变量初始化访问器。
 * <p>
 * 在 SSA 转换（{@link SSATransform}）之后运行，为方法中的 {@link SSAVar} 创建并初始化
 * 对应的代码变量（{@link CodeVar}），处理 this 引用、方法参数标记，并通过 Phi 指令
 * 将相互连接的 SSA 变量合并到同一个代码变量上，从而在反编译输出中还原源码级的局部变量。
 */
@JadxVisitor(
		name = "InitCodeVariables",
		desc = "Initialize code variables",
		runAfter = SSATransform.class
)
public class InitCodeVariables extends AbstractVisitor {

	@Override
	public void visit(MethodNode mth) throws JadxException {
		initCodeVars(mth);
	}

	/**
	 * 重新初始化方法的代码变量。
	 * <p>
	 * 先重置所有 SSA 变量的类型与代码变量信息，再重新执行初始化流程。
	 *
	 * @param mth 目标方法节点
	 */
	public static void rerun(MethodNode mth) {
		for (SSAVar sVar : mth.getSVars()) {
			sVar.resetTypeAndCodeVar();
		}
		initCodeVars(mth);
	}

	private static void initCodeVars(MethodNode mth) {
		RegisterArg thisArg = mth.getThisArg();
		if (thisArg != null) {
			initCodeVar(mth, thisArg);
		}
		for (RegisterArg mthArg : mth.getArgRegs()) {
			initCodeVar(mth, mthArg);
		}
		for (SSAVar ssaVar : mth.getSVars()) {
			initCodeVar(ssaVar);
		}
	}

	/**
	 * 为寄存器参数所对应的 SSA 变量初始化代码变量。
	 * <p>
	 * 若该寄存器参数尚未关联 SSA 变量，则先为其创建一个新的 SSA 变量。
	 *
	 * @param mth    所属方法节点
	 * @param regArg 寄存器参数
	 */
	public static void initCodeVar(MethodNode mth, RegisterArg regArg) {
		SSAVar ssaVar = regArg.getSVar();
		if (ssaVar == null) {
			ssaVar = mth.makeNewSVar(regArg);
		}
		initCodeVar(ssaVar);
	}

	/**
	 * 为指定的 SSA 变量初始化其代码变量。
	 * <p>
	 * 若代码变量已设置则直接返回；否则根据赋值参数上的标志设置 this、名称、
	 * 以及是否为已声明（方法参数或自定义声明）等属性。
	 *
	 * @param ssaVar 目标 SSA 变量
	 */
	public static void initCodeVar(SSAVar ssaVar) {
		if (ssaVar.isCodeVarSet()) {
			return;
		}
		CodeVar codeVar = new CodeVar();
		RegisterArg assignArg = ssaVar.getAssign();
		if (assignArg.contains(AFlag.THIS)) {
			codeVar.setName(RegisterArg.THIS_ARG_NAME);
			codeVar.setThis(true);
		}
		if (assignArg.contains(AFlag.METHOD_ARGUMENT) || assignArg.contains(AFlag.CUSTOM_DECLARE)) {
			codeVar.setDeclared(true);
		}

		setCodeVar(ssaVar, codeVar);
	}

	private static void setCodeVar(SSAVar ssaVar, CodeVar codeVar) {
		List<PhiInsn> phiList = ssaVar.getPhiList();
		if (!phiList.isEmpty()) {
			Set<SSAVar> vars = new LinkedHashSet<>();
			vars.add(ssaVar);
			collectConnectedVars(phiList, vars);
			setCodeVarType(codeVar, vars);
			vars.forEach(var -> {
				if (var.isCodeVarSet()) {
					codeVar.mergeFlagsFrom(var.getCodeVar());
				}
				var.setCodeVar(codeVar);
			});
		} else {
			ssaVar.setCodeVar(codeVar);
		}
	}

	private static void setCodeVarType(CodeVar codeVar, Set<SSAVar> vars) {
		if (vars.size() > 1) {
			List<ArgType> imTypes = vars.stream()
					.map(SSAVar::getImmutableType)
					.filter(Objects::nonNull)
					.filter(ArgType::isTypeKnown)
					.distinct()
					.collect(Collectors.toList());
			int imCount = imTypes.size();
			if (imCount == 1) {
				codeVar.setType(imTypes.get(0));
			} else if (imCount > 1) {
				throw new JadxRuntimeException("Several immutable types in one variable: " + imTypes + ", vars: " + vars);
			}
		}
	}

	private static void collectConnectedVars(List<PhiInsn> phiInsnList, Set<SSAVar> vars) {
		if (phiInsnList.isEmpty()) {
			return;
		}
		for (PhiInsn phiInsn : phiInsnList) {
			SSAVar resultVar = phiInsn.getResult().getSVar();
			if (vars.add(resultVar)) {
				collectConnectedVars(resultVar.getPhiList(), vars);
			}
			phiInsn.getArguments().forEach(arg -> {
				SSAVar sVar = ((RegisterArg) arg).getSVar();
				if (vars.add(sVar)) {
					collectConnectedVars(sVar.getPhiList(), vars);
				}
			});
		}
	}
}
