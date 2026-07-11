package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType.WildcardBound;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.PrimitiveType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference.TypeCompareEnum.*;
import static com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.isEmpty;

/**
 * 类型比较器，用于比较两个类型之间的关系 (相等、更窄、更宽或冲突)
 * 支持基本类型、对象类型、数组类型、泛型类型和通配符类型的比较
 */
public class TypeCompare {
    private static final Logger LOG = LoggerFactory.getLogger(TypeCompare.class);

    private final RootNode root;
    private final Comparator<ArgType> comparator;
    private final Comparator<ArgType> reversedComparator;

    /**
     * 构造类型比较器
     *
     * @param root 根节点，用于类继承关系查询
     */
    public TypeCompare(RootNode root) {
        this.root = root;
        this.comparator = new ArgTypeComparator();
        this.reversedComparator = comparator.reversed();
    }

    /**
     * 比较两个类节点的类型
     *
     * @param first 第一个类节点
     * @param second 第二个类节点
     * @return 类型比较结果
     */
    public TypeCompareEnum compareTypes(ClassNode first, ClassNode second) {
        return compareObjects(first.getType(), second.getType());
    }

    /**
     * 比较两个类信息的类型
     *
     * @param first 第一个类信息
     * @param second 第二个类信息
     * @return 类型比较结果
     */
    public TypeCompareEnum compareTypes(ClassInfo first, ClassInfo second) {
        return compareObjects(first.getType(), second.getType());
    }

    /**
     * 比较两个对象类型 (带预检查)
     *
     * @param first 第一个类型
     * @param second 第二个类型
     * @return 类型比较结果
     */
    public TypeCompareEnum compareObjects(ArgType first, ArgType second) {
        if (first == second || Objects.equals(first, second)) {
            return TypeCompareEnum.EQUAL;
        }
        return compareObjectsNoPreCheck(first, second);
    }

    /**
     * 比较两个类型并返回第一个参数的比较结果 (更窄、更宽或冲突)
     */
    public TypeCompareEnum compareTypes(ArgType first, ArgType second) {
        if (first == second || Objects.equals(first, second)) {
            return TypeCompareEnum.EQUAL;
        }
        boolean firstKnown = first.isTypeKnown();
        boolean secondKnown = second.isTypeKnown();
        if (firstKnown != secondKnown) {
            if (firstKnown) {
                return compareWithUnknown(first, second);
            } else {
                return compareWithUnknown(second, first).invert();
            }
        }
        boolean firstArray = first.isArray();
        boolean secondArray = second.isArray();
        if (firstArray != secondArray) {
            if (firstArray) {
                return compareArrayWithOtherType(first, second);
            } else {
                return compareArrayWithOtherType(second, first).invert();
            }
        }
        if (firstArray /* && secondArray */) {
            // 两个都是数组类型，比较数组元素类型
            return compareTypes(first.getArrayElement(), second.getArrayElement());
        }
        if (!firstKnown /* && !secondKnown */) {
            int variantLen = Integer.compare(first.getPossibleTypes().length, second.getPossibleTypes().length);
            return variantLen > 0 ? WIDER : NARROW;
        }
        boolean firstPrimitive = first.isPrimitive();
        boolean secondPrimitive = second.isPrimitive();

        boolean firstObj = first.isObject();
        boolean secondObj = second.isObject();
        if (firstObj && secondObj) {
            return compareObjectsNoPreCheck(first, second);
        } else {
            // 基本类型与对象类型冲突
            if (firstObj && secondPrimitive) {
                return CONFLICT;
            }
            if (firstPrimitive && secondObj) {
                return CONFLICT;
            }
        }
        if (firstPrimitive && secondPrimitive) {
            return comparePrimitives(first.getPrimitiveType(), second.getPrimitiveType());
        }

        LOG.warn("Type compare function not complete, can't compare {} and {}", first, second);
        return TypeCompareEnum.CONFLICT;
    }

