package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;

public class AnnotationDefaultAttr extends PinnedAttribute {

    private final EncodedValue value;

    public AnnotationDefaultAttr(EncodedValue value) {
        this.value = value;
    }

    public EncodedValue getValue() {
        return value;
    }

    @Override
    public IJadxAttrType<? extends IJadxAttribute> getAttrType() {
        return JadxAttrType.ANNOTATION_DEFAULT;
    }

    @Override
    public String toString() {
        return "ANNOTATION_DEFAULT: " + value;
    }
}
