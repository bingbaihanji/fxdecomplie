package com.bingbaihanji.fxdecomplie.model;

/**
 * 带父节点链接的轻量级路径节点接口
 *
 * @param <T> 包装的值类型
 * @author bingbaihanji
 * @date 2026-06-18
 */
public interface PathNode<T> {

    /** @return 父节点,根节点返回 null */
    PathNode<?> getParent();

    /** @return 当前节点包装的值 */
    T getValue();

    /** @return 当前节点值的具体类型 */
    Class<T> getValueType();

    /**
     * 沿父节点链向上查找第一个匹配指定类型的值
     *
     * @param <V>  目标类型
     * @param type 要查找的类型
     * @return 匹配的值,若未找到则返回 null
     */
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
