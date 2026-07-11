package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspMethod;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodBridgeAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodOverrideAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.RenameReasonAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.AccessInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.rename.RenameVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompare;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompareEnum;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeInferenceVisitor;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@JadxVisitor(
        name = "OverrideMethodVisitor",
        desc = "Mark override methods and revert type erasure",
        runBefore = {
                TypeInferenceVisitor.class,
                RenameVisitor.class
        }
)
/**
 * 重写方法访问器
 * <p>
 * 遍历类的父类型层次结构，标记出属于方法重写（override）的方法，并为其附加
 * {@link MethodOverrideAttr} 属性 同时尝试还原因类型擦除（type erasure）而丢失的
 * 返回值类型与参数类型，使其与基类/接口中的方法签名保持一致若修正后的签名可能引发
 * 方法冲突，还会进行冲突检测与重命名处理
 */
public class OverrideMethodVisitor extends AbstractVisitor {

    // TODO: 此时反混淆器尚不可用，且映射文件已保存
    private static String makeNewAlias(MethodNode mth) {
        ClassNode cls = mth.getParentClass();
        String baseName = mth.getAlias();
        int k = 2;
        while (true) {
            String alias = baseName + k;
            MethodNode methodNode = cls.searchMethodByShortName(alias);
            if (methodNode == null) {
                return alias;
            }
            k++;
        }
    }

    /**
     * 收集类的所有父类型信息，并对类中每个方法进行重写检测与类型修正
     *
     * @param cls 待处理的类节点
     * @return 始终返回 {@code true}，以便继续访问该类的内部结构
     */
    @Override
    public boolean visit(ClassNode cls) throws JadxException {
        SuperTypesData superData = collectSuperTypes(cls);
        if (superData != null) {
            for (MethodNode mth : cls.getMethods()) {
                processMth(mth, superData);
            }
        }
        return true;
    }

    private void processMth(MethodNode mth, SuperTypesData superData) {
        if (mth.isConstructor() || mth.getAccessFlags().isStatic() || mth.getAccessFlags().isPrivate()) {
            return;
        }
        MethodOverrideAttr attr = processOverrideMethods(mth, superData);
        if (attr != null) {
            if (attr.getBaseMethods().isEmpty()) {
                throw new JadxRuntimeException("No base methods for override attribute: " + attr.getOverrideList());
            }
            mth.addAttr(attr);
            IMethodDetails baseMth = Utils.getOne(attr.getBaseMethods());
            if (baseMth != null) {
                boolean updated = fixMethodReturnType(mth, baseMth, superData);
                updated |= fixMethodArgTypes(mth, baseMth, superData);
                if (updated) {
                    // 检查新签名是否会导致方法冲突
                    checkMethodSignatureCollisions(mth, mth.root().getArgs().isRenameValid());
                }
            }
        }
    }

    private MethodOverrideAttr processOverrideMethods(MethodNode mth, SuperTypesData superData) {
        MethodOverrideAttr result = mth.get(AType.METHOD_OVERRIDE);
        if (result != null) {
            return result;
        }
        ClassNode cls = mth.getParentClass();
        String signature = mth.methodInfo().makeSignature(false);
        List<IMethodDetails> overrideList = new ArrayList<>();
        Set<IMethodDetails> baseMethods = new HashSet<>();
        for (ArgType superType : superData.getSuperTypes()) {
            ClassNode classNode = mth.root().resolveClass(superType);
            if (classNode != null) {
                MethodNode ovrdMth = searchOverriddenMethod(classNode, mth, signature);
                if (ovrdMth != null) {
                    if (isMethodVisibleInCls(ovrdMth, cls)) {
                        overrideList.add(ovrdMth);
                        MethodOverrideAttr attr = ovrdMth.get(AType.METHOD_OVERRIDE);
                        if (attr != null) {
                            addBaseMethod(superData, overrideList, baseMethods, superType);
                            return buildOverrideAttr(mth, overrideList, baseMethods, attr);
                        }
                    }
                }
            } else {
                ClspClass clsDetails = mth.root().getClsp().getClsDetails(superType);
                if (clsDetails != null) {
                    Map<String, ClspMethod> methodsMap = clsDetails.getMethodsMap();
                    for (Map.Entry<String, ClspMethod> entry : methodsMap.entrySet()) {
                        String mthShortId = entry.getKey();
                        // 不检查完整签名，类路径中的方法是可信的
                        // 即：同一个类中不会包含签名相同的方法
                        if (mthShortId.startsWith(signature)) {
                            overrideList.add(entry.getValue());
                            break;
                        }
                    }
                }
            }
            addBaseMethod(superData, overrideList, baseMethods, superType);
        }
        return buildOverrideAttr(mth, overrideList, baseMethods, null);
    }

