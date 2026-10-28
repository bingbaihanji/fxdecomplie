package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.ResourceIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 工作区索引缓存 — 带版本号的 GZIP 二进制格式.
 *
 * <p>缓存键为「路径 + 大小 + mtime」，打开时不读取文件内容；写入使用临时文件原子替换，
 * 读取失败（损坏/版本不符）返回 null 触发重建。旧版 JSON 缓存不再读取。</p>
 *
 * @author bingbaihanji
 * @date 2026-10-28
 */
public final class WorkspaceIndexCache {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndexCache.class);

    private static final String CACHE_DIR_NAME = "index-cache";
    private static final String CACHE_FILE_SUFFIX = ".idx.gz";
    private static final String LEGACY_FILE_SUFFIX = ".idx.json";
    private static final int MAGIC = 0x46584944;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_ENTRY_COUNT = 5_000_000;
    private static final int MAX_RESOURCE_BYTES = 16 * 1024 * 1024;

    private WorkspaceIndexCache() {
        throw new AssertionError("utility class");
    }

    /**
     * 计算缓存键（路径 + 文件大小 + 最后修改时间），目录使用汇总信息.
     */
    public static String computeFingerprint(Path filePath) {
        try {
            if (Files.isDirectory(filePath)) {
                return computeDirectoryFingerprint(filePath);
            }
            CRC32 crc = new CRC32();
            String raw = filePath.toAbsolutePath() + "|" + Files.size(filePath)
                    + "|" + Files.getLastModifiedTime(filePath).toMillis();
            crc.update(raw.getBytes(StandardCharsets.UTF_8));
            return String.format("%016x", crc.getValue());
        } catch (IOException e) {
            log.debug("计算内容指纹失败: {}", filePath, e);
            return null;
        }
    }

    /**
     * 从缓存加载索引数据；不存在、损坏或版本不符时返回 null.
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
            CachedIndex cached = readFrom(cacheFile);
            if (cached.classes == null || cached.classes.isEmpty()) {
                return null;
            }
            log.info("索引缓存命中: {} ({} classes, {} resources)",
                    fingerprint, cached.classes.size(),
                    cached.resources == null ? 0 : cached.resources.size());
            return cached;
        } catch (Exception e) {
            log.debug("加载索引缓存失败: {}", fingerprint, e);
            try {
                Files.deleteIfExists(cacheFile);
            } catch (IOException ignored) {
            }
            return null;
        }
    }

    /**
     * 保存索引缓存（临时文件 + 原子替换）.
     */
    public static void save(String fingerprint, WorkspaceIndex index) {
        if (fingerprint == null || fingerprint.isBlank() || index == null) {
            return;
        }
        Path target = cacheFilePath(fingerprint);
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(cacheDirectory());
            writeTo(tmp, CachedIndex.from(index));
            moveAtomically(tmp, target);
            log.info("索引缓存已保存: {} ({} classes, {} resources)",
                    fingerprint, index.classes().size(), index.resources().size());
        } catch (Exception e) {
            log.debug("保存索引缓存失败: {}", fingerprint, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * 清除所有索引缓存（含旧版 JSON）.
     */
    public static void clearAll() {
        try {
            Path cacheDir = cacheDirectory();
            if (!Files.exists(cacheDir)) {
                return;
            }
            try (var stream = Files.list(cacheDir)) {
                stream.filter(WorkspaceIndexCache::isCacheFile).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
            }
            log.info("索引缓存已清除");
        } catch (IOException e) {
            log.debug("清除索引缓存失败", e);
        }
    }

    private static boolean isCacheFile(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(CACHE_FILE_SUFFIX)
                || name.endsWith(LEGACY_FILE_SUFFIX)
                || name.endsWith(CACHE_FILE_SUFFIX + ".tmp");
    }

    private static void writeTo(Path target, CachedIndex cached) throws IOException {
        List<CachedClassEntry> classes = cached.classes == null ? List.of() : cached.classes;
        List<CachedResourceEntry> resources =
                cached.resources == null ? List.of() : cached.resources;
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(
                new BufferedOutputStream(Files.newOutputStream(target))))) {
            out.writeInt(MAGIC);
            out.writeInt(FORMAT_VERSION);
            out.writeInt(classes.size());
            for (CachedClassEntry entry : classes) {
                writeClass(out, entry);
            }
            out.writeInt(resources.size());
            for (CachedResourceEntry resource : resources) {
                out.writeUTF(resource.fullPath == null ? "" : resource.fullPath);
                byte[] bytes = resource.bytes == null ? new byte[0] : resource.bytes;
                out.writeInt(bytes.length);
                out.write(bytes);
            }
        }
    }

    private static void writeClass(DataOutputStream out, CachedClassEntry entry)
            throws IOException {
        out.writeUTF(entry.fullPath == null ? "" : entry.fullPath);
        out.writeUTF(entry.internalName == null ? "" : entry.internalName);
        out.writeUTF(entry.simpleName == null ? "" : entry.simpleName);
        out.writeUTF(entry.superName == null ? "" : entry.superName);
        List<String> interfaces = entry.interfaces == null ? List.of() : entry.interfaces;
        out.writeInt(interfaces.size());
        for (String itf : interfaces) {
            out.writeUTF(itf);
        }
        List<CachedMemberEntry> methods = entry.methods == null ? List.of() : entry.methods;
        out.writeInt(methods.size());
        for (CachedMemberEntry method : methods) {
            out.writeUTF(method.name() == null ? "" : method.name());
            out.writeUTF(method.descriptor() == null ? "" : method.descriptor());
        }
        List<CachedMemberEntry> fields = entry.fields == null ? List.of() : entry.fields;
        out.writeInt(fields.size());
        for (CachedMemberEntry field : fields) {
            out.writeUTF(field.name() == null ? "" : field.name());
            out.writeUTF(field.descriptor() == null ? "" : field.descriptor());
        }
    }

    private static CachedIndex readFrom(Path file) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(
                new BufferedInputStream(Files.newInputStream(file))))) {
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("无效的索引缓存魔数: " + magic);
            }
            int version = in.readInt();
            if (version != FORMAT_VERSION) {
                throw new IOException("不支持的索引缓存版本: " + version);
            }
            int classCount = readCount(in, MAX_ENTRY_COUNT);
            List<CachedClassEntry> classes = new ArrayList<>(classCount);
            for (int i = 0; i < classCount; i++) {
                classes.add(readClass(in));
            }
            int resourceCount = readCount(in, MAX_ENTRY_COUNT);
            List<CachedResourceEntry> resources = new ArrayList<>(resourceCount);
            for (int i = 0; i < resourceCount; i++) {
                CachedResourceEntry resource = new CachedResourceEntry();
                resource.fullPath = in.readUTF();
                int length = readCount(in, MAX_RESOURCE_BYTES);
                byte[] bytes = in.readNBytes(length);
                if (bytes.length != length) {
                    throw new EOFException("索引缓存资源数据被截断");
                }
                resource.bytes = bytes;
                resources.add(resource);
            }
            CachedIndex cached = new CachedIndex();
            cached.classes = classes;
            cached.resources = resources;
            return cached;
        }
    }

    private static CachedClassEntry readClass(DataInputStream in) throws IOException {
        CachedClassEntry entry = new CachedClassEntry();
        entry.fullPath = in.readUTF();
        entry.internalName = in.readUTF();
        entry.simpleName = in.readUTF();
        String superName = in.readUTF();
        entry.superName = superName.isEmpty() ? null : superName;
        int interfaceCount = readCount(in, MAX_ENTRY_COUNT);
        List<String> interfaces = new ArrayList<>(interfaceCount);
        for (int i = 0; i < interfaceCount; i++) {
            interfaces.add(in.readUTF());
        }
        entry.interfaces = interfaces;
        int methodCount = readCount(in, MAX_ENTRY_COUNT);
        List<CachedMemberEntry> methods = new ArrayList<>(methodCount);
        for (int i = 0; i < methodCount; i++) {
            methods.add(new CachedMemberEntry(in.readUTF(), in.readUTF()));
        }
        entry.methods = methods;
        int fieldCount = readCount(in, MAX_ENTRY_COUNT);
        List<CachedMemberEntry> fields = new ArrayList<>(fieldCount);
        for (int i = 0; i < fieldCount; i++) {
            fields.add(new CachedMemberEntry(in.readUTF(), in.readUTF()));
        }
        entry.fields = fields;
        return entry;
    }

    private static int readCount(DataInputStream in, int max) throws IOException {
        int value = in.readInt();
        if (value < 0 || value > max) {
            throw new IOException("索引缓存长度非法: " + value);
        }
        return value;
    }

    private static void moveAtomically(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
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

    /** 缓存的索引结构 */
    static class CachedIndex {

        public List<CachedClassEntry> classes;

        public List<CachedResourceEntry> resources;

        static CachedIndex from(WorkspaceIndex index) {
            CachedIndex cached = new CachedIndex();
            List<ClassIndexEntry> classEntries = index.classes();
            cached.classes = new ArrayList<>(classEntries.size());
            for (ClassIndexEntry entry : classEntries) {
                cached.classes.add(CachedClassEntry.from(entry));
            }
            List<ResourceIndexEntry> resources = index.resources();
            cached.resources = new ArrayList<>(resources.size());
            for (ResourceIndexEntry resource : resources) {
                cached.resources.add(CachedResourceEntry.from(resource));
            }
            return cached;
        }

        WorkspaceIndex toWorkspaceIndex(Workspace workspace) {
            List<ClassIndexEntry> classEntries =
                    new ArrayList<>(classes == null ? 0 : classes.size());
            if (classes != null) {
                for (CachedClassEntry ce : classes) {
                    classEntries.add(ce.toClassIndexEntry(workspace));
                }
            }
            List<ResourceIndexEntry> resourceEntries =
                    new ArrayList<>(resources == null ? 0 : resources.size());
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
            for (MemberIndexEntry method : entry.methods()) {
                ce.methods.add(new CachedMemberEntry(method.name(), method.descriptor()));
            }
            ce.fields = new ArrayList<>(entry.fields().size());
            for (MemberIndexEntry field : entry.fields()) {
                ce.fields.add(new CachedMemberEntry(field.name(), field.descriptor()));
            }
            return ce;
        }

        ClassIndexEntry toClassIndexEntry(Workspace workspace) {
            List<MemberIndexEntry> methodList = new ArrayList<>(methods.size());
            for (CachedMemberEntry method : methods) {
                methodList.add(new MemberIndexEntry(fullPath, method.name, method.descriptor));
            }
            List<MemberIndexEntry> fieldList = new ArrayList<>(fields.size());
            for (CachedMemberEntry field : fields) {
                fieldList.add(new MemberIndexEntry(fullPath, field.name, field.descriptor));
            }
            FileTreeNode node = workspace == null ? null
                    : workspace.findNodeByPath(fullPath);
            FileTreeNode.ByteLoader loader;
            if (node != null) {
                loader = node::resolveBytes;
            } else if (workspace != null) {
                loader = () -> WorkspaceByteReader.readClassBytes(
                        workspace, internalName, false);
            } else {
                loader = null;
            }
            return new ClassIndexEntry(fullPath, internalName, simpleName,
                    loader, methodList, fieldList, superName, interfaces, node);
        }
    }

    /** 缓存的成员条目 */
    record CachedMemberEntry(String name, String descriptor) {
    }

    /** 缓存的资源条目 */
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
