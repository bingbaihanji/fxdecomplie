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

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.util.*;

import com.bingbaihanji.classgraph.metadata.ClassInfo.ReachableAndDirectlyRelatedClasses;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintWriter;
import java.util.*;

/**
 * 一个<i>去重后</i>(已去重)的 {@link ClassInfo} 对象列表，同时存储了可达类(通过给定的类关系获得，可以是直接关系或间接路径)
 * 和直接相关类(仅通过直接关系可达的类)(默认情况下，将 {@link ClassInfoList} 作为 {@link List} 访问时
 * 只返回可达类；通过调用 {@link #directOnly()} 可以获取直接相关类)
 *
 * <p>
 * ClassGraph 返回的大多数 {@link ClassInfoList} 对象按 {@link ClassInfo#getName()} 的值进行字典序排序
 * 一个例外是由 {@link ClassInfo#getSuperclasses()} 返回的类，它们按类层次结构的升序排列
 */
public class ClassInfoList extends InfoList<ClassInfo> {
    /** 一个不可修改的空 {@link ClassInfoList} */
    static final ClassInfoList EMPTY_LIST = new ClassInfoList();
    /** serialVersionUID */
    private static final long serialVersionUID = 1L;

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /** 是否按名称排序 */
    private final boolean sortByName;
    /** 直接相关类 */
    // 注意：此字段标记为 transient 以满足 Scrutinizer 的要求，因为此类扩展了 ArrayList(ArrayList 是
    // Serializable 的)，因此所有字段都必须是可序列化的(而 Set 是一个接口，不是 Serializable)
    // 将此字段标记为 transient 意味着直接关系将在序列化时丢失，但 Serializable 接口
    // 如今已不再广泛使用
    private transient Set<ClassInfo> directlyRelatedClasses;

    /**
     * 构造一个可修改的 {@link ClassInfo} 对象列表，包含可达类(通过传递闭包获得)和直接相关类(在图中一步之遥)
     *
     * @param reachableClasses
     *            可达类
     * @param directlyRelatedClasses
     *            直接相关类
     * @param sortByName
     *            是否按名称排序
     */
    ClassInfoList(final Set<ClassInfo> reachableClasses, final Set<ClassInfo> directlyRelatedClasses,
                  final boolean sortByName) {
        super(reachableClasses);
        this.sortByName = sortByName;
        if (sortByName) {
            // 在构造函数中调用 CollectionUtils.sortIfNotEmpty(this) 有点冒险，
            // 但父类构造函数已经调用完毕，所以应该没问题 :-)
            CollectionUtils.sortIfNotEmpty(this);
        }
        // 如果未提供 directlyRelatedClasses，则假定所有可达类都是直接相关的
        this.directlyRelatedClasses = directlyRelatedClasses == null ? reachableClasses : directlyRelatedClasses;
    }

    /**
     * 构造一个可修改的 {@link ClassInfo} 对象列表
     *
     * @param reachableAndDirectlyRelatedClasses
     *            可达类和直接相关类
     * @param sortByName
     *            是否按名称排序
     */
    ClassInfoList(final ReachableAndDirectlyRelatedClasses reachableAndDirectlyRelatedClasses,
                  final boolean sortByName) {
        this(reachableAndDirectlyRelatedClasses.reachableClasses,
                reachableAndDirectlyRelatedClasses.directlyRelatedClasses, sortByName);
    }

    /**
     * 构造一个可修改的 {@link ClassInfo} 对象列表，其中每个类都是直接相关的
     *
     * @param reachableClasses
     *            可达类
     * @param sortByName
     *            是否按名称排序
     */
    ClassInfoList(final Set<ClassInfo> reachableClasses, final boolean sortByName) {
        this(reachableClasses, /* directlyRelatedClasses = */ null, sortByName);
    }

    /**
     * 构造一个新的空的可修改的 {@link ClassInfo} 对象列表
     */
    public ClassInfoList() {
        super(1);
        this.sortByName = false;
        directlyRelatedClasses = new HashSet<>(2);
    }

    /**
     * 给定一个大小提示，构造一个新的空的可修改的 {@link ClassInfo} 对象列表
     *
     * @param sizeHint
     *            大小提示
     */
    public ClassInfoList(final int sizeHint) {
        super(sizeHint);
        this.sortByName = false;
        directlyRelatedClasses = new HashSet<>(2);
    }

