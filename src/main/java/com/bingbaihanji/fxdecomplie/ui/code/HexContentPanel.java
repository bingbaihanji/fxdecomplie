package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.hex.ByteArrayProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.HexView;
import com.bingbaihanji.fxdecomplie.ui.hex.HexViewController;
import com.bingbaihanji.fxdecomplie.ui.hex.highlight.JavaBytecodeHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData;
import javafx.scene.Node;

/**
 * HEX 内容面板，使用 {@link HexView} 以十六进制表格展示类文件原始字节，
 * 支持选择、搜索、跳转、复制和 Java 字节码结构高亮
 *
 * @author bingbaihanji
 * @date 2026-06-30
 */
public class HexContentPanel extends AbstractCodeContentPanel {

    static {
        // 注册 Java 字节码结构高亮器
        HexViewController.getInstance().registerHighlighter(new JavaBytecodeHighlighter());
    }

    private final byte[] classBytes;
    private HexView hexView;
    private volatile ThemeData theme;

    /**
     * 构造 HEX 内容面板
     *
     * @param classBytes 类文件原始字节码(内部会做防御性拷贝)
     * @param theme      编辑器主题数据
     */
    public HexContentPanel(byte[] classBytes, ThemeData theme) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.theme = theme;
    }

    private static String hexColor(javafx.scene.paint.Color c) {
        return String.format("#%02X%02X%02X",
                (int) (c.getRed() * 255),
                (int) (c.getGreen() * 255),
                (int) (c.getBlue() * 255));
    }

    /** @return 内容类型标识 "hex" */
    @Override
    public String getContentType() {
        return "hex";
    }

    /** 异步构建内容：直接返回 classBytes，无耗时操作 */
    @Override
    protected Object buildContentAsync(Object cancelToken) {
        return classBytes;
    }

    /** 根据异步结果创建 HexView 作为显示节点 */
    @Override
    protected Node createContent(Object contentData) {
        HexView hex = new HexView();
        applyTheme(hex);
        if (contentData instanceof byte[] bytes) {
            hex.setProvider(new ByteArrayProvider(bytes));
            HexViewController.getInstance().attach(hex);
        }
        this.hexView = hex;
        return hex;
    }

    /** 重新应用编辑器主题 */
    public void reapplyTheme(ThemeData newTheme) {
        this.theme = newTheme;
        if (hexView != null) {
            applyTheme(hexView);
        }
    }

    private void applyTheme(HexView hex) {
        if (theme == null) {
            return;
        }
        String bg = hexColor(theme.editorBackground());
        String fg = hexColor(theme.editorForeground());
        hex.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s;", bg, fg));
    }

    /** @return HEX 视图组件 */
    public HexView getHexView() {
        return hexView;
    }

    /** 释放资源：清理 HexView 引用并调用父类清理 */
    @Override
    public void dispose() {
        super.dispose();
        hexView = null;
    }
}
