package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.NodeDeclareRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarNode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.CustomResourcesLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.JadxPlugin;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.events.IJadxEvents;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.ICodeLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.JadxCodeInput;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.JadxPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxAfterLoadPass;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.pass.types.JadxPassType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.utils.tasks.ITaskExecutor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.SaveCode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.ExportGradle;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.OutDirs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.JadxPluginManager;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.PluginContext;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.events.JadxEventsImpl;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.DecompilerScheduler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.FileUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.tasks.TaskExecutor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.xmlgen.ResourcesSaver;
import com.bingbaihanji.fxdecomplie.core.jadx.zip.ZipReader;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Jadx 反编译器的核心入口类，提供加载、反编译和保存功能。
 * <p>
 * 实现了 {@link Closeable} 接口，支持 try-with-resources 自动释放资源。
 * <p>
 * Jadx API 使用示例：
 *
 * <pre>
 * <code>
 *
 * JadxArgs args = new JadxArgs();
 * args.getInputFiles().add(new File("test.apk"));
 * args.setOutDir(new File("jadx-test-output"));
 * try (JadxDecompiler jadx = new JadxDecompiler(args)) {
 *    jadx.load();
 *    jadx.save();
 * }
 * </code>
 * </pre>
 * <p>
 * 除了使用 save() 方法保存所有反编译结果外，也可以遍历反编译后的类逐个处理：
 *
 * <pre>
 * <code>
 *
 *  for(JavaClass cls : jadx.getClasses()) {
 *      System.out.println(cls.getCode());
 *  }
 * </code>
 * </pre>
 */
public final class JadxDecompiler implements Closeable {
    /** 日志记录器 */
    private static final Logger LOG = LoggerFactory.getLogger(JadxDecompiler.class);

    /** 反编译参数配置 */
    private final JadxArgs args;
    /** 插件管理器 */
    private final JadxPluginManager pluginManager;
    /** 已加载的代码输入源列表 */
    private final List<ICodeLoader> loadedInputs = new ArrayList<>();
    /** ZIP 文件读取器 */
    private final ZipReader zipReader;
    /** 反编译调度器，用于构建反编译批次 */
    private final IDecompileScheduler decompileScheduler = new DecompilerScheduler();
    /** 资源加载器 */
    private final ResourcesLoader resourcesLoader;
    /** 自定义代码加载器列表 */
    private final List<ICodeLoader> customCodeLoaders = new ArrayList<>();
    /** 自定义资源加载器列表 */
    private final List<CustomResourcesLoader> customResourcesLoaders = new ArrayList<>();
    /** 自定义处理阶段映射表，按阶段类型分组存储 */
    private final Map<JadxPassType, List<JadxPass>> customPasses = new HashMap<>();
    /** 需要在关闭时释放的资源列表 */
    private final List<Closeable> closeableList = new ArrayList<>();
    /** 根节点，包含所有已加载的类和资源信息 */
    private RootNode root;
    /** 反编译后的 Java 类列表（缓存） */
    private List<JavaClass> classes;
    /** 资源文件列表（缓存） */
    private List<ResourceFile> resources;
    /** 事件系统实现 */
    private IJadxEvents events = new JadxEventsImpl();

    /** 使用默认参数创建 JadxDecompiler 实例 */
    public JadxDecompiler() {
        this(new JadxArgs());
    }

    /** 使用指定参数创建 JadxDecompiler 实例 */
    public JadxDecompiler(JadxArgs args) {
        this.args = Objects.requireNonNull(args);
        this.pluginManager = new JadxPluginManager(this);
        this.resourcesLoader = new ResourcesLoader(this);
        this.zipReader = new ZipReader(args.getSecurity());
    }

    /** 获取 Jadx 版本号 */
    public static String getVersion() {
        return Jadx.getVersion();
    }

