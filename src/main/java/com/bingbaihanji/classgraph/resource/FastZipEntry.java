 
package com.bingbaihanji.classgraph.resource;

import com.bingbaihanji.classgraph.util.VersionFinder;

import java.io.IOException;
import java.util.Calendar;
import java.util.TimeZone;

/** {@link LogicalZipFile} 中的一个 ZIP 条目 */
public class FastZipEntry implements Comparable<FastZipEntry> {
    /** ZIP 条目的路径 */
    public final String entryName;
    /** ZIP 条目的压缩后大小(以字节为单位) */
    public final long compressedSize;
    /** ZIP 条目的未压缩大小(以字节为单位) */
    public final long uncompressedSize;
    /** 此资源的文件属性，如果未知则为 0 */
    public final int fileAttributes;
    /**
     * 未版本化的条目名称(即去掉 "META_INF/versions/{versionInt}/" 前缀的 entryName)
     */
    public final String entryNameUnversioned;
    /** 父逻辑 ZIP 文件 */
    final LogicalZipFile parentLogicalZipFile;
    /** 如果 ZIP 条目已压缩(deflated)则为 true；如果已存储(stored)则为 false */
    final boolean isDeflated;
    /**
     * 版本代码(&gt;= 9)，对于基础层或非版本化 JAR(兼容 JDK 7 或 8)则为 8
     */
    final int version;
    /** 条目本地头部的偏移量，相对于父逻辑 ZIP 文件 */
    private final long locHeaderPos;
    /** MSDOS 格式的最后修改时间(当 {@link FastZipEntry#lastModifiedTimeMillis} 为 0L 时使用) */
    private final int lastModifiedTimeMSDOS;
    /** MSDOS 格式的最后修改日期(当 {@link FastZipEntry#lastModifiedTimeMillis} 为 0L 时使用) */
    private final int lastModifiedDateMSDOS;
    /** 自纪元以来的最后修改毫秒数，如果未知则为 0L */
    private volatile long lastModifiedTimeMillis;
    /** 用于 ZIP 条目原始数据(可以是已存储或已压缩的)的 {@link Slice} */
    private volatile Slice slice;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param parentLogicalZipFile
     *            包含此条目的父逻辑 ZIP 文件
     * @param locHeaderPos
     *            此条目在父逻辑 ZIP 文件中的 LOC 头部偏移量
     * @param entryName
     *            条目的名称
     * @param isDeflated
     *            如果条目已压缩则为 true；如果条目已存储则为 false
     * @param compressedSize
     *            条目的压缩后大小
     * @param uncompressedSize
     *            条目的未压缩大小
     * @param lastModifiedTimeMillis
     *            自纪元以来的最后修改日期/时间(毫秒)，如果未知则为 0L(此时将提供 MSDOS 时间和日期字段)
     * @param lastModifiedTimeMSDOS
     *            MSDOS 格式的最后修改时间(当 lastModifiedMillis 为 0L 时使用)
     * @param lastModifiedDateMSDOS
     *            MSDOS 格式的最后修改日期(当 lastModifiedMillis 为 0L 时使用)
     * @param fileAttributes
     *            ZIP 条目中的 POSIX 文件属性位
     */
    FastZipEntry(final LogicalZipFile parentLogicalZipFile, final long locHeaderPos, final String entryName,
                 final boolean isDeflated, final long compressedSize, final long uncompressedSize,
                 final long lastModifiedTimeMillis, final int lastModifiedTimeMSDOS, final int lastModifiedDateMSDOS,
                 final int fileAttributes, final boolean enableMultiReleaseVersions) {
        this.parentLogicalZipFile = parentLogicalZipFile;
        this.locHeaderPos = locHeaderPos;
        this.entryName = entryName;
        this.isDeflated = isDeflated;
        this.compressedSize = compressedSize;
        this.uncompressedSize = !isDeflated && uncompressedSize < 0 ? compressedSize : uncompressedSize;
        this.lastModifiedTimeMillis = lastModifiedTimeMillis;
        this.lastModifiedTimeMSDOS = lastModifiedTimeMSDOS;
        this.lastModifiedDateMSDOS = lastModifiedDateMSDOS;
        this.fileAttributes = fileAttributes;

        // 获取多版本 JAR 的版本号，并去除版本前缀
        int entryVersion = 8;
        String entryNameWithoutVersionPrefix = entryName;
        if (entryName.startsWith(LogicalZipFile.MULTI_RELEASE_PATH_PREFIX)
                && entryName.length() > LogicalZipFile.MULTI_RELEASE_PATH_PREFIX.length() + 1) {
            // 这是一个多版本 JAR 路径
            final int nextSlashIdx = entryName.indexOf('/', LogicalZipFile.MULTI_RELEASE_PATH_PREFIX.length());
            if (nextSlashIdx > 0) {
                // 获取版本号之后的路径，即去除 "META-INF/versions/{versionInt}/" 前缀
                final String versionStr = entryName.substring(LogicalZipFile.MULTI_RELEASE_PATH_PREFIX.length(),
                        nextSlashIdx);
                // 对于多版本 JAR，版本号必须是 >= 9 的整数
                // Integer.parseInt() 较慢，因此这里使用自定义实现(对于大型 classpath，
                // 此方法会被调用很多次，令人惊讶的是 Integer.parseInt() 曾是一个瓶颈)
                int versionInt = 0;
                if (versionStr.length() < 6 && !versionStr.isEmpty()) {
                    for (int i = 0; i < versionStr.length(); i++) {
                        final char c = versionStr.charAt(i);
                        if (c < '0' || c > '9') {
                            versionInt = 0;
                            break;
                        }
                        if (versionInt == 0) {
                            versionInt = c - '0';
                        } else {
                            versionInt = versionInt * 10 + c - '0';
                        }
                    }
                }
                if (versionInt != 0) {
                    entryVersion = versionInt;
                }
                // 对于超出范围的版本号或无效路径，将版本设置为 8
                if (entryVersion < 9 || entryVersion > VersionFinder.JAVA_MAJOR_VERSION) {
                    entryVersion = 8;
                }
                if (!enableMultiReleaseVersions && entryVersion > 8) {
                    // 去除版本路径前缀
                    entryNameWithoutVersionPrefix = entryName.substring(nextSlashIdx + 1);
                    // 对于 META-INF/versions/{versionInt}/META-INF/*，不去除版本前缀：
                    // "其意图是 META-INF 目录不能被版本化"
                    // http://mail.openjdk.java.net/pipermail/jigsaw-dev/2018-October/013954.html
                    if (entryNameWithoutVersionPrefix.startsWith(LogicalZipFile.META_INF_PATH_PREFIX)) {
                        entryVersion = 8;
                        entryNameWithoutVersionPrefix = entryName;
                    }
                }
            }
        }
        this.version = entryVersion;
        this.entryNameUnversioned = entryNameWithoutVersionPrefix;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 延迟获取 ZIP 条目切片 -- 延迟到需要读取 ZIP 条目数据时才执行，以避免在读取中央目录时为每个条目
     * 随机寻址 ZIP 文件
     *
     * @return 条目起始偏移量在物理 ZIP 文件中的偏移量
     * @throws IOException
     *             如果发生 I/O 异常
     */
    public Slice getSlice() throws IOException {
        Slice result = slice;
        if (result == null) {
            synchronized (this) {
                result = slice;
                if (result == null) {
                    final RandomAccessReader randomAccessReader = parentLogicalZipFile.slice.randomAccessReader();

                    // 检查头部魔数
                    if (randomAccessReader.readInt(locHeaderPos) != 0x04034b50) {
                        throw new IOException("Zip entry has bad LOC header: " + entryName);
                    }
                    final long dataStartPos = locHeaderPos + 30 + randomAccessReader.readShort(locHeaderPos + 26)
                            + randomAccessReader.readShort(locHeaderPos + 28);
                    if (dataStartPos > parentLogicalZipFile.slice.sliceLength) {
                        throw new IOException("Unexpected EOF when trying to read zip entry data: " + entryName);
                    }

                    // 创建一个新的 Slice，仅包装 ZIP 条目的数据，并标记其是否已压缩
                    result = parentLogicalZipFile.slice.slice(dataStartPos, compressedSize, isDeflated,
                            uncompressedSize);
                    slice = result;
                }
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此 ZIP 条目的路径，使用 "!/" 作为父逻辑 ZIP 文件与条目名称之间的分隔符
     *
     * @return 条目的路径
     */
    public String getPath() {
        return parentLogicalZipFile.getPath() + "!/" + entryName;
    }

    /**
     * 获取以纪元毫秒表示的最后修改时间，如果未知则返回 0L
     *
     * @return 以纪元毫秒表示的最后修改时间
     */
    public long getLastModifiedTimeMillis() {
        // 如果 lastModifiedTimeMillis 为零，但有可用的 MSDOS 日期和时间
        if (lastModifiedTimeMillis == 0L && (lastModifiedDateMSDOS != 0 || lastModifiedTimeMSDOS != 0)) {
            // 从 MS-DOS 日期和时间格式转换为纪元毫秒
            final int lastModifiedSecond = (lastModifiedTimeMSDOS & 0b11111) * 2;
            final int lastModifiedMinute = lastModifiedTimeMSDOS >> 5 & 0b111111;
            final int lastModifiedHour = lastModifiedTimeMSDOS >> 11;
            final int lastModifiedDay = lastModifiedDateMSDOS & 0b11111;
            final int lastModifiedMonth = (lastModifiedDateMSDOS >> 5 & 0b111) - 1;
            final int lastModifiedYear = (lastModifiedDateMSDOS >> 9) + 1980;

            final Calendar lastModifiedCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            lastModifiedCalendar.set(lastModifiedYear, lastModifiedMonth, lastModifiedDay, lastModifiedHour,
                    lastModifiedMinute, lastModifiedSecond);
            lastModifiedCalendar.set(Calendar.MILLISECOND, 0);

            // 通过覆盖为零的 lastModifiedTimeMillis 字段来缓存转换后的时间
            lastModifiedTimeMillis = lastModifiedCalendar.getTimeInMillis();
        }

        // 返回最后修改时间，如果完全未知则返回 0L
        return lastModifiedTimeMillis;
    }

    /**
     * 按版本号降序排列，然后按未版本化条目路径的字典序升序排列
     *
     * @param o
     *            要比较的对象
     * @return 比较结果
     */
    @Override
    public int compareTo(final FastZipEntry o) {
        final int diff0 = o.version - this.version;
        if (diff0 != 0) {
            return diff0;
        }
        final int diff1 = entryNameUnversioned.compareTo(o.entryNameUnversioned);
        if (diff1 != 0) {
            return diff1;
        }
        final int diff2 = entryName.compareTo(o.entryName);
        if (diff2 != 0) {
            return diff2;
        }
        // 对于具有相同条目名称的多个条目，按位置连续顺序返回，使得较早的条目覆盖较晚的条目
        // (这是一个为保持一致性而做出的任意决定)
        final long diff3 = locHeaderPos - o.locHeaderPos;
        return diff3 < 0L ? -1 : diff3 > 0L ? 1 : 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return parentLogicalZipFile.hashCode() ^ version ^ entryName.hashCode() ^ (int) locHeaderPos;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof FastZipEntry)) {
            return false;
        }
        final FastZipEntry other = (FastZipEntry) obj;
        return this.parentLogicalZipFile.equals(other.parentLogicalZipFile) && this.compareTo(other) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "jar:file:" + getPath();
    }
}
