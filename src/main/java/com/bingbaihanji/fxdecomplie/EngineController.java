package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.CodeViewContext;
import com.bingbaihanji.fxdecomplie.ui.graph.GraphDialog;
import com.bingbaihanji.fxdecomplie.ui.graph.GraphService;
import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritanceService;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 引擎切换与图形展示控制器：反编译引擎切换、当前标签页刷新、
 * 继承图 / 控制流图 / 方法调用图展示、多引擎对比
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者（Mediator 模式）
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/classTabOpener 延迟初始化
 *
 * @author bingbaihanji
 */
public final class EngineController {

    private static final Logger log = LoggerFactory.getLogger(EngineController.class);

    private final MainWindow owner;

    public EngineController(MainWindow owner) {
        this.owner = owner;
    }

    /** 切换反编译引擎并重新反编译当前文件 */
    public void selectEngine(DecompilerTypeEnum engine) {
        if (owner.currentEngine() == engine) {
            return;
        }
        log.info("切换反编译引擎: {} -> {}", owner.currentEngine(), engine);
        owner.setCurrentEngine(engine);
        owner.config().decompiler().defaultEngine(engine);
        if (owner.menuBar() != null) {
            owner.menuBar().setSelectedEngine(engine);
        }
        owner.tabManager().setCurrentEngineName(engine.name());
        owner.statusBar().setEngine(engine.name());
        owner.statusBar().setFilePath(I18nUtil.getString("status.currentEngine", engine.name()));

        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        CodeEditorTab currentTab = owner.tabManager().currentCodeTab();
        if (view != null && currentTab != null) {
            TabPane targetPane = view.splitEditorPane().tabPaneFor(currentTab);
            if (targetPane == null) {
                targetPane = view.splitEditorPane().primaryTabPane();
            }
            owner.classTabOpener().cancelCurrentTask();
            owner.classTabOpener().refreshCurrentClassTab(
                    view.workspace(), targetPane, currentTab, engine, owner.lineNumbersEnabled());
        }
    }

    /** 用当前引擎重新反编译当前类 */
    public void refreshCurrentTab() {
        refreshCurrentTab(owner.currentEngine());
    }

    /** 用指定引擎重新反编译当前类 */
    void refreshCurrentTab(DecompilerTypeEnum engine) {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        CodeEditorTab currentTab = owner.tabManager().currentCodeTab();
        if (view == null || currentTab == null) {
            owner.statusBar().setFilePath(I18nUtil.getString("toolbar.reload.disabled"));
            return;
        }
        TabPane targetPane = view.splitEditorPane().tabPaneFor(currentTab);
        if (targetPane == null) {
            targetPane = view.splitEditorPane().primaryTabPane();
        }
        owner.classTabOpener().cancelCurrentTask();
        owner.classTabOpener().refreshCurrentClassTab(
                view.workspace(), targetPane, currentTab, engine, owner.lineNumbersEnabled());
        owner.statusBar().setFilePath(I18nUtil.getString("status.reloading", currentTab.getOpenFile().fullPath()));
    }

