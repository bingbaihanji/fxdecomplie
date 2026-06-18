package com.bingbihanji.fxdecomplie.ui.theme;

import com.bingbihanji.fxdecomplie.config.AppConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/**
 * 应用主题工具类，负责加载暗色样式表和编辑器主题。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppTheme {

    private static final Logger logger = LoggerFactory.getLogger(AppTheme.class);

    /** 暗色 CSS 样式表路径 */
    private static final String DARK_STYLESHEET =
            "/com/bingbihanji/fxdecomplie/themes/dark.css";
    /** 内置暗色主题 JSON 路径 */
    private static final String DARK_PLUS_THEME =
            "/com/bingbihanji/fxdecomplie/themes/dark-plus.json";

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
     * 加载编辑器主题。
     *
     * @param config 应用配置
     * @return 主题数据（优先配置文件路径 → 内置 Dark+ → 硬编码默认）
     */
    public static VsCodeThemeLoader.ThemeData loadEditorTheme(AppConfig config) {
        String configuredPath = config.theme().path() == null ? "" : config.theme().path().trim();
        try {
            if (!configuredPath.isEmpty()) {
                return VsCodeThemeLoader.load(Path.of(configuredPath));
            }
            return VsCodeThemeLoader.loadResource(DARK_PLUS_THEME);
        } catch (IOException | RuntimeException e) {
            logger.warn("Failed to load editor theme, using default dark", e);
            return VsCodeThemeLoader.defaultDark();
        }
    }
}
