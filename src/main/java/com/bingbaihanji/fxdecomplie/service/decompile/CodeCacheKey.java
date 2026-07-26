package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

/**
 * 反编译缓存键 — 组合工作区、类、引擎、选项。
 *
 * <p>使用 \0 分隔符避免前缀碰撞（\0 不出现在 JVM 内部名称中）。</p>
 */
public record CodeCacheKey(String workspaceKey, String internalName,
                           DecompilerTypeEnum engine, String optionsHash) {

    private static final String SEP = "\0";

    /** 构建用于精确匹配的复合键 */
    public String compositeKey() {
        return workspaceKey + SEP + internalName + SEP + SEP
                + engine.name() + SEP + optionsHash;
    }

    /** 构建用于前缀批量失效的键 */
    public String invalidationPrefix() {
        return workspaceKey + SEP + internalName + SEP + SEP;
    }

    /** 构建工作区级失效前缀 */
    public String workspacePrefix() {
        return workspaceKey + SEP;
    }
}
