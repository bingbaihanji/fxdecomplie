package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources;

/**
 * 资源加载器接口。
 * <p>
 * 提供注册资源容器工厂和资源表解析器提供者的能力，
 * 用于扩展反编译器的资源加载机制，支持解析 APK 中的
 * resources.arsc 等 Android 资源文件。
 */
public interface IResourcesLoader {

    /**
     * 注册资源容器工厂。
     * <p>
     * 资源容器工厂用于创建不同类型的资源容器，
     * 例如从 APK 入口中解析出的资源文件集合。
     *
     * @param resContainerFactory 要注册的资源容器工厂
     */
    void addResContainerFactory(IResContainerFactory resContainerFactory);

    /**
     * 注册资源表解析器提供者。
     * <p>
     * 资源表解析器用于解析 Android 二进制资源表（如 resources.arsc），
     * 将二进制格式的资源 ID 映射为可读的资源名称和值。
     *
     * @param resTableParserProvider 要注册的资源表解析器提供者
     */
    void addResTableParserProvider(IResTableParserProvider resTableParserProvider);
}
