package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * 导出流水线:按原始顺序提交反编译,在有界窗口内边完成边写盘.
 *
 * <p>内存上界只与窗口大小(反编译并发数)有关，与导出总量无关.</p>
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
final class ExportPipeline {

    private static final Logger log = LoggerFactory.getLogger(ExportPipeline.class);

    private ExportPipeline() {
        throw new AssertionError("utility class");
    }

    static void run(List<FileTreeNode> items, ExportConfig config,
                    ClassBytecodeProvider provider, CommentScope commentScope,
                    ExportService.ExportState state, BooleanSupplier canceled) throws IOException {
        // JADX 内核共享进程级临时目录，适配层用单许可信号量串行化；
        // 导出窗口必须为 1，否则等待信号量的时间会计入 30s 超时预算。
        // 其他引擎为共享反编译池保留至少一个线程，避免导出占满池导致交互式反编译排队。
        int parallelism = config.engine() == DecompilerTypeEnum.JADX
                ? 1
                : Math.max(1, DecompilerRunner.decompilerThreads() - 1);
        ExecutorService decompileExecutor = Executors.newFixedThreadPool(
                parallelism, daemonFactory("export-decompile-"));
        try (ExportSink sink = ExportWriter.openSink(config)) {
            ArrayDeque<Future<ItemResult>> inFlight = new ArrayDeque<>(parallelism);
            int nextSubmit = 0;
            int nextWrite = 0;
            boolean interruptedOrCanceled = false;
            while (nextWrite < items.size()) {
                if (canceled != null && canceled.getAsBoolean()) {
                    interruptedOrCanceled = true;
                    break;
                }
                while (inFlight.size() < parallelism && nextSubmit < items.size()) {
                    FileTreeNode item = items.get(nextSubmit++);
                    inFlight.addLast(decompileExecutor.submit(
                            () -> decompileItem(item, config, provider, commentScope, canceled)));
                }
                Future<ItemResult> future = inFlight.removeFirst();
                FileTreeNode item = items.get(nextWrite++);
                ItemResult result;
                try {
                    result = future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    interruptedOrCanceled = true;
                    break;
                } catch (java.util.concurrent.CancellationException e) {
                    interruptedOrCanceled = true;
                    break;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof VirtualMachineError error) {
                        throw error;
                    }
                    result = ItemResult.error(messageOf(cause));
                }
                if (result.content() == null) {
                    if (canceled != null && canceled.getAsBoolean()) {
                        interruptedOrCanceled = true;
                        break;
                    }
                    if (result.error() != null) {
                        state.errors.add(item.getFullPath() + ": " + result.error());
                    }
                    state.advance(item.getFullPath());
                    continue;
                }
                try {
                    if (sink.write(result.content())) {
                        state.successCount++;
                    }
                } catch (IOException e) {
                    state.errors.add(item.getFullPath() + ": " + messageOf(e));
                    state.advance(item.getFullPath());
                    // 路径校验/冲突策略失败由 sink 包装为 IOException,流本身仍可用;
                    // 只有真正的 ZIP 写入 IO 错误才中止整个归档.
                    boolean unsafePath = e instanceof UnsafeExportPathException;
                    if (config.format() == ExportConfig.Format.ZIP && !unsafePath) {
                        log.error("ZIP 写入失败,中止后续条目: {}", item.getFullPath(), e);
                        for (Future<ItemResult> pending : inFlight) {
                            pending.cancel(true);
                        }
                        break;
                    }
                    continue;
                }
                state.advance(item.getFullPath());
            }
            if (interruptedOrCanceled || (canceled != null && canceled.getAsBoolean()
                    && nextWrite < items.size())) {
                state.errors.add("导出已取消");
                for (Future<ItemResult> future : inFlight) {
                    future.cancel(true);
                }
            }
        } finally {
            decompileExecutor.shutdownNow();
            try {
                decompileExecutor.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static ItemResult decompileItem(FileTreeNode item, ExportConfig config,
                                            ClassBytecodeProvider provider,
                                            CommentScope commentScope,
                                            BooleanSupplier canceled) {
        if (canceled != null && canceled.getAsBoolean()) {
            return ItemResult.error(null);
        }
        try {
            DecompilerContext context = DecompilerContext.of(provider,
                    config.engineOptions(), null,
                    commentScope == null ? "" : commentScope.workspaceHash());
            try {
                return ItemResult.success(buildExportContent(item, context, config,
                        commentScope, canceled));
            } finally {
                try {
                    context.close();
                } catch (Exception e) {
                    log.debug("关闭反编译上下文失败", e);
                }
            }
        } catch (Exception e) {
            return ItemResult.error(messageOf(e));
        }
    }

    private static ExportService.ExportContent buildExportContent(
            FileTreeNode data, DecompilerContext context, ExportConfig config,
            CommentScope commentScope, BooleanSupplier canceled) throws IOException {
        if (data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data, context);
            if (bytes == null) {
                throw new IllegalStateException("未找到类字节码: " + data.getFullPath());
            }
            BooleanSupplier active = canceled == null
                    ? () -> !Thread.currentThread().isInterrupted()
                    : () -> !canceled.getAsBoolean();
            String source = DecompilerRunner.decompileWithTimeoutNoClose(
                    data.getFullPath(), bytes, config.engine(), context, active);
            if (DecompilerRunner.isFailureOutput(source)) {
                throw new IllegalStateException(firstLine(source));
            }
            String fullPath = data.getFullPath();
            String internalName = ClassNameUtil.normalizeInternalName(fullPath);
            String workspaceHash = commentScope == null ? "" : commentScope.workspaceHash();
            source = RenameService.applyRenames(source, workspaceHash, internalName);
            if (commentScope != null && commentScope.enabled()) {
                source = CommentExportDecorator.applyForClass(
                        source, data.getFullPath(), commentScope);
            }
            String javaPath = RenameService.renamedJavaPath(internalName, workspaceHash);
            return new ExportService.ExportContent(javaPath,
                    source.getBytes(StandardCharsets.UTF_8));
        }

        byte[] bytes = data.resolveBytes();
        if (bytes == null) {
            throw new IllegalStateException("资源字节码未找到");
        }
        return new ExportService.ExportContent(data.getFullPath(), bytes);
    }

    private static byte[] resolveClassBytes(FileTreeNode data, DecompilerContext context)
            throws IOException {
        byte[] bytes = data.resolveBytes();
        if (bytes != null) {
            return bytes;
        }
        String fullPath = data.getFullPath();
        String internalName = ClassNameUtil.normalizeInternalName(fullPath);
        return context == null ? null : context.getClassBytes(internalName);
    }

    private static String firstLine(String text) {
        if (text == null || text.isBlank()) {
            return "反编译失败";
        }
        int end = text.indexOf('\n');
        return end >= 0 ? text.substring(0, end).trim() : text.trim();
    }

    private static String messageOf(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        String message = throwable.getMessage();
        return message != null ? message : throwable.getClass().getSimpleName();
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger id = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + id.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private record ItemResult(ExportService.ExportContent content, String error) {

        static ItemResult success(ExportService.ExportContent content) {
            return new ItemResult(content, null);
        }

        static ItemResult error(String error) {
            return new ItemResult(null, error);
        }
    }
}
