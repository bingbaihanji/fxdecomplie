package com.bingbaihanji.fxdecomplie.model;

/**
 * Lightweight path node with parent linkage.
 *
 * @param <T> wrapped value type
 * @author bingbaihanji
 * @date 2026-06-18
 */
public interface PathNode<T> {

    PathNode<?> getParent();

    T getValue();

    Class<T> getValueType();

    default <V> V getValueOfType(Class<V> type) {
        PathNode<?> path = this;
        while (path != null) {
            if (type.isAssignableFrom(path.getValueType())) {
                return type.cast(path.getValue());
            }
            path = path.getParent();
        }
        return null;
    }

}
