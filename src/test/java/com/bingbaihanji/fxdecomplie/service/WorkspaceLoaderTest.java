package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceLoaderTest {

    @Test
    void nullFileReportsErrorThroughUiExecutor() {
        AtomicBoolean usedUiExecutor = new AtomicBoolean(false);
        AtomicReference<String> error = new AtomicReference<>();
        Executor uiExecutor = task -> {
            usedUiExecutor.set(true);
            task.run();
        };

        WorkspaceLoader.loadAsync(null, new AppConfig(), uiExecutor,
                workspace -> fail("workspace should not be loaded"),
                error::set);

        assertTrue(usedUiExecutor.get());
        assertEquals("File is null", error.get());
    }

    @Test
    void missingFileReportsClearError() {
        AtomicReference<String> error = new AtomicReference<>();
        File missing = new File("target/missing-workspace-input.jar");

        WorkspaceLoader.loadAsync(missing, new AppConfig(), Runnable::run,
                workspace -> fail("workspace should not be loaded"),
                error::set);

        assertNotNull(error.get());
        assertTrue(error.get().contains("File not found"));
        assertTrue(error.get().contains(missing.getAbsolutePath()));
    }
}
