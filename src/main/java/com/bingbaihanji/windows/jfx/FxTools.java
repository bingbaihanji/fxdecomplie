package com.bingbaihanji.windows.jfx;

import com.bingbaihanji.windows.platform.win32.NativeWindowsTools;
import com.sun.jna.platform.win32.WinDef;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * JavaFX 窗口工具类 — 原生句柄获取、窗口定位、屏幕信息等跨平台工具
 *
 * <h3>主要能力</h3>
 * <ul>
 *   <li><b>原生句柄</b> — 通过 {@link WindowToolkit} 委托获取原生窗口句柄</li>
 *   <li><b>窗口定位</b> — 居中、相对于父窗口/屏幕定位</li>
 *   <li><b>屏幕信息</b> — 获取屏幕边界、视觉边界(排除任务栏)</li>
 *   <li><b>Stage 快捷操作</b> — 设置图标、安全关闭等</li>
 * </ul>
 *
 * <p>主题/DWM 相关功能已迁移至 {@link DefaultWindowTheme}</p>
 *
 * @author bingbaihanji
 * @see DefaultWindowTheme
 * @see WindowToolkit
 */
public final class FxTools {

    private static final Logger logger = LoggerFactory.getLogger(FxTools.class);

    private FxTools() {
        throw new AssertionError("工具类，不可实例化");
    }

    // ==================== 窗口句柄 ====================

    /**
     * 获取指定 Stage 的 Win32 原生窗口句柄(HWND)
     *
     * <p>通过 {@link WindowToolkit} 获取原生句柄， 转换为 JNA HWND 对象
     * 非 Windows 平台或获取失败返回 null</p>
     *
     * @param stage JavaFX Stage
     * @return 原生 HWND，获取失败返回 null
     */
    public static WinDef.HWND getWindowHandle(Stage stage) {
        return WindowToolkit.nativeHandle(stage)
                .filter(handle -> "win32".equals(handle.platformId()))
                .map(handle -> NativeWindowsTools.getHWndByEnumeration(handle.value()))
                .orElse(null);
    }


    // ==================== 窗口居中 ====================

    /**
     * 将 Stage 居中到屏幕中央
     * 应在 {@code stage.show()} 之前调用
     *
     * @param stage 目标 Stage
     */
    public static void centerOnScreen(Stage stage) {
        centerOnScreen(stage, Screen.getPrimary());
    }

