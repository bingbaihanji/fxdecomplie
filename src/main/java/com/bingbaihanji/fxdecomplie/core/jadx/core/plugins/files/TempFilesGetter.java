package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 临时文件目录提供者，将 jadx 的配置、缓存与临时文件统一存放在系统临时目录下
 * <p>
 * 所有文件位于一个进程启动时创建的临时根目录中，并在 JVM 退出时自动删除
 * 该类为单例，通过 {@link #INSTANCE} 访问
 * </p>
 */
public class TempFilesGetter implements IJadxFilesGetter {

    /** 全局单例实例 */
    public static final TempFilesGetter INSTANCE = new TempFilesGetter();

    private TempFilesGetter() {
    }

    /**
     * {@inheritDoc}
     *
     * @return 临时根目录下的 {@code config} 子目录
     */
    @Override
    public Path getConfigDir() {
        return makeSubDir("config");
    }

    /**
     * {@inheritDoc}
     *
     * @return 临时根目录下的 {@code cache} 子目录
     */
    @Override
    public Path getCacheDir() {
        return makeSubDir("cache");
    }

    /**
     * {@inheritDoc}
     *
     * @return 临时根目录下的 {@code tmp} 子目录
     */
    @Override
    public Path getTempDir() {
        return makeSubDir("tmp");
    }

    /**
     * 在临时根目录下创建 (若不存在)并返回指定名称的子目录
     *
     * @param subDir 子目录名称
     * @return 已确保存在的子目录路径
     */
    private Path makeSubDir(String subDir) {
        Path dir = TempRootHolder.TEMP_ROOT_DIR.resolve(subDir);
        IoUtils.makeDirs(dir);
        return dir;
    }

    /**
     * 临时根目录持有者，采用按需初始化的懒加载单例 (Initialization-on-demand holder)
     * 首次访问时创建临时根目录，并注册 JVM 退出时删除
     */
    private static final class TempRootHolder {
        /** 临时文件根目录 */
        public static final Path TEMP_ROOT_DIR;

        static {
            try {
                TEMP_ROOT_DIR = Files.createTempDirectory("jadx-temp-");
                TEMP_ROOT_DIR.toFile().deleteOnExit();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create temp directory", e);
            }
        }
    }
}
