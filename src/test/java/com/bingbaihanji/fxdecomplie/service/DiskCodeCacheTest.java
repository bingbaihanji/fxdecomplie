package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
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

    /** 回归: 刷新访问时间不应影响已读取内容的返回(重复读取走 touch 成功路径) */
    @Test
    void repeatedLoadsReturnContent() {
        DiskCodeCache cache = cache();
        CodeCacheKey key = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        cache.save(key, "source");

        assertEquals(Optional.of("source"), cache.load(key));
        assertEquals(Optional.of("source"), cache.load(key));
    }

    @Test
    void clearAllRemovesEverything() {
        DiskCodeCache cache = new DiskCodeCache(tempDir.resolve("cache"));
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.clearAll();
        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
    }

    /** 清理只应作用于反编译缓存子树,不得删除缓存根目录下的姊妹缓存 */
    @Test
    void clearAllKeepsSiblingCaches() throws IOException {
        Path indexCacheFile = tempDir.resolve("index-cache").resolve("x.idx.gz");
        Path jadxRuntimeFile = tempDir.resolve("jadx").resolve("runtime").resolve("x");
        Files.createDirectories(indexCacheFile.getParent());
        Files.createDirectories(jadxRuntimeFile.getParent());
        Files.writeString(indexCacheFile, "index");
        Files.writeString(jadxRuntimeFile, "jadx");

        DiskCodeCache cache = new DiskCodeCache(tempDir);
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.clearAll();

        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
        assertTrue(Files.exists(indexCacheFile));
        assertTrue(Files.exists(jadxRuntimeFile));
    }

    /** 读取命中应刷新文件 mtime,供容量清理按最近使用近似淘汰 */
    @Test
    void loadRefreshesFileAccessTime() throws IOException {
        DiskCodeCache cache = new DiskCodeCache(tempDir);
        CodeCacheKey key = key("w", "fp", "com/example/A");
        cache.save(key, "source");

        List<Path> files;
        try (var stream = Files.walk(tempDir.resolve("v2"))) {
            files = stream.filter(Files::isRegularFile).toList();
        }
        assertEquals(1, files.size());
        Path cached = files.get(0);
        Files.setLastModifiedTime(cached,
                FileTime.fromMillis(System.currentTimeMillis() - 86_400_000L));
        long before = Files.getLastModifiedTime(cached).toMillis();

        assertEquals(Optional.of("source"), cache.load(key));

        assertTrue(Files.getLastModifiedTime(cached).toMillis() > before);
    }
}
