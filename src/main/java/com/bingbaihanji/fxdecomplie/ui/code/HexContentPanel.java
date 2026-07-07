package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData;
import com.bingbaihanji.fxdecomplie.ui.view.HexTabView;
import javafx.scene.Node;

/**
 * HEX 内容面板,使用 {@link HexTabView} 以十六进制表格展示类文件原始字节
 *
 * @author bingbaihanji
 * @date 2026-06-30
 */
public class HexContentPanel extends AbstractCodeContentPanel {

    private final byte[] classBytes;
    private HexTabView hexTabView;
    private volatile ThemeData theme;

    /**
     * 构造 HEX 内容面板
     *
     * @param classBytes 类文件原始字节码（内部会做防御性拷贝）
     * @param theme      编辑器主题数据
     */
    public HexContentPanel(byte[] classBytes, ThemeData theme) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.theme = theme;
    }

    /** @return 内容类型标识 "hex" */
    @Override
    public String getContentType() {
        return "hex";
    }

    /** 异步构建内容：直接返回 classBytes,无耗时操作 */
    @Override
    protected Object buildContentAsync(Object cancelToken) {
        return classBytes;
    }

    /** 根据异步结果创建 HexTabView 作为显示节点 */
    @Override
    protected Node createContent(Object contentData) {
        HexTabView hex = new HexTabView(theme);
        if (contentData instanceof byte[] bytes) {
            hex.load(bytes);
        }
        this.hexTabView = hex;
        return hex;
    }

    /** 重新应用编辑器主题 */
    public void reapplyTheme(ThemeData newTheme) {
        this.theme = newTheme;
        if (hexTabView != null) {
            hexTabView.setTheme(newTheme);
        }
    }

    /** @return HEX 视图组件 */
    public HexTabView getHexTabView() {
        return hexTabView;
    }

    /** 释放资源：清理 HexTabView 引用并调用父类清理 */
    @Override
    public void dispose() {
        super.dispose();
        hexTabView = null;
    }
}
