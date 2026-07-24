package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.concurrent.CompletableFuture;

/**
 * 项目唯一扫描入口 — 所有 class 扫描必须通过此接口。
 *
 * <p>实现类负责将内部扫描引擎（当前为 ClassGraph）适配为此接口。</p>
 *
 * @author bingbaihanji
 */
public interface ClassScanService {

    /** 同步扫描 */
    ClassScanResult scan(ClassScanRequest request);

    /** 异步扫描 */
    CompletableFuture<ClassScanResult> scanAsync(ClassScanRequest request);

    /** 取消指定请求的扫描 */
    void cancel(ClassScanRequest request);
}
