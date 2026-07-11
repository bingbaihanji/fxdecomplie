package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.AnonymousClassAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.mods.ConstructorInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.MethodUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.trycatch.ExcHandlerAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AbstractVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.AttachMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ConstInlineVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.JadxVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.ssa.SSATransform;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 类型推断访问器。
 * <p>
 * 为 SSA 变量计算最佳类型。该访问器在 SSA 变换、常量内联和方法详情附加之后运行，
 * 通过收集类型边界信息并进行类型传播来推断每个变量的最优类型。
 * </p>
 */
@JadxVisitor(
        name = "Type Inference",
        desc = "Calculate best types for SSA variables",
        runAfter = {
                SSATransform.class,
                ConstInlineVisitor.class,
                AttachMethodDetails.class
        }
)
public final class TypeInferenceVisitor extends AbstractVisitor {
    private static final Logger LOG = LoggerFactory.getLogger(TypeInferenceVisitor.class);

    private RootNode root;
    private TypeUpdate typeUpdate;

    @Nullable
    private static ArgType getSsaImmutableType(SSAVar ssaVar) {
        if (ssaVar.getAssign().contains(AFlag.IMMUTABLE_TYPE)) {
            return ssaVar.getAssign().getInitType();
        }
        for (RegisterArg reg : ssaVar.getUseList()) {
            if (reg.contains(AFlag.IMMUTABLE_TYPE)) {
                return reg.getInitType();
            }
        }
        return null;
    }

    /**
     * 初始化类型推断访问器，设置根节点和类型更新器
     */
    @Override
    public void init(RootNode root) {
        this.root = root;
        this.typeUpdate = root.getTypeUpdate();
    }

    /**
     * 对单个方法执行类型推断：先赋予不可变类型，再收集类型边界，最后进行类型传播。
     * 无代码的方法直接跳过；推断过程中的栈溢出或异常会作为错误附加到方法上，不会中断整体流程。
     */
    @Override
    public void visit(MethodNode mth) {
        if (mth.isNoCode()) {
            return;
        }
        if (false) {
            LOG.info("Start type inference in method: {}", mth);
        }
        try {
            assignImmutableTypes(mth);
            initTypeBounds(mth);
            runTypePropagation(mth);
        } catch (StackOverflowError | BootstrapMethodError e) {
            mth.addError("Type inference failed with stack overflow", new JadxOverflowException(e.getMessage()));
        } catch (Exception e) {
            mth.addError("Type inference failed", e);
        }
    }

    /**
     * 收集初始类型边界信息，包括赋值边界和使用边界
     */
    void initTypeBounds(MethodNode mth) {
        List<SSAVar> ssaVars = mth.getSVars();
        ssaVars.forEach(this::attachBounds);
        ssaVars.forEach(this::mergePhiBounds);
        if (false) {
            ssaVars.stream().sorted()
                    .forEach(ssaVar -> LOG.debug("Type bounds for {}: {}", ssaVar.toShortString(), ssaVar.getTypeInfo().getBounds()));
        }
    }

    /**
     * 执行类型传播：根据使用情况推测类型，并尝试将其设置到当前变量
     * 以及所有通过 {@link TypeUpdate#apply(MethodNode, SSAVar, ArgType)} 关联的指令
     */
    boolean runTypePropagation(MethodNode mth) {
        List<SSAVar> ssaVars = mth.getSVars();
        ssaVars.forEach(var -> setImmutableType(mth, var));
        ssaVars.forEach(var -> setBestType(mth, var));
        return true;
    }

    private void setImmutableType(MethodNode mth, SSAVar ssaVar) {
        try {
            ArgType immutableType = ssaVar.getImmutableType();
            if (immutableType != null) {
                TypeUpdateResult result = typeUpdate.applyWithWiderIgnSame(mth, ssaVar, immutableType);
                if (false && result == TypeUpdateResult.REJECT) {
                    LOG.info("Reject initial immutable type {} for {}", immutableType, ssaVar);
                }
            }
        } catch (JadxOverflowException e) {
            throw e;
        } catch (Exception e) {
            mth.addWarnComment("Failed to set immutable type for var: " + ssaVar, e);
        }
    }

    private void setBestType(MethodNode mth, SSAVar ssaVar) {
        try {
            calculateFromBounds(mth, ssaVar);
        } catch (JadxOverflowException e) {
            throw e;
        } catch (Exception e) {
            mth.addWarnComment("Failed to calculate best type for var: " + ssaVar, e);
        }
    }

