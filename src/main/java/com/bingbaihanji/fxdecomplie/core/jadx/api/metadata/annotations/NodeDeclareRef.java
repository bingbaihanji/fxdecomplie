package com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;

import java.util.Objects;

/**
 * 节点声明引用注解，用于标记代码中某个节点 (类 方法 字段或变量等)的声明位置
 * 实现 {@link ICodeAnnotation} 接口，注解类型为 {@link AnnType#DECLARATION}
 */
public class NodeDeclareRef implements ICodeAnnotation {

    private final ICodeNodeRef node;

    private int defPos;

    /**
     * 构造节点声明引用注解
     *
     * @param node 被声明的节点引用，不可为 null
     */
    public NodeDeclareRef(ICodeNodeRef node) {
        this.node = Objects.requireNonNull(node);
    }

    /**
     * 获取被声明的节点引用
     *
     * @return 节点引用
     */
    public ICodeNodeRef getNode() {
        return node;
    }

    /**
     * 获取声明位置
     *
     * @return 声明在代码中的位置
     */
    public int getDefPos() {
        return defPos;
    }

    /**
     * 设置声明位置
     *
     * @param defPos 声明在代码中的位置
     */
    public void setDefPos(int defPos) {
        this.defPos = defPos;
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.DECLARATION;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NodeDeclareRef)) {
            return false;
        }
        return node.equals(((NodeDeclareRef) o).node);
    }

    @Override
    public int hashCode() {
        return node.hashCode();
    }

    @Override
    public String toString() {
        return "NodeDeclareRef{" + node + '}';
    }
}
