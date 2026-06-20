package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.platform.FxTools;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;
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
 * FxDecompiler 应用启动器，负责启用 JavaFX 预览特性并引导 Application 启动。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class FxDecompilerApp {

    static {
        System.setProperty("fxdecomplie.appDir", resolveAppDirForLogging().toString());
    }

    private static final Logger logger = LoggerFactory.getLogger(FxDecompilerApp.class);

    /** JavaFX 预览属性名 */
    private static final String JAVAFX_PREVIEW_PROPERTY = "javafx.enablePreview";
    /** 抑制预览警告属性名 */
    private static final String JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY = "javafx.suppressPreviewWarning";
    /** 启动错误日志文件路径（位于应用根目录 logs 子目录下） */
    private static final Path STARTUP_ERROR_LOG =
            AppConfig.appDir().resolve("logs").resolve("startup-error.log");

    private FxDecompilerApp() {
    }

    private static Path resolveAppDirForLogging() {
        try {
            var codeSource = FxDecompilerApp.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                Path jarPath = Path.of(codeSource.getLocation().toURI());
                Path parent = jarPath.getParent();
                if (parent != null) return parent;
            }
        } catch (Exception ignored) {
        }
        return Path.of(System.getProperty("user.dir"));
    }

    public static void main(String[] args) {
        try {
            enableJavaFxPreview();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> writeStartupFailure(error));
            Application.launch(FxApplication.class, args);
        } catch (Throwable ex) {
            logger.error("FxDecompiler launcher failed", ex);
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
                writer.println("FxDecompiler startup failed:");
                ex.printStackTrace(writer);
            }
            Files.writeString(STARTUP_ERROR_LOG, buffer.toString());
        } catch (Exception e) {
            logger.warn("Failed to write startup error log", e);
        }
    }

    public static final class FxApplication extends Application {

        /** 为主窗口应用平台原生窗口外观（DWM 暗色主题、阴影、圆角等） */
        public void initWindows(Stage primaryStage) {
            FxTools.applyWindowDarkMode(primaryStage);
        }

        /** 应用配置引用 */
        private AppConfig config;

        @Override
        public void start(Stage stage) {
            try {
                startApplication(stage);
                initWindows(stage);
            } catch (Throwable ex) {
                logger.error("FxDecompiler startup failed", ex);
                writeStartupFailure(ex);
                throw ex;
            }
        }

        // initStyle(EXTENDED) 在 JavaFX 中被标记为 deprecated，当前配合 AppHeaderBar 提供完整窗口交互，功能正常，暂时保留。
        @SuppressWarnings("deprecation")
        /** 启动 JavaFX 应用 */
        private void startApplication(Stage stage) {
            logger.info("FxDecompiler startup build: headerBar-2026-06-17");
            config = AppConfig.load();
            FxTools.loadPlatformConfig(config);
            DiskCodeCache.cleanIfNeeded();
            applyConfiguredLocale();

            setAppIcon(stage);

            try {
                stage.initStyle(StageStyle.EXTENDED);
            } catch (Exception e) {
                logger.warn("EXTENDED stage style not supported, falling back to DECORATED", e);
                stage.initStyle(StageStyle.DECORATED);
            }

            stage.setX(config.window().x());
            stage.setY(config.window().y());
            stage.setWidth(config.window().width());
            stage.setHeight(config.window().height());
            stage.setMaximized(config.window().maximized());

            MainWindow window = new MainWindow(config, true, getHostServices());
            window.show(stage);
            openStartupPath(window);

            stage.setOnCloseRequest(e -> saveWindowState(stage));
        }

        /** 处理 --open <path> 启动参数。 */
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
                com.bingbaihanji.fxdecomplie.utils.I18nUtil.switchLocale(Locale.ENGLISH);
            } else if ("zh-CN".equalsIgnoreCase(config.language())) {
                com.bingbaihanji.fxdecomplie.utils.I18nUtil.switchLocale(Locale.SIMPLIFIED_CHINESE);
            }
        }

        @Override
        public void stop() {
            DecompilerFactory.cleanup();
            if (config != null) {
                config.save();
            }
        }

        private static void setAppIcon(Stage stage) {
            try {
                var stream = FxApplication.class.getResourceAsStream("/icon/logo.png");
                if (stream != null) {
                    stage.getIcons().add(new javafx.scene.image.Image(stream));
                }
            } catch (Exception ignored) {
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
            config.save();
        }

    }
}
