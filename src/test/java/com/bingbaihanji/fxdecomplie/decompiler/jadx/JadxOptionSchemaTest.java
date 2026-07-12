package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JadxOptionSchemaTest {

    @Test
    void fromOptionsMapReturnsDefaultsForEmptyMap() {
        JadxEngineOptions opts = JadxOptionSchema.fromOptionsMap(Map.of());
        assertEquals(JadxEngineOptions.DEFAULTS, opts);
    }

    @Test
    void fromOptionsMapParsesBooleanValues() {
        JadxEngineOptions opts = JadxOptionSchema.fromOptionsMap(
                Map.of("useImports", "false", "debugInfo", "false"));

        assertFalse(opts.useImports());
        assertFalse(opts.debugInfo());
        assertEquals(JadxEngineOptions.DEFAULTS.extractFinally(), opts.extractFinally());
    }

    @Test
    void fromOptionsMapParsesIntegerValues() {
        JadxEngineOptions opts = JadxOptionSchema.fromOptionsMap(
                Map.of("threadsCount", "8", "deobfuscationMinLength", "5"));

        assertEquals(8, opts.threadsCount());
        assertEquals(5, opts.deobfuscationMinLength());
    }

    @Test
    void optionsChangeChangesCanonicalString() {
        JadxEngineOptions a = JadxEngineOptions.DEFAULTS;
        JadxEngineOptions b = JadxOptionSchema.fromOptionsMap(Map.of("useImports", "false"));

        assertNotEquals(JadxOptionSchema.toCanonicalString(a),
                JadxOptionSchema.toCanonicalString(b));
    }

    @Test
    void sameOptionsSameCanonicalString() {
        JadxEngineOptions a = JadxOptionSchema.fromOptionsMap(Map.of("useImports", "false"));
        JadxEngineOptions b = JadxOptionSchema.fromOptionsMap(Map.of("useImports", "false"));

        assertEquals(JadxOptionSchema.toCanonicalString(a),
                JadxOptionSchema.toCanonicalString(b));
    }

    @Test
    void unknownKeysIgnored() {
        JadxEngineOptions opts = JadxOptionSchema.fromOptionsMap(
                Map.of("nonexistentKey", "value"));

        assertEquals(JadxEngineOptions.DEFAULTS, opts);
    }
}
