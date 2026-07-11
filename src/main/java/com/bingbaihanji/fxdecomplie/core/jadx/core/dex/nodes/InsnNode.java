package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LineAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * DEX 指令节点，表示反编译过程中的单条指令
 * 继承自 {@link LineAttrNode}，包含指令类型、结果寄存器、参数列表和偏移量等信息
 */
public class InsnNode extends LineAttrNode {
    /** 指令类型 */
    protected final InsnType insnType;
    /** 指令的参数列表 */
    private final List<InsnArg> arguments;
    /** 指令在字节码中的偏移量，-1 表示未设置 */
    protected int offset;
    /** 结果寄存器参数（指令的赋值目标），可能为 null */
    private RegisterArg result;

    /**
     * 构造指定类型和参数数量的指令节点
     *
     * @param type      指令类型
     * @param argsCount 参数数量
     */
    public InsnNode(InsnType type, int argsCount) {
        this(type, argsCount == 0 ? Collections.emptyList() : new ArrayList<>(argsCount));
    }

    /**
     * 构造指定类型和参数列表的指令节点
     *
     * @param type 指令类型
     * @param args 参数列表
     */
    public InsnNode(InsnType type, List<InsnArg> args) {
        this.insnType = type;
        this.arguments = args;
        this.offset = -1;
        for (InsnArg arg : args) {
            attachArg(arg);
        }
    }

    /**
     * 将单个参数包装为 ONE_ARG 类型的指令节点
     *
     * @param arg 需要包装的参数
     * @return 包装后的指令节点
     */
    public static InsnNode wrapArg(InsnArg arg) {
        InsnNode insn = new InsnNode(InsnType.ONE_ARG, 1);
        insn.addArg(arg);
        return insn;
    }

    /**
     * 复制指令参数（若参数为 null 则返回 null）
     *
     * @param arg 需要复制的参数
     * @param <T> 参数类型
     * @return 复制后的参数
     */
    @SuppressWarnings("unchecked")
    public static <T extends InsnArg> @Nullable T duplicateArg(@Nullable T arg) {
        if (arg == null) {
            return null;
        }
        return (T) arg.duplicate();
    }

    /**
     * 向指令添加参数
     *
     * @param arg 要添加的参数
     */
    public void addArg(InsnArg arg) {
        arguments.add(arg);
        attachArg(arg);
    }

    /**
     * 替换指定索引位置的参数
     *
     * @param n   参数索引
     * @param arg 新的参数
     */
    public void setArg(int n, InsnArg arg) {
        arguments.set(n, arg);
        attachArg(arg);
    }

    /**
     * 将参数绑定到当前指令，并更新 SSA 变量的使用信息
     *
     * @param arg 需要绑定的参数
     */
    protected void attachArg(InsnArg arg) {
        arg.setParentInsn(this);
        if (arg.isRegister()) {
            RegisterArg reg = (RegisterArg) arg;
            SSAVar ssaVar = reg.getSVar();
            if (ssaVar != null) {
                ssaVar.use(reg);
            }
        }
    }

    /**
     * 获取指令类型
     *
     * @return 指令类型
     */
    public InsnType getType() {
        return insnType;
    }

    /**
     * 获取指令的结果寄存器参数
     *
     * @return 结果寄存器参数，可能为 null
     */
    public RegisterArg getResult() {
        return result;
    }

    /**
     * 设置指令的结果寄存器参数如果结果参数关联了 SSA 变量，则更新其赋值信息
     *
     * @param res 结果寄存器参数，可为 null
     */
    public void setResult(@Nullable RegisterArg res) {
        this.result = res;
        if (res != null) {
            res.setParentInsn(this);
            SSAVar ssaVar = res.getSVar();
            if (ssaVar != null) {
                ssaVar.setAssign(res);
            }
        }
    }

    /**
     * 获取指令参数的可迭代视图
     *
     * @return 参数的可迭代集合
     */
    public Iterable<InsnArg> getArguments() {
        return arguments;
    }

    /**
     * 获取指令参数列表
     *
     * @return 参数列表
     */
    public List<InsnArg> getArgList() {
        return arguments;
    }

    /**
     * 获取参数数量
     *
     * @return 参数数量
     */
    public int getArgsCount() {
        return arguments.size();
    }

    /**
     * 获取指定索引位置的参数
     *
     * @param n 参数索引
     * @return 指定位置的参数
     */
    public InsnArg getArg(int n) {
        return arguments.get(n);
    }

