package com.bingbaihanji.fxdecomplie.ui.inheritance;

/**
 * 类继承层次节点记录。
 *
 * @param className   类的全内部路径
 * @param displayName 显示用的简短类名
 * @param relation    相对于查询类的关系
 * @param depth       距离查询类的步数
 * @author bingbaihanji
 * @date 2026-06-17
 */
public record InheritanceNode(
        String className,
        String displayName,
        RelationType relation,
        int depth
) {
    public enum RelationType {SELF, SUPER_CLASS, SUBCLASS, INTERFACE}
}
