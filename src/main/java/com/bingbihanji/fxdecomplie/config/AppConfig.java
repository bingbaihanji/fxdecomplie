package com.bingbihanji.fxdecomplie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class AppConfig {

    /** 配置目录 (~/.fxdecompiler) */
    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".fxdecompiler");
    /** 配置文件路径 */
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    /** JSON 序列化/反序列化器 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** 日志记录器 */
    private static final System.Logger LOG = System.getLogger(AppConfig.class.getName());

    /** 窗口配置 */
    public Window window = new Window();
    /** 主题配置 */
    public Theme theme = new Theme();
    /** 反编译器配置 */
    public Decompiler decompiler = new Decompiler();
    private static final int MAX_RECENT_FILES = 20;

    /** 最近打开的文件列表（路径字符串），预留后续实现最近文件菜单 */
    public List<String> recentFiles = new ArrayList<>();

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

    /**
     * 加载配置。如果配置文件不存在或读取失败，返回默认配置。
     *
     * @return 配置对象，永不返回 null
     */
    public static AppConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                return GSON.fromJson(json, AppConfig.class);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to load config, using defaults", e);
        }
        return new AppConfig();
    }

    /** 保存配置到文件。失败时记录日志但不抛出异常。 */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to save config", e);
        }
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
        /** 默认反编译引擎，有效值: PROCYON, CFR, VINEFLOWER */
        public String defaultEngine = "VINEFLOWER";
        /** 是否显示行号 */
        public boolean lineNumbersEnabled = true;
        /** 是否自动换行 */
        public boolean wrapText = true;

        @Override
        public String toString() {
            return "Decompiler{defaultEngine='" + defaultEngine + "', lineNumbersEnabled=" + lineNumbersEnabled + ", wrapText=" + wrapText + "}";
        }
    }
}
