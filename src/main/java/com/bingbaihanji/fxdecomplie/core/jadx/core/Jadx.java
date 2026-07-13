package com.bingbaihanji.fxdecomplie.core.jadx.core;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.DeobfuscatorVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.SaveDeobfMapping;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockFinisher;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockProcessor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockSplitter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.debuginfo.DebugInfoApplyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.debuginfo.DebugInfoAttachVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.finaly.MarkFinallyVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.fixaccessmodifiers.FixAccessModifiers;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.gradle.NonFinalResIdsVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.kotlin.ProcessKotlinInternals;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare.AddAndroidConstants;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.prepare.CollectConstValues;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.variables.ProcessVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.CodeRenameVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.RenameVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.SourceFileRename;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.shrink.CodeShrinkVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ssa.SSATransform;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.FinishTypeInference;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.FixTypesVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.usage.UsageInfoVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.Manifest;

/**
 * Jadx 反编译引擎核心入口类
 * <p>
 * 负责编排反编译流水线 (Pass Pipeline)：根据反编译模式 (AUTO/SIMPLE/FALLBACK)，
 * 依次注册并执行一系列 {@link IDexTreeVisitor} 访问器，将 DEX 字节码逐步转换为
 * 结构化控制流图 (CFG)再到中间表示 (IR)，最终生成可读的 Java 源码
 * <p>
 * 同时提供版本号查询功能，通过读取 MANIFEST.MF 中的 jadx-version 属性获取版本信息
 */
public class Jadx {
    /** 开发版本标识：当无法从 MANIFEST.MF 读取到版本号时使用 */
    public static final String VERSION_DEV = "dev";
    private static final Logger LOG = LoggerFactory.getLogger(Jadx.class);
    /** 缓存的版本号，首次调用 {@link #getVersion()} 时惰性初始化 */
    private static String version;

    private Jadx() {
    }

    /**
     * 根据反编译模式获取对应的访问器流水线列表
     * <p>
     * 支持四种模式：
     * <ul>
     *   <li>{@code AUTO} / {@code RESTRUCTURE}：采用完整的区域模式流水线，流程最完整，反编译质量最高</li>
     *   <li>{@code SIMPLE}：采用简化模式，跳过区域重建等复杂步骤，速度快但输出不够优化</li>
     *   <li>{@code FALLBACK}：降级模式，仅做最基本的 try-catch 附加和指令处理</li>
     * </ul>
     *
     * @param args 反编译参数配置
     * @return 按顺序排列的访问器列表
     * @throws JadxRuntimeException 当反编译模式未知时抛出
     */
    public static List<IDexTreeVisitor> getPassesList(JadxArgs args) {
        switch (args.getDecompilationMode()) {
            case AUTO:
            case RESTRUCTURE:
                return getRegionsModePasses(args);
            case SIMPLE:
                return getSimpleModePasses(args);
            case FALLBACK:
                return getFallbackPassesList();
            default:
                throw new JadxRuntimeException("Unknown decompilation mode: " + args.getDecompilationMode());
        }
    }

    /**
     * 获取预反编译阶段的访问器流水线列表
     * <p>
     * 这些访问器在正式反编译前执行，负责签名处理 方法重写检测 Android 常量添加 
     * 重命名与反混淆 使用信息收集 常量值收集以及匿名类和内联方法的预处理
     *
     * @return 预反编译阶段的访问器列表
     */
    public static List<IDexTreeVisitor> getPreDecompilePassesList() {
        List<IDexTreeVisitor> passes = new ArrayList<>();
        passes.add(new SignatureProcessor());
        passes.add(new OverrideMethodVisitor());
        passes.add(new AddAndroidConstants());

        // 重命名与反混淆
        passes.add(new DeobfuscatorVisitor());
        passes.add(new SourceFileRename());
        passes.add(new RenameVisitor());
        passes.add(new SaveDeobfMapping());

        passes.add(new UsageInfoVisitor());
        passes.add(new CollectConstValues());
        passes.add(new ProcessAnonymous());
        passes.add(new ProcessMethodsForInline());
        return passes;
    }

