package com.bingbaihanji.fxdecomplie.constants;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BootstrapPathsTest {

    private static final String[] PROPERTIES = {
            BootstrapPaths.APP_DIR_PROPERTY, "java.io.tmpdir", "jna.tmpdir",
            "javafx.cachedir", "prism.cachedir"
    };
    private final String[] previous = new String[PROPERTIES.length];

    @AfterEach
    void restoreProperties() {
        for (int i = 0; i < PROPERTIES.length; i++) {
            if (previous[i] == null) {
                System.clearProperty(PROPERTIES[i]);
            } else {
                System.setProperty(PROPERTIES[i], previous[i]);
            }
        }
        BootstrapPaths.resetForTesting();
    }

    @Test
    void configureRedirectsAllRuntimeDirectoriesUnderAppDir() {
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
        }
        Path appDir = BootstrapPaths.resolveAppDir();

        BootstrapPaths.configure();

        Path temp = appDir.resolve("cache").resolve("tmp");
        assertEquals(appDir.toString(), System.getProperty(BootstrapPaths.APP_DIR_PROPERTY));
        assertEquals(temp.toString(), System.getProperty("java.io.tmpdir"));
        assertEquals(temp.toString(), System.getProperty("jna.tmpdir"));
        assertEquals(appDir.resolve("cache").resolve("javafx").toString(),
                System.getProperty("javafx.cachedir"));
        assertEquals(appDir.resolve("cache").resolve("javafx").toString(),
                System.getProperty("prism.cachedir"));
        assertTrue(temp.isAbsolute());
    }

    @Test
    void configureAppDirDoesNotTouchTempDir() {
        for (int i = 0; i < PROPERTIES.length; i++) {
            previous[i] = System.getProperty(PROPERTIES[i]);
        }
        String oldTemp = System.getProperty("java.io.tmpdir");

        BootstrapPaths.configureAppDir();

        assertEquals(BootstrapPaths.resolveAppDir().toString(),
                System.getProperty(BootstrapPaths.APP_DIR_PROPERTY));
        assertEquals(oldTemp, System.getProperty("java.io.tmpdir"));
    }
}
