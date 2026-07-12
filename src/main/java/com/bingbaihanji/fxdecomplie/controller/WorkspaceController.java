package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.DecompilerProject;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.service.ExportService;
import com.bingbaihanji.fxdecomplie.service.ProjectFileManager;
import com.bingbaihanji.fxdecomplie.service.WorkspaceLoader;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.CodeOnlyWindow;
import com.bingbaihanji.fxdecomplie.ui.quickopen.QuickOpenDialog;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作区/文件/项目生命周期控制器：打开文件/目录/项目、保存项目、
 * 关闭工作区、保存当前文件、树中定位、快速打开类、最近文件、新窗口及异步工作区加载
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者 (Mediator 模式)
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/classTabOpener 延迟初始化
 *
 * @author bingbaihanji
 */
public final class WorkspaceController {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceController.class);

    private final MainWindow owner;

    public WorkspaceController(MainWindow owner) {
        this.owner = owner;
    }

    /** 将项目文件中保存的引擎名字符串还原为枚举值,非法值回退到默认引擎 */
    private static DecompilerTypeEnum parseEngine(String value, DecompilerTypeEnum fallback) {
        try {
            return DecompilerTypeEnum.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback == null ? DecompilerTypeEnum.JADX : fallback;
        }
    }

    /** 打开 JAR/ZIP/Class 文件 */
    public void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("dialog.openFile.title"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Java Archives & Class Files", "*.jar", "*.zip", "*.class"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(owner.stage());
        if (file != null) {
            loadFile(file);
        }
    }

    /** 打开目录 */
    public void openDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(I18nUtil.getString("dialog.openDir.title"));
        File dir = chooser.showDialog(owner.stage());
        if (dir != null) {
            loadFile(dir);
        }
    }

    /** 打开项目文件 */
    public void openProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("project.open"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("FxDecompiler Project", "*.fxdproj"));
        File file = chooser.showOpenDialog(owner.stage());
        if (file == null) {
            return;
        }
        try {
            DecompilerProject project = ProjectFileManager.load(file.toPath());
            owner.setCurrentEngine(parseEngine(project.engine(), owner.currentEngine()));
            owner.config().decompiler().defaultEngine(owner.currentEngine());
            if (!project.exportPath().isBlank()) {
                owner.config().export().lastPath(project.exportPath());
            }
            for (String inputPath : project.inputPaths()) {
                File input = new File(inputPath);
                if (input.exists()) {
                    loadFile(input);
                }
            }
            owner.statusBar().setFilePath(I18nUtil.getString("project.opened", file.getAbsolutePath()));
        } catch (IOException ex) {
            owner.showError(I18nUtil.getString("project.open.failed", ex.getMessage()));
        }
    }

    /** 保存当前项目文件 */
    public void saveProject() {
        List<String> inputPaths = new ArrayList<>();
        String selectedPath = "";
        Tab selected = owner.outerTabPane().getSelectionModel().getSelectedItem();
        for (Tab tab : owner.outerTabPane().getTabs()) {
            WorkspaceView view = owner.tabManager().getWorkspaceViews().get(tab);
            if (view == null) {
                continue;
            }
            String path = view.workspace().getSourceFile().getAbsolutePath();
            inputPaths.add(path);
            if (tab == selected) {
                selectedPath = path;
            }
        }
        if (inputPaths.isEmpty()) {
            owner.showWarning(I18nUtil.getString("project.save"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("project.save"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("FxDecompiler Project", "*.fxdproj"));
        chooser.setInitialFileName("workspace.fxdproj");
        File file = chooser.showSaveDialog(owner.stage());
        if (file == null) {
            return;
        }
        try {
            ProjectFileManager.save(file.toPath(), new DecompilerProject(
                    1, owner.currentEngine().name(), inputPaths, selectedPath, owner.config().export().lastPath()));
            owner.statusBar().setFilePath(I18nUtil.getString("project.saved", file.getAbsolutePath()));
        } catch (IOException ex) {
            owner.showError(I18nUtil.getString("project.save.failed", ex.getMessage()));
        }
    }

    /** 关闭当前选中的工作区标签页(含解编译结果和文件树) */
    public void closeCurrentWorkspace() {
        Tab selected = owner.outerTabPane().getSelectionModel().getSelectedItem();
        if (selected != null && owner.tabManager().getWorkspaceViews().containsKey(selected)) {
            owner.tabManager().requestCloseWorkspaceTab(selected);
        }
    }

    /** 关闭除当前选中之外的所有工作区标签页 */
    public void closeOtherWorkspaces() {
        Tab selected = owner.outerTabPane().getSelectionModel().getSelectedItem();
        if (selected == null || !owner.tabManager().getWorkspaceViews().containsKey(selected)) {
            return;
        }
        owner.tabManager().requestCloseOtherWorkspaces(selected);
    }

    /** 保存当前代码标签页为 .java 文件 */
    public void saveCurrentFile() {
        CodeEditorTab codeTab = owner.tabManager().currentCodeTab();
        if (codeTab == null) {
            owner.showWarning(I18nUtil.getString("dialog.save.title"), I18nUtil.getString("dialog.save.nofile"));
            return;
        }
        if (!codeTab.isSourceReady()) {
            owner.showWarning(I18nUtil.getString("dialog.save.title"), I18nUtil.getString("dialog.save.pending"));
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("dialog.saveFile.title"));
        chooser.setInitialFileName(codeTab.getOpenFile().className() + ".java");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Source", "*.java"));
        File file = chooser.showSaveDialog(owner.stage());
        if (file == null) {
            return;
        }

        try {
            ExportService.exportCurrentCode(owner.decoratedCurrentSource(codeTab), file.toPath());
            owner.statusBar().setFilePath(I18nUtil.getString("status.saved", file.getAbsolutePath()));
        } catch (IOException ex) {
            owner.showError(I18nUtil.getString("dialog.save.failed", ex.getMessage()));
        }
    }

    /** 在文件树中定位当前打开的类文件 */
    public void locateCurrentFileInTree() {
        CodeEditorTab currentTab = owner.tabManager().currentCodeTab();
        if (currentTab == null) {
            owner.statusBar().setFilePath(I18nUtil.getString("toolbar.reload.disabled"));
            return;
        }
        String fullPath = currentTab.getOpenFile().fullPath();
        boolean found = owner.tabManager().selectTreeNodeByPath(fullPath);
        if (!found) {
            owner.statusBar().setFilePath(I18nUtil.getString("toolbar.localizer.failed"));
        }
    }

    /** 快速打开类 */
    public void quickOpenClass() {
        var view = owner.requireWorkspaceOrWarn(I18nUtil.getString("menu.edit.quickOpen"),
                I18nUtil.getString("dialog.needOpenFile"));
        if (view == null) {
            return;
        }
        java.util.List<String> classNames = new java.util.ArrayList<>();
        collectClassNames(view.workspace().getTreeRoot(), classNames);
        QuickOpenDialog.show(owner.stage(), classNames, fullPath -> {
            FileTreeNode node = view.workspace().findNodeByPath(fullPath);
            if (node != null) {
                owner.classTabOpener().openClassTab(node, view.workspace(), view.codeTabPane(),
                        owner.currentEngine(), owner.lineNumbersEnabled());
            } else {
                owner.statusBar().setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            }
        });
    }

    /** 打开最近文件 */
    public void openRecentFile(String path) {
        loadFile(new java.io.File(path));
    }

    /** 启动参数或外部入口打开文件/目录 */
    public void openInitialFile(File file) {
        if (file != null && file.exists()) {
            loadFile(file);
        }
    }

    /** 获取最近文件列表 */
    public java.util.List<String> getRecentFiles() {
        return owner.config().recentFiles();
    }

    /** 清空最近文件列表 */
    public void clearRecentFiles() {
        owner.config().clearRecentFiles();
    }

    /** 打开新窗口 */
    public void openNewWindow() {
        CodeEditorTab currentTab = owner.tabManager().currentCodeTab();
        if (currentTab == null) {
            owner.showWarning(I18nUtil.getString("dialog.warning.title"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        if (!currentTab.isSourceReady()) {
            owner.showWarning(I18nUtil.getString("dialog.warning.title"),
                    I18nUtil.getString("dialog.save.pending"));
            return;
        }
        CodeOnlyWindow.openFrom(currentTab, owner.config(), owner.stage());
    }

    /** 递归收集文件树中所有 .class 节点的完整路径(用于快速打开对话框) */
    private void collectClassNames(FileTreeModel item, java.util.List<String> result) {
        FileTreeNode data = item.getValue();
        if (data != null && data.isClassFile()) {
            result.add(data.getFullPath());
        }
        for (FileTreeModel child : item.getChildren()) {
            collectClassNames(child, result);
        }
    }

    /** 异步加载并打开文件(JAR/ZIP/Class/目录),在工作区标签页中展示文件树和反编译内容 */
    void loadFile(File file) {
        log.info("loadFile: {} (size={}, isDir={})", file.getAbsolutePath(),
                file.length(), file.isDirectory());
        owner.statusBar().setFilePath(I18nUtil.getString("status.loading", file.getAbsolutePath()));
        owner.statusBar().setTask(I18nUtil.getString("task.loading"));

        WorkspaceLoader.loadAsync(file, owner.config(), Platform::runLater,
                workspace -> {
                    owner.statusBar().clearTask();
                    owner.tabManager().addWorkspaceTab(workspace,
                            (node, codeTabPane) -> owner.classTabOpener().openClassTab(
                                    node, workspace, codeTabPane, owner.currentEngine(), owner.lineNumbersEnabled()),
                            (node, codeTabPane) -> owner.classTabOpener().openTextFileTab(
                                    node, workspace, codeTabPane),
                            owner.exportController()::exportTreeItem,
                            node -> owner.searchController().openFindUsagesForNode(workspace, node),
                            owner.searchController()::openSearchForPackage,
                            (node, codeTabPane) -> owner.classTabOpener().openFileInHexView(
                                    node, workspace, codeTabPane));
                    owner.refreshToolbarState();
                },
                errorMsg -> {
                    owner.statusBar().clearTask();
                    owner.showError(I18nUtil.getString("dialog.load.error") + ": " + errorMsg);
                });
    }
}
