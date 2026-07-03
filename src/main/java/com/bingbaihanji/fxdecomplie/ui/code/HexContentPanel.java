package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader.ThemeData;
import com.bingbaihanji.fxdecomplie.ui.view.HexTabView;
import javafx.scene.Node;

/**
 * HEX 内容面板，使用 {@link HexTabView} 以十六进制表格展示类文件原始字节。
 *
 * @author bingbaihanji
 * @date 2026-06-30
 */
public class HexContentPanel extends AbstractCodeContentPanel {

    private final byte[] classBytes;
    private HexTabView hexTabView;
    private volatile ThemeData theme;

    public HexContentPanel(byte[] classBytes, ThemeData theme) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
        this.theme = theme;
    }

    @Override
    public String getContentType() {
        return "hex";
    }

    @Override
    protected Object buildContentAsync(Object cancelToken) {
        return classBytes;
    }

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

    @Override
    public void dispose() {
        super.dispose();
        hexTabView = null;
    }
}
