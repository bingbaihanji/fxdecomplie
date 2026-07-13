package com.bingbaihanji.fxdecomplie.core.jadx.core;

import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.CodeGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.DecompileModeOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.LoadStage;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DepthTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ProcessState.*;

/**
 * 类处理核心组件，负责类的加载 处理和代码生成
 * 管理反编译过程中的各个阶段 (pass)，协调类的依赖处理和代码生成流程
 */
public class ProcessClass {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessClass.class);

    /** 未生成代码的标记对象 */
    private static final ICodeInfo NOT_GENERATED = new SimpleCodeInfo("");

    /** 反编译处理阶段列表 (访问者链) */
    private final List<IDexTreeVisitor> passes;
    /** 不同反编译模式对应的 ProcessClass 实例缓存 */
    private final Map<DecompilationMode, ProcessClass> modesMap = new EnumMap<>(DecompilationMode.class);

    /**
     * 构造函数，初始化处理阶段列表
     *
     * @param passesList 反编译处理阶段列表
     */
    public ProcessClass(List<IDexTreeVisitor> passesList) {
        this.passes = passesList;
    }

    /**
     * 根据反编译模式获取对应的处理阶段列表
     *
     * @param baseArgs 基础配置参数
     * @param mode     反编译模式
     * @return 处理阶段列表
     * @throws JadxRuntimeException 遇到未知的反编译模式时抛出
     */
    private static List<IDexTreeVisitor> getPassesForMode(JadxArgs baseArgs, DecompilationMode mode) {
        switch (mode) {
            case FALLBACK:
                return Jadx.getFallbackPassesList();

            case SIMPLE:
                // 将属性复制到新参数中
                // 需要与 Jadx.getSimpleModePasses 方法中的属性使用保持同步
                JadxArgs args = new JadxArgs();
                args.setDebugInfo(baseArgs.isDebugInfo());
                args.setCommentsLevel(baseArgs.getCommentsLevel());
                return Jadx.getSimpleModePasses(args);

            default:
                throw new JadxRuntimeException("Unexpected decompilation mode: " + mode);
        }
    }

    /**
     * 处理类节点的核心方法
     *
     * @param cls     待处理的类节点
     * @param codegen 是否执行代码生成阶段
     * @return 代码生成模式下返回生成的代码信息，否则返回 null
     */
    @Nullable
    private ICodeInfo process(ClassNode cls, boolean codegen) {
        if (!codegen && cls.getState() == PROCESS_COMPLETE) {
            // 无需处理，类已完成处理
            return null;
        }
        Utils.checkThreadInterrupt();
        synchronized (cls.getClassInfo()) {
            try {
                if (cls.contains(AFlag.CLASS_DEEP_RELOAD)) {
                    cls.remove(AFlag.CLASS_DEEP_RELOAD);
                    cls.deepUnload();
                    cls.add(AFlag.CLASS_UNLOADED);
                }
                if (cls.contains(AFlag.CLASS_UNLOADED)) {
                    cls.root().runPreDecompileStageForClass(cls);
                    cls.remove(AFlag.CLASS_UNLOADED);
                }
                if (cls.getState() == GENERATED_AND_UNLOADED) {
                    // 强制重新加载代码
                    cls.setState(NOT_LOADED);
                }
                if (codegen) {
                    cls.setLoadStage(LoadStage.CODEGEN_STAGE);
                    if (cls.contains(AFlag.RELOAD_AT_CODEGEN_STAGE)) {
                        cls.remove(AFlag.RELOAD_AT_CODEGEN_STAGE);
                        cls.unload();
                    }
                } else {
                    cls.setLoadStage(LoadStage.PROCESS_STAGE);
                }
                if (cls.getState() == NOT_LOADED) {
                    cls.load();
                }
                if (cls.getState() == LOADED) {
                    cls.setState(PROCESS_STARTED);
                    for (IDexTreeVisitor visitor : passes) {
                        DepthTraversal.visit(visitor, cls);
                    }
                    cls.setState(PROCESS_COMPLETE);
                }
                if (codegen) {
                    Utils.checkThreadInterrupt();
                    ICodeInfo code = CodeGen.generate(cls);
                    if (!cls.contains(AFlag.DONT_UNLOAD_CLASS)) {
                        cls.unload();
                        cls.setState(GENERATED_AND_UNLOADED);
                    }
                    return code;
                }
                return null;
            } catch (StackOverflowError | Exception e) {
                if (codegen) {
                    throw e;
                }
                cls.addError("Class process error: " + e.getClass().getSimpleName(), e);
                return null;
            }
        }
    }

    /**
     * 为指定类生成反编译代码
     * 先处理顶级父类，然后处理依赖类，最后生成当前类的代码
     *
     * @param cls 待生成代码的类节点
     * @return 生成的代码信息
     * @throws JadxRuntimeException 代码生成失败时抛出
     */
    @NotNull
    public ICodeInfo generateCode(ClassNode cls) {
        ClassNode topParentClass = cls.getTopParentClass();
        if (topParentClass != cls) {
            return generateCode(topParentClass);
        }
        try {
            if (cls.contains(AFlag.DONT_GENERATE)) {
                process(cls, false);
                return NOT_GENERATED;
            }
            for (ClassNode depCls : cls.getDependencies()) {
                process(depCls, false);
            }
            if (!cls.getCodegenDeps().isEmpty()) {
                process(cls, false);
                for (ClassNode codegenDep : cls.getCodegenDeps()) {
                    process(codegenDep, false);
                }
            }
            ICodeInfo code = process(cls, true);
            if (code == null) {
                throw new JadxRuntimeException("Codegen failed");
            }
            return code;
        } catch (StackOverflowError | Exception e) {
            throw new JadxRuntimeException("Failed to generate code for class: " + cls.getFullName(), e);
        }
    }

    /**
     * 强制处理类，不处理其依赖项
     * 用于需要单独处理某个类而不触发完整依赖链处理的场景
     *
     * @param cls 待处理的类节点
     * @throws JadxRuntimeException 处理失败时抛出
     */
    public void forceProcess(ClassNode cls) {
        ClassNode topParentClass = cls.getTopParentClass();
        if (topParentClass != cls) {
            forceProcess(topParentClass);
            return;
        }
        try {
            process(cls, false);
        } catch (StackOverflowError | Exception e) {
            throw new JadxRuntimeException("Failed to process class: " + cls.getFullName(), e);
        }
    }

    /**
     * 强制为类生成代码，不处理其依赖项
     * 跳过依赖处理阶段，直接执行代码生成
     *
     * @param cls 待生成代码的类节点
     * @return 生成的代码信息，失败时返回 null
     * @throws JadxRuntimeException 代码生成失败时抛出
     */
    public @Nullable ICodeInfo forceGenerateCode(ClassNode cls) {
        try {
            return process(cls, true);
        } catch (StackOverflowError | Exception e) {
            throw new JadxRuntimeException("Failed to generate code for class: " + cls.getFullName(), e);
        }
    }

    /**
     * 使用指定的反编译模式为类生成代码
     * 根据不同的反编译模式 (如 SIMPLE FALLBACK)使用不同的处理流程
     *
     * @param cls  待生成代码的类节点
     * @param mode 反编译模式
     * @return 生成的代码信息，失败时返回 null
     */
    public @Nullable ICodeInfo forceGenerateCodeForMode(ClassNode cls, DecompilationMode mode) {
        synchronized (modesMap) {
            ProcessClass prCls = modesMap.computeIfAbsent(mode, m -> {
                RootNode root = cls.root();
                ProcessClass newPrCls = new ProcessClass(getPassesForMode(root.getArgs(), m));
                newPrCls.initPasses(root);
                return newPrCls;
            });
            try {
                cls.addAttr(new DecompileModeOverrideAttr(mode));
                return prCls.forceGenerateCode(cls);
            } finally {
                cls.remove(AType.DECOMPILE_MODE_OVERRIDE);
            }
        }
    }

    /**
     * 初始化所有处理阶段 (访问者)
     * 在开始处理类之前调用，确保所有访问者已准备好
     *
     * @param root 根节点，包含整个 DEX 文件的结构信息
     */
    public void initPasses(RootNode root) {
        for (IDexTreeVisitor pass : passes) {
            try {
                pass.init(root);
            } catch (Exception e) {
                LOG.error("Visitor init failed: {}", pass.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 处理方法直到遇到指定名称的访问者
     * 用于调试或部分处理场景，可控制是否包含目标访问者本身
     *
     * @param mth            待处理的方法节点
     * @param visitorName    目标访问者名称
     * @param includeVisitor 是否包含目标访问者本身 (true: 执行到目标访问者并包含; false: 执行到目标访问者之前)
     * @return 是否找到并执行了目标访问者
     */
    public boolean processMethodUntilVisitor(MethodNode mth, String visitorName, boolean includeVisitor) {
        IDexTreeVisitor foundPass = null;
        IDexTreeVisitor prevPass = null;
        for (IDexTreeVisitor pass : passes) {
            if (pass.getName().equals(visitorName)) {
                if (includeVisitor) {
                    foundPass = pass;
                } else {
                    foundPass = prevPass;
                }
                break;
            }
            prevPass = pass;
        }
        if (foundPass == null) {
            return false;
        }
        return processMethodToVisitor(mth, foundPass);
    }

    /**
     * 处理方法直到指定的访问者
     * 重新加载方法后，按顺序执行处理阶段直到指定访问者
     *
     * @param mth               待处理的方法节点
     * @param lastPassToProcess 最后一个要执行的处理阶段
     * @return 是否成功执行到目标访问者
     * @throws JadxRuntimeException 处理失败时抛出
     */
    public boolean processMethodToVisitor(MethodNode mth, IDexTreeVisitor lastPassToProcess) {
        synchronized (mth.getTopParentClass().getClassInfo()) {
            try {
                mth.unload();
                mth.load();
                for (IDexTreeVisitor pass : passes) {
                    DepthTraversal.visit(pass, mth);
                    if (pass == lastPassToProcess) {
                        return true;
                    }
                }
            } catch (Exception e) {
                throw new JadxRuntimeException("Failed to process method to visitor: " + lastPassToProcess, e);
            }
            return false;
        }
    }

    /**
     * 获取处理阶段 (访问者)列表
     *
     * @return 反编译处理阶段列表
     */
    // TODO: 将 passes 列表设为私有且不可见
    public List<IDexTreeVisitor> getPasses() {
        return passes;
    }
}
