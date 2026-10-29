package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiskCodeCacheTest {

    @TempDir
    Path tempDir;

    private DiskCodeCache cache() {
        return new DiskCodeCache(tempDir.resolve("cache"));
    }

    private static CodeCacheKey key(String internalName, String classFingerprint, int keyVersion) {
        return new CodeCacheKey("ws|stamp", classFingerprint, internalName,
                DecompilerTypeEnum.CFR, "default", keyVersion);
    }

    private static CodeCacheKey key(String workspace, String classFingerprint, String internalName) {
        return new CodeCacheKey(workspace, classFingerprint, internalName,
                DecompilerTypeEnum.CFR, "default", 1);
    }

    @Test
    void differentClassFingerprintsAreStoredSeparately() {
        DiskCodeCache cache = cache();
        CodeCacheKey older = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey newer = key("com/example/Foo", "bbbbbbbbbbbbbbbb", 1);

        cache.save(older, "old-source");
        cache.save(newer, "new-source");

        assertEquals(Optional.of("old-source"), cache.load(older));
        assertEquals(Optional.of("new-source"), cache.load(newer));
    }

    @Test
    void differentKeyVersionsAreStoredSeparately() {
        DiskCodeCache cache = cache();
        CodeCacheKey v1 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey v2 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 2);

        cache.save(v1, "v1-source");
        cache.save(v2, "v2-source");

        assertEquals(Optional.of("v1-source"), cache.load(v1));
        assertEquals(Optional.of("v2-source"), cache.load(v2));
    }

    @Test
    void loadReturnsOnlyExactKey() {
        DiskCodeCache cache = cache();
        CodeCacheKey exact = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        cache.save(exact, "source");

        assertEquals(Optional.of("source"), cache.load(exact));
        assertTrue(cache.load(key("com/example/Bar", "aaaaaaaaaaaaaaaa", 1)).isEmpty());
        assertTrue(cache.load(new CodeCacheKey("ws|stamp", "aaaaaaaaaaaaaaaa", "com/example/Foo",
                DecompilerTypeEnum.VINEFLOWER, "default", 1)).isEmpty());
        assertTrue(cache.load(new CodeCacheKey("ws|stamp", "aaaaaaaaaaaaaaaa", "com/example/Foo",
                DecompilerTypeEnum.CFR, "other-options", 1)).isEmpty());
    }

    @Test
    void invalidateClassRemovesAllFingerprintAndVersionVariants() {
        DiskCodeCache cache = cache();
        CodeCacheKey fp1 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey fp2 = key("com/example/Foo", "bbbbbbbbbbbbbbbb", 2);
        CodeCacheKey other = key("com/example/Bar", "aaaaaaaaaaaaaaaa", 1);

        cache.save(fp1, "fp1-source");
        cache.save(fp2, "fp2-source");
        cache.save(other, "other-source");

        cache.invalidate(fp1, CodeCacheScope.CLASS);

        assertTrue(cache.load(fp1).isEmpty());
        assertTrue(cache.load(fp2).isEmpty());
        assertEquals(Optional.of("other-source"), cache.load(other));
    }

    @Test
    void saveIgnoresBlankSource() {
        DiskCodeCache cache = cache();
        CodeCacheKey key = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);

        cache.save(key, "   ");
        cache.save(key, null);

        assertTrue(cache.load(key).isEmpty());
    }

    @Test
    void invalidatesWorkspaceScopeOnly() {
        DiskCodeCache cache = new DiskCodeCache(tempDir);
        cache.save(key("w1", "fp", "com/example/A"), "a1");
        cache.save(key("w1", "fp", "com/example/B"), "b1");
        cache.save(key("w2", "fp", "com/example/A"), "a2");

        cache.invalidate(key("w1", "fp", "com/example/A"), CodeCacheScope.WORKSPACE);

        assertNull(cache.load(key("w1", "fp", "com/example/A")).orElse(null));
        assertNull(cache.load(key("w1", "fp", "com/example/B")).orElse(null));
        assertEquals("a2", cache.load(key("w2", "fp", "com/example/A")).orElse(null));
    }

    @Test
    void invalidatesAllScopeOnInjectedRoot() {
        DiskCodeCache cache = new DiskCodeCache(tempDir.resolve("cache"));
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.invalidate(key("w", "fp", "com/example/A"), CodeCacheScope.ALL);
        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
    }

    @Test
    void clearAllRemovesEverything() {
        DiskCodeCache cache = new DiskCodeCache(tempDir.resolve("cache"));
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.clearAll();
        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
    }
}
