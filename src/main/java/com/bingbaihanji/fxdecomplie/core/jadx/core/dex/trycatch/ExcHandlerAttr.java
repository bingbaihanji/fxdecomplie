package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;

/**
 * 异常处理器属性。
 * 作为附加在异常处理器入口块上的属性，用于关联对应的 {@link ExceptionHandler}
 * 及其所属的 try-catch 块（{@link TryCatchBlockAttr}）。
 */
public class ExcHandlerAttr implements IJadxAttribute {

	/** 关联的异常处理器 */
	private final ExceptionHandler handler;

	/**
	 * 构造异常处理器属性。
	 *
	 * @param handler 关联的异常处理器
	 */
	public ExcHandlerAttr(ExceptionHandler handler) {
		this.handler = handler;
	}

	@Override
	public AType<ExcHandlerAttr> getAttrType() {
		return AType.EXC_HANDLER;
	}

	/**
	 * @return 该处理器所属的 try-catch 块
	 */
	public TryCatchBlockAttr getTryBlock() {
		return handler.getTryBlock();
	}

	/**
	 * @return 关联的异常处理器
	 */
	public ExceptionHandler getHandler() {
		return handler;
	}

	@Override
	public String toString() {
		return "ExcHandler: " + handler;
	}
}
