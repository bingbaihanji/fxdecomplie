package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

public class DepthTraversal {

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

	private DepthTraversal() {
	}
}
