package com.bingbaihanji.windows.jfx;

import com.bingbaihanji.windows.jfx.win32.WindowsWindowPlatformProvider;
import com.bingbaihanji.windows.platform.NativeWindowHandle;
import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import com.bingbaihanji.windows.platform.WindowOperationStatus;
import javafx.application.Platform;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * JavaFX 原生窗口操作的平台无关入口点
 *
 * <p>调用方应优先使用此类，而非直接访问 Win32/JNA不支持的平台将返回
 * {@link WindowOperationStatus#SKIPPED}，而不是抛出异常</p>
 */
public final class WindowToolkit {

    public static final Duration DEFAULT_HANDLE_TIMEOUT = Duration.ofMillis(800);

    private static volatile ExecutorService executor = newExecutor();
    private static volatile WindowPlatformProvider provider = detectProvider();

    private WindowToolkit() {
        throw new AssertionError("工具类，不可实例化");
    }

    /**
     * 返回当前平台提供者
     */
    public static WindowPlatformProvider provider() {
        return provider;
    }

    /**
     * 替换平台提供者用于测试和嵌入式场景
     */
    public static void setProvider(WindowPlatformProvider replacement) {
        provider = Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * 重置为自动检测的平台提供者
     */
    public static void resetProvider() {
        provider = detectProvider();
    }

    /**
     * 返回当前平台是否支持原生窗口操作
     */
    public static boolean isNativeSupported() {
        return provider.isSupported();
    }

    /**
     * 获取指定 Stage 的原生窗口句柄，使用默认超时时间
     */
    public static Optional<NativeWindowHandle> nativeHandle(Stage stage) {
        return nativeHandle(stage, DEFAULT_HANDLE_TIMEOUT);
    }


    /**
     * 获取指定 Stage 的原生窗口句柄，支持自定义超时时间
     *
     * @param stage   JavaFX Stage
     * @param timeout 获取句柄的最大等待时间
     * @return 原生窗口句柄，获取失败或 stage 为 null 时返回 {@code Optional.empty()}
     */
    public static Optional<NativeWindowHandle> nativeHandle(Stage stage, Duration timeout) {
        if (stage == null) {
            return Optional.empty();
        }
        return provider.nativeHandle(stage, normalizeTimeout(timeout));
    }

    /**
     * 异步获取原生窗口句柄
     */
    public static CompletableFuture<Optional<NativeWindowHandle>> nativeHandleAsync(Stage stage) {
        return CompletableFuture.supplyAsync(() -> nativeHandle(stage), executor());
    }

    /**
     * 为窗口应用原生外观属性，使用默认超时时间
     */
    public static WindowOperationResult applyAppearance(Window window, WindowAppearance appearance) {
        if (!(window instanceof Stage stage)) {
            return WindowOperationResult.skipped("applyAppearance", "窗口不是 Stage 类型");
        }
        return applyAppearance(stage, appearance, DEFAULT_HANDLE_TIMEOUT);
    }

    /**
     * 为 Stage 应用原生外观属性，支持自定义超时时间
     *
     * @param stage      目标 Stage
     * @param appearance 原生外观配置
     * @param timeout    操作超时时间
     * @return 操作结果
     */
    public static WindowOperationResult applyAppearance(Stage stage,
                                                        WindowAppearance appearance,
                                                        Duration timeout) {
        if (stage == null) {
            return WindowOperationResult.skipped("applyAppearance", "Stage 为 null");
        }
        if (appearance == null || !appearance.hasNativeAttributes()) {
            return WindowOperationResult.skipped("applyAppearance", "未请求任何原生外观属性");
        }
        return provider.applyAppearance(stage, appearance, normalizeTimeout(timeout));
    }

    /**
     * 异步为窗口应用原生外观属性，完成后在 JavaFX 线程中回调
     *
     * @param window     目标窗口
     * @param appearance 原生外观配置
     * @param onComplete 完成后的回调(在 JavaFX 线程中执行，失败时为 null)
     * @return 可组合的异步结果
     */
    public static CompletableFuture<WindowOperationResult> applyAppearanceAsync(
            Window window,
            WindowAppearance appearance,
            Consumer<WindowOperationResult> onComplete) {
        CompletableFuture<WindowOperationResult> future = CompletableFuture.supplyAsync(
                () -> applyAppearance(window, appearance),
                executor());
        if (onComplete != null) {
            future.whenComplete((result, failure) -> runCallbackOnFxThread(() -> {
                if (failure != null) {
                    onComplete.accept(WindowOperationResult.failed(
                            "applyAppearance", 0, failure.getMessage()));
                } else {
                    onComplete.accept(result);
                }
            }));
        }
        return future;
    }

    /**
     * 设置窗口置顶，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult setAlwaysOnTop(Stage stage, boolean alwaysOnTop) {
        if (stage == null) {
            return WindowOperationResult.skipped("setAlwaysOnTop", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> stage.setAlwaysOnTop(alwaysOnTop));
        return WindowOperationResult.success("setAlwaysOnTop", "通过 JavaFX Stage API 应用");
    }

    /**
     * 最小化窗口，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult minimize(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("minimize", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> stage.setIconified(true));
        return WindowOperationResult.success("minimize", "通过 JavaFX Stage API 应用");
    }

    /**
     * 最大化窗口，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult maximize(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("maximize", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> stage.setMaximized(true));
        return WindowOperationResult.success("maximize", "通过 JavaFX Stage API 应用");
    }

    /**
     * 还原窗口(取消最小化和最大化)，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult restore(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("restore", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> {
            stage.setIconified(false);
            stage.setMaximized(false);
        });
        return WindowOperationResult.success("restore", "通过 JavaFX Stage API 应用");
    }

    /**
     * 将窗口带到最前，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult bringToFront(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("bringToFront", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> {
            if (!stage.isShowing()) {
                stage.show();
            }
            stage.toFront();
            stage.requestFocus();
        });
        return WindowOperationResult.success("bringToFront", "通过 JavaFX Stage API 应用");
    }

    /**
     * 通过原生 API 强制将窗口带到最前，适用于 JavaFX API 不够强力的场景
     */
    public static WindowOperationResult forceBringToFront(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("forceBringToFront", "Stage 为 null");
        }
        return provider.bringToFront(stage, DEFAULT_HANDLE_TIMEOUT);
    }

    /**
     * 设置窗口不透明度
     *
     * @param stage   目标 Stage
     * @param opacity 不透明度，取值范围 (0, 1]
     * @throws IllegalArgumentException 如果 opacity 不在 (0, 1] 范围内
     */
    public static WindowOperationResult setOpacity(Stage stage, double opacity) {
        if (stage == null) {
            return WindowOperationResult.skipped("setOpacity", "Stage 为 null");
        }
        if (opacity <= 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("不透明度必须在 (0, 1] 范围内");
        }
        FxTools.runOnFxThread(() -> stage.setOpacity(opacity));
        return WindowOperationResult.success("setOpacity", "通过 JavaFX Stage API 应用");
    }

    /**
     * 闪烁任务栏窗口图标(使用默认参数)
     */
    public static WindowOperationResult flash(Stage stage) {
        return flash(stage, 0, 0);
    }

    /**
     * 通过原生 API 闪烁任务栏窗口图标
     *
     * @param stage     目标 Stage
     * @param count     闪烁次数，0 表示使用系统默认
     * @param timeoutMs 每次闪烁的超时毫秒数，0 表示使用系统默认
     */
    public static WindowOperationResult flash(Stage stage, int count, int timeoutMs) {
        if (stage == null) {
            return WindowOperationResult.skipped("flash", "Stage 为 null");
        }
        return provider.flash(stage, count, timeoutMs, DEFAULT_HANDLE_TIMEOUT);
    }

    /**
     * 停止任务栏窗口图标闪烁
     */
    public static WindowOperationResult stopFlash(Stage stage) {
        if (stage == null) {
            return WindowOperationResult.skipped("stopFlash", "Stage 为 null");
        }
        return provider.stopFlash(stage, DEFAULT_HANDLE_TIMEOUT);
    }

    /**
     * 禁用或启用窗口大小调整，使用 JavaFX Stage API 实现
     */
    public static WindowOperationResult disableResize(Stage stage, boolean disabled) {
        if (stage == null) {
            return WindowOperationResult.skipped("disableResize", "Stage 为 null");
        }
        FxTools.runOnFxThread(() -> stage.setResizable(!disabled));
        return WindowOperationResult.success("disableResize", "通过 JavaFX Stage API 应用");
    }

    /**
     * 在任务切换器(Alt+Tab)中隐藏或显示窗口，通过原生 API 实现
     */
    public static WindowOperationResult hideFromTaskSwitcher(Stage stage, boolean hide) {
        if (stage == null) {
            return WindowOperationResult.skipped("hideFromTaskSwitcher", "Stage 为 null");
        }
        return provider.hideFromTaskSwitcher(stage, hide, DEFAULT_HANDLE_TIMEOUT);
    }

    /**
     * 获取窗口所在屏幕的缩放比例
     * 优先使用原生 API 获取精确缩放值，回退到 JavaFX 屏幕 API
     */
    public static double windowScaleFactor(Stage stage) {
        if (stage == null) {
            return Screen.getPrimary().getOutputScaleX();
        }
        double nativeScale = provider.windowScaleFactor(stage, Duration.ofMillis(150));
        if (nativeScale > 0.0 && nativeScale != 1.0) {
            return nativeScale;
        }
        return FxTools.getScreenForWindow(stage).getOutputScaleX();
    }

    /**
     * 停止本工具包创建的后台辅助线程
     *
     * <p>线程为守护线程，正常应用关闭时调用此方法不是必须的
     * 主要用于嵌入式使用和测试套件场景</p>
     */
    public static void shutdown() {
        executor.shutdownNow();
    }

    private static Duration normalizeTimeout(Duration timeout) {
        if (timeout == null || timeout.isNegative()) {
            return DEFAULT_HANDLE_TIMEOUT;
        }
        return timeout;
    }

    private static void runCallbackOnFxThread(Runnable callback) {
        if (Platform.isFxApplicationThread()) {
            callback.run();
            return;
        }
        try {
            Platform.runLater(callback);
        } catch (IllegalStateException ignored) {
            callback.run();
        }
    }

    private static ExecutorService executor() {
        ExecutorService current = executor;
        if (!current.isShutdown()) {
            return current;
        }
        synchronized (WindowToolkit.class) {
            if (executor.isShutdown()) {
                executor = newExecutor();
            }
            return executor;
        }
    }

    private static ExecutorService newExecutor() {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "window-toolkit-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    private static WindowPlatformProvider detectProvider() {
        if (com.sun.jna.Platform.isWindows()) {
            return WindowsWindowPlatformProvider.INSTANCE;
        }
        return NoopWindowPlatformProvider.INSTANCE;
    }
}
