package com.bingbaihanji.fxdecomplie.service;

import java.io.IOException;

/**
 * 导出路径不安全或冲突策略无法解析.
 *
 * <p>包装为 IOException 以便流式写入层统一处理,同时与真正的流 IO 错误区分:
 * 前者跳过该条目继续导出,ZIP 归档不会因此中止.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
final class UnsafeExportPathException extends IOException {

    UnsafeExportPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