    /**
     * 获取区域模式 (AUTO/RESTRUCTURE)的完整访问器流水线列表
     * <p>
     * 流水线依次经过多个中间表示层：指令 IR → 基本块 IR → 区域 IR，
     * 涵盖 SSA 变换 类型推断 方法内联 控制流区域重建 代码简化等完整流程，
     * 是反编译质量最高的模式
     *
     * @param args 反编译参数配置，决定是否启用调试信息 finally 提取 方法内联 CFG 输出等可选步骤
     * @return 区域模式的访问器列表
     */
    public static List<IDexTreeVisitor> getRegionsModePasses(JadxArgs args) {
        List<IDexTreeVisitor> passes = new ArrayList<>();
        // 指令级中间表示 (IR)
        passes.add(new CheckCode());
        if (args.isDebugInfo()) {
            passes.add(new DebugInfoAttachVisitor());
        }
        passes.add(new AttachTryCatchVisitor());
        if (args.getCommentsLevel() != CommentsLevel.NONE) {
            passes.add(new AttachCommentsVisitor());
        }
        passes.add(new AttachMethodDetails());
        passes.add(new ProcessInstructionsVisitor());

        // 基本块级中间表示 (IR)
        passes.add(new BlockSplitter());
        passes.add(new BlockProcessor());
        passes.add(new BlockFinisher());
        if (args.isRawCFGOutput()) {
            passes.add(DotGraphVisitor.dumpRaw());
        }

        passes.add(new SSATransform());
        passes.add(new MoveInlineVisitor());
        passes.add(new ConstructorVisitor());
        passes.add(new InitCodeVariables());
        if (args.isExtractFinally()) {
            passes.add(new MarkFinallyVisitor());
        }
        passes.add(new ConstInlineVisitor());
        passes.add(new TypeInferenceVisitor());
        if (args.isDebugInfo()) {
            passes.add(new DebugInfoApplyVisitor());
        }
        passes.add(new FixTypesVisitor());
        passes.add(new FinishTypeInference());

        passes.add(new AdjustForIfMergeVisitor());

        if (args.getUseKotlinMethodsForVarNames() != JadxArgs.UseKotlinMethodsForVarNames.DISABLE) {
            passes.add(new ProcessKotlinInternals());
        }
        passes.add(new CodeRenameVisitor());
        if (args.isInlineMethods()) {
            passes.add(new InlineMethods());
        }
        passes.add(new GenericTypesVisitor());
        passes.add(new ShadowFieldVisitor());
        passes.add(new DeboxingVisitor());
        passes.add(new AnonymousClassVisitor());
        passes.add(new ModVisitor());
        passes.add(new CodeShrinkVisitor());
        passes.add(new ReplaceNewArray());
        if (args.isCfgOutput()) {
            passes.add(DotGraphVisitor.dump());
        }

        // 区域级中间表示 (IR)
        passes.add(new RegionMakerVisitor());
        passes.add(new IfRegionVisitor());
        if (args.isRestoreSwitchOverString()) {
            passes.add(new SwitchOverStringVisitor());
        }
        passes.add(new ReturnVisitor());
        passes.add(new CleanRegions());

        passes.add(new MethodThrowsVisitor());

        passes.add(new CodeShrinkVisitor());
        passes.add(new MethodInvokeVisitor());
        passes.add(new SimplifyVisitor());
        passes.add(new CheckRegions());

        passes.add(new EnumVisitor());
        passes.add(new FixSwitchOverEnum());
        passes.add(new NonFinalResIdsVisitor());
        passes.add(new ExtractFieldInit());
        passes.add(new FixAccessModifiers());
        passes.add(new ClassModifier());
        passes.add(new LoopRegionVisitor());
        passes.add(new SwitchBreakVisitor());

        if (args.isInlineMethods()) {
            passes.add(new MarkMethodsForInline());
        }
        passes.add(new ProcessVariables());
        passes.add(new ApplyVariableNames());

        passes.add(new PrepareForCodeGen());
        if (args.isCfgOutput()) {
            passes.add(DotGraphVisitor.dumpRegions());
        }
        return passes;
    }

