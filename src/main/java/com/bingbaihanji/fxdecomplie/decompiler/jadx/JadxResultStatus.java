package com.bingbaihanji.fxdecomplie.decompiler.jadx;

/**
 * jadx 反编译结果状态枚举
 *
 * @author bingbaihanji
 */
public enum JadxResultStatus {
    /** 反编译成功 */
    OK,
    /** jadx 未加载到任何类 */
    NO_CLASSES_LOADED,
    /** 反编译完成但目标类源码为空 */
    EMPTY_OUTPUT,
    /** 反编译过程中抛出异常 */
    EXCEPTION
}
