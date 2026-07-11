package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.args.*;
import com.bingbaihanji.fxdecomplie.core.jadx.api.data.ICodeData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IAliasProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IRenameCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.AnnotatedCodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.InMemoryCodeCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.loader.JadxBasePluginLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.loader.JadxPluginLoader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.security.IJadxSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.api.security.JadxSecurityFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.api.security.impl.JadxSecurity;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.IUsageInfoCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.impl.EmptyUsageInfoCache;
import com.bingbaihanji.fxdecomplie.core.jadx.api.usage.impl.InMemoryUsageInfoCache;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.DeobfAliasProvider;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions.DeobfWhitelist;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions.JadxRenameConditions;
import com.bingbaihanji.fxdecomplie.core.jadx.core.export.ExportGradleType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.PluginContext;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files.IJadxFilesGetter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.plugins.files.TempFilesGetter;
import com.bingbaihanji.fxdecomplie.util.ByteUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.File;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Jadx 反编译器参数配置类
 * <p>
 * 包含反编译过程中的所有可配置选项，如输入/输出路径、反编译模式、
 * 去混淆设置、代码格式化选项、插件配置等
 * <p>
 * 实现 {@link Closeable} 接口，用于释放内部缓存和插件加载器资源
 */
public class JadxArgs implements Closeable {
    /** 默认线程数，取 CPU 核心数的一半（最少为 1） */
    public static final int DEFAULT_THREADS_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    /** 默认换行符，使用系统行分隔符 */
    public static final String DEFAULT_NEW_LINE_STR = System.lineSeparator();
    /** 默认缩进字符串，4 个空格 */
    public static final String DEFAULT_INDENT_STR = "    ";
    /** 默认输出根目录名 */
    public static final String DEFAULT_OUT_DIR = "jadx-output";
    /** 默认源码输出子目录名 */
    public static final String DEFAULT_SRC_DIR = "sources";
    /** 默认资源输出子目录名 */
    public static final String DEFAULT_RES_DIR = "resources";
    private static final Logger LOG = LoggerFactory.getLogger(JadxArgs.class);
    /**
     * 要排除处理的反编译通道列表
     */
    private final List<String> disabledPasses = new ArrayList<>();
    /** 待反编译的输入文件列表 */
    private List<File> inputFiles = new ArrayList<>(1);
    /** 输出根目录 */
    private File outDir;
    /** 源码输出目录 */
    private File outDirSrc;
    /** 资源输出目录 */
    private File outDirRes;
    /** 代码缓存，用于缓存反编译结果 */
    private ICodeCache codeCache = new InMemoryCodeCache();
    /**
     * 使用数据缓存在代码重新加载之间保存类、方法和字段的使用位置信息
     * 如果不需要代码重新加载，可以设置为 {@link EmptyUsageInfoCache}
     */
    private IUsageInfoCache usageInfoCache = new InMemoryUsageInfoCache();
    /** 反编译使用的线程数 */
    private int threadsCount = DEFAULT_THREADS_COUNT;

    /** 是否输出控制流图（CFG） */
    private boolean cfgOutput = false;
    /** 是否输出原始控制流图 */
    private boolean rawCFGOutput = false;

    /** 是否显示不一致的代码（可能导致编译错误的代码） */
    private boolean showInconsistentCode = false;

    /** 是否使用 import 语句 */
    private boolean useImports = true;
    /** 是否保留调试信息 */
    private boolean debugInfo = true;
    /** 是否插入调试行号 */
    private boolean insertDebugLines = false;
    /** 是否提取 finally 块 */
    private boolean extractFinally = true;
    /** 是否内联匿名类 */
    private boolean inlineAnonymousClasses = true;
    /** 是否内联方法 */
    private boolean inlineMethods = true;
    /** 是否允许内联 Kotlin lambda 表达式 */
    private boolean allowInlineKotlinLambda = true;
    /** 是否将内部类移至顶级 */
    private boolean moveInnerClasses = true;

    /** 是否跳过资源文件的处理 */
    private boolean skipResources = false;
    /** 是否跳过源代码的处理 */
    private boolean skipSources = false;
    /** 是否使用 HTTP 头来检测资源文件扩展名 */
    private boolean useHeadersForDetectResourceExtensions;

    /**
     * 类过滤器谓词，允许根据类的全限定名过滤要处理的类
     */
    private @Nullable Predicate<String> classFilter = null;

