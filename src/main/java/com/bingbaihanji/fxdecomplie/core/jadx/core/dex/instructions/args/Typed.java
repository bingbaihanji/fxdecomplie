package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AttrNode;

/**
 * 带类型信息的抽象节点基类。
 * <p>
 * 继承自 {@link AttrNode}，增加一个 {@link ArgType} 类型字段，
 * 为指令参数、SSA 变量等具有类型特征的节点提供统一的类型存取接口。
 * 子类可以通过重写 {@link #isTypeImmutable()} 来声明类型是否不可变。
 * </p>
 */
public abstract class Typed extends AttrNode {

	/** 节点的类型信息 */
	protected ArgType type;

	/**
	 * 获取当前节点的类型。
	 *
	 * @return 类型信息
	 */
	public ArgType getType() {
		return type;
	}

	/**
	 * 设置当前节点的类型。
	 *
	 * @param type 类型信息
	 */
	public void setType(ArgType type) {
		this.type = type;
	}

	/**
	 * 判断当前节点的类型是否不可变。
	 * <p>
	 * 默认返回 {@code false}，表示类型可以被修改。
	 * 子类可重写此方法以声明类型不可变。
	 * </p>
	 *
	 * @return 类型不可变时返回 true
	 */
	public boolean isTypeImmutable() {
		return false;
	}
}