    private void calculateFromBounds(MethodNode mth, SSAVar ssaVar) {
        TypeInfo typeInfo = ssaVar.getTypeInfo();
        Set<ITypeBound> bounds = typeInfo.getBounds();
        Optional<ArgType> bestTypeOpt = selectBestTypeFromBounds(bounds);
        if (bestTypeOpt.isEmpty()) {
            if (false) {
                LOG.warn("Failed to select best type from bounds, count={} : ", bounds.size());
                for (ITypeBound bound : bounds) {
                    LOG.warn("  {}", bound);
                }
            }
            return;
        }
        ArgType candidateType = bestTypeOpt.get();
        TypeUpdateResult result = typeUpdate.apply(mth, ssaVar, candidateType);
        if (false && result == TypeUpdateResult.REJECT) {
            if (ssaVar.getTypeInfo().getType().equals(candidateType)) {
                LOG.info("Same type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds);
            } else if (candidateType.isTypeKnown()) {
                LOG.debug("Type rejected: {} -> {}, bounds: {}", ssaVar, candidateType, bounds);
            }
        }
    }

    private Optional<ArgType> selectBestTypeFromBounds(Set<ITypeBound> bounds) {
        return bounds.stream()
                .map(ITypeBound::getType)
                .filter(Objects::nonNull)
                .max(typeUpdate.getTypeCompare().getComparator());
    }

    private void attachBounds(SSAVar var) {
        TypeInfo typeInfo = var.getTypeInfo();
        typeInfo.getBounds().clear();
        RegisterArg assign = var.getAssign();
        addAssignBound(typeInfo, assign);

        for (RegisterArg regArg : var.getUseList()) {
            addBound(typeInfo, makeUseBound(regArg));
        }
    }

    private void mergePhiBounds(SSAVar ssaVar) {
        for (PhiInsn usedInPhi : ssaVar.getUsedInPhi()) {
            Set<ITypeBound> bounds = ssaVar.getTypeInfo().getBounds();
            bounds.addAll(usedInPhi.getResult().getSVar().getTypeInfo().getBounds());
            for (InsnArg arg : usedInPhi.getArguments()) {
                bounds.addAll(((RegisterArg) arg).getSVar().getTypeInfo().getBounds());
            }
        }
    }

    private void addBound(TypeInfo typeInfo, ITypeBound bound) {
        if (bound == null) {
            return;
        }
        if (bound instanceof ITypeBoundDynamic
                || bound.getType() != ArgType.UNKNOWN) {
            typeInfo.getBounds().add(bound);
        }
    }

    private void addAssignBound(TypeInfo typeInfo, RegisterArg assign) {
        ArgType immutableType = assign.getImmutableType();
        if (immutableType != null) {
            addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, immutableType));
            return;
        }
        InsnNode insn = assign.getParentInsn();
        if (insn == null || insn.getResult() == null) {
            addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, assign.getInitType()));
            return;
        }
        switch (insn.getType()) {
            case NEW_INSTANCE:
                ArgType clsType = (ArgType) ((IndexInsnNode) insn).getIndex();
                addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, clsType));
                break;

            case CONSTRUCTOR:
                ArgType ctrClsType = replaceAnonymousType((ConstructorInsn) insn);
                addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, ctrClsType));
                break;

            case CONST:
                LiteralArg constLit = (LiteralArg) insn.getArg(0);
                addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, constLit.getType()));
                break;

            case MOVE_EXCEPTION:
                ExcHandlerAttr excHandlerAttr = insn.get(AType.EXC_HANDLER);
                if (excHandlerAttr != null) {
                    for (ClassInfo catchType : excHandlerAttr.getHandler().getCatchTypes()) {
                        addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, catchType.getType()));
                    }
                } else {
                    addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, insn.getResult().getInitType()));
                }
                break;

            case INVOKE:
                addBound(typeInfo, makeAssignInvokeBound((InvokeNode) insn));
                break;

            case IGET:
                addBound(typeInfo, makeAssignFieldGetBound((IndexInsnNode) insn));
                break;

            case CHECK_CAST:
                if (insn.contains(AFlag.SOFT_CAST)) {
                    // 忽略边界约束，将在类型更新时进行检查
                } else {
                    addBound(typeInfo, new TypeBoundCheckCastAssign(root, (IndexInsnNode) insn));
                }
                break;

            default:
                ArgType type = insn.getResult().getInitType();
                addBound(typeInfo, new TypeBoundConst(BoundEnum.ASSIGN, type));
                break;
        }
    }

    private ArgType replaceAnonymousType(ConstructorInsn ctr) {
        if (ctr.isNewInstance()) {
            ClassNode ctrCls = root.resolveClass(ctr.getClassType());
            if (ctrCls != null && ctrCls.contains(AFlag.DONT_GENERATE)) {
                AnonymousClassAttr baseTypeAttr = ctrCls.get(AType.ANONYMOUS_CLASS);
                if (baseTypeAttr != null && baseTypeAttr.getInlineType() == AnonymousClassAttr.InlineType.CONSTRUCTOR) {
                    return baseTypeAttr.getBaseType();
                }
            }
        }
        return ctr.getClassType().getType();
    }

    private ITypeBound makeAssignFieldGetBound(IndexInsnNode insn) {
        ArgType initType = insn.getResult().getInitType();
        if (initType.containsTypeVariable()) {
            return new TypeBoundFieldGetAssign(root, insn, initType);
        }
        return new TypeBoundConst(BoundEnum.ASSIGN, initType);
    }

    private ITypeBound makeAssignInvokeBound(InvokeNode invokeNode) {
        ArgType boundType = invokeNode.getCallMth().getReturnType();
        ArgType genericReturnType = root.getMethodUtils().getMethodGenericReturnType(invokeNode);
        if (genericReturnType != null) {
            if (genericReturnType.containsTypeVariable()) {
                InvokeType invokeType = invokeNode.getInvokeType();
                if (invokeNode.getArgsCount() != 0
                        && invokeType != InvokeType.STATIC && invokeType != InvokeType.SUPER) {
                    return new TypeBoundInvokeAssign(root, invokeNode, genericReturnType);
                }
            } else {
                boundType = genericReturnType;
            }
        }
        return new TypeBoundConst(BoundEnum.ASSIGN, boundType);
    }

    @Nullable
    private ITypeBound makeUseBound(RegisterArg regArg) {
        InsnNode insn = regArg.getParentInsn();
        if (insn == null) {
            return null;
        }
        if (insn instanceof BaseInvokeNode) {
            ITypeBound invokeUseBound = makeInvokeUseBound(regArg, (BaseInvokeNode) insn);
            if (invokeUseBound != null) {
                return invokeUseBound;
            }
        }
        if (insn.getType() == InsnType.CHECK_CAST && insn.contains(AFlag.SOFT_CAST)) {
            // 忽略软类型转换
            return null;
        }
        return new TypeBoundConst(BoundEnum.USE, regArg.getInitType(), regArg);
    }

    private ITypeBound makeInvokeUseBound(RegisterArg regArg, BaseInvokeNode invoke) {
        InsnArg instanceArg = invoke.getInstanceArg();
        if (instanceArg == null) {
            return null;
        }
        MethodUtils methodUtils = root.getMethodUtils();
        IMethodDetails methodDetails = methodUtils.getMethodDetails(invoke);
        if (methodDetails == null) {
            return null;
        }
        if (instanceArg != regArg) {
            int argIndex = invoke.getArgIndex(regArg) - invoke.getFirstArgOffset();
            ArgType argType = methodDetails.getArgTypes().get(argIndex);
            if (!argType.containsTypeVariable()) {
                return null;
            }
            return new TypeBoundInvokeUse(root, invoke, regArg, argType);
        }

        // 对于重写方法，使用原始声明类作为类型
        if (methodDetails instanceof MethodNode) {
            MethodNode callMth = (MethodNode) methodDetails;
            ClassInfo declCls = methodUtils.getMethodOriginDeclClass(callMth);
            return new TypeBoundConst(BoundEnum.USE, declCls.getType(), regArg);
        }
        return null;
    }

    private void assignImmutableTypes(MethodNode mth) {
        for (SSAVar ssaVar : mth.getSVars()) {
            ArgType immutableType = getSsaImmutableType(ssaVar);
            if (immutableType != null) {
                ssaVar.markAsImmutable(immutableType);
            }
        }
    }

    @Override
    public String getName() {
        return "TypeInferenceVisitor";
    }
}
