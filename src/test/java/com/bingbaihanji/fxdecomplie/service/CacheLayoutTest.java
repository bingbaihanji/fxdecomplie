package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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
    void safeSegmentRejectsBlankAndDotSegments() {
        assertEquals("_", CacheLayout.safeSegment(null));
        assertEquals("_", CacheLayout.safeSegment(""));
        assertEquals("_", CacheLayout.safeSegment("   "));
        assertEquals("_", CacheLayout.safeSegment("."));
        assertEquals("_", CacheLayout.safeSegment(".."));
        assertEquals("_", CacheLayout.safeSegment(".. "));
        assertEquals("_", CacheLayout.safeSegment(" . "));
    }

    @Test
    void safeSegmentReplacesControlAndSeparatorChars() {
        assertEquals("A_B", CacheLayout.safeSegment("A\u0001B"));
        assertEquals("A_B", CacheLayout.safeSegment("A\u007FB"));
        assertEquals("a_b", CacheLayout.safeSegment("a/b"));
        assertEquals("a_b", CacheLayout.safeSegment("a\\b"));
        assertEquals("a_b", CacheLayout.safeSegment("a:b"));
        assertEquals("a_b_c_d_e_f_g", CacheLayout.safeSegment("a*b?c\"d<e>f|g"));
    }

    @Test
    void safeSegmentStripsTrailingSpacesAndDots() {
        assertEquals("exa", CacheLayout.safeSegment("exa "));
        assertEquals("Foo", CacheLayout.safeSegment("Foo. "));
        assertEquals("report", CacheLayout.safeSegment("report..."));
        assertEquals("a", CacheLayout.safeSegment("a . "));
        assertFalse(CacheLayout.safeSegment("trailing. ").endsWith("."));
        assertFalse(CacheLayout.safeSegment("trailing. ").endsWith(" "));
    }

    @Test
    void safeSegmentCapsLength() {
        String segment = CacheLayout.safeSegment("a".repeat(300));
        assertEquals(120, segment.length(), segment);
        assertTrue(segment.matches("a{103}~[0-9a-f]{16}"), segment);
        assertEquals(segment, CacheLayout.safeSegment("a".repeat(300)));
    }

    @Test
    void safeSegmentGuardsWindowsReservedNames() {
        assertEquals("_CON", CacheLayout.safeSegment("CON"));
        assertEquals("_con", CacheLayout.safeSegment("con"));
        assertEquals("_PRN", CacheLayout.safeSegment("PRN"));
        assertEquals("_aux", CacheLayout.safeSegment("aux"));
        assertEquals("_NUL", CacheLayout.safeSegment("NUL"));
        assertEquals("_COM1", CacheLayout.safeSegment("COM1"));
        assertEquals("_lpt9", CacheLayout.safeSegment("lpt9"));
        assertEquals("_CON.txt", CacheLayout.safeSegment("CON.txt"));
        assertEquals("COM0", CacheLayout.safeSegment("COM0"));
        assertEquals("COM10", CacheLayout.safeSegment("COM10"));
        assertEquals("LPT0", CacheLayout.safeSegment("LPT0"));
    }

    @Test
    void decompilePathSanitizesHostileNames() {
        CodeCacheKey control = new CodeCacheKey("ws", "fp", "com/example/A\u0001B",
                DecompilerTypeEnum.CFR, "default", 1);
        Path controlFile = CacheLayout.decompilePath(tempDir, control);
        assertTrue(controlFile.normalize().startsWith(tempDir.normalize()));
        assertEquals("A_B.fp.default.1.java", controlFile.getFileName().toString());
        assertEquals("example", controlFile.getParent().getFileName().toString());
        assertSafePath(controlFile);

        CodeCacheKey trailing = new CodeCacheKey("ws", "fp", "exa /Foo. ",
                DecompilerTypeEnum.CFR, "default", 1);
        Path trailingFile = CacheLayout.decompilePath(tempDir, trailing);
        assertEquals("exa", trailingFile.getParent().getFileName().toString());
        assertEquals("Foo.fp.default.1.java", trailingFile.getFileName().toString());
        assertSafePath(trailingFile);
    }

    @Test
    void decompilePathCapsLongSimpleNames() {
        CodeCacheKey key = new CodeCacheKey("ws", "fp", "com/example/" + "B".repeat(300),
                DecompilerTypeEnum.CFR, "default", 1);
        Path file = CacheLayout.decompilePath(tempDir, key);
        assertTrue(file.normalize().startsWith(tempDir.normalize()));
        assertEquals("example", file.getParent().getFileName().toString());
        String name = file.getFileName().toString();
        int firstDot = name.indexOf('.');
        assertEquals(120, firstDot, name);
        assertTrue(name.substring(0, firstDot).matches("B{103}~[0-9a-f]{16}"), name);
        assertTrue(name.endsWith(".java"), name);
        assertSafePath(file);
    }

    @Test
    void packageOfAndSimpleNameOf() {
        assertEquals("", CacheLayout.packageOf(null));
        assertNull(CacheLayout.simpleNameOf(null));
        assertEquals("", CacheLayout.packageOf("Foo"));
        assertEquals("Foo", CacheLayout.simpleNameOf("Foo"));
        assertEquals("", CacheLayout.packageOf("/Foo"));
        assertEquals("Foo", CacheLayout.simpleNameOf("/Foo"));
        assertEquals("com/example", CacheLayout.packageOf("com/example/Foo$Inner"));
        assertEquals("Foo$Inner", CacheLayout.simpleNameOf("com/example/Foo$Inner"));
        assertEquals("com", CacheLayout.packageOf("com/"));
        assertEquals("", CacheLayout.simpleNameOf("com/"));
    }

    @Test
    void decompilePathSanitizesOptionsHash() {
        CodeCacheKey key = new CodeCacheKey("ws", "fp", "p/C",
                DecompilerTypeEnum.CFR, "opt/with:bad", 2);
        Path file = CacheLayout.decompilePath(tempDir, key);
        assertEquals("C.fp.opt_with_bad.2.java", file.getFileName().toString());

        CodeCacheKey defaultKey = new CodeCacheKey("ws", "fp", "p/C",
                DecompilerTypeEnum.CFR, "default", 2);
        assertEquals("C.fp.default.2.java",
                CacheLayout.decompilePath(tempDir, defaultKey).getFileName().toString());

        CodeCacheKey blankKey = new CodeCacheKey("ws", "fp", "p/C",
                DecompilerTypeEnum.CFR, "   ", 2);
        assertEquals("C.fp.default.2.java",
                CacheLayout.decompilePath(tempDir, blankKey).getFileName().toString());
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

    @Test
    void cleanupLegacyIsNoOpWhenRootMissing() {
        Path missing = tempDir.resolve("missing-root");
        assertDoesNotThrow(() -> CacheLayout.cleanupLegacy(missing));
        assertFalse(Files.exists(missing));
    }

    @Test
    void cleanupLegacyIsIdempotent() throws Exception {
        Path legacy = tempDir.resolve("v2").resolve("a").resolve("x");
        Files.createDirectories(legacy.getParent());
        Files.writeString(legacy, "x");

        CacheLayout.cleanupLegacy(tempDir);
        CacheLayout.cleanupLegacy(tempDir);

        assertFalse(Files.exists(tempDir.resolve("v2")));
    }

    @Test
    void cleanupLegacyDoesNotFollowLinks() throws Exception {
        Path sentinelDir = tempDir.resolve("sentinel");
        Files.createDirectories(sentinelDir);
        Path sentinel = sentinelDir.resolve("keep.txt");
        Files.writeString(sentinel, "keep");

        Path legacyRoot = tempDir.resolve("v2");
        Files.createDirectories(legacyRoot);
        Path sibling = legacyRoot.resolve("sibling.txt");
        Files.writeString(sibling, "x");

        Path link = legacyRoot.resolve("link");
        boolean linkCreated = false;
        try {
            Files.createSymbolicLink(link, sentinelDir);
            linkCreated = true;
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // Windows 创建符号链接通常需要开发者模式或管理员权限，失败时退化为验证普通文件不被误删
        }

        CacheLayout.cleanupLegacy(tempDir);

        assertFalse(Files.exists(legacyRoot));
        assertFalse(Files.exists(sibling));
        assertTrue(Files.exists(sentinel));
        assertEquals("keep", Files.readString(sentinel));
        if (linkCreated) {
            assertFalse(Files.exists(link));
        }
    }

    /** 逐段断言路径不含控制字符、不以空格或点结尾 */
    private static void assertSafePath(Path path) {
        for (Path segment : path) {
            String name = segment.toString();
            assertFalse(name.chars().anyMatch(c -> c < 0x20 || c == 0x7F), name);
            assertFalse(name.endsWith(" "), name);
            assertFalse(name.endsWith("."), name);
        }
    }
}
