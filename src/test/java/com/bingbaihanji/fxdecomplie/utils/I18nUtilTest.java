package com.bingbaihanji.fxdecomplie.utils;

import com.bingbaihanji.util.I18nContext;
import com.bingbaihanji.util.I18nUtil;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;

import static org.junit.jupiter.api.Assertions.*;

class I18nUtilTest {

    @Test
    void createBundleForChineseLoadsSuccessfully() {
        ResourceBundle bundle = I18nContext.of(Locale.SIMPLIFIED_CHINESE).getBundle();
        assertNotNull(bundle);
        assertTrue(bundle.containsKey("tab.source") || bundle.containsKey("menu.file"));
    }

    @Test
    void createBundleForEnglishLoadsSuccessfully() {
        ResourceBundle bundle = I18nContext.of(Locale.ENGLISH).getBundle();
        assertNotNull(bundle);
    }

    @Test
    void languageOnlyFallbackLoadsForRegionalEnglishLocale() {
        ResourceBundle bundle = I18nContext.of(Locale.US).getBundle();
        assertNotNull(bundle);
        assertEquals("File", bundle.getString("menu.file"));
    }

    @Test
    void createBundleForUnsupportedLocaleFallsBack() {
        try {
            ResourceBundle bundle = I18nContext.of(new Locale("no", "NO")).getBundle();
            assertNotNull(bundle);
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("find resource bundle") || e.getMessage().contains("Failed to load"));
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

    @Test
    void missingKeyWithDefaultReturnsDefault() {
        String result = I18nUtil.getStringOrDefault("nonexistent.key.12345", "fallback");
        assertEquals("fallback", result);
    }
}
