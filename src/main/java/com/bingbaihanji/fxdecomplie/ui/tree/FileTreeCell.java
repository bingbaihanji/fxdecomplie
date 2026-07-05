package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

/**
 * 文件树单元格渲染器,根据节点类型显示对应的图标和样式
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeCell extends TreeCell<FileTreeNode> {

    private static final Logger log = LoggerFactory.getLogger(FileTreeCell.class);

    /** 树节点图标统一尺寸 */
    private static final double ICON_SIZE = 16.0;

    /** 包(文件夹)图标 */
    private static final Image PACKAGE_ICON = loadIcon("/icon/package.png");
    /** 字节码(.class)图标 */
    private static final Image CLASS_FILE_ICON = loadIcon("/icon/javabytecode.png");
    private final Function<FileTreeNode, String> displayNameProvider;

    /**
     * 复用的 ImageView，避免滚动回收时频繁创建/销毁 JavaFX 节点。
     * 滚动时 updateItem() 调用频率极高，每次 new ImageView() 会产生大量 GC 压力导致卡顿。
     */
    private final ImageView iconView;

    /** 当前图标对应的样式类名，用于增量更新避免不必要的 removeAll+add 操作 */
    private String currentIconStyleClass;

    public FileTreeCell() {
        this(null);
    }

    public FileTreeCell(Function<FileTreeNode, String> displayNameProvider) {
        this.displayNameProvider = displayNameProvider;
        this.iconView = new ImageView();
        this.iconView.setFitWidth(ICON_SIZE);
        this.iconView.setFitHeight(ICON_SIZE);
        this.iconView.setPreserveRatio(true);
        this.iconView.setSmooth(true);
        // 开启位图缓存：滚动时复用已渲染的像素快照，减少重绘开销
        setCache(true);
    }

    private static Image loadIcon(String path) {
        try (var stream = FileTreeCell.class.getResourceAsStream(path)) {
            if (stream != null) {
                return new Image(stream);
            }
        } catch (Exception ignored) {
            log.debug("加载文件树图标失败: {}", path, ignored);
        }
        return null;
    }

    /** 根据节点类型解析对应图标,无匹配时返回 null(不显示图标) */
    private static Image resolveImage(FileTreeNode item) {
        return switch (item.getNodeType()) {
            case PACKAGE -> PACKAGE_ICON;
            case CLASS_FILE -> CLASS_FILE_ICON;
            // 其他类型暂保留无图标,后续可扩展
            default -> null;
        };
    }

    /** 节点类型 → CSS 样式类名 */
    private static String iconStyleClass(FileTreeNode item) {
        return switch (item.getNodeType()) {
            case PACKAGE -> "file-tree-icon-package";
            case CLASS_FILE -> "file-tree-icon-class";
            case JAVA_FILE -> "file-tree-icon-java";
            case RESOURCE -> "file-tree-icon-resource";
            case BINARY -> "file-tree-icon-binary";
        };
    }

    @Override
    protected void updateItem(FileTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
            return;
        }

        // 复用 ImageView，只切换 Image 引用和样式类（避免每次 new ImageView()）
        Image image = resolveImage(item);
        iconView.setImage(image);

        // 增量更新 CSS 样式类，避免 removeAll + add 导致的样式闪烁
        String newStyleClass = iconStyleClass(item);
        if (currentIconStyleClass != null && !currentIconStyleClass.equals(newStyleClass)) {
            iconView.getStyleClass().remove(currentIconStyleClass);
        }
        if (!iconView.getStyleClass().contains(newStyleClass)) {
            // 首次使用或样式变更后才添加
            iconView.getStyleClass().removeIf(s -> s.startsWith("file-tree-icon-") && !s.equals(newStyleClass));
            iconView.getStyleClass().add(newStyleClass);
        }
        if (!iconView.getStyleClass().contains("file-tree-icon")) {
            iconView.getStyleClass().add("file-tree-icon");
        }
        currentIconStyleClass = newStyleClass;

        String displayName = displayNameProvider == null ? null : displayNameProvider.apply(item);
        setText(displayName == null || displayName.isBlank() ? item.getName() : displayName);
        setGraphic(iconView);
    }

    /**
     * 从外部强制刷新 cell 的显示文本。
     *
     * <p>由于 FileTreeNode 是不可变的（显示名由外部 rename 映射动态决定），
     * TreeView 无法感知到需要更新。此方法供 FileTreeView 在 rename 后调用，
     * 直接触发 updateItem 以获取最新的 displayName。</p>
     */
    public void refreshDisplay() {
        updateItem(getItem(), isEmpty());
    }
}
