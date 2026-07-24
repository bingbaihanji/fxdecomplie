 
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.classpath.handler.HandlerRegistry;
import com.bingbaihanji.classgraph.classpath.handler.HandlerRegistry.HandlerRegistryEntry;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanClassLoader;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.FastPathResolver;
import com.bingbaihanji.classgraph.util.FileUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

/** 用于查找唯一有序类路径元素的类 */
public class ClasspathFinder {
    /** 类路径顺序 */
    private final ClasspathOrder classpathOrder;

    /** {@link ModuleFinder}，如果需要扫描模块的话 */
    private final ModuleFinder moduleFinder;

    /**
     * ClassLoader 被调用以加载类的默认顺序，遵循父级优先/父级后置委托顺序
     */
    private ClassLoader[] classLoaderOrderRespectingParentDelegation;

    /**
     * 如果找到的某个类加载器是现有的 {@link ScanClassLoader} 实例，则首先委托到该类加载器，
     * 而不是尝试从当前扫描的 {@link ScanClassLoader} 加载，这样嵌套扫描之间的类才能兼容(#485)
     */
    private ScanClassLoader delegateClassGraphClassLoader;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 用于查找唯一有序类路径元素的类
     *
     * @param ScanConfig
     *            {@link ScanConfig}
     * @param log
     *            日志
     */
    public ClasspathFinder(final ScanConfig ScanConfig, final ReflectionUtils reflectionUtils, final LogNode log) {
        final LogNode classpathFinderLog = log == null ? null : log.log("Finding classpath and modules");

        // 如果覆盖的类加载器是 AppClassLoader，则需要扫描传统类路径(#639)
        boolean forceScanJavaClassPath = false;

        // 如果覆盖了类加载器，检查覆盖的类加载器是否为 JPMS 类加载器
        // 如果是，则需要启用非系统模块扫描
        boolean scanNonSystemModules;
        if (ScanConfig.overrideClasspath != null) {
            // 如果类路径被覆盖，则不扫描非系统模块
            scanNonSystemModules = false;
        } else if (ScanConfig.overrideClassLoaders != null) {
            // 如果覆盖了类加载器，仅当覆盖的类加载器是 JPMS
            // AppClassLoader 或 PlatformClassLoader 时才扫描非系统模块
            scanNonSystemModules = false;
            for (final ClassLoader classLoader : ScanConfig.overrideClassLoaders) {
                final String classLoaderClassName = classLoader.getClass().getName();
                // 无法直接实例化 AppClassLoader 或 PlatformClassLoader，因此如果这些作为
                // 覆盖类加载器传入，它们必定是通过
                // Thread.currentThread().getContextClassLoader() [.getParent()] 或类似方式获取的
                if (!ScanConfig.enableSystemJarsAndModules
                        && "jdk.internal.loader.ClassLoaders$PlatformClassLoader".equals(classLoaderClassName)) {
                    if (classpathFinderLog != null) {
                        classpathFinderLog
                                .log("overrideClassLoaders() was called with an instance of " + classLoaderClassName
                                        + ", so enableSystemJarsAndModules() was called automatically");
                    }
                    ScanConfig.enableSystemJarsAndModules = true;
                }
                if ("jdk.internal.loader.ClassLoaders$AppClassLoader".equals(classLoaderClassName)
                        || "jdk.internal.loader.ClassLoaders$PlatformClassLoader".equals(classLoaderClassName)) {
                    if (classpathFinderLog != null) {
                        classpathFinderLog
                                .log("overrideClassLoaders() was called with an instance of " + classLoaderClassName
                                        + ", so the `java.class.path` classpath will also be scanned");
                    }
                    forceScanJavaClassPath = true;
                }
            }
        } else {
            // 如果类加载器和类路径都未被覆盖，则仅在启用了模块扫描时才扫描非系统模块
            scanNonSystemModules = ScanConfig.scanModules;
        }

        // 仅在请求时才实例化模块查找器
        moduleFinder = scanNonSystemModules || ScanConfig.enableSystemJarsAndModules
                ? new ModuleFinder(new CallStackReader(reflectionUtils).getClassContext(classpathFinderLog),
                ScanConfig, scanNonSystemModules,
                /* scanSystemModules = */ ScanConfig.enableSystemJarsAndModules, reflectionUtils,
                classpathFinderLog)
                : null;

        classpathOrder = new ClasspathOrder(ScanConfig, reflectionUtils);

        // 仅在类路径和类加载器都未被覆盖时才查找环境类加载器
        final ClassLoaderFinder classLoaderFinder = ScanConfig.overrideClasspath == null
                && ScanConfig.overrideClassLoaders == null
                ? new ClassLoaderFinder(ScanConfig, reflectionUtils, classpathFinderLog)
                : null;
        final ClassLoader[] contextClassLoaders = classLoaderFinder == null ? new ClassLoader[0]
                : classLoaderFinder.getContextClassLoaders();
        final ClassLoader defaultClassLoader = contextClassLoaders.length > 0 ? contextClassLoaders[0] : null;
        if (ScanConfig.overrideClasspath != null) {
            // 手动覆盖类路径
            if (ScanConfig.overrideClassLoaders != null && classpathFinderLog != null) {
                classpathFinderLog.log("It is not possible to override both the classpath and the ClassLoaders -- "
                        + "ignoring the ClassLoader override");
            }
            final LogNode overrideLog = classpathFinderLog == null ? null
                    : classpathFinderLog.log("Overriding classpath with: " + ScanConfig.overrideClasspath);
            classpathOrder.addClasspathEntries(ScanConfig.overrideClasspath,
                    // 如果覆盖了类路径，则 ScanClassLoader 中用于加载类的类加载器
                    // 会被一个从覆盖类路径加载的自定义 URLClassLoader 覆盖
                    // 这里仅使用 defaultClassLoader 作为占位符
                    defaultClassLoader, ScanConfig, overrideLog);
            if (overrideLog != null) {
                overrideLog.log("WARNING: when the classpath is overridden, there is no guarantee that the classes "
                        + "found by classpath scanning will be the same as the classes loaded by the "
                        + "context classloader");
            }
            classLoaderOrderRespectingParentDelegation = contextClassLoaders;
        }

        // 如果启用了系统 JAR 和模块，将 JRE rt.jar 添加到类路径开头
        if (ScanConfig.enableSystemJarsAndModules) {
            final String jreRtJar = SystemJarFinder.getJreRtJarPath();

            // 将 rt.jar 和/或 lib/ext JAR 添加到类路径开头(如果启用)
            final LogNode systemJarsLog = classpathFinderLog == null ? null
                    : classpathFinderLog.log("System jars:");
            if (jreRtJar != null) {
                if (ScanConfig.enableSystemJarsAndModules) {
                    classpathOrder.addSystemClasspathEntry(jreRtJar, defaultClassLoader);
                    if (systemJarsLog != null) {
                        systemJarsLog.log("Found rt.jar: " + jreRtJar);
                    }
                } else if (systemJarsLog != null) {
                    systemJarsLog.log((ScanConfig.enableSystemJarsAndModules ? "" : "Scanning disabled for rt.jar: ")
                            + jreRtJar);
                }
            }
            final boolean scanAllLibOrExtJars = !ScanConfig.libOrExtJarAcceptReject.acceptAndRejectAreEmpty();
            for (final String libOrExtJarPath : SystemJarFinder.getJreLibOrExtJars()) {
                if (scanAllLibOrExtJars
                        || ScanConfig.libOrExtJarAcceptReject.isSpecificallyAcceptedAndNotRejected(libOrExtJarPath)) {
                    classpathOrder.addSystemClasspathEntry(libOrExtJarPath, defaultClassLoader);
                    if (systemJarsLog != null) {
                        systemJarsLog.log("Found lib or ext jar: " + libOrExtJarPath);
                    }
                } else if (systemJarsLog != null) {
                    systemJarsLog.log("Scanning disabled for lib or ext jar: " + libOrExtJarPath);
                }
            }
        }

        if (ScanConfig.overrideClasspath == null) {
            // 列出 ClassLoaderHandler
            if (classpathFinderLog != null) {
                final LogNode classLoaderHandlerLog = classpathFinderLog.log("ClassLoaderHandlers:");
                for (final HandlerRegistryEntry classLoaderHandlerEntry : //
                        HandlerRegistry.CLASS_LOADER_HANDLERS) {
                    classLoaderHandlerLog.log(classLoaderHandlerEntry.classLoaderHandler.getClass().getName());
                }
            }

            // 查找所有唯一类加载器，按委托顺序
            final LogNode classloaderOrderLog = classpathFinderLog == null ? null
                    : classpathFinderLog.log("Finding unique classloaders in delegation order");
            final ClassLoaderOrder classLoaderOrder = new ClassLoaderOrder(reflectionUtils);
            final ClassLoader[] origClassLoaderOrder = ScanConfig.overrideClassLoaders != null
                    ? ScanConfig.overrideClassLoaders.toArray(new ClassLoader[0])
                    : contextClassLoaders;
            if (origClassLoaderOrder != null) {
                for (final ClassLoader classLoader : origClassLoaderOrder) {
                    classLoaderOrder.delegateTo(classLoader, /* isParent = */ false, classloaderOrderLog);
                }
            }

            // 获取所有父级类加载器
            final Set<ClassLoader> allParentClassLoaders = classLoaderOrder.getAllParentClassLoaders();

            // 从每个 ClassLoader 获取类路径 URL
            final LogNode classloaderURLLog = classpathFinderLog == null ? null
                    : classpathFinderLog.log("Obtaining URLs from classloaders in delegation order");
            final List<ClassLoader> finalClassLoaderOrder = new ArrayList<>();
            for (final Entry<ClassLoader, List<HandlerRegistryEntry>> ent : classLoaderOrder
                    .getClassLoaderOrder()) {
                final ClassLoader classLoader = ent.getKey();
                for (final HandlerRegistryEntry classLoaderHandlerRegistryEntry : ent.getValue()) {
                    // 将类路径条目添加到 ignoredClasspathOrder 或 classpathOrder
                    if (!ScanConfig.ignoreParentClassLoaders || !allParentClassLoaders.contains(classLoader)) {
                        // 否则将类路径条目添加到 classpathOrder，并将类加载器添加到
                        // 最终的类加载器顺序中
                        final LogNode classloaderHandlerLog = classloaderURLLog == null ? null
                                : classloaderURLLog.log("Classloader " + classLoader.getClass().getName()
                                + " is handled by "
                                + classLoaderHandlerRegistryEntry.classLoaderHandler.getClass().getName());
                        classLoaderHandlerRegistryEntry.findClasspathOrder(classLoader, classpathOrder, ScanConfig,
                                classloaderHandlerLog);
                        finalClassLoaderOrder.add(classLoader);
                    } else if (classloaderURLLog != null) {
                        classloaderURLLog
                                .log("Ignoring parent classloader " + classLoader + ", normally handled by "
                                        + classLoaderHandlerRegistryEntry.classLoaderHandler.getClass().getName());
                    }
                    // 检查是否应首先委托到先前扫描的 ScanClassLoader
                    if (classLoader instanceof ScanClassLoader) {
                        delegateClassGraphClassLoader = (ScanClassLoader) classLoader;
                    }
                }
            }

            // 需要记录类加载器的委托顺序，特别是为了遵循父级后置委托
            // 顺序，因为这不是默认行为(issue #267)
            classLoaderOrderRespectingParentDelegation = finalClassLoaderOrder.toArray(new ClassLoader[0]);
        }

        // 仅在父级类加载器未被忽略、类加载器未被覆盖且类路径未被覆盖的情况下扫描 java.class.path，
        // 除非仅启用了模块扫描且遇到了未命名模块层 -- 在这种情况下，必须强制扫描 java.class.path，
        // 因为 ModuleLayer API 不允许打开未命名模块
        if (forceScanJavaClassPath
                || (!ScanConfig.ignoreParentClassLoaders && ScanConfig.overrideClassLoaders == null
                && ScanConfig.overrideClasspath == null)
                || (moduleFinder != null && moduleFinder.forceScanJavaClassPath())) {
            final String[] pathElements = JarUtils.smartPathSplit(System.getProperty("java.class.path"), ScanConfig);
            if (pathElements.length > 0) {
                final LogNode sysPropLog = classpathFinderLog == null ? null
                        : classpathFinderLog.log("Getting classpath entries from java.class.path");
                for (final String pathElement : pathElements) {
                    // pathElement 也未在忽略的父级类加载器中列出
                    final String pathElementResolved = FastPathResolver.resolve(FileUtils.currDirPath(),
                            pathElement);
                    classpathOrder.addClasspathEntry(pathElementResolved, defaultClassLoader, ScanConfig, sysPropLog);
                }
            }
        }
    }

