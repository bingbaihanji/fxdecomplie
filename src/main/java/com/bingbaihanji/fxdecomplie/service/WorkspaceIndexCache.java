package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.ResourceIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.utils.json.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 工作区索引缓存 — 基于内容指纹的 class 元数据持久化,避免同一个大包重复打开时重建整套索引
 *
 * @author bingbaihanji
 * @date 2026-08-24
 */
public final class WorkspaceIndexCache {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndexCache.class);

    private static final String CACHE_DIR_NAME = "index-cache";

    private static final String CACHE_FILE_SUFFIX = ".idx.json";

    private WorkspaceIndexCache() {
        throw new AssertionError("utility class");
    }

    /**
     * 计算文件的内容指纹(基于文件路径 + 大小 + 最后修改时间的 CRC32)
     *
     * @param filePath 文件路径
     * @return 内容指纹字符串(16 位十六进制)
     */
    public static String computeFingerprint(Path filePath) {
        try {
            if (Files.isDirectory(filePath)) {
                return computeDirectoryFingerprint(filePath);
            }
            CRC32 crc = new CRC32();
            crc.update(filePath.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8));
            try (InputStream in = Files.newInputStream(filePath)) {
                byte[] buffer = new byte[1024 * 1024];
                int read;
                while ((read = in.read(buffer)) >= 0) {
                    if (read > 0) {
                        crc.update(buffer, 0, read);
                    }
                }
            }
            return String.format("%016x", crc.getValue());
        } catch (IOException e) {
            log.debug("计算内容指纹失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 从缓存加载 class 元数据索引
     *
     * @param fingerprint 内容指纹
     * @return 缓存索引,若不存在或损坏则返回 null
     */
    public static CachedIndex load(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return null;
        }
        Path cacheFile = cacheFilePath(fingerprint);
        if (!Files.exists(cacheFile)) {
            return null;
        }
        try {
            String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
            CachedIndex cached = JSONUtils.fromJson(json, CachedIndex.class);
            if (cached == null || cached.classes == null || cached.classes.isEmpty()) {
                return null;
            }
            log.info("索引缓存命中: {} ({} classes, {} resources)",
                    fingerprint, cached.classes.size(),
                    cached.resources == null ? 0 : cached.resources.size());
            return cached;
        } catch (Exception e) {
            log.debug("加载索引缓存失败: {}", fingerprint, e);
            return null;
        }
    }

    /**
     * 保存 class 元数据索引到缓存
     *
     * @param fingerprint 内容指纹
     * @param index 要缓存的索引
     */
    public static void save(String fingerprint, WorkspaceIndex index) {
        if (fingerprint == null || fingerprint.isBlank() || index == null) {
            return;
        }
        try {
            Path cacheDir = cacheDirectory();
            Files.createDirectories(cacheDir);
            CachedIndex cached = new CachedIndex();
            cached.classes = new ArrayList<>(index.classes().size());
            for (ClassIndexEntry entry : index.classes()) {
                cached.classes.add(CachedClassEntry.from(entry));
            }
            cached.resources = new ArrayList<>(index.resources().size());
            for (ResourceIndexEntry resource : index.resources()) {
                cached.resources.add(CachedResourceEntry.from(resource));
            }
            String json = JSONUtils.toJson(cached);
            Files.writeString(cacheFilePath(fingerprint), json, StandardCharsets.UTF_8);
            log.info("索引缓存已保存: {} ({} classes, {} resources)",
                    fingerprint, cached.classes.size(), cached.resources.size());
        } catch (Exception e) {
            log.debug("保存索引缓存失败: {}", fingerprint, e);
        }
    }

    /**
     * 清除所有缓存
     */
    public static void clearAll() {
        try {
            Path cacheDir = cacheDirectory();
            if (Files.exists(cacheDir)) {
                try (var stream = Files.list(cacheDir)) {
                    stream.filter(p -> p.toString().endsWith(CACHE_FILE_SUFFIX))
                            .forEach(p -> {
                                try {
                                    Files.deleteIfExists(p);
                                } catch (IOException ignored) {
                                }
                            });
                }
                log.info("索引缓存已清除");
            }
        } catch (IOException e) {
            log.debug("清除索引缓存失败", e);
        }
    }

    private static String computeDirectoryFingerprint(Path dirPath) throws IOException {
        long maxSize = 0;
        long maxMtime = 0;
        long totalSize = 0;
        int fileCount = 0;
        try (var stream = Files.walk(dirPath)) {
            var it = stream.filter(Files::isRegularFile).iterator();
            while (it.hasNext()) {
                Path p = it.next();
                try {
                    long size = Files.size(p);
                    long mtime = Files.getLastModifiedTime(p).toMillis();
                    maxSize = Math.max(maxSize, size);
                    maxMtime = Math.max(maxMtime, mtime);
                    totalSize += size;
                    fileCount++;
                } catch (IOException ignored) {
                }
            }
        }
        String raw = dirPath.toAbsolutePath() + "|" + maxSize + "|" + maxMtime
                + "|" + totalSize + "|" + fileCount;
        CRC32 crc = new CRC32();
        crc.update(raw.getBytes(StandardCharsets.UTF_8));
        return String.format("%016x", crc.getValue());
    }

    private static Path cacheDirectory() {
        return AppPaths.cacheDir().resolve(CACHE_DIR_NAME);
    }

    private static Path cacheFilePath(String fingerprint) {
        return cacheDirectory().resolve(fingerprint + CACHE_FILE_SUFFIX);
    }

    /** 缓存的索引结构(JSON 序列化用) */
    static class CachedIndex {

        public List<CachedClassEntry> classes;

        public List<CachedResourceEntry> resources;

        WorkspaceIndex toWorkspaceIndex(Workspace workspace) {
            List<ClassIndexEntry> classEntries = new ArrayList<>(classes == null ? 0 : classes.size());
            if (classes != null) {
                for (CachedClassEntry ce : classes) {
                    classEntries.add(ce.toClassIndexEntry(workspace));
                }
            }
            List<ResourceIndexEntry> resourceEntries = new ArrayList<>(resources == null ? 0 : resources.size());
            if (resources != null) {
                for (CachedResourceEntry re : resources) {
                    resourceEntries.add(re.toResourceIndexEntry());
                }
            }
            return WorkspaceIndex.buildFromCache(classEntries, resourceEntries);
        }

        boolean hasClasses() {
            return classes != null && !classes.isEmpty();
        }

        int classCount() {
            return classes == null ? 0 : classes.size();
        }
    }

    /** 缓存的 class 条目 */
    static class CachedClassEntry {

        public String fullPath;

        public String internalName;

        public String simpleName;

        public String superName;

        public List<String> interfaces;

        public List<CachedMemberEntry> methods;

        public List<CachedMemberEntry> fields;

        static CachedClassEntry from(ClassIndexEntry entry) {
            CachedClassEntry ce = new CachedClassEntry();
            ce.fullPath = entry.fullPath();
            ce.internalName = entry.internalName();
            ce.simpleName = entry.simpleName();
            ce.superName = entry.superName();
            ce.interfaces = entry.interfaces();
            ce.methods = new ArrayList<>(entry.methods().size());
            for (MemberIndexEntry m : entry.methods()) {
                ce.methods.add(new CachedMemberEntry(m.name(), m.descriptor()));
            }
            ce.fields = new ArrayList<>(entry.fields().size());
            for (MemberIndexEntry f : entry.fields()) {
                ce.fields.add(new CachedMemberEntry(f.name(), f.descriptor()));
            }
            return ce;
        }

        ClassIndexEntry toClassIndexEntry(Workspace workspace) {
            List<MemberIndexEntry> methodList = new ArrayList<>(methods.size());
            for (CachedMemberEntry m : methods) {
                methodList.add(new MemberIndexEntry(fullPath, m.name, m.descriptor));
            }
            List<MemberIndexEntry> fieldList = new ArrayList<>(fields.size());
            for (CachedMemberEntry f : fields) {
                fieldList.add(new MemberIndexEntry(fullPath, f.name, f.descriptor));
            }
            return new ClassIndexEntry(fullPath, internalName, simpleName,
                    workspace == null ? null
                            : () -> WorkspaceByteReader.readClassBytes(
                            workspace, internalName, false),
                    methodList, fieldList, superName, interfaces);
        }
    }

    /** 缓存的成员条目 */
    record CachedMemberEntry(String name, String descriptor) {
    }

    static class CachedResourceEntry {

        public String fullPath;

        public byte[] bytes;

        static CachedResourceEntry from(ResourceIndexEntry entry) {
            CachedResourceEntry re = new CachedResourceEntry();
            re.fullPath = entry.fullPath();
            re.bytes = entry.bytes();
            return re;
        }

        ResourceIndexEntry toResourceIndexEntry() {
            return new ResourceIndexEntry(fullPath, bytes, true);
        }
    }
}
