package com.bingbaihanji.fxdecomplie.ui.theme;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * VS Code 主题加载器,解析 VS Code 主题 JSON 文件并生成编辑器配色方案
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class VsCodeThemeLoader {

    /** 内置默认暗色主题(懒加载) */
    private static final ThemeData DEFAULT_DARK = buildDefaultDark();

    private VsCodeThemeLoader() {
        throw new AssertionError("utility class");
    }

    public static ThemeData defaultDark() {
        return DEFAULT_DARK;
    }

    /** 构建内置默认暗色主题 */
    private static ThemeData buildDefaultDark() {
        Map<String, StyleAttributeMap> tokenStyles = new LinkedHashMap<>();
        tokenStyles.put("keyword", style("#ad7abe", true, false));
        tokenStyles.put("comment", style("#6a9955", false, true));
        tokenStyles.put("string", style("#96be78", false, false));
        tokenStyles.put("annotation", style("#ba9e22", false, false));
        tokenStyles.put("number", style("#33a8a0", false, false));
        tokenStyles.put("field", style("#ff9696", false, false));
        tokenStyles.put("parameter", style("#3e98c3", false, false));
        tokenStyles.put("method", style("#dd9438", false, false));
        tokenStyles.put("type", style("#4ec9b0", false, false));
        tokenStyles.put("default", style("#9aa7b0", false, false));

        return new ThemeData("Default Dark", "dark",
                Color.web("#191b1d"), Color.web("#9aa7b0"), Color.web("#414c4c"),
                "Fira Code Light", 14, tokenStyles);
    }

    /** 创建样式属性 */
    private static StyleAttributeMap style(String colorHex, boolean bold, boolean italic) {
        var b = StyleAttributeMap.builder().setTextColor(Color.web(colorHex));
        if (bold) b.setBold(true);
        if (italic) b.setItalic(true);
        return b.build();
    }

    /** 从文件路径加载主题 */
    public static ThemeData load(Path jsonPath) throws IOException {
        String json = Files.readString(jsonPath);
        return parse(json);
    }

    /** 从 classpath 资源加载主题 */
    public static ThemeData loadResource(String resourcePath) throws IOException {
        try (InputStream in = VsCodeThemeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        }
    }

    /** 解析主题 JSON */
    private static ThemeData parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String name = getString(root, "name", "Unknown");
        String type = getString(root, "type", "dark");

        JsonObject colors = root.getAsJsonObject("colors");
        Color bg = parseColor(colors, "editor.background", "#1e1e1e");
        Color fg = parseColor(colors, "editor.foreground", "#d4d4d4");
        Color ln = parseColor(colors, "editorLineNumber.foreground", "#858585");

        Map<String, StyleAttributeMap> tokenStyles = new LinkedHashMap<>();
        JsonArray tokenColors = root.getAsJsonArray("tokenColors");
        if (tokenColors != null) {
            for (JsonElement e : tokenColors) {
                JsonObject tc = e.getAsJsonObject();
                String scope = extractScope(tc.get("scope"));
                if (scope == null) continue;

                JsonObject settings = tc.getAsJsonObject("settings");
                String fgHex = getString(settings, "foreground", "#d4d4d4");
                String fontStyle = getString(settings, "fontStyle", "");
                boolean bold = fontStyle.contains("bold");
                boolean italic = fontStyle.contains("italic");

                String key = scope.contains(",") ? scope.split(",")[0].trim() : scope;
                if (key.startsWith(".")) key = key.substring(1);
                tokenStyles.putIfAbsent(key, style(fgHex, bold, italic));
            }
        }

        return new ThemeData(name, type, bg, fg, ln, "Consolas", 14, tokenStyles);
    }

    /** 从 scope 字段提取第一个 scope 名称 */
    private static String extractScope(JsonElement scopeElem) {
        if (scopeElem == null) return null;
        if (scopeElem.isJsonPrimitive()) return scopeElem.getAsString();
        if (scopeElem.isJsonArray()) {
            JsonArray arr = scopeElem.getAsJsonArray();
            return arr.isEmpty() ? null : arr.get(0).getAsString();
        }
        return null;
    }

    /** 从 colors 节点解析颜色 */
    private static Color parseColor(JsonObject colors, String key, String defaultHex) {
        if (colors == null) return Color.web(defaultHex);
        JsonElement e = colors.get(key);
        if (e == null || e.isJsonNull()) return Color.web(defaultHex);
        return Color.web(e.getAsString());
    }

    /** 安全获取 JSON 字符串属性 */
    private static String getString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : def;
    }

    /**
     * 主题数据
     *
     * @param name                  主题名称
     * @param type                  主题类型(dark/light)
     * @param editorBackground      编辑器背景色
     * @param editorForeground      编辑器前景色
     * @param lineNumberForeground  行号颜色
     * @param fontFamily            字体
     * @param fontSize              字号
     * @param tokenStyles           token 类型 → 样式映射
     */
    public record ThemeData(
            String name,
            String type,
            Color editorBackground,
            Color editorForeground,
            Color lineNumberForeground,
            String fontFamily,
            int fontSize,
            Map<String, StyleAttributeMap> tokenStyles
    ) {
    }
}
