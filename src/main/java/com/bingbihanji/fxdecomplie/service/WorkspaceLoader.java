package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.application.Platform;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * 工作区加载器。处理文件发现、树构建和工作区创建流水线。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspaceLoader {

	private WorkspaceLoader() {
		throw new AssertionError("utility class");
	}

	/**
	 * 加载文件并创建工作区（在后台线程上调用）。
	 *
	 * @param file      输入文件/目录
	 * @param config    应用配置，用于记录最近文件
	 * @param onSuccess 工作区创建成功回调（在 JavaFX 线程上调用）
	 * @param onError   加载失败回调（在 JavaFX 线程上调用）
	 */
	public static void loadAsync(File file, AppConfig config,
	                             Consumer<Workspace> onSuccess,
	                             Consumer<String> onError) {
		String name = file.getName();
		BackgroundTasks.run("FileLoader-" + name, () -> {
			try {
				// ---- Step 1: scan JAR/ZIP/directory for all .class and resource entries ----
				var entries = ClassDiscoverer.discover(file);
				// ---- Step 2: build hierarchical file tree with bytecode caching ----
				TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);
				// ---- Step 3: create workspace model with empty index ----
				boolean isArchive = isArchiveFile(file);
				Workspace workspace = new Workspace(name, file, treeRoot, isArchive,
						WorkspaceIndex.EMPTY);
				// ---- Step 4: notify UI on JavaFX thread and kick off async index build ----
				Platform.runLater(() -> {
					onSuccess.accept(workspace);
					// Async: build full index after UI is shown
					BackgroundTasks.run("Index-" + name, () -> {
						WorkspaceIndex fullIndex = WorkspaceIndex.build(treeRoot);
						workspace.setIndex(fullIndex);
					});
				});
				config.addRecentFile(file.getAbsolutePath());
			} catch (IOException e) {
				Platform.runLater(() -> onError.accept(e.getMessage()));
			}
		});
	}

	/** 判断文件是否为归档文件（JAR/ZIP） */
	private static boolean isArchiveFile(File file) {
		String name = file.getName().toLowerCase();
		return name.endsWith(".jar") || name.endsWith(".zip");
	}
}
