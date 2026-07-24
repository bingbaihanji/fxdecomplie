package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析后的注解 DTO。
 *
 * @param className        注解类名（内部名称）
 * @param parameterValues  参数名到值的映射
 * @param visible          是否运行时可见（RetentionPolicy.RUNTIME）
 */
public record ParsedAnnotation(
    String className,
    Map<String, Object> parameterValues,
    boolean visible
) {
    public ParsedAnnotation {
        parameterValues = parameterValues == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(parameterValues));
    }

    /** 便捷构造：无参数的注解 */
    public ParsedAnnotation(String className, boolean visible) {
        this(className, Map.of(), visible);
    }

    /** 获取指定参数的值 */
    @SuppressWarnings("unchecked")
    public <T> T parameter(String name) {
        return (T) parameterValues.get(name);
    }

    /** 获取指定参数的值，不存在时返回默认值 */
    @SuppressWarnings("unchecked")
    public <T> T parameterOrDefault(String name, T defaultValue) {
        T value = (T) parameterValues.get(name);
        return value != null ? value : defaultValue;
    }
}
