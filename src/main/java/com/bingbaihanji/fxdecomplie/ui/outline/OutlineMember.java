package com.bingbaihanji.fxdecomplie.ui.outline;

/**
 * 大纲成员记录。从反编译源码中提取的字段、方法或内部类。
 *
 * @param name      成员名称
 * @param type      成员类型
 * @param modifiers 修饰符（如 "public static"）
 * @param lineNumber 行号（1-based）
 * @author bingbaihanji
 * @date 2026-06-17
 */
public record OutlineMember(
        String name,
        MemberType type,
        String modifiers,
        int lineNumber
) {
    public enum MemberType {FIELD, METHOD, INNER_CLASS}
}
