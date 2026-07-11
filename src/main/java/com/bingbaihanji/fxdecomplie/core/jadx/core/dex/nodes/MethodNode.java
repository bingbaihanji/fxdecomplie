package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JavaMethod;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.NodeDeclareRef;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarNode;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ICodeReader;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IDebugInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.IMethodData;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types.ExceptionsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.api.utils.CodeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodThrowsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.NotificationAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo.AFType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnDecoder;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.TypeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.Region;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExceptionHandler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.InitCodeVariables;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.DecodeException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.lockList;

/**
 * 方法节点，表示 DEX 字节码中的一个方法。
 * <p>
 * 继承自 {@link NotificationAttrNode}，实现了方法详情、可加载、代码节点等接口。
 * 包含方法的字节码指令、基本块、异常处理器、循环信息、SSA 变量等反编译所需数据。
 * </p>
 */
public class MethodNode extends NotificationAttrNode implements IMethodDetails, ILoadable, ICodeNode, Comparable<MethodNode> {
    private static final Logger LOG = LoggerFactory.getLogger(MethodNode.class);
    /** 空指令数组，用于无代码的方法 */
    private static final InsnNode[] EMPTY_INSN_ARRAY = new InsnNode[0];

    /** 方法元信息（名称、签名等） */
    private final MethodInfo mthInfo;
    /** 所属父类节点 */
    private final ClassNode parentClass;
    /** 字节码读取器，用于读取方法的原始字节码 */
    private final ICodeReader codeReader;
    /** 方法字节码中的原始指令数量 */
    private final int insnsCount;
    /** 访问标志（public/private/static 等） */
    private AccessInfo accFlags;
    /** 标识该方法是否无代码（如抽象方法、接口方法） */
    private boolean noCode;
    /** 寄存器总数 */
    private int regsCount;
    /** 参数起始寄存器索引 */
    private int argsStartReg;

    /** 标识方法是否已加载反编译数据 */
    private boolean loaded;

    // 加载后可用的附加信息，卸载时保留
    private ArgType retType;
    private List<ArgType> argTypes;
    private List<ArgType> typeParameters;

    // 反编译数据，卸载时重置
    /** this 引用的寄存器参数（非静态方法） */
    private RegisterArg thisArg;
    /** 方法参数的寄存器参数列表 */
    private List<RegisterArg> argsList;
    /** 方法指令数组 */
    private @Nullable InsnNode[] instructions;
    /** 基本块列表 */
    private List<BlockNode> blocks;
    /** 基本块最大计数 ID */
    private int blocksMaxCId;
    /** 入口基本块 */
    private BlockNode enterBlock;
    /** 出口基本块 */
    private BlockNode exitBlock;
    /** SSA 变量列表 */
    private List<SSAVar> sVars;
    /** 异常处理器列表 */
    private List<ExceptionHandler> exceptionHandlers;
    /** 循环信息列表 */
    private List<LoopInfo> loops;
    /** 反编译后的代码区域 */
    private Region region;

    /** 调用此方法的方法列表（调用者） */
    private List<MethodNode> useIn = Collections.emptyList();
    /** 未解析的调用此方法的方法信息 */
    private List<MethodInfo> unresolvedUsed = Collections.emptyList();
    /** 此方法调用的方法集合（被调用者） */
    private Set<MethodNode> methodsUsed = new HashSet<>();
    /** 标识此方法是否包含递归自调用 */
    private boolean callsSelf = false;

    private JavaMethod javaNode;

    private MethodNode(ClassNode classNode, IMethodData mthData) {
        this.mthInfo = MethodInfo.fromRef(classNode.root(), mthData.getMethodRef());
        this.parentClass = classNode;
        this.accFlags = new AccessInfo(mthData.getAccessFlags(), AFType.METHOD);
        ICodeReader codeReader = mthData.getCodeReader();
        if (codeReader == null) {
            this.noCode = true;
            this.codeReader = null;
            this.insnsCount = 0;
        } else {
            this.noCode = false;
            this.codeReader = codeReader.copy();
            this.insnsCount = codeReader.getUnitsCount();
        }

        this.retType = mthInfo.getReturnType();
        this.argTypes = mthInfo.getArgumentsTypes();
        this.typeParameters = Collections.emptyList();
        unload();
    }

