package com.bingbaihanji.fxdecomplie.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件树节点数据模型表示文件树中的单个节点,可以是包 类文件 Java源文件等
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeNode {

    private static final Logger log = LoggerFactory.getLogger(FileTreeNode.class);
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".svg", ".ico", ".webp");
    /** 节点显示名称 */
    private final String name;
    /** 完整内部路径(如 "com/example/Main.class") */
    private final String fullPath;
    /** 节点类型 */
    private final NodeTypeEnum nodeType;
    /** 可选资源清理回调,例如关闭归档句柄通过 AtomicReference 保证单次执行 */
    private final AtomicReference<Runnable> cleanupRef = new AtomicReference<>();
    /** 缓存的文件字节,按需加载后保留,避免重复读取同一打开文件使用 SoftReference 允许 GC 在内存压力下回收 */
    private volatile SoftReference<byte[]> cachedBytesRef;
    /** 懒加载字节来源,用于 JAR/ZIP/目录条目 */
    private volatile ByteLoader byteLoader;
    /** 条目原始大小,未知时为 -1 */
    private volatile long size = -1L;

    /**
     * 构造文件树节点
     *
     * @param name     节点显示名称
     * @param fullPath 完整内部路径
     * @param nodeType 节点类型
     */
    public FileTreeNode(String name, String fullPath, NodeTypeEnum nodeType) {
        this.name = name;
        this.fullPath = fullPath;
        this.nodeType = nodeType;
    }

    /** @return 节点显示名称 */
    public String getName() {
        return name;
    }

    /** @return 完整内部路径 */
    public String getFullPath() {
        return fullPath;
    }

    /** @return 节点类型 */
    public NodeTypeEnum getNodeType() {
        return nodeType;
    }

    /** @return 缓存的字节码,可能为 null(SoftReference 可能已被 GC 回收) */
    public byte[] getCachedBytes() {
        SoftReference<byte[]> ref = cachedBytesRef;
        return ref != null ? ref.get() : null;
    }

    /** @param cachedBytes 缓存的字节码 */
    public synchronized void setCachedBytes(byte[] cachedBytes) {
        this.cachedBytesRef = cachedBytes == null ? null : new SoftReference<>(cachedBytes);
        this.size = cachedBytes == null ? -1L : cachedBytes.length;
    }

    /** @param byteLoader 懒加载字节来源 */
    public void setByteLoader(ByteLoader byteLoader) {
        this.byteLoader = byteLoader;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    /**
     * 设置资源清理回调每个节点仅支持一个清理动作,设置后可通过 {@link #close()} 触发
     *
     * @param cleanup 节点关闭时执行的清理动作(例如关闭共享 ZipFile)
     */
    public void setCleanup(Runnable cleanup) {
        cleanupRef.set(cleanup);
    }

    /**
     * 执行清理回调并原子性置空,保证并发调用时仅执行一次
     * 使用 {@link AtomicReference#getAndSet} 原子交换保证线程安全
     */
    public void close() {
        this.byteLoader = null;
        Runnable action = cleanupRef.getAndSet(null);
        if (action != null) {
            action.run();
        }
    }

    /** @return 当前节点是否存在可读取的字节来源 */
    public boolean hasByteSource() {
        return getCachedBytes() != null || byteLoader != null;
    }

    /**
     * 读取字节但不写入节点缓存适合索引构建等批处理场景,避免预热阶段占用过多内存
     */
    public byte[] readBytes() throws IOException {
        byte[] cached = getCachedBytes();
        if (cached != null) {
            return cached;
        }
        ByteLoader loader = this.byteLoader;
        return loader == null ? null : loader.load();
    }

    /**
     * 读取并缓存字节适合打开单个文件 导出当前节点等用户显式操作
     */
    public synchronized byte[] resolveBytes() throws IOException {
        byte[] cached = getCachedBytes();
        if (cached != null) {
            return cached;
        }
        ByteLoader loader = this.byteLoader;
        if (loader != null) {
            try {
                byte[] bytes = loader.load();
                if (bytes != null) {
                    setCachedBytes(bytes);
                }
                return bytes;
            } catch (IOException e) {
                log.debug("加载字节失败 [{}]: {}", fullPath, e.getMessage());
                throw e;
            } catch (Exception e) {
                log.warn("加载字节时发生未知异常 [{}]", fullPath, e);
            }
        }
        return null;
    }

    /** @return 是否为 class 文件 */
    public boolean isClassFile() {
        return nodeType == NodeTypeEnum.CLASS_FILE;
    }

    /** @return 是否为可查看的文本文件(资源文件或 Java 源文件) */
    public boolean isTextFile() {
        return nodeType == NodeTypeEnum.RESOURCE || nodeType == NodeTypeEnum.JAVA_FILE;
    }

    /** @return 是否为二进制资源文件(DLL/SO/EXE 等,可用 Hex 查看) */
    public boolean isBinaryFile() {
        return nodeType == NodeTypeEnum.BINARY;
    }

    /** @return 是否为图片文件 */
    public boolean isImageFile() {
        String name = getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        return dot >= 0 && IMAGE_EXTENSIONS.contains(name.substring(dot));
    }

    @Override
    public String toString() {
        return "FileTreeNode{name='" + name + "', type=" + nodeType + ", path='" + fullPath + "'}";
    }

    public enum NodeTypeEnum {
        /** 包节点(中间层级) */
        PACKAGE,
        /** .class 文件 */
        CLASS_FILE,
        /** .java 源文件 */
        JAVA_FILE,
        /** 其他资源文件 */
        RESOURCE,
        /** 二进制文件 */
        BINARY
    }

    /** 字节懒加载接口,用于延迟读取归档条目或文件系统中的字节内容 */
    @FunctionalInterface
    public interface ByteLoader {
        /** @return 加载到的字节数组 */
        byte[] load() throws IOException;
    }
}
