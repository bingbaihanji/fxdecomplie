package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 寄存器参数表示类
 * <p>
 * 继承自 {@link InsnArg}，实现 {@link Named} 接口，
 * 用于表示 Dalvik/ART 字节码中的寄存器操作数
 * 在 SSA 变换之后会关联一个 {@link SSAVar} 变量
 * </p>
 */
public class RegisterArg extends InsnArg implements Named {
    /** this 关键字的参数名称常量 */
    public static final String THIS_ARG_NAME = "this";
    /** super 关键字的参数名称常量 */
    public static final String SUPER_ARG_NAME = "super";

    /** 寄存器编号 */
    protected final int regNum;
    /** SSA 变量引用，在 SSATransform 阶段之后不为 null */
    private SSAVar sVar;

    /**
     * 构造寄存器参数
     *
     * @param rn   寄存器编号
     * @param type 初始类型，不会被修改，可以是未知类型
     */
    public RegisterArg(int rn, ArgType type) {
        this.type = type; // 初始类型，不会改变，可以是未知类型
        this.regNum = rn;
    }

    /**
     * 获取寄存器编号
     *
     * @return 寄存器编号
     */
    public int getRegNum() {
        return regNum;
    }

    /**
     * 判断是否为寄存器类型参数
     *
     * @return 始终返回 true
     */
    @Override
    public boolean isRegister() {
        return true;
    }

    /**
     * 获取初始类型 (构造时设置的类型，不会随类型推断改变)
     *
     * @return 初始类型
     */
    public ArgType getInitType() {
        return type;
    }

    /**
     * 获取当前类型如果已关联 SSA 变量，则返回 SSA 变量的推断类型 
     * 否则返回 {@link ArgType#UNKNOWN}
     *
     * @return 当前类型
     */
    @Override
    public ArgType getType() {
        if (sVar != null) {
            return sVar.getTypeInfo().getType();
        }
        return ArgType.UNKNOWN;
    }

    /**
     * 设置新的类型必须已关联 SSA 变量，否则抛出异常
     *
     * @param newType 新类型
     * @throws JadxRuntimeException 如果没有关联 SSA 变量
     */
    @Override
    public void setType(ArgType newType) {
        if (sVar == null) {
            throw new JadxRuntimeException("Can't change type for register without SSA variable: " + this);
        }
        sVar.setType(newType);
    }

    /**
     * 强制设置初始类型，不依赖 SSA 变量
     *
     * @param type 要设置的类型
     */
    public void forceSetInitType(ArgType type) {
        this.type = type;
    }

    /**
     * 获取不可变类型如果 SSA 变量存在则委托给 SSA 变量 
     * 否则检查是否设置了 {@link AFlag#IMMUTABLE_TYPE} 标志
     *
     * @return 不可变类型，如果不存在则返回 null
     */
    @Nullable
    public ArgType getImmutableType() {
        if (sVar != null) {
            return sVar.getImmutableType();
        }
        if (contains(AFlag.IMMUTABLE_TYPE)) {
            return type;
        }
        return null;
    }

    /**
     * 判断类型是否不可变
     *
     * @return 如果类型不可变返回 true
     */
    @Override
    public boolean isTypeImmutable() {
        if (sVar != null) {
            return sVar.isTypeImmutable();
        }
        return contains(AFlag.IMMUTABLE_TYPE);
    }

    /**
     * 获取关联的 SSA 变量
     *
     * @return SSA 变量，可能为 null
     */
    public SSAVar getSVar() {
        return sVar;
    }

    /**
     * 设置关联的 SSA 变量 (包级私有，仅允许内部调用)
     *
     * @param sVar SSA 变量，不能为 null
     */
    void setSVar(@NotNull SSAVar sVar) {
        this.sVar = sVar;
    }

    /**
     * 重置 SSA 变量引用为 null
     */
    public void resetSSAVar() {
        this.sVar = null;
    }

