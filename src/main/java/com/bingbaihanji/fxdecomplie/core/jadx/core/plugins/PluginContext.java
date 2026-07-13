package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginContext;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPluginInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.data.IJadxFiles;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.data.IJadxPlugins;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.data.JadxPluginRuntimeData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.IJadxEvents;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.gui.JadxGuiContext;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.JadxCodeInput;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.MergeCodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.JadxPluginOptions;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionDescription;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.resources.IResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files.JadxFilesData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.ZipReader;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import org.jetbrains.annotations.Nullable;

import java.io.Closeable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 插件上下文，作为单个插件运行时的核心载体
 * <p>
 * 同时实现 {@link JadxPluginContext} (提供给插件访问反编译器能力的接口)与
 * {@link JadxPluginRuntimeData} (对外暴露插件运行时数据)，并负责在正确的类加载器下
 * 初始化/卸载插件 注册代码输入 处理选项与输入哈希等
 */
public class PluginContext implements JadxPluginContext, JadxPluginRuntimeData, Comparable<PluginContext> {
    /** 所属的反编译器实例 */
    private final JadxDecompiler decompiler;
    /** 全局插件数据 (所有插件的集合视图) */
    private final JadxPluginsData pluginsData;
    /** 当前插件实例 */
    private final JadxPlugin plugin;
    /** 当前插件的元信息 */
    private final JadxPluginInfo pluginInfo;
    /** 加载当前插件的类加载器 */
    private final ClassLoader pluginClassLoader;
    /** 插件注册的代码输入列表 */
    private final List<JadxCodeInput> codeInputs = new ArrayList<>();
    /** 应用级上下文 (提供 GUI 上下文 文件获取器等) */
    private AppContext appContext;
    /** 插件注册的选项 (可能为空) */
    private @Nullable JadxPluginOptions options;
    /** 插件注册的输入哈希提供者 (可能为空) */
    private @Nullable Supplier<String> inputsHashSupplier;

    /** 插件是否已初始化 */
    private boolean initialized;

    /**
     * 构造插件上下文
     *
     * @param decompiler  所属反编译器实例
     * @param pluginsData 全局插件数据
     * @param plugin      当前插件实例
     */
    PluginContext(JadxDecompiler decompiler, JadxPluginsData pluginsData, JadxPlugin plugin) {
        this.decompiler = decompiler;
        this.pluginsData = pluginsData;
        this.plugin = plugin;
        this.pluginInfo = plugin.getPluginInfo();
        this.pluginClassLoader = plugin.getClass().getClassLoader();
    }

    /**
     * 初始化插件 (在插件类加载器上下文中调用 {@link JadxPlugin#init})，并标记为已初始化
     */
    public void init() {
        classLoaderWrap(() -> {
            plugin.init(this);
            initialized = true;
        });
    }

    /**
     * 卸载插件 仅当插件已初始化时才执行卸载逻辑
     */
    public void unload() {
        if (initialized) {
            classLoaderWrap(plugin::unload);
        }
    }

    /**
     * 在插件类加载器作为线程上下文类加载器的环境下执行给定任务，执行完成后恢复原有类加载器
     *
     * @param task 需要在插件类加载器上下文中执行的任务
     */
    public void classLoaderWrap(Runnable task) {
        Thread thread = Thread.currentThread();
        ClassLoader prevClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(pluginClassLoader);
        try {
            task.run();
        } finally {
            thread.setContextClassLoader(prevClassLoader);
        }
    }

    /** 插件是否已初始化 */
    @Override
    public boolean isInitialized() {
        return initialized;
    }

    /** 获取反编译参数 */
    @Override
    public JadxArgs getArgs() {
        return decompiler.getArgs();
    }

    /** 获取所属的反编译器实例 */
    @Override
    public JadxDecompiler getDecompiler() {
        return decompiler;
    }

    /** 向反编译器注册一个自定义 pass */
    @Override
    public void addPass(JadxPass pass) {
        decompiler.addCustomPass(pass);
    }

    /** 注册一个代码输入 */
    @Override
    public void addCodeInput(JadxCodeInput codeInput) {
        this.codeInputs.add(codeInput);
    }

    /** 获取插件注册的所有代码输入 */
    @Override
    public List<JadxCodeInput> getCodeInputs() {
        return codeInputs;
    }

