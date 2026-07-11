package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;

public interface IFieldData extends IFieldRef {

	int getAccessFlags();

	List<IJadxAttribute> getAttributes();
}
