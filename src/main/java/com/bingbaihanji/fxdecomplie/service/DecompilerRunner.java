package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.util.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

/**
 * 共享的带保护的反编译运行器,用于标签页、全文搜索和导出
 *
 * <h3>整体流水线</h3>
 * <ol>
 *   <li><b>提交</b> — 调用方通过 {@link #decompileWithTimeout} 提交反编译请求</li>
 *   <li><b>执行</b> — 在线程池中运行 {@link #decompileWithFallback}：
 *     <ol type="a">
 *       <li>首选引擎反编译 → 成功则返回</li>
 *       <li>引擎抛异常或返回失败文本 → 按 VF→CFR→Procyon→JD 顺序回退</li>
 *     </ol>
 *   </li>
 *   <li><b>超时保护</b> — 30s 超时中断,连续超时 3 次重建线程池丢弃僵死线程</li>
 *   <li><b>上下文清理</b> — finally 块或异常路径中关闭 DecompilerContext</li>
 * </ol>
 *
 * <h3>线程模型</h3>
 * 使用独立线程池（1-2 核,队列 2-4）,与 BackgroundTasks 主线程池隔离,
 * 避免反编译任务（CPU/IO 密集）阻塞索引和搜索等短任务
 */
public final class DecompilerRunner {

    private static final Logger log = LoggerFactory.getLogger(DecompilerRunner.class);
    private static final DecompilerTypeEnum[] JD_FALLBACK_ENGINES = {
            DecompilerTypeEnum.VINEFLOWER,
            DecompilerTypeEnum.CFR,
            DecompilerTypeEnum.PROCYON,
            // JD-Core 对新语法支持较弱,只作为其他引擎失败后的最后兜底
            DecompilerTypeEnum.JD
    };
    private static final int TIMEOUT_SECONDS = 30;
    /** 连续超时阈值：超过后重建线程池以丢弃可能僵死的线程 */
    private static final int CONSECUTIVE_TIMEOUT_THRESHOLD = 3;
    private static final int MAX_DECOMPILER_THREADS = Math.clamp(
            Runtime.getRuntime().availableProcessors() / 2, 1, 2);
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final AtomicInteger consecutiveTimeouts = new AtomicInteger();
    private static volatile ThreadPoolExecutor executor = createExecutor();

    private DecompilerRunner() {
        throw new AssertionError("utility class");
    }

