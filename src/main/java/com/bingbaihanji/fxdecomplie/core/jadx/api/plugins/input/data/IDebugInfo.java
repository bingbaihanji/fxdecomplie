package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import java.util.List;
import java.util.Map;

/**
 * 调试信息接口，提供方法的源码行号映射和局部变量信息。
 */
public interface IDebugInfo {

	/**
	 * 将指令偏移量映射到源代码行号。
	 * 键为指令偏移量，值为对应的源码行号。
	 */
	Map<Integer, Integer> getSourceLineMapping();

	/** 获取方法内的局部变量列表 */
	List<ILocalVar> getLocalVars();
}