    private void addBaseMethod(SuperTypesData superData, List<IMethodDetails> overrideList, Set<IMethodDetails> baseMethods,
                               ArgType superType) {
        if (superData.getEndTypes().contains(superType.getObject())) {
            IMethodDetails last = Utils.last(overrideList);
            if (last != null) {
                baseMethods.add(last);
            }
        }
    }

    @Nullable
    private MethodNode searchOverriddenMethod(ClassNode cls, MethodNode mth, String signature) {
        // 通过精确的完整签名（含返回值）搜索，以对抗混淆（参见测试
        // 'TestOverrideWithSameName'）
        String shortId = mth.methodInfo().getShortId();
        for (MethodNode supMth : cls.getMethods()) {
            if (supMth.methodInfo().getShortId().equals(shortId) && !supMth.getAccessFlags().isStatic()) {
                return supMth;
            }
        }
        // 按不含返回值的签名搜索，并检查其返回值是否为更宽的类型
        for (MethodNode supMth : cls.getMethods()) {
            if (supMth.methodInfo().getShortId().startsWith(signature) && !supMth.getAccessFlags().isStatic()) {
                TypeCompare typeCompare = cls.root().getTypeCompare();
                ArgType supRetType = supMth.methodInfo().getReturnType();
                ArgType mthRetType = mth.methodInfo().getReturnType();
                TypeCompareEnum res = typeCompare.compareTypes(supRetType, mthRetType);
                if (res.isWider()) {
                    return supMth;
                }
                if (res == TypeCompareEnum.UNKNOWN || res == TypeCompareEnum.CONFLICT) {
                    mth.addDebugComment("Possible override for method " + supMth.methodInfo().getFullId());
                }
            }
        }
        return null;
    }

    @Nullable
    private MethodOverrideAttr buildOverrideAttr(MethodNode mth, List<IMethodDetails> overrideList,
                                                 Set<IMethodDetails> baseMethods, @Nullable MethodOverrideAttr attr) {
        if (overrideList.isEmpty() && attr == null) {
            return null;
        }
        if (attr == null) {
            // 追踪到了基类方法
            List<IMethodDetails> cleanOverrideList = overrideList.stream().distinct().collect(Collectors.toList());
            return applyOverrideAttr(mth, cleanOverrideList, baseMethods, false);
        }
        // 追踪在已处理过的方法处停止 -> 开始合并
        List<IMethodDetails> mergedOverrideList = Utils.mergeLists(overrideList, attr.getOverrideList());
        List<IMethodDetails> cleanOverrideList = mergedOverrideList.stream().distinct().collect(Collectors.toList());
        Set<IMethodDetails> mergedBaseMethods = Utils.mergeSets(baseMethods, attr.getBaseMethods());
        return applyOverrideAttr(mth, cleanOverrideList, mergedBaseMethods, true);
    }

