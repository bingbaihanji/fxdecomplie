package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import com.bingbaihanji.fxdecomplie.util.text.JavaSourceAnalyzer;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 光标处重命名控制器：光标处标识符/类重命名、类重命名目标推断、重命名后标签页与文件树刷新。
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者（Mediator 模式）。
 * 反混淆/ProGuard 映射/快照恢复已拆分至 {@link DeobfuscationController}。
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
            RenameEntry entry) {
        if (entry == null || entry.type() == null) {
            return false;
        }
        return RenameService.TYPE_CLASS.equals(entry.type())
                || RenameService.TYPE_IDENTIFIER.equals(entry.type());
    }

    /** @return true 若重命名条目是类级别的重命名 */
    private static boolean isClassRenameEntry(
            RenameEntry entry) {
        return entry != null
                && RenameService.TYPE_CLASS.equals(entry.type());
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
        String caretName = RenameService
                .identifierAt(text, offset);
        if (caretName.isBlank()) {
            owner.statusBar().setFilePath("Rename unavailable: place cursor on a Java name");
            return;
        }
        WorkspaceIndex index = context.workspace() != null && context.workspace().isIndexReady()
                ? context.workspace().getIndex() : context.workspaceIndex();
        String wsHash = CommentScope.workspaceHash(context.workspace());
        var targetOpt = RenameService.resolveTarget(
                text, offset, context.classInternalName(), context.classBytes(), index, wsHash);
        var target = targetOpt.orElseGet(() -> new RenameService.RenameTarget(
                new RenameEntry(
                        RenameService.TYPE_IDENTIFIER,
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

        RenameEntry visibleEntry =
                new RenameEntry(
                        baseEntry.type(), baseEntry.className(), oldName, newName,
                        baseEntry.desc());
        String currentRenamedSource = RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(currentRenamedSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            currentRenamedSource = RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            currentRenamedSource = RenameService
                    .replaceVisibleClassDeclaration(currentRenamedSource, oldName, newName);
        }
        boolean currentTabChanged = false;
        if (!java.util.Objects.equals(currentRenamedSource, text)) {
            String displayClass = RenameService
                    .displayClassName(codeTab.getOpenFile().fullPath(), wsHash);
            codeTab.updateSourceCode(displayClass, currentRenamedSource);
            reinstallCodeContext(codeTab, currentRenamedSource);
            currentTabChanged = true;
        }

        // 保存
        RenameEntry entry =
                new RenameEntry(
                        baseEntry.type(), baseEntry.className(), baseEntry.oldName(),
                        newName, baseEntry.desc());
        boolean saved = RenameService.save(wsHash,
                entry);
        String postSaveSource = RenameService
                .applySingleRename(text, visibleEntry, codeTab.getOpenFile().fullPath(), wsHash);
        if (java.util.Objects.equals(postSaveSource, text)
                && allowVisibleIdentifierFallback(visibleEntry)) {
            postSaveSource = RenameService
                    .replaceVisibleIdentifier(text, oldName, newName);
        }
        if (isClassRenameEntry(visibleEntry)) {
            postSaveSource = RenameService
                    .replaceVisibleClassDeclaration(postSaveSource, oldName, newName);
        }
        String savedDisplayClass = RenameService
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
        owner.refreshWorkspaceTree(context.workspace());
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
            RenameEntry baseEntry,
            String currentName, String sourceCode) {
        if (context == null || codeTab == null || baseEntry == null
                || currentName == null || currentName.isBlank()
                || !RenameService.TYPE_CLASS
                .equals(baseEntry.type())) {
            return;
        }
        String currentClass = JavaSourceAnalyzer.normalizeInternalClassName(context.classInternalName());
        String entryClass = RenameService
                .originalInternalName(baseEntry.className(), workspaceHash);
        if (!ClassNameUtil.sameInternalName(entryClass, currentClass)) {
            return;
        }
        String displayClass = RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        if (currentName.equals(displayClass)) {
            return;
        }
        RenameEntry repairEntry =
                new RenameEntry(
                        RenameService.TYPE_CLASS,
                        baseEntry.className(), baseEntry.oldName(), currentName, baseEntry.desc());
        boolean saved = RenameService
                .save(workspaceHash, repairEntry);
        if (!saved) {
            return;
        }
        String repairedDisplay = RenameService
                .displayClassName(codeTab.getOpenFile().fullPath(), workspaceHash);
        codeTab.updateSourceCode(repairedDisplay, sourceCode);
        reinstallCodeContext(codeTab, sourceCode);
        if (context.workspace() != null) {
            context.workspace().clearSourceSearchCaches();
            owner.refreshWorkspaceTree(context.workspace());
        }
        log.info("修复类显示映射: class={}, visible={}, display={}",
                baseEntry.className(), currentName, repairedDisplay);
    }

    /** 当光标位于类声明处时强制将重命名目标设为本类 */
    private RenameService.RenameTarget forceCurrentClassRenameTarget(
            CodeViewContext context, String sourceCode, int offset, String caretName,
            String workspaceHash,
            RenameService.RenameTarget target) {
        if (context == null || caretName == null || caretName.isBlank()
                || context.classInternalName() == null || context.classInternalName().isBlank()) {
            return target;
        }
        if (!isCurrentClassRenamePosition(context, sourceCode, offset, caretName, workspaceHash)) {
            return target;
        }
        String ownerInternal = JavaSourceAnalyzer.normalizeInternalClassName(context.classInternalName());
        String originalOwner = RenameService
                .originalInternalName(ownerInternal, workspaceHash);
        String originalLeaf = JavaSourceAnalyzer.classLeafName(originalOwner);
        if (originalLeaf.isBlank()) {
            originalLeaf = JavaSourceAnalyzer.classLeafName(ownerInternal);
        }
        return new RenameService.RenameTarget(
                new RenameEntry(
                        RenameService.TYPE_CLASS,
                        originalOwner, originalLeaf, caretName, ""),
                "class", caretName);
    }

    private boolean isCurrentClassRenamePosition(CodeViewContext context, String sourceCode,
                                                 int offset, String caretName, String workspaceHash) {
        String currentDisplay = RenameService
                .displayClassName(context.classInternalName(), workspaceHash);
        String currentOriginal = JavaSourceAnalyzer.classLeafName(context.classInternalName());
        if (caretName.equals(currentDisplay) || caretName.equals(currentOriginal)) {
            return true;
        }
        return JavaSourceAnalyzer.isDeclaredTypeNameAt(sourceCode, offset, caretName);
    }

    private RenameService.RenameTarget refineClassRenameTarget(
            Workspace workspace, String currentClassName, String caretName,
            String workspaceHash,
            RenameService.RenameTarget target) {
        if (target == null || caretName == null || caretName.isBlank()) {
            return target;
        }
        var entry = target.entry();
        if (entry != null && RenameService.TYPE_CLASS
                .equals(entry.type())) {
            return target;
        }
        String currentInternal = JavaSourceAnalyzer.normalizeInternalClassName(currentClassName);
        String currentSimple = JavaSourceAnalyzer.tokenSimpleName(currentInternal);
        FileTreeNode node = null;
        String currentDisplayName = RenameService
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
        return new RenameService.RenameTarget(
                new RenameEntry(
                        RenameService.TYPE_CLASS,
                        ownerPath, caretName, caretName, ""),
                "class", caretName);
    }

    /** 重命名后刷新所有已打开的代码标签页,可指定跳过某个标签页(触发源标签页已自行更新) */
    private int refreshOpenTabsAfterRename(Workspace workspace, String workspaceHash,
                                           RenameEntry visibleEntry,
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
                    renamedSource = RenameService
                            .applyRenames(currentVisibleSource, workspaceHash, openFile.fullPath());
                } else if (RenameService.TYPE_CLASS
                        .equals(visibleEntry.type())) {
                    // 类重命名：仅通过 applySingleRename 应用(有包上下文检查)
                    // 不使用 replaceVisibleIdentifier(会按简单名全局替换导致跨包误匹配)
                    renamedSource = RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                } else {
                    renamedSource = RenameService
                            .applySingleRename(currentVisibleSource, visibleEntry,
                                    openFile.fullPath(), workspaceHash);
                    if (java.util.Objects.equals(renamedSource, currentVisibleSource)
                            && allowVisibleIdentifierFallback(visibleEntry)) {
                        renamedSource = RenameService
                                .replaceVisibleIdentifier(currentVisibleSource,
                                        visibleEntry.oldName(), visibleEntry.newName());
                    }
                }
                if (java.util.Objects.equals(renamedSource, currentVisibleSource)) {
                    continue;
                }
                String displayClass = RenameService
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

}
