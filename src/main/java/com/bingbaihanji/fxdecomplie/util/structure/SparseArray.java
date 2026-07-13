package com.bingbaihanji.fxdecomplie.util.structure;


import com.bingbaihanji.fxdecomplie.util.collection.ContainerHelpers;
import com.bingbaihanji.fxdecomplie.util.collection.EmptyArray;

import java.util.Objects;

/**
 * int → Object 稀疏映射(SparseArray)
 *
 * <p>
 * {@code SparseArray} 把 int 键映射到对象值与「Object 数组」不同,它的键之间可以有空洞相比
 * {@link java.util.HashMap},它更省内存：既避免了 int 键的自动装箱,底层结构也不需要为每个映射额外创建 Entry 对象
 *
 * <p>
 * 内部用两个平行数组保存数据,并按键升序排列,查找走二分因此不适合超大规模数据：查找是二分 增删要移动数组元素
 * 对于数百量级的容器,性能差距通常小于 50%
 *
 * <p>
 * <b>删除优化：</b>删除时不会立即压缩数组,而是把对应槽位标记为「已删除」占位这些占位可以在后续「相同键的再次写入」时复用,
 * 或在需要扩容 以及读取 {@link #size()} / {@code keyAt} / {@code valueAt} 等时统一做一次垃圾回收(gc)压缩
 *
 * <p>
 * 可通过 {@link #keyAt(int)} 与 {@link #valueAt(int)} 遍历元素以升序下标遍历,键按升序返回,值也按对应键的升序返回
 *
 * <p>
 * 本类参考 Android {@code android.util.SparseArray} 的设计思想,实现完全基于 Java SE,不依赖 Android
 * 本类不是线程安全的
 *
 * @param <E> 值类型
 * @author bingbaihanji
 * @date 2026-07-10 14:17:42
 * @description int → Object 稀疏数组(SparseArray)将整数映射到对象
 */
public class SparseArray<E> implements Cloneable {
    /**
     * 已删除槽位的占位标记(惰性删除,等待 gc 压缩)
     */
    private static final Object DELETED = new Object();

    /**
     * 是否存在待压缩的已删除槽位
     */
    private boolean mGarbage = false;

    private int[] mKeys;
    private Object[] mValues;
    private int mSize;

    /**
     * 创建一个空的 SparseArray
     */
    public SparseArray() {
        this(0);
    }

