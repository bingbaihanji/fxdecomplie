package com.bingbaihanji.fxdecomplie.platform.win32;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef;

/**
 * Win32 API 常量统一管理类
 */
public final class Win32Constants {
    private Win32Constants() {
    }

    public static final class WindowStyle {
        public static final long WS_OVERLAPPED = 0x00000000L;
        public static final long WS_POPUP = 0x80000000L;
        public static final long WS_CHILD = 0x40000000L;
        public static final long WS_MINIMIZE = 0x20000000L;
        public static final long WS_VISIBLE = 0x10000000L;
        public static final long WS_DISABLED = 0x08000000L;
        public static final long WS_CLIPSIBLINGS = 0x04000000L;
        public static final long WS_CLIPCHILDREN = 0x02000000L;
        public static final long WS_MAXIMIZE = 0x01000000L;
        public static final long WS_CAPTION = 0x00C00000L;
        public static final long WS_BORDER = 0x00800000L;
        public static final long WS_DLGFRAME = 0x00400000L;
        public static final long WS_VSCROLL = 0x00200000L;
        public static final long WS_HSCROLL = 0x00100000L;
        public static final long WS_SYSMENU = 0x00080000L;
        public static final long WS_THICKFRAME = 0x00040000L;
        public static final long WS_GROUP = 0x00020000L;
        public static final long WS_TABSTOP = 0x00010000L;
        public static final long WS_MINIMIZEBOX = 0x00020000L;
        public static final long WS_MAXIMIZEBOX = 0x00010000L;
        public static final long WS_OVERLAPPEDWINDOW = WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU
                | WS_THICKFRAME | WS_MINIMIZEBOX | WS_MAXIMIZEBOX;
        public static final long WS_TILEDWINDOW = WS_OVERLAPPEDWINDOW;
        public static final long WS_POPUPWINDOW = WS_POPUP | WS_BORDER | WS_SYSMENU;
        public static final long WS_SIZEBOX = WS_THICKFRAME;
        public static final long WS_TILED = WS_OVERLAPPED;
        private WindowStyle() {
        }
    }

    public static final class WindowStyleEx {
        public static final int WS_EX_DLGMODALFRAME = 0x00000001;
        public static final int WS_EX_NOPARENTNOTIFY = 0x00000004;
        public static final int WS_EX_TOPMOST = 0x00000008;
        public static final int WS_EX_ACCEPTFILES = 0x00000010;
        public static final int WS_EX_TRANSPARENT = 0x00000020;
        public static final int WS_EX_MDICHILD = 0x00000040;
        public static final int WS_EX_TOOLWINDOW = 0x00000080;
        public static final int WS_EX_WINDOWEDGE = 0x00000100;
        public static final int WS_EX_CLIENTEDGE = 0x00000200;
        public static final int WS_EX_CONTEXTHELP = 0x00000400;
        public static final int WS_EX_RIGHT = 0x00001000;
        public static final int WS_EX_LEFT = 0x00000000;
        public static final int WS_EX_RTLREADING = 0x00002000;
        public static final int WS_EX_LTRREADING = 0x00000000;
        public static final int WS_EX_LEFTSCROLLBAR = 0x00004000;
        public static final int WS_EX_RIGHTSCROLLBAR = 0x00000000;
        public static final int WS_EX_CONTROLPARENT = 0x00010000;
        public static final int WS_EX_STATICEDGE = 0x00020000;
        public static final int WS_EX_APPWINDOW = 0x00040000;
        public static final int WS_EX_LAYERED = 0x00080000;
        public static final int WS_EX_NOINHERITLAYOUT = 0x00100000;
        public static final int WS_EX_NOREDIRECTIONBITMAP = 0x00200000;
        public static final int WS_EX_LAYOUTRTL = 0x00400000;
        public static final int WS_EX_COMPOSITED = 0x02000000;
        public static final int WS_EX_NOACTIVATE = 0x08000000;
        public static final int WS_EX_OVERLAPPEDWINDOW = WS_EX_WINDOWEDGE | WS_EX_CLIENTEDGE;
        public static final int WS_EX_PALETTEWINDOW = WS_EX_WINDOWEDGE | WS_EX_TOOLWINDOW | WS_EX_TOPMOST;
        private WindowStyleEx() {
        }
    }

    public static final class WindowLongIndex {
        public static final int GWL_WNDPROC = -4;
        public static final int GWL_HINSTANCE = -6;
        public static final int GWL_HWNDPARENT = -8;
        public static final int GWL_STYLE = -16;
        public static final int GWL_EXSTYLE = -20;
        public static final int GWL_USERDATA = -21;
        public static final int GWL_ID = -12;
        private WindowLongIndex() {
        }
    }

