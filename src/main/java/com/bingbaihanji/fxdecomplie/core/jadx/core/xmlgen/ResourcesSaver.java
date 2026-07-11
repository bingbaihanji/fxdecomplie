package com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.security.IJadxSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.SaveCode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 资源文件保存器。
 * <p>
 * 负责将反编译后的资源文件（XML、图片、二进制数据等）保存到指定的输出目录。
 * 该类实现了 {@link Runnable} 接口，可在后台线程中异步执行资源保存操作。
 * </p>
 */
public class ResourcesSaver implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ResourcesSaver.class);

    /** 待保存的资源文件 */
    private final ResourceFile resourceFile;
    /** 输出目录 */
    private final File outDir;
    /** 安全检查器，用于防止路径遍历攻击 */
    private final IJadxSecurity security;

    /**
     * 构造资源保存器。
     *
     * @param decompiler  Jadx 反编译器实例，用于获取安全配置
     * @param outDir      资源输出目录
     * @param resourceFile 待保存的资源文件
     */
    public ResourcesSaver(JadxDecompiler decompiler, File outDir, ResourceFile resourceFile) {
        this.resourceFile = resourceFile;
        this.outDir = outDir;
        this.security = decompiler.getArgs().getSecurity();
    }

    @Override
    public void run() {
        try {
            saveResources(resourceFile.loadContent());
        } catch (StackOverflowError | Exception e) {
            LOG.warn("Failed to save resource: {}", resourceFile.getOriginalName(), e);
        }
    }

    /**
     * 递归保存资源容器。
     * <p>
     * 若资源数据类型为 RES_TABLE，则先将其保存为 res/values/public.xml，
     * 再递归保存所有子文件；否则直接保存到输出目录。
     * </p>
     *
     * @param rc 资源容器
     */
    private void saveResources(ResContainer rc) {
        if (rc == null) {
            return;
        }
        if (rc.getDataType() == ResContainer.DataType.RES_TABLE) {
            saveToFile(rc, new File(outDir, "res/values/public.xml"));
            for (ResContainer subFile : rc.getSubFiles()) {
                saveResources(subFile);
            }
        } else {
            save(rc, outDir);
        }
    }

    /**
     * 将资源容器保存到指定输出目录，并进行路径遍历安全检查。
     *
     * @param rc     资源容器
     * @param outDir 输出目录
     */
    private void save(ResContainer rc, File outDir) {
        File outFile = new File(outDir, rc.getFileName());
        if (!security.isInSubDirectory(outDir, outFile)) {
            LOG.error("Invalid resource name or path traversal attack detected: {}", outFile.getPath());
            return;
        }
        saveToFile(rc, outFile);
    }

    /**
     * 根据资源数据类型将资源内容写入目标文件。
     * <ul>
     *   <li>TEXT / RES_TABLE：以文本形式保存</li>
     *   <li>DECODED_DATA：以二进制字节数组形式保存</li>
     *   <li>RES_LINK：从链接的资源文件复制内容</li>
     * </ul>
     *
     * @param rc      资源容器
     * @param outFile 目标输出文件
     */
    private void saveToFile(ResContainer rc, File outFile) {
        switch (rc.getDataType()) {
            case TEXT:
            case RES_TABLE:
                SaveCode.save(rc.getText(), outFile);
                return;

            case DECODED_DATA:
                byte[] data = rc.getDecodedData();
                FileUtils.makeDirsForFile(outFile);
                try {
                    Files.write(outFile.toPath(), data);
                } catch (Exception e) {
                    LOG.warn("Resource '{}' not saved, got exception", rc.getName(), e);
                }
                return;

            case RES_LINK:
                ResourceFile resFile = rc.getResLink();
                FileUtils.makeDirsForFile(outFile);
                try {
                    saveResourceFile(resFile, outFile);
                } catch (Exception e) {
                    LOG.warn("Resource '{}' not saved, got exception", rc.getName(), e);
                }
                return;

            default:
                LOG.warn("Resource '{}' not saved, unknown type", rc.getName());
                break;
        }
    }

    /**
     * 通过解码资源文件流将内容复制到目标文件。
     * 若复制过程中发生异常，则删除已部分写入的文件以避免残留损坏文件。
     *
     * @param resFile 源资源文件
     * @param outFile 目标输出文件
     * @throws JadxException 当资源解码或保存失败时抛出
     */
    private void saveResourceFile(ResourceFile resFile, File outFile) throws JadxException {
        ResourcesLoader.decodeStream(resFile, (size, is) -> {
            Path target = outFile.toPath();
            try {
                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                Files.deleteIfExists(target); // 删除已部分写入的文件
                throw new JadxRuntimeException("Resource file save error", e);
            }
            return null;
        });
    }
}