    /**
     * 是否保存通过 {@code classFilter} 筛选的类的依赖项
     */
    private boolean includeDependencies = false;

    /** 用户自定义重命名映射文件路径 */
    private Path userRenamesMappingsPath = null;
    /** 用户自定义重命名映射模式 */
    private UserRenamesMappingsMode userRenamesMappingsMode = UserRenamesMappingsMode.getDefault();

    /** 是否启用去混淆 */
    private boolean deobfuscationOn = false;
    /** 是否使用源文件名作为类名的别名 */
    private UseSourceNameAsClassNameAlias useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.getDefault();
    /** 源名称重复次数限制 */
    private int sourceNameRepeatLimit = 10;

    /** 生成的重命名映射文件路径 */
    private File generatedRenamesMappingFile = null;
    /** 生成的重命名映射文件模式 */
    private GeneratedRenamesMappingFileMode generatedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.getDefault();
    /** 资源名称来源 */
    private ResourceNameSource resourceNameSource = ResourceNameSource.AUTO;

    /** 去混淆时名称的最小长度 */
    private int deobfuscationMinLength = 0;
    /** 去混淆时名称的最大长度 */
    private int deobfuscationMaxLength = Integer.MAX_VALUE;

    /**
     * 去混淆白名单，包含要排除去混淆处理的类和包名列表（以 '.*' 结尾表示包）
     */
    private List<String> deobfuscationWhitelist = DeobfWhitelist.DEFAULT_LIST;

    /**
     * 去混淆器和重命名访问器的节点别名提供器
     */
    private IAliasProvider aliasProvider = new DeobfAliasProvider();

    /**
     * 去混淆器中节点重命名的条件
     */
    private IRenameCondition renameCondition = JadxRenameConditions.buildDefault();

    /** 是否将 Unicode 字符转义为 \\uXXXX 形式 */
    private boolean escapeUnicode = false;
    /** 是否将常量字段替换为其实际值 */
    private boolean replaceConsts = true;
    /** 是否保留字节码中的访问修饰符（即使它们与源码不一致） */
    private boolean respectBytecodeAccModifiers = false;
    /** Gradle 项目导出类型，null 表示不导出为 Gradle 项目 */
    private @Nullable ExportGradleType exportGradleType = null;

    /** 是否恢复字符串上的 switch 语句 */
    private boolean restoreSwitchOverString = true;

    /** 是否跳过 XML 格式化输出 */
    private boolean skipXmlPrettyPrint = false;

    /** 文件系统是否区分大小写 */
    private boolean fsCaseSensitive;
    /** 重命名标志集合 */
    private Set<RenameEnum> renameFlags = EnumSet.allOf(RenameEnum.class);
    /** 输出格式 */
    private OutputFormatEnum outputFormat = OutputFormatEnum.JAVA;
    /** 反编译模式 */
    private DecompilationMode decompilationMode = DecompilationMode.AUTO;
    /** 代码数据对象，用于存储反编译后的代码元信息 */
    private ICodeData codeData;
    /** 代码中使用的换行符 */
    private String codeNewLineStr = DEFAULT_NEW_LINE_STR;
    /** 代码中使用的缩进字符串 */
    private String codeIndentStr = DEFAULT_INDENT_STR;
    /** 代码写入器工厂函数，用于创建 ICodeWriter 实例 */
    private Function<JadxArgs, ICodeWriter> codeWriterProvider = AnnotatedCodeWriter::new;
    /** 注释级别，控制反编译输出中注释的详细程度 */
    private CommentsLevel commentsLevel = CommentsLevel.INFO;
    /** 整数字面量的输出格式 */
    private IntegerFormat integerFormat = IntegerFormat.AUTO;
    /**
     * 每条指令在方法中允许的类型更新最大次数
     * 值必须大于等于 1，默认值为 10
     */
    private int typeUpdatesLimitCount = 10;
    /** 是否使用 dx 格式输入（Android Dalvik 可执行文件） */
    private boolean useDxInput = false;
    /** 是否使用 Kotlin 方法名作为变量名 */
    private UseKotlinMethodsForVarNames useKotlinMethodsForVarNames = UseKotlinMethodsForVarNames.APPLY;
    /**
     * 附加文件结构信息获取器
     * 默认使用临时目录
     */
    private IJadxFilesGetter filesGetter = TempFilesGetter.INSTANCE;
    /**
     * 附加数据验证和安全检查
     */
    private IJadxSecurity security = new JadxSecurity(JadxSecurityFlag.all());
    /**
     * 是否跳过文件保存（可用于性能测试）
     */
    private boolean skipFilesSave = false;
    /**
     * 是否运行额外的开销较大的检查，以验证内部不变量和信息完整性
     */
    private boolean runDebugChecks = false;
    /** 插件选项键值对 */
    private Map<String, String> pluginOptions = new HashMap<>();
    /** 已禁用的插件名称集合 */
    private Set<String> disabledPlugins = new HashSet<>();
    /** 插件加载器 */
    private JadxPluginLoader pluginLoader = new JadxBasePluginLoader();
    /** 是否加载 jadx 内置类集合文件 */
    private boolean loadJadxClsSetFile = true;

