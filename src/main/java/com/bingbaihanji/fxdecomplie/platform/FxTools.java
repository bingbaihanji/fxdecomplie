package com.bingbaihanji.fxdecomplie.platform;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.platform.win32.DwmWindowCornerPreference;
import com.bingbaihanji.fxdecomplie.platform.win32.NativeWindowsTools;
import com.bingbaihanji.fxdecomplie.platform.win32.SystemBackdropType;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.lang.reflect.Method;

/**
 * JavaFX 窗口工具类，提供原生窗口句柄获取和 Windows DWM 暗色主题适配。
 *
 * <p>使用单例模式，通过静态方法暴露所有功能。获取窗口句柄时优先反射
 * {@code StageHelper.getPeer() -> getRawHandle()}，失败则回退到
 * {@code FindWindow} 按标题查找。</p>
 *
 * <p>暗色主题应用通过后台线程轮询，避免阻塞 JavaFX UI 线程。
 * 可通过 {@link #loadPlatformConfig(AppConfig)} 在启动时注入平台配置。</p>
 *
 * @author bingbaihanji
 */
public final class FxTools {

    // ==================== 查找句柄重试常量 ====================

    /** FindWindow 查找最大重试次数（每次 20ms，总计约 1 秒） */
    private static final int FIND_WINDOW_MAX_RETRIES = 50;
    /** FindWindow 查找重试间隔（毫秒） */
    private static final int FIND_WINDOW_RETRY_DELAY_MS = 20;

    // ==================== DWM 属性重试常量 ====================

    /** DWM 属性应用最大重试次数 */
    private static final int DWM_MAX_RETRIES = 10;
    /** DWM 属性应用重试间隔（毫秒），累计最多 800ms */
    private static final int DWM_RETRY_DELAY_MS = 80;

    // ==================== 平台配置（启动时注入） ====================

    /** 窗口边框颜色 (COLORREF: 0x00BBGGRR)，默认 0x00888800（暗黄绿） */
    private static int windowBorderColor = 0x00888800;
    /** 主窗口圆角偏好 */
    private static DwmWindowCornerPreference cornerPreference = DwmWindowCornerPreference.DO_NOT_ROUND;

    private FxTools() {
    }

    // ==================== 单例 ====================

    /** 获取单例实例。 */
    public static FxTools getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /** 静态内部类持有单例，线程安全的懒加载。 */
    private static class SingletonHolder {
        private static final FxTools INSTANCE = new FxTools();
    }

    // ==================== 配置注入 ====================

    /**
     * 从 AppConfig 加载平台相关配置值。
     * 应在 Application.start() 中尽早调用一次。
     */
    public static void loadPlatformConfig(AppConfig config) {
        if (config == null || config.platform() == null) return;
        AppConfig.Platform p = config.platform();
        windowBorderColor = p.windowBorderColor();
        cornerPreference = parseCornerPreference(p.cornerPreference());
    }

    private static DwmWindowCornerPreference parseCornerPreference(String value) {
        try {
            return DwmWindowCornerPreference.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            return DwmWindowCornerPreference.DO_NOT_ROUND;
        }
    }



    // ==================== 窗口句柄 ====================

    /**
     * 获取指定 Stage 的 Win32 原生窗口句柄。
     *
     * <p>先通过反射调用内部 API 获取，失败则用 FindWindow 按标题查找。
     * 注意：FindWindow 内部有最长约 1 秒的重试等待，请在后台线程调用。</p>
     *
     * @param stage JavaFX Stage
     * @return 原生 HWND，获取失败返回 null
     */
    public static WinDef.HWND getWindowHandle(Stage stage) {
        return getInstance().getWindowHandleInternal(stage);
    }

    /** 内部实现：先反射，后 FindWindow 回退。 */
    private WinDef.HWND getWindowHandleInternal(Stage stage) {
        if (stage == null) return null;
        WinDef.HWND hwnd = getHandleByReflection(stage);
        return hwnd != null ? hwnd : getHandleByTitle(stage);
    }

