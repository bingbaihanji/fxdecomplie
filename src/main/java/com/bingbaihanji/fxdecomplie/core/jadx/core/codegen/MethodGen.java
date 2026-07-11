package com.bingbaihanji.fxdecomplie.core.jadx.core.codegen;

import com.bingbaihanji.fxdecomplie.core.jadx.api.CommentsLevel;
import com.bingbaihanji.fxdecomplie.core.jadx.api.DecompilationMode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.args.IntegerFormat;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.InsnCodeOffset;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarNode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.AccessFlags;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.AnnotationMethodParamsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.Jadx;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodeGenUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ConstStringNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IfNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.CodeVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.CatchAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DepthTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.IDexTreeVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.MethodGen.FallbackOption.*;

/**
 * 方法代码生成器
 * <p>
 * 负责将 {@link MethodNode} 生成为 Java 源码文本，包括方法定义（修饰符、返回类型、方法名、
 * 参数列表、throws、注解等）与方法体方法体根据反编译模式（AUTO/RESTRUCTURE/SIMPLE/FALLBACK）
 * 采用不同的生成策略，并在生成失败时回退到指令转储（fallback dump）
 */
public class MethodGen {
    private static final Logger LOG = LoggerFactory.getLogger(MethodGen.class);

    /** 当前正在生成的方法节点 */
    private final MethodNode mth;
    /** 所属类的代码生成器 */
    private final ClassGen classGen;
    /** 注解生成器 */
    private final AnnotationGen annotationGen;
    /** 变量/参数名称生成器 */
    private final NameGen nameGen;

    public MethodGen(ClassGen classGen, MethodNode mth) {
        this.mth = mth;
        this.classGen = classGen;
        this.annotationGen = classGen.getAnnotationGen();
        this.nameGen = new NameGen(mth, classGen);
    }

    /**
     * 将给定指令数组以回退方式逐条输出到代码写入器
     *
     * @param code    代码写入器
     * @param mth     方法节点
     * @param insnArr 指令数组
     * @param option  回退选项
     */
    public static void addFallbackInsns(ICodeWriter code, MethodNode mth, InsnNode[] insnArr, FallbackOption option) {
        int startIndent = code.getIndent();
        MethodGen methodGen = getFallbackMethodGen(mth);
        InsnGen insnGen = new InsnGen(methodGen, true);
        InsnNode prevInsn = null;
        for (InsnNode insn : insnArr) {
            if (insn == null) {
                continue;
            }
            methodGen.dumpInsn(code, insnGen, option, startIndent, prevInsn, insn);
            prevInsn = insn;
        }
    }

    private static boolean isCommentEscapeNeeded(InsnNode insn, FallbackOption option) {
        if (option == COMMENTED_DUMP) {
            if (insn.getType() == InsnType.CONST_STR) {
                String str = ((ConstStringNode) insn).getString();
                return str.contains("*/");
            }
        }
        return false;
    }