    /**
     * 比较数组类型与其他类型
     *
     * @param array 数组类型
     * @param other 其他类型
     * @return 类型比较结果
     */
    private TypeCompareEnum compareArrayWithOtherType(ArgType array, ArgType other) {
        if (!other.isTypeKnown()) {
            if (other.contains(PrimitiveType.ARRAY)) {
                return NARROW;
            }
            return CONFLICT;
        }
        if (other.isObject()) {
            if (other.equals(ArgType.OBJECT)) {
                return NARROW;
            }
            return CONFLICT;
        }
        if (other.isPrimitive()) {
            return CONFLICT;
        }
        throw new JadxRuntimeException("Unprocessed type: " + other + " in array compare");
    }

    /**
     * 将已知类型与未知类型进行比较
     *
     * @param known 已知类型
     * @param unknown 未知类型
     * @return 类型比较结果
     */
    private TypeCompareEnum compareWithUnknown(ArgType known, ArgType unknown) {
        if (unknown == ArgType.UNKNOWN) {
            return NARROW;
        }
        if (unknown == ArgType.UNKNOWN_OBJECT && (known.isObject() || known.isArray())) {
            return NARROW;
        }
        if (known.equals(ArgType.OBJECT) && unknown.isArray()) {
            return WIDER;
        }
        PrimitiveType knownPrimitive;
        if (known.isPrimitive()) {
            knownPrimitive = known.getPrimitiveType();
        } else if (known.isArray()) {
            knownPrimitive = PrimitiveType.ARRAY;
        } else {
            knownPrimitive = PrimitiveType.OBJECT;
        }
        PrimitiveType[] possibleTypes = unknown.getPossibleTypes();
        for (PrimitiveType possibleType : possibleTypes) {
            if (possibleType == knownPrimitive) {
                return NARROW;
            }
        }
        return CONFLICT;
    }

    /**
     * 比较两个对象类型 (不带预检查)
     *
     * @param first 第一个类型
     * @param second 第二个类型
     * @return 类型比较结果
     */
    private TypeCompareEnum compareObjectsNoPreCheck(ArgType first, ArgType second) {
        boolean objectsEquals = first.getObject().equals(second.getObject());
        boolean firstGenericType = first.isGenericType();
        boolean secondGenericType = second.isGenericType();
        if (firstGenericType && secondGenericType && !objectsEquals) {
            return CONFLICT;
        }
        boolean firstGeneric = first.isGeneric();
        boolean secondGeneric = second.isGeneric();

        if (firstGenericType || secondGenericType) {
            ArgType firstWildcardType = first.getWildcardType();
            ArgType secondWildcardType = second.getWildcardType();
            if (firstWildcardType != null || secondWildcardType != null) {
                if (firstWildcardType != null && secondGenericType && first.getWildcardBound() == WildcardBound.UNBOUND) {
                    return CONFLICT;
                }
                if (firstGenericType && secondWildcardType != null && second.getWildcardBound() == WildcardBound.UNBOUND) {
                    return CONFLICT;
                }
            }
            if (firstGenericType) {
                return compareGenericTypeWithObject(first, second);
            } else {
                return compareGenericTypeWithObject(second, first).invert();
            }
        }
        if (objectsEquals) {
            if (firstGeneric != secondGeneric) {
                return firstGeneric ? NARROW_BY_GENERIC : WIDER_BY_GENERIC;
            }
            // 同一对象上两个都是泛型
            if (first.getWildcardBound() != null && second.getWildcardBound() != null) {
                // 两个都是通配符
                return compareWildcardTypes(first, second);
            }
            List<ArgType> firstGenericTypes = first.getGenericTypes();
            List<ArgType> secondGenericTypes = second.getGenericTypes();
            if (isEmpty(firstGenericTypes) || isEmpty(secondGenericTypes)) {
                // 检查外部类型
                ArgType firstOuterType = first.getOuterType();
                ArgType secondOuterType = second.getOuterType();
                if (firstOuterType != null && secondOuterType != null) {
                    return compareTypes(firstOuterType, secondOuterType);
                }
            } else {
                // 比较泛型数组
                int len = firstGenericTypes.size();
                if (len == secondGenericTypes.size()) {
                    for (int i = 0; i < len; i++) {
                        TypeCompareEnum res = compareTypes(firstGenericTypes.get(i), secondGenericTypes.get(i));
                        if (res != EQUAL) {
                            return res;
                        }
                    }
                }
            }
        }
        boolean firstIsObjCls = first.equals(ArgType.OBJECT);
        if (firstIsObjCls || second.equals(ArgType.OBJECT)) {
            return firstIsObjCls ? WIDER : NARROW;
        }
        if (ArgType.isInstanceOf(root, first, second)) {
            return NARROW;
        }
        if (ArgType.isInstanceOf(root, second, first)) {
            return WIDER;
        }
        if (!ArgType.isClsKnown(root, first) || !ArgType.isClsKnown(root, second)) {
            return UNKNOWN;
        }
        return TypeCompareEnum.CONFLICT;
    }

