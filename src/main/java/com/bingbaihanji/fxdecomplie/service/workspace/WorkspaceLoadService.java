package com.bingbaihanji.fxdecomplie.service.workspace;

import java.nio.file.Path;

/**
 * 工作区加载服务 — 从文件/目录加载 Workspace.
 *
 * <p>替代 {@code WorkspaceLoader} 静态工具类,支持取消和配置保护参数.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public interface WorkspaceLoadService {

    /** 加载工作区 */
    WorkspaceLoadResult load(Path fileOrDir);

    /** 取消当前加载 */
    void cancel();

    /**
     * 可配置的保护参数.
     */
    record Limits(int maxDepth, int maxFiles) {

        public static final Limits DEFAULT = new Limits(50, 100_000);

        public Limits {
            if (maxDepth < 1) {
                throw new IllegalArgumentException("maxDepth must be >= 1");
            }
            if (maxFiles < 1) {
                throw new IllegalArgumentException("maxFiles must be >= 1");
            }
        }
    }
}