    /**
     * 加载并初始化反编译器。
     * <p>
     * 执行流程：重置状态 -> 验证参数 -> 加载插件 -> 加载输入文件 -> 初始化根节点 ->
     * 加载类和资源 -> 初始化类路径 -> 合并处理阶段 -> 运行预反编译阶段 -> 初始化各处理阶段
     */
    public void load() {
        reset();
        JadxArgsValidator.validate(this);
        LOG.info("loading ...");
        FileUtils.updateTempRootDir(args.getFilesGetter().getTempDir());
        loadPlugins();
        loadInputFiles();

        root = new RootNode(this);
        root.init();
        // 加载类和资源
        root.loadClasses(loadedInputs);
        root.loadResources(resourcesLoader, getResources());
        root.finishClassLoad();
        root.initClassPath();
        // 初始化处理阶段
        root.mergePasses(customPasses);
        root.runPreDecompileStage();
        root.initPasses();
        loadFinished();
    }

    /**
     * 重新加载处理阶段和插件，但不重新处理类和输入文件。
     * <p>
     * 适用于需要在不重新加载输入文件的情况下刷新插件和处理逻辑的场景。
     */
    public void reloadPasses() {
        LOG.info("reloading (passes only) ...");
        customPasses.clear();
        root.resetPasses();
        events.reset();
        unloadPlugins();

        loadPlugins();
        root.mergePasses(customPasses);
        root.restartVisitors();
        root.initPasses();
        loadFinished();
    }

