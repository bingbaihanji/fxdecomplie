package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.ui.DialogHelper;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.*;
import com.bingbaihanji.fxdecomplie.ui.search.*;
import com.bingbaihanji.fxdecomplie.ui.usage.FindUsageDialog;
import com.bingbaihanji.util.I18nUtil;
import javafx.application.Platform;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 搜索与用法查找控制器：工作区搜索对话框、Find Usages、包搜索、
 * 全文源码缓存构建与工作区索引就绪等待。
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者（Mediator 模式）。
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/classTabOpener 延迟初始化。
 *
 * @author bingbaihanji
 */
public final class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final MainWindow owner;

    public SearchController(MainWindow owner) {
        this.owner = owner;
    }

    /** 委托 ClassTabOpener 计算 L2 缓存工作区键,确保跨组件缓存复用 */
    private static String workspaceKey(Workspace workspace) {
        return com.bingbaihanji.fxdecomplie.ui.code.ClassTabOpener.computeWorkspaceKey(workspace);
    }

    public void searchInWorkspace(String selectedText) {
        if (selectedText == null || selectedText.isBlank()) {
            return;
        }
        openSearch(selectedText);
    }

    /** 复制引用到剪贴板后在状态栏显示提示 */
    public void copyReference(String referenceText) {
        if (referenceText != null && !referenceText.isEmpty()) {
            owner.statusBar().setFilePath(I18nUtil.getString("status.copied") + ": " + referenceText);
        }
    }

    /** 打开搜索对话框 */
    public void openSearch() {
        openSearch("");
    }

    /** 打开搜索对话框,可预填初始查询关键词 */
    public void openSearch(String initialQuery) {
        var view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            DialogHelper.showWarning(owner.stage(), I18nUtil.getString("search.title"),
                    I18nUtil.getString("dialog.needOpenFile"));
            return;
        }
        withWorkspaceIndex(view.workspace(), index -> openSearchWithIndex(view, index, initialQuery));
    }

    /** 装配所有搜索 Provider、构建源码缓存并弹出搜索对话框 */
    private void openSearchWithIndex(WorkspaceView view, WorkspaceIndex index, String initialQuery) {
        // 从已打开标签页构建源码缓存
        java.util.Map<String, String> sourceCache = new HashMap<>();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (javafx.scene.control.Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && codeTab.getOpenFile().engine() == owner.currentEngine()
                        && codeTab.isSourceReady()) {
                    sourceCache.put(codeTab.getOpenFile().fullPath(),
                            codeTab.getOpenFile().sourceCode());
                }
            }
        }

        // 构建 classPath → displayName 映射(支持搜索反混淆/重命名后的名称)
        String wsHash = com.bingbaihanji.fxdecomplie.model.CommentScope
                .of(view.workspace(), "").workspaceHash();
        java.util.Map<String, String> displayNamesByPath = new HashMap<>();
        for (String classPath : index.classPaths()) {
            String display = com.bingbaihanji.fxdecomplie.rename.RenameService
                    .displayClassName(classPath, wsHash);
            if (display != null && !display.isBlank()) {
                String pathWithoutExt = classPath.endsWith(".class")
                        ? classPath.substring(0, classPath.length() - 6) : classPath;
                String simpleOriginal = pathWithoutExt.substring(
                        pathWithoutExt.lastIndexOf('/') + 1);
                // 仅当 display 与原始简单名不同时才加入映射(有重命名/反混淆)
                if (!display.equals(simpleOriginal)) {
                    displayNamesByPath.put(classPath, display + ".class");
                }
            }
        }

        // 创建包含所有 Provider 的 SearchService
        SearchService searchService = new SearchService();
        searchService.setExcludePatterns(owner.config().search().excludePatterns());
        searchService.addProvider(new ClassSearchProvider(index.classPaths(), displayNamesByPath));
        searchService.addProvider(new IndexedMemberSearchProvider(index));
        searchService.addProvider(new MethodSearchProvider());
        searchService.addProvider(new CodeSearchProvider());
        searchService.addProvider(new CommentSearchProvider());
        searchService.addProvider(new ResourceSearchProvider(index.resourceBytesByPath()));
        searchService.addProvider(new BytecodeSearchProvider(index));

        SearchDialog.show(owner.stage(), searchService, sourceCache,
                () -> buildFullSourceCache(view, sourceCache),
                owner.config().search().fullSourceSearch(), owner.config().search().resultLimit(),
                initialQuery,
                (fullPath, lineNumber) -> owner.navigationController().openClassByPath(view, fullPath, lineNumber));
    }

    /** 查找当前工作区内的类/方法/字段使用 */
    public void openFindUsages() {
        var view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            DialogHelper.showWarning(owner.stage(), I18nUtil.getString("usage.title"), I18nUtil.getString("dialog.export.noworkspace"));
            return;
        }
        withWorkspaceIndex(view.workspace(), index ->
                FindUsageDialog.show(owner.stage(), index,
                        (fullPath, lineNumber) -> owner.navigationController().openClassByPath(view, fullPath, lineNumber)));
    }

    /**
     * 构建完整源码缓存供全文搜索使用,优先复用工作区级缓存和 L2 标签页缓存
     * 对未缓存的类逐类解编译(含 30 秒超时和 JD-Core 回退),完成后存入工作区缓存
     * 单次调用最多运行 120 秒,中间可被中断取消
     */
    public Map<String, String> buildFullSourceCache(WorkspaceView view,
                                                    Map<String, String> openTabSourceCache) {
        Map<String, String> safeOpenTabs = openTabSourceCache == null ? Map.of() : openTabSourceCache;
        Map<String, String> engineOptions = DecompilerOptions.forEngine(owner.config(), owner.currentEngine());
        String cacheKey = owner.currentEngine().name() + "|" + DecompilerOptions.hash(engineOptions);
        Map<String, String> cached = view.workspace().getSourceSearchCache(cacheKey);
        if (cached != null) {
            Map<String, String> merged = new LinkedHashMap<>(cached);
            merged.putAll(safeOpenTabs);
            return merged;
        }

        Map<String, String> fullSourceCache = new LinkedHashMap<>(safeOpenTabs);
        var index = awaitWorkspaceIndex(view.workspace());
        boolean indexReady = index != WorkspaceIndex.EMPTY;
        var context = DecompilerContext.fromWorkspaceIndex(index, engineOptions);
        var classes = index.classes();
        int total = classes.size();
        int processed = 0;
        long startTime = System.currentTimeMillis();
        boolean completed = indexReady;
        for (var cls : classes) {
            if (Thread.currentThread().isInterrupted()) {
                completed = false;
                break;
            }
            if (System.currentTimeMillis() - startTime > 120_000) { // 总计 2 分钟超时
                completed = false;
                break;
            }
            processed++;
            if (total > 100 && processed % 100 == 0) {
                int pct = processed * 100 / total;
                Platform.runLater(() -> owner.statusBar().setTask(
                        I18nUtil.getString("task.indexing") + " (" + pct + "%)"));
            }
            if (!fullSourceCache.containsKey(cls.fullPath())) {
                if (Thread.currentThread().isInterrupted()) {
                    completed = false;
                    break;
                }
                // 优先查询 L2 内存缓存(复用已打开标签页的解编译结果,避免重复解编)
                byte[] classBytes = cls.bytes();
                if (classBytes == null || classBytes.length == 0) {
                    log.debug("全文搜索跳过不可读类: {}", cls.fullPath());
                    continue;
                }
                String fp = ClassTabOpener.computeClassFingerprint(classBytes);
                String source = owner.classTabOpener().getDecompileCache().get(
                        workspaceKey(view.workspace()) + "_" + fp, cls.internalName(),
                        owner.currentEngine(), DecompilerOptions.hash(engineOptions));
                if (source == null) {
                    // L2 miss: 带超时和 JD 回退的全量解编译
                    source = DecompilerRunner.decompileWithTimeout(
                            cls.fullPath(), classBytes, owner.currentEngine(), context,
                            () -> !Thread.currentThread().isInterrupted());
                }
                if (!DecompilerRunner.isTransientFailureOutput(source)) {
                    fullSourceCache.put(cls.fullPath(), source);
                }
            }
        }
        fullSourceCache.values().removeIf(String::isEmpty);
        if (completed) {
            view.workspace().putSourceSearchCache(cacheKey, fullSourceCache);
        }
        return fullSourceCache;
    }

    /**
     * 确保工作区索引可用后执行回调若索引已就绪则同步回调；否则触发后台构建并异步等待
     * 工作区关闭后不再执行回调,避免操作已释放的 UI
     */
    public void withWorkspaceIndex(Workspace workspace, Consumer<WorkspaceIndex> onReady) {
        if (workspace == null || onReady == null) {
            return;
        }
        if (workspace.isIndexReady()) {
            onReady.accept(workspace.getIndex());
            return;
        }
        WorkspaceIndexService.ensureIndexingStarted(workspace);
        owner.statusBar().setTask(I18nUtil.getString("task.indexing"));
        owner.statusBar().setFilePath(I18nUtil.getString("status.indexing", workspace.getName()));
        workspace.getIndexFuture().whenComplete((index, error) -> Platform.runLater(() -> {
            owner.statusBar().clearTask();
            if (error != null) {
                DialogHelper.showError(owner.stage(), I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.index.failed", error.getMessage()));
                return;
            }
            if (owner.tabManager().currentWorkspaceView() == null
                    || !workspace.equals(owner.tabManager().currentWorkspaceView().workspace())) {
                owner.statusBar().setFilePath(I18nUtil.getString("status.indexingComplete"));
                return;
            }
            onReady.accept(index);
        }));
    }

    /** 同步等待工作区索引构建完成(阻塞调用线程,仅后台线程使用) */
    public WorkspaceIndex awaitWorkspaceIndex(Workspace workspace) {
        if (workspace == null) {
            return WorkspaceIndex.EMPTY;
        }
        if (workspace.isIndexReady()) {
            return workspace.getIndex();
        }
        WorkspaceIndexService.ensureIndexingStarted(workspace);
        try {
            return workspace.getIndexFuture().get(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.debug("等待工作区索引超时或失败", e);
            return WorkspaceIndex.EMPTY;
        }
    }

    /** 为文件树右键菜单触发的 Find Usages 打开对话框 */
    public void openFindUsagesForNode(Workspace workspace, FileTreeNode node) {
        if (workspace == null || node == null || !node.isClassFile()) {
            return;
        }
        withWorkspaceIndex(workspace, index ->
                FindUsageDialog.show(owner.stage(), index,
                        (fullPath, lineNumber) -> {
                            WorkspaceView view = owner.tabManager().currentWorkspaceView();
                            if (view != null) {
                                owner.navigationController().openClassByPath(view, fullPath, lineNumber);
                            }
                        },
                        (node.getFullPath().endsWith(".class") ? node.getFullPath().substring(0, node.getFullPath().length() - 6) : node.getFullPath())));
    }

    /** 提取节点所在的包路径作为搜索关键词并打开搜索对话框 */
    public void openSearchForPackage(FileTreeNode node) {
        String query = "";
        if (node != null) {
            String path = node.getFullPath();
            int slash = path.lastIndexOf('/');
            if (slash > 0) {
                query = path.substring(0, slash);
                owner.statusBar().setFilePath(I18nUtil.getString(
                        "status.searchPackage", query));
            }
        }
        openSearch(query);
    }
}
