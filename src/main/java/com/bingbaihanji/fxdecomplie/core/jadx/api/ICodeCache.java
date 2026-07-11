package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.io.Closeable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 代码缓存接口，用于存储和检索反编译后的代码信息。
 * <p>
 * 实现此接口的缓存可用于避免重复反编译同一个类，从而提升整体反编译性能。
 * 该接口继承了 {@link Closeable}，允许在不再需要时释放缓存持有的资源。
 * </p>
 */
public interface ICodeCache extends Closeable {

	/**
	 * 将指定类的代码信息添加到缓存中。
	 *
	 * @param clsFullName 类的完全限定名
	 * @param codeInfo    要缓存的代码信息
	 */
	void add(String clsFullName, ICodeInfo codeInfo);

	/**
	 * 从缓存中移除指定类的代码信息。
	 *
	 * @param clsFullName 类的完全限定名
	 */
	void remove(String clsFullName);

	/**
	 * 从缓存中获取指定类的代码信息。
	 *
	 * @param clsFullName 类的完全限定名
	 * @return 对应类的代码信息，不会返回 {@code null}
	 */
	@NotNull
	ICodeInfo get(String clsFullName);

	/**
	 * 从缓存中获取指定类的反编译源代码字符串。
	 *
	 * @param clsFullName 类的完全限定名
	 * @return 反编译后的源代码字符串；如果缓存中不存在该类，则返回 {@code null}
	 */
	@Nullable
	String getCode(String clsFullName);

	/**
	 * 检查缓存中是否包含指定类的代码信息。
	 *
	 * @param clsFullName 类的完全限定名
	 * @return 如果缓存中存在该类的代码信息则返回 {@code true}，否则返回 {@code false}
	 */
	boolean contains(String clsFullName);
}
