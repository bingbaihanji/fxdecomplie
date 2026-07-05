package com.bingbaihanji.javafx.jna.win32;

import com.bingbaihanji.windows.platform.WindowOperationStatus;
import com.bingbaihanji.windows.platform.win32.NativeWindowsTools;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeWindowsToolsTest {

    @Test
    void colorRefHelpersUseWindowsByteOrder() {
        int colorRef = NativeWindowsTools.rgbToColorRef(0x11, 0x22, 0x33);

        assertEquals(0x00332211, colorRef);
        assertEquals(0x11, NativeWindowsTools.colorRefRed(colorRef));
        assertEquals(0x22, NativeWindowsTools.colorRefGreen(colorRef));
        assertEquals(0x33, NativeWindowsTools.colorRefBlue(colorRef));
    }

    @Test
    void nullHandleOperationsSkipOrReturnDefaults() {
        assertEquals(WindowOperationStatus.SKIPPED,
                NativeWindowsTools.closeWindow(null).status());
        assertEquals(WindowOperationStatus.SKIPPED,
                NativeWindowsTools.setWindowTitle(null, "x").status());
        assertEquals("", NativeWindowsTools.getWindowTitle(null));
        assertFalse(NativeWindowsTools.isWindow(null));
        assertFalse(NativeWindowsTools.isValidWindowHandle(null));
        assertEquals(0L, NativeWindowsTools.nativeHandleValue(null));
        assertEquals("", NativeWindowsTools.nativeHandleHex(null));
        assertNull(NativeWindowsTools.clientToScreen(null, 1, 2));
        assertNull(NativeWindowsTools.screenToClient(null, 1, 2));
    }

    @Test
    void windowRecordsExposeDimensions() {
        NativeWindowsTools.WindowRect rect = new NativeWindowsTools.WindowRect(10, 20, 110, 70);

        assertEquals(100, rect.width());
        assertEquals(50, rect.height());
        assertFalse(rect.isEmpty());
        assertTrue(new NativeWindowsTools.WindowRect(0, 0, 0, 1).isEmpty());
    }
}