    private static boolean needLabel(InsnNode insn, InsnNode prevInsn) {
        if (insn.contains(AType.EXC_HANDLER)) {
            return true;
        }
        if (insn.contains(AType.JUMP)) {
            // 不为 if 的 else 分支添加标签
            if (prevInsn != null && prevInsn.getType() == InsnType.IF) {
                List<JumpInfo> jumps = insn.getAll(AType.JUMP);
                if (jumps.size() == 1) {
                    JumpInfo jump = jumps.get(0);
                    if (jump.getSrc() == prevInsn.getOffset() && jump.getDest() == insn.getOffset()) {
                        int target = ((IfNode) prevInsn).getTarget();
                        return insn.getOffset() == target;
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * 返回方法代码生成的回退（fallback）变体
     *
     * @param mth 方法节点
     * @return 使用回退配置构建的 MethodGen
     */
    public static MethodGen getFallbackMethodGen(MethodNode mth) {
        ClassGen clsGen = new ClassGen(mth.getParentClass(), null, false, true, true, IntegerFormat.AUTO);
        return new MethodGen(clsGen, mth);
    }

    /**
     * 获取基本块对应的标签名，形如 {@code L<cId>}
     */
    public static String getLabelName(BlockNode block) {
        return String.format("L%d", block.getCId());
    }

    /**
     * 获取 if 指令跳转目标对应的标签名
     */
    public static String getLabelName(IfNode insn) {
        BlockNode thenBlock = insn.getThenBlock();
        if (thenBlock != null) {
            return getLabelName(thenBlock);
        }
        return getLabelName(insn.getTarget());
    }

    /**
     * 获取指令偏移量对应的标签名，负偏移使用 {@code LB_} 前缀
     */
    public static String getLabelName(int offset) {
        if (offset < 0) {
            return String.format("LB_%x", -offset);
        }
        return String.format("L%x", offset);
    }

    public ClassGen getClassGen() {
        return classGen;
    }

    public NameGen getNameGen() {
        return nameGen;
    }

    public MethodNode getMethodNode() {
        return mth;
    }

    /**
     * 生成方法定义部分（不含方法体的花括号内容）
     * <p>
     * 处理静态初始化块、匿名构造器、覆盖注解、访问修饰符、返回类型/构造器名、参数列表、
     * throws 声明以及注解默认值等
     *
     * @param code 代码写入器
     * @return 若在方法名后需要追加空格（普通方法定义）返回 true 匿名构造器等场景返回 false
     */
    public boolean addDefinition(ICodeWriter code) {
        if (mth.methodInfo().isClassInit()) {
            code.startLine();
            code.attachDefinition(mth);
            code.add("static");
            return true;
        }
        if (mth.contains(AFlag.ANONYMOUS_CONSTRUCTOR)) {
            // 不添加方法名和参数
            code.startLine();
            code.attachDefinition(mth);
            return false;
        }
        if (false) {
            ClassGen.addMthUsageInfo(code, mth);
        }
        addOverrideAnnotation(code, mth);
        annotationGen.addForMethod(code, mth);

        AccessInfo clsAccFlags = mth.getParentClass().getAccessFlags();
        AccessInfo ai = mth.getAccessFlags();
        // 接口中的方法不添加 'abstract' 和 'public'
        if (clsAccFlags.isInterface()) {
            ai = ai.remove(AccessFlags.ABSTRACT);
            ai = ai.remove(AccessFlags.PUBLIC);
        }
        // 注解类不添加 'public'
        if (clsAccFlags.isAnnotation()) {
            ai = ai.remove(AccessFlags.PUBLIC);
        }
        if (mth.methodInfo().hasAlias() && !ai.isConstructor()) {
            CodeGenUtils.addRenamedComment(code, mth, mth.getName());
        }
        if (mth.contains(AFlag.INCONSISTENT_CODE) && mth.checkCommentsLevel(CommentsLevel.ERROR)) {
            code.startLine("/*");
            code.incIndent();
            code.startLine("Code decompiled incorrectly, please refer to instructions dump.");
            if (!mth.root().getArgs().isShowInconsistentCode()) {
                if (code.isMetadataSupported()) {
                    code.startLine("To view partially-correct code enable 'Show inconsistent code' option in preferences");
                } else {
                    code.startLine("To view partially-correct add '--show-bad-code' argument");
                }
            }
            code.decIndent();
            code.startLine("*/");
        }

        code.startLineWithNum(mth.getSourceLine());
        code.add(ai.makeString(mth.checkCommentsLevel(CommentsLevel.INFO)));
        if (clsAccFlags.isInterface() && !mth.isNoCode() && !mth.getAccessFlags().isStatic()) {
            // 接口中带方法体的方法添加 'default'
            code.add("default ");
        }

        if (classGen.addGenericTypeParameters(code, mth.getTypeParameters(), false)) {
            code.add(' ');
        }
        if (ai.isConstructor()) {
            code.attachDefinition(mth);
            code.add(classGen.getClassNode().getAlias()); // 构造器
        } else {
            classGen.useType(code, mth.getReturnType());
            code.add(' ');
            MethodNode defMth = getMethodForDefinition();
            code.attachDefinition(defMth);
            code.add(defMth.getAlias());
        }
        code.add('(');
        addMethodArguments(code);
        code.add(')');

        annotationGen.addThrows(mth, code);

        // 为注解类添加默认值
        if (mth.getParentClass().getAccessFlags().isAnnotation()) {
            EncodedValue def = annotationGen.getAnnotationDefaultValue(mth);
            if (def != null) {
                code.add(" default ");
                annotationGen.encodeValue(mth.root(), code, def);
            }
        }
        return true;
    }

    private MethodNode getMethodForDefinition() {
        MethodReplaceAttr replaceAttr = mth.get(AType.METHOD_REPLACE);
        if (replaceAttr != null) {
            return replaceAttr.getReplaceMth();
        }
        return mth;
    }

    private void addOverrideAnnotation(ICodeWriter code, MethodNode mth) {
        MethodOverrideAttr overrideAttr = mth.get(AType.METHOD_OVERRIDE);
        if (overrideAttr == null) {
            return;
        }
        if (!overrideAttr.getBaseMethods().contains(mth)) {
            code.startLine("@Override");
            if (mth.checkCommentsLevel(CommentsLevel.INFO)) {
                code.add(" // ");
                code.add(Utils.listToString(overrideAttr.getOverrideList(), ", ",
                        md -> md.methodInfo().getDeclClass().getAliasFullName()));
            }
        }
        if (false) {
            code.startLine("// related by override: ");
            code.add(Utils.listToString(overrideAttr.getRelatedMthNodes(), ", ", m -> m.getParentClass().getFullName()));
        }
    }

    private void addMethodArguments(ICodeWriter code) {
        List<RegisterArg> args = mth.getArgRegs();
        AnnotationMethodParamsAttr paramsAnnotation = mth.get(JadxAttrType.ANNOTATION_MTH_PARAMETERS);
        int argNum = -1;
        int lastArgNum = args.size() - 1;
        boolean first = true;
        for (RegisterArg mthArg : args) {
            argNum++;
            if (SkipMethodArgsAttr.isSkip(mth, argNum)) {
                continue;
            }
            if (first) {
                first = false;
            } else {
                code.add(", ");
            }
            SSAVar ssaVar = mthArg.getSVar();
            CodeVar var;
            if (ssaVar == null) {
                // 抽象方法或接口方法
                var = CodeVar.fromMthArg(mthArg, classGen.isFallbackMode());
            } else {
                var = ssaVar.getCodeVar();
            }

            // 添加参数注解
            if (paramsAnnotation != null) {
                annotationGen.addForParameter(code, paramsAnnotation, argNum);
            }
            if (var.isFinal()) {
                code.add("final ");
            }
            ArgType argType;
            ArgType varType = var.getType();
            if (varType == null || varType == ArgType.UNKNOWN) {
                // 反编译出错时会出现该情况
                argType = mthArg.getInitType();
            } else {
                argType = varType;
            }
            if (argNum == lastArgNum && mth.getAccessFlags().isVarArgs()) {
                // 将最后一个数组参数改为可变参数（varargs）
                if (argType.isArray()) {
                    ArgType elType = argType.getArrayElement();
                    classGen.useType(code, elType);
                    code.add("...");
                } else {
                    mth.addWarnComment("Last argument in varargs method is not array: " + var);
                    classGen.useType(code, argType);
                }
            } else {
                classGen.useType(code, argType);
            }
            code.add(' ');
            String varName = nameGen.assignArg(var);
            if (code.isMetadataSupported() && ssaVar != null /* for fallback mode */) {
                code.attachDefinition(VarNode.get(mth, var));
            }
            code.add(varName);
        }
    }

    /**
     * 根据反编译模式生成方法体指令代码
     * <p>
     * 支持四种模式：
     * <ul>
     *   <li>AUTO —— 自动选择：回退模式或无区域（region）时走指令转储，否则用区域模式</li>
     *   <li>RESTRUCTURE —— 使用区域重组（RegionGen）生成</li>
     *   <li>SIMPLE —— 简化模式（基本块行号生成）</li>
     *   <li>FALLBACK —— 原始指令转储模式</li>
     * </ul>
     *
     * @param code 代码写入器
     * @throws CodegenException 代码生成失败时抛出
     */
    public void addInstructions(ICodeWriter code) throws CodegenException {
        JadxArgs args = mth.root().getArgs();
        DecompileModeOverrideAttr modeOverrideAttr = mth.getTopParentClass().get(AType.DECOMPILE_MODE_OVERRIDE);
        DecompilationMode mode;
        if (modeOverrideAttr != null) {
            mode = modeOverrideAttr.getMode();
        } else {
            mode = args.getDecompilationMode();
        }
        switch (mode) {
            case AUTO:
                if (classGen.isFallbackMode() || mth.getRegion() == null) {
                    // TODO: 先尝试简化模式
                    dumpInstructions(code);
                } else {
                    addRegionInsns(code);
                }
                break;

            case RESTRUCTURE:
                addRegionInsns(code);
                break;

            case SIMPLE:
                addSimpleMethodCode(code);
                break;

            case FALLBACK:
                addFallbackMethodCode(code, FALLBACK_MODE);
                break;
        }
    }

    /**
     * 使用区域重组（RegionGen）生成方法体指令
     * <p>
     * 若生成过程中发生栈溢出或异常，则记录错误并回退到指令转储
     *
     * @param code 代码写入器
     * @throws CodegenException 需要重启代码生成时抛出
     */
    public void addRegionInsns(ICodeWriter code) throws CodegenException {
        try {
            RegionGen regionGen = new RegionGen(this);
            regionGen.makeRegion(code, mth.getRegion());
        } catch (StackOverflowError | BootstrapMethodError e) {
            mth.addError("Method code generation error", new JadxOverflowException("StackOverflow"));
            CodeGenUtils.addErrors(code, mth);
            dumpInstructions(code);
        } catch (Exception e) {
            if (mth.getParentClass().getTopParentClass().contains(AFlag.RESTART_CODEGEN)) {
                throw e;
            }
            mth.addError("Method code generation error", e);
            CodeGenUtils.addErrors(code, mth);
            dumpInstructions(code);
        }
    }

    private void addSimpleMethodCode(ICodeWriter code) {
        if (mth.getBasicBlocks() == null) {
            code.startLine("// Blocks not ready for simple mode, using fallback");
            addFallbackMethodCode(code, FALLBACK_MODE);
            return;
        }
        JadxArgs args = mth.root().getArgs();
        ICodeWriter tmpCode = args.getCodeWriterProvider().apply(args);
        try {
            tmpCode.setIndent(code.getIndent());
            generateSimpleCode(tmpCode);
            code.add(tmpCode);
        } catch (Exception e) {
            mth.addError("Simple mode code generation failed", e);
            CodeGenUtils.addError(code, "Simple mode code generation failed", e);
            dumpInstructions(code);
        }
    }

    private void generateSimpleCode(ICodeWriter code) throws CodegenException {
        SimpleModeHelper helper = new SimpleModeHelper(mth);
        List<BlockNode> blocks = helper.prepareBlocks();
        InsnGen insnGen = new InsnGen(this, true);
        for (BlockNode block : blocks) {
            if (block.contains(AFlag.DONT_GENERATE)) {
                continue;
            }
            if (helper.isNeedStartLabel(block)) {
                code.decIndent();
                code.startLine(getLabelName(block)).add(':');
                code.incIndent();
            }
            for (InsnNode insn : block.getInstructions()) {
                if (!insn.contains(AFlag.DONT_GENERATE)) {
                    if (insn.getResult() != null) {
                        CodeVar codeVar = insn.getResult().getSVar().getCodeVar();
                        if (!codeVar.isDeclared()) {
                            insn.add(AFlag.DECLARE_VAR);
                            codeVar.setDeclared(true);
                        }
                    }
                    InsnCodeOffset.attach(code, insn);
                    insnGen.makeInsn(insn, code);
                    addCatchComment(code, insn, false);
                    CodeGenUtils.addCodeComments(code, mth, insn);
                }
            }
            if (helper.isNeedEndGoto(block)) {
                code.startLine("goto ").add(getLabelName(block.getSuccessors().get(0)));
            }
        }
    }

    /**
     * 转储方法指令：以注释形式输出回退指令，并生成一条抛出
     * {@code UnsupportedOperationException("Method not decompiled...")} 的语句
     *
     * @param code 代码写入器
     */
    public void dumpInstructions(ICodeWriter code) {
        if (mth.checkCommentsLevel(CommentsLevel.ERROR)) {
            code.startLine("/*");
            addFallbackMethodCode(code, COMMENTED_DUMP);
            code.startLine("*/");
        }
        code.startLine("throw new UnsupportedOperationException(\"Method not decompiled: ")
                .add(mth.getParentClass().getClassInfo().getAliasFullName())
                .add('.')
                .add(mth.getAlias())
                .add('(')
                .add(Utils.listToString(mth.methodInfo().getArgumentsTypes()))
                .add("):")
                .add(mth.methodInfo().getReturnType().toString())
                .add("\");");
    }

    /**
     * 生成回退模式的方法代码：重新加载原始指令并逐条转储
     *
     * @param code           代码写入器
     * @param fallbackOption 回退选项（回退模式 / 块转储 / 注释转储）
     */
    public void addFallbackMethodCode(ICodeWriter code, FallbackOption fallbackOption) {
        if (fallbackOption == COMMENTED_DUMP && mth.getCommentsLevel() != CommentsLevel.DEBUG) {
            long insnCountEstimate = mth.getInsnsCount();
            if (insnCountEstimate > 200) {
                code.incIndent();
                code.startLine("Method dump skipped, instruction units count: " + insnCountEstimate);
                if (code.isMetadataSupported()) {
                    code.startLine("To view this dump change 'Code comments level' option to 'DEBUG'");
                } else {
                    code.startLine("To view this dump add '--comments-level debug' option");
                }
                code.decIndent();
                return;
            }
        }
        if (fallbackOption != FALLBACK_MODE) {
            List<JadxError> errors = mth.getAll(AType.JADX_ERROR); // 卸载前保留错误信息
            try {
                // 加载原始指令
                mth.unload();
                mth.load();
                for (IDexTreeVisitor visitor : Jadx.getFallbackPassesList()) {
                    DepthTraversal.visit(visitor, mth);
                }
            } catch (Exception e) {
                LOG.error("Error reload instructions in fallback mode:", e);
                code.startLine("// Can't load method instructions: " + e.getMessage());
                return;
            } finally {
                mth.addAttr(AType.JADX_ERROR, errors);
            }
        }
        InsnNode[] insnArr = mth.getInstructions();
        if (insnArr == null) {
            code.startLine("// Can't load method instructions.");
            return;
        }
        code.incIndent();
        if (mth.getThisArg() != null) {
            code.startLine(nameGen.useArg(mth.getThisArg())).add(" = this;");
        }
        addFallbackInsns(code, mth, insnArr, fallbackOption);
        code.decIndent();
    }

    private boolean dumpInsn(ICodeWriter code, InsnGen insnGen, FallbackOption option, int startIndent,
                             @Nullable InsnNode prevInsn, InsnNode insn) {
        if (insn.contains(AType.JADX_ERROR)) {
            for (JadxError error : insn.getAll(AType.JADX_ERROR)) {
                code.startLine("// ").add(error.getError());
            }
            return true;
        }
        if (option != BLOCK_DUMP && needLabel(insn, prevInsn)) {
            code.decIndent();
            code.startLine(getLabelName(insn.getOffset()) + ':');
            code.incIndent();
        }
        if (insn.getType() == InsnType.NOP) {
            return true;
        }
        try {
            boolean escapeComment = isCommentEscapeNeeded(insn, option);
            if (escapeComment) {
                code.decIndent();
                code.startLine("*/");
                code.startLine("//  ");
            } else {
                code.startLineWithNum(insn.getSourceLine());
            }
            InsnCodeOffset.attach(code, insn);
            RegisterArg resArg = insn.getResult();
            if (resArg != null) {
                ArgType varType = resArg.getInitType();
                if (varType.isTypeKnown()) {
                    code.add(varType.toString()).add(' ');
                }
            }
            insnGen.makeInsn(insn, code, InsnGen.Flags.INLINE);
            if (escapeComment) {
                code.startLine("/*");
                code.incIndent();
            }
            addCatchComment(code, insn, true);
            CodeGenUtils.addCodeComments(code, mth, insn);
        } catch (Exception e) {
            LOG.debug("Error generate fallback instruction: ", e.getCause());
            code.setIndent(startIndent);
            code.startLine("// error: " + insn);
        }
        return false;
    }

    private void addCatchComment(ICodeWriter code, InsnNode insn, boolean raw) {
        CatchAttr catchAttr = insn.get(AType.EXC_CATCH);
        if (catchAttr == null) {
            return;
        }
        code.add("     // Catch:");
        for (ExceptionHandler handler : catchAttr.getHandlers()) {
            code.add(' ');
            classGen.useClass(code, handler.getArgType());
            code.add(" -> ");
            if (raw) {
                code.add(getLabelName(handler.getHandlerOffset()));
            } else {
                code.add(getLabelName(handler.getHandlerBlock()));
            }
        }
    }

    /** 回退代码生成选项 */
    public enum FallbackOption {
        /** 完整回退模式 */
        FALLBACK_MODE,
        /** 块转储 */
        BLOCK_DUMP,
        /** 注释形式的转储 */
        COMMENTED_DUMP
    }
}
