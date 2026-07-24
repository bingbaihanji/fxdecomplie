 
package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypePathNode;
import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 引用类型或基本类型的类型签名子类包括 {@link ReferenceType}
 * (其子类为 {@link ClassRef}、{@link TypeVar} 和 {@link ArrayType})和
 * {@link BaseType}
 */
public abstract class TypeSignature extends HierarchicalType {
    /** 构造函数 */
    protected TypeSignature() {
        // 空
    }

    /**
     * 解析一个类型签名
     *
     * @param TypeParser
     *            解析器
     * @param definingClass
     *            包含该类型描述符的类
     * @return 解析后的类型描述符或类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    public static TypeSignature parse(final TypeParser TypeParser, final String definingClass) throws ParseException {
        final ReferenceType refType = ReferenceType
                .parseReferenceType(TypeParser, definingClass);
        if (refType != null) {
            return refType;
        }
        final BaseType baseType = BaseType.parse(TypeParser);
        if (baseType != null) {
            return baseType;
        }
        return null;
    }

    /**
     * 解析一个类型签名
     *
     * @param typeDescriptor
     *            要解析的类型描述符或类型签名
     * @param definingClass
     *            包含该类型描述符的类
     * @return 解析后的类型描述符或类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    public static TypeSignature parse(final String typeDescriptor, final String definingClass) throws ParseException {
        final TypeParser TypeParser = new TypeParser(typeDescriptor);
        TypeSignature typeSignature;
        typeSignature = parse(TypeParser, definingClass);
        if (typeSignature == null) {
            throw new ParseException(TypeParser, "Could not parse type signature");
        }
        if (TypeParser.hasMore()) {
            throw new ParseException(TypeParser, "Extra characters at end of type descriptor");
        }
        return typeSignature;
    }

    /**
     * 获取类型签名中引用的任何类的名称
     *
     * @param refdClassNames
     *            引用的类名
     */
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        final String className = getClassName();
        if (className != null && !className.isEmpty()) {
            refdClassNames.add(getClassName());
        }
    }

    /**
     * 获取类型签名中引用的任何类对应的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息
     */
    @Override
    public final void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                              final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final Set<String> refdClassNames = new HashSet<>();
        findReferencedClassNames(refdClassNames);
        for (final String refdClassName : refdClassNames) {
            final ClassInfo classInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
            classInfo.scanResult = scanResult;
            refdClassInfo.add(classInfo);
        }
    }

    /**
     * 获取此类型上的任何类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     *
     * @return 此类型上的任何类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     */
    @Override
    public AnnotationInfoList getTypeAnnotationInfo() {
        return typeAnnotationInfo;
    }

    /**
     * 比较基本类型，忽略泛型类型参数
     *
     * @param other
     *            要比较的另一个 {@link TypeSignature}
     * @return 如果两个 {@link TypeSignature} 对象相等(忽略类型参数)，则返回 true
     */
    public abstract boolean equalsIgnoringTypeParams(final TypeSignature other);

    /**
     * 向此类型添加一个类型注解
     *
     * @param typePath
     *            类型路径
     * @param annotationInfo
     *            要添加的注解
     */
    @Override
    public abstract void addTypeAnnotation(List<TypePathNode> typePath, AnnotationInfo annotationInfo);
}