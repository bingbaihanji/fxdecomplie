package com.bingbaihanji.fxdecomplie.core.jadx.core.plugins;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.JadxCodeInput;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.loader.JadxPluginLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.JadxPluginOptions;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionDescription;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.versions.VerifyRequiredVersion;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Jadx 插件管理器，负责插件的加载、注册、卸载、解析和初始化
 * <p>
 * 管理所有插件的生命周期，处理插件间的冲突（通过 {@code provides} 机制），
 * 并维护已解析的插件集合供反编译器使用
 */
public class JadxPluginManager {
    private static final Logger LOG = LoggerFactory.getLogger(JadxPluginManager.class);

    private final JadxDecompiler decompiler;
    private final JadxPluginsData pluginsData;
    private final Set<String> disabledPlugins;
    private final SortedSet<PluginContext> allPlugins = new TreeSet<>();
    private final SortedSet<PluginContext> resolvedPlugins = new TreeSet<>();
    private final Map<String, String> provideSuggestions = new TreeMap<>();

    /** 插件添加监听器列表，当新插件被添加时通知所有监听器 */
    private final List<Consumer<PluginContext>> addPluginListeners = new ArrayList<>();

    /**
     * 构造插件管理器
     *
     * @param decompiler 关联的 Jadx 反编译器实例
     */
    public JadxPluginManager(JadxDecompiler decompiler) {
        this.decompiler = decompiler;
        this.pluginsData = new JadxPluginsData(decompiler, this);
        this.disabledPlugins = decompiler.getArgs().getDisabledPlugins();
    }

    /**
     * 添加冲突插件的解决方案建议
     * <p>
     * 当多个插件提供相同的功能（相同 provides 标识）时，通过此方法指定优先使用的插件
     *
     * @param provides  提供的功能标识
     * @param pluginId  建议优先使用的插件 ID
     */
    public void providesSuggestion(String provides, String pluginId) {
        provideSuggestions.put(provides, pluginId);
    }

    /**
     * 使用指定的插件加载器加载所有插件
     * <p>
     * 加载前会清空已有的插件列表，加载完成后自动执行冲突解析
     *
     * @param pluginLoader 插件加载器
     */
    public void load(JadxPluginLoader pluginLoader) {
        allPlugins.clear();
        VerifyRequiredVersion verifyRequiredVersion = new VerifyRequiredVersion();
        for (JadxPlugin plugin : pluginLoader.load()) {
            addPlugin(plugin, verifyRequiredVersion);
        }
        resolve();
    }

    /**
     * 注册单个插件
     * <p>
     * 如果插件已被禁用，则跳过注册注册完成后自动执行冲突解析
     *
     * @param plugin 要注册的插件实例
     */
    public void register(JadxPlugin plugin) {
        Objects.requireNonNull(plugin);
        PluginContext addedPlugin = addPlugin(plugin, new VerifyRequiredVersion());
        if (addedPlugin == null) {
            LOG.debug("Can't register plugin, it was disabled: {}", plugin.getPluginInfo().getPluginId());
            return;
        }
        LOG.debug("Register plugin: {}", addedPlugin.getPluginId());
        resolve();
    }

    /**
     * 添加插件到所有插件集合中
     * <p>
     * 会检查插件是否被禁用、Jadx 版本是否兼容，以及是否存在重复的插件 ID
     *
     * @param plugin                 要添加的插件
     * @param verifyRequiredVersion  版本验证器
     * @return 成功添加的插件上下文，若插件被禁用或版本不兼容则返回 null
     */
    private @Nullable PluginContext addPlugin(JadxPlugin plugin, VerifyRequiredVersion verifyRequiredVersion) {
        PluginContext pluginContext = new PluginContext(decompiler, pluginsData, plugin);
        if (disabledPlugins.contains(pluginContext.getPluginId())) {
            return null;
        }
        String requiredJadxVersion = pluginContext.getPluginInfo().getRequiredJadxVersion();
        if (!verifyRequiredVersion.isCompatible(requiredJadxVersion)) {
            LOG.warn("Plugin '{}' not loaded: requires '{}' jadx version which it is not compatible with current: {}",
                    pluginContext, requiredJadxVersion, verifyRequiredVersion.getJadxVersion());
            return null;
        }
        LOG.debug("Loading plugin: {}", pluginContext);
        if (!allPlugins.add(pluginContext)) {
            throw new IllegalArgumentException("Duplicate plugin id: " + pluginContext + ", class " + plugin.getClass());
        }
        addPluginListeners.forEach(l -> l.accept(pluginContext));
        return pluginContext;
    }

    /**
     * 根据插件 ID 卸载单个插件
     * <p>
     * 从所有插件集合中移除匹配的插件，并重新执行冲突解析
     *
     * @param pluginId 要卸载的插件 ID
     * @return 若成功移除插件则返回 true，否则返回 false
     */
    public boolean unload(String pluginId) {
        boolean result = allPlugins.removeIf(context -> {
            if (context.getPluginId().equals(pluginId)) {
                LOG.debug("Unload plugin: {}", pluginId);
                return true;
            }
            return false;
        });
        resolve();
        return result;
    }

    /** 获取所有已加载的插件上下文（包含未解析的冲突插件） */
    public SortedSet<PluginContext> getAllPluginContexts() {
        return allPlugins;
    }

    /** 获取已解析（消除冲突后）的插件上下文集合 */
    public SortedSet<PluginContext> getResolvedPluginContexts() {
        return resolvedPlugins;
    }

