package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheKey;
import com.bingbaihanji.fxdecomplie.service.decompile.CodeCacheScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private static DiskCodeCache cache(Path root) {
        return new DiskCodeCache(root.resolve("decompile"), CacheLimits.L3_BYTES);
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
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey older = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey newer = key("com/example/Foo", "bbbbbbbbbbbbbbbb", 1);

        cache.save(older, "old-source");
        cache.save(newer, "new-source");

        assertEquals(Optional.of("old-source"), cache.load(older));
        assertEquals(Optional.of("new-source"), cache.load(newer));
    }

    @Test
    void differentKeyVersionsAreStoredSeparately() {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey v1 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey v2 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 2);

        cache.save(v1, "v1-source");
        cache.save(v2, "v2-source");

        assertEquals(Optional.of("v1-source"), cache.load(v1));
        assertEquals(Optional.of("v2-source"), cache.load(v2));
    }

    @Test
    void loadReturnsOnlyExactKey() {
        DiskCodeCache cache = cache(tempDir);
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
    void invalidateClassRemovesAllFingerprintVersionAndEngineVariants() {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey fp1 = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        CodeCacheKey fp2 = key("com/example/Foo", "bbbbbbbbbbbbbbbb", 2);
        CodeCacheKey vineflower = new CodeCacheKey("ws|stamp", "cccccccccccccccc", "com/example/Foo",
                DecompilerTypeEnum.VINEFLOWER, "default", 1);
        CodeCacheKey other = key("com/example/Bar", "aaaaaaaaaaaaaaaa", 1);

        cache.save(fp1, "fp1-source");
        cache.save(fp2, "fp2-source");
        cache.save(vineflower, "vineflower-source");
        cache.save(other, "other-source");

        cache.invalidate(fp1, CodeCacheScope.CLASS);

        assertTrue(cache.load(fp1).isEmpty());
        assertTrue(cache.load(fp2).isEmpty());
        assertTrue(cache.load(vineflower).isEmpty());
        assertEquals(Optional.of("other-source"), cache.load(other));
    }

    @Test
    void saveIgnoresBlankSource() {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey key = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);

        cache.save(key, "   ");
        cache.save(key, null);

        assertTrue(cache.load(key).isEmpty());
    }

    @Test
    void invalidatesWorkspaceScopeOnly() {
        DiskCodeCache cache = cache(tempDir);
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
        DiskCodeCache cache = cache(tempDir);
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.invalidate(key("w", "fp", "com/example/A"), CodeCacheScope.ALL);
        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
    }

    /** 回归: 刷新访问时间不应影响已读取内容的返回(重复读取走 touch 成功路径) */
    @Test
    void repeatedLoadsReturnContent() {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey key = key("com/example/Foo", "aaaaaaaaaaaaaaaa", 1);
        cache.save(key, "source");

        assertEquals(Optional.of("source"), cache.load(key));
        assertEquals(Optional.of("source"), cache.load(key));
    }

    @Test
    void clearAllRemovesEverything() {
        DiskCodeCache cache = cache(tempDir);
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.clearAll();
        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
    }

    /** 清理只应作用于反编译缓存子树,不得删除缓存根目录下的姊妹缓存 */
    @Test
    void clearAllKeepsSiblingCaches() throws IOException {
        Path indexCacheFile = tempDir.resolve("index").resolve("v1").resolve("x.idx.gz");
        Path jadxRuntimeFile = tempDir.resolve("jadx").resolve("runtime").resolve("x");
        Files.createDirectories(indexCacheFile.getParent());
        Files.createDirectories(jadxRuntimeFile.getParent());
        Files.writeString(indexCacheFile, "index");
        Files.writeString(jadxRuntimeFile, "jadx");

        DiskCodeCache cache = cache(tempDir);
        cache.save(key("w", "fp", "com/example/A"), "source");
        cache.clearAll();

        assertNull(cache.load(key("w", "fp", "com/example/A")).orElse(null));
        assertTrue(Files.exists(indexCacheFile));
        assertTrue(Files.exists(jadxRuntimeFile));
    }

    /** 读取命中应刷新文件 mtime,供配额守卫按最近使用近似淘汰 */
    @Test
    void loadRefreshesFileAccessTime() throws IOException {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey key = key("w", "fp", "com/example/A");
        cache.save(key, "source");

        List<Path> files;
        try (var stream = Files.walk(tempDir.resolve("decompile"))) {
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

    /** 内部名按包路径分目录,文件名保留类名/指纹/选项/键版本 */
    @Test
    void usesPackageDirectories() {
        DiskCodeCache cache = new DiskCodeCache(tempDir.resolve("decompile"), CacheLimits.L3_BYTES);
        cache.save(key("w", "fp", "com/example/Foo"), "source");
        assertTrue(Files.exists(tempDir.resolve("decompile")
                .resolve(CacheLayout.safeSegment("w"))
                .resolve("cfr").resolve("com").resolve("example")
                .resolve("Foo.fp.default.1.java")));
    }

    /** 配额淘汰按 mtime 从最旧开始删除,直到回落到目标水位 */
    @Test
    void quotaEvictionDeletesOldestToTarget() throws Exception {
        Path root = tempDir.resolve("decompile");
        // 关闭写入触发,由测试显式调用 evictIfNeeded,避免保存阶段提前淘汰;
        // 配额(29)略小于三个文件总大小(30)才会触发淘汰
        DiskQuotaGuard guard = new DiskQuotaGuard(root, 29, Runnable::run,
                Long.MAX_VALUE, Integer.MAX_VALUE, System::nanoTime);
        DiskCodeCache cache = new DiskCodeCache(root, guard);
        cache.save(key("w", "fp1", "com/example/A"), "1234567890");
        cache.save(key("w", "fp2", "com/example/B"), "1234567890");
        cache.save(key("w", "fp3", "com/example/C"), "1234567890");
        Path fileA;
        try (var walk = Files.walk(root)) {
            fileA = walk.filter(p -> p.getFileName().toString().startsWith("A."))
                    .findFirst().orElseThrow();
        }
        Files.setLastModifiedTime(fileA, FileTime.fromMillis(1_000L));
        guard.evictIfNeeded();
        assertNull(cache.load(key("w", "fp1", "com/example/A")).orElse(null));
        assertTrue(guard.stats("t", 0, 0).bytes() <= (long) (29 * 0.7) + 100);
    }

    /** 统计快照应反映实际文件数/字节数与命中、未命中计数 */
    @Test
    void statsReportEntriesBytesAndHitMissCounters() {
        DiskCodeCache cache = cache(tempDir);
        CodeCacheKey key = key("w", "fp", "com/example/Foo");
        cache.save(key, "source");
        cache.save(key, "   ");

        assertEquals(Optional.of("source"), cache.load(key));
        assertTrue(cache.load(key("w", "missing", "com/example/Bar")).isEmpty());

        CacheStats stats = cache.stats();
        assertEquals("decompile-l3", stats.name());
        assertEquals(1, stats.entries());
        assertEquals("source".getBytes(StandardCharsets.UTF_8).length, stats.bytes());
        assertEquals(1, stats.hitCount());
        assertEquals(1, stats.missCount());
    }
}