    /**
     * 通过反射 {@code com.sun.javafx.stage.StageHelper#getPeer(Window)}
     * 获取窗口的 {@code getRawHandle()} 返回值。
     *
     * <p>该方法依赖 JavaFX 内部 API，可能在未来的 JDK 版本中失效，
     * 此时返回 null 由调用方回退处理。</p>
     */
    private WinDef.HWND getHandleByReflection(Stage stage) {
        try {
            Class<?> stageHelperCls = Class.forName("com.sun.javafx.stage.StageHelper");
            Method getPeerMethod = stageHelperCls.getMethod("getPeer", Window.class);
            Object peer = getPeerMethod.invoke(null, stage);
            if (peer == null) return null;
            try {
                Method getRawHandle = peer.getClass().getMethod("getRawHandle");
                Object raw = getRawHandle.invoke(peer);
                if (raw instanceof Long) {
                    return new WinDef.HWND(new Pointer((Long) raw));
                }
                if (raw instanceof Integer) {
                    return new WinDef.HWND(new Pointer(Integer.toUnsignedLong((Integer) raw)));
                }
            } catch (NoSuchMethodException ignore) {
                // getRawHandle 不存在，继续尝试其他方式
            }
        } catch (Throwable ignored) {
            // 反射完全失败，回退到标题查找
        }
        return null;
    }

    /**
     * 通过 Win32 {@code FindWindow(null, title)} 按窗口标题查找句柄。
     *
     * <p>窗口显示后原生句柄可能不会立刻创建，因此内置重试循环
     * （最多 {@value #FIND_WINDOW_MAX_RETRIES} 次，每次 {@value #FIND_WINDOW_RETRY_DELAY_MS}ms）。
     * 找到非零句柄立即返回，超时或标题为空返回 null。</p>
     *
     * @param stage JavaFX Stage（标题需已设置）
     * @return 原生 HWND，超时或标题为空时返回 null
     */
    private WinDef.HWND getHandleByTitle(Stage stage) {
        String title = stage.getTitle();
        if (title == null || title.isEmpty()) return null;
        for (int i = 0; i < FIND_WINDOW_MAX_RETRIES; i++) {
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, title);
            if (hwnd != null && Pointer.nativeValue(hwnd.getPointer()) != 0L) {
                return hwnd;
            }
            try {
                Thread.sleep(FIND_WINDOW_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    // ==================== Windows DWM 主题 ====================

    /**
     * 为弹窗类 Stage（搜索、设置、导出等对话框）应用 Windows 暗色主题和阴影。
     *
     * <p>在后台线程中轮询原生窗口句柄（最多 {@value #DWM_MAX_RETRIES} 次，
     * 每次 {@value #DWM_RETRY_DELAY_MS}ms），找到后通过 Platform.runLater
     * 执行 DWM API。非 Windows 平台直接返回。</p>
     *
     * @param window 弹窗对应的 JavaFX Window
     */
    public static void applyWindowDarkMode(Window window) {
        if (!com.sun.jna.Platform.isWindows()) return;
        if (!(window instanceof Stage stage)) return;
        new Thread(() -> scheduleDwmRetry(stage, 0)).start();
    }


    /**
     * DWM 设置重试调度核心。
     *
     * <p>每次尝试获取原生窗口句柄，成功则通过 Platform.runLater
     * 切换到 JavaFX 线程执行 DWM API 调用。失败则等待
     * {@value #DWM_RETRY_DELAY_MS}ms 后重试，最多
     * {@value #DWM_MAX_RETRIES} 次。</p>
     *
     * @param stage   目标 Stage
     * @param attempt 当前尝试次数（从 0 开始）
     */
    private static void scheduleDwmRetry(Stage stage, int attempt) {
        WinDef.HWND handle = getWindowHandle(stage);
        if (handle != null) {
            Platform.runLater(() -> {
                NativeWindowsTools.setWindowDarkMode(handle, true);
                NativeWindowsTools.enableWindowShadow(handle, true);
                NativeWindowsTools.setSystemStageStyle(handle, SystemBackdropType.TRANSIENTWINDOW);
                NativeWindowsTools.setWindowCornerPreference(handle, cornerPreference);
                NativeWindowsTools.setWindowBorderColor(handle, windowBorderColor);

            });
            return;
        }
        if (attempt < DWM_MAX_RETRIES) {
            try {
                Thread.sleep(DWM_RETRY_DELAY_MS);
            } catch (InterruptedException e) {
                return;
            }
            scheduleDwmRetry(stage, attempt + 1);
        }
    }
}
