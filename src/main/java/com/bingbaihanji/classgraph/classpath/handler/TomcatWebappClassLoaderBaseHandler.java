 
package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.util.List;

/** 从 Tomcat/Catalina WebappClassLoaderBase 提取类路径条目 */
class TomcatWebappClassLoaderBaseHandler implements ClassLoaderHandler {
    /** 类不可构造 */
    public TomcatWebappClassLoaderBaseHandler() {
    }

    /**
     * 如果此类加载器委托给其父级，则返回 true
     *
     * @param classLoader
     *            {@link ClassLoader}
     * @return 如果此类加载器委托给其父级，则返回 true
     */
    private static boolean isParentFirst(final ClassLoader classLoader, final ReflectionUtils reflectionUtils) {
        final Object delegateObject = reflectionUtils.getFieldVal(false, classLoader, "delegate");
        if (delegateObject != null) {
            return (boolean) delegateObject;
        }
        // 假设父级优先委托顺序
        return true;
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
                "org.apache.catalina.loader.WebappClassLoaderBase");
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
        final boolean isParentFirst = isParentFirst(classLoader, classLoaderOrder.reflectionUtils);
        if (isParentFirst) {
            // 使用父级优先委托顺序
            classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        }
        if ("org.apache.tomee.catalina.TomEEWebappClassLoader".equals(classLoader.getClass().getName())) {
            // TomEEWebappClassLoader 有很多复杂的委托规则，包括特定于类名的委托，
            // 当前 ClassGraph 模型不支持这些规则，因此我们只是尝试用固定顺序来近似委托顺序
            try {
                classLoaderOrder.delegateTo(Class.forName("org.apache.openejb.OpenEJB").getClassLoader(),
                        /* isParent = */ true, log);
            } catch (LinkageError | ClassNotFoundException e) {
                // 忽略
            }
        }
        classLoaderOrder.add(classLoader, log);
        if (!isParentFirst) {
            // 使用父级最后委托顺序
            classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        }
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
        // 类型 StandardRoot(实现 WebResourceRoot)
        final Object resources = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getResources");
        // 类型 List<URL>
        final Object baseURLs = classpathOrder.reflectionUtils.invokeMethod(false, resources, "getBaseUrls");
        classpathOrder.addClasspathEntryObject(baseURLs, classLoader, ScanConfig, log);
        // 类型 List<List<WebResourceSet>>
        // 成员：preResources、mainResources、classResources、jarResources、
        // postResources
        @SuppressWarnings("unchecked") final List<List<?>> allResources = (List<List<?>>) classpathOrder.reflectionUtils.getFieldVal(false,
                resources, "allResources");
        if (allResources != null) {
            // 类型 List<WebResourceSet>
            for (final List<?> webResourceSetList : allResources) {
                // 类型 WebResourceSet
                // {DirResourceSet, FileResourceSet, JarResourceSet, JarWarResourceSet,
                // EmptyResourceSet}
                for (final Object webResourceSet : webResourceSetList) {
                    if (webResourceSet != null) {
                        // 对于 DirResourceSet
                        final File file = (File) classpathOrder.reflectionUtils.invokeMethod(false, webResourceSet,
                                "getFileBase");
                        String base = file == null ? null : file.getPath();
                        if (base == null) {
                            // 对于 FileResourceSet
                            base = (String) classpathOrder.reflectionUtils.invokeMethod(false, webResourceSet,
                                    "getBase");
                        }
                        if (base == null) {
                            // 对于 JarResourceSet 和 JarWarResourceSet
                            // 文件系统上 JAR 所在 WAR 文件的绝对路径
                            base = (String) classpathOrder.reflectionUtils.invokeMethod(false, webResourceSet,
                                    "getBaseUrlString");
                        }
                        if (base != null) {
                            // 对于 JarWarResourceSet：WAR 文件中 JAR 文件所在的路径
                            final String archivePath = (String) classpathOrder.reflectionUtils.getFieldVal(false,
                                    webResourceSet, "archivePath");
                            if (archivePath != null && !archivePath.isEmpty()) {
                                // 如果 archivePath 非空，则表示这是 WAR 中的 JAR
                                base += "!" + (archivePath.startsWith("/") ? archivePath : "/" + archivePath);
                            }
                            final String className = webResourceSet.getClass().getName();
                            final boolean isJar = "java.org.apache.catalina.webresources.JarResourceSet"
                                    .equals(className)
                                    || "java.org.apache.catalina.webresources.JarWarResourceSet".equals(className);
                            // 此 WebResourceSet 中资源将从中提供服务的路径，
                            // 例如对于资源 JAR，这将是 "META-INF/resources"
                            final String internalPath = (String) classpathOrder.reflectionUtils.invokeMethod(false,
                                    webResourceSet, "getInternalPath");
                            if (internalPath != null && !internalPath.isEmpty() && !"/".equals(internalPath)) {
                                classpathOrder.addClasspathEntryObject(base + (isJar ? "!" : "")
                                                + (internalPath.startsWith("/") ? internalPath : "/" + internalPath),
                                        classLoader, ScanConfig, log);
                            } else {
                                classpathOrder.addClasspathEntryObject(base, classLoader, ScanConfig, log);
                            }
                        }
                    }
                }
            }
        }
        // 这可能与上述内容重复，也可能不重复
        final Object urls = classpathOrder.reflectionUtils.invokeMethod(false, classLoader, "getURLs");
        classpathOrder.addClasspathEntryObject(urls, classLoader, ScanConfig, log);
    }
}
