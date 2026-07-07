package com.bingbaihanji.windows.jfx;

import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowCornerPreference;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import javafx.stage.Window;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 可复用的 JavaFX 窗口默认原生主题
 *
 * <p>此类故意仅依赖平台包中的类型项目可以将自己的配置格式映射为
 * {@link WindowAppearance},并在启动时调用 {@link #configure(WindowAppearance)}</p>
 */
public final class DefaultWindowTheme {

    private static final Logger log = LoggerFactory.getLogger(DefaultWindowTheme.class);

    private static volatile WindowAppearance defaultAppearance = WindowAppearance.darkDialog(
            0x00888800,
            WindowCornerPreference.DO_NOT_ROUND);

    private DefaultWindowTheme() {
        throw new AssertionError("工具类,不可实例化");
    }

    /**
     * 更新包级别的默认外观配置,供 {@link #applyWindowDarkMode(Window)} 使用
     *
     * @param appearance 新的默认外观配置,不能为 null
     */
    public static void configure(WindowAppearance appearance) {
        defaultAppearance = Objects.requireNonNull(appearance, "appearance");
    }

    /**
     * 返回当前默认外观配置
     */
    public static WindowAppearance defaultAppearance() {
        return defaultAppearance;
    }

    /**
     * 异步应用已配置的原生外观到窗口
     *
     * <p>不支持的平台将被忽略失败时以 debug 级别记录日志,
     * 因为原生主题属性属于渐进增强,而非必需的应用行为</p>
     */
    public static CompletableFuture<WindowOperationResult> applyWindowDarkMode(Window window) {
        return apply(window, defaultAppearance);
    }

    /**
     * 异步应用指定的外观配置到窗口
     *
     * @param window     目标窗口
     * @param appearance 要应用的外观配置
     * @return 可组合的异步操作结果
     */
    public static CompletableFuture<WindowOperationResult> apply(Window window, WindowAppearance appearance) {
        return WindowToolkit.applyAppearanceAsync(window, appearance, result -> {
            if (result.isFailure()) {
                log.debug("应用原生窗口外观失败: {}", result);
            }
        });
    }
}
