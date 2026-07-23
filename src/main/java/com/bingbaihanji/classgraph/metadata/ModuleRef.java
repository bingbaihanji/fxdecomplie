/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.reflection.ReflectionUtils;
import com.bingbaihanji.classgraph.utils.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** ModuleReference 代理，使用反射编写以保持与 JDK 7 和 8 的向后兼容性 */
public class ModuleRef implements Comparable<ModuleRef> {
    /** 模块名称 */
    private final String name;

    /** 模块的 ModuleReference */
    private final Object reference;

    /** 模块的 ModuleLayer */
    private final Object layer;

    /** 模块的 ModuleDescriptor */
    private final Object descriptor;

    /** 模块中的包列表 */
    private final List<String> packages;

    /** 模块的位置 URI(可能为 null) */
    private final URI location;
    /** 加载模块中类的 ClassLoader可能为 null，表示引导类加载器 */
    private final ClassLoader classLoader;
    ReflectionUtils reflectionUtils;
    /** 模块的位置 URI，作为缓存的字符串(可能为 null) */
    private String locationStr;
    /** 由位置 URI 形成的文件如果位置 URI 是 "jrt:" URI，则该文件不存在 */
    private File locationFile;
    /** 原始模块版本，如果没有则为 null */
    private String rawVersion;

    /**
     * 构造函数
     *
     * @param moduleReference
     *            模块引用，JPMS 类型 ModuleReference
     * @param moduleLayer
     *            模块层，JPMS 类型 ModuleLayer
     * @param reflectionUtils
     *            ReflectionUtils 实例
     */
    public ModuleRef(final Object moduleReference, final Object moduleLayer,
                     final ReflectionUtils reflectionUtils) {
        if (moduleReference == null) {
            throw new IllegalArgumentException("moduleReference 不能为 null");
        }
        if (moduleLayer == null) {
            throw new IllegalArgumentException("moduleLayer 不能为 null");
        }
        this.reference = moduleReference;
        this.layer = moduleLayer;
        this.reflectionUtils = reflectionUtils;

        this.descriptor = reflectionUtils.invokeMethod(/* throwException = */ true, moduleReference, "descriptor");
        if (this.descriptor == null) {
            // 不应该发生
            throw new IllegalArgumentException("moduleReference.descriptor() 不应返回 null");
        }
        this.name = (String) reflectionUtils.invokeMethod(/* throwException = */ true, this.descriptor, "name");
        @SuppressWarnings("unchecked") final Set<String> modulePackages = (Set<String>) reflectionUtils.invokeMethod(/* throwException = */ true,
                this.descriptor, "packages");
        if (modulePackages == null) {
            // 不应该发生
            throw new IllegalArgumentException("moduleReference.descriptor().packages() 不应返回 null");
        }
        this.packages = new ArrayList<>(modulePackages);
        CollectionUtils.sortIfNotEmpty(this.packages);
        final Object optionalRawVersion = reflectionUtils.invokeMethod(/* throwException = */ true, this.descriptor,
                "rawVersion");
        if (optionalRawVersion != null) {
            final Boolean isPresent = (Boolean) reflectionUtils.invokeMethod(/* throwException = */ true,
                    optionalRawVersion, "isPresent");
            if (isPresent != null && isPresent) {
                this.rawVersion = (String) reflectionUtils.invokeMethod(/* throwException = */ true,
                        optionalRawVersion, "get");
            }
        }
        final Object moduleLocationOptional = reflectionUtils.invokeMethod(/* throwException = */ true,
                moduleReference, "location");
        if (moduleLocationOptional == null) {
            // 不应该发生
            throw new IllegalArgumentException("moduleReference.location() 不应返回 null");
        }
        final Object moduleLocationIsPresent = reflectionUtils.invokeMethod(/* throwException = */ true,
                moduleLocationOptional, "isPresent");
        if (moduleLocationIsPresent == null) {
            // 不应该发生
            throw new IllegalArgumentException("moduleReference.location().isPresent() 不应返回 null");
        }
        if ((Boolean) moduleLocationIsPresent) {
            this.location = (URI) reflectionUtils.invokeMethod(/* throwException = */ true, moduleLocationOptional,
                    "get");
            if (this.location == null) {
                // 不应该发生
                throw new IllegalArgumentException("moduleReference.location().get() 不应返回 null");
            }
        } else {
            this.location = null;
        }

        // 查找模块的类加载器
        this.classLoader = (ClassLoader) reflectionUtils.invokeMethod(/* throwException = */ true, moduleLayer,
                "findLoader", String.class, this.name);
    }

