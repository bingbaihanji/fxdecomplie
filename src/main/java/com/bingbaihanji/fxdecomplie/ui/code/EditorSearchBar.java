package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器内搜索栏在 CodeArea 中提供 Ctrl+F 查找功能
 *
 * <p>TODO: 颜色值(#333333, #3c3c3c, #cccccc, #5c2020)硬编码,应改为使用主题系统(AppTheme / VsCodeThemeLoader)</p>
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class EditorSearchBar extends HBox {

    private final TextField input;
    private final Label statusLabel;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button closeBtn;
    private final List<Integer> matchPositions = new ArrayList<>();
    private CodeArea codeArea;
    private int currentMatch = -1;

    /**
     * 构造编辑器内搜索栏,绑定到指定 CodeArea
     *
     * @param codeArea 目标代码编辑区(可为 null,后续通过 {@link #rebind(CodeArea)} 绑定)
     */
    public EditorSearchBar(CodeArea codeArea) {
        super(6);
        this.codeArea = codeArea;
        setPadding(new Insets(4, 8, 4, 8));
        setStyle("-fx-background-color: #333333;");

        input = new TextField();
        input.setPromptText(I18nUtil.getString("editor.find.placeholder"));
        input.setFont(Font.font("Consolas", 13));
        input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
        HBox.setHgrow(input, Priority.ALWAYS);

        statusLabel = new Label();
        statusLabel.setMinWidth(96);
        statusLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        prevBtn = new Button("▲");
        prevBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        prevBtn.setOnAction(e -> navigateMatch(-1));

        nextBtn = new Button("▼");
        nextBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        nextBtn.setOnAction(e -> navigateMatch(1));

        closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        closeBtn.setOnAction(e -> hide());

        getChildren().addAll(input, statusLabel, prevBtn, nextBtn, closeBtn);

        input.textProperty().addListener((obs, old, text) -> performSearch());
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
                focusCodeArea();
            } else if (e.getCode() == KeyCode.ENTER) {
                navigateMatch(e.isShiftDown() ? -1 : 1);
                input.requestFocus();
            }
        });
    }

    /** 显示搜索栏并聚焦输入框 */
    public void show() {
        show(null);
    }

    /**
     * 显示搜索栏,可选填充初始查询文本
     *
     * @param initialQuery 初始搜索文本(null 或空字符串表示不填充)
     */
    public void show(String initialQuery) {
        setVisible(true);
        setManaged(true);
        if (initialQuery != null && !initialQuery.isBlank()) {
            input.setText(initialQuery);
        }
        input.requestFocus();
        if (!input.getText().isBlank()) {
            input.selectAll();
            performSearch();
        }
    }

    /** 重新绑定到新的 CodeArea(源码刷新时调用) */
    public void rebind(CodeArea newArea) {
        if (newArea != null) {
            this.codeArea = newArea;
            hide();
        }
    }

    /** 隐藏搜索栏并清空匹配状态 */
    public void hide() {
        setVisible(false);
        setManaged(false);
        matchPositions.clear();
        currentMatch = -1;
        updateStatus();
    }

    /** 跳转到下一个匹配项(循环) */
    public void navigateNext() {
        navigateMatch(1);
    }

    /** 跳转到上一个匹配项(循环) */
    public void navigatePrevious() {
        navigateMatch(-1);
    }

    private void performSearch() {
        matchPositions.clear();
        currentMatch = -1;
        String text = input.getText();
        String content = codeArea == null ? null : codeArea.getText();
        if (text == null || text.isEmpty() || content == null) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            updateStatus();
            return;
        }

        // 大小写不敏感地扫描全文,记录所有匹配的起始偏移
        int idx = 0;
        int textLen = text.length();
        while (idx <= content.length() - textLen) {
            boolean found = content.regionMatches(true, idx, text, 0, textLen);
            if (found) {
                matchPositions.add(idx);
                idx += textLen; // 跳过已匹配区域,避免重叠匹配
            } else {
                idx++;
            }
        }

        if (!matchPositions.isEmpty()) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            navigateMatch(1);
            input.requestFocus();
        } else {
            input.setStyle("-fx-background-color: #5c2020; -fx-text-fill: #cccccc;");
            updateStatus();
        }
    }

    private void navigateMatch(int direction) {
        if (matchPositions.isEmpty() || codeArea == null) {
            updateStatus();
            return;
        }
        // 使用取模实现循环导航：正向+1 取模,反向先加 size 再取模避免负数
        if (direction > 0) {
            currentMatch = (currentMatch + 1) % matchPositions.size();
        } else {
            currentMatch = (currentMatch - 1 + matchPositions.size()) % matchPositions.size();
        }
        int pos = matchPositions.get(currentMatch);
        int endPos = pos + input.getText().length();
        TextPos startTp = offsetToTextPos(pos);
        TextPos endTp = offsetToTextPos(endPos);
        codeArea.select(startTp, endTp);
        updateStatus();
    }

    private void updateStatus() {
        if (statusLabel == null) {
            return;
        }
        if (input.getText() == null || input.getText().isEmpty()) {
            statusLabel.setText("");
            return;
        }
        if (matchPositions.isEmpty()) {
            statusLabel.setText(I18nUtil.getString("editor.find.noResults"));
            return;
        }
        statusLabel.setText(I18nUtil.getString("editor.find.matchCount",
                currentMatch + 1, matchPositions.size()));
    }

    private void focusCodeArea() {
        if (codeArea != null) {
            codeArea.requestFocus();
        }
    }

    /**
     * 将平坦字符偏移转换为 TextPos(行列坐标),用于 CodeArea 文本选区定位
     *
     * @param targetOffset 目标字符偏移量
     * @return 对应的 TextPos,targetOffset <= 0 时返回 (0,0)
     */
    private TextPos offsetToTextPos(int targetOffset) {
        String content = codeArea.getText();
        if (content == null || targetOffset <= 0) {
            return TextPos.ZERO;
        }
        int line = 0;
        int lineStart = 0;
        for (int i = 0; i < targetOffset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int col = targetOffset - lineStart;
        return TextPos.ofLeading(line, col);
    }
}
