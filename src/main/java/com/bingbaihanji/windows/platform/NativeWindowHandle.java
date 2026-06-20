package com.bingbaihanji.windows.platform;

import java.util.Locale;
import java.util.Objects;

/**
 * 以数值形式暴露的不透明原生窗口句柄
 *
 * <p>该值与平台相关在 Windows 上为 HWND 指针值</p>
 */
public record NativeWindowHandle(String platformId, long value) {

    public NativeWindowHandle {
        platformId = Objects.requireNonNull(platformId, "platformId");
        if (platformId.isBlank()) {
            throw new IllegalArgumentException("platformId 不能为空");
        }
        if (value == 0L) {
            throw new IllegalArgumentException("原生句柄值不能为零");
        }
    }

    /**
     * 返回该句柄的十六进制字符串表示
     */
    public String hexValue() {
        return "0x" + Long.toUnsignedString(value, 16).toUpperCase(Locale.ROOT);
    }
}