    /** 使用默认选项创建 JadxArgs 实例 */
    public JadxArgs() {
        // 使用默认选项
    }

    /**
     * 构建插件相关的哈希字符串，作为选项哈希的一部分
     * 将所有已解析插件上下文的输入哈希以 ":" 连接
     *
     * @param decompiler Jadx 反编译器实例（可为 null，此时返回空字符串）
     * @return 插件哈希字符串
     */
    private static String buildPluginsHash(@Nullable JadxDecompiler decompiler) {
        if (decompiler == null) {
            return "";
        }
        return decompiler.getPluginManager().getResolvedPluginContexts()
                .stream()
                .map(PluginContext::getInputsHash)
                .collect(Collectors.joining(":"));
    }

    /**
     * 设置输出根目录，并自动设置源码和资源子目录
     *
     * @param rootDir 输出根目录
     */
    public void setRootDir(File rootDir) {
        setOutDir(rootDir);
        setOutDirSrc(new File(rootDir, DEFAULT_SRC_DIR));
        setOutDirRes(new File(rootDir, DEFAULT_RES_DIR));
    }

    /**
     * 关闭并释放所有内部资源，包括代码缓存、使用数据缓存和插件加载器
     * 关闭过程中的异常会被记录到日志，不会抛出
     */
    @Override
    public void close() {
        try {
            if (codeCache != null) {
                codeCache.close();
            }
            if (usageInfoCache != null) {
                usageInfoCache.close();
            }
            if (pluginLoader != null) {
                pluginLoader.close();
            }
        } catch (Exception e) {
            LOG.error("Failed to close JadxArgs", e);
        }
    }

    /**
     * 获取待反编译的输入文件列表
     *
     * @return 输入文件列表
     */
    public List<File> getInputFiles() {
        return inputFiles;
    }

    /**
     * 设置输入文件列表，替换已有的输入文件集合
     *
     * @param inputFiles 输入文件列表
     */
    public void setInputFiles(List<File> inputFiles) {
        this.inputFiles = inputFiles;
    }

    /**
     * 向输入文件列表追加一个文件
     *
     * @param inputFile 待添加的输入文件
     */
    public void addInputFile(File inputFile) {
        this.inputFiles.add(inputFile);
    }

    /**
     * 设置单个输入文件（内部通过追加方式实现，不会清空已有列表）
     *
     * @param inputFile 输入文件
     */
    public void setInputFile(File inputFile) {
        addInputFile(inputFile);
    }

    public File getOutDir() {
        return outDir;
    }

    public void setOutDir(File outDir) {
        this.outDir = outDir;
    }

    public File getOutDirSrc() {
        return outDirSrc;
    }

    public void setOutDirSrc(File outDirSrc) {
        this.outDirSrc = outDirSrc;
    }

    public File getOutDirRes() {
        return outDirRes;
    }

    public void setOutDirRes(File outDirRes) {
        this.outDirRes = outDirRes;
    }

    public int getThreadsCount() {
        return threadsCount;
    }

    public void setThreadsCount(int threadsCount) {
        this.threadsCount = Math.max(1, threadsCount); // 确保线程数不小于 1
    }

    public boolean isCfgOutput() {
        return cfgOutput;
    }

    public void setCfgOutput(boolean cfgOutput) {
        this.cfgOutput = cfgOutput;
    }

    public boolean isRawCFGOutput() {
        return rawCFGOutput;
    }

    public void setRawCFGOutput(boolean rawCFGOutput) {
        this.rawCFGOutput = rawCFGOutput;
    }

