package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 指令参数。
 * 可以是：寄存器、字面量、指令或名称
 */
public abstract class InsnArg extends Typed {

    private static final Logger LOG = LoggerFactory.getLogger(InsnArg.class);

    /** 所属的父指令；对于方法参数而言为 null */
    @Nullable("Null for method arguments")
    protected InsnNode parentInsn;

    /** 创建一个寄存器参数 */
    public static RegisterArg reg(int regNum, ArgType type) {
        return new RegisterArg(regNum, type);
    }

    /** 从指令数据中读取指定序号的寄存器并创建寄存器参数 */
    public static RegisterArg reg(InsnData insn, int argNum, ArgType type) {
        return reg(insn.getReg(argNum), type);
    }

    /** 若类型已知则创建类型不可变的寄存器参数，否则创建普通寄存器参数 */
    public static RegisterArg typeImmutableIfKnownReg(InsnData insn, int argNum, ArgType type) {
        if (type.isTypeKnown()) {
            return typeImmutableReg(insn.getReg(argNum), type);
        }
        return reg(insn.getReg(argNum), type);
    }

    /** 从指令数据中读取指定序号的寄存器并创建类型不可变的寄存器参数 */
    public static RegisterArg typeImmutableReg(InsnData insn, int argNum, ArgType type) {
        return typeImmutableReg(insn.getReg(argNum), type);
    }

    /** 创建类型不可变的寄存器参数 */
    public static RegisterArg typeImmutableReg(int regNum, ArgType type) {
        return reg(regNum, type, true);
    }

    /** 创建寄存器参数，可选是否将类型标记为不可变 */
    public static RegisterArg reg(int regNum, ArgType type, boolean typeImmutable) {
        RegisterArg reg = new RegisterArg(regNum, type);
        if (typeImmutable) {
            reg.add(AFlag.IMMUTABLE_TYPE);
        }
        return reg;
    }

    /** 创建字面量参数 */
    public static LiteralArg lit(long literal, ArgType type) {
        return LiteralArg.makeWithFixedType(literal, type);
    }

    /** 从指令数据中读取字面量并创建字面量参数 */
    public static LiteralArg lit(InsnData insn, ArgType type) {
        return lit(insn.getLiteral(), type);
    }

    /** 将指令包装为参数，并标记其为已包装 */
    private static InsnWrapArg wrap(InsnNode insn) {
        insn.add(AFlag.WRAPPED);
        return new InsnWrapArg(insn);
    }

