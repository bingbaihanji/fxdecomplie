package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.gradle;

import java.util.Map;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.AnnotationVisibility;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.CodeFeaturesAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IFieldInfoRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.SwitchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.FixSwitchOverEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.JadxVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.DepthRegionTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.IRegionIterativeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.GradleInfoStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.android.AndroidResourcesUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
		name = "NonFinalResIdsVisitor",
		desc = "Detect usage of android resource constants in cases where constant expressions are required.",
		runAfter = FixSwitchOverEnum.class
)
public class NonFinalResIdsVisitor extends AbstractVisitor implements IRegionIterativeVisitor {

	private boolean nonFinalResIdsFlagRequired = false;

	private GradleInfoStorage gradleInfoStorage;

	@Override
    public void init(RootNode root) throws JadxException {
		gradleInfoStorage = root.getGradleInfoStorage();
	}

	@Override
	public boolean visit(ClassNode cls) throws JadxException {
		if (nonFinalResIdsFlagRequired) {
			return false;
		}
		AnnotationsAttr annotationsList = cls.get(JadxAttrType.ANNOTATION_LIST);
		if (visitAnnotationList(annotationsList)) {
			return false;
		}
		return super.visit(cls);
	}

	private static boolean isCustomResourceClass(ClassInfo cls) {
		ClassInfo parentClass = cls.getParentClass();
		return parentClass != null && "R".equals(parentClass.getShortName()) && !"android.R".equals(parentClass.getFullName());
	}

	@Override
	public void visit(MethodNode mth) throws JadxException {
		AnnotationsAttr annotationsList = mth.get(JadxAttrType.ANNOTATION_LIST);
		if (visitAnnotationList(annotationsList)) {
			nonFinalResIdsFlagRequired = true;
			return;
		}

		if (nonFinalResIdsFlagRequired || !CodeFeaturesAttr.contains(mth, CodeFeaturesAttr.CodeFeature.SWITCH)) {
			return;
		}
		DepthRegionTraversal.traverseIterative(mth, this);
	}

	private boolean visitAnnotationList(AnnotationsAttr annotationsList) {
		if (annotationsList != null) {
			for (IAnnotation annotation : annotationsList.getAll()) {
				if (annotation.getVisibility() == AnnotationVisibility.SYSTEM) {
					continue;
				}
				for (Map.Entry<String, EncodedValue> entry : annotation.getValues().entrySet()) {
					Object value = entry.getValue().getValue();
					if (value instanceof IFieldInfoRef && isCustomResourceClass(((IFieldInfoRef) value).getFieldInfo().getDeclClass())) {
						gradleInfoStorage.setNonFinalResIds(true);
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public boolean visitRegion(MethodNode mth, IRegion region) {
		if (nonFinalResIdsFlagRequired) {
			return false;
		}
		if (region instanceof SwitchRegion) {
			return detectSwitchOverResIds((SwitchRegion) region);
		}
		return false;
	}

	private boolean detectSwitchOverResIds(SwitchRegion switchRegion) {
		for (SwitchRegion.CaseInfo caseInfo : switchRegion.getCases()) {
			for (Object key : caseInfo.getKeys()) {
				if (key instanceof FieldNode) {
					ClassNode topParentClass = ((FieldNode) key).getTopParentClass();
					if (AndroidResourcesUtils.isResourceClass(topParentClass) && !"android.R".equals(topParentClass.getFullName())) {
						this.nonFinalResIdsFlagRequired = true;
						gradleInfoStorage.setNonFinalResIds(true);
						return false;
					}
				}
			}
		}
		return false;
	}
}