    /**
     * 判断当前是否处于回退（fallback）反编译模式
     *
     * @return 若反编译模式为 {@link DecompilationMode#FALLBACK} 则返回 true
     */
    public boolean isFallbackMode() {
        return decompilationMode == DecompilationMode.FALLBACK;
    }

    /**
     * @deprecated 请使用 {@link #setDecompilationMode(DecompilationMode)} 属性代替
     */
    @Deprecated
    public void setFallbackMode(boolean fallbackMode) {
        if (fallbackMode) {
            this.decompilationMode = DecompilationMode.FALLBACK;
        }
    }

    public boolean isShowInconsistentCode() {
        return showInconsistentCode;
    }

    public void setShowInconsistentCode(boolean showInconsistentCode) {
        this.showInconsistentCode = showInconsistentCode;
    }

    public boolean isUseImports() {
        return useImports;
    }

    public void setUseImports(boolean useImports) {
        this.useImports = useImports;
    }

    public boolean isDebugInfo() {
        return debugInfo;
    }

    public void setDebugInfo(boolean debugInfo) {
        this.debugInfo = debugInfo;
    }

    public boolean isInsertDebugLines() {
        return insertDebugLines;
    }

    public void setInsertDebugLines(boolean insertDebugLines) {
        this.insertDebugLines = insertDebugLines;
    }

    public boolean isInlineAnonymousClasses() {
        return inlineAnonymousClasses;
    }

    public void setInlineAnonymousClasses(boolean inlineAnonymousClasses) {
        this.inlineAnonymousClasses = inlineAnonymousClasses;
    }

    public boolean isInlineMethods() {
        return inlineMethods;
    }

    public void setInlineMethods(boolean inlineMethods) {
        this.inlineMethods = inlineMethods;
    }

    public boolean isAllowInlineKotlinLambda() {
        return allowInlineKotlinLambda;
    }

    public void setAllowInlineKotlinLambda(boolean allowInlineKotlinLambda) {
        this.allowInlineKotlinLambda = allowInlineKotlinLambda;
    }

    public boolean isMoveInnerClasses() {
        return moveInnerClasses;
    }

    public void setMoveInnerClasses(boolean moveInnerClasses) {
        this.moveInnerClasses = moveInnerClasses;
    }

    public boolean isExtractFinally() {
        return extractFinally;
    }

    public void setExtractFinally(boolean extractFinally) {
        this.extractFinally = extractFinally;
    }

    public boolean isSkipResources() {
        return skipResources;
    }

    public void setSkipResources(boolean skipResources) {
        this.skipResources = skipResources;
    }

    public boolean isSkipSources() {
        return skipSources;
    }

    public void setSkipSources(boolean skipSources) {
        this.skipSources = skipSources;
    }

    public boolean isIncludeDependencies() {
        return includeDependencies;
    }

    public void setIncludeDependencies(boolean includeDependencies) {
        this.includeDependencies = includeDependencies;
    }

    public Predicate<String> getClassFilter() {
        return classFilter;
    }

    public void setClassFilter(Predicate<String> classFilter) {
        this.classFilter = classFilter;
    }

    public Path getUserRenamesMappingsPath() {
        return userRenamesMappingsPath;
    }

    public void setUserRenamesMappingsPath(Path path) {
        this.userRenamesMappingsPath = path;
    }

    public UserRenamesMappingsMode getUserRenamesMappingsMode() {
        return userRenamesMappingsMode;
    }

    public void setUserRenamesMappingsMode(UserRenamesMappingsMode mode) {
        this.userRenamesMappingsMode = mode;
    }

    public boolean isDeobfuscationOn() {
        return deobfuscationOn;
    }

    public void setDeobfuscationOn(boolean deobfuscationOn) {
        this.deobfuscationOn = deobfuscationOn;
    }

    /**
     * 判断去混淆映射文件是否为强制保存（覆盖）模式
     *
     * @return 若映射文件模式为 {@link GeneratedRenamesMappingFileMode#OVERWRITE} 则返回 true
     */
    public boolean isDeobfuscationForceSave() {
        return generatedRenamesMappingFileMode == GeneratedRenamesMappingFileMode.OVERWRITE;
    }

    /**
     * 设置去混淆映射文件是否强制保存（覆盖）
     * 传入 true 时会将映射文件模式设为 {@link GeneratedRenamesMappingFileMode#OVERWRITE}
     *
     * @param deobfuscationForceSave 是否强制保存
     */
    public void setDeobfuscationForceSave(boolean deobfuscationForceSave) {
        if (deobfuscationForceSave) {
            this.generatedRenamesMappingFileMode = GeneratedRenamesMappingFileMode.OVERWRITE;
        }
    }