    /**
     * 获取参数名称优先返回 super/this 关键字名称，否则返回 SSA 变量的名称
     *
     * @return 参数名称，如果无法确定则返回 null
     */
    @Override
    public String getName() {
        if (isSuper()) {
            return SUPER_ARG_NAME;
        }
        if (isThis()) {
            return THIS_ARG_NAME;
        }
        if (sVar == null) {
            return null;
        }
        return sVar.getName();
    }

    /**
     * 设置参数名称如果 SSA 变量存在且名称不为 null，则委托给 SSA 变量设置
     *
     * @param name 参数名称
     */
    @Override
    public void setName(String name) {
        if (sVar != null && name != null) {
            sVar.setName(name);
        }
    }

    /**
     * 判断是否为 super 引用
     *
     * @return 如果包含 SUPER 标志返回 true
     */
    private boolean isSuper() {
        return contains(AFlag.SUPER);
    }

    /**
     * 如果当前名称未知 (为 null)，则设置指定名称
     *
     * @param name 要设置的名称
     */
    public void setNameIfUnknown(String name) {
        if (getName() == null) {
            setName(name);
        }
    }

    /**
     * 判断当前参数名称是否与另一个参数名称相等
     *
     * @param arg 要比较的参数
     * @return 如果名称相等返回 true
     */
    public boolean isNameEquals(InsnArg arg) {
        String n = getName();
        if (n == null || !(arg instanceof Named)) {
            return false;
        }
        return n.equals(((Named) arg).getName());
    }

    /**
     * 复制当前寄存器参数，保持相同的寄存器编号、初始类型和 SSA 变量
     *
     * @return 复制后的寄存器参数
     */
    @Override
    public RegisterArg duplicate() {
        return duplicate(getRegNum(), getInitType(), sVar);
    }

    /**
     * 复制当前寄存器参数，使用指定的初始类型
     *
     * @param initType 指定的初始类型
     * @return 复制后的寄存器参数
     */
    public RegisterArg duplicate(ArgType initType) {
        return duplicate(getRegNum(), initType, sVar);
    }

    /**
     * 复制当前寄存器参数并创建一个新的 SSA 变量
     *
     * @param mth 所属方法节点，用于创建新 SSA 变量
     * @return 带有新 SSA 变量的寄存器参数副本
     */
    public RegisterArg duplicateWithNewSSAVar(MethodNode mth) {
        RegisterArg duplicate = duplicate(regNum, getInitType(), null);
        mth.makeNewSVar(duplicate);
        return duplicate;
    }

    /**
     * 复制当前寄存器参数，使用指定的寄存器编号和 SSA 变量
     *
     * @param regNum 指定的寄存器编号
     * @param sVar   指定的 SSA 变量，可为 null
     * @return 复制后的寄存器参数
     */
    public RegisterArg duplicate(int regNum, @Nullable SSAVar sVar) {
        return duplicate(regNum, getInitType(), sVar);
    }

    /**
     * 使用指定的寄存器编号、初始类型和 SSA 变量创建寄存器参数副本
     *
     * @param regNum   寄存器编号
     * @param initType 初始类型
     * @param sVar     SSA 变量，可为 null 如果非 null，仅设置引用，
     *                 赋值或使用绑定将在后续阶段完成
     * @return 复制后的寄存器参数
     */
    public RegisterArg duplicate(int regNum, ArgType initType, @Nullable SSAVar sVar) {
        RegisterArg dup = new RegisterArg(regNum, initType);
        if (sVar != null) {
            // 仅在此处设置引用，赋值或使用的绑定将在后续阶段完成
            dup.setSVar(sVar);
        }
        return copyCommonParams(dup);
    }

    /**
     * 获取赋值指令节点返回对该寄存器进行赋值操作的指令
     *
     * @return 赋值指令节点，如果 SSA 变量不存在则返回 null
     */
    @Nullable
    public InsnNode getAssignInsn() {
        if (sVar == null) {
            return null;
        }
        return sVar.getAssign().getParentInsn();
    }

