package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;

import java.io.IOException;

/**
 * 增量导出写入器:按原始顺序接收已反编译内容并立即写出.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
interface ExportSink extends AutoCloseable {

    /**
     * 写入单个条目.
     *
     * @return true 表示已写出;false 表示按冲突策略跳过
     */
    boolean write(FileTreeNode item, ExportService.ExportContent content) throws IOException;

    @Override
    void close() throws IOException;
}
