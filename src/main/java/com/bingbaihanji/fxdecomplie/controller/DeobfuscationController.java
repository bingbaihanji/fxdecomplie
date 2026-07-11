package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * 反混淆与 ProGuard 映射控制器：自动反混淆扫描与预览、ProGuard 映射导入/导出、重命名快照恢复
 * <p>
 * 从 RenameController 拆分而来，通过 owner 访问共享状态与协作者 (Mediator 模式)
 * 与 RenameController 共用 {@code owner.refreshWorkspaceTree(...)} 刷新文件树
 *
 * @author bingbaihanji
 */
public final class DeobfuscationController {

    private static final Logger log = LoggerFactory.getLogger(DeobfuscationController.class);

    private final MainWindow owner;

    public DeobfuscationController(MainWindow owner) {
        this.owner = owner;
    }

    /** 自动反混淆：后台扫描混淆名称,在 FX 线程弹出预览对话框 */
    public void deobfuscate() {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            return;
        }
        Workspace workspace = view.workspace();
        log.info("deobfuscate: starting, workspace={}", workspace.getName());
        owner.statusBar().setTask("Deobfuscating");
        owner.statusBar().setFilePath("Scanning obfuscated names...");
        // 将树遍历和扫描移至后台线程,避免阻塞 FX 线程
        BackgroundTasks.run("Deobfuscate-Scan", () -> {
            try {
                java.util.List<FileTreeNode> nodesSnapshot = new java.util.ArrayList<>();
                owner.collectTreeNodes(workspace.getTreeRoot(), nodesSnapshot);
                long classNodeCount = nodesSnapshot.stream().filter(FileTreeNode::isClassFile).count();
                WorkspaceIndex index = workspace.isIndexReady()
                        ? workspace.getIndex()
                        : WorkspaceIndex.EMPTY;
                java.util.List<RenameEntry> suggestions =
                        com.bingbaihanji.fxdecomplie.rename.AutoDeobfuscator.scan(nodesSnapshot, index);
                boolean memberScanComplete = index != WorkspaceIndex.EMPTY;
                log.info("deobfuscate: scan returned {} suggestions (nodes={}, memberScanComplete={})",
                        suggestions.size(), nodesSnapshot.size(), memberScanComplete);
                if (!memberScanComplete) {
                    WorkspaceIndexService.ensureIndexingStarted(workspace);
                }
                Platform.runLater(() ->
                        showDeobfuscatePreview(workspace, suggestions, memberScanComplete, classNodeCount));
            } catch (Exception ex) {
                log.error("反混淆扫描失败", ex);
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    owner.showError("Deobfuscate failed: " + ex.getMessage());
                });
            }
        });
    }

    /** 显示反混淆预览对话框,提交用户选择的重命名条目并刷新所有相关标签页 */
    private void showDeobfuscatePreview(Workspace workspace,
                                        java.util.List<RenameEntry> suggestions,
                                        boolean memberScanComplete,
                                        long classNodeCount) {
        owner.statusBar().clearTask();
        if (suggestions == null || suggestions.isEmpty()) {
            com.bingbaihanji.fxdecomplie.rename.DeobfuscatePreviewDialog.show(owner.stage(), List.of());
            if (!memberScanComplete) {
                owner.statusBar().setFilePath("No obfuscated names found in " + classNodeCount
                        + " class nodes. Indexing continues for fields and methods.");
            }
            return;
        }
        log.info("showDeobfuscatePreview: {} suggestions to show", suggestions.size());
        String wsHash = CommentScope.workspaceHash(workspace);
        java.util.List<RenameEntry> selected =
                com.bingbaihanji.fxdecomplie.rename.DeobfuscatePreviewDialog.show(owner.stage(), suggestions);
        log.info("showDeobfuscatePreview: dialog returned {} selected entries", selected.size());
        if (selected.isEmpty()) {
            owner.statusBar().setFilePath("Deobfuscate cancelled or no entries selected");
            return;
        }
        int saved = RenameService.saveAll(wsHash, selected, true);
        if (saved == 0) {
            owner.statusBar().setFilePath("Deobfuscate failed: rename mapping was not saved");
            owner.showError("Deobfuscate failed: rename mapping was not saved.");
            return;
        }
        if (saved != selected.size()) {
            log.warn("反混淆批量保存未完全成功: selected={}, saved={}", selected.size(), saved);
        }
        if (workspace != null) {
            workspace.clearSourceSearchCaches();
        }
        int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
        owner.refreshWorkspaceTree(workspace);
        owner.statusBar().setFilePath("Deobfuscated: " + selected.size()
                + " selected, " + saved + " saved, " + reloadTabs + " tabs reload scheduled"
                + (memberScanComplete ? "" : "; index still building for member names"));
    }

    /** 导入 ProGuard 映射文件并应用为重命名条目 */
    public void importProGuardMapping() {
        WorkspaceView view = owner.requireWorkspaceOrWarn("Import ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
        if (view == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import ProGuard Mapping");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Mapping Files", "*.txt", "*.map"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showOpenDialog(owner.stage());
        if (file == null) {
            return;
        }
        try {
            String text = java.nio.file.Files.readString(file.toPath());
            java.util.List<RenameEntry> entries =
                    RenameService.parseProGuardMapping(text);
            if (entries.isEmpty()) {
                owner.showWarning("Import ProGuard Mapping", "No mapping entries found.");
                return;
            }
            Workspace workspace = view.workspace();
            String wsHash = CommentScope.workspaceHash(workspace);
            int saved = RenameService.saveAll(wsHash, entries, true);
            if (saved == 0) {
                owner.showError("Import ProGuard Mapping", "No mapping entries could be saved.");
                return;
            }
            int reloadTabs = reloadAndRefreshAfterRename(workspace);
            owner.statusBar().setFilePath("Imported mapping: " + saved + " saved, "
                    + reloadTabs + " tabs reload scheduled");
        } catch (java.io.IOException e) {
            log.error("导入 ProGuard mapping 失败", e);
            owner.showError("Import ProGuard Mapping", e.getMessage());
        }
    }

    /** 将当前工作区的重命名映射导出为 ProGuard 映射文件 */
    public void exportProGuardMapping() {
        WorkspaceView view = owner.requireWorkspaceOrWarn("Export ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
        if (view == null) {
            return;
        }
        String wsHash = CommentScope.workspaceHash(view.workspace());
        String mapping = RenameService.exportProGuard(wsHash);
        if (mapping.isBlank()) {
            owner.showWarning("Export ProGuard Mapping", "No rename mappings found.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export ProGuard Mapping");
        chooser.setInitialFileName("mapping.txt");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Mapping Files", "*.txt"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        File file = chooser.showSaveDialog(owner.stage());
        if (file == null) {
            return;
        }
        try {
            java.nio.file.Files.writeString(file.toPath(), mapping);
            owner.statusBar().setFilePath("Exported mapping: " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            log.error("导出 ProGuard mapping 失败", e);
            owner.showError("Export ProGuard Mapping", e.getMessage());
        }
    }

    /** 恢复当前工作区最近一次重命名快照 */
    public void restoreLastRenameSnapshot() {
        WorkspaceView view = owner.requireWorkspaceOrWarn("Restore Rename Snapshot", I18nUtil.getString("dialog.export.noworkspace"));
        if (view == null) {
            return;
        }
        Workspace workspace = view.workspace();
        String wsHash = CommentScope.workspaceHash(workspace);
        boolean restored = RenameService
                .restoreLatestBackup(wsHash);
        if (!restored) {
            owner.statusBar().setFilePath("No rename snapshot found");
            owner.showWarning("Restore Rename Snapshot", "No rename snapshot found for current workspace.");
            return;
        }
        int reloadTabs = reloadAndRefreshAfterRename(workspace);
        owner.statusBar().setFilePath("Rename snapshot restored, " + reloadTabs + " tabs reloaded");
    }

    /** 反混淆/重命名后统一刷新：清缓存 + 重载已开标签 + 刷新文件树@return 受影响的标签数 */
    private int reloadAndRefreshAfterRename(Workspace workspace) {
        workspace.clearSourceSearchCaches();
        int changed = reloadOpenTabsAfterDeobfuscate(workspace);
        owner.refreshWorkspaceTree(workspace);
        return changed;
    }

    /** 反混淆后重新反编译所有已打开的代码标签页以应用新的重命名映射 */
    private int reloadOpenTabsAfterDeobfuscate(Workspace workspace) {
        WorkspaceView view = owner.workspaceViewFor(workspace);
        if (view == null) {
            return 0;
        }
        owner.classTabOpener().cancelCurrentTask();
        java.util.List<java.util.Map.Entry<TabPane, CodeEditorTab>> tabs = new java.util.ArrayList<>();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab && codeTab.getOpenFile() != null) {
                    tabs.add(java.util.Map.entry(pane, codeTab));
                }
            }
        }
        for (var item : tabs) {
            owner.classTabOpener().refreshCurrentClassTab(workspace, item.getKey(), item.getValue(),
                    item.getValue().getOpenFile().engine(), owner.lineNumbersEnabled(), false, false);
        }
        return tabs.size();
    }
}
