package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.util.List;

/**
 * 反编译调度器接口
 * <p>
 * 负责将待反编译的类列表拆分为多个批次，
 * 以支持并行反编译和进度跟踪不同的实现可以使用不同的
 * 分批策略 (如按类数量、按依赖关系等)
 */
public interface IDecompileScheduler {
    /**
     * 将类列表构建为反编译批次
     * <p>
     * 每个内部列表代表一个独立的批次，调度器可以并行处理
     * 每个批次中的类，同时保证批次间的顺序满足依赖关系
     *
     * @param classes 待反编译的类列表
     * @return 分批后的类列表，每个内部列表为一个批次
     */
    List<List<JavaClass>> buildBatches(List<JavaClass> classes);
}
