package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types;

import org.jetbrains.annotations.Nullable;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlagsScope;

/**
 * 内部类信息。
 * <p>
 * 表示 class 文件中 InnerClasses 属性的一条记录，包含内部类的全限定名、
 * 外部类全限定名（若为顶层内部类则为 null）、原始简单名称（匿名类时为 null）
 * 以及内部类的访问标志。
 * </p>
 */
public class InnerClsInfo {

	/** 内部类的全限定名 */
	private final String innerCls;

	/** 外部类的全限定名，顶层内部类时可能为 null */
	private final @Nullable String outerCls;

	/** 内部类的原始简单名称，匿名类时为 null */
	private final @Nullable String name;

	/** 内部类的访问标志位 */
	private final int accessFlags;

	/**
	 * 构造一个内部类信息实例。
	 *
	 * @param innerCls    内部类的全限定名
	 * @param outerCls    外部类的全限定名，可为 null
	 * @param name        内部类的原始简单名称，可为 null
	 * @param accessFlags 内部类的访问标志位
	 */
	public InnerClsInfo(String innerCls, @Nullable String outerCls, @Nullable String name, int accessFlags) {
		this.innerCls = innerCls;
		this.outerCls = outerCls;
		this.name = name;
		this.accessFlags = accessFlags;
	}

	/**
	 * 获取内部类的全限定名。
	 *
	 * @return 内部类全限定名
	 */
	public String getInnerCls() {
		return innerCls;
	}

	/**
	 * 获取外部类的全限定名。
	 *
	 * @return 外部类全限定名，可能为 null
	 */
	public @Nullable String getOuterCls() {
		return outerCls;
	}

	/**
	 * 获取内部类的原始简单名称。
	 *
	 * @return 原始简单名称，匿名类时为 null
	 */
	public @Nullable String getName() {
		return name;
	}

	/**
	 * 获取内部类的访问标志位。
	 *
	 * @return 访问标志位的原始 int 值
	 */
	public int getAccessFlags() {
		return accessFlags;
	}

	@Override
	public String toString() {
		return "InnerCls{" + innerCls
				+ ", outerCls=" + outerCls
				+ ", name=" + name
				+ ", accessFlags=" + AccessFlags.format(accessFlags, AccessFlagsScope.CLASS)
				+ '}';
	}
}
