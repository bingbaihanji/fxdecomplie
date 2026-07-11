package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.ExceptionsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttributeReader;

import java.util.List;

public class JavaExceptionsAttr extends ExceptionsAttr implements IJavaAttribute {
    public JavaExceptionsAttr(List<String> list) {
        super(list);
    }

    public static IJavaAttributeReader reader() {
        return (clsData, reader) -> new JavaExceptionsAttr(reader.readClassesList(clsData.getConstPoolReader()));
    }
}