    /**
     * 比较两个通配符类型
     *
     * @param first 第一个通配符类型
     * @param second 第二个通配符类型
     * @return 类型比较结果
     */
    private TypeCompareEnum compareWildcardTypes(ArgType first, ArgType second) {
        WildcardBound firstWildcardBound = first.getWildcardBound();
        WildcardBound secondWildcardBound = second.getWildcardBound();
        if (firstWildcardBound == WildcardBound.UNBOUND) {
            return WIDER;
        }
        if (secondWildcardBound == WildcardBound.UNBOUND) {
            return NARROW;
        }
        TypeCompareEnum wildcardCompare = compareTypes(first.getWildcardType(), second.getWildcardType());
        if (firstWildcardBound == secondWildcardBound) {
            return wildcardCompare;
        }
        return CONFLICT;
    }

    /**
     * 比较泛型类型与普通对象类型
     *
     * @param genericType 泛型类型 (类型变量)
     * @param objType 对象类型
     * @return 类型比较结果
     */
    private TypeCompareEnum compareGenericTypeWithObject(ArgType genericType, ArgType objType) {
        if (objType.isGenericType()) {
            return compareTypeVariables(genericType, objType);
        }
        if (objType.isWildcard()) {
            return CONFLICT_BY_GENERIC;
        }
        boolean rootObject = objType.equals(ArgType.OBJECT);
        List<ArgType> extendTypes = genericType.getExtendTypes();
        if (extendTypes.isEmpty()) {
            return rootObject ? NARROW : CONFLICT;
        }
        if (extendTypes.contains(objType) || rootObject) {
            return NARROW;
        }
        for (ArgType extendType : extendTypes) {
            TypeCompareEnum res = compareObjectsNoPreCheck(extendType, objType);
            if (!res.isNarrow()) {
                return res;
            }
        }
        return NARROW;
    }

    /**
     * 比较两个类型变量 (泛型类型)，依据其上界 (extends 类型)判断宽窄关系
     *
     * @param first 第一个类型变量
     * @param second 第二个类型变量
     * @return 类型比较结果
     */
    private TypeCompareEnum compareTypeVariables(ArgType first, ArgType second) {
        if (first.getObject().equals(second.getObject())) {
            List<ArgType> firstExtendTypes = removeObject(first.getExtendTypes());
            List<ArgType> secondExtendTypes = removeObject(second.getExtendTypes());
            if (firstExtendTypes.equals(secondExtendTypes)) {
                return EQUAL;
            }
            int firstExtSize = firstExtendTypes.size();
            int secondExtSize = secondExtendTypes.size();
            if (firstExtSize == 0) {
                return WIDER;
            }
            if (secondExtSize == 0) {
                return NARROW;
            }
            if (firstExtSize == 1 && secondExtSize == 1) {
                return compareTypes(firstExtendTypes.get(0), secondExtendTypes.get(0));
            }
        }
        return CONFLICT;
    }

