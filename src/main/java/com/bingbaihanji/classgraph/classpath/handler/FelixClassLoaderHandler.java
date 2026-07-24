 
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OSGi Felix ClassLoader 的自定义类加载器处理器
 *
 * <p>
 * 该处理器将包 JAR 及所有关联的 Bundle-Classpath JAR 添加到类路径中进行扫描
 *
 * @author elrufaie
 */
class FelixClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public FelixClassLoaderHandler() {
    }

    /**
     * 获取内容位置
     *
     * @param content
     *            内容对象
     * @return 内容位置
     */
    private static File getContentLocation(final Object content, final ReflectionUtils reflectionUtils) {
        return (File) reflectionUtils.invokeMethod(false, content, "getFile");
    }

    /**
     * 添加包
     *
     * @param bundleWiring
     *            包连接
     * @param classLoader
     *            类加载器
     * @param classpathOrderOut
     *            类路径顺序输出
     * @param bundles
     *            包集合
     * @param ScanConfig
     *            扫描规范
     * @param log
     *            日志
     */
    private static void addBundle(final Object bundleWiring, final ClassLoader classLoader,
                                  final ClasspathOrder classpathOrderOut, final Set<Object> bundles, final ScanConfig ScanConfig,
                                  final LogNode log) {
        // 跟踪已处理的包以防止循环
        bundles.add(bundleWiring);

        // 获取此连接的修订版
        final Object revision = classpathOrderOut.reflectionUtils.invokeMethod(false, bundleWiring, "getRevision");
        // 获取内容
        final Object content = classpathOrderOut.reflectionUtils.invokeMethod(false, revision, "getContent");
        final File location = content != null ? getContentLocation(content, classpathOrderOut.reflectionUtils)
                : null;
        if (location != null) {
            // 添加包对象
            classpathOrderOut.addClasspathEntry(location, classLoader, ScanConfig, log);

            // 以及任何嵌入内容
            final List<?> embeddedContent = (List<?>) classpathOrderOut.reflectionUtils.invokeMethod(false,
                    revision, "getContentPath");
            if (embeddedContent != null) {
                for (final Object embedded : embeddedContent) {
                    if (embedded != content) {
                        final File embeddedLocation = embedded != null
                                ? getContentLocation(embedded, classpathOrderOut.reflectionUtils)
                                : null;
                        if (embeddedLocation != null) {
                            classpathOrderOut.addClasspathEntry(embeddedLocation, classLoader, ScanConfig, log);
                        }
                    }
                }
            }
        }
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
                "org.apache.felix.framework.BundleWiringImpl$BundleClassLoaderJava5")
                || ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass,
                "org.apache.felix.framework.BundleWiringImpl$BundleClassLoader");
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
        // 获取 ClassLoader 包的连接
        final Set<Object> bundles = new HashSet<>();
        final Object bundleWiring = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "m_wiring");
        addBundle(bundleWiring, classLoader, classpathOrder, bundles, ScanConfig, log);

        // 处理我们可能连接到的任何其他包TODO：使用 ScanConfig 缩小我们跟踪的连接列表

        final List<?> requiredWires = (List<?>) classpathOrder.reflectionUtils.invokeMethod(false, bundleWiring,
                "getRequiredWires", String.class, null);
        if (requiredWires != null) {
            for (final Object wire : requiredWires) {
                final Object provider = classpathOrder.reflectionUtils.invokeMethod(false, wire,
                        "getProviderWiring");
                if (!bundles.contains(provider)) {
                    addBundle(provider, classLoader, classpathOrder, bundles, ScanConfig, log);
                }
            }
        }
    }
}
