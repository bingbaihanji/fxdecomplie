package com.bingbaihanji.windows.platform.win32;

import com.bingbaihanji.windows.platform.WindowBackdropType;
import com.bingbaihanji.windows.platform.WindowCornerPreference;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;

/**
 * Windows 原生窗口操作工具类
 *
 * <p>封装基于 JNA 的 Win32 / DWM API，提供窗口样式、透明度、状态控制等常用操作</p>
 *
 * @author bingbaihanji
 */
public final class NativeWindowsTools {
    private NativeWindowsTools() {
        throw new AssertionError("utility class");
    }

    /**
     * 根据长整型句柄值重建 Win32 HWND
     *
     * @param headId 原生句柄长整型值(来自 {@code com.bingbaihanji.javafx.jna.NativeWindowHandle#value()})
     * @return 重建的 HWND，值为 0 时返回 null
     */
    public static WinDef.HWND getHWndByEnumeration(Long headId) {
        if (headId == null || headId == 0L) {
            return null;
        }
        return new WinDef.HWND(Pointer.createConstant(headId));
    }

    /**
     * 从长整型指针值创建 HWND
     *
     * @param value 原生指针值
     * @return HWND，值为 0 返回 null
     */
    public static WinDef.HWND hwndFromNativeValue(long value) {
        return value == 0L ? null : new WinDef.HWND(Pointer.createConstant(value));
    }

    /**
     * 获取 HWND 的原始指针值(64 位无符号)
     *
     * @param hwnd 窗口句柄
     * @return 指针值，hwnd 为 null 返回 0
     */
    public static long nativeHandleValue(WinDef.HWND hwnd) {
        return hwnd == null ? 0L : Pointer.nativeValue(hwnd.getPointer());
    }

    // ==================== 句柄工具 ====================

    /**
     * 获取 HWND 的十六进制字符串表示，用于调试和日志输出
     *
     * @param hwnd 窗口句柄
     * @return 如 "0xABCD1234"，hwnd 为空返回空串
     */
    public static String nativeHandleHex(WinDef.HWND hwnd) {
        long value = nativeHandleValue(hwnd);
        return value == 0L ? "" : "0x" + Long.toUnsignedString(value, 16).toUpperCase();
    }

    /**
     * 判断一个 HWND 是否对应系统中存在的有效窗口
     *
     * @param hwnd 窗口句柄
     * @return true=是有效窗口
     */
    public static boolean isWindow(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsWindow(hwnd);
    }

    /**
     * 判断 HWND 是否非零且对应有效窗口(更严格的校验)
     *
     * @param hwnd 窗口句柄
     * @return true=非零且为有效窗口
     */
    public static boolean isValidWindowHandle(WinDef.HWND hwnd) {
        return nativeHandleValue(hwnd) != 0L && isWindow(hwnd);
    }

