package com.bingbihanji.fxdecomplie.ui.search;

import com.bingbihanji.fxdecomplie.model.SearchOptions;
import com.bingbihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbihanji.fxdecomplie.service.SearchService;
import com.bingbihanji.fxdecomplie.utils.I18nUtil;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 全文搜索对话框。带防抖的实时搜索，TreeView 按类型分组显示结果。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchDialog {

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
        int resultLimit = Math.clamp(maxResults, 50, 2000);
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(I18nUtil.getString("search.title"));

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

        // Search option toggles
        javafx.scene.control.ToggleButton regexToggle = new javafx.scene.control.ToggleButton(".*");
        regexToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        regexToggle.setTooltip(new javafx.scene.control.Tooltip("Regex"));

        javafx.scene.control.ToggleButton caseToggle = new javafx.scene.control.ToggleButton("Aa");
        caseToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        caseToggle.setTooltip(new javafx.scene.control.Tooltip("Case sensitive"));

        javafx.scene.control.ToggleButton wordToggle = new javafx.scene.control.ToggleButton("W");
        wordToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
        wordToggle.setTooltip(new javafx.scene.control.Tooltip("Whole word"));

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
                    setText(result.lineNumber() + "  " + result.matchLine());
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

        // Track search generation to discard stale results
        AtomicInteger searchGen = new AtomicInteger(0);
        // Store last full search results for combo re-filtering
        AtomicReference<List<SearchResult>> lastAllResults = new AtomicReference<>(new ArrayList<>());
        AtomicReference<Future<?>> currentSearchTask = new AtomicReference<>();

        // Debounce 200ms
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        input.textProperty().addListener((obs, old, text) -> {
            debounce.stop();
            resultTree.setRoot(null);
            statusLabel.setText(text.isEmpty() ? "" : I18nUtil.getString("search.searching"));
            int gen = searchGen.incrementAndGet();
            BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            debounce.setOnFinished(e -> {
                if (text.isEmpty()) {
                    statusLabel.setText("");
                    return;
                }
                String selectedType = searchTypeCombo.getValue();
                Future<?> task = BackgroundTasks.run("search-worker", () -> {
                    Map<String, String> effectiveSourceCache = sourceCache;
                    if (fullSourceSearch.isSelected() && fullSourceLoader != null) {
                        Platform.runLater(() -> statusLabel.setText(
                                I18nUtil.getString("search.preparingFullSource")));
                        effectiveSourceCache = fullSourceLoader.load();
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }
                    SearchOptions options = new SearchOptions(
                            regexToggle.isSelected(),
                            caseToggle.isSelected(),
                            wordToggle.isSelected());
                    List<SearchResult> all = searchService.searchAll(
                            text, effectiveSourceCache, options, resultLimit);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    lastAllResults.set(all);
                    List<SearchResult> filtered = filterByType(all, selectedType);
                    Platform.runLater(() -> {
                        if (gen != searchGen.get()) return; // stale search
                        TreeItem<SearchResult> rootNode = buildResultTree(filtered, resultLimit);
                        resultTree.setRoot(rootNode);
                        if (rootNode != null) rootNode.setExpanded(true);
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
        });

        // Re-filter existing results when combo changes (no re-search)
        searchTypeCombo.valueProperty().addListener((obs, old, val) -> {
            String text = input.getText();
            if (text == null || text.isBlank()) return;
            List<SearchResult> all = lastAllResults.get();
            if (all == null || all.isEmpty()) return;
            List<SearchResult> filtered = filterByType(all, val);
            TreeItem<SearchResult> rootNode = buildResultTree(filtered, resultLimit);
            resultTree.setRoot(rootNode);
            if (rootNode != null) rootNode.setExpanded(true);
            int shown = rootNode != null ? countLeaves(rootNode) : 0;
            statusLabel.setText(shown >= resultLimit
                    ? I18nUtil.getString("search.tooMany", resultLimit)
                    : I18nUtil.getString("search.resultCount", shown));
        });

        // Double-click to jump
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
                com.bingbihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.setOnCloseRequest(event -> BackgroundTasks.cancel(currentSearchTask.get()));
        dialog.show();
        input.requestFocus();
    }

    /** Filter results by the selected search type in the combo box */
    private static List<SearchResult> filterByType(List<SearchResult> results, String filterType) {
        if (filterType == null || I18nUtil.getString("search.type.all").equals(filterType)) {
            return results;
        }
        List<SearchResult> filtered = new ArrayList<>();
        for (SearchResult r : results) {
            if (matchesFilter(r.matchType(), filterType)) {
                filtered.add(r);
            }
        }
        return filtered;
    }

    private static boolean matchesFilter(SearchResult.MatchType type, String filter) {
        return switch (filter) {
            case String value when value.equals(I18nUtil.getString("search.type.class")) ->
                    type == SearchResult.MatchType.CLASS_NAME;
            case String value when value.equals(I18nUtil.getString("search.type.method")) ->
                    type == SearchResult.MatchType.METHOD_NAME
                            || type == SearchResult.MatchType.FIELD_NAME;
            case String value when value.equals(I18nUtil.getString("search.type.code")) ->
                    type == SearchResult.MatchType.CODE_TEXT;
            case String value when value.equals(I18nUtil.getString("search.type.resource")) ->
                    type == SearchResult.MatchType.RESOURCE_TEXT;
            case String value when value.equals(I18nUtil.getString("search.type.comment")) ->
                    type == SearchResult.MatchType.COMMENT_TEXT;
            case String value when value.equals(I18nUtil.getString("search.type.bytecode")) ->
                    type == SearchResult.MatchType.BYTECODE_TEXT;
            default -> true;
        };
    }

    /** Build TreeView structure grouped by match type label, limited to maxResults leaves */
    private static TreeItem<SearchResult> buildResultTree(List<SearchResult> results,
                                                          int maxResults) {
        TreeItem<SearchResult> rootNode = new TreeItem<>(new SearchResult("",
                I18nUtil.getString("search.root"), 0, SearchResult.MatchType.CODE_TEXT));

        // Use LinkedHashMap to preserve group order
        Map<String, List<SearchResult>> groups = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String label = groupLabel(r.matchType());
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(r);
        }

        int leafCount = 0;
        for (var entry : groups.entrySet()) {
            if (leafCount >= maxResults) break;
            // Group header uses a special SearchResult with empty path and group label as matchLine
            TreeItem<SearchResult> groupItem = new TreeItem<>(
                    new SearchResult("", entry.getKey(), 0, SearchResult.MatchType.CODE_TEXT));
            for (SearchResult r : entry.getValue()) {
                if (leafCount >= maxResults) break;
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

    /** Map match type to display label */
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

    /** Count leaf nodes under a tree item (recursive) */
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
