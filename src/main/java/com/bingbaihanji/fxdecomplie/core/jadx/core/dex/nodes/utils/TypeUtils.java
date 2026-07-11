package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.core.clsp.ClspClass;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.ClassTypeVarsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.MethodTypeVarsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes.NotificationAttrNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.BaseInvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.isEmpty;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.notEmpty;

/**
 * 类型工具类
 * <p>
 * 提供泛型类型相关的辅助能力，包括获取类的泛型参数、解析类型变量、
 * 展开类型变量的边界、以及在实例类型与泛型声明之间进行类型变量替换等
 * 主要服务于反编译过程中的泛型还原与类型推断
 * </p>
 */
public class TypeUtils {
    private final RootNode root;

    public TypeUtils(RootNode rootNode) {
        this.root = rootNode;
    }

    private static Collection<ArgType> getKnownTypeVarsAtClass(ClassNode cls) {
        if (cls.isInner()) {
            Set<ArgType> typeVars = new HashSet<>(cls.getGenericTypeParameters());
            cls.visitParentClasses(parent -> typeVars.addAll(parent.getGenericTypeParameters()));
            return typeVars;
        }
        return cls.getGenericTypeParameters();
    }

    private static Set<ArgType> collectKnownTypeVarsAtMethod(MethodNode mth) {
        Set<ArgType> typeVars = new HashSet<>();
        typeVars.addAll(getKnownTypeVarsAtClass(mth.getParentClass()));
        typeVars.addAll(mth.getTypeParameters());
        return typeVars.isEmpty() ? Collections.emptySet() : typeVars;
    }

    private static Map<ArgType, ArgType> mergeTypeMaps(Map<ArgType, ArgType> base, Map<ArgType, ArgType> addition) {
        if (base.isEmpty()) {
            return addition;
        }
        if (addition.isEmpty()) {
            return base;
        }
        Map<ArgType, ArgType> map = new HashMap<>(base.size() + addition.size());
        for (Map.Entry<ArgType, ArgType> entry : base.entrySet()) {
            ArgType value = entry.getValue();
            ArgType type = addition.remove(value);
            if (type != null) {
                map.put(entry.getKey(), type);
            } else {
                map.put(entry.getKey(), entry.getValue());
            }
        }
        map.putAll(addition);
        return map;
    }

    private static void addTypeVarMapping(Map<ArgType, ArgType> map, ArgType typeVar, InsnArg arg) {
        if (arg == null || typeVar == null || !typeVar.isTypeKnown()) {
            return;
        }
        if (typeVar.isGenericType()) {
            map.put(typeVar, arg.getType());
        }
        // TODO: 解析内层类型变量：将 'List<T> -> List<String>' 解析为 'T -> String'
    }

    /**
     * 获取指定类型对应类声明的泛型类型参数列表
     * 优先从已解析的 {@link ClassNode} 获取，否则回退到类路径信息 {@link ClspClass}
     *
     * @param type 目标类型
     * @return 泛型类型参数列表，若无泛型参数则返回空列表
     */
    public List<ArgType> getClassGenerics(ArgType type) {
        ClassNode classNode = root.resolveClass(type);
        if (classNode != null) {
            return classNode.getGenericTypeParameters();
        }
        ClspClass clsDetails = root.getClsp().getClsDetails(type);
        if (clsDetails == null || clsDetails.getTypeParameters().isEmpty()) {
            return Collections.emptyList();
        }
        List<ArgType> generics = clsDetails.getTypeParameters();
        return generics == null ? Collections.emptyList() : generics;
    }

    /**
     * 获取指定类型的类级类型变量属性 {@link ClassTypeVarsAttr}
     * 若属性尚未构建则会即时构建并缓存到对应的 {@link ClassNode}
     *
     * @param type 目标类型
     * @return 类型变量属性 若无法解析对应类则返回 {@code null}
     */
    @Nullable
    public ClassTypeVarsAttr getClassTypeVars(ArgType type) {
        ClassNode classNode = root.resolveClass(type);
        if (classNode == null) {
            return null;
        }
        ClassTypeVarsAttr typeVarsAttr = classNode.get(AType.CLASS_TYPE_VARS);
        if (typeVarsAttr != null) {
            return typeVarsAttr;
        }
        return buildClassTypeVarsAttr(classNode);
    }

    /**
     * 在类的上下文中展开类型中的类型变量，为其补充已知的边界（extends）信息
     *
     * @param cls  提供类型变量上下文的类
     * @param type 待展开的类型
     * @return 展开后的类型（原对象，可能被就地修改）
     */
    public ArgType expandTypeVariables(ClassNode cls, ArgType type) {
        if (type.containsTypeVariable()) {
            expandTypeVar(cls, type, getKnownTypeVarsAtClass(cls));
        }
        return type;
    }

