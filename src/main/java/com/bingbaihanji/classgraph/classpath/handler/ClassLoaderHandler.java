 
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

/**
 * ClassLoader 处理器
 *
 * <p>
 * 如果您创建了自定义的 ClassLoaderHandler，请考虑将其提交到 ClassGraph 开源项目
 */
public interface ClassLoaderHandler {

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类之一
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true
     */
    default boolean canHandle(Class<?> classLoaderClass, LogNode log) {
        return false;
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
    default void findClassLoaderOrder(ClassLoader classLoader, ClassLoaderOrder classLoaderOrder,
                                      LogNode log) {
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
    default void findClasspathOrder(ClassLoader classLoader, ClasspathOrder classpathOrder,
                                    ScanConfig ScanConfig, LogNode log) {
    }
}
