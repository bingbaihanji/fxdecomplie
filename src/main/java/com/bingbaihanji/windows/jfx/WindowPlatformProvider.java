package com.bingbaihanji.windows.jfx;

import com.bingbaihanji.windows.platform.NativeWindowHandle;
import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import com.bingbaihanji.windows.platform.WindowOperationStatus;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.Optional;

/**
 * 平台特定 JavaFX 窗口集成策略接口
 *
 * <p>每个平台实现此接口来提供原生窗口操作能力
 * 各方法都有默认实现,返回 {@link WindowOperationStatus#SKIPPED},
 * 确保不支持的平台不会抛出异常</p>
 */
public interface WindowPlatformProvider {

    /**
     * 返回平台标识符,如 "win32"、"generic"
     */
    String platformId();

    /**
     * 返回当前平台是否支持原生操作
     */
    boolean isSupported();

    /**
     * 获取 Stage 的原生窗口句柄
     *
     * @param stage   JavaFX Stage
     * @param timeout 获取句柄的最大等待时间
     * @return 原生窗口句柄,获取失败返回 {@code Optional.empty()}
     */
    Optional<NativeWindowHandle> nativeHandle(Stage stage, Duration timeout);

    /**
     * 应用原生窗口外观属性(暗色模式、阴影、背景材质等)
     *
     * @param stage      目标 Stage
     * @param appearance 原生外观配置
     * @param timeout    操作超时时间
     * @return 操作结果,不支持时返回 SKIPPED
     */
    default WindowOperationResult applyAppearance(Stage stage,
                                                  WindowAppearance appearance,
                                                  Duration timeout) {
        return WindowOperationResult.skipped("applyAppearance",
                platformId() + " 不支持原生外观属性");
    }

    /**
     * 通过原生 API 设置窗口置顶
     */
    default WindowOperationResult setAlwaysOnTop(Stage stage, boolean alwaysOnTop, Duration timeout) {
        return WindowOperationResult.skipped("setAlwaysOnTop",
                platformId() + " 不支持原生置顶控制");
    }

    /**
     * 通过原生 API 最小化窗口
     */
    default WindowOperationResult minimize(Stage stage, Duration timeout) {
        return WindowOperationResult.skipped("minimize",
                platformId() + " 不支持原生最小化");
    }

    /**
     * 通过原生 API 最大化窗口
     */
    default WindowOperationResult maximize(Stage stage, Duration timeout) {
        return WindowOperationResult.skipped("maximize",
                platformId() + " 不支持原生最大化");
    }

    /**
     * 通过原生 API 还原窗口
     */
    default WindowOperationResult restore(Stage stage, Duration timeout) {
        return WindowOperationResult.skipped("restore",
                platformId() + " 不支持原生还原");
    }

    /**
     * 通过原生 API 将窗口带到最前
     */
    default WindowOperationResult bringToFront(Stage stage, Duration timeout) {
        return WindowOperationResult.skipped("bringToFront",
                platformId() + " 不支持原生前台控制");
    }

    /**
     * 通过原生 API 设置窗口不透明度
     */
    default WindowOperationResult setOpacity(Stage stage, double opacity, Duration timeout) {
        return WindowOperationResult.skipped("setOpacity",
                platformId() + " 不支持原生不透明度");
    }

    /**
     * 通过原生 API 闪烁任务栏窗口图标
     */
    default WindowOperationResult flash(Stage stage, int count, int timeoutMs, Duration timeout) {
        return WindowOperationResult.skipped("flash",
                platformId() + " 不支持原生任务栏闪烁");
    }

    /**
     * 停止任务栏窗口图标闪烁
     */
    default WindowOperationResult stopFlash(Stage stage, Duration timeout) {
        return WindowOperationResult.skipped("stopFlash",
                platformId() + " 不支持原生任务栏闪烁");
    }

    /**
     * 通过原生 API 禁用/启用窗口大小调整
     */
    default WindowOperationResult disableResize(Stage stage, boolean disabled, Duration timeout) {
        return WindowOperationResult.skipped("disableResize",
                platformId() + " 不支持原生调整大小样式更新");
    }

    /**
     * 在任务切换器(Alt+Tab)中隐藏或显示窗口
     */
    default WindowOperationResult hideFromTaskSwitcher(Stage stage, boolean hide, Duration timeout) {
        return WindowOperationResult.skipped("hideFromTaskSwitcher",
                platformId() + " 不支持原生任务切换器更新");
    }

    /**
     * 获取窗口所在屏幕的缩放比例
     *
     * @param stage   目标 Stage
     * @param timeout 操作超时时间
     * @return 缩放比例,默认返回 1.0
     */
    default double windowScaleFactor(Stage stage, Duration timeout) {
        return 1.0;
    }
}
