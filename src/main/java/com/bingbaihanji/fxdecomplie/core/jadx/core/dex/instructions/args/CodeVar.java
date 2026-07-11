package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.annotations.VarNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 代码变量
 * 表示反编译生成代码中的一个变量，可关联多个 SSA 变量，
 * 并携带名称、类型及 final/this/已声明等标记信息
 */
public class CodeVar {
    private String name;
    /** 变量类型 在类型推断之前可能为 null，仅对不可变类型提前设置 */
    private ArgType type;
    private List<SSAVar> ssaVars = Collections.emptyList();

    private boolean isFinal;
    private boolean isThis;
    private boolean isDeclared;

    /** 缓存的变量节点 在代码生成阶段设置和使用 */
    private VarNode cachedVarNode;

    /** 由方法参数寄存器创建代码变量，可选是否关联寄存器对应的 SSA 变量 */
    public static CodeVar fromMthArg(RegisterArg mthArg, boolean linkRegister) {
        CodeVar var = new CodeVar();
        var.setType(mthArg.getInitType());
        var.setName(mthArg.getName());
        var.setThis(mthArg.isThis());
        var.setDeclared(true);
        var.setThis(mthArg.isThis());
        if (linkRegister) {
            var.setSsaVars(Collections.singletonList(new SSAVar(mthArg.getRegNum(), 0, mthArg)));
        }
        return var;
    }

    /** 获取变量名 */
    public String getName() {
        return name;
    }

    /** 设置变量名 */
    public void setName(String name) {
        this.name = name;
    }

    /** 获取变量类型 */
    public ArgType getType() {
        return type;
    }

    /** 设置变量类型 */
    public void setType(ArgType type) {
        this.type = type;
    }

    /** 获取关联的所有 SSA 变量 */
    public List<SSAVar> getSsaVars() {
        return ssaVars;
    }

    /** 设置关联的 SSA 变量列表 */
    public void setSsaVars(List<SSAVar> ssaVars) {
        this.ssaVars = ssaVars;
    }

    /** 添加一个关联的 SSA 变量 (去重) */
    public void addSsaVar(SSAVar ssaVar) {
        if (ssaVars.isEmpty()) {
            ssaVars = new ArrayList<>(3);
        }
        if (!ssaVars.contains(ssaVar)) {
            ssaVars.add(ssaVar);
        }
    }

    /** 获取任意一个关联的 SSA 变量 若无则抛出异常 */
    public SSAVar getAnySsaVar() {
        if (ssaVars.isEmpty()) {
            throw new IllegalStateException("CodeVar without SSA variables attached: " + this);
        }
        return ssaVars.get(0);
    }

    /** 是否为 final 变量 */
    public boolean isFinal() {
        return isFinal;
    }

    /** 设置是否为 final 变量 */
    public void setFinal(boolean aFinal) {
        isFinal = aFinal;
    }

    /** 是否为 this 变量 */
    public boolean isThis() {
        return isThis;
    }

    /** 设置是否为 this 变量 */
    public void setThis(boolean aThis) {
        isThis = aThis;
    }

    /** 是否已声明 */
    public boolean isDeclared() {
        return isDeclared;
    }

    /** 设置是否已声明 */
    public void setDeclared(boolean declared) {
        isDeclared = declared;
    }

    /** 获取缓存的变量节点 */
    public VarNode getCachedVarNode() {
        return cachedVarNode;
    }

    /** 设置缓存的变量节点 */
    public void setCachedVarNode(VarNode varNode) {
        this.cachedVarNode = varNode;
    }

    /**
     * 以“或”运算合并另一个代码变量的标记 (已声明/this/final)
     */
    public void mergeFlagsFrom(CodeVar other) {
        if (other.isDeclared()) {
            setDeclared(true);
        }
        if (other.isThis()) {
            setThis(true);
        }
        if (other.isFinal()) {
            setFinal(true);
        }
    }

    @Override
    public String toString() {
        return (isFinal ? "final " : "") + type + ' ' + name;
    }
}