    public GeneratedRenamesMappingFileMode getGeneratedRenamesMappingFileMode() {
        return generatedRenamesMappingFileMode;
    }

    public void setGeneratedRenamesMappingFileMode(GeneratedRenamesMappingFileMode mode) {
        this.generatedRenamesMappingFileMode = mode;
    }

    public UseSourceNameAsClassNameAlias getUseSourceNameAsClassNameAlias() {
        return useSourceNameAsClassNameAlias;
    }

    public void setUseSourceNameAsClassNameAlias(UseSourceNameAsClassNameAlias useSourceNameAsClassNameAlias) {
        this.useSourceNameAsClassNameAlias = useSourceNameAsClassNameAlias;
    }

    public int getSourceNameRepeatLimit() {
        return sourceNameRepeatLimit;
    }

    public void setSourceNameRepeatLimit(int sourceNameRepeatLimit) {
        this.sourceNameRepeatLimit = sourceNameRepeatLimit;
    }

    /**
     * @deprecated 请使用 {@link #getUseSourceNameAsClassNameAlias()} 代替
     */
    @Deprecated
    public boolean isUseSourceNameAsClassAlias() {
        return getUseSourceNameAsClassNameAlias().toBoolean();
    }

    /**
     * @deprecated 请使用 {@link #setUseSourceNameAsClassNameAlias(UseSourceNameAsClassNameAlias)} 代替
     */
    @Deprecated
    public void setUseSourceNameAsClassAlias(boolean useSourceNameAsClassAlias) {
        var useSourceNameAsClassNameAlias = UseSourceNameAsClassNameAlias.create(useSourceNameAsClassAlias);
        setUseSourceNameAsClassNameAlias(useSourceNameAsClassNameAlias);
    }

    public int getDeobfuscationMinLength() {
        return deobfuscationMinLength;
    }

    public void setDeobfuscationMinLength(int deobfuscationMinLength) {
        this.deobfuscationMinLength = deobfuscationMinLength;
    }

    public int getDeobfuscationMaxLength() {
        return deobfuscationMaxLength;
    }

    public void setDeobfuscationMaxLength(int deobfuscationMaxLength) {
        this.deobfuscationMaxLength = deobfuscationMaxLength;
    }

    public List<String> getDeobfuscationWhitelist() {
        return this.deobfuscationWhitelist;
    }

    public void setDeobfuscationWhitelist(List<String> deobfuscationWhitelist) {
        this.deobfuscationWhitelist = deobfuscationWhitelist;
    }

    public File getGeneratedRenamesMappingFile() {
        return generatedRenamesMappingFile;
    }

    public void setGeneratedRenamesMappingFile(File file) {
        this.generatedRenamesMappingFile = file;
    }

    public ResourceNameSource getResourceNameSource() {
        return resourceNameSource;
    }

    public void setResourceNameSource(ResourceNameSource resourceNameSource) {
        this.resourceNameSource = resourceNameSource;
    }

    public IAliasProvider getAliasProvider() {
        return aliasProvider;
    }

    public void setAliasProvider(IAliasProvider aliasProvider) {
        this.aliasProvider = aliasProvider;
    }

    public IRenameCondition getRenameCondition() {
        return renameCondition;
    }

    public void setRenameCondition(IRenameCondition renameCondition) {
        this.renameCondition = renameCondition;
    }

    public boolean isEscapeUnicode() {
        return escapeUnicode;
    }

    public void setEscapeUnicode(boolean escapeUnicode) {
        this.escapeUnicode = escapeUnicode;
    }

    public boolean isReplaceConsts() {
        return replaceConsts;
    }

    public void setReplaceConsts(boolean replaceConsts) {
        this.replaceConsts = replaceConsts;
    }

    public boolean isRespectBytecodeAccModifiers() {
        return respectBytecodeAccModifiers;
    }

    public void setRespectBytecodeAccModifiers(boolean respectBytecodeAccModifiers) {
        this.respectBytecodeAccModifiers = respectBytecodeAccModifiers;
    }

    /**
     * 判断是否导出为 Gradle 项目
     *
     * @return 若 {@code exportGradleType} 非 null 则返回 true
     */
    public boolean isExportAsGradleProject() {
        return exportGradleType != null;
    }

