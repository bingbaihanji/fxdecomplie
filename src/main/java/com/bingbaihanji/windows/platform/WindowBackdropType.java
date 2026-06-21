package com.bingbaihanji.windows.platform;

/**
 * 平台无关的系统渲染窗口背景材质描述
 *
 * <p>对应 Windows 11 DWM 系统背景类型（DWMWA_SYSTEMBACKDROP_TYPE），
 * 非 Windows 平台或旧版 Windows 上此设置无效果</p>
 */
public enum WindowBackdropType {
    /** 由系统自动选择背景效果（默认行为） */
    AUTO,
    /** 不使用任何背景效果 */
    NONE,
    /** Mica 云母材质（适用于主窗口背景，Windows 11 Build 22000+） */
    MAIN_WINDOW,
    /** Acrylic 亚克力材质（适用于临时窗口/弹出窗口背景，Windows 11 Build 22621+） */
    TRANSIENT_WINDOW,
    /** Mica 标签页变体（适用于标签页式 MDI 窗口背景，Windows 11 Build 22621+） */
    TABBED_WINDOW
}
