package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.decompiler.ClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.model.ExportConfig;
import com.bingbaihanji.fxdecomplie.model.ExportResult;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.ExportService;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceClassBytecodeProvider;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.export.ExportDialog;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 导出控制器:批量导出所有类 右键子树导出 导出配置持久化 
 * 导出进度/结果对话框及打开输出目录
 * <p>
 * 从 MainWindow 拆分而来,通过 owner 访问共享状态与协作者 (Mediator 模式)
 * 所有协作者均在调用时通过 owner 访问,以适应 tabManager/classTabOpener 延迟初始化
 *
 * @author bingbaihanji
 */
public final class ExportController {

    private static final Logger log = LoggerFactory.getLogger(ExportController.class);

    private final MainWindow owner;

    public ExportController(MainWindow owner) {
        this.owner = owner;
    }

    /** 批量导出所有类为 .java 文件 */
    public void exportAllFiles() {
        WorkspaceView view = owner.requireWorkspaceOrWarn(I18nUtil.getString("dialog.export.title"),
                I18nUtil.getString("dialog.export.noworkspace"));
        if (view == null) {
            return;
        }
        Workspace workspace = view.workspace();
        // 不再等待完整索引:依赖类在导出过程中按需解析
        BackgroundTasks.run("Export-CollectNodes", () -> {
            List<FileTreeNode> nodes = new ArrayList<>();
            owner.collectTreeNodes(workspace.getTreeRoot(), nodes);
            Platform.runLater(() -> doExport(nodes,
                    new WorkspaceClassBytecodeProvider(workspace), workspace));
        });
    }

