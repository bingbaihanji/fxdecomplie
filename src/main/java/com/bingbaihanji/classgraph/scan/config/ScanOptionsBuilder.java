package com.bingbaihanji.classgraph.scan.config;

import java.util.*;
import java.util.function.Consumer;

/**
 * {@link ScanOptions} 的流式构造器。
 *
 * <p>线程不安全 — 每个构建过程使用一个实例。构建完成后调用 {@link #build()}
 * 生成不可变的 {@link ScanOptions}。</p>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 方式1: 直接设置
 * ScanOptions opts = new ScanOptionsBuilder()
 *     .enableClassInfo(true)
 *     .enableFieldInfo(true)
 *     .scanJars(false)
 *     .build();
 *
 * // 方式2: 使用子配置构造器
 * ScanOptions opts = new ScanOptionsBuilder()
 *     .metadata(m -> m.toBuilder()
 *         .enableClassInfo(true)
 *         .enableFieldInfo(true)
 *         .build())
 *     .classpath(c -> c.toBuilder()
 *         .scanDirs(false)
 *         .build())
 *     .build();
 * }</pre>
 */
public final class ScanOptionsBuilder {

    private ScanMetadataOptions metadata = ScanMetadataOptions.DEFAULT;
    private ScanClasspathOptions classpath = ScanClasspathOptions.DEFAULT;
    private ScanRuntimeOptions runtime = ScanRuntimeOptions.DEFAULT;
    private ScanFilters filters = ScanFilters.DEFAULT;
    private int maxThreads;

    // ─── 子配置构建器工厂 ───

    /** 使用 lambda 自定义元数据选项 */
    public ScanOptionsBuilder metadata(Consumer<MetadataBuilder> configurator) {
        MetadataBuilder builder = new MetadataBuilder(metadata);
        configurator.accept(builder);
        this.metadata = builder.build();
        return this;
    }

    /** 直接设置元数据选项 */
    public ScanOptionsBuilder metadata(ScanMetadataOptions metadata) {
        this.metadata = metadata != null ? metadata : ScanMetadataOptions.DEFAULT;
        return this;
    }

    /** 使用 lambda 自定义类路径选项 */
    public ScanOptionsBuilder classpath(Consumer<ClasspathBuilder> configurator) {
        ClasspathBuilder builder = new ClasspathBuilder(classpath);
        configurator.accept(builder);
        this.classpath = builder.build();
        return this;
    }

    /** 直接设置类路径选项 */
    public ScanOptionsBuilder classpath(ScanClasspathOptions classpath) {
        this.classpath = classpath != null ? classpath : ScanClasspathOptions.DEFAULT;
        return this;
    }

    /** 使用 lambda 自定义运行时选项 */
    public ScanOptionsBuilder runtime(Consumer<RuntimeBuilder> configurator) {
        RuntimeBuilder builder = new RuntimeBuilder(runtime);
        configurator.accept(builder);
        this.runtime = builder.build();
        return this;
    }

    /** 直接设置运行时选项 */
    public ScanOptionsBuilder runtime(ScanRuntimeOptions runtime) {
        this.runtime = runtime != null ? runtime : ScanRuntimeOptions.DEFAULT;
        return this;
    }

    /** 使用 lambda 自定义过滤器选项 */
    public ScanOptionsBuilder filters(Consumer<FiltersBuilder> configurator) {
        FiltersBuilder builder = new FiltersBuilder(filters);
        configurator.accept(builder);
        this.filters = builder.build();
        return this;
    }

    /** 直接设置过滤器选项 */
    public ScanOptionsBuilder filters(ScanFilters filters) {
        this.filters = filters != null ? filters : ScanFilters.DEFAULT;
        return this;
    }

    // ─── 便捷方法（直接设置常用选项） ───

    public ScanOptionsBuilder enableClassInfo(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableClassInfo(v).build();
        return this;
    }

    public ScanOptionsBuilder enableFieldInfo(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableFieldInfo(v).build();
        return this;
    }

    public ScanOptionsBuilder enableMethodInfo(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableMethodInfo(v).build();
        return this;
    }

    public ScanOptionsBuilder enableAnnotationInfo(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableAnnotationInfo(v).build();
        return this;
    }

