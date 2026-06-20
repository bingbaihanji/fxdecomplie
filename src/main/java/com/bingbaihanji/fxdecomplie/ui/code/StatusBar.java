package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * 状态栏组件,显示当前文件路径、编码和光标位置
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class StatusBar extends HBox {

    /** 左侧：类路径标签 */
    private final Label pathLabel;
    /** 中间：编码标签 */
    private final Label encodingLabel;
    /** 当前反编译引擎 */
    private final Label engineLabel;
    /** 当前后台任务提示 */
    private final Label taskLabel;
    /** 右侧：光标位置标签 */
    private final Label positionLabel;

    public StatusBar() {
        setPadding(new Insets(2, 8, 2, 8));
        getStyleClass().add("status-bar");
        setAlignment(Pos.CENTER_LEFT);

        pathLabel = new Label("");
        pathLabel.getStyleClass().add("status-label");
        pathLabel.setMaxWidth(Double.MAX_VALUE);

        Region spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        taskLabel = new Label("");
        taskLabel.getStyleClass().add("status-label");
        taskLabel.setPadding(new Insets(0, 16, 0, 0));

        engineLabel = new Label("");
        engineLabel.getStyleClass().add("status-label");
        engineLabel.setPadding(new Insets(0, 16, 0, 0));

        encodingLabel = new Label("UTF-8");
        encodingLabel.getStyleClass().add("status-label");
        encodingLabel.setPadding(new Insets(0, 16, 0, 0));

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        positionLabel = new Label("1:1");
        positionLabel.getStyleClass().add("status-label");

        getChildren().addAll(pathLabel, spacer1, taskLabel, engineLabel, encodingLabel, spacer2, positionLabel);
    }

    /** 更新类路径显示 */
    public void setFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            pathLabel.setText("");
            pathLabel.setTooltip(null);
            return;
        }
        String display = filePath;
        if (display.endsWith(".class")) {
            display = display.substring(0, display.length() - 6)
                    .replace('/', '>')
                    .replace('\\', '>');
        }
        pathLabel.setText(display);
        pathLabel.setTooltip(new Tooltip(filePath));
    }

    /** 更新光标位置(行:列) */
    public void setCursorPosition(int line, int column) {
        positionLabel.setText(line + ":" + column);
    }

    /** 更新编码显示 */
    public void setEncoding(String encoding) {
        encodingLabel.setText(encoding);
    }

    /** 更新当前反编译引擎显示 */
    public void setEngine(String engine) {
        engineLabel.setText(engine == null || engine.isBlank() ? ""
                : com.bingbaihanji.fxdecomplie.utils.I18nUtil.getString("status.engine", engine));
    }

    /** 更新当前任务提示 */
    public void setTask(String task) {
        taskLabel.setText(task == null || task.isBlank() ? "" : task);
    }

    /** 清除当前任务提示 */
    public void clearTask() {
        taskLabel.setText("");
    }

    /** 清除状态栏显示 */
    public void clear() {
        pathLabel.setText("");
        taskLabel.setText("");
        engineLabel.setText("");
        positionLabel.setText("1:1");
        encodingLabel.setText("UTF-8");
    }
}
