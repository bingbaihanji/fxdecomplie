package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.Collections;
import java.util.List;

/**
 * 工作区加载结果 — 结构化替代裸 Workspace 返回。
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public record WorkspaceLoadResult(
        Workspace workspace,
        Status status,
        List<String> warnings,
        List<String> errors,
        long elapsedMs) {

    public enum Status {
        /** 加载成功，无异常 */
        OK,
        /** 部分成功（如某些目录不可读但主文件已加载） */
        PARTIAL,
        /** 加载失败 */
        FAILED
    }

    public static WorkspaceLoadResult ok(Workspace ws, long elapsedMs) {
        return new WorkspaceLoadResult(ws, Status.OK,
                Collections.emptyList(), Collections.emptyList(), elapsedMs);
    }

    public static WorkspaceLoadResult partial(Workspace ws, List<String> warnings,
                                               long elapsedMs) {
        return new WorkspaceLoadResult(ws, Status.PARTIAL,
                warnings, Collections.emptyList(), elapsedMs);
    }

    public static WorkspaceLoadResult failed(List<String> errors) {
        return new WorkspaceLoadResult(null, Status.FAILED,
                Collections.emptyList(), errors, 0);
    }

    public boolean isOk() { return status == Status.OK; }
    public boolean isFailed() { return status == Status.FAILED; }
}
