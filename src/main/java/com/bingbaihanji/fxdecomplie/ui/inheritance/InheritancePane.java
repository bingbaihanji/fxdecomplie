package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 类继承层次面板显示当前类的父类链和已知子类
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritancePane extends VBox {

    private final TreeView<InheritanceNode> treeView;
    /** 加载代数计数器,用于丢弃已过期的异步加载结果 */
    private final AtomicLong loadGeneration = new AtomicLong();
    private OpenHandler openHandler;

    /** 构造继承层次面板,初始化标题标签和树形控件 */
    public InheritancePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        Label title = new Label(I18nUtil.getString("inheritance.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        treeView = new TreeView<>();
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(InheritanceNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String prefix = switch (item.relation()) {
                        case SELF -> "";
                        case SUPER_CLASS -> "↑ ";
                        case SUBCLASS -> "↓ ";
                        case INTERFACE -> "I ";
                    };
                    setText(prefix + item.displayName());
                    Color c = item.relation() == InheritanceNode.RelationType.SELF ? Color.web("#dcdcaa")
                            : item.relation() == InheritanceNode.RelationType.SUPER_CLASS ? Color.web("#c586c0")
                              : Color.web("#9cdcfe");
                    setTextFill(c);
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && openHandler != null) {
                TreeItem<InheritanceNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null
                        && item.getValue().relation() != InheritanceNode.RelationType.SELF) {
                    openHandler.open(item.getValue().className());
                }
            }
        });

        getChildren().addAll(title, treeView);
    }

    /**
     * 加载指定类的继承层次树
     * <p>先显示"加载中"状态,然后在后台线程构建继承树,通过代数计数器确保只展示最新请求的结果</p>
     *
     * @param fullPath 目标类全限定路径
     * @param index    工作区索引,用于读取父类/子类字节码
     */
    public void load(String fullPath, WorkspaceIndex index) {
        load(fullPath, index, null);
    }

    /**
     * 加载指定类的继承层次树,允许传入当前类字节码以便索引未完成时快速展示局部继承信息
     *
     * @param fullPath 目标类全限定路径
     * @param index    工作区索引,用于读取父类/子类字节码
     * @param rootBytes 当前类字节码,可为空
     */
    public void load(String fullPath, WorkspaceIndex index, byte[] rootBytes) {
        showIndexing();
        // 递增代数,标记本次请求
        long gen = loadGeneration.incrementAndGet();
        BackgroundTasks.run("InheritanceBuild", () -> {
            TreeItem<InheritanceNode> root = InheritanceService.buildTree(fullPath, index, rootBytes);
            Platform.runLater(() -> {
                if (loadGeneration.get() != gen) {
                    return; // 已过期,丢弃
                }
                if (root != null) {
                    treeView.setRoot(root);
                    root.setExpanded(true);
                } else {
                    showUnavailable();
                }
            });
        });
    }

    /** 显示"正在索引中"提示 */
    public void showIndexing() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.indexing"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    /** 显示"继承信息不可用"提示 */
    public void showUnavailable() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.unavailable"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    /** 显示"等待索引构建"提示 */
    public void showIndexPending() {
        treeView.setRoot(new TreeItem<>(new InheritanceNode("",
                I18nUtil.getString("inheritance.indexPending"),
                InheritanceNode.RelationType.SELF, 0)));
    }

    /** 清空树形视图内容 */
    public void clear() {
        treeView.setRoot(null);
    }

    /** 设置双击树节点的打开处理器 */
    public void setOpenHandler(OpenHandler handler) {
        this.openHandler = handler;
    }

    /** 树节点双击打开回调接口 */
    @FunctionalInterface
    public interface OpenHandler {
        void open(String className);
    }
}
