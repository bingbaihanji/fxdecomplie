package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;
import com.bingbaihanji.fxdecomplie.model.SearchResult;
import com.bingbaihanji.fxdecomplie.model.SearchScope;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.SearchService;
import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全文搜索对话框带防抖的实时搜索,TreeView 按类型分组显示结果
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchDialog {

    private static final Logger log = LoggerFactory.getLogger(SearchDialog.class);

    private static final int DEFAULT_MAX_RESULTS = 200;

    /** 工具类,禁止实例化 */
    private SearchDialog() {
        throw new AssertionError("utility class");
    }

    /** 简单调用：使用默认参数打开搜索对话框 */
    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, JumpCallback onJump) {
        show(owner, searchService, sourceCache, null, false, DEFAULT_MAX_RESULTS, onJump);
    }

    /** 使用自定义 SourceCacheLoader 打开搜索对话框 */
    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, SourceCacheLoader fullSourceLoader,
                            JumpCallback onJump) {
        show(owner, searchService, sourceCache, fullSourceLoader, false,
                DEFAULT_MAX_RESULTS, onJump);
    }

    /** 使用自定义结果上限打开搜索对话框 */
    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, SourceCacheLoader fullSourceLoader,
                            boolean defaultFullSourceSearch, int maxResults,
                            JumpCallback onJump) {
        show(owner, searchService, sourceCache, fullSourceLoader, defaultFullSourceSearch,
                maxResults, "", onJump);
    }

    /**
     * 打开全文搜索对话框(完整参数版本)
     * 200ms 防抖输入,按类型分组在 TreeView 中展示结果,双击结果项跳转
     *
     * @param owner                   父窗口
     * @param searchService           搜索服务(聚合多个 SearchProvider)
     * @param sourceCache             已反编译的源码缓存(类路径 → 源码文本)
     * @param fullSourceLoader        全量源码加载器(用于搜索所有类)；为 null 则禁用全量搜索选项
     * @param defaultFullSourceSearch 是否默认选中全量源码搜索
     * @param maxResults              最大返回结果数(限制在 50~2000 之间)
     * @param initialQuery            初始搜索关键词(可为空字符串)
     * @param onJump                  双击结果项时的跳转回调
     */
    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, SourceCacheLoader fullSourceLoader,
                            boolean defaultFullSourceSearch, int maxResults,
                            String initialQuery, JumpCallback onJump) {
        int resultLimit = Math.clamp(maxResults, 50, 2000);
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(I18nUtil.getString("search.title"));
        IconHelper.setStageIcon(dialog);

        TextField input = new TextField();
        input.setPromptText(I18nUtil.getString("search.prompt"));
        input.setStyle("-fx-font-size: 14px; -fx-padding: 8px;");

        ComboBox<String> searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll(
                I18nUtil.getString("search.type.all"),
                I18nUtil.getString("search.type.class"),
                I18nUtil.getString("search.type.method"),
                I18nUtil.getString("search.type.code"),
                I18nUtil.getString("search.type.resource"),
                I18nUtil.getString("search.type.comment"),
                I18nUtil.getString("search.type.bytecode"));
        searchTypeCombo.setValue(I18nUtil.getString("search.type.all"));
        searchTypeCombo.setStyle("-fx-font-size: 13px;");

        Label comboLabel = new Label(I18nUtil.getString("search.scope"));
        comboLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        CheckBox fullSourceSearch = new CheckBox(I18nUtil.getString("search.fullSource"));
        fullSourceSearch.setStyle("-fx-text-fill: #cccccc;");
        fullSourceSearch.setDisable(fullSourceLoader == null);
        fullSourceSearch.setSelected(fullSourceLoader != null && defaultFullSourceSearch);

        // 搜索选项开关
        javafx.scene.control.ToggleButton regexToggle = new javafx.scene.control.ToggleButton(".*");
        regexToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        regexToggle.setTooltip(new javafx.scene.control.Tooltip("正则表达式"));

        javafx.scene.control.ToggleButton caseToggle = new javafx.scene.control.ToggleButton("Aa");
        caseToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        caseToggle.setTooltip(new javafx.scene.control.Tooltip("区分大小写"));

        javafx.scene.control.ToggleButton wordToggle = new javafx.scene.control.ToggleButton("W");
        wordToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        wordToggle.setTooltip(new javafx.scene.control.Tooltip("全词匹配"));

        javafx.scene.layout.HBox searchOptionsBar = new javafx.scene.layout.HBox(4,
                regexToggle, caseToggle, wordToggle);

        TreeView<SearchResult> resultTree = new TreeView<>();
        resultTree.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(resultTree, Priority.ALWAYS);

        resultTree.setCellFactory(tv -> new TreeCell<SearchResult>() {
            @Override
            protected void updateItem(SearchResult result, boolean empty) {
                super.updateItem(result, empty);
                if (empty || result == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (result.fullPath() == null || result.fullPath().isBlank()) {
                        setText(result.matchLine());
                    } else {
                        setText(result.fullPath() + ":" + result.lineNumber()
                                + "  " + result.matchLine());
                    }
                    setGraphic(null);
                }
            }
        });

        Label statusLabel = new Label("");
        statusLabel.setStyle("-fx-text-fill: #858585; -fx-padding: 4px;");

        VBox rootPane = new VBox(6, input, comboLabel, searchTypeCombo,
                fullSourceSearch, searchOptionsBar, resultTree, statusLabel);
        rootPane.setPadding(new Insets(8));
        rootPane.setStyle("-fx-background-color: #2d2d2d;");

        // 搜索代数计数器：每次输入变化递增,用于丢弃过时的搜索结果
        AtomicInteger searchGen = new AtomicInteger(0);
        // 当前正在执行的搜索任务引用,用于取消旧任务
        AtomicReference<Future<?>> currentSearchTask = new AtomicReference<>();
        // 对话框关闭标志,防止重复清理
        AtomicBoolean closed = new AtomicBoolean(false);

        // 200ms 输入防抖：用户在输入框中快速打字时,每次变化等待 200ms 无新输入后再触发搜索
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        Runnable scheduleSearch = () -> {
            String text = input.getText();
            // 停止之前的防抖计时,清空旧结果,显示"搜索中"状态
            debounce.stop();
            resultTree.setRoot(null);
            statusLabel.setText(text == null || text.isEmpty() ? "" : I18nUtil.getString("search.searching"));
            // 递增代数并取消之前的搜索任务,确保旧任务结果被丢弃
            int gen = searchGen.incrementAndGet();
            BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            // 防抖计时结束后执行实际搜索
            debounce.setOnFinished(e -> {
                String query = input.getText();
                if (query == null || query.isEmpty()) {
                    statusLabel.setText("");
                    return;
                }
                // 解析搜索范围：根据下拉框选择的类型确定 SearchScope
                SearchScope scope = scopeFromLabel(searchTypeCombo.getValue());
                // 全量源码搜索仅在范围与源码相关时启用
                boolean includeFullSource = fullSourceLoader != null
                        && fullSourceSearch.isSelected()
                        && scope.sourceCacheRelevant();
                // 构建搜索选项：正则、大小写、全词匹配
                SearchOptions options = new SearchOptions(
                        regexToggle.isSelected(),
                        caseToggle.isSelected(),
                        wordToggle.isSelected());
                // 提交搜索任务到后台线程池
                Future<?> task = BackgroundTasks.run("search-worker", () -> {
                    Map<String, String> effectiveSourceCache = sourceCache;
                    // 如果启用了全量源码搜索,加载全量源码缓存(可能耗时较长)
                    if (includeFullSource && fullSourceLoader != null) {
                        Platform.runLater(() -> statusLabel.setText(
                                I18nUtil.getString("search.preparingFullSource")));
                        effectiveSourceCache = fullSourceLoader.load();
                        // 加载完成后检查是否已被取消
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }
                    // 聚合所有 Provider 执行搜索
                    List<SearchResult> results = searchService.searchAll(
                            query, effectiveSourceCache, options, resultLimit, scope);
                    // 搜索完成后再次检查是否已被取消
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    // 回到 FX 线程渲染结果
                    Platform.runLater(() -> {
                        // 代数不匹配说明期间有新输入,丢弃过期结果
                        if (gen != searchGen.get()) {
                            return;
                        }
                        // 构建分组结果树
                        TreeItem<SearchResult> rootNode = buildResultTree(results, resultLimit);
                        resultTree.setRoot(rootNode);
                        if (rootNode != null) {
                            rootNode.setExpanded(true);
                        }
                        // 统计实际显示的叶子节点数量
                        int shown = rootNode != null
                                ? countLeaves(rootNode) : 0;
                        statusLabel.setText(shown >= resultLimit
                                ? I18nUtil.getString("search.tooMany", resultLimit)
                                : I18nUtil.getString("search.resultCount", shown));
                    });
                }, rejected -> Platform.runLater(() -> {
                    if (gen == searchGen.get()) {
                        statusLabel.setText("Search task rejected: background queue is full");
                    }
                }));
                currentSearchTask.set(task);
            });
            debounce.playFromStart();
        };

        // 当搜索范围切换时,更新"全量源码搜索"复选框的可用状态
        Runnable updateFullSourceState = () -> {
            SearchScope scope = scopeFromLabel(searchTypeCombo.getValue());
            fullSourceSearch.setDisable(fullSourceLoader == null || !scope.sourceCacheRelevant());
        };

        input.textProperty().addListener((obs, old, text) -> scheduleSearch.run());
        searchTypeCombo.valueProperty().addListener((obs, old, val) -> {
            updateFullSourceState.run();
            scheduleSearch.run();
        });
        fullSourceSearch.selectedProperty().addListener((obs, old, val) -> scheduleSearch.run());
        regexToggle.selectedProperty().addListener((obs, old, val) -> scheduleSearch.run());
        caseToggle.selectedProperty().addListener((obs, old, val) -> scheduleSearch.run());
        wordToggle.selectedProperty().addListener((obs, old, val) -> scheduleSearch.run());
        updateFullSourceState.run();

        // 双击搜索结果项 → 跳转到对应文件位置
        resultTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<SearchResult> selected = resultTree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.isLeaf()) {
                    SearchResult result = selected.getValue();
                    if (result != null && result.fullPath() != null
                            && !result.fullPath().isBlank()) {
                        onJump.jump(result.fullPath(), result.lineNumber());
                    }
                }
            }
        });

        // ESC 键关闭对话框
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        Scene scene = new Scene(rootPane, 700, 520);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        // 对话框关闭时清理：递增代数使进行中的任务结果被丢弃,停止防抖定时器,取消后台任务
        dialog.setOnCloseRequest(event -> {
            if (closed.compareAndSet(false, true)) {
                searchGen.incrementAndGet();
                debounce.stop();
                BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            }
        });
        dialog.setOnHidden(event -> {
            if (closed.compareAndSet(false, true)) {
                searchGen.incrementAndGet();
                debounce.stop();
                BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            }
        });
        dialog.show();
        DefaultWindowTheme.applyWindowDarkMode(dialog);
        input.requestFocus();
        if (initialQuery != null && !initialQuery.isBlank()) {
            input.setText(initialQuery);
            input.selectAll();
        }
    }

    /** 将下拉框显示标签(国际化文本)映射为对应的 SearchScope 枚举值 */
    private static SearchScope scopeFromLabel(String label) {
        if (label == null || label.equals(I18nUtil.getString("search.type.all"))) {
            return SearchScope.ALL;
        }
        if (label.equals(I18nUtil.getString("search.type.class"))) {
            return SearchScope.CLASS;
        }
        if (label.equals(I18nUtil.getString("search.type.method"))) {
            return SearchScope.METHOD;
        }
        if (label.equals(I18nUtil.getString("search.type.code"))) {
            return SearchScope.CODE;
        }
        if (label.equals(I18nUtil.getString("search.type.resource"))) {
            return SearchScope.RESOURCE;
        }
        if (label.equals(I18nUtil.getString("search.type.comment"))) {
            return SearchScope.COMMENT;
        }
        if (label.equals(I18nUtil.getString("search.type.bytecode"))) {
            return SearchScope.BYTECODE;
        }
        return SearchScope.ALL;
    }

    /**
     * 构建搜索结果树：按匹配类型(类名/方法/代码/资源/注释/字节码)分组,
     * 每组显示为可展开的 TreeItem,限制总叶子节点不超过 maxResults
     *
     * @param results    扁平搜索结果列表
     * @param maxResults 最大叶子节点数量
     * @return 根节点(类型为"搜索结果")
     */
    private static TreeItem<SearchResult> buildResultTree(List<SearchResult> results,
                                                          int maxResults) {
        TreeItem<SearchResult> rootNode = new TreeItem<>(new SearchResult("",
                I18nUtil.getString("search.root"), 0, SearchResult.MatchType.CODE_TEXT));

        // 使用 LinkedHashMap 保持分组顺序
        Map<String, List<SearchResult>> groups = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String label = groupLabel(r.matchType());
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(r);
        }

        int leafCount = 0;
        for (var entry : groups.entrySet()) {
            if (leafCount >= maxResults) {
                break;
            }
            // 分组标题使用特殊的 SearchResult: 空路径,以分组标签作为 matchLine
            TreeItem<SearchResult> groupItem = new TreeItem<>(
                    new SearchResult("", entry.getKey(), 0, SearchResult.MatchType.CODE_TEXT));
            for (SearchResult r : entry.getValue()) {
                if (leafCount >= maxResults) {
                    break;
                }
                groupItem.getChildren().add(new TreeItem<>(r));
                leafCount++;
            }
            if (!groupItem.getChildren().isEmpty()) {
                rootNode.getChildren().add(groupItem);
                groupItem.setExpanded(true);
            }
        }
        return rootNode;
    }

    /**
     * 将匹配类型映射为对应的国际化显示标签(用于分组标题)
     * 方法名和字段名共用"方法/成员"分组
     */
    private static String groupLabel(SearchResult.MatchType type) {
        return switch (type) {
            case CLASS_NAME -> I18nUtil.getString("search.group.class");
            case METHOD_NAME, FIELD_NAME -> I18nUtil.getString("search.group.method");
            case CODE_TEXT -> I18nUtil.getString("search.group.code");
            case RESOURCE_TEXT -> I18nUtil.getString("search.group.resource");
            case COMMENT_TEXT -> I18nUtil.getString("search.group.comment");
            case BYTECODE_TEXT -> I18nUtil.getString("search.group.bytecode");
            default -> I18nUtil.getString("search.group.code");
        };
    }

    /**
     * 递归统计树节点下的叶子节点数量
     *
     * @param root 结果树根节点
     * @return 叶子节点总数
     */
    private static int countLeaves(TreeItem<SearchResult> root) {
        int count = 0;
        for (TreeItem<SearchResult> child : root.getChildren()) {
            if (child.isLeaf()) {
                count++;
            } else {
                count += countLeaves(child);
            }
        }
        return count;
    }

    /** 搜索结果双击跳转回调：传入文件全路径和行号 */
    @FunctionalInterface
    public interface JumpCallback {
        void jump(String fullPath, int lineNumber);
    }

    /** 全量源码缓存加载器：供后台线程调用以获取完整反编译源码 */
    @FunctionalInterface
    public interface SourceCacheLoader {
        Map<String, String> load();
    }
}
