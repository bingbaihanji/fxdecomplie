package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.SourceFileAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttributeReader;

public class JavaSourceFileAttr extends SourceFileAttr implements IJavaAttribute {

	public JavaSourceFileAttr(String fileName) {
		super(fileName);
	}

	public static IJavaAttributeReader reader() {
		return (clsData, reader) -> new JavaSourceFileAttr(clsData.getConstPoolReader().getUtf8(reader.readU2()));
	}
}
