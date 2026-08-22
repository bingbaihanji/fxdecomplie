package com.bingbaihanji.fxdecomplie.decompiler;

/**
 * 反编译失败原因分类 — 替代字符串匹配判断.
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public enum FailureKind {
    /** 任务被取消 */
    CANCELLED,
    /** 反编译超时 */
    TIMEOUT,
    /** 队列满或资源不足 */
    BUSY,
    /** 类文件未找到 */
    CLASS_NOT_FOUND,
    /** 引擎内部错误 */
    ENGINE_ERROR,
    /** 不支持的 class 版本 */
    UNSUPPORTED_CLASS_VERSION,
    /** IO 错误 */
    IO_ERROR
}
