package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 工作区加载器处理文件发现、树构建和工作区创建流水线
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspaceLoader {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceLoader.class);

    private WorkspaceLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * 加载文件并创建工作区(在后台线程上调用)
     *
     * @param file      输入文件/目录
     * @param config    应用配置,用于记录最近文件
     * @param onSuccess 工作区创建成功回调(在 JavaFX 线程上调用)
     * @param onError   加载失败回调(在 JavaFX 线程上调用)
     */
    public static void loadAsync(File file, AppConfig config,
                                 Consumer<Workspace> onSuccess,
                                 Consumer<String> onError) {
        String name = file.getName();
        log.info("开始加载文件: {} ({}), isDir={}", file.getAbsolutePath(),
                file.length(), file.isDirectory());
        long loadStart = System.currentTimeMillis();
        BackgroundTasks.run("FileLoader-" + name, () -> {
            try {
                // ---- 步骤 1: 扫描 JAR/ZIP/目录中的所有 .class 和资源条目 ----
                long t1 = System.currentTimeMillis();
                var entries = ClassDiscoverer.discover(file);
                log.info("文件扫描完成: {} -> {} 个条目 ({}ms)", name, entries.size(),
                        System.currentTimeMillis() - t1);
                // ---- 步骤 2: 构建带字节码缓存的层级文件树 ----
                TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);
                // ---- 步骤 3: 创建带空索引的工作区模型 ----
                boolean isArchive = isArchiveFile(file);
                String contentStamp = computeContentStamp(file);
                Workspace workspace = new Workspace(name, file, treeRoot, isArchive,
                        WorkspaceIndex.EMPTY, contentStamp);
                long totalElapsed = System.currentTimeMillis() - loadStart;
                log.info("工作区创建完成: {} (archive={}, {}ms)", name, isArchive, totalElapsed);
                // ---- 步骤 4: 在 JavaFX 线程上通知 UI完整索引按需构建 ----
                Platform.runLater(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(workspace);
                    }
                    config.addRecentFile(file.getAbsolutePath());
                });
            } catch (Exception e) {
                long totalElapsed = System.currentTimeMillis() - loadStart;
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.error("文件加载失败: {} ({}ms): {}", file.getAbsolutePath(), totalElapsed, msg, e);
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(msg);
                    }
                });
            } catch (Error e) {
                // OOM / StackOverflow 等致命错误：通知 UI 后重新抛出以保留原始语义
                log.error("文件加载致命错误: {}: {}", file.getAbsolutePath(),
                        e.getClass().getSimpleName(), e);
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept("Fatal: " + e.getClass().getSimpleName());
                    }
                });
                throw e;
            }
        });
    }

    /** 判断文件是否为归档文件(JAR/ZIP),验证扩展名 + 文件头 ZIP 魔数 */
    private static boolean isArchiveFile(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".jar") && !name.endsWith(".zip")) {
            return false;
        }
        // 验证文件头是否为 ZIP 格式（PK\x03\x04）,避免把伪装成 .jar 的普通文件当作归档
        try (var in = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            return in.read(header) == 4
                    && header[0] == 0x50 && header[1] == 0x4B
                    && header[2] == 0x03 && header[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 计算文件/目录的内容指纹,用于检测工作区内容是否变更
     * 文件：基于 lastModified + 文件大小；目录：遍历所有文件取最大修改时间、总大小、文件计数
     */
    private static String computeContentStamp(File file) throws IOException {
        if (!file.isDirectory()) {
            return file.lastModified() + "_" + file.length();
        }
        Path root = file.toPath();
        long[] values = new long[3]; // max mtime, total size, file count
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(path -> {
                try {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    values[0] = Math.max(values[0], attrs.lastModifiedTime().toMillis());
                    values[1] += attrs.size();
                    values[2]++;
                } catch (IOException e) {
                    log.debug("计算工作区内容指纹时跳过文件: {}", path, e);
                }
            });
        }
        return values[0] + "_" + values[1] + "_" + values[2];
    }
}