    /**
     * 设置是否导出为 Gradle 项目
     * 传入 true 且当前未指定 Gradle 类型时，默认使用 {@link ExportGradleType#AUTO}
     * 传入 false 时清除导出类型
     *
     * @param exportAsGradleProject 是否导出为 Gradle 项目
     */
    public void setExportAsGradleProject(boolean exportAsGradleProject) {
        if (exportAsGradleProject) {
            if (exportGradleType == null) {
                exportGradleType = ExportGradleType.AUTO;
            }
        } else {
            exportGradleType = null;
        }
    }

    public @Nullable ExportGradleType getExportGradleType() {
        return exportGradleType;
    }

    public void setExportGradleType(@Nullable ExportGradleType exportGradleType) {
        this.exportGradleType = exportGradleType;
    }

    public boolean isRestoreSwitchOverString() {
        return restoreSwitchOverString;
    }

    public void setRestoreSwitchOverString(boolean restoreSwitchOverString) {
        this.restoreSwitchOverString = restoreSwitchOverString;
    }

    public boolean isSkipXmlPrettyPrint() {
        return skipXmlPrettyPrint;
    }

    public void setSkipXmlPrettyPrint(boolean skipXmlPrettyPrint) {
        this.skipXmlPrettyPrint = skipXmlPrettyPrint;
    }

    public boolean isFsCaseSensitive() {
        return fsCaseSensitive;
    }

    public void setFsCaseSensitive(boolean fsCaseSensitive) {
        this.fsCaseSensitive = fsCaseSensitive;
    }

    public boolean isRenameCaseSensitive() {
        return renameFlags.contains(RenameEnum.CASE);
    }

    public void setRenameCaseSensitive(boolean renameCaseSensitive) {
        updateRenameFlag(renameCaseSensitive, RenameEnum.CASE);
    }

    public boolean isRenameValid() {
        return renameFlags.contains(RenameEnum.VALID);
    }

    public void setRenameValid(boolean renameValid) {
        updateRenameFlag(renameValid, RenameEnum.VALID);
    }

    public boolean isRenamePrintable() {
        return renameFlags.contains(RenameEnum.PRINTABLE);
    }

    public void setRenamePrintable(boolean renamePrintable) {
        updateRenameFlag(renamePrintable, RenameEnum.PRINTABLE);
    }

    /**
     * 根据开关状态更新重命名标志集合
     *
     * @param enabled 为 true 时添加标志，为 false 时移除标志
     * @param flag    要更新的重命名标志
     */
    private void updateRenameFlag(boolean enabled, RenameEnum flag) {
        if (enabled) {
            renameFlags.add(flag);
        } else {
            renameFlags.remove(flag);
        }
    }

    public Set<RenameEnum> getRenameFlags() {
        return renameFlags;
    }

    public void setRenameFlags(Set<RenameEnum> renameFlags) {
        this.renameFlags = renameFlags;
    }

    public OutputFormatEnum getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(OutputFormatEnum outputFormat) {
        this.outputFormat = outputFormat;
    }

    /**
     * 判断输出格式是否为 JSON
     *
     * @return 若输出格式为 {@link OutputFormatEnum#JSON} 则返回 true
     */
    public boolean isJsonOutput() {
        return outputFormat == OutputFormatEnum.JSON;
    }

    public DecompilationMode getDecompilationMode() {
        return decompilationMode;
    }

    public void setDecompilationMode(DecompilationMode decompilationMode) {
        this.decompilationMode = decompilationMode;
    }

    public ICodeCache getCodeCache() {
        return codeCache;
    }

    public void setCodeCache(ICodeCache codeCache) {
        this.codeCache = codeCache;
    }

    public Function<JadxArgs, ICodeWriter> getCodeWriterProvider() {
        return codeWriterProvider;
    }

    public void setCodeWriterProvider(Function<JadxArgs, ICodeWriter> codeWriterProvider) {
        this.codeWriterProvider = codeWriterProvider;
    }

    public IUsageInfoCache getUsageInfoCache() {
        return usageInfoCache;
    }

    public void setUsageInfoCache(IUsageInfoCache usageInfoCache) {
        this.usageInfoCache = usageInfoCache;
    }

    public ICodeData getCodeData() {
        return codeData;
    }

    public void setCodeData(ICodeData codeData) {
        this.codeData = codeData;
    }

    public String getCodeIndentStr() {
        return codeIndentStr;
    }