    public static final class SetWindowPosFlags {
        public static final int SWP_NOSIZE = 0x0001;
        public static final int SWP_NOMOVE = 0x0002;
        public static final int SWP_NOZORDER = 0x0004;
        public static final int SWP_NOREDRAW = 0x0008;
        public static final int SWP_NOACTIVATE = 0x0010;
        public static final int SWP_FRAMECHANGED = 0x0020;
        public static final int SWP_SHOWWINDOW = 0x0040;
        public static final int SWP_HIDEWINDOW = 0x0080;
        public static final int SWP_NOCOPYBITS = 0x0100;
        public static final int SWP_NOOWNERZORDER = 0x0200;
        public static final int SWP_NOSENDCHANGING = 0x0400;
        public static final int SWP_DRAWFRAME = SWP_FRAMECHANGED;
        public static final int SWP_NOREPOSITION = SWP_NOOWNERZORDER;
        public static final int SWP_DEFERERASE = 0x2000;
        public static final int SWP_ASYNCWINDOWPOS = 0x4000;
        private SetWindowPosFlags() {
        }
    }

    public static final class HWndInsertAfter {
        public static final WinDef.HWND HWND_TOP = new WinDef.HWND(new Pointer(0));
        public static final WinDef.HWND HWND_BOTTOM = new WinDef.HWND(new Pointer(1));
        public static final WinDef.HWND HWND_TOPMOST = new WinDef.HWND(new Pointer(-1));
        public static final WinDef.HWND HWND_NOTOPMOST = new WinDef.HWND(new Pointer(-2));
        private HWndInsertAfter() {
        }
    }

    public static final class ShowWindowCmd {
        public static final int SW_HIDE = 0;
        public static final int SW_SHOWNORMAL = 1;
        public static final int SW_NORMAL = 1;
        public static final int SW_SHOWMINIMIZED = 2;
        public static final int SW_SHOWMAXIMIZED = 3;
        public static final int SW_MAXIMIZE = 3;
        public static final int SW_SHOWNOACTIVATE = 4;
        public static final int SW_SHOW = 5;
        public static final int SW_MINIMIZE = 6;
        public static final int SW_SHOWMINNOACTIVE = 7;
        public static final int SW_SHOWNA = 8;
        public static final int SW_RESTORE = 9;
        public static final int SW_SHOWDEFAULT = 10;
        public static final int SW_FORCEMINIMIZE = 11;
        private ShowWindowCmd() {
        }
    }

    public static final class WindowMessage {
        public static final int WM_NULL = 0x0000;
        public static final int WM_CREATE = 0x0001;
        public static final int WM_DESTROY = 0x0002;
        public static final int WM_MOVE = 0x0003;
        public static final int WM_SIZE = 0x0005;
        public static final int WM_ACTIVATE = 0x0006;
        public static final int WM_SETFOCUS = 0x0007;
        public static final int WM_KILLFOCUS = 0x0008;
        public static final int WM_ENABLE = 0x000A;
        public static final int WM_SETREDRAW = 0x000B;
        public static final int WM_SETTEXT = 0x000C;
        public static final int WM_GETTEXT = 0x000D;
        public static final int WM_GETTEXTLENGTH = 0x000E;
        public static final int WM_PAINT = 0x000F;
        public static final int WM_CLOSE = 0x0010;
        public static final int WM_QUIT = 0x0012;
        public static final int WM_ERASEBKGND = 0x0014;
        public static final int WM_SYSCOLORCHANGE = 0x0015;
        public static final int WM_SHOWWINDOW = 0x0018;
        public static final int WM_ACTIVATEAPP = 0x001C;
        public static final int WM_SETCURSOR = 0x0020;
        public static final int WM_MOUSEACTIVATE = 0x0021;
        public static final int WM_GETMINMAXINFO = 0x0024;
        public static final int WM_WINDOWPOSCHANGING = 0x0046;
        public static final int WM_WINDOWPOSCHANGED = 0x0047;
        public static final int WM_NCCREATE = 0x0081;
        public static final int WM_NCDESTROY = 0x0082;
        public static final int WM_NCCALCSIZE = 0x0083;
        public static final int WM_NCHITTEST = 0x0084;
        public static final int WM_NCPAINT = 0x0085;
        public static final int WM_NCACTIVATE = 0x0086;
        public static final int WM_NCMOUSEMOVE = 0x00A0;
        public static final int WM_NCLBUTTONDOWN = 0x00A1;
        public static final int WM_NCLBUTTONUP = 0x00A2;
        public static final int WM_NCLBUTTONDBLCLK = 0x00A3;
        public static final int WM_NCRBUTTONDOWN = 0x00A4;
        public static final int WM_NCRBUTTONUP = 0x00A5;
        public static final int WM_NCRBUTTONDBLCLK = 0x00A6;
        public static final int WM_NCMBUTTONDOWN = 0x00A7;
        public static final int WM_NCMBUTTONUP = 0x00A8;
        public static final int WM_NCMBUTTONDBLCLK = 0x00A9;
        public static final int WM_KEYDOWN = 0x0100;
        public static final int WM_KEYUP = 0x0101;
        public static final int WM_CHAR = 0x0102;
        public static final int WM_SYSKEYDOWN = 0x0104;
        public static final int WM_SYSKEYUP = 0x0105;
        public static final int WM_SYSCHAR = 0x0106;
        public static final int WM_MOUSEMOVE = 0x0200;
        public static final int WM_LBUTTONDOWN = 0x0201;
        public static final int WM_LBUTTONUP = 0x0202;
        public static final int WM_LBUTTONDBLCLK = 0x0203;
        public static final int WM_RBUTTONDOWN = 0x0204;
        public static final int WM_RBUTTONUP = 0x0205;
        public static final int WM_RBUTTONDBLCLK = 0x0206;
        public static final int WM_MBUTTONDOWN = 0x0207;
        public static final int WM_MBUTTONUP = 0x0208;
        public static final int WM_MBUTTONDBLCLK = 0x0209;
        public static final int WM_MOUSEWHEEL = 0x020A;
        public static final int WM_MOUSEHWHEEL = 0x020E;
        public static final int WM_DWMCOMPOSITIONCHANGED = 0x031E;
        private WindowMessage() {
        }
    }

