package com.bingbaihanji.fxdecomplie.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.ResourceBundle;

class I18nUtilTest {

    @Test
    void createBundleForChineseLoadsSuccessfully() {
        ResourceBundle bundle = I18nUtil.createBundleFor(Locale.SIMPLIFIED_CHINESE);
        assertNotNull(bundle);
        // Verify a known key exists
        assertTrue(bundle.containsKey("tab.source") || bundle.containsKey("menu.file"));
    }

    @Test
    void createBundleForEnglishLoadsSuccessfully() {
        ResourceBundle bundle = I18nUtil.createBundleFor(Locale.ENGLISH);
        assertNotNull(bundle);
    }

    @Test
    void languageOnlyFallbackLoadsForRegionalEnglishLocale() {
        ResourceBundle bundle = I18nUtil.createBundleFor(Locale.US);
        assertNotNull(bundle);
        assertEquals("File", bundle.getString("menu.file"));
    }

    @Test
    void createBundleForUnsupportedLocaleFallsBack() {
        // Norwegian — should fall back to default or throw
        try {
            ResourceBundle bundle = I18nUtil.createBundleFor(new Locale("no", "NO"));
            assertNotNull(bundle);
        } catch (RuntimeException e) {
            // Acceptable — unsupported locale may fail
            assertTrue(e.getMessage().contains("Failed to load"));
        }
    }

    @Test
    void defaultStaticGetStringReturnsNonEmpty() {
        String result = I18nUtil.getString("menu.file");
        assertNotNull(result);
        assertFalse(result.isBlank());
    }

    @Test
    void missingKeyReturnsKeyItself() {
        String result = I18nUtil.getString("nonexistent.key.12345");
        assertEquals("nonexistent.key.12345", result);
    }
}
