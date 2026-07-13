package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.JadxCodeInput;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.JadxPluginOptions;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.List;

/**
 * 插件运行时数据
 * <p>
 * 承载单个插件在运行期间的状态与相关信息，
 * 包括插件实例 代码输入 配置选项等
 */
public interface JadxPluginRuntimeData {
    /**
     * 判断插件是否已完成初始化
     *
     * @return 已初始化返回 true
     */
    boolean isInitialized();

    /**
     * 获取插件 ID
     *
     * @return 插件唯一标识
     */
    String getPluginId();

    /**
     * 获取插件实例
     *
     * @return 插件对象
     */
    JadxPlugin getPluginInstance();

    /**
     * 获取插件元信息
     *
     * @return 插件信息对象
     */
    JadxPluginInfo getPluginInfo();

    /**
     * 获取该插件提供的所有代码输入
     *
     * @return 代码输入列表
     */
    List<JadxCodeInput> getCodeInputs();

    /**
     * 获取插件的配置选项
     *
     * @return 插件选项，可能为 null
     */
    @Nullable
    JadxPluginOptions getOptions();

    /**
     * 获取输入内容的哈希值
     *
     * @return 输入哈希字符串
     */
    String getInputsHash();

    /**
     * 便捷方法，用于简化从自定义文件加载代码的过程
     *
     * @param files     待加载的文件路径列表
     * @param closeable 加载完成后需关闭的资源，可为 null
     * @return 代码加载器
     */
    ICodeLoader loadCodeFiles(List<Path> files, @Nullable Closeable closeable);
}
