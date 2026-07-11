package com.bingbaihanji.fxdecomplie.constants;

/**
 * 重命名条目类型常量。
 */
public final class RenameTypes {

    public static final String CLASS = "class";
    public static final String METHOD = "method";
    public static final String FIELD = "field";
    public static final String PARAM = "param";
    public static final String IDENTIFIER = "identifier";

    private RenameTypes() {
        throw new AssertionError("constants");
    }
}