    /**
     * 在方法的上下文中展开类型中的类型变量，为其补充已知的边界（extends）信息
     *
     * @param mth  提供类型变量上下文的方法
     * @param type 待展开的类型
     * @return 展开后的类型（原对象，可能被就地修改）
     */
    public ArgType expandTypeVariables(MethodNode mth, ArgType type) {
        if (type.containsTypeVariable()) {
            expandTypeVar(mth, type, getKnownTypeVarsAtMethod(mth));
        }
        return type;
    }

    private void expandTypeVar(NotificationAttrNode node, ArgType type, Collection<ArgType> typeVars) {
        if (typeVars.isEmpty()) {
            return;
        }
        boolean allExtendsEmpty = true;
        for (ArgType argType : typeVars) {
            if (notEmpty(argType.getExtendTypes())) {
                allExtendsEmpty = false;
                break;
            }
        }
        if (allExtendsEmpty) {
            return;
        }
        type.visitTypes(t -> {
            if (t.isGenericType()) {
                String typeVarName = t.getObject();
                for (ArgType typeVar : typeVars) {
                    if (typeVar.getObject().equals(typeVarName)) {
                        t.setExtendTypes(typeVar.getExtendTypes());
                        return null;
                    }
                }
                node.addWarnComment("Unknown type variable: " + typeVarName + " in type: " + type);
            }
            return null;
        });
    }

    /**
     * 获取方法上下文中所有已知的类型变量（包含所属类及其父类、以及方法自身声明的类型变量）
     * 结果会缓存到方法的 {@link MethodTypeVarsAttr} 属性中
     *
     * @param mth 目标方法
     * @return 已知类型变量集合
     */
    public Set<ArgType> getKnownTypeVarsAtMethod(MethodNode mth) {
        MethodTypeVarsAttr typeVarsAttr = mth.get(AType.METHOD_TYPE_VARS);
        if (typeVarsAttr != null) {
            return typeVarsAttr.getTypeVars();
        }
        Set<ArgType> typeVars = collectKnownTypeVarsAtMethod(mth);
        MethodTypeVarsAttr varsAttr = MethodTypeVarsAttr.build(typeVars);
        mth.addAttr(varsAttr);
        return varsAttr.getTypeVars();
    }

    /**
     * 在当前方法中查找未知的类型变量，仅返回第一个
     *
     * @return 未知的类型变量 若未找到则返回 {@code null}
     */
    @Nullable
    public ArgType checkForUnknownTypeVars(MethodNode mth, ArgType checkType) {
        Set<ArgType> knownTypeVars = getKnownTypeVarsAtMethod(mth);
        return checkType.visitTypes(type -> {
            if (type.isGenericType() && !knownTypeVars.contains(type)) {
                return type;
            }
            return null;
        });
    }

    /**
     * 判断指定类型在给定方法上下文中是否包含未知的类型变量
     *
     * @param mth  目标方法
     * @param type 待检查的类型
     * @return 若包含未知类型变量则返回 {@code true}
     */
    public boolean containsUnknownTypeVar(MethodNode mth, ArgType type) {
        return checkForUnknownTypeVars(mth, type) != null;
    }

    /**
     * 使用实例类型中的实际类型替换 {@code typeWithGeneric} 中的泛型类型
     * <br>
     * 示例：
     * <ul>
     * <li>{@code instanceType: Set<String>}
     * <li>{@code typeWithGeneric: Iterator<E>}
     * <li>{@code return: Iterator<String>}
     * </ul>
     *
     * @param instanceType    携带实际泛型实参的实例类型
     * @param typeWithGeneric 含有待替换泛型变量的类型
     * @return 替换后的类型 若无法替换则返回 {@code null}
     */
    @Nullable
    public ArgType replaceClassGenerics(ArgType instanceType, ArgType typeWithGeneric) {
        return replaceClassGenerics(instanceType, instanceType, typeWithGeneric);
    }

