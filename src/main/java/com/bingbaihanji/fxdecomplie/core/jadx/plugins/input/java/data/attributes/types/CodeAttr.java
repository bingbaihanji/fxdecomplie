package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttributeReader;

public class CodeAttr implements IJavaAttribute {
    private final int offset;

    public CodeAttr(int offset) {
        this.offset = offset;
    }

    public static IJavaAttributeReader reader() {
        return (clsData, reader) -> new CodeAttr(reader.getOffset());
    }

    public int getOffset() {
        return offset;
    }
}
