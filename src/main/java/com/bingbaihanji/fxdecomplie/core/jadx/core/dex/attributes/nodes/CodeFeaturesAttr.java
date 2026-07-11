package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

import java.util.EnumSet;
import java.util.Set;

public class CodeFeaturesAttr implements IJadxAttribute {

    private final Set<CodeFeature> codeFeatures = EnumSet.noneOf(CodeFeature.class);

    public static boolean contains(MethodNode mth, CodeFeature feature) {
        CodeFeaturesAttr codeFeaturesAttr = mth.get(AType.METHOD_CODE_FEATURES);
        if (codeFeaturesAttr == null) {
            return false;
        }
        return codeFeaturesAttr.getCodeFeatures().contains(feature);
    }

    public static void add(MethodNode mth, CodeFeature feature) {
        CodeFeaturesAttr codeFeaturesAttr = mth.get(AType.METHOD_CODE_FEATURES);
        if (codeFeaturesAttr == null) {
            codeFeaturesAttr = new CodeFeaturesAttr();
            mth.addAttr(codeFeaturesAttr);
        }
        codeFeaturesAttr.getCodeFeatures().add(feature);
    }

    public Set<CodeFeature> getCodeFeatures() {
        return codeFeatures;
    }

    @Override
    public AType<CodeFeaturesAttr> getAttrType() {
        return AType.METHOD_CODE_FEATURES;
    }

    @Override
    public String toAttrString() {
        return "CodeFeatures{" + codeFeatures + '}';
    }

    @Override
    public String toString() {
        return toAttrString();
    }

    public enum CodeFeature {
        /**
         * Code contains switch instruction
         */
        SWITCH,

        /**
         * Code contains new-array instruction
         */
        NEW_ARRAY,
    }
}