    /**
     * 创建一个空的 SparseArray,并预留可容纳指定数量映射的容量
     *
     * <p>
     * 初始容量为 0 时使用轻量表示,不额外分配数组
     *
     * @param initialCapacity 初始容量
     */
    public SparseArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = EmptyArray.INT;
            mValues = EmptyArray.OBJECT;
        } else {
            mValues = new Object[initialCapacity];
            mKeys = new int[mValues.length];
        }
        mSize = 0;
    }

    @Override
    @SuppressWarnings("unchecked")
    public SparseArray<E> clone() {
        SparseArray<E> clone = null;
        try {
            clone = (SparseArray<E>) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* 不会发生：本类实现了 Cloneable */
        }
        return clone;
    }

    /**
     * 判断键是否存在等价于 {@link #indexOfKey(int)} >= 0
     *
     * @param key 待判断的键
     * @return 键存在时返回 true
     */
    public boolean contains(int key) {
        return indexOfKey(key) >= 0;
    }

    /**
     * 获取键对应的值,不存在时返回 {@code null}
     *
     * @param key 键
     * @return 键对应的值或 null
     */
    public E get(int key) {
        return get(key, null);
    }

    /**
     * 获取键对应的值,不存在时返回指定的默认值
     *
     * @param key 键
     * @param valueIfKeyNotFound 键不存在时返回的默认值
     * @return 键对应的值或默认值
     */
    @SuppressWarnings("unchecked")
    public E get(int key, E valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0 || mValues[i] == DELETED) {
            return valueIfKeyNotFound;
        } else {
            return (E) mValues[i];
        }
    }

    /**
     * 删除指定键的映射(若存在)
     *
     * @param key 键
     */
    public void delete(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                mValues[i] = DELETED;
                mGarbage = true;
            }
        }
    }

    /**
     * 删除指定键的映射(若存在),并返回其旧值
     *
     * @param key 键
     * @return 被删除的旧值,不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public E removeReturnOld(int key) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            if (mValues[i] != DELETED) {
                final E old = (E) mValues[i];
                mValues[i] = DELETED;
                mGarbage = true;
                return old;
            }
        }
        return null;
    }

    /**
     * {@link #delete(int)} 的别名
     *
     * @param key 键
     */
    public void remove(int key) {
        delete(key);
    }

    /**
     * 删除指定下标处的映射
     *
     * <p>
     * 下标越界({@code index >= size()})时抛出 {@link ArrayIndexOutOfBoundsException}
     *
     * @param index 映射下标
     */
    public void removeAt(int index) {
        if (index < 0 || index >= mSize) {
            // 内部数组可能略大于 mSize,仅当越过有效范围时才主动抛出
            throw new ArrayIndexOutOfBoundsException(index);
        }
        if (mValues[index] != DELETED) {
            mValues[index] = DELETED;
            mGarbage = true;
        }
    }

    /**
     * 批量删除一段连续下标的映射
     *
     * @param index 起始下标
     * @param size 删除数量
     */
    public void removeAtRange(int index, int size) {
        final int end = Math.min(mSize, index + size);
        for (int i = index; i < end; i++) {
            removeAt(i);
        }
    }

    /**
     * 压缩内部数组,清除所有「已删除」占位
     */
    private void gc() {
        int n = mSize;
        int o = 0;
        int[] keys = mKeys;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            Object val = values[i];

            if (val != DELETED) {
                if (i != o) {
                    keys[o] = keys[i];
                    values[o] = val;
                    values[i] = null;
                }

                o++;
            }
        }

        mGarbage = false;
        mSize = o;
    }

    /**
     * {@link #put(int, Object)} 的别名,便于以「下标赋值」风格调用
     *
     * @param key 键
     * @param value 值
     * @see #put(int, Object)
     */
    public void set(int key, E value) {
        put(key, value);
    }

    /**
     * 写入一个键值映射 若键已存在则替换其值
     *
     * @param key 键
     * @param value 值
     */
    public void put(int key, E value) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

            if (i < mSize && mValues[i] == DELETED) {
                mKeys[i] = key;
                mValues[i] = value;
                return;
            }

            if (mGarbage && mSize >= mKeys.length) {
                gc();

                // 压缩后下标可能变化,需要重新二分定位
                i = ~ContainerHelpers.binarySearch(mKeys, mSize, key);
            }

            mKeys = GrowingArrayUtils.insert(mKeys, mSize, i, key);
            mValues = GrowingArrayUtils.insert(mValues, mSize, i, value);
            mSize++;
        }
    }

    /**
     * 返回当前映射数量
     *
     * @return 映射数量
     */
    public int size() {
        if (mGarbage) {
            gc();
        }

        return mSize;
    }

    /**
     * 返回下标 {@code index}(范围 {@code 0...size()-1})处映射的键
     *
     * <p>
     * 以升序下标访问时,键按升序返回：{@code keyAt(0)} 是最小键,{@code keyAt(size()-1)} 是最大键
     * 下标越界({@code index >= size()})时抛出 {@link ArrayIndexOutOfBoundsException}
     *
     * @param index 映射下标
     * @return 该下标处的键
     */
    public int keyAt(int index) {
        if (mGarbage) {
            gc();
        }
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        return mKeys[index];
    }

    /**
     * 返回下标 {@code index}(范围 {@code 0...size()-1})处映射的值
     *
     * <p>
     * 以升序下标访问时,值按其对应键的升序返回下标越界({@code index >= size()})时抛出
     * {@link ArrayIndexOutOfBoundsException}
     *
     * @param index 映射下标
     * @return 该下标处的值
     */
    @SuppressWarnings("unchecked")
    public E valueAt(int index) {
        if (mGarbage) {
            gc();
        }
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        return (E) mValues[index];
    }

    /**
     * 直接修改下标 {@code index}(范围 {@code 0...size()-1})处映射的值
     *
     * <p>
     * 下标越界({@code index >= size()})时抛出 {@link ArrayIndexOutOfBoundsException}
     *
     * @param index 映射下标
     * @param value 新值
     */
    public void setValueAt(int index, E value) {
        if (mGarbage) {
            gc();
        }
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }

        mValues[index] = value;
    }

    /**
     * 返回键对应的下标 键不存在时返回负数
     *
     * @param key 键
     * @return {@link #keyAt(int)} 可返回该键的下标,不存在时返回负数
     */
    public int indexOfKey(int key) {
        if (mGarbage) {
            gc();
        }

        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    /**
     * 返回某个值对应的下标 无键映射到该值时返回负数
     *
     * <p>
     * 与按键查找不同,这是线性搜索 多个键可能映射到同一值,此处只返回其中一个
     * 另外注意：本方法用 {@code ==} 比较值,而非 {@code equals}
     *
     * @param value 值
     * @return 值对应的下标,不存在时返回 -1
     */
    public int indexOfValue(E value) {
        if (mGarbage) {
            gc();
        }

        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 返回某个值对应的下标 无键映射到该值时返回负数
     *
     * <p>
     * 与 {@link #indexOfValue(Object)} 的区别是：本方法用 {@code equals} 比较值
     *
     * @param value 值
     * @return 值对应的下标,不存在时返回 -1
     */
    public int indexOfValueByValue(E value) {
        if (mGarbage) {
            gc();
        }

        for (int i = 0; i < mSize; i++) {
            if (value == null) {
                if (mValues[i] == null) {
                    return i;
                }
            } else {
                if (value.equals(mValues[i])) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * 清空所有映射
     */
    public void clear() {
        int n = mSize;
        Object[] values = mValues;

        for (int i = 0; i < n; i++) {
            values[i] = null;
        }

        mSize = 0;
        mGarbage = false;
    }

    /**
     * 追加一个键值映射,针对「键大于所有已有键」的场景做了优化
     *
     * <p>
     * 若键不大于当前最大键,则退化为 {@link #put(int, Object)}
     *
     * @param key 键
     * @param value 值
     */
    public void append(int key, E value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        if (mGarbage && mSize >= mKeys.length) {
            gc();
        }

        mKeys = GrowingArrayUtils.append(mKeys, mSize, key);
        mValues = GrowingArrayUtils.append(mValues, mSize, value);
        mSize++;
    }

    /**
     * 返回形如 {@code {key1=value1, key2=value2}} 的字符串
     *
     * <p>
     * 若某个值就是本对象自身,对应位置显示 {@code (this Map)}
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        if (size() <= 0) {
            return "{}";
        }

        StringBuilder buffer = new StringBuilder(mSize * 28);
        buffer.append('{');
        for (int i = 0; i < mSize; i++) {
            if (i > 0) {
                buffer.append(", ");
            }
            int key = keyAt(i);
            buffer.append(key);
            buffer.append('=');
            Object value = valueAt(i);
            if (value != this) {
                buffer.append(value);
            } else {
                buffer.append("(this Map)");
            }
        }
        buffer.append('}');
        return buffer.toString();
    }

    /**
     * 与另一个 {@link SparseArray} 按内容比较
     *
     * <p>
     * 出于向后兼容原因不实现 {@link Object#equals(Object)},本方法作为手动调用的替代
     *
     * @param other 待比较的 SparseArray
     * @return 内容相同时返回 true
     */
    public boolean contentEquals(SparseArray<?> other) {
        if (other == null) {
            return false;
        }

        int size = size();
        if (size != other.size()) {
            return false;
        }

        // 上面的 size() 调用已完成 gc 压缩
        for (int index = 0; index < size; index++) {
            if (mKeys[index] != other.mKeys[index]
                    || !Objects.equals(mValues[index], other.mValues[index])) {
                return false;
            }
        }

        return true;
    }

    /**
     * 返回本 {@link SparseArray} 内容的哈希值,综合所有键与值的哈希
     *
     * <p>
     * 出于向后兼容原因不实现 {@link Object#hashCode()},本方法作为手动调用的替代
     *
     * @return 内容哈希值
     */
    public int contentHashCode() {
        int hash = 0;
        int size = size();
        // 上面的 size() 调用已完成 gc 压缩
        for (int index = 0; index < size; index++) {
            int key = mKeys[index];
            hash = 31 * hash + key;
            hash = 31 * hash + Objects.hashCode(mValues[index]);
        }
        return hash;
    }
}
