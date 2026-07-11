package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types;

import java.util.ArrayList;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.MethodParametersAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.ConstPoolReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttributeReader;

public class JavaMethodParametersAttr extends MethodParametersAttr implements IJavaAttribute {
	public JavaMethodParametersAttr(List<Info> list) {
		super(list);
	}

	public static IJavaAttributeReader reader() {
		return (clsData, reader) -> {
			ConstPoolReader constPool = clsData.getConstPoolReader();
			int count = reader.readU1();
			List<Info> params = new ArrayList<>(count);
			for (int i = 0; i < count; i++) {
				String name = constPool.getUtf8(reader.readU2());
				int accessFlags = reader.readU2();
				params.add(new Info(accessFlags, name));
			}
			return new JavaMethodParametersAttr(params);
		};
	}
}
