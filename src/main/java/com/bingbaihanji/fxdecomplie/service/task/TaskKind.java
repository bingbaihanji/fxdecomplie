package com.bingbaihanji.fxdecomplie.service.task;

/**
 * 任务类型，用于路由到合适的线程池。
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public enum TaskKind {
    /** 搜索、打开类、Ctrl+Click 等 UI 触发的交互操作 */
    INTERACTIVE,
    /** 磁盘缓存写入、索引构建等磁盘/CPU 密集型批处理 */
    IO,
    /** 导出任务专用 */
    EXPORT,
    /** 反编译任务（独立池，避免阻塞短任务） */
    DECOMPILE
}
