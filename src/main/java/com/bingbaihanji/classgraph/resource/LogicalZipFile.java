 
package com.bingbaihanji.classgraph.resource;

import com.bingbaihanji.classgraph.util.*;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 逻辑 ZIP 文件，表示包含在 PhysicalZipFile 的 ZipFileSlice 中的 ZIP 文件
 */
public class LogicalZipFile extends ZipFileSlice {
    /** {@code "META_INF/"} */
    static final String META_INF_PATH_PREFIX = "META-INF/";
    /** {@code "META-INF/versions/"} */
    public static final String MULTI_RELEASE_PATH_PREFIX = META_INF_PATH_PREFIX + "versions/";
    /** {@code "META-INF/MANIFEST.MF"} */
    private static final String MANIFEST_PATH = META_INF_PATH_PREFIX + "MANIFEST.MF";
    /** {@code "Implementation-Title"} 清单键 */
    private static final byte[] IMPLEMENTATION_TITLE_KEY = manifestKeyToBytes("Implementation-Title");
    /** {@code "Specification-Title"} 清单键 */
    private static final byte[] SPECIFICATION_TITLE_KEY = manifestKeyToBytes("Specification-Title");
    /** {@code "Class-Path"} 清单键 */
    private static final byte[] CLASS_PATH_KEY = manifestKeyToBytes("Class-Path");
    /** {@code "Bundle-ClassPath"} 清单键 */
    private static final byte[] BUNDLE_CLASSPATH_KEY = manifestKeyToBytes("Bundle-ClassPath");
    /** {@code "Spring-Boot-Classes"} 清单键 */
    private static final byte[] SPRING_BOOT_CLASSES_KEY = manifestKeyToBytes("Spring-Boot-Classes");
    /** {@code "Spring-Boot-Lib"} 清单键 */
    private static final byte[] SPRING_BOOT_LIB_KEY = manifestKeyToBytes("Spring-Boot-Lib");
    /** {@code "Multi-Release"} 清单键 */
    private static final byte[] MULTI_RELEASE_KEY = manifestKeyToBytes("Multi-Release");

    // -------------------------------------------------------------------------------------------------------------
    /** {@code "Add-Exports"} 清单键 */
    private static final byte[] ADD_EXPORTS_KEY = manifestKeyToBytes("Add-Exports");
    /** {@code "Add-Opens"} 清单键 */
    private static final byte[] ADD_OPENS_KEY = manifestKeyToBytes("Add-Opens");
    /** {@code "Automatic-Module-Name"} 清单键 */
    private static final byte[] AUTOMATIC_MODULE_NAME_KEY = manifestKeyToBytes("Automatic-Module-Name");
    /** 用于快速将 ASCII 字符转换为小写 */
    private static byte[] toLowerCase = new byte[256];

    static {
        for (int i = 32; i < 127; i++) {
            toLowerCase[i] = (byte) Character.toLowerCase((char) i);
        }
    }

