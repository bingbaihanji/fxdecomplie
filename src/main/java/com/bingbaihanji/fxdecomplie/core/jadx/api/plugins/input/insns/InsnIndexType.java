package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns;

/**
 * 指令索引类型枚举
 * <p>
 * 表示指令中内嵌索引所引用的常量池条目的类别，
 * 用于确定该索引应解析为哪种引用
 */
public enum InsnIndexType {
    /** 无索引引用 */
    NONE,
    /** 类型引用 */
    TYPE_REF,
    /** 字符串引用 */
    STRING_REF,
    /** 字段引用 */
    FIELD_REF,
    /** 方法引用 */
    METHOD_REF,
    /** 调用点（动态调用的 call site） */
    CALL_SITE
}
