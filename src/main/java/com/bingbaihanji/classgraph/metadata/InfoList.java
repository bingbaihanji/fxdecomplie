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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 命名对象列表
 *
 * @param <T>
 *            元素类型
 */
public class InfoList<T extends Named> extends LazyList<T> {
    /** 序列化版本 UID */
    static final long serialVersionUID = 1L;

    /**
     * 构造函数
     */
    InfoList() {
        super();
    }

    /**
     * 构造函数
     *
     * @param sizeHint
     *            大小提示
     */
    InfoList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 构造函数
     *
     * @param infoCollection
     *            初始元素集合
     */
    InfoList(final Collection<T> infoCollection) {
        super(infoCollection);
    }

    // 令 Scrutinizer 满意
    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    // 令 Scrutinizer 满意
    @Override
    public int hashCode() {
        return super.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此列表中所有项的名称，通过对列表中的每一项调用 {@code getName()} 来获取
     *
     * @return 此列表中所有项的名称，通过对列表中的每一项调用 {@code getName()} 来获取
     */
    public List<String> getNames() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> names = new ArrayList<>(this.size());
            for (final T i : this) {
                if (i != null) {
                    names.add(i.getName());
                }
            }
            return names;
        }
    }

    /**
     * 获取此列表中所有项的字符串表示，通过对列表中的每一项调用 {@code toString()} 来获取
     *
     * @return 此列表中所有项的字符串表示，通过对列表中的每一项调用 {@code toString()} 来获取
     */
    public List<String> getAsStrings() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> toStringVals = new ArrayList<>(this.size());
            for (final T i : this) {
                toStringVals.add(i == null ? "null" : i.toString());
            }
            return toStringVals;
        }
    }

    /**
     * 获取此列表中所有项的字符串表示，仅使用命名类的<a href=
     * "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">简单
     * 名称</a>如果对象是 {@code MetadataNode} 的子类(例如 {@link ClassInfo}、{@link MethodInfo} 或
     * {@link FieldInfo} 对象)，则调用 {@code MetadataNode#toStringWithSimpleNames()}；否则对列表中的每一项
     * 调用 {@code toString()}
     *
     * @return 此列表中所有项的字符串表示，仅使用命名类的<a href=
     *         "https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/lang/Class.html#getSimpleName()">
     *         简单名称</a>
     */
    public List<String> getAsStringsWithSimpleNames() {
        if (this.isEmpty()) {
            return Collections.emptyList();
        } else {
            final List<String> toStringVals = new ArrayList<>(this.size());
            for (final T i : this) {
                toStringVals.add(i == null ? "null"
                        : i instanceof MetadataNode ? ((MetadataNode) i).toStringWithSimpleNames()
                        : i.toString());
            }
            return toStringVals;
        }
    }
}
