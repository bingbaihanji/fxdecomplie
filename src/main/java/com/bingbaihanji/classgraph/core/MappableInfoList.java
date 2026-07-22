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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 可通过名称索引的命名对象列表
 *
 * @param <T>
 *            元素类型
 */
public class MappableInfoList<T extends HasName> extends InfoList<T> {
    /** 序列化版本 UID */
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     */
    MappableInfoList() {
        super();
    }

    /**
     * 构造函数
     *
     * @param sizeHint
     *            大小提示
     */
    MappableInfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造函数
     *
     * @param infoCollection
     *            初始元素集合
     */
    MappableInfoList(final Collection<T> infoCollection) {
        super(infoCollection);
    }

    /**
     * 获取此列表的索引，作为从每个列表项的名称(通过对每个列表项调用 {@code getName()} 获取)到列表项的映射
     *
     * @return 此列表的索引，作为从每个列表项的名称(通过对每个列表项调用 {@code getName()} 获取)到列表项的映射
     */
    public Map<String, T> asMap() {
        final Map<String, T> nameToInfoObject = new HashMap<>(Math.max(16, (int) Math.ceil(size() / 0.75f)));
        for (final T i : this) {
            if (i != null) {
                nameToInfoObject.put(i.getName(), i);
            }
        }
        return nameToInfoObject;
    }

    /**
     * 检查此列表是否包含具有给定名称的项
     *
     * @param name
     *            要搜索的名称
     * @return 如果此列表包含具有给定名称的项，则返回 true
     */
    public boolean containsName(final String name) {
        for (final T i : this) {
            if (i != null && i.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取具有给定名称的列表项，如果未找到则返回 null
     *
     * @param name
     *            要搜索的名称
     * @return 具有给定名称的列表项，如果未找到则返回 null
     */
    @SuppressWarnings("null")
    public T get(final String name) {
        for (final T i : this) {
            if (i != null && i.getName().equals(name)) {
                return i;
            }
        }
        return null;
    }
}