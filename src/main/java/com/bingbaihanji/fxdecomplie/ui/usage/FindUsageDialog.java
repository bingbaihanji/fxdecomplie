package com.bingbaihanji.fxdecomplie.ui.usage;

import com.bingbaihanji.fxdecomplie.model.UsageResult;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.UsageSearchService;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dialog for ASM-backed class/member usage search.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class FindUsageDialog {

    private FindUsageDialog() {
        throw new AssertionError("utility class");
    }

    public static void show(Window owner, WorkspaceIndex index, JumpCallback onJump) {
        show(owner, index, onJump, "");
    }

    public static void show(Window owner, WorkspaceIndex index, JumpCallback onJump,
                            String initialQuery) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(I18nUtil.getString("usage.title"));
        setDialogIcon(dialog);

        TextField input = new TextField();
        input.setPromptText(I18nUtil.getString("usage.query.prompt"));
        input.setText(initialQuery == null ? "" : initialQuery);
        HBox.setHgrow(input, Priority.ALWAYS);

        Button searchButton = new Button(I18nUtil.getString("usage.search"));
        HBox searchBar = new HBox(6, input, searchButton);

        TreeView<UsageResult> resultTree = new TreeView<>();
        resultTree.setShowRoot(false);
        VBox.setVgrow(resultTree, Priority.ALWAYS);
        resultTree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(UsageResult result, boolean empty) {
                super.updateItem(result, empty);
                if (empty || result == null) {
                    setText(null);
                } else {
                    setText(result.sourcePath().isBlank()
                            ? result.displayText()
                            : result.sourcePath() + ":" + result.lineNumber()
                              + "  " + result.displayText());
                }
            }
        });

        Label status = new Label("");
        status.setStyle("-fx-text-fill: #858585;");

        AtomicReference<Future<?>> currentSearchTask = new AtomicReference<>();

        Runnable runSearch = () -> {
            String query = input.getText();
            if (query == null || query.isBlank()) {
                status.setText("");
                resultTree.setRoot(null);
                return;
            }
            status.setText(I18nUtil.getString("usage.searching"));
            BackgroundTasks.cancel(currentSearchTask.getAndSet(null));
            Future<?> task = BackgroundTasks.run("FindUsages", () -> {
                List<UsageResult> results = UsageSearchService.findUsages(index, query);
                Platform.runLater(() -> {
                    resultTree.setRoot(buildTree(results));
                    status.setText(I18nUtil.getString("usage.resultCount", results.size()));
                });
            });
            currentSearchTask.set(task);
        };
        searchButton.setOnAction(event -> runSearch.run());
        input.setOnAction(event -> runSearch.run());
        input.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        resultTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<UsageResult> selected = resultTree.getSelectionModel().getSelectedItem();
                if (selected != null && selected.isLeaf() && selected.getValue() != null
                        && !selected.getValue().sourcePath().isBlank()) {
                    UsageResult result = selected.getValue();
                    onJump.jump(result.sourcePath(), result.lineNumber());
                }
            }
        });

        VBox root = new VBox(8, searchBar, resultTree, status);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #2d2d2d;");

        Scene scene = new Scene(root, 780, 520);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.setOnHidden(event -> BackgroundTasks.cancel(currentSearchTask.get()));
        dialog.show();
        com.bingbaihanji.fxdecomplie.platform.FxTools.applyWindowDarkMode(dialog);
        input.requestFocus();
        if (input.getText() != null && !input.getText().isBlank()) {
            runSearch.run();
        }
    }

    private static TreeItem<UsageResult> buildTree(List<UsageResult> results) {
        TreeItem<UsageResult> root = new TreeItem<>();
        Map<UsageResult.UsageType, List<UsageResult>> groups = new LinkedHashMap<>();
        for (UsageResult result : results) {
            groups.computeIfAbsent(result.type(), key -> new ArrayList<>()).add(result);
        }
        for (var entry : groups.entrySet()) {
            TreeItem<UsageResult> group = new TreeItem<>(
                    new UsageResult("", 1, entry.getKey(), groupLabel(entry.getKey())));
            for (UsageResult result : entry.getValue()) {
                group.getChildren().add(new TreeItem<>(result));
            }
            group.setExpanded(true);
            root.getChildren().add(group);
        }
        return root;
    }

    private static String groupLabel(UsageResult.UsageType type) {
        return switch (type) {
            case CLASS_REFERENCE -> I18nUtil.getString("usage.group.class");
            case METHOD_CALL -> I18nUtil.getString("usage.group.method");
            case FIELD_ACCESS -> I18nUtil.getString("usage.group.field");
        };
    }

    @FunctionalInterface
    public interface JumpCallback {
        void jump(String fullPath, int lineNumber);
    }

    private static void setDialogIcon(Stage stage) {
        try {
            var stream = FindUsageDialog.class.getResourceAsStream("/icon/logo.png");
            if (stream != null) {
                stage.getIcons().add(new javafx.scene.image.Image(stream));
            }
        } catch (Exception ignored) {
        }
    }
}
