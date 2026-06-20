package com.bingbaihanji.fxdecomplie.platform.win32;

/**
 * 系统背景类型枚举(Windows 11 Mica / Acrylic 效果)
 *
 * @see NativeWindowsTools#setSystemStageStyle
 */
public enum SystemBackdropType {
    /** 由系统自动选择背景效果(默认行为) */
    AUTO(0),
    /** 不使用任何背景效果 */
    NONE(1),
    /** Mica 云母材质(适用于主窗口背景,Windows 11 Build 22000+) */
    MAINWINDOW(2),
    /** Acrylic 亚克力材质(适用于临时窗口 / 弹出窗口背景,Windows 11 Build 22621+) */
    TRANSIENTWINDOW(3),
    /** Mica 标签页变体(适用于标签页式 MDI 窗口背景,Windows 11 Build 22621+) */
    TABBEDWINDOW(4);

    private final int value;

    SystemBackdropType(int value) {
        this.value = value;
    }

    public static SystemBackdropType fromValue(int value) {
        for (SystemBackdropType type : values()) {
            if (type.value == value) return type;
        }
        throw new IllegalArgumentException("Unknown SystemBackdropType: " + value);
    }

    public int getValue() {
        return value;
    }
}
