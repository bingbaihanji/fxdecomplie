package com.bingbaihanji.fxdecomplie.service.decompile;

/** 缓存失效范围 */
public enum CodeCacheScope {
    /** 失效单个类在所有引擎下的缓存 */
    CLASS,
    /** 失效整个工作区的所有缓存 */
    WORKSPACE,
    /** 清空全部缓存 */
    ALL
}
