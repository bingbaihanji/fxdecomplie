/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.util;

import com.bingbaihanji.classgraph.reflection.ReflectionUtils;
import com.bingbaihanji.classgraph.util.VersionFinder.OperatingSystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 文件工具类
 */
public final class FileUtils {
    /**
     * 文件缓冲区数组的最大大小比 {@link Integer#MAX_VALUE} 小 8 字节，
     * 因为某些虚拟机在数组中保留头字
     */
    public static final int MAX_BUFFER_SIZE = Integer.MAX_VALUE - 8;
    /** DirectByteBuffer.cleaner() 方法 */
    private static Method directByteBufferCleanerMethod;

    //    /** jdk.incubator.foreign.MemorySegment 类(JDK 14+) */
    //    private static Class<?> memorySegmentClass;
    //
    //    /** jdk.incubator.foreign.MemorySegment.ofByteBuffer 方法(JDK 14+) */
    //    private static Method memorySegmentOfByteBufferMethod;
    //
    //    /** jdk.incubator.foreign.MemorySegment.ofByteBuffer 方法(JDK 14+) */
    //    private static Method memorySegmentCloseMethod;
    /** Cleaner.clean() 方法 */
    private static Method cleanerCleanMethod;
    /** attachment() 方法 */
    private static Method attachmentMethod;
    /** Unsafe 对象 */
    private static Object theUnsafe;
    /** 类的静态字段是否已初始化 */
    private static AtomicBoolean initialized = new AtomicBoolean();
    /**
     * 当前目录路径(仅在首次访问此字段时读取当前目录一次，
     * 因此不会反映当前目录的后续更改)
     */
    private static String currDirPath;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造方法
     */
    private FileUtils() {
        // 不可构造
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取当前目录(仅在首次调用时查看当前目录，然后缓存此值供将来读取)
     *
     * @return 当前目录，以字符串形式返回
     */
    public static String currDirPath() {
        if (currDirPath == null) {
            // user.dir 应该是 JVM 启动时的当前目录，
            // 这也是类路径元素相对于其解析的位置
            Path path = null;
            final String currDirPathStr = System.getProperty("user.dir");
            if (currDirPathStr != null) {
                try {
                    path = Paths.get(currDirPathStr);
                } catch (final InvalidPathException e) {
                    // 穿透到备用方案
                }
            }
            if (path == null) {
                // user.dir 很可能总是被设置的但万一没有设置，就尝试读取
                // ClassGraph 首次被调用时的实际当前目录
                try {
                    path = Paths.get("");
                } catch (final InvalidPathException e) {
                    // 穿透到备用方案
                }
            }

            // 以 ClassGraph 中规范化所有其他路径的相同方式规范化当前目录，
            // 以保持一致性
            currDirPath = FastPathResolver.resolve(path == null ? "" : path.toString());
        }
        return currDirPath;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 针对"zip slip"漏洞对相对路径进行清理：如果 URL 中发现 ".." 则移除路径段，
     * 但不允许导航到路径层次根目录之上将每个 "!" 字符视为新的路径层次根目录
     * 同时移除 "." 和空路径段("//")
     *
     * @param path
     *            要清理的路径
     * @param removeInitialSlash
     *            如果为 true，则从返回路径的开头移除所有 '/' 字符
     * @param removeFinalSlash
     *            如果为 true，则从返回路径的末尾移除所有 '/' 字符
     * @return 清理后的路径
     */
    public static String sanitizeEntryPath(final String path, final boolean removeInitialSlash,
                                           final boolean removeFinalSlash) {
        if (path.isEmpty()) {
            return "";
        }

        // 查找所有 '/' 和 '!' 字符位置，这些字符将路径分割为多个段
        boolean foundSegmentToSanitize = false;
        final int pathLen = path.length();
        final char[] pathChars = new char[pathLen];
        path.getChars(0, pathLen, pathChars, 0);
        {
            int lastSepIdx = -1;
            char prevC = '\0';
            for (int i = 0, ii = pathLen + 1; i < ii; i++) {
                final char c = i == pathLen ? '\0' : pathChars[i];
                if (c == '/' || c == '!' || c == '\0') {
                    final int segmentLength = i - (lastSepIdx + 1);
                    if (
                        // 发现空段 "//" 或 "!!"
                            (segmentLength == 0 && prevC == c)
                                    // 发现段 "."
                                    || (segmentLength == 1 && pathChars[i - 1] == '.')
                                    // 发现段 ".."
                                    || (segmentLength == 2 && pathChars[i - 2] == '.' && pathChars[i - 1] == '.')) {
                        foundSegmentToSanitize = true;
                    }
                    lastSepIdx = i;
                }
                prevC = c;
            }
        }

        // 处理 ".."、"." 和空路径段(如果发现任何)
        final boolean pathHasInitialSlash = pathChars[0] == '/';
        final boolean pathHasInitialSlashSlash = pathHasInitialSlash && pathLen > 1 && pathChars[1] == '/';
        final StringBuilder pathSanitized = new StringBuilder(pathLen + 16);
        if (foundSegmentToSanitize) {
            // 在 "!" 段标记之间分别进行清理(".." 不应跨越前导的 "!" 应用)
            final List<List<CharSequence>> allSectionSegments = new ArrayList<>();
            List<CharSequence> currSectionSegments = new ArrayList<>();
            allSectionSegments.add(currSectionSegments);
            int lastSepIdx = -1;
            for (int i = 0; i < pathLen + 1; i++) {
                final char c = i == pathLen ? '\0' : pathChars[i];
                if (c == '/' || c == '!' || c == '\0') {
                    final int segmentStartIdx = lastSepIdx + 1;
                    final int segmentLen = i - segmentStartIdx;
                    if (segmentLen == 0 || (segmentLen == 1 && pathChars[segmentStartIdx] == '.')) {
                        // 忽略空段 "//" 或等幂段 "/./"
                    } else if (segmentLen == 2 && pathChars[segmentStartIdx] == '.'
                            && pathChars[segmentStartIdx + 1] == '.') {
                        // 遇到 ".." 时移除一个段，但不允许 ".." 超过层次结构的顶部
                        if (!currSectionSegments.isEmpty()) {
                            currSectionSegments.remove(currSectionSegments.size() - 1);
                        }
                    } else {
                        // 遇到普通路径段
                        currSectionSegments.add(path.subSequence(segmentStartIdx, segmentStartIdx + segmentLen));
                    }
                    if (c == '!' && !currSectionSegments.isEmpty()) {
                        // 开始新的段
                        currSectionSegments = new ArrayList<>();
                        allSectionSegments.add(currSectionSegments);
                    }
                    lastSepIdx = i;
                }
            }
            // 将段和子段转回路径字符串
            for (final List<CharSequence> sectionSegments : allSectionSegments) {
                if (!sectionSegments.isEmpty()) {
                    // 用 "!" 分隔段
                    if (pathSanitized.length() > 0) {
                        pathSanitized.append('!');
                    }
                    for (final CharSequence sectionSegment : sectionSegments) {
                        pathSanitized.append('/');
                        pathSanitized.append(sectionSegment);
                    }
                }
            }
            if (pathSanitized.length() == 0 && pathHasInitialSlash) {
                pathSanitized.append('/');
            }
        } else {
            pathSanitized.append(path);
        }

        // 旨在保留 UNC 路径开头的双斜杠(#736)
        // 例如 //server/file/path
        if (VersionFinder.OS == OperatingSystem.Windows && pathHasInitialSlashSlash) {
            pathSanitized.insert(0, '/');
        }

        int startIdx = 0;
        if (removeInitialSlash || !pathHasInitialSlash) {
            // 如果需要移除前导 "/"，或者原始路径中本不存在前导斜杠，
            // 则剥离前导 "/"(上面构建字符串的代码在每个段前都添加了 "/")
            // 注意："/" 总是在 "!" 之后添加，因为 "jar:" URL 期望如此
            while (startIdx < pathSanitized.length() && pathSanitized.charAt(startIdx) == '/') {
                startIdx++;
            }
        }
        if (removeFinalSlash) {
            while (pathSanitized.length() > 0 && pathSanitized.charAt(pathSanitized.length() - 1) == '/') {
                pathSanitized.setLength(pathSanitized.length() - 1);
            }
        }

        return pathSanitized.substring(startIdx);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查路径是否以 ".class" 扩展名结尾，忽略大小写
     *
     * @param path
     *            文件路径
     * @return 如果路径具有 ".class" 扩展名(忽略大小写)则返回 true
     */
    public static boolean isClassfile(final String path) {
        final int len = path.length();
        return len > 6 && path.regionMatches(true, len - 6, ".class", 0, 6);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查 {@link File} 是否存在且可读
     *
     * @param file
     *            一个 {@link File}
     * @return 如果文件存在且可读则返回 true
     */
    public static boolean canRead(final File file) {
        try {
            return file.canRead();
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * 检查 {@link Path} 是否存在且可读
     *
     * @param path
     *            一个 {@link Path}
     * @return 如果文件存在且可读则返回 true
     */
    public static boolean canRead(final Path path) {
        try {
            return canRead(path.toFile());
        } catch (final UnsupportedOperationException ignored) {
        }
        try {
            return Files.isReadable(path);
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * 检查 {@link File} 是否存在、是普通文件且可读
     *
     * @param file
     *            一个 {@link File}
     * @return 如果文件存在、是普通文件且可读则返回 true
     */
    public static boolean canReadAndIsFile(final File file) {
        try {
            if (!file.canRead()) {
                return false;
            }
        } catch (final SecurityException e) {
            return false;
        }
        return file.isFile();
    }

    /**
     * 检查 {@link Path} 是否存在、是普通文件且可读
     *
     * @param path
     *            一个 {@link Path}
     * @return 如果文件存在、是普通文件且可读则返回 true
     */
    public static boolean canReadAndIsFile(final Path path) {
        try {
            return canReadAndIsFile(path.toFile());
        } catch (final UnsupportedOperationException ignored) {
        }
        try {
            if (!Files.isReadable(path)) {
                return false;
            }
        } catch (final SecurityException e) {
            return false;
        }
        return Files.isRegularFile(path);
    }

    public static boolean isFile(final Path path) {
        try {
            return path.toFile().isFile();
        } catch (final UnsupportedOperationException e) {
            return Files.isRegularFile(path);
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * 检查 {@link File} 是否可读：如果不存在、不是普通文件或无法读取，
     * 则抛出 IOException
     *
     * @param file
     *            一个 {@link File}
     * @throws IOException
     *             如果文件不存在、不是普通文件或无法读取
     */
    public static void checkCanReadAndIsFile(final File file) throws IOException {
        try {
            if (!file.canRead()) {
                throw new FileNotFoundException("File does not exist or cannot be read: " + file);
            }
        } catch (final SecurityException e) {
            throw new FileNotFoundException("File " + file + " cannot be accessed: " + e);
        }
        if (!file.isFile()) {
            throw new IOException("Not a regular file: " + file);
        }
    }

    /**
     * 检查 {@link Path} 是否可读：如果不存在、不是普通文件或无法读取，
     * 则抛出 IOException
     *
     * @param path
     *            一个 {@link Path}
     * @throws IOException
     *             如果路径不存在、不是普通文件或无法读取
     */
    public static void checkCanReadAndIsFile(final Path path) throws IOException {
        try {
            checkCanReadAndIsFile(path.toFile());
            return;
        } catch (final UnsupportedOperationException ignored) {
        }
        try {
            if (!Files.isReadable(path)) {
                throw new FileNotFoundException("Path does not exist or cannot be read: " + path);
            }
        } catch (final SecurityException e) {
            throw new FileNotFoundException("Path " + path + " cannot be accessed: " + e);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("Not a regular file: " + path);
        }
    }

    /**
     * 检查 {@link File} 是否存在、是目录且可读
     *
     * @param file
     *            一个 {@link File}
     * @return 如果文件存在、是目录且可读则返回 true
     */
    public static boolean canReadAndIsDir(final File file) {
        try {
            if (!file.canRead()) {
                return false;
            }
        } catch (final SecurityException e) {
            return false;
        }
        return file.isDirectory();
    }

    /**
     * 检查 {@link Path} 是否存在、是目录且可读
     *
     * @param path
     *            一个 {@link Path}
     * @return 如果文件存在、是目录且可读则返回 true
     */
    public static boolean canReadAndIsDir(final Path path) {
        try {
            return canReadAndIsDir(path.toFile());
        } catch (final UnsupportedOperationException ignored) {
        }
        try {
            if (!Files.isReadable(path)) {
                return false;
            }
        } catch (final SecurityException e) {
            return false;
        }
        return Files.isDirectory(path);
    }

    public static boolean isDir(final Path path) {
        try {
            return path.toFile().isDirectory();
        } catch (final UnsupportedOperationException e) {
            return Files.isDirectory(path);
        } catch (final SecurityException e) {
            return false;
        }
    }

    /**
     * 检查 {@link File} 是否可读：如果不存在、不是目录或无法读取，
     * 则抛出 IOException
     *
     * @param file
     *            一个 {@link File}
     * @throws IOException
     *             如果文件不存在、不是目录或无法读取
     */
    public static void checkCanReadAndIsDir(final File file) throws IOException {
        try {
            if (!file.canRead()) {
                throw new FileNotFoundException("Directory does not exist or cannot be read: " + file);
            }
        } catch (final SecurityException e) {
            throw new FileNotFoundException("File " + file + " cannot be accessed: " + e);
        }
        if (!file.isDirectory()) {
            throw new IOException("Not a directory: " + file);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取父目录路径
     *
     * @param path
     *            路径
     * @param separator
     *            分隔符
     * @return 父目录路径
     */
    public static String getParentDirPath(final String path, final char separator) {
        final int lastSlashIdx = path.lastIndexOf(separator);
        if (lastSlashIdx <= 0) {
            return "";
        }
        return path.substring(0, lastSlashIdx);
    }

    /**
     * 获取父目录路径
     *
     * @param path
     *            路径
     * @return 父目录路径
     */
    public static String getParentDirPath(final String path) {
        return getParentDirPath(path, '/');
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取 clean() 方法、attachment() 方法和 theUnsafe 字段，在 doPrivileged 中调用
     */
    private static void lookupCleanMethodPrivileged() {
        if (VersionFinder.JAVA_MAJOR_VERSION < 9) {
            try {
                // 参见:
                // https://stackoverflow.com/a/19447758/3950982
                cleanerCleanMethod = Class.forName("sun.misc.Cleaner").getDeclaredMethod("clean");
                cleanerCleanMethod.setAccessible(true);
                final Class<?> directByteBufferClass = Class.forName("sun.nio.ch.DirectBuffer");
                directByteBufferCleanerMethod = directByteBufferClass.getDeclaredMethod("cleaner");
                attachmentMethod = directByteBufferClass.getMethod("attachment");
                attachmentMethod.setAccessible(true);
            } catch (final SecurityException e) {
                throw new RuntimeException(
                        "You need to grant classgraph RuntimePermission(\"accessClassInPackage.sun.misc\") "
                                + "and ReflectPermission(\"suppressAccessChecks\")",
                        e);
            } catch (final ReflectiveOperationException | LinkageError e) {
                // 忽略
            }
        } else if (VersionFinder.JAVA_MAJOR_VERSION < 24) {
            // JDK 24+ 报告："A terminally deprecated method in sun.misc.Unsafe has been called"
            // 如果使用了 Unsafe::invokeCleaner，除非使用直接内存映射而不是 FileChannel，
            // 否则我们实际上不需要 cleaner 方法(ClassGraph#enableMemoryMapping
            // 现在对 JDK 24+ 已禁用此功能)
            // 参见: https://github.com/classgraph/classgraph/issues/899
            try {
                Class<?> unsafeClass;
                try {
                    unsafeClass = Class.forName("sun.misc.Unsafe");
                } catch (final ReflectiveOperationException | LinkageError e) {
                    throw new RuntimeException("Could not get class sun.misc.Unsafe", e);
                }
                final Field theUnsafeField = unsafeClass.getDeclaredField("theUnsafe");
                theUnsafeField.setAccessible(true);
                theUnsafe = theUnsafeField.get(null);
                cleanerCleanMethod = unsafeClass.getMethod("invokeCleaner", ByteBuffer.class);
                cleanerCleanMethod.setAccessible(true);
            } catch (final SecurityException e) {
                throw new RuntimeException(
                        "You need to grant classgraph RuntimePermission(\"accessClassInPackage.sun.misc\") "
                                + "and ReflectPermission(\"suppressAccessChecks\")",
                        e);
            } catch (final ReflectiveOperationException | LinkageError ex) {
                // 忽略
            }
            //}
        }
    }

    /**
     * 关闭直接字节缓冲区(在 doPrivileged 中运行)
     *
     * @param byteBuffer
     *            字节缓冲区
     * @param log
     *            日志
     * @return 如果成功则返回 true
     */
    private static boolean closeDirectByteBufferPrivileged(final ByteBuffer byteBuffer, final LogNode log) {
        if (!byteBuffer.isDirect()) {
            // 无需操作
            return true;
        }
        try {
            if (VersionFinder.JAVA_MAJOR_VERSION < 9) {
                if (attachmentMethod == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, attachmentMethod == null");
                    }
                    return false;
                }
                // 确保不清理重复项和切片，因为这可能导致对同一缓冲区进行重复清理尝试，
                // 从而触发崩溃：
                // "A fatal error has been detected by the Java Runtime Environment: EXCEPTION_ACCESS_VIOLATION"
                // 参见: https://stackoverflow.com/a/31592947/3950982
                if (attachmentMethod.invoke(byteBuffer) != null) {
                    // 缓冲区是重复项或切片
                    return false;
                }
                // 调用 ((DirectBuffer) byteBuffer).cleaner().clean()
                if (directByteBufferCleanerMethod == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleanerMethod == null");
                    }
                    return false;
                }
                try {
                    directByteBufferCleanerMethod.setAccessible(true);
                } catch (final Exception e) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleanerMethod.setAccessible(true) failed");
                    }
                    return false;
                }
                final Object cleanerInstance = directByteBufferCleanerMethod.invoke(byteBuffer);
                if (cleanerInstance == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleaner == null");
                    }
                    return false;
                }
                if (cleanerCleanMethod == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleanMethod == null");
                    }
                    return false;
                }
                try {
                    cleanerCleanMethod.invoke(cleanerInstance);
                    return true;
                } catch (final Exception e) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleanMethod.invoke(cleaner) failed: " + e);
                    }
                    return false;
                }
                //    } else if (memorySegmentOfByteBufferMethod != null) {
                //        // JDK 14+
                //        final Object memorySegment = memorySegmentOfByteBufferMethod.invoke(null, byteBuffer);
                //        if (memorySegment == null) {
                //            if (log != null) {
                //                log.log("Got null MemorySegment, could not unmap ByteBuffer");
                //            }
                //            return false;
                //        }
                //        memorySegmentCloseMethod.invoke(memorySegment);
                //        return true;
            } else if (VersionFinder.JAVA_MAJOR_VERSION < 24) {
                if (theUnsafe == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, theUnsafe == null");
                    }
                    return false;
                }
                if (cleanerCleanMethod == null) {
                    if (log != null) {
                        log.log("Could not unmap ByteBuffer, cleanMethod == null");
                    }
                    return false;
                }
                try {
                    cleanerCleanMethod.invoke(theUnsafe, byteBuffer);
                    return true;
                } catch (final IllegalArgumentException e) {
                    // 缓冲区是重复项或切片
                    return false;
                }
            } else {
                // TODO: 在 JDK 24+ 上，使用 Arena -- 参见 FileSlice
                return false;
            }
        } catch (final ReflectiveOperationException | SecurityException e) {
            if (log != null) {
                log.log("Could not unmap ByteBuffer: " + e);
            }
            return false;
        }
    }

    /**
     * 关闭一个 {@code DirectByteBuffer} -- 特别是将取消映射 {@link MappedByteBuffer}
     *
     * @param byteBuffer
     *            要关闭/取消映射的 {@link ByteBuffer}
     * @param reflectionUtils
     *            反射工具
     * @param log
     *            日志
     * @return 如果 byteBuffer 已关闭/取消映射则返回 true
     */
    public static boolean closeDirectByteBuffer(final ByteBuffer byteBuffer, final ReflectionUtils reflectionUtils,
                                                final LogNode log) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            if (!initialized.get()) {
                try {
                    reflectionUtils.doPrivileged(new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            lookupCleanMethodPrivileged();
                            return null;
                        }
                    });
                } catch (final Throwable e) {
                    throw new RuntimeException("Cannot get buffer cleaner method", e);
                }
                initialized.set(true);
            }
            try {
                return reflectionUtils.doPrivileged(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        return closeDirectByteBufferPrivileged(byteBuffer, log);
                    }
                });
            } catch (final Throwable t) {
                return false;
            }
        } else {
            // 无需取消映射
            return false;
        }
    }

    public static FileAttributesGetter createCachedAttributesGetter() {
        final Map<Path, BasicFileAttributes> cache = new HashMap<>();
        return new FileAttributesGetter() {
            @Override
            public BasicFileAttributes get(final Path path) {
                BasicFileAttributes attributes = cache.get(path);
                if (attributes == null) {
                    attributes = readAttributes(path);
                    cache.put(path, attributes);
                }
                return attributes;
            }
        };
    }

    public static BasicFileAttributes readAttributes(final Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class);
        } catch (final IOException e) {
            return new BasicFileAttributes() {
                @Override
                public FileTime lastModifiedTime() {
                    return FileTime.fromMillis(path.toFile().lastModified());
                }

                @Override
                public FileTime lastAccessTime() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public FileTime creationTime() {
                    return FileTime.fromMillis(0);
                }

                @Override
                public boolean isRegularFile() {
                    return FileUtils.isFile(path);
                }

                @Override
                public boolean isDirectory() {
                    return FileUtils.isDir(path);
                }

                @Override
                public boolean isSymbolicLink() {
                    return false;
                }

                @Override
                public boolean isOther() {
                    return !isRegularFile() && !isDirectory();
                }

                @Override
                public long size() {
                    return path.toFile().length();
                }

                @Override
                public Object fileKey() {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }

    public interface FileAttributesGetter {
        BasicFileAttributes get(Path path);
    }
}
