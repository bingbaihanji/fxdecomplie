package com.bingbaihanji.classgraph.util;

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
