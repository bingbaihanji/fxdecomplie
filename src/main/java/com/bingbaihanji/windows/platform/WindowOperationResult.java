package com.bingbaihanji.windows.platform;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 原生窗口操作的结果对象
 *
 * <p>原生 API 常因平台或操作系统版本不支持特定功能而失败
 * 返回结果对象使上层 API 保持可观测性，同时不强制每个调用方捕获平台特定异常</p>
 */
public record WindowOperationResult(
        WindowOperationStatus status,
        String operation,
        String message,
        int nativeCode
) {

    public WindowOperationResult {
        status = Objects.requireNonNull(status, "status");
        operation = operation == null || operation.isBlank() ? "windowOperation" : operation;
        message = message == null ? "" : message;
    }

    /**
     * 创建表示成功的操作结果
     */
    public static WindowOperationResult success(String operation) {
        return new WindowOperationResult(WindowOperationStatus.SUCCESS, operation, "", 0);
    }

    /**
     * 创建表示成功并携带描述信息的操作结果
     */
    public static WindowOperationResult success(String operation, String message) {
        return new WindowOperationResult(WindowOperationStatus.SUCCESS, operation, message, 0);
    }

    /**
     * 创建表示已跳过(当前平台不支持)的操作结果
     */
    public static WindowOperationResult skipped(String operation, String message) {
        return new WindowOperationResult(WindowOperationStatus.SKIPPED, operation, message, 0);
    }

    /**
     * 创建表示失败的操作结果
     *
     * @param operation  操作名称
     * @param nativeCode 原生错误码
     * @param message    失败描述信息
     */
    public static WindowOperationResult failed(String operation, int nativeCode, String message) {
        return new WindowOperationResult(WindowOperationStatus.FAILED, operation, message, nativeCode);
    }

    /**
     * 检查操作是否成功
     */
    public boolean isSuccess() {
        return status == WindowOperationStatus.SUCCESS;
    }

    /**
     * 检查操作是否被跳过
     */
    public boolean isSkipped() {
        return status == WindowOperationStatus.SKIPPED;
    }

    /**
     * 检查操作是否失败
     */
    public boolean isFailure() {
        return status == WindowOperationStatus.FAILED;
    }

    /**
     * 合并多个操作结果为一个汇总结果
     *
     * <p>合并规则：如任一结果失败则整体为失败；否则如有任一成功则整体为成功；
     * 全部跳过则整体为跳过各结果的消息以"; "拼接</p>
     *
     * @param operation 汇总操作名称
     * @param results   待合并的结果集合
     * @return 合并后的汇总结果
     */
    public static WindowOperationResult combine(String operation, Collection<WindowOperationResult> results) {
        if (results == null || results.isEmpty()) {
            return skipped(operation, "未请求任何原生窗口属性");
        }
        WindowOperationResult failure = results.stream()
                .filter(WindowOperationResult::isFailure)
                .findFirst()
                .orElse(null);
        String message = results.stream()
                .filter(result -> !result.message().isBlank())
                .map(result -> result.operation() + ": " + result.message())
                .collect(Collectors.joining("; "));
        if (failure != null) {
            return failed(operation, failure.nativeCode(), message);
        }
        boolean anySuccess = results.stream().anyMatch(WindowOperationResult::isSuccess);
        return anySuccess
                ? success(operation, message)
                : skipped(operation, message);
    }
}
