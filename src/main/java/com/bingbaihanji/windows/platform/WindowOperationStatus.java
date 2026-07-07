package com.bingbaihanji.windows.platform;

/**
 * 平台窗口操作的结果分类
 *
 * <p>所有原生操作均返回 {@link WindowOperationResult},其中状态由此枚举表示
 * 调用方根据状态决定后续处理逻辑：成功则继续、跳过则忽略、失败则记录或回退</p>
 */
public enum WindowOperationStatus {
    /** 操作成功完成 */
    SUCCESS,
    /** 操作被跳过（平台不支持、参数无效、句柄不可用等） */
    SKIPPED,
    /** 操作执行失败（原生 API 返回错误） */
    FAILED
}
