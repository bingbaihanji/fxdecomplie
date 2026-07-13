package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;

import java.nio.file.Path;

/**
 * 将所有 jadx 文件统一存放在同一个基础目录下的文件目录提供者
 * <p>
 * 配置 缓存与临时文件分别对应基础目录下的 {@code config} {@code cache} {@code temp} 子目录
 * </p>
 */
public class SingleDirFilesGetter implements IJadxFilesGetter {
    /** 存放所有 jadx 文件的基础目录 */
    private final Path baseDir;

    /**
     * 构造文件目录提供者
     *
     * @param baseDir 存放所有 jadx 文件的基础目录
     */
    public SingleDirFilesGetter(Path baseDir) {
        this.baseDir = baseDir;
    }

    /**
     * {@inheritDoc}
     *
     * @return 基础目录下的 {@code config} 子目录
     */
    @Override
    public Path getConfigDir() {
        return makeSubDir("config");
    }

    /**
     * {@inheritDoc}
     *
     * @return 基础目录下的 {@code cache} 子目录
     */
    @Override
    public Path getCacheDir() {
        return makeSubDir("cache");
    }

    /**
     * {@inheritDoc}
     *
     * @return 基础目录下的 {@code temp} 子目录
     */
    @Override
    public Path getTempDir() {
        return makeSubDir("temp");
    }

    /**
     * 在基础目录下创建 (若不存在)并返回指定名称的子目录
     *
     * @param subDir 子目录名称
     * @return 已确保存在的子目录路径
     */
    private Path makeSubDir(String subDir) {
        Path dir = baseDir.resolve(subDir);
        IoUtils.makeDirs(dir);
        return dir;
    }
}
