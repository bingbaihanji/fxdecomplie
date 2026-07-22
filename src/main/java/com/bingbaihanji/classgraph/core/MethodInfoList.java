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
package com.bingbaihanji.classgraph.core;

import com.bingbaihanji.classgraph.utils.LogNode;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** {@link MethodInfo} 对象列表 */
public class MethodInfoList extends InfoList<MethodInfo> {
    /** 不可修改的空 {@link MethodInfoList} */
    static final MethodInfoList EMPTY_LIST = new MethodInfoList();
    /** 序列化版本 UID */
    private static final long serialVersionUID = 1L;

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /** 构造一个新的可修改的空 {@link MethodInfo} 对象列表 */
    public MethodInfoList() {
        super();
    }

    /**
     * 根据大小提示构造一个新的可修改的空 {@link MethodInfo} 对象列表
     *
     * @param sizeHint
     *            大小提示
     */
    public MethodInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 根据初始的 {@link MethodInfo} 对象集合构造一个新的可修改的 {@link MethodInfoList}
     *
     * @param methodInfoCollection
     *            {@link MethodInfo} 对象的集合
     */
    public MethodInfoList(final Collection<MethodInfo> methodInfoCollection) {
        super(methodInfoCollection);
    }

    /**
     * 返回不可修改的空 {@link MethodInfoList}
     *
     * @return 不可修改的空 {@link MethodInfoList}
     */
    public static MethodInfoList emptyList() {
        return EMPTY_LIST;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型描述符或类型签名中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息集合
     * @param log
     *            日志
     */
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        for (final MethodInfo mi : this) {
            mi.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将此 {@link MethodInfoList} 转换为从方法名称到具有该名称的方法的 {@link MethodInfoList} 的映射
     *
     * @return 此 {@link MethodInfoList} 作为从方法名称到具有该名称的方法的 {@link MethodInfoList} 的映射
     */
    public Map<String, MethodInfoList> asMap() {
        // 注意：MethodInfoList 继承自 InfoList 而非 MappableInfoList，因为一个名称可能被多个
        // MethodInfo 对象共享(因此 asMap() 需要返回 Map<String, MethodInfoList> 而非 Map<String, MethodInfo>)
        final Map<String, MethodInfoList> methodNameToMethodInfoList = new HashMap<>();
        for (final MethodInfo methodInfo : this) {
            final String name = methodInfo.getName();
            MethodInfoList methodInfoList = methodNameToMethodInfoList.get(name);
            if (methodInfoList == null) {
                methodInfoList = new MethodInfoList(1);
                methodNameToMethodInfoList.put(name, methodInfoList);
            }
            methodInfoList.add(methodInfo);
        }
        return methodNameToMethodInfoList;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查列表是否包含具有给定名称的方法
     *
     * @param methodName
     *            方法名称
     * @return 如果列表包含具有给定名称的方法，则返回 true
     */
    public boolean containsName(final String methodName) {
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回所有匹配给定名称的方法列表(由于重载，可能有一个以上的方法具有给定名称，
     * 因此返回 {@link MethodInfoList} 而非单个 {@link MethodInfo})
     *
     * @param methodName
     *            方法名称
     * @return 此列表中具有给定名称的 {@link MethodInfo} 对象的 {@link MethodInfoList}
     *         (由于重载，可能有一个以上的方法具有给定名称，因此返回 {@link MethodInfoList}
     *         而非单个 {@link MethodInfo})如果没有方法具有匹配的名称，则返回空列表
     */
    public MethodInfoList get(final String methodName) {
        boolean hasMethodWithName = false;
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                hasMethodWithName = true;
                break;
            }
        }
        if (!hasMethodWithName) {
            return EMPTY_LIST;
        } else {
            final MethodInfoList matchingMethods = new MethodInfoList(2);
            for (final MethodInfo mi : this) {
                if (mi.getName().equals(methodName)) {
                    matchingMethods.add(mi);
                }
            }
            return matchingMethods;
        }
    }

    /**
     * 返回具有给定名称的单个方法，如果未找到则返回 null如果存在两个具有给定名称的方法，
     * 则抛出 {@link IllegalArgumentException}
     *
     * @param methodName
     *            方法名称
     * @return 列表中具有给定名称的 {@link MethodInfo} 对象(如果恰好存在一个具有给定名称的方法)
     *         如果没有具有给定名称的方法，则返回 null
     * @throws IllegalArgumentException
     *             如果存在两个或更多具有给定名称的方法
     */
    public MethodInfo getSingleMethod(final String methodName) {
        int numMethodsWithName = 0;
        MethodInfo lastFoundMethod = null;
        for (final MethodInfo mi : this) {
            if (mi.getName().equals(methodName)) {
                numMethodsWithName++;
                lastFoundMethod = mi;
            }
        }
        if (numMethodsWithName == 0) {
            return null;
        } else if (numMethodsWithName == 1) {
            return lastFoundMethod;
        } else {
            throw new IllegalArgumentException("类 " + iterator().next().getClassInfo().getName()
                    + " 中有多个名为 \"" + methodName + "\" 的方法");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找此列表中满足给定过滤谓词的 {@link MethodInfo} 对象子集
     *
     * @param filter
     *            要应用的 {@link MethodInfoFilter} 过滤器
     * @return 此列表中满足给定过滤谓词的 {@link MethodInfo} 对象子集
     */
    public MethodInfoList filter(final MethodInfoFilter filter) {
        final MethodInfoList methodInfoFiltered = new MethodInfoList();
        for (final MethodInfo resource : this) {
            if (filter.accept(resource)) {
                methodInfoFiltered.add(resource);
            }
        }
        return methodInfoFiltered;
    }

    /**
     * 使用将 {@link MethodInfo} 对象映射为布尔值的谓词过滤 {@link MethodInfoList}，
     * 为列表中谓词为 true 的所有项生成另一个 {@link MethodInfoList}
     */
    @FunctionalInterface
    public interface MethodInfoFilter {
        /**
         * 是否允许 {@link MethodInfo} 列表项通过过滤器
         *
         * @param methodInfo
         *            要过滤的 {@link MethodInfo} 项
         * @return 是否允许该项通过过滤器如果为 true，则将其复制到输出列表；如果为 false，则将其排除
         */
        boolean accept(MethodInfo methodInfo);
    }
}
