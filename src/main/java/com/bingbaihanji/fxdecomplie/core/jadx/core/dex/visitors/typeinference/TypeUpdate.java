package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.ArithNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.BaseInvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.IndexInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.*;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.TypeUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxOverflowException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeUpdateResult.*;

/**
 * 类型更新器，负责在类型推断过程中对 SSA 变量及其相关指令参数执行类型检查和类型传播。
 * <p>
 * 当某个变量的类型发生变化时，会通过监听器机制将类型变更传播到所有相关的指令和变量上，
 * 同时通过边界检查和类型比较确保类型更新的正确性。
 * </p>
 */
public final class TypeUpdate {
    private static final Logger LOG = LoggerFactory.getLogger(TypeUpdate.class);

    private final RootNode root;
    private final Map<InsnType, ITypeListener> listenerRegistry;
    private final TypeCompare comparator;
    private final JadxArgs args;

    /**
     * 构造类型更新器
     *
     * @param root 根节点，提供全局的类路径信息和编译参数
     */
    public TypeUpdate(RootNode root) {
        this.root = root;
        this.args = root.getArgs();
        this.listenerRegistry = initListenerRegistry();
        this.comparator = new TypeCompare(root);
    }

    private static TypeUpdateResult processCallbacks(TypeUpdateInfo updateInfo, TypeUpdateResult result) {
        TypeUpdateResult current = result;
        while (true) {
            TypeUpdateRequest cbReq = updateInfo.pollNextCallback();
            if (cbReq == null) {
                return current;
            }
            ITypeUpdateCallback callback = Objects.requireNonNull(cbReq.getCallback());
            current = callback.updateCallback(current);
            if (current == null) {
                // 无结果，将回调重新放回队列
                // 等待结果计算完成后再执行
                updateInfo.saveCallback(cbReq);
                return null;
            }
            if (current == REJECT) {
                updateInfo.rollbackUpdate(cbReq.getArg());
            }
            // 继续处理下一个回调
        }
    }

    private static boolean isAssign(InsnNode insn, InsnArg arg) {
        return insn.getResult() == arg;
    }