    public ScanOptionsBuilder enableExternalClasses(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableExternalClasses(v).build();
        return this;
    }

    public ScanOptionsBuilder enableInterClassDependencies(boolean v) {
        this.metadata = new MetadataBuilder(metadata).enableInterClassDependencies(v).build();
        return this;
    }

    public ScanOptionsBuilder scanJars(boolean v) {
        this.classpath = new ClasspathBuilder(classpath).scanJars(v).build();
        return this;
    }

    public ScanOptionsBuilder scanNestedJars(boolean v) {
        this.classpath = new ClasspathBuilder(classpath).scanNestedJars(v).build();
        return this;
    }

    public ScanOptionsBuilder scanDirs(boolean v) {
        this.classpath = new ClasspathBuilder(classpath).scanDirs(v).build();
        return this;
    }

    public ScanOptionsBuilder scanModules(boolean v) {
        this.classpath = new ClasspathBuilder(classpath).scanModules(v).build();
        return this;
    }

    public ScanOptionsBuilder enableSystemJarsAndModules(boolean v) {
        this.classpath = new ClasspathBuilder(classpath).enableSystemJarsAndModules(v).build();
        return this;
    }

    public ScanOptionsBuilder ignoreClassVisibility(boolean v) {
        this.filters = new FiltersBuilder(filters).ignoreClassVisibility(v).build();
        return this;
    }

    public ScanOptionsBuilder ignoreFieldVisibility(boolean v) {
        this.filters = new FiltersBuilder(filters).ignoreFieldVisibility(v).build();
        return this;
    }

    public ScanOptionsBuilder ignoreMethodVisibility(boolean v) {
        this.filters = new FiltersBuilder(filters).ignoreMethodVisibility(v).build();
        return this;
    }

    public ScanOptionsBuilder maxThreads(int v) {
        this.maxThreads = Math.max(0, v);
        return this;
    }

    // ─── 构建 ───

    /** 构建不可变的 {@link ScanOptions} */
    public ScanOptions build() {
        return new ScanOptions(metadata, classpath, runtime, filters, maxThreads);
    }

    // ─── 子构建器 ───

    /** {@link ScanMetadataOptions} 的构建器 */
    public static final class MetadataBuilder {
        private boolean enableClassInfo;
        private boolean enableFieldInfo;
        private boolean enableMethodInfo;
        private boolean enableAnnotationInfo;
        private boolean enableStaticFinalValues;
        private boolean enableInterClassDeps;
        private boolean enableExternalClasses;

        MetadataBuilder(ScanMetadataOptions base) {
            this.enableClassInfo = base.enableClassInfo();
            this.enableFieldInfo = base.enableFieldInfo();
            this.enableMethodInfo = base.enableMethodInfo();
            this.enableAnnotationInfo = base.enableAnnotationInfo();
            this.enableStaticFinalValues = base.enableStaticFinalFieldConstantInitializerValues();
            this.enableInterClassDeps = base.enableInterClassDependencies();
            this.enableExternalClasses = base.enableExternalClasses();
        }

        public MetadataBuilder enableClassInfo(boolean v) { this.enableClassInfo = v; return this; }
        public MetadataBuilder enableFieldInfo(boolean v) { this.enableFieldInfo = v; return this; }
        public MetadataBuilder enableMethodInfo(boolean v) { this.enableMethodInfo = v; return this; }
        public MetadataBuilder enableAnnotationInfo(boolean v) { this.enableAnnotationInfo = v; return this; }
        public MetadataBuilder enableStaticFinalFieldConstantInitializerValues(boolean v) { this.enableStaticFinalValues = v; return this; }
        public MetadataBuilder enableInterClassDependencies(boolean v) { this.enableInterClassDeps = v; return this; }
        public MetadataBuilder enableExternalClasses(boolean v) { this.enableExternalClasses = v; return this; }

        /** 快捷方法：启用所有元数据 */
        public MetadataBuilder withAll() {
            this.enableClassInfo = true;
            this.enableFieldInfo = true;
            this.enableMethodInfo = true;
            this.enableAnnotationInfo = true;
            this.enableStaticFinalValues = true;
            this.enableInterClassDeps = true;
            this.enableExternalClasses = true;
            return this;
        }

