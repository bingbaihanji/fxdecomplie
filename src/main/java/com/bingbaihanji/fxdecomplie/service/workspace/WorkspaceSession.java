package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.time.Instant;
import java.util.Objects;

/**
 * 一次已打开工作区的会话标识.
 *
 * @param id        会话 ID(绝对路径 + 内容戳的短哈希)
 * @param workspace 工作区
 * @param openedAt  打开时间
 * @param active    是否为当前活动工作区
 * @author bingbaihanji
 */
public record WorkspaceSession(String id, Workspace workspace,
                               Instant openedAt, boolean active) {

    public WorkspaceSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(workspace, "workspace");
        Objects.requireNonNull(openedAt, "openedAt");
    }
}
