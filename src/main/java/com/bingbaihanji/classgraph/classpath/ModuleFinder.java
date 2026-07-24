 
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.util.CollectionUtils;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.*;

/** 用于查找可见模块的类 */
public class ModuleFinder {
    private final ReflectionUtils reflectionUtils;
    /** 系统模块引用 */
    private List<ModuleRef> systemModuleRefs;
    /** 非系统模块引用 */
    private List<ModuleRef> nonSystemModuleRefs;
    /** 如果为 true，则必须强制扫描 {@code java.class.path}，因为存在匿名模块层 */
    private boolean forceScanJavaClassPath;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 用于查找可见模块的类
     *
     * @param callStack
     *            调用栈
     * @param ScanConfig
     *            扫描规格
     * @param scanNonSystemModules
     *            是否扫描未命名和非系统模块
     * @param scanSystemModules
     *            是否扫描系统模块
     * @param log
     *            日志
     */
    public ModuleFinder(final Class<?>[] callStack, final ScanConfig ScanConfig, final boolean scanNonSystemModules,
                        final boolean scanSystemModules, final ReflectionUtils reflectionUtils, final LogNode log) {
        this.reflectionUtils = reflectionUtils;

        // 获取模块解析顺序
        List<ModuleRef> allModuleRefsList = null;
        if (ScanConfig.overrideModuleLayers == null) {
            // 从调用栈上的类和系统(JDK9+)查找模块引用
            if (callStack != null && callStack.length > 0) {
                allModuleRefsList = findModuleRefsFromCallstack(callStack, ScanConfig, scanNonSystemModules, log);
            }
        } else {
            if (log != null) {
                final LogNode subLog = log.log("Overriding module layers");
                for (final Object moduleLayer : ScanConfig.overrideModuleLayers) {
                    subLog.log(moduleLayer.toString());
                }
            }
            allModuleRefsList = findModuleRefs(new LinkedHashSet<>(ScanConfig.overrideModuleLayers), ScanConfig, log);
        }
        if (allModuleRefsList != null) {
            // 将模块拆分为系统模块和非系统模块
            systemModuleRefs = new ArrayList<>();
            nonSystemModuleRefs = new ArrayList<>();
            for (final ModuleRef moduleRef : allModuleRefsList) {
                if (moduleRef != null) {
                    final boolean isSystemModule = moduleRef.isSystemModule();
                    if (isSystemModule && scanSystemModules) {
                        systemModuleRefs.add(moduleRef);
                    } else if (!isSystemModule && scanNonSystemModules) {
                        nonSystemModuleRefs.add(moduleRef);
                    }
                }
            }
        }
        // 记录所有已识别的模块
        if (log != null) {
            if (scanSystemModules) {
                final LogNode sysSubLog = log.log("System modules found:");
                if (systemModuleRefs != null && !systemModuleRefs.isEmpty()) {
                    for (final ModuleRef moduleRef : systemModuleRefs) {
                        sysSubLog.log(moduleRef.toString());
                    }
                } else {
                    sysSubLog.log("[None]");
                }
            } else {
                log.log("Scanning of system modules is not enabled");
            }
            if (scanNonSystemModules) {
                final LogNode nonSysSubLog = log.log("Non-system modules found:");
                if (nonSystemModuleRefs != null && !nonSystemModuleRefs.isEmpty()) {
                    for (final ModuleRef moduleRef : nonSystemModuleRefs) {
                        nonSysSubLog.log(moduleRef.toString());
                    }
                } else {
                    nonSysSubLog.log("[None]");
                }
            } else {
                log.log("Scanning of non-system modules is not enabled");
            }
        }
    }

    /**
     * 获取系统模块的 {@link ModuleRef} 包装器
     *
     * @return 系统模块的 {@link ModuleRef} 包装器，如果没有找到模块则返回 null(例如在 JDK 7 或 8 上)
     */
    public List<ModuleRef> getSystemModuleRefs() {
        return systemModuleRefs;
    }

