package com.bingbaihanji.javafx.fx;

import com.bingbaihanji.windows.jfx.WindowPlatformProvider;
import com.bingbaihanji.windows.jfx.WindowToolkit;
import com.bingbaihanji.windows.platform.NativeWindowHandle;
import com.bingbaihanji.windows.platform.WindowAppearance;
import com.bingbaihanji.windows.platform.WindowOperationResult;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WindowToolkitTest {

    @AfterEach
    void resetProvider() {
        WindowToolkit.resetProvider();
    }

    @Test
    void providerCanBeReplacedForEmbeddingAndTests() {
        WindowPlatformProvider provider = new StubProvider();

        WindowToolkit.setProvider(provider);

        assertSame(provider, WindowToolkit.provider());
        assertTrue(WindowToolkit.isNativeSupported());
    }

    @Test
    void applyAppearanceSkipsNonStageWindows() {
        WindowOperationResult result = WindowToolkit.applyAppearance(
                null,
                WindowAppearance.builder().darkMode(true).build());

        assertTrue(result.isSkipped());
    }

    @Test
    void nativeHandleReturnsEmptyForNullStage() {
        WindowToolkit.setProvider(new StubProvider());

        assertFalse(WindowToolkit.nativeHandle(null).isPresent());
    }

    private static final class StubProvider implements WindowPlatformProvider {
        @Override
        public String platformId() {
            return "stub";
        }

        @Override
        public boolean isSupported() {
            return true;
        }

        @Override
        public Optional<NativeWindowHandle> nativeHandle(Stage stage, Duration timeout) {
            return Optional.of(new NativeWindowHandle(platformId(), 1L));
        }
    }
}
