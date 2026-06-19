package com.bingbaihanji.fxdecomplie.platform.win32;

/**
 * DWM 窗口圆角偏好枚举
 *
 * @see NativeWindowsTools#setWindowCornerPreference
 */
public enum DwmWindowCornerPreference {
    DEFAULT(0),
    DO_NOT_ROUND(1),
    ROUND(2),
    ROUND_SMALL(3);

    private final int value;

    DwmWindowCornerPreference(int value) {
        this.value = value;
    }

    public static DwmWindowCornerPreference fromValue(int value) {
        for (DwmWindowCornerPreference pref : values()) {
            if (pref.value == value) return pref;
        }
        throw new IllegalArgumentException("Unknown DwmWindowCornerPreference: " + value);
    }

    public int getValue() {
        return value;
    }
}
