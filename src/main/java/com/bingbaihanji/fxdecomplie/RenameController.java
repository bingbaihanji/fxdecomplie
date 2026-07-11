package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import com.bingbaihanji.fxdecomplie.util.text.JavaSourceAnalyzer;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

/**
 * 重命名与反混淆控制器：光标处重命名、类重命名目标推断、标签页刷新、
 * ProGuard 映射导入/导出、反混淆预览与快照恢复。
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者（Mediator 模式）。
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/classTabOpener 延迟初始化。
 *
 * @author bingbaihanji
 */
public final class RenameController {

    private static final Logger log = LoggerFactory.getLogger(RenameController.class);

    private final MainWindow owner;

    public RenameController(MainWindow owner) {
        this.owner = owner;
    }

    /** @return true 若重命名条目允许回退到可见标识符替换(类名或通用标识符类型) */
    private static boolean allowVisibleIdentifierFallback(
            com.bingbaihanji.fxdecomplie.rename.RenameEntry entry) {
        if (entry == null || entry.type() == null) {
            return false;
        }
        return com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS.equals(entry.type())
                || com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_IDENTIFIER.equals(entry.type());
    }

    /** @return true 若重命名条目是类级别的重命名 */
    private static boolean isClassRenameEntry(
            com.bingbaihanji.fxdecomplie.rename.RenameEntry entry) {
        return entry != null
                && com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS.equals(entry.type());
    }

