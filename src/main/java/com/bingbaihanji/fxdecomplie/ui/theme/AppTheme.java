package com.bingbaihanji.fxdecomplie.ui.theme;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * 应用主题工具类,负责加载暗色样式表和编辑器主题
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppTheme {

    private static final Logger log = LoggerFactory.getLogger(AppTheme.class);

    /** 暗色 CSS 样式表路径 */
    private static final String DARK_STYLESHEET =
            "/com/bingbaihanji/fxdecomplie/themes/dark.css";

    private AppTheme() {
        throw new AssertionError("utility class");
    }

    /** @return 暗色 CSS 样式表 URL */
    public static String darkStylesheet() {
        URL resource = AppTheme.class.getResource(DARK_STYLESHEET);
        if (resource == null) {
            throw new IllegalStateException("Missing stylesheet: " + DARK_STYLESHEET);
        }
        return resource.toExternalForm();
    }

    /**
     * 加载编辑器主题
     *
     * @param config 应用配置
     * @return 主题数据(通过 ThemeManager 按名称加载,失败时回退到硬编码默认值)
     */
    public static VsCodeThemeLoader.ThemeData loadEditorTheme(AppConfig config) {
        String themeName = config.theme().editorTheme();
        try {
            // 优先加载用户配置的主题名,空白/空值时回退到内置 Dark+ 主题
            if (themeName != null && !themeName.isBlank()) {
                return ThemeManager.resolveThemeData(themeName);
            }
            return ThemeManager.resolveThemeData("Dark+");
        } catch (RuntimeException e) {
            // 最外层兜底：主题加载异常时使用硬编码默认暗色主题,确保编辑器始终可渲染
            log.warn("加载编辑器主题失败,使用默认暗色主题", e);
            return VsCodeThemeLoader.defaultDark();
        }
    }
}
