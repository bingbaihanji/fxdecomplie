package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.gui.JadxGuiContext;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files.IJadxFilesGetter;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class AppContext {

    private @Nullable JadxGuiContext guiContext;

    private IJadxFilesGetter filesGetter;

    public @Nullable JadxGuiContext getGuiContext() {
        return guiContext;
    }

    public void setGuiContext(@Nullable JadxGuiContext guiContext) {
        this.guiContext = guiContext;
    }

    public IJadxFilesGetter getFilesGetter() {
        return Objects.requireNonNull(filesGetter);
    }

    public void setFilesGetter(IJadxFilesGetter filesGetter) {
        this.filesGetter = filesGetter;
    }
}