        public ScanMetadataOptions build() {
            return new ScanMetadataOptions(enableClassInfo, enableFieldInfo,
                enableMethodInfo, enableAnnotationInfo, enableStaticFinalValues,
                enableInterClassDeps, enableExternalClasses);
        }
    }

    /** {@link ScanClasspathOptions} 的构建器 */
    public static final class ClasspathBuilder {
        private boolean scanJars = true;
        private boolean scanNestedJars = true;
        private boolean scanDirs = true;
        private boolean scanModules = true;
        private boolean enableSystemJarsAndModules;
        private Set<String> allowedURLSchemes;
        private List<Object> overrideClasspath;
        private int maxBufferedJarRAMSize = 64 * 1024 * 1024;
        private boolean enableMemoryMapping;
        private boolean enableMultiReleaseVersions;
        private boolean removeTemporaryFilesAfterScan;
        private boolean ignoreParentClassLoaders;

        ClasspathBuilder(ScanClasspathOptions base) {
            this.scanJars = base.scanJars();
            this.scanNestedJars = base.scanNestedJars();
            this.scanDirs = base.scanDirs();
            this.scanModules = base.scanModules();
            this.enableSystemJarsAndModules = base.enableSystemJarsAndModules();
            this.allowedURLSchemes = base.allowedURLSchemes().isEmpty() ? null
                : new HashSet<>(base.allowedURLSchemes());
            this.overrideClasspath = base.overrideClasspath().isEmpty() ? null
                : new ArrayList<>(base.overrideClasspath());
            this.maxBufferedJarRAMSize = base.maxBufferedJarRAMSize();
            this.enableMemoryMapping = base.enableMemoryMapping();
            this.enableMultiReleaseVersions = base.enableMultiReleaseVersions();
            this.removeTemporaryFilesAfterScan = base.removeTemporaryFilesAfterScan();
            this.ignoreParentClassLoaders = base.ignoreParentClassLoaders();
        }

        public ClasspathBuilder scanJars(boolean v) { this.scanJars = v; return this; }
        public ClasspathBuilder scanNestedJars(boolean v) { this.scanNestedJars = v; return this; }
        public ClasspathBuilder scanDirs(boolean v) { this.scanDirs = v; return this; }
        public ClasspathBuilder scanModules(boolean v) { this.scanModules = v; return this; }
        public ClasspathBuilder enableSystemJarsAndModules(boolean v) { this.enableSystemJarsAndModules = v; return this; }
        public ClasspathBuilder enableMemoryMapping(boolean v) { this.enableMemoryMapping = v; return this; }
        public ClasspathBuilder enableMultiReleaseVersions(boolean v) { this.enableMultiReleaseVersions = v; return this; }
        public ClasspathBuilder removeTemporaryFilesAfterScan(boolean v) { this.removeTemporaryFilesAfterScan = v; return this; }
        public ClasspathBuilder ignoreParentClassLoaders(boolean v) { this.ignoreParentClassLoaders = v; return this; }
        public ClasspathBuilder maxBufferedJarRAMSize(int v) { this.maxBufferedJarRAMSize = v; return this; }

        public ClasspathBuilder addURLScheme(String scheme) {
            if (this.allowedURLSchemes == null) this.allowedURLSchemes = new HashSet<>();
            this.allowedURLSchemes.add(scheme);
            return this;
        }

        public ClasspathBuilder addClasspathOverride(Object path) {
            if (this.overrideClasspath == null) this.overrideClasspath = new ArrayList<>();
            this.overrideClasspath.add(path);
            return this;
        }

        public ScanClasspathOptions build() {
            return new ScanClasspathOptions(scanJars, scanNestedJars, scanDirs,
                scanModules, enableSystemJarsAndModules, allowedURLSchemes,
                overrideClasspath, maxBufferedJarRAMSize, enableMemoryMapping,
                enableMultiReleaseVersions, removeTemporaryFilesAfterScan,
                ignoreParentClassLoaders);
        }
    }

