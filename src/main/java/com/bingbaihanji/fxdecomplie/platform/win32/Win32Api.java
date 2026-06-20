package com.bingbaihanji.fxdecomplie.platform.win32;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.W32APIOptions;

import java.util.List;

/**
 * JNA 接口定义,package-private,仅供 {@link NativeWindowsTools} 使用
 */
final class Win32Api {
    private Win32Api() {
    }

    interface DwmApi extends Library {
        DwmApi INSTANCE = Native.load("dwmapi", DwmApi.class);

        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);

        int DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, IntByReference pvAttribute, int cbAttribute);

        int DwmExtendFrameIntoClientArea(WinDef.HWND hwnd, MARGINS pMarInset);

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

    interface User32Api extends User32 {
        User32Api INSTANCE = Native.load("user32", User32Api.class, W32APIOptions.DEFAULT_OPTIONS);

        boolean SetLayeredWindowAttributes(HWND hwnd, int crKey, byte bAlpha, int dwFlags);

        boolean ReleaseCapture();

        LRESULT SendMessage(HWND hwnd, int msg, WPARAM wParam, LPARAM lParam);

        boolean ClientToScreen(HWND hwnd, POINT lpPoint);

        boolean ScreenToClient(HWND hwnd, POINT lpPoint);

        boolean SetWindowPos(HWND hwnd, HWND hWndInsertAfter, int X, int Y, int cx, int cy, int uFlags);

        boolean ShowWindow(HWND hwnd, int nCmdShow);

        boolean IsZoomed(HWND hwnd);

        boolean IsIconic(HWND hwnd);

        boolean SetWindowText(HWND hwnd, String lpString);

        int GetWindowText(HWND hwnd, char[] lpString, int nMaxCount);

        boolean GetWindowRect(HWND hwnd, WinDef.RECT rect);

        boolean GetClientRect(HWND hwnd, WinDef.RECT rect);

        boolean MoveWindow(HWND hwnd, int X, int Y, int nWidth, int nHeight, boolean bRepaint);
    }

    interface Kernel32Api extends Kernel32 {
        Kernel32Api INSTANCE = Native.load("kernel32", Kernel32Api.class, W32APIOptions.DEFAULT_OPTIONS);

        int GetCurrentProcessId();
    }
}