    /**
     * 判断两个寄存器参数的寄存器编号和初始类型是否相等
     *
     * @param arg 要比较的寄存器参数
     * @return 如果寄存器编号和类型均相等返回 true
     */
    public boolean equalRegisterAndType(RegisterArg arg) {
        return regNum == arg.regNum && type.equals(arg.type);
    }

    /**
     * 判断两个参数是否具有相同的寄存器编号和 SSA 变量
     *
     * @param arg 要比较的参数
     * @return 如果寄存器编号和 SSA 变量均相等返回 true
     */
    public boolean sameRegAndSVar(InsnArg arg) {
        if (this == arg) {
            return true;
        }
        if (!arg.isRegister()) {
            return false;
        }
        RegisterArg reg = (RegisterArg) arg;
        return regNum == reg.getRegNum()
                && Objects.equals(sVar, reg.getSVar());
    }

    /**
     * 判断两个参数是否具有相同的寄存器编号
     *
     * @param arg 要比较的参数
     * @return 如果寄存器编号相同返回 true
     */
    public boolean sameReg(InsnArg arg) {
        if (!arg.isRegister()) {
            return false;
        }
        return regNum == ((RegisterArg) arg).getRegNum();
    }

    /**
     * 判断两个参数是否具有相同的类型
     *
     * @param arg 要比较的参数
     * @return 如果类型相同返回 true
     */
    public boolean sameType(InsnArg arg) {
        return this.getType().equals(arg.getType());
    }

    /**
     * 判断两个寄存器参数是否对应同一个源码变量
     *
     * @param arg 要比较的寄存器参数
     * @return 如果属于同一个源码变量返回 true
     */
    public boolean sameCodeVar(RegisterArg arg) {
        return this.getSVar().getCodeVar() == arg.getSVar().getCodeVar();
    }

    /**
     * 判断当前 SSA 变量是否与其他 SSA 变量关联 (共享同一个源码变量)
     *
     * @return 如果关联的 SSA 变量数量大于 1 返回 true
     */
    public boolean isLinkedToOtherSsaVars() {
        return getSVar().getCodeVar().getSsaVars().size() > 1;
    }

    /**
     * 返回哈希码，基于寄存器编号
     *
     * @return 寄存器编号作为哈希码
     */
    @Override
    public int hashCode() {
        return regNum;
    }

    /**
     * 判断两个寄存器参数是否相等比较寄存器编号和 SSA 变量
     *
     * @param obj 要比较的对象
     * @return 如果相等返回 true
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegisterArg)) {
            return false;
        }
        RegisterArg other = (RegisterArg) obj;
        return regNum == other.regNum
                && Objects.equals(sVar, other.getSVar());
    }

    /**
     * 返回简短的字符串表示，格式为 "r{寄存器编号}v{SSA版本号}"
     *
     * @return 简短字符串表示
     */
    @Override
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append("r").append(regNum);
        if (sVar != null) {
            sb.append('v').append(sVar.getVersion());
        }
        return sb.toString();
    }

    /**
     * 返回完整的字符串表示，包含寄存器编号、SSA 版本、名称、类型、初始类型和属性信息
     *
     * @return 完整字符串表示
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(r").append(regNum);
        if (sVar != null) {
            sb.append('v').append(sVar.getVersion());
        }
        if (getName() != null) {
            sb.append(" '").append(getName()).append('\'');
        }
        ArgType type = sVar != null ? getType() : null;
        if (type != null) {
            sb.append(' ').append(type);
        }
        ArgType initType = getInitType();
        if (type == null || (!type.equals(initType) && !type.isTypeKnown())) {
            sb.append(" I:").append(initType);
        }
        if (!isAttrStorageEmpty()) {
            sb.append(' ').append(getAttributesString());
        }
        sb.append(')');
        return sb.toString();
    }
}
