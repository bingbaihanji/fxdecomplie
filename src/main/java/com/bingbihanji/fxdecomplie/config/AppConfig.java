package com.bingbihanji.fxdecomplie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Application configuration POJO. Loaded from and persisted to {@code ~/.fxdecompiler/config.json}.
 * Holds window geometry, theme settings, decompiler preferences, export defaults,
 * search options, language selection, and recent file history.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class AppConfig {

    /** 配置目录 (~/.fxdecompiler) */
    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".fxdecompiler");
    /** 配置文件路径 */
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    /** JSON 序列化/反序列化器 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final int MAX_RECENT_FILES = 20;
    /** 配置结构版本，后续用于迁移旧配置 */
    public int schemaVersion = 1;
    /** 界面语言: zh-CN / en，空字符串表示跟随系统 */
    public String language = "";
    /** 窗口配置 */
    public Window window = new Window();
    /** 主题配置 */
    public Theme theme = new Theme();
    /** 反编译器配置 */
    public Decompiler decompiler = new Decompiler();
    /** 导出配置 */
    public Export export = new Export();
    /** 搜索配置 */
    public Search search = new Search();
    /** 最近打开的文件列表（路径字符串），预留后续实现最近文件菜单 */
    public List<String> recentFiles = new ArrayList<>();

    /**
     * 加载配置。如果配置文件不存在或读取失败，返回默认配置。
     *
     * @return 配置对象，永不返回 null
     */
    public static AppConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                AppConfig loaded = GSON.fromJson(json, AppConfig.class);
                if (loaded != null) {
                    loaded.normalize();
                    return loaded;
                }
            }
        } catch (IOException e) {
            logger.warn("Failed to load config, using defaults", e);
        }
        return new AppConfig();
    }

    /** 添加最近文件（去重，最新在前，自动保存） */
    public void addRecentFile(String path) {
        if (path == null || path.isBlank()) return;
        recentFiles.remove(path);
        recentFiles.addFirst(path);
        while (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.removeLast();
        }
        save();
    }

    /** 保存配置到文件。失败时记录日志但不抛出异常。 */
    public void save() {
        try {
            normalize();
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException e) {
            logger.warn("Failed to save config", e);
        }
    }

    private void normalize() {
        if (window == null) window = new Window();
        if (theme == null) theme = new Theme();
        if (decompiler == null) decompiler = new Decompiler();
        if (export == null) export = new Export();
        if (search == null) search = new Search();
        if (recentFiles == null) recentFiles = new ArrayList<>();
        if (language == null) language = "";
    }

    public static class Window {
        /** 窗口宽度 */
        public int width = 1200;
        /** 窗口高度 */
        public int height = 800;
        /** 窗口 X 坐标 */
        public int x = 100;
        /** 窗口 Y 坐标 */
        public int y = 100;
        /** 是否最大化 */
        public boolean maximized = false;

        @Override
        public String toString() {
            return "Window{width=" + width + ", height=" + height + ", x=" + x + ", y=" + y + ", maximized=" + maximized + "}";
        }
    }

    public static class Theme {
        /** 主题文件路径（空字符串表示使用内置默认主题） */
        public String path = "";
        /** 编辑器字体 */
        public String fontFamily = "Consolas";
        /** 编辑器字号 */
        public int fontSize = 14;

        @Override
        public String toString() {
            return "Theme{path='" + path + "', fontFamily='" + fontFamily + "', fontSize=" + fontSize + "}";
        }
    }

    public static class Decompiler {
        /** 默认反编译引擎 */
        public com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum defaultEngine =
                com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum.VINEFLOWER;
        /** 是否显示行号 */
        public boolean lineNumbersEnabled = true;
        /** 是否自动换行 */
        public boolean wrapText = true;

        @Override
        public String toString() {
            return "Decompiler{defaultEngine='" + defaultEngine + "', lineNumbersEnabled=" + lineNumbersEnabled + ", wrapText=" + wrapText + "}";
        }
    }

    public static class Export {
        /** 默认导出引擎，空字符串表示跟随当前菜单引擎 */
        public String defaultEngine = "";
        /** 默认导出格式，有效值: DIR, ZIP */
        public String defaultFormat = "DIR";
        /** 默认冲突策略，有效值: SKIP, OVERWRITE, RENAME */
        public String conflictPolicy = "OVERWRITE";
        /** 是否默认导出资源文件 */
        public boolean exportResources = true;
        /** 最近一次导出路径 */
        public String lastPath = "";

        @Override
        public String toString() {
            return "Export{defaultEngine='" + defaultEngine + "', defaultFormat='"
                    + defaultFormat + "', conflictPolicy='" + conflictPolicy
                    + "', exportResources=" + exportResources + ", lastPath='"
                    + lastPath + "'}";
        }
    }

    public static class Search {
        /** 是否默认启用完整源码搜索 */
        public boolean fullSourceSearch = false;
        /** 搜索结果显示上限 */
        public int resultLimit = 200;
        /** 搜索排除路径模式（支持通配符 *） */
        public List<String> excludePatterns = new java.util.ArrayList<>();

        @Override
        public String toString() {
            return "Search{fullSourceSearch=" + fullSourceSearch
                    + ", resultLimit=" + resultLimit
                    + ", excludePatterns=" + excludePatterns + "}";
        }
    }
}
