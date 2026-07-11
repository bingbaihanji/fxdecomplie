package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import org.jetbrains.annotations.Nullable;

/**
 * 表示 DEX 字节码中的方法调用指令节点
 * <p>
 * 封装了被调用方法的元信息、调用类型（如虚调用、静态调用、接口调用等）
 * 以及参数绑定逻辑支持范围格式（range）和非范围格式的指令解码
 */
public class InvokeNode extends BaseInvokeNode {

    /** 调用类型（如 STATIC、VIRTUAL、DIRECT、INTERFACE 等） */
    private final InvokeType type;
    /** 被调用方法的元信息 */
    private final MethodInfo mth;

    /**
     * 根据方法信息和指令数据构造调用节点
     * 对于非静态调用，自动添加实例参数（this）作为第一个参数
     *
     * @param mthInfo    被调用方法信息
     * @param insn       原始 DEX 指令数据
     * @param invokeType 调用类型
     * @param isRange    是否为范围格式的指令
     */
    public InvokeNode(MethodInfo mthInfo, InsnData insn, InvokeType invokeType, boolean isRange) {
        this(mthInfo, insn, invokeType, invokeType != InvokeType.STATIC, isRange);
    }

    /**
     * 根据方法信息、指令数据和调用类型构造调用节点，可显式控制是否需要实例参数
     *
     * @param mth          被调用方法信息
     * @param insn         原始 DEX 指令数据
     * @param type         调用类型
     * @param instanceCall 是否需要实例参数（this），非静态调用时为 true
     * @param isRange      是否为范围格式的指令
     */
    public InvokeNode(MethodInfo mth, InsnData insn, InvokeType type, boolean instanceCall, boolean isRange) {
        super(InsnType.INVOKE, mth.getArgsCount() + (instanceCall ? 1 : 0));
        this.mth = mth;
        this.type = type;

        int k = isRange ? insn.getReg(0) : 0;
        if (instanceCall) {
            int r = isRange ? k : insn.getReg(k);
            addReg(r, mth.getDeclClass().getType());
            k++;
        }
        for (ArgType arg : mth.getArgumentsTypes()) {
            addReg(isRange ? k : insn.getReg(k), arg);
            k += arg.getRegCount();
        }
        int resReg = insn.getResultReg();
        if (resReg != -1) {
            setResult(InsnArg.reg(resReg, mth.getReturnType()));
        }
    }

    /**
     * 构造一个指定参数数量的调用节点（用于节点复制或手动构建场景）
     *
     * @param mth        被调用方法信息
     * @param invokeType 调用类型
     * @param argsCount  参数数量（含实例参数）
     */
    public InvokeNode(MethodInfo mth, InvokeType invokeType, int argsCount) {
        super(InsnType.INVOKE, argsCount);
        this.mth = mth;
        this.type = invokeType;
    }

    /** @return 调用类型 */
    public InvokeType getInvokeType() {
        return type;
    }

    /** @return 被调用方法的元信息 */
    @Override
    public MethodInfo getCallMth() {
        return mth;
    }

    /**
     * 获取实例参数（即 this 引用）
     * 仅对非静态调用有效，实例参数始终位于参数列表的第一个位置
     *
     * @return 实例参数（非静态调用时），静态调用或无参数时返回 null
     */
    @Override
    @Nullable
    public InsnArg getInstanceArg() {
        if (type != InvokeType.STATIC && getArgsCount() > 0) {
            return getArg(0);
        }
        return null;
    }

    /** @return 是否为静态方法调用 */
    @Override
    public boolean isStaticCall() {
        return type == InvokeType.STATIC;
    }

    /**
     * 判断是否为多态调用
     * <p>
     * 包括两种情形：
     * <ul>
     *   <li>显式的 POLYMORPHIC 调用类型</li>
     *   <li>对 {@code java.lang.invoke.MethodHandle.invoke} 或
     *       {@code invokeExact} 的虚调用（Java 字节码中多态签名调用的表示方式）</li>
     * </ul>
     *
     * @return true 表示是多态调用
     */
    public boolean isPolymorphicCall() {
        if (type == InvokeType.POLYMORPHIC) {
            return true;
        }
        // Java 字节码使用修改过方法信息的虚调用来表示多态调用
        if (type == InvokeType.VIRTUAL
                && "java.lang.invoke.MethodHandle".equals(mth.getDeclClass().getFullName())
                && ("invoke".equals(mth.getName()) || "invokeExact".equals(mth.getName()))) {
            return true;
        }
        return false;
    }

    /**
     * 获取第一个实际参数的偏移量
     * 静态调用第一个参数从索引 0 开始，非静态调用从索引 1 开始（索引 0 为 this）
     *
     * @return 第一个实际参数的索引偏移
     */
    @Override
    public int getFirstArgOffset() {
        return type == InvokeType.STATIC ? 0 : 1;
    }

    /** @return 当前节点的深拷贝（包含参数和属性的完整复制） */
    @Override
    public InsnNode copy() {
        return copyCommonParams(new InvokeNode(mth, type, getArgsCount()));
    }

    /**
     * 判断两个节点是否语义相同
     * 比较调用类型和被调用方法，以及基类中的通用参数属性
     *
     * @param obj 待比较的指令节点
     * @return true 如果两个节点表示相同的调用
     */
    @Override
    public boolean isSame(InsnNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof InvokeNode) || !super.isSame(obj)) {
            return false;
        }
        InvokeNode other = (InvokeNode) obj;
        return type == other.type && mth.equals(other.mth);
    }

    @Override
    public String toString() {
        return baseString() + " " + type + " call: " + mth + attributesString();
    }
}
