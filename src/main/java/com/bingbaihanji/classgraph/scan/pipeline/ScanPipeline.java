package com.bingbaihanji.classgraph.scan.pipeline;

import com.bingbaihanji.classgraph.scan.config.ScanOptions;
import com.bingbaihanji.classgraph.util.InterruptionChecker;
import com.bingbaihanji.classgraph.util.LogNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/**
 * 扫描管道 — 将扫描分解为一系列有序的 {@link ScanStep} 步骤。
 *
 * <p>管道保证按注册顺序执行步骤。每个步骤可以：
 * <ul>
 *   <li>从 {@link ScanContext} 读取前一步骤的结果</li>
 *   <li>执行其处理逻辑</li>
 *   <li>将结果写回 {@link ScanContext} 供后续步骤使用</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * ScanPipeline<String, ScanResult> pipeline = ScanPipeline
 *     .startingWith(new ClasspathDiscoveryStep())
 *     .then(new ClasspathMaskingStep())
 *     .then(new ClassfileScanningStep())
 *     .then(new ResultAssemblyStep());
 *
 * ScanResult result = pipeline.execute(initialInput, context);
 * }</pre>
 *
 * @param <I> 初始输入类型
 * @param <O> 最终输出类型
 */
public final class ScanPipeline<I, O> {

    private static final Logger log = LoggerFactory.getLogger(ScanPipeline.class);

    private final List<StepDescriptor<?, ?>> steps;

    private ScanPipeline(List<StepDescriptor<?, ?>> steps) {
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    /**
     * 创建管道构建器，以初始步骤开始。
     *
     * @param firstStep 第一个处理步骤
     * @param <I> 初始输入类型
     * @param <T> 第一个步骤的输出类型
     * @return 构建器
     */
    public static <I, T> PipelineBuilder<I, T> startingWith(
            ScanStep<I, T> firstStep) {
        Objects.requireNonNull(firstStep, "firstStep");
        PipelineBuilder<I, T> builder = new PipelineBuilder<>();
        builder.steps.add(new StepDescriptor<>(firstStep));
        return builder;
    }

    /**
     * 执行管道。
     *
     * @param input   初始输入
     * @param context 共享上下文
     * @return 最终输出
     * @throws Exception 任何步骤失败时抛出
     */
    @SuppressWarnings("unchecked")
    public O execute(I input, ScanContext context)
            throws InterruptedException, java.util.concurrent.ExecutionException, Exception {
        Object currentInput = input;
        int stepIdx = 0;

        for (StepDescriptor<?, ?> descriptor : steps) {
            context.checkInterrupted();
            if (descriptor.step.shouldSkip(context)) {
                log.debug("Skipping step: {}", descriptor.step.name());
                continue;
            }
            log.debug("Executing step {}/{}: {}", ++stepIdx, steps.size(),
                descriptor.step.name());
            try {
                currentInput = ((ScanStep<Object, Object>) descriptor.step)
                    .execute(currentInput, context);
            } catch (Exception e) {
                log.error("Pipeline step failed: {}", descriptor.step.name(), e);
                throw e;
            }
        }

        return (O) currentInput;
    }

    /** 返回管道中的步骤数量 */
    public int stepCount() {
        return steps.size();
    }

    /** 返回管道中步骤名称的不可变列表 */
    public List<String> stepNames() {
        return steps.stream()
            .map(d -> d.step.name())
            .toList();
    }

    // ─── 内部类型 ───

    private record StepDescriptor<I, O>(ScanStep<I, O> step) {}

    /**
     * 流式管道构建器。
     *
     * @param <I> 当前阶段的输入类型
     * @param <O> 当前阶段的输出类型
     */
    public static final class PipelineBuilder<I, O> {
        private final List<StepDescriptor<?, ?>> steps = new ArrayList<>();

        private PipelineBuilder() {}

        /**
         * 添加下一个步骤。新步骤的输入类型必须与前一步骤的输出类型兼容。
         */
        public <N> PipelineBuilder<I, N> then(ScanStep<O, N> nextStep) {
            Objects.requireNonNull(nextStep, "nextStep");
            steps.add(new StepDescriptor<>(nextStep));
            @SuppressWarnings("unchecked")
            PipelineBuilder<I, N> cast = (PipelineBuilder<I, N>) this;
            return cast;
        }

        /** 构建不可变管道 */
        public ScanPipeline<I, O> build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("Pipeline must have at least one step");
            }
            return new ScanPipeline<>(steps);
        }
    }

    // ─── 工厂方法 ───

    /**
     * 创建带有基本配置的 ScanContext。
     */
    public static ScanContext createContext(ScanOptions options,
                                            ExecutorService executor,
                                            InterruptionChecker checker,
                                            int parallelism) {
        return new ScanContext(options, executor, checker, parallelism,
            new LogNode());
    }
}
