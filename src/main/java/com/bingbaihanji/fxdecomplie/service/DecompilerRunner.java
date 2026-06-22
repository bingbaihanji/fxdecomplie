package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipFile;

/**
 * 共享的带保护的反编译运行器,用于标签页、全文搜索和导出
 */
public final class DecompilerRunner {

    private static final Logger logger = LoggerFactory.getLogger(DecompilerRunner.class);
    private static final DecompilerTypeEnum[] JD_FALLBACK_ENGINES = {
            DecompilerTypeEnum.VINEFLOWER,
            DecompilerTypeEnum.CFR,
            DecompilerTypeEnum.PROCYON
    };
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_DECOMPILER_THREADS = Math.clamp(Runtime.getRuntime().availableProcessors() / 2, 1, 2);
    private static final AtomicInteger THREAD_ID = new AtomicInteger();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            MAX_DECOMPILER_THREADS,
            MAX_DECOMPILER_THREADS,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(MAX_DECOMPILER_THREADS * 2),
            r -> {
                Thread t = new Thread(r, "decompiler-" + THREAD_ID.incrementAndGet());
                t.setDaemon(true);
                return t;
            });

    static {
        EXECUTOR.allowCoreThreadTimeOut(true);
    }

    private DecompilerRunner() {
        throw new AssertionError("utility class");
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

    public static String decompileWithTimeout(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context,
                                              BooleanSupplier active,
                                              int timeoutSeconds) {
        if (classBytes == null) {
            closeContext(context);
            return failureOutput(classFilePath, "类字节码未找到");
        }
        BooleanSupplier requestActive = active == null ? () -> true : active;
        if (!requestActive.getAsBoolean()) {
            closeContext(context);
            throw new CancellationException("反编译请求已被替换");
        }

        Future<String> future = null;
        int timeout = Math.max(1, timeoutSeconds);
        try {
            future = EXECUTOR.submit(() -> {
                try {
                    return decompileWithFallback(classFilePath, classBytes, engine, context);
                } finally {
                    closeContext(context);
                }
            });
            String source = future.get(timeout, TimeUnit.SECONDS);
            if (!requestActive.getAsBoolean()) {
                throw new CancellationException("反编译请求已被替换");
            }
            return source;
        } catch (RejectedExecutionException e) {
            closeContext(context);
            return I18nUtil.getString("decompile.busy", classFilePath);
        } catch (TimeoutException e) {
            future.cancel(true);
            return I18nUtil.getString("decompile.timeout", classFilePath, timeout)
                    + "\n" + I18nUtil.getString("decompile.timeoutHint");
        } catch (InterruptedException e) {
            future.cancel(true);
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
        return DecompilerContext.of(classPath::getClassBytes, options, classPath);
    }

    public static boolean isTransientFailureOutput(String source) {
        if (source == null || source.isBlank()) {
            return true;
        }
        String normalized = source.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("decompiler is busy")
                || normalized.contains("反编译器正忙")
                || normalized.contains("decompilation timed out")
                || normalized.contains("反编译超时")
                || normalized.contains("jd-core error")
                || normalized.contains("jd-core decompile failed")
                || normalized.startsWith("// decompile failed");
    }

    private static String decompileWithFallback(String classFilePath, byte[] classBytes,
                                                DecompilerTypeEnum engine,
                                                DecompilerContext context) {
        DecompilerTypeEnum selectedEngine = engine == null
                ? DecompilerTypeEnum.VINEFLOWER : engine;
        DecompilerContext effectiveContext = context == null
                ? DecompilerContext.EMPTY : context;
        String source = decompileWithEngine(classFilePath, classBytes,
                selectedEngine, effectiveContext);
        if (selectedEngine != DecompilerTypeEnum.JD || !isJdFailureOutput(source)) {
            return source;
        }

        String jdReason = extractJdFailureReason(source);
        for (DecompilerTypeEnum fallback : JD_FALLBACK_ENGINES) {
            try {
                String fallbackSource = decompileWithEngine(classFilePath, classBytes,
                        fallback, effectiveContext);
                if (fallbackSource != null && !fallbackSource.isBlank()
                        && !isDecompilerFailureOutput(fallbackSource)) {
                    logger.warn("JD-Core 反编译 {} 失败;使用 {} 回退引擎原因: {}",
                            classFilePath, fallback, jdReason);
                    return withFallbackNotice(fallbackSource, fallback, jdReason);
                }
            } catch (RuntimeException e) {
                logger.warn("回退反编译器 {} 对 {} 反编译失败", fallback, classFilePath, e);
            }
        }
        return source;
    }

    private static String decompileWithEngine(String classFilePath, byte[] classBytes,
                                              DecompilerTypeEnum engine,
                                              DecompilerContext context) {
        return DecompilerFactory.getDecompiler(engine)
                .decompile(classFilePath, classBytes, context);
    }

    private static byte[] resolveWorkspaceClassBytes(Workspace workspace, String internalName) {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        String normalized = DecompilerContext.normalizeInternalName(internalName);
        if (workspace.isIndexReady()) {
            byte[] bytes = workspace.getIndex().getClassBytes(normalized);
            if (bytes != null) {
                return bytes;
            }
        }

        FileTreeNode node = workspace.findNodeByPath(normalized + ".class");
        if (node != null) {
            try {
                return WorkspaceByteReader.readNodeBytes(workspace, node, false);
            } catch (IOException e) {
                logger.debug("解析依赖类失败: {}", normalized, e);
            }
        }

        try {
            return WorkspaceByteReader.readClassBytes(workspace, normalized, false);
        } catch (IOException e) {
            logger.debug("从工作区读取依赖类失败: {}", normalized, e);
        }
        return null;
    }

    private static void closeContext(DecompilerContext context) {
        if (context == null || context == DecompilerContext.EMPTY
                || context == DecompilerContext.LEGACY_GLOBAL) {
            return;
        }
        try {
            context.close();
        } catch (Exception e) {
            logger.debug("关闭反编译上下文失败", e);
        }
    }

    /**
     * 单次反编译使用的工作区 classpath。归档输入复用同一个 ZipFile，避免大 JAR
     * 依赖解析时为每个引用类重复打开归档导致交互打开变慢。
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
                    return readArchiveEntry(path);
                }
                if (node != null) {
                    return WorkspaceByteReader.readNodeBytes(workspace, node, false);
                }
                return WorkspaceByteReader.readClassBytes(workspace, normalized, false);
            } catch (IOException e) {
                logger.debug("读取依赖类失败: {}", normalized, e);
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

    private static String failureOutput(String classFilePath, String reason) {
        return "// 反编译失败: " + classFilePath
                + "\n// " + (reason == null || reason.isBlank() ? "未知错误" : reason);
    }

    private static boolean isJdFailureOutput(String source) {
        if (source == null) {
            return false;
        }
        return source.contains("JD-Core Error:")
                || source.contains("JD-Core decompile failed");
    }

    private static boolean isDecompilerFailureOutput(String source) {
        if (source == null) {
            return true;
        }
        String trimmed = source.trim();
        return trimmed.startsWith("// CFR decompile failed")
                || trimmed.startsWith("// Procyon decompile failed")
                || trimmed.startsWith("// Vineflower decompile failed")
                || trimmed.startsWith("// Decompile failed")
                || isJdFailureOutput(source);
    }

    private static String extractJdFailureReason(String source) {
        if (source == null || source.isBlank()) {
            return "未知的 JD-Core 错误";
        }
        String normalized = source.replace("\r\n", "\n");
        int errorIndex = normalized.indexOf("JD-Core Error:");
        if (errorIndex < 0) {
            errorIndex = normalized.indexOf("JD-Core decompile failed");
        }
        String reason = errorIndex >= 0 ? normalized.substring(errorIndex) : normalized;
        int lineEnd = reason.indexOf('\n');
        if (lineEnd >= 0) {
            reason = reason.substring(0, lineEnd);
        }
        reason = reason.replace("*/", "* /").trim();
        return reason.length() > 220 ? reason.substring(0, 220) + "..." : reason;
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
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(2, TimeUnit.SECONDS)) {
                EXECUTOR.shutdownNow();
            }
        } catch (InterruptedException e) {
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
