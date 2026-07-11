package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourceFile;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ResourcesLoader;

import java.io.Closeable;
import java.io.File;
import java.util.List;

/**
 * 自定义资源加载器接口
 * <p>
 * 实现此接口以支持从文件中加载自定义资源（如图片、配置文件等），
 * 并将其添加到资源列表中加载完成后应关闭资源
 */
public interface CustomResourcesLoader extends Closeable {
    /**
     * 从文件中加载资源并添加到资源列表
     *
     * @param loader 资源加载器，用于辅助加载嵌套资源
     * @param list   待添加已加载资源的列表
     * @param file   要加载的文件
     * @return 如果文件加载成功则返回 true
     */
    boolean load(ResourcesLoader loader, List<ResourceFile> list, File file);
}
