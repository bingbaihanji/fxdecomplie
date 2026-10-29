package com.bingbaihanji.fxdecomplie.service;

import java.io.IOException;

/**
 * 增量导出写入器:按原始顺序接收已反编译内容并立即写出.
 *
 * <p>由单个写线程使用(ZipOutputStream 非线程安全).{@code write} 返回 false
 * 表示该条目按冲突策略被跳过.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
interface ExportSink extends AutoCloseable {

    boolean write(ExportService.ExportContent content) throws IOException;

    @Override
    void close() throws IOException;
}
