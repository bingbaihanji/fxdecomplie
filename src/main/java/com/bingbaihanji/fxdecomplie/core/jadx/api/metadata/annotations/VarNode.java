package com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.CodeVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import org.jetbrains.annotations.Nullable;

/**
 * 变量信息节点，表示反编译代码中的一个局部变量引用
 * 实现 {@link ICodeNodeRef} 接口，用于在代码元数据中标识变量的定义与引用位置
 */
public class VarNode implements ICodeNodeRef {

    private final MethodNode mth;
    private final int reg;
    private final int ssa;
    private final ArgType type;
    private final VarRef varRef;
    private @Nullable String name;
    private int defPos;

    /**
     * 基于 SSA 变量构造变量节点
     *
     * @param mth    所属方法节点
     * @param ssaVar SSA 变量
     */
    protected VarNode(MethodNode mth, SSAVar ssaVar) {
        this(mth, ssaVar.getRegNum(), ssaVar.getVersion(),
                ssaVar.getCodeVar().getType(), ssaVar.getCodeVar().getName());
    }

    /**
     * 构造变量节点
     *
     * @param mth  所属方法节点
     * @param reg  寄存器编号
     * @param ssa  SSA 版本号
     * @param type 变量类型
     * @param name 变量名称
     */
    public VarNode(MethodNode mth, int reg, int ssa, ArgType type, String name) {
        this.mth = mth;
        this.reg = reg;
        this.ssa = ssa;
        this.type = type;
        this.name = name;
        this.varRef = VarRef.fromVarNode(this);
    }

    /**
     * 根据方法节点与寄存器参数获取对应的变量节点
     *
     * @param mth 方法节点
     * @param reg 寄存器参数
     * @return 对应的变量节点 若该寄存器无 SSA 变量则返回 null
     */
    @Nullable
    public static VarNode get(MethodNode mth, RegisterArg reg) {
        SSAVar ssaVar = reg.getSVar();
        if (ssaVar == null) {
            return null;
        }
        return get(mth, ssaVar);
    }

    /**
     * 根据方法节点与代码变量获取对应的变量节点
     *
     * @param mth     方法节点
     * @param codeVar 代码变量
     * @return 对应的变量节点
     */
    @Nullable
    public static VarNode get(MethodNode mth, CodeVar codeVar) {
        return get(mth, codeVar.getAnySsaVar());
    }

    /**
     * 根据方法节点与 SSA 变量获取对应的变量节点
     * 若变量为 this 引用则返回 null 否则优先返回代码变量中缓存的变量节点，
     * 不存在时创建新节点并写入缓存
     *
     * @param mth    方法节点
     * @param ssaVar SSA 变量
     * @return 对应的变量节点 若为 this 引用则返回 null
     */
    @Nullable
    public static VarNode get(MethodNode mth, SSAVar ssaVar) {
        CodeVar codeVar = ssaVar.getCodeVar();
        if (codeVar.isThis()) {
            return null;
        }
        VarNode cachedVarNode = codeVar.getCachedVarNode();
        if (cachedVarNode != null) {
            return cachedVarNode;
        }
        VarNode newVarNode = new VarNode(mth, ssaVar);
        codeVar.setCachedVarNode(newVarNode);
        return newVarNode;
    }

    /**
     * 根据方法节点与寄存器参数获取变量的代码注解引用
     *
     * @param mth 方法节点
     * @param reg 寄存器参数
     * @return 变量引用注解 若无对应变量节点则返回 null
     */
    @Nullable
    public static ICodeAnnotation getRef(MethodNode mth, RegisterArg reg) {
        VarNode varNode = get(mth, reg);
        if (varNode == null) {
            return null;
        }
        return varNode.getVarRef();
    }

    /**
     * 获取变量所属的方法节点
     *
     * @return 方法节点
     */
    public MethodNode getMth() {
        return mth;
    }

    /**
     * 获取变量的寄存器编号
     *
     * @return 寄存器编号
     */
    public int getReg() {
        return reg;
    }

    /**
     * 获取变量的 SSA 版本号
     *
     * @return SSA 版本号
     */
    public int getSsa() {
        return ssa;
    }

    /**
     * 获取变量类型
     *
     * @return 变量类型
     */
    public ArgType getType() {
        return type;
    }

    /**
     * 获取变量名称
     *
     * @return 变量名称，可能为 null
     */
    @Nullable
    public String getName() {
        return name;
    }

    /**
     * 设置变量名称
     *
     * @param name 变量名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取变量引用注解
     *
     * @return 变量引用
     */
    public VarRef getVarRef() {
        return varRef;
    }

    @Override
    public int getDefPosition() {
        return defPos;
    }

    @Override
    public void setDefPosition(int pos) {
        this.defPos = pos;
    }

    @Override
    public AnnType getAnnType() {
        return AnnType.VAR;
    }

    @Override
    public int hashCode() {
        int h = 31 * getReg() + getSsa();
        return 31 * h + mth.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VarNode)) {
            return false;
        }
        VarNode other = (VarNode) o;
        return getReg() == other.getReg()
                && getSsa() == other.getSsa()
                && getMth().equals(other.getMth());
    }

    @Override
    public String toString() {
        return "VarNode{r" + reg + 'v' + ssa + '}';
    }
}
