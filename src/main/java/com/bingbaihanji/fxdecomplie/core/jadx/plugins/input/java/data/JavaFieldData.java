package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IFieldData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.JavaAttrStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.JavaAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types.ConstValueAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr;

public class JavaFieldData implements IFieldData {
	private String name;
	private String parentClassType;
	private String type;
	private int accessFlags;
	private JavaAttrStorage attributes;

	@Override
	public String getParentClassType() {
		return parentClassType;
	}

	public void setParentClassType(String parentClassType) {
		this.parentClassType = parentClassType;
	}

	@Override
	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	@Override
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public int getAccessFlags() {
		return accessFlags;
	}

	public void setAccessFlags(int accessFlags) {
		this.accessFlags = accessFlags;
	}

	public void setAttributes(JavaAttrStorage attributes) {
		this.attributes = attributes;
	}

	@Override
	public List<IJadxAttribute> getAttributes() {
		int size = attributes.size();
		if (size == 0) {
			return Collections.emptyList();
		}
		List<IJadxAttribute> list = new ArrayList<>(size);
		Utils.addToList(list, JavaAnnotationsAttr.merge(attributes));
		Utils.addToList(list, attributes.get(JavaAttrType.CONST_VALUE), ConstValueAttr::getValue);
		Utils.addToList(list, attributes.get(JavaAttrType.SIGNATURE));
		return list;
	}

	@Override
	public String toString() {
		return parentClassType + "->" + name + ":" + type;
	}
}
