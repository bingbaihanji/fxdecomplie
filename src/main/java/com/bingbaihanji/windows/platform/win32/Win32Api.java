package com.bingbaihanji.windows.platform.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.List;

/**
 * JNA 本地 API 接口定义,仅供 {@link NativeWindowsTools} 内部使用
 *
 * <h3>职责</h3>
 * <ul>
 *   <li>{@code DwmApi} — DWM 桌面窗口管理器(dwmapi.dll)</li>
 *   <li>{@code User32Api} — 用户32窗口操作(user32.dll)</li>
 *   <li>{@code Kernel32Api} — 内核32(kernel32.dll)</li>
 * </ul>
 *
 * <p>内部接口供 {@link NativeWindowsTools} 和 {@code fx.win32} 包使用</p>
 */
public final class Win32Api {
    private Win32Api() {
    }

    /** DWM API(dwmapi.dll)— 桌面窗口管理器 */
    public interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        /** 设置 DWM 窗口属性(如暗色模式 圆角 背景材质等) */
        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);

        /** 获取 DWM 窗口属性 */
        int DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);

        /** 获取 DWM RECT 类型窗口属性 */
        int DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, WinDef.RECT pvAttribute, int cbAttribute);

        /** 扩展窗口框架到客户区,用于启用 DWM 玻璃/阴影效果 */
        int DwmExtendFrameIntoClientArea(WinDef.HWND hwnd, MARGINS pMarInset);

        /** DWM MARGINS 结构体：定义窗口非客户区扩展边距 */
        class MARGINS extends com.sun.jna.Structure {
            public int cxLeftWidth;
            public int cxRightWidth;
            public int cyTopHeight;
            public int cyBottomHeight;

            @Override
            protected List<String> getFieldOrder() {
                return java.util.Arrays.asList("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight");
            }
        }
    }

    /** User32 API(user32.dll)— 窗口操作扩展 */
    public interface User32Api extends User32 {
        User32Api INSTANCE = Native.load("user32", User32Api.class, W32APIOptions.DEFAULT_OPTIONS);

        /** 设置分层窗口透明度属性 */
        @Override
        boolean SetLayeredWindowAttributes(HWND hwnd, int crKey, byte bAlpha, int dwFlags);

        /** 释放鼠标捕获 */
        boolean ReleaseCapture();

        /** 发送窗口消息 */
        @Override
        LRESULT SendMessage(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam);

        /** 客户区坐标转屏幕坐标 */
        boolean ClientToScreen(HWND hwnd, POINT lpPoint);

        /** 屏幕坐标转客户区坐标 */
        boolean ScreenToClient(HWND hwnd, POINT lpPoint);

        /** 设置窗口位置和 Z 序 */
        @Override
        boolean SetWindowPos(HWND hwnd, HWND hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);

        /** 显示/隐藏窗口 */
        @Override
        boolean ShowWindow(HWND hwnd, int nCmdShow);

        /** 判断窗口是否最大化 */
        boolean IsZoomed(HWND hwnd);

        /** 判断窗口是否最小化 */
        boolean IsIconic(HWND hwnd);

        /** 设置窗口标题文本 */
        boolean SetWindowText(HWND hwnd, String lpString);

        /** 获取窗口标题文本 */
        @Override
        int GetWindowText(HWND hwnd, char[] lpString, int nMaxCount);

        /** 获取窗口标题文本长度 */
        @Override
        int GetWindowTextLength(HWND hwnd);

        /** 获取窗口矩形(屏幕坐标) */
        @Override
        boolean GetWindowRect(HWND hwnd, WinDef.RECT rect);

        /** 获取窗口客户区矩形 */
        @Override
        boolean GetClientRect(HWND hwnd, WinDef.RECT rect);

        /** 移动/调整窗口位置和尺寸 */
        @Override
        boolean MoveWindow(HWND hwnd, int X, int Y, int nWidth, int nHeight, boolean bRepaint);

        // -- 前台/焦点/可见性 --

        /** 将窗口设为前台窗口并激活 */
        @Override
        boolean SetForegroundWindow(HWND hwnd);

        /** 获取当前前台窗口 */
        @Override
        HWND GetForegroundWindow();

        /** 将窗口提升到 Z 序顶部 */
        @Override
        boolean BringWindowToTop(HWND hwnd);

        /** 判断句柄是否对应有效窗口 */
        @Override
        boolean IsWindow(HWND hwnd);

        /** 判断窗口是否可见 */
        @Override
        boolean IsWindowVisible(HWND hwnd);

        /** 判断窗口是否可接收输入 */
        @Override
        boolean IsWindowEnabled(HWND hwnd);

        /** 启用/禁用窗口输入 */
        boolean EnableWindow(HWND hwnd, boolean bEnable);

        // -- 窗口闪烁 --

        /** 闪烁窗口任务栏按钮以引起用户注意 */
        boolean FlashWindowEx(FLASHWINFO pfwi);

        /** 获取窗口位置信息(含正常/最大化/最小化状态和矩形) */
        boolean GetWindowPlacement(HWND hwnd, WINDOWPLACEMENT lpwndpl);

        // -- 窗口位置持久化 --

        /** 设置窗口位置信息 */
        boolean SetWindowPlacement(HWND hwnd, WINDOWPLACEMENT lpwndpl);

        /** 获取窗口 DPI(Windows 10 1607+) */
        int GetDpiForWindow(HWND hwnd);

        /** 获取系统指标(屏幕尺寸等) */
        @Override
        int GetSystemMetrics(int nIndex);

        // -- DPI --

        /** FLASHWINFO 结构体 */
        class FLASHWINFO extends com.sun.jna.Structure {
            public int cbSize;
            public HWND hwnd;
            public int dwFlags;
            /** 闪烁次数,0 表示持续直到窗口获得焦点 */
            public int uCount;
            /** 闪烁间隔(毫秒),0 为默认 */
            public int dwTimeout;

            @Override
            protected List<String> getFieldOrder() {
                return java.util.Arrays.asList("cbSize", "hwnd", "dwFlags", "uCount", "dwTimeout");
            }
        }

        // -- 系统信息 --

        /** WINDOWPLACEMENT 结构体 */
        class WINDOWPLACEMENT extends com.sun.jna.Structure {
            public int length;
            public int flags;
            /** 当前显示状态：SW_SHOWNORMAL / SW_SHOWMAXIMIZED / SW_SHOWMINIMIZED */
            public int showCmd;
            /** 最小化时窗口左上角位置 */
            public POINT ptMinPosition;
            /** 最大化时窗口左上角位置 */
            public POINT ptMaxPosition;
            /** 正常(恢复)状态下的窗口矩形 */
            public WinDef.RECT rcNormalPosition;

            @Override
            protected List<String> getFieldOrder() {
                return java.util.Arrays.asList(
                        "length", "flags", "showCmd",
                        "ptMinPosition", "ptMaxPosition", "rcNormalPosition");
            }
        }
    }

    /** Kernel32 API(kernel32.dll)— 进程信息 */
    public interface Kernel32Api extends Kernel32 {
        Kernel32Api INSTANCE = Native.load("kernel32", Kernel32Api.class, W32APIOptions.DEFAULT_OPTIONS);

        /** 获取当前进程 ID */
        @Override
        int GetCurrentProcessId();
    }
}
