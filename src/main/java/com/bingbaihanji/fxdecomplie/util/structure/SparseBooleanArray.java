package com.bingbaihanji.fxdecomplie.util.structure;


import com.bingbaihanji.fxdecomplie.util.collection.ContainerHelpers;
import com.bingbaihanji.fxdecomplie.util.collection.EmptyArray;

/**
 * int → boolean 稀疏映射(SparseBooleanArray)
 *
 * <p>
 * {@code SparseBooleanArray} 把 int 键映射到 boolean 值键之间可以有空洞相比用 {@link java.util.HashMap} 存
 * {@code Integer → Boolean},它更省内存：既避免了键和值的自动装箱,也不需要为每个映射额外创建 Entry 对象
 *
 * <p>
 * 内部用两个平行数组保存数据,并按键升序排列,查找走二分因此不适合超大规模数据：查找是二分、增删要移动数组元素
 * 对于数百量级的容器,性能差距通常小于 50%可通过 {@link #keyAt(int)} 与 {@link #valueAt(int)} 按升序下标遍历
 *
 * <p>
 * 本类参考 Android {@code android.util.SparseBooleanArray} 的设计思想,实现完全基于 Java SE,不依赖 Android
 * 本类不是线程安全的
 *
 * @author bingbaihanji
 * @date 2026-07-10 14:19:28
 * @description int → boolean  将整数映射为布尔值
 */
public class SparseBooleanArray implements Cloneable {
    private int[] mKeys;
    private boolean[] mValues;
    private int mSize;

    /**
     * 创建一个空的 SparseBooleanArray
     */
    public SparseBooleanArray() {
        this(0);
    }

    /**
     * 创建一个空的 SparseBooleanArray,并预留可容纳指定数量映射的容量
     *
     * <p>
     * 初始容量为 0 时使用轻量表示,不额外分配数组
     *
     * @param initialCapacity 初始容量
     */
    public SparseBooleanArray(int initialCapacity) {
        if (initialCapacity == 0) {
            mKeys = EmptyArray.INT;
            mValues = EmptyArray.BOOLEAN;
        } else {
            mKeys = new int[initialCapacity];
            mValues = new boolean[mKeys.length];
        }
        mSize = 0;
    }

    @Override
    public SparseBooleanArray clone() {
        SparseBooleanArray clone = null;
        try {
            clone = (SparseBooleanArray) super.clone();
            clone.mKeys = mKeys.clone();
            clone.mValues = mValues.clone();
        } catch (CloneNotSupportedException cnse) {
            /* 不会发生：本类实现了 Cloneable */
        }
        return clone;
    }

    /**
     * 获取键对应的值,不存在时返回 {@code false}
     *
     * @param key 键
     * @return 键对应的值或 false
     */
    public boolean get(int key) {
        return get(key, false);
    }

    /**
     * 获取键对应的值,不存在时返回指定的默认值
     *
     * @param key 键
     * @param valueIfKeyNotFound 键不存在时返回的默认值
     * @return 键对应的值或默认值
     */
    public boolean get(int key, boolean valueIfKeyNotFound) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i < 0) {
            return valueIfKeyNotFound;
        } else {
            return mValues[i];
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
            System.arraycopy(mKeys, i + 1, mKeys, i, mSize - (i + 1));
            System.arraycopy(mValues, i + 1, mValues, i, mSize - (i + 1));
            mSize--;
        }
    }

    /**
     * 删除指定下标处的映射
     *
     * @param index 映射下标
     */
    public void removeAt(int index) {
        if (index < 0 || index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        System.arraycopy(mKeys, index + 1, mKeys, index, mSize - (index + 1));
        System.arraycopy(mValues, index + 1, mValues, index, mSize - (index + 1));
        mSize--;
    }

    /**
     * 写入一个键值映射；若键已存在则替换其值
     *
     * @param key 键
     * @param value 值
     */
    public void put(int key, boolean value) {
        int i = ContainerHelpers.binarySearch(mKeys, mSize, key);

        if (i >= 0) {
            mValues[i] = value;
        } else {
            i = ~i;

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
    public boolean valueAt(int index) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return mValues[index];
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
    public void setValueAt(int index, boolean value) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mValues[index] = value;
    }

    /**
     * 直接修改下标 {@code index}(范围 {@code 0...size()-1})处映射的键
     *
     * <p>
     * 下标越界({@code index >= size()})时抛出 {@link ArrayIndexOutOfBoundsException}
     *
     * @param index 映射下标
     * @param key 新键
     */
    public void setKeyAt(int index, int key) {
        if (index >= mSize) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        mKeys[index] = key;
    }

    /**
     * 返回键对应的下标；键不存在时返回负数
     *
     * @param key 键
     * @return {@link #keyAt(int)} 可返回该键的下标,不存在时返回负数
     */
    public int indexOfKey(int key) {
        return ContainerHelpers.binarySearch(mKeys, mSize, key);
    }

    /**
     * 返回某个值对应的下标；无键映射到该值时返回负数
     *
     * <p>
     * 与按键查找不同,这是线性搜索；多个键可能映射到同一值,此处只返回其中一个
     *
     * @param value 值
     * @return 值对应的下标,不存在时返回 -1
     */
    public int indexOfValue(boolean value) {
        for (int i = 0; i < mSize; i++) {
            if (mValues[i] == value) {
                return i;
            }
        }

        return -1;
    }

    /**
     * 清空所有映射
     */
    public void clear() {
        mSize = 0;
    }

    /**
     * 追加一个键值映射,针对「键大于所有已有键」的场景做了优化
     *
     * <p>
     * 若键不大于当前最大键,则退化为 {@link #put(int, boolean)}
     *
     * @param key 键
     * @param value 值
     */
    public void append(int key, boolean value) {
        if (mSize != 0 && key <= mKeys[mSize - 1]) {
            put(key, value);
            return;
        }

        mKeys = GrowingArrayUtils.append(mKeys, mSize, key);
        mValues = GrowingArrayUtils.append(mValues, mSize, value);
        mSize++;
    }

    @Override
    public int hashCode() {
        int hashCode = mSize;
        for (int i = 0; i < mSize; i++) {
            hashCode = 31 * hashCode + mKeys[i] + (mValues[i] ? 1 : 0);
        }
        return hashCode;
    }

    @Override
    public boolean equals(Object that) {
        if (this == that) {
            return true;
        }

        if (!(that instanceof SparseBooleanArray other)) {
            return false;
        }

        if (mSize != other.mSize) {
            return false;
        }

        for (int i = 0; i < mSize; i++) {
            if (mKeys[i] != other.mKeys[i]) {
                return false;
            }
            if (mValues[i] != other.mValues[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 返回形如 {@code {key1=value1, key2=value2}} 的字符串
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
            boolean value = valueAt(i);
            buffer.append(value);
        }
        buffer.append('}');
        return buffer.toString();
    }
}
