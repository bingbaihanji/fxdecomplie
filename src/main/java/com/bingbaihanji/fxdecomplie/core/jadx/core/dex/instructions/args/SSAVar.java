package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.utils.CodegenEscapeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RegDebugInfoAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.PhiInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * SSA (静态单赋值)变量表示类
 * <p>
 * 每个 SSA 变量由寄存器编号 (regNum)和版本号 (version)唯一标识，
 * 记录了变量的赋值点 (assign)和所有使用点 (useList)，
 * 并维护类型推断信息 (typeInfo)和关联的代码变量 (codeVar)
 * </p>
 */
public class SSAVar implements Comparable<SSAVar> {
    private static final Logger LOG = LoggerFactory.getLogger(SSAVar.class);

    /** SSA 变量比较器：先按寄存器编号排序，编号相同则按版本号排序 */
    private static final Comparator<SSAVar> SSA_VAR_COMPARATOR =
            Comparator.comparingInt(SSAVar::getRegNum).thenComparingInt(SSAVar::getVersion);

    /** 寄存器编号 */
    private final int regNum;
    /** SSA 版本号，同一寄存器的不同版本代表不同的赋值 */
    private final int version;
    /** 使用点列表：所有引用该 SSA 变量的寄存器参数 */
    private final List<RegisterArg> useList = new ArrayList<>(2);
    /** 类型推断信息 */
    private final TypeInfo typeInfo = new TypeInfo();
    /** 赋值点：定义该 SSA 变量的寄存器参数 */
    private RegisterArg assign;
    /** 在 Phi 指令中作为输入使用的列表 */
    private List<PhiInsn> usedInPhi = null;
    /** 关联的代码变量，由 InitCodeVariables 阶段设置 */
    @Nullable("Set in InitCodeVariables pass")
    private CodeVar codeVar;

    /**
     * 构造 SSA 变量
     *
     * @param regNum 寄存器编号
     * @param v      SSA 版本号
     * @param assign 赋值点的寄存器参数
     */
    public SSAVar(int regNum, int v, @NotNull RegisterArg assign) {
        this.regNum = regNum;
        this.version = v;
        this.assign = assign;

        assign.setSVar(this);
    }

    /** 获取寄存器编号 */
    public int getRegNum() {
        return regNum;
    }

    /** 获取 SSA 版本号 */
    public int getVersion() {
        return version;
    }

    /** 获取赋值点的寄存器参数 */
    public @NotNull RegisterArg getAssign() {
        return assign;
    }

    /**
     * 设置新的赋值点如果存在旧赋值点且与新赋值点不同，
     * 会先重置旧赋值点的 SSA 变量引用
     *
     * @param assign 新的赋值点寄存器参数
     */
    public void setAssign(@NotNull RegisterArg assign) {
        RegisterArg oldAssign = this.assign;
        if (oldAssign == null) {
            this.assign = assign;
        } else if (oldAssign != assign) {
            oldAssign.resetSSAVar();
            this.assign = assign;
        }
    }

    /** 获取赋值点所在指令，如果赋值参数没有父指令则返回 null */
    public @Nullable InsnNode getAssignInsn() {
        return assign.getParentInsn();
    }

    /** 获取所有使用点的寄存器参数列表 */
    public List<RegisterArg> getUseList() {
        return useList;
    }

    /** 获取该 SSA 变量被使用的次数 */
    public int getUseCount() {
        return useList.size();
    }

    /**
     * 如果类型已标记为不可变，则返回赋值点的初始类型 否则返回 null
     */
    @Nullable
    public ArgType getImmutableType() {
        if (isTypeImmutable()) {
            return assign.getInitType();
        }
        return null;
    }

    /** 判断该 SSA 变量的类型是否为不可变 */
    public boolean isTypeImmutable() {
        return assign.contains(AFlag.IMMUTABLE_TYPE);
    }

    /**
     * 将该 SSA 变量的类型标记为不可变如果指定类型与当前初始类型不同，会强制更新类型
     *
     * @param type 要标记为不可变的类型
     */
    public void markAsImmutable(ArgType type) {
        assign.add(AFlag.IMMUTABLE_TYPE);
        ArgType initType = assign.getInitType();
        if (!initType.equals(type)) {
            assign.forceSetInitType(type);
            if (false) {
                LOG.debug("Update immutable type at var {} assign with type: {} previous type: {}", this.toShortString(), type, initType);
            }
        }
    }

