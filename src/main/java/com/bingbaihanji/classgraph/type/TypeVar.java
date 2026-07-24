package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypePathNode;
import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.scan.ScanResult;

import java.util.*;

/** 一个类型变量签名 */
public final class TypeVar extends TypeRef {
    /** 类型变量名称 */
    private final String name;

    /** 此类型变量所属类的名称 */
    private final String definingClassName;

    /** 此类型变量所属的方法签名 */
    MethodType containingMethodSignature;

    /** 解析后的类型形参(如果有) */
    private TypeParam TypeParamCached;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param typeVariableName
     *            类型变量名称
     * @param definingClassName
     *            定义类名
     */
    private TypeVar(final String typeVariableName, final String definingClassName) {
        super();
        this.name = typeVariableName;
        this.definingClassName = definingClassName;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析一个 TypeVar
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            定义类名
     * @return 类型变量签名
     * @throws ParseException
     *             如果解析失败
     */
    public static TypeSignature parse(final TypeParser TypeParser, final String definingClassName) throws ParseException {
        final char peek = TypeParser.peek();
        if (peek == 'T') {
            TypeParser.next();
            // Scala 的类型变量名称中可能包含 '$' (#495)
            if (!TypeUtils.getIdentifierToken(TypeParser, /* stopAtDollarSign = */ false, /* stopAtDot = */ true)) {
                throw new ParseException(TypeParser, "Could not parse type variable signature");
            }
            TypeParser.expect(';');
            final TypeVar TypeVar = new TypeVar(TypeParser.currToken(),
                    definingClassName);

            // 将类型变量签名保存在解析器状态中，以便方法和类类型签名可以链接到类型签名
            @SuppressWarnings("unchecked")
            List<TypeVar> TypeVars = (List<TypeVar>) TypeParser.getState();
            if (TypeVars == null) {
                TypeParser.setState(TypeVars = new ArrayList<>());
            }
            TypeVars.add(TypeVar);

            return TypeVar;
        } else {
            return null;
        }
    }

    /**
     * 获取类型变量的名称
     *
     * @return 类型变量名称
     */
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 在定义方法和/或封闭类的类型形参中查找类型变量(例如 "T")，并返回具有相同名称的
     * 类型形参(例如 "T extends com.xyz.Cls")
     *
     * @return 类型形参(例如 "T extends com.xyz.Cls"，或者如果类型形参没有任何边界，则为简单的 "T")
     * @throws IllegalArgumentException
     *             如果在定义方法或封闭类中找不到与类型变量同名的类型形参
     */
    public TypeParam resolve() {
        if (TypeParamCached != null) {
            return TypeParamCached;
        }
        // 尝试根据包含方法解析类型变量
        if (containingMethodSignature != null && containingMethodSignature.TypeParams != null
                && !containingMethodSignature.TypeParams.isEmpty()) {
            for (final TypeParam TypeParam : containingMethodSignature.TypeParams) {
                if (TypeParam.name.equals(this.name)) {
                    TypeParamCached = TypeParam;
                    return TypeParam;
                }
            }
        }
        // 如果失败，尝试根据包含类解析类型变量
        if (getClassName() != null) {
            final ClassInfo containingClassInfo = getClassInfo();
            if (containingClassInfo == null) {
                throw new IllegalArgumentException("Could not find ClassInfo object for " + definingClassName);
            }
            ClassType containingClassSignature = null;
            try {
                containingClassSignature = containingClassInfo.getTypeSignature();
            } catch (final Exception e) {
                // 忽略
            }
            if (containingClassSignature != null && containingClassSignature.TypeParams != null
                    && !containingClassSignature.TypeParams.isEmpty()) {
                for (final TypeParam TypeParam : containingClassSignature.TypeParams) {
                    if (TypeParam.name.equals(this.name)) {
                        TypeParamCached = TypeParam;
                        return TypeParam;
                    }
                }
            }
        }
        // 如果失败，则这是一个无法解析的类型变量
        // 返回一个仅设置了名称的新的 TypeParam，没有类或接口边界(#706)
        final TypeParam TypeParam = new TypeParam(name, null,
                Collections.<ReferenceType>emptyList());
        TypeParam.setScanResult(scanResult);
        TypeParamCached = TypeParam;
        return TypeParam;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        if (typePath.isEmpty()) {
            addTypeAnnotation(annotationInfo);
        } else {
            throw new IllegalArgumentException("Type variable should have empty typePath");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回 definingClassName，以便 getClassInfo() 返回包含类的 {@link ClassInfo} 对象
     *
     * @return 定义类名
     */
    @Override
    public String getClassName() {
        return definingClassName;
    }

    /**
     * 获取类型签名中引用的任何类的名称
     *
     * @param refdClassNames
     *            引用的类名
     */
    @Override
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        // 解析后的类型变量中存在的所有类名都必须存在于封闭方法或类中，
        // 因此不需要在解析后的类型变量中查找类引用
    }

    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (TypeParamCached != null) {
            TypeParamCached.setScanResult(scanResult);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof TypeVar)) {
            return false;
        }
        final TypeVar other = (TypeVar) obj;
        return other.name.equals(this.name) && Objects.equals(other.typeAnnotationInfo, this.typeAnnotationInfo);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.TypeSignature#equalsIgnoringTypeParams(com.bingbaihanji.classgraph.core.TypeSignature)
     */
    @Override
    public boolean equalsIgnoringTypeParams(final TypeSignature other) {
        if (other instanceof ClassRef) {
            if ("java.lang.Object".equals(((ClassRef) other).className)) {
                // java.lang.Object 可以与任何类型协调，因此它可以与任何类型变量协调
                return true;
            }
            // 根据包含类的类型形参解析类型变量
            TypeParam TypeParam;
            try {
                TypeParam = resolve();
            } catch (final IllegalArgumentException e) {
                // 如果无法解析对应的类型形参：
                // 未知类型变量始终可以与具体类协调
                return true;
            }
            if (TypeParam.classBound == null
                    && (TypeParam.interfaceBounds == null || TypeParam.interfaceBounds.isEmpty())) {
                // 如果类型形参没有边界，则仅假定类型变量可以通过类型推断与类协调
                return true;
            }
            if (TypeParam.classBound != null) {
                if (TypeParam.classBound instanceof ClassRef) {
                    if (TypeParam.classBound.equals(other)) {
                        // T extends X，且 X == other
                        return true;
                    }
                } else if (TypeParam.classBound instanceof TypeVar) {
                    // "X" 可以与 "Y extends X" 协调
                    return this.equalsIgnoringTypeParams(TypeParam.classBound);
                } else /* if (TypeParam.classBound instanceof ArrayType) */ {
                    return false;
                }
            }
            if (TypeParam.interfaceBounds != null) {
                for (final ReferenceType interfaceBound : TypeParam.interfaceBounds) {
                    if (interfaceBound instanceof ClassRef) {
                        if (interfaceBound.equals(other)) {
                            // T implements X，且 X == other
                            return true;
                        }
                    } else if (interfaceBound instanceof TypeVar) {
                        // "X" 可以与 "Y implements X" 协调
                        return this.equalsIgnoringTypeParams(interfaceBound);
                    } else /* if (interfaceBound instanceof ArrayType) */ {
                        return false;
                    }
                }
            }
            // 类型变量有一个具体的边界，该边界不能与 'other' 协调
            // (我们不遵循类层次结构将边界与类引用进行比较，
            // 因为编译器在类型擦除期间只应使用边界，而不是类层次结构中的其他类)
            return false;
        }
        // 技术上讲，我认为由于捕获的原因，类型变量永远不会彼此相等，
        // 但这里仅比较变量名称是否相等
        // (通常不应触发此逻辑，因为我们目前只比较类型擦除后的签名与非类型擦除的签名)
        return this.equals(other);
    }

    /**
     * 返回类型变量及其类型边界(如果有)(例如 "X extends xyz.Cls")
     * 你可以通过调用 {@link #resolve()} 来获取结构化形式
     * 如果没有类型边界，或者类型边界未知(即 {@link #resolve()} 返回 null)，则仅返回类型变量
     *
     * @return 字符串表示
     */
    public String toStringWithTypeBound() {
        try {
            return resolve().toString();
        } catch (final IllegalArgumentException e) {
            // 无法解析类型形参
            return name;
        }
    }

    @Override
    public void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                 final StringBuilder buf) {
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                if (annotationsToExclude == null || !annotationsToExclude.contains(annotationInfo)) {
                    annotationInfo.toString(useSimpleNames, buf);
                    buf.append(' ');
                }
            }
        }
        buf.append(name);
    }
}