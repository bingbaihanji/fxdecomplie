package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.GenericInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.ConstructorInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
		name = "GenericTypesVisitor",
		desc = "Fix and apply generic type info",
		runAfter = TypeInferenceVisitor.class,
		runBefore = { CodeShrinkVisitor.class, MethodInvokeVisitor.class }
)
/**
 * 泛型类型信息访问器。
 * <p>
 * 修正并应用构造器指令上的泛型类型信息：对 {@code new} 构造器调用，
 * 依据其结果变量推断出的泛型类型（如 {@code new ArrayList<String>()}），
 * 附加 {@link GenericInfoAttr} 属性，以便在反编译输出中还原菱形/显式泛型参数。
 */
public class GenericTypesVisitor extends AbstractVisitor {
	private static final Logger LOG = LoggerFactory.getLogger(GenericTypesVisitor.class);

	/**
	 * 遍历方法所有基本块中的 CONSTRUCTOR 指令，为其附加泛型类型信息。
	 *
	 * @param mth 待处理的方法节点
	 */
	@Override
	public void visit(MethodNode mth) throws JadxException {
		if (mth.isNoCode()) {
			return;
		}
		for (BlockNode block : mth.getBasicBlocks()) {
			for (InsnNode insn : block.getInstructions()) {
				if (insn.getType() == InsnType.CONSTRUCTOR) {
					attachGenericTypesInfo(mth, (ConstructorInsn) insn);
				}
			}
		}
	}

	private void attachGenericTypesInfo(MethodNode mth, ConstructorInsn insn) {
		try {
			RegisterArg resultArg = insn.getResult();
			if (resultArg == null) {
				return;
			}
			ArgType argType = resultArg.getSVar().getCodeVar().getType();
			if (argType == null || argType.getGenericTypes() == null) {
				return;
			}
			ClassNode cls = mth.root().resolveClass(insn.getClassType());
			if (cls != null && cls.getGenericTypeParameters().isEmpty()) {
				return;
			}
			insn.addAttr(new GenericInfoAttr(argType.getGenericTypes()));
		} catch (Exception e) {
			LOG.error("Failed to attach constructor generic info", e);
		}
	}
}
