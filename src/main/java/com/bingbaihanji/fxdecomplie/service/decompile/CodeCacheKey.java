package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 反编译缓存键 — 工作区、类指纹、内部名、引擎、选项与键版本.
 *
 * <p>使用 \0 分隔符避免前缀碰撞(\0 不出现在 JVM 内部名称中).</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public record CodeCacheKey(String workspaceId, String classFingerprint, String internalName,
                           DecompilerTypeEnum engine, String optionsHash, int keyVersion) {

    private static final String SEP = "\0";

    public CodeCacheKey {
        Objects.requireNonNull(workspaceId, "workspaceId");
        Objects.requireNonNull(classFingerprint, "classFingerprint");
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(optionsHash, "optionsHash");
    }

    /** 构建用于精确匹配的复合键 */
    public String compositeKey() {
        return workspaceId + SEP + classFingerprint + SEP + internalName + SEP
                + engine.name() + SEP + optionsHash + SEP + keyVersion;
    }

    /** 构建用于前缀批量失效的键(精确到类) */
    public String invalidationPrefix() {
        return workspaceId + SEP + classFingerprint + SEP + internalName + SEP;
    }

    /** 构建工作区级失效前缀 */
    public String workspacePrefix() {
        return workspaceId + SEP;
    }
}
