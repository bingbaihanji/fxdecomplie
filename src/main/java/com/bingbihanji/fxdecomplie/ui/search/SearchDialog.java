package com.bingbihanji.fxdecomplie.ui.search;

import com.bingbihanji.fxdecomplie.app.BackgroundTasks;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 全文搜索对话框。带防抖的实时搜索，TreeView 按类型分组显示结果。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchDialog {

    private static final int MAX_RESULTS = 200;

    private SearchDialog() { throw new AssertionError("utility class"); }

    @FunctionalInterface
    public interface JumpCallback { void jump(String fullPath, int lineNumber); }

    public static void show(javafx.stage.Window owner, SearchService searchService,
                            Map<String, String> sourceCache, JumpCallback onJump) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("全文搜索");

        TextField input = new TextField();
        input.setPromptText("输入关键词搜索...");
        input.setStyle("-fx-font-size: 14px; -fx-padding: 8px;");

        ComboBox<String> searchTypeCombo = new ComboBox<>();
        searchTypeCombo.getItems().addAll("全部", "类名", "方法", "代码", "资源", "注释", "字节码");
        searchTypeCombo.setValue("全部");
        searchTypeCombo.setStyle("-fx-font-size: 13px;");

        Label comboLabel = new Label("搜索范围:");
        comboLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

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

        VBox rootPane = new VBox(6, input, comboLabel, searchTypeCombo, resultTree, statusLabel);
        rootPane.setPadding(new Insets(8));
        rootPane.setStyle("-fx-background-color: #2d2d2d;");

        // Track search generation to discard stale results
        AtomicInteger searchGen = new AtomicInteger(0);

        /** Build the result tree from a filtered list of results */
        Runnable buildTree = () -> { /* placeholder — set inside listener */ };

        // Debounce 200ms
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        input.textProperty().addListener((obs, old, text) -> {
            debounce.stop();
            resultTree.setRoot(null);
            statusLabel.setText(text.isEmpty() ? "" : "搜索中...");
            int gen = searchGen.incrementAndGet();
            debounce.setOnFinished(e -> {
                if (text.isEmpty()) { statusLabel.setText(""); return; }
                String selectedType = searchTypeCombo.getValue();
                BackgroundTasks.run("search-worker", () -> {
                    List<SearchResult> all = searchService.searchAll(text, sourceCache);
                    List<SearchResult> filtered = filterByType(all, selectedType);
                    Platform.runLater(() -> {
                        if (gen != searchGen.get()) return; // stale search
                        TreeItem<SearchResult> rootNode = buildResultTree(filtered);
                        resultTree.setRoot(rootNode);
                        if (rootNode != null) rootNode.setExpanded(true);
                        int shown = rootNode != null
                                ? countLeaves(rootNode) : 0;
                        statusLabel.setText(shown >= MAX_RESULTS
                                ? "结果过多，仅显示前 " + MAX_RESULTS + " 条" : shown + " 条结果");
                    });
                });
            });
            debounce.playFromStart();
        });

        // Re-apply filter when combo changes (no re-search needed)
        searchTypeCombo.valueProperty().addListener((obs, old, val) -> {
            String text = input.getText();
            if (text == null || text.isBlank()) return;
            int gen = searchGen.incrementAndGet();
            BackgroundTasks.run("search-worker", () -> {
                List<SearchResult> all = searchService.searchAll(text, sourceCache);
                List<SearchResult> filtered = filterByType(all, val);
                Platform.runLater(() -> {
                    if (gen != searchGen.get()) return;
                    TreeItem<SearchResult> rootNode = buildResultTree(filtered);
                    resultTree.setRoot(rootNode);
                    if (rootNode != null) rootNode.setExpanded(true);
                    int shown = rootNode != null ? countLeaves(rootNode) : 0;
                    statusLabel.setText(shown >= MAX_RESULTS
                            ? "结果过多，仅显示前 " + MAX_RESULTS + " 条" : shown + " 条结果");
                });
            });
        });

        // Double-click to jump
        resultTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<SearchResult> selected = resultTree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.isLeaf()) {
                    SearchResult result = selected.getValue();
                    onJump.jump(result.fullPath(), result.lineNumber());
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
        dialog.show();
        input.requestFocus();
    }

    /** Filter results by the selected search type in the combo box */
    private static List<SearchResult> filterByType(List<SearchResult> results, String filterType) {
        if (filterType == null || "全部".equals(filterType)) {
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
            case "类名" -> type == SearchResult.MatchType.CLASS_NAME;
            case "方法" -> type == SearchResult.MatchType.METHOD_NAME
                        || type == SearchResult.MatchType.FIELD_NAME;
            case "代码" -> type == SearchResult.MatchType.CODE_TEXT;
            case "资源" -> type == SearchResult.MatchType.RESOURCE_TEXT;
            case "注释" -> type == SearchResult.MatchType.COMMENT_TEXT;
            case "字节码" -> type == SearchResult.MatchType.BYTECODE_TEXT;
            default -> true;
        };
    }

    /** Build TreeView structure grouped by match type label, limited to MAX_RESULTS leaves */
    private static TreeItem<SearchResult> buildResultTree(List<SearchResult> results) {
        TreeItem<SearchResult> rootNode = new TreeItem<>(new SearchResult("", "搜索结果", 0, SearchResult.MatchType.CODE_TEXT));

        // Use LinkedHashMap to preserve group order
        Map<String, List<SearchResult>> groups = new LinkedHashMap<>();
        for (SearchResult r : results) {
            String label = groupLabel(r.matchType());
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(r);
        }

        int leafCount = 0;
        for (var entry : groups.entrySet()) {
            if (leafCount >= MAX_RESULTS) break;
            // Group header uses a special SearchResult with empty path and group label as matchLine
            TreeItem<SearchResult> groupItem = new TreeItem<>(
                    new SearchResult("", entry.getKey(), 0, SearchResult.MatchType.CODE_TEXT));
            for (SearchResult r : entry.getValue()) {
                if (leafCount >= MAX_RESULTS) break;
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
            case CLASS_NAME -> "[类名]";
            case METHOD_NAME, FIELD_NAME -> "[方法]";
            case CODE_TEXT -> "[代码]";
            case RESOURCE_TEXT -> "[资源]";
            case COMMENT_TEXT -> "[注释]";
            case BYTECODE_TEXT -> "[字节码]";
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
}
