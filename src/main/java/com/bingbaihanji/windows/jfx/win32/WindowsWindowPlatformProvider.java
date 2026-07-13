package com.bingbaihanji.windows.jfx.win32;

import com.bingbaihanji.windows.jfx.WindowPlatformProvider;
import com.bingbaihanji.windows.platform.NativeWindowHandle;
import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import com.bingbaihanji.windows.platform.win32.NativeWindowsTools;
import com.sun.jna.platform.win32.WinDef;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;

/**
 * Windows 平台的 {@link WindowPlatformProvider} 实现,基于 JNA Win32/DWM API
 *
 * <p>通过 {@link NativeWindowsTools} 调用底层原生 API,提供窗口外观 状态控制等能力
 * 仅在 Windows 平台可用,其他平台应使用 {@code NoopWindowPlatformProvider}</p>
 */
public final class WindowsWindowPlatformProvider implements WindowPlatformProvider {

    public static final WindowsWindowPlatformProvider INSTANCE = new WindowsWindowPlatformProvider();

    private WindowsWindowPlatformProvider() {
    }

    @Override
    public String platformId() {
        return "win32";
    }

    @Override
    public boolean isSupported() {
        return com.sun.jna.Platform.isWindows();
    }

    @Override
    public Optional<NativeWindowHandle> nativeHandle(Stage stage, Duration timeout) {
        return hwnd(stage, timeout)
                .map(handle -> new NativeWindowHandle(platformId(), Win32WindowHandles.pointerValue(handle)));
    }

    @Override
    public WindowOperationResult applyAppearance(Stage stage,
                                                 WindowAppearance appearance,
                                                 Duration timeout) {
        return withHandle("applyAppearance", stage, timeout, hwnd -> {
            ArrayList<WindowOperationResult> results = new ArrayList<>();
            if (appearance.darkMode() != null) {
                results.add(NativeWindowsTools.setWindowDarkMode(hwnd, appearance.darkMode()));
            }
            if (appearance.shadow() != null) {
                results.add(NativeWindowsTools.enableWindowShadow(hwnd, appearance.shadow()));
            }
            if (appearance.backdropType() != null) {
                results.add(NativeWindowsTools.setSystemStageStyle(hwnd, appearance.backdropType()));
            }
            if (appearance.cornerPreference() != null) {
                results.add(NativeWindowsTools.setWindowCornerPreference(hwnd, appearance.cornerPreference()));
            }
            if (appearance.borderColor() != null) {
                results.add(NativeWindowsTools.setWindowBorderColor(hwnd, appearance.borderColor()));
            }
            if (appearance.captionColor() != null) {
                results.add(NativeWindowsTools.setWindowCaptionColor(hwnd, appearance.captionColor()));
            }
            if (appearance.textColor() != null) {
                results.add(NativeWindowsTools.setWindowTextColor(hwnd, appearance.textColor()));
            }
            if (appearance.frameMargins() != null) {
                WindowAppearance.FrameMargins margins = appearance.frameMargins();
                results.add(NativeWindowsTools.extendFrameIntoClientArea(
                        hwnd, margins.left(), margins.right(), margins.top(), margins.bottom()));
            }
            return WindowOperationResult.combine("applyAppearance", results);
        });
    }

    @Override
    public WindowOperationResult setAlwaysOnTop(Stage stage, boolean alwaysOnTop, Duration timeout) {
        return withHandle("setAlwaysOnTop", stage, timeout,
                hwnd -> NativeWindowsTools.setAlwaysOnTop(hwnd, alwaysOnTop));
    }

    @Override
    public WindowOperationResult minimize(Stage stage, Duration timeout) {
        return withHandle("minimize", stage, timeout, NativeWindowsTools::minimizeWindow);
    }

    @Override
    public WindowOperationResult maximize(Stage stage, Duration timeout) {
        return withHandle("maximize", stage, timeout, NativeWindowsTools::maximizeWindow);
    }

    @Override
    public WindowOperationResult restore(Stage stage, Duration timeout) {
        return withHandle("restore", stage, timeout, NativeWindowsTools::restoreWindow);
    }

    @Override
    public WindowOperationResult bringToFront(Stage stage, Duration timeout) {
        return withHandle("bringToFront", stage, timeout, NativeWindowsTools::bringToFront);
    }

    @Override
    public WindowOperationResult setOpacity(Stage stage, double opacity, Duration timeout) {
        return withHandle("setOpacity", stage, timeout,
                hwnd -> NativeWindowsTools.setWindowAlpha(hwnd, (float) opacity));
    }

    @Override
    public WindowOperationResult flash(Stage stage, int count, int timeoutMs, Duration timeout) {
        return withHandle("flash", stage, timeout,
                hwnd -> NativeWindowsTools.flashWindow(hwnd, count, timeoutMs));
    }

    @Override
    public WindowOperationResult stopFlash(Stage stage, Duration timeout) {
        return withHandle("stopFlash", stage, timeout, NativeWindowsTools::stopFlashWindow);
    }

    @Override
    public WindowOperationResult disableResize(Stage stage, boolean disabled, Duration timeout) {
        return withHandle("disableResize", stage, timeout,
                hwnd -> NativeWindowsTools.disableResize(hwnd, disabled));
    }

    @Override
    public WindowOperationResult hideFromTaskSwitcher(Stage stage, boolean hide, Duration timeout) {
        return withHandle("hideFromTaskSwitcher", stage, timeout,
                hwnd -> NativeWindowsTools.hideFromAltTab(hwnd, hide));
    }

    @Override
    public double windowScaleFactor(Stage stage, Duration timeout) {
        return hwnd(stage, timeout)
                .map(NativeWindowsTools::getWindowScaleFactor)
                .orElse(1.0);
    }

    /**
     * 获取 Stage 对应的原生 Win32 窗口句柄
     */
    private Optional<WinDef.HWND> hwnd(Stage stage, Duration timeout) {
        if (!isSupported() || stage == null) {
            return Optional.empty();
        }
        return Win32WindowHandles.find(stage, timeout);
    }

    /**
     * 获取窗口句柄后执行指定操作,句柄不可用时返回 SKIPPED 结果
     */
    private WindowOperationResult withHandle(String operation,
                                             Stage stage,
                                             Duration timeout,
                                             Function<WinDef.HWND, WindowOperationResult> action) {
        return hwnd(stage, timeout)
                .map(hwnd -> {
                    if (!NativeWindowsTools.isWindow(hwnd)) {
                        return WindowOperationResult.skipped(operation, "Window handle is no longer valid");
                    }
                    return action.apply(hwnd);
                })
                .orElseGet(() -> WindowOperationResult.skipped(operation, "原生窗口句柄不可用"));
    }
}
