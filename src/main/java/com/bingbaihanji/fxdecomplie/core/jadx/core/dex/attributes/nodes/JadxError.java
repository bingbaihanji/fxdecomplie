package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * 表示 jadx 反编译过程中产生的错误信息
 * <p>
 * 此类封装了错误描述字符串和可选的异常原因，实现 {@link Comparable} 接口，
 * 以便按错误描述进行排序和去重
 * </p>
 */
public class JadxError implements Comparable<JadxError> {

    /** 错误描述信息 */
    private final String error;
    /** 导致此错误的异常 (可为空) */
    private final Throwable cause;

    /**
     * 构造一个 JadxError 实例
     *
     * @param error 错误描述，不能为空
     * @param cause 导致错误的异常，可为空
     */
    public JadxError(String error, Throwable cause) {
        this.error = Objects.requireNonNull(error);
        this.cause = cause;
    }

    /**
     * 获取错误描述信息
     *
     * @return 错误描述字符串
     */
    public String getError() {
        return error;
    }

    /**
     * 获取导致此错误的异常
     *
     * @return 异常对象，可能为空
     */
    public Throwable getCause() {
        return cause;
    }

    /**
     * 按错误描述字符串进行字典序比较
     *
     * @param o 要比较的另一个 JadxError
     * @return 比较结果
     */
    @Override
    public int compareTo(@NotNull JadxError o) {
        return this.error.compareTo(o.getError());
    }

    /**
     * 基于错误描述字符串判断两个 JadxError 是否相等
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JadxError other = (JadxError) o;
        return error.equals(other.error);
    }

    /**
     * 基于错误描述字符串计算哈希码
     */
    @Override
    public int hashCode() {
        return error.hashCode();
    }

    /**
     * 返回包含错误描述和异常堆栈的字符串表示
     */
    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("JadxError: ").append(error).append(' ');
        if (cause != null) {
            str.append(cause.getClass());
            str.append(':');
            str.append(cause.getMessage());
            str.append('\n');
            str.append(Utils.getStackTrace(cause));
        }
        return str.toString();
    }
}
