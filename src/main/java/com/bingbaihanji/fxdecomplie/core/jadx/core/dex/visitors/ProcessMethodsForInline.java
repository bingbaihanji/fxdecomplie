package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.Consts;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.usage.UsageInfoVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
		name = "ProcessMethodsForInline",
		desc = "Mark methods for future inline",
		runAfter = {
				UsageInfoVisitor.class
		}
)
public class ProcessMethodsForInline extends AbstractVisitor {

	private boolean inlineMethods;

	@Override
	public void init(RootNode root) {
		inlineMethods = root.getArgs().isInlineMethods();
	}

	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		if (!inlineMethods) {
			return false;
		}
		for (MethodNode mth : cls.getMethods()) {
			if (canInline(mth)) {
				mth.add(AFlag.METHOD_CANDIDATE_FOR_INLINE);
				fixClassDependencies(mth);
			}
		}
		return true;
	}

	private static boolean canInline(MethodNode mth) {
		if (mth.isNoCode() || mth.contains(AFlag.DONT_GENERATE)) {
			return false;
		}
		AccessInfo accessFlags = mth.getAccessFlags();
		boolean isSynthetic = accessFlags.isSynthetic() || mth.getName().contains("$");
		return isSynthetic && canInlineMethod(mth, accessFlags);
	}

	private static boolean canInlineMethod(MethodNode mth, AccessInfo accessFlags) {
		if (accessFlags.isStatic()) {
			return true;
		}
		return mth.isConstructor() && mth.root().getArgs().isInlineAnonymousClasses();
	}

	private static void fixClassDependencies(MethodNode mth) {
		ClassNode parentClass = mth.getTopParentClass();
		for (MethodNode useInMth : mth.getUseIn()) {
			// remove possible cross dependency
			// to force class with inline method to be processed before its usage
			ClassNode useTopCls = useInMth.getTopParentClass();
			if (useTopCls != parentClass) {
				parentClass.removeDependency(useTopCls);
				useTopCls.addCodegenDep(parentClass);
				if (Consts.DEBUG_USAGE) {
					parentClass.addDebugComment("Remove dependency: " + useTopCls + " to inline " + mth);
					useTopCls.addDebugComment("Add dependency: " + parentClass + " to inline " + mth);
				}
			}
		}
	}

	@Override
	public String getName() {
		return "ProcessMethodsForInline";
	}
}
