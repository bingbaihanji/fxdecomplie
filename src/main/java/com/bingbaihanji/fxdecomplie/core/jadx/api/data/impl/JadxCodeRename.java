package com.bingbaihanji.fxdecomplie.core.jadx.api.data.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeRename;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaCodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.IJavaNodeRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * {@link ICodeRename} 的默认实现，表示对代码中某个实体 (方法参数 局部变量等)的重命名条目
 * <p>
 * 每个重命名条目由三部分组成：
 * <ul>
 *   <li>{@code nodeRef} —— 节点引用，标识重命名所属的类/方法等声明节点</li>
 *   <li>{@code codeRef} —— 代码引用，进一步定位方法内部的具体实体 (可为 null)</li>
 *   <li>{@code newName} —— 重命名后的新名称</li>
 * </ul>
 * 该类支持 JSON 序列化，用于持久化用户的重命名数据
 * </p>
 */
public class JadxCodeRename implements ICodeRename {
    /** 节点引用，标识重命名所属的声明节点 (类/方法等) */
    private IJavaNodeRef nodeRef;
    /** 代码引用，定位方法内部的具体实体 (如参数 变量)，可为 null */
    @Nullable
    private IJavaCodeRef codeRef;
    /** 重命名后的新名称 */
    private String newName;

    /**
     * 构造一个不含代码引用的重命名条目
     *
     * @param nodeRef 节点引用
     * @param newName 新名称
     */
    public JadxCodeRename(IJavaNodeRef nodeRef, String newName) {
        this(nodeRef, null, newName);
    }

    /**
     * 构造一个完整的重命名条目
     *
     * @param nodeRef 节点引用
     * @param codeRef 代码引用，可为 null
     * @param newName 新名称
     */
    public JadxCodeRename(IJavaNodeRef nodeRef, @Nullable IJavaCodeRef codeRef, String newName) {
        this.nodeRef = nodeRef;
        this.codeRef = codeRef;
        this.newName = newName;
    }

    /**
     * 无参构造函数，供 JSON 反序列化使用
     */
    public JadxCodeRename() {
        // 用于 JSON 序列化
    }

    /**
     * 获取节点引用
     *
     * @return 节点引用
     */
    @Override
    public IJavaNodeRef getNodeRef() {
        return nodeRef;
    }

    /**
     * 设置节点引用
     *
     * @param nodeRef 节点引用
     */
    public void setNodeRef(IJavaNodeRef nodeRef) {
        this.nodeRef = nodeRef;
    }

    /**
     * 获取代码引用
     *
     * @return 代码引用，可能为 null
     */
    @Override
    public IJavaCodeRef getCodeRef() {
        return codeRef;
    }

    /**
     * 设置代码引用
     *
     * @param codeRef 代码引用
     */
    public void setCodeRef(IJavaCodeRef codeRef) {
        this.codeRef = codeRef;
    }

    /**
     * 获取重命名后的新名称
     *
     * @return 新名称
     */
    @Override
    public String getNewName() {
        return newName;
    }

    /**
     * 设置重命名后的新名称
     *
     * @param newName 新名称
     */
    public void setNewName(String newName) {
        this.newName = newName;
    }

    /**
     * 与另一个重命名条目比较大小
     * <p>
     * 依次比较节点引用 代码引用 (当两者均非空时) 新名称，用于排序
     * </p>
     *
     * @param other 待比较的另一个重命名条目
     * @return 比较结果
     */
    @Override
    public int compareTo(@NotNull ICodeRename other) {
        int cmpNodeRef = this.getNodeRef().compareTo(other.getNodeRef());
        if (cmpNodeRef != 0) {
            return cmpNodeRef;
        }
        if (this.getCodeRef() != null && other.getCodeRef() != null) {
            return this.getCodeRef().compareTo(other.getCodeRef());
        }
        return this.getNewName().compareTo(other.getNewName());
    }

    /**
     * 判断两个重命名条目是否相等 (基于节点引用和代码引用)
     *
     * @param o 待比较对象
     * @return 若节点引用和代码引用均相等则返回 true
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ICodeRename)) {
            return false;
        }
        ICodeRename other = (ICodeRename) o;
        return getNodeRef().equals(other.getNodeRef())
                && Objects.equals(getCodeRef(), other.getCodeRef());
    }

    /**
     * 计算哈希值 (基于节点引用和代码引用)
     *
     * @return 哈希值
     */
    @Override
    public int hashCode() {
        return 31 * getNodeRef().hashCode() + Objects.hashCode(getCodeRef());
    }

    @Override
    public String toString() {
        return "JadxCodeRename{" + nodeRef
                + ", codeRef=" + codeRef
                + ", newName='" + newName + '\''
                + '}';
    }
}
