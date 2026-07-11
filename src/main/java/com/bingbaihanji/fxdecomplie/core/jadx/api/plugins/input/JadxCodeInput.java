package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input;

import java.nio.file.Path;
import java.util.List;

/**
 * 代码输入接口，用于加载输入文件并返回对应的代码加载器
 */
public interface JadxCodeInput {
    /**
     * 加载指定路径列表中的文件
     *
     * @param input 要加载的文件路径列表
     * @return 用于遍历加载结果的代码加载器
     */
    ICodeLoader loadFiles(List<Path> input);
}
