package com.bingbaihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * 字节码级别的用法搜索结果
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record UsageResult(
        String sourcePath,
        int lineNumber,
        UsageType type,
        String displayText
) {

    public UsageResult {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(type, "type");
        displayText = displayText == null ? "" : displayText;
        lineNumber = Math.max(1, lineNumber);
    }

    /** 用法的类型 */
    public enum UsageType {
        /** 类引用 */
        CLASS_REFERENCE,
        /** 方法调用 */
        METHOD_CALL,
        /** 字段访问 */
        FIELD_ACCESS
    }
}
