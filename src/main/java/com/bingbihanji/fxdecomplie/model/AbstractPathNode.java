package com.bingbihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * Base class for path nodes with parent linkage.
 * Stores the parent reference, value, and its runtime type for type-safe retrieval.
 *
 * @param <T> wrapped value type
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
