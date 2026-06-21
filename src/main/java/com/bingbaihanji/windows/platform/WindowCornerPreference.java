package com.bingbaihanji.windows.platform;

/**
 * 平台无关的窗口圆角偏好设置
 *
 * <p>对应 Windows 11 DWM 圆角属性（DWMWA_WINDOW_CORNER_PREFERENCE），
 * 非 Windows 平台或旧版 Windows 上此设置无效果</p>
 */
public enum WindowCornerPreference {
    /** 由系统默认决定 */
    DEFAULT,
    /** 不使用圆角 */
    DO_NOT_ROUND,
    /** 启用标准圆角 */
    ROUND,
    /** 启用小圆角 */
    ROUND_SMALL
}
