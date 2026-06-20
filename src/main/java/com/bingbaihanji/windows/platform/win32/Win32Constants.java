package com.bingbaihanji.windows.platform.win32;

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
        /** 系统命令消息(用于最大化/最小化/还原等) */
        public static final int WM_SYSCOMMAND = 0x0112;
        /** 系统命令：调整窗口大小 */
        public static final int SC_SIZE = 0xF000;
        /** 系统命令：移动窗口 */
        public static final int SC_MOVE = 0xF010;
        /** 系统命令：最小化窗口 */
        public static final int SC_MINIMIZE = 0xF020;
        /** 系统命令：最大化窗口 */
        public static final int SC_MAXIMIZE = 0xF030;
        /** 系统命令：关闭窗口 */
        public static final int SC_CLOSE = 0xF060;
        /** 系统命令：还原窗口 */
        public static final int SC_RESTORE = 0xF120;
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

    /** 窗口闪烁标志 */
    public static final class FlashWindowFlags {
        /** 停止闪烁 */
        public static final int FLASHW_STOP = 0x0000;
        /** 闪烁标题栏 */
        public static final int FLASHW_CAPTION = 0x0001;
        /** 闪烁任务栏按钮 */
        public static final int FLASHW_TRAY = 0x0002;
        /** 同时闪烁标题栏和任务栏按钮 */
        public static final int FLASHW_ALL = FLASHW_CAPTION | FLASHW_TRAY;
        /** 持续闪烁直到窗口获得焦点 */
        public static final int FLASHW_TIMER = 0x0004;
        /** 持续闪烁直到窗口获得焦点(前台不打断) */
        public static final int FLASHW_TIMERNOFG = 0x000C;
        private FlashWindowFlags() {
        }
    }

    public static final class WindowPlacementFlag {
        public static final int WPF_SETMINPOSITION = 0x0001;
        public static final int WPF_RESTORETOMAXIMIZED = 0x0002;
        public static final int WPF_ASYNCWINDOWPLACEMENT = 0x0004;
        private WindowPlacementFlag() {
        }
    }

    /** 系统指标常量(GetSystemMetrics 参数) */
    public static final class SystemMetric {
        public static final int SM_CXSCREEN = 0;
        public static final int SM_CYSCREEN = 1;
        public static final int SM_CXVSCROLL = 2;
        public static final int SM_CYHSCROLL = 3;
        public static final int SM_CYCAPTION = 4;
        public static final int SM_CXBORDER = 5;
        public static final int SM_CYBORDER = 6;
        public static final int SM_CXDLGFRAME = 7;
        public static final int SM_CYDLGFRAME = 8;
        public static final int SM_CXICON = 11;
        public static final int SM_CYICON = 12;
        public static final int SM_CXCURSOR = 13;
        public static final int SM_CYCURSOR = 14;
        public static final int SM_CYMENU = 15;
        public static final int SM_CXFULLSCREEN = 16;
        public static final int SM_CYFULLSCREEN = 17;
        public static final int SM_CYKANJIWINDOW = 18;
        public static final int SM_MOUSEPRESENT = 19;
        public static final int SM_CYVSCROLL = 20;
        public static final int SM_CXHSCROLL = 21;
        public static final int SM_DEBUG = 22;
        public static final int SM_SWAPBUTTON = 23;
        public static final int SM_CXMIN = 28;
        public static final int SM_CYMIN = 29;
        public static final int SM_CXSIZE = 30;
        public static final int SM_CYSIZE = 31;
        public static final int SM_CXFRAME = 32;
        public static final int SM_CYFRAME = 33;
        public static final int SM_CXMINTRACK = 34;
        public static final int SM_CYMINTRACK = 35;
        public static final int SM_CXDOUBLECLK = 36;
        public static final int SM_CYDOUBLECLK = 37;
        public static final int SM_CXICONSPACING = 38;
        public static final int SM_CYICONSPACING = 39;
        public static final int SM_MENUDROPALIGNMENT = 40;
        public static final int SM_PENWINDOWS = 41;
        public static final int SM_DBCSENABLED = 42;
        public static final int SM_CMOUSEBUTTONS = 43;
        public static final int SM_SECURE = 44;
        public static final int SM_CXEDGE = 45;
        public static final int SM_CYEDGE = 46;
        public static final int SM_CXMINSPACING = 47;
        public static final int SM_CYMINSPACING = 48;
        public static final int SM_CXSMICON = 49;
        public static final int SM_CYSMICON = 50;
        public static final int SM_CYSMCAPTION = 51;
        public static final int SM_CXSMSIZE = 52;
        public static final int SM_CYSMSIZE = 53;
        public static final int SM_CXMENUSIZE = 54;
        public static final int SM_CYMENUSIZE = 55;
        public static final int SM_ARRANGE = 56;
        public static final int SM_CXMINIMIZED = 57;
        public static final int SM_CYMINIMIZED = 58;
        public static final int SM_CXMAXTRACK = 59;
        public static final int SM_CYMAXTRACK = 60;
        public static final int SM_CXMAXIMIZED = 61;
        public static final int SM_CYMAXIMIZED = 62;
        public static final int SM_NETWORK = 63;
        public static final int SM_CLEANBOOT = 67;
        public static final int SM_CXDRAG = 68;
        public static final int SM_CYDRAG = 69;
        public static final int SM_SHOWSOUNDS = 70;
        public static final int SM_CXMENUCHECK = 71;
        public static final int SM_CYMENUCHECK = 72;
        public static final int SM_SLOWMACHINE = 73;
        public static final int SM_MIDEASTENABLED = 74;
        public static final int SM_MOUSEWHEELPRESENT = 75;
        /** 虚拟屏幕左上角 X 坐标 */
        public static final int SM_XVIRTUALSCREEN = 76;
        /** 虚拟屏幕左上角 Y 坐标 */
        public static final int SM_YVIRTUALSCREEN = 77;
        /** 虚拟屏幕宽度 */
        public static final int SM_CXVIRTUALSCREEN = 78;
        /** 虚拟屏幕高度 */
        public static final int SM_CYVIRTUALSCREEN = 79;
        /** 显示器数量 */
        public static final int SM_CMONITORS = 80;
        public static final int SM_SAMEDISPLAYFORMAT = 81;
        public static final int SM_IMMENABLED = 82;
        public static final int SM_CXFOCUSBORDER = 83;
        public static final int SM_CYFOCUSBORDER = 84;
        public static final int SM_TABLETPC = 86;
        public static final int SM_MEDIACENTER = 87;
        public static final int SM_STARTER = 88;
        public static final int SM_SERVERR2 = 89;
        public static final int SM_MOUSEHORIZONTALWHEELPRESENT = 91;
        public static final int SM_CXPADDEDBORDER = 92;
        public static final int SM_DIGITIZER = 94;
        /** 最大触摸点数，0 表示无触摸设备 */
        public static final int SM_MAXIMUMTOUCHES = 95;
        /** 是否远程桌面会话，非零表示远程会话 */
        public static final int SM_REMOTESESSION = 0x1000;
        public static final int SM_SHUTTINGDOWN = 0x2000;
        public static final int SM_REMOTECONTROL = 0x2001;
        public static final int SM_CONVERTIBLESLATEMODE = 0x2003;
        public static final int SM_SYSTEMDOCKED = 0x2004;
        private SystemMetric() {
        }
    }

    /**
     * DWM (Desktop Window Manager) 窗口属性常量
     *
     * <ul>
     *   <li>DWMWA_USE_IMMERSIVE_DARK_MODE — Windows 10 1809+</li>
     *   <li>DWMWA_WINDOW_CORNER_PREFERENCE — Windows 11 22000+</li>
     *   <li>DWMWA_SYSTEMBACKDROP_TYPE — Windows 11 22621+</li>
     *   <li>DWMWA_BORDER_COLOR / CAPTION_COLOR / TEXT_COLOR — Windows 11 22000+</li>
     * </ul>
     */
    public static final class DwmAttribute {
        /** 启用/禁用非客户区渲染 */
        public static final int DWMWA_NCRENDERING_ENABLED = 1;
        /** 非客户区渲染策略 */
        public static final int DWMWA_NCRENDERING_POLICY = 2;
        /** 强制禁用过渡动画 */
        public static final int DWMWA_TRANSITIONS_FORCEDISABLED = 3;
        /** 允许在非客户区绘制 */
        public static final int DWMWA_ALLOW_NCPAINT = 4;
        /** 标题按钮边界 */
        public static final int DWMWA_CAPTION_BUTTON_BOUNDS = 5;
        /** 非客户区 RTL 布局 */
        public static final int DWMWA_NONCLIENT_RTL_LAYOUT = 6;
        /** 强制图标表示 */
        public static final int DWMWA_FORCE_ICONIC_REPRESENTATION = 7;
        /** Flip3D 策略 */
        public static final int DWMWA_FLIP3D_POLICY = 8;
        /** 扩展框架边界 */
        public static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;
        /** 是否有图标位图 */
        public static final int DWMWA_HAS_ICONIC_BITMAP = 10;
        /** 禁止 Peek 预览 */
        public static final int DWMWA_DISALLOW_PEEK = 11;
        /** 从 Peek 预览中排除 */
        public static final int DWMWA_EXCLUDED_FROM_PEEK = 12;
        /** 隐藏窗口 */
        public static final int DWMWA_CLOAK = 13;
        /** 窗口是否被隐藏 */
        public static final int DWMWA_CLOAKED = 14;
        /** 冻结窗口表示 */
        public static final int DWMWA_FREEZE_REPRESENTATION = 15;
        /**
         * 沉浸式暗色模式(Windows 10 1809+)
         * 设置标题栏为暗色风格
         */
        public static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
        /**
         * 窗口圆角偏好(Windows 11 22000+)
         * 取值见 {@link DwmCornerPreference}
         */
        public static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
        /** 窗口边框颜色(Windows 11 22000+)，COLORREF 格式 */
        public static final int DWMWA_BORDER_COLOR = 34;
        /** 标题栏颜色(Windows 11 22000+)，COLORREF 格式 */
        public static final int DWMWA_CAPTION_COLOR = 35;
        /** 标题栏文字颜色(Windows 11 22000+)，COLORREF 格式 */
        public static final int DWMWA_TEXT_COLOR = 36;
        /** 可见框架边框宽度(Windows 11 22000+) */
        public static final int DWMWA_VISIBLE_FRAME_BORDER_THICKNESS = 37;
        /**
         * 系统背景材质类型(Windows 11 22621+)
         * 取值见 {@link DwmBackdropType}
         */
        public static final int DWMWA_SYSTEMBACKDROP_TYPE = 38;
        private DwmAttribute() {
        }
    }

    /** DWM 配色常量 */
    public static final class DwmColor {
        /** 让系统按主题自动选择颜色 */
        public static final int DEFAULT = 0xFFFFFFFF;
        /** 禁用对应颜色渲染 */
        public static final int NONE = 0xFFFFFFFE;
        private DwmColor() {
        }
    }

    /**
     * DWM 窗口圆角偏好常量
     *
     * <p>对应 DWMWA_WINDOW_CORNER_PREFERENCE 属性的取值</p>
     */
    public static final class DwmCornerPreference {
        /** 由系统或应用默认决定 */
        public static final int DEFAULT = 0;
        /** 不应用圆角 */
        public static final int DO_NOT_ROUND = 1;
        /** 使用标准圆角 */
        public static final int ROUND = 2;
        /** 使用小圆角 */
        public static final int ROUND_SMALL = 3;
        private DwmCornerPreference() {
        }
    }

    /**
     * DWM 系统背景材质类型常量
     *
     * <p>对应 DWMWA_SYSTEMBACKDROP_TYPE 属性的取值</p>
     */
    public static final class DwmBackdropType {
        /** 由系统自动选择背景效果(默认行为) */
        public static final int AUTO = 0;
        /** 不使用任何背景效果 */
        public static final int NONE = 1;
        /** Mica 云母材质(适用于主窗口背景，Windows 11 Build 22000+) */
        public static final int MAINWINDOW = 2;
        /** Acrylic 亚克力材质(适用于临时窗口/弹出窗口背景，Windows 11 Build 22621+) */
        public static final int TRANSIENTWINDOW = 3;
        /** Mica 标签页变体(适用于标签页式 MDI 窗口背景，Windows 11 Build 22621+) */
        public static final int TABBEDWINDOW = 4;
        private DwmBackdropType() {
        }
    }
}
