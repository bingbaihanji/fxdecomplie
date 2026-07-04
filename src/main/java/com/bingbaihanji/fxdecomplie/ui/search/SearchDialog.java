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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全文搜索对话框带防抖的实时搜索,TreeView 按类型分组显示结果
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchDialog {

    private static final Logger logger = LoggerFactory.getLogger(SearchDialog.class);

    private static final int DEFAULT_MAX_RESULTS = 200;

    private SearchDialog() {
        throw new AssertionError("utility class");
    }

    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, JumpCallback onJump) {
        show(owner, searchService, sourceCache, null, false, DEFAULT_MAX_RESULTS, onJump);
    }

    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, SourceCacheLoader fullSourceLoader,
                            JumpCallback onJump) {
        show(owner, searchService, sourceCache, fullSourceLoader, false,
                DEFAULT_MAX_RESULTS, onJump);
    }

    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, SourceCacheLoader fullSourceLoader,
                            boolean defaultFullSourceSearch, int maxResults,
                            JumpCallback onJump) {
        show(owner, searchService, sourceCache, fullSourceLoader, defaultFullSourceSearch,
                maxResults, "", onJump);
    }

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

        // 跟踪搜索代数,用于丢弃过时结果
        AtomicInteger searchGen = new AtomicInteger(0);
        AtomicReference<Future<?>> currentSearchTask = new AtomicReference<>();

        // 200ms 防抖
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        Runnable scheduleSearch = () -> {
            String text = input.getText();
            debounce.stop();
            resultTree.setRoot(null);
            statusLabel.setText(text == null || text.isEmpty() ? "" : I18nUtil.getString("search.searching"));
            int gen = searchGen.incrementAndGet();
            BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            debounce.setOnFinished(e -> {
                String query = input.getText();
                if (query == null || query.isEmpty()) {
                    statusLabel.setText("");
                    return;
                }
                SearchScope scope = scopeFromLabel(searchTypeCombo.getValue());
                boolean includeFullSource = fullSourceLoader != null
                        && fullSourceSearch.isSelected()
                        && scope.sourceCacheRelevant();
                SearchOptions options = new SearchOptions(
                        regexToggle.isSelected(),
                        caseToggle.isSelected(),
                        wordToggle.isSelected());
                Future<?> task = BackgroundTasks.run("search-worker", () -> {
                    Map<String, String> effectiveSourceCache = sourceCache;
                    if (includeFullSource && fullSourceLoader != null) {
                        Platform.runLater(() -> statusLabel.setText(
                                I18nUtil.getString("search.preparingFullSource")));
                        effectiveSourceCache = fullSourceLoader.load();
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }
                    List<SearchResult> results = searchService.searchAll(
                            query, effectiveSourceCache, options, resultLimit, scope);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    Platform.runLater(() -> {
                        if (gen != searchGen.get()) return; // 过时搜索,丢弃
                        TreeItem<SearchResult> rootNode = buildResultTree(results, resultLimit);
                        resultTree.setRoot(rootNode);
                        if (rootNode != null) {
                            rootNode.setExpanded(true);
                        }
                        int shown = rootNode != null
                                ? countLeaves(rootNode) : 0;
                        statusLabel.setText(shown >= resultLimit
                                ? I18nUtil.getString("search.tooMany", resultLimit)
                                : I18nUtil.getString("search.resultCount", shown));
                    });
                });
                currentSearchTask.set(task);
            });
            debounce.playFromStart();
        };

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

        // 双击跳转
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

        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) dialog.close();
        });

        Scene scene = new Scene(rootPane, 700, 520);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.setOnCloseRequest(event -> BackgroundTasks.cancel(currentSearchTask.get()));
        dialog.setOnHidden(event -> {
            debounce.stop();
            BackgroundTasks.cancel(currentSearchTask.get());
        });
        dialog.show();
        DefaultWindowTheme.applyWindowDarkMode(dialog);
        input.requestFocus();
        if (initialQuery != null && !initialQuery.isBlank()) {
            input.setText(initialQuery);
            input.selectAll();
        }
    }

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

    /** 构建 TreeView 结构,按匹配类型分组,限制最多 maxResults 个叶子节点 */
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

    /** 将匹配类型映射为显示标签 */
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

    /** 统计树节点下的叶子节点数量(递归) */
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

    @FunctionalInterface
    public interface JumpCallback {
        void jump(String fullPath, int lineNumber);
    }

    @FunctionalInterface
    public interface SourceCacheLoader {
        Map<String, String> load();
    }
}