    private static ThreadPoolExecutor createExecutor() {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(
                MAX_DECOMPILER_THREADS,
                MAX_DECOMPILER_THREADS,
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(MAX_DECOMPILER_THREADS * 2),
                r -> {
                    Thread t = new Thread(r,
                            "decompiler-" + THREAD_ID.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    /**
     * 条件性重建线程池仅在连续超时计数达到阈值时执行,检查和重置均在内
     * 部锁保护下原子完成,避免多线程竞态导致双重重建或阈值检查被绕过
     *
     * @return true 表示线程池已被重建
     */
    private static synchronized boolean maybeRebuildExecutor() {
        int timeouts = consecutiveTimeouts.incrementAndGet();
        if (timeouts < CONSECUTIVE_TIMEOUT_THRESHOLD) {
            return false;
        }
        ThreadPoolExecutor old = executor;
        executor = createExecutor();
        old.shutdownNow();
        consecutiveTimeouts.set(0);
        log.info("检测到 {} 次连续超时,已重建反编译线程池", CONSECUTIVE_TIMEOUT_THRESHOLD);
        return true;
    }

    public static String decompileWithTimeout(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context) {
        return decompileWithTimeout(classFilePath, classBytes, engine, context, () -> true);
    }

    public static String decompileWithTimeout(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context,
                                              BooleanSupplier active) {
        return decompileWithTimeout(classFilePath, classBytes, engine, context,
                active, TIMEOUT_SECONDS);
    }

    /**
     * 批量反编译专用：不关闭 context,由调用方管理生命周期
     */
    public static String decompileWithTimeoutNoClose(String classFilePath, byte[] classBytes,
                                                      DecompilerTypeEnum engine,
                                                      DecompilerContext context,
                                                      BooleanSupplier active) {
        return decompileWithTimeout(classFilePath, classBytes, engine, context,
                active, TIMEOUT_SECONDS, false);
    }

    public static String decompileWithTimeout(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context,
                                              BooleanSupplier active,
                                              int timeoutSeconds) {
        return decompileWithTimeout(classFilePath, classBytes, engine, context,
                active, timeoutSeconds, true);
    }

    /**
     * 带上下文生命周期控制的反编译方法
     *
     * @param closeContext 若为 false,调用方负责关闭 context（用于导出等批量场景）
     */
    public static String decompileWithTimeout(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context,
                                              BooleanSupplier active,
                                              int timeoutSeconds,
                                              boolean closeContext) {
        if (classBytes == null) {
            if (closeContext) {
                closeContext(context);
            }
            return failureOutput(classFilePath, "类字节码未找到");
        }
        BooleanSupplier requestActive = active == null ? () -> true : active;
        if (!requestActive.getAsBoolean()) {
            if (closeContext) {
                closeContext(context);
            }
            throw new CancellationException("反编译请求已被替换");
        }

        log.debug("反编译开始: {} engine={}, timeout={}s", classFilePath, engine, timeoutSeconds);
        long decompileStart = System.currentTimeMillis();
        Future<String> future = null;
        int timeout = Math.max(1, timeoutSeconds);
        try {
            future = executor.submit(() -> {
                try {
                    return decompileWithFallback(classFilePath, classBytes, engine, context);
                } finally {
                    if (closeContext) {
                        closeContext(context);
                    }
                }
            });
            String source = future.get(timeout, TimeUnit.SECONDS);
            if (!requestActive.getAsBoolean()) {
                throw new CancellationException("反编译请求已被替换");
            }
            // 反编译成功,重置连续超时计数器
            consecutiveTimeouts.set(0);
            long elapsed = System.currentTimeMillis() - decompileStart;
            boolean isFailure = isFailureOutput(source);
            log.debug("反编译完成: {} engine={} ({}ms) failure={}", classFilePath, engine, elapsed, isFailure);
            return source;
        } catch (RejectedExecutionException e) {
            if (closeContext) {
                closeContext(context);
            }
            log.warn("反编译被拒绝(队列满): {} engine={}", classFilePath, engine);
            return I18nUtil.getString("decompile.busy", classFilePath);
        } catch (TimeoutException e) {
            long elapsed = System.currentTimeMillis() - decompileStart;
            log.warn("反编译超时: {} engine={} ({}ms, timeout={}s)", classFilePath, engine,
                    elapsed, timeoutSeconds);
            future.cancel(true);
            // 给后台线程一个短暂的窗口退出(其 finally 块会关闭 context),
            // 然后作为安全兜底再尝试关闭(close 是幂等的)
            try {
                future.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
                // 任务可能尚未退出,忽略
            }
            if (closeContext) {
                closeContext(context);
            }
            // 连续超时超过阈值时重建线程池,丢弃可能僵死的守护线程
            boolean rebuilt = maybeRebuildExecutor();
            if (rebuilt) {
                return I18nUtil.getString("decompile.timeout", classFilePath, timeout)
                        + "\n" + I18nUtil.getString("decompile.timeoutHint")
                        + "\n" + I18nUtil.getString("decompile.busyRecovered");
            }
            return I18nUtil.getString("decompile.timeout", classFilePath, timeout)
                    + "\n" + I18nUtil.getString("decompile.timeoutHint");
        } catch (InterruptedException e) {
            future.cancel(true);
            if (closeContext) {
                closeContext(context);
            }
            Thread.currentThread().interrupt();
            throw new RuntimeException("反编译被中断: " + classFilePath, e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return failureOutput(classFilePath, cause.getMessage() != null
                    ? cause.getMessage() : cause.getClass().getSimpleName());
        }
    }

    public static DecompilerContext contextForWorkspace(Workspace workspace,
                                                        Map<String, String> options) {
        if (workspace == null) {
            return DecompilerContext.withOptions(options);
        }
        WorkspaceClassPath classPath = new WorkspaceClassPath(workspace);
        return DecompilerContext.singleUse(classPath::getClassBytes, options, classPath);
    }

    /**
     * 瞬时失败（繁忙/超时）,此类输出不应缓存,但提示用户可重试
     */
    public static boolean isTransientFailureOutput(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        String normalized = source.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("decompiler is busy")
                || normalized.contains("反编译器正忙")
                || normalized.contains("decompilation timed out")
                || normalized.contains("反编译超时");
    }

    /**
     * 任意失败输出（瞬时 + 永久）,统一判定入口
     * 缓存写入前调用此方法可以避免把错误文本当作源码持久化
     */
    public static boolean isFailureOutput(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        if (isTransientFailureOutput(source)) {
            return true;
        }
        String trimmed = source.trim();
        // JD-Core 返回的特殊失败文本
        if (trimmed.startsWith("// JD-Core Error:")
                || trimmed.startsWith("// JD-Core decompile failed")) {
            return true;
        }
        // 引擎级失败前缀
        return trimmed.startsWith("// Vineflower Error:")
                || trimmed.startsWith("// CFR Error:")
                || trimmed.startsWith("// Procyon Error:")
                || trimmed.startsWith("// CFR decompile failed")
                || trimmed.startsWith("// Procyon decompile failed")
                || trimmed.startsWith("// Vineflower decompile failed")
                || trimmed.startsWith("// Decompile failed")
                || trimmed.startsWith("// 反编译失败");
    }

    private static String decompileWithFallback(String classFilePath, byte[] classBytes,
                                                DecompilerTypeEnum engine,
                                                DecompilerContext context) {
        DecompilerTypeEnum selectedEngine = engine == null
                ? DecompilerTypeEnum.VINEFLOWER : engine;
        DecompilerContext effectiveContext = context == null
                ? DecompilerContext.EMPTY : context;
        String source;
        try {
            source = decompileWithEngine(classFilePath, classBytes,
                    selectedEngine, effectiveContext);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("{} 反编译 {} 抛出异常,尝试回退引擎", selectedEngine, classFilePath, e);
            source = failureOutput(classFilePath, selectedEngine + ": " + message);
        }

        // 仅 JD-Core 失败时走回退（Vineflower/CFR/Procyon 的 "Error:" 输出也视为失败）
        if (!isEngineFailureOutput(source, selectedEngine)) {
            return source;
        }

        String reason = extractFailureReason(source);
        log.warn("{} 反编译 {} 失败,尝试回退引擎,原因: {}", selectedEngine, classFilePath, reason);
        for (DecompilerTypeEnum fallback : JD_FALLBACK_ENGINES) {
            if (fallback == selectedEngine) {
                continue;
            }
            try {
                String fallbackSource = decompileWithEngine(classFilePath, classBytes,
                        fallback, effectiveContext);
                if (fallbackSource != null && !fallbackSource.isBlank()
                        && !isDecompilerFailureOutput(fallbackSource)) {
                    log.warn("{} 反编译 {} 失败;使用 {} 回退引擎原因: {}",
                            selectedEngine, classFilePath, fallback, reason);
                    return withFallbackNotice(fallbackSource, fallback, reason);
                }
            } catch (RuntimeException e) {
                log.warn("回退反编译器 {} 对 {} 反编译失败", fallback, classFilePath, e);
            }
        }
        return source;
    }

    /** 判断指定引擎的输出是否为失败结果（含 Vineflower/CFR/Procyon 的 Error 前缀） */
    private static boolean isEngineFailureOutput(String source, DecompilerTypeEnum engine) {
        if (source == null) {
            return true;
        }
        if (isDecompilerFailureOutput(source)) {
            return true;
        }
        String trimmed = source.trim();
        return switch (engine) {
            case VINEFLOWER -> trimmed.startsWith("// Vineflower Error:");
            case CFR -> trimmed.startsWith("// CFR Error:");
            case PROCYON -> trimmed.startsWith("// Procyon Error:");
            case JD -> isJdFailureOutput(source);
        };
    }

    private static String decompileWithEngine(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context) {
        return DecompilerFactory.getDecompiler(engine)
                .decompile(classFilePath, classBytes, context);
    }

    private static void closeContext(DecompilerContext context) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (Exception e) {
            log.debug("关闭反编译上下文失败", e);
        }
    }

    private static String failureOutput(String classFilePath, String reason) {
        return "// 反编译失败: " + classFilePath
                + "\n// " + (reason == null || reason.isBlank() ? "未知错误" : reason);
    }

    private static boolean isJdFailureOutput(String source) {
        if (source == null) {
            return false;
        }
        String trimmed = source.trim();
        return trimmed.startsWith("// JD-Core Error:")
                || trimmed.startsWith("// JD-Core decompile failed");
    }

    private static boolean isDecompilerFailureOutput(String source) {
        if (source == null) {
            return true;
        }
        String trimmed = source.trim();
        return trimmed.startsWith("// CFR decompile failed")
                || trimmed.startsWith("// Procyon decompile failed")
                || trimmed.startsWith("// Vineflower decompile failed")
                || trimmed.startsWith("// Vineflower Error:")
                || trimmed.startsWith("// CFR Error:")
                || trimmed.startsWith("// Procyon Error:")
                || trimmed.startsWith("// Decompile failed")
                || trimmed.startsWith("// 反编译失败")
                || isJdFailureOutput(source);
    }

    private static String extractFailureReason(String source) {
        if (source == null || source.isBlank()) {
            return "未知错误";
        }
        String normalized = source.replace("\r\n", "\n");
        int errorIndex = findFirst(normalized,
                "Vineflower Error:", "CFR Error:", "Procyon Error:",
                "JD-Core Error:", "JD-Core decompile failed",
                "CFR decompile failed", "Procyon decompile failed",
                "Vineflower decompile failed", "Decompile failed", "反编译失败");
        String reason = errorIndex >= 0 ? normalized.substring(errorIndex) : normalized;
        int lineEnd = reason.indexOf('\n');
        if (lineEnd >= 0) {
            reason = reason.substring(0, lineEnd);
        }
        reason = reason.replace("*/", "* /").trim();
        return reason.length() > 220 ? reason.substring(0, 220) + "..." : reason;
    }

    private static int findFirst(String haystack, String... needles) {
        int best = -1;
        for (String n : needles) {
            int idx = haystack.indexOf(n);
            if (idx >= 0 && (best < 0 || idx < best)) {
                best = idx;
            }
        }
        return best;
    }

    private static String withFallbackNotice(String source, DecompilerTypeEnum fallback,
                                             String reason) {
        String notice = I18nUtil.getString("decompile.jdFallback", fallback.name()) + "\n"
                + I18nUtil.getString("decompile.jdFallbackReason", reason) + "\n\n";
        String normalized = source.replace("\r\n", "\n");
        if (normalized.startsWith("package ")) {
            int packageEnd = normalized.indexOf(";\n");
            if (packageEnd > 0) {
                int insertAt = packageEnd + 2;
                return normalized.substring(0, insertAt) + "\n" + notice + normalized.substring(insertAt);
            }
        }
        return notice + source;
    }

    public static void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 单次反编译使用的工作区 classpath归档输入复用同一个 ZipFile,避免大 JAR
     * 依赖解析时为每个引用类重复打开归档导致交互打开变慢
     */
    private static final class WorkspaceClassPath implements AutoCloseable {
        private static final int MAX_HIT_CACHE = 256;

        private final Workspace workspace;
        private final Map<String, byte[]> hitCache = Collections.synchronizedMap(
                new LinkedHashMap<>(64, 0.75f, true) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                        return size() > MAX_HIT_CACHE;
                    }
                });
        private final Set<String> missCache = Collections.synchronizedSet(new HashSet<>());
        private ZipFile zipFile;

        private WorkspaceClassPath(Workspace workspace) {
            this.workspace = workspace;
        }

        private byte[] getClassBytes(String internalName) {
            String normalized = DecompilerContext.normalizeInternalName(internalName);
            byte[] cached = hitCache.get(normalized);
            if (cached != null) {
                return cached;
            }
            if (missCache.contains(normalized)) {
                return null;
            }

            byte[] bytes = readClassBytes(normalized);
            if (bytes != null) {
                hitCache.put(normalized, bytes);
            } else {
                missCache.add(normalized);
            }
            return bytes;
        }

        private byte[] readClassBytes(String normalized) {
            if (workspace.isIndexReady()) {
                byte[] bytes = workspace.getIndex().getClassBytes(normalized);
                if (bytes != null) {
                    return bytes;
                }
            }

            String path = normalized + ".class";
            FileTreeNode node = workspace.findNodeByPath(path);
            byte[] cached = node == null ? null : node.getCachedBytes();
            if (cached != null) {
                return cached;
            }

            try {
                if (workspace.isArchive()) {
                    String archivePath = node == null ? path : node.getFullPath();
                    return readArchiveEntry(archivePath);
                }
                if (node != null) {
                    return WorkspaceByteReader.readNodeBytes(workspace, node, false);
                }
                return WorkspaceByteReader.readClassBytes(workspace, normalized, false);
            } catch (IOException e) {
                log.debug("读取依赖类失败: {}", normalized, e);
                return null;
            }
        }

        private synchronized byte[] readArchiveEntry(String path) throws IOException {
            if (zipFile == null) {
                zipFile = new ZipFile(workspace.getSourceFile());
            }
            var entry = zipFile.getEntry(path);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            try (var in = zipFile.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (zipFile != null) {
                zipFile.close();
                zipFile = null;
            }
            hitCache.clear();
            missCache.clear();
        }
    }
}
