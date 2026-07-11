package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.loader;

import java.io.Closeable;
import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;

/**
 * Jadx 插件加载器接口。
 * <p>
 * 定义了插件加载的标准契约，负责从各种来源（如 JAR 文件、目录等）发现并加载 {@link JadxPlugin} 实例。
 * 实现此接口的加载器需要提供具体的插件发现和加载策略。
 * </p>
 * <p>
 * 此接口继承自 {@link Closeable}，允许加载器持有需要显式释放的外部资源（如文件句柄、类加载器等）。
 * </p>
 *
 * @see JadxPlugin
 */
public interface JadxPluginLoader extends Closeable {

	/**
	 * 加载并返回所有可用的 Jadx 插件列表。
	 * <p>
	 * 该方法负责扫描指定来源，发现并实例化所有可用的插件。返回的插件列表可能为空，
	 * 但不会返回 {@code null}。
	 * </p>
	 *
	 * @return 已加载的 {@link JadxPlugin} 实例列表，不会为 {@code null}
	 */
	List<JadxPlugin> load();
}