    /**
     * 设置窗口系统背景样式(Acrylic / Mica)
     *
     * @param hwnd         窗口句柄
     * @param backdropType 背景类型(平台无关枚举，内部转换为 DWM 常量)
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult setSystemStageStyle(WinDef.HWND hwnd, WindowBackdropType backdropType) {
        if (hwnd == null) {
            return skippedNullHandle("setSystemStageStyle");
        }
        if (backdropType == null) {
            return WindowOperationResult.skipped("setSystemStageStyle", "背景类型为空");
        }
        int dwmValue = toDwmBackdropType(backdropType);
        IntByReference backdrop = new IntByReference(dwmValue);
        int result = Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_SYSTEMBACKDROP_TYPE,
                backdrop,
                Integer.BYTES
        );
        return hresult("setSystemStageStyle", result);
    }

    /**
     * 获取窗口系统背景样式
     *
     * @return 当前背景类型(平台无关枚举)，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static WindowBackdropType getSystemStageStyle(WinDef.HWND hwnd) {
        Integer value = getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_SYSTEMBACKDROP_TYPE);
        if (value == null) {
            return null;
        }
        return fromDwmBackdropType(value);
    }

    /**
     * 设置窗口圆角偏好
     *
     * @param hwnd             窗口句柄
     * @param cornerPreference 圆角偏好(平台无关枚举，内部转换为 DWM 常量)
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult setWindowCornerPreference(WinDef.HWND hwnd,
                                                                  WindowCornerPreference cornerPreference) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowCornerPreference");
        }
        if (cornerPreference == null) {
            return WindowOperationResult.skipped("setWindowCornerPreference", "圆角偏好为空");
        }
        int dwmValue = toDwmCornerPreference(cornerPreference);
        IntByReference value = new IntByReference(dwmValue);
        int result = Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_WINDOW_CORNER_PREFERENCE,
                value,
                Integer.BYTES
        );
        return hresult("setWindowCornerPreference", result);
    }

    // ==================== DWM 窗口效果 ====================

    /**
     * 获取窗口圆角偏好
     *
     * @return 当前圆角偏好(平台无关枚举)，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static WindowCornerPreference getWindowCornerPreference(WinDef.HWND hwnd) {
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
            return fromDwmCornerPreference(value.getValue());
        }
        return null;
    }

    /**
     * 设置窗口暗色模式
     *
     * @param hwnd        窗口句柄
     * @param useDarkMode true=启用暗色模式，false=使用亮色模式
     * @implNote 需要 Windows 10 1809+
     */
    public static WindowOperationResult setWindowDarkMode(WinDef.HWND hwnd, boolean useDarkMode) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowDarkMode");
        }
        IntByReference value = new IntByReference(useDarkMode ? 1 : 0);
        int result = Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_USE_IMMERSIVE_DARK_MODE,
                value,
                Integer.BYTES
        );
        return hresult("setWindowDarkMode", result);
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
     * 设置窗口标题栏颜色
     *
     * @param hwnd     窗口句柄
     * @param colorRef 颜色值(COLORREF 格式：0x00BBGGRR)
     * @return 操作结果
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult setWindowCaptionColor(WinDef.HWND hwnd, int colorRef) {
        return setDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_CAPTION_COLOR,
                colorRef, "setWindowCaptionColor");
    }

    /**
     * 获取窗口标题栏颜色
     *
     * @param hwnd 窗口句柄
     * @return 标题栏颜色值(COLORREF 格式：0x00BBGGRR)，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static Integer getWindowCaptionColor(WinDef.HWND hwnd) {
        return getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_CAPTION_COLOR);
    }

    /**
     * 设置窗口标题栏文本颜色
     *
     * @param hwnd     窗口句柄
     * @param colorRef 颜色值(COLORREF 格式：0x00BBGGRR)
     * @return 操作结果
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult setWindowTextColor(WinDef.HWND hwnd, int colorRef) {
        return setDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_TEXT_COLOR,
                colorRef, "setWindowTextColor");
    }

    /**
     * 获取窗口标题栏文本颜色
     *
     * @param hwnd 窗口句柄
     * @return 文本颜色值(COLORREF 格式：0x00BBGGRR)，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static Integer getWindowTextColor(WinDef.HWND hwnd) {
        return getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_TEXT_COLOR);
    }

    /**
     * 设置窗口边框颜色
     *
     * @param hwnd     窗口句柄
     * @param rgbColor 颜色值(COLORREF 格式：0x00BBGGRR)
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult setWindowBorderColor(WinDef.HWND hwnd, int rgbColor) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowBorderColor");
        }
        IntByReference value = new IntByReference(rgbColor);
        int result = Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_BORDER_COLOR,
                value,
                Integer.BYTES
        );
        return hresult("setWindowBorderColor", result);
    }

    /**
     * 获取窗口边框颜色
     *
     * @param hwnd 窗口句柄
     * @return 边框颜色值(COLORREF 格式：0x00BBGGRR)，获取失败返回 null
     * @implNote 需要 Windows 11+
     */
    public static Integer getWindowBorderColor(WinDef.HWND hwnd) {
        return getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_BORDER_COLOR);
    }

    /**
     * 重置窗口边框颜色为系统默认值
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult resetWindowBorderColor(WinDef.HWND hwnd) {
        return setWindowBorderColor(hwnd, Win32Constants.DwmColor.DEFAULT);
    }

    /**
     * 禁用窗口边框颜色(设为透明，不绘制边框)
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     * @implNote 需要 Windows 11+
     */
    public static WindowOperationResult disableWindowBorderColor(WinDef.HWND hwnd) {
        return setWindowBorderColor(hwnd, Win32Constants.DwmColor.NONE);
    }

    /**
     * 获取窗口可见边框厚度
     *
     * @param hwnd 窗口句柄
     * @return 边框厚度(像素)，获取失败返回 null
     * @implNote 需要 Windows 10+
     */
    public static Integer getVisibleFrameBorderThickness(WinDef.HWND hwnd) {
        return getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_VISIBLE_FRAME_BORDER_THICKNESS);
    }

    /**
     * 获取窗口扩展框架边界矩形(包含 DWM 渲染的阴影和边框区域)
     *
     * @param hwnd 窗口句柄
     * @return 扩展框架边界矩形，获取失败返回 null
     * @implNote 需要 Windows Vista+
     */
    public static WinDef.RECT getExtendedFrameBounds(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return null;
        }
        WinDef.RECT rect = new WinDef.RECT();
        int result = Win32Api.DwmApi.INSTANCE.DwmGetWindowAttribute(
                hwnd,
                Win32Constants.DwmAttribute.DWMWA_EXTENDED_FRAME_BOUNDS,
                rect,
                rect.size()
        );
        return result == 0 ? rect : null;
    }

    /**
     * 判断窗口是否启用了 DWM 非客户区渲染
     *
     * @param hwnd 窗口句柄
     * @return true=启用非客户区渲染，false=禁用，获取失败返回 null
     * @implNote 需要 Windows Vista+
     */
    public static Boolean isNonClientRenderingEnabled(WinDef.HWND hwnd) {
        Integer value = getDwmIntAttribute(hwnd, Win32Constants.DwmAttribute.DWMWA_NCRENDERING_ENABLED);
        return value == null ? null : value != 0;
    }

    /**
     * 设置 DWM 窗口整型属性(通用方法)
     *
     * @param hwnd      窗口句柄
     * @param attribute DWM 属性 ID(如 {@code DWMWA_CAPTION_COLOR})
     * @param value     属性值
     * @param operation 操作名称(用于日志和返回结果标识)
     * @return 操作结果
     * @implNote 需要 Windows Vista+
     */
    public static WindowOperationResult setDwmIntAttribute(WinDef.HWND hwnd,
                                                           int attribute,
                                                           int value,
                                                           String operation) {
        if (hwnd == null) {
            return skippedNullHandle(operation);
        }
        IntByReference ref = new IntByReference(value);
        int result = Win32Api.DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, ref, Integer.BYTES);
        return hresult(operation, result);
    }

    /**
     * 获取 DWM 窗口整型属性(通用方法)
     *
     * @param hwnd      窗口句柄
     * @param attribute DWM 属性 ID(如 {@code DWMWA_CAPTION_COLOR})
     * @return 属性值，获取失败返回 null
     * @implNote 需要 Windows Vista+
     */
    public static Integer getDwmIntAttribute(WinDef.HWND hwnd, int attribute) {
        if (hwnd == null) {
            return null;
        }
        IntByReference value = new IntByReference();
        int result = Win32Api.DwmApi.INSTANCE.DwmGetWindowAttribute(hwnd, attribute, value, Integer.BYTES);
        return result == 0 ? value.getValue() : null;
    }

    /**
     * 将 RGB 分量转换为 COLORREF 格式的颜色值
     *
     * @param red   红色分量(0..255)
     * @param green 绿色分量(0..255)
     * @param blue  蓝色分量(0..255)
     * @return COLORREF 格式颜色值(0x00BBGGRR)
     * @throws IllegalArgumentException 任意分量不在 0..255 范围内
     */
    public static int rgbToColorRef(int red, int green, int blue) {
        if ((red | green | blue) < 0 || red > 255 || green > 255 || blue > 255) {
            throw new IllegalArgumentException("RGB 分量必须在 0..255 范围内");
        }
        return (blue << 16) | (green << 8) | red;
    }

    /**
     * 从 COLORREF 颜色值中提取红色分量
     *
     * @param colorRef COLORREF 格式颜色值(0x00BBGGRR)
     * @return 红色分量(0..255)
     */
    public static int colorRefRed(int colorRef) {
        return colorRef & 0xFF;
    }

    /**
     * 从 COLORREF 颜色值中提取绿色分量
     *
     * @param colorRef COLORREF 格式颜色值(0x00BBGGRR)
     * @return 绿色分量(0..255)
     */
    public static int colorRefGreen(int colorRef) {
        return (colorRef >>> 8) & 0xFF;
    }

    /**
     * 从 COLORREF 颜色值中提取蓝色分量
     *
     * @param colorRef COLORREF 格式颜色值(0x00BBGGRR)
     * @return 蓝色分量(0..255)
     */
    public static int colorRefBlue(int colorRef) {
        return (colorRef >>> 16) & 0xFF;
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
    public static WindowOperationResult extendFrameIntoClientArea(WinDef.HWND hwnd,
                                                                  int left,
                                                                  int right,
                                                                  int top,
                                                                  int bottom) {
        if (hwnd == null) {
            return skippedNullHandle("extendFrameIntoClientArea");
        }
        Win32Api.DwmApi.MARGINS margins = new Win32Api.DwmApi.MARGINS();
        margins.cxLeftWidth = left;
        margins.cxRightWidth = right;
        margins.cyTopHeight = top;
        margins.cyBottomHeight = bottom;
        int result = Win32Api.DwmApi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins);
        return hresult("extendFrameIntoClientArea", result);
    }

    /**
     * 扩展窗口框架到整个客户区(-1, -1, -1, -1)，启用 DWM 阴影和玻璃效果
     */
    public static WindowOperationResult extendFrameIntoClientArea(WinDef.HWND hwnd) {
        return extendFrameIntoClientArea(hwnd, -1, -1, -1, -1);
    }

    /**
     * 设置窗口阴影
     *
     * @param hwnd    窗口句柄
     * @param enabled true=启用阴影，false=禁用阴影
     */
    public static WindowOperationResult enableWindowShadow(WinDef.HWND hwnd, boolean enabled) {
        if (hwnd == null) {
            return skippedNullHandle("enableWindowShadow");
        }
        Win32Api.DwmApi.MARGINS margins = new Win32Api.DwmApi.MARGINS();
        margins.cxLeftWidth = 0;
        margins.cxRightWidth = 0;
        margins.cyTopHeight = 0;
        margins.cyBottomHeight = enabled ? 1 : 0;
        int result = Win32Api.DwmApi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, margins);
        return hresult("enableWindowShadow", result);
    }

    /**
     * 设置窗口透明度
     *
     * @param hwnd  窗口句柄
     * @param value 透明度值(0.0 = 完全透明，1.0 = 完全不透明)
     */
    public static WindowOperationResult setWindowAlpha(WinDef.HWND hwnd, float value) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowAlpha");
        }
        if (value <= 0 || value > 1) {
            throw new IllegalArgumentException("Alpha 值必须在 (0, 1] 范围内，实际收到: " + value);
        }
        byte alpha = (byte) (value * 255);

        BaseTSD.LONG_PTR exStyle = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE);
        long style = exStyle.longValue();
        long newStyle = style | Win32Constants.WindowStyleEx.WS_EX_LAYERED;

        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE,
                new BaseTSD.LONG_PTR(newStyle).toPointer());

        boolean ok = Win32Api.User32Api.INSTANCE.SetLayeredWindowAttributes(
                hwnd, 0, alpha, Win32Constants.LayeredWindowAttribute.LWA_ALPHA);
        return boolResult("setWindowAlpha", ok);
    }

    /**
     * 清除窗口分层属性，恢复窗口为完全不透明状态
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     */
    public static WindowOperationResult clearWindowAlpha(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("clearWindowAlpha");
        }
        long style = getExtendedWindowStyle(hwnd);
        if ((style & Win32Constants.WindowStyleEx.WS_EX_LAYERED) == 0) {
            return WindowOperationResult.success("clearWindowAlpha");
        }
        return setExtendedWindowStyle(hwnd, style & ~Win32Constants.WindowStyleEx.WS_EX_LAYERED,
                "clearWindowAlpha");
    }

    /**
     * 设置窗口鼠标穿透模式启用后鼠标点击将穿透窗口到达下层窗口
     *
     * @param hwnd         窗口句柄
     * @param clickThrough true=启用鼠标穿透，false=禁用
     * @return 操作结果
     */
    public static WindowOperationResult setClickThrough(WinDef.HWND hwnd, boolean clickThrough) {
        if (hwnd == null) {
            return skippedNullHandle("setClickThrough");
        }
        long style = getExtendedWindowStyle(hwnd);
        long mask = Win32Constants.WindowStyleEx.WS_EX_LAYERED | Win32Constants.WindowStyleEx.WS_EX_TRANSPARENT;
        long newStyle = clickThrough ? (style | mask) : (style & ~Win32Constants.WindowStyleEx.WS_EX_TRANSPARENT);
        return setExtendedWindowStyle(hwnd, newStyle, "setClickThrough");
    }

    // ==================== 窗口透明度 ====================

    /**
     * 判断窗口是否处于鼠标穿透模式
     *
     * @param hwnd 窗口句柄
     * @return true=鼠标穿透，鼠标点击会穿过窗口
     */
    public static boolean isClickThrough(WinDef.HWND hwnd) {
        long style = getExtendedWindowStyle(hwnd);
        return (style & Win32Constants.WindowStyleEx.WS_EX_TRANSPARENT) != 0;
    }

    /**
     * 设置窗口置顶 / 取消置顶
     */
    public static WindowOperationResult setAlwaysOnTop(WinDef.HWND hwnd, boolean alwaysOnTop) {
        if (hwnd == null) {
            return skippedNullHandle("setAlwaysOnTop");
        }
        WinDef.HWND insertAfter = alwaysOnTop
                ? Win32Constants.HWndInsertAfter.HWND_TOPMOST
                : Win32Constants.HWndInsertAfter.HWND_NOTOPMOST;
        boolean ok = Win32Api.User32Api.INSTANCE.SetWindowPos(
                hwnd, insertAfter, 0, 0, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_NOACTIVATE);
        return boolResult("setAlwaysOnTop", ok);
    }

    /** 最小化窗口 */
    public static WindowOperationResult minimizeWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("minimizeWindow");
        }
        return showWindow(hwnd, Win32Constants.ShowWindowCmd.SW_MINIMIZE, "minimizeWindow");
    }

    /** 最大化窗口 */
    public static WindowOperationResult maximizeWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("maximizeWindow");
        }
        return showWindow(hwnd, Win32Constants.ShowWindowCmd.SW_MAXIMIZE, "maximizeWindow");
    }

    // ==================== 窗口状态 ====================

    /** 还原窗口 */
    public static WindowOperationResult restoreWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("restoreWindow");
        }
        return showWindow(hwnd, Win32Constants.ShowWindowCmd.SW_RESTORE, "restoreWindow");
    }

    /**
     * 显示窗口(使用 SW_SHOW 命令)
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     */
    public static WindowOperationResult showWindow(WinDef.HWND hwnd) {
        return showWindow(hwnd, Win32Constants.ShowWindowCmd.SW_SHOW, "showWindow");
    }

    /**
     * 隐藏窗口(使用 SW_HIDE 命令)
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     */
    public static WindowOperationResult hideWindow(WinDef.HWND hwnd) {
        return showWindow(hwnd, Win32Constants.ShowWindowCmd.SW_HIDE, "hideWindow");
    }

    /**
     * 以指定的显示命令控制窗口状态
     *
     * @param hwnd        窗口句柄
     * @param showCommand 显示命令常量(如 {@code SW_SHOW}、{@code SW_HIDE}、{@code SW_MAXIMIZE} 等)
     * @return 操作结果
     */
    public static WindowOperationResult showWindow(WinDef.HWND hwnd, int showCommand) {
        return showWindow(hwnd, showCommand, "showWindow");
    }

    /**
     * 向窗口发送关闭消息(WM_CLOSE)，请求窗口关闭
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     */
    public static WindowOperationResult closeWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("closeWindow");
        }
        Win32Api.User32Api.INSTANCE.PostMessage(hwnd, Win32Constants.WindowMessage.WM_CLOSE,
                new WinDef.WPARAM(0), new WinDef.LPARAM(0));
        return WindowOperationResult.success("closeWindow");
    }

    /**
     * 向窗口发送系统命令(WM_SYSCOMMAND)，如最大化、最小化、关闭等
     *
     * @param hwnd    窗口句柄
     * @param command 系统命令常量(如 {@code SC_MAXIMIZE}、{@code SC_MINIMIZE} 等)
     * @return 操作结果
     */
    public static WindowOperationResult sendSystemCommand(WinDef.HWND hwnd, int command) {
        if (hwnd == null) {
            return skippedNullHandle("sendSystemCommand");
        }
        Win32Api.User32Api.INSTANCE.SendMessage(hwnd, Win32Constants.WindowMessage.WM_SYSCOMMAND,
                new WinDef.WPARAM(command), new WinDef.LPARAM(0));
        return WindowOperationResult.success("sendSystemCommand");
    }

    /** 判断窗口是否最大化 */
    public static boolean isWindowMaximized(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsZoomed(hwnd);
    }

    /** 判断窗口是否最小化 */
    public static boolean isWindowMinimized(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsIconic(hwnd);
    }

    /**
     * 判断窗口是否处于置顶状态
     *
     * @param hwnd 窗口句柄
     * @return true=窗口置顶，false=非置顶
     */
    public static boolean isAlwaysOnTop(WinDef.HWND hwnd) {
        return (getExtendedWindowStyle(hwnd) & Win32Constants.WindowStyleEx.WS_EX_TOPMOST) != 0;
    }

    /**
     * 判断指定窗口是否为当前前台窗口(拥有输入焦点的顶层窗口)
     *
     * @param hwnd 窗口句柄
     * @return true=是前台窗口
     */
    public static boolean isForegroundWindow(WinDef.HWND hwnd) {
        WinDef.HWND foreground = Win32Api.User32Api.INSTANCE.GetForegroundWindow();
        return nativeHandleValue(hwnd) != 0L && nativeHandleValue(hwnd) == nativeHandleValue(foreground);
    }

    /**
     * 获取当前前台窗口句柄
     *
     * @return 前台窗口 HWND，无前台窗口时返回值可能无效
     */
    public static WinDef.HWND getForegroundWindow() {
        return Win32Api.User32Api.INSTANCE.GetForegroundWindow();
    }

    /**
     * 将窗口强制带到前台(即使当前进程不在前台也可将窗口弹到最前)
     *
     * <p>某些 Windows 版本会限制 {@code SetForegroundWindow}，
     * 此时使用 Alt+Tab 模拟 + SetWindowPos 作为补充手段</p>
     */
    public static WindowOperationResult bringToFront(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("bringToFront");
        }
        Win32Api.User32Api api = Win32Api.User32Api.INSTANCE;
        // 先尝试恢复最小化的窗口
        if (api.IsIconic(hwnd)) {
            api.ShowWindow(hwnd, Win32Constants.ShowWindowCmd.SW_RESTORE);
        }
        // 模拟 Alt 键释放前台锁定(AllowSetForegroundWindow 等效方案)
        api.SendMessage(hwnd, Win32Constants.WindowMessage.WM_SYSCOMMAND,
                new WinDef.WPARAM(Win32Constants.WindowMessage.SC_RESTORE),
                new WinDef.LPARAM(0));
        boolean foreground = api.SetForegroundWindow(hwnd);
        boolean top = api.BringWindowToTop(hwnd);
        boolean positioned = api.SetWindowPos(hwnd, Win32Constants.HWndInsertAfter.HWND_TOP,
                0, 0, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_SHOWWINDOW);
        return foreground || top || positioned
                ? WindowOperationResult.success("bringToFront")
                : WindowOperationResult.failed("bringToFront", Native.getLastError(),
                "SetForegroundWindow、BringWindowToTop 和 SetWindowPos 均失败");
    }

    /** 判断窗口是否可见 */
    public static boolean isWindowVisible(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsWindowVisible(hwnd);
    }

    /**
     * 判断窗口是否有任务栏按钮
     *
     * @param hwnd 窗口句柄
     * @return true=窗口在任务栏中可见
     */
    public static boolean hasTaskbarButton(WinDef.HWND hwnd) {
        long style = getExtendedWindowStyle(hwnd);
        return (style & Win32Constants.WindowStyleEx.WS_EX_APPWINDOW) != 0
                || (style & Win32Constants.WindowStyleEx.WS_EX_TOOLWINDOW) == 0;
    }

    // ==================== 前台/焦点 ====================

    /**
     * 闪烁窗口任务栏图标以引起用户注意
     *
     * @param hwnd      窗口句柄
     * @param count     闪烁次数(0 = 持续闪烁直到窗口获得焦点)
     * @param timeoutMs 闪烁间隔毫秒数(0 = 使用系统默认)
     */
    public static WindowOperationResult flashWindow(WinDef.HWND hwnd, int count, int timeoutMs) {
        if (hwnd == null) {
            return skippedNullHandle("flashWindow");
        }
        Win32Api.User32Api.FLASHWINFO fw = new Win32Api.User32Api.FLASHWINFO();
        fw.cbSize = fw.size();
        fw.hwnd = hwnd;
        fw.dwFlags = count > 0
                ? Win32Constants.FlashWindowFlags.FLASHW_ALL
                : Win32Constants.FlashWindowFlags.FLASHW_TIMER;
        fw.uCount = Math.max(0, count);
        fw.dwTimeout = Math.max(0, timeoutMs);
        fw.write();
        return boolResult("flashWindow", Win32Api.User32Api.INSTANCE.FlashWindowEx(fw));
    }

    // ==================== 窗口可见性 ====================

    /**
     * 闪烁窗口任务栏图标，持续闪烁直到用户点击窗口
     */
    public static WindowOperationResult flashWindowUntilForeground(WinDef.HWND hwnd) {
        return flashWindow(hwnd, 0, 0);
    }

    /** 停止窗口闪烁 */
    public static WindowOperationResult stopFlashWindow(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("stopFlashWindow");
        }
        Win32Api.User32Api.FLASHWINFO fw = new Win32Api.User32Api.FLASHWINFO();
        fw.cbSize = fw.size();
        fw.hwnd = hwnd;
        fw.dwFlags = Win32Constants.FlashWindowFlags.FLASHW_STOP;
        fw.uCount = 0;
        fw.dwTimeout = 0;
        fw.write();
        return boolResult("stopFlashWindow", Win32Api.User32Api.INSTANCE.FlashWindowEx(fw));
    }

    // ==================== 窗口闪烁 ====================

    /**
     * 判断窗口是否可接收鼠标/键盘输入
     */
    public static boolean isWindowEnabled(WinDef.HWND hwnd) {
        return hwnd != null && Win32Api.User32Api.INSTANCE.IsWindowEnabled(hwnd);
    }

    /**
     * 启用或禁用窗口的鼠标/键盘输入
     * 禁用后窗口无法接收点击和按键，适合在长时间任务期间防止用户误操作
     */
    public static WindowOperationResult enableWindow(WinDef.HWND hwnd, boolean enabled) {
        if (hwnd == null) {
            return skippedNullHandle("enableWindow");
        }
        Win32Api.User32Api.INSTANCE.EnableWindow(hwnd, enabled);
        return WindowOperationResult.success("enableWindow");
    }

    /**
     * 获取窗口基本样式标志位
     *
     * @param hwnd 窗口句柄
     * @return 样式标志位组合(如 {@code WS_CAPTION | WS_SYSMENU} 等)，hwnd 为 null 返回 0
     */
    public static long getWindowStyle(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return 0L;
        }
        return Win32Api.User32Api.INSTANCE
                .GetWindowLongPtr(hwnd, Win32Constants.WindowLongIndex.GWL_STYLE)
                .longValue();
    }

    // ==================== 窗口启用/禁用 ====================

    /**
     * 获取窗口扩展样式标志位
     *
     * @param hwnd 窗口句柄
     * @return 扩展样式标志位组合(如 {@code WS_EX_LAYERED | WS_EX_TOPMOST} 等)，hwnd 为 null 返回 0
     */
    public static long getExtendedWindowStyle(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return 0L;
        }
        return Win32Api.User32Api.INSTANCE
                .GetWindowLongPtr(hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE)
                .longValue();
    }

    /**
     * 判断窗口是否具有指定的基本样式标志位(全部命中才算具有)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 样式标志位掩码
     * @return true=窗口具有全部指定样式
     */
    public static boolean hasWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return (getWindowStyle(hwnd) & styleMask) == styleMask;
    }

    // ==================== 窗口样式 ====================

    /**
     * 判断窗口是否具有指定的扩展样式标志位(全部命中才算具有)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 扩展样式标志位掩码
     * @return true=窗口具有全部指定扩展样式
     */
    public static boolean hasExtendedWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return (getExtendedWindowStyle(hwnd) & styleMask) == styleMask;
    }

    /**
     * 设置窗口基本样式标志位(直接替换，非叠加)
     *
     * @param hwnd  窗口句柄
     * @param style 新的样式标志位组合
     * @return 操作结果
     */
    public static WindowOperationResult setWindowStyle(WinDef.HWND hwnd, long style) {
        return setWindowStyle(hwnd, style, "setWindowStyle");
    }

    /**
     * 设置窗口扩展样式标志位(直接替换，非叠加)
     *
     * @param hwnd  窗口句柄
     * @param style 新的扩展样式标志位组合
     * @return 操作结果
     */
    public static WindowOperationResult setExtendedWindowStyle(WinDef.HWND hwnd, long style) {
        return setExtendedWindowStyle(hwnd, style, "setExtendedWindowStyle");
    }

    /**
     * 向窗口添加基本样式标志位(在位或操作后保留已有样式)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 要添加的样式标志位掩码
     * @return 操作结果
     */
    public static WindowOperationResult addWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return setWindowStyle(hwnd, getWindowStyle(hwnd) | styleMask, "addWindowStyle");
    }

    /**
     * 从窗口移除基本样式标志位(在位与非操作后保留其他样式)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 要移除的样式标志位掩码
     * @return 操作结果
     */
    public static WindowOperationResult removeWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return setWindowStyle(hwnd, getWindowStyle(hwnd) & ~styleMask, "removeWindowStyle");
    }

    /**
     * 向窗口添加扩展样式标志位(在位或操作后保留已有样式)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 要添加的扩展样式标志位掩码
     * @return 操作结果
     */
    public static WindowOperationResult addExtendedWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return setExtendedWindowStyle(hwnd, getExtendedWindowStyle(hwnd) | styleMask, "addExtendedWindowStyle");
    }

    /**
     * 从窗口移除扩展样式标志位(在位与非操作后保留其他样式)
     *
     * @param hwnd      窗口句柄
     * @param styleMask 要移除的扩展样式标志位掩码
     * @return 操作结果
     */
    public static WindowOperationResult removeExtendedWindowStyle(WinDef.HWND hwnd, long styleMask) {
        return setExtendedWindowStyle(hwnd, getExtendedWindowStyle(hwnd) & ~styleMask, "removeExtendedWindowStyle");
    }

    /**
     * 控制窗口最小化按钮的可见性
     *
     * @param hwnd    窗口句柄
     * @param visible true=显示最小化按钮，false=隐藏
     * @return 操作结果
     */
    public static WindowOperationResult setMinimizeBoxVisible(WinDef.HWND hwnd, boolean visible) {
        return visible
                ? addWindowStyle(hwnd, Win32Constants.WindowStyle.WS_MINIMIZEBOX)
                : removeWindowStyle(hwnd, Win32Constants.WindowStyle.WS_MINIMIZEBOX);
    }

    /** 禁用 / 恢复最大化按钮 */
    public static WindowOperationResult disableMaximize(WinDef.HWND hwnd, boolean disable) {
        if (hwnd == null) {
            return skippedNullHandle("disableMaximize");
        }
        BaseTSD.LONG_PTR style = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_STYLE);
        long currentStyle = style.longValue();
        long newStyle = disable
                ? currentStyle & ~Win32Constants.WindowStyle.WS_MAXIMIZEBOX
                : currentStyle | Win32Constants.WindowStyle.WS_MAXIMIZEBOX;
        return setWindowStyle(hwnd, newStyle, "disableMaximize");
    }

    /** 禁用 / 恢复窗口大小调整 */
    public static WindowOperationResult disableResize(WinDef.HWND hwnd, boolean disable) {
        if (hwnd == null) {
            return skippedNullHandle("disableResize");
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
        return setWindowStyle(hwnd, newStyle, "disableResize");
    }

    /**
     * 控制窗口是否在 Alt+Tab 任务切换列表中显示
     *
     * <p>隐藏后窗口不在 Alt+Tab 和任务栏中出现，适合浮动工具栏等辅助窗口
     * 本质是添加/移除 {@code WS_EX_TOOLWINDOW} 扩展样式</p>
     *
     * @param hwnd 窗口句柄
     * @param hide true=隐藏，false=恢复显示
     */
    public static WindowOperationResult hideFromAltTab(WinDef.HWND hwnd, boolean hide) {
        if (hwnd == null) {
            return skippedNullHandle("hideFromAltTab");
        }
        BaseTSD.LONG_PTR exStyle = Win32Api.User32Api.INSTANCE.GetWindowLongPtr(
                hwnd, Win32Constants.WindowLongIndex.GWL_EXSTYLE);
        long style = exStyle.longValue();
        long newStyle = hide
                ? style | Win32Constants.WindowStyleEx.WS_EX_TOOLWINDOW
                : style & ~Win32Constants.WindowStyleEx.WS_EX_TOOLWINDOW;
        return setExtendedWindowStyle(hwnd, newStyle, "hideFromAltTab");
    }

    /**
     * 控制窗口是否在任务栏中显示
     *
     * @param hwnd 窗口句柄
     * @param show true=显示在任务栏，false=从任务栏移除
     * @return 操作结果
     */
    public static WindowOperationResult showInTaskbar(WinDef.HWND hwnd, boolean show) {
        if (hwnd == null) {
            return skippedNullHandle("showInTaskbar");
        }
        long style = getExtendedWindowStyle(hwnd);
        long newStyle = show
                ? (style | Win32Constants.WindowStyleEx.WS_EX_APPWINDOW)
                & ~Win32Constants.WindowStyleEx.WS_EX_TOOLWINDOW
                : (style | Win32Constants.WindowStyleEx.WS_EX_TOOLWINDOW)
                & ~Win32Constants.WindowStyleEx.WS_EX_APPWINDOW;
        return setExtendedWindowStyle(hwnd, newStyle, "showInTaskbar");
    }

    /**
     * 获取窗口完整位置信息(含正常/最大化/最小化状态和各状态下的矩形)
     *
     * <p>与 {@link #getWindowRect} 不同，此方法能获取恢复状态下的矩形，
     * 即使窗口当前处于最大化或最小化状态适合用于持久化窗口状态</p>
     *
     * @return 窗口位置信息，获取失败返回 null
     */
    private static Win32Api.User32Api.WINDOWPLACEMENT getNativeWindowPlacement(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return null;
        }
        Win32Api.User32Api.WINDOWPLACEMENT wp = new Win32Api.User32Api.WINDOWPLACEMENT();
        wp.length = wp.size();
        wp.write();
        if (Win32Api.User32Api.INSTANCE.GetWindowPlacement(hwnd, wp)) {
            wp.read();
            return wp;
        }
        return null;
    }

    /**
     * 获取窗口完整位置信息(含正常/最大化/最小化状态和各状态下的矩形)
     *
     * <p>即使窗口当前处于最大化或最小化状态，也能获取恢复状态下的矩形，
     * 适合用于持久化窗口位置</p>
     *
     * @param hwnd 窗口句柄
     * @return 窗口位置信息，获取失败返回 null
     */
    public static WindowPlacement getWindowPlacement(WinDef.HWND hwnd) {
        Win32Api.User32Api.WINDOWPLACEMENT placement = getNativeWindowPlacement(hwnd);
        return placement == null ? null : toWindowPlacement(placement);
    }

    /**
     * 恢复(设置)窗口位置信息
     *
     * @param hwnd      窗口句柄
     * @param placement 之前通过 {@link #getWindowPlacement} 获取的位置信息
     * @return 操作结果
     */
    public static WindowOperationResult setWindowPlacement(WinDef.HWND hwnd,
                                                           WindowPlacement placement) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowPlacement");
        }
        if (placement == null) {
            return WindowOperationResult.skipped("setWindowPlacement", "窗口位置信息为空");
        }
        Win32Api.User32Api.WINDOWPLACEMENT nativePlacement = toNativePlacement(placement);
        nativePlacement.length = nativePlacement.size();
        nativePlacement.write();
        return boolResult("setWindowPlacement",
                Win32Api.User32Api.INSTANCE.SetWindowPlacement(hwnd, nativePlacement));
    }

    // ==================== 窗口位置持久化 ====================

    /**
     * 将窗口恢复到正常(非最大化/最小化)状态下的位置和大小
     * 即使当前最大化，也仅恢复位置矩形而不改变最大化状态
     *
     * @param hwnd 窗口句柄
     * @param x    正常状态左上角 x
     * @param y    正常状态左上角 y
     * @param w    正常状态宽度
     * @param h    正常状态高度
     */
    public static WindowOperationResult setWindowNormalRect(WinDef.HWND hwnd, int x, int y, int w, int h) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowNormalRect");
        }
        Win32Api.User32Api.WINDOWPLACEMENT wp = getNativeWindowPlacement(hwnd);
        if (wp == null) {
            return WindowOperationResult.failed("setWindowNormalRect", Native.getLastError(),
                    "GetWindowPlacement 失败");
        }
        wp.rcNormalPosition.left = x;
        wp.rcNormalPosition.top = y;
        wp.rcNormalPosition.right = x + w;
        wp.rcNormalPosition.bottom = y + h;
        wp.length = wp.size();
        wp.write();
        return boolResult("setWindowNormalRect",
                Win32Api.User32Api.INSTANCE.SetWindowPlacement(hwnd, wp));
    }

    /**
     * 获取窗口所在显示器的 DPI
     *
     * @param hwnd 窗口句柄
     * @return DPI 值(96 = 100%)，非 Windows 10 1607+ 回退到主显示器 DPI
     */
    public static int getWindowDpi(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return Win32Api.User32Api.INSTANCE
                    .GetDpiForWindow(Win32Api.User32Api.INSTANCE.GetDesktopWindow());
        }
        return Win32Api.User32Api.INSTANCE.GetDpiForWindow(hwnd);
    }

    /**
     * 获取窗口缩放比例(相对于 96 DPI)
     *
     * @param hwnd 窗口句柄
     * @return 缩放比例，如 1.25 表示 125%
     */
    public static double getWindowScaleFactor(WinDef.HWND hwnd) {
        return getWindowDpi(hwnd) / 96.0;
    }

    /**
     * 获取系统度量值(如屏幕尺寸、边框宽度等)
     *
     * @param metric 系统度量索引常量(如 {@code SM_CXSCREEN}、{@code SM_CYSCREEN} 等)
     * @return 对应的系统度量值
     */
    public static int getSystemMetric(int metric) {
        return Win32Api.User32Api.INSTANCE.GetSystemMetrics(metric);
    }

    // ==================== DPI ====================

    /**
     * 获取虚拟屏幕(所有显示器拼合区域)的边界矩形
     *
     * @return 虚拟屏幕边界矩形(屏幕坐标)
     */
    public static WindowRect getVirtualScreenBounds() {
        int left = getSystemMetric(Win32Constants.SystemMetric.SM_XVIRTUALSCREEN);
        int top = getSystemMetric(Win32Constants.SystemMetric.SM_YVIRTUALSCREEN);
        int width = getSystemMetric(Win32Constants.SystemMetric.SM_CXVIRTUALSCREEN);
        int height = getSystemMetric(Win32Constants.SystemMetric.SM_CYVIRTUALSCREEN);
        return new WindowRect(left, top, left + width, top + height);
    }

    /**
     * 获取当前连接的显示器数量
     *
     * @return 显示器数量
     */
    public static int getMonitorCount() {
        return getSystemMetric(Win32Constants.SystemMetric.SM_CMONITORS);
    }

    /**
     * 判断当前会话是否为远程桌面会话
     *
     * @return true=远程会话(RDP 等)
     */
    public static boolean isRemoteSession() {
        return getSystemMetric(Win32Constants.SystemMetric.SM_REMOTESESSION) != 0;
    }

    /**
     * 判断当前系统是否支持触摸输入
     *
     * @return true=系统具有触摸功能
     */
    public static boolean isTouchAvailable() {
        return getSystemMetric(Win32Constants.SystemMetric.SM_MAXIMUMTOUCHES) > 0;
    }

    /** 获取窗口标题 */
    public static String getWindowTitle(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return "";
        }
        int length = Win32Api.User32Api.INSTANCE.GetWindowTextLength(hwnd);
        if (length <= 0) {
            return "";
        }
        char[] buffer = new char[length + 1];
        int copied = Win32Api.User32Api.INSTANCE.GetWindowText(hwnd, buffer, buffer.length);
        return copied <= 0 ? "" : new String(buffer, 0, copied);
    }

    /** 设置窗口标题 */
    public static WindowOperationResult setWindowTitle(WinDef.HWND hwnd, String title) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowTitle");
        }
        return boolResult("setWindowTitle",
                Win32Api.User32Api.INSTANCE.SetWindowText(hwnd, title == null ? "" : title));
    }

    /** 获取窗口矩形(屏幕坐标) */
    public static WinDef.RECT getWindowRect(WinDef.HWND hwnd) {
        WinDef.RECT rect = new WinDef.RECT();
        if (hwnd != null) {
            Win32Api.User32Api.INSTANCE.GetWindowRect(hwnd, rect);
        }
        return rect;
    }

    // ==================== 窗口信息 ====================

    /**
     * 获取窗口在屏幕坐标下的边界矩形
     *
     * @param hwnd 窗口句柄
     * @return 窗口边界矩形(屏幕坐标)，hwnd 为 null 返回 (0,0,0,0)
     */
    public static WindowRect getWindowBounds(WinDef.HWND hwnd) {
        return toWindowRect(getWindowRect(hwnd));
    }

    /** 获取窗口客户区矩形 */
    public static WinDef.RECT getClientRect(WinDef.HWND hwnd) {
        WinDef.RECT rect = new WinDef.RECT();
        if (hwnd != null) {
            Win32Api.User32Api.INSTANCE.GetClientRect(hwnd, rect);
        }
        return rect;
    }

    /**
     * 获取窗口客户区在客户坐标下的边界矩形
     *
     * @param hwnd 窗口句柄
     * @return 客户区矩形(客户坐标)，hwnd 为 null 返回 (0,0,0,0)
     */
    public static WindowRect getClientBounds(WinDef.HWND hwnd) {
        return toWindowRect(getClientRect(hwnd));
    }

    /**
     * 获取窗口宽度
     *
     * @param hwnd 窗口句柄
     * @return 窗口宽度(像素)，hwnd 为 null 返回 0
     */
    public static int getWindowWidth(WinDef.HWND hwnd) {
        return getWindowBounds(hwnd).width();
    }

    /**
     * 获取窗口高度
     *
     * @param hwnd 窗口句柄
     * @return 窗口高度(像素)，hwnd 为 null 返回 0
     */
    public static int getWindowHeight(WinDef.HWND hwnd) {
        return getWindowBounds(hwnd).height();
    }

    /** 移动 / 调整窗口位置和大小 */
    public static WindowOperationResult moveWindow(WinDef.HWND hwnd, int x, int y, int width, int height, boolean repaint) {
        if (hwnd == null) {
            return skippedNullHandle("moveWindow");
        }
        return boolResult("moveWindow",
                Win32Api.User32Api.INSTANCE.MoveWindow(hwnd, x, y, width, height, repaint));
    }

    /**
     * 设置窗口的 Z 序位置、屏幕位置和大小(一步完成)
     *
     * @param hwnd        窗口句柄
     * @param insertAfter Z 序插入位置(可为 null 保持当前 Z 序)
     * @param x           左上角 x 坐标
     * @param y           左上角 y 坐标
     * @param width       窗口宽度
     * @param height      窗口高度
     * @param flags       操作标志位组合(如 {@code SWP_NOMOVE | SWP_NOSIZE} 等)
     * @return 操作结果
     */
    public static WindowOperationResult setWindowPos(WinDef.HWND hwnd,
                                                     WinDef.HWND insertAfter,
                                                     int x,
                                                     int y,
                                                     int width,
                                                     int height,
                                                     int flags) {
        if (hwnd == null) {
            return skippedNullHandle("setWindowPos");
        }
        return boolResult("setWindowPos",
                Win32Api.User32Api.INSTANCE.SetWindowPos(hwnd, insertAfter, x, y, width, height, flags));
    }

    /**
     * 移动窗口到指定屏幕坐标(保持大小和 Z 序不变)
     *
     * @param hwnd 窗口句柄
     * @param x    目标左上角 x 坐标
     * @param y    目标左上角 y 坐标
     * @return 操作结果
     */
    public static WindowOperationResult moveWindowTo(WinDef.HWND hwnd, int x, int y) {
        if (hwnd == null) {
            return skippedNullHandle("moveWindowTo");
        }
        return setWindowPos(hwnd, null, x, y, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_NOZORDER);
    }

    /**
     * 调整窗口大小(保持位置和 Z 序不变)
     *
     * @param hwnd   窗口句柄
     * @param width  目标宽度
     * @param height 目标高度
     * @return 操作结果
     */
    public static WindowOperationResult resizeWindow(WinDef.HWND hwnd, int width, int height) {
        if (hwnd == null) {
            return skippedNullHandle("resizeWindow");
        }
        return setWindowPos(hwnd, null, 0, 0, width, height,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOZORDER);
    }

    /**
     * 将窗口居中到虚拟屏幕(所有显示器组合区域)的中心
     *
     * @param hwnd 窗口句柄
     * @return 操作结果
     */
    public static WindowOperationResult centerWindowOnVirtualScreen(WinDef.HWND hwnd) {
        if (hwnd == null) {
            return skippedNullHandle("centerWindowOnVirtualScreen");
        }
        WindowRect screen = getVirtualScreenBounds();
        WindowRect window = getWindowBounds(hwnd);
        int x = screen.left() + (screen.width() - window.width()) / 2;
        int y = screen.top() + (screen.height() - window.height()) / 2;
        return moveWindowTo(hwnd, x, y);
    }

    /**
     * 将窗口客户区坐标转换为屏幕坐标
     *
     * @param hwnd 窗口句柄
     * @param x    客户区 x 坐标
     * @param y    客户区 y 坐标
     * @return 屏幕坐标点，转换失败返回 null
     */
    public static WindowPoint clientToScreen(WinDef.HWND hwnd, int x, int y) {
        if (hwnd == null) {
            return null;
        }
        WinDef.POINT point = new WindowPoint(x, y).toNativePoint();
        if (Win32Api.User32Api.INSTANCE.ClientToScreen(hwnd, point)) {
            return new WindowPoint(point.x, point.y);
        }
        return null;
    }

    /**
     * 将屏幕坐标转换为窗口客户区坐标
     *
     * @param hwnd 窗口句柄
     * @param x    屏幕 x 坐标
     * @param y    屏幕 y 坐标
     * @return 客户区坐标点，转换失败返回 null
     */
    public static WindowPoint screenToClient(WinDef.HWND hwnd, int x, int y) {
        if (hwnd == null) {
            return null;
        }
        WinDef.POINT point = new WindowPoint(x, y).toNativePoint();
        if (Win32Api.User32Api.INSTANCE.ScreenToClient(hwnd, point)) {
            return new WindowPoint(point.x, point.y);
        }
        return null;
    }

    /** 打印当前进程所有窗口信息(仅在 DEBUG 日志级别时输出) */
    public static void printAllWindowsInfo() {
        org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(NativeWindowsTools.class);
        if (!logger.isDebugEnabled()) {
            return;
        }
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
                String title = trimNullTerminated(windowText);
                logger.debug("  标题: {}", title.isBlank() ? "(无标题)" : title);
            }
            return true;
        }, null);

        if (windowCount[0] == 0) {
            logger.debug("未找到任何窗口");
        }
    }

    private static WindowOperationResult showWindow(WinDef.HWND hwnd, int showCommand, String operation) {
        if (hwnd == null) {
            return skippedNullHandle(operation);
        }
        Win32Api.User32Api.INSTANCE.ShowWindow(hwnd, showCommand);
        return WindowOperationResult.success(operation);
    }

    private static WindowOperationResult setWindowStyle(WinDef.HWND hwnd, long style, String operation) {
        if (hwnd == null) {
            return skippedNullHandle(operation);
        }
        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd,
                Win32Constants.WindowLongIndex.GWL_STYLE,
                new BaseTSD.LONG_PTR(style).toPointer());
        return refreshWindowFrame(hwnd, operation);
    }

    // ==================== 调试 ====================

    private static WindowOperationResult setExtendedWindowStyle(WinDef.HWND hwnd, long style, String operation) {
        if (hwnd == null) {
            return skippedNullHandle(operation);
        }
        Win32Api.User32Api.INSTANCE.SetWindowLongPtr(
                hwnd,
                Win32Constants.WindowLongIndex.GWL_EXSTYLE,
                new BaseTSD.LONG_PTR(style).toPointer());
        return refreshWindowFrame(hwnd, operation);
    }

    // ==================== 内部工具 ====================

    private static WindowPlacement toWindowPlacement(Win32Api.User32Api.WINDOWPLACEMENT placement) {
        return new WindowPlacement(
                placement.flags,
                placement.showCmd,
                new WindowPoint(placement.ptMinPosition.x, placement.ptMinPosition.y),
                new WindowPoint(placement.ptMaxPosition.x, placement.ptMaxPosition.y),
                toWindowRect(placement.rcNormalPosition));
    }

    private static Win32Api.User32Api.WINDOWPLACEMENT toNativePlacement(WindowPlacement placement) {
        Win32Api.User32Api.WINDOWPLACEMENT nativePlacement = new Win32Api.User32Api.WINDOWPLACEMENT();
        nativePlacement.length = nativePlacement.size();
        nativePlacement.flags = placement.flags();
        nativePlacement.showCmd = placement.showCommand();
        nativePlacement.ptMinPosition = placement.minPosition() == null
                ? new WindowPoint(0, 0).toNativePoint()
                : placement.minPosition().toNativePoint();
        nativePlacement.ptMaxPosition = placement.maxPosition() == null
                ? new WindowPoint(0, 0).toNativePoint()
                : placement.maxPosition().toNativePoint();
        nativePlacement.rcNormalPosition = placement.normalPosition() == null
                ? new WindowRect(0, 0, 0, 0).toNativeRect()
                : placement.normalPosition().toNativeRect();
        return nativePlacement;
    }

    private static WindowRect toWindowRect(WinDef.RECT rect) {
        if (rect == null) {
            return new WindowRect(0, 0, 0, 0);
        }
        return new WindowRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    private static String trimNullTerminated(char[] chars) {
        int length = 0;
        while (length < chars.length && chars[length] != '\0') {
            length++;
        }
        return new String(chars, 0, length).trim();
    }

    private static WindowOperationResult refreshWindowFrame(WinDef.HWND hwnd, String operation) {
        boolean ok = Win32Api.User32Api.INSTANCE.SetWindowPos(
                hwnd, null, 0, 0, 0, 0,
                Win32Constants.SetWindowPosFlags.SWP_NOMOVE
                        | Win32Constants.SetWindowPosFlags.SWP_NOSIZE
                        | Win32Constants.SetWindowPosFlags.SWP_NOZORDER
                        | Win32Constants.SetWindowPosFlags.SWP_FRAMECHANGED);
        return boolResult(operation, ok);
    }

    private static WindowOperationResult skippedNullHandle(String operation) {
        return WindowOperationResult.skipped(operation, "窗口句柄为空");
    }

    private static WindowOperationResult hresult(String operation, int hresult) {
        return hresult == 0
                ? WindowOperationResult.success(operation)
                : WindowOperationResult.failed(operation, hresult,
                "原生调用失败，HRESULT 0x" + Integer.toHexString(hresult).toUpperCase());
    }

    private static WindowOperationResult boolResult(String operation, boolean success) {
        return success
                ? WindowOperationResult.success(operation)
                : WindowOperationResult.failed(operation, 0, "原生调用返回 false");
    }

    /**
     * 将平台无关的 {@link WindowBackdropType} 转换为 DWM 系统背景类型整型常量
     */
    private static int toDwmBackdropType(WindowBackdropType type) {
        return switch (type) {
            case AUTO -> Win32Constants.DwmBackdropType.AUTO;
            case NONE -> Win32Constants.DwmBackdropType.NONE;
            case MAIN_WINDOW -> Win32Constants.DwmBackdropType.MAINWINDOW;
            case TRANSIENT_WINDOW -> Win32Constants.DwmBackdropType.TRANSIENTWINDOW;
            case TABBED_WINDOW -> Win32Constants.DwmBackdropType.TABBEDWINDOW;
        };
    }

    /**
     * 将 DWM 系统背景类型整型常量转换为平台无关的 {@link WindowBackdropType}
     * 无法识别的值返回 null
     */
    private static WindowBackdropType fromDwmBackdropType(int dwmValue) {
        return switch (dwmValue) {
            case Win32Constants.DwmBackdropType.AUTO -> WindowBackdropType.AUTO;
            case Win32Constants.DwmBackdropType.NONE -> WindowBackdropType.NONE;
            case Win32Constants.DwmBackdropType.MAINWINDOW -> WindowBackdropType.MAIN_WINDOW;
            case Win32Constants.DwmBackdropType.TRANSIENTWINDOW -> WindowBackdropType.TRANSIENT_WINDOW;
            case Win32Constants.DwmBackdropType.TABBEDWINDOW -> WindowBackdropType.TABBED_WINDOW;
            default -> null;
        };
    }

    /**
     * 将平台无关的 {@link WindowCornerPreference} 转换为 DWM 圆角偏好整型常量
     */
    private static int toDwmCornerPreference(WindowCornerPreference preference) {
        return switch (preference) {
            case DEFAULT -> Win32Constants.DwmCornerPreference.DEFAULT;
            case DO_NOT_ROUND -> Win32Constants.DwmCornerPreference.DO_NOT_ROUND;
            case ROUND -> Win32Constants.DwmCornerPreference.ROUND;
            case ROUND_SMALL -> Win32Constants.DwmCornerPreference.ROUND_SMALL;
        };
    }

    // ==================== 枚举转换(平台无关枚举 <-> DWM 整型常量) ====================

    /**
     * 将 DWM 圆角偏好整型常量转换为平台无关的 {@link WindowCornerPreference}
     * 无法识别的值返回 null
     */
    private static WindowCornerPreference fromDwmCornerPreference(int dwmValue) {
        return switch (dwmValue) {
            case Win32Constants.DwmCornerPreference.DEFAULT -> WindowCornerPreference.DEFAULT;
            case Win32Constants.DwmCornerPreference.DO_NOT_ROUND -> WindowCornerPreference.DO_NOT_ROUND;
            case Win32Constants.DwmCornerPreference.ROUND -> WindowCornerPreference.ROUND;
            case Win32Constants.DwmCornerPreference.ROUND_SMALL -> WindowCornerPreference.ROUND_SMALL;
            default -> null;
        };
    }

    /** 窗口坐标点，对应 Win32 POINT 结构体 */
    public record WindowPoint(int x, int y) {
        private WinDef.POINT toNativePoint() {
            WinDef.POINT point = new WinDef.POINT();
            point.x = x;
            point.y = y;
            return point;
        }
    }

    /** 窗口矩形信息，对应 Win32 RECT 结构体 */
    public record WindowRect(int left, int top, int right, int bottom) {
        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }

        private WinDef.RECT toNativeRect() {
            WinDef.RECT rect = new WinDef.RECT();
            rect.left = left;
            rect.top = top;
            rect.right = right;
            rect.bottom = bottom;
            return rect;
        }
    }

    /** 窗口位置信息，对应 Win32 WINDOWPLACEMENT 结构体，包含正常/最大化/最小化状态下的位置和矩形 */
    public record WindowPlacement(
            int flags,
            int showCommand,
            WindowPoint minPosition,
            WindowPoint maxPosition,
            WindowRect normalPosition
    ) {
    }
}
