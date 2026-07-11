package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 精简的 I/O 工具方法，供 jadx 内部使用
 * <p>
 * 原 FileUtils 中可替换为 ByteUtils/JDK API 的方法已移除，
 * 此处仅保留目录管理、临时文件、文件名处理等 jadx 专属逻辑
 */
public final class IoUtils {
    /** 读取缓冲区大小：8KB */
    public static final int READ_BUFFER_SIZE = 8 * 1024;
    /** jadx 实例临时目录前缀 */
    public static final String JADX_TMP_INSTANCE_PREFIX = "jadx-instance-";
    /** jadx 临时文件前缀 */
    public static final String JADX_TMP_PREFIX = "jadx-tmp-";

    private static final Logger LOG = LoggerFactory.getLogger(IoUtils.class);
    private static final int MAX_FILENAME_LENGTH = 128;
    private static final int MAX_UNIQUE_ID_LENGTH = 3;
    private static final Object MKDIR_SYNC = new Object();
    private static Path tempRootDir = createTempRootDir();

    private IoUtils() {
    }

    // -- temp root dir ---

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

    // -- expand dirs ---

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

    // -- make dirs ---

    public static void makeDirsForFile(Path path) {
        if (path != null) {
            makeDirs(path.toAbsolutePath().getParent().toFile());
        }
    }

    public static void makeDirsForFile(File file) {
        if (file != null) {
            makeDirs(file.getParentFile());
        }
    }

    public static void makeDirs(@Nullable File dir) {
        if (dir != null) {
            synchronized (MKDIR_SYNC) {
                if (!dir.mkdirs() && !dir.isDirectory()) {
                    throw new JadxRuntimeException("Can't create directory " + dir);
                }
            }
        }
    }

    public static void makeDirs(@Nullable Path dir) {
        if (dir != null) {
            makeDirs(dir.toFile());
        }
    }

    // -- delete ---

    public static void deleteDirIfExists(Path dir) {
        if (Files.exists(dir)) {
            try {
                deleteDir(dir);
            } catch (Exception e) {
                LOG.error("Failed to delete dir: {}", dir.toAbsolutePath(), e);
            }
        }
    }

    private static void deleteDir(Path dir) {
        deleteDir(dir, false);
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
            if (!files.isEmpty()) {
                files.parallelStream().forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        LOG.warn("Failed to delete file {}", path.toAbsolutePath(), e);
                    }
                });
            }
            if (keepRootDir) {
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

    public static void clearTempRootDir() {
        if (Files.isDirectory(tempRootDir)) {
            clearDir(tempRootDir);
        }
    }

    private static void clearDir(Path clearDir) {
        try {
            deleteDir(clearDir, true);
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to clear directory " + clearDir, e);
        }
    }

    // -- file name ---

    @NotNull
    public static File prepareFile(File file) {
        File saveFile = cutFileName(file);
        makeDirsForFile(saveFile);
        return saveFile;
    }

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

    public static String getPathBaseName(Path file) {
        String fileName = file.getFileName().toString();
        int extEndIndex = fileName.lastIndexOf('.');
        if (extEndIndex == -1) {
            return fileName;
        }
        return fileName.substring(0, extEndIndex);
    }
}