    /** 加载输入文件，通过插件和自定义代码加载器解析输入路径 */
    private void loadInputFiles() {
        loadedInputs.clear();
        List<Path> inputPaths = Utils.collectionMap(args.getInputFiles(), File::toPath);
        List<Path> inputFiles = FileUtils.expandDirs(inputPaths);
        long start = System.currentTimeMillis();
        for (PluginContext plugin : pluginManager.getResolvedPluginContexts()) {
            for (JadxCodeInput codeLoader : plugin.getCodeInputs()) {
                try {
                    ICodeLoader loader = codeLoader.loadFiles(inputFiles);
                    if (loader != null && !loader.isEmpty()) {
                        loadedInputs.add(loader);
                    }
                } catch (Exception e) {
                    LOG.warn("Failed to load code for plugin: {}", plugin, e);
                }
            }
        }
        loadedInputs.addAll(customCodeLoaders);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Loaded using {} inputs plugin in {} ms", loadedInputs.size(), System.currentTimeMillis() - start);
        }
    }

    /** 重置反编译器状态，卸载插件并清空根节点、类列表和资源列表 */
    private void reset() {
        unloadPlugins();
        root = null;
        classes = null;
        resources = null;
        events.reset();
    }

    /** 关闭反编译器，释放所有已加载的资源和临时文件 */
    @Override
    public void close() {
        reset();
        closeAll(loadedInputs);
        closeAll(customCodeLoaders);
        closeAll(customResourcesLoaders);
        closeAll(closeableList);
        FileUtils.deleteDirIfExists(args.getFilesGetter().getTempDir());
        args.close();
        FileUtils.clearTempRootDir();
    }

    private void closeAll(List<? extends Closeable> list) {
        try {
            for (Closeable closeable : list) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    LOG.warn("Fail to close '{}'", closeable, e);
                }
            }
        } finally {
            list.clear();
        }
    }

    /** 加载并初始化插件 */
    private void loadPlugins() {
        pluginManager.providesSuggestion("java-input", args.isUseDxInput() ? "java-convert" : "java-input");
        pluginManager.load(args.getPluginLoader());
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resolved plugins: {}", pluginManager.getResolvedPluginContexts());
        }
        pluginManager.initResolved();
        if (LOG.isDebugEnabled()) {
            List<String> passes = customPasses.values().stream().flatMap(Collection::stream)
                    .map(p -> p.getInfo().getName()).collect(Collectors.toList());
            LOG.debug("Loaded custom passes: {} {}", passes.size(), passes);
        }
    }

    /** 卸载已解析的插件 */
    private void unloadPlugins() {
        pluginManager.unloadResolved();
    }

    /** 加载完成回调，触发加载后处理阶段的初始化 */
    private void loadFinished() {
        LOG.debug("Load finished");
        List<JadxPass> list = customPasses.get(JadxAfterLoadPass.TYPE);
        if (list != null) {
            for (JadxPass pass : list) {
                ((JadxAfterLoadPass) pass).init(this);
            }
        }
    }

    /** 注册自定义插件 */
    @SuppressWarnings("unused")
    public void registerPlugin(JadxPlugin plugin) {
        pluginManager.register(plugin);
    }

    /** 保存所有反编译结果（源码和资源）到输出目录 */
    public void save() {
        save(!args.isSkipSources(), !args.isSkipResources());
    }

    /** 带进度回调的保存方法，按指定间隔报告进度 */
    @SuppressWarnings("BusyWait")
    public void save(int intervalInMillis, ProgressListener listener) {
        try {
            ITaskExecutor tasks = getSaveTaskExecutor();
            tasks.execute();
            long total = tasks.getTasksCount();
            while (tasks.isRunning()) {
                listener.progress(tasks.getProgress(), total);
                Thread.sleep(intervalInMillis);
            }
        } catch (InterruptedException e) {
            LOG.error("Save interrupted", e);
            Thread.currentThread().interrupt();
        }
    }

    /** 仅保存反编译后的源码 */
    public void saveSources() {
        save(true, false);
    }

    /** 仅保存资源文件 */
    public void saveResources() {
        save(false, true);
    }

    private void save(boolean saveSources, boolean saveResources) {
        ITaskExecutor executor = getSaveTasks(saveSources, saveResources);
        executor.execute();
        executor.awaitTermination();
    }

    public ITaskExecutor getSaveTaskExecutor() {
        return getSaveTasks(!args.isSkipSources(), !args.isSkipResources());
    }

    @Deprecated(forRemoval = true)
    public ExecutorService getSaveExecutor() {
        ITaskExecutor executor = getSaveTaskExecutor();
        executor.execute();
        return executor.getInternalExecutor();
    }

    @Deprecated(forRemoval = true)
    public List<Runnable> getSaveTasks() {
        return Collections.singletonList(this::save);
    }

    private TaskExecutor getSaveTasks(boolean saveSources, boolean saveResources) {
        if (root == null) {
            throw new JadxRuntimeException("No loaded files");
        }
        OutDirs outDirs;
        ExportGradle gradleExport;
        if (args.getExportGradleType() != null) {
            gradleExport = new ExportGradle(root, args.getOutDir(), getResources());
            outDirs = gradleExport.init();
        } else {
            gradleExport = null;
            outDirs = new OutDirs(args.getOutDirSrc(), args.getOutDirRes());
            outDirs.makeDirs();
        }

        TaskExecutor executor = new TaskExecutor();
        executor.setThreadsCount(args.getThreadsCount());
        if (saveResources) {
            // 先保存资源，因为反编译过程可能会中途停止或失败
            appendResourcesSaveTasks(executor, outDirs.getResOutDir());
        }
        if (saveSources) {
            appendSourcesSave(executor, outDirs.getSrcOutDir());
        }
        if (gradleExport != null) {
            executor.addSequentialTask(gradleExport::generateGradleFiles);
        }
        return executor;
    }

    private void appendResourcesSaveTasks(ITaskExecutor executor, File outDir) {
        if (args.isSkipFilesSave()) {
            return;
        }
        // 优先处理 AndroidManifest.xml 以加载完整的资源 id 表
        for (ResourceFile resourceFile : getResources()) {
            if (resourceFile.getType() == ResourceType.MANIFEST) {
                new ResourcesSaver(this, outDir, resourceFile).run();
                break;
            }
        }
        Set<String> inputFileNames = args.getInputFiles().stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toSet());
        Set<String> codeSources = collectCodeSources();

        List<Runnable> tasks = new ArrayList<>();
        for (ResourceFile resourceFile : getResources()) {
            ResourceType resType = resourceFile.getType();
            if (resType == ResourceType.MANIFEST) {
                // 已处理过，跳过
                continue;
            }
            String resOriginalName = resourceFile.getOriginalName();
            if (resType != ResourceType.ARSC && inputFileNames.contains(resOriginalName)) {
                // 忽略由输入文件生成的资源
                continue;
            }
            if (codeSources.contains(resOriginalName)) {
                // 不输出代码源资源（.dex、.class 等）
                // 不要信任文件扩展名，仅使用被设置为类输入的源
                continue;
            }
            tasks.add(new ResourcesSaver(this, outDir, resourceFile));
        }
        executor.addParallelTasks(tasks);
    }

    private Set<String> collectCodeSources() {
        Set<String> set = new HashSet<>();
        for (ClassNode cls : root.getClasses(true)) {
            if (cls.getClsData() == null) {
                // 排除合成类
                continue;
            }
            String inputFileName = cls.getInputFileName();
            if (inputFileName.endsWith(".class")) {
                // 截断 .class 名称以获取源 .jar 文件
                // 当前模板："<可选输入文件>:<.jar>:<完整类名>"
                // TODO: 添加属性以设置文件名或对资源名的引用
                int endIdx = inputFileName.lastIndexOf(':');
                if (endIdx != -1) {
                    int startIdx = inputFileName.lastIndexOf(':', endIdx - 1) + 1;
                    inputFileName = inputFileName.substring(startIdx, endIdx);
                }
            }
            set.add(inputFileName);
        }
        return set;
    }

    private void appendSourcesSave(ITaskExecutor executor, File outDir) {
        List<JavaClass> classes = getClasses();
        List<JavaClass> processQueue = filterClasses(classes);
        List<List<JavaClass>> batches;
        try {
            batches = decompileScheduler.buildBatches(processQueue);
        } catch (Exception e) {
            throw new JadxRuntimeException("Decompilation batches build failed", e);
        }
        List<Runnable> decompileTasks = new ArrayList<>(batches.size());
        for (List<JavaClass> decompileBatch : batches) {
            decompileTasks.add(() -> {
                for (JavaClass cls : decompileBatch) {
                    try {
                        ClassNode clsNode = cls.getClassNode();
                        ICodeInfo code = clsNode.getCode();
                        SaveCode.save(outDir, clsNode, code);
                    } catch (Exception e) {
                        LOG.error("Error saving class: {}", cls, e);
                    }
                }

            });
        }
        executor.addParallelTasks(decompileTasks);
    }

    private List<JavaClass> filterClasses(List<JavaClass> classes) {
        Predicate<String> classFilter = args.getClassFilter();
        List<JavaClass> list = new ArrayList<>(classes.size());
        for (JavaClass cls : classes) {
            ClassNode clsNode = cls.getClassNode();
            if (clsNode.contains(AFlag.DONT_GENERATE)) {
                continue;
            }
            if (classFilter != null && !classFilter.test(clsNode.getClassInfo().getFullName())) {
                if (!args.isIncludeDependencies()) {
                    clsNode.add(AFlag.DONT_GENERATE);
                }
                continue;
            }
            list.add(cls);
        }
        return list;
    }

    public synchronized List<JavaClass> getClasses() {
        if (root == null) {
            return Collections.emptyList();
        }
        if (classes == null) {
            List<ClassNode> classNodeList = root.getClasses();
            List<JavaClass> clsList = new ArrayList<>(classNodeList.size());
            for (ClassNode classNode : classNodeList) {
                if (!classNode.contains(AFlag.DONT_GENERATE) && !classNode.isInner()) {
                    clsList.add(convertClassNode(classNode));
                }
            }
            classes = Collections.unmodifiableList(clsList);
        }
        return classes;
    }

    public List<JavaClass> getClassesWithInners() {
        return Utils.collectionMap(root.getClasses(), this::convertClassNode);
    }

    public synchronized List<ResourceFile> getResources() {
        if (resources == null) {
            if (root == null) {
                return Collections.emptyList();
            }
            resources = resourcesLoader.load(root);
        }
        return resources;
    }

    public List<JavaPackage> getPackages() {
        return Utils.collectionMap(root.getPackages(), this::convertPackageNode);
    }

    public int getErrorsCount() {
        if (root == null) {
            return 0;
        }
        return root.getErrorsCounter().getErrorCount();
    }

    public int getWarnsCount() {
        if (root == null) {
            return 0;
        }
        return root.getErrorsCounter().getWarnsCount();
    }

    public void printErrorsReport() {
        if (root == null) {
            return;
        }
        root.getClsp().printMissingClasses();
        root.getErrorsCounter().printReport();
    }

    /**
     * 内部 API，不稳定，不建议外部使用！
     */
    @ApiStatus.Internal
    public RootNode getRoot() {
        return root;
    }

    /**
     * 根据 ClassNode 获取对应的 JavaClass，不触发加载和反编译
     */
    @ApiStatus.Internal
    synchronized JavaClass convertClassNode(ClassNode cls) {
        JavaClass javaClass = cls.getJavaNode();
        if (javaClass == null) {
            javaClass = cls.isInner()
                    ? new JavaClass(cls, convertClassNode(cls.getParentClass()))
                    : new JavaClass(cls, this);
            cls.setJavaNode(javaClass);
        }
        return javaClass;
    }

    @ApiStatus.Internal
    synchronized JavaField convertFieldNode(FieldNode fld) {
        JavaField javaField = fld.getJavaNode();
        if (javaField == null) {
            JavaClass parentCls = convertClassNode(fld.getParentClass());
            javaField = new JavaField(parentCls, fld);
            fld.setJavaNode(javaField);
        }
        return javaField;
    }

    @ApiStatus.Internal
    synchronized JavaMethod convertMethodNode(MethodNode mth) {
        JavaMethod javaMethod = mth.getJavaNode();
        if (javaMethod == null) {
            javaMethod = new JavaMethod(convertClassNode(mth.getParentClass()), mth);
            mth.setJavaNode(javaMethod);
        }
        return javaMethod;
    }

    @ApiStatus.Internal
    synchronized JavaPackage convertPackageNode(PackageNode pkg) {
        JavaPackage foundPkg = pkg.getJavaNode();
        if (foundPkg != null) {
            return foundPkg;
        }
        List<JavaClass> clsList = Utils.collectionMap(pkg.getClasses(), this::convertClassNode);
        List<JavaClass> clsListNoDup = Utils.collectionMap(pkg.getClassesNoDup(), this::convertClassNode);
        int subPkgsCount = pkg.getSubPackages().size();
        List<JavaPackage> subPkgs = subPkgsCount == 0 ? Collections.emptyList() : new ArrayList<>(subPkgsCount);
        JavaPackage javaPkg = new JavaPackage(pkg, clsList, clsListNoDup, subPkgs);
        if (subPkgsCount != 0) {
            // add subpackages after parent to avoid endless recursion
            for (PackageNode subPackage : pkg.getSubPackages()) {
                subPkgs.add(convertPackageNode(subPackage));
            }
        }
        pkg.setJavaNode(javaPkg);
        return javaPkg;
    }

    @Nullable
    public JavaClass searchJavaClassByOrigFullName(String fullName) {
        return getRoot().getClasses().stream()
                .filter(cls -> cls.getClassInfo().getFullName().equals(fullName))
                .findFirst()
                .map(this::convertClassNode)
                .orElse(null);
    }

    @Nullable
    public ClassNode searchClassNodeByOrigFullName(String fullName) {
        return getRoot().getClasses().stream()
                .filter(cls -> cls.getClassInfo().getFullName().equals(fullName))
                .findFirst()
                .orElse(null);
    }

    // 如果类包含 DONT_GENERATE 标志，则返回其父类
    @Nullable
    public JavaClass searchJavaClassOrItsParentByOrigFullName(String fullName) {
        ClassNode node = getRoot().getClasses().stream()
                .filter(cls -> cls.getClassInfo().getFullName().equals(fullName))
                .findFirst()
                .orElse(null);
        if (node != null) {
            if (node.contains(AFlag.DONT_GENERATE)) {
                return convertClassNode(node.getTopParentClass());
            } else {
                return convertClassNode(node);
            }
        }
        return null;
    }

    @Nullable
    public JavaClass searchJavaClassByAliasFullName(String fullName) {
        return getRoot().getClasses().stream()
                .filter(cls -> cls.getClassInfo().getAliasFullName().equals(fullName))
                .findFirst()
                .map(this::convertClassNode)
                .orElse(null);
    }

    @Nullable
    public JavaNode getJavaNodeByRef(ICodeNodeRef ann) {
        return getJavaNodeByCodeAnnotation(null, ann);
    }

    @Nullable
    public JavaNode getJavaNodeByCodeAnnotation(@Nullable ICodeInfo codeInfo, @Nullable ICodeAnnotation ann) {
        if (ann == null) {
            return null;
        }
        switch (ann.getAnnType()) {
            case CLASS:
                return convertClassNode((ClassNode) ann);
            case METHOD:
                return convertMethodNode((MethodNode) ann);
            case FIELD:
                return convertFieldNode((FieldNode) ann);
            case PKG:
                return convertPackageNode((PackageNode) ann);
            case DECLARATION:
                return getJavaNodeByCodeAnnotation(codeInfo, ((NodeDeclareRef) ann).getNode());
            case VAR:
                return resolveVarNode((VarNode) ann);
            case VAR_REF:
                return resolveVarRef(codeInfo, (VarRef) ann);
            case OFFSET:
                // 偏移注解没有对应的 Java 节点对象
                return null;
            default:
                throw new JadxRuntimeException("Unknown annotation type: " + ann.getAnnType() + ", class: " + ann.getClass());
        }
    }

    private JavaVariable resolveVarNode(VarNode varNode) {
        JavaMethod javaNode = convertMethodNode(varNode.getMth());
        return new JavaVariable(javaNode, varNode);
    }

    @Nullable
    private JavaVariable resolveVarRef(ICodeInfo codeInfo, VarRef varRef) {
        if (codeInfo == null) {
            throw new JadxRuntimeException("Missing code info for resolve VarRef: " + varRef);
        }
        ICodeAnnotation varNodeAnn = codeInfo.getCodeMetadata().getAt(varRef.getRefPos());
        if (varNodeAnn != null && varNodeAnn.getAnnType() == ICodeAnnotation.AnnType.DECLARATION) {
            ICodeNodeRef nodeRef = ((NodeDeclareRef) varNodeAnn).getNode();
            if (nodeRef.getAnnType() == ICodeAnnotation.AnnType.VAR) {
                return resolveVarNode((VarNode) nodeRef);
            }
        }
        return null;
    }

    List<JavaNode> convertNodes(Collection<? extends ICodeNodeRef> nodesList) {
        return nodesList.stream()
                .map(this::getJavaNodeByRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Nullable
    public JavaNode getJavaNodeAtPosition(ICodeInfo codeInfo, int pos) {
        ICodeAnnotation ann = codeInfo.getCodeMetadata().getAt(pos);
        return getJavaNodeByCodeAnnotation(codeInfo, ann);
    }

    @Nullable
    public JavaNode getClosestJavaNode(ICodeInfo codeInfo, int pos) {
        ICodeAnnotation ann = codeInfo.getCodeMetadata().getClosestUp(pos);
        return getJavaNodeByCodeAnnotation(codeInfo, ann);
    }

    @Nullable
    public JavaNode getEnclosingNode(ICodeInfo codeInfo, int pos) {
        ICodeNodeRef obj = codeInfo.getCodeMetadata().getNodeAt(pos);
        if (obj == null) {
            return null;
        }
        return getJavaNodeByRef(obj);
    }

    public void reloadCodeData() {
        root.notifyCodeDataListeners();
    }

    public JadxArgs getArgs() {
        return args;
    }

    public JadxPluginManager getPluginManager() {
        return pluginManager;
    }

    public IDecompileScheduler getDecompileScheduler() {
        return decompileScheduler;
    }

    public IJadxEvents events() {
        return events;
    }

    public void setEventsImpl(IJadxEvents eventsImpl) {
        this.events = eventsImpl;
    }

    public void addCustomCodeLoader(ICodeLoader customCodeLoader) {
        customCodeLoaders.add(customCodeLoader);
    }

    public List<ICodeLoader> getCustomCodeLoaders() {
        return customCodeLoaders;
    }

    public void addCustomResourcesLoader(CustomResourcesLoader loader) {
        if (customResourcesLoaders.contains(loader)) {
            return;
        }
        customResourcesLoaders.add(loader);
    }

    public List<CustomResourcesLoader> getCustomResourcesLoaders() {
        return customResourcesLoaders;
    }

    public void addCustomPass(JadxPass pass) {
        customPasses.computeIfAbsent(pass.getPassType(), l -> new ArrayList<>()).add(pass);
    }

    public ResourcesLoader getResourcesLoader() {
        return resourcesLoader;
    }

    public ZipReader getZipReader() {
        return zipReader;
    }

    public void addCloseable(Closeable closeable) {
        closeableList.add(closeable);
    }

    @Override
    public String toString() {
        return "jadx decompiler " + getVersion();
    }

    /** 保存进度监听器接口 */
    public interface ProgressListener {
        /** 进度更新回调 */
        void progress(long done, long total);
    }
}
