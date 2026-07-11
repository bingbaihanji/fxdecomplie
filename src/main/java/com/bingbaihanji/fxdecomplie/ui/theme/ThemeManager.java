package com.bingbaihanji.fxdecomplie.ui.theme;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 编辑器主题管理器
 *
 * <p>内置主题从 classpath 加载(当前仅 "Dark+" 对应 dark-plus.json),
 * 外部主题以 JSON 文件形式存储在 {@code <appDir>/themes/} 目录下,
 * 由用户通过设置界面导入管理</p>
 *
 * @author bingbaihanji
 * @date 2026-06-29
 */
public final class ThemeManager {

    private static final Logger log = LoggerFactory.getLogger(ThemeManager.class);

    /** 内置暗色主题 classpath 资源路径 */
    private static final String DARK_PLUS_RESOURCE =
            "/com/bingbaihanji/fxdecomplie/themes/dark-plus.json";

    /** 内置主题名列表(不可变) */
    private static final List<String> BUILTIN_THEMES = List.of("Dark+");

    private ThemeManager() {
        throw new AssertionError("utility class");
    }

    /** @return 内置主题名列表 */
    public static List<String> getBuiltinThemes() {
        return BUILTIN_THEMES;
    }

    /** @return themes 目录路径,首次调用时自动创建 */
    public static Path themesDir() {
        Path dir = AppPaths.themesDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.warn("创建 themes 目录失败: {}", dir, e);
        }
        return dir;
    }

    /**
     * 扫描 {@code <appDir>/themes/} 目录返回外部主题名称列表
     * 每个 JSON 文件名去掉 .json 后缀即为主题名
     */
    public static List<String> getExternalThemes() {
        Path dir = themesDir();
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .map(f -> {
                        String name = f.getFileName().toString();
                        return name.substring(0, name.length() - 5);
                    })
                    .forEach(names::add);
        } catch (IOException e) {
            log.warn("扫描外部主题目录失败: {}", dir, e);
        }
        Collections.sort(names);
        return names;
    }

    /** @return 所有可用主题名：内置在前,外部在后 */
    public static List<String> getAllThemes() {
        List<String> all = new ArrayList<>(BUILTIN_THEMES);
        all.addAll(getExternalThemes());
        return all;
    }

    /**
     * 按名称解析 ThemeData：内置走 classpath,外部从 themes 目录加载
     * 加载失败时回退到 Dark+
     */
    public static VsCodeThemeLoader.ThemeData resolveThemeData(String themeName) {
        if (themeName == null || themeName.isBlank() || "Dark+".equals(themeName)) {
            return loadBuiltinDarkPlus();
        }
        Path file = themesDir().resolve(themeName + ".json").normalize();
        if (!file.startsWith(themesDir().normalize())) {
            log.warn("非法主题名称(路径穿越): {}", themeName);
            return loadBuiltinDarkPlus();
        }
        if (Files.isRegularFile(file)) {
            try {
                return VsCodeThemeLoader.load(file);
            } catch (IOException | RuntimeException e) {
                log.warn("加载外部主题失败 [{}],回退到 Dark+", themeName, e);
            }
        } else {
            log.warn("外部主题文件不存在 [{}],回退到 Dark+", file);
        }
        return loadBuiltinDarkPlus();
    }

    /**
     * 导入外部主题：先验证 JSON 有效性,再复制到 themes 目录
     * 重名时自动追加序号 "name (2)", "name (3)" ...
     *
     * @param sourceFile 源 JSON 文件
     * @return 导入后的主题名(不含 .json 扩展名)
     * @throws IOException 读取/写入失败,或 JSON 解析失败
     */
    public static String importTheme(Path sourceFile) throws IOException {
        VsCodeThemeLoader.ThemeData themeData = VsCodeThemeLoader.load(sourceFile);
        String baseName = themeData.name();
        if (baseName == null || baseName.isBlank()) {
            String fileName = sourceFile.getFileName().toString();
            baseName = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - 5)
                    : fileName;
        }

        String resolvedName = baseName;
        Path dest = themesDir().resolve(resolvedName + ".json").normalize();
        if (!dest.startsWith(themesDir().normalize())) {
            throw new IOException("Invalid theme name: " + resolvedName);
        }
        int seq = 2;
        while (Files.exists(dest)) {
            resolvedName = baseName + " (" + seq + ")";
            dest = themesDir().resolve(resolvedName + ".json");
            seq++;
        }

        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        log.info("已导入外部主题: {} → {}", sourceFile, dest);
        return resolvedName;
    }

    /**
     * 导出主题为 VS Code 格式 JSON 文件
     * 内置主题直接复制 classpath 资源 外部主题直接复制源文件
     *
     * @param themeName 主题名称
     * @param targetFile 目标文件路径
     * @throws IOException 读取/写入失败
     */
    public static void exportTheme(String themeName, Path targetFile) throws IOException {
        if ("Dark+".equals(themeName)) {
            try (var in = ThemeManager.class.getResourceAsStream(DARK_PLUS_RESOURCE)) {
                if (in == null) {
                    throw new IOException("内置主题资源未找到: " + DARK_PLUS_RESOURCE);
                }
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Path source = themesDir().resolve(themeName + ".json").normalize();
            if (!source.startsWith(themesDir().normalize())) {
                throw new IOException("非法主题名称(路径穿越): " + themeName);
            }
            if (!Files.isRegularFile(source)) {
                throw new IOException("主题文件不存在: " + source);
            }
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        log.info("已导出主题 [{}] 到: {}", themeName, targetFile);
    }

    /**
     * 删除外部主题文件内置主题不可删除
     *
     * @param themeName 主题名称
     * @return true 如果删除成功
     */
    public static boolean deleteExternalTheme(String themeName) {
        if (themeName == null || themeName.isBlank() || "Dark+".equals(themeName)) {
            log.warn("不允许删除内置主题: {}", themeName);
            return false;
        }
        Path file = themesDir().resolve(themeName + ".json").normalize();
        if (!file.startsWith(themesDir().normalize())) {
            log.warn("非法主题名称(路径穿越): {}", themeName);
            return false;
        }
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                log.info("已删除外部主题: {}", themeName);
            }
            return deleted;
        } catch (IOException e) {
            log.warn("删除外部主题失败: {}", themeName, e);
            return false;
        }
    }

    private static VsCodeThemeLoader.ThemeData loadBuiltinDarkPlus() {
        try {
            return VsCodeThemeLoader.loadResource(DARK_PLUS_RESOURCE);
        } catch (IOException | RuntimeException e) {
            log.warn("加载内置 Dark+ 失败,使用硬编码默认值", e);
            return VsCodeThemeLoader.defaultDark();
        }
    }
}