    public static final class HitTestCode {
        public static final int HTERROR = -2;
        public static final int HTTRANSPARENT = -1;
        public static final int HTNOWHERE = 0;
        public static final int HTCLIENT = 1;
        public static final int HTCAPTION = 2;
        public static final int HTSYSMENU = 3;
        public static final int HTGROWBOX = 4;
        public static final int HTSIZE = HTGROWBOX;
        public static final int HTMENU = 5;
        public static final int HTHSCROLL = 6;
        public static final int HTVSCROLL = 7;
        public static final int HTMINBUTTON = 8;
        public static final int HTMAXBUTTON = 9;
        public static final int HTLEFT = 10;
        public static final int HTRIGHT = 11;
        public static final int HTTOP = 12;
        public static final int HTTOPLEFT = 13;
        public static final int HTTOPRIGHT = 14;
        public static final int HTBOTTOM = 15;
        public static final int HTBOTTOMLEFT = 16;
        public static final int HTBOTTOMRIGHT = 17;
        public static final int HTBORDER = 18;
        public static final int HTREDUCE = HTMINBUTTON;
        public static final int HTZOOM = HTMAXBUTTON;
        public static final int HTSIZEFIRST = HTLEFT;
        public static final int HTSIZELAST = HTBOTTOMRIGHT;
        public static final int HTOBJECT = 19;
        public static final int HTCLOSE = 20;
        public static final int HTHELP = 21;
        private HitTestCode() {
        }
    }

    public static final class LayeredWindowAttribute {
        public static final int LWA_COLORKEY = 0x00000001;
        public static final int LWA_ALPHA = 0x00000002;
        private LayeredWindowAttribute() {
        }
    }

    public static final class DwmAttribute {
        public static final int DWMWA_NCRENDERING_ENABLED = 1;
        public static final int DWMWA_NCRENDERING_POLICY = 2;
        public static final int DWMWA_TRANSITIONS_FORCEDISABLED = 3;
        public static final int DWMWA_ALLOW_NCPAINT = 4;
        public static final int DWMWA_CAPTION_BUTTON_BOUNDS = 5;
        public static final int DWMWA_NONCLIENT_RTL_LAYOUT = 6;
        public static final int DWMWA_FORCE_ICONIC_REPRESENTATION = 7;
        public static final int DWMWA_FLIP3D_POLICY = 8;
        public static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;
        public static final int DWMWA_HAS_ICONIC_BITMAP = 10;
        public static final int DWMWA_DISALLOW_PEEK = 11;
        public static final int DWMWA_EXCLUDED_FROM_PEEK = 12;
        public static final int DWMWA_CLOAK = 13;
        public static final int DWMWA_CLOAKED = 14;
        public static final int DWMWA_FREEZE_REPRESENTATION = 15;
        public static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        public static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        public static final int DWMWA_BORDER_COLOR = 34;
        public static final int DWMWA_CAPTION_COLOR = 35;
        public static final int DWMWA_TEXT_COLOR = 36;
        public static final int DWMWA_VISIBLE_FRAME_BORDER_THICKNESS = 37;
        public static final int DWMWA_SYSTEMBACKDROP_TYPE = 38;
        private DwmAttribute() {
        }
    }
}