    public void showInheritanceGraph(CodeViewContext context) {
        if (context == null) {
            return;
        }
        String fullPath = context.classInternalName();
        if (fullPath == null || fullPath.isBlank()) {
            showGraphFailed(fullPath, null);
            return;
        }
        Workspace workspace = context.workspace();
        WorkspaceIndex index = workspace != null && workspace.isIndexReady()
                ? workspace.getIndex()
                : context.workspaceIndex();
        owner.statusBar().setTask(I18nUtil.getString("task.loading"));
        owner.statusBar().setFilePath(I18nUtil.getString("graph.building", fullPath));
        log.info("请求查看继承图: {}", fullPath);
        GraphDialog dialog = new GraphDialog(owner.stage(),
                I18nUtil.getString("context.inheritanceGraph") + " - " + fullPath);
        dialog.show();
        BackgroundTasks.run("InheritanceGraph-" + fullPath, () -> {
            try {
                byte[] classBytes = owner.classBytesForContext(context);
                if (classBytes == null || classBytes.length == 0) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                WorkspaceIndex graphIndex = workspace != null
                        ? owner.searchController().awaitWorkspaceIndex(workspace)
                        : index;
                if (graphIndex == WorkspaceIndex.EMPTY && index != null) {
                    graphIndex = index;
                }
                var tree = InheritanceService.buildTree(fullPath, graphIndex, classBytes);
                if (tree == null) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                String dot = GraphService.toInheritanceDOT(tree);
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    dialog.showDot(dot);
                });
            } catch (Exception e) {
                log.error("查看继承图失败: {}", fullPath, e);
                Platform.runLater(() -> showGraphFailed(dialog, fullPath, e));
            }
        });
    }

    public void showControlFlowGraph(CodeViewContext context) {
        if (context == null || context.openFile() == null) {
            return;
        }
        String source = context.openFile().sourceCode();
        // 获取当前光标行,确定所在方法
        WorkspaceView view = owner.workspaceViewFor(context.workspace());
        if (view == null) {
            return;
        }
        int line = 1;
        CodeEditorTab codeTab = view.splitEditorPane().currentCodeTab();
        if (codeTab != null && codeTab.getCodeArea() != null) {
            var caret = codeTab.getCodeArea().getCaretPosition();
            if (caret != null) {
                line = caret.index() + 1;
            }
        }
        String methodName = com.bingbaihanji.fxdecomplie.ui.code.CodeSyncHelper
                .findMethodAtLine(source, line);
        if (methodName == null || methodName.isBlank()) {
            showGraphFailed(methodName, null);
            return;
        }
        owner.statusBar().setTask(I18nUtil.getString("task.loading"));
        owner.statusBar().setFilePath("CFG - " + methodName);
        GraphDialog dialog = new GraphDialog(owner.stage(), "CFG - " + methodName);
        dialog.show();
        BackgroundTasks.run("CFG-" + methodName, () -> {
            try {
                byte[] classBytes = owner.classBytesForContext(context);
                if (classBytes == null || classBytes.length == 0) {
                    Platform.runLater(() -> showGraphFailed(dialog, null, null));
                    return;
                }
                String dot = com.bingbaihanji.fxdecomplie.ui.graph.CfgAnalyzer
                        .buildCfgDot(classBytes, methodName, null);
                Platform.runLater(() -> dialog.showDot(dot));
                Platform.runLater(owner.statusBar()::clearTask);
            } catch (Exception e) {
                log.error("CFG生成失败", e);
                Platform.runLater(() -> {
                    showGraphFailed(dialog, null, e);
                    owner.statusBar().clearTask();
                });
            }
        });
    }

    public void showMethodGraph(CodeViewContext context) {
        if (context == null) {
            return;
        }
        String fullPath = context.classInternalName();
        if (fullPath == null || fullPath.isBlank()) {
            showGraphFailed(fullPath, null);
            return;
        }
        owner.statusBar().setTask(I18nUtil.getString("task.loading"));
        owner.statusBar().setFilePath(I18nUtil.getString("graph.building", fullPath));
        log.info("请求查看方法图: {}", fullPath);
        GraphDialog dialog = new GraphDialog(owner.stage(),
                I18nUtil.getString("context.methodGraph") + " - " + fullPath);
        dialog.show();
        BackgroundTasks.run("MethodGraph-" + fullPath, () -> {
            try {
                byte[] classBytes = owner.classBytesForContext(context);
                if (classBytes == null || classBytes.length == 0) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                var graph = GraphService.parseMethodCalls(classBytes);
                if (graph.methods().isEmpty()) {
                    Platform.runLater(() -> showGraphFailed(dialog, fullPath, null));
                    return;
                }
                String dot = GraphService.toMethodDOT(graph);
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    dialog.showDot(dot);
                });
            } catch (Exception e) {
                log.error("查看方法图失败: {}", fullPath, e);
                Platform.runLater(() -> showGraphFailed(dialog, fullPath, e));
            }
        });
    }

    private void showGraphFailed(String fullPath, Throwable error) {
        showGraphFailed(null, fullPath, error);
    }

    private void showGraphFailed(GraphDialog dialog, String fullPath, Throwable error) {
        owner.statusBar().clearTask();
        owner.statusBar().setFilePath(I18nUtil.getString("graph.renderFailed"));
        String message = I18nUtil.getString("graph.renderFailed")
                + (fullPath == null || fullPath.isBlank() ? "" : ": " + fullPath);
        if (error != null) {
            message += System.lineSeparator() + error.getMessage();
        }
        if (dialog != null) {
            dialog.showMessage(message);
            return;
        }
        if (error == null) {
            owner.showWarning(I18nUtil.getString("dialog.warning.title"), message);
        } else {
            owner.showError(message);
        }
    }

    /** 用全部引擎反编译当前类并排打开标签页,方便对比输出 */
    public void compareEngines() {
        WorkspaceView view = owner.requireWorkspaceOrWarn(I18nUtil.getString("menu.engine.compareAll"),
                I18nUtil.getString("dialog.needOpenFile"));
        if (view == null) {
            return;
        }
        CodeEditorTab currentTab = owner.tabManager().currentCodeTab();
        if (currentTab == null) {
            owner.showWarning(I18nUtil.getString("menu.engine.compareAll"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }

        String fullPath = currentTab.getOpenFile().fullPath();
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node == null) {
            owner.statusBar().setFilePath(I18nUtil.getString("status.locateFailed", fullPath));
            return;
        }

        owner.statusBar().setFilePath(I18nUtil.getString("status.compareAllEngines", node.getFullPath()));
        DecompilerTypeEnum[] engines = DecompilerTypeEnum.values();
        // 第一个引擎在主 panel 打开 其余引擎通过 splitRight 分布到不同分屏
        for (int i = 0; i < engines.length; i++) {
            final TabPane targetPane;
            if (i == 0) {
                targetPane = view.splitEditorPane().primaryTabPane();
            } else {
                TabPane newCell = view.splitEditorPane().splitRight(null);
                if (newCell != null) {
                    targetPane = newCell;
                } else {
                    // 已达最大分屏数,剩余引擎放入最右侧 cell
                    var panes = view.splitEditorPane().allTabPanes();
                    targetPane = panes.get(panes.size() - 1);
                }
            }
            owner.classTabOpener().openClassTab(node, view.workspace(), targetPane,
                    engines[i], owner.lineNumbersEnabled(), i == 0, i == 0);
        }
    }

    /** 获取当前活动代码标签页实际使用的反编译引擎 */
    public DecompilerTypeEnum activeCodeTabEngine() {
        CodeEditorTab currentTab = owner.tabManager() == null ? null : owner.tabManager().currentCodeTab();
        if (currentTab == null || currentTab.getOpenFile() == null) {
            return owner.currentEngine();
        }
        return currentTab.getOpenFile().engine();
    }
}
