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
package com.bingbaihanji.classgraph.utils;

import java.util.*;

/**
 * 集合工具类
 */
public final class CollectionUtils {
    /** 不可构造 */
    private CollectionUtils() {
        // 空
    }

    /**
     * 如果集合非空则排序(防止当一个不可变空列表被多次返回时，
     * 在一个线程中排序而在另一个线程中遍历时抛出 {@link ConcurrentModificationException} -- #334)
     *
     * @param <T>
     *            元素类型
     * @param list
     *            列表
     */
    public static <T extends Comparable<? super T>> void sortIfNotEmpty(final List<T> list) {
        if (list.size() > 1) {
            list.sort(null);
        }
    }

    /**
     * 如果集合非空则排序(防止当一个不可变空列表被多次返回时，
     * 在一个线程中排序而在另一个线程中遍历时抛出 {@link ConcurrentModificationException} -- #334)
     *
     * @param <T>
     *            元素类型
     * @param list
     *            列表
     * @param comparator
     *            比较器
     */
    public static <T> void sortIfNotEmpty(final List<T> list, final Comparator<? super T> comparator) {
        if (list.size() > 1) {
            list.sort(comparator);
        }
    }

    /**
     * 复制并排序集合
     *
     * @param elts
     *            要复制并排序的集合
     * @return 排序后的集合副本
     */
    public static <T extends Comparable<T>> List<T> sortCopy(final Collection<T> elts) {
        final List<T> sortedCopy = new ArrayList<>(elts);
        if (sortedCopy.size() > 1) {
            sortedCopy.sort(null);
        }
        return sortedCopy;
    }
}