    /** 注册插件选项，并将当前已有的插件选项值应用到其中 */
    @Override
    public void registerOptions(JadxPluginOptions options) {
        try {
            this.options = Objects.requireNonNull(options);
            options.setOptions(getArgs().getPluginOptions());
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to apply options for plugin: " + getPluginId(), e);
        }
    }

    /** 注册输入哈希提供者，用于自定义计算插件输入的哈希值 */
    @Override
    public void registerInputsHashSupplier(Supplier<String> supplier) {
        this.inputsHashSupplier = supplier;
    }

    /**
     * 获取插件输入哈希：若注册了自定义提供者则使用之，否则回退到基于选项的默认哈希
     */
    @Override
    public String getInputsHash() {
        if (inputsHashSupplier == null) {
            return defaultOptionsHash();
        }
        try {
            return inputsHashSupplier.get();
        } catch (Exception e) {
            throw new JadxRuntimeException("Failed to get inputs hash for plugin: " + getPluginId(), e);
        }
    }

    /**
     * 计算基于插件选项的默认哈希 仅统计会影响代码生成的选项
     *  (跳过带有 {@link OptionFlag#NOT_CHANGING_CODE} 标志的选项)
     */
    private String defaultOptionsHash() {
        if (options == null) {
            return "";
        }
        Map<String, String> allOptions = getArgs().getPluginOptions();
        StringBuilder sb = new StringBuilder();
        for (OptionDescription optDesc : options.getOptionsDescriptions()) {
            if (!optDesc.getFlags().contains(OptionFlag.NOT_CHANGING_CODE)) {
                sb.append(':').append(allOptions.get(optDesc.name()));
            }
        }
        return ByteUtils.md5Sum(sb.toString());
    }

    /** 获取事件总线 */
    @Override
    public IJadxEvents events() {
        return decompiler.events();
    }

    /** 获取资源加载器 */
    @Override
    public IResourcesLoader getResourcesLoader() {
        return decompiler.getResourcesLoader();
    }

    /** 获取应用级上下文 */
    public AppContext getAppContext() {
        return appContext;
    }

    /** 设置应用级上下文 */
    public void setAppContext(AppContext appContext) {
        this.appContext = appContext;
    }

    /** 获取 GUI 上下文 (在非 GUI 环境下可能为空) */
    @Override
    public @Nullable JadxGuiContext getGuiContext() {
        return appContext.getGuiContext();
    }

    /** 获取当前插件实例 */
    @Override
    public JadxPlugin getPluginInstance() {
        return plugin;
    }

    /** 获取当前插件的元信息 */
    @Override
    public JadxPluginInfo getPluginInfo() {
        return pluginInfo;
    }

    /** 获取插件 ID */
    @Override
    public String getPluginId() {
        return pluginInfo.getPluginId();
    }

    /** 获取插件注册的选项 (可能为空) */
    @Override
    public @Nullable JadxPluginOptions getOptions() {
        return options;
    }

    /** 获取所有插件的集合视图 */
    @Override
    public IJadxPlugins plugins() {
        return pluginsData;
    }

    /** 获取当前插件对应的文件数据访问接口 */
    @Override
    public IJadxFiles files() {
        return new JadxFilesData(pluginInfo, appContext.getFilesGetter());
    }

    /**
     * 加载给定的代码文件，将所有已注册代码输入的加载结果合并为一个代码加载器
     *
     * @param files     待加载的文件路径列表
     * @param closeable 加载完成后需要关闭的资源 (可能为空)
     * @return 合并后的代码加载器
     */
    @Override
    public ICodeLoader loadCodeFiles(List<Path> files, @Nullable Closeable closeable) {
        return new MergeCodeLoader(
                Utils.collectionMap(codeInputs, codeInput -> codeInput.loadFiles(files)),
                closeable);
    }

    /** 获取 ZIP 读取器 */
    @Override
    public ZipReader getZipReader() {
        return decompiler.getZipReader();
    }

    /** 基于插件 ID 判断两个插件上下文是否相等 */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PluginContext)) {
            return false;
        }
        return this.getPluginId().equals(((PluginContext) other).getPluginId());
    }

    /** 基于插件 ID 计算哈希值 */
    @Override
    public int hashCode() {
        return getPluginId().hashCode();
    }

    /** 基于插件 ID 进行比较 (用于排序) */
    @Override
    public int compareTo(PluginContext other) {
        return this.getPluginId().compareTo(other.getPluginId());
    }

    /** 返回插件 ID 作为字符串表示 */
    @Override
    public String toString() {
        return getPluginId();
    }
}
