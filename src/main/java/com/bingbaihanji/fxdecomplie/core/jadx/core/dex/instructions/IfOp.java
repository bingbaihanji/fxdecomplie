package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

/**
 * Dex 字节码中 if 系列指令的比较操作符枚举。
 * <p>
 * 每种操作符对应一种比较语义和一个符号字符串表示，
 * 并支持通过 {@link #invert()} 获取逻辑反转后的操作符。
 * </p>
 */
public enum IfOp {
    /** 等于 (==) */
    EQ("=="),
    /** 不等于 (!=) */
    NE("!="),
    /** 小于 (<) */
    LT("<"),
    /** 小于等于 (<=) */
    LE("<="),
    /** 大于 (>) */
    GT(">"),
    /** 大于等于 (>=) */
    GE(">=");

    /** 操作符的符号字符串表示 */
    private final String symbol;

    IfOp(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 获取操作符的符号字符串。
     *
     * @return 符号字符串，如 "=="、"<" 等
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * 获取当前操作符的逻辑反转操作符。
     * <p>
     * 例如 EQ 反转得到 NE，LT 反转得到 GE，依此类推。
     * </p>
     *
     * @return 反转后的 IfOp
     * @throws JadxRuntimeException 当操作符类型未知时抛出
     */
    public IfOp invert() {
        switch (this) {
            case EQ:
                return NE;
            case NE:
                return EQ;

            case LT:
                return GE;
            case LE:
                return GT;

            case GT:
                return LE;
            case GE:
                return LT;

            default:
                throw new JadxRuntimeException("Unknown if operations type: " + this);
        }
    }
}
