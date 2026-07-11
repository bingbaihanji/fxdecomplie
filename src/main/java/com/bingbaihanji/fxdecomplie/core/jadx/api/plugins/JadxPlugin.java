package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxAfterLoadPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPreparePass;

/**
 * 所有 jadx 插件的基础接口
 * <br>
 * 要创建新插件，需实现此接口，并在 resources 目录下添加
 * {@code META-INF/services/jadx.api.plugins.JadxPlugin} 文件，
 * 文件内容为实现类的完整类名
 */
public interface JadxPlugin {

    /**
     * 提供插件信息的方法，例如插件名称和描述
     * 该方法可能会被多次调用
     *
     * @return 插件信息对象，包含插件的名称、描述等元数据
     */
    JadxPluginInfo getPluginInfo();

    /**
     * 初始化插件
     * <p>
     * 通过 {@link JadxPluginContext} 注册处理通道 (passes)、代码输入和选项
     * 对于耗时操作，建议使用 {@link JadxPreparePass} 或 {@link JadxAfterLoadPass} 代替
     *
     * @param context 插件上下文，用于注册通道、输入和选项
     */
    void init(JadxPluginContext context);

    /**
     * 插件卸载处理器
     * <p>
     * 可用于在插件卸载时清理资源，例如释放文件句柄、关闭连接等
     * 默认实现为空操作，子类可按需覆盖
     */
    default void unload() {
        // 可选方法，子类可按需覆盖
    }
}