    private MethodOverrideAttr applyOverrideAttr(MethodNode mth, List<IMethodDetails> overrideList,
                                                 Set<IMethodDetails> baseMethods, boolean update) {
        // 若重写链中包含未解析的方法，则禁止重命名
        boolean dontRename = overrideList.stream().anyMatch(m -> !(m instanceof MethodNode));
        SortedSet<MethodNode> relatedMethods = null;
        List<MethodNode> mthNodes = getMethodNodes(mth, overrideList);
        if (update) {
            // 合并所有重写属性中的关联方法
            for (MethodNode mthNode : mthNodes) {
                MethodOverrideAttr ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE);
                if (ovrdAttr != null) {
                    // 复用一个已分配的集合
                    relatedMethods = ovrdAttr.getRelatedMthNodes();
                    break;
                }
            }
            if (relatedMethods != null) {
                relatedMethods.addAll(mthNodes);
            } else {
                relatedMethods = new TreeSet<>(mthNodes);
            }
            for (MethodNode mthNode : mthNodes) {
                MethodOverrideAttr ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE);
                if (ovrdAttr != null) {
                    SortedSet<MethodNode> set = ovrdAttr.getRelatedMthNodes();
                    if (relatedMethods != set) {
                        relatedMethods.addAll(set);
                    }
                }
            }
        } else {
            relatedMethods = new TreeSet<>(mthNodes);
        }

        int depth = 0;
        for (MethodNode mthNode : mthNodes) {
            if (dontRename) {
                mthNode.add(AFlag.DONT_RENAME);
            }
            if (depth == 0) {
                // 跳过当前（第一个）方法
                depth = 1;
                continue;
            }
            if (update) {
                MethodOverrideAttr ovrdAttr = mthNode.get(AType.METHOD_OVERRIDE);
                if (ovrdAttr != null) {
                    ovrdAttr.setRelatedMthNodes(relatedMethods);
                    continue;
                }
            }
            mthNode.addAttr(new MethodOverrideAttr(Utils.listTail(overrideList, depth), relatedMethods, baseMethods));
            depth++;
        }
        return new MethodOverrideAttr(overrideList, relatedMethods, baseMethods);
    }

    @NotNull
    private List<MethodNode> getMethodNodes(MethodNode mth, List<IMethodDetails> overrideList) {
        List<MethodNode> list = new ArrayList<>(1 + overrideList.size());
        list.add(mth);
        for (IMethodDetails md : overrideList) {
            if (md instanceof MethodNode) {
                list.add((MethodNode) md);
            }
        }
        return list;
    }

    /**
     * 检查父类方法在当前类中是否可见
     * 注意：此方法为 {@code ModVisitor.isFieldVisibleInMethod} 的简化版
     */
    private boolean isMethodVisibleInCls(MethodNode superMth, ClassNode cls) {
        AccessInfo accessFlags = superMth.getAccessFlags();
        if (accessFlags.isPrivate()) {
            return false;
        }
        if (accessFlags.isPublic() || accessFlags.isProtected()) {
            return true;
        }
        // 包级私有可见性
        return Objects.equals(superMth.getParentClass().getPackage(), cls.getPackage());
    }

    @Nullable
    private SuperTypesData collectSuperTypes(ClassNode cls) {
        Set<ArgType> superTypes = new LinkedHashSet<>();
        Set<String> endTypes = new HashSet<>();
        collectSuperTypes(cls, superTypes, endTypes);
        if (superTypes.isEmpty()) {
            return null;
        }
        if (endTypes.isEmpty()) {
            throw new JadxRuntimeException("No end types in class hierarchy: " + cls);
        }
        return new SuperTypesData(new ArrayList<>(superTypes), endTypes);
    }

    private void collectSuperTypes(ClassNode cls, Set<ArgType> superTypes, Set<String> endTypes) {
        RootNode root = cls.root();
        int k = 0;
        ArgType superClass = cls.getSuperClass();
        if (superClass != null) {
            k += addSuperType(root, superTypes, endTypes, superClass);
        }
        for (ArgType iface : cls.getInterfaces()) {
            k += addSuperType(root, superTypes, endTypes, iface);
        }
        if (k == 0) {
            endTypes.add(cls.getType().getObject());
        }
    }

    private int addSuperType(RootNode root, Set<ArgType> superTypes, Set<String> endTypes, ArgType superType) {
        if (Objects.equals(superType, ArgType.OBJECT)) {
            return 0;
        }
        if (!superTypes.add(superType)) {
            // 发现 'super' 循环引用，停止处理
            return 0;
        }
        ClassNode classNode = root.resolveClass(superType);
        if (classNode != null) {
            collectSuperTypes(classNode, superTypes, endTypes);
            return 1;
        }
        ClspClass clsDetails = root.getClsp().getClsDetails(superType);
        if (clsDetails != null) {
            int k = 0;
            for (ArgType parentType : clsDetails.getParents()) {
                k += addSuperType(root, superTypes, endTypes, parentType);
            }
            if (k == 0) {
                endTypes.add(superType.getObject());
            }
            return 1;
        }
        // 未找到任何信息 => 视为继承层次终点
        endTypes.add(superType.getObject());
        return 1;
    }

    private boolean fixMethodReturnType(MethodNode mth, IMethodDetails baseMth, SuperTypesData superData) {
        ArgType returnType = mth.getReturnType();
        if (returnType == ArgType.VOID) {
            return false;
        }
        boolean updated = updateReturnType(mth, baseMth, superData);
        if (updated) {
            mth.addDebugComment("Return type fixed from '" + returnType + "' to match base method");
        }
        return updated;
    }

    private boolean updateReturnType(MethodNode mth, IMethodDetails baseMth, SuperTypesData superData) {
        ArgType baseReturnType = baseMth.getReturnType();
        if (mth.getReturnType().equals(baseReturnType)) {
            return false;
        }
        if (!baseReturnType.containsTypeVariable()) {
            return false;
        }
        TypeCompare typeCompare = mth.root().getTypeUpdate().getTypeCompare();
        ArgType baseCls = baseMth.methodInfo().getDeclClass().getType();
        for (ArgType superType : superData.getSuperTypes()) {
            TypeCompareEnum compareResult = typeCompare.compareTypes(superType, baseCls);
            if (compareResult == TypeCompareEnum.NARROW_BY_GENERIC) {
                ArgType targetRetType = mth.root().getTypeUtils().replaceClassGenerics(superType, baseReturnType);
                if (targetRetType != null
                        && !targetRetType.containsTypeVariable()
                        && !targetRetType.equals(mth.getReturnType())) {
                    mth.updateReturnType(targetRetType);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean fixMethodArgTypes(MethodNode mth, IMethodDetails baseMth, SuperTypesData superData) {
        List<ArgType> mthArgTypes = mth.getArgTypes();
        List<ArgType> baseArgTypes = baseMth.getArgTypes();
        if (mthArgTypes.equals(baseArgTypes)) {
            return false;
        }
        int argCount = mthArgTypes.size();
        if (argCount != baseArgTypes.size()) {
            return false;
        }
        boolean changed = false;
        List<ArgType> newArgTypes = new ArrayList<>(argCount);
        for (int argNum = 0; argNum < argCount; argNum++) {
            ArgType newType = updateArgType(mth, baseMth, superData, argNum);
            if (newType != null) {
                changed = true;
                newArgTypes.add(newType);
            } else {
                newArgTypes.add(mthArgTypes.get(argNum));
            }
        }
        if (changed) {
            mth.updateArgTypes(newArgTypes, "Method arguments types fixed to match base method");
        }
        return changed;
    }

    private ArgType updateArgType(MethodNode mth, IMethodDetails baseMth, SuperTypesData superData, int argNum) {
        ArgType arg = mth.getArgTypes().get(argNum);
        ArgType baseArg = baseMth.getArgTypes().get(argNum);
        if (arg.equals(baseArg)) {
            return null;
        }
        if (!baseArg.containsTypeVariable()) {
            return null;
        }
        TypeCompare typeCompare = mth.root().getTypeUpdate().getTypeCompare();
        ArgType baseCls = baseMth.methodInfo().getDeclClass().getType();
        for (ArgType superType : superData.getSuperTypes()) {
            TypeCompareEnum compareResult = typeCompare.compareTypes(superType, baseCls);
            if (compareResult == TypeCompareEnum.NARROW_BY_GENERIC) {
                ArgType targetArgType = mth.root().getTypeUtils().replaceClassGenerics(superType, baseArg);
                if (targetArgType != null
                        && !targetArgType.containsTypeVariable()
                        && !targetArgType.equals(arg)) {
                    return targetArgType;
                }
            }
        }
        return null;
    }

    private void checkMethodSignatureCollisions(MethodNode mth, boolean rename) {
        String mthName = mth.methodInfo().getAlias();
        String newSignature = MethodInfo.makeShortId(mthName, mth.getArgTypes(), null);
        for (MethodNode otherMth : mth.getParentClass().getMethods()) {
            String otherMthName = otherMth.getAlias();
            if (otherMthName.equals(mthName) && otherMth != mth) {
                String otherSignature = otherMth.methodInfo().makeSignature(true, false);
                if (otherSignature.equals(newSignature)) {
                    if (rename) {
                        if (otherMth.contains(AFlag.DONT_RENAME) || otherMth.contains(AType.METHOD_OVERRIDE)) {
                            otherMth.addWarnComment("Can't rename method to resolve collision");
                        } else {
                            otherMth.methodInfo().setAlias(makeNewAlias(otherMth));
                            otherMth.addAttr(new RenameReasonAttr("avoid collision after fix types in other method"));
                        }
                    }
                    otherMth.addAttr(new MethodBridgeAttr(mth));
                    return;
                }
            }
        }
    }

    @Override
    public String getName() {
        return "OverrideMethodVisitor";
    }

    private static final class SuperTypesData {
        private final List<ArgType> superTypes;
        private final Set<String> endTypes;

        private SuperTypesData(List<ArgType> superTypes, Set<String> endTypes) {
            this.superTypes = superTypes;
            this.endTypes = endTypes;
        }

        public List<ArgType> getSuperTypes() {
            return superTypes;
        }

        public Set<String> getEndTypes() {
            return endTypes;
        }
    }
}
