package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
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
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class EditorSearchBar extends HBox {

    private final TextField input;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button closeBtn;
    private final List<Integer> matchPositions = new ArrayList<>();
    private CodeArea codeArea;
    private int currentMatch = -1;

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

        prevBtn = new Button("▲");
        prevBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        prevBtn.setOnAction(e -> navigateMatch(-1));

        nextBtn = new Button("▼");
        nextBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        nextBtn.setOnAction(e -> navigateMatch(1));

        closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        closeBtn.setOnAction(e -> hide());

        getChildren().addAll(input, prevBtn, nextBtn, closeBtn);

        input.textProperty().addListener((obs, old, text) -> performSearch());
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                hide();
            } else if (e.getCode() == KeyCode.ENTER) {
                navigateMatch(1);
            }
        });
    }

    public void show() {
        setVisible(true);
        setManaged(true);
        input.requestFocus();
        if (!input.getText().isEmpty()) {
            input.selectAll();
            performSearch();
        }
    }

    /** 重新绑定到新的 CodeArea（源码刷新时调用） */
    public void rebind(CodeArea newArea) {
        if (newArea != null) {
            this.codeArea = newArea;
            hide();
        }
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        matchPositions.clear();
        currentMatch = -1;
    }

    private void performSearch() {
        matchPositions.clear();
        currentMatch = -1;
        String text = input.getText();
        String content = codeArea.getText();
        if (text == null || text.isEmpty() || content == null) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            return;
        }

        int idx = 0;
        int textLen = text.length();
        while (idx <= content.length() - textLen) {
            boolean found = content.regionMatches(true, idx, text, 0, textLen);
            if (found) {
                matchPositions.add(idx);
                idx += textLen;
            } else {
                idx++;
            }
        }

        if (!matchPositions.isEmpty()) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            navigateMatch(1);
        } else {
            input.setStyle("-fx-background-color: #5c2020; -fx-text-fill: #cccccc;");
        }
    }

    private void navigateMatch(int direction) {
        if (matchPositions.isEmpty()) return;
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
        codeArea.requestFocus();
    }

    /** 将平坦字符偏移转换为 TextPos(行列坐标) */
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
