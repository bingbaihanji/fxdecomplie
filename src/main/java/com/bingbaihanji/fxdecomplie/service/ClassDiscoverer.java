package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 类文件发现器支持 JAR/ZIP/目录/单个 .class 文件
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassDiscoverer {

    private static final Logger log = LoggerFactory.getLogger(ClassDiscoverer.class);

    /** 资源文件扩展名匹配模式 */
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
            ".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql"
                    + "|png|jpg|jpeg|gif|bmp|svg|ico|webp)$");

    private ClassDiscoverer() {
        throw new AssertionError("utility class");
    }

    /**
     * 发现文件中的所有类条目自动判断输入类型(JAR/ZIP/目录/单文件)
     *
     * @param file 输入文件或目录
     * @return 发现的条目列表
     * @throws IOException 读取文件失败时抛出
     */
    public static List<ClassEntry> discover(File file) throws IOException {
        String name = file.getName().toLowerCase();
        log.debug("发现文件条目: {} ({} bytes)", file.getAbsolutePath(), file.length());
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            List<ClassEntry> entries = discoverJar(file);
            log.info("JAR/ZIP 发现 {} 个条目: {}", entries.size(), file.getName());
            return entries;
        } else if (file.isDirectory()) {
            List<ClassEntry> entries = discoverDirectory(file);
            log.info("目录发现 {} 个条目: {}", entries.size(), file.getName());
            return entries;
        } else if (name.endsWith(".class")) {
            List<ClassEntry> entries = discoverClassFile(file);
            log.debug("发现单个 class 文件: {}", file.getName());
            return entries;
        } else {
            log.debug("非标准文件类型,按资源处理: {}", file.getName());
            return List.of(new ClassEntry(file.getName(), file.getName(),
                    guessType(file.getName()), null));
        }
    }

    /** 从 JAR/ZIP 文件发现条目 */
    private static List<ClassEntry> discoverJar(File file) throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        SharedJarArchive archive = new SharedJarArchive(file);
        boolean success = false;
        try {
            Enumeration<JarEntry> e = archive.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                FileTreeNode.NodeTypeEnum type = guessType(path);
                FileTreeNode.ByteLoader loader = null;
                Runnable cleanup = null;
                if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE
                        || type == FileTreeNode.NodeTypeEnum.RESOURCE
                        || type == FileTreeNode.NodeTypeEnum.JAVA_FILE) {
                    archive.retain();
                    loader = () -> archive.read(path);
                    cleanup = archive::release;
                }
                String displayName = path.substring(path.lastIndexOf('/') + 1);
                entries.add(new ClassEntry(displayName, path, type, null, loader,
                        normalizedSize(entry.getSize()), cleanup, 1));
                if (isArchivePath(path)) {
                    discoverNestedArchive(archive, List.of(path), entries, 1);
                }
            }
            success = true;
            return entries;
        } finally {
            if (!success || archive.referenceCount() == 0) {
                archive.close();
            }
        }
    }

    private static void discoverNestedArchive(SharedJarArchive archive,
                                              List<String> archiveChain,
                                              List<ClassEntry> entries,
                                              int depth) {
        if (depth > 8) {
            log.warn("嵌套归档层级过深,已跳过: {}", sourcePath(archiveChain));
            return;
        }
        String parentSource = sourcePath(archiveChain);
        SharedJarArchive nestedArchive;
        try {
            nestedArchive = archive.openNestedArchive(archiveChain);
        } catch (IOException e) {
            log.warn("读取嵌套归档失败: {}", parentSource, e);
            return;
        }

        try {
            Enumeration<JarEntry> nestedEntries = nestedArchive.entries();
            while (nestedEntries.hasMoreElements()) {
                JarEntry entry = nestedEntries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                String fullPath = parentSource + ':' + entryName;
                FileTreeNode.NodeTypeEnum type = guessType(entryName);
                FileTreeNode.ByteLoader loader = null;
                Runnable cleanup = null;
                if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE
                        || type == FileTreeNode.NodeTypeEnum.RESOURCE
                        || type == FileTreeNode.NodeTypeEnum.JAVA_FILE) {
                    List<String> classChain = append(archiveChain, entryName);
                    archive.retain();
                    loader = () -> archive.readNested(classChain);
                    cleanup = archive::release;
                }
                String displayName = entryName.substring(entryName.lastIndexOf('/') + 1);
                entries.add(new ClassEntry(displayName, fullPath, type, null, loader,
                        normalizedSize(entry.getSize()), cleanup, 2));
                if (isArchivePath(entryName)) {
                    discoverNestedArchive(archive, append(archiveChain, entryName), entries, depth + 1);
                }
            }
        } catch (RuntimeException e) {
            log.warn("解析嵌套归档失败: {}", parentSource, e);
        }
    }

    private static boolean isArchivePath(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".jar") || lower.endsWith(".zip");
    }

    private static String sourcePath(List<String> chain) {
        return String.join(":", chain);
    }

    private static List<String> append(List<String> chain, String entryName) {
        List<String> next = new ArrayList<>(chain.size() + 1);
        next.addAll(chain);
        next.add(entryName);
        return List.copyOf(next);
    }

    private static long normalizedSize(long size) {
        return size < 0 ? -1L : size;
    }

    /** 从目录递归发现条目 */
    private static List<ClassEntry> discoverDirectory(File dir) throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        Path root = dir.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> {
                try {
                    return Files.isRegularFile(p) && !Files.isSymbolicLink(p);
                } catch (Exception e) {
                    log.debug("File walk filter 跳过异常文件: {}", p, e);
                    return false;
                }
            }).forEach(p -> {
                String relativePath = root.relativize(p).toString().replace('\\', '/');
                String displayName = p.getFileName().toString();
                FileTreeNode.NodeTypeEnum type = guessType(displayName);
                FileTreeNode.ByteLoader loader = null;
                if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE
                        || type == FileTreeNode.NodeTypeEnum.RESOURCE
                        || type == FileTreeNode.NodeTypeEnum.JAVA_FILE) {
                    loader = () -> Files.readAllBytes(p);
                }
                long size = -1L;
                try {
                    size = Files.size(p);
                } catch (IOException ex) {
                    log.debug("读取文件大小失败: {}", p, ex);
                }
                entries.add(new ClassEntry(displayName, relativePath, type, null, loader,
                        size, null, 1));
            });
        }
        return entries;
    }

    /** 从单个 .class 文件创建条目 */
    private static List<ClassEntry> discoverClassFile(File file) throws IOException {
        return List.of(new ClassEntry(file.getName(), file.getName(),
                FileTreeNode.NodeTypeEnum.CLASS_FILE, null,
                () -> Files.readAllBytes(file.toPath()), file.length(), null, 1));
    }

    /** 根据文件扩展名判断节点类型 */
    private static FileTreeNode.NodeTypeEnum guessType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".class")) {
            return FileTreeNode.NodeTypeEnum.CLASS_FILE;
        }
        if (lower.endsWith(".java")) {
            return FileTreeNode.NodeTypeEnum.JAVA_FILE;
        }
        if (RESOURCE_PATTERN.matcher(lower).matches()) {
            return FileTreeNode.NodeTypeEnum.RESOURCE;
        }
        return FileTreeNode.NodeTypeEnum.BINARY;
    }

    /**
     * 发现的文件条目
     *
     * @param name     显示名称(如 "Main.class")
     * @param fullPath 完整内部路径(如 "com/example/Main.class")
     * @param nodeType 节点类型
     * @param bytes    .class 文件的字节码(非 class 文件为 null)
     */
    public record ClassEntry(
            String name,
            String fullPath,
            FileTreeNode.NodeTypeEnum nodeType,
            byte[] bytes,
            FileTreeNode.ByteLoader byteLoader,
            long size,
            Runnable cleanup,
            int phase
    ) {

        public ClassEntry(String name, String fullPath, FileTreeNode.NodeTypeEnum nodeType,
                          byte[] bytes, FileTreeNode.ByteLoader byteLoader) {
            this(name, fullPath, nodeType, bytes, byteLoader,
                    bytes == null ? -1L : bytes.length, null, 1);
        }

        public ClassEntry(String name, String fullPath,
                          FileTreeNode.NodeTypeEnum nodeType, byte[] bytes) {
            this(name, fullPath, nodeType, bytes, null,
                    bytes == null ? -1L : bytes.length, null, 1);
        }

        /** 设置发现阶段(1=根归档, 2=嵌套归档) */
        public ClassEntry withPhase(int phase) {
            return new ClassEntry(name, fullPath, nodeType, bytes, byteLoader, size, cleanup, phase);
        }
    }

    private static final class SharedJarArchive implements AutoCloseable {

        private final JarFile jar;

        /** 以归档链为键缓存已展开并打开的嵌套归档,避免每个 class 重复解压和线性扫描 */
        private final Map<String, NestedArchive> nestedArchives = new HashMap<>();

        private final AtomicInteger references = new AtomicInteger();

        private final AtomicBoolean closed = new AtomicBoolean();

        private final Path temporaryFile;

        private SharedJarArchive(File file) throws IOException {
            this(file, null);
        }

        private SharedJarArchive(File file, Path temporaryFile) throws IOException {
            this.jar = new JarFile(file);
            this.temporaryFile = temporaryFile;
        }

        private static byte[] readEntry(JarFile source, String entryName) throws IOException {
            JarEntry entry = source.getJarEntry(entryName);
            if (entry == null || entry.isDirectory()) {
                throw new IOException("归档条目未找到: " + entryName);
            }
            try (var in = source.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }

        private Enumeration<JarEntry> entries() {
            return jar.entries();
        }

        private void retain() {
            references.incrementAndGet();
        }

        private void release() {
            int remaining = references.decrementAndGet();
            if (remaining <= 0) {
                if (remaining < 0) {
                    log.debug("共享归档释放次数超过保留次数");
                }
                close();
            }
        }

        private int referenceCount() {
            return references.get();
        }

        private byte[] read(String entryName) throws IOException {
            synchronized (jar) {
                return readEntry(jar, entryName);
            }
        }

        private byte[] readNested(List<String> chain) throws IOException {
            if (chain == null || chain.isEmpty()) {
                return null;
            }
            if (chain.size() == 1) {
                return read(chain.getFirst());
            }
            int last = chain.size() - 1;
            SharedJarArchive parent = openNestedArchive(chain.subList(0, last));
            return parent.read(chain.get(last));
        }

        /**
         * 打开嵌套归档并缓存其 JarFile.
         *
         * <p>Spring Boot fatjar 中通常有数百个依赖 JAR.旧实现对每个 class
         * 都从外层字节数组起点用 JarInputStream 找目标 entry,导致相同依赖 JAR
         * 被反复解压和扫描.这里每层归档只展开一次,后续使用 JarFile 中央目录读取.</p>
         */
        private synchronized SharedJarArchive openNestedArchive(List<String> chain)
                throws IOException {
            if (chain == null || chain.isEmpty()) {
                return this;
            }
            String key = sourcePath(chain);
            NestedArchive cached = nestedArchives.get(key);
            if (cached != null) {
                return cached.archive();
            }

            byte[] bytes;
            if (chain.size() == 1) {
                bytes = read(chain.getFirst());
            } else {
                NestedArchive parentEntry = nestedArchives.get(
                        sourcePath(chain.subList(0, chain.size() - 1)));
                SharedJarArchive parent = parentEntry == null ? null : parentEntry.archive();
                if (parent == null) {
                    parent = openNestedArchive(
                            chain.subList(0, chain.size() - 1));
                }
                bytes = parent.read(chain.getLast());
            }

            Path tempFile = Files.createTempFile("fxdecomplie-nested-", ".jar");
            try {
                Files.write(tempFile, bytes);
                SharedJarArchive nested = new SharedJarArchive(tempFile.toFile(), tempFile);
                nestedArchives.put(key, new NestedArchive(nested));
                return nested;
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (NestedArchive nested : nestedArchives.values()) {
                nested.archive().close();
            }
            nestedArchives.clear();
            try {
                jar.close();
            } catch (IOException e) {
                log.debug("关闭归档失败", e);
            }
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException e) {
                    temporaryFile.toFile().deleteOnExit();
                    log.debug("删除嵌套归档临时文件失败: {}", temporaryFile, e);
                }
            }
        }

        private record NestedArchive(SharedJarArchive archive) {
        }
    }
}