    /**
     * 给定一个初始的 {@link ClassInfo} 对象列表，构造一个新的可修改的空 {@link ClassInfoList}
     *
     * <p>
     * 如果传入的 {@link Collection} 不是 {@link Set}，则 {@link ClassInfo} 对象在被添加到返回的列表之前
     * 会先进行去重(通过将其添加到 Set 中)返回列表中的 {@link ClassInfo} 对象将按名称排序
     *
     * @param classInfoCollection
     *            要添加到 {@link ClassInfoList} 中的 {@link ClassInfo} 对象的初始集合
     */
    public ClassInfoList(final Collection<ClassInfo> classInfoCollection) {
        this(classInfoCollection instanceof Set //
                        ? (Set<ClassInfo>) classInfoCollection
                        : new HashSet<>(classInfoCollection), //
                /* directlyRelatedClasses = */ null, /* sortByName = */ true);
    }

    /**
     * 返回一个不可修改的空 {@link ClassInfoList}
     *
     * @return 不可修改的空 {@link ClassInfoList}
     */
    public static ClassInfoList emptyList() {
        return EMPTY_LIST;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将此 {@link ClassInfo} 对象列表转换为 {@code Class<?>} 对象列表，将列表中的每一项强制转换为
     * 所请求的超类或接口类型会导致类加载器加载每个 {@link ClassInfo} 对象命名的类(如果尚未加载的话)
     *
     * <p>
     * <b>重要说明：</b>由于 {@code superclassOrInterfaceType} 是一个已加载类的类引用，
     * 因此至关重要的是 {@code superclassOrInterfaceType} 必须由此 {@code ClassInfo} 对象所引用的类
     * 所使用的同一个类加载器加载，否则类转换将失败
     *
     * @param <T>
     *            超类或接口
     * @param superclassOrInterfaceType
     *            用于将每个加载的类强制转换到的超类或接口类引用
     * @param ignoreExceptions
     *            如果为 true，则忽略类加载期间或尝试将生成的 {@code Class<?>} 引用强制转换到
     *            所请求类型时抛出的任何异常或错误——而是跳过该元素(即返回的列表可能包含比输入列表更少的项)
     *            如果为 false，则在类无法加载或无法强制转换到所请求类型时抛出
     *            {@link IllegalArgumentException}
     * @return 与此列表中每个 {@link ClassInfo} 对象对应的已加载的 {@code Class<?>} 对象
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，并且在尝试加载或强制转换任何类时抛出了异常或错误
     */
    public <T> List<Class<T>> loadClasses(final Class<T> superclassOrInterfaceType,
                                          final boolean ignoreExceptions) {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<Class<T>> classRefs = new ArrayList<>();
            for (final ClassInfo classInfo : this) {
                final Class<T> classRef = classInfo.loadClass(superclassOrInterfaceType, ignoreExceptions);
                if (classRef != null) {
                    classRefs.add(classRef);
                }
            }
            return classRefs.isEmpty() ? Collections.<Class<T>>emptyList() : classRefs;
        }
    }

    /**
     * 将此 {@link ClassInfo} 对象列表转换为 {@code Class<?>} 对象列表，将列表中的每一项强制转换为
     * 所请求的超类或接口类型会导致类加载器加载每个 {@link ClassInfo} 对象命名的类(如果尚未加载的话)
     *
     * <p>
     * <b>重要说明：</b>由于 {@code superclassOrInterfaceType} 是一个已加载类的类引用，
     * 因此至关重要的是 {@code superclassOrInterfaceType} 必须由此 {@code ClassInfo} 对象所引用的类
     * 所使用的同一个类加载器加载，否则类转换将失败
     *
     * @param <T>
     *            超类或接口
     * @param superclassOrInterfaceType
     *            用于将每个加载的类强制转换到的超类或接口类引用
     * @return 与此列表中每个 {@link ClassInfo} 对象对应的已加载的 {@code Class<?>} 对象
     * @throws IllegalArgumentException
     *             如果在尝试加载或强制转换任何类时抛出了异常或错误
     */
    public <T> List<Class<T>> loadClasses(final Class<T> superclassOrInterfaceType) {
        return loadClasses(superclassOrInterfaceType, /* ignoreExceptions = */ false);
    }

