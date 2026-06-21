package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.TextPos;

/**
 * 代码区右键上下文菜单，提供跳转声明、查看继承图、查看方法图、添加注释四个功能
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeAreaContextMenu extends ContextMenu {

    private final CodeArea codeArea;
    private final CodeViewContext context;
    private final CodeActionHandler actionHandler;

    private final MenuItem gotoDeclarationItem;
    private final MenuItem inheritanceGraphItem;
    private final MenuItem methodGraphItem;
    private final MenuItem addCommentItem;

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

        addCommentItem = new MenuItem(I18nUtil.getString("context.addComment"));
        addCommentItem.setOnAction(e -> onAddComment());

        getItems().addAll(
                gotoDeclarationItem,
                inheritanceGraphItem,
                methodGraphItem,
                new SeparatorMenuItem(),
                addCommentItem
        );

        setOnShowing(e -> refreshState());
    }

    /** 每次显示前更新菜单项状态 */
    private void refreshState() {
        boolean hasMeta = context != null && context.metadata() != null
                && !context.metadata().isEmpty();
        boolean hasBytes = context != null && context.classBytes() != null
                && context.classBytes().length > 0;
        boolean hasIndex = context != null && context.workspace() != null
                && context.workspace().isIndexReady();

        gotoDeclarationItem.setDisable(!hasMeta && !hasIndex);
        inheritanceGraphItem.setDisable(!hasBytes || !hasIndex);
        methodGraphItem.setDisable(!hasBytes);
        addCommentItem.setDisable(codeArea == null);
    }

    private void onGotoDeclaration() {
        if (actionHandler == null || context == null) return;
        int line = getCaretLine();
        var refs = context.metadata().getRefsAtLine(line);
        if (!refs.isEmpty()) {
            actionHandler.goToDeclaration(refs.get(0));
        }
    }

    private void onShowInheritanceGraph() {
        if (actionHandler == null || context == null) return;
        actionHandler.showInheritanceGraph(context);
    }

    private void onShowMethodGraph() {
        if (actionHandler == null || context == null) return;
        actionHandler.showMethodGraph(context);
    }

    private void onAddComment() {
        if (actionHandler == null || context == null) return;
        TextPos caret = codeArea.getCaretPosition();
        actionHandler.addOrUpdateComment(context, caret);
    }

    /** 从 CodeArea 文本中计算当前光标所在行号（1-based） */
    private int getCaretLine() {
        String text = codeArea.getText();
        if (text == null || text.isEmpty()) return 1;
        int idx = codeArea.getCaretPosition().index();
        int line = 1;
        for (int i = 0; i < idx && i < text.length(); i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }
}