    /**
     * 将 Stage 居中到指定屏幕中央
     *
     * @param stage  目标 Stage
     * @param screen 目标屏幕
     */
    public static void centerOnScreen(Stage stage, Screen screen) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(screen, "screen");
        Rectangle2D bounds = screen.getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }

    /**
     * 将未显示的 Stage 居中于父窗口后，注册一次性的尺寸监听来完成微调居中
     *
     * <p>适用场景：窗口在 {@code show()} 前未设置 size，
     * 或 size 由场景内容动态决定时首次布局完成后自动居中一次</p>
     *
     * @param stage  目标 Stage
     * @param parent 父窗口
     */
    public static void centerOnParentAfterShow(Stage stage, Window parent) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(parent, "parent");
        EventHandler<WindowEvent> handler = new EventHandler<>() {
            @Override
            public void handle(WindowEvent event) {
                stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, this);
                centerOnParent(stage, parent);
            }
        };
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, handler);
    }

    /**
     * 将 Stage 居中于父窗口
     *
     * @param stage  目标 Stage
     * @param parent 父窗口
     */
    public static void centerOnParent(Stage stage, Window parent) {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(parent, "parent");
        double pw = parent.getWidth();
        double ph = parent.getHeight();
        double sw = stage.getWidth();
        double sh = stage.getHeight();
        stage.setX(parent.getX() + (pw - sw) / 2);
        stage.setY(parent.getY() + (ph - sh) / 2);
    }

    // ==================== 同步居中 ====================

    /**
     * 同步居中 Stage 到屏幕中央 — 先调用 {@code sizeToScene()}，
     * 然后居中到主屏幕的视觉边界中央
     *
     * @param stage 目标 Stage
     */
    public static void centerOnScreenSync(Stage stage) {
        Objects.requireNonNull(stage, "stage");
        stage.sizeToScene();
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        stage.setX(bounds.getMinX() + (bounds.getWidth() - stage.getWidth()) / 2);
        stage.setY(bounds.getMinY() + (bounds.getHeight() - stage.getHeight()) / 2);
    }

    // ==================== 屏幕信息 ====================

    /**
     * 获取主屏幕的视觉边界(排除任务栏)
     */
    public static Rectangle2D getPrimaryVisualBounds() {
        return Screen.getPrimary().getVisualBounds();
    }

    /**
     * 获取主屏幕的完整边界
     */
    public static Rectangle2D getPrimaryBounds() {
        return Screen.getPrimary().getBounds();
    }

    /**
     * 获取所有屏幕列表
     */
    public static List<Screen> getScreens() {
        return Screen.getScreens();
    }

    /**
     * 返回包含指定点的屏幕，找不到则返回主屏幕
     *
     * @param x 屏幕坐标 x
     * @param y 屏幕坐标 y
     */
    public static Screen getScreenAt(double x, double y) {
        for (Screen screen : Screen.getScreens()) {
            if (screen.getBounds().contains(x, y)) {
                return screen;
            }
        }
        return Screen.getPrimary();
    }

    /**
     * 获取包含指定窗口大部分区域的屏幕，用于多显示器场景恢复窗口位置
     *
     * @param window 目标窗口
     * @return 包含窗口中心点的屏幕，找不到返回主屏幕
     */
    public static Screen getScreenForWindow(Window window) {
        if (window == null) {
            return Screen.getPrimary();
        }
        double cx = window.getX() + window.getWidth() / 2;
        double cy = window.getY() + window.getHeight() / 2;
        return getScreenAt(cx, cy);
    }

    // ==================== 窗口图标 ====================

    /**
     * 从 classpath 加载图标并设置到 Stage
     * 加载失败时静默跳过(不抛异常)
     *
     * @param stage     目标 Stage
     * @param classpath classpath 路径，如 {@code "icons/app.png"}
     */
    public static void setStageIcon(Stage stage, String classpath) {
        try (InputStream is = FxTools.class.getClassLoader().getResourceAsStream(classpath)) {
            if (is != null) {
                stage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {
            logger.debug("设置Stage图标失败: {}", classpath, ignored);
            // 图标加载失败不影响功能
        }
    }

    /** 为多个 Stage 批量设置图标(例如所有弹出窗口) */
    public static void setStageIcons(String classpath, Stage... stages) {
        Image icon = null;
        try (InputStream is = FxTools.class.getClassLoader().getResourceAsStream(classpath)) {
            if (is != null) {
                icon = new Image(is);
            }
        } catch (Exception ignored) {
            logger.debug("批量设置Stage图标失败: {}", classpath, ignored);
            return;
        }
        if (icon == null) {
            return;
        }
        for (Stage stage : stages) {
            stage.getIcons().add(icon);
        }
    }

    // ==================== Stage 快捷操作 ====================

    /**
     * 在 JavaFX 线程安全地关闭 Stage
     * 当前已在 FX 线程则直接关闭，否则通过 {@code Platform.runLater} 调度
     */
    public static void closeStage(Stage stage) {
        if (stage == null) return;
        if (Platform.isFxApplicationThread()) {
            stage.close();
        } else {
            Platform.runLater(stage::close);
        }
    }

    /**
     * 在后台线程执行操作，完成后在 FX 线程执行回调
     *
     * <pre>{@code
     * FxTools.runAsync(() -> computeResult(), result -> updateUI(result));
     * }</pre>
     *
     * @param background 后台耗时操作
     * @param onSuccess  完成后的 UI 更新回调(在 FX 线程执行)
     * @param <T>        返回值类型
     */
    public static <T> void runAsync(java.util.concurrent.Callable<T> background, Consumer<T> onSuccess) {
        Thread t = new Thread(() -> {
            try {
                T result = background.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Exception e) {
                Platform.runLater(() -> {
                    throw new RuntimeException(e);
                });
            }
        }, "fx-async");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 确保代码在 JavaFX Application Thread 上执行
     * 如果已在 FX 线程则同步执行，否则通过 {@code Platform.runLater} 调度
     *
     * @param action 需要在 FX 线程执行的操作
     */
    public static void runOnFxThread(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }

    // ==================== 窗口可见性 ====================

    /**
     * 切换 Stage 的显示/隐藏状态，避免 null 检查
     *
     * @param stage 目标 Stage
     * @param show  true=显示(如已最小化则还原)，false=隐藏
     */
    public static void toggleStage(Stage stage, boolean show) {
        if (stage == null) return;
        if (show) {
            if (stage.isIconified()) {
                stage.setIconified(false);
            } else if (!stage.isShowing()) {
                stage.show();
            }
        } else {
            stage.hide();
        }
    }

    /**
     * 将 Stage 移到最前并请求焦点
     */
    public static void toFront(Stage stage) {
        if (stage == null) return;
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();
    }
}
