package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.ClassTabOpener;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import com.bingbaihanji.windows.jfx.WindowToolkit;
import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowCornerPreference;
import javafx.application.Application;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * FxDecompiler 应用启动器,负责启用 JavaFX 预览特性并引导 Application 启动
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class FxDecompilerApp {

    private static final Logger logger = LoggerFactory.getLogger(FxDecompilerApp.class);
    /** JavaFX 预览属性名 */
    private static final String JAVAFX_PREVIEW_PROPERTY = "javafx.enablePreview";
    /** 抑制预览警告属性名 */
    private static final String JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY = "javafx.suppressPreviewWarning";
    /** 启动错误日志文件路径(位于应用根目录 logs 子目录下) */
    private static final Path STARTUP_ERROR_LOG =
            AppConfig.appDir().resolve("logs").resolve("startup-error.log");

    static {
        System.setProperty("fxdecomplie.appDir", resolveAppDirForLogging().toString());
    }

    private FxDecompilerApp() {
    }

    private static Path resolveAppDirForLogging() {
        try {
            var codeSource = FxDecompilerApp.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarPath = Path.of(codeSource.getLocation().toURI());
                if (Files.isRegularFile(jarPath)
                        && jarPath.toString().endsWith(".jar")) {
                    Path parent = jarPath.getParent();
                    if (parent != null) {
                        return parent.toAbsolutePath().normalize();
                    }
                } else if (Files.isDirectory(jarPath)) {
                    return appRoot(jarPath);
                }
            }
        } catch (Exception ignored) {
            logger.debug("解析应用目录失败，回退到 user.dir", ignored);
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /** IDE 开发期 classpath 在 target/classes/，返回项目根目录避免 mvn clean 删除数据 */
    private static Path appRoot(Path codeSourceDir) {
        Path normalized = codeSourceDir.toAbsolutePath().normalize();
        Path name = normalized.getFileName();
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

    public static void main(String[] args) {
        try {
            enableJavaFxPreview();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> writeStartupFailure(error));
            Application.launch(FxApplication.class, args);
        } catch (Throwable ex) {
            logger.error("FxDecompiler 启动器失败", ex);
            writeStartupFailure(ex);
            throw ex;
        }
    }

    /** 启用 JavaFX 预览特性 */
    private static void enableJavaFxPreview() {
        System.setProperty(JAVAFX_PREVIEW_PROPERTY, "true");
        System.setProperty(JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY, "true");
    }

    /** 写入启动失败日志 */
    private static void writeStartupFailure(Throwable ex) {
        try {
            Files.createDirectories(STARTUP_ERROR_LOG.getParent());
            StringWriter buffer = new StringWriter();
            try (PrintWriter writer = new PrintWriter(buffer)) {
                writer.println("FxDecompiler 启动失败:");
                ex.printStackTrace(writer);
            }
            Files.writeString(STARTUP_ERROR_LOG, buffer.toString());
        } catch (Exception e) {
            logger.warn("写入启动错误日志失败", e);
        }
    }

    public static final class FxApplication extends Application {

        /** 应用配置引用 */
        private AppConfig config;
        /** 主窗口控制器,用于直接关闭窗口时释放工作区资源 */
        private MainWindow window;
        /** 主 Stage,用于在所有退出路径上持久化窗口状态 */
        private Stage primaryStage;

        private static WindowAppearance windowAppearance(AppConfig config) {
            AppConfig.Platform platform = config == null ? null : config.platform();
            int borderColor = platform == null ? 0x00888800 : platform.windowBorderColor();
            WindowCornerPreference cornerPreference = parseCornerPreference(
                    platform == null ? null : platform.cornerPreference());
            return WindowAppearance.darkDialog(borderColor, cornerPreference);
        }

        private static WindowCornerPreference parseCornerPreference(String value) {
            if (value == null || value.isBlank()) {
                return WindowCornerPreference.DO_NOT_ROUND;
            }
            try {
                return WindowCornerPreference.valueOf(value);
            } catch (IllegalArgumentException ignored) {
                return WindowCornerPreference.DO_NOT_ROUND;
            }
        }

        /** 为主窗口应用平台原生窗口外观(DWM 暗色主题、阴影、圆角等) */
        public void initWindows(Stage primaryStage) {
            DefaultWindowTheme.applyWindowDarkMode(primaryStage);
        }

        @Override
        public void start(Stage stage) {
            try {
                startApplication(stage);
                initWindows(stage);
            } catch (Throwable ex) {
                logger.error("FxDecompiler 启动失败", ex);
                writeStartupFailure(ex);
                throw ex;
            }
        }

        // initStyle(EXTENDED) 在 JavaFX 中被标记为 deprecated,当前配合 AppHeaderBar 提供完整窗口交互,功能正常,暂时保留
        @SuppressWarnings("deprecation")
        /** 启动 JavaFX 应用 */
        private void startApplication(Stage stage) {
            primaryStage = stage;
            logger.info("FxDecompiler 启动构建版本: headerBar-2026-06-17");
            config = AppConfig.load();
            DefaultWindowTheme.configure(windowAppearance(config));
            DiskCodeCache.cleanIfNeeded();
            CommentManager.setRootDir(AppConfig.appDir().resolve("fxdecomplie").resolve("comments"));
            com.bingbaihanji.fxdecomplie.rename.RenameService.setRootDir(
                    AppConfig.appDir().resolve("fxdecomplie").resolve("renames"));
            applyConfiguredLocale();

            IconHelper.setStageIcon(stage);

            try {
                stage.initStyle(StageStyle.EXTENDED);
            } catch (Exception e) {
                logger.warn("EXTENDED 窗口样式不支持,回退到 DECORATED", e);
                stage.initStyle(StageStyle.DECORATED);
            }

            stage.setX(config.window().x());
            stage.setY(config.window().y());
            stage.setWidth(config.window().width());
            stage.setHeight(config.window().height());
            stage.setMaximized(config.window().maximized());

            window = new MainWindow(config, true, getHostServices());
            window.show(stage);
            openStartupPath(window);

            stage.setOnCloseRequest(e -> saveWindowState(stage));
        }

        /** 处理 --open <path> 启动参数 */
        private void openStartupPath(MainWindow window) {
            var args = getParameters().getRaw();
            for (int i = 0; i < args.size(); i++) {
                String arg = args.get(i);
                if ("--open".equals(arg) && i + 1 < args.size()) {
                    Path path = Path.of(args.get(++i));
                    if (Files.exists(path)) {
                        window.openInitialFile(path.toFile());
                    }
                }
            }
        }

        private void applyConfiguredLocale() {
            if ("en".equalsIgnoreCase(config.language())) {
                com.bingbaihanji.util.I18nUtil.switchLocale(Locale.ENGLISH);
            } else if ("zh-CN".equalsIgnoreCase(config.language())) {
                com.bingbaihanji.util.I18nUtil.switchLocale(Locale.SIMPLIFIED_CHINESE);
            }
        }

        @Override
        public void stop() {
            BackgroundTasks.shutdown();
            ClassTabOpener.shutdown();
            if (window != null) {
                window.shutdownResources();
            }
            WindowToolkit.shutdown();
            DecompilerFactory.cleanup();
            if (config != null) {
                if (primaryStage != null) {
                    saveWindowState(primaryStage);
                }
                config.save();
            }
        }

        /** 保存窗口状态到配置 */
        private void saveWindowState(Stage stage) {
            if (!stage.isMaximized()) {
                config.window().x((int) stage.getX());
                config.window().y((int) stage.getY());
                config.window().width((int) stage.getWidth());
                config.window().height((int) stage.getHeight());
            }
            config.window().maximized(stage.isMaximized());
        }

    }
}
