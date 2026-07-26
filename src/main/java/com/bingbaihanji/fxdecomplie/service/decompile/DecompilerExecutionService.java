package com.bingbaihanji.fxdecomplie.service.decompile;

import java.util.concurrent.Future;

/**
 * 反编译执行服务 — 将反编译任务提交到专用线程池。
 *
 * <p>替代 {@code DecompilerRunner} 的静态方法，支持注入和测试替换。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public interface DecompilerExecutionService {

    /** 提交反编译请求，返回可等待的 Future */
    Future<DecompileResult> submit(DecompileRequest request);

    /** 根据 requestId 取消进行中的反编译 */
    void cancel(String requestId);

    /** 当前活跃的反编译任务数 */
    int activeCount();

    /** 累计超时次数 */
    int timeoutCount();
}
