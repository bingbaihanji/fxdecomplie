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
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.classloaderhandler.ClassLoaderHandlerRegistry;
import com.bingbaihanji.classgraph.classloaderhandler.ClassLoaderHandlerRegistry.ClassLoaderHandlerRegistryEntry;
import com.bingbaihanji.classgraph.core.ClassGraph;
import com.bingbaihanji.classgraph.reflection.ReflectionUtils;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.*;
import java.util.Map.Entry;

/** 用于查找所有唯一类加载器的类 */
public class ClassLoaderOrder {
    /** {@link ClassLoader} 顺序 */
    private final Map<ClassLoader, List<ClassLoaderHandlerRegistryEntry>> classLoaderOrder = new LinkedHashMap<>();
    /**
     * 已添加到顺序中的所有 {@link ClassLoader} 实例集合，用于防止类加载器被重复添加
     */
    // 这里必须使用 IdentityHashMap 作为映射和集合，因为 TomEE 会异常地使
    // CxfContainerClassLoader 实例(通过 .equals())等于它所委托的
    // TomEEWebappClassLoader 实例(#515)
    private final Set<ClassLoader> added = Collections.newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());
    /**
     * 已委托的所有 {@link ClassLoader} 实例集合，用于防止委托中的无限循环
     */
    private final Set<ClassLoader> delegatedTo = Collections
            .newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());
    /**
     * 已委托的所有父级 {@link ClassLoader} 实例集合，用于支持 {@link ClassGraph#ignoreParentClassLoaders()}
     */
    private final Set<ClassLoader> allParentClassLoaders = Collections
            .newSetFromMap(new IdentityHashMap<ClassLoader, Boolean>());
    public ReflectionUtils reflectionUtils;

    // -------------------------------------------------------------------------------------------------------------

    public ClassLoaderOrder(final ReflectionUtils reflectionUtils) {
        this.reflectionUtils = reflectionUtils;
    }

    /** 获取能够处理给定 ClassLoader 的 ClassLoaderHandler */
    private static List<ClassLoaderHandlerRegistryEntry> getClassLoaderHandlerRegistryEntries(
            final ClassLoader classLoader, final LogNode log) {
        List<ClassLoaderHandlerRegistryEntry> ents = new ArrayList<>();
        boolean matched = false;
        for (final ClassLoaderHandlerRegistryEntry ent : ClassLoaderHandlerRegistry.CLASS_LOADER_HANDLERS) {
            if (ent.canHandle(classLoader.getClass(), log)) {
                // 此 ClassLoaderHandler 可以处理该 ClassLoader 类或其某个父类
                ents.add(ent);
                matched = true;
            }
        }
        if (!matched) {
            ents.add(ClassLoaderHandlerRegistry.FALLBACK_HANDLER);
        }
        return ents;
    }

    /**
     * 获取 {@link ClassLoader} 顺序
     *
     * @return {@link ClassLoader} 顺序，以键值对形式返回：{@link ClassLoader}、
     *         {@link ClassLoaderHandlerRegistryEntry}
     */
    public List<Entry<ClassLoader, List<ClassLoaderHandlerRegistryEntry>>> getClassLoaderOrder() {
        return new ArrayList<>(classLoaderOrder.entrySet());
    }

    /**
     * 获取所有父级类加载器
     *
     * @return 所有父级类加载器
     */
    public Set<ClassLoader> getAllParentClassLoaders() {
        return allParentClassLoaders;
    }

    /**
     * 将 {@link ClassLoader} 添加到当前 ClassLoader 顺序中
     *
     * @param classLoader
     *            类加载器
     * @param log
     *            日志
     */
    public void add(final ClassLoader classLoader, final LogNode log) {
        if (classLoader == null) {
            return;
        }
        if (added.add(classLoader)) {
            classLoaderOrder.put(classLoader, getClassLoaderHandlerRegistryEntries(classLoader, log));
        }
    }

    /**
     * 递归委托到另一个 {@link ClassLoader}
     *
     * @param classLoader
     *            类加载器
     * @param isParent
     *            如果这是另一个类加载器的父级则为 true
     * @param log
     *            日志
     */
    public void delegateTo(final ClassLoader classLoader, final boolean isParent, final LogNode log) {
        if (classLoader == null) {
            return;
        }
        // 在检查类加载器是否已在 delegatedTo 集合中之前，先检查它是否为父级，
        // 这样即使类加载器是上下文类加载器同时也是父级，它仍会被标记为
        // 父级类加载器
        if (isParent) {
            allParentClassLoaders.add(classLoader);
        }
        // 不要重复委托到同一个类加载器
        if (delegatedTo.add(classLoader)) {
            add(classLoader, log);
            // 递归获取委托顺序
            // (注意：如果有多个 ClassLoaderHandler 可以处理此 classloader，结果可能不正确)
            for (final ClassLoaderHandlerRegistryEntry entry : getClassLoaderHandlerRegistryEntries(classLoader,
                    /* 不重复记录日志 -- 上面的 add 方法也会记录 */ null)) {
                entry.findClassLoaderOrder(classLoader, this, log);
            }
        }
    }
}
