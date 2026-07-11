package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspMethod;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodBridgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.SkipMethodArgsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.BaseInvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 方法工具类。
 * <p>
 * 提供与方法相关的辅助能力，包括解析方法详情与方法节点、判断被跳过的参数、
 * 在类继承体系中查找重载方法、获取方法的重写基方法及原始声明类等。
 * 主要服务于反编译过程中的方法调用解析与类型推断。
 * </p>
 */
public class MethodUtils {
    private final RootNode root;

    public MethodUtils(RootNode rootNode) {
        this.root = rootNode;
    }

    /**
     * 获取方法调用指令对应的方法详情。
     * 优先读取指令上已附加的 {@link AType#METHOD_DETAILS} 属性，否则回退到被调用方法的解析。
     *
     * @param invokeNode 方法调用指令
     * @return 方法详情；无法解析时返回 {@code null}
     */
    @Nullable
    public IMethodDetails getMethodDetails(BaseInvokeNode invokeNode) {
        IMethodDetails methodDetails = invokeNode.get(AType.METHOD_DETAILS);
        if (methodDetails != null) {
            return methodDetails;
        }
        return getMethodDetails(invokeNode.getCallMth());
    }

    /**
     * 根据方法信息获取方法详情。
     * 优先解析为工程内的 {@link MethodNode}，否则回退到类路径信息中的方法详情。
     *
     * @param callMth 被调用方法的信息
     * @return 方法详情；无法解析时返回 {@code null}
     */
    @Nullable
    public IMethodDetails getMethodDetails(MethodInfo callMth) {
        MethodNode mthNode = root.resolveMethod(callMth);
        if (mthNode != null) {
            return mthNode;
        }
        return root.getClsp().getMethodDetails(callMth);
    }

    /**
     * 将方法调用指令解析为工程内的方法节点。
     *
     * @param invokeNode 方法调用指令
     * @return 对应的 {@link MethodNode}；若目标不是工程内方法则返回 {@code null}
     */
    @Nullable
    public MethodNode resolveMethod(BaseInvokeNode invokeNode) {
        IMethodDetails methodDetails = getMethodDetails(invokeNode);
        if (methodDetails instanceof MethodNode) {
            return (MethodNode) methodDetails;
        }
        return null;
    }

    /**
     * 判断方法调用中的某个参数是否应被跳过（不生成到反编译源码中）。
     *
     * @param invokeNode 方法调用指令
     * @param arg        待判断的参数
     * @return 若该参数被标记为跳过则返回 {@code true}
     */
    public boolean isSkipArg(BaseInvokeNode invokeNode, InsnArg arg) {
        MethodNode mth = resolveMethod(invokeNode);
        if (mth == null) {
            return false;
        }
        SkipMethodArgsAttr skipArgsAttr = mth.get(AType.SKIP_MTH_ARGS);
        if (skipArgsAttr == null) {
            return false;
        }
        int argIndex = invokeNode.getArgIndex(arg);
        return skipArgsAttr.isSkip(argIndex);
    }

    /**
     * 从 {@code startCls} 开始，在类继承体系中查找同名且参数个数相同的方法（即是否存在重载）。
     * 注意：{@code startCls} 可能与 {@code mthInfo.getDeclClass()} 不同。
     *
     * @param startCls 查找的起始类类型
     * @param mthInfo  待检查的方法信息
     * @return 若存在重载方法则返回 {@code true}
     */
    public boolean isMethodArgsOverloaded(ArgType startCls, MethodInfo mthInfo) {
        return processMethodArgsOverloaded(startCls, mthInfo, null);
    }

    /**
     * 从 {@code startCls} 开始，在类继承体系中收集所有与给定方法构成重载的方法详情。
     *
     * @param startCls 查找的起始类类型
     * @param mthInfo  待检查的方法信息
     * @return 重载方法详情列表
     */
    public List<IMethodDetails> collectOverloadedMethods(ArgType startCls, MethodInfo mthInfo) {
        List<IMethodDetails> list = new ArrayList<>();
        processMethodArgsOverloaded(startCls, mthInfo, list);
        return list;
    }

