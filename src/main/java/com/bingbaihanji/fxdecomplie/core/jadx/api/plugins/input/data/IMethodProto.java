package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import java.util.List;

/**
 * 方法原型（签名）接口。
 * <p>
 * 描述方法的返回类型与参数类型列表，不包含方法名，
 * 用于表示方法的类型签名信息。
 */
public interface IMethodProto {

	/**
	 * 获取方法的返回类型。
	 *
	 * @return 返回类型的描述符字符串
	 */
	String getReturnType();

	/**
	 * 获取方法的参数类型列表。
	 *
	 * @return 按声明顺序排列的参数类型描述符列表
	 */
	List<String> getArgTypes();
}
