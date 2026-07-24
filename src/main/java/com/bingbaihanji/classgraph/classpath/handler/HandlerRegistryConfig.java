package com.bingbaihanji.classgraph.classpath.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler 注册表配置 — 允许以可插拔方式注册自定义 ClassLoader 处理器。
 *
 * <p>使用方式：
 * <pre>{@code
 * HandlerRegistryConfig config = HandlerRegistryConfig.defaults()
 *     .withAdditional(new MyCustomHandler())
 *     .without(OSGiDefaultClassLoaderHandler.class);
 * HandlerRegistry registry = new HandlerRegistry(config);
 * }</pre>
 */
public final class HandlerRegistryConfig {

    private final List<ClassLoaderHandler> handlers;
    private final List<Class<? extends ClassLoaderHandler>> excludedHandlerTypes;

    private HandlerRegistryConfig(List<ClassLoaderHandler> handlers,
                                   List<Class<? extends ClassLoaderHandler>> excluded) {
        this.handlers = Collections.unmodifiableList(new ArrayList<>(handlers));
        this.excludedHandlerTypes = Collections.unmodifiableList(new ArrayList<>(excluded));
    }

    /** 使用默认处理器创建配置 */
    public static HandlerRegistryConfig defaults() {
        List<ClassLoaderHandler> handlers = new ArrayList<>();
        for (HandlerRegistry.HandlerRegistryEntry entry
                : HandlerRegistry.CLASS_LOADER_HANDLERS) {
            handlers.add(entry.classLoaderHandler);
        }
        return new HandlerRegistryConfig(handlers, List.of());
    }

    /** 创建空配置（仅手动注册的处理器） */
    public static HandlerRegistryConfig empty() {
        return new HandlerRegistryConfig(List.of(), List.of());
    }

    /** 添加额外的处理器 */
    public HandlerRegistryConfig withAdditional(ClassLoaderHandler handler) {
        List<ClassLoaderHandler> newHandlers = new ArrayList<>(this.handlers);
        newHandlers.add(handler);
        return new HandlerRegistryConfig(newHandlers, this.excludedHandlerTypes);
    }

    /** 排除指定类型的处理器 */
    @SafeVarargs
    public final HandlerRegistryConfig without(
            Class<? extends ClassLoaderHandler>... handlerTypes) {
        List<Class<? extends ClassLoaderHandler>> newExcluded =
            new ArrayList<>(this.excludedHandlerTypes);
        Collections.addAll(newExcluded, handlerTypes);
        return new HandlerRegistryConfig(this.handlers, newExcluded);
    }

    /** 替换全部处理器列表 */
    public HandlerRegistryConfig withHandlers(
            List<ClassLoaderHandler> replacementHandlers) {
        return new HandlerRegistryConfig(
            new ArrayList<>(replacementHandlers), this.excludedHandlerTypes);
    }

    // ─── 包内访问器 ───

    List<ClassLoaderHandler> effectiveHandlers() {
        if (excludedHandlerTypes.isEmpty()) {
            return handlers;
        }
        List<ClassLoaderHandler> result = new ArrayList<>();
        for (ClassLoaderHandler h : handlers) {
            boolean excluded = false;
            for (Class<? extends ClassLoaderHandler> excludedType : excludedHandlerTypes) {
                if (excludedType.isInstance(h)) {
                    excluded = true;
                    break;
                }
            }
            if (!excluded) {
                result.add(h);
            }
        }
        return Collections.unmodifiableList(result);
    }
}
