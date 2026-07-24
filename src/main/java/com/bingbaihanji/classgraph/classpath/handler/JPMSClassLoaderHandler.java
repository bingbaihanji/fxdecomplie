 
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.net.URL;

/**
 * 一个占位 ClassLoaderHandler，用于匹配 Java 9+ 类加载器，但不尝试从中提取 URL(模块扫描使用与类路径扫描不同的机制)
 */
class JPMSClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public JPMSClassLoaderHandler() {
    }

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true
     */
    @Override
    public boolean canHandle(final Class<?> classLoaderClass, final LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "jdk.internal.loader.ClassLoaders$AppClassLoader")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "jdk.internal.loader.BuiltinClassLoader");
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序
     *
     * @param classLoader
     *            要查找委托顺序的 {@link ClassLoader}
     * @param classLoaderOrder
     *            要更新的 {@link ClassLoaderOrder} 对象
     * @param log
     *            日志
     */
    @Override
    public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                     final LogNode log) {
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        classLoaderOrder.add(classLoader, log);
    }

    /**
     * 查找关联 {@link ClassLoader} 的类路径条目
     *
     * @param classLoader
     *            要查找类路径条目顺序的 {@link ClassLoader}
     * @param classpathOrder
     *            要更新的 {@link ClasspathOrder} 对象
     * @param ScanConfig
     *            {@link ScanConfig}
     * @param log
     *            日志
     */
    @Override
    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanConfig ScanConfig, final LogNode log) {
        // JDK9 类加载器有一个字段 URLClassPath ucp，其中包含未命名模块的 URL，但它不可见因此模块必须使用 JPMS API 进行扫描
        // 然而，Java 代理可以通过直接添加到 ucp 字段来扩展 UCP(#537)，并且无法读取此字段
        // 因此，我们需要使用 Narcissus 打破 Java 的封装来读取它，以应对这种小型边界情况
        final Object ucpVal = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "ucp");
        if (ucpVal != null) {
            final URL[] urls = (URL[]) classpathOrder.reflectionUtils.invokeMethod(false, ucpVal, "getURLs");
            classpathOrder.addClasspathEntryObject(urls, classLoader, ScanConfig, log);
        }
    }
}