    public void setCodeIndentStr(String codeIndentStr) {
        this.codeIndentStr = codeIndentStr;
    }

    public String getCodeNewLineStr() {
        return codeNewLineStr;
    }

    public void setCodeNewLineStr(String codeNewLineStr) {
        this.codeNewLineStr = codeNewLineStr;
    }

    public CommentsLevel getCommentsLevel() {
        return commentsLevel;
    }

    public void setCommentsLevel(CommentsLevel commentsLevel) {
        this.commentsLevel = commentsLevel;
    }

    public IntegerFormat getIntegerFormat() {
        return integerFormat;
    }

    public void setIntegerFormat(IntegerFormat format) {
        this.integerFormat = format;
    }

    public int getTypeUpdatesLimitCount() {
        return typeUpdatesLimitCount;
    }

    public void setTypeUpdatesLimitCount(int typeUpdatesLimitCount) {
        this.typeUpdatesLimitCount = Math.max(1, typeUpdatesLimitCount);
    }

    public boolean isUseDxInput() {
        return useDxInput;
    }

    public void setUseDxInput(boolean useDxInput) {
        this.useDxInput = useDxInput;
    }

    public UseKotlinMethodsForVarNames getUseKotlinMethodsForVarNames() {
        return useKotlinMethodsForVarNames;
    }

    public void setUseKotlinMethodsForVarNames(UseKotlinMethodsForVarNames useKotlinMethodsForVarNames) {
        this.useKotlinMethodsForVarNames = useKotlinMethodsForVarNames;
    }

    public IJadxFilesGetter getFilesGetter() {
        return filesGetter;
    }

    public void setFilesGetter(IJadxFilesGetter filesGetter) {
        this.filesGetter = filesGetter;
    }

    public IJadxSecurity getSecurity() {
        return security;
    }

    public void setSecurity(IJadxSecurity security) {
        this.security = security;
    }

    public boolean isSkipFilesSave() {
        return skipFilesSave;
    }

    public void setSkipFilesSave(boolean skipFilesSave) {
        this.skipFilesSave = skipFilesSave;
    }

    public boolean isRunDebugChecks() {
        return runDebugChecks;
    }

    public void setRunDebugChecks(boolean runDebugChecks) {
        this.runDebugChecks = runDebugChecks;
    }

    public List<String> getDisabledPasses() {
        return disabledPasses;
    }

    public Map<String, String> getPluginOptions() {
        return pluginOptions;
    }

    public void setPluginOptions(Map<String, String> pluginOptions) {
        this.pluginOptions = pluginOptions;
    }

    public Set<String> getDisabledPlugins() {
        return disabledPlugins;
    }

    public void setDisabledPlugins(Set<String> disabledPlugins) {
        this.disabledPlugins = disabledPlugins;
    }

    public JadxPluginLoader getPluginLoader() {
        return pluginLoader;
    }

    public void setPluginLoader(JadxPluginLoader pluginLoader) {
        this.pluginLoader = pluginLoader;
    }

    public boolean isLoadJadxClsSetFile() {
        return loadJadxClsSetFile;
    }

    public void setLoadJadxClsSetFile(boolean loadJadxClsSetFile) {
        this.loadJadxClsSetFile = loadJadxClsSetFile;
    }

    public boolean isUseHeadersForDetectResourceExtensions() {
        return useHeadersForDetectResourceExtensions;
    }

    public void setUseHeadersForDetectResourceExtensions(boolean useHeadersForDetectResourceExtensions) {
        this.useHeadersForDetectResourceExtensions = useHeadersForDetectResourceExtensions;
    }

    /**
     * 计算所有可能影响反编译结果代码的选项的哈希值
     * 用于缓存键生成，确保选项变更时能生成不同的缓存键
     *
     * @param decompiler Jadx 反编译器实例（可为 null，此时不包含插件哈希）
     * @return 选项哈希值的 MD5 字符串
     */
    public String makeCodeArgsHash(@Nullable JadxDecompiler decompiler) {
        String argStr = "args:" + decompilationMode + useImports + showInconsistentCode
                + inlineAnonymousClasses + inlineMethods + moveInnerClasses + allowInlineKotlinLambda
                + deobfuscationOn + deobfuscationMinLength + deobfuscationMaxLength + deobfuscationWhitelist
                + useSourceNameAsClassNameAlias + sourceNameRepeatLimit
                + resourceNameSource + useHeadersForDetectResourceExtensions
                + useKotlinMethodsForVarNames
                + insertDebugLines + extractFinally
                + debugInfo + escapeUnicode + replaceConsts + restoreSwitchOverString
                + respectBytecodeAccModifiers + fsCaseSensitive + renameFlags
                + commentsLevel + useDxInput + integerFormat + typeUpdatesLimitCount
                + "|" + buildPluginsHash(decompiler);
        return ByteUtils.md5Sum(argStr);
    }

