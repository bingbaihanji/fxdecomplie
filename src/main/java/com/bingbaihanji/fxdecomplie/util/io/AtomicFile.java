package com.bingbaihanji.fxdecomplie.util.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * 用于对文件执行原子操作的辅助类：先写入一个新文件,写入成功完成后将其重命名为原始文件
 * <p>
 * 原子文件通过确保文件在重命名为原始文件之前已完全写入并同步到磁盘来保证文件完整性
 * 之前这是通过先将原始文件重命名为备份文件来实现的,但这种方法无法处理文件首次创建的情况
 * 本类也能正确处理旧实现创建的备份文件
 * <p>
 * 原子文件不提供任何文件锁语义当文件可能被多个线程或进程并发访问或修改时,请勿使用此类
 * 调用者负责在访问文件时确保适当的互斥不变量
 */
public final class AtomicFile {
    private static final Logger log = LoggerFactory.getLogger(AtomicFile.class);

    private final File baseFile;
    private final File newFile;
    private final File legacyBackupFile;

    /**
     * 为位于给定 File 路径的文件创建一个新的 AtomicFile
     * 写入时创建的新文件将在同一路径后附加 ".new"
     *
     * @param baseName 基础文件对象
     */
    public AtomicFile(File baseName) {
        baseFile = Objects.requireNonNull(baseName, "baseName");
        newFile = new File(baseFile.getPath() + ".new");
        legacyBackupFile = new File(baseFile.getPath() + ".bak");
    }

    /**
     * 为位于给定 File 路径的文件创建一个新的 AtomicFile,并指定提交标签(仅用于日志记录)
     * 写入时创建的新文件将在同一路径后附加 ".new"
     *
     * @param baseName  基础文件对象
     * @param commitTag 提交标签(用于日志,当前实现忽略该参数)
     * @deprecated commitTag 参数未被使用，请使用 {@link #AtomicFile(File)}
     */
    @Deprecated
    public AtomicFile(File baseName, String commitTag) {
        this(baseName);
    }