    /**
     * 根据方法数据构建方法节点实例。
     *
     * @param classNode  所属父类节点
     * @param methodData 方法数据（来自输入插件）
     * @return 构建完成的方法节点
     */
    public static MethodNode build(ClassNode classNode, IMethodData methodData) {
        MethodNode methodNode = new MethodNode(classNode, methodData);
        methodNode.addAttrs(methodData.getAttributes());
        return methodNode;
    }

    /**
     * 卸载方法的反编译数据，释放内存。返回类型、参数类型和类型参数不会被卸载。
     */
    @Override
    public void unload() {
        loaded = false;
        // 不卸载 retType、argTypes、typeParameters
        thisArg = null;
        argsList = null;
        sVars = Collections.emptyList();
        instructions = null;
        blocks = null;
        blocksMaxCId = 0;
        enterBlock = null;
        exitBlock = null;
        region = null;
        exceptionHandlers = Collections.emptyList();
        loops = Collections.emptyList();
        unloadAttributes();
    }

    /**
     * 更新方法的参数类型和返回类型。
     *
     * @param argTypes 新的参数类型列表
     * @param retType  新的返回类型
     */
    public void updateTypes(List<ArgType> argTypes, ArgType retType) {
        this.argTypes = argTypes;
        this.retType = retType;
    }

    /**
     * 更新方法的泛型类型参数列表。
     *
     * @param typeParameters 新的类型参数列表
     */
    public void updateTypeParameters(List<ArgType> typeParameters) {
        this.typeParameters = typeParameters;
    }

