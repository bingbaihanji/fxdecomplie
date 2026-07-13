package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IBranchRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * if 语句区域
 * <p>
 * 表示反编译过程中的 if 条件分支结构，包含条件 then 分支区域和可选的 else 分支区域
 */
public final class IfRegion extends ConditionRegion implements IBranchRegion {
    /** then 分支区域 (条件成立时执行的代码块) */
    private IContainer thenRegion;
    /** else 分支区域 (条件不成立时执行的代码块，可能为 null) */
    private IContainer elseRegion;

    public IfRegion(IRegion parent) {
        super(parent);
    }

    /** 获取 then 分支区域 */
    public IContainer getThenRegion() {
        return thenRegion;
    }

    /** 设置 then 分支区域 */
    public void setThenRegion(IContainer thenRegion) {
        this.thenRegion = thenRegion;
    }

    /** 获取 else 分支区域 */
    public IContainer getElseRegion() {
        return elseRegion;
    }

    /** 设置 else 分支区域 */
    public void setElseRegion(IContainer elseRegion) {
        this.elseRegion = elseRegion;
    }

    /**
     * 反转当前 if 区域：反转条件，并交换 then 分支与 else 分支
     */
    public void invert() {
        invertCondition();
        // 交换 then 分支与 else 分支
        IContainer tmp = thenRegion;
        thenRegion = elseRegion;
        elseRegion = tmp;
    }

    /** 获取该 if 语句对应的源码行号 */
    public int getSourceLine() {
        return getConditionSourceLine();
    }

    @Override
    public List<IContainer> getSubBlocks() {
        List<BlockNode> conditionBlocks = getConditionBlocks();
        List<IContainer> all = new ArrayList<>(conditionBlocks.size() + 2);
        all.addAll(conditionBlocks);
        if (thenRegion != null) {
            all.add(thenRegion);
        }
        if (elseRegion != null) {
            all.add(elseRegion);
        }
        return Collections.unmodifiableList(all);
    }

    @Override
    public List<IContainer> getBranches() {
        List<IContainer> branches = new ArrayList<>(2);
        branches.add(thenRegion);
        branches.add(elseRegion);
        return Collections.unmodifiableList(branches);
    }

    @Override
    public boolean replaceSubBlock(IContainer oldBlock, IContainer newBlock) {
        if (oldBlock == thenRegion) {
            thenRegion = newBlock;
            updateParent(thenRegion, this);
            return true;
        }
        if (oldBlock == elseRegion) {
            elseRegion = newBlock;
            updateParent(elseRegion, this);
            return true;
        }
        return false;
    }

    @Override
    public void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
        regionGen.makeIf(this, code, true);
    }

    @Override
    public String baseString() {
        StringBuilder sb = new StringBuilder();
        if (thenRegion != null) {
            sb.append(thenRegion.baseString());
        }
        if (elseRegion != null) {
            sb.append(elseRegion.baseString());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "IF " + getConditionBlocks() + " THEN: " + thenRegion + " ELSE: " + elseRegion;
    }
}