    /**
     * 设置变量类型如果类型已标记为不可变且新类型不同，则抛出异常
     *
     * @param type 要设置的类型
     * @throws JadxRuntimeException 当尝试更改不可变类型时
     */
    public void setType(ArgType type) {
        ArgType imType = getImmutableType();
        if (imType != null && !imType.equals(type)) {
            throw new JadxRuntimeException("Can't change immutable type " + imType + " to " + type + " for " + this);
        }
        updateType(type);
    }

    /** 强制设置变量类型，不检查不可变标记 */
    public void forceSetType(ArgType type) {
        updateType(type);
    }

    /** 更新类型推断信息，并同步更新关联的代码变量类型 */
    private void updateType(ArgType type) {
        typeInfo.setType(type);
        if (codeVar != null) {
            codeVar.setType(type);
        }
    }

    /**
     * 注册一个使用点如果该参数之前关联了其他 SSA 变量，会先从旧变量的使用列表中移除
     *
     * @param arg 引用该 SSA 变量的寄存器参数
     */
    public void use(RegisterArg arg) {
        if (arg.getSVar() != null) {
            arg.getSVar().removeUse(arg);
        }
        arg.setSVar(this);
        useList.add(arg);
    }

    /**
     * 移除指定的使用点
     *
     * @param arg 要移除的寄存器参数
     */
    public void removeUse(RegisterArg arg) {
        useList.removeIf(registerArg -> registerArg == arg);
    }

    /**
     * 记录该 SSA 变量在某个 Phi 指令中被使用
     *
     * @param phiInsn 使用该变量的 Phi 指令
     */
    public void addUsedInPhi(PhiInsn phiInsn) {
        if (usedInPhi == null) {
            usedInPhi = new ArrayList<>(1);
        }
        usedInPhi.add(phiInsn);
    }

    /**
     * 移除某个 Phi 指令的使用记录，列表清空后置为 null
     *
     * @param phiInsn 要移除的 Phi 指令
     */
    public void removeUsedInPhi(PhiInsn phiInsn) {
        if (usedInPhi != null) {
            usedInPhi.removeIf(insn -> insn == phiInsn);
            if (usedInPhi.isEmpty()) {
                usedInPhi = null;
            }
        }
    }

    /** 根据当前使用列表重新计算该 SSA 变量在哪些 Phi 指令中被使用 */
    public void updateUsedInPhiList() {
        this.usedInPhi = null;
        for (RegisterArg reg : useList) {
            InsnNode parentInsn = reg.getParentInsn();
            if (parentInsn != null && parentInsn.getType() == InsnType.PHI) {
                addUsedInPhi((PhiInsn) parentInsn);
            }
        }
    }

    /** 当该变量仅在唯一一个 Phi 指令中被使用时返回该指令，否则返回 null */
    @Nullable
    public PhiInsn getOnlyOneUseInPhi() {
        if (usedInPhi != null && usedInPhi.size() == 1) {
            return usedInPhi.get(0);
        }
        return null;
    }

    /** 获取该变量作为输入使用的所有 Phi 指令列表 */
    public List<PhiInsn> getUsedInPhi() {
        if (usedInPhi == null) {
            return Collections.emptyList();
        }
        return usedInPhi;
    }

    /**
     * 合并赋值点的 Phi 指令与 usedInPhi 列表，返回与该变量相关的所有 Phi 指令
     */
    public List<PhiInsn> getPhiList() {
        InsnNode assignInsn = getAssign().getParentInsn();
        if (assignInsn != null && assignInsn.getType() == InsnType.PHI) {
            PhiInsn assignPhi = (PhiInsn) assignInsn;
            if (usedInPhi == null) {
                return Collections.singletonList(assignPhi);
            }
            List<PhiInsn> list = new ArrayList<>(1 + usedInPhi.size());
            list.add(assignPhi);
            list.addAll(usedInPhi);
            return list;
        }
        if (usedInPhi == null) {
            return Collections.emptyList();
        }
        return usedInPhi;
    }

    /** 判断该变量的赋值点是否位于 Phi 指令中 */
    public boolean isAssignInPhi() {
        InsnNode assignInsn = getAssignInsn();
        return assignInsn != null && assignInsn.getType() == InsnType.PHI;
    }

    /** 判断该变量是否在任意 Phi 指令中被使用 */
    public boolean isUsedInPhi() {
        return usedInPhi != null && !usedInPhi.isEmpty();
    }

    /** 获取变量名称，代码变量未设置时返回 null */
    public String getName() {
        if (codeVar == null) {
            return null;
        }
        return codeVar.getName();
    }