    /**
     * 加载方法的反编译数据，包括指令解码和参数初始化。
     *
     * @throws DecodeException 如果指令解码失败
     */
    @Override
    public void load() throws DecodeException {
        if (loaded) {
            // 方法已加载，直接返回
            return;
        }
        try {
            loaded = true;
            if (noCode) {
                regsCount = 0;
                // TODO: 无代码时不需要寄存器
                initArguments(this.argTypes);
                return;
            }

            this.regsCount = codeReader.getRegistersCount();
            this.argsStartReg = codeReader.getArgsStartReg();
            initArguments(this.argTypes);

            if (contains(AType.JADX_ERROR)) {
                // 存在错误时不加载指令
                this.instructions = EMPTY_INSN_ARRAY;
            } else {
                InsnDecoder decoder = new InsnDecoder(this);
                this.instructions = decoder.process(codeReader);
            }
        } catch (Exception e) {
            if (!noCode) {
                unload();
                noCode = true;
                // 以无代码模式加载
                load();
                noCode = false;
            }
            throw new DecodeException(this, "Load method exception: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * 重新加载方法数据，先卸载再加载。
     */
    public void reload() {
        unload();
        try {
            load();
        } catch (DecodeException e) {
            throw new JadxRuntimeException("Failed to reload method " + getClass().getName() + "." + getName());
        }
    }

    /**
     * 初始化方法参数寄存器，包括 this 引用（非静态方法）和方法参数。
     *
     * @param args 参数类型列表
     */
    private void initArguments(List<ArgType> args) {
        int pos = getArgsStartPos(args);
        TypeUtils typeUtils = root().getTypeUtils();
        if (accFlags.isStatic()) {
            thisArg = null;
        } else {
            ArgType thisClsType = typeUtils.expandTypeVariables(this, parentClass.getType());
            RegisterArg arg = InsnArg.reg(pos++, thisClsType);
            arg.add(AFlag.THIS);
            arg.add(AFlag.IMMUTABLE_TYPE);
            thisArg = arg;
        }
        if (args.isEmpty()) {
            argsList = Collections.emptyList();
            return;
        }
        argsList = new ArrayList<>(args.size());
        for (ArgType argType : args) {
            ArgType expandedType = typeUtils.expandTypeVariables(this, argType);
            RegisterArg regArg = InsnArg.reg(pos, expandedType);
            regArg.add(AFlag.METHOD_ARGUMENT);
            regArg.add(AFlag.IMMUTABLE_TYPE);
            argsList.add(regArg);
            pos += argType.getRegCount();
        }
    }

    /**
     * 计算参数在寄存器中的起始位置。
     *
     * @param args 参数类型列表
     * @return 参数起始寄存器位置
     */
    private int getArgsStartPos(List<ArgType> args) {
        if (noCode) {
            return 0;
        }
        if (argsStartReg != -1) {
            return argsStartReg;
        }
        int pos = regsCount;
        for (ArgType arg : args) {
            pos -= arg.getRegCount();
        }
        if (!accFlags.isStatic()) {
            pos--;
        }
        return pos;
    }

    @Override
    @NotNull
    public List<ArgType> getArgTypes() {
        if (argTypes == null) {
            throw new JadxRuntimeException("Method generic types not initialized: " + this);
        }
        return argTypes;
    }

    /**
     * 更新方法的参数类型，并重新初始化参数寄存器。
     *
     * @param newArgTypes 新的参数类型列表
     * @param comment     调试注释信息
     */
    public void updateArgTypes(List<ArgType> newArgTypes, String comment) {
        this.addDebugComment(comment + ", original types: " + getArgTypes());
        this.argTypes = Collections.unmodifiableList(newArgTypes);
        initArguments(newArgTypes);
    }

    /**
     * 判断方法是否包含泛型参数（即原始参数类型与当前参数类型不同）。
     *
     * @return 如果包含泛型参数返回 true
     */
    public boolean containsGenericArgs() {
        return !Objects.equals(mthInfo.getArgumentsTypes(), getArgTypes());
    }

    @Override
    @NotNull
    public ArgType getReturnType() {
        return retType;
    }

    /**
     * 更新方法的返回类型。
     *
     * @param type 新的返回类型
     */
    public void updateReturnType(ArgType type) {
        this.retType = type;
    }

    /**
     * 判断方法返回类型是否为 void。
     *
     * @return 如果返回类型为 void 返回 true
     */
    public boolean isVoidReturn() {
        return mthInfo.getReturnType().equals(ArgType.VOID);
    }

    /**
     * 收集方法参数的变量节点列表，用于代码元数据映射。
     *
     * @return 参数变量节点列表
     */
    public List<VarNode> collectArgNodes() {
        ICodeInfo codeInfo = getTopParentClass().getCode();
        int mthDefPos = getDefPosition();
        int lineEndPos = CodeUtils.getLineEndForPos(codeInfo.getCodeStr(), mthDefPos);
        int argsCount = mthInfo.getArgsCount();
        List<VarNode> args = new ArrayList<>(argsCount);
        codeInfo.getCodeMetadata().searchDown(mthDefPos, (pos, ann) -> {
            if (pos > lineEndPos) {
                // 到达行尾时停止搜索
                return Boolean.TRUE;
            }
            if (ann instanceof NodeDeclareRef) {
                ICodeNodeRef declRef = ((NodeDeclareRef) ann).getNode();
                if (declRef instanceof VarNode) {
                    VarNode varNode = (VarNode) declRef;
                    if (!varNode.getMth().equals(this)) {
                        // 已进入其他方法范围，停止搜索
                        return Boolean.TRUE;
                    }
                    args.add(varNode);
                }
            }
            return null;
        });
        if (args.size() != argsCount) {
            LOG.warn("Incorrect args count, expected: {}, got: {}", argsCount, args.size());
        }
        return args;
    }

    /**
     * 返回参数寄存器列表（不含 this）。
     *
     * @return 参数寄存器列表
     * @throws JadxRuntimeException 如果参数寄存器尚未加载
     */
    public List<RegisterArg> getArgRegs() {
        if (argsList == null) {
            throw new JadxRuntimeException("Method arg registers not loaded: " + this
                    + ", class status: " + parentClass.getTopParentClass().getState());
        }
        return argsList;
    }

    /**
     * 返回全部参数寄存器列表，非静态方法会在首位包含 this 参数。
     *
     * @return 包含 this（如有）的全部参数寄存器列表
     */
    public List<RegisterArg> getAllArgRegs() {
        List<RegisterArg> argRegs = getArgRegs();
        if (thisArg != null) {
            List<RegisterArg> list = new ArrayList<>(argRegs.size() + 1);
            list.add(thisArg);
            list.addAll(argRegs);
            return list;
        }
        return argRegs;
    }

    /**
     * 返回 this 引用寄存器参数，静态方法返回 null。
     *
     * @return this 参数寄存器，静态方法为 null
     */
    @Nullable
    public RegisterArg getThisArg() {
        return thisArg;
    }

    /**
     * 标记跳过第一个参数（如合成参数）。
     */
    public void skipFirstArgument() {
        this.add(AFlag.SKIP_FIRST_ARG);
    }

    @Override
    public List<ArgType> getTypeParameters() {
        return typeParameters;
    }

    /**
     * 返回方法原始名称。
     *
     * @return 方法名
     */
    public String getName() {
        return mthInfo.getName();
    }

    /**
     * 返回方法别名（重命名后使用的名称）。
     *
     * @return 方法别名
     */
    public String getAlias() {
        return mthInfo.getAlias();
    }

    @Override
    public ClassNode getDeclaringClass() {
        return parentClass;
    }

    /**
     * 返回方法直接所属的父类节点。
     *
     * @return 父类节点
     */
    public ClassNode getParentClass() {
        return parentClass;
    }

    /**
     * 返回最顶层的父类节点（穿透内部类嵌套）。
     *
     * @return 顶层父类节点
     */
    public ClassNode getTopParentClass() {
        return parentClass.getTopParentClass();
    }

    /**
     * 判断方法是否无代码（抽象方法、接口方法或加载失败）。
     *
     * @return 无代码返回 true
     */
    public boolean isNoCode() {
        return noCode;
    }

    /**
     * 返回方法解码后的指令数组。
     *
     * @return 指令数组，未加载时可能为 null
     */
    public InsnNode[] getInstructions() {
        return instructions;
    }

    /**
     * 释放指令数组占用的内存。
     */
    public void unloadInsnArr() {
        this.instructions = null;
    }

    /**
     * 初始化基本块列表。
     */
    public void initBasicBlocks() {
        blocks = new ArrayList<>();
    }

    /**
     * 完成基本块构建，锁定基本块和循环信息使其不可变。
     */
    public void finishBasicBlocks() {
        blocks = lockList(blocks);
        loops = lockList(loops);
        blocks.forEach(BlockNode::lock);
    }

    /**
     * 返回方法的基本块列表。
     *
     * @return 基本块列表
     */
    public List<BlockNode> getBasicBlocks() {
        return blocks;
    }

    /**
     * 设置方法的基本块列表并更新块位置。
     *
     * @param blocks 基本块列表
     */
    public void setBasicBlocks(List<BlockNode> blocks) {
        this.blocks = blocks;
        updateBlockPositions();
    }

    /**
     * 更新基本块的位置索引。
     */
    public void updateBlockPositions() {
        BlockNode.updateBlockPositions(blocks);
    }

    /**
     * 返回下一个基本块的计数 ID 并自增。
     *
     * @return 下一个基本块计数 ID
     */
    public int getNextBlockCId() {
        return blocksMaxCId++;
    }

    /**
     * 返回入口基本块。
     *
     * @return 入口基本块
     */
    public BlockNode getEnterBlock() {
        return enterBlock;
    }

    /**
     * 设置入口基本块。
     *
     * @param enterBlock 入口基本块
     */
    public void setEnterBlock(BlockNode enterBlock) {
        this.enterBlock = enterBlock;
    }

    /**
     * 返回出口基本块。
     *
     * @return 出口基本块
     */
    public BlockNode getExitBlock() {
        return exitBlock;
    }

    /**
     * 设置出口基本块。
     *
     * @param exitBlock 出口基本块
     */
    public void setExitBlock(BlockNode exitBlock) {
        this.exitBlock = exitBlock;
    }

    /**
     * 返回出口块前驱的基本块列表（即方法退出前的块）。
     *
     * @return 出口前驱基本块列表
     */
    public List<BlockNode> getPreExitBlocks() {
        return exitBlock.getPredecessors();
    }

    /**
     * 判断给定基本块是否为出口前驱块。
     *
     * @param block 待检查的基本块
     * @return 若为出口前驱块返回 true
     */
    public boolean isPreExitBlock(BlockNode block) {
        List<BlockNode> successors = block.getSuccessors();
        if (successors.size() == 1) {
            return successors.get(0).equals(exitBlock);
        }
        return exitBlock.getPredecessors().contains(block);
    }

    /**
     * 重置循环信息列表。
     */
    public void resetLoops() {
        this.loops = new ArrayList<>();
    }

    /**
     * 注册一个循环信息并分配 ID。
     *
     * @param loop 循环信息
     */
    public void registerLoop(LoopInfo loop) {
        if (loops.isEmpty()) {
            loops = new ArrayList<>(5);
        }
        loop.setId(loops.size());
        loops.add(loop);
    }

    /**
     * 返回包含指定基本块的循环（返回首个匹配）。
     *
     * @param block 基本块
     * @return 包含该块的循环信息，无则返回 null
     */
    @Nullable
    public LoopInfo getLoopForBlock(BlockNode block) {
        if (loops.isEmpty()) {
            return null;
        }
        for (LoopInfo loop : loops) {
            if (loop.getLoopBlocks().contains(block)) {
                return loop;
            }
        }
        return null;
    }

    /**
     * 返回包含指定基本块的所有循环。
     *
     * @param block 基本块
     * @return 包含该块的所有循环信息列表
     */
    public List<LoopInfo> getAllLoopsForBlock(BlockNode block) {
        if (loops.isEmpty()) {
            return Collections.emptyList();
        }
        List<LoopInfo> list = new ArrayList<>(loops.size());
        for (LoopInfo loop : loops) {
            if (loop.getLoopBlocks().contains(block)) {
                list.add(loop);
            }
        }
        return list;
    }

    /**
     * 返回方法中的循环数量。
     *
     * @return 循环数量
     */
    public int getLoopsCount() {
        return loops.size();
    }

    /**
     * 返回方法中所有循环信息的可迭代集合。
     *
     * @return 循环信息集合
     */
    public Iterable<LoopInfo> getLoops() {
        return loops;
    }

    /**
     * 添加一个异常处理器。
     *
     * @param handler 异常处理器
     * @return 添加的异常处理器
     */
    public ExceptionHandler addExceptionHandler(ExceptionHandler handler) {
        if (exceptionHandlers.isEmpty()) {
            exceptionHandlers = new ArrayList<>(2);
        }
        exceptionHandlers.add(handler);
        return handler;
    }

    /**
     * 清除已标记为移除的异常处理器。
     *
     * @return 若有处理器被移除返回 true
     */
    public boolean clearExceptionHandlers() {
        return exceptionHandlers.removeIf(ExceptionHandler::isRemoved);
    }

    /**
     * 返回方法的异常处理器集合。
     *
     * @return 异常处理器集合
     */
    public Iterable<ExceptionHandler> getExceptionHandlers() {
        return exceptionHandlers;
    }

    /**
     * 判断方法是否没有异常处理器。
     *
     * @return 无异常处理器返回 true
     */
    public boolean isNoExceptionHandlers() {
        return exceptionHandlers.isEmpty();
    }

    /**
     * 返回异常处理器数量。
     *
     * @return 异常处理器数量
     */
    public int getExceptionHandlersCount() {
        return exceptionHandlers.size();
    }

    @Override
    public List<ArgType> getThrows() {
        MethodThrowsAttr throwsAttr = get(AType.METHOD_THROWS);
        if (throwsAttr != null) {
            return Utils.collectionMap(throwsAttr.getList(), ArgType::object);
        }
        ExceptionsAttr exceptionsAttr = get(JadxAttrType.EXCEPTIONS);
        if (exceptionsAttr != null) {
            return Utils.collectionMap(exceptionsAttr.getList(), ArgType::object);
        }
        return Collections.emptyList();
    }

    /**
     * 判断是否存在同名且参数数量相同的方法（即方法被重载）。
     *
     * @return 若存在重载方法返回 true
     */
    public boolean isArgsOverloaded() {
        MethodInfo thisMthInfo = this.mthInfo;
        // 先在当前类中快速检查
        for (MethodNode method : parentClass.getMethods()) {
            if (method == this) {
                continue;
            }
            if (method.getMethodInfo().isOverloadedBy(thisMthInfo)) {
                return true;
            }
        }
        return root().getMethodUtils().isMethodArgsOverloaded(parentClass.getClassInfo().getType(), thisMthInfo);
    }

    /**
     * 判断方法是否为构造方法。
     *
     * @return 为构造方法返回 true
     */
    public boolean isConstructor() {
        return accFlags.isConstructor() && mthInfo.isConstructor();
    }

    /**
     * 判断方法是否为默认（无参）构造方法。
     *
     * @return 为默认构造方法返回 true
     */
    public boolean isDefaultConstructor() {
        if (isConstructor()) {
            int defaultArgCount = 0;
            // 针对非静态内部类构造方法的处理：它带有一个合成参数
            if (parentClass.getClassInfo().isInner()
                    && !parentClass.getAccessFlags().isStatic()) {
                ClassNode outerCls = parentClass.getParentClass();
                if (argsList != null && !argsList.isEmpty()
                        && argsList.get(0).getInitType().equals(outerCls.getClassInfo().getType())) {
                    defaultArgCount = 1;
                }
            }
            return argsList == null || argsList.size() == defaultArgCount;
        }
        return false;
    }

    /**
     * 返回方法使用的寄存器总数。
     *
     * @return 寄存器总数
     */
    public int getRegsCount() {
        return regsCount;
    }

    /**
     * 返回参数起始寄存器索引。
     *
     * @return 参数起始寄存器索引
     */
    public int getArgsStartReg() {
        return argsStartReg;
    }

    /**
     * 创建一个新的合成（伪造）寄存器参数。
     *
     * @param type 参数类型
     * @return 新建的合成寄存器参数
     */
    public RegisterArg makeSyntheticRegArg(ArgType type) {
        RegisterArg arg = InsnArg.reg(0, type);
        arg.add(AFlag.SYNTHETIC);
        SSAVar ssaVar = makeNewSVar(arg);
        InitCodeVariables.initCodeVar(ssaVar);
        ssaVar.setType(type);
        return arg;
    }

    /**
     * 创建一个带名称的合成寄存器参数。
     *
     * @param type 参数类型
     * @param name 参数名称
     * @return 新建的合成寄存器参数
     */
    public RegisterArg makeSyntheticRegArg(ArgType type, String name) {
        RegisterArg arg = makeSyntheticRegArg(type);
        arg.setName(name);
        return arg;
    }

    /**
     * 为指定赋值寄存器参数创建新的 SSA 变量，自动分配版本号。
     *
     * @param assignArg 赋值寄存器参数
     * @return 新建的 SSA 变量
     */
    public SSAVar makeNewSVar(@NotNull RegisterArg assignArg) {
        int regNum = assignArg.getRegNum();
        return makeNewSVar(regNum, getNextSVarVersion(regNum), assignArg);
    }

    /**
     * 使用指定寄存器号和版本号创建新的 SSA 变量。
     *
     * @param regNum    寄存器号
     * @param version   版本号
     * @param assignArg 赋值寄存器参数
     * @return 新建的 SSA 变量
     */
    public SSAVar makeNewSVar(int regNum, int version, @NotNull RegisterArg assignArg) {
        SSAVar var = new SSAVar(regNum, version, assignArg);
        if (sVars.isEmpty()) {
            sVars = new ArrayList<>();
        }
        sVars.add(var);
        return var;
    }

    private int getNextSVarVersion(int regNum) {
        int v = -1;
        for (SSAVar sVar : sVars) {
            if (sVar.getRegNum() == regNum) {
                v = Math.max(v, sVar.getVersion());
            }
        }
        v++;
        return v;
    }

    /**
     * 移除指定的 SSA 变量。
     *
     * @param var 待移除的 SSA 变量
     */
    public void removeSVar(SSAVar var) {
        sVars.remove(var);
    }

    /**
     * 返回方法的 SSA 变量列表。
     *
     * @return SSA 变量列表
     */
    public List<SSAVar> getSVars() {
        return sVars;
    }

    @Override
    public int getRawAccessFlags() {
        return accFlags.rawValue();
    }

    @Override
    public AccessInfo getAccessFlags() {
        return accFlags;
    }

    @Override
    public void setAccessFlags(AccessInfo newAccessFlags) {
        this.accFlags = newAccessFlags;
    }

    /**
     * 返回方法反编译后的代码区域。
     *
     * @return 代码区域
     */
    public Region getRegion() {
        return region;
    }

    /**
     * 设置方法反编译后的代码区域。
     *
     * @param region 代码区域
     */
    public void setRegion(Region region) {
        this.region = region;
    }

    @Override
    public RootNode root() {
        return parentClass.root();
    }

    @Override
    public String typeName() {
        return "method";
    }

    @Override
    public String getInputFileName() {
        return parentClass.getInputFileName();
    }

    @Override
    public MethodInfo getMethodInfo() {
        return mthInfo;
    }

    /**
     * 返回方法字节码在文件中的偏移量。
     *
     * @return 代码偏移量，无代码时返回 0
     */
    public long getMethodCodeOffset() {
        return noCode ? 0 : codeReader.getCodeOffset();
    }

    /**
     * 返回方法的调试信息。
     *
     * @return 调试信息，无代码时返回 null
     */
    @Nullable
    public IDebugInfo getDebugInfo() {
        return noCode ? null : codeReader.getDebugInfo();
    }

    /**
     * 忽略此方法，标记为不生成代码并置为无代码。
     */
    public void ignoreMethod() {
        add(AFlag.DONT_GENERATE);
        noCode = true;
    }

    @Override
    public void rename(String newName) {
        MethodOverrideAttr overrideAttr = get(AType.METHOD_OVERRIDE);
        if (overrideAttr != null) {
            for (MethodNode relatedMth : overrideAttr.getRelatedMthNodes()) {
                relatedMth.getMethodInfo().setAlias(newName);
            }
        } else {
            mthInfo.setAlias(newName);
        }
    }

    /**
     * 计算当前阶段的指令数量。
     *
     * @return 指令数量，无法确定时返回 -1
     */
    public long countInsns() {
        if (instructions != null) {
            return instructions.length;
        }
        if (blocks != null) {
            return blocks.stream().mapToLong(block -> block.getInstructions().size()).sum();
        }
        return -1;
    }

    /**
     * 返回方法字节码中的原始指令数量。
     *
     * @return 原始指令数量
     */
    public int getInsnsCount() {
        return insnsCount;
    }

    /**
     * 返回带注释和注解的方法源码。
     *
     * @return 方法源码字符串
     */
    public String getCodeStr() {
        return CodeUtils.extractMethodCode(this, getTopParentClass().getCode());
    }

    @Override
    public boolean isVarArg() {
        return accFlags.isVarArgs();
    }

    /**
     * 判断方法反编译数据是否已加载。
     *
     * @return 已加载返回 true
     */
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * 返回方法的字节码读取器。
     *
     * @return 字节码读取器，无代码时可能为 null
     */
    public @Nullable ICodeReader getCodeReader() {
        return codeReader;
    }

    @Override
    public List<MethodNode> getUseIn() {
        return useIn;
    }

    /**
     * 设置调用此方法的方法列表（调用者），设置后请勿修改传入列表。
     *
     * @param useIn 调用者方法列表
     */
    public void setUseIn(List<MethodNode> useIn) {
        this.useIn = useIn;

        // 通知所有调用者方法：此方法（被调用者）被它们使用
        for (MethodNode methodUsedIn : useIn) {
            methodUsedIn.addUsed(this);
        }
    }

    /**
     * 添加一个被此方法调用的方法（被调用者）。
     *
     * @param used 被调用的方法
     */
    public void addUsed(MethodNode used) {
        if (used != null) {
            this.methodsUsed.add(used);
        }
    }

    /**
     * 返回此方法调用的方法集合（被调用者），返回前会剔除无效项。
     *
     * @return 被调用的方法集合
     */
    public Set<MethodNode> getUsed() {
        this.removeInvalidMethodsUsed();
        return methodsUsed;
    }

    /**
     * 设置此方法调用的方法集合（被调用者）。
     *
     * @param methodsUsed 被调用的方法列表
     */
    public void setUsed(List<MethodNode> methodsUsed) {
        this.methodsUsed = new HashSet<>(methodsUsed);
    }

    /**
     * 返回未解析的调用此方法的方法信息列表。
     *
     * @return 未解析的调用者方法信息列表
     */
    public List<MethodInfo> getUnresolvedUsed() {
        return unresolvedUsed;
    }

    /**
     * 设置未解析的调用此方法的方法信息列表。
     *
     * @param unresolvedUsed 未解析的调用者方法信息列表
     */
    public void setUnresolvedUsed(List<MethodInfo> unresolvedUsed) {
        this.unresolvedUsed = unresolvedUsed;
    }

    /**
     * 设置此方法是否包含递归自调用。
     *
     * @param callsSelf 是否递归自调用
     */
    public void setCallsSelf(boolean callsSelf) {
        this.callsSelf = callsSelf;
    }

    /**
     * 判断此方法是否包含递归自调用。
     *
     * @return 包含递归自调用返回 true
     */
    public boolean callsSelf() {
        return this.callsSelf;
    }

    // 如果此方法（调用者）已从被调用者的调用者列表中移除，
    // 则从被调用方法（被调用者）列表中移除对应项
    private void removeInvalidMethodsUsed() {
        methodsUsed.removeIf(methodUsed -> !methodUsed.getUseIn().contains(this));
    }

    /**
     * 返回关联的 Java API 方法对象。
     *
     * @return Java 方法对象
     */
    public JavaMethod getJavaNode() {
        return javaNode;
    }

    @ApiStatus.Internal
    public void setJavaNode(JavaMethod javaNode) {
        this.javaNode = javaNode;
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.METHOD;
    }

    @Override
    public int hashCode() {
        return mthInfo.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        MethodNode other = (MethodNode) obj;
        return mthInfo.equals(other.mthInfo);
    }

    @Override
    public int compareTo(@NotNull MethodNode o) {
        return mthInfo.compareTo(o.mthInfo);
    }

    @Override
    public String toAttrString() {
        return IMethodDetails.super.toAttrString() + " (m)";
    }

    @Override
    public String toString() {
        return parentClass + "." + mthInfo.getName()
                + '(' + Utils.listToString(argTypes) + "):"
                + retType;
    }
}