    /** 在父指令中查找指定参数的索引，未找到返回 -1 */
    private static int getArgIndex(InsnNode parent, InsnArg arg) {
        int count = parent.getArgsCount();
        for (int i = 0; i < count; i++) {
            if (parent.getArg(i) == arg) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将指令包装为参数。
     * 对于 CONST/MOVE 指令，若能内联则直接返回其参数并标记不生成代码；
     * 若不能内联（强制内联标志），则作为包装指令处理。
     */
    @NotNull
    public static InsnArg wrapInsnIntoArg(InsnNode insn) {
        InsnType type = insn.getType();
        if (type == InsnType.CONST || type == InsnType.MOVE) {
            if (insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
                RegisterArg resArg = insn.getResult();
                InsnArg arg = wrap(insn);
                if (resArg != null) {
                    arg.setType(resArg.getType());
                }
                return arg;
            } else {
                InsnArg arg = insn.getArg(0);
                insn.add(AFlag.DONT_GENERATE);
                return arg;
            }
        }
        return wrapArg(insn);
    }

    /**
     * 将指令包装为指令参数（内部包装）。
     * <p>
     * 优先使用 {@link InsnArg#wrapInsnIntoArg(InsnNode)}。
     * 本方法不支持 MOVE 和 CONST 指令！
     */
    public static InsnArg wrapArg(InsnNode insn) {
        RegisterArg resArg = insn.getResult();
        InsnArg arg = wrap(insn);
        switch (insn.getType()) {
            case CONST:
            case MOVE:
                throw new JadxRuntimeException("Don't wrap MOVE or CONST insns: " + insn);

            case CONST_STR:
                arg.setType(ArgType.STRING);
                if (resArg != null) {
                    resArg.setType(ArgType.STRING);
                }
                break;
            case CONST_CLASS:
                arg.setType(ArgType.CLASS);
                if (resArg != null) {
                    resArg.setType(ArgType.CLASS);
                }
                break;

            default:
                if (resArg != null) {
                    arg.setType(resArg.getType());
                }
                break;
        }
        return arg;
    }

    /** 是否为寄存器参数 */
    public boolean isRegister() {
        return false;
    }

    /** 是否为字面量参数 */
    public boolean isLiteral() {
        return false;
    }

    /** 是否为包装指令参数 */
    public boolean isInsnWrap() {
        return false;
    }

    /** 是否为具名参数 */
    public boolean isNamed() {
        return false;
    }

    /** 获取所属的父指令 */
    @Nullable
    public InsnNode getParentInsn() {
        return parentInsn;
    }

    /** 设置所属的父指令 */
    public void setParentInsn(@Nullable InsnNode parentInsn) {
        this.parentInsn = parentInsn;
    }

    /** 将指令包装进当前参数（默认解除原参数绑定） */
    @Nullable("if wrap failed")
    public InsnArg wrapInstruction(MethodNode mth, InsnNode insn) {
        return wrapInstruction(mth, insn, true);
    }

    /**
     * 将指令包装进当前参数所在的位置。
     *
     * @param unbind 是否解除当前参数的使用绑定
     * @return 包装后的参数；若包装失败则返回 null
     */
    @Nullable("if wrap failed")
    public InsnArg wrapInstruction(MethodNode mth, InsnNode insn, boolean unbind) {
        InsnNode parent = parentInsn;
        if (parent == null) {
            return null;
        }
        if (parent == insn) {
            LOG.debug("Can't wrap instruction info itself: {}", insn);
            return null;
        }
        int i = getArgIndex(parent, this);
        if (i == -1) {
            return null;
        }
        if (insn.getType() == InsnType.MOVE && this.isRegister()) {
            // 为 MOVE 指令保留变量名（在 for-each 循环的迭代变量中需要）
            String name = ((RegisterArg) this).getName();
            if (name != null) {
                InsnArg arg = insn.getArg(0);
                if (arg.isRegister()) {
                    ((RegisterArg) arg).setNameIfUnknown(name);
                } else if (arg.isInsnWrap()) {
                    InsnNode wrapInsn = ((InsnWrapArg) arg).getWrapInsn();
                    RegisterArg registerArg = wrapInsn.getResult();
                    if (registerArg != null) {
                        registerArg.setNameIfUnknown(name);
                    }
                }
            }
        }
        RegisterArg resArg = insn.getResult();
        InsnArg arg = wrapInsnIntoArg(insn);
        InsnArg oldArg = parent.getArg(i);
        if (arg.getType() == ArgType.UNKNOWN) {
            // 若被包装指令缺少结果，则恢复参数类型
            arg.setType(oldArg.getType());
        }
        parent.setArg(i, arg);
        InsnRemover.unbindArgUsage(mth, oldArg);
        if (unbind) {
            InsnRemover.unbindArgUsage(mth, this);
        }
        if (resArg != null && !insn.contains(AFlag.FORCE_ASSIGN_INLINE)) {
            // 被包装指令中不再需要结果
            InsnRemover.unbindResult(mth, insn);
            insn.setResult(null);
        }
        return arg;
    }

    /** 是否为零字面量 */
    public boolean isZeroLiteral() {
        return false;
    }

    /** 是否为零常量（零字面量或包装的零 CONST 指令） */
    public boolean isZeroConst() {
        if (isZeroLiteral()) {
            return true;
        }
        if (isInsnWrap()) {
            InsnNode wrapInsn = ((InsnWrapArg) this).getWrapInsn();
            if (wrapInsn.getType() == InsnType.CONST) {
                return wrapInsn.getArg(0).isZeroLiteral();
            }
        }
        return false;
    }

    /** 是否为布尔值 false（值为 0 的布尔字面量） */
    public boolean isFalse() {
        if (isLiteral()) {
            LiteralArg litArg = (LiteralArg) this;
            return litArg.getLiteral() == 0 && Objects.equals(litArg.getType(), ArgType.BOOLEAN);
        }
        return false;
    }

    /** 是否为布尔值 true（值为 1 的布尔字面量） */
    public boolean isTrue() {
        if (isLiteral()) {
            LiteralArg litArg = (LiteralArg) this;
            return litArg.getLiteral() == 1 && Objects.equals(litArg.getType(), ArgType.BOOLEAN);
        }
        return false;
    }

    /** 是否为当前对象引用 this */
    public boolean isThis() {
        return contains(AFlag.THIS);
    }

    /**
     * 对于来自其他类的 'this' 也返回 true（常见于匿名类中）
     */
    public boolean isAnyThis() {
        if (contains(AFlag.THIS)) {
            return true;
        }
        InsnNode wrappedInsn = unwrap();
        if (wrappedInsn != null && wrappedInsn.getType() == InsnType.IGET) {
            return wrappedInsn.getArg(0).isAnyThis();
        }
        return false;
    }

    /** 若为包装指令参数则返回其内部指令，否则返回 null */
    public InsnNode unwrap() {
        if (isInsnWrap()) {
            return ((InsnWrapArg) this).getWrapInsn();
        }
        return null;
    }

    /** 是否为常量（字面量或包装的常量指令） */
    public boolean isConst() {
        return isLiteral() || (isInsnWrap() && ((InsnWrapArg) this).getWrapInsn().isConstInsn());
    }

    /** 判断两者是否为相同的常量 */
    public boolean isSameConst(InsnArg other) {
        if (isConst() && other.isConst()) {
            return this.equals(other);
        }
        return false;
    }

    /** 判断是否与指定寄存器参数指向相同的寄存器和 SSA 变量 */
    public boolean isSameVar(RegisterArg arg) {
        if (arg == null) {
            return false;
        }
        if (isRegister()) {
            return ((RegisterArg) this).sameRegAndSVar(arg);
        }
        return false;
    }

    /** 判断是否与指定 SSA 变量相同 */
    public boolean isSameVar(SSAVar ssaVar) {
        if (ssaVar == null) {
            return false;
        }
        if (isRegister()) {
            SSAVar thisSsaVar = ((RegisterArg) this).getSVar();
            return Objects.equals(thisSsaVar, ssaVar);
        }
        return false;
    }

    /** 判断是否与指定寄存器参数指向相同的代码变量（CodeVar） */
    public boolean isSameCodeVar(RegisterArg arg) {
        if (arg == null) {
            return false;
        }
        if (isRegister()) {
            return ((RegisterArg) this).sameCodeVar(arg);
        }
        return false;
    }

    /** 判断当前参数是否使用了指定的寄存器变量 */
    public boolean isUseVar(RegisterArg arg) {
        return InsnUtils.containsVar(this, arg);
    }

    /** 将公共参数（属性、父指令）复制到副本上 */
    protected final <T extends InsnArg> T copyCommonParams(T copy) {
        copy.copyAttributesFrom(this);
        copy.setParentInsn(parentInsn);
        return copy;
    }

    /** 复制当前参数，返回一个副本 */
    public abstract InsnArg duplicate();

    /** 返回简短的字符串表示 */
    public String toShortString() {
        return this.toString();
    }
}
