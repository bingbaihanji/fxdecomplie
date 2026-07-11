package com.bingbaihanji.fxdecomplie.util.collection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * List 工具类,提供判空、元素拼接、类型转换、去重、合并等常用操作
 *
 * @author bingbaihanji
 * @date 2026-06-12
 */
public final class ListUtils {

    private ListUtils() {
        throw new AssertionError("工具类不允许实例化");
    }

    // 判空

    /**
     * 判断 List 是否为 null 或无元素
     */
    public static boolean isNullOrEmpty(List<?> list) {
        return list == null || list.isEmpty();
    }

    /**
     * 判断 List 是否非 null 且有元素
     */
    public static boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

    /**
     * 如果 list 为 null 或空,则返回 defaultList,否则返回原 list
     */
    public static <T> List<T> defaultIfEmpty(List<T> list, List<T> defaultList) {
        return isNotEmpty(list) ? list : defaultList;
    }

    /**
     * 返回不可变的空 List
     */
    public static <T> List<T> emptyList() {
        return Collections.emptyList();
    }

    // 创建

    /**
     * 快速创建包含指定元素的 ArrayList
     */
    @SafeVarargs
    public static <T> List<T> newArrayList(T... items) {
        List<T> list = new ArrayList<>(items.length);
        Collections.addAll(list, items);
        return list;
    }

    /**
     * 将 Iterable 转换为 ArrayList
     */
    public static <T> List<T> toList(Iterable<T> iterable) {
        if (iterable == null) {
            return new ArrayList<>();
        }
        return StreamSupport.stream(iterable.spliterator(), false)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // 类型转换

    /**
     * 将字符串数组转换为整数列表,自动跳过无法解析的元素
     */
    public static List<Integer> toIntList(String[] arr) {
        if (arr == null) {
            return new ArrayList<>();
        }
        List<Integer> result = new ArrayList<>(arr.length);
        for (String str : arr) {
            if (str != null) {
                try {
                    result.add(Integer.parseInt(str.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略无法解析的元素
                }
            }
        }
        return result;
    }

    /**
     * 将字符串数组转换为长整数列表,自动跳过无法解析的元素
     */
    public static List<Long> toLongList(String[] arr) {
        if (arr == null) {
            return new ArrayList<>();
        }
        List<Long> result = new ArrayList<>(arr.length);
        for (String str : arr) {
            if (str != null) {
                try {
                    result.add(Long.parseLong(str.trim()));
                } catch (NumberFormatException ignored) {
                    // 忽略无法解析的元素
                }
            }
        }
        return result;
    }

    @Deprecated
    public static Integer[] stringArr2intArr(String[] arr) {
        if (arr == null) {
            return new Integer[0];
        }
        Integer[] result = new Integer[arr.length];
        for (int i = 0; i < arr.length; i++) {
            String str = arr[i];
            if (str != null) {
                try {
                    result[i] = Integer.parseInt(str.trim());
                } catch (NumberFormatException e) {
                    // 保持为 null
                }
            }
        }
        return result;
    }

    // 元素操作

    /**
     * 返回去重后的新 List(借助 LinkedHashSet 保持插入顺序)
     */
    public static <T> List<T> distinct(List<T> list) {
        if (isNullOrEmpty(list)) {
            return new ArrayList<>();
        }
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    /**
     * 返回 List 的第一个元素,如果为空则返回 null
     */
    public static <T> T first(List<T> list) {
        if (isNullOrEmpty(list)) {
            return null;
        }
        return list.getFirst();
    }

    /**
     * 返回 List 的最后一个元素,如果为空则返回 null
     */
    public static <T> T last(List<T> list) {
        if (isNullOrEmpty(list)) {
            return null;
        }
        return list.getLast();
    }

    // 字符串拼接

    /**
     * 将多个对象拼接为逗号分隔的字符串,null 元素会输出 "null"
     */
    public static String toString(Object... objs) {
        return SetUtils.toString(objs);
    }

    /**
     * 将可迭代对象中的所有元素连接为逗号分隔的字符串,null 元素视为空字符串
     */
    public static String join(Iterable<?> iterable) {
        return SetUtils.join(iterable);
    }

    // 合并

    /**
     * 合并多个 List 到一个新的 ArrayList 中(不修改原 List)
     */
    @SafeVarargs
    public static <T> List<T> merge(List<T> first, List<T>... others) {
        List<T> result = new ArrayList<>();
        if (first != null) {
            result.addAll(first);
        }
        if (others != null) {
            for (List<T> other : others) {
                if (other != null) {
                    result.addAll(other);
                }
            }
        }
        return result;
    }

    /**
     * 使用 Supplier 创建新的 List 容器,再将多个 List 合并进去
     */
    @SafeVarargs
    public static <T> List<T> merge(Supplier<List<T>> supplier, List<T> first, List<T>... others) {
        List<T> result = supplier.get();
        if (first != null) {
            result.addAll(first);
        }
        if (others != null) {
            for (List<T> other : others) {
                if (other != null) {
                    result.addAll(other);
                }
            }
        }
        return result;
    }

}