    /**
     * 从类型变量的上界列表中移除 Object 类型 (Object 上界不提供额外约束信息)
     *
     * @param extendTypes 上界类型列表
     * @return 移除 Object 后的上界列表
     */
    private List<ArgType> removeObject(List<ArgType> extendTypes) {
        if (extendTypes.contains(ArgType.OBJECT)) {
            if (extendTypes.size() == 1) {
                return Collections.emptyList();
            }
            List<ArgType> result = new ArrayList<>(extendTypes);
            result.remove(ArgType.OBJECT);
            return result;
        }
        return extendTypes;
    }

    /**
     * 比较两个基本类型的宽窄关系 (依据类型宽度，例如 int 比 byte 更宽)
     *
     * @param type1 第一个基本类型
     * @param type2 第二个基本类型
     * @return 类型比较结果
     */
    private TypeCompareEnum comparePrimitives(PrimitiveType type1, PrimitiveType type2) {
        if (type1 == PrimitiveType.BOOLEAN || type2 == PrimitiveType.BOOLEAN) {
            return type1 == type2 ? EQUAL : CONFLICT;
        }

        if (type1 == PrimitiveType.VOID || type2 == PrimitiveType.VOID) {
            return type1 == type2 ? EQUAL : CONFLICT;
        }

        if (type1 == PrimitiveType.BYTE && type2 == PrimitiveType.CHAR) {
            return WIDER;
        }

        if (type1 == PrimitiveType.SHORT && type2 == PrimitiveType.CHAR) {
            return WIDER;
        }

        int type1Width = getTypeWidth(type1);
        int type2Width = getTypeWidth(type2);
        if (type1Width > type2Width) {
            return WIDER;
        } else if (type1Width < type2Width) {
            return NARROW;
        } else {
            return EQUAL;
        }
    }

    /**
     * 返回基本类型对应的宽度序号，用于宽窄比较 (BYTE 最小，DOUBLE 最大)
     * 对于 BOOLEAN、OBJECT、ARRAY、VOID 等不参与宽窄比较的类型抛出异常
     *
     * @param type 基本类型
     * @return 类型宽度序号
     */
    private byte getTypeWidth(PrimitiveType type) {
        switch (type) {
            case BYTE:
                return 0;
            case SHORT:
                return 1;
            case CHAR:
                return 2;
            case INT:
                return 3;
            case LONG:
                return 4;
            case FLOAT:
                return 5;
            case DOUBLE:
                return 6;
            case BOOLEAN:
            case OBJECT:
            case ARRAY:
            case VOID:
                throw new JadxRuntimeException("Type " + type + " should not be here");
        }

        throw new JadxRuntimeException("Unhandled type: " + type);
    }

    /**
     * 返回正向类型比较器
     *
     * @return 正向 {@link ArgType} 比较器
     */
    public Comparator<ArgType> getComparator() {
        return comparator;
    }

    /**
     * 返回反向类型比较器
     *
     * @return 反向 {@link ArgType} 比较器
     */
    public Comparator<ArgType> getReversedComparator() {
        return reversedComparator;
    }

    /**
     * 基于 {@link #compareTypes(ArgType, ArgType)} 结果实现的 {@link ArgType} 比较器
     * 冲突返回 -2，更宽返回 -1，更窄返回 1，相等返回 0
     */
    private final class ArgTypeComparator implements Comparator<ArgType> {
        @Override
        public int compare(ArgType a, ArgType b) {
            TypeCompareEnum result = compareTypes(a, b);
            switch (result) {
                case CONFLICT:
                    return -2;

                case WIDER:
                case WIDER_BY_GENERIC:
                    return -1;

                case NARROW:
                case NARROW_BY_GENERIC:
                    return 1;

                case EQUAL:
                default:
                    return 0;
            }
        }
    }
}