    /**
     * 返回包含主要配置选项的字符串表示，主要用于调试和日志输出
     *
     * @return 描述当前参数配置的字符串
     */
    @Override
    public String toString() {
        return "JadxArgs{" + "inputFiles=" + inputFiles
                + ", outDir=" + outDir
                + ", outDirSrc=" + outDirSrc
                + ", outDirRes=" + outDirRes
                + ", threadsCount=" + threadsCount
                + ", decompilationMode=" + decompilationMode
                + ", showInconsistentCode=" + showInconsistentCode
                + ", useImports=" + useImports
                + ", skipResources=" + skipResources
                + ", skipSources=" + skipSources
                + ", includeDependencies=" + includeDependencies
                + ", userRenamesMappingsPath=" + userRenamesMappingsPath
                + ", userRenamesMappingsMode=" + userRenamesMappingsMode
                + ", deobfuscationOn=" + deobfuscationOn
                + ", generatedRenamesMappingFile=" + generatedRenamesMappingFile
                + ", generatedRenamesMappingFileMode=" + generatedRenamesMappingFileMode
                + ", resourceNameSource=" + resourceNameSource
                + ", useSourceNameAsClassNameAlias=" + useSourceNameAsClassNameAlias
                + ", sourceNameRepeatLimit=" + sourceNameRepeatLimit
                + ", useKotlinMethodsForVarNames=" + useKotlinMethodsForVarNames
                + ", insertDebugLines=" + insertDebugLines
                + ", extractFinally=" + extractFinally
                + ", deobfuscationMinLength=" + deobfuscationMinLength
                + ", deobfuscationMaxLength=" + deobfuscationMaxLength
                + ", deobfuscationWhitelist=" + deobfuscationWhitelist
                + ", escapeUnicode=" + escapeUnicode
                + ", replaceConsts=" + replaceConsts
                + ", restoreSwitchOverString=" + restoreSwitchOverString
                + ", respectBytecodeAccModifiers=" + respectBytecodeAccModifiers
                + ", exportGradleType=" + exportGradleType
                + ", skipXmlPrettyPrint=" + skipXmlPrettyPrint
                + ", fsCaseSensitive=" + fsCaseSensitive
                + ", renameFlags=" + renameFlags
                + ", outputFormat=" + outputFormat
                + ", commentsLevel=" + commentsLevel
                + ", codeCache=" + codeCache
                + ", codeWriter=" + codeWriterProvider.apply(this).getClass().getSimpleName()
                + ", useDxInput=" + useDxInput
                + ", pluginOptions=" + pluginOptions
                + ", cfgOutput=" + cfgOutput
                + ", rawCFGOutput=" + rawCFGOutput
                + ", useHeadersForDetectResourceExtensions=" + useHeadersForDetectResourceExtensions
                + ", typeUpdatesLimitCount=" + typeUpdatesLimitCount
                + '}';
    }

    /**
     * 重命名标志枚举，控制去混淆时的重命名行为
     * <ul>
     *   <li>CASE - 区分大小写</li>
     *   <li>VALID - 仅使用有效标识符字符</li>
     *   <li>PRINTABLE - 仅使用可打印字符</li>
     * </ul>
     */
    public enum RenameEnum {
        CASE, VALID, PRINTABLE
    }

    /**
     * 输出格式枚举
     * <ul>
     *   <li>JAVA - Java 源代码格式</li>
     *   <li>JSON - JSON 结构化格式</li>
     * </ul>
     */
    public enum OutputFormatEnum {
        JAVA, JSON
    }

    /**
     * Kotlin 方法用于变量名的策略枚举
     * <ul>
     *   <li>DISABLE - 不使用 Kotlin 方法名</li>
     *   <li>APPLY - 应用 Kotlin 方法名</li>
     *   <li>APPLY_AND_HIDE - 应用并隐藏原始名称</li>
     * </ul>
     */
    public enum UseKotlinMethodsForVarNames {
        DISABLE, APPLY, APPLY_AND_HIDE
    }
}