    /**
     * 获取简化模式 (SIMPLE)的访问器流水线列表
     * <p>
     * 相比区域模式，跳过了控制流区域重建及大量优化步骤，仅保留基本块处理 
     * SSA 变换 类型推断和基础代码简化，反编译速度更快，但输出结构不如区域模式优化
     *
     * @param args 反编译参数配置
     * @return 简化模式的访问器列表
     */
    public static List<IDexTreeVisitor> getSimpleModePasses(JadxArgs args) {
        List<IDexTreeVisitor> passes = new ArrayList<>();
        if (args.isDebugInfo()) {
            passes.add(new DebugInfoAttachVisitor());
        }
        passes.add(new AttachTryCatchVisitor());
        if (args.getCommentsLevel() != CommentsLevel.NONE) {
            passes.add(new AttachCommentsVisitor());
        }
        passes.add(new AttachMethodDetails());
        passes.add(new ProcessInstructionsVisitor());

        passes.add(new BlockSplitter());
        if (args.isRawCFGOutput()) {
            passes.add(DotGraphVisitor.dumpRaw());
        }
        passes.add(new MethodVisitor("DisableBlockLock", mth -> mth.add(AFlag.DISABLE_BLOCKS_LOCK)));
        passes.add(new BlockProcessor());
        passes.add(new SSATransform());
        passes.add(new MoveInlineVisitor());
        passes.add(new ConstructorVisitor());
        passes.add(new InitCodeVariables());
        passes.add(new ConstInlineVisitor());
        passes.add(new TypeInferenceVisitor());
        if (args.isDebugInfo()) {
            passes.add(new DebugInfoApplyVisitor());
        }
        passes.add(new FixTypesVisitor());
        passes.add(new FinishTypeInference());
        passes.add(new CodeRenameVisitor());
        passes.add(new DeboxingVisitor());
        passes.add(new ModVisitor());
        passes.add(new CodeShrinkVisitor());
        passes.add(new ReplaceNewArray());
        passes.add(new SimplifyVisitor());
        passes.add(new MethodVisitor("ForceGenerateAll", mth -> mth.remove(AFlag.DONT_GENERATE)));
        if (args.isCfgOutput()) {
            passes.add(DotGraphVisitor.dump());
        }
        return passes;
    }

    /**
     * 获取降级模式 (FALLBACK)的访问器流水线列表
     * <p>
     * 当标准反编译流程失败时使用的兜底模式，仅附加 try-catch 结构 注释
     * 和基础指令处理，然后直接调用 {@link FallbackModeVisitor} 生成代码，
     * 保证至少能输出某种形式的可读结果
     *
     * @return 降级模式的访问器列表
     */
    public static List<IDexTreeVisitor> getFallbackPassesList() {
        List<IDexTreeVisitor> passes = new ArrayList<>();
        passes.add(new AttachTryCatchVisitor());
        passes.add(new AttachCommentsVisitor());
        passes.add(new ProcessInstructionsVisitor());
        passes.add(new FallbackModeVisitor());
        return passes;
    }

    /**
     * 获取 Jadx 版本号 (惰性初始化并缓存)
     *
     * @return 版本号字符串 若无法获取则返回 {@link #VERSION_DEV}
     */
    public static String getVersion() {
        if (version == null) {
            version = searchJadxVersion();
        }
        return version;
    }

    /**
     * 判断当前是否为开发版本
     *
     * @return 若版本号等于 {@link #VERSION_DEV} 返回 true，否则返回 false
     */
    public static boolean isDevVersion() {
        return getVersion().equals(VERSION_DEV);
    }

    /**
     * 从类加载器可见的所有 META-INF/MANIFEST.MF 中搜索 jadx-version 属性
     *
     * @return 找到的版本号 若未找到或发生异常则返回 {@link #VERSION_DEV}
     */
    private static String searchJadxVersion() {
        try {
            ClassLoader classLoader = Jadx.class.getClassLoader();
            if (classLoader != null) {
                Enumeration<URL> resources = classLoader.getResources("META-INF/MANIFEST.MF");
                while (resources.hasMoreElements()) {
                    try (InputStream is = resources.nextElement().openStream()) {
                        Manifest manifest = new Manifest(is);
                        String ver = manifest.getMainAttributes().getValue("jadx-version");
                        if (ver != null) {
                            return ver;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("无法读取 manifest 文件", e);
        }
        return VERSION_DEV;
    }
}
