package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.variables;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.DeclareVariablesAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.CodeVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.SSAVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.ConstructorInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions.loops.LoopRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.AbstractRegionVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.regions.DepthRegionTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompare;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompareEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.RegionUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

/**
 * 变量处理访问者
 * <p>
 * 负责在反编译过程中处理方法中的局部变量：移除未使用的指令结果 收集并合并代码变量 
 * 校验变量类型，并为每个代码变量确定合适的声明位置
 */
public class ProcessVariables extends AbstractVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessVariables.class);

    private static void removeUnusedResults(MethodNode mth) {
        DepthRegionTraversal.traverse(mth, new AbstractRegionVisitor() {
            @Override
            public void processBlock(MethodNode mth, IBlock container) {
                for (InsnNode insn : container.getInstructions()) {
                    RegisterArg resultArg = insn.getResult();
                    if (resultArg == null) {
                        continue;
                    }
                    SSAVar ssaVar = resultArg.getSVar();
                    if (isVarUnused(mth, ssaVar)) {
                        boolean remove = false;
                        if (insn.canRemoveResult()) {
                            // 移除未使用的结果
                            remove = true;
                        } else if (canRemoveInsn(insn)) {
                            // 移除整条指令
                            insn.add(AFlag.REMOVE);
                            insn.add(AFlag.DONT_GENERATE);
                            remove = true;
                        }
                        if (remove) {
                            insn.setResult(null);
                            mth.removeSVar(ssaVar);
                            for (RegisterArg arg : ssaVar.getUseList()) {
                                arg.resetSSAVar();
                            }
                        }
                    }
                }
            }

            /**
             * 当指令结果未被使用时移除该指令
             */
            private boolean canRemoveInsn(InsnNode insn) {
                if (insn.isConstInsn()) {
                    return true;
                }
                switch (insn.getType()) {
                    case CAST:
                    case CHECK_CAST:
                        return true;
                    default:
                        return false;
                }
            }

            private boolean isVarUnused(MethodNode mth, @Nullable SSAVar ssaVar) {
                if (ssaVar == null) {
                    return true;
                }
                List<RegisterArg> useList = ssaVar.getUseList();
                if (useList.isEmpty()) {
                    return true;
                }
                if (ssaVar.isUsedInPhi()) {
                    return false;
                }
                return ListUtils.allMatch(useList, arg -> isArgUnused(mth, arg));
            }

            private boolean isArgUnused(MethodNode mth, RegisterArg arg) {
                if (arg.contains(AFlag.REMOVE)) {
                    return true;
                }
                // 检查构造函数中已被移除的参数
                InsnNode parentInsn = arg.getParentInsn();
                if (parentInsn != null
                        && parentInsn.getType() == InsnType.CONSTRUCTOR
                        && parentInsn.contains(AType.METHOD_DETAILS)) {
                    MethodNode resolveMth = mth.root().getMethodUtils().resolveMethod(((ConstructorInsn) parentInsn));
                    if (resolveMth != null && resolveMth.contains(AType.SKIP_MTH_ARGS)) {
                        int insnPos = parentInsn.getArgIndex(arg);
                        List<RegisterArg> mthArgs = resolveMth.getArgRegs();
                        if (0 <= insnPos && insnPos < mthArgs.size()) {
                            RegisterArg mthArg = mthArgs.get(insnPos);
                            if (mthArg.contains(AFlag.REMOVE) && arg.sameType(mthArg)) {
                                arg.add(AFlag.DONT_GENERATE);
                                return true;
                            }
                        }
                    }
                }
                return false;
            }
        });
    }

    private static boolean canDeclareAt(VarUsage usage, UsePlace usePlace) {
        IRegion region = usePlace.getRegion();
        // 用于处理在多个循环中使用的变量声明的变通方案
        if (region instanceof LoopRegion) {
            for (UsePlace use : usage.getAssigns()) {
                if (!RegionUtils.isRegionContainsRegion(region, use.getRegion())) {
                    return false;
                }
            }
        }
        // 不能在 else-if 链中的 'else' 与下一个 'if' 之间声明
        if (region.contains(AFlag.ELSE_IF_CHAIN)) {
            return false;
        }
        return isAllUseAfter(usePlace, usage.getAssigns())
                && isAllUseAfter(usePlace, usage.getUses());
    }

    /**
     * 检查所有 {@code usePlaces} 是否都在 {@code checkPlace} 之后
     */
    private static boolean isAllUseAfter(UsePlace checkPlace, List<UsePlace> usePlaces) {

        IRegion region = checkPlace.getRegion();
        IBlock block = checkPlace.getBlock();
        Set<UsePlace> toCheck = new ArraySet<>(usePlaces);
        boolean blockFound = false;
        for (IContainer subBlock : region.getSubBlocks()) {
            if (!blockFound && subBlock == block) {
                blockFound = true;
            }
            if (blockFound) {
                toCheck.removeIf(usePlace -> isContainerContainsUsePlace(subBlock, usePlace));
                if (toCheck.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isContainerContainsUsePlace(IContainer subBlock, UsePlace usePlace) {
        if (subBlock == usePlace.getBlock()) {
            return true;
        }
        if (subBlock instanceof IRegion) {
            // TODO: 建立索引以加快检查速度
            return RegionUtils.isRegionContainsRegion(subBlock, usePlace.getRegion());
        }
        return false;
    }

    private static boolean checkDeclareAtAssign(SSAVar var) {
        RegisterArg arg = var.getAssign();
        InsnNode parentInsn = arg.getParentInsn();
        if (parentInsn == null
                || parentInsn.contains(AFlag.WRAPPED)
                || parentInsn.getType() == InsnType.PHI) {
            return false;
        }
        if (!arg.equals(parentInsn.getResult())) {
            return false;
        }
        parentInsn.add(AFlag.DECLARE_VAR);
        var.getCodeVar().setDeclared(true);
        return true;
    }

    private static void declareVarInRegion(IContainer region, CodeVar var) {
        if (var.isDeclared()) {
            LOG.warn("Try to declare already declared variable: {}", var);
            return;
        }
        DeclareVariablesAttr dv = region.get(AType.DECLARE_VARIABLES);
        if (dv == null) {
            dv = new DeclareVariablesAttr();
            region.addAttr(dv);
        }
        dv.addVar(var);
        var.setDeclared(true);
    }

    @Override
    public void visit(MethodNode mth) throws JadxException {
        if (mth.isNoCode() || mth.getSVars().isEmpty()) {
            return;
        }
        removeUnusedResults(mth);

        List<CodeVar> codeVars = collectCodeVars(mth);
        if (codeVars.isEmpty()) {
            return;
        }
        checkCodeVars(mth, codeVars);
        // TODO: 若已应用调试信息，则按名称合并代码变量 (需要检查变量作用域)

        // 收集所有变量的使用情况
        CollectUsageRegionVisitor usageCollector = new CollectUsageRegionVisitor();
        DepthRegionTraversal.traverse(mth, usageCollector);
        Map<SSAVar, VarUsage> ssaUsageMap = usageCollector.getUsageMap();
        if (ssaUsageMap.isEmpty()) {
            return;
        }

        Map<CodeVar, List<VarUsage>> codeVarUsage = mergeUsageMaps(codeVars, ssaUsageMap);

        for (Entry<CodeVar, List<VarUsage>> entry : codeVarUsage.entrySet()) {
            declareVar(mth, entry.getKey(), entry.getValue());
        }
    }

    private void checkCodeVars(MethodNode mth, List<CodeVar> codeVars) {
        int unknownTypesCount = 0;
        for (CodeVar codeVar : codeVars) {
            ArgType codeVarType = codeVar.getType();
            if (codeVarType == null) {
                codeVar.setType(ArgType.UNKNOWN);
                unknownTypesCount++;
            } else {
                codeVar.getSsaVars()
                        .forEach(ssaVar -> {
                            ArgType ssaType = ssaVar.getImmutableType();
                            if (ssaType != null && ssaType.isTypeKnown()) {
                                TypeCompare comparator = mth.root().getTypeUpdate().getTypeCompare();
                                TypeCompareEnum result = comparator.compareTypes(ssaType, codeVarType);
                                if (result == TypeCompareEnum.CONFLICT || result.isNarrow()) {
                                    mth.addWarn("Incorrect type for immutable var: ssa=" + ssaType
                                            + ", code=" + codeVarType
                                            + ", for " + ssaVar.getDetailedVarInfo(mth));
                                }
                            }
                        });
            }
        }
        if (unknownTypesCount != 0) {
            mth.addWarn("Unknown variable types count: " + unknownTypesCount);
        }
    }

    private void declareVar(MethodNode mth, CodeVar codeVar, List<VarUsage> usageList) {
        if (codeVar.isDeclared()) {
            return;
        }

        VarUsage mergedUsage = new VarUsage(null);
        for (VarUsage varUsage : usageList) {
            mergedUsage.getAssigns().addAll(varUsage.getAssigns());
            mergedUsage.getUses().addAll(varUsage.getUses());
        }
        if (mergedUsage.getAssigns().isEmpty() && mergedUsage.getUses().isEmpty()) {
            return;
        }

        // 检查变量是否可以在其某个赋值处声明
        if (checkDeclareAtAssign(usageList, mergedUsage)) {
            return;
        }
        // TODO: 搜索最近的区域用于声明

        // 未找到合适区域，则在方法起始处声明
        declareVarInRegion(mth.getRegion(), codeVar);
    }

    private List<CodeVar> collectCodeVars(MethodNode mth) {
        Map<CodeVar, List<SSAVar>> codeVars = new LinkedHashMap<>();
        for (SSAVar ssaVar : mth.getSVars()) {
            if (ssaVar.getCodeVar().isThis()) {
                continue;
            }
            CodeVar codeVar = ssaVar.getCodeVar();
            List<SSAVar> list = codeVars.computeIfAbsent(codeVar, k -> new ArrayList<>());
            list.add(ssaVar);
        }

        for (Entry<CodeVar, List<SSAVar>> entry : codeVars.entrySet()) {
            CodeVar codeVar = entry.getKey();
            List<SSAVar> list = entry.getValue();
            for (SSAVar ssaVar : list) {
                CodeVar localCodeVar = ssaVar.getCodeVar();
                codeVar.mergeFlagsFrom(localCodeVar);
            }
            if (list.size() > 1) {
                for (SSAVar ssaVar : list) {
                    ssaVar.setCodeVar(codeVar);
                }
            }
            codeVar.setSsaVars(list);
        }
        return new ArrayList<>(codeVars.keySet());
    }

    private Map<CodeVar, List<VarUsage>> mergeUsageMaps(List<CodeVar> codeVars, Map<SSAVar, VarUsage> ssaUsageMap) {
        Map<CodeVar, List<VarUsage>> codeVarUsage = new LinkedHashMap<>(codeVars.size());
        for (CodeVar codeVar : codeVars) {
            List<VarUsage> list = new ArrayList<>();
            for (SSAVar ssaVar : codeVar.getSsaVars()) {
                VarUsage usage = ssaUsageMap.get(ssaVar);
                if (usage != null) {
                    list.add(usage);
                }
            }
            codeVarUsage.put(codeVar, Utils.lockList(list));
        }
        return codeVarUsage;
    }

    private boolean checkDeclareAtAssign(List<VarUsage> list, VarUsage mergedUsage) {
        if (mergedUsage.getAssigns().isEmpty()) {
            return false;
        }
        for (VarUsage u : list) {
            for (UsePlace assign : u.getAssigns()) {
                if (canDeclareAt(mergedUsage, assign)) {
                    return checkDeclareAtAssign(u.getVar());
                }
            }
        }
        return false;
    }
}

