package com.bingbaihanji.classgraph.classpath.handler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderFinder;
import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.reflect.Array;
import java.util.HashSet;
import java.util.Set;

/**
 * 从 Eclipse Equinox ClassLoader 提取类路径条目
 */
class EquinoxClassLoaderHandler implements ClassLoaderHandler {
    /** 字段名 */
    private static final String[] FIELD_NAMES = {"cp", "nestedDirName"};
    /**
     * 如果已读取系统包则为 true我们假设类路径上只有一个系统包，因此这是静态的
     */
    private boolean alreadyReadSystemBundles;

    /** 类不可构造 */
    public EquinoxClassLoaderHandler() {
    }

    /**
     * 添加包文件
     *
     * @param bundlefile
     *            包文件
     * @param path
     *            路径
     * @param classLoader
     *            类加载器
     * @param classpathOrderOut
     *            类路径顺序
     * @param ScanConfig
     *            扫描规范
     * @param log
     *            日志
     */
    private static void addBundleFile(final Object bundlefile, final Set<Object> path,
                                      final ClassLoader classLoader, final ClasspathOrder classpathOrderOut, final ScanConfig ScanConfig,
                                      final LogNode log) {
        // 避免陷入无限循环
        if (bundlefile != null && path.add(bundlefile)) {
            // 类型 File
            final Object baseFile = classpathOrderOut.reflectionUtils.getFieldVal(false, bundlefile, "basefile");
            if (baseFile != null) {
                boolean foundClassPathElement = false;
                for (final String fieldName : FIELD_NAMES) {
                    final Object fieldVal = classpathOrderOut.reflectionUtils.getFieldVal(false, bundlefile,
                            fieldName);
                    if (fieldVal != null) {
                        foundClassPathElement = true;
                        // 找到了基文件和类路径元素，例如 "bin/"
                        Object base = baseFile;
                        String sep = "/";
                        if ("org.eclipse.osgi.storage.bundlefile.NestedDirBundleFile"
                                .equals(bundlefile.getClass().getName())) {
                            // 使用 "!/" 分隔符处理嵌套的 ZipBundleFile
                            final Object baseBundleFile = classpathOrderOut.reflectionUtils.getFieldVal(false,
                                    bundlefile, "baseBundleFile");
                            if (baseBundleFile != null && "org.eclipse.osgi.storage.bundlefile.ZipBundleFile"
                                    .equals(baseBundleFile.getClass().getName())) {
                                base = baseBundleFile;
                                sep = "!/";
                            }
                        }
                        final String pathElement = base + sep + fieldVal;
                        classpathOrderOut.addClasspathEntry(pathElement, classLoader, ScanConfig, log);
                        break;
                    }
                }
                if (!foundClassPathElement) {
                    // 未找到类路径元素，直接使用基文件
                    classpathOrderOut.addClasspathEntry(baseFile.toString(), classLoader, ScanConfig, log);
                }

            }
            addBundleFile(classpathOrderOut.reflectionUtils.getFieldVal(false, bundlefile, "wrapped"), path,
                    classLoader, classpathOrderOut, ScanConfig, log);
            addBundleFile(classpathOrderOut.reflectionUtils.getFieldVal(false, bundlefile, "next"), path,
                    classLoader, classpathOrderOut, ScanConfig, log);
        }
    }

    /**
     * 添加类路径条目
     *
     * @param owner
     *            所有者
     * @param classLoader
     *            类加载器
     * @param classpathOrderOut
     *            类路径顺序输出
     * @param ScanConfig
     *            扫描规范
     * @param log
     *            日志
     */
    private static void addClasspathEntries(final Object owner, final ClassLoader classLoader,
                                            final ClasspathOrder classpathOrderOut, final ScanConfig ScanConfig, final LogNode log) {
        // 类型 ClasspathEntry[]
        final Object entries = classpathOrderOut.reflectionUtils.getFieldVal(false, owner, "entries");
        if (entries != null) {
            for (int i = 0, n = Array.getLength(entries); i < n; i++) {
                // 类型 ClasspathEntry
                final Object entry = Array.get(entries, i);
                // 类型 BundleFile
                final Object bundlefile = classpathOrderOut.reflectionUtils.getFieldVal(false, entry, "bundlefile");
                addBundleFile(bundlefile, new HashSet<>(), classLoader, classpathOrderOut, ScanConfig, log);
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
                "org.eclipse.osgi.internal.loader.EquinoxClassLoader");
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
        // 类型 ClasspathManager
        final Object manager = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "manager");
        addClasspathEntries(manager, classLoader, classpathOrder, ScanConfig, log);

        // 类型 FragmentClasspath[]
        final Object fragments = classpathOrder.reflectionUtils.getFieldVal(false, manager, "fragments");
        if (fragments != null) {
            for (int f = 0, fragLength = Array.getLength(fragments); f < fragLength; f++) {
                // 类型 FragmentClasspath
                final Object fragment = Array.get(fragments, f);
                addClasspathEntries(fragment, classLoader, classpathOrder, ScanConfig, log);
            }
        }
        // 仅读取系统包一次(所有包对此应给出相同结果)
        if (!alreadyReadSystemBundles) {
            // 类型 BundleLoader
            final Object delegate = classpathOrder.reflectionUtils.getFieldVal(false, classLoader, "delegate");
            // 类型 EquinoxContainer
            final Object container = classpathOrder.reflectionUtils.getFieldVal(false, delegate, "container");
            // 类型 Storage
            final Object storage = classpathOrder.reflectionUtils.getFieldVal(false, container, "storage");
            // 类型 ModuleContainer
            final Object moduleContainer = classpathOrder.reflectionUtils.getFieldVal(false, storage,
                    "moduleContainer");
            // 类型 ModuleDatabase
            final Object moduleDatabase = classpathOrder.reflectionUtils.getFieldVal(false, moduleContainer,
                    "moduleDatabase");
            // 类型 HashMap<Integer, EquinoxModule>
            final Object modulesById = classpathOrder.reflectionUtils.getFieldVal(false, moduleDatabase,
                    "modulesById");
            // 类型 EquinoxSystemModule(模块 0 始终是系统模块)
            final Object module0 = classpathOrder.reflectionUtils.invokeMethod(false, modulesById, "get",
                    Object.class, 0L);
            // 类型 Bundle
            final Object bundle = classpathOrder.reflectionUtils.invokeMethod(false, module0, "getBundle");
            // 类型 BundleContext
            final Object bundleContext = classpathOrder.reflectionUtils.invokeMethod(false, bundle,
                    "getBundleContext");
            // 类型 Bundle[]
            final Object bundles = classpathOrder.reflectionUtils.invokeMethod(false, bundleContext, "getBundles");
            if (bundles != null) {
                for (int i = 0, n = Array.getLength(bundles); i < n; i++) {
                    // 类型 EquinoxBundle
                    final Object equinoxBundle = Array.get(bundles, i);
                    // 类型 EquinoxModule
                    final Object module = classpathOrder.reflectionUtils.getFieldVal(false, equinoxBundle,
                            "module");
                    // 类型 String
                    String location = (String) classpathOrder.reflectionUtils.getFieldVal(false, module,
                            "location");
                    if (location != null) {
                        final int fileIdx = location.indexOf("file:");
                        if (fileIdx >= 0) {
                            location = location.substring(fileIdx);
                            classpathOrder.addClasspathEntry(location, classLoader, ScanConfig, log);
                        }
                    }
                }
            }
            alreadyReadSystemBundles = true;
        }
    }
}
