package com.bingbaihanji.fxdecomplie.ui;

import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.NavigationService;
import com.bingbaihanji.fxdecomplie.ui.tree.FileTreeView;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * 文件树右键上下文菜单 — 从 {@link WorkspaceTabManager} 中提取的独立子系统。
 *
 * <p>负责在文件树视图中安装右键菜单并响应各类节点操作：
 * 打开、Hex查看、导航、导出、搜索、复制路径、展开/折叠等。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
final class TreeContextMenuHelper {

    private TreeContextMenuHelper() {
        throw new AssertionError("utility class");
    }

    /**
     * 在文件树视图中安装右键上下文菜单。
     *
     * @param treeView          文件树
     * @param workspace         当前工作区
     * @param codeTabPane       代码标签页面板
     * @param navigationService 导航服务
     * @param onClassClick      类节点点击回调
     * @param onTextFileClick   文本文件节点点击回调
     * @param onExportNode      导出节点回调
     * @param onFindUsage       查找使用回调
     * @param onSearchPackage   搜索包回调
     * @param onHexClick        Hex查看回调
     */
    static void installTreeContextMenu(FileTreeView treeView, Workspace workspace,
                                        TabPane codeTabPane, NavigationService navigationService,
                                        BiConsumer<FileTreeNode, TabPane> onClassClick,
                                        BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                        WorkspaceTabManager.TreeItemAction onExportNode,
                                        Consumer<FileTreeNode> onFindUsage,
                                        Consumer<FileTreeNode> onSearchPackage,
                                        BiConsumer<FileTreeNode, TabPane> onHexClick) {
        String workspaceHash = CommentScope.workspaceHash(workspace);
        treeView.setCellFactory(tv -> new com.bingbaihanji.fxdecomplie.ui.tree.FileTreeCell(
                node -> displayTreeNodeName(workspaceHash, node)));

        ContextMenu cachedMenu = new ContextMenu();
        treeView.addEventFilter(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            TreeItem<FileTreeNode> item = findTreeItemForContextMenuEvent(event, treeView);
            if (item == null || item.getValue() == null) return;
            treeView.getSelectionModel().select(item);
            rebuildTreeContextMenuItems(cachedMenu, item, workspace, codeTabPane,
                    navigationService, onClassClick, onTextFileClick, onExportNode,
                    onFindUsage, onSearchPackage, onHexClick);
            cachedMenu.show(treeView, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    @SuppressWarnings("unchecked")
    private static TreeItem<FileTreeNode> findTreeItemForContextMenuEvent(
            ContextMenuEvent event, TreeView<FileTreeNode> treeView) {
        Node target = event.getPickResult().getIntersectedNode();
        while (target != null) {
            if (target instanceof TreeCell<?> cell && cell.getTreeItem() != null) {
                return (TreeItem<FileTreeNode>) cell.getTreeItem();
            }
            target = target.getParent();
        }
        return treeView.getSelectionModel().getSelectedItem();
    }

    static String displayTreeNodeName(String workspaceHash, FileTreeNode node) {
        if (node == null) return "";
        if (!node.isClassFile() || workspaceHash.isBlank()) return node.getName();
        String displayName = RenameService.displayClassName(node.getFullPath(), workspaceHash);
        return displayName == null || displayName.isBlank()
                ? node.getName() : displayName + ".class";
    }

    private static void rebuildTreeContextMenuItems(ContextMenu menu,
                                                     TreeItem<FileTreeNode> item,
                                                     Workspace workspace,
                                                     TabPane codeTabPane,
                                                     NavigationService navigationService,
                                                     BiConsumer<FileTreeNode, TabPane> onClassClick,
                                                     BiConsumer<FileTreeNode, TabPane> onTextFileClick,
                                                     WorkspaceTabManager.TreeItemAction onExportNode,
                                                     Consumer<FileTreeNode> onFindUsage,
                                                     Consumer<FileTreeNode> onSearchPackage,
                                                     BiConsumer<FileTreeNode, TabPane> onHexClick) {
        menu.getItems().clear();
        FileTreeNode node = item.getValue();

        MenuItem open = new MenuItem(I18nUtil.getString("context.open"));
        open.setDisable(!node.isClassFile() && !node.isTextFile());
        open.setOnAction(e -> {
            if (node.isClassFile()) {
                navigationService.openPath(navigationService.classPath(workspace, node),
                        workspace, codeTabPane, onClassClick, onTextFileClick);
            } else if (node.isTextFile()) {
                navigationService.openPath(navigationService.resourcePath(workspace, node),
                        workspace, codeTabPane, onClassClick, onTextFileClick);
            }
        });

        MenuItem openHex = new MenuItem("Open in Hex");
        openHex.setDisable(node.isClassFile());
        openHex.setOnAction(e -> onHexClick.accept(node, codeTabPane));

        MenuItem export = new MenuItem(I18nUtil.getString("context.exportNode"));
        export.setOnAction(e -> onExportNode.accept(item));

        MenuItem findUsage = new MenuItem(I18nUtil.getString("context.findUsages"));
        findUsage.setDisable(!node.isClassFile());
        findUsage.setOnAction(e -> onFindUsage.accept(node));

        MenuItem searchPackage = new MenuItem(I18nUtil.getString("context.searchPackage"));
        searchPackage.setOnAction(e -> onSearchPackage.accept(node));

        MenuItem back = new MenuItem(I18nUtil.getString("context.back"));
        back.setDisable(!navigationService.canGoBack());
        back.setOnAction(e -> navigationService.goBack(workspace, codeTabPane,
                onClassClick, onTextFileClick));

        MenuItem forward = new MenuItem(I18nUtil.getString("context.forward"));
        forward.setDisable(!navigationService.canGoForward());
        forward.setOnAction(e -> navigationService.goForward(workspace, codeTabPane,
                onClassClick, onTextFileClick));

        MenuItem copyPath = new MenuItem(I18nUtil.getString("context.copyPath"));
        copyPath.setOnAction(e -> copyToClipboard(node.getFullPath()));

        MenuItem copyClassName = new MenuItem(I18nUtil.getString("context.copyClassName"));
        copyClassName.setDisable(!node.isClassFile());
        copyClassName.setOnAction(e -> {
            String fp = node.getFullPath();
            String name = fp.endsWith(".class") ? fp.substring(0, fp.length() - 6) : fp;
            copyToClipboard(name.replace('/', '.').replace('\\', '.'));
        });

        MenuItem expand = new MenuItem(I18nUtil.getString("context.expand"));
        expand.setOnAction(e -> expandAll(item));

        MenuItem collapse = new MenuItem(I18nUtil.getString("context.collapse"));
        collapse.setOnAction(e -> collapseAll(item));

        menu.getItems().addAll(open, openHex, back, forward, export, findUsage,
                searchPackage, new SeparatorMenuItem(),
                copyPath, copyClassName, new SeparatorMenuItem(), expand, collapse);
    }

    private static void copyToClipboard(String value) {
        ClipboardContent content = new ClipboardContent();
        content.putString(value);
        Clipboard.getSystemClipboard().setContent(content);
    }

    private static void expandAll(TreeItem<FileTreeNode> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(true);
            item.getChildren().forEach(TreeContextMenuHelper::expandAll);
        }
    }

    private static void collapseAll(TreeItem<FileTreeNode> item) {
        if (item != null && !item.isLeaf()) {
            item.setExpanded(false);
            item.getChildren().forEach(TreeContextMenuHelper::collapseAll);
        }
    }
}
