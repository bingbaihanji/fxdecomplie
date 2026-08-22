package com.bingbaihanji.fxdecomplie.ui.code;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 代码标签页上挂载的异步请求生命周期状态.
 *
 * <p>每个 {@link CodeEditorTab} 持有一个 CodeTabState,跟踪反编译请求的
 * generation(用于丢弃过期结果),当前阶段和错误信息.</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public final class CodeTabState {

    private final AtomicLong generation = new AtomicLong(0);

    private volatile Phase phase = Phase.IDLE;

    private volatile String currentRequestId;

    private volatile String errorMessage;

    /** 递增 generation 并进入 LOADING 阶段,返回新 generation */
    public long nextGeneration() {
        phase = Phase.LOADING;
        return generation.incrementAndGet();
    }

    // ─── generation ───

    /** 当前 generation 值 */
    public long currentGeneration() {
        return generation.get();
    }

    /** 验证给定 generation 是否仍然是最新的 */
    public boolean isCurrent(long gen) {
        return generation.get() == gen;
    }

    public void setSuccess() {phase = Phase.SUCCESS;}

    // ─── 阶段管理 ───

    public void setCancelled() {phase = Phase.CANCELLED;}

    public void setError(String msg) {
        phase = Phase.ERROR;
        errorMessage = msg;
    }

    public void setIdle() {
        phase = Phase.IDLE;
        errorMessage = null;
    }

    public Phase phase() {return phase;}

    // ─── 访问器 ───

    public String errorMessage() {return errorMessage;}

    public String currentRequestId() {return currentRequestId;}

    public void setCurrentRequestId(String id) {this.currentRequestId = id;}

    public boolean isLoading() {return phase == Phase.LOADING;}

    // ─── 便利方法 ───

    public boolean isSuccess() {return phase == Phase.SUCCESS;}

    public boolean isTerminal() {
        return phase == Phase.SUCCESS || phase == Phase.CANCELLED || phase == Phase.ERROR;
    }

    /** 标签页状态阶段 */
    public enum Phase {
        /** 空闲,尚未发起反编译 */
        IDLE,
        /** 反编译进行中 */
        LOADING,
        /** 反编译成功 */
        SUCCESS,
        /** 任务被取消 */
        CANCELLED,
        /** 反编译失败 */
        ERROR
    }
}
