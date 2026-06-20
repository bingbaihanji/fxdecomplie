package com.bingbaihanji.windows.jfx;

import com.bingbaihanji.windows.platform.NativeWindowHandle;
import javafx.stage.Stage;

import java.time.Duration;
import java.util.Optional;

final class NoopWindowPlatformProvider implements WindowPlatformProvider {

    static final NoopWindowPlatformProvider INSTANCE = new NoopWindowPlatformProvider();

    private NoopWindowPlatformProvider() {
    }

    @Override
    public String platformId() {
        return "generic";
    }

    @Override
    public boolean isSupported() {
        return false;
    }

    @Override
    public Optional<NativeWindowHandle> nativeHandle(Stage stage, Duration timeout) {
        return Optional.empty();
    }
}
