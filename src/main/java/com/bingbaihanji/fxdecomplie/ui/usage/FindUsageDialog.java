package com.bingbaihanji.fxdecomplie.ui.usage;

import com.bingbaihanji.fxdecomplie.model.UsageResult;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.UsageSearchService;
import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 ASM 的类/成员使用搜索对话框
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class FindUsageDialog {

    private static final Logger log = LoggerFactory.getLogger(FindUsageDialog.class);

    private FindUsageDialog() {
        throw new AssertionError("utility class");
    }

    /** 显示查找使用对话框（无初始查询） */
    public static void show(Window owner, WorkspaceIndex index, JumpCallback onJump) {
        show(owner, index, onJump, "");
    }

    /**
     * 显示查找使用对话框
     *
     * @param owner        父窗口
     * @param index        工作区索引
     * @param onJump       跳转回调,双击结果时导航
     * @param initialQuery 初始查询文本,可为空
     */
    public static void show(Window owner, WorkspaceIndex index, JumpCallback onJump,
                            String initialQuery) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(I18nUtil.getString("usage.title"));
        IconHelper.setStageIcon(dialog);

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
        java.util.concurrent.atomic.AtomicLong searchGeneration = new java.util.concurrent.atomic.AtomicLong();
        dialog.setOnHidden(e -> {
            searchGeneration.incrementAndGet();
            BackgroundTasks.cancel(currentSearchTask.get());
        });

        Runnable runSearch = () -> {
            Future<?> oldTask = currentSearchTask.getAndSet(null);
            BackgroundTasks.cancel(oldTask);
            long gen = searchGeneration.incrementAndGet();
            String query = input.getText();
            if (query == null || query.isBlank()) {
                status.setText("");
                resultTree.setRoot(null);
                return;
            }
            status.setText(I18nUtil.getString("usage.searching"));
            Future<?> task = BackgroundTasks.run("FindUsages", () -> {
                List<UsageResult> results = UsageSearchService.findUsages(index, query);
                Platform.runLater(() -> {
                    if (searchGeneration.get() != gen) {
                        return; // 已过期,丢弃
                    }
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
        dialog.show();
        DefaultWindowTheme.applyWindowDarkMode(dialog);
        input.requestFocus();
        if (input.getText() != null && !input.getText().isBlank()) {
            runSearch.run();
        }
    }

    /** 将搜索结果按使用类型分组,构建 TreeView 层级结构 */
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

    /** 根据使用类型返回对应的国际化分组标签 */
    private static String groupLabel(UsageResult.UsageType type) {
        return switch (type) {
            case CLASS_REFERENCE -> I18nUtil.getString("usage.group.class");
            case METHOD_CALL -> I18nUtil.getString("usage.group.method");
            case FIELD_ACCESS -> I18nUtil.getString("usage.group.field");
        };
    }

    /** 跳转回调接口,双击搜索结果时导航到对应文件和行号 */
    @FunctionalInterface
    public interface JumpCallback {
        /** 跳转到指定文件的指定行 */
        void jump(String fullPath, int lineNumber);
    }
}
