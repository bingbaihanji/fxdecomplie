package com.bingbaihanji.fxdecomplie.core.jadx.zip;

import com.bingbaihanji.fxdecomplie.core.jadx.zip.fallback.FallbackException;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.fallback.FallbackZipParser;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.parser.JadxZipParser;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.IJadxZipSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.security.JadxZipSecurity;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Jadx 的 Zip 读取器包装类，提供自定义的 Zip 解析器（{@link JadxZipParser}），
 * 当自定义解析器无法处理时，自动回退到 Java 默认实现
 */
public class ZipReader {
    private final ZipReaderOptions options;

    /** 使用默认选项创建 ZipReader 实例 */
    public ZipReader() {
        this(ZipReaderOptions.getDefault());
    }

    /** 使用指定的标志集合创建 ZipReader 实例 */
    public ZipReader(Set<ZipReaderFlags> flags) {
        this(new ZipReaderOptions(new JadxZipSecurity(), flags));
    }

    /** 使用指定的安全策略创建 ZipReader 实例 */
    public ZipReader(IJadxZipSecurity security) {
        this(new ZipReaderOptions(security, ZipReaderFlags.none()));
    }

    /** 使用指定的选项创建 ZipReader 实例 */
    public ZipReader(ZipReaderOptions options) {
        this.options = options;
    }

    /**
     * 打开指定的 Zip 文件并返回其内容
     * 优先使用自定义解析器，若失败则根据配置回退到默认实现
     *
     * @param zipFile 要打开的 Zip 文件
     * @return Zip 文件的内容对象
     * @throws IOException 如果文件不存在或无法解析
     */
    @SuppressWarnings("resource")
    public ZipContent open(File zipFile) throws IOException {
        if (!zipFile.exists()) {
            throw new FileNotFoundException(zipFile.getAbsolutePath());
        }
        try {
            JadxZipParser jadxParser = new JadxZipParser(zipFile, options);
            IZipParser detectedParser = detectParser(zipFile, jadxParser);
            return detectedParser.open();
        } catch (FallbackException e) {
            throw e;
        } catch (Exception e) {
            if (options.getFlags().contains(ZipReaderFlags.DONT_USE_FALLBACK)) {
                throw new IOException("Failed to open zip: " + zipFile, e);
            }
            // 切换到回退解析器
            return buildFallbackParser(zipFile).open();
        }
    }

    /**
     * 遍历 Zip 文件中的所有有效条目
     * 访问者返回非 null 值时将停止遍历并返回该值
     *
     * @param file    要遍历的 Zip 文件
     * @param visitor 条目访问者函数
     * @param <R>     返回值类型
     * @return 访问者返回的非 null 值，若遍历完成则返回 null
     */
    public <R> @Nullable R visitEntries(File file, Function<IZipEntry, R> visitor) {
        try (ZipContent content = open(file)) {
            for (IZipEntry entry : content.getEntries()) {
                R result = visitor.apply(entry);
                if (result != null) {
                    return result;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to process zip file: " + file.getAbsolutePath(), e);
        }
        return null;
    }

    /**
     * 读取 Zip 文件中的所有非目录条目，并通过访问者处理每个条目及其输入流
     *
     * @param file    要读取的 Zip 文件
     * @param visitor 条目和输入流的消费者
     */
    public void readEntries(File file, BiConsumer<IZipEntry, InputStream> visitor) {
        visitEntries(file, entry -> {
            if (!entry.isDirectory()) {
                try (InputStream in = entry.getInputStream()) {
                    visitor.accept(entry, in);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to process zip entry: " + entry, e);
                }
            }
            return null;
        });
    }

    /** 获取当前 ZipReader 的配置选项 */
    public ZipReaderOptions getOptions() {
        return options;
    }

    /**
     * 根据文件类型和配置标志选择合适的 Zip 解析器
     * <p>
     * 对于 .apk 文件或设置了禁用回退标志时，始终使用 Jadx 自定义解析器 
     * 当自定义解析器无法打开文件，或配置了以回退解析器为默认时，使用回退解析器
     *
     * @param zipFile    要解析的 Zip 文件
     * @param jadxParser Jadx 自定义解析器实例
     * @return 选定的 Zip 解析器
     * @throws IOException 构建回退解析器失败时抛出
     */
    private IZipParser detectParser(File zipFile, JadxZipParser jadxParser) throws IOException {
        if (zipFile.getName().endsWith(".apk")
                || options.getFlags().contains(ZipReaderFlags.DONT_USE_FALLBACK)) {
            return jadxParser;
        }
        if (!jadxParser.canOpen()) {
            return buildFallbackParser(zipFile);
        }
        // 默认情况
        if (options.getFlags().contains(ZipReaderFlags.FALLBACK_AS_DEFAULT)) {
            return buildFallbackParser(zipFile);
        }
        return jadxParser;
    }

    /** 构建基于 Java 默认实现的回退 Zip 解析器 */
    private FallbackZipParser buildFallbackParser(File zipFile) throws IOException {
        return new FallbackZipParser(zipFile, options);
    }
}
