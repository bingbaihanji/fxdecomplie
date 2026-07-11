package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.context.AppContext;
import com.bingbaihanji.fxdecomplie.controller.MainWindow;
import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
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

    private static final Logger log = LoggerFactory.getLogger(FxDecompilerApp.class);
    /** JavaFX 预览属性名 */
    private static final String JAVAFX_PREVIEW_PROPERTY = "javafx.enablePreview";
    /** 抑制预览警告属性名 */
    private static final String JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY = "javafx.suppressPreviewWarning";
    /** 启动错误日志文件路径(位于应用根目录 logs 子目录下) */
    private static final Path STARTUP_ERROR_LOG = AppPaths.startupErrorLog();

    static {
        System.setProperty("fxdecomplie.appDir", resolveAppDirForLogging().toString());
    }

    /** 工具类私有构造,防止实例化 */
    private FxDecompilerApp() {
    }

    /** 在 static 初始化块中解析应用根目录,用于配置 logback 日志路径 */
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
            log.debug("解析应用目录失败,回退到 user.dir", ignored);
        }
        return Path.of(System.getProperty("user.dir"));
    }

    /**
     * IDE 开发期 classpath 在 target/classes/,返回项目根目录避免 mvn clean 删除数据
     *
     * @param codeSourceDir 代码源目录(classpath 中的 classes 目录)
     * @return 项目根目录或原始 codeSourceDir
     */
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

    /**
     * 程序主入口:启用 JavaFX 预览特性后启动 JavaFX Application
     *
     * @param args 命令行参数,支持 --open &lt;path&gt; 在启动时自动打开文件
     */
    public static void main(String[] args) {
        try {
            enableJavaFxPreview();
            Thread.setDefaultUncaughtExceptionHandler((thread, error) -> writeStartupFailure(error));
            Application.launch(FxApplication.class, args);
        } catch (Throwable ex) {
            log.error("FxDecompiler 启动器失败", ex);
            writeStartupFailure(ex);
            throw ex;
        }
    }

    /** 启用 JavaFX 预览特性 */
    private static void enableJavaFxPreview() {
        System.setProperty(JAVAFX_PREVIEW_PROPERTY, "true");
        System.setProperty(JAVAFX_SUPPRESS_PREVIEW_WARNING_PROPERTY, "true");
    }

    /**
     * 将启动失败的异常堆栈写入启动错误日志文件
     *
     * @param ex 启动过程中抛出的异常
     */
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
            log.warn("写入启动错误日志失败", e);
        }
    }

    /**
     * JavaFX Application 实现,负责窗口创建、配置加载、启动参数处理与应用生命周期管理
     */
    public static final class FxApplication extends Application {

        /** 应用配置引用 */
        private AppConfig config;
        /** 主窗口控制器,用于直接关闭窗口时释放工作区资源 */
        private MainWindow window;
        /** 主 Stage,用于在所有退出路径上持久化窗口状态 */
        private Stage primaryStage;

        /** 根据应用配置构建 Windows 原生窗口外观(DWM 暗色对话框、边框颜色、圆角偏好) */
        private static WindowAppearance windowAppearance(AppConfig config) {
            AppConfig.Platform platform = config == null ? null : config.platform();
            int borderColor = platform == null ? 0x00888800 : platform.windowBorderColor();
            WindowCornerPreference cornerPreference = parseCornerPreference(
                    platform == null ? null : platform.cornerPreference());
            return WindowAppearance.darkDialog(borderColor, cornerPreference);
        }

        /** 解析圆角偏好字符串为枚举值,解析失败默认不圆角 */
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

        /** JavaFX Application 启动入口:执行应用启动并配置原生窗口外观 */
        @Override
        public void start(Stage stage) {
            try {
                startApplication(stage);
                initWindows(stage);
            } catch (Throwable ex) {
                log.error("FxDecompiler 启动失败", ex);
                writeStartupFailure(ex);
                throw ex;
            }
        }

        /**
         * 启动 JavaFX 应用主流程:加载配置、初始化缓存与重命名服务、设置国际化、创建主窗口
         *
         * <p>initStyle(EXTENDED) 在 JavaFX 中被标记为 deprecated,
         * 当前配合 AppHeaderBar 提供完整窗口交互,功能正常,暂时保留</p>
         */
        @SuppressWarnings("deprecation")
        private void startApplication(Stage stage) {
            primaryStage = stage;
            log.info("FxDecompiler 启动构建版本: headerBar-2026-06-17");
            config = AppContext.getInstance().start();
            DefaultWindowTheme.configure(windowAppearance(config));
            applyConfiguredLocale();

            IconHelper.setStageIcon(stage);

            try {
                stage.initStyle(StageStyle.EXTENDED);
            } catch (Exception e) {
                log.warn("EXTENDED 窗口样式不支持,回退到 DECORATED", e);
                stage.initStyle(StageStyle.DECORATED);
            }

            // 验证保存的窗口位置是否在可见屏幕范围内
            double savedX = config.window().x();
            double savedY = config.window().y();
            boolean onScreen = !javafx.stage.Screen.getScreensForRectangle(
                    savedX, savedY, 1, 1).isEmpty();
            if (onScreen) {
                stage.setX(savedX);
                stage.setY(savedY);
            } else {
                javafx.geometry.Rectangle2D bounds =
                        javafx.stage.Screen.getPrimary().getVisualBounds();
                stage.setX(bounds.getMinX() + 100);
                stage.setY(bounds.getMinY() + 100);
            }
            stage.setWidth(Math.max(config.window().width(), 400));
            stage.setHeight(Math.max(config.window().height(), 300));
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
                    try {
                        Path path = Path.of(args.get(++i));
                        if (Files.exists(path)) {
                            window.openInitialFile(path.toFile());
                        }
                    } catch (java.nio.file.InvalidPathException e) {
                        log.warn("无效的 --open 路径参数: {}", args.get(i), e);
                    }
                }
            }
        }

        /** 根据配置的语言设置切换应用的国际化语言 */
        private void applyConfiguredLocale() {
            if ("en".equalsIgnoreCase(config.language())) {
                I18nUtil.switchLocale(Locale.ENGLISH);
            } else if ("zh-CN".equalsIgnoreCase(config.language())) {
                I18nUtil.switchLocale(Locale.SIMPLIFIED_CHINESE);
            }
        }

        /**
         * 应用关闭时释放资源:保存窗口状态与配置、停止后台任务、关闭反编译缓存
         */
        @Override
        public void stop() {
            // 先保存配置,确保异步任务中的变更不会丢失
            if (config != null) {
                if (primaryStage != null) {
                    saveWindowState(primaryStage);
                }
                config.save();
            }
            if (window != null) {
                window.shutdownResources();
            }
            AppContext.getInstance().shutdown();
        }

        /**
         * 将当前窗口的位置、大小和最大化状态保存到配置对象
         *
         * @param stage 主窗口 Stage
         */
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
