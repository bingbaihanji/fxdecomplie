package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

/**
 * 方法替换属性：对该方法的调用应替换为指定的方法（用于合成方法的重定向）。
 */
public class MethodReplaceAttr extends PinnedAttribute {

	/** 用于替换的目标方法 */
	private final MethodNode replaceMth;

	/**
	 * 构造方法替换属性。
	 *
	 * @param replaceMth 用于替换的目标方法
	 */
	public MethodReplaceAttr(MethodNode replaceMth) {
		this.replaceMth = replaceMth;
	}

	/**
	 * 获取用于替换的目标方法。
	 *
	 * @return 替换目标方法
	 */
	public MethodNode getReplaceMth() {
		return replaceMth;
	}

	@Override
	public AType<MethodReplaceAttr> getAttrType() {
		return AType.METHOD_REPLACE;
	}

	/**
	 * 返回方法替换属性的字符串表示。
	 */
	@Override
	public String toString() {
		return "REPLACED_BY: " + replaceMth;
	}
}
