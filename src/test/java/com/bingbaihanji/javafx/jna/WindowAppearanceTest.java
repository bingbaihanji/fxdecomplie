package com.bingbaihanji.javafx.jna;

import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowBackdropType;
import com.bingbaihanji.windows.platform.WindowCornerPreference;
import com.bingbaihanji.windows.platform.win32.NativeWindowsTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindowAppearanceTest {

    @Test
    void emptyAppearanceHasNoNativeAttributes() {
        WindowAppearance appearance = WindowAppearance.builder().build();

        assertFalse(appearance.hasNativeAttributes());
    }

    @Test
    void darkDialogRequestsAllDefaultAttributes() {
        WindowAppearance appearance = WindowAppearance.darkDialog(
                0x00888800,
                WindowCornerPreference.DO_NOT_ROUND);

        assertTrue(appearance.hasNativeAttributes());
        assertEquals(Boolean.TRUE, appearance.darkMode());
        assertEquals(Boolean.TRUE, appearance.shadow());
        assertEquals(WindowBackdropType.TRANSIENT_WINDOW, appearance.backdropType());
        assertEquals(WindowCornerPreference.DO_NOT_ROUND, appearance.cornerPreference());
        assertEquals(WindowAppearance.FrameMargins.fullClientArea(), appearance.frameMargins());
    }

    @Test
    void rgbToColorRefUsesWindowsByteOrder() {
        assertEquals(0x00332211, NativeWindowsTools.rgbToColorRef(0x11, 0x22, 0x33));
        assertThrows(IllegalArgumentException.class,
                () -> NativeWindowsTools.rgbToColorRef(256, 0, 0));
    }
}
