package com.bingbaihanji.fxdecomplie.platform.win32;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

/**
 * Windows 原生窗口操作工具类
 *
 * <p>封装基于 JNA 的 Win32 / DWM API，提供窗口样式、透明度、状态控制等常用操作。</p>
 *
 * @author bingbaihanji
 */
public final class NativeWindowsTools {
    private NativeWindowsTools() {
    }

    // ==================== 句柄工具 ====================

    public static WinDef.HWND getHWndByEnumeration(Long headId) {
        if (headId == null || headId == 0L) return null;
        return new WinDef.HWND(Pointer.createConstant(headId));
    }

    // ==================== DWM 窗口效果 ====================

    /**
     * 设置窗口系统背景样式（Acrylic / Mica）
     *
     * @param hwnd     窗口句柄
     * @param backdropType 背景类型
     * @implNote 需要 Windows 11+
     */
    public static void setSystemStageStyle(WinDef.HWND hwnd, SystemBackdropType backdropType) {
        if (hwnd == null) {
            return;
        }
        IntByReference backdrop = new IntByReference(backdropType.getValue());
        Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_SYSTEMBACKDROP_TYPE,
                backdrop,
                Integer.BYTES
        );
    }

    /**
     * 设置窗口圆角偏好
     *
     * @implNote 需要 Windows 11+
     */
    public static void setWindowCornerPreference(WinDef.HWND hwnd, DwmWindowCornerPreference cornerPreference) {
        if (hwnd == null) {
            return;
        }
        IntByReference value = new IntByReference(cornerPreference.getValue());
        Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_WINDOW_CORNER_PREFERENCE,
                value,
                Integer.BYTES
        );
    }

    /**
     * 获取窗口圆角偏好
     *
     * @return 当前圆角偏好，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static DwmWindowCornerPreference getWindowCornerPreference(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return null;
        }
        IntByReference value = new IntByReference();
        int result = Win32Api.DwmApi.INSTANCE.DwmGetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_WINDOW_CORNER_PREFERENCE,
                value,
                Integer.BYTES
        );
        if (result == 0) {
            return DwmWindowCornerPreference.fromValue(value.getValue());
        }
        return null;
    }

    /**
     * 设置窗口暗色模式
     *
     * @implNote 需要 Windows 10 1809+
     */
    public static void setWindowDarkMode(WinDef.HWND hwnd, boolean useDarkMode) {
        if (hwnd == null) {
            return;
        }
        IntByReference value = new IntByReference(useDarkMode ? 1 : 0);
        Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_USE_IMMERSIVE_DARK_MODE,
                value,
                Integer.BYTES
        );
    }

    /**
     * 获取窗口暗色模式状态
     *
     * @return true=暗色模式，false=亮色模式，获取失败返回 null
     */
    public static Boolean getWindowDarkMode(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return null;
        }
        IntByReference value = new IntByReference();
        int result = Win32Api.DwmApi.INSTANCE.DwmGetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_USE_IMMERSIVE_DARK_MODE,
                value,
                Integer.BYTES
        );
        if (result == 0) {
            return value.getValue() != 0;
        }
        return null;
    }

    /**
     * 设置窗口边框颜色
     *
     * @param hwnd     窗口句柄
     * @param rgbColor 颜色值（COLORREF 格式：0x00BBGGRR）
     * @implNote 需要 Windows 11+
     */
    public static void setWindowBorderColor(WinDef.HWND hwnd, int rgbColor) {
        if (hwnd == null) {
            return;
        }
        IntByReference value = new IntByReference(rgbColor);
        Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_BORDER_COLOR,
                value,
                Integer.BYTES
        );
    }

    /**
     * 扩展窗口框架到客户区，使 DWM 渲染窗口阴影和边框
     *
     * @param hwnd   窗口句柄
     * @param left   左侧扩展像素
     * @param right  右侧扩展像素
     * @param top    顶部扩展像素
     * @param bottom 底部扩展像素
     */
    public static void extendFrameIntoClientArea(WinDef.HWND hwnd, int left, int right, int top, int bottom) {
        if (hwnd == null) {
            return;
        }
        Win32Api.DwmApi.MARGINS margins = new Win32Api.DwmApi.MARGINS();
        margins.cxLeftWidth = left;
        margins.cxRightWidth = right;
        margins.cyTopHeight = top;
        margins.cyBottomHeight = bottom;
        Win32Api.DwmApi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins);
    }

    /**
     * 扩展窗口框架到整个客户区（-1, -1, -1, -1），启用 DWM 阴影和玻璃效果
     */
    public static void extendFrameIntoClientArea(WinDef.HWND hwnd) {
        extendFrameIntoClientArea(hwnd, -1, -1, -1, -1);
    }

    /**
     * 设置窗口阴影
     *
     * @param hwnd    窗口句柄
     * @param enabled true=启用阴影，false=禁用阴影
     */
    public static void enableWindowShadow(WinDef.HWND hwnd, boolean enabled) {
        if (hwnd == null) {
            return;
        }
        Win32Api.DwmApi.MARGINS margins = new Win32Api.DwmApi.MARGINS();
        margins.cxLeftWidth = 0;
        margins.cxRightWidth = 0;
        margins.cyTopHeight = 0;
        margins.cyBottomHeight = enabled ? 1 : 0;
        Win32Api.DwmApi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins);
    }

    // ==================== 窗口透明度 ====================

    /**
     * 设置窗口透明度
     *
     * @param hwnd  窗口句柄
     * @param value 透明度值（0.0 = 完全透明，1.0 = 完全不透明）
     */
    public static void setWindowAlpha(WinDef.HWND hwnd, float value) {
        if (hwnd == null) {
            return;
        }
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException("Alpha value must be in range (0, 1], received: " + value);
        }
        byte alpha = (byte) (value * 255);

        BaseTSD.LONG_PTR exStyle = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE);
        long style = exStyle.longValue();
        long newStyle = style | Win32Constants.WindowStyleEx.WS_EX_LAYERED;

        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE,
                new BaseTSD.LONG_PTR(newStyle).toPointer());

        Win32Api.User32Api.INSTANCE.SetLayeredWindowAttributes(
                hwnd, 0, alpha, Win32Constants.LayeredWindowAttribute.LWA_ALPHA);
    }

    // ==================== 窗口状态 ====================

    /**
     * 设置窗口置顶 / 取消置顶
     */
    public static void setAlwaysOnTop(WinDef.HWND hwnd, boolean alwaysOnTop) {
        if (hwnd == null) {
            return;
        }
        WinDef.HWND insertAfter = alwaysOnTop
                ? Win32Constants.HWndInsertAfter.HWND_TOPMOST
                : Win32Constants.HWndInsertAfter.HWND_NOTOPMOST;
        Win32Api.User32Api.INSTANCE.SetWindowPos(
                hwnd, insertAfter, 0, 0, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_NOACTIVATE);
    }

    /** 最小化窗口 */
    public static void minimizeWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return;
        }
        Win32Api.User32Api.INSTANCE.ShowWindow(hwnd, Win32Constants.ShowWindowCmd.SW_MINIMIZE);
    }

    /** 最大化窗口 */
    public static void maximizeWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return;
        }
        Win32Api.User32Api.INSTANCE.ShowWindow(hwnd, Win32Constants.ShowWindowCmd.SW_MAXIMIZE);
    }

    /** 还原窗口 */
    public static void restoreWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return;
        }
        Win32Api.User32Api.INSTANCE.ShowWindow(hwnd, Win32Constants.ShowWindowCmd.SW_RESTORE);
    }

    /** 判断窗口是否最大化 */
    public static boolean isWindowMaximized(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsZoomed(hwnd);
    }

    /** 判断窗口是否最小化 */
    public static boolean isWindowMinimized(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsIconic(hwnd);
    }

    // ==================== 窗口样式 ====================

    /** 禁用 / 恢复最大化按钮 */
    public static void disableMaximize(WinDef.HWND hwnd, boolean disable) {
        if (hwnd == null) {
            return;
        }
        BaseTSD.LONG_PTR style = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_STYLE);
        long currentStyle = style.longValue();
        long newStyle = disable
                ? currentStyle & ~Win32Constants.WindowStyle.WS_MAXIMIZEBOX
                : currentStyle | Win32Constants.WindowStyle.WS_MAXIMIZEBOX;
        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_STYLE,
                new BaseTSD.LONG_PTR(newStyle).toPointer());
        refreshWindowFrame(hwnd);
    }

    /** 禁用 / 恢复窗口大小调整 */
    public static void disableResize(WinDef.HWND hwnd, boolean disable) {
        if (hwnd == null) {
            return;
        }
        BaseTSD.LONG_PTR style = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_STYLE);
        long currentStyle = style.longValue();
        long newStyle;
        if (disable) {
            newStyle = currentStyle & ~Win32Constants.WindowStyle.WS_THICKFRAME;
            newStyle &= ~Win32Constants.WindowStyle.WS_MAXIMIZEBOX;
        } else {
            newStyle = currentStyle | Win32Constants.WindowStyle.WS_THICKFRAME;
            newStyle |= Win32Constants.WindowStyle.WS_MAXIMIZEBOX;
        }
        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_STYLE,
                new BaseTSD.LONG_PTR(newStyle).toPointer());
        refreshWindowFrame(hwnd);
    }

    // ==================== 窗口信息 ====================

    /** 获取窗口标题 */
    public static String getWindowTitle(WinDef.HWND hwnd) {
        if (hwnd == null) return "";
        char[] buffer = new char[512];
        Win32Api.User32Api.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        return new String(buffer).trim();
    }

    /** 设置窗口标题 */
    public static void setWindowTitle(WinDef.HWND hwnd, String title) {
        if (hwnd == null) {
            return;
        }
        Win32Api.User32Api.INSTANCE.SetWindowText(hwnd, title);
    }

    /** 获取窗口矩形（屏幕坐标） */
    public static WinDef.RECT getWindowRect(WinDef.HWND hwnd) {
        WinDef.RECT rect = new WinDef.RECT();
        if (hwnd != null) {
            Win32Api.User32Api.INSTANCE.GetWindowRect(hwnd, rect);
        }
        return rect;
    }

    /** 获取窗口客户区矩形 */
    public static WinDef.RECT getClientRect(WinDef.HWND hwnd) {
        WinDef.RECT rect = new WinDef.RECT();
        if (hwnd != null) {
            Win32Api.User32Api.INSTANCE.GetClientRect(hwnd, rect);
        }
        return rect;
    }

    /** 移动 / 调整窗口位置和大小 */
    public static void moveWindow(WinDef.HWND hwnd, int x, int y, int width, int height, boolean repaint) {
        if (hwnd == null) {
            return;
        }
        Win32Api.User32Api.INSTANCE.MoveWindow(hwnd, x, y, width, height, repaint);
    }

    // ==================== 调试 ====================

    /** 打印当前进程所有窗口信息（仅在 DEBUG 日志级别时输出） */
    public static void printAllWindowsInfo() {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NativeWindowsTools.class);
        if (!logger.isDebugEnabled()) return;
        int currentPid = Win32Api.Kernel32Api.INSTANCE.GetCurrentProcessId();
        final int[] windowCount = {0};

        User32.INSTANCE.EnumWindows((hWnd, lParam) -> {
            IntByReference windowPid = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(hWnd, windowPid);
            if (windowPid.getValue() == currentPid) {
                windowCount[0]++;
                char[] className = new char[256];
                User32.INSTANCE.GetClassName(hWnd, className, className.length);
                char[] windowText = new char[256];
                User32.INSTANCE.GetWindowText(hWnd, windowText, windowText.length);
                long hwndValue = Pointer.nativeValue(hWnd.getPointer());
                logger.debug("窗口 {}:", windowCount[0]);
                logger.debug("  句柄: 0x{}", Long.toHexString(hwndValue).toUpperCase());
                logger.debug("  类名: {}", new String(className).trim());
                logger.debug("  标题: {}", (new String(windowText).trim().isEmpty() ? "(无标题)" : new String(windowText).trim()));
            }
            return true;
        }, null);

        if (windowCount[0] == 0) {
            logger.debug("未找到任何窗口");
        }
    }

    // ==================== 内部工具 ====================

    private static void refreshWindowFrame(WinDef.HWND hwnd) {
        Win32Api.User32Api.INSTANCE.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_NOZORDER
                        | Win32Constants.SetWindowPosFlags.SWP_FRAMECHANGED);
    }
}
