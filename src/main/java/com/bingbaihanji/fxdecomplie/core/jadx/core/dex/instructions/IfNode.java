package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.InsnData;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.PrimitiveType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;

import java.util.List;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils.getBlockByOffset;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils.selectOther;

/**
 * 表示条件跳转指令节点 (if)
 * <p>
 * 继承自 {@link GotoNode}，包含一个比较操作符 ({@link IfOp})和两个参与比较的参数，
 * 并维护条件成立时跳转的 {@code thenBlock} 与条件不成立时进入的 {@code elseBlock}
 */
public class IfNode extends GotoNode {

    // 调整默认类型优先级
    private static final ArgType WIDE_TYPE = ArgType.unknown(
            PrimitiveType.INT, PrimitiveType.BOOLEAN,
            PrimitiveType.OBJECT, PrimitiveType.ARRAY,
            PrimitiveType.BYTE, PrimitiveType.SHORT, PrimitiveType.CHAR);
    private static final ArgType NUMBERS_TYPE = ArgType.unknown(
            PrimitiveType.INT, PrimitiveType.BYTE, PrimitiveType.SHORT, PrimitiveType.CHAR);
    /** 条件比较操作符 (如 EQ、NE、LT、GE 等) */
    protected IfOp op;
    /** 条件成立时跳转到的目标基本块 */
    private BlockNode thenBlock;
    /** 条件不成立时进入的基本块 */
    private BlockNode elseBlock;

    /**
     * 根据原始 DEX 指令数据构造条件跳转节点
     * 若指令只有一个寄存器，则第二个参数默认为字面量 0 (与 0 比较)
     *
     * @param insn 原始 DEX 指令数据
     * @param op   条件比较操作符
     */
    public IfNode(InsnData insn, IfOp op) {
        super(InsnType.IF, insn.getTarget(), 2);
        this.op = op;
        ArgType argType = narrowTypeByOp(op);
        addArg(InsnArg.reg(insn, 0, argType));
        if (insn.getRegsCount() == 1) {
            addArg(InsnArg.lit(0, argType));
        } else {
            addArg(InsnArg.reg(insn, 1, argType));
        }
    }

    /**
     * 根据显式参数构造条件跳转节点
     *
     * @param op           条件比较操作符
     * @param targetOffset 跳转目标偏移
     * @param arg1         第一个比较参数
     * @param arg2         第二个比较参数
     */
    public IfNode(IfOp op, int targetOffset, InsnArg arg1, InsnArg arg2) {
        this(op, targetOffset);
        addArg(arg1);
        addArg(arg2);
    }

    /**
     * 内部构造方法，仅设置操作符和跳转目标，不添加参数
     *
     * @param op           条件比较操作符
     * @param targetOffset 跳转目标偏移
     */
    private IfNode(IfOp op, int targetOffset) {
        super(InsnType.IF, targetOffset, 2);
        this.op = op;
    }

    /**
     * 根据操作符收窄参数的候选类型
     * 相等/不等比较 (EQ/NE)允许更宽泛的类型 (含布尔、对象、数组等)，
     * 其他数值比较仅允许数值类型
     *
     * @param op 条件比较操作符
     * @return 收窄后的候选类型
     */
    private static ArgType narrowTypeByOp(IfOp op) {
        if (op == IfOp.EQ || op == IfOp.NE) {
            return WIDE_TYPE;
        }
        return NUMBERS_TYPE;
    }

    /** @return 条件比较操作符 */
    public IfOp getOp() {
        return op;
    }

    /**
     * 反转条件
     * 将操作符替换为其逻辑取反形式，并交换 thenBlock 与 elseBlock
     */
    public void invertCondition() {
        op = op.invert();
        BlockNode tmp = thenBlock;
        thenBlock = elseBlock;
        elseBlock = tmp;
    }

    /**
     * 规范化条件，将 'a != false' 改写为 'a == true'
     */
    public void normalize() {
        if (getOp() == IfOp.NE && getArg(1).isFalse()) {
            changeCondition(IfOp.EQ, getArg(0), LiteralArg.litTrue());
        }
    }

    /**
     * 替换条件的操作符和两个比较参数
     *
     * @param op   新的条件比较操作符
     * @param arg1 新的第一个参数
     * @param arg2 新的第二个参数
     */
    public void changeCondition(IfOp op, InsnArg arg1, InsnArg arg2) {
        this.op = op;
        setArg(0, arg1);
        setArg(1, arg2);
    }

    /**
     * 根据当前基本块的后继块初始化 thenBlock 和 elseBlock
     * 若只有一个后继块，则 thenBlock 与 elseBlock 相同
     *
     * @param curBlock 当前所在基本块
     */
    @Override
    public void initBlocks(BlockNode curBlock) {
        List<BlockNode> successors = curBlock.getSuccessors();
        thenBlock = getBlockByOffset(target, successors);
        if (successors.size() == 1) {
            elseBlock = thenBlock;
        } else {
            elseBlock = selectOther(thenBlock, successors);
        }
    }

    /**
     * 将指向 origin 的目标块替换为 replace
     *
     * @param origin  原目标块
     * @param replace 替换后的块
     * @return true 如果发生了替换
     */
    @Override
    public boolean replaceTargetBlock(BlockNode origin, BlockNode replace) {
        boolean replaced = false;
        if (thenBlock == origin) {
            thenBlock = replace;
            replaced = true;
        }
        if (elseBlock == origin) {
            elseBlock = replace;
            replaced = true;
        }
        return replaced;
    }

    /** @return 条件成立时跳转到的目标基本块 */
    public BlockNode getThenBlock() {
        return thenBlock;
    }

    /** @return 条件不成立时进入的基本块 */
    public BlockNode getElseBlock() {
        return elseBlock;
    }

    /** @return 跳转目标偏移 (若 thenBlock 已初始化则返回其起始偏移，否则返回原始 target) */
    @Override
    public int getTarget() {
        return thenBlock == null ? target : thenBlock.getStartOffset();
    }

    /**
     * 判断两个节点是否语义相同
     * 在基类比较的基础上额外比较条件操作符
     *
     * @param obj 待比较的指令节点
     * @return true 如果两个条件跳转节点相同
     */
    @Override
    public boolean isSame(InsnNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof IfNode) || !super.isSame(obj)) {
            return false;
        }
        IfNode other = (IfNode) obj;
        return op == other.op;
    }

    /** @return 当前节点的深拷贝 (包含 then/else 块引用和通用参数属性) */
    @Override
    public InsnNode copy() {
        IfNode copy = new IfNode(op, target);
        copy.thenBlock = thenBlock;
        copy.elseBlock = elseBlock;
        return copyCommonParams(copy);
    }

    @Override
    public String toString() {
        return InsnUtils.formatOffset(offset) + ": "
                + InsnUtils.insnTypeToString(insnType)
                + getArg(0) + ' ' + op.getSymbol() + ' ' + getArg(1)
                + "  -> " + (thenBlock != null ? thenBlock : InsnUtils.formatOffset(target))
                + attributesString();
    }
}