    /**
     * 将此 {@link ClassInfo} 对象列表转换为 {@code Class<?>} 对象列表会导致类加载器加载
     * 每个 {@link ClassInfo} 对象命名的类(如果尚未加载的话)
     *
     * @param ignoreExceptions
     *            如果为 true，则忽略类加载期间抛出的任何异常或错误如果类加载期间抛出了异常或错误，
     *            则不会将对应的 {@link ClassInfo} 对象的 {@code Class<?>} 引用添加到输出类中，
     *            因此返回的列表可能包含比输入列表更少的项如果为 false，则在类无法加载时抛出
     *            {@link IllegalArgumentException}
     * @return 与此列表中每个 {@link ClassInfo} 对象对应的已加载的 {@code Class<?>} 对象
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，并且在尝试加载任何类时抛出了异常或错误
     */
    public List<Class<?>> loadClasses(final boolean ignoreExceptions) {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<Class<?>> classRefs = new ArrayList<>();
            // 尝试加载每个类
            for (final ClassInfo classInfo : this) {
                final Class<?> classRef = classInfo.loadClass(ignoreExceptions);
                if (classRef != null) {
                    classRefs.add(classRef);
                }
            }
            return classRefs.isEmpty() ? Collections.<Class<?>>emptyList() : classRefs;
        }
    }

    /**
     * 将此 {@link ClassInfo} 对象列表转换为 {@code Class<?>} 对象列表会导致类加载器加载
     * 每个 {@link ClassInfo} 对象命名的类(如果尚未加载的话)
     *
     * @return 与此列表中每个 {@link ClassInfo} 对象对应的已加载的 {@code Class<?>} 对象
     * @throws IllegalArgumentException
     *             如果在尝试加载任何类时抛出了异常或错误
     */
    public List<Class<?>> loadClasses() {
        return loadClasses(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取直接相关的类列表，而非通过多步可达的类例如，如果此 {@link ClassInfoList} 是通过查询
     * 某个给定类的所有超类生成的，那么 {@link #directOnly()} 将只返回该类的直接超类
     *
     * @return 直接相关类的列表
     */
    public ClassInfoList directOnly() {
        return new ClassInfoList(directlyRelatedClasses, directlyRelatedClasses, sortByName);
    }

    /**
     * 反序列化时恢复 directlyRelatedClasses 字段，避免 {@link #directOnly()} 抛出 NPE。
     * 由于直接关系在序列化时已经丢失，这里保守地将所有可达类视为直接相关。
     */
    private void readObject(final ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (directlyRelatedClasses == null) {
            directlyRelatedClasses = new HashSet<>(this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 求此 {@link ClassInfoList} 与一个或多个其他列表的并集
     *
     * @param others
     *            要与此列表求并集的其他 {@link ClassInfoList}
     * @return 此 {@link ClassInfoList} 与其他列表的并集
     */
    public ClassInfoList union(final ClassInfoList... others) {
        final Set<ClassInfo> reachableClassesUnion = new LinkedHashSet<>(this);
        final Set<ClassInfo> directlyRelatedClassesUnion = new LinkedHashSet<>(directlyRelatedClasses);
        for (final ClassInfoList other : others) {
            reachableClassesUnion.addAll(other);
            directlyRelatedClassesUnion.addAll(other.directlyRelatedClasses);
        }
        return new ClassInfoList(reachableClassesUnion, directlyRelatedClassesUnion, sortByName);
    }

    /**
     * 求此 {@link ClassInfoList} 与一个或多个其他列表的交集
     *
     * @param others
     *            要与此列表求交集的其他 {@link ClassInfoList}
     * @return 此 {@link ClassInfoList} 与其他列表的交集
     */
    public ClassInfoList intersect(final ClassInfoList... others) {
        // 将第一个不按名称排序的 ClassInfoList 放在列表头部，
        // 以便在交集中保留其顺序 (#238)
        final ArrayDeque<ClassInfoList> intersectionOrder = new ArrayDeque<>();
        intersectionOrder.add(this);
        boolean foundFirst = false;
        for (final ClassInfoList other : others) {
            if (other.sortByName) {
                intersectionOrder.add(other);
            } else if (!foundFirst) {
                foundFirst = true;
                intersectionOrder.push(other);
            } else {
                intersectionOrder.add(other);
            }
        }
        final ClassInfoList first = intersectionOrder.remove();
        final Set<ClassInfo> reachableClassesIntersection = new LinkedHashSet<>(first);
        while (!intersectionOrder.isEmpty()) {
            reachableClassesIntersection.retainAll(intersectionOrder.remove());
        }
        final Set<ClassInfo> directlyRelatedClassesIntersection = new LinkedHashSet<>(directlyRelatedClasses);
        for (final ClassInfoList other : others) {
            directlyRelatedClassesIntersection.retainAll(other.directlyRelatedClasses);
        }
        return new ClassInfoList(reachableClassesIntersection, directlyRelatedClassesIntersection,
                first.sortByName);
    }

    /**
     * 求此 {@link ClassInfoList} 与另一个 {@link ClassInfoList} 的差集，即 (this \ other)
     *
     * @param other
     *            要从此列表中减去的另一个 {@link ClassInfoList}
     * @return 此 {@link ClassInfoList} 与另一个的差集，即 (this \ other)
     */
    public ClassInfoList exclude(final ClassInfoList other) {
        final Set<ClassInfo> reachableClassesDifference = new LinkedHashSet<>(this);
        final Set<ClassInfo> directlyRelatedClassesDifference = new LinkedHashSet<>(directlyRelatedClasses);
        reachableClassesDifference.removeAll(other);
        directlyRelatedClassesDifference.removeAll(other.directlyRelatedClasses);
        return new ClassInfoList(reachableClassesDifference, directlyRelatedClassesDifference, sortByName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 找出此 {@link ClassInfoList} 中给定过滤谓词为 true 的子集
     *
     * @param filter
     *            要应用的 {@link ClassInfoFilter}
     * @return 此 {@link ClassInfoList} 中给定过滤谓词为 true 的子集
     */
    public ClassInfoList filter(final ClassInfoFilter filter) {
        final Set<ClassInfo> reachableClassesFiltered = new LinkedHashSet<>(size());
        final Set<ClassInfo> directlyRelatedClassesFiltered = new LinkedHashSet<>(directlyRelatedClasses.size());
        for (final ClassInfo ci : this) {
            if (filter.accept(ci)) {
                reachableClassesFiltered.add(ci);
                if (directlyRelatedClasses.contains(ci)) {
                    directlyRelatedClassesFiltered.add(ci);
                }
            }
        }
        return new ClassInfoList(reachableClassesFiltered, directlyRelatedClassesFiltered, sortByName);
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含标准类(非接口或注解的类)
     *
     * @return 过滤后的列表，仅包含标准类
     */
    public ClassInfoList getStandardClasses() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isStandardClass();
            }
        });
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 过滤此 {@link ClassInfoList}，仅包含非注解的接口另见 {@link #getInterfacesAndAnnotations()}
     *
     * @return 过滤后的列表，仅包含接口
     */
    public ClassInfoList getInterfaces() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isInterface();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含接口和注解(注解是接口，可以被实现)另见 {@link #getInterfaces()}
     *
     * @return 过滤后的列表，仅包含接口
     */
    public ClassInfoList getInterfacesAndAnnotations() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isInterfaceOrAnnotation();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含已实现的接口，即非注解接口，或已被某个类实现的注解
     *
     * @return 过滤后的列表，仅包含已实现的接口
     */
    public ClassInfoList getImplementedInterfaces() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isImplementedInterface();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含注解
     *
     * @return 过滤后的列表，仅包含注解
     */
    public ClassInfoList getAnnotations() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isAnnotation();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含 {@link Enum} 类
     *
     * @return 过滤后的列表，仅包含枚举
     */
    public ClassInfoList getEnums() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isEnum();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含 {@code record} 类
     *
     * @return 过滤后的列表，仅包含 {@code record} 类
     */
    public ClassInfoList getRecords() {
        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return ci.isRecord();
            }
        });
    }

    /**
     * 过滤此 {@link ClassInfoList}，仅包含可赋值给所请求类 assignableToClass 的类
     * (即 assignableToClass 是列表元素的超类或已实现的接口)
     *
     * @param superclassOrInterface
     *            要过滤的超类或接口
     * @return 过滤后的列表，仅包含那些对应的 {@code Class<?>} 引用满足
     *         {@code assignableToClassRef.isAssignableFrom(listItemClassRef)} 为 true 的类
     *         如果没有类可赋值给所请求的类，则返回空列表
     * @throws IllegalArgumentException
     *             如果 classInfo 为 null
     */
    public ClassInfoList getAssignableTo(final ClassInfo superclassOrInterface) {
        if (superclassOrInterface == null) {
            throw new IllegalArgumentException("assignableToClass parameter cannot be null");
        }
        // 获取 assignableFromClass 的子类和实现类
        final Set<ClassInfo> allAssignableFromClasses = new HashSet<>();
        if (superclassOrInterface.isStandardClass()) {
            allAssignableFromClasses.addAll(superclassOrInterface.getSubclasses());
        } else if (superclassOrInterface.isInterfaceOrAnnotation()) {
            allAssignableFromClasses.addAll(superclassOrInterface.getClassesImplementing());
        }
        // 一个类是其自身的超类或接口
        allAssignableFromClasses.add(superclassOrInterface);

        return filter(new ClassInfoFilter() {
            @Override
            public boolean accept(final ClassInfo ci) {
                return allAssignableFromClasses.contains(ci);
            }
        });
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化返回的图表仅显示类间依赖关系
     * sizeX 和 sizeY 参数是要求 GraphViz 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     * 在使用此方法之前，必须在扫描前调用 {@link ClassGraph#enableInterClassDependencies()}
     *
     * @param sizeX
     *            GraphViz 布局宽度(英寸)
     * @param sizeY
     *            GraphViz 布局高度(英寸)
     * @param includeExternalClasses
     *            如果为 true，并且扫描前调用了 {@link ClassGraph#enableExternalClasses()}，则在依赖图中显示
     *            "外部类"(未被接受的类)
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableInterClassDependencies()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFileFromInterClassDependencies(final float sizeX, final float sizeY,
                                                                    final boolean includeExternalClasses) {
        if (isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        final ScanConfig ScanConfig = get(0).scanResult.ScanConfig;
        if (!ScanConfig.enableInterClassDependencies) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableInterClassDependencies() before #scan()");
        }
        return GraphvizDotfileGenerator.generateGraphVizDotFileFromInterClassDependencies(this, sizeX, sizeY,
                includeExternalClasses);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化返回的图表仅显示类间依赖关系
     * sizeX 和 sizeY 参数是要求 GraphViz 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     * 在使用此方法之前，必须在扫描前调用 {@link ClassGraph#enableInterClassDependencies()}
     *
     * <p>
     * 等效于以 (10.5f, 8f, ScanConfig.enableExternalClasses) 参数调用
     * {@link #generateGraphVizDotFileFromInterClassDependencies(float, float, boolean)}，
     * 其中 ScanConfig.enableExternalClasses 在扫描前调用了
     * {@link ClassGraph#enableExternalClasses()} 时为 true
     *
     * @param sizeX
     *            GraphViz 布局宽度(英寸)
     * @param sizeY
     *            GraphViz 布局高度(英寸)
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableInterClassDependencies()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFileFromInterClassDependencies(final float sizeX, final float sizeY) {
        if (isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        final ScanConfig ScanConfig = get(0).scanResult.ScanConfig;
        if (!ScanConfig.enableInterClassDependencies) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableInterClassDependencies() before #scan()");
        }
        return GraphvizDotfileGenerator.generateGraphVizDotFileFromInterClassDependencies(this, sizeX, sizeY,
                ScanConfig.enableExternalClasses);
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化返回的图表仅显示类间依赖关系
     * sizeX 和 sizeY 参数是要求 GraphViz 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     * 在使用此方法之前，必须在扫描前调用 {@link ClassGraph#enableInterClassDependencies()}
     *
     * <p>
     * 等效于以 (10.5f, 8f, ScanConfig.enableExternalClasses) 参数调用
     * {@link #generateGraphVizDotFileFromInterClassDependencies(float, float, boolean)}，
     * 其中 ScanConfig.enableExternalClasses 在扫描前调用了
     * {@link ClassGraph#enableExternalClasses()} 时为 true
     *
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableInterClassDependencies()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFileFromInterClassDependencies() {
        if (isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        final ScanConfig ScanConfig = get(0).scanResult.ScanConfig;
        if (!ScanConfig.enableInterClassDependencies) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableInterClassDependencies() before #scan()");
        }
        return GraphvizDotfileGenerator.generateGraphVizDotFileFromInterClassDependencies(this, 10.5f, 8.0f,
                ScanConfig.enableExternalClasses);
    }

    /**
     * 已弃用：请改用 {@link #generateGraphVizDotFileFromInterClassDependencies()}
     *
     * @deprecated 请改用 {@link #generateGraphVizDotFileFromInterClassDependencies()}
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableInterClassDependencies()}(因为没有可图表化的内容)
     */
    @Deprecated
    public String generateGraphVizDotFileFromClassDependencies() {
        return generateGraphVizDotFileFromInterClassDependencies();
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化sizeX 和 sizeY 参数是要求 GraphViz
     * 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     *
     * <p>
     * 要显示非公共类，请在扫描前调用 {@link ClassGraph#ignoreClassVisibility()}
     *
     * <p>
     * 要显示字段，请在扫描前调用 {@link ClassGraph#enableFieldInfo()}要显示非公共字段，
     * 还须在扫描前调用 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * <p>
     * 要显示方法，请在扫描前调用 {@link ClassGraph#enableMethodInfo()}要显示非公共方法，
     * 还须在扫描前调用 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * <p>
     * 要显示注解，请在扫描前调用 {@link ClassGraph#enableAnnotationInfo()}要显示非公共注解，
     * 还须在扫描前调用 {@link ClassGraph#ignoreFieldVisibility()}(注解没有独立的可见性修饰符)
     *
     * @param sizeX
     *            GraphViz 布局宽度(英寸)
     * @param sizeY
     *            GraphViz 布局高度(英寸)
     * @param showFields
     *            如果为 true，在图中类节点内显示字段
     * @param showFieldTypeDependencyEdges
     *            如果为 true，显示类与其字段类型之间的边
     * @param showMethods
     *            如果为 true，在图中类节点内显示方法
     * @param showMethodTypeDependencyEdges
     *            如果为 true，显示类与其方法的返回类型和/或参数类型之间的边
     * @param showAnnotations
     *            如果为 true，在图中显示注解
     * @param useSimpleNames
     *            是否在类型签名中对类使用简单名称(如果为 true，则在方法和字段类型签名中
     *            从类名中去除包名)
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableClassInfo()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFile(final float sizeX, final float sizeY, final boolean showFields,
                                          final boolean showFieldTypeDependencyEdges, final boolean showMethods,
                                          final boolean showMethodTypeDependencyEdges, final boolean showAnnotations,
                                          final boolean useSimpleNames) {
        if (isEmpty()) {
            throw new IllegalArgumentException("List is empty");
        }
        final ScanConfig ScanConfig = get(0).scanResult.ScanConfig;
        if (!ScanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return GraphvizDotfileGenerator.generateGraphVizDotFile(this, sizeX, sizeY, showFields,
                showFieldTypeDependencyEdges, showMethods, showMethodTypeDependencyEdges, showAnnotations,
                useSimpleNames, ScanConfig);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化sizeX 和 sizeY 参数是要求 GraphViz
     * 渲染 .dot 文件时使用的图像输出尺寸(以英寸为单位)
     *
     * <p>
     * 要显示非公共类，请在扫描前调用 {@link ClassGraph#ignoreClassVisibility()}
     *
     * <p>
     * 要显示字段，请在扫描前调用 {@link ClassGraph#enableFieldInfo()}要显示非公共字段，
     * 还须在扫描前调用 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * <p>
     * 要显示方法，请在扫描前调用 {@link ClassGraph#enableMethodInfo()}要显示非公共方法，
     * 还须在扫描前调用 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * <p>
     * 要显示注解，请在扫描前调用 {@link ClassGraph#enableAnnotationInfo()}要显示非公共注解，
     * 还须在扫描前调用 {@link ClassGraph#ignoreFieldVisibility()}(注解没有独立的可见性修饰符)
     *
     * <p>
     * 此方法在字段和方法的类型签名中使用类的简单名称(去除包名)
     *
     * @param sizeX
     *            GraphViz 布局宽度(英寸)
     * @param sizeY
     *            GraphViz 布局高度(英寸)
     * @param showFields
     *            如果为 true，在图中类节点内显示字段
     * @param showFieldTypeDependencyEdges
     *            如果为 true，显示类与其字段类型之间的边
     * @param showMethods
     *            如果为 true，在图中类节点内显示方法
     * @param showMethodTypeDependencyEdges
     *            如果为 true，显示类与其方法的返回类型和/或参数类型之间的边
     * @param showAnnotations
     *            如果为 true，在图中显示注解
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableClassInfo()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFile(final float sizeX, final float sizeY, final boolean showFields,
                                          final boolean showFieldTypeDependencyEdges, final boolean showMethods,
                                          final boolean showMethodTypeDependencyEdges, final boolean showAnnotations) {
        return generateGraphVizDotFile(sizeX, sizeY, showFields, showFieldTypeDependencyEdges, showMethods,
                showMethodTypeDependencyEdges, showAnnotations, true);
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化
     *
     * <p>
     * 如果已通过 {@link ClassGraph#enableMethodInfo()}、{@link ClassGraph#enableFieldInfo()} 和
     * {@link ClassGraph#enableAnnotationInfo()} 启用了方法、字段和注解，则会显示它们
     *
     * <p>
     * 仅显示公共类、方法和字段，除非已调用 {@link ClassGraph#ignoreClassVisibility()}、
     * {@link ClassGraph#ignoreMethodVisibility()} 和/或 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @param sizeX
     *            GraphViz 布局宽度(英寸)
     * @param sizeY
     *            GraphViz 布局高度(英寸)
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableClassInfo()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFile(final float sizeX, final float sizeY) {
        return generateGraphVizDotFile(sizeX, sizeY, /* showFields = */ true,
                /* showFieldTypeDependencyEdges = */ true, /* showMethods = */ true,
                /* showMethodTypeDependencyEdges = */ true, /* showAnnotations = */ true);
    }

    /**
     * 生成一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化
     *
     * <p>
     * 如果已通过 {@link ClassGraph#enableMethodInfo()}、{@link ClassGraph#enableFieldInfo()} 和
     * {@link ClassGraph#enableAnnotationInfo()} 启用了方法、字段和注解，则会显示它们
     *
     * <p>
     * 仅显示公共类、方法和字段，除非已调用 {@link ClassGraph#ignoreClassVisibility()}、
     * {@link ClassGraph#ignoreMethodVisibility()} 和/或 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @return GraphViz 文件内容
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableClassInfo()}(因为没有可图表化的内容)
     */
    public String generateGraphVizDotFile() {
        return generateGraphVizDotFile(/* sizeX = */ 10.5f, /* sizeY = */ 8.0f, /* showFields = */ true,
                /* showFieldTypeDependencyEdges = */ true, /* showMethods = */ true,
                /* showMethodTypeDependencyEdges = */ true, /* showAnnotations = */ true);
    }

    /**
     * 生成并保存一个 .dot 文件，可输入到 GraphViz 中进行类图的布局和可视化
     *
     * <p>
     * 如果已通过 {@link ClassGraph#enableMethodInfo()}、{@link ClassGraph#enableFieldInfo()} 和
     * {@link ClassGraph#enableAnnotationInfo()} 启用了方法、字段和注解，则会显示它们
     *
     * <p>
     * 仅显示公共类、方法和字段，除非已调用 {@link ClassGraph#ignoreClassVisibility()}、
     * {@link ClassGraph#ignoreMethodVisibility()} 和/或 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @param file
     *            用于保存 GraphViz .dot 文件的文件
     * @throws IOException
     *             如果文件无法保存
     * @throws IllegalArgumentException
     *             如果此 {@link ClassInfoList} 为空，或者扫描前未调用
     *             {@link ClassGraph#enableClassInfo()}(因为没有可图表化的内容)
     */
    public void generateGraphVizDotFile(final File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(file)) {
            writer.print(generateGraphVizDotFile());
        }
    }

    /* (non-Javadoc)
     * @see java.util.ArrayList#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof ClassInfoList)) {
            return false;
        }
        final ClassInfoList other = (ClassInfoList) obj;
        if ((directlyRelatedClasses == null) != (other.directlyRelatedClasses == null)) {
            return false;
        }
        if (directlyRelatedClasses == null) {
            return super.equals(other);
        }
        return super.equals(other) && directlyRelatedClasses.equals(other.directlyRelatedClasses);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.util.ArrayList#hashCode()
     */
    @Override
    public int hashCode() {
        return super.hashCode() ^ (directlyRelatedClasses == null ? 0 : directlyRelatedClasses.hashCode());
    }

    /**
     * 使用一个将 {@link ClassInfo} 对象映射为布尔值的谓词来过滤 {@link ClassInfoList}，
     * 为列表中谓词为 true 的所有项生成另一个 {@link ClassInfoList}
     */
    @FunctionalInterface
    public interface ClassInfoFilter {
        /**
         * 是否允许某个 {@link ClassInfo} 列表项通过过滤器
         *
         * @param classInfo
         *            要过滤的 {@link ClassInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，则该项被复制到输出列表；如果为 false，则被排除
         */
        boolean accept(ClassInfo classInfo);
    }
}
