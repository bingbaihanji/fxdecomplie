package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationDefaultAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.*;

public class JavaAnnotationDefaultAttr extends AnnotationDefaultAttr implements IJavaAttribute {

    public JavaAnnotationDefaultAttr(EncodedValue value) {
        super(value);
    }

    public static IJavaAttributeReader reader() {
        return (clsData, reader) -> new JavaAnnotationDefaultAttr(EncodedValueReader.read(clsData, reader));
    }

    public static AnnotationDefaultAttr convert(JavaAttrStorage attributes) {
        return attributes.get(JavaAttrType.ANNOTATION_DEFAULT);
    }
}
