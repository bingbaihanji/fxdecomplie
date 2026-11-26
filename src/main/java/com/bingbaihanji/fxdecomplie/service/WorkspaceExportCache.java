package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.cache.CacheKeys;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.DecompileCacheService;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.Map;
import java.util.Objects;

/**
 * 工作区导出只读缓存 — 只调用 {@link DecompileCacheService#load}，不写回.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class WorkspaceExportCache implements ExportSourceCache {

    private final Workspace workspace;

    private final DecompileCacheService cache;

    public WorkspaceExportCache(Workspace workspace, DecompileCacheService cache) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.cache = Objects.requireNonNull(cache, "cache");
    }

    @Override
    public String load(FileTreeNode node, byte[] classBytes,
                       DecompilerTypeEnum engine, Map<String, String> options) {
        String internalName = ClassNameUtil.normalizeInternalName(node.getFullPath());
        CodeCacheKey key = CacheKeys.forClass(workspace, classBytes, internalName,
                engine, options);
        return cache.load(key).orElse(null);
    }
}
