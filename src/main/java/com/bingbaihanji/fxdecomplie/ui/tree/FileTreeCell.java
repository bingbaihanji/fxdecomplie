package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 文件树单元格渲染器,根据节点类型显示对应的图标和样式
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeCell extends TreeCell<FileTreeNode> {

    private static final Logger logger = LoggerFactory.getLogger(FileTreeCell.class);

    /** 树节点图标统一尺寸 */
    private static final double ICON_SIZE = 16.0;

    /** 包(文件夹)图标 */
    private static final Image PACKAGE_ICON = loadIcon("/icon/package.png");
    /** 字节码(.class)图标 */
    private static final Image CLASS_FILE_ICON = loadIcon("/icon/javabytecode.png");

    private static Image loadIcon(String path) {
        try {
            var stream = FileTreeCell.class.getResourceAsStream(path);
            if (stream != null) {
                return new Image(stream);
            }
        } catch (Exception ignored) {
            logger.debug("加载文件树图标失败: {}", path, ignored);
        }
        return null;
    }

    private static ImageView createIcon(FileTreeNode item) {
        ImageView iv = new ImageView();
        iv.setFitWidth(ICON_SIZE);
        iv.setFitHeight(ICON_SIZE);
        iv.setPreserveRatio(true);
        Image image = resolveImage(item);
        if (image != null) {
            iv.setImage(image);
        }
        // 添加 CSS 类用于主题样式
        iv.getStyleClass().add("file-tree-icon");
        iv.getStyleClass().add(iconStyleClass(item));
        return iv;
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

    /** 节点类型 → CSS 样式类名(用于 hover/选中变色等) */
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
        } else {
            ImageView iconView = createIcon(item);
            setText(item.getName());
            setGraphic(iconView);
        }
    }
}
