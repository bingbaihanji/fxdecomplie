package com.bingbaihanji.fxdecomplie.core.jadx.core.export;

import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.IoUtils;

import java.io.File;

/**
 * 反编译输出目录配置，封装源码输出目录和资源输出目录
 */
public class OutDirs {
    /** 源码输出目录 */
    private final File srcOutDir;
    /** 资源输出目录 */
    private final File resOutDir;

    /**
     * 构造输出目录配置
     *
     * @param srcOutDir 源码输出目录
     * @param resOutDir 资源输出目录
     */
    public OutDirs(File srcOutDir, File resOutDir) {
        this.srcOutDir = srcOutDir;
        this.resOutDir = resOutDir;
    }

    /**
     * 获取源码输出目录
     */
    public File getSrcOutDir() {
        return srcOutDir;
    }

    /**
     * 获取资源输出目录
     */
    public File getResOutDir() {
        return resOutDir;
    }

    /**
     * 确保源码和资源输出目录存在，不存在则自动创建
     */
    public void makeDirs() {
        IoUtils.makeDirs(srcOutDir);
        IoUtils.makeDirs(resOutDir);
    }
}
