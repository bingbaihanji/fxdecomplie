package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * 工作区加载器处理文件发现、树构建和工作区创建流水线
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspaceLoader {

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
        BackgroundTasks.run("FileLoader-" + name, () -> {
            try {
                // ---- 步骤 1: 扫描 JAR/ZIP/目录中的所有 .class 和资源条目 ----
                var entries = ClassDiscoverer.discover(file);
                // ---- 步骤 2: 构建带字节码缓存的层级文件树 ----
                TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);
                // ---- 步骤 3: 创建带空索引的工作区模型 ----
                boolean isArchive = isArchiveFile(file);
                Workspace workspace = new Workspace(name, file, treeRoot, isArchive,
                        WorkspaceIndex.EMPTY);
                // ---- 步骤 4: 在 JavaFX 线程上通知 UI完整索引按需构建 ----
                Platform.runLater(() -> {
                    if (onSuccess != null) {
                        onSuccess.accept(workspace);
                    }
                    config.addRecentFile(file.getAbsolutePath());
                });
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept(msg);
                    }
                });
            } catch (Error e) {
                // OOM / StackOverflow 等致命错误：通知 UI 后重新抛出以保留原始语义
                Platform.runLater(() -> {
                    if (onError != null) {
                        onError.accept("Fatal: " + e.getClass().getSimpleName());
                    }
                });
                throw e;
            }
        });
    }

    /** 判断文件是否为归档文件(JAR/ZIP)，验证扩展名 + 文件头 ZIP 魔数 */
    private static boolean isArchiveFile(File file) {
        String name = file.getName().toLowerCase();
        if (!name.endsWith(".jar") && !name.endsWith(".zip")) {
            return false;
        }
        // 验证文件头是否为 ZIP 格式（PK\x03\x04），避免把伪装成 .jar 的普通文件当作归档
        try (var in = new java.io.FileInputStream(file)) {
            byte[] header = new byte[4];
            return in.read(header) == 4
                    && header[0] == 0x50 && header[1] == 0x4B
                    && header[2] == 0x03 && header[3] == 0x04;
        } catch (IOException e) {
            return false;
        }
    }
}
