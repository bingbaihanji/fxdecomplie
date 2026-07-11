package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.CodeLinkHandler;
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
 * 代码区右键上下文菜单,提供跳转声明、查看继承图、查看方法图、添加注释四个功能
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class CodeAreaContextMenu extends ContextMenu {

    private static final Logger log = LoggerFactory.getLogger(CodeAreaContextMenu.class);

    private final CodeArea codeArea;
    private final CodeViewContext context;
    private final CodeActionHandler actionHandler;

    private final MenuItem gotoDeclarationItem;
    private final MenuItem inheritanceGraphItem;
    private final MenuItem methodGraphItem;
    private final MenuItem controlFlowGraphItem;
    private final MenuItem addCommentItem;
    private final MenuItem deleteCommentItem;
    /** 右键点击位置,用于确定操作目标 */
    private TextPos actionPosition;

    /**
     * 构造右键上下文菜单
     *
     * @param codeArea      代码编辑器区域
     * @param context       代码视图上下文
     * @param actionHandler 菜单动作处理器
     */
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

    /** 根据右键事件计算点击位置并选中对应文本,为后续操作做准备 */
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

    /** 跳转到光标处符号的声明位置 */
    private void onGotoDeclaration() {
        if (actionHandler == null || context == null || codeArea == null) {
            return;
        }
        int line = currentLine();
        actionHandler.goToDeclaration(context, line, tokenAtActionPosition());
    }

    /** 显示当前类的继承关系图 */
    private void onShowInheritanceGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        log.info("代码区右键菜单触发查看继承图: {}", context.classInternalName());
        actionHandler.showInheritanceGraph(context);
    }

    /** 显示当前类的方法调用关系图 */
    private void onShowMethodGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        log.info("代码区右键菜单触发查看方法图: {}", context.classInternalName());
        actionHandler.showMethodGraph(context);
    }

    /** 显示当前方法的控制流图(CFG) */
    private void onShowControlFlowGraph() {
        if (actionHandler == null || context == null) {
            return;
        }
        log.info("代码区右键菜单触发查看CFG: {}", context.classInternalName());
        actionHandler.showControlFlowGraph(context);
    }

    /** 在当前光标位置添加或更新注释 */
    private void onAddComment() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        actionHandler.addOrUpdateComment(context, caret);
    }

    /** 对光标处的符号执行重命名操作 */
    private void onRename() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        actionHandler.renameAtCaret(context, caret);
    }

    /** 删除光标所在行的注释 */
    private void onDeleteComment() {
        if (actionHandler == null || context == null) {
            return;
        }
        TextPos caret = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        int line = caret == null ? 1 : Math.max(1, caret.index() + 1);
        actionHandler.deleteComment(context, line);
    }

    /** @return 当前操作位置所在行号(1-based),优先使用右键位置,其次使用光标位置 */
    private int currentLine() {
        TextPos pos = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        return pos == null ? 1 : Math.max(1, pos.index() + 1);
    }

    /** @return 当前操作位置处的代码符号(用于导航跳转) */
    private String tokenAtActionPosition() {
        TextPos pos = actionPosition != null ? actionPosition : codeArea.getCaretPosition();
        return CodeLinkHandler.navigationTokenAt(codeArea.getText(), pos);
    }
}