    public void renameAtCaret(CodeViewContext context,
                              jfx.incubator.scene.control.richtext.TextPos caret) {
        if (context == null || caret == null) {
            owner.statusBar().setFilePath("Rename unavailable: no code context");
            return;
        }
        WorkspaceView view = owner.workspaceViewFor(context.workspace());
        if (view == null) {
            owner.statusBar().setFilePath("Rename unavailable: workspace not found");
            return;
        }
        CodeEditorTab codeTab = owner.codeTabForContext(view, context);
        if (codeTab == null || codeTab.getCodeArea() == null) {
            owner.statusBar().setFilePath("Rename unavailable: code tab not found");
            return;
        }
        String text = codeTab.getCodeArea().getText();
        if (text == null || text.isEmpty()) {
            owner.statusBar().setFilePath("Rename unavailable: source is empty");
            return;
        }
        int offset = JavaSourceAnalyzer.flatOffset(text, caret);
        String caretName = com.bingbaihanji.fxdecomplie.rename.RenameService
                .identifierAt(text, offset);
        if (caretName.isBlank()) {
            owner.statusBar().setFilePath("Rename unavailable: place cursor on a Java name");
            return;
        }
        WorkspaceIndex index = context.workspace() != null && context.workspace().isIndexReady()
                ? context.workspace().getIndex() : context.workspaceIndex();
        String wsHash = CommentScope.workspaceHash(context.workspace());
        var targetOpt = com.bingbaihanji.fxdecomplie.rename.RenameService.resolveTarget(
                text, offset, context.classInternalName(), context.classBytes(), index, wsHash);
        var target = targetOpt.orElseGet(() -> new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_IDENTIFIER,
                        context.classInternalName(), caretName, caretName, ""),
                "identifier", caretName));
        target = refineClassRenameTarget(context.workspace(), context.classInternalName(),
                caretName, wsHash, target);
        target = forceCurrentClassRenameTarget(context, text, offset, caretName, wsHash, target);
        var baseEntry = target.entry();
        String oldName = target.currentName();
        repairCurrentClassDisplayMappingIfNeeded(context, codeTab, wsHash, baseEntry, oldName, text);

        // 显示对话框
        String newName = com.bingbaihanji.fxdecomplie.rename.RenameDialog
                .show(owner.stage(), oldName, target.kind());
        if (newName == null || newName.equals(oldName)) {
            owner.statusBar().setFilePath("Rename canceled: " + oldName);
            return;
        }

        com.bingbaihanji.fxdecomplie.rename.RenameEntry visibleEntry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        baseEntry.type(), baseEntry.className(), oldName, newName,
                        baseEntry.desc());
        String currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(currentRenamedSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            currentRenamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleClassDeclaration(currentRenamedSource, oldName, newName);
        }
        boolean currentTabChanged = false;
        if (!java.util.Objects.equals(currentRenamedSource, text)) {
            String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .displayClassName(codeTab.getOpenFile().fullPath(), wsHash);
            codeTab.updateSourceCode(displayClass, currentRenamedSource);
            reinstallCodeContext(codeTab, currentRenamedSource);
            currentTabChanged = true;
        }

        // 保存
        com.bingbaihanji.fxdecomplie.rename.RenameEntry entry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        baseEntry.type(), baseEntry.className(), baseEntry.oldName(),
                        newName, baseEntry.desc());
        boolean saved = com.bingbaihanji.fxdecomplie.rename.RenameService.save(wsHash,
                entry);
        String postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(postSaveSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            postSaveSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .replaceVisibleClassDeclaration(postSaveSource, oldName, newName);
        }
        String savedDisplayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), wsHash);
        String currentVisibleSource = codeTab.getCodeArea() == null
                ? codeTab.getOpenFile().sourceCode() : codeTab.getCodeArea().getText();
        if (!java.util.Objects.equals(postSaveSource, currentVisibleSource)
                || !java.util.Objects.equals(savedDisplayClass, codeTab.getOpenFile().className())) {
            codeTab.updateSourceCode(savedDisplayClass, postSaveSource);
            reinstallCodeContext(codeTab, postSaveSource);
            currentTabChanged = true;
        }

        if (context.workspace() != null) {
            context.workspace().clearSourceSearchCaches();
        }
        int changedTabs = refreshOpenTabsAfterRename(context.workspace(), wsHash, visibleEntry, codeTab);
        refreshWorkspaceTree(context.workspace());
        int totalChangedTabs = changedTabs + (currentTabChanged ? 1 : 0);
        log.info("重命名完成: type={}, class={}, old={}, new={}, changedTabs={}",
                baseEntry.type(), baseEntry.className(), oldName, newName, totalChangedTabs);
        if (totalChangedTabs == 0) {
            owner.statusBar().setFilePath((saved ? "Rename saved" : "Rename memory-only")
                    + " but no visible text changed: " + oldName + " -> " + newName);
        } else {
            owner.statusBar().setFilePath((saved ? "Renamed " : "Renamed memory-only ")
                    + target.kind() + " [" + baseEntry.type()
                    + "]: " + oldName + " -> " + newName);
        }
    }

    /** 修复当前类的重命名显示映射(当显示名与重命名映射不一致时自动修正) */
    private void repairCurrentClassDisplayMappingIfNeeded(
            CodeViewContext context, CodeEditorTab codeTab, String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameEntry baseEntry,
            String currentName, String sourceCode) {
        if (context == null || codeTab == null || baseEntry == null
                || currentName == null || currentName.isBlank()
                || !com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                .equals(baseEntry.type())) {
            return;
        }
        String currentClass = JavaSourceAnalyzer.normalizeInternalClassName(context.classInternalName());
        String entryClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(baseEntry.className(), workspaceHash);
        if (!ClassNameUtil.sameInternalName(entryClass, currentClass)) {
            return;
        }
        String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        if (currentName.equals(displayClass)) {
            return;
        }
        com.bingbaihanji.fxdecomplie.rename.RenameEntry repairEntry =
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        baseEntry.className(), baseEntry.oldName(), currentName, baseEntry.desc());
        boolean saved = com.bingbaihanji.fxdecomplie.rename.RenameService
                .save(workspaceHash, repairEntry);
        if (!saved) {
            return;
        }
        String repairedDisplay = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        codeTab.updateSourceCode(repairedDisplay, sourceCode);
        reinstallCodeContext(codeTab, sourceCode);
        if (context.workspace() != null) {
            context.workspace().clearSourceSearchCaches();
            refreshWorkspaceTree(context.workspace());
        }
        log.info("修复类显示映射: class={}, visible={}, display={}",
                baseEntry.className(), currentName, repairedDisplay);
    }

    /** 当光标位于类声明处时强制将重命名目标设为本类 */
    private com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget forceCurrentClassRenameTarget(
            CodeViewContext context, String sourceCode, int offset, String caretName,
            String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget target) {
        if (context == null || caretName == null || caretName.isBlank()
                || context.classInternalName() == null || context.classInternalName().isBlank()) {
            return target;
        }
        if (!isCurrentClassRenamePosition(context, sourceCode, offset, caretName, workspaceHash)) {
            return target;
        }
        String ownerInternal = JavaSourceAnalyzer.normalizeInternalClassName(context.classInternalName());
        String originalOwner = com.bingbaihanji.fxdecomplie.rename.RenameService
                .originalInternalName(ownerInternal, workspaceHash);
        String originalLeaf = JavaSourceAnalyzer.classLeafName(originalOwner);
        if (originalLeaf.isBlank()) {
            originalLeaf = JavaSourceAnalyzer.classLeafName(ownerInternal);
        }
        return new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        originalOwner, originalLeaf, caretName, ""),
                "class", caretName);
    }

    private boolean isCurrentClassRenamePosition(CodeViewContext context, String sourceCode,
                                                 int offset, String caretName, String workspaceHash) {
        String currentDisplay = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(context.classInternalName(), workspaceHash);
        String currentOriginal = JavaSourceAnalyzer.classLeafName(context.classInternalName());
        if (caretName.equals(currentDisplay) || caretName.equals(currentOriginal)) {
            return true;
        }
        return JavaSourceAnalyzer.isDeclaredTypeNameAt(sourceCode, offset, caretName);
    }

    private com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget refineClassRenameTarget(
            Workspace workspace, String currentClassName, String caretName,
            String workspaceHash,
            com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget target) {
        if (target == null || caretName == null || caretName.isBlank()) {
            return target;
        }
        var entry = target.entry();
        if (entry != null && com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                .equals(entry.type())) {
            return target;
        }
        String currentInternal = JavaSourceAnalyzer.normalizeInternalClassName(currentClassName);
        String currentSimple = JavaSourceAnalyzer.tokenSimpleName(currentInternal);
        FileTreeNode node = null;
        String currentDisplayName = com.bingbaihanji.fxdecomplie.rename.RenameService
                .displayClassName(currentClassName, workspaceHash);
        if (caretName.equals(currentSimple) || caretName.equals(currentDisplayName)) {
            node = workspace == null ? null : workspace.findNodeByPath(currentInternal + ".class");
        }
        if (node == null) {
            node = owner.navigationController().findNodeBySimpleNameInTree(workspace, caretName, currentClassName);
        }
        if (node == null || !node.isClassFile()) {
            return target;
        }
        String ownerPath = node.getFullPath();
        if (ownerPath.endsWith(".class")) {
            ownerPath = ownerPath.substring(0, ownerPath.length() - ".class".length());
        }
        return new com.bingbaihanji.fxdecomplie.rename.RenameService.RenameTarget(
                new com.bingbaihanji.fxdecomplie.rename.RenameEntry(
                        com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS,
                        ownerPath, caretName, caretName, ""),
                "class", caretName);
    }

    /** 重命名后刷新所有已打开的代码标签页,可指定跳过某个标签页(触发源标签页已自行更新) */
    private int refreshOpenTabsAfterRename(Workspace workspace, String workspaceHash,
                                           com.bingbaihanji.fxdecomplie.rename.RenameEntry visibleEntry,
                                           CodeEditorTab skipTab) {
        WorkspaceView view = owner.workspaceViewFor(workspace);
        if (view == null || workspaceHash == null || workspaceHash.isBlank()) {
            return 0;
        }
        int changedTabs = 0;
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (!(tab instanceof CodeEditorTab codeTab) || codeTab.getOpenFile() == null) {
                    continue;
                }
                if (skipTab != null && codeTab == skipTab) {
                    continue;
                }
                OpenFile openFile = codeTab.getOpenFile();
                String currentVisibleSource = codeTab.getCodeArea() == null
                        ? openFile.sourceCode() : codeTab.getCodeArea().getText();
                String renamedSource;
                if (visibleEntry == null) {
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applyRenames(currentVisibleSource, workspaceHash, openFile.fullPath());
                } else if (com.bingbaihanji.fxdecomplie.rename.RenameService.TYPE_CLASS
                        .equals(visibleEntry.type())) {
                    // 类重命名：仅通过 applySingleRename 应用(有包上下文检查)
                    // 不使用 replaceVisibleIdentifier(会按简单名全局替换导致跨包误匹配)
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                } else {
                    renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                    if (java.util.Objects.equals(renamedSource, currentVisibleSource)
                            && allowVisibleIdentifierFallback(visibleEntry)) {
                        renamedSource = com.bingbaihanji.fxdecomplie.rename.RenameService
                                .replaceVisibleIdentifier(currentVisibleSource,
                                        visibleEntry.oldName(), visibleEntry.newName());
                    }
                }
                if (java.util.Objects.equals(renamedSource, currentVisibleSource)) {
                    continue;
                }
                String displayClass = com.bingbaihanji.fxdecomplie.rename.RenameService
                        .displayClassName(openFile.fullPath(), workspaceHash);
                codeTab.updateSourceCode(displayClass, renamedSource);
                changedTabs++;
                reinstallCodeContext(codeTab, renamedSource);
            }
        }
        return changedTabs;
    }

    /** 在源码更新后重新安装代码标签页的上下文菜单(Ctrl+Click 导航和右键菜单) */
    private void reinstallCodeContext(CodeEditorTab codeTab, String sourceCode) {
        if (codeTab == null) {
            return;
        }
        var panel = codeTab.getCodeViewPanel();
        CodeViewContext oldCtx = panel == null ? null : panel.getContextMenuContext();
        CodeActionHandler handler = panel == null ? null : panel.getContextMenuHandler();
        if (oldCtx != null && handler != null) {
            String sourceHash = CommentExportDecorator.sourceHash(sourceCode);
            CodeViewContext newCtx = new CodeViewContext(oldCtx.workspace(), oldCtx.node(),
                    codeTab.getOpenFile(), oldCtx.classBytes(), codeTab.getMetadata(),
                    oldCtx.workspaceIndex(), oldCtx.workspaceHash(), sourceHash,
                    oldCtx.optionsHash());
            panel.installContextMenu(newCtx, handler);
        }
    }

    /**
     * 刷新工作区文件树,确保所有可见 cell 的显示名与当前重命名状态一致
     *
     * <p>JavaFX {@code Node.refresh()} 仅重新应用 CSS,不会触发 TreeCell 的
     * {@code updateItem}FileTreeView.refreshVisibleCells() 通过重建 cell factory
     * 强制 VirtualFlow 重建所有可见 cell,从而触发 updateItem 获取最新显示名</p>
     */
    private void refreshWorkspaceTree(Workspace workspace) {
        WorkspaceView view = owner.workspaceViewFor(workspace);
        if (view != null && view.treeView() != null) {
            view.treeView().refreshVisibleCells();
        }
    }

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
                java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> suggestions =
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
                    DialogHelper.showError(owner.stage(), I18nUtil.getString("dialog.error.title"),
                            "Deobfuscate failed: " + ex.getMessage());
                });
            }
        });
    }

    /** 显示反混淆预览对话框,提交用户选择的重命名条目并刷新所有相关标签页 */
    private void showDeobfuscatePreview(Workspace workspace,
                                        java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> suggestions,
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
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> selected =
                com.bingbaihanji.fxdecomplie.rename.DeobfuscatePreviewDialog.show(owner.stage(), suggestions);
        log.info("showDeobfuscatePreview: dialog returned {} selected entries", selected.size());
        if (selected.isEmpty()) {
            owner.statusBar().setFilePath("Deobfuscate cancelled or no entries selected");
            return;
        }
        int saved = com.bingbaihanji.fxdecomplie.rename.RenameService.saveAll(wsHash, selected, true);
        if (saved == 0) {
            owner.statusBar().setFilePath("Deobfuscate failed: rename mapping was not saved");
            DialogHelper.showError(owner.stage(), I18nUtil.getString("dialog.error.title"),
                    "Deobfuscate failed: rename mapping was not saved.");
            return;
        }
        if (saved != selected.size()) {
            log.warn("反混淆批量保存未完全成功: selected={}, saved={}", selected.size(), saved);
        }
        if (workspace != null) {
            workspace.clearSourceSearchCaches();
        }
        int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
        refreshWorkspaceTree(workspace);
        owner.statusBar().setFilePath("Deobfuscated: " + selected.size()
                + " selected, " + saved + " saved, " + reloadTabs + " tabs reload scheduled"
                + (memberScanComplete ? "" : "; index still building for member names"));
    }

    public void importProGuardMapping() {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            DialogHelper.showWarning(owner.stage(), "Import ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
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
            java.util.List<com.bingbaihanji.fxdecomplie.rename.RenameEntry> entries =
                    com.bingbaihanji.fxdecomplie.rename.RenameService.parseProGuardMapping(text);
            if (entries.isEmpty()) {
                DialogHelper.showWarning(owner.stage(), "Import ProGuard Mapping", "No mapping entries found.");
                return;
            }
            Workspace workspace = view.workspace();
            String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                    .of(workspace, "").workspaceHash();
            int saved = com.bingbaihanji.fxdecomplie.rename.RenameService.saveAll(wsHash, entries, true);
            if (saved == 0) {
                DialogHelper.showError(owner.stage(), "Import ProGuard Mapping", "No mapping entries could be saved.");
                return;
            }
            workspace.clearSourceSearchCaches();
            int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
            refreshWorkspaceTree(workspace);
            owner.statusBar().setFilePath("Imported mapping: " + saved + " saved, "
                    + reloadTabs + " tabs reload scheduled");
        } catch (java.io.IOException e) {
            log.error("导入 ProGuard mapping 失败", e);
            DialogHelper.showError(owner.stage(), "Import ProGuard Mapping", e.getMessage());
        }
    }

    public void exportProGuardMapping() {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            DialogHelper.showWarning(owner.stage(), "Export ProGuard Mapping", I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(view.workspace(), "").workspaceHash();
        String mapping = com.bingbaihanji.fxdecomplie.rename.RenameService.exportProGuard(wsHash);
        if (mapping.isBlank()) {
            DialogHelper.showWarning(owner.stage(), "Export ProGuard Mapping", "No rename mappings found.");
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
            DialogHelper.showError(owner.stage(), "Export ProGuard Mapping", e.getMessage());
        }
    }

    public void restoreLastRenameSnapshot() {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            DialogHelper.showWarning(owner.stage(), "Restore Rename Snapshot", I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        Workspace workspace = view.workspace();
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(workspace, "").workspaceHash();
        boolean restored = com.bingbaihanji.fxdecomplie.rename.RenameService
                .restoreLatestBackup(wsHash);
        if (!restored) {
            owner.statusBar().setFilePath("No rename snapshot found");
            DialogHelper.showWarning(owner.stage(), "Restore Rename Snapshot", "No rename snapshot found for current workspace.");
            return;
        }
        workspace.clearSourceSearchCaches();
        int reloadTabs = reloadOpenTabsAfterDeobfuscate(workspace);
        refreshWorkspaceTree(workspace);
        owner.statusBar().setFilePath("Rename snapshot restored, " + reloadTabs + " tabs reloaded");
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
