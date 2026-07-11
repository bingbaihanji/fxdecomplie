package com.bingbaihanji.fxdecomplie.model;

/**
 * 反编译引擎单个可配置参数的定义
 * <p>每个参数包含键名、类型、默认值、国际化 key 和分类信息,
 * 枚举型参数还可指定可选值列表</p>
 *
 * @param key          参数键名,对应反编译引擎的配置属性名(camelCase 格式)
 * @param type         参数类型(布尔、整数、字符串、枚举)
 * @param defaultValue 默认值字符串,由 UI 层解析为对应类型
 * @param i18nKey      国际化标签 key,用于在 UI 中显示参数名称
 * @param helpKey      国际化帮助文本 key,用于在 UI 中显示参数说明
 * @param category     参数分类(常用 / 高级)
 * @param enumValues   枚举型参数的可选值列表；非枚举型为 null
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

    /**
     * 返回 fallback 英文标签
     * <p>当国际化资源缺失时,将 camelCase 格式的 key 转换为可读文本：
     * 首字母大写,每个大写字母前插入空格</p>
     *
     * @return 可读的参数标签文本
     */
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

    /** 参数类型：布尔开关、整数、字符串、枚举 */
    public enum ParamType {BOOLEAN, INTEGER, STRING, ENUM}

    /** 参数分类：常用参数、高级参数 */
    public enum Category {COMMON, ADVANCED}
}