    /** 为单个树节点构建导出任务（右键菜单入口，不等待完整索引） */
    public void exportTreeItem(TreeItem<FileTreeNode> rootItem) {
        if (rootItem == null || rootItem.getValue() == null) {
            return;
        }
        WorkspaceView exportView = owner.tabManager().currentWorkspaceView();
        Workspace exportWorkspace = exportView == null ? null : exportView.workspace();
        owner.statusBar().setTask(I18nUtil.getString("task.exporting"));
        BackgroundTasks.run("Export-CollectNodes", () -> {
            try {
                List<FileTreeNode> nodesSnapshot = new ArrayList<>();
                collectTreeNodes(rootItem, nodesSnapshot);
                ClassBytecodeProvider provider;
                if (exportWorkspace == null) {
                    WorkspaceIndex snapshotIndex = WorkspaceIndex.build(nodesSnapshot);
                    provider = snapshotIndex::getClassBytes;
                } else {
                    provider = new WorkspaceClassBytecodeProvider(exportWorkspace);
                }
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    doExport(nodesSnapshot, provider, exportWorkspace);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    owner.showError(I18nUtil.getString("dialog.index.failed", ex.getMessage()));
                });
            }
        });
    }

    /** 弹出导出配置对话框,提交后台导出任务并显示进度条,支持取消操作 */
    private void doExport(List<FileTreeNode> nodes,
                          ClassBytecodeProvider provider, Workspace workspace) {
        var configOpt = ExportDialog.show(owner.stage(), owner.config(), owner.currentEngine());
        if (configOpt.isEmpty()) {
            return;
        }
        ExportConfig exportConfig = configOpt.get();
        persistExportConfig(exportConfig);

        ExportDialog.ProgressHandle progressHandle = ExportDialog.showProgress(owner.stage());
        AtomicReference<Future<?>> exportTaskRef = new AtomicReference<>();
        AtomicBoolean exportCanceled = new AtomicBoolean(false);
        // 必须在提交任务前注册取消回调,避免用户点击取消时 exportTaskRef 尚未设置
        progressHandle.setOnCancel(() -> {
            exportCanceled.set(true);
            Future<?> f = exportTaskRef.get();
            if (f != null) {
                BackgroundTasks.cancel(f);
            }
        });
        owner.statusBar().setTask(I18nUtil.getString("task.exporting"));
        exportTaskRef.set(BackgroundTasks.run(BackgroundTasks.PoolType.EXPORT, "Export", () -> {
            try {
                ExportResult result = ExportService.exportAll(
                        nodes, exportConfig, provider, owner.commentScope(workspace, exportConfig),
                        (path, pct) -> Platform.runLater(() -> {
                            owner.statusBar().setFilePath(I18nUtil.getString(
                                    "status.exporting.detail", pct, path));
                            progressHandle.update(path, pct);
                        }),
                        (java.util.function.BooleanSupplier) () -> exportCanceled.get());
                // 立即快照,避免 Platform.runLater 延迟执行期间被用户点取消覆盖
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    owner.statusBar().clearTask();
                    if (cancelled) {
                        owner.statusBar().setFilePath(I18nUtil.getString("dialog.export.canceled"));
                        owner.showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                        return;
                    }
                    owner.statusBar().setFilePath(I18nUtil.getString(
                            "status.exportDone", exportConfig.outputPath()));
                    showExportResult(exportConfig, result);
                });
            } catch (java.io.IOException ex) {
                log.error("导出失败", ex);
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    owner.statusBar().clearTask();
                    if (cancelled) {
                        owner.statusBar().setFilePath(I18nUtil.getString("dialog.export.canceled"));
                        owner.showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                        return;
                    }
                    owner.showError(I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                });
            } catch (Exception ex) {
                log.error("导出失败", ex);
                final boolean cancelled = exportCanceled.get();
                Platform.runLater(() -> {
                    progressHandle.close();
                    owner.statusBar().clearTask();
                    if (cancelled) {
                        owner.showWarning(I18nUtil.getString("dialog.export.title"),
                                I18nUtil.getString("dialog.export.canceled"));
                    } else {
                        owner.showError(I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                    }
                });
            }
        }, rejected -> Platform.runLater(() -> {
            exportCanceled.set(true);
            progressHandle.close();
            owner.statusBar().clearTask();
            owner.showError("Export task rejected: background queue is full");
        })));
    }

    /** 将导出对话框中选择的选项写回全局配置,下次打开时记住 */
    private void persistExportConfig(ExportConfig exportConfig) {
        boolean followCurrent = owner.config().export().defaultEngine().isBlank();
        if (!followCurrent || exportConfig.engine() != owner.currentEngine()) {
            owner.config().export().defaultEngine(exportConfig.engine().name());
        }
        owner.config().export().defaultFormat(exportConfig.format().name());
        owner.config().export().conflictPolicy(exportConfig.conflictPolicy().name());
        owner.config().export().exportResources(exportConfig.exportResources());
        owner.config().export().lastPath(exportConfig.outputPath().toString());
        owner.config().save();
    }

    /** 根据导出结果弹出成功/部分成功对话框,支持打开输出目录和复制错误详情列表 */
    private void showExportResult(ExportConfig exportConfig, ExportResult result) {
        if (!result.hasErrors()) {
            showExportDoneDialog(I18nUtil.getString("dialog.export.success.title"),
                    I18nUtil.getString("dialog.export.success.message",
                            result.successCount(), exportConfig.outputPath()),
                    exportConfig.outputPath());
            return;
        }

        int limit = Math.min(8, result.errors().size());
        StringBuilder message = new StringBuilder(I18nUtil.getString(
                "dialog.export.partial.message",
                result.successCount(), result.totalFiles(), result.failedCount()));
        message.append(System.lineSeparator()).append(System.lineSeparator());
        for (int i = 0; i < limit; i++) {
            message.append("- ").append(result.errors().get(i)).append(System.lineSeparator());
        }
        if (result.errors().size() > limit) {
            message.append(I18nUtil.getString(
                    "dialog.export.moreErrors", result.errors().size() - limit));
        }
        showExportDoneDialog(I18nUtil.getString("dialog.export.partial.title"),
                message.toString(), exportConfig.outputPath(), result.errors());
    }

    /** 递归收集树节点数据(在 FX 线程调用,避免后台线程访问 TreeItem) */
    private void collectTreeNodes(TreeItem<FileTreeNode> item,
                                  List<FileTreeNode> result) {
        FileTreeNode data = item.getValue();
        if (data != null) {
            result.add(data);
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectTreeNodes(child, result);
        }
    }

    /** 显示导出成功对话框(无错误详情列表) */
    private void showExportDoneDialog(String title, String message, Path outputPath) {
        showExportDoneDialog(title, message, outputPath, List.of());
    }

    /** 显示导出完成对话框(含错误详情列表及打开输出目录/复制详情按钮) */
    private void showExportDoneDialog(String title, String message, Path outputPath,
                                      List<String> details) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner.stage());
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        DialogHelper.applyNativeStyle(alert);
        ButtonType openOutput = new ButtonType(I18nUtil.getString("dialog.export.openOutput"));
        alert.getButtonTypes().add(openOutput);
        log.info("显示导出完成对话框: {}", message);
        if (details != null && !details.isEmpty()) {
            ButtonType copyDetails = new ButtonType(I18nUtil.getString("dialog.copyDetails"));
            alert.getButtonTypes().add(copyDetails);
            alert.showAndWait().ifPresent(button -> {
                if (button == openOutput) {
                    openOutputLocation(outputPath);
                } else if (button == copyDetails) {
                    EditorActionsController.copyToClipboard(String.join(System.lineSeparator(), details));
                }
            });
            return;
        }
        alert.showAndWait().ifPresent(button -> {
            if (button == openOutput) {
                openOutputLocation(outputPath);
            }
        });
    }

    /** 使用操作系统资源管理器打开导出目录(依赖 HostServices) */
    private void openOutputLocation(Path outputPath) {
        if (outputPath == null) {
            return;
        }
        Path target = Files.isDirectory(outputPath)
                ? outputPath : outputPath.getParent();
        if (target != null && owner.hostServices() != null) {
            owner.hostServices().showDocument(target.toUri().toString());
        }
    }
}
