package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.util.I18nUtil;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.ContextMenuEvent;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 代码区右键上下文菜单，提供跳转声明、查看继承图、查看方法图、添加注释四个功能
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeAreaContextMenu extends ContextMenu {

    private static final Logger logger = LoggerFactory.getLogger(CodeAreaContextMenu.class);

    private final CodeArea codeArea;
    private final CodeViewContext context;
    private final CodeActionHandler actionHandler;

    private final MenuItem gotoDeclarationItem;
    private final MenuItem inheritanceGraphItem;
    private final MenuItem methodGraphItem;
    private final MenuItem controlFlowGraphItem;
    private final MenuItem addCommentItem;
    private final MenuItem deleteCommentItem;
    private TextPos actionPosition;

    public CodeAreaContextMenu(CodeArea codeArea, CodeViewContext context, CodeActionHandler actionHandler) {
        this.codeArea = codeArea;
        this.context = context;
        this.actionHandler = actionHandler;

        gotoDeclarationItem = new MenuItem(I18nUtil.getString("context.gotoDeclaration"));
        gotoDeclarationItem.setOnAction(e -> onGotoDeclaration());

        inheritanceGraphItem = new MenuItem(I18nUtil.getString("context.inheritanceGraph"));
        inheritanceGraphItem.setOnAction(e -> onShowInheritanceGraph());

        methodGraphItem = new MenuItem(I18nUtil.getString("context.methodGraph"));
        methodGraphItem.setOnAction(e -> onShowMethodGraph());

        controlFlowGraphItem = new MenuItem("Show CFG");
        controlFlowGraphItem.setOnAction(e -> onShowControlFlowGraph());

        MenuItem renameItem = new MenuItem("Rename (Shift+F6)");
        renameItem.setOnAction(e -> onRename());

        addCommentItem = new MenuItem(I18nUtil.getString("context.addComment"));
        addCommentItem.setOnAction(e -> onAddComment());

        deleteCommentItem = new MenuItem(I18nUtil.getString("comment.delete"));
        deleteCommentItem.setOnAction(e -> onDeleteComment());

        getItems().addAll(
                gotoDeclarationItem,
                inheritanceGraphItem,
                methodGraphItem,
                controlFlowGraphItem,
                new SeparatorMenuItem(),
                renameItem,
                new SeparatorMenuItem(),
                addCommentItem,
                deleteCommentItem
        );

        setOnShowing(e -> refreshState());
    }

    private static int flatOffset(String text, TextPos pos) {
        int line = Math.max(0, pos.index());
        int offset = Math.max(0, pos.offset());
        int currentLine = 0;
        int lineStart = 0;
        for (int i = 0; i < text.length() && currentLine < line; i++) {
            if (text.charAt(i) == '\n') {
                currentLine++;
                lineStart = i + 1;
            }
        }
        return Math.min(text.length(), lineStart + offset);
    }

    private static boolean isJavaNameChar(char ch) {
        return Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '$';
    }

    public void prepare(ContextMenuEvent event) {
        actionPosition = null;
        if (codeArea == null || event == null) {
            return;
        }
        TextPos pos = codeArea.getTextPosition(event.getScreenX(), event.getScreenY());
        if (pos != null) {
            actionPosition = pos;
            codeArea.select(pos, pos);
        }
    }

    /** 每次显示前更新菜单项状态 */
    private void refreshState() {
        boolean hasClassContext = context != null && context.classInternalName() != null
                && !context.classInternalName().isBlank();

        gotoDeclarationItem.setDisable(context == null || codeArea == null);
        inheritanceGraphItem.setDisable(!hasClassContext);
        methodGraphItem.setDisable(!hasClassContext);
        controlFlowGraphItem.setDisable(!hasClassContext);
        addCommentItem.setDisable(codeArea == null);
    }

    private void onGotoDeclaration() {
        if (actionHandler == null || context == null || codeArea == null) {
            return;
        }
        int line = currentLine();
        actionHandler.goToDeclaration(context, line, tokenAtActionPosition());
    }

    private void onShowInheritanceGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        logger.info("代码区右键菜单触发查看继承图: {}", context.classInternalName());
        actionHandler.showInheritanceGraph(context);
    }

    private void onShowMethodGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        logger.info("代码区右键菜单触发查看方法图: {}", context.classInternalName());
        actionHandler.showMethodGraph(context);
    }

    private void onShowControlFlowGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        logger.info("代码区右键菜单触发查看CFG: {}", context.classInternalName());
        actionHandler.showControlFlowGraph(context);
    }

    private void onAddComment() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        actionHandler.addOrUpdateComment(context, caret);
    }

    private void onRename() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        actionHandler.renameAtCaret(context, caret);
    }

    private void onDeleteComment() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        int line = caret == null ? 1 : Math.max(1, caret.index() + 1);
        actionHandler.deleteComment(context, line);
    }

    private int currentLine() {
        TextPos pos = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        return pos == null ? 1 : Math.max(1, pos.index() + 1);
    }

    private String tokenAtActionPosition() {
        String text = codeArea.getText();
        TextPos pos = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        if (text == null || text.isEmpty() || pos == null) {
            return "";
        }
        int offset = flatOffset(text, pos);
        if (offset < 0 || offset > text.length()) {
            return "";
        }
        int start = offset;
        while (start > 0 && isJavaNameChar(text.charAt(start - 1))) {
            start--;
        }
        int end = offset;
        while (end < text.length() && isJavaNameChar(text.charAt(end))) {
            end++;
        }
        return start < end ? text.substring(start, end) : "";
    }
}
