package com.bingbaihanji.fxdecomplie.ui.quickopen;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.util.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * 快速打开类对话框类似 IntelliJ Ctrl+N输入类名实时过滤 workspace 所有 class,回车打开
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class QuickOpenDialog {

    private static final Logger logger = LoggerFactory.getLogger(QuickOpenDialog.class);

    private QuickOpenDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示快速打开对话框
     * @param owner      父窗口
     * @param classNames workspace 中所有类的全路径列表
     * @param onSelect   选中回调
     */
    public static void show(javafx.stage.Window owner,
                            List<String> classNames,
                            Consumer<String> onSelect) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle(I18nUtil.getString("quickopen.title"));
        IconHelper.setStageIcon(dialog);

        TextField input = new TextField();
        input.setPromptText(I18nUtil.getString("quickopen.prompt"));
        input.setStyle("-fx-font-size: 15px; -fx-padding: 10px; -fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");

        ListView<String> listView = new ListView<>();
        listView.setStyle("-fx-background-color: #252526;");
        FilteredList<String> filtered = new FilteredList<>(
                FXCollections.observableArrayList(classNames), p -> true);
        listView.setItems(filtered);
        if (!listView.getItems().isEmpty()) {
            listView.getSelectionModel().select(0);
        }

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int idx = item.lastIndexOf('/');
                    String display = idx >= 0 ? item.substring(idx + 1) + "  —  " + item : item;
                    setText(display);
                    setTextFill(javafx.scene.paint.Color.web("#cccccc"));
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace; -fx-font-size: 13px;");
                }
            }
        });

        // 实时过滤
        input.textProperty().addListener((obs, old, text) -> {
            String lower = text.toLowerCase();
            filtered.setPredicate(name -> {
                if (text.isEmpty()) return true;
                String nameLower = name.toLowerCase();
                String simple = nameLower.substring(nameLower.lastIndexOf('/') + 1);
                return simple.startsWith(lower) || nameLower.contains(lower);
            });
            if (!listView.getItems().isEmpty()) {
                listView.getSelectionModel().select(0);
            }
        });

        // 键盘导航
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listView.getSelectionModel().selectNext();
                listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                listView.getSelectionModel().selectPrevious();
                listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    onSelect.accept(selected);
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        // 双击
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    onSelect.accept(selected);
                }
            }
        });

        VBox rootPane = new VBox(6, input, listView);
        rootPane.setPadding(new Insets(8));
        rootPane.setStyle("-fx-background-color: #2d2d2d;");
        rootPane.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(rootPane, 550, 400);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.show();
        DefaultWindowTheme.applyWindowDarkMode(dialog);
        input.requestFocus();
    }

}
