package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;

import java.util.Map;

/**
 * 导出只读源码缓存 — 命中时跳过反编译；导出流程不回写.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
@FunctionalInterface
public interface ExportSourceCache {

    /**
     * 查询类源码.
     *
     * @param node       类文件节点
     * @param classBytes 类字节码
     * @param engine     导出引擎
     * @param options    引擎选项
     * @return 原始反编译源码；未命中返回 null
     */
    String load(FileTreeNode node, byte[] classBytes,
                DecompilerTypeEnum engine, Map<String, String> options);

    /** 空实现：永不命中 */
    static ExportSourceCache none() {
        return (node, classBytes, engine, options) -> null;
    }
}
