package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.FileTypeDetector;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.entry.ResourceEntry;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.IZipEntry;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * 资源文件类，表示反编译过程中的资源文件
 * 封装了资源文件的元数据信息，包括文件名、类型、混淆名称以及对应的 ZIP 条目
 */
public class ResourceFile {
    private final JadxDecompiler decompiler;
    private final String name;
    private ResourceType type;

    private @Nullable IZipEntry zipEntry;
    private String deobfName;

    /**
     * 受保护构造方法，创建资源文件实例
     *
     * @param decompiler 反编译器实例
     * @param name       资源名称
     * @param type       资源类型
     */
    protected ResourceFile(JadxDecompiler decompiler, String name, ResourceType type) {
        this.decompiler = decompiler;
        this.name = name;
        this.type = type;
    }

    /**
     * 根据文件对象创建资源文件实例
     *
     * @param decompiler 反编译器实例
     * @param file       资源文件对象
     * @param type       资源类型
     * @return 新创建的资源文件实例
     */
    public static ResourceFile createResourceFile(JadxDecompiler decompiler, File file, ResourceType type) {
        return new ResourceFile(decompiler, file.getAbsolutePath(), type);
    }

    /**
     * 根据资源名称创建资源文件实例
     * 若名称未通过安全校验 (非法条目名)，则返回 {@code null}
     *
     * @param decompiler 反编译器实例
     * @param name       资源名称
     * @param type       资源类型
     * @return 新创建的资源文件实例，名称非法时返回 {@code null}
     */
    public static ResourceFile createResourceFile(JadxDecompiler decompiler, String name, ResourceType type) {
        if (!decompiler.getArgs().getSecurity().isValidEntryName(name)) {
            return null;
        }
        return new ResourceFile(decompiler, name, type);
    }

    /**
     * 获取资源的原始名称 (未经过混淆处理的名称)
     *
     * @return 资源原始名称
     */
    public String getOriginalName() {
        return name;
    }

    /**
     * 获取资源的反混淆名称
     * 若未设置反混淆名称，则返回原始名称
     *
     * @return 反混淆名称，未设置时返回原始名称
     */
    public String getDeobfName() {
        return deobfName != null ? deobfName : name;
    }

    /**
     * 设置资源的反混淆名称
     *
     * @param resFullName 反混淆后的完整资源名称
     */
    public void setDeobfName(String resFullName) {
        this.deobfName = resFullName;
    }

    /**
     * 获取资源类型
     *
     * @return 资源类型
     */
    public ResourceType getType() {
        return type;
    }

    /**
     * 加载并返回该资源文件的内容容器
     *
     * @return 资源内容容器
     */
    public ResContainer loadContent() {
        return ResourcesLoader.loadContent(decompiler, this);
    }

    /**
     * 根据资源条目为该资源设置别名 (即反混淆后的规范化路径)
     * 当 {@code useHeaders} 为 {@code true} 时，会读取文件头字节以探测真实扩展名 
     * 否则直接从原始名称提取扩展名
     *
     * @param entry      资源条目
     * @param useHeaders 是否通过文件头字节探测扩展名
     * @return 若成功设置了新的别名则返回 {@code true}，别名与原名相同则返回 {@code false}
     */
    public boolean setAlias(ResourceEntry entry, boolean useHeaders) {
        StringBuilder sb = new StringBuilder();
        sb.append("res/").append(entry.getTypeName()).append(entry.getConfig());
        sb.append("/").append(entry.getKeyName());

        if (useHeaders) {
            try {
                int maxBytesToReadLimit = 4096;
                byte[] bytes = ResourcesLoader.decodeStream(this, (size, is) -> {
                    int bytesToRead;
                    if (size > 0) {
                        bytesToRead = (int) Math.min(size, maxBytesToReadLimit);
                    } else if (size == 0) {
                        bytesToRead = 0;
                    } else {
                        bytesToRead = maxBytesToReadLimit;
                    }
                    if (bytesToRead == 0) {
                        return new byte[0];
                    }
                    return is.readNBytes(bytesToRead);
                });

                String fileExtension = FileTypeDetector.detectFileExtension(bytes);
                if (!CodegenEscapeUtils.isEmpty(fileExtension)) {
                    sb.append(fileExtension);
                } else {
                    sb.append(getExtFromName(name));
                }
            } catch (JadxException ignored) {
            }
        } else {
            sb.append(getExtFromName(name));
        }
        String alias = sb.toString();
        if (!alias.equals(name)) {
            setDeobfName(alias);
            type = ResourceType.getFileType(alias);
            return true;
        }
        return false;
    }

    /**
     * 从资源名称中提取文件扩展名
     * 对经 aapt2 资源压缩后仍保留的 .9.png 点九图做特殊处理
     *
     * @param name 资源名称
     * @return 提取到的扩展名 (含点号)，无扩展名时返回空字符串
     */
    private String getExtFromName(String name) {
        // 经 aapt2 进行资源压缩时，图片的 .9.png 扩展名始终会被保留
        if (name.contains(".9.png")) {
            return ".9.png";
        }

        int lastDot = name.lastIndexOf('.');
        if (lastDot != -1) {
            return name.substring(lastDot);
        }

        return "";
    }

    /**
     * 获取该资源对应的 ZIP 条目
     *
     * @return ZIP 条目，若资源来自普通文件则返回 {@code null}
     */
    public @Nullable IZipEntry getZipEntry() {
        return zipEntry;
    }

    /**
     * 设置该资源对应的 ZIP 条目
     *
     * @param zipEntry ZIP 条目，可为 {@code null}
     */
    void setZipEntry(@Nullable IZipEntry zipEntry) {
        this.zipEntry = zipEntry;
    }

    /**
     * 获取关联的反编译器实例
     *
     * @return 反编译器实例
     */
    public JadxDecompiler getDecompiler() {
        return decompiler;
    }

    /**
     * 返回资源文件的字符串表示，包含名称与类型
     *
     * @return 字符串表示
     */
    @Override
    public String toString() {
        return "ResourceFile{name='" + name + '\'' + ", type=" + type + '}';
    }
}