    /** 如果为 true，则不应在资源名称中去除多版本信息 */
    private final boolean enableMultiReleaseVersions;
    /** ZIP 文件条目列表 */
    public List<FastZipEntry> entries;
    /** "Class-Path" 清单条目的值(如果清单中存在)，否则为 null */
    public String classPathManifestEntryValue;
    /** "Bundle-ClassPath" 清单条目的值(如果清单中存在)，否则为 null */
    public String bundleClassPathManifestEntryValue;
    /** "Add-Exports" 清单条目的值(如果清单中存在)，否则为 null */
    public String addExportsManifestEntryValue;
    /** "Add-Opens" 清单条目的值(如果清单中存在)，否则为 null */
    public String addOpensManifestEntryValue;
    /** "Automatic-Module-Name" 清单条目的值(如果清单中存在)，否则为 null */
    public String automaticModuleNameManifestEntryValue;
    /** 如果为 true，则这是一个 JRE JAR */
    public boolean isJREJar;
    /** 在此 ZIP 文件的 classpath 中找到的 classpath 根集合 */
    Set<String> classpathRoots = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    /** 如果为 true，则这是一个多版本 JAR */
    private boolean isMultiReleaseJar;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从物理 ZIP 文件的切片构造逻辑 ZIP 文件
     *
     * @param zipFileSlice
     *            ZIP 文件切片
     * @param JarReader
     *            嵌套 JAR 处理器
     * @param log
     *            日志
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws InterruptedException
     *             如果线程被中断
     */
    LogicalZipFile(final ZipFileSlice zipFileSlice, final JarReader JarReader, final LogNode log,
                   final boolean enableMultiReleaseVersions) throws IOException, InterruptedException {
        super(zipFileSlice);
        this.enableMultiReleaseVersions = enableMultiReleaseVersions;
        readCentralDirectory(JarReader, log);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从清单中提取值，并以字符串形式返回该值以及终止换行符之后的索引
     * 清单文件支持三种不同的行终止符类型，条目可以使用行终止符后跟空格的方式跨行拆分
     *
     * @param manifest
     *            清单字节数组
     * @param startIdx
     *            清单值的起始索引
     * @return 清单值
     */
    private static Entry<String, Integer> getManifestValue(final byte[] manifest, final int startIdx) {
        // 检查清单条目是否跨多行拆分
        int curr = startIdx;
        final int len = manifest.length;
        while (curr < len && manifest[curr] == (byte) ' ') {
            // 跳过开头的空格
            curr++;
        }
        final int firstNonSpaceIdx = curr;
        boolean isMultiLine = false;
        for (; curr < len && !isMultiLine; curr++) {
            final byte b = manifest[curr];
            if (b == (byte) '\r' && curr < len - 1 && manifest[curr + 1] == (byte) '\n') {
                if (curr < len - 2 && manifest[curr + 2] == (byte) ' ') {
                    isMultiLine = true;
                }
                break;
            } else if (b == (byte) '\r' || b == (byte) '\n') {
                if (curr < len - 1 && manifest[curr + 1] == (byte) ' ') {
                    isMultiLine = true;
                }
                break;
            }
        }
        String val;
        if (!isMultiLine) {
            // 单行值的快速路径
            val = new String(manifest, firstNonSpaceIdx, curr - firstNonSpaceIdx, StandardCharsets.UTF_8);
        } else {
            // 跳过多行值中的(换行符 + 空格)序列
            final ByteArrayOutputStream buf = new ByteArrayOutputStream();
            curr = firstNonSpaceIdx;
            for (; curr < len; curr++) {
                final byte b = manifest[curr];
                boolean isLineEnd;
                if (b == (byte) '\r' && curr < len - 1 && manifest[curr + 1] == (byte) '\n') {
                    // CRLF
                    curr += 2;
                    isLineEnd = true;
                } else if (b == '\r' || b == '\n') {
                    // CR 或 LF
                    curr += 1;
                    isLineEnd = true;
                } else {
                    buf.write(b);
                    isLineEnd = false;
                }
                if (isLineEnd && curr < len && manifest[curr] != (byte) ' ') {
                    // 如果换行后没有跟空格，则值结束
                    break;
                }
                // 如果换行后跟有空格，则 for 循环头部的 curr++ 将跳过该空格
            }
            try {
                val = buf.toString("UTF-8");
            } catch (final UnsupportedEncodingException e) {
                // 不应发生
                throw new RuntimeException("UTF-8 encoding is not supported in your JRE", e);
            }
        }
        return new SimpleEntry<>(val.endsWith(" ") ? val.trim() : val, curr);
    }

    /**
     * 将清单键转换为字节数组
     *
     * @param key
     *            清单键
     * @return 小写化的清单键字节数组
     */
    private static byte[] manifestKeyToBytes(final String key) {
        final byte[] bytes = new byte[key.length()];
        for (int i = 0; i < key.length(); i++) {
            bytes[i] = (byte) Character.toLowerCase(key.charAt(i));
        }
        return bytes;
    }

    /**
     * 在指定位置检查键是否匹配
     *
     * @param manifest
     *            清单字节数组
     * @param key
     *            键
     * @param pos
     *            要尝试匹配的位置
     * @return 如果键在此位置匹配则返回 true
     */
    private static boolean keyMatchesAtPosition(final byte[] manifest, final byte[] key, final int pos) {
        if (pos + key.length + 1 > manifest.length || manifest[pos + key.length] != ':') {
            return false;
        }
        for (int i = 0; i < key.length; i++) {
            // 清单键不区分大小写
            if (toLowerCase[manifest[i + pos]] != key[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 ZIP 文件的清单条目
     *
     * @param manifestZipEntry
     *            清单 ZIP 条目
     * @param log
     *            日志
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws InterruptedException
     *             如果线程被中断
     */
    private void parseManifest(final FastZipEntry manifestZipEntry, final LogNode log)
            throws IOException, InterruptedException {
        // 将清单条目内容加载为字节数组
        final byte[] manifest = manifestZipEntry.getSlice().load();

        // 查找字段键(由换行符分隔)
        for (int i = 0; i < manifest.length; ) {
            // 换行后清单键之前不能有任何空格，因此键立即开始
            boolean skip = false;
            if (manifest[i] == (byte) '\n' || manifest[i] == (byte) '\r') {
                // 跳过空行
                skip = true;

            } else if (keyMatchesAtPosition(manifest, IMPLEMENTATION_TITLE_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + IMPLEMENTATION_TITLE_KEY.length + 1);
                if ("Java Runtime Environment".equalsIgnoreCase(manifestValueAndEndIdx.getKey())) {
                    isJREJar = true;
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, SPECIFICATION_TITLE_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + SPECIFICATION_TITLE_KEY.length + 1);
                if ("Java Platform API Specification".equalsIgnoreCase(manifestValueAndEndIdx.getKey())) {
                    isJREJar = true;
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, CLASS_PATH_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + CLASS_PATH_KEY.length + 1);
                // 将 Class-Path 清单条目值添加到 classpath
                classPathManifestEntryValue = manifestValueAndEndIdx.getKey();
                if (log != null) {
                    log.log("Found Class-Path entry in manifest file: " + classPathManifestEntryValue);
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, BUNDLE_CLASSPATH_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + BUNDLE_CLASSPATH_KEY.length + 1);
                // 将 Bundle-ClassPath 清单条目值添加到 classpath
                bundleClassPathManifestEntryValue = manifestValueAndEndIdx.getKey();
                if (log != null) {
                    log.log("Found Bundle-ClassPath entry in manifest file: " + bundleClassPathManifestEntryValue);
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, SPRING_BOOT_CLASSES_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + SPRING_BOOT_CLASSES_KEY.length + 1);
                final String springBootClassesFieldVal = manifestValueAndEndIdx.getKey();
                if (!"BOOT-INF/classes".equals(springBootClassesFieldVal)
                        && !"BOOT-INF/classes/".equals(springBootClassesFieldVal)
                        && !"WEB-INF/classes".equals(springBootClassesFieldVal)
                        && !"WEB-INF/classes/".equals(springBootClassesFieldVal)) {
                    throw new IOException("Spring boot classes are at \"" + springBootClassesFieldVal
                            + "\" rather than the standard location \"BOOT-INF/classes/\" or \"WEB-INF/classes/\" "
                            + "-- please report this at https://github.com/classgraph/classgraph/issues");
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, SPRING_BOOT_LIB_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + SPRING_BOOT_LIB_KEY.length + 1);
                final String springBootLibFieldVal = manifestValueAndEndIdx.getKey();
                if (!"BOOT-INF/lib".equals(springBootLibFieldVal) && !"BOOT-INF/lib/".equals(springBootLibFieldVal)
                        && !"WEB-INF/lib".equals(springBootLibFieldVal)
                        && !"WEB-INF/lib/".equals(springBootLibFieldVal)) {
                    throw new IOException("Spring boot lib jars are at \"" + springBootLibFieldVal
                            + "\" rather than the standard location \"BOOT-INF/lib/\" or \"WEB-INF/lib/\" "
                            + "-- please report this at https://github.com/classgraph/classgraph/issues");
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, MULTI_RELEASE_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + MULTI_RELEASE_KEY.length + 1);
                if ("true".equalsIgnoreCase(manifestValueAndEndIdx.getKey())) {
                    isMultiReleaseJar = true;
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, ADD_EXPORTS_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + ADD_EXPORTS_KEY.length + 1);
                addExportsManifestEntryValue = manifestValueAndEndIdx.getKey();
                if (log != null) {
                    log.log("Found Add-Exports entry in manifest file: " + addExportsManifestEntryValue);
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, ADD_OPENS_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + ADD_OPENS_KEY.length + 1);
                addOpensManifestEntryValue = manifestValueAndEndIdx.getKey();
                if (log != null) {
                    log.log("Found Add-Opens entry in manifest file: " + addOpensManifestEntryValue);
                }
                i = manifestValueAndEndIdx.getValue();

            } else if (keyMatchesAtPosition(manifest, AUTOMATIC_MODULE_NAME_KEY, i)) {
                final Entry<String, Integer> manifestValueAndEndIdx = getManifestValue(manifest,
                        i + AUTOMATIC_MODULE_NAME_KEY.length + 1);
                automaticModuleNameManifestEntryValue = manifestValueAndEndIdx.getKey();
                if (log != null) {
                    log.log("Found Automatic-Module-Name entry in manifest file: "
                            + automaticModuleNameManifestEntryValue);
                }
                i = manifestValueAndEndIdx.getValue();

            } else {

                // 键名称未被识别 -- 跳到下一个键
                skip = true;
            }

            if (skip) {
                // 字段键不匹配 -- 跳到下一个键(在下一个换行符之后，且该换行符后不跟空格)
                for (; i < manifest.length - 2; i++) {
                    if (manifest[i] == (byte) '\r' && manifest[i + 1] == (byte) '\n'
                            && manifest[i + 2] != (byte) ' ') {
                        i += 2;
                        break;
                    } else if ((manifest[i] == (byte) '\r' || manifest[i] == (byte) '\n')
                            && manifest[i + 1] != (byte) ' ') {
                        i++;
                        break;
                    }
                }
                if (i >= manifest.length - 2) {
                    break;
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 读取 ZIP 文件的中央目录
     *
     * @param JarReader
     *            嵌套 JAR 处理器
     * @param log
     *            日志
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws InterruptedException
     *             如果线程被中断
     */
    @SuppressWarnings("resource")
    private void readCentralDirectory(final JarReader JarReader, final LogNode log)
            throws IOException, InterruptedException {
        if (slice.sliceLength < 22) {
            throw new IOException("Zipfile too short to have a central directory");
        }

        final RandomAccessReader reader = slice.randomAccessReader();

        // 扫描中央目录结束(EOCD)签名尾部注释最大可达 64kB，
        // 因此需要回溯扫描那么多字节以确定这是否是有效的 ZIP 文件但为了速度，
        // 最初只尝试最多回溯读取 32 个字符
        long eocdPos = -1;
        for (long i = slice.sliceLength - 22, iMin = slice.sliceLength - 22 - 32; i >= iMin && i >= 0L; --i) {
            if (reader.readUnsignedInt(i) == 0x06054b50L) {
                eocdPos = i;
                break;
            }
        }
        if (eocdPos < 0 && slice.sliceLength > 22 + 32) {
            // 如果未找到 EOCD 签名，将文件最后 64kB 作为一个整体块读入 RAM，
            // 以便以更高速度回溯扫描以定位 EOCD 签名
            final int bytesToRead = (int) Math.min(slice.sliceLength, 65536);
            final byte[] eocdBytes = new byte[bytesToRead];
            final long readStartOff = slice.sliceLength - bytesToRead;
            if (reader.read(readStartOff, eocdBytes, 0, bytesToRead) < bytesToRead) {
                // 不应发生
                throw new IOException("Zipfile is truncated");
            }
            try (final ArraySlice arraySlice = new ArraySlice(eocdBytes, /* isDeflatedZipEntry = */ false,
                    /* inflatedLengthHint = */ 0L, JarReader)) {
                final RandomAccessReader eocdReader = arraySlice.randomAccessReader();
                for (long i = eocdBytes.length - 22L; i >= 0L; --i) {
                    if (eocdReader.readUnsignedInt(i) == 0x06054b50L) {
                        eocdPos = i + readStartOff;
                        break;
                    }
                }
            }
        }
        if (eocdPos < 0) {
            throw new IOException("Jarfile central directory signature not found: " + getPath());
        }
        long numEnt = reader.readUnsignedShort(eocdPos + 8);
        if (reader.readUnsignedShort(eocdPos + 4) > 0 || reader.readUnsignedShort(eocdPos + 6) > 0
                || numEnt != reader.readUnsignedShort(eocdPos + 10)) {
            throw new IOException("Multi-disk jarfiles not supported: " + getPath());
        }
        long cenSize = reader.readUnsignedInt(eocdPos + 12);
        long cenOff = reader.readUnsignedInt(eocdPos + 16);
        long cenPos = eocdPos - cenSize;

        // 检查 Zip64 中央目录结束定位器记录
        final long zip64cdLocIdx = eocdPos - 20;
        if (zip64cdLocIdx >= 0 && reader.readUnsignedInt(zip64cdLocIdx) == 0x07064b50L) {
            if (reader.readUnsignedInt(zip64cdLocIdx + 4) > 0 || reader.readUnsignedInt(zip64cdLocIdx + 16) > 1) {
                throw new IOException("Multi-disk jarfiles not supported: " + getPath());
            }
            final long eocdPos64 = reader.readLong(zip64cdLocIdx + 8);
            if (reader.readUnsignedInt(eocdPos64) != 0x06064b50L) {
                throw new IOException("Zip64 central directory at location " + eocdPos64
                        + " does not have Zip64 central directory header: " + getPath());
            }
            final long numEnt64 = reader.readLong(eocdPos64 + 24);
            if (reader.readUnsignedInt(eocdPos64 + 16) > 0 || reader.readUnsignedInt(eocdPos64 + 20) > 0
                    || numEnt64 != reader.readLong(eocdPos64 + 32)) {
                throw new IOException("Multi-disk jarfiles not supported: " + getPath());
            }
            if (numEnt == 0xffff) {
                numEnt = numEnt64;
            } else if (numEnt != numEnt64) {
                // 条目数量不匹配 -- 触发手动计数条目
                numEnt = -1L;
            }

            final long cenSize64 = reader.readLong(eocdPos64 + 40);
            if (cenSize == 0xffffffffL) {
                cenSize = cenSize64;
            } else if (cenSize != cenSize64) {
                throw new IOException(
                        "Mismatch in central directory size: " + cenSize + " vs. " + cenSize64 + ": " + getPath());
            }

            // 重新计算中央目录位置
            cenPos = eocdPos64 - cenSize;

            final long cenOff64 = reader.readLong(eocdPos64 + 48);
            if (cenOff == 0xffffffffL) {
                cenOff = cenOff64;
            } else if (cenOff != cenOff64) {
                throw new IOException(
                        "Mismatch in central directory offset: " + cenOff + " vs. " + cenOff64 + ": " + getPath());
            }
        }

        if (cenSize > eocdPos) {
            throw new IOException(
                    "Central directory size out of range: " + cenSize + " vs. " + eocdPos + ": " + getPath());
        }

        // 获取第一个本地文件头部的偏移量
        final long locPos = cenPos - cenOff;
        if (locPos < 0) {
            throw new IOException("Local file header offset out of range: " + locPos + ": " + getPath());
        }

        // 如果中央目录小于 2GB，将其读入字节数组如果中央目录大于 2GB，
        // 需要使用 ZipFileSliceReader 直接从文件中读取每个条目字段
        RandomAccessReader cenReader;
        if (cenSize > FileUtils.MAX_BUFFER_SIZE) {
            // 创建一个覆盖中央目录的切片(这允许使用较慢的 FileSlice API 访问大于 2GB 的中央目录，
            // 该 API 直接读取文件，但同时可以在每次读取偏移量时无需加上 cenPos，
            // 从而使此切片或下面 "else" 子句中的切片可以使用相同的索引(即从中央目录起始位置算起的偏移量)来访问)
            cenReader = slice.slice(cenPos, cenSize, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L)
                    .randomAccessReader();
        } else {
            // 为了速度将中央目录读入 RAM，然后将其包装在 ArraySlice 中
            // (ArraySlice 的随机访问速度比 FileSlice 更快)
            final byte[] entryBytes = new byte[(int) cenSize];
            if (reader.read(cenPos, entryBytes, 0, (int) cenSize) < cenSize) {
                // 不应发生
                throw new IOException("Zipfile is truncated");
            }
            cenReader = new ArraySlice(entryBytes, /* isDeflatedZipEntry = */ false, /* inflatedSizeHint = */ 0L,
                    JarReader).randomAccessReader();
        }

        if (numEnt == -1L) {
            // numEnt 和 numEnt64 不一致 -- 手动计数条目
            numEnt = 0;
            for (long entOff = 0; entOff + 46 <= cenSize; ) {
                final long sig = cenReader.readUnsignedInt(entOff);
                if (sig != 0x02014b50L) {
                    throw new IOException("Invalid central directory signature: 0x"
                            + Integer.toString((int) sig, 16) + ": " + getPath());
                }
                final int filenameLen = cenReader.readUnsignedShort(entOff + 28);
                final int extraFieldLen = cenReader.readUnsignedShort(entOff + 30);
                final int commentLen = cenReader.readUnsignedShort(entOff + 32);
                entOff += 46 + filenameLen + extraFieldLen + commentLen;
                numEnt++;
            }
        }

        // 不能有超过 (Integer.MAX_VALUE - 8) 个条目，因为它们存储在 ArrayList 中
        if (numEnt > FileUtils.MAX_BUFFER_SIZE) {
            // 在这种(几乎不可能发生的)情况下，一种替代方案是仅返回前 2B 个条目
            throw new IOException("Too many zipfile entries: " + numEnt);
        }

        // 确保没有通过伪造条目数量来进行 DoS 攻击
        if (numEnt > cenSize / 46) {
            // 最小的目录条目大小为 46 字节
            throw new IOException("Too many zipfile entries: " + numEnt + " (expected a max of " + cenSize / 46
                    + " based on central directory size)");
        }

        // 枚举条目
        entries = new ArrayList<>((int) numEnt);
        FastZipEntry manifestZipEntry = null;
        try {
            int entSize = 0;
            for (long entOff = 0; entOff + 46 <= cenSize; entOff += entSize) {
                final long sig = cenReader.readUnsignedInt(entOff);
                if (sig != 0x02014b50L) {
                    throw new IOException("Invalid central directory signature: 0x"
                            + Integer.toString((int) sig, 16) + ": " + getPath());
                }
                final int filenameLen = cenReader.readUnsignedShort(entOff + 28);
                final int extraFieldLen = cenReader.readUnsignedShort(entOff + 30);
                final int commentLen = cenReader.readUnsignedShort(entOff + 32);
                entSize = 46 + filenameLen + extraFieldLen + commentLen;

                // 获取并清理条目名称
                final long filenameStartOff = entOff + 46;
                final long filenameEndOff = filenameStartOff + filenameLen;
                if (filenameEndOff > cenSize) {
                    if (log != null) {
                        log.log("Filename extends past end of entry -- skipping entry at offset " + entOff);
                    }
                    break;
                }
                final String entryName = cenReader.readString(filenameStartOff, filenameLen);
                String entryNameSanitized = FileUtils.sanitizeEntryPath(entryName, /* removeInitialSlash = */ true,
                        /* removeFinalSlash = */ false);
                if (entryNameSanitized.isEmpty() || entryName.endsWith("/")) {
                    // 跳过目录条目
                    continue;
                }

                // 检查条目标志位
                final int flags = cenReader.readUnsignedShort(entOff + 8);
                if ((flags & 1) != 0) {
                    if (log != null) {
                        log.log("Skipping encrypted zip entry: " + entryNameSanitized);
                    }
                    continue;
                }

                // 检查压缩方法
                final int compressionMethod = cenReader.readUnsignedShort(entOff + 10);
                if (compressionMethod != /* stored */ 0 && compressionMethod != /* deflated */ 8) {
                    if (log != null) {
                        log.log("Skipping zip entry with invalid compression method " + compressionMethod + ": "
                                + entryNameSanitized);
                    }
                    continue;
                }
                final boolean isDeflated = compressionMethod == /* deflated */ 8;

                // 获取压缩后和未压缩的大小
                long compressedSize = (cenReader.readUnsignedInt(entOff + 20));
                long uncompressedSize = (cenReader.readUnsignedInt(entOff + 24));

                // 获取外部文件属性
                final int fileAttributes = cenReader.readUnsignedShort(entOff + 40);

                long pos = cenReader.readUnsignedInt(entOff + 42);

                // 检查扩展字段中的 Zip64 头部
                // 参见：
                // https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
                // https://github.com/LuaDist/zip/blob/master/proginfo/extrafld.txt
                long lastModifiedMillis = 0L;
                if (extraFieldLen > 0) {
                    for (int extraFieldOff = 0; extraFieldOff + 4 < extraFieldLen; ) {
                        final long tagOff = filenameEndOff + extraFieldOff;
                        final int tag = cenReader.readUnsignedShort(tagOff);
                        final int size = cenReader.readUnsignedShort(tagOff + 2);
                        if (extraFieldOff + 4 + size > extraFieldLen) {
                            // 无效的大小
                            if (log != null) {
                                log.log("Skipping zip entry with invalid extra field size: " + entryNameSanitized);
                            }
                            break;
                        }
                        if (tag == 1 && size >= 20) {
                            // Zip64 扩展信息额外字段
                            final long uncompressedSize64 = cenReader.readLong(tagOff + 4 + 0);
                            if (uncompressedSize == 0xffffffffL) {
                                uncompressedSize = uncompressedSize64;
                            } else if (uncompressedSize != uncompressedSize64) {
                                throw new IOException("Mismatch in uncompressed size: " + uncompressedSize + " vs. "
                                        + uncompressedSize64 + ": " + entryNameSanitized);
                            }
                            final long compressedSize64 = cenReader.readLong(tagOff + 4 + 8);
                            if (compressedSize == 0xffffffffL) {
                                compressedSize = compressedSize64;
                            } else if (compressedSize != compressedSize64) {
                                throw new IOException("Mismatch in compressed size: " + compressedSize + " vs. "
                                        + compressedSize64 + ": " + entryNameSanitized);
                            }
                            // 只有压缩后大小和未压缩大小是必需字段
                            if (size >= 28) {
                                final long pos64 = cenReader.readLong(tagOff + 4 + 16);
                                if (pos == 0xffffffffL) {
                                    pos = pos64;
                                } else if (pos != pos64) {
                                    throw new IOException("Mismatch in entry pos: " + pos + " vs. " + pos64 + ": "
                                            + entryNameSanitized);
                                }
                            }
                            break;

                        } else if (tag == 0x5455 && size >= 5) {
                            // 扩展 Unix 时间戳
                            final int bits = cenReader.readUnsignedByte(tagOff + 4 + 0);
                            if ((bits & 1) == 1 && size >= 5 + 8) {
                                lastModifiedMillis = cenReader.readLong(tagOff + 4 + 1) * 1000L;
                            }

                        } else if (tag == 0x5855 && size >= 20) {
                            // Unix 额外字段(已弃用)
                            lastModifiedMillis = cenReader.readLong(tagOff + 4 + 8) * 1000L;
                            // 此额外字段中还有可选的 UID 和 GID 字段(当前被忽略)

                        } else if (tag == 0x7855) {
                            // Info-ZIP Unix UID 和 GID 字段(当前被忽略)

                        } else if (tag == 0x7075) {
                            // Info-ZIP Unicode 路径额外字段
                            final int version = cenReader.readUnsignedByte(tagOff + 4 + 0);
                            if (version != 1) {
                                throw new IOException("Unknown Unicode entry name format " + version
                                        + " in extra field: " + entryNameSanitized);
                            } else if (size > 9) {
                                // 将非 Unicode 条目名称替换为 Unicode 版本
                                try {
                                    entryNameSanitized = cenReader.readString(tagOff + 9, size - 9);
                                } catch (final IllegalArgumentException e) {
                                    throw new IOException("Malformed extended Unicode entry name for entry: "
                                            + entryNameSanitized);
                                }
                            }
                        }
                        extraFieldOff += 4 + size;
                    }
                }

                int lastModifiedTimeMSDOS = 0;
                int lastModifiedDateMSDOS = 0;
                if (lastModifiedMillis == 0L) {
                    // 如果未提供 Unix 时间戳，则从 MS-DOS 格式转换 ZIP 条目时间戳
                    lastModifiedTimeMSDOS = cenReader.readUnsignedShort(entOff + 12);
                    lastModifiedDateMSDOS = cenReader.readUnsignedShort(entOff + 14);
                }

                if (compressedSize < 0) {
                    if (log != null) {
                        log.log("Skipping zip entry with invalid compressed size (" + compressedSize + "): "
                                + entryNameSanitized);
                    }
                    continue;
                }
                if (uncompressedSize < 0) {
                    if (log != null) {
                        log.log("Skipping zip entry with invalid uncompressed size (" + uncompressedSize + "): "
                                + entryNameSanitized);
                    }
                    continue;
                }
                if (pos < 0) {
                    if (log != null) {
                        log.log("Skipping zip entry with invalid pos (" + pos + "): " + entryNameSanitized);
                    }
                    continue;
                }

                final long locHeaderPos = locPos + pos;
                if (locHeaderPos < 0) {
                    if (log != null) {
                        log.log("Skipping zip entry with invalid loc header position (" + locHeaderPos + "): "
                                + entryNameSanitized);
                    }
                    continue;
                }
                if (locHeaderPos + 4 >= slice.sliceLength) {
                    if (log != null) {
                        log.log("Unexpected EOF when trying to read LOC header: " + entryNameSanitized);
                    }
                    continue;
                }

                // 添加 ZIP 条目
                final FastZipEntry entry = new FastZipEntry(this, locHeaderPos, entryNameSanitized, isDeflated,
                        compressedSize, uncompressedSize, lastModifiedMillis, lastModifiedTimeMSDOS,
                        lastModifiedDateMSDOS, fileAttributes, enableMultiReleaseVersions);
                entries.add(entry);

                // 记录清单条目
                if (entry.entryName.equals(MANIFEST_PATH)) {
                    manifestZipEntry = entry;
                }
            }
        } catch (EOFException | IndexOutOfBoundsException e) {
            // 如果任何条目不在文件范围内，则停止读取条目
            if (log != null) {
                log.log("Reached premature EOF"
                        + (entries.isEmpty() ? "" : " after reading zip entry " + entries.get(entries.size() - 1)));
            }
        }

        // 解析清单文件(如果存在)
        if (manifestZipEntry != null) {
            parseManifest(manifestZipEntry, log);
        }

        // 对于多版本 JAR，丢弃那些被最新版本特定条目覆盖的旧条目或非版本化条目
        if (isMultiReleaseJar) {
            if (VersionFinder.JAVA_MAJOR_VERSION < 9) {
                if (log != null) {
                    log.log("This is a multi-release jar, but JRE version " + VersionFinder.JAVA_MAJOR_VERSION
                            + " does not support multi-release jars");
                }
            } else {
                if (log != null) {
                    // 查找 JAR 中所有唯一的多版本版本号
                    final Set<Integer> versionsFound = new HashSet<>();
                    for (final FastZipEntry entry : entries) {
                        if (entry.version > 8) {
                            versionsFound.add(entry.version);
                        }
                    }
                    final List<Integer> versionsFoundSorted = new ArrayList<>(versionsFound);
                    CollectionUtils.sortIfNotEmpty(versionsFoundSorted);
                    log.log("This is a multi-release jar, with versions: "
                            + StringUtils.join(", ", versionsFoundSorted));
                }

                // 按版本号降序排序，为版本覆盖做准备
                CollectionUtils.sortIfNotEmpty(entries);

                // 覆盖出现在多个版本部分中的文件，使得每个未版本化路径只有一个条目，
                // 即具有最高版本号的版本化路径
                final List<FastZipEntry> unversionedZipEntriesMasked = new ArrayList<>(entries.size());
                final Map<String, String> unversionedPathToVersionedPath = new HashMap<>();
                for (final FastZipEntry versionedZipEntry : entries) {
                    if (!unversionedPathToVersionedPath.containsKey(versionedZipEntry.entryNameUnversioned)) {
                        // 这是此条目未版本化路径的第一个 FastZipEntry
                        unversionedPathToVersionedPath.put(versionedZipEntry.entryNameUnversioned,
                                versionedZipEntry.entryName);
                        unversionedZipEntriesMasked.add(versionedZipEntry);
                    } else if (log != null) {
                        log.log(unversionedPathToVersionedPath.get(versionedZipEntry.entryNameUnversioned)
                                + " masks " + versionedZipEntry.entryName);
                    }
                }

                // 用版本覆盖后的条目替换原始条目
                entries = unversionedZipEntriesMasked;
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public boolean equals(final Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.fastzipfilereader.ZipFileSlice#toString()
     */
    @Override
    public String toString() {
        return getPath();
    }
}
