 
package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypePathNode;
import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfoList;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.TypeUtils.ModifierType;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.*;

/** 类类型签名(在 ClassParser 文档中称为"ClassSignature") */
public final class ClassType extends HierarchicalType {

    /** 类类型参数 */
    final List<TypeParam> TypeParams;
    /** 类信息 */
    private final ClassInfo classInfo;
    /** 超类类型 */
    private final ClassRef superclassSignature;

    /** 超接口签名 */
    private final List<ClassRef> superinterfaceSignatures;

    /**
     * throws 签名(通常为 null)这些仅出现在 Scala 类中，当类标记了 {@code @throws} 时出现，
     * 它们违反了 ClassParser 规范(#495)，但我们仍然解析它们
     */
    private final List<TypeRef> throwsSignatures;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param classInfo
     *            类的 {@link ClassInfo} 对象
     * @param TypeParams
     *            类类型参数
     * @param superclassSignature
     *            超类签名
     * @param superinterfaceSignatures
     *            超接口签名
     * @param throwsSignatures
     *            throws 签名(这些实际上是无效的，但可能由 Scala 添加：#495)通常为 null
     */
    public ClassType(final ClassInfo classInfo, final List<TypeParam> TypeParams,
                     final ClassRef superclassSignature,
                     final List<ClassRef> superinterfaceSignatures,
                     final List<TypeRef> throwsSignatures) {
        super();
        this.classInfo = classInfo;
        this.TypeParams = TypeParams;
        this.superclassSignature = superclassSignature;
        this.superinterfaceSignatures = superinterfaceSignatures;
        this.throwsSignatures = throwsSignatures;
    }

    /**
     * 用于创建合成类类型描述符的构造函数(#662)
     *
     * @param classInfo
     *            类
     * @param superclass
     *            超类
     * @param interfaces
     *            实现的接口
     */
    public ClassType(final ClassInfo classInfo, final ClassInfo superclass, final ClassInfoList interfaces) {
        super();
        this.classInfo = classInfo;
        this.TypeParams = Collections.emptyList();
        ClassRef superclassSignature = null;
        try {
            superclassSignature = superclass == null ? null
                    : (ClassRef) TypeSignature
                    .parse("L" + superclass.getName().replace('.', '/') + ";", classInfo.getName());
        } catch (final ParseException e) {
            // 静默失败(不应发生)
        }
        this.superclassSignature = superclassSignature;
        this.superinterfaceSignatures = interfaces == null || interfaces.isEmpty()
                ? Collections.<ClassRef>emptyList()
                : new ArrayList<ClassRef>(interfaces.size());
        if (interfaces != null) {
            for (final ClassInfo iface : interfaces) {
                try {
                    final ClassRef ifaceSignature = (ClassRef) TypeSignature
                            .parse("L" + iface.getName().replace('.', '/') + ";", classInfo.getName());
                    this.superinterfaceSignatures.add(ifaceSignature);
                } catch (final ParseException e) {
                    // 静默失败(不应发生)
                }
            }
        }
        this.throwsSignatures = null;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 解析类类型签名或类类型描述符
     *
     * @param typeDescriptor
     *            要解析的类类型签名或类类型描述符
     * @param classInfo
     *            类信息
     * @return 解析后的类类型签名或类类型描述符
     * @throws ParseException
     *             如果类类型签名无法解析
     */
    public static ClassType parse(final String typeDescriptor, final ClassInfo classInfo) throws ParseException {
        final TypeParser TypeParser = new TypeParser(typeDescriptor);
        // 定义类名用于使用定义类的类型描述符解析类型变量
        // 但这里我们正在解析定义类的类型描述符，所以它不可能包含指向自身的变量 => 直接使用 null 作为定义类名
        final String definingClassNameNull = null;
        final List<TypeParam> TypeParams = TypeParam.parseList(TypeParser, definingClassNameNull);
        final ClassRef superclassSignature = ClassRef.parse(TypeParser,
                definingClassNameNull);
        List<ClassRef> superinterfaceSignatures;
        if (TypeParser.hasMore()) {
            superinterfaceSignatures = new ArrayList<>();
            while (TypeParser.hasMore()) {
                if (TypeParser.peek() == '^') {
                    // 类类型签名中存在非法的 "throws" 后缀 -- 穿透处理
                    break;
                }
                final ClassRef superinterfaceSignature = ClassRef.parse(TypeParser,
                        definingClassNameNull);
                if (superinterfaceSignature == null) {
                    throw new ParseException(TypeParser, "Could not parse superinterface signature");
                }
                superinterfaceSignatures.add(superinterfaceSignature);
            }
        } else {
            superinterfaceSignatures = Collections.emptyList();
        }
        List<TypeRef> throwsSignatures;
        if (TypeParser.peek() == '^') {
            // 此类型签名的末尾存在非法的 "throws" 后缀
            // Scala 在将某个类标记为 "@throws" 时会添加这些后缀(#495)
            // 具有此类类型签名的类会被 javac 和 javap 拒绝，如果在子类上调用
            // getClass().getGenericSuperclass() 会抛出 GenericSignatureFormatError
            // 但由于类型擦除，JVM 会忽略类型签名，而 Scala 似乎依赖这一点 --
            // 或者至少 Scala 团队从未注意到这个问题，因为这些类在纯 Scala 环境中可以正常运行
            // 由于此问题在 Scala 领域可能广泛存在，因此接受这些无效的类型签名并实际解析出
            // 所有 "throws" 后缀，比抛出异常并拒绝解析类型签名更好
            throwsSignatures = new ArrayList<>();
            while (TypeParser.peek() == '^') {
                TypeParser.expect('^');
                final ClassRef ClassType = ClassRef.parse(TypeParser,
                        classInfo.getName());
                if (ClassType != null) {
                    throwsSignatures.add(ClassType);
                } else {
                    final TypeVar typeVar = (TypeVar) TypeVar.parse(TypeParser,
                            classInfo.getName());
                    if (typeVar != null) {
                        throwsSignatures.add(typeVar);
                    } else {
                        throw new ParseException(TypeParser, "Missing type variable signature");
                    }
                }
            }
        } else {
            throwsSignatures = null;
        }
        if (TypeParser.hasMore()) {
            throw new ParseException(TypeParser, "Extra characters at end of type descriptor");
        }
        return new ClassType(classInfo, TypeParams, superclassSignature, superinterfaceSignatures,
                throwsSignatures);
    }

    /**
     * 获取类的类型参数
     *
     * @return 类的类型参数
     */
    public List<TypeParam> getTypeParams() {
        return TypeParams;
    }

    /**
     * 获取超类的类型签名(对于 {@link Object} 可能为 null，因为它没有超类)
     *
     * @return 超类的类型签名，如果无超类则返回 null(即对于 {@link Object})
     */
    public ClassRef getSuperclassSignature() {
        return superclassSignature;
    }

    /**
     * 获取所有超接口的类型签名
     *
     * @return 所有超接口的类型签名
     */
    public List<ClassRef> getSuperinterfaceSignatures() {
        return superinterfaceSignatures;
    }

    /**
     * 获取 throws 签名根据 ClassParser 规范，这些是无效的(因此此方法目前是非公开的)，
     * 但可能由 Scala 编译器添加(参见 bug #495)
     *
     * @return throws 签名
     */
    List<TypeRef> getThrowsSignatures() {
        return throwsSignatures;
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    public void addTypeAnnotation(final List<TypePathNode> typePath, final AnnotationInfo annotationInfo) {
        // 类类型的各个部分各自具有其自己的 addTypeAnnotation 方法
        throw new IllegalArgumentException(
                "Cannot call this method on " + ClassType.class.getSimpleName());
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    public String getClassName() {
        return classInfo != null ? classInfo.getName() : null;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassInfo()
     */
    @Override
    public ClassInfo getClassInfo() {
        return classInfo;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (TypeParams != null) {
            for (final TypeParam TypeParam : TypeParams) {
                TypeParam.setScanResult(scanResult);
            }
        }
        if (this.superclassSignature != null) {
            this.superclassSignature.setScanResult(scanResult);
        }
        if (superinterfaceSignatures != null) {
            for (final ClassRef ClassRef : superinterfaceSignatures) {
                ClassRef.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型签名中引用的所有类名
     *
     * @param refdClassNames
     *            被引用的类名集合
     */
    public void findReferencedClassNames(final Set<String> refdClassNames) {
        for (final TypeParam TypeParam : TypeParams) {
            TypeParam.findReferencedClassNames(refdClassNames);
        }
        if (superclassSignature != null) {
            superclassSignature.findReferencedClassNames(refdClassNames);
        }
        if (superinterfaceSignatures != null) {
            for (final ClassRef typeSignature : superinterfaceSignatures) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
        if (throwsSignatures != null) {
            for (final TypeRef typeSignature : throwsSignatures) {
                typeSignature.findReferencedClassNames(refdClassNames);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型描述符或类型签名中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息集合
     */
    @Override
    public void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                        final Set<ClassInfo> refdClassInfo, final LogNode log) {
        final Set<String> refdClassNames = new HashSet<>();
        findReferencedClassNames(refdClassNames);
        for (final String refdClassName : refdClassNames) {
            final ClassInfo clsInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
            clsInfo.scanResult = scanResult;
            refdClassInfo.add(clsInfo);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return TypeParams.hashCode() + (superclassSignature == null ? 1 : superclassSignature.hashCode()) * 7
                + (superinterfaceSignatures == null ? 1 : superinterfaceSignatures.hashCode()) * 15;
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ClassType)) {
            return false;
        }
        final ClassType o = (ClassType) obj;
        return Objects.equals(o.TypeParams, this.TypeParams)
                && Objects.equals(o.superclassSignature, this.superclassSignature)
                && Objects.equals(o.superinterfaceSignatures, this.superinterfaceSignatures);
    }

    /**
     * 渲染为字符串形式
     *
     * @param className
     *            类名
     * @param useSimpleNames
     *            是否使用简单名称
     * @param modifiers
     *            类修饰符
     * @param isAnnotation
     *            如果类是注解则为 true
     * @param isInterface
     *            如果类是接口则为 true
     * @param annotationsToExclude
     *            要排除的注解
     * @param buf
     *            字符串构建器
     */
    public void toStringInternal(final String className, final boolean useSimpleNames, final int modifiers,
                                 final boolean isAnnotation, final boolean isInterface, final AnnotationInfoList annotationsToExclude,
                                 final StringBuilder buf) {
        if (throwsSignatures != null) {
            for (final TypeRef throwsSignature : throwsSignatures) {
                if (buf.length() > 0) {
                    buf.append(' ');
                }
                buf.append("@throws(").append(throwsSignature).append(")");
            }
        }
        if (modifiers != 0) {
            if (buf.length() > 0) {
                buf.append(' ');
            }
            TypeUtils.modifiersToString(modifiers, ModifierType.CLASS, /* ignored */ false, buf);
        }
        if (buf.length() > 0) {
            buf.append(' ');
        }
        buf.append(isAnnotation ? "@interface"
                : isInterface ? "interface" : (modifiers & 0x4000) != 0 ? "enum" : "class");
        buf.append(' ');
        if (className != null) {
            buf.append(useSimpleNames ? ClassInfo.getSimpleName(className) : className);
        }
        if (!TypeParams.isEmpty()) {
            buf.append('<');
            for (int i = 0; i < TypeParams.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                TypeParams.get(i).toStringInternal(useSimpleNames, null, buf);
            }
            buf.append('>');
        }
        if (superclassSignature != null) {
            final StringBuilder sb = new StringBuilder();
            superclassSignature.toString(useSimpleNames, sb);
            final String superSig = sb.toString();
            // 即使超类是 Object，superSig 也可能带有类类型注解
            if (!"java.lang.Object".equals(superSig)
                    && !("Object".equals(superSig) && "java.lang.Object".equals(superclassSignature.className))) {
                buf.append(" extends ");
                buf.append(superSig);
            }
        }
        if (superinterfaceSignatures != null && !superinterfaceSignatures.isEmpty()) {
            buf.append(isInterface ? " extends " : " implements ");
            for (int i = 0; i < superinterfaceSignatures.size(); i++) {
                if (i > 0) {
                    buf.append(", ");
                }
                superinterfaceSignatures.get(i).toStringInternal(useSimpleNames, null, buf);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 内部转换为字符串
     *
     * @param useSimpleNames
     *            是否使用简单名称
     * @param annotationsToExclude
     *            要排除的注解
     * @param buf
     *            字符串构建器
     */
    @Override
    public void toStringInternal(final boolean useSimpleNames, final AnnotationInfoList annotationsToExclude,
                                 final StringBuilder buf) {
        toStringInternal(classInfo.getName(), useSimpleNames, classInfo.getModifiers(), classInfo.isAnnotation(),
                classInfo.isInterface(), annotationsToExclude, buf);
    }
}