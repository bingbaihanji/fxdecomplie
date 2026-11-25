package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Map;

/**
 * 类源码加载结果。
 *
 * @param sourceCode   最终展示源码（已应用重命名与可选注释）
 * @param engine       实际使用的引擎（可能因 JD-Core 版本回退而变化）
 * @param cacheHit     是否命中 L2/L3
 * @param failure      是否为反编译失败输出
 * @param displayName  重命名后的显示类名
 * @param workspaceHash 工作区哈希
 * @param options      实际使用的引擎选项
 * @author bingbaihanji
 */
public record ClassSourceResult(String sourceCode, DecompilerTypeEnum engine,
                                boolean cacheHit, boolean failure,
                                String displayName, String workspaceHash,
                                Map<String, String> options) {

    public ClassSourceResult {
        options = options == null ? Map.of() : Map.copyOf(options);
        displayName = displayName == null ? "" : displayName;
        workspaceHash = workspaceHash == null ? "" : workspaceHash;
    }
}
