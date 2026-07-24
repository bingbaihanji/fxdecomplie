package com.bingbaihanji.classgraph.scan.config;

import java.util.Collections;
import java.util.List;

/**
 * 运行时选项 — 控制类加载器和模块层发现行为。
 *
 * <p>不可变，通过 {@link ScanOptionsBuilder} 构建。</p>
 */
public final class ScanRuntimeOptions {

    private final List<ClassLoader> addedClassLoaders;
    private final List<ClassLoader> overrideClassLoaders;
    private final List<Object> addedModuleLayers;
    private final List<Object> overrideModuleLayers;
    private final List<Object> classpathElementFilters;
    private final boolean initializeLoadedClasses;
    private final boolean ignoreParentModuleLayers;

    ScanRuntimeOptions(List<ClassLoader> addedClassLoaders,
                       List<ClassLoader> overrideClassLoaders,
                       List<Object> addedModuleLayers,
                       List<Object> overrideModuleLayers,
                       List<Object> classpathElementFilters,
                       boolean initializeLoadedClasses,
                       boolean ignoreParentModuleLayers) {
        this.addedClassLoaders = freezeList(addedClassLoaders);
        this.overrideClassLoaders = freezeList(overrideClassLoaders);
        this.addedModuleLayers = freezeList(addedModuleLayers);
        this.overrideModuleLayers = freezeList(overrideModuleLayers);
        this.classpathElementFilters = freezeList(classpathElementFilters);
        this.initializeLoadedClasses = initializeLoadedClasses;
        this.ignoreParentModuleLayers = ignoreParentModuleLayers;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> freezeList(List<T> list) {
        return list == null ? List.of()
            : Collections.unmodifiableList(List.copyOf(list));
    }

    public static final ScanRuntimeOptions DEFAULT = new ScanRuntimeOptions(
        null, null, null, null, null, false, false
    );

    public List<ClassLoader> addedClassLoaders() { return addedClassLoaders; }
    public List<ClassLoader> overrideClassLoaders() { return overrideClassLoaders; }
    public List<Object> addedModuleLayers() { return addedModuleLayers; }
    public List<Object> overrideModuleLayers() { return overrideModuleLayers; }
    public List<Object> classpathElementFilters() { return classpathElementFilters; }
    public boolean initializeLoadedClasses() { return initializeLoadedClasses; }
    public boolean ignoreParentModuleLayers() { return ignoreParentModuleLayers; }
}