    /**
     * 使用实例类型中的实际类型替换 {@code typeWithGeneric} 中的泛型类型
     * 相比 {@link #replaceClassGenerics(ArgType, ArgType)}，可单独指定提供泛型映射的源类型
     *
     * @param instanceType      携带实际泛型实参的实例类型
     * @param genericSourceType 提供类型变量映射的源类型
     * @param typeWithGeneric   含有待替换泛型变量的类型
     * @return 替换后的类型 若无法替换则返回 {@code null}
     */
    @Nullable
    public ArgType replaceClassGenerics(ArgType instanceType, ArgType genericSourceType, ArgType typeWithGeneric) {
        if (typeWithGeneric == null || genericSourceType == null) {
            return null;
        }
        Map<ArgType, ArgType> typeVarsMap = Collections.emptyMap();
        ClassTypeVarsAttr typeVars = getClassTypeVars(instanceType);
        if (typeVars != null) {
            typeVarsMap = mergeTypeMaps(typeVarsMap, typeVars.getTypeVarsMapFor(genericSourceType));
        }
        typeVarsMap = mergeTypeMaps(typeVarsMap, getTypeVariablesMapping(instanceType));
        ArgType outerType = instanceType.getOuterType();
        while (outerType != null) {
            typeVarsMap = mergeTypeMaps(typeVarsMap, getTypeVariablesMapping(outerType));
            outerType = outerType.getOuterType();
        }
        return replaceTypeVariablesUsingMap(typeWithGeneric, typeVarsMap);
    }