    /**
     * 获取类路径顺序
     *
     * @return 从 ClassLoader 获取的原始类路径元素的顺序
     */
    public ClasspathOrder getClasspathOrder() {
        return classpathOrder;
    }

    /**
     * 获取 {@link ModuleFinder}
     *
     * @return {@link ModuleFinder}
     */
    public ModuleFinder getModuleFinder() {
        return moduleFinder;
    }

    /**
     * 获取 ClassLoader 顺序，遵循父级优先/父级后置委托顺序
     *
     * @return 类加载器顺序
     */
    public ClassLoader[] getClassLoaderOrderRespectingParentDelegation() {
        return classLoaderOrderRespectingParentDelegation;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 如果找到的某个类加载器是现有的 {@link ScanClassLoader} 实例，则首先委托到该类加载器，
     * 而不是尝试从当前扫描的 {@link ScanClassLoader} 加载，这样嵌套扫描之间的类才能兼容(#485)
     *
     * @return 在使用此扫描自身的 {@link ScanClassLoader} 加载类之前要委托的
     *         {@link ScanClassLoader}(如果没有则返回 null)
     */
    public ScanClassLoader getDelegateClassGraphClassLoader() {
        return delegateClassGraphClassLoader;
    }

    /**
     * 返回要委托到的 ScanClassLoader(如果存在)
     *
     * @return 在使用此扫描自身的 {@link ScanClassLoader} 加载类之前要委托的
     *         {@link ScanClassLoader}(如果没有则返回 null)
     */
    public ScanClassLoader getDelegateScanClassLoader() {
        return delegateClassGraphClassLoader;
    }
}
