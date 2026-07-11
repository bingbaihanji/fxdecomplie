package com.bingbaihanji.fxdecomplie.config;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.util.AtomicFile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 应用配置 POJO,从 {@code <appDir>/config/config.json} 加载并持久化
 * 包含窗口几何信息、主题设置、反编译器偏好、导出默认值、
 * 搜索选项、语言选择及最近文件历史
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class AppConfig {

    /** JSON 序列化/反序列化器 */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);
    /** 应用根目录(JAR 所在目录,开发期回退到 user.dir) */
    private static final Path APP_DIR = resolveAppDir();
    /** 配置目录 (<appDir>/config) */
    private static final Path CONFIG_DIR = APP_DIR.resolve("config");
    /** 配置文件路径 */
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    /** 应用进程内唯一配置实例锁 */
    private static final Object INSTANCE_LOCK = new Object();
    /** 最近文件最大数量 */
    private static final int MAX_RECENT_FILES = 20;
    /** 应用进程内唯一配置实例 */
    private static volatile AppConfig instance;
    /** 配置结构版本,用于后续迁移旧配置 */
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
     * 解析应用根目录:优先取 JAR 包所在目录,开发期回退到 user.dir
     * 所有应用数据(配置、缓存、日志)均存放在此目录下
     */
    public static Path appDir() {
        return APP_DIR;
    }

    /**
     * 解析应用根目录:优先取 JAR 包所在目录,开发期 classpath 在 target/classes/ 时回退到项目根
     *
     * @return 应用根目录的绝对路径
     */
    private static Path resolveAppDir() {
        try {
            var codeSource = AppConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarPath = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(jarPath) && jarPath.toString().endsWith(".jar")) {
                    Path parent = jarPath.getParent();
                    if (parent != null) {
                        return parent.toAbsolutePath().normalize();
                    }
                } else if (Files.isDirectory(jarPath)) {
                    return appRoot(jarPath);
                }
            }
        } catch (Exception ignored) {
            log.debug("解析应用目录失败,回退到 user.dir", ignored);
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * 开发期智能解析:classpath 在 target/classes/ 时返回项目根目录,
     * 避免 mvn clean 删除 config 目录
     *
     * @param codeSourceDir 代码源目录(classpath 中的 classes 目录)
     * @return 项目根目录或原始 codeSourceDir
     */
    private static Path appRoot(Path codeSourceDir) {
        Path normalized = codeSourceDir.toAbsolutePath().normalize();
        Path name = normalized.getFileName();
        // 开发期 classpath 在 target/classes/,config 存项目根避免被 mvn clean 删除
        if (name != null && "classes".equalsIgnoreCase(name.toString())) {
            Path target = normalized.getParent();
            if (target != null && target.getFileName() != null
                    && "target".equalsIgnoreCase(target.getFileName().toString())
                    && target.getParent() != null) {
                return target.getParent();
            }
        }
        return normalized;
    }

    /** @return 应用进程内唯一配置实例,首次调用时从磁盘加载 */
    public static AppConfig getInstance() {
        AppConfig current = instance;
        if (current != null) {
            return current;
        }
        synchronized (INSTANCE_LOCK) {
            if (instance == null) {
                instance = loadFromDisk();
            }
            return instance;
        }
    }

    /**
     * 加载配置的兼容入口
     * <p>应用运行期应共享同一份配置对象,避免多个控制器各自持有配置快照</p>
     *
     * @return 应用进程内唯一配置实例,永不返回 null
     */
    public static AppConfig load() {
        return getInstance();
    }

    /**
     * 重新从磁盘读取配置并替换进程内唯一实例
     * 主要用于设置迁移或测试场景,普通业务代码应使用 {@link #getInstance()}
     */
    public static AppConfig reload() {
        synchronized (INSTANCE_LOCK) {
            instance = loadFromDisk();
            return instance;
        }
    }

    /**
     * 从磁盘加载配置如果配置文件不存在或读取失败,返回默认配置
     *
     * @return 配置对象,永不返回 null
     */
    private static AppConfig loadFromDisk() {
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
            log.warn("加载配置失败,将备份损坏文件并使用默认配置", e);
            backupCorruptedConfig();
        }
        AppConfig defaults = new AppConfig();
        defaults.normalize();
        return defaults;
    }

    /** 将损坏的配置文件重命名为 .bak 后缀,便于用户排查与手工恢复 */
    private static void backupCorruptedConfig() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                Path backup = Path.of(CONFIG_FILE + ".bak");
                // 若已存在同名备份,追加序号避免覆盖
                for (int i = 2; i <= 100 && Files.exists(backup); i++) {
                    backup = Path.of(CONFIG_FILE + ".bak." + i);
                }
                Files.move(CONFIG_FILE, backup);
                log.info("已备份损坏的配置文件到: {}", backup);
            }
        } catch (IOException moveEx) {
            log.warn("备份损坏配置文件失败", moveEx);
        }
    }

    private static Window copyWindow(Window source) {
        Window copy = new Window();
        if (source != null) {
            copy.width = source.width;
            copy.height = source.height;
            copy.x = source.x;
            copy.y = source.y;
            copy.maximized = source.maximized;
        }
        return copy;
    }

    private static Theme copyTheme(Theme source) {
        Theme copy = new Theme();
        if (source != null) {
            copy.path = source.path;
            copy.fontFamily = source.fontFamily;
            copy.fontSize = source.fontSize;
            copy.editorTheme = source.editorTheme;
        }
        return copy;
    }

    private static Decompiler copyDecompiler(Decompiler source) {
        Decompiler copy = new Decompiler();
        if (source != null) {
            copy.defaultEngine = source.defaultEngine;
            copy.lineNumbersEnabled = source.lineNumbersEnabled;
            copy.wrapText = source.wrapText;
            copy.engineOptions = deepCopyEngineOptions(source.engineOptions);
        }
        return copy;
    }

    private static Export copyExport(Export source) {
        Export copy = new Export();
        if (source != null) {
            copy.defaultEngine = source.defaultEngine;
            copy.defaultFormat = source.defaultFormat;
            copy.conflictPolicy = source.conflictPolicy;
            copy.exportResources = source.exportResources;
            copy.lastPath = source.lastPath;
        }
        return copy;
    }

    private static Search copySearch(Search source) {
        Search copy = new Search();
        if (source != null) {
            copy.fullSourceSearch = source.fullSourceSearch;
            copy.resultLimit = source.resultLimit;
            copy.excludePatterns = source.excludePatterns == null
                    ? new ArrayList<>() : new ArrayList<>(source.excludePatterns);
        }
        return copy;
    }

    private static Platform copyPlatform(Platform source) {
        Platform copy = new Platform();
        if (source != null) {
            copy.windowBorderColor = source.windowBorderColor;
            copy.cornerPreference = source.cornerPreference;
        }
        return copy;
    }

    private static Map<String, Map<String, String>> deepCopyEngineOptions(
            Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new LinkedHashMap<>();
        if (source == null) {
            return copy;
        }
        for (var entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public void schemaVersion(int v) {
        schemaVersion = v;
    }

    /** @return 当前配置的深拷贝,用于设置对话框等编辑草稿场景 */
    public AppConfig copy() {
        synchronized (this) {
            AppConfig copy = new AppConfig();
            copy.schemaVersion = schemaVersion;
            copy.language = language;
            copy.window = copyWindow(window);
            copy.theme = copyTheme(theme);
            copy.decompiler = copyDecompiler(decompiler);
            copy.export = copyExport(export);
            copy.search = copySearch(search);
            copy.platform = copyPlatform(platform);
            copy.recentFiles = recentFiles == null ? new ArrayList<>() : new ArrayList<>(recentFiles);
            copy.normalize();
            return copy;
        }
    }

    /** 使用另一份配置的值覆盖当前实例,保留当前对象引用 */
    public void copyFrom(AppConfig source) {
        Objects.requireNonNull(source, "source");
        if (source == this) {
            return;
        }
        AppConfig copy = source.copy();
        synchronized (this) {
            schemaVersion = copy.schemaVersion;
            language = copy.language;
            window = copy.window;
            theme = copy.theme;
            decompiler = copy.decompiler;
            export = copy.export;
            search = copy.search;
            platform = copy.platform;
            recentFiles = copy.recentFiles;
            normalize();
        }
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

    /** 获取最近文件列表的不可变副本(线程安全) */
    public List<String> recentFiles() {
        synchronized (this) {
            return List.copyOf(recentFiles);
        }
    }

    public synchronized void recentFiles(List<String> v) {
        recentFiles = v;
    }

    /** 添加最近文件(去重,最新在前,异步保存) */
    public void addRecentFile(String path) {
        if (path == null || path.isBlank()) {
            return;
        }
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
                AtomicFile af = new AtomicFile(CONFIG_FILE.toFile());
                af.write(os -> {
                    try {
                        os.write(GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (IOException | RuntimeException e) {
                log.warn("保存配置失败", e);
            }
        }
    }

    /** 异步保存,避免阻塞 UI 线程 */
    private void saveAsync() {
        // 只有应用级单例配置会自动落盘测试或临时配置对象仍可显式调用 save()
        if (this != instance) {
            return;
        }
        Thread.ofVirtual().start(this::save);
    }

    /**
     * 规范化配置值:填充 null 字段为默认值、修正越界数值、清理空白/无效的最近文件条目
     */
    private void normalize() {
        if (window == null) {
            window = new Window();
        }
        if (theme == null) {
            theme = new Theme();
        }
        if (decompiler == null) {
            decompiler = new Decompiler();
        }
        if (export == null) {
            export = new Export();
        }
        if (search == null) {
            search = new Search();
        }
        if (platform == null) {
            platform = new Platform();
        }
        if (recentFiles == null) {
            recentFiles = new ArrayList<>();
        }
        if (language == null) {
            language = "";
        }
        if (decompiler.defaultEngine == null) {
            decompiler.defaultEngine = DecompilerTypeEnum.JADX;
        }
        if (decompiler.engineOptions == null) {
            decompiler.engineOptions = new LinkedHashMap<>();
        }
        if (theme.path == null) {
            theme.path = "";
        }
        if (theme.editorTheme == null || theme.editorTheme.isBlank()) {
            theme.editorTheme = "Dark+";
        }
        if (theme.fontFamily == null || theme.fontFamily.isBlank()) {
            theme.fontFamily = "Consolas";
        }
        theme.fontSize = Math.clamp(theme.fontSize, 8, 48);
        window.width = Math.max(640, window.width);
        window.height = Math.max(480, window.height);
        if (export.defaultEngine == null) {
            export.defaultEngine = "";
        }
        if (export.defaultFormat == null || export.defaultFormat.isBlank()
                || !java.util.Set.of("DIR", "ZIP").contains(export.defaultFormat)) {
            export.defaultFormat = "DIR";
        }
        if (export.conflictPolicy == null || export.conflictPolicy.isBlank()
                || !java.util.Set.of("SKIP", "OVERWRITE", "RENAME").contains(export.conflictPolicy)) {
            export.conflictPolicy = "OVERWRITE";
        }
        if (export.lastPath == null) {
            export.lastPath = "";
        }
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
        /** 编辑器配色主题名称, "" 或 "Dark+" 表示内置默认 */
        private String editorTheme = "";

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

        public String editorTheme() {
            return editorTheme;
        }

        public void editorTheme(String v) {
            editorTheme = v;
        }

        @Override
        public String toString() {
            return "Theme{path='" + path + "', editorTheme='" + editorTheme
                    + "', fontFamily='" + fontFamily + "', fontSize=" + fontSize + "}";
        }
    }

    public static class Decompiler {
        /** 默认反编译引擎 */
        private DecompilerTypeEnum defaultEngine =
                DecompilerTypeEnum.JADX;
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
