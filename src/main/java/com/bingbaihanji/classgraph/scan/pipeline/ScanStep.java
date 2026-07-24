package com.bingbaihanji.classgraph.scan.pipeline;

/**
 * 扫描管道中的一个处理步骤。
 *
 * <p>每个步骤接收共享的 {@link ScanContext}，执行其工作，
 * 并可选择性地更新上下文供后续步骤使用。</p>
 *
 * <p>实现应为无状态或仅在构造时接收配置 — 所有可变状态
 * 应通过 {@link ScanContext} 传递。</p>
 *
 * @param <I> 输入类型（本步骤消费的数据类型）
 * @param <O> 输出类型（本步骤产出的数据类型，存入 context）
 */
@FunctionalInterface
public interface ScanStep<I, O> {

    /**
     * 执行此步骤的处理。
     *
     * @param input   输入数据（来自前一步骤或初始输入）
     * @param context 共享的扫描上下文
     * @return 本步骤的输出
     * @throws Exception 处理失败时抛出
     */
    O execute(I input, ScanContext context) throws Exception;

    /**
     * 返回此步骤的名称（用于日志/调试）。
     * 默认返回类名。
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 返回此步骤是否应跳过执行。
     * 默认总是执行。子类可重写以实现条件执行。
     */
    default boolean shouldSkip(ScanContext context) {
        return false;
    }
}
