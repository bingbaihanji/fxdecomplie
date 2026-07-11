package com.bingbaihanji.fxdecomplie.core.jadx.api.data;

/**
 * 可重命名节点接口
 * <p>
 * 由支持重命名操作的节点 (如类、方法、字段等)实现，用于对节点进行重命名
 */
public interface IRenameNode {

    /**
     * 将节点重命名为指定的新名称
     *
     * @param newName 新名称
     */
    void rename(String newName);
}