    /** {@link ScanRuntimeOptions} 的构建器 */
    public static final class RuntimeBuilder {
        private List<ClassLoader> addedClassLoaders;
        private List<ClassLoader> overrideClassLoaders;
        private List<Object> addedModuleLayers;
        private List<Object> overrideModuleLayers;
        private List<Object> classpathElementFilters;
        private boolean initializeLoadedClasses;
        private boolean ignoreParentModuleLayers;

        RuntimeBuilder(ScanRuntimeOptions base) {
            this.addedClassLoaders = base.addedClassLoaders().isEmpty() ? null
                : new ArrayList<>(base.addedClassLoaders());
            this.overrideClassLoaders = base.overrideClassLoaders().isEmpty() ? null
                : new ArrayList<>(base.overrideClassLoaders());
            this.addedModuleLayers = base.addedModuleLayers().isEmpty() ? null
                : new ArrayList<>(base.addedModuleLayers());
            this.overrideModuleLayers = base.overrideModuleLayers().isEmpty() ? null
                : new ArrayList<>(base.overrideModuleLayers());
            this.classpathElementFilters = base.classpathElementFilters().isEmpty() ? null
                : new ArrayList<>(base.classpathElementFilters());
            this.initializeLoadedClasses = base.initializeLoadedClasses();
            this.ignoreParentModuleLayers = base.ignoreParentModuleLayers();
        }

        public RuntimeBuilder addClassLoader(ClassLoader cl) {
            if (this.addedClassLoaders == null) this.addedClassLoaders = new ArrayList<>();
            this.addedClassLoaders.add(cl);
            return this;
        }

        public RuntimeBuilder overrideClassLoaders(ClassLoader... cls) {
            this.overrideClassLoaders = new ArrayList<>(List.of(cls));
            return this;
        }

        public RuntimeBuilder addModuleLayer(Object layer) {
            if (this.addedModuleLayers == null) this.addedModuleLayers = new ArrayList<>();
            this.addedModuleLayers.add(layer);
            return this;
        }

        public RuntimeBuilder initializeLoadedClasses(boolean v) { this.initializeLoadedClasses = v; return this; }
        public RuntimeBuilder ignoreParentModuleLayers(boolean v) { this.ignoreParentModuleLayers = v; return this; }

        public ScanRuntimeOptions build() {
            return new ScanRuntimeOptions(addedClassLoaders, overrideClassLoaders,
                addedModuleLayers, overrideModuleLayers, classpathElementFilters,
                initializeLoadedClasses, ignoreParentModuleLayers);
        }
    }

    /** {@link ScanFilters} 的构建器 */
    public static final class FiltersBuilder {
        private boolean ignoreClassVisibility;
        private boolean ignoreFieldVisibility;
        private boolean ignoreMethodVisibility;
        private boolean disableRuntimeInvisibleAnnotations;
        private boolean extendScanningUpwardsToExternalClasses = true;

        FiltersBuilder(ScanFilters base) {
            this.ignoreClassVisibility = base.ignoreClassVisibility();
            this.ignoreFieldVisibility = base.ignoreFieldVisibility();
            this.ignoreMethodVisibility = base.ignoreMethodVisibility();
            this.disableRuntimeInvisibleAnnotations = base.disableRuntimeInvisibleAnnotations();
            this.extendScanningUpwardsToExternalClasses = base.extendScanningUpwardsToExternalClasses();
        }

        public FiltersBuilder ignoreClassVisibility(boolean v) { this.ignoreClassVisibility = v; return this; }
        public FiltersBuilder ignoreFieldVisibility(boolean v) { this.ignoreFieldVisibility = v; return this; }
        public FiltersBuilder ignoreMethodVisibility(boolean v) { this.ignoreMethodVisibility = v; return this; }
        public FiltersBuilder disableRuntimeInvisibleAnnotations(boolean v) { this.disableRuntimeInvisibleAnnotations = v; return this; }
        public FiltersBuilder extendScanningUpwardsToExternalClasses(boolean v) { this.extendScanningUpwardsToExternalClasses = v; return this; }

        public ScanFilters build() {
            return new ScanFilters(ignoreClassVisibility, ignoreFieldVisibility,
                ignoreMethodVisibility, disableRuntimeInvisibleAnnotations,
                extendScanningUpwardsToExternalClasses);
        }
    }
}
