package com.bingbaihanji.fxdecomplie.config;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Application configuration POJO. Loaded from and persisted to {@code <appDir>/config/config.json}.
 * Holds window geometry, theme settings, decompiler preferences, export defaults,
 * search options, language selection, and recent file history.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class AppConfig {

    /** 应用根目录(JAR 所在目录,开发期回退到 user.dir) */
    private static final Path APP_DIR = resolveAppDir();
    /** 配置目录 (<appDir>/config) */
    private static final Path CONFIG_DIR = APP_DIR.resolve("config");
    /** 配置文件路径 */
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");

    /**
     * 解析应用根目录：优先取 JAR 包所在目录,开发期回退到 user.dir
     * 所有应用数据(配置、缓存、日志)均存放在此目录下
     */
    public static Path appDir() {
        return APP_DIR;
    }

    private static Path resolveAppDir() {
        try {
            var codeSource = AppConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarPath = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar")) {
                    Path parent = jarPath.getParent();
                    if (parent != null) return parent;
                }
            }
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir"));
    }
    /** JSON 序列化/反序列化器 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final int MAX_RECENT_FILES = 20;
    /** 配置结构版本,后续用于迁移旧配置 */
    private int schemaVersion = 1;
    /** 界面语言: zh-CN / en,空字符串表示跟随系统 */
    private String language = "";
    /** 窗口配置 */
    private Window window = new Window();
    /** 主题配置 */
    private Theme theme = new Theme();
    /** 反编译器配置 */
    private Decompiler decompiler = new Decompiler();
    /** 导出配置 */
    private Export export = new Export();
    /** 搜索配置 */
    private Search search = new Search();
    /** 平台原生窗口配置 */
    private Platform platform = new Platform();
    /** 最近打开的文件列表(路径字符串) */
    private List<String> recentFiles = new ArrayList<>();

    /**
     * 加载配置如果配置文件不存在或读取失败,返回默认配置
     *
     * @return 配置对象,永不返回 null
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
        } catch (IOException | com.google.gson.JsonSyntaxException | com.google.gson.JsonIOException e) {
            logger.warn("Failed to load config, using defaults", e);
        }
        return new AppConfig();
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public void schemaVersion(int v) {
        schemaVersion = v;
    }

    public String language() {
        return language;
    }

    public void language(String v) {
        language = v;
    }

    public Window window() {
        return window;
    }

    public void window(Window v) {
        window = v;
    }

    public Theme theme() {
        return theme;
    }

    public void theme(Theme v) {
        theme = v;
    }

    public Decompiler decompiler() {
        return decompiler;
    }

    public void decompiler(Decompiler v) {
        decompiler = v;
    }

    public Export export() {
        return export;
    }

    public void export(Export v) {
        export = v;
    }

    public Search search() {
        return search;
    }

    public void search(Search v) {
        search = v;
    }

    public Platform platform() {
        return platform;
    }

    public void platform(Platform v) {
        platform = v;
    }

    public List<String> recentFiles() {
        synchronized (this) {
            return List.copyOf(recentFiles);
        }
    }

    public void recentFiles(List<String> v) {
        recentFiles = v;
    }

    /** 添加最近文件(去重,最新在前,异步保存) */
    public void addRecentFile(String path) {
        if (path == null || path.isBlank()) return;
        synchronized (this) {
            recentFiles.remove(path);
            recentFiles.addFirst(path);
            while (recentFiles.size() > MAX_RECENT_FILES) {
                recentFiles.removeLast();
            }
        }
        saveAsync();
    }

    /** 清空最近文件列表(异步保存) */
    public void clearRecentFiles() {
        synchronized (this) {
            recentFiles.clear();
        }
        saveAsync();
    }

    /** 保存配置到文件(同步)失败时记录日志但不抛出异常 */
    public void save() {
        synchronized (this) {
            try {
                normalize();
                Files.createDirectories(CONFIG_DIR);
                Files.writeString(CONFIG_FILE, GSON.toJson(this));
            } catch (IOException e) {
                logger.warn("Failed to save config", e);
            }
        }
    }

    /** 异步保存,避免阻塞 UI 线程 */
    private void saveAsync() {
        Thread.ofVirtual().start(this::save);
    }

    private void normalize() {
        if (window == null) window = new Window();
        if (theme == null) theme = new Theme();
        if (decompiler == null) decompiler = new Decompiler();
        if (export == null) export = new Export();
        if (search == null) search = new Search();
        if (platform == null) platform = new Platform();
        if (recentFiles == null) recentFiles = new ArrayList<>();
        if (language == null) language = "";
        if (decompiler.defaultEngine == null) {
            decompiler.defaultEngine = DecompilerTypeEnum.VINEFLOWER;
        }
        if (decompiler.engineOptions == null) {
            decompiler.engineOptions = new LinkedHashMap<>();
        }
        if (theme.path == null) theme.path = "";
        if (theme.fontFamily == null || theme.fontFamily.isBlank()) theme.fontFamily = "Consolas";
        theme.fontSize = Math.clamp(theme.fontSize, 8, 48);
        window.width = Math.max(640, window.width);
        window.height = Math.max(480, window.height);
        if (export.defaultEngine == null) export.defaultEngine = "";
        if (export.defaultFormat == null || export.defaultFormat.isBlank()) export.defaultFormat = "DIR";
        if (export.conflictPolicy == null || export.conflictPolicy.isBlank()) {
            export.conflictPolicy = "OVERWRITE";
        }
        if (export.lastPath == null) export.lastPath = "";
        search.resultLimit = Math.clamp(search.resultLimit, 50, 2000);
        if (platform.cornerPreference == null || platform.cornerPreference.isBlank()) {
            platform.cornerPreference = "DO_NOT_ROUND";
        }
        if (search.excludePatterns == null) {
            search.excludePatterns = new ArrayList<>();
        }
        LinkedHashSet<String> normalizedRecent = new LinkedHashSet<>();
        for (String path : recentFiles) {
            if (path != null && !path.isBlank()) {
                normalizedRecent.add(path);
            }
            if (normalizedRecent.size() >= MAX_RECENT_FILES) {
                break;
            }
        }
        recentFiles = new ArrayList<>(normalizedRecent);
    }

    public static class Window {
        /** 窗口宽度 */
        private int width = 1200;
        /** 窗口高度 */
        private int height = 800;
        /** 窗口 X 坐标 */
        private int x = 100;
        /** 窗口 Y 坐标 */
        private int y = 100;
        /** 是否最大化 */
        private boolean maximized = false;

        public int width() {
            return width;
        }

        public void width(int v) {
            width = v;
        }

        public int height() {
            return height;
        }

        public void height(int v) {
            height = v;
        }

        public int x() {
            return x;
        }

        public void x(int v) {
            x = v;
        }

        public int y() {
            return y;
        }

        public void y(int v) {
            y = v;
        }

        public boolean maximized() {
            return maximized;
        }

        public void maximized(boolean v) {
            maximized = v;
        }

        @Override
        public String toString() {
            return "Window{width=" + width + ", height=" + height + ", x=" + x + ", y=" + y + ", maximized=" + maximized + "}";
        }
    }

    public static class Theme {
        /** 主题文件路径(空字符串表示使用内置默认主题) */
        private String path = "";
        /** 编辑器字体 */
        private String fontFamily = "Consolas";
        /** 编辑器字号 */
        private int fontSize = 14;

        public String path() {
            return path;
        }

        public void path(String v) {
            path = v;
        }

        public String fontFamily() {
            return fontFamily;
        }

        public void fontFamily(String v) {
            fontFamily = v;
        }

        public int fontSize() {
            return fontSize;
        }

        public void fontSize(int v) {
            fontSize = v;
        }

        @Override
        public String toString() {
            return "Theme{path='" + path + "', fontFamily='" + fontFamily + "', fontSize=" + fontSize + "}";
        }
    }

    public static class Decompiler {
        /** 默认反编译引擎 */
        private DecompilerTypeEnum defaultEngine =
                DecompilerTypeEnum.VINEFLOWER;
        /** 是否显示行号 */
        private boolean lineNumbersEnabled = true;
        /** 是否自动换行 */
        private boolean wrapText = true;
        /** 各引擎的自定义选项 (引擎名 → 选项键值对) */
        private Map<String, Map<String, String>> engineOptions = new LinkedHashMap<>();

        public DecompilerTypeEnum defaultEngine() {
            return defaultEngine;
        }

        public void defaultEngine(DecompilerTypeEnum v) {
            defaultEngine = v;
        }

        public boolean lineNumbersEnabled() {
            return lineNumbersEnabled;
        }

        public void lineNumbersEnabled(boolean v) {
            lineNumbersEnabled = v;
        }

        public boolean wrapText() {
            return wrapText;
        }

        public void wrapText(boolean v) {
            wrapText = v;
        }

        public Map<String, Map<String, String>> engineOptions() {
            return engineOptions;
        }

        public void engineOptions(Map<String, Map<String, String>> v) {
            engineOptions = v;
        }

        @Override
        public String toString() {
            return "Decompiler{defaultEngine='" + defaultEngine + "', lineNumbersEnabled=" + lineNumbersEnabled + ", wrapText=" + wrapText + "}";
        }
    }

    public static class Export {
        /** 默认导出引擎,空字符串表示跟随当前菜单引擎 */
        private String defaultEngine = "";
        /** 默认导出格式,有效值: DIR, ZIP */
        private String defaultFormat = "DIR";
        /** 默认冲突策略,有效值: SKIP, OVERWRITE, RENAME */
        private String conflictPolicy = "OVERWRITE";
        /** 是否默认导出资源文件 */
        private boolean exportResources = true;
        /** 最近一次导出路径 */
        private String lastPath = "";

        public String defaultEngine() {
            return defaultEngine;
        }

        public void defaultEngine(String v) {
            defaultEngine = v;
        }

        public String defaultFormat() {
            return defaultFormat;
        }

        public void defaultFormat(String v) {
            defaultFormat = v;
        }

        public String conflictPolicy() {
            return conflictPolicy;
        }

        public void conflictPolicy(String v) {
            conflictPolicy = v;
        }

        public boolean exportResources() {
            return exportResources;
        }

        public void exportResources(boolean v) {
            exportResources = v;
        }

        public String lastPath() {
            return lastPath;
        }

        public void lastPath(String v) {
            lastPath = v;
        }

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
        private boolean fullSourceSearch = false;
        /** 搜索结果显示上限 */
        private int resultLimit = 200;
        /** 搜索排除路径模式(支持通配符 *) */
        private List<String> excludePatterns = new ArrayList<>();

        public boolean fullSourceSearch() {
            return fullSourceSearch;
        }

        public void fullSourceSearch(boolean v) {
            fullSourceSearch = v;
        }

        public int resultLimit() {
            return resultLimit;
        }

        public void resultLimit(int v) {
            resultLimit = v;
        }

        public List<String> excludePatterns() {
            return excludePatterns;
        }

        public void excludePatterns(List<String> v) {
            excludePatterns = v;
        }

        @Override
        public String toString() {
            return "Search{fullSourceSearch=" + fullSourceSearch
                    + ", resultLimit=" + resultLimit
                    + ", excludePatterns=" + excludePatterns + "}";
        }
    }

    /** Windows 平台原生窗口外观配置,通过 DWM API 应用非 Windows 平台此项被忽略 */
    public static class Platform {
        /** 窗口边框颜色 (COLORREF: 0x00BBGGRR) */
        private int windowBorderColor = 0x00888800;
        /** 窗口圆角偏好: DO_NOT_ROUND / ROUND / DEFAULT */
        private String cornerPreference = "DO_NOT_ROUND";


        public int windowBorderColor() {
            return windowBorderColor;
        }

        public void windowBorderColor(int v) {
            windowBorderColor = v;
        }

        public String cornerPreference() {
            return cornerPreference;
        }

        public void cornerPreference(String v) {
            cornerPreference = v;
        }

        @Override
        public String toString() {
            return "Platform{" +
                    "windowBorderColor=" + windowBorderColor +
                    ", cornerPreference='" + cornerPreference + '\'' +
                    '}';
        }
    }
}