    /**
     * 解析插件冲突
     * <p>
     * 按 {@code provides} 标识对所有插件分组，每组仅保留一个插件：
     * 若组内仅有一个插件则直接保留 若存在多个，则优先选择建议插件
     * （通过 {@link #providesSuggestion}），否则选取组内第一个
     * 解析结果写入 {@link #resolvedPlugins}
     */
    private synchronized void resolve() {
        Map<String, List<PluginContext>> provides = allPlugins.stream()
                .collect(Collectors.groupingBy(p -> p.getPluginInfo().getProvides()));
        List<PluginContext> resolved = new ArrayList<>(provides.size());
        provides.forEach((provide, list) -> {
            if (list.size() == 1) {
                resolved.add(list.get(0));
            } else {
                String suggestion = provideSuggestions.get(provide);
                if (suggestion != null) {
                    list.stream().filter(p -> p.getPluginId().equals(suggestion))
                            .findFirst()
                            .ifPresent(resolved::add);
                } else {
                    PluginContext selected = list.get(0);
                    resolved.add(selected);
                    LOG.debug("Select providing '{}' plugin '{}', candidates: {}", provide, selected, list);
                }
            }
        });
        resolvedPlugins.clear();
        resolvedPlugins.addAll(resolved);
    }

    /** 初始化所有已加载的插件 */
    public void initAll() {
        init(allPlugins);
    }

    /** 初始化已解析（消除冲突后）的插件 */
    public void initResolved() {
        init(resolvedPlugins);
    }

    /**
     * 初始化指定的插件集合
     * <p>
     * 为未设置应用上下文的插件注入默认上下文并调用其初始化方法，
     * 随后对提供了选项的插件执行选项合法性校验单个插件初始化失败不影响其他插件
     *
     * @param pluginContexts 待初始化的插件上下文集合
     */
    public void init(SortedSet<PluginContext> pluginContexts) {
        AppContext defAppContext = buildDefaultAppContext();
        for (PluginContext context : pluginContexts) {
            try {
                if (context.getAppContext() == null) {
                    context.setAppContext(defAppContext);
                }
                context.init();
            } catch (Exception e) {
                LOG.error("Failed to init plugin: {}", context.getPluginId(), e);
            }
        }
        for (PluginContext context : pluginContexts) {
            JadxPluginOptions options = context.getOptions();
            if (options != null) {
                verifyOptions(context, options);
            }
        }
    }

    /** 卸载所有已加载的插件 */
    public void unloadAll() {
        unload(allPlugins);
    }

    /** 卸载已解析（消除冲突后）的插件 */
    public void unloadResolved() {
        unload(resolvedPlugins);
    }

    /**
     * 卸载指定的插件集合
     * <p>
     * 依次调用每个插件的卸载方法，单个插件卸载失败仅记录警告日志，不影响其他插件
     *
     * @param pluginContexts 待卸载的插件上下文集合
     */
    public void unload(SortedSet<PluginContext> pluginContexts) {
        for (PluginContext context : pluginContexts) {
            try {
                context.unload();
            } catch (Exception e) {
                LOG.warn("Failed to unload plugin: {}", context.getPluginId(), e);
            }
        }
    }

    /** 构建默认的应用上下文，注入 GUI 上下文与文件获取器 */
    private AppContext buildDefaultAppContext() {
        AppContext appContext = new AppContext();
        appContext.setGuiContext(null);
        appContext.setFilesGetter(decompiler.getArgs().getFilesGetter());
        return appContext;
    }

    /**
     * 校验插件选项描述的合法性
     * <p>
     * 要求选项描述非空、每个选项名以「插件 ID.」为前缀、描述文本非空、取值列表非空，
     * 任一条件不满足即抛出异常
     *
     * @param pluginContext 插件上下文
     * @param options       插件选项
     */
    private void verifyOptions(PluginContext pluginContext, JadxPluginOptions options) {
        String pluginId = pluginContext.getPluginId();
        List<OptionDescription> descriptions = options.getOptionsDescriptions();
        if (descriptions == null) {
            throw new IllegalArgumentException("Null option descriptions in plugin id: " + pluginId);
        }
        String prefix = pluginId + '.';
        descriptions.forEach(descObj -> {
            String optName = descObj.name();
            if (optName == null || !optName.startsWith(prefix)) {
                throw new IllegalArgumentException("Plugin option name should start with plugin id: '" + prefix + "', option: " + optName);
            }
            String desc = descObj.description();
            if (desc == null || desc.isEmpty()) {
                throw new IllegalArgumentException("Plugin option description not set, plugin: " + pluginId);
            }
            List<String> values = descObj.values();
            if (values == null) {
                throw new IllegalArgumentException("Plugin option values is null, option: " + optName + ", plugin: " + pluginId);
            }
        });
    }

    /**
     * 汇总所有已解析插件提供的代码输入
     *
     * @return 已解析插件的 {@link JadxCodeInput} 列表
     */
    public List<JadxCodeInput> getCodeInputs() {
        return getResolvedPluginContexts()
                .stream()
                .flatMap(p -> p.getCodeInputs().stream())
                .collect(Collectors.toList());
    }

    /**
     * 注册插件添加监听器
     * <p>
     * 注册后会立即对当前已添加的所有插件执行一次监听器回调
     *
     * @param listener 插件添加监听器
     */
    public void registerAddPluginListener(Consumer<PluginContext> listener) {
        this.addPluginListeners.add(listener);
        // 对已添加的插件立即执行监听器
        getAllPluginContexts().forEach(listener);
    }
}
