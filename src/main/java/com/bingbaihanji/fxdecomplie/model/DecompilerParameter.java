package com.bingbaihanji.fxdecomplie.model;

/**
 * 反编译引擎单个可配置参数的定义。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public record DecompilerParameter(
        String key,
        ParamType type,
        String defaultValue,
        String i18nKey,
        String helpKey,
        Category category,
        String[] enumValues) {

    public enum ParamType { BOOLEAN, INTEGER, STRING, ENUM }
    public enum Category { COMMON, ADVANCED }

    /** 返回 fallback 英文标签：将 camelCase key 转为可读文本 */
    public String fallbackLabel() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append(' ');
            }
            if (i == 0) {
                c = Character.toUpperCase(c);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
