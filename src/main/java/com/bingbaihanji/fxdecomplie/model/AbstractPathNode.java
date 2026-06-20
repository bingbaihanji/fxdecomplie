package com.bingbaihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * 带父节点链接的路径节点基类
 * 存储父节点引用、值及其运行时类型，用于类型安全的检索
 *
 * @param <T> 包装的值类型
 * @author bingbaihanji
 * @date 2026-06-18
 */
abstract class AbstractPathNode<T> implements PathNode<T> {

    private final PathNode<?> parent;
    private final T value;
    private final Class<T> valueType;

    AbstractPathNode(PathNode<?> parent, T value, Class<T> valueType) {
        this.parent = parent;
        this.value = Objects.requireNonNull(value, "value");
        this.valueType = Objects.requireNonNull(valueType, "valueType");
    }

    @Override
    public PathNode<?> getParent() {
        return parent;
    }

    @Override
    public T getValue() {
        return value;
    }

    @Override
    public Class<T> getValueType() {
        return valueType;
    }
}
