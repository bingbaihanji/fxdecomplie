package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipFile;

/**
 * 单文件字节读取工具
 *
 * <p>用户打开当前 class 时不应排队等待完整索引读取共享 JarFile归档输入使用独立
 * ZipFile 读取目标 entry,保证交互路径优先</p>
 *
 * @author bingbaihanji
 * @date 2026-07-07
 */
public final class WorkspaceByteReader {

    private WorkspaceByteReader() {
        throw new AssertionError("utility class");
    }

    /**
     * 读取节点的原始字节,优先使用节点缓存,其次按工作区类型(归档/目录)解析
     *
     * @param cacheNode 读取成功后是否回存到节点缓存
     */
    public static byte[] readNodeBytes(Workspace workspace, FileTreeNode node, boolean cacheNode)
            throws IOException {
        if (node == null) {
            return null;
        }
        byte[] cached = node.getCachedBytes();
        if (cached != null) {
            return cached;
        }

        if (workspace != null && workspace.isArchive()) {
            byte[] bytes = readArchiveEntry(workspace.getSourceFile(), node.getFullPath());
            if (cacheNode && bytes != null) {
                node.setCachedBytes(bytes);
            }
            return bytes;
        }

        byte[] bytes = node.resolveBytes();
        if (cacheNode && bytes != null) {
            node.setCachedBytes(bytes);
        }
        return bytes;
    }

    /**
     * 按内部名查找类的字节码,顺序尝试：节点树查找、归档 entry、磁盘文件
     *
     * @param cacheNode 读取成功后是否回存到节点缓存
     */
    static byte[] readClassBytes(Workspace workspace, String internalName, boolean cacheNode)
            throws IOException {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        String path = normalizeClassPath(internalName);
        FileTreeNode node = workspace.findNodeByPath(path);
        if (node != null) {
            return readNodeBytes(workspace, node, cacheNode);
        }

        if (workspace.isArchive()) {
            return readArchiveEntry(workspace.getSourceFile(), path);
        }

        File source = workspace.getSourceFile();
        if (source.isDirectory()) {
            File file = new File(source, path);
            return file.isFile() ? Files.readAllBytes(file.toPath()) : null;
        }
        if (source.isFile() && source.getName().equals(new File(path).getName())) {
            return Files.readAllBytes(source.toPath());
        }
        return null;
    }

    /** 从 JAR/ZIP 归档中读取指定 entry 的字节,使用独立 ZipFile 以避免线程竞争 */
    private static byte[] readArchiveEntry(File archive, String entryPath) throws IOException {
        if (archive == null || entryPath == null || entryPath.isBlank()) {
            return null;
        }
        String normalized = entryPath.replace('\\', '/');
        try (ZipFile zip = new ZipFile(archive)) {
            var entry = zip.getEntry(normalized);
            if (entry == null || entry.isDirectory()) {
                return null;
            }
            try (var in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    /** 将内部名统一为以 .class 结尾的标准路径格式 */
    private static String normalizeClassPath(String internalName) {
        String normalized = internalName.replace('\\', '/');
        return normalized.endsWith(".class") ? normalized : normalized + ".class";
    }
}
