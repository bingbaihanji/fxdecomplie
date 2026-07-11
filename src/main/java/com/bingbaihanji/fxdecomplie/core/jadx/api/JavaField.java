package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * 对字段节点（{@link FieldNode}）的对外封装，向 API 使用者提供字段的名称、
 * 类型、访问标志和引用关系等信息。
 */
public final class JavaField implements JavaNode {

    /** 被封装的底层字段节点 */
    private final FieldNode field;
    /** 声明该字段的所属类 */
    private final JavaClass parent;

    JavaField(JavaClass cls, FieldNode f) {
        this.field = f;
        this.parent = cls;
    }

    /** 获取字段名（使用别名，可能经过反混淆重命名） */
    @Override
    public String getName() {
        return field.getAlias();
    }

    /** 获取字段的完整名称（所属类全名 + 字段名） */
    @Override
    public String getFullName() {
        return parent.getFullName() + '.' + getName();
    }

    /** 获取字段的原始名称（未经别名替换） */
    public String getRawName() {
        return field.getName();
    }

    /** 获取声明该字段的类 */
    @Override
    public JavaClass getDeclaringClass() {
        return parent;
    }

    /** 获取最顶层的外部类（内部类会向上追溯到顶层类） */
    @Override
    public JavaClass getTopParentClass() {
        return parent.getTopParentClass();
    }

    /** 获取字段的访问标志（public/private/static 等） */
    public AccessInfo getAccessFlags() {
        return field.getAccessFlags();
    }

    /** 获取字段的类型，并尝试解析类别名 */
    public ArgType getType() {
        return ArgType.tryToResolveClassAlias(field.root(), field.getType());
    }

    /** 获取该字段定义在源码中的位置（偏移量） */
    @Override
    public int getDefPos() {
        return field.getDefPosition();
    }

    /** 获取引用了该字段的节点列表（即哪些地方使用了本字段） */
    @Override
    public List<JavaNode> getUseIn() {
        return getDeclaringClass().getRootDecompiler().convertNodes(field.getUseIn());
    }

    /** 移除字段的别名，恢复为原始名称 */
    @Override
    public void removeAlias() {
        this.field.getFieldInfo().removeAlias();
    }

    /** 判断给定的代码注解是否指向该字段自身 */
    @Override
    public boolean isOwnCodeAnnotation(ICodeAnnotation ann) {
        if (ann.getAnnType() == ICodeAnnotation.AnnType.FIELD) {
            return ann.equals(field);
        }
        return false;
    }

    /** 获取该字段对应的代码节点引用 */
    @Override
    public ICodeNodeRef getCodeNodeRef() {
        return field;
    }

    /**
     * 内部 API，不稳定！
     */
    @ApiStatus.Internal
    public FieldNode getFieldNode() {
        return field;
    }

    @Override
    public int hashCode() {
        return field.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof JavaField && field.equals(((JavaField) o).field);
    }

    @Override
    public String toString() {
        return field.toString();
    }
}