    /**
     * 同步文件输出流,确保数据写入磁盘
     *
     * @param fos 输出流
     * @return 同步成功返回 true,否则 false
     */
    private static boolean sync(FileOutputStream fos) {
        try {
            fos.getFD().sync();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 安静地关闭流,忽略任何异常
     *
     * @param closeable 可关闭对象(可为 null)
     */
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // 尽力清理,忽略关闭异常
            }
        }
    }

    /**
     * 重命名文件,处理目标为目录的特殊情况
     * <p>
     * 使用 {@link Files#move} 替代 {@link File#renameTo},后者在 Windows 上
     * 当目标文件已存在时不可靠
     *
     * @param source 源文件
     * @param target 目标文件
     */
    private static void rename(File source, File target) {
        // 如果目标是目录,则先删除(兼容旧错误用法)
        if (target.isDirectory()) {
            if (!target.delete()) {
                log.warn("Failed to delete file which is a directory " + target);
            }
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to rename {} to {}", source, target, e);
        }
    }

    /**
     * 返回基础文件的路径通常不应直接使用此方法,因为该路径下的数据可能无效
     *
     * @return 基础文件
     */
    public File getBaseFile() {
        return baseFile;
    }

    /**
     * 删除原子文件,包括基础文件和新文件
     */
    public void delete() {
        deleteIfExists(baseFile);
        deleteIfExists(newFile);
        deleteIfExists(legacyBackupFile);
    }

    /**
     * 开始对文件进行新的写入操作返回一个 FileOutputStream,您可以将新文件数据写入其中
     * 现有文件将被新数据替换您<em>不得</em>直接关闭给定的 FileOutputStream,
     * 而应调用 {@link #finishWrite(FileOutputStream)} 或 {@link #failWrite(FileOutputStream)}
     * <p>
     * 注意：如果另一个线程当前正在执行写入操作,此方法将简单地用当前线程正在写入的新文件
     * 替换该线程正在写入的内容 当另一个线程完成写入后,新的写入操作将不再安全(或会被丢失)
     * 您必须自行对 AtomicFile 的访问进行线程保护
     *
     * @return 用于写入新数据的文件输出流
     * @throws IOException 如果无法创建新文件
     */
    public FileOutputStream startWrite() throws IOException {
        // 处理旧版备份文件(如果存在)
        if (legacyBackupFile.exists()) {
            rename(legacyBackupFile, baseFile);
        }

        try {
            return new FileOutputStream(newFile);
        } catch (FileNotFoundException e) {
            File parent = newFile.getParentFile();
            if (parent == null) {
                throw new IOException("Failed to create new file " + newFile, e);
            }
            if (!parent.isDirectory()) {
                Files.createDirectories(parent.toPath());
            }
            // 尝试设置目录权限(仅对支持POSIX的平台有效,此处忽略异常)
            try {
                parent.setExecutable(true, false);
                parent.setReadable(true, false);
                parent.setWritable(true, false);
            } catch (SecurityException ignored) {
                // 忽略权限设置失败
            }
            try {
                return new FileOutputStream(newFile);
            } catch (FileNotFoundException e2) {
                throw new IOException("Failed to create new file " + newFile, e2);
            }
        }
    }

    /**
     * 当您已成功完成对 {@link #startWrite()} 返回的流的写入后调用此方法
     * 此方法将关闭、同步并提交新数据下一次尝试读取原子文件将返回新文件流
     *
     * @param str 要完成的输出流(不能为 null)
     */
    public void finishWrite(FileOutputStream str) {
        if (str == null) {
            return;
        }
        if (!sync(str)) {
            log.warn("Failed to sync file output stream");
        }
        try {
            str.close();
        } catch (IOException e) {
            log.warn("Failed to close file output stream", e);
        }
        rename(newFile, baseFile);
    }

    /**
     * 当写入 {@link #startWrite()} 返回的流失败时调用此方法
     * 此方法将关闭当前写入流并删除新文件
     *
     * @param str 要失败的输出流(可以为 null)
     */
    public void failWrite(FileOutputStream str) {
        if (str == null) {
            return;
        }
        try {
            str.close();
        } catch (IOException e) {
            log.warn("Failed to close file output stream", e);
        }
        deleteIfExists(newFile);
    }

    /**
     * @deprecated 此方法不安全,不推荐使用
     */
    @Deprecated
    public void truncate() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(baseFile)) {
            sync(fos);
        } catch (FileNotFoundException e) {
            throw new IOException("Couldn't append " + baseFile);
        }
    }

    /**
     * @deprecated 此方法不安全,不推荐使用
     */
    @Deprecated
    public FileOutputStream openAppend() throws IOException {
        try {
            return new FileOutputStream(baseFile, true);
        } catch (FileNotFoundException e) {
            throw new IOException("Couldn't append " + baseFile);
        }
    }

    /**
     * 打开原子文件以进行读取读取完成后应调用 FileInputStream 的 close() 方法
     * <p>
     * 您必须自行对 AtomicFile 的访问进行线程保护
     *
     * @return 用于读取的文件输入流
     * @throws FileNotFoundException 如果基础文件不存在
     */
    public FileInputStream openRead() throws FileNotFoundException {
        if (legacyBackupFile.exists()) {
            rename(legacyBackupFile, baseFile);
        }

        // 如果 .new 文件和基础文件同时存在,说明上一次写入操作被中断或未完成,
        // 此时删除 .new 文件,保证读取的是稳定的基础文件
        if (newFile.exists() && baseFile.exists()) {
            deleteIfExists(newFile);
        }
        return new FileInputStream(baseFile);
    }

    /**
     * 检查原始文件或旧版备份文件是否存在
     *
     * @return 如果原始文件或备份文件存在则返回 true
     */
    public boolean exists() {
        return baseFile.exists() || legacyBackupFile.exists();
    }

    /**
     * 获取原子文件的最后修改时间
     *
     * @return 自纪元以来的最后修改时间(毫秒)如果文件不存在或发生 I/O 错误,则返回 0
     */
    public long getLastModifiedTime() {
        if (legacyBackupFile.exists()) {
            return legacyBackupFile.lastModified();
        }
        return baseFile.lastModified();
    }

    /**
     * 便捷方法,等同于调用 {@link #openRead()} 并读取文件全部内容到字节数组
     *
     * @return 包含文件全部内容的字节数组
     * @throws IOException 如果读取失败
     */
    public byte[] readFully() throws IOException {
        try (FileInputStream stream = openRead()) {
            return stream.readAllBytes();
        }
    }

    /**
     * 使用给定的写入器(Consumer)执行写入操作此方法会自动处理开始、完成或失败提交
     *
     * @param writeContent 接收 FileOutputStream 并执行写入的 Consumer
     */
    public void write(Consumer<FileOutputStream> writeContent) {
        Objects.requireNonNull(writeContent, "writeContent");
        FileOutputStream out = null;
        try {
            out = startWrite();
            writeContent.accept(out);
            finishWrite(out);
            out = null;
        } catch (RuntimeException e) {
            failWrite(out);
            out = null;
            throw e;
        } catch (IOException e) {
            failWrite(out);
            out = null;
            throw new UncheckedIOException(e);
        } catch (Error e) {
            failWrite(out);
            out = null;
            throw e;
        } finally {
            closeQuietly(out);
        }
    }

    private static void deleteIfExists(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            log.warn("Failed to delete file {}", file, e);
        }
    }

    @Override
    public String toString() {
        return "AtomicFile[" + baseFile + "]";
    }
}
