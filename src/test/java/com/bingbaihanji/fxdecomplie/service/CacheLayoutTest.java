package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CacheLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    void productionPathsAreSeparated() {
        String root = CacheLayout.cacheRoot().toString().replace('\\', '/');
        assertTrue(root.endsWith("/cache"));
        assertTrue(CacheLayout.decompileDir().toString().replace('\\', '/')
                .endsWith("/cache/decompile/v1"));
        assertTrue(CacheLayout.indexDir().toString().replace('\\', '/')
                .endsWith("/cache/index/v1"));
        assertTrue(CacheLayout.jadxDir().toString().replace('\\', '/')
                .endsWith("/cache/jadx/runtime"));
    }

    @Test
    void decompilePathUsesPackageDirectoriesAndKeyVersion() {
        CodeCacheKey key = new CodeCacheKey("C:\\ws|stamp", "abcdef0123456789",
                "com/example/Foo$Inner", DecompilerTypeEnum.CFR, "default", 3);
        Path file = CacheLayout.decompilePath(tempDir, key);
        String normalized = file.toString().replace('\\', '/');
        assertTrue(normalized.endsWith(
                "/C__ws_stamp/cfr/com/example/Foo$Inner.abcdef0123456789.default.3.java"),
                normalized);
    }

    @Test
    void unsafeSegmentsCannotEscapeRoot() {
        CodeCacheKey key = new CodeCacheKey("..", "fp", "../../evil/..",
                DecompilerTypeEnum.CFR, "default", 1);
        Path file = CacheLayout.decompilePath(tempDir, key);
        assertTrue(file.normalize().startsWith(tempDir.normalize()));
        assertFalse(file.toString().contains(".."));
    }

    @Test
    void cleanupLegacyRemovesOnlyOldLayouts() throws Exception {
        Path legacyDecompile = tempDir.resolve("v2").resolve("x");
        Path legacyIndex = tempDir.resolve("index-cache").resolve("x.idx.gz");
        Path keepDecompile = tempDir.resolve("decompile").resolve("v1").resolve("x");
        Path keepIndex = tempDir.resolve("index").resolve("v1").resolve("x.idx.gz");
        Path keepJadx = tempDir.resolve("jadx").resolve("runtime").resolve("x");
        for (Path p : new Path[]{legacyDecompile, legacyIndex, keepDecompile, keepIndex, keepJadx}) {
            Files.createDirectories(p.getParent());
            Files.writeString(p, "x");
        }

        CacheLayout.cleanupLegacy(tempDir);

        assertFalse(Files.exists(legacyDecompile));
        assertFalse(Files.exists(legacyIndex));
        assertTrue(Files.exists(keepDecompile));
        assertTrue(Files.exists(keepIndex));
        assertTrue(Files.exists(keepJadx));
    }
}