    /**
     * 获取模块名称，即 {@code getReference().descriptor().name()}
     *
     * @return 模块名称，即 {@code getReference().descriptor().name()}可能为 null 或空字符串
     */
    public String getName() {
        return name;
    }

    /**
     * 获取模块引用(JPMS 类型 ModuleReference)
     *
     * @return 模块引用(JPMS 类型 ModuleReference)
     */
    public Object getReference() {
        return reference;
    }

    /**
     * 获取模块层(JPMS 类型 ModuleLayer)
     *
     * @return 模块层(JPMS 类型 ModuleLayer)
     */
    public Object getLayer() {
        return layer;
    }

    /**
     * 获取模块描述符，即 {@code getReference().descriptor()}(JPMS 类型 ModuleDescriptor)
     *
     * @return 模块描述符，即 {@code getReference().descriptor()}(JPMS 类型 ModuleDescriptor)
     */
    public Object getDescriptor() {
        return descriptor;
    }

    /**
     * 获取模块中的包列表(不包括非包目录)
     *
     * @return 模块中的包列表(不包括非包目录)
     */
    public List<String> getPackages() {
        return packages;
    }

    /**
     * 获取模块位置，即 {@code getReference().location()}对于没有位置的模块返回 null
     *
     * @return 模块位置，即 {@code getReference().location()}对于没有位置的模块返回 null
     */
    public URI getLocation() {
        return location;
    }

    /**
     * 以字符串形式获取模块位置，即 {@code getReference().location().toString()}对于没有位置的模块返回 null
     *
     * @return 以字符串形式表示的模块位置，即 {@code getReference().location().toString()}对于没有位置的模块返回 null
     */
    public String getLocationStr() {
        if (locationStr == null && location != null) {
            locationStr = location.toString();
        }
        return locationStr;
    }

    /**
     * 以 File 形式获取模块位置，即 {@code new File(getReference().location())}对于没有位置的模块，
     * 或者对于系统(或 jlinked)模块(其 "jrt:" 位置 URI 仅包含模块名称而不包含模块 jar 位置)，返回 null
     *
     * @return 以 File 形式表示的模块位置，即 {@code new File(getReference().location())}对于没有位置的模块，
     *         或者位置为 "jrt:" URI 的模块返回 null
     */
    public File getLocationFile() {
        if (locationFile == null && location != null && "file".equals(location.getScheme())) {
            locationFile = new File(location);
        }
        return locationFile;
    }

    /**
     * 获取模块的原始版本字符串，如果模块未提供则返回 null
     *
     * @return 模块的原始版本，通过 {@code ModuleReference#rawVersion().orElse(null)} 获取
     */
    public String getRawVersion() {
        return rawVersion;
    }

    /**
     * 检查此模块是否为系统模块
     *
     * @return 如果此模块是系统模块则返回 true
     */
    public boolean isSystemModule() {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("javafx.")
                || name.startsWith("oracle.");
    }

    /**
     * 获取模块的类加载器
     *
     * @return 模块的类加载器，即
     *         {@code moduleLayer.findLoader(getReference().descriptor().name())}
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ModuleRef)) {
            return false;
        }
        final ModuleRef modRef = (ModuleRef) obj;
        return modRef.reference.equals(this.reference) && modRef.layer.equals(this.layer);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return reference.hashCode() * layer.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return reference.toString();
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final ModuleRef o) {
        final int diff = this.name.compareTo(o.name);
        return diff != 0 ? diff : this.hashCode() - o.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 打开模块，返回一个 {@link ModuleReaderProxy}
     *
     * @return 模块的 {@link ModuleReaderProxy}
     * @throws IOException
     *             如果模块无法打开
     */
    public ModuleReaderProxy open() throws IOException {
        return new ModuleReaderProxy(this);
    }
}