    /**
     * 对所有相关变量执行类型检查和类型传播
     */
    public TypeUpdateResult apply(MethodNode mth, SSAVar ssaVar, ArgType candidateType) {
        return apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_EMPTY);
    }

    /**
     * 允许更宽泛的类型，用于从调试信息和某些特殊情况中应用类型
     */
    public TypeUpdateResult applyWithWiderAllow(MethodNode mth, SSAVar ssaVar, ArgType candidateType) {
        return apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_WIDER);
    }

    /**
     * 强制设置类型，即使当前类型相同也会重新应用
     */
    public TypeUpdateResult applyWithWiderIgnSame(MethodNode mth, SSAVar ssaVar, ArgType candidateType) {
        return apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_WIDER_IGNORE_SAME);
    }

    /**
     * 从调试信息中应用类型，允许更宽泛的类型匹配
     */
    public TypeUpdateResult applyDebugInfo(MethodNode mth, SSAVar ssaVar, ArgType candidateType) {
        return apply(mth, ssaVar, candidateType, TypeUpdateFlags.FLAGS_APPLY_DEBUG);
    }

    private TypeUpdateResult apply(MethodNode mth, SSAVar ssaVar, ArgType candidateType, TypeUpdateFlags flags) {
        try {
            if (candidateType == null || !candidateType.isTypeKnown()) {
                return REJECT;
            }
            if (false) {
                LOG.debug("Start type update for {} to {}", ssaVar.toShortString(), candidateType);
            }
            TypeUpdateInfo updateInfo = new TypeUpdateInfo(mth, flags, args);
            TypeUpdateResult result = queueTypeUpdate(updateInfo, ssaVar.getAssign(), candidateType, null);
            if (result == null) {
                result = runUpdate(updateInfo);
            }
            if (result == REJECT) {
                return result;
            }
            if (updateInfo.isEmpty()) {
                return SAME;
            }
            if (false) {
                LOG.debug("Applying type {} to {}:", candidateType, ssaVar.toShortString());
                updateInfo.getSortedUpdates().forEach(upd -> LOG.debug("  {} -> {} in {}",
                        upd.getType(), upd.getArg().toShortString(), upd.getArg().getParentInsn()));
            }
            updateInfo.applyUpdates();
            return CHANGED;
        } catch (JadxOverflowException e) {
            throw e;
        } catch (Exception e) {
            throw new JadxRuntimeException("Type update failed for variable: " + ssaVar + ", new type: " + candidateType, e);
        }
    }

    private TypeUpdateResult runUpdate(TypeUpdateInfo updateInfo) {
        TypeUpdateResult result = REJECT;
        while (true) {
            TypeUpdateRequest request = updateInfo.pollNextRequest();
            if (request == null) {
                return result;
            }
            InsnArg updateArg = request.getArg();
            ArgType updateType = request.getCandidateType();
            TypeUpdateResult newResult;
            if (request.isDirect()) {
                newResult = requestUpdate(updateInfo, updateArg, updateType);
            } else {
                newResult = updateTypeForArg(updateInfo, updateArg, updateType);
            }
            updateInfo.saveCallback(request);
            if (newResult == null) {
                // 无结果，继续处理下一个请求
            } else {
                // 将结果通过回调链向上传播
                result = processCallbacks(updateInfo, newResult);
            }
        }
    }

    /**
     * 为 InsnArg（指令参数）排队一个类型更新请求。
     *
     * @param callback 当此更新的结果计算完成后执行的回调，
     *                 可以为 null —— 此时回调将原样传递结果不做改变
     * @return 如果更新已加入队列则返回 null；如果未入队（校验失败）则返回非空结果
     */
    public @Nullable TypeUpdateResult queueTypeUpdate(TypeUpdateInfo updateInfo,
                                                      InsnArg arg, ArgType candidateType, @Nullable ITypeUpdateCallback callback) {
        // 校验本可以在队列处理时、请求运行前完成，但这里提前执行以加快处理速度
        // 这可能会增加代码复杂度，因为每次都需要检查结果是否为 null
        TypeUpdateResult res = verifyType(updateInfo, arg, candidateType);
        if (res != null) {
            if (callback == null) {
                return res;
            }
            TypeUpdateResult result = callback.updateCallback(res);
            if (result == null) {
                updateInfo.saveCallback(new TypeUpdateRequest(arg, candidateType, false, callback));
            }
            return result;
        }
        updateInfo.queueRequest(new TypeUpdateRequest(arg, candidateType, false, callback));
        return null;
    }

    /**
     * 排队一个直接类型更新请求（不经过校验，标记为 direct）。
     *
     * @param callback 结果计算完成后执行的回调，可以为 null
     * @return 恒返回 null（请求已加入队列）
     */
    public @Nullable TypeUpdateResult queueDirectTypeUpdate(TypeUpdateInfo updateInfo, InsnArg arg, ArgType candidateType,
                                                            @Nullable ITypeUpdateCallback callback) {
        updateInfo.queueRequest(new TypeUpdateRequest(arg, candidateType, true, callback));
        return null;
    }

    private TypeUpdateResult updateTypeForArg(TypeUpdateInfo updateInfo, InsnArg arg, ArgType candidateType) {
        if (false) {
            LOG.debug("-> update type for: {} to {}", arg, candidateType);
        }
        if (arg instanceof RegisterArg) {
            RegisterArg reg = (RegisterArg) arg;
            return updateTypeForSsaVar(updateInfo, reg.getSVar(), candidateType);
        }
        return requestUpdate(updateInfo, arg, candidateType);
    }

    private @Nullable TypeUpdateResult verifyType(TypeUpdateInfo updateInfo, InsnArg arg, ArgType candidateType) {
        if (candidateType == null) {
            throw new JadxRuntimeException("Null type update for arg: " + arg);
        }
        if (updateInfo.isProcessed(arg)) {
            return CHANGED;
        }
        ArgType currentType = arg.getType();
        TypeUpdateFlags typeUpdateFlags = updateInfo.getFlags();
        if (Objects.equals(currentType, candidateType)) {
            if (!typeUpdateFlags.isIgnoreSame()) {
                return SAME;
            }
        } else {
            if (candidateType.isWildcard()) {
                if (false) {
                    LOG.debug("Wildcard type rejected for {}: candidate={}, current={}", arg, candidateType, currentType);
                }
                return REJECT;
            }

            TypeCompareEnum compareResult = comparator.compareTypes(candidateType, currentType);
            if (compareResult.isConflict()) {
                if (false) {
                    LOG.debug("Type rejected for {}: candidate={} in conflict with current={}", arg, candidateType, currentType);
                }
                return REJECT;
            }
            if (compareResult == TypeCompareEnum.UNKNOWN && typeUpdateFlags.isIgnoreUnknown()) {
                return REJECT;
            }
            if (arg.isTypeImmutable() && currentType != ArgType.UNKNOWN) {
                // 不改变类型
                if (compareResult == TypeCompareEnum.EQUAL) {
                    return SAME;
                }
                if (false) {
                    LOG.debug("Type rejected for {} due to conflict: candidate={}, current={}", arg, candidateType, currentType);
                }
                return REJECT;
            }
            if (compareResult == TypeCompareEnum.WIDER_BY_GENERIC && typeUpdateFlags.isKeepGenerics()) {
                if (false) {
                    LOG.debug("Type rejected for {}: candidate={} is removing generic from current={}", arg, candidateType, currentType);
                }
                return REJECT;
            }
            if (compareResult.isWider() && !typeUpdateFlags.isAllowWider()) {
                if (false) {
                    LOG.debug("Type rejected for {}: candidate={} is wider than current={}", arg, candidateType, currentType);
                }
                return REJECT;
            }
            if (candidateType.containsTypeVariable()) {
                // 拒绝未知的类型变量
                ArgType unknownTypeVar = root.getTypeUtils().checkForUnknownTypeVars(updateInfo.getMth(), candidateType);
                if (unknownTypeVar != null) {
                    if (false) {
                        LOG.debug("Type rejected for {}: candidate: '{}' has unknown type var: '{}'", arg, candidateType, unknownTypeVar);
                    }
                    return REJECT;
                }
            }
        }
        return null;
    }

    private TypeUpdateResult updateTypeForSsaVar(TypeUpdateInfo updateInfo, SSAVar ssaVar, ArgType candidateType) {
        TypeInfo typeInfo = ssaVar.getTypeInfo();
        ArgType immutableType = ssaVar.getImmutableType();
        if (immutableType != null && !Objects.equals(immutableType, candidateType)) {
            if (false) {
                LOG.info("Reject change immutable type {} to {} for {}", immutableType, candidateType, ssaVar);
            }
            return REJECT;
        }
        if (!inBounds(updateInfo, ssaVar, typeInfo.getBounds(), candidateType)) {
            return REJECT;
        }
        var updateCallback = new ArgsListUpdateCallback<>(this, updateInfo, ssaVar.getUseList(), candidateType, true);
        updateCallback.setFinalResultCallback(result -> {
            if (result == REJECT) {
                // 回滚当前 SSA 变量中所有寄存器的更新
                updateInfo.rollbackUpdate(ssaVar.getAssign());
                ssaVar.getUseList().forEach(updateInfo::rollbackUpdate);
            }
            return result;
        });
        return queueDirectTypeUpdate(updateInfo, ssaVar.getAssign(), candidateType, updateCallback);
    }

    private TypeUpdateResult requestUpdate(TypeUpdateInfo updateInfo, InsnArg arg, ArgType candidateType) {
        if (updateInfo.isProcessed(arg)) {
            return CHANGED;
        }
        updateInfo.requestUpdate(arg, candidateType);
        InsnNode insn = arg.getParentInsn();
        if (insn == null) {
            return SAME;
        }
        ITypeListener listener = listenerRegistry.get(insn.getType());
        if (listener == null) {
            return CHANGED;
        }
        if (false) {
            LOG.debug("Run listener for insn: {}, arg: {}, type: {}", insn.getType(), arg, candidateType);
        }
        return listener.update(updateInfo, insn, arg, candidateType);
    }

    boolean inBounds(Set<ITypeBound> bounds, ArgType candidateType) {
        for (ITypeBound bound : bounds) {
            ArgType boundType = bound.getType();
            if (boundType != null && !checkBound(candidateType, bound, boundType)) {
                return false;
            }
        }
        return true;
    }

    private boolean inBounds(TypeUpdateInfo updateInfo, SSAVar ssaVar, Set<ITypeBound> bounds, ArgType candidateType) {
        for (ITypeBound bound : bounds) {
            ArgType boundType;
            if (updateInfo != null && bound instanceof ITypeBoundDynamic) {
                boundType = ((ITypeBoundDynamic) bound).getType(updateInfo);
            } else {
                boundType = bound.getType();
            }
            if (boundType != null && !checkBound(candidateType, bound, boundType)) {
                if (false) {
                    LOG.debug("Reject type '{}' for {} by bound: {} from {}", candidateType, ssaVar, boundType, bound);
                }
                return false;
            }
        }
        return true;
    }

    private boolean checkBound(ArgType candidateType, ITypeBound bound, ArgType boundType) {
        TypeCompareEnum compareResult = comparator.compareTypes(candidateType, boundType);
        switch (compareResult) {
            case EQUAL:
                return true;

            case WIDER:
                return bound.getBound() != BoundEnum.USE;

            case NARROW:
                if (bound.getBound() == BoundEnum.ASSIGN) {
                    return !boundType.isTypeKnown() && checkAssignForUnknown(boundType, candidateType);
                }
                return true;

            case WIDER_BY_GENERIC:
            case NARROW_BY_GENERIC:
                // 允许将对象替换为带已知泛型类型的同一对象
                // 因为对外部方法和字段的信息不完整
                return true;

            case CONFLICT:
            case CONFLICT_BY_GENERIC:
                return false;

            case UNKNOWN:
                LOG.debug("Can't compare types, unknown hierarchy: {} and {}", candidateType, boundType);
                comparator.compareTypes(candidateType, boundType);
                return true;

            default:
                throw new JadxRuntimeException("Not processed type compare enum: " + compareResult);
        }
    }

    private boolean checkAssignForUnknown(ArgType boundType, ArgType candidateType) {
        if (boundType == ArgType.UNKNOWN) {
            return true;
        }
        boolean candidateArray = candidateType.isArray();
        if (boundType.isArray() && candidateArray) {
            return checkAssignForUnknown(boundType.getArrayElement(), candidateType.getArrayElement());
        }
        if (candidateArray && boundType.contains(PrimitiveType.ARRAY)) {
            return true;
        }
        if (candidateType.isObject() && boundType.contains(PrimitiveType.OBJECT)) {
            return true;
        }
        if (candidateType.isPrimitive() && boundType.contains(candidateType.getPrimitiveType())) {
            return true;
        }
        return false;
    }

    private Map<InsnType, ITypeListener> initListenerRegistry() {
        Map<InsnType, ITypeListener> registry = new EnumMap<>(InsnType.class);
        registry.put(InsnType.CONST, this::sameFirstArgListener);
        registry.put(InsnType.MOVE, this::moveListener);
        registry.put(InsnType.PHI, this::allSameListener);
        registry.put(InsnType.AGET, this::arrayGetListener);
        registry.put(InsnType.APUT, this::arrayPutListener);
        registry.put(InsnType.IF, this::ifListener);
        registry.put(InsnType.ARITH, this::arithListener);
        registry.put(InsnType.NEG, this::suggestAllSameListener);
        registry.put(InsnType.NOT, this::suggestAllSameListener);
        registry.put(InsnType.CHECK_CAST, this::checkCastListener);
        registry.put(InsnType.INVOKE, this::invokeListener);
        registry.put(InsnType.CONSTRUCTOR, this::invokeListener);
        return registry;
    }

    private TypeUpdateResult invokeListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        BaseInvokeNode invoke = (BaseInvokeNode) insn;
        if (isAssign(invoke, arg)) {
            // TODO: 实现反向类型传播（从结果传播到实例）
            return SAME;
        }
        if (invoke.getInstanceArg() == arg) {
            IMethodDetails methodDetails = root.getMethodUtils().getMethodDetails(invoke);
            if (methodDetails == null) {
                return SAME;
            }
            TypeUtils typeUtils = root.getTypeUtils();
            Set<ArgType> knownTypeVars = typeUtils.getKnownTypeVarsAtMethod(updateInfo.getMth());
            Map<ArgType, ArgType> typeVarsMap = typeUtils.getTypeVariablesMapping(candidateType);

            ArgType returnType = methodDetails.getReturnType();
            List<ArgType> argTypes = methodDetails.getArgTypes();
            int argsCount = argTypes.size();

            Supplier<ArgType> getReturnType;
            Function<Integer, ArgType> getArgType;
            if (typeVarsMap.isEmpty()) {
                // 泛型无法解析 => 按原样使用
                getReturnType = () -> returnType;
                getArgType = argTypes::get;
            } else {
                // 应用前先解析类型
                getReturnType = () -> typeUtils.replaceTypeVariablesUsingMap(returnType, typeVarsMap);
                getArgType = argNum -> typeUtils.replaceClassGenerics(candidateType, argTypes.get(argNum));
            }
            return new InvokeUpdateCallback(this, updateInfo, invoke, argsCount, knownTypeVars, getReturnType, getArgType)
                    .runQueue();
        }
        return SAME;
    }

    private TypeUpdateResult sameFirstArgListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        InsnArg changeArg = isAssign(insn, arg) ? insn.getArg(0) : insn.getResult();
        if (updateInfo.hasUpdateWithType(changeArg, candidateType)) {
            return CHANGED;
        }
        return queueTypeUpdate(updateInfo, changeArg, candidateType, null);
    }

    private TypeUpdateResult moveListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        if (insn.getResult() == null) {
            return CHANGED;
        }
        boolean assignChanged = isAssign(insn, arg);
        InsnArg changeArg = assignChanged ? insn.getArg(0) : insn.getResult();

        // 允许结果类型更宽
        TypeCompareEnum cmp = comparator.compareTypes(candidateType, changeArg.getType());
        boolean correctType = cmp.isEqual() || (assignChanged ? cmp.isWider() : cmp.isNarrow());

        return queueTypeUpdate(updateInfo, changeArg, candidateType, result -> {
            if (result == SAME && !correctType) {
                if (false) {
                    LOG.debug("Move insn types mismatch: {} -> {}, change arg: {}, insn: {}",
                            candidateType, changeArg.getType(), changeArg, insn);
                }
                return REJECT;
            }
            if (result == REJECT && correctType) {
                return CHANGED;
            }
            return result;
        });
    }

    /**
     * 所有参数必须具有相同的类型
     */
    private TypeUpdateResult allSameListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        if (!isAssign(insn, arg)) {
            return queueTypeUpdate(updateInfo, insn.getResult(), candidateType, null);
        }
        // 更新参数为相同类型
        var updateCallback = new ArgsListUpdateCallback<>(this, updateInfo, insn.getArgList(), candidateType, false);
        updateCallback.setArgsFilter(a -> a != arg);
        return updateCallback.runFirstQueue();
    }

    private TypeUpdateResult arithListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        ArithNode arithInsn = (ArithNode) insn;
        if (candidateType == ArgType.BOOLEAN && arithInsn.getOp().isBitOp()) {
            // 强制所有参数为 boolean
            return allSameListener(updateInfo, insn, arg, candidateType);
        }
        return suggestAllSameListener(updateInfo, insn, arg, candidateType);
    }

    /**
     * 尝试将候选类型设置到所有参数上，遇到拒绝时不失败
     */
    private TypeUpdateResult suggestAllSameListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        var updateCallback = new ArgsListUpdateCallback<>(this, updateInfo, insn.getArgList(), candidateType, false);
        updateCallback.setArgsFilter(a -> a != arg);
        updateCallback.setIgnoreReject(true);
        if (!isAssign(insn, arg)) {
            RegisterArg resultArg = insn.getResult();
            if (resultArg != null) {
                // 从结果开始
                return queueTypeUpdate(updateInfo, resultArg, candidateType, updateCallback);
            }
        }
        // 从第一个参数开始
        return updateCallback.runFirstQueue();
    }

    private TypeUpdateResult checkCastListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        IndexInsnNode checkCast = (IndexInsnNode) insn;
        if (isAssign(insn, arg)) {
            InsnArg insnArg = insn.getArg(0);
            return queueTypeUpdate(updateInfo, insnArg, candidateType,
                    r -> r == REJECT ? SAME : r);
        }
        ArgType castType = (ArgType) checkCast.getIndex();
        TypeCompareEnum res = comparator.compareTypes(candidateType, castType);
        if (res == TypeCompareEnum.CONFLICT) {
            // 允许将一个接口强转为另一个接口
            if (!isInterfaces(candidateType, castType)) {
                return REJECT;
            }
        }
        if (res == TypeCompareEnum.CONFLICT_BY_GENERIC) {
            if (!insn.contains(AFlag.SOFT_CAST)) {
                return REJECT;
            }
        }
        if (res == TypeCompareEnum.NARROW_BY_GENERIC && candidateType.containsGeneric()) {
            // 将泛型类型传播到结果
            return queueTypeUpdate(updateInfo, checkCast.getResult(), candidateType, null);
        }
        ArgType currentType = checkCast.getArg(0).getType();
        return candidateType.equals(currentType) ? SAME : CHANGED;
    }

    private boolean isInterfaces(ArgType firstType, ArgType secondType) {
        if (!firstType.isObject() || !secondType.isObject()) {
            return false;
        }
        ClspClass firstCls = root.getClsp().getClsDetails(firstType);
        ClspClass secondCls = root.getClsp().getClsDetails(secondType);
        if (firstCls != null && !firstCls.isInterface()) {
            return false;
        }
        if (secondCls != null && !secondCls.isInterface()) {
            return false;
        }
        if (firstCls == null || secondCls == null) {
            return true;
        }
        return secondCls.isInterface() && firstCls.isInterface();
    }

    private TypeUpdateResult arrayGetListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        if (isAssign(insn, arg)) {
            return queueTypeUpdate(updateInfo, insn.getArg(0), ArgType.array(candidateType), result -> {
                if (result == REJECT) {
                    ArgType arrType = insn.getArg(0).getType();
                    if (arrType.isTypeKnown() && arrType.isArray() && arrType.getArrayElement().isPrimitive()) {
                        TypeCompareEnum compResult = comparator.compareTypes(candidateType, arrType.getArrayElement());
                        if (compResult == TypeCompareEnum.WIDER) {
                            // 允许基本类型的隐式向上转型（int a = byteArr[n]）
                            return CHANGED;
                        }
                    }
                }
                return result;
            });
        }
        InsnArg arrArg = insn.getArg(0);
        if (arrArg == arg) {
            ArgType arrayElement = candidateType.getArrayElement();
            if (arrayElement == null) {
                return REJECT;
            }
            return queueTypeUpdate(updateInfo, insn.getResult(), arrayElement, result -> {
                if (result == REJECT) {
                    ArgType resType = insn.getResult().getType();
                    if (resType.isTypeKnown() && resType.isPrimitive()) {
                        TypeCompareEnum compResult = comparator.compareTypes(resType, arrayElement);
                        if (compResult == TypeCompareEnum.WIDER) {
                            // 允许基本类型的隐式向上转型（int a = byteArr[n]）
                            return CHANGED;
                        }
                    }
                }
                return result;
            });
        }
        // 索引参数
        return SAME;
    }

    private TypeUpdateResult arrayPutListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        InsnArg arrArg = insn.getArg(0);
        InsnArg putArg = insn.getArg(2);
        if (arrArg == arg) {
            ArgType arrayElement = candidateType.getArrayElement();
            if (arrayElement == null) {
                return REJECT;
            }
            return queueTypeUpdate(updateInfo, putArg, arrayElement, result -> {
                if (result == REJECT) {
                    ArgType putType = putArg.getType();
                    if (putType.isTypeKnown()) {
                        TypeCompareEnum compResult = comparator.compareTypes(arrayElement, putType);
                        if (compResult == TypeCompareEnum.WIDER || compResult == TypeCompareEnum.WIDER_BY_GENERIC) {
                            // 允许更宽的结果（即允许把任意对象放入 Object[]，或把 byte 放入 int[]）
                            return CHANGED;
                        }
                    }
                }
                return result;
            });
        }
        if (arrArg == putArg) {
            return queueTypeUpdate(updateInfo, arrArg, ArgType.array(candidateType), null);
        }
        // 索引
        return SAME;
    }

    private TypeUpdateResult ifListener(TypeUpdateInfo updateInfo, InsnNode insn, InsnArg arg, ArgType candidateType) {
        InsnArg firstArg = insn.getArg(0);
        InsnArg secondArg = insn.getArg(1);
        InsnArg updateArg = firstArg == arg ? secondArg : firstArg;
        return queueTypeUpdate(updateInfo, updateArg, candidateType, result -> {
            if (result == REJECT) {
                // 对对象和数组做宽松检查 —— 不比较精确类型
                ArgType updateArgType = updateArg.getType();
                if (candidateType.isObject() && updateArgType.canBeObject()) {
                    return SAME;
                }
                if (candidateType.isArray() && updateArgType.canBeArray()) {
                    return SAME;
                }
                if (candidateType.isPrimitive()) {
                    if (updateArgType.canBePrimitive(candidateType.getPrimitiveType())) {
                        return SAME;
                    }
                    if (updateArgType.isTypeKnown() && candidateType.getRegCount() == updateArgType.getRegCount()) {
                        return SAME;
                    }
                }
            }
            return result;
        });
    }

    /**
     * 返回内部使用的类型比较器
     *
     * @return {@link TypeCompare} 实例
     */
    public TypeCompare getTypeCompare() {
        return comparator;
    }
}
