package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files;

import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files.IJadxFilesGetter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 文件操作工具类。
 * <p>
 * 提供文件/目录的创建、删除、复制、读写等常用操作，
 * 以及临时文件管理、流处理、路径转换、哈希计算等实用方法。
 */
public class FileUtils {
    /** 读取缓冲区大小：8KB */
    public static final int READ_BUFFER_SIZE = 8 * 1024;
    /** jadx 实例临时目录前缀 */
    public static final String JADX_TMP_INSTANCE_PREFIX = "jadx-instance-";
    /** jadx 临时文件前缀 */
    public static final String JADX_TMP_PREFIX = "jadx-tmp-";
    private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);
    /** 文件名最大长度限制 */
    private static final int MAX_FILENAME_LENGTH = 128;
    /** 唯一标识符最大长度 */
    private static final int MAX_UNIQUE_ID_LENGTH = 3;
    private static final Object MKDIR_SYNC = new Object();
    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ZIP_FILE_MAGIC = {0x50, 0x4B, 0x03, 0x04};
    private static Path tempRootDir = createTempRootDir();

    private FileUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 更新临时根目录。
     * <p>
     * 在指定的新根目录下创建 jadx 实例临时目录，并更新全局临时根目录引用。
     *
     * @param newTempRootDir 新的临时根目录路径
     * @return 新创建的实例临时目录路径
     */
    public static synchronized Path updateTempRootDir(Path newTempRootDir) {
        try {
            makeDirs(newTempRootDir);
            Path dir = Files.createTempDirectory(newTempRootDir, JADX_TMP_INSTANCE_PREFIX);
            tempRootDir = dir;
            dir.toFile().deleteOnExit();
            return dir;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to update temp root directory", e);
        }
    }

    private static Path createTempRootDir() {
        try {
            Path dir = Files.createTempDirectory(JADX_TMP_INSTANCE_PREFIX);
            dir.toFile().deleteOnExit();
            return dir;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to create temp root directory", e);
        }
    }

    /**
     * 列出指定目录下的所有文件（不递归）。
     *
     * @param dir 目录路径
     * @return 文件路径列表
     */
    public static List<Path> listFiles(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.collect(Collectors.toList());
        } catch (IOException e) {
            throw new JadxRuntimeException("Failed to list files in directory: " + dir, e);
        }
    }

    /**
     * 列出指定目录下满足过滤条件的所有文件（不递归）。
     *
     * @param dir    目录路径
     * @param filter 文件过滤条件
     * @return 符合条件的文件路径列表
     */
    public static List<Path> listFiles(Path dir, Predicate<? super Path> filter) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(filter).collect(Collectors.toList());
        } catch (IOException e) {
            throw new JadxRuntimeException("Failed to list files in directory: " + dir, e);
        }
    }

    /**
     * 展开路径列表：将其中的目录递归展开为其下的所有普通文件，普通文件保持不变。
     *
     * @param paths 待展开的路径列表（可包含目录和文件）
     * @return 展开后的文件路径列表
     */
    public static List<Path> expandDirs(List<Path> paths) {
        List<Path> files = new ArrayList<>(paths.size());
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                expandDir(path, files);
            } else {
                files.add(path);
            }
        }
        return files;
    }

    private static void expandDir(Path dir, List<Path> files) {
        try (Stream<Path> walk = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        } catch (Exception e) {
            LOG.error("Failed to list files in directory: {}", dir, e);
        }
    }

    /**
     * 将源文件作为一个条目写入 jar 输出流。
     *
     * @param jar       jar 输出流
     * @param source    源文件
     * @param entryName jar 中的条目名称
     * @throws IOException 读写发生错误时抛出
     */
    public static void addFileToJar(JarOutputStream jar, File source, String entryName) throws IOException {
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source))) {
            JarEntry entry = new JarEntry(entryName);
            entry.setTime(source.lastModified());
            jar.putNextEntry(entry);

            copyStream(in, jar);
            jar.closeEntry();
        }
    }

    /**
     * 为指定文件创建其所在的父目录（若不存在）。
     *
     * @param path 文件路径
     */
    public static void makeDirsForFile(Path path) {
        if (path != null) {
            makeDirs(path.toAbsolutePath().getParent().toFile());
        }
    }

    /**
     * 为指定文件创建其所在的父目录（若不存在）。
     *
     * @param file 文件
     */
    public static void makeDirsForFile(File file) {
        if (file != null) {
            makeDirs(file.getParentFile());
        }
    }

    /**
     * 创建目录（含所有必需的父目录）。线程安全。
     *
     * @param dir 目录（可为 null，为 null 时不做任何操作）
     * @throws JadxRuntimeException 无法创建目录时抛出
     */
    public static void makeDirs(@Nullable File dir) {
        if (dir != null) {
            synchronized (MKDIR_SYNC) {
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    throw new JadxRuntimeException("Can't create directory " + dir);
                }
            }
        }
    }

    /**
     * 创建目录（含所有必需的父目录）。
     *
     * @param dir 目录（可为 null，为 null 时不做任何操作）
     */
    public static void makeDirs(@Nullable Path dir) {
        if (dir != null) {
            makeDirs(dir.toFile());
        }
    }

    /**
     * 删除指定文件（若存在）。
     *
     * @param filePath 文件路径
     * @throws IOException 删除发生错误时抛出
     */
    public static void deleteFileIfExists(Path filePath) throws IOException {
        Files.deleteIfExists(filePath);
    }

    /**
     * 递归删除目录及其全部内容。
     *
     * @param dir 待删除的目录
     * @return 恒为 true
     */
    public static boolean deleteDir(File dir) {
        deleteDir(dir.toPath());
        return true;
    }

    /**
     * 递归删除目录及其全部内容。
     *
     * @param dir 待删除的目录
     */
    public static void deleteDir(Path dir) {
        deleteDir(dir, false);
    }

    /**
     * 若目录存在则递归删除，删除失败仅记录日志而不抛出异常。
     *
     * @param dir 待删除的目录
     */
    public static void deleteDirIfExists(Path dir) {
        if (Files.exists(dir)) {
            try {
                deleteDir(dir);
            } catch (Exception e) {
                LOG.error("Failed to delete dir: {}", dir.toAbsolutePath(), e);
            }
        }
    }

    private static void deleteDir(Path dir, boolean keepRootDir) {
        try {
            List<Path> files = new ArrayList<>();
            List<Path> directories = new ArrayList<>();
            Files.walkFileTree(dir, Collections.emptySet(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                @Override
                public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                    files.add(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path directory, IOException exc) {
                    directories.add(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
            // 并行删除文件
            if (!files.isEmpty()) {
                files.parallelStream().forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        LOG.warn("Failed to delete file {}", path.toAbsolutePath(), e);
                    }
                });
            }
            // 所有文件删除完毕后，移除空目录
            if (keepRootDir) {
                // 根目录总是位于最后
                ListUtils.removeLast(directories);
            }
            for (Path directory : directories) {
                try {
                    Files.delete(directory);
                } catch (IOException e) {
                    LOG.warn("Failed to delete directory {}", directory.toAbsolutePath(), e);
                }
            }
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to delete directory " + dir, e);
        }
    }

    /**
     * 清空临时根目录下的全部内容（保留根目录本身）。
     */
    public static void clearTempRootDir() {
        if (Files.isDirectory(tempRootDir)) {
            clearDir(tempRootDir);
        }
    }

    /**
     * 清空指定目录下的全部内容（保留目录本身）。
     *
     * @param clearDir 待清空的目录
     * @throws JadxRuntimeException 清空失败时抛出
     */
    public static void clearDir(Path clearDir) {
        try {
            deleteDir(clearDir, true);
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to clear directory " + clearDir, e);
        }
    }

    /**
     * 已废弃。
     * 请迁移到使用 jadx args 中的 {@link IJadxFilesGetter} 来获取临时目录。
     *
     * @param prefix 临时目录名前缀
     * @return 创建的临时目录路径
     */
    @Deprecated
    public static Path createTempDir(String prefix) {
        try {
            Path dir = Files.createTempDirectory(tempRootDir, prefix);
            dir.toFile().deleteOnExit();
            return dir;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to create temp directory with suffix: " + prefix, e);
        }
    }

    /**
     * 已废弃。
     * 请迁移到使用 jadx args 中的 {@link IJadxFilesGetter} 来获取临时目录。
     *
     * @param suffix 临时文件名后缀
     * @return 创建的临时文件路径
     */
    @Deprecated
    public static Path createTempFile(String suffix) {
        try {
            Path path = Files.createTempFile(tempRootDir, JADX_TMP_PREFIX, suffix);
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to create temp file with suffix: " + suffix, e);
        }
    }

    /**
     * 已废弃。
     * 建议使用 jadx args 中的 {@link IJadxFilesGetter} 来获取临时目录。
     *
     * @param suffix 临时文件名后缀
     * @return 创建的临时文件路径（退出时不自动删除）
     */
    @Deprecated
    public static Path createTempFileNoDelete(String suffix) {
        try {
            return Files.createTempFile(Files.createTempDirectory("jadx-persist"), "jadx-", suffix);
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to create temp file with suffix: " + suffix, e);
        }
    }

    /**
     * 已废弃。
     * 请迁移到使用 jadx args 中的 {@link IJadxFilesGetter} 来获取临时目录。
     *
     * @param fileName 文件名（不添加前缀）
     * @return 创建的临时文件路径
     */
    @Deprecated
    public static Path createTempFileNonPrefixed(String fileName) {
        try {
            Path path = Files.createFile(tempRootDir.resolve(fileName));
            path.toFile().deleteOnExit();
            return path;
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to create non-prefixed temp file: " + fileName, e);
        }
    }

    /**
     * 将输入流的全部内容复制到输出流。
     *
     * @param input  输入流
     * @param output 输出流
     * @throws IOException 读写发生错误时抛出
     */
    public static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        while (true) {
            int count = input.read(buffer);
            if (count == -1) {
                break;
            }
            output.write(buffer, 0, count);
        }
    }

    /**
     * 将输入流的全部内容读取为字节数组。
     *
     * @param input 输入流
     * @return 字节数组
     * @throws IOException 读取发生错误时抛出
     */
    public static byte[] streamToByteArray(InputStream input) throws IOException {
        return input.readAllBytes();
    }

    /**
     * 将输入流的全部内容按 UTF-8 编码读取为字符串。
     *
     * @param input 输入流
     * @return 字符串内容
     * @throws IOException 读取发生错误时抛出
     */
    public static String streamToString(InputStream input) throws IOException {
        return new String(streamToByteArray(input), StandardCharsets.UTF_8);
    }

    /**
     * 安全关闭 {@link Closeable} 资源，关闭异常仅记录日志。
     *
     * @param c 待关闭的资源（可为 null）
     */
    public static void close(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException e) {
            LOG.error("Close exception for {}", c, e);
        }
    }

    /**
     * 将字符串按 UTF-8 编码写入文件（自动创建父目录，覆盖已有内容）。
     *
     * @param file 目标文件
     * @param data 字符串内容
     * @throws IOException 写入发生错误时抛出
     */
    public static void writeFile(Path file, String data) throws IOException {
        FileUtils.makeDirsForFile(file);
        Files.writeString(file, data, StandardCharsets.UTF_8,
                StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 将字节数组写入文件（自动创建父目录，覆盖已有内容）。
     *
     * @param file 目标文件
     * @param data 字节内容
     * @throws IOException 写入发生错误时抛出
     */
    public static void writeFile(Path file, byte[] data) throws IOException {
        FileUtils.makeDirsForFile(file);
        Files.write(file, data, StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    /**
     * 将输入流内容写入文件（自动创建父目录，覆盖已有文件）。
     *
     * @param file 目标文件
     * @param is   输入流
     * @throws IOException 写入发生错误时抛出
     */
    public static void writeFile(Path file, InputStream is) throws IOException {
        FileUtils.makeDirsForFile(file);
        Files.copy(is, file, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 读取文本文件的全部内容为字符串。
     *
     * @param textFile 文本文件
     * @return 文件内容
     * @throws IOException 读取发生错误时抛出
     */
    public static String readFile(Path textFile) throws IOException {
        return Files.readString(textFile);
    }

    /**
     * 重命名（移动）文件，若目标已存在则覆盖。失败时记录日志并返回 false。
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @return 成功返回 true，失败返回 false
     */
    public static boolean renameFile(Path sourcePath, Path targetPath) {
        try {
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (NoSuchFileException e) {
            LOG.error("File to rename not found {}", sourcePath, e);
        } catch (FileAlreadyExistsException e) {
            LOG.error("File with that name already exists {}", targetPath, e);
        } catch (IOException e) {
            LOG.error("Error renaming file {}", e.getMessage(), e);
        }
        return false;
    }

    /**
     * 准备用于保存的文件：截断过长的文件名，并创建其父目录。
     *
     * @param file 原始文件
     * @return 处理后可安全保存的文件
     */
    @NotNull
    public static File prepareFile(File file) {
        File saveFile = cutFileName(file);
        makeDirsForFile(saveFile);
        return saveFile;
    }

    /**
     * 截断超过最大长度限制的文件名（追加哈希唯一标识以避免冲突），保留扩展名。
     *
     * @param file 原始文件
     * @return 文件名长度合法的文件（未超长时返回原文件）
     */
    public static File cutFileName(File file) {
        String name = file.getName();
        if (name.length() <= MAX_FILENAME_LENGTH) {
            return file;
        }

        String uniqueID = String.valueOf(name.hashCode());
        if (uniqueID.length() > MAX_UNIQUE_ID_LENGTH) {
            uniqueID = uniqueID.substring(0, MAX_UNIQUE_ID_LENGTH);
        }
        int dotIndex = name.indexOf('.');
        int lengthOfSuffix = name.length() - dotIndex;
        int cutAt = MAX_FILENAME_LENGTH - lengthOfSuffix - uniqueID.length() - 1;
        if (cutAt <= 0) {
            name = name.substring(0, MAX_FILENAME_LENGTH - 1);
        } else {
            name = name.substring(0, cutAt) + uniqueID + name.substring(dotIndex);
        }
        return new File(file.getParentFile(), name);
    }

    /**
     * 将字节数组转换为十六进制字符串（小写）。
     *
     * @param bytes 字节数组
     * @return 十六进制字符串，输入为空时返回空字符串
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        byte[] hexChars = new byte[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    /**
     * 将单个字节值转换为补零的两位十六进制字符串。
     *
     * @param value 字节值（仅取低 8 位）
     * @return 两位十六进制字符串
     */
    public static String byteToHex(int value) {
        int v = value & 0xFF;
        byte[] hexChars = new byte[]{HEX_ARRAY[v >>> 4], HEX_ARRAY[v & 0x0F]};
        return new String(hexChars, StandardCharsets.US_ASCII);
    }

    /**
     * 将 int 值转换为补零的 8 位十六进制字符串。
     *
     * @param value 整数值
     * @return 8 位十六进制字符串
     */
    public static String intToHex(int value) {
        byte[] hexChars = new byte[8];
        int v = value;
        for (int i = 7; i >= 0; i--) {
            hexChars[i] = HEX_ARRAY[v & 0x0F];
            v >>>= 4;
        }
        return new String(hexChars, StandardCharsets.US_ASCII);
    }

    /**
     * 通过文件魔数（PK\03\04）判断文件是否为 ZIP 格式。
     *
     * @param file 待检测的文件
     * @return 是 ZIP 文件返回 true，否则或读取失败返回 false
     */
    public static boolean isZipFile(File file) {
        try (InputStream is = new FileInputStream(file)) {
            int len = ZIP_FILE_MAGIC.length;
            byte[] headers = new byte[len];
            int read = is.read(headers);
            return read == len && Arrays.equals(headers, ZIP_FILE_MAGIC);
        } catch (Exception e) {
            LOG.error("Failed to read zip file: {}", file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * 获取路径对应文件的基础名（去除扩展名）。
     *
     * @param file 文件路径
     * @return 不含扩展名的文件名
     */
    public static String getPathBaseName(Path file) {
        String fileName = file.getFileName().toString();
        int extEndIndex = fileName.lastIndexOf('.');
        if (extEndIndex == -1) {
            return fileName;
        }
        return fileName.substring(0, extEndIndex);
    }

    /**
     * 判断路径的文件名是否以指定扩展名结尾（忽略大小写）。
     *
     * @param path      文件路径
     * @param extension 扩展名
     * @return 匹配返回 true
     */
    public static boolean hasExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        return fileName.toLowerCase().endsWith(extension);
    }

    /**
     * 将路径字符串转换为 {@link File}。
     *
     * @param path 路径字符串（可为 null）
     * @return 对应的 File，输入为 null 时返回 null
     */
    public static File toFile(String path) {
        if (path == null) {
            return null;
        }
        return new File(path);
    }

    /**
     * 将 File 列表转换为 Path 列表。
     *
     * @param files File 列表
     * @return Path 列表
     */
    public static List<Path> toPaths(List<File> files) {
        return files.stream().map(File::toPath).collect(Collectors.toList());
    }

    /**
     * 将 File 数组转换为 Path 列表。
     *
     * @param files File 数组
     * @return Path 列表
     */
    public static List<Path> toPaths(File[] files) {
        return Stream.of(files).map(File::toPath).collect(Collectors.toList());
    }

    /**
     * 将 File 数组转换为 Path 列表，并对每个路径字符串去除首尾空白。
     *
     * @param files File 数组
     * @return Path 列表
     */
    public static List<Path> toPathsWithTrim(File[] files) {
        return Stream.of(files).map(FileUtils::toPathWithTrim).collect(Collectors.toList());
    }

    /**
     * 将 File 转换为 Path，并对路径字符串去除首尾空白。
     *
     * @param file 文件
     * @return 去除空白后的 Path
     */
    public static Path toPathWithTrim(File file) {
        return toPathWithTrim(file.getPath());
    }

    /**
     * 将路径字符串去除首尾空白后转换为 Path。
     *
     * @param file 路径字符串
     * @return 去除空白后的 Path
     */
    public static Path toPathWithTrim(String file) {
        return Path.of(file.trim());
    }

    /**
     * 将文件名字符串列表转换为 Path 列表。
     *
     * @param fileNames 文件名列表
     * @return Path 列表
     */
    public static List<Path> fileNamesToPaths(List<String> fileNames) {
        return fileNames.stream().map(Paths::get).collect(Collectors.toList());
    }

    /**
     * 将 Path 列表转换为 File 列表。
     *
     * @param paths Path 列表
     * @return File 列表
     */
    public static List<File> toFiles(List<Path> paths) {
        return paths.stream().map(Path::toFile).collect(Collectors.toList());
    }

    /**
     * 计算字符串（UTF-8 编码）的 MD5 哈希值。
     *
     * @param str 输入字符串
     * @return 十六进制表示的 MD5 值
     */
    public static String md5Sum(String str) {
        return md5Sum(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 计算字节数组的 MD5 哈希值。
     *
     * @param data 输入字节数组
     * @return 十六进制表示的 MD5 值
     * @throws JadxRuntimeException 计算失败时抛出
     */
    public static String md5Sum(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to build hash", e);
        }
    }

    /**
     * 基于输入文件的修改时间戳计算哈希值。
     * <p>
     * 递归展开目录后按序对各文件的最后修改时间进行摘要，可用于判断输入是否发生变化。
     *
     * @param inputPaths 输入路径列表（可包含目录）
     * @return 输入文件时间戳的 MD5 哈希值
     * @throws JadxRuntimeException 计算失败时抛出
     */
    public static String buildInputsHash(List<Path> inputPaths) {
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             DataOutputStream data = new DataOutputStream(bout)) {
            List<Path> inputFiles = FileUtils.expandDirs(inputPaths);
            Collections.sort(inputFiles);
            data.write(inputPaths.size());
            data.write(inputFiles.size());
            for (Path inputFile : inputFiles) {
                FileTime modifiedTime = Files.getLastModifiedTime(inputFile);
                data.writeLong(modifiedTime.toMillis());
            }
            return FileUtils.md5Sum(bout.toByteArray());
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to build hash for inputs", e);
        }
    }
}
