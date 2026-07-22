package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.WorkspaceIndexService;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * 状态栏组件,显示当前文件路径 编码 光标位置 进度条 内存占用 后台任务队列长度
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
    /** 索引构建提示 */
    private final Label indexLabel;
    /** 进度条 */
    private final ProgressBar progressBar;
    /** 后台任务队列长度 */
    private final Label queueLabel;
    /** JVM 内存占用 */
    private final Label memLabel;
    /** 右侧：光标位置标签 */
    private final Label positionLabel;
    /** 定时刷新监控信息的时间线 */
    private final Timeline monitorTimeline;
    /** 当前进度条是否由索引进度刷新逻辑显示 */
    private boolean indexProgressVisible;

    /** 构建状态栏布局：路径 | 弹性空间 | 任务 | 进度条 | 队列 | 内存 | 引擎 | 编码 | 弹性空间 | 光标位置 */
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
        taskLabel.setPadding(new Insets(0, 8, 0, 0));

        indexLabel = new Label("");
        indexLabel.getStyleClass().add("status-label");
        indexLabel.setPadding(new Insets(0, 8, 0, 0));
        indexLabel.setVisible(false);
        indexLabel.setManaged(false);

        progressBar = new ProgressBar(0);
        progressBar.getStyleClass().add("status-progress");
        progressBar.setPrefWidth(120);
        progressBar.setPrefHeight(14);
        progressBar.setVisible(false);
        progressBar.setManaged(false);

        queueLabel = new Label("");
        queueLabel.getStyleClass().add("status-label");
        queueLabel.setPadding(new Insets(0, 8, 0, 0));
        queueLabel.setVisible(false);
        queueLabel.setManaged(false);

        memLabel = new Label("");
        memLabel.getStyleClass().add("status-label");
        memLabel.setPadding(new Insets(0, 16, 0, 0));

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

        getChildren().addAll(pathLabel, spacer1, taskLabel, indexLabel, progressBar, queueLabel, memLabel,
                engineLabel, encodingLabel, spacer2, positionLabel);

        // 周期性刷新内存 队列和索引进度
        monitorTimeline = new Timeline(new KeyFrame(Duration.millis(500), e -> refreshMonitors()));
        monitorTimeline.setCycleCount(Animation.INDEFINITE);
        monitorTimeline.play();
        // 启动时立即刷新一次
        refreshMonitors();
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
                : I18nUtil.getString("status.currentEngine", engine));
    }

    /** 更新当前任务提示 */
    public void setTask(String task) {
        taskLabel.setText(task == null || task.isBlank() ? "" : task);
    }

    /** 清除当前任务提示 */
    public void clearTask() {
        taskLabel.setText("");
    }

    /**
     * 设置进度条值
     *
     * @param progress 0.0~1.0 的进度值，-1 表示不确定进度
     */
    public void setProgress(double progress) {
        indexProgressVisible = false;
        progressBar.setVisible(true);
        progressBar.setManaged(true);
        progressBar.setProgress(progress);
    }

    /** 隐藏进度条并重置为 0 */
    public void clearProgress() {
        indexProgressVisible = false;
        progressBar.setVisible(false);
        progressBar.setManaged(false);
        progressBar.setProgress(0);
    }

    /** 清除状态栏显示 */
    public void clear() {
        pathLabel.setText("");
        taskLabel.setText("");
        indexLabel.setText("");
        indexLabel.setVisible(false);
        indexLabel.setManaged(false);
        engineLabel.setText("");
        positionLabel.setText("1:1");
        encodingLabel.setText("UTF-8");
        clearProgress();
    }

    /** 停止监控定时器(应用关闭时调用) */
    public void stopMonitor() {
        monitorTimeline.stop();
    }

    /** 刷新内存和队列监控信息 */
    private void refreshMonitors() {
        // 内存信息
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        String memText = I18nUtil.getString("status.memory", String.valueOf(used), String.valueOf(max));
        if (!memText.equals(memLabel.getText())) {
            memLabel.setText(memText);
            memLabel.setTooltip(new Tooltip("Used: " + used + " MB / Max: " + max + " MB"));
        }

        // 队列信息
        int pending = BackgroundTasks.getTotalPendingTasks();
        if (pending > 0) {
            queueLabel.setText(I18nUtil.getString("status.queue", String.valueOf(pending)));
            queueLabel.setVisible(true);
            queueLabel.setManaged(true);
        } else {
            queueLabel.setVisible(false);
            queueLabel.setManaged(false);
        }

        WorkspaceIndexService.IndexProgressSnapshot snapshot = WorkspaceIndexService.getProgressSnapshot();
        if (snapshot != null && snapshot.progress() >= 0.0 && snapshot.progress() <= 1.0) {
            int pct = (int) Math.round(snapshot.progress() * 100);
            indexLabel.setText(I18nUtil.getString("status.indexing", snapshot.workspaceName())
                    + " " + pct + "%");
            indexLabel.setVisible(true);
            indexLabel.setManaged(true);
            progressBar.setVisible(true);
            progressBar.setManaged(true);
            progressBar.setProgress(snapshot.progress());
            progressBar.setTooltip(new Tooltip(snapshot.processed() + "/" + snapshot.total()
                    + " " + snapshot.phase()));
            indexProgressVisible = true;
        } else {
            indexLabel.setVisible(false);
            indexLabel.setManaged(false);
            progressBar.setTooltip(null);
            if (indexProgressVisible) {
                progressBar.setVisible(false);
                progressBar.setManaged(false);
                progressBar.setProgress(0);
                indexProgressVisible = false;
            }
        }
    }
}
