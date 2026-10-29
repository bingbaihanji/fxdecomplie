package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 反编译缓存键 — 工作区、类指纹、内部名、引擎、选项与键版本.
 *
 * <p>各字段以 \0 分隔符拼接为复合键. JVM 内部名称不会包含 NUL,工作区 ID 与选项哈希
 * 也由本项目生成,实践上同样不含 NUL;该分隔符用于降低前缀碰撞概率,并非严格的编码保证.</p>
 *
 * <p>{@link #invalidationPrefix()} 与 {@link #workspacePrefix()} 只用于缓存失效扫描:
 * 前缀匹配范围过大最多造成误删(表现为缓存未命中后重新反编译),不会返回错误数据.
 * L2 的 CLASS 级失效按 {@link #invalidationPrefix()} 前缀删除,只清理当前 classFingerprint
 * 下的条目,旧指纹的条目会一直保留到容量淘汰(eviction)时才被清理;
 * L3 的 CLASS 级失效则按工作区 + 内部名删除该类的全部指纹/选项/键版本变体.</p>
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
