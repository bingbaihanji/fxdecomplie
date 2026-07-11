package com.bingbaihanji.fxdecomplie.util.structure;

import java.lang.reflect.Array;

/**
 * 稀疏数组(Sparse*)使用的动态数组增长工具
 *
 * <p>
 * 本类为 {@link SparseArray}、{@link SparseIntArray}、{@link SparseLongArray}、{@link SparseBooleanArray}
 * 提供「按需扩容 + 追加 / 插入」的底层数组操作参考 Android {@code com.android.internal.util.GrowingArrayUtils} 的思路,
 * 但完全基于 Java SE 实现,不依赖 Android 平台(原版通过 {@code VMRuntime.newUnpaddedArray} 分配无填充数组,
 * 这里直接使用普通 {@code new} 分配)
 *
 * <p>
 * 约定：{@code array} 的前 {@code currentSize} 个槽位为有效数据,其余为预留容量当有效数据填满整个数组时才触发扩容,
 * 扩容倍率见 {@link #growSize(int)}
 *
 * <p>
 * 本类仅供 structure 包内部使用
 *
 * @author bingbaihanji
 * @since 1.0
 */
final class GrowingArrayUtils {

    /**
     * 禁止实例化
     */
    private GrowingArrayUtils() {
        throw new AssertionError("No instances.");
    }

    /**
     * 计算扩容后的目标容量
     *
     * <p>
     * 增长策略与 Android 原版一致：容量不超过 4 时扩到 8,否则翻倍
     *
     * @param currentSize 当前有效元素数量
     * @return 扩容后的新容量
     */
    static int growSize(int currentSize) {
        return currentSize <= 4 ? 8 : currentSize * 2;
    }

    /**
     * 在数组末尾追加一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param element 待追加元素
     * @param <T> 元素类型
     * @return 追加后的数组(可能是扩容后的新数组)
     */
    @SuppressWarnings("unchecked")
    static <T> T[] append(T[] array, int currentSize, T element) {
        assert currentSize <= array.length;
        if (currentSize + 1 > array.length) {
            T[] newArray = (T[]) Array.newInstance(array.getClass().getComponentType(), growSize(currentSize));
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    /**
     * 在 int 数组末尾追加一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param element 待追加元素
     * @return 追加后的数组(可能是扩容后的新数组)
     */
    static int[] append(int[] array, int currentSize, int element) {
        assert currentSize <= array.length;
        if (currentSize + 1 > array.length) {
            int[] newArray = new int[growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    /**
     * 在 long 数组末尾追加一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param element 待追加元素
     * @return 追加后的数组(可能是扩容后的新数组)
     */
    static long[] append(long[] array, int currentSize, long element) {
        assert currentSize <= array.length;
        if (currentSize + 1 > array.length) {
            long[] newArray = new long[growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    /**
     * 在 boolean 数组末尾追加一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param element 待追加元素
     * @return 追加后的数组(可能是扩容后的新数组)
     */
    static boolean[] append(boolean[] array, int currentSize, boolean element) {
        assert currentSize <= array.length;
        if (currentSize + 1 > array.length) {
            boolean[] newArray = new boolean[growSize(currentSize)];
            System.arraycopy(array, 0, newArray, 0, currentSize);
            array = newArray;
        }
        array[currentSize] = element;
        return array;
    }

    /**
     * 在指定下标处插入一个元素,容量不足时自动扩容
     *
     * <p>
     * 若数组仍有空闲容量,则原地后移 [index, currentSize) 区间并写入 否则分配扩容数组并分两段复制
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param index 插入位置
     * @param element 待插入元素
     * @param <T> 元素类型
     * @return 插入后的数组(可能是扩容后的新数组)
     */
    @SuppressWarnings("unchecked")
    static <T> T[] insert(T[] array, int currentSize, int index, T element) {
        assert currentSize <= array.length;
        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }
        T[] newArray = (T[]) Array.newInstance(array.getClass().getComponentType(), growSize(currentSize));
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    /**
     * 在 int 数组指定下标处插入一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param index 插入位置
     * @param element 待插入元素
     * @return 插入后的数组(可能是扩容后的新数组)
     */
    static int[] insert(int[] array, int currentSize, int index, int element) {
        assert currentSize <= array.length;
        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }
        int[] newArray = new int[growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    /**
     * 在 long 数组指定下标处插入一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param index 插入位置
     * @param element 待插入元素
     * @return 插入后的数组(可能是扩容后的新数组)
     */
    static long[] insert(long[] array, int currentSize, int index, long element) {
        assert currentSize <= array.length;
        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }
        long[] newArray = new long[growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

    /**
     * 在 boolean 数组指定下标处插入一个元素,容量不足时自动扩容
     *
     * @param array 原数组,前 currentSize 个槽位有效
     * @param currentSize 当前有效元素数量
     * @param index 插入位置
     * @param element 待插入元素
     * @return 插入后的数组(可能是扩容后的新数组)
     */
    static boolean[] insert(boolean[] array, int currentSize, int index, boolean element) {
        assert currentSize <= array.length;
        if (currentSize + 1 <= array.length) {
            System.arraycopy(array, index, array, index + 1, currentSize - index);
            array[index] = element;
            return array;
        }
        boolean[] newArray = new boolean[growSize(currentSize)];
        System.arraycopy(array, 0, newArray, 0, index);
        newArray[index] = element;
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        return newArray;
    }

}
