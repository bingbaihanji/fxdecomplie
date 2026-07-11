package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.custom.ICustomPayload;

public interface ICallSite extends ICustomPayload {

	List<EncodedValue> getValues();

	void load();
}
