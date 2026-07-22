package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.Objects;

/**
 * 继承引用树节点,表示继承层次中的一个类或方法引用
 *
 * @param className       类内部名称(如 com/example/UserService)
 * @param displayName     显示用的简短名称
 * @param kind            节点类型
 * @param fullPath        类文件完整路径(可跳转时为非空)
 * @param depth           距离查询类的步数
 * @param navigable       是否可导航(双击跳转)
 * @param descriptor      JVM 方法描述符(仅方法级节点使用,如 "(Ljava/lang/String;)V")
 * @param ownerClassName  所属类的内部名称(仅方法级节点使用,表示此方法属于哪个类)
 * @param lineNumber      方法在源码中的行号(-1 表示未知)
 * @author bingbaihanji
 * @date 2026-07-20
 */
public record InheritanceReferenceNode(
        String className,
        String displayName,
        Kind kind,
        String fullPath,
        int depth,
        boolean navigable,
        String descriptor,
        String ownerClassName,
        int lineNumber
) {
    /**
     * 向后兼容构造器 —— 类级别节点,无方法信息
     */
    public InheritanceReferenceNode(String className, String displayName, Kind kind,
                                    String fullPath, int depth, boolean navigable) {
        this(className, displayName, kind, fullPath, depth, navigable, null, null, -1);
    }

    public InheritanceReferenceNode {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
    }

    /**
     * 方法级别节点构造器
     */
    public static InheritanceReferenceNode methodNode(String methodName, String descriptor,
                                                      Kind kind, String ownerClassName,
                                                      String ownerFullPath, int depth) {
        String display = methodName
                + (descriptor != null && !descriptor.isEmpty() ? descriptor : "");
        return new InheritanceReferenceNode(methodName, display, kind,
                ownerFullPath, depth, ownerFullPath != null && !ownerFullPath.isBlank(),
                descriptor, ownerClassName, -1);
    }
}
