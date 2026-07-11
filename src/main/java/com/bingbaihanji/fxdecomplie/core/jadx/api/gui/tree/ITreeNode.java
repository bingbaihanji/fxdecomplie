package com.bingbaihanji.fxdecomplie.core.jadx.api.gui.tree;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeNode;

/**
 * 树节点接口，扩展自 {@link TreeNode}，用于表示 GUI 树视图中的一个节点
 * <p>
 * 每个树节点具有唯一标识符、显示名称、图标以及与代码节点的关联引用
 * 该接口是 GUI 树组件与底层代码元数据之间的桥梁
 */
public interface ITreeNode extends TreeNode {

    /**
     * 获取节点的唯一标识符
     * <p>
     * 该标识符与语言环境无关，用于在树中唯一标识一个节点
     *
     * @return 节点的唯一标识符
     */
    String getID();

    /**
     * 获取节点的显示标题
     * <p>
     * 该标题在 GUI 树视图中展示，表示节点的可读名称
     *
     * @return 节点的名称
     */
    String getName();

    /**
     * 获取节点图标
     * <p>
     * 该图标在 GUI 树视图中展示，用于视觉上区分不同类型的节点
     *
     * @return 节点的图标对象
     */
    Icon getIcon();

    /**
     * 获取与此树节点关联的代码节点引用
     * <p>
     * 通过该引用可以定位到反编译结果中对应的代码元素 (如类、方法、字段等)
     * 如果该节点没有关联的代码元素，则返回 {@code null}
     *
     * @return 关联的代码节点引用，可能为 {@code null}
     */
    @Nullable
    ICodeNodeRef getCodeNodeRef();
}
