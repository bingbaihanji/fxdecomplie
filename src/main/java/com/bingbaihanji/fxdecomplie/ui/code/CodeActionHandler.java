package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import jfx.incubator.scene.control.richtext.TextPos;

/**
 * 代码视图操作回调接口,定义 CodeViewPanel 需要调用的外部操作
 *
 * <p>实现方(如 MainWindow)负责具体逻辑：导航、图形展示、注释管理等</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public interface CodeActionHandler {

    /** 跳转到声明(Ctrl+Click / 右键菜单) */
    void goToDeclaration(CodeMetadata.Reference reference);

    /**
     * 跳转到声明(右键菜单)
     *
     * <p>默认仍按行级 metadata 跳转 实现方可以结合当前 token 和工作区索引做兜底解析</p>
     */
    default void goToDeclaration(CodeViewContext context, int lineNumber, String token) {
        if (context == null || context.metadata() == null) {
            return;
        }
        var refs = context.metadata().getRefsAtLine(lineNumber);
        if (!refs.isEmpty()) {
            goToDeclaration(refs.get(0));
        }
    }

    /** 打开指定类的指定行 */
    void openClass(String fullPath, int line);

    /** 展示当前类的继承图 */
    void showInheritanceGraph(CodeViewContext context);

    /** 展示当前类的方法调用图 */
    void showMethodGraph(CodeViewContext context);

    /** 添加或更新当前光标位置的注释 */
    void addOrUpdateComment(CodeViewContext context, TextPos caretPosition);

    /** 删除光标所在行的注释 */
    default void deleteComment(CodeViewContext context, int line) {
    }

    /** 显示当前方法的控制流图 */
    default void showControlFlowGraph(CodeViewContext context) {
    }

    /** 重命名光标处的标识符 */
    default void renameAtCaret(CodeViewContext context, jfx.incubator.scene.control.richtext.TextPos caret) {
    }

    /** 在 workspace 内搜索选中文本 */
    void searchInWorkspace(String selectedText);

    /**
     * 复制引用字符串到剪贴板的回调(Alt+Ctrl+Shift+C) 
     * 引用字符串已由 CodeViewPanel 通过 {@link com.bingbaihanji.fxdecomplie.util.reference.CopyReferenceHelper} 计算,
     * 子类可覆盖此方法以显示状态栏提示 
     *
     * @param referenceText 已生成的引用字符串
     */
    default void copyReference(String referenceText) {
        // 子类可覆盖以显示状态栏提示
    }
}