    /**
     * 构建泛型类型的类型变量到实际类型的映射
     * 例如对 {@code Map<String, Integer>}，返回 {@code K -> String, V -> Integer}
     *
     * @param clsType 携带实际泛型实参的类型
     * @return 类型变量到实际类型的映射 无法构建时返回空映射
     */
    public Map<ArgType, ArgType> getTypeVariablesMapping(ArgType clsType) {
        if (!clsType.isGeneric()) {
            return Collections.emptyMap();
        }
        List<ArgType> typeParameters = root.getTypeUtils().getClassGenerics(clsType);
        if (typeParameters.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ArgType> actualTypes = clsType.getGenericTypes();
        if (isEmpty(actualTypes)) {
            return Collections.emptyMap();
        }
        int genericParamsCount = actualTypes.size();
        if (genericParamsCount != typeParameters.size()) {
            return Collections.emptyMap();
        }
        Map<ArgType, ArgType> replaceMap = new HashMap<>(genericParamsCount);
        for (int i = 0; i < genericParamsCount; i++) {
            ArgType actualType = actualTypes.get(i);
            ArgType typeVar = typeParameters.get(i);
            if (typeVar.getExtendTypes() != null) {
                // 强制使用短格式（仅类型变量名）
                typeVar = ArgType.genericType(typeVar.getObject());
            }
            replaceMap.put(typeVar, actualType);
        }
        return replaceMap;
    }

    /**
     * 根据方法调用指令，构建其类型变量到实际类型的映射
     * 映射来源包括返回值以及各实参与形参类型的对应关系
     *
     * @param invokeInsn 方法调用指令
     * @return 类型变量到实际类型的映射 无方法详情时返回空映射
     */
    public Map<ArgType, ArgType> getTypeVarMappingForInvoke(BaseInvokeNode invokeInsn) {
        IMethodDetails mthDetails = root.getMethodUtils().getMethodDetails(invokeInsn);
        if (mthDetails == null) {
            return Collections.emptyMap();
        }
        Map<ArgType, ArgType> map = new HashMap<>(1 + invokeInsn.getArgsCount());
        addTypeVarMapping(map, mthDetails.getReturnType(), invokeInsn.getResult());
        int argCount = Math.min(mthDetails.getArgTypes().size(), invokeInsn.getArgsCount() - invokeInsn.getFirstArgOffset());
        for (int i = 0; i < argCount; i++) {
            addTypeVarMapping(map, mthDetails.getArgTypes().get(i), invokeInsn.getArg(i + invokeInsn.getFirstArgOffset()));
        }
        return map;
    }

    /**
     * 根据方法调用的实参类型，替换方法泛型返回类型中的类型变量
     *
     * @param invokeInsn      方法调用指令
     * @param details         方法详情
     * @param typeWithGeneric 含有待替换类型变量的类型
     * @return 替换后的类型 若无法替换则返回 {@code null}
     */
    @Nullable
    public ArgType replaceMethodGenerics(BaseInvokeNode invokeInsn, IMethodDetails details, ArgType typeWithGeneric) {
        if (typeWithGeneric == null) {
            return null;
        }
        List<ArgType> methodArgTypes = details.getArgTypes();
        if (methodArgTypes.isEmpty()) {
            return null;
        }
        int firstArgOffset = invokeInsn.getFirstArgOffset();
        int argsCount = methodArgTypes.size();
        for (int i = 0; i < argsCount; i++) {
            ArgType methodArgType = methodArgTypes.get(i);
            InsnArg insnArg = invokeInsn.getArg(i + firstArgOffset);
            ArgType insnType = insnArg.getType();
            if (methodArgType.equals(typeWithGeneric)) {
                return insnType;
            }
        }
        // TODO: 为类型变量构建完整的映射表
        return null;
    }

    /**
     * 依据给定的类型变量映射，递归替换类型中的所有类型变量
     * 支持数组、通配符、外层泛型以及带泛型实参的类型等结构
     *
     * @param replaceType 待替换的类型
     * @param replaceMap  类型变量到实际类型的映射
     * @return 替换后的类型 若无可替换内容或映射为空则返回 {@code null}
     */
    @Nullable
    public ArgType replaceTypeVariablesUsingMap(ArgType replaceType, Map<ArgType, ArgType> replaceMap) {
        if (replaceMap.isEmpty()) {
            return null;
        }
        if (replaceType.isGenericType()) {
            return replaceMap.get(replaceType);
        }
        if (replaceType.isArray()) {
            ArgType replaced = replaceTypeVariablesUsingMap(replaceType.getArrayElement(), replaceMap);
            if (replaced == null) {
                return null;
            }
            return ArgType.array(replaced);
        }

        ArgType wildcardType = replaceType.getWildcardType();
        if (wildcardType != null && wildcardType.containsTypeVariable()) {
            ArgType newWildcardType = replaceTypeVariablesUsingMap(wildcardType, replaceMap);
            if (newWildcardType == null) {
                return null;
            }
            return ArgType.wildcard(newWildcardType, replaceType.getWildcardBound());
        }

        if (replaceType.isGeneric()) {
            ArgType outerType = replaceType.getOuterType();
            if (outerType != null) {
                ArgType replacedOuter = replaceTypeVariablesUsingMap(outerType, replaceMap);
                if (replacedOuter == null) {
                    return null;
                }
                ArgType innerType = replaceType.getInnerType();
                ArgType replacedInner = replaceTypeVariablesUsingMap(innerType, replaceMap);
                return ArgType.outerGeneric(replacedOuter, replacedInner == null ? innerType : replacedInner);
            }
            List<ArgType> genericTypes = replaceType.getGenericTypes();
            if (notEmpty(genericTypes)) {
                List<ArgType> newTypes = Utils.collectionMap(genericTypes, t -> {
                    ArgType type = replaceTypeVariablesUsingMap(t, replaceMap);
                    return type == null ? t : type;
                });
                return ArgType.generic(replaceType, newTypes);
            }
        }
        return null;
    }

    private ClassTypeVarsAttr buildClassTypeVarsAttr(ClassNode cls) {
        Map<String, Map<ArgType, ArgType>> map = new HashMap<>();
        ArgType currentClsType = cls.getClassInfo().getType();
        map.put(currentClsType.getObject(), getTypeVariablesMapping(currentClsType));

        cls.visitSuperTypes((parent, type) -> {
            List<ArgType> currentVars = type.getGenericTypes();
            if (Utils.isEmpty(currentVars)) {
                return;
            }
            int varsCount = currentVars.size();
            List<ArgType> sourceTypeVars = getClassGenerics(type);
            if (varsCount == sourceTypeVars.size()) {
                Map<ArgType, ArgType> parentTypeMap = map.get(parent.getObject());
                Map<ArgType, ArgType> varsMap = new HashMap<>(varsCount);
                for (int i = 0; i < varsCount; i++) {
                    ArgType currentTypeVar = currentVars.get(i);
                    ArgType resultType = parentTypeMap != null ? parentTypeMap.get(currentTypeVar) : null;
                    varsMap.put(sourceTypeVars.get(i), resultType != null ? resultType : currentTypeVar);
                }
                map.put(type.getObject(), varsMap);
            }
        });
        List<ArgType> currentTypeVars = cls.getGenericTypeParameters();
        ClassTypeVarsAttr typeVarsAttr = new ClassTypeVarsAttr(currentTypeVars, map);
        cls.addAttr(typeVarsAttr);
        return typeVarsAttr;
    }

    /**
     * 遍历指定类型的所有父类型（父类与接口），对每一对 (子类型, 父类型) 调用回调
     * 已解析的类委托给 {@link ClassNode#visitSuperTypes}，否则基于类路径信息递归遍历
     *
     * @param type     起始类型
     * @param consumer 接收 (子类型, 父类型) 的回调
     */
    public void visitSuperTypes(ArgType type, BiConsumer<ArgType, ArgType> consumer) {
        ClassNode cls = root.resolveClass(type);
        if (cls != null) {
            cls.visitSuperTypes(consumer);
        } else {
            ClspClass clspClass = root.getClsp().getClsDetails(type);
            if (clspClass != null) {
                for (ArgType superType : clspClass.getParents()) {
                    if (!superType.equals(ArgType.OBJECT)) {
                        consumer.accept(type, superType);
                        visitSuperTypes(superType, consumer);
                    }
                }
            }
        }
    }
}
