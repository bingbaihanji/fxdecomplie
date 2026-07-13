package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.LoopInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.BlockNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions.ConditionRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.conditions.IfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.BlockUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 循环区域
 * <p>
 * 表示反编译过程中的循环结构 (如 for/while/do-while)，包含循环信息 条件头块 
 * 循环体以及可选的前置条件块当 {@code header} 为 null 时表示无限循环
 */
public final class LoopRegion extends ConditionRegion {

    /** 循环信息 (循环的标识及相关元数据) */
    private final LoopInfo info;
    /** 条件是否位于循环末尾 (对应 do-while 形式的循环) */
    private final boolean conditionAtEnd;
    /** 循环条件头块 (无条件的无限循环时为 null) */
    private final @Nullable BlockNode header;
    // 每次循环时必须在条件之前执行的指令
    private @Nullable BlockNode preCondition;

    /** 循环体区域 */
    private IRegion body;
    /** 循环类型 (for/while/do-while 等) */
    private LoopType type;

    public LoopRegion(IRegion parent, LoopInfo info, @Nullable BlockNode header, boolean reversed) {
        super(parent);
        this.info = info;
        this.header = header;
        this.conditionAtEnd = reversed;
        if (header != null) {
            updateCondition(header);
        }
    }

    /** 获取循环信息 */
    public LoopInfo getInfo() {
        return info;
    }

    /** 获取循环条件头块 (无限循环时返回 null) */
    @Nullable
    public BlockNode getHeader() {
        return header;
    }

    /** 判断是否为无限循环 (无条件头块) */
    public boolean isEndless() {
        return header == null;
    }

    /** 获取循环体区域 */
    public IRegion getBody() {
        return body;
    }

    /** 设置循环体区域 */
    public void setBody(IRegion body) {
        this.body = body;
    }

    /** 判断条件是否位于循环末尾 (do-while 形式) */
    public boolean isConditionAtEnd() {
        return conditionAtEnd;
    }

    /**
     * 设置每次循环时必须在条件之前执行的指令
     */
    public void setPreCondition(BlockNode preCondition) {
        this.preCondition = preCondition;
    }

    /**
     * 检查前置条件指令是否可以内联到循环条件中
     */
    public boolean checkPreCondition() {
        List<InsnNode> insns = preCondition.getInstructions();
        if (insns.isEmpty()) {
            return true;
        }
        IfCondition condition = getCondition();
        if (condition == null) {
            return false;
        }
        List<RegisterArg> conditionArgs = condition.getRegisterArgs();
        if (conditionArgs.isEmpty()) {
            return false;
        }
        int size = insns.size();
        for (int i = 0; i < size; i++) {
            InsnNode insn = insns.get(i);
            if (insn.getResult() == null) {
                return false;
            }
            RegisterArg res = insn.getResult();
            if (res.getSVar().getUseCount() > 1) {
                return false;
            }
            boolean found = false;
            // 在其他指令中查找该结果参数
            for (int j = i + 1; j < size; j++) {
                if (insns.get(i).containsVar(res)) {
                    found = true;
                }
            }
            // 或在 if 指令中查找
            if (!found && InsnUtils.containsVar(conditionArgs, res)) {
                found = true;
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * 将所有前置条件块指令移动到条件块指令之前
     */
    public void mergePreCondition() {
        if (preCondition != null && header != null) {
            List<InsnNode> condInsns = header.getInstructions();
            List<InsnNode> preCondInsns = preCondition.getInstructions();
            preCondInsns.addAll(condInsns);
            condInsns.clear();
            condInsns.addAll(preCondInsns);
            header.add(AFlag.ALLOW_MULTIPLE_INSNS_LOOP_COND);
            preCondInsns.clear();
            preCondition = null;
        }
    }

    /** 获取循环对应的源码行号 */
    public int getSourceLine() {
        InsnNode lastInsn = BlockUtils.getLastInsn(header);
        int headerLine = lastInsn == null ? 0 : lastInsn.getSourceLine();
        if (headerLine != 0) {
            return headerLine;
        }
        return getConditionSourceLine();
    }

    /** 获取循环类型 */
    public LoopType getType() {
        return type;
    }

    /** 设置循环类型 */
    public void setType(LoopType type) {
        this.type = type;
    }

    @Override
    public List<IContainer> getSubBlocks() {
        List<IContainer> all = new ArrayList<>(2 + getConditionBlocks().size());
        if (preCondition != null) {
            all.add(preCondition);
        }
        all.addAll(getConditionBlocks());
        if (body != null) {
            all.add(body);
        }
        return all;
    }

    @Override
    public boolean replaceSubBlock(IContainer oldBlock, IContainer newBlock) {
        return false;
    }

    @Override
    public void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
        regionGen.makeLoop(this, code);
    }

    @Override
    public String baseString() {
        return body == null ? "-" : body.baseString();
    }

    @Override
    public String toString() {
        return "LOOP:" + info.getId() + ": " + baseString();
    }
}
