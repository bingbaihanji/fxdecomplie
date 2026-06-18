package com.bingbihanji.fxdecomplie.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    @Test
    void loadsWithDefaultValues() {
        AppConfig config = new AppConfig();
        assertNotNull(config);
        assertEquals(1200, config.window.width);
        assertEquals(800, config.window.height);
        assertEquals(14, config.theme.fontSize);
    }

    @Test
    void addRecentFileDeduplicates() {
        AppConfig config = new AppConfig();
        config.addRecentFile("/test/a.jar");
        config.addRecentFile("/test/b.jar");
        config.addRecentFile("/test/a.jar"); // should move to front
        assertEquals(2, config.recentFiles.size());
        assertEquals("/test/a.jar", config.recentFiles.get(0));
        assertEquals("/test/b.jar", config.recentFiles.get(1));
    }

    @Test
    void addRecentFileTrimsToMax() {
        AppConfig config = new AppConfig();
        for (int i = 0; i < 25; i++) {
            config.addRecentFile("/test/" + i + ".jar");
        }
        assertTrue(config.recentFiles.size() <= 20);
    }

    @Test
    void normalizeFillsNullFields() throws Exception {
        AppConfig config = new AppConfig();
        config.window = null;
        config.theme = null;
        // normalize() is private, use reflection to invoke it
        java.lang.reflect.Method method = AppConfig.class.getDeclaredMethod("normalize");
        method.setAccessible(true);
        method.invoke(config);
        assertNotNull(config.window);
        assertNotNull(config.theme);
    }
}
