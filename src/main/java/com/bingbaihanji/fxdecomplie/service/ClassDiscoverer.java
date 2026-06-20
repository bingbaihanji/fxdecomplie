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
import java.util.List;
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

    private static final Logger logger = LoggerFactory.getLogger(ClassDiscoverer.class);

    /** 资源文件扩展名匹配模式 */
    private static final Pattern RESOURCE_PATTERN = Pattern.compile(
            ".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql)$");

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
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return discoverJar(file);
        } else if (file.isDirectory()) {
            return discoverDirectory(file);
        } else if (name.endsWith(".class")) {
            return discoverClassFile(file);
        } else {
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
                if (entry.isDirectory()) continue;
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
                        normalizedSize(entry.getSize()), cleanup));
            }
            success = true;
            return entries;
        } finally {
            if (!success || archive.referenceCount() == 0) {
                archive.close();
            }
        }
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
                    logger.debug("Failed to read file size: {}", p, ex);
                }
                entries.add(new ClassEntry(displayName, relativePath, type, null, loader,
                        size, null));
            });
        }
        return entries;
    }

    /** 从单个 .class 文件创建条目 */
    private static List<ClassEntry> discoverClassFile(File file) throws IOException {
        return List.of(new ClassEntry(file.getName(), file.getName(),
                FileTreeNode.NodeTypeEnum.CLASS_FILE, null,
                () -> Files.readAllBytes(file.toPath()), file.length(), null));
    }

    /** 根据文件扩展名判断节点类型 */
    private static FileTreeNode.NodeTypeEnum guessType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".class")) return FileTreeNode.NodeTypeEnum.CLASS_FILE;
        if (lower.endsWith(".java")) return FileTreeNode.NodeTypeEnum.JAVA_FILE;
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
            Runnable cleanup
    ) {
        public ClassEntry(String name, String fullPath, FileTreeNode.NodeTypeEnum nodeType,
                          byte[] bytes, FileTreeNode.ByteLoader byteLoader) {
            this(name, fullPath, nodeType, bytes, byteLoader,
                    bytes == null ? -1L : bytes.length, null);
        }

        public ClassEntry(String name, String fullPath,
                           FileTreeNode.NodeTypeEnum nodeType, byte[] bytes) {
            this(name, fullPath, nodeType, bytes, null,
                    bytes == null ? -1L : bytes.length, null);
        }
    }

    private static final class SharedJarArchive implements AutoCloseable {
        private final JarFile jar;
        private final AtomicInteger references = new AtomicInteger();
        private final AtomicBoolean closed = new AtomicBoolean();

        private SharedJarArchive(File file) throws IOException {
            this.jar = new JarFile(file);
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
                    logger.debug("Shared archive released more times than retained");
                }
                close();
            }
        }

        private int referenceCount() {
            return references.get();
        }

        private byte[] read(String entryName) throws IOException {
            synchronized (jar) {
                JarEntry entry = jar.getJarEntry(entryName);
                if (entry == null || entry.isDirectory()) {
                    throw new IOException("Archive entry not found: " + entryName);
                }
                try (var in = jar.getInputStream(entry)) {
                    return in.readAllBytes();
                }
            }
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                jar.close();
            } catch (IOException e) {
                logger.debug("Failed to close archive", e);
            }
        }
    }
}
