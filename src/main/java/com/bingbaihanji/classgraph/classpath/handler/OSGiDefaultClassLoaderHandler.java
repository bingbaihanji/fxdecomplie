 
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;

/**
 * 处理 OSGi DefaultClassLoader
 *
 * @author lukehutch
 */
class OSGiDefaultClassLoaderHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public OSGiDefaultClassLoaderHandler() {
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
                "org.eclipse.osgi.internal.baseadaptor.DefaultClassLoader");
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
        final Object classpathManager = classpathOrder.reflectionUtils.invokeMethod(false, classLoader,
                "getClasspathManager");
        final Object[] entries = (Object[]) classpathOrder.reflectionUtils.getFieldVal(false, classpathManager,
                "entries");
        if (entries != null) {
            for (final Object entry : entries) {
                final Object bundleFile = classpathOrder.reflectionUtils.invokeMethod(false, entry,
                        "getBundleFile");
                final File baseFile = (File) classpathOrder.reflectionUtils.invokeMethod(false, bundleFile,
                        "getBaseFile");
                if (baseFile != null) {
                    classpathOrder.addClasspathEntry(baseFile.getPath(), classLoader, ScanConfig, log);
                }
            }
        }
    }
}
