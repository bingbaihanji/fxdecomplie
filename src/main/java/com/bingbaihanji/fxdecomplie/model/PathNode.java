package com.bingbaihanji.fxdecomplie.model;

/**
 * 带父节点链接的轻量级路径节点接口
 *
 * @param <T> 包装的值类型
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
