package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;

public class ExcHandlerAttr implements IJadxAttribute {

	private final ExceptionHandler handler;

	public ExcHandlerAttr(ExceptionHandler handler) {
		this.handler = handler;
	}

	@Override
	public AType<ExcHandlerAttr> getAttrType() {
		return AType.EXC_HANDLER;
	}

	public TryCatchBlockAttr getTryBlock() {
		return handler.getTryBlock();
	}

	public ExceptionHandler getHandler() {
		return handler;
	}

	@Override
	public String toString() {
		return "ExcHandler: " + handler;
	}
}
