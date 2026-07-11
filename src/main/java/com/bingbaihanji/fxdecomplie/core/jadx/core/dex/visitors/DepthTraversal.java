package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

/**
 * 深度优先遍历工具类。
 * <p>
 * 提供对 {@link ClassNode} 和 {@link MethodNode} 的深度优先访问能力，
 * 将访问者（{@link IDexTreeVisitor}）逐层应用到类节点及其内部类、方法上。
 * 遍历过程中捕获异常并记录到对应的节点错误信息中，防止因单个节点异常导致整个遍历中断。
 * </p>
 */
public class DepthTraversal {

	/**
	 * 以深度优先方式访问类节点。
	 * <p>
	 * 首先调用访问者的 {@code visit(ClassNode)} 方法，若返回 {@code true}，
	 * 则递归访问该类的所有内部类和所有方法。遍历过程中若发生栈溢出、
	 * 引导方法错误或其他异常，将错误信息附加到类节点上。
	 * </p>
	 *
	 * @param visitor 访问者实例
	 * @param cls     待访问的类节点
	 */
	public static void visit(IDexTreeVisitor visitor, ClassNode cls) {
		try {
			if (visitor.visit(cls)) {
				cls.getInnerClasses().forEach(inCls -> visit(visitor, inCls));
				cls.getMethods().forEach(mth -> visit(visitor, mth));
			}
		} catch (StackOverflowError | BootstrapMethodError | Exception e) {
			cls.addError(e.getClass().getSimpleName() + " in pass: " + visitor.getClass().getSimpleName(), e);
		}
	}

	/**
	 * 访问方法节点。
	 * <p>
	 * 若方法节点已包含 {@link AType#JADX_ERROR} 错误属性，则跳过访问以避免对错误节点重复处理。
	 * 否则调用访问者的 {@code visit(MethodNode)} 方法。遍历过程中若发生异常，
	 * 将错误信息附加到方法节点上。
	 * </p>
	 *
	 * @param visitor 访问者实例
	 * @param mth     待访问的方法节点
	 */
	public static void visit(IDexTreeVisitor visitor, MethodNode mth) {
		try {
			if (mth.contains(AType.JADX_ERROR)) {
				return;
			}
			visitor.visit(mth);
		} catch (StackOverflowError | BootstrapMethodError | Exception e) {
			mth.addError(e.getClass().getSimpleName() + " in pass: " + visitor.getClass().getSimpleName(), e);
		}
	}

	/** 私有构造方法，防止实例化工具类。 */
	private DepthTraversal() {
	}
}
