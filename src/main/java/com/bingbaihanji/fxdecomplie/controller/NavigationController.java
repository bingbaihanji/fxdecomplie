package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.ClassNodeResolver;
import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.ui.WorkspaceView;
import com.bingbaihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbaihanji.fxdecomplie.ui.code.CodeViewContext;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.fxdecomplie.util.text.JavaSourceAnalyzer;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 代码导航控制器：Ctrl+Click 跳转声明、引用解析、类名/令牌到文件树节点的多策略查找
 * <p>
 * 从 MainWindow 拆分而来，通过 owner 访问共享状态与协作者 (Mediator 模式)
 * 所有协作者均在调用时通过 owner 访问，以适应 tabManager/classTabOpener 延迟初始化
 *
 * @author bingbaihanji
 */
public final class NavigationController {

    private static final Logger log = LoggerFactory.getLogger(NavigationController.class);

    private final MainWindow owner;

    public NavigationController(MainWindow owner) {
        this.owner = owner;
    }

    public void goToDeclaration(CodeMetadata.Reference reference) {
        if (reference == null || reference.targetClass() == null) {
            return;
        }
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null) {
            return;
        }
        FileTreeNode node = ClassNodeResolver.findNodeForReference(view.workspace(), reference);
        if (node != null) {
            owner.classTabOpener().openClassTab(node, view.workspace(), view.codeTabPane(),
                    owner.currentEngine(), owner.lineNumbersEnabled());
        } else {
            owner.statusBar().setFilePath(I18nUtil.getString("status.locateFailed", reference.targetClass()));
        }
    }

    public void goToDeclaration(CodeViewContext context, int lineNumber, String token) {
        goToDeclaration(context, lineNumber, token, -1);
    }

    public void goToDeclaration(CodeViewContext context, int lineNumber, String token, int columnHint) {
        if (context == null || context.workspace() == null) {
            return;
        }
        String targetToken = JavaSourceAnalyzer.sanitizeDeclarationToken(token);

        // 当点击的是当前类声明名时,不跳转(避免同名类跨包误导航)
        if (isCurrentClassDeclaration(context, lineNumber, targetToken)) {
            return;
        }
        Workspace workspace = context.workspace();
        WorkspaceIndex index = workspace.isIndexReady() ? workspace.getIndex() : context.workspaceIndex();
        String sourceCode = context.openFile() == null ? "" : context.openFile().sourceCode();

        if (shouldPreferPackageNavigation(sourceCode, lineNumber, targetToken)) {
            FileTreeNode packageNode = ClassNodeResolver.findPackageNodeForToken(workspace, targetToken,
                    context.classInternalName(), sourceCode, lineNumber);
            if (packageNode != null) {
                revealNodeInTree(workspace, packageNode);
                return;
            }
        }

        if (context.metadata() != null) {
            var refs = context.metadata().getRefsAtLine(lineNumber);
            if (!refs.isEmpty()) {
                // 计算点击处的列号,用于同名类精确匹配
                int column = columnHint >= 0 ? columnHint : computeClickColumn(context, lineNumber, token);
                CodeMetadata.Reference selected = selectReference(refs, token, column);
                if (openReferenceInWorkspace(context.workspace(), selected)) {
                    return;
                }
            }
        }

        if (targetToken.isBlank()) {
            owner.statusBar().setFilePath(I18nUtil.getString("status.locateFailed", ""));
            return;
        }

        owner.statusBar().setFilePath(I18nUtil.getString("status.locating", targetToken));

        boolean preferClassNavigation = JavaSourceAnalyzer.shouldPreferClassNavigation(targetToken)
                || JavaSourceAnalyzer.looksLikeClassUsageAtLine(sourceCode, lineNumber, targetToken);
        FileTreeNode node = null;
        if (preferClassNavigation) {
            node = ClassNodeResolver.findNodeForToken(workspace, index, targetToken,
                    context.classInternalName(), sourceCode, true);
            if (node != null) {
                openNodeInWorkspace(workspace, node);
                return;
            }
        }

        if (revealDeclarationInCurrentTab(context, lineNumber, targetToken)) {
            return;
        }

        FileTreeNode packageNode = ClassNodeResolver.findPackageNodeForToken(workspace, targetToken,
                context.classInternalName(), sourceCode, lineNumber);
        if (packageNode != null) {
            revealNodeInTree(workspace, packageNode);
            return;
        }

        if (!preferClassNavigation) {
            if (JavaSourceAnalyzer.shouldSearchWorkspaceForClassToken(targetToken)) {
                node = ClassNodeResolver.findNodeForToken(workspace, index, targetToken,
                        context.classInternalName(), sourceCode, true);
            }
        }
        if (node != null) {
            openNodeInWorkspace(workspace, node);
            return;
        }

        owner.statusBar().setFilePath(I18nUtil.getString("status.locateFailed", targetToken));
    }

    private boolean shouldPreferPackageNavigation(String sourceCode, int lineNumber, String token) {
        if (sourceCode == null || sourceCode.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return false;
        }
        String line = JavaSourceAnalyzer.stripLineComment(lines[lineNumber - 1]).strip();
        if (line.startsWith("package ")) {
            return true;
        }
        if (!line.startsWith("import ")) {
            return false;
        }
        if (line.endsWith(".*;")) {
            return true;
        }
        String clean = JavaSourceAnalyzer.sanitizeDeclarationToken(token);
        String imported = line.substring("import ".length()).strip();
        if (imported.startsWith("static ")) {
            imported = imported.substring("static ".length()).strip();
        }
        if (imported.endsWith(";")) {
            imported = imported.substring(0, imported.length() - 1).strip();
        }
        String simpleImportName = JavaSourceAnalyzer.tokenSimpleName(imported);
        return imported.contains(".") && !simpleImportName.equals(clean);
    }

    /**
     * 判断点击处是否为当前类的声明名
     * 防止同名类跨包误导航(如 com.pig4cloud.service.a 和 com.pig4cloud.domain.a)
     */
    private boolean isCurrentClassDeclaration(CodeViewContext context, int lineNumber, String token) {
        if (token == null || token.isBlank() || context.classInternalName() == null) {
            return false;
        }
        String currentSimpleName = JavaSourceAnalyzer.classLeafName(context.classInternalName());
        if (!token.equals(currentSimpleName)) {
            return false;
        }
        String sourceCode = context.openFile() == null ? "" : context.openFile().sourceCode();
        if (sourceCode.isBlank()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return false;
        }
        String line = JavaSourceAnalyzer.stripLineComment(lines[lineNumber - 1]).strip();
        return line.contains("class " + token) || line.contains("interface " + token)
                || line.contains("enum " + token) || line.contains("record " + token)
                || line.contains("@interface " + token);
    }

    public void openClass(String fullPath, int line) {
        WorkspaceView view = owner.tabManager().currentWorkspaceView();
        if (view == null || fullPath == null) {
            return;
        }
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node != null) {
            owner.classTabOpener().openClassTab(node, view.workspace(), view.codeTabPane(),
                    owner.currentEngine(), owner.lineNumbersEnabled());
        }
    }

    /** 根据引用信息在工作区中定位并打开目标类 */
    private boolean openReferenceInWorkspace(Workspace workspace, CodeMetadata.Reference reference) {
        FileTreeNode node = ClassNodeResolver.findNodeForReference(workspace, reference);
        if (node != null) {
            openNodeInWorkspace(workspace, node);
            return true;
        }
        return false;
    }

    /**
     * 计算用户点击处 token 在源码行中的起始列号
     *
     * <p>从反编译源码中定位指定行,找到 token 在该行中首次出现的位置
     * 用于在多个同名引用中精确匹配用户点击的那一个 </p>
     *
     * @param context    代码视图上下文
     * @param lineNumber 行号(从 1 开始)
     * @param token      点击处的标识符
     * @return token 在行中的起始列号(从 0 开始),-1 表示未找到
     */
    private int computeClickColumn(CodeViewContext context, int lineNumber, String token) {
        if (context == null || context.openFile() == null || token == null || token.isBlank()) {
            return -1;
        }
        String sourceCode = context.openFile().sourceCode();
        if (sourceCode == null || sourceCode.isBlank()) {
            return -1;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        if (lineNumber < 1 || lineNumber > lines.length) {
            return -1;
        }
        String line = lines[lineNumber - 1];
        String cleanToken = JavaSourceAnalyzer.sanitizeDeclarationToken(token);
        if (cleanToken.isBlank()) {
            return -1;
        }
        int best = -1;
        int searchFrom = 0;
        while (searchFrom < line.length()) {
            int idx = line.indexOf(cleanToken, searchFrom);
            if (idx < 0) {
                break;
            }
            int end = idx + cleanToken.length();
            boolean leftBoundary = idx == 0 || !Character.isJavaIdentifierPart(line.charAt(idx - 1));
            boolean rightBoundary = end >= line.length() || !Character.isJavaIdentifierPart(line.charAt(end));
            if (leftBoundary && rightBoundary) {
                if (best < 0) {
                    best = idx;
                }
                int tokenSimpleStart = cleanToken.lastIndexOf('.') + 1;
                if (tokenSimpleStart <= 0 || idx + tokenSimpleStart >= line.length()) {
                    return idx;
                }
                return idx + tokenSimpleStart;
            }
            searchFrom = idx + cleanToken.length();
        }
        return best;
    }

    /**
     * 从引用列表中选择与 token 匹配的引用,支持按列位置精确匹配
     *
     * <p>当同一行有多个同简单名的引用(如混淆后的多个 {@code a} 类)时,
     * 列位置匹配可以精确选择用户点击的那个引用,避免误导航 </p>
     *
     * @param refs   行上的所有引用
     * @param token  点击处的标识符
     * @param column 点击处的列号(从 0 开始,-1 表示未知)
     * @return 匹配的引用
     */
    private CodeMetadata.Reference selectReference(List<CodeMetadata.Reference> refs,
                                                   String token, int column) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        String targetToken = JavaSourceAnalyzer.sanitizeDeclarationToken(token);
        if (!targetToken.isBlank()) {
            String simpleToken = JavaSourceAnalyzer.tokenSimpleName(targetToken);
            // 分两轮匹配：第一轮找有精确列位置的引用,第二轮找无列位置的引用
            // 这样字节码增强的引用(有 columnStart)优先于正则提取的引用(columnStart=-1)
            CodeMetadata.Reference bestWithColumn = null;
            int closestDistance = Integer.MAX_VALUE;
            CodeMetadata.Reference fallbackNoColumn = null;

            for (CodeMetadata.Reference ref : refs) {
                if (ref.targetClass() == null) {
                    continue;
                }
                String normalized = ref.targetClass().replace('.', '/');
                // 完全限定名精确匹配(最高优先级)
                if (ref.targetClass().equals(targetToken)
                        || normalized.equals(targetToken.replace('.', '/'))) {
                    return ref;
                }
                // 简单名匹配
                if (JavaSourceAnalyzer.tokenSimpleName(normalized).equals(simpleToken)) {
                    if (ref.columnStart() >= 0) {
                        // 有列位置的引用(来自字节码增强)：按距离选最近的
                        int distance = column >= 0
                                ? Math.abs(ref.columnStart() - column)
                                : 0; // 无点击列信息时,取第一个有列位置的
                        if (bestWithColumn == null || distance < closestDistance) {
                            closestDistance = distance;
                            bestWithColumn = ref;
                        }
                    } else if (fallbackNoColumn == null) {
                        // 无列位置的引用(来自正则提取)：仅作为兜底
                        fallbackNoColumn = ref;
                    }
                }
            }
            // 优先返回有列位置的匹配(字节码增强的精确引用)
            if (bestWithColumn != null) {
                return bestWithColumn;
            }
            if (fallbackNoColumn != null) {
                return fallbackNoColumn;
            }
            return null;
        }
        return refs.getFirst();
    }

    /** 在当前工作区中打开指定树节点对应的类标签页 */
    private void openNodeInWorkspace(Workspace workspace, FileTreeNode node) {
        WorkspaceView view = owner.workspaceViewFor(workspace);
        if (view == null || node == null) {
            return;
        }
        owner.classTabOpener().openClassTab(node, workspace, view.codeTabPane(),
                owner.currentEngine(), owner.lineNumbersEnabled());
    }

    private void revealNodeInTree(Workspace workspace, FileTreeNode node) {
        WorkspaceView view = owner.workspaceViewFor(workspace);
        if (view == null || node == null) {
            return;
        }
        owner.tabManager().selectTreeNodeByPath(node.getFullPath());
        owner.statusBar().setFilePath(I18nUtil.getString("status.navigatedTo",
                node.getFullPath(), 1));
    }

    /** 尝试在当前代码标签页中定位并滚动到 token 的声明行 */
    private boolean revealDeclarationInCurrentTab(CodeViewContext context, int clickedLine,
                                                  String token) {
        if (context == null || token == null || token.isBlank()
                || context.openFile() == null) {
            return false;
        }
        String source = context.openFile().sourceCode();
        int declarationLine = JavaSourceAnalyzer.findDeclarationLine(source, token, clickedLine);
        if (declarationLine <= 0) {
            return false;
        }
        WorkspaceView view = owner.workspaceViewFor(context.workspace());
        if (view == null) {
            return false;
        }
        String fullPath = context.openFile().fullPath();
        for (TabPane pane : view.splitEditorPane().allTabPanes()) {
            for (Tab tab : pane.getTabs()) {
                if (tab instanceof CodeEditorTab codeTab
                        && fullPath.equals(codeTab.getOpenFile().fullPath())
                        && context.openFile().engine() == codeTab.getOpenFile().engine()) {
                    pane.getSelectionModel().select(codeTab);
                    codeTab.revealLine(declarationLine);
                    owner.statusBar().setFilePath(I18nUtil.getString(
                            "status.navigatedTo", fullPath, declarationLine));
                    return true;
                }
            }
        }
        return false;
    }

    /** BFS 遍历整个文件树按简单类名查找节点(委托 {@link ClassNodeResolver},保留供 RenameController 调用) */
    public FileTreeNode findNodeBySimpleNameInTree(Workspace workspace, String token,
                                                   String currentClassName) {
        return ClassNodeResolver.findNodeBySimpleNameInTree(workspace, token, currentClassName);
    }

    /** 在工作区中按完整路径打开类并延迟跳转到指定行(搜索/FindUsages 双击回调) */
    public void openClassByPath(WorkspaceView view, String fullPath, int lineNumber) {
        FileTreeNode node = view.workspace().findNodeByPath(fullPath);
        if (node != null) {
            owner.classTabOpener().openClassTab(node, view.workspace(), view.codeTabPane(),
                    owner.currentEngine(), owner.lineNumbersEnabled());
            // 反编译完成后导航到目标行
            navigateToLine(view, fullPath, lineNumber, 0);
        }
    }

    /** 延迟轮询工作区标签页,等待反编译完成并将 CodeArea 滚动到目标行(最多 2 秒) */
    private void navigateToLine(WorkspaceView view, String fullPath, int lineNumber, int retries) {
        // 最多约 2 秒
        if (retries > 20) {
            owner.statusBar().setFilePath(I18nUtil.getString("status.navigateTimeout", fullPath));
            return;
        }
        if (!owner.tabManager().isWorkspaceActive(view)) {
            return; // 工作区已关闭
        }
        javafx.animation.PauseTransition delay = new javafx.animation.PauseTransition(
                javafx.util.Duration.millis(100));
        delay.setOnFinished(e -> {
            if (!owner.tabManager().isWorkspaceActive(view)) {
                return;
            }
            for (TabPane pane : view.splitEditorPane().allTabPanes()) {
                for (javafx.scene.control.Tab tab : pane.getTabs()) {
                    if (tab instanceof CodeEditorTab codeTab
                            && codeTab.getOpenFile() != null
                            && codeTab.getOpenFile().fullPath().equals(fullPath)) {
                        var area = codeTab.getCodeArea();
                        if (area.getText() != null && !area.getText().isEmpty()) {
                            try {
                                codeTab.revealLine(lineNumber);
                                owner.statusBar().setFilePath(I18nUtil.getString(
                                        "status.navigatedTo", fullPath, lineNumber));
                            } catch (Exception ignored) {
                                log.debug("导航跳转行失败", ignored);
                            }
                            return;
                        }
                        navigateToLine(view, fullPath, lineNumber, retries + 1);
                        return;
                    }
                }
            }
        });
        delay.play();
    }
}
