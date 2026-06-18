package com.bingbihanji.fxdecomplie;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.service.DiskCodeCache;
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

    private static final Logger logger = LoggerFactory.getLogger(FxDecompilerApp.class);

    /** JavaFX 预览属性名 */
    private static final String JAVAFX_PREVIEW_PROPERTY = "javafx.enablePreview";
    /** 抑制预览警告属性名 */
    private static final String JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY = "javafx.suppressPreviewWarning";
    /** 启动错误日志文件路径 */
    private static final Path STARTUP_ERROR_LOG = Path.of(
            System.getProperty("user.home"),
            ".fxdecompiler",
            "startup-error.log"
    );

    private FxDecompilerApp() {
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

        /** 应用配置引用 */
        private AppConfig config;

        @Override
        public void start(Stage primaryStage) {
            try {
                startApplication(primaryStage);
            } catch (Throwable ex) {
                logger.error("FxDecompiler startup failed", ex);
                writeStartupFailure(ex);
                throw ex;
            }
        }

        // initStyle(EXTENDED) 在 JavaFX 中被标记为 deprecated，当前配合 AppHeaderBar 提供完整窗口交互，功能正常，暂时保留。
        @SuppressWarnings("deprecation")
        /** 启动 JavaFX 应用 */
        private void startApplication(Stage primaryStage) {
            logger.info("FxDecompiler startup build: headerBar-2026-06-17");
            config = AppConfig.load();
            DiskCodeCache.cleanIfNeeded();
            applyConfiguredLocale();

            primaryStage.initStyle(StageStyle.EXTENDED);

            primaryStage.setX(config.window.x);
            primaryStage.setY(config.window.y);
            primaryStage.setWidth(config.window.width);
            primaryStage.setHeight(config.window.height);
            primaryStage.setMaximized(config.window.maximized);

            MainWindow window = new MainWindow(config, true);
            window.show(primaryStage);
            openStartupPath(window);

            primaryStage.setOnCloseRequest(e -> saveWindowState(primaryStage));
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
            if ("en".equalsIgnoreCase(config.language)) {
                com.bingbihanji.fxdecomplie.utils.I18nUtil.switchLocale(Locale.ENGLISH);
            } else if ("zh-CN".equalsIgnoreCase(config.language)) {
                com.bingbihanji.fxdecomplie.utils.I18nUtil.switchLocale(Locale.SIMPLIFIED_CHINESE);
            }
        }

        @Override
        public void stop() {
            DecompilerFactory.cleanup();
            if (config != null) {
                config.save();
            }
        }

        /** 保存窗口状态到配置 */
        private void saveWindowState(Stage stage) {
            if (!stage.isMaximized()) {
                config.window.x = (int) stage.getX();
                config.window.y = (int) stage.getY();
                config.window.width = (int) stage.getWidth();
                config.window.height = (int) stage.getHeight();
            }
            config.window.maximized = stage.isMaximized();
            config.save();
        }

    }
}