    /**
     * 检查指令是否包含指定参数（通过引用比较）
     *
     * @param arg 要检查的参数
     * @return 如果包含该参数则返回 true
     */
    public boolean containsArg(InsnArg arg) {
        if (getArgsCount() == 0) {
            return false;
        }
        for (InsnArg a : arguments) {
            if (a == arg) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查指令是否包含指定的寄存器变量
     *
     * @param arg 要检查的寄存器参数
     * @return 如果包含该变量则返回 true
     */
    public boolean containsVar(RegisterArg arg) {
        if (getArgsCount() == 0) {
            return false;
        }
        return InsnUtils.containsVar(arguments, arg);
    }

    /**
     * 使用递归搜索将指令参数替换为另一个参数
     *
     * @param from 原参数
     * @param to   新参数
     * @return 如果替换成功则返回 true
     */
    public boolean replaceArg(InsnArg from, InsnArg to) {
        int count = getArgsCount();
        for (int i = 0; i < count; i++) {
            InsnArg arg = arguments.get(i);
            if (arg == from) {
                InsnRemover.unbindArgUsage(null, arg);
                setArg(i, to);
                return true;
            }
            if (arg.isInsnWrap() && ((InsnWrapArg) arg).getWrapInsn().replaceArg(from, to)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从指令中移除指定的参数（通过引用查找）
     *
     * @param arg 要移除的参数
     * @return 如果找到并移除成功则返回 true
     */
    protected boolean removeArg(InsnArg arg) {
        int index = getArgIndex(arg);
        if (index == -1) {
            return false;
        }
        removeArg(index);
        return true;
    }

    /**
     * 从指令中移除指定索引位置的参数，并解绑其变量使用信息
     *
     * @param index 要移除的参数索引
     * @return 被移除的参数
     */
    public InsnArg removeArg(int index) {
        InsnArg arg = arguments.get(index);
        arguments.remove(index);
        InsnRemover.unbindArgUsage(null, arg);
        return arg;
    }

    /**
     * 获取指定参数在参数列表中的索引（通过引用比较）
     *
     * @param arg 要查找的参数
     * @return 参数索引，未找到则返回 -1
     */
    public int getArgIndex(InsnArg arg) {
        int count = getArgsCount();
        for (int i = 0; i < count; i++) {
            if (arg == arguments.get(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 添加一个寄存器参数（从指令数据读取指定索引的寄存器）
     *
     * @param insn 指令数据
     * @param i    寄存器索引
     * @param type 参数类型
     */
    protected void addReg(InsnData insn, int i, ArgType type) {
        addArg(InsnArg.reg(insn, i, type));
    }

    /**
     * 添加一个指定寄存器编号的寄存器参数
     *
     * @param regNum 寄存器编号
     * @param type   参数类型
     */
    protected void addReg(int regNum, ArgType type) {
        addArg(InsnArg.reg(regNum, type));
    }

    /**
     * 添加一个字面量参数
     *
     * @param literal 字面量值
     * @param type    参数类型
     */
    protected void addLit(long literal, ArgType type) {
        addArg(InsnArg.lit(literal, type));
    }

    /**
     * 添加一个从指令数据读取的字面量参数
     *
     * @param insn 指令数据
     * @param type 参数类型
     */
    protected void addLit(InsnData insn, ArgType type) {
        addArg(InsnArg.lit(insn, type));
    }

    /**
     * 获取指令偏移量
     *
     * @return 指令偏移量
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 设置指令偏移量
     *
     * @param offset 指令偏移量
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    /**
     * 递归收集指令中的所有寄存器参数（包括包装指令内部的寄存器参数）
     *
     * @param collection 用于收集结果的集合
     */
    public void getRegisterArgs(Collection<RegisterArg> collection) {
        for (InsnArg arg : this.getArguments()) {
            if (arg.isRegister()) {
                collection.add((RegisterArg) arg);
            } else if (arg.isInsnWrap()) {
                ((InsnWrapArg) arg).getWrapInsn().getRegisterArgs(collection);
            }
        }
    }

    /**
     * 判断指令是否为常量指令（CONST、CONST_STR 或 CONST_CLASS）
     *
     * @return 如果是常量指令则返回 true
     */
    public boolean isConstInsn() {
        switch (getType()) {
            case CONST:
            case CONST_STR:
            case CONST_CLASS:
                return true;

            default:
                return false;
        }
    }

    /**
     * 判断指令是否为退出边指令（RETURN、THROW、CONTINUE 或 BREAK）
     *
     * @return 如果是退出边指令则返回 true
     */
    public boolean isExitEdgeInsn() {
        switch (getType()) {
            case RETURN:
            case THROW:
            case CONTINUE:
            case BREAK:
                return true;

            default:
                return false;
        }
    }

    /**
     * 判断指令的结果是否可以被移除（仅 INVOKE 和 CONSTRUCTOR 允许）
     *
     * @return 如果结果可以被移除则返回 true
     */
    public boolean canRemoveResult() {
        switch (getType()) {
            case INVOKE:
            case CONSTRUCTOR:
                return true;

            default:
                return false;
        }
    }

    /**
     * 判断该指令是否可以安全地重新排序（不影响语义）
     * 常量、类型转换、算术等无副作用的指令可以重排 字段读取等暂不允许重排
     *
     * @return 如果指令可以重排则返回 true
     */
    public boolean canReorder() {
        if (contains(AFlag.DONT_GENERATE)) {
            if (getType() == InsnType.MONITOR_EXIT) {
                return false;
            }
            return true;
        }
        for (InsnArg arg : getArguments()) {
            if (arg.isInsnWrap()) {
                InsnNode wrapInsn = ((InsnWrapArg) arg).getWrapInsn();
                if (!wrapInsn.canReorder()) {
                    return false;
                }
            }
        }

        switch (getType()) {
            case CONST:
            case CONST_STR:
            case CONST_CLASS:
            case CAST:
            case MOVE:
            case ARITH:
            case NEG:
            case CMP_L:
            case CMP_G:
            case CHECK_CAST:
            case INSTANCE_OF:
            case FILL_ARRAY:
            case FILLED_NEW_ARRAY:
            case NEW_ARRAY:
            case STR_CONCAT:
                return true;

            case SGET:
            case IGET:
                // TODO: 允许移动 final 字段
                return false;

            default:
                return false;
        }
    }

    /**
     * 判断指令的参数中是否包含包装指令（InsnWrapArg）
     *
     * @return 如果包含包装指令则返回 true
     */
    public boolean containsWrappedInsn() {
        for (InsnArg arg : this.getArguments()) {
            if (arg.isInsnWrap()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 访问当前指令及其所有内部（被包装的）指令
     *
     * @param visitor 指令访问器
     */
    public void visitInsns(Consumer<InsnNode> visitor) {
        visitor.accept(this);
        for (InsnArg arg : this.getArguments()) {
            if (arg.isInsnWrap()) {
                ((InsnWrapArg) arg).getWrapInsn().visitInsns(visitor);
            }
        }
    }

    /**
     * 访问当前指令及其所有内部（被包装的）指令
     * 访问器返回非 null 值时终止访问并返回该值
     *
     * @param visitor 指令访问函数
     * @param <R>     返回值类型
     * @return 首个非 null 的访问结果，若全部为 null 则返回 null
     */
    @Nullable
    public <R> R visitInsns(Function<InsnNode, R> visitor) {
        R result = visitor.apply(this);
        if (result != null) {
            return result;
        }
        for (InsnArg arg : this.getArguments()) {
            if (arg.isInsnWrap()) {
                InsnNode innerInsn = ((InsnWrapArg) arg).getWrapInsn();
                R res = innerInsn.visitInsns(visitor);
                if (res != null) {
                    return res;
                }
            }
        }
        return null;
    }

    /**
     * 递归访问所有参数（包括内部指令的参数），但不访问包装参数本身
     *
     * @param visitor 参数访问器
     */
    public void visitArgs(Consumer<InsnArg> visitor) {
        for (InsnArg arg : getArguments()) {
            if (arg.isInsnWrap()) {
                ((InsnWrapArg) arg).getWrapInsn().visitArgs(visitor);
            } else {
                visitor.accept(arg);
            }
        }
    }

    /**
     * 递归访问所有参数（包括内部指令的参数），但不访问包装参数本身
     * 访问器返回非 null 值时终止访问并返回该值
     *
     * @param visitor 参数访问函数
     * @param <R>     返回值类型
     * @return 首个非 null 的访问结果，若全部为 null 则返回 null
     */
    @Nullable
    public <R> R visitArgs(Function<InsnArg, R> visitor) {
        for (InsnArg arg : getArguments()) {
            R result;
            if (arg.isInsnWrap()) {
                InsnNode wrapInsn = ((InsnWrapArg) arg).getWrapInsn();
                result = wrapInsn.visitArgs(visitor);
            } else {
                result = visitor.apply(arg);
            }
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * “软”相等比较：不比较参数，只比较指令类型和参数数量等指令自身的特征
     *
     * @param other 另一条指令
     * @return 如果两条指令“软相等”则返回 true
     */
    public boolean isSame(InsnNode other) {
        if (this == other) {
            return true;
        }
        if (insnType != other.insnType) {
            return false;
        }
        int size = arguments.size();
        if (size != other.arguments.size()) {
            return false;
        }
        // 检查被包装的指令
        for (int i = 0; i < size; i++) {
            InsnArg arg = arguments.get(i);
            InsnArg otherArg = other.arguments.get(i);
            if (arg.isInsnWrap()) {
                if (!otherArg.isInsnWrap()) {
                    return false;
                }
                InsnNode wrapInsn = ((InsnWrapArg) arg).getWrapInsn();
                InsnNode otherWrapInsn = ((InsnWrapArg) otherArg).getWrapInsn();
                if (!wrapInsn.isSame(otherWrapInsn)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * “硬”相等比较：比较所有参数（包括结果和参数列表）
     *
     * @param other 另一条指令
     * @return 如果两条指令完全相等则返回 true
     */
    public boolean isDeepEquals(InsnNode other) {
        if (this == other) {
            return true;
        }
        return isSame(other)
                && Objects.equals(result, other.result)
                && Objects.equals(arguments, other.arguments);
    }

    /**
     * 将当前指令的公共参数（参数列表、属性、源码行、偏移量）复制到目标指令
     *
     * @param copy 目标指令
     * @param <T>  指令类型
     * @return 完成复制的目标指令
     */
    protected final <T extends InsnNode> T copyCommonParams(T copy) {
        if (copy.getArgsCount() == 0) {
            for (InsnArg arg : this.getArguments()) {
                copy.addArg(arg.duplicate());
            }
        }
        copy.copyAttributesFrom(this);
        copy.copyLines(this);
        copy.setOffset(this.getOffset());
        return copy;
    }

    /**
     * 从另一条指令复制属性及源码行信息
     *
     * @param attrNode 源指令
     */
    public void copyAttributesFrom(InsnNode attrNode) {
        super.copyAttributesFrom(attrNode);
        this.addSourceLineFrom(attrNode);
    }

    /**
     * 复制 InsnNode 对象
     * <br>
     * 注意：无法复制带结果参数的指令
     * （同一个 SSA 变量不能用于两个不同的赋值）
     * <br>
     * 推荐使用以下方法：
     * <ul>
     * <li>{@link #copyWithoutResult()} 明确表示不需要结果
     * <li>{@link #copy(RegisterArg)} 提供新的结果参数
     * <li>{@link #copyWithNewSsaVar(MethodNode)} 为结果参数创建新的 SSA 变量
     * </ul>
     *
     * @return 复制得到的指令
     */
    public InsnNode copy() {
        if (this.getClass() != InsnNode.class) {
            throw new JadxRuntimeException("Copy method not implemented in insn class " + this.getClass().getSimpleName());
        }
        return copyCommonParams(new InsnNode(insnType, getArgsCount()));
    }

    /**
     * 复制指令但不带结果参数参见 {@link #copy()}
     *
     * @param <T> 指令类型
     * @return 不带结果的复制指令
     */
    @SuppressWarnings("unchecked")
    public <T extends InsnNode> T copyWithoutResult() {
        return (T) copy();
    }

    /**
     * 复制指令并保留结果参数，但要求结果参数未关联 SSA 变量
     *
     * @return 不带 SSA 的复制指令
     * @throws JadxRuntimeException 当结果参数已关联 SSA 变量时抛出
     */
    public InsnNode copyWithoutSsa() {
        InsnNode copy = copyWithoutResult();
        if (result != null) {
            if (result.getSVar() == null) {
                copy.setResult(result.duplicate());
            } else {
                throw new JadxRuntimeException("Can't copy if SSA var is set");
            }
        }
        return copy;
    }

    /**
     * 复制指令并设置新的返回（结果）参数参见 {@link #copy()}
     *
     * @param newReturnArg 新的结果参数
     * @return 复制得到的指令
     */
    public InsnNode copy(RegisterArg newReturnArg) {
        InsnNode copy = copy();
        copy.setResult(newReturnArg);
        return copy;
    }

    /**
     * 复制指令，并为结果参数创建新的 SSA 变量参见 {@link #copy()}
     *
     * @param mth 所属方法节点
     * @return 复制得到的指令
     * @throws JadxRuntimeException 当结果参数为 null 时抛出
     */
    public InsnNode copyWithNewSsaVar(MethodNode mth) {
        RegisterArg result = getResult();
        if (result == null) {
            throw new JadxRuntimeException("Result in null");
        }
        int regNum = result.getRegNum();
        RegisterArg resDupArg = result.duplicate(regNum, null);
        mth.makeNewSVar(resDupArg);
        return copy(resDupArg);
    }

    /**
     * 修正寄存器参数中的 SSA 变量信息
     * 必须在修改指令之后调用
     */
    public void rebindArgs() {
        RegisterArg resArg = getResult();
        if (resArg != null) {
            SSAVar ssaVar = resArg.getSVar();
            if (ssaVar == null) {
                throw new JadxRuntimeException("No SSA var for result arg: " + resArg + " from " + resArg.getParentInsn());
            }
            ssaVar.setAssign(resArg);
        }
        for (InsnArg arg : getArguments()) {
            if (arg instanceof RegisterArg) {
                RegisterArg reg = (RegisterArg) arg;
                SSAVar ssaVar = reg.getSVar();
                ssaVar.use(reg);
                ssaVar.updateUsedInPhiList();
            } else if (arg instanceof InsnWrapArg) {
                ((InsnWrapArg) arg).getWrapInsn().rebindArgs();
            }
        }
    }

    /**
     * 判断该指令是否可能抛出异常
     *
     * @return 如果指令可能抛出异常则返回 true
     */
    public boolean canThrowException() {
        switch (getType()) {
            case RETURN:
            case IF:
            case GOTO:
            case MOVE:
            case MOVE_EXCEPTION:
            case NEG:
            case CONST:
            case CONST_STR:
            case CONST_CLASS:
            case CMP_L:
            case CMP_G:
            case NOP:
                return false;

            default:
                return true;
        }
    }

    /**
     * 从源指令继承元数据（源码行、代码注释、偏移量等）
     * 对 RETURN 指令做特殊处理：合成指令会重写偏移量和注释
     *
     * @param sourceInsn 源指令
     */
    public void inheritMetadata(InsnNode sourceInsn) {
        if (insnType == InsnType.RETURN) {
            this.copyLines(sourceInsn);
            if (this.contains(AFlag.SYNTHETIC)) {
                this.setOffset(sourceInsn.getOffset());
                this.rewriteAttributeFrom(sourceInsn, AType.CODE_COMMENTS);
            } else {
                this.copyAttributeFrom(sourceInsn, AType.CODE_COMMENTS);
            }
        } else {
            this.copyAttributeFrom(sourceInsn, AType.CODE_COMMENTS);
            this.addSourceLineFrom(sourceInsn);
        }
    }

    /**
     * 仅按对象标识（引用）比较，计算哈希码
     */
    @SuppressWarnings("EmptyMethod")
    @Override
    public final int hashCode() {
        return super.hashCode();
    }

    /**
     * 仅按对象标识（引用）比较是否相等
     */
    @Override
    public final boolean equals(Object obj) {
        return super.equals(obj);
    }

    /**
     * 追加参数内容，若行过长则换行
     *
     * @param sb 目标字符串构建器
     * @return 如果参数发生了换行则返回 true
     */
    protected boolean appendArgs(StringBuilder sb) {
        if (arguments.isEmpty()) {
            return false;
        }
        String argsStr = Utils.listToString(arguments);
        if (argsStr.length() < 120) {
            sb.append(argsStr);
            return false;
        }
        // 换行显示参数
        String separator = "\n  ";
        sb.append(separator).append(Utils.listToString(arguments, separator));
        sb.append('\n');
        return true;
    }

    /**
     * 返回指令属性的字符串表示
     *
     * @return 属性字符串
     */
    protected String attributesString() {
        StringBuilder sb = new StringBuilder();
        appendAttributes(sb);
        return sb.toString();
    }

    /**
     * 将指令属性及源码行信息追加到字符串构建器
     *
     * @param sb 目标字符串构建器
     */
    protected void appendAttributes(StringBuilder sb) {
        if (!isAttrStorageEmpty()) {
            sb.append(' ').append(getAttributesString());
        }
        if (getSourceLine() != 0) {
            sb.append(" (LINE:").append(getSourceLine()).append(')');
        }
    }

    /**
     * 返回指令的基础字符串表示（偏移量、类型、结果和参数）
     *
     * @return 基础字符串
     */
    protected String baseString() {
        StringBuilder sb = new StringBuilder();
        if (offset != -1) {
            sb.append(InsnUtils.formatOffset(offset)).append(": ");
        }
        sb.append(insnType).append(' ');
        if (result != null) {
            sb.append(result).append(" = ");
        }
        appendArgs(sb);
        return sb.toString();
    }

    /**
     * 返回指令的完整字符串表示（基础字符串 + 属性字符串）
     *
     * @return 指令的字符串表示
     */
    @Override
    public String toString() {
        return baseString() + attributesString();
    }
}