    /**
     * 设置变量名称 (委托给关联的代码变量)
     *
     * @param name 变量名称
     * @throws JadxRuntimeException 当代码变量尚未初始化时
     */
    public void setName(String name) {
        if (name != null) {
            if (codeVar == null) {
                throw new JadxRuntimeException("CodeVar not initialized for name set in SSAVar: " + this);
            }
            codeVar.setName(name);
        }
    }

    /** 获取类型推断信息对象 */
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    /**
     * 获取关联的代码变量
     *
     * @throws JadxRuntimeException 当代码变量尚未设置时
     */
    @NotNull
    public CodeVar getCodeVar() {
        if (codeVar == null) {
            throw new JadxRuntimeException("Code variable not set in " + this);
        }
        return codeVar;
    }

    /**
     * 设置关联的代码变量，并将自身注册到该代码变量中 
     * 若类型为不可变则同步设置代码变量类型
     *
     * @param codeVar 要关联的代码变量
     */
    public void setCodeVar(@NotNull CodeVar codeVar) {
        this.codeVar = codeVar;
        codeVar.addSsaVar(this);
        ArgType imType = getImmutableType();
        if (imType != null) {
            codeVar.setType(imType);
        }
    }

    /** 重置类型和代码变量：非不可变类型重置为 UNKNOWN，清空类型边界并解除代码变量关联 */
    public void resetTypeAndCodeVar() {
        if (!isTypeImmutable()) {
            updateType(ArgType.UNKNOWN);
        }
        this.typeInfo.getBounds().clear();
        this.codeVar = null;
    }

    /** 判断是否已设置代码变量 */
    public boolean isCodeVarSet() {
        return codeVar != null;
    }

    /**
     * 收集该 SSA 变量的详细信息，包含寄存器/版本号、调试名称集合与类型集合，用于调试输出
     *
     * @param mth 该变量所属的方法节点
     * @return 变量详细信息字符串
     */
    public String getDetailedVarInfo(MethodNode mth) {
        Set<ArgType> types = new HashSet<>();
        Set<String> names = Collections.emptySet();

        List<RegisterArg> useArgs = new ArrayList<>(1 + useList.size());
        useArgs.add(assign);
        useArgs.addAll(useList);

        if (mth.contains(AType.LOCAL_VARS_DEBUG_INFO)) {
            names = new HashSet<>();
            for (RegisterArg arg : useArgs) {
                RegDebugInfoAttr debugInfoAttr = arg.get(AType.REG_DEBUG_INFO);
                if (debugInfoAttr != null) {
                    names.add(debugInfoAttr.getName());
                    types.add(debugInfoAttr.getRegType());
                }
            }
        }

        for (RegisterArg arg : useArgs) {
            ArgType initType = arg.getInitType();
            if (initType.isTypeKnown()) {
                types.add(initType);
            }
            ArgType type = arg.getType();
            if (type.isTypeKnown()) {
                types.add(type);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append('r').append(regNum).append('v').append(version);
        if (!names.isEmpty()) {
            String orderedNames = names.stream()
                    .sorted()
                    .collect(Collectors.joining(", ", "[", "]"));

            sb.append(", names: ").append(orderedNames);
        }
        if (!types.isEmpty()) {
            String orderedTypes = types.stream()
                    .map(String::valueOf)
                    .sorted()
                    .collect(Collectors.joining(", ", "[", "]"));

            sb.append(", types: ").append(orderedTypes);
        }
        return sb.toString();
    }

    /** 基于寄存器编号与版本号判断两个 SSA 变量是否相等 */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SSAVar)) {
            return false;
        }
        SSAVar ssaVar = (SSAVar) o;
        return regNum == ssaVar.regNum && version == ssaVar.version;
    }

    /** 基于寄存器编号与版本号计算哈希值 */
    @Override
    public int hashCode() {
        return 31 * regNum + version;
    }

    /** 按寄存器编号、版本号顺序比较两个 SSA 变量 */
    @Override
    public int compareTo(@NotNull SSAVar o) {
        return SSA_VAR_COMPARATOR.compare(this, o);
    }

    /** 返回该变量的简短字符串表示，格式为 r{寄存器编号}v{版本号} */
    public String toShortString() {
        return "r" + regNum + 'v' + version;
    }

    @Override
    public String toString() {
        return toShortString()
                + (CodegenEscapeUtils.notEmpty(getName()) ? " '" + getName() + "' " : "")
                + ' ' + typeInfo.getType();
    }
}
