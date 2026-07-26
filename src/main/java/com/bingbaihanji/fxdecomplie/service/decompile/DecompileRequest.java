package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 反编译请求 — 不可变，包含所有反编译所需参数。
 */
public record DecompileRequest(
        Workspace workspace,
        FileTreeNode classNode,
        DecompilerTypeEnum preferredEngine,
        String optionsHash,
        String requestId,
        long timeout,
        TimeUnit timeoutUnit) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Workspace workspace;
        private FileTreeNode classNode;
        private DecompilerTypeEnum preferredEngine = DecompilerTypeEnum.JADX;
        private String optionsHash = "default";
        private String requestId = UUID.randomUUID().toString();
        private long timeout = 30;
        private TimeUnit timeoutUnit = TimeUnit.SECONDS;

        public Builder workspace(Workspace v) { workspace = v; return this; }
        public Builder classNode(FileTreeNode v) { classNode = v; return this; }
        public Builder preferredEngine(DecompilerTypeEnum v) { preferredEngine = v; return this; }
        public Builder optionsHash(String v) { optionsHash = v; return this; }
        public Builder requestId(String v) { requestId = v; return this; }
        public Builder timeout(long v) { timeout = v; return this; }
        public Builder timeoutUnit(TimeUnit v) { timeoutUnit = v; return this; }

        public DecompileRequest build() {
            if (workspace == null) throw new IllegalStateException("workspace is required");
            if (classNode == null) throw new IllegalStateException("classNode is required");
            return new DecompileRequest(workspace, classNode, preferredEngine,
                    optionsHash, requestId, timeout, timeoutUnit);
        }
    }
}