    /**
     * 获取方法调用的泛型返回类型。
     *
     * @param invokeNode 方法调用指令
     * @return 含有泛型信息的返回类型；若返回类型不含泛型或无法解析则返回 {@code null}
     */
    @Nullable
    public ArgType getMethodGenericReturnType(BaseInvokeNode invokeNode) {
        IMethodDetails methodDetails = getMethodDetails(invokeNode);
        if (methodDetails != null) {
            ArgType returnType = methodDetails.getReturnType();
            if (returnType != null && returnType.containsGeneric()) {
                return returnType;
            }
        }
        return null;
    }

    private boolean processMethodArgsOverloaded(ArgType startCls, MethodInfo mthInfo, @Nullable List<IMethodDetails> collectedMths) {
        if (startCls == null || !startCls.isObject()) {
            return false;
        }
        boolean isMthConstructor = mthInfo.isConstructor() || mthInfo.isClassInit();
        ClassNode classNode = root.resolveClass(startCls);
        if (classNode != null) {
            for (MethodNode mth : classNode.getMethods()) {
                if (mthInfo.isOverloadedBy(mth.getMethodInfo())) {
                    if (collectedMths == null) {
                        return true;
                    }
                    collectedMths.add(mth);
                }
            }
            if (!isMthConstructor) {
                if (processMethodArgsOverloaded(classNode.getSuperClass(), mthInfo, collectedMths)) {
                    if (collectedMths == null) {
                        return true;
                    }
                }
                for (ArgType parentInterface : classNode.getInterfaces()) {
                    if (processMethodArgsOverloaded(parentInterface, mthInfo, collectedMths)) {
                        if (collectedMths == null) {
                            return true;
                        }
                    }
                }
            }
        } else {
            ClspClass clsDetails = root.getClsp().getClsDetails(startCls);
            if (clsDetails == null) {
                // 类信息不可用
                return false;
            }
            for (ClspMethod clspMth : clsDetails.getMethodsMap().values()) {
                if (mthInfo.isOverloadedBy(clspMth.getMethodInfo())) {
                    if (collectedMths == null) {
                        return true;
                    }
                    collectedMths.add(clspMth);
                }
            }
            if (!isMthConstructor) {
                for (ArgType parent : clsDetails.getParents()) {
                    if (processMethodArgsOverloaded(parent, mthInfo, collectedMths)) {
                        if (collectedMths == null) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /**
     * 获取方法所重写的基方法（若存在重写关系）。
     *
     * @param mth 目标方法
     * @return 被重写的基方法详情；若不存在重写关系则返回 {@code null}
     */
    @Nullable
    public IMethodDetails getOverrideBaseMth(MethodNode mth) {
        MethodOverrideAttr overrideAttr = mth.get(AType.METHOD_OVERRIDE);
        if (overrideAttr == null) {
            return null;
        }
        return Utils.getOne(overrideAttr.getBaseMethods());
    }

    /**
     * 获取方法的原始声明类。
     * 若方法为重写方法则返回其基方法的声明类；若为桥接方法则递归追溯被桥接方法的声明类；
     * 否则返回方法自身的声明类。
     *
     * @param mth 目标方法
     * @return 方法的原始声明类信息
     */
    public ClassInfo getMethodOriginDeclClass(MethodNode mth) {
        IMethodDetails baseMth = getOverrideBaseMth(mth);
        if (baseMth != null) {
            return baseMth.getMethodInfo().getDeclClass();
        }
        MethodBridgeAttr bridgeAttr = mth.get(AType.BRIDGED_BY);
        if (bridgeAttr != null) {
            return getMethodOriginDeclClass(bridgeAttr.getBridgeMth());
        }
        return mth.getMethodInfo().getDeclClass();
    }
}
