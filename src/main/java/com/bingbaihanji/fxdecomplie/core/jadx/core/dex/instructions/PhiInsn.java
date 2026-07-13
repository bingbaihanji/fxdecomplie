package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IBlock;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnRemover;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示 SSA (静态单赋值)形式中的 Phi 函数指令节点
 * <p>
 * Phi 指令用于在控制流汇合点合并来自不同前驱基本块的多个变量定义，
 * 每个参数都与其来源的前驱块一一绑定该节点被标记为不可内联 不生成源码
 * <p>
 * 注意：不允许通过 {@link #addArg} / {@link #setArg} 直接操作参数，
 * 必须使用 {@link #bindArg} 系列方法以保证参数与前驱块的绑定关系一致
 */
public final class PhiInsn extends InsnNode {

    // 参数到前驱块的映射 (顺序与参数列表一致)
    private final List<BlockNode> blockBinds;

    /**
     * 构造 Phi 指令并设置结果寄存器
     *
     * @param regNum       结果寄存器编号
     * @param predecessors 前驱块数量 (用于预分配参数容量)
     */
    public PhiInsn(int regNum, int predecessors) {
        this(predecessors);
        setResult(InsnArg.reg(regNum, ArgType.UNKNOWN));
        add(AFlag.DONT_INLINE);
        add(AFlag.DONT_GENERATE);
    }

    /**
     * 内部构造方法，初始化指令类型和前驱块绑定列表
     *
     * @param argsCount 参数数量 (预分配容量)
     */
    private PhiInsn(int argsCount) {
        super(InsnType.PHI, argsCount);
        this.blockBinds = new ArrayList<>(argsCount);
    }

    /**
     * 为指定前驱块创建并绑定一个与结果寄存器同型的参数
     *
     * @param pred 前驱基本块
     * @return 新创建并已绑定的寄存器参数
     */
    public RegisterArg bindArg(BlockNode pred) {
        RegisterArg arg = InsnArg.reg(getResult().getRegNum(), getResult().getInitType());
        bindArg(arg, pred);
        return arg;
    }

    /**
     * 将给定参数绑定到指定前驱块
     *
     * @param arg  待绑定的寄存器参数
     * @param pred 前驱基本块
     * @throws JadxRuntimeException 当前驱块重复或为 null 时抛出
     */
    public void bindArg(RegisterArg arg, BlockNode pred) {
        if (blockBinds.contains(pred)) {
            throw new JadxRuntimeException("Duplicate predecessors in PHI insn: " + pred + ", " + this);
        }
        if (pred == null) {
            throw new JadxRuntimeException("Null bind block in PHI insn: " + this);
        }
        super.addArg(arg);
        blockBinds.add(pred);
    }

    /**
     * 根据参数查找其绑定的前驱块
     *
     * @param arg 寄存器参数
     * @return 绑定的前驱块，未找到时返回 null
     */
    @Nullable
    public BlockNode getBlockByArg(RegisterArg arg) {
        int index = getArgIndex(arg);
        if (index == -1) {
            return null;
        }
        return blockBinds.get(index);
    }

    /**
     * 根据参数索引获取对应的前驱块
     *
     * @param argIndex 参数索引
     * @return 对应的前驱块
     */
    public BlockNode getBlockByArgIndex(int argIndex) {
        return blockBinds.get(argIndex);
    }

    /**
     * 获取第 n 个参数 (Phi 指令的参数均为寄存器参数)
     *
     * @param n 参数索引
     * @return 第 n 个寄存器参数
     */
    @Override
    @NotNull
    public RegisterArg getArg(int n) {
        return (RegisterArg) super.getArg(n);
    }

    /**
     * 根据前驱块查找其绑定的参数
     *
     * @param block 前驱基本块
     * @return 绑定的寄存器参数，未找到时返回 null
     */
    public @Nullable RegisterArg getArgByBlock(BlockNode block) {
        for (int i = 0; i < blockBinds.size(); i++) {
            if (blockBinds.get(i) == block) {
                return getArg(i);
            }
        }
        return null;
    }

    /**
     * 移除指定参数，同时移除其绑定的前驱块
     *
     * @param arg 待移除的参数
     * @return true 如果成功移除
     */
    @Override
    public boolean removeArg(InsnArg arg) {
        int index = getArgIndex(arg);
        if (index == -1) {
            return false;
        }
        removeArg(index);
        return true;
    }

    /**
     * 移除指定索引处的参数，同步移除其前驱块绑定，并刷新 SSA 变量的 Phi 使用列表
     *
     * @param index 参数索引
     * @return 被移除的寄存器参数
     */
    @Override
    public RegisterArg removeArg(int index) {
        RegisterArg reg = (RegisterArg) super.removeArg(index);
        blockBinds.remove(index);
        reg.getSVar().updateUsedInPhiList();
        return reg;
    }

    /**
     * 根据 SSA 变量查找对应的参数
     *
     * @param ssaVar SSA 变量
     * @return 使用该 SSA 变量的寄存器参数，未找到时返回 null
     */
    @Nullable
    public RegisterArg getArgBySsaVar(SSAVar ssaVar) {
        if (getArgsCount() == 0) {
            return null;
        }
        for (InsnArg insnArg : getArguments()) {
            RegisterArg reg = (RegisterArg) insnArg;
            if (reg.getSVar() == ssaVar) {
                return reg;
            }
        }
        return null;
    }

    /**
     * 根据块 (IBlock)查找绑定的参数
     *
     * @param block 基本块
     * @return 绑定的寄存器参数，未找到时返回 null
     */
    @Nullable
    public RegisterArg getArgByBlock(IBlock block) {
        if (getArgsCount() == 0) {
            return null;
        }
        int index = blockBinds.indexOf(block);
        if (index == -1) {
            return null;
        }
        return getArg(index);
    }

    /**
     * 将参数 from 替换为 to，并同步维护相关 SSA 变量的 Phi 使用关系
     * 仅支持寄存器参数之间的替换
     *
     * @param from 原参数
     * @param to   替换后的参数
     * @return true 如果替换成功
     */
    @Override
    public boolean replaceArg(InsnArg from, InsnArg to) {
        if (!(from instanceof RegisterArg) || !(to instanceof RegisterArg)) {
            return false;
        }

        int argIndex = getArgIndex(from);
        if (argIndex == -1) {
            return false;
        }
        ((RegisterArg) to).getSVar().addUsedInPhi(this);
        super.setArg(argIndex, to);

        InsnRemover.unbindArgUsage(null, from);
        ((RegisterArg) from).getSVar().updateUsedInPhiList();
        return true;
    }

    /**
     * 禁止直接添加参数，必须使用 {@link #bindArg} 以维护参数与前驱块的绑定关系
     *
     * @throws JadxRuntimeException 始终抛出
     */
    @Override
    public void addArg(InsnArg arg) {
        throw new JadxRuntimeException("Direct addArg is forbidden for PHI insn, bindArg must be used");
    }

    /**
     * 禁止直接设置参数，必须使用 {@link #bindArg} 以维护参数与前驱块的绑定关系
     *
     * @throws JadxRuntimeException 始终抛出
     */
    @Override
    public void setArg(int n, InsnArg arg) {
        throw new JadxRuntimeException("Direct setArg is forbidden for PHI insn, bindArg must be used");
    }

    /** @return 当前 Phi 节点的拷贝 (复制通用参数属性) */
    @Override
    public InsnNode copy() {
        return copyCommonParams(new PhiInsn(getArgsCount()));
    }

    @Override
    public String toString() {
        return baseString() + " binds: " + blockBinds + attributesString();
    }
}