    /**
     * 获取非系统模块的 {@link ModuleRef} 包装器
     *
     * @return 非系统模块的 {@link ModuleRef} 包装器，如果没有找到模块则返回 null(例如在 JDK 7 或 8 上)
     */
    public List<ModuleRef> getNonSystemModuleRefs() {
        return nonSystemModuleRefs;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 强制扫描 java class path
     *
     * @return 如果为 true，则必须强制扫描 {@code java.class.path}，因为存在匿名模块层
     */
    public boolean forceScanJavaClassPath() {
        return forceScanJavaClassPath;
    }

    /**
     * 递归查找祖先层的拓扑排序顺序
     *
     * <p>
     * (JDK(截至 10.0.0.1)在 ModuleLayer#layers() 和 Configuration#configurations()
     * 中使用了一种有问题的(非拓扑的)DFS 排序来解析层，但当我向 Jigsaw 邮件列表报告此错误时，
     * Alan 没有看出问题所在)
     *
     * @param layer
     *            层
     * @param layerVisited
     *            已访问的层
     * @param parentLayers
     *            父层
     * @param layerOrderOut
     *            层顺序
     */
    private void findLayerOrder(final Object /* ModuleLayer */ layer,
                                final Set<Object> /* Set<ModuleLayer> */ layerVisited,
                                final Set<Object> /* Set<ModuleLayer> */ parentLayers,
                                final Deque<Object> /* Deque<ModuleLayer> */ layerOrderOut) {
        if (layerVisited.add(layer)) {
            @SuppressWarnings("unchecked") final List<Object> /* List<ModuleLayer> */ parents = (List<Object>) reflectionUtils
                    .invokeMethod(/* throwException = */ true, layer, "parents");
            if (parents != null) {
                parentLayers.addAll(parents);
                for (final Object parent : parents) {
                    findLayerOrder(parent, layerVisited, parentLayers, layerOrderOut);
                }
            }
            layerOrderOut.push(layer);
        }
    }

    /**
     * 获取层列表中所有可见的 ModuleReference
     *
     * @param layers
     *            层列表
     * @param ScanConfig
     *            扫描规格
     * @param log
     *            日志
     * @return 模块引用列表
     */
    private List<ModuleRef> findModuleRefs(final LinkedHashSet<Object> layers, final ScanConfig ScanConfig,
                                           final LogNode log) {
        if (layers.isEmpty()) {
            return Collections.emptyList();
        }

        // 遍历层 DAG 以查找层解析顺序
        final Deque<Object> /* Deque<ModuleLayer> */ layerOrder = new ArrayDeque<>();
        final Set<Object> /* Set<ModuleLayer */ parentLayers = new HashSet<>();
        for (final Object layer : layers) {
            if (layer != null) {
                findLayerOrder(layer, /* layerVisited = */ new HashSet<>(), parentLayers, layerOrder);
            }
        }
        if (ScanConfig.addedModuleLayers != null) {
            for (final Object layer : ScanConfig.addedModuleLayers) {
                if (layer != null) {
                    findLayerOrder(layer, /* layerVisited = */ new HashSet<>(), parentLayers, layerOrder);
                }
            }
        }

        // 如果 ScanConfig.ignoreParentModuleLayers 为 true，则从层顺序中移除父层
        List<Object> /* List<ModuleLayer> */ layerOrderFinal;
        if (ScanConfig.ignoreParentModuleLayers) {
            layerOrderFinal = new ArrayList<>();
            for (final Object layer : layerOrder) {
                if (!parentLayers.contains(layer)) {
                    layerOrderFinal.add(layer);
                }
            }
        } else {
            layerOrderFinal = new ArrayList<>(layerOrder);
        }

        // 在有序层中查找模块
        final Set<Object> /* Set<ModuleReference> */ addedModules = new HashSet<>();
        final LinkedHashSet<ModuleRef> moduleRefOrder = new LinkedHashSet<>();
        for (final Object /* ModuleLayer */ layer : layerOrderFinal) {
            final Object /* Configuration */ configuration = reflectionUtils
                    .invokeMethod(/* throwException = */ true, layer, "configuration");
            if (configuration != null) {
                // 从层配置中获取 ModuleReference
                @SuppressWarnings("unchecked") final Set<Object> /* Set<ResolvedModule> */ modules = (Set<Object>) reflectionUtils
                        .invokeMethod(/* throwException = */ true, configuration, "modules");
                if (modules != null) {
                    final List<ModuleRef> modulesInLayer = new ArrayList<>();
                    for (final Object /* ResolvedModule */ module : modules) {
                        final Object /* ModuleReference */ moduleReference = reflectionUtils
                                .invokeMethod(/* throwException = */ true, module, "reference");
                        if (moduleReference != null && addedModules.add(moduleReference)) {
                            try {
                                modulesInLayer.add(new ModuleRef(moduleReference, layer, reflectionUtils));
                            } catch (final IllegalArgumentException e) {
                                if (log != null) {
                                    log.log("Exception while creating ModuleRef for module " + moduleReference, e);
                                }
                            }
                        }
                    }
                    // 按名称对层中的模块排序
                    CollectionUtils.sortIfNotEmpty(modulesInLayer);
                    moduleRefOrder.addAll(modulesInLayer);
                }
            }
        }
        return new ArrayList<>(moduleRefOrder);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取所有层中所有可见的 ModuleReference，给定一个栈帧 {@code Class<?>} 引用数组
     *
     * @param callStack
     *            调用栈
     * @param ScanConfig
     *            扫描规格
     * @param scanNonSystemModules
     *            是否包含未命名和非系统模块
     * @param log
     *            日志
     * @return 模块引用列表
     */
    private List<ModuleRef> findModuleRefsFromCallstack(final Class<?>[] callStack, final ScanConfig ScanConfig,
                                                        final boolean scanNonSystemModules, final LogNode log) {
        final LinkedHashSet<Object> layers = new LinkedHashSet<>();
        if (callStack != null) {
            for (final Class<?> stackFrameClass : callStack) {
                final Object /* Module */ module = reflectionUtils.invokeMethod(/* throwException = */ false,
                        stackFrameClass, "getModule");
                if (module != null) {
                    final Object /* ModuleLayer */ layer = reflectionUtils.invokeMethod(/* throwException = */ true,
                            module, "getLayer");
                    if (layer != null) {
                        layers.add(layer);
                    } else if (scanNonSystemModules) {
                        // getLayer() 对未命名模块返回 null -- 如果返回了 null 则仍将其添加到列表，
                        // 以便我们可以从 java.class.path 获取类
                        forceScanJavaClassPath = true;
                    }
                }
            }
        }
        // 添加引导层中的系统模块，如果它们尚未在堆栈跟踪中找到
        Class<?> moduleLayerClass = null;
        try {
            moduleLayerClass = Class.forName("java.lang.ModuleLayer");
        } catch (ClassNotFoundException | LinkageError e) {
            // 忽略
        }
        if (moduleLayerClass != null) {
            final Object /* ModuleLayer */ bootLayer = reflectionUtils
                    .invokeStaticMethod(/* throwException = */ false, moduleLayerClass, "boot");
            if (bootLayer != null) {
                layers.add(bootLayer);
            } else if (scanNonSystemModules) {
                // getLayer() 对未命名模块返回 null -- 如果返回了 null 则仍将其添加到列表，
                // 以便我们可以从 java.class.path 获取类(我不确定引导层是否真的可能
                // 为 null，但这里是为了完整性)
                forceScanJavaClassPath = true;
            }
        }
        return findModuleRefs(layers, ScanConfig, log);
    }
}
