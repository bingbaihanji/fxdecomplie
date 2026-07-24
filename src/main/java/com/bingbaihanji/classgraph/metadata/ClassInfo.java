 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.bytecode.ClassParser.ClassContainment;
import com.bingbaihanji.classgraph.bytecode.ClassParser.ClassTypeAnnotationDecorator;
import com.bingbaihanji.classgraph.classpath.Classpath;
import com.bingbaihanji.classgraph.classpath.ModuleClasspath;
import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.*;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.LogNode;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;

/** 保存在扫描过程中遇到的类的元数据 */
public class ClassInfo extends MetadataNode implements Comparable<ClassInfo>, Named {
    /** 注解的修饰符位 */
    static final int ANNOTATION_CLASS_MODIFIER = 0x2000;

    // -------------------------------------------------------------------------------------------------------------
    // 核心属性
    /** 类层次结构关系图（超类、子类、接口、注解关系） */
    private final ClassHierarchy hierarchy = new ClassHierarchy(this);
    /** 类成员查询（字段、方法及其覆盖顺序） */
    private final ClassMembers members = new ClassMembers(this);
    /** 发现该类的类路径元素 */
    public transient Classpath Classpath;
    /** 获取该类的类加载器 */
    public transient ClassLoader classLoader;
    /** 类模块的信息 */
    public ModuleInfo moduleInfo;
    /** 包含该类的包的信息 */
    public PackageInfo packageInfo;
    /** 类注解信息，包括可选的注解参数值 */
    public AnnotationInfoList annotationInfo;
    /** 字段信息 */
    public FieldInfoList fieldInfo;
    /** 方法信息 */
    public MethodInfoList methodInfo;
    /** 类的名称 */
    protected String name;
    /** 类类型签名字符串 */
    protected String typeSignatureStr;
    /**
     * 如果为 true，则该类仅作为超类 / 已实现接口 / 注解被其他类的 class 文件引用，
     * 但该类本身不是已接受（未被拒绝）的类，也不在已接受（未被拒绝）的包中
     *
     * 如果为 false，则该 class 文件在扫描过程中已匹配（即已读取其 class 文件内容），
     * 即该类是已接受（且未被拒绝）的包中的已接受（且未被拒绝）的类
     */
    protected boolean isExternalClass = true;
    /**
     * 当类实际被扫描到时设置为 true（与仅仅作为超类、接口或注解被引用的类相区别）
     */
    protected boolean isScannedClass;
    /** 该类 class 文件对应的 {@link Resource} */
    protected transient Resource classfileResource;
    /**
     * 此注解具有 {@link Inherited} 元注解，这意味着任何被该注解标注的类，
     * 也会隐式地将该注解应用于所有子类
     */
    boolean isInherited;
    /** 对于注解类，参数的默认值 */
    AnnotationParameterValueList annotationDefaultParamValues;
    /** {@link ClassType} 实例的类型注解装饰器 */
    transient List<ClassTypeAnnotationDecorator> typeAnnotationDecorators;
    /**
     * 当 annotationDefaultParamValues 中的任何 Object[] 装箱类型数组已被延迟转换为原始数组时设置为 true
     */
    transient boolean annotationDefaultParamValuesHasBeenConvertedToPrimitive;
    /** 类修饰符标志，例如 Modifier.PUBLIC */
    private int modifiers;
    /** 如果该类是记录类则为 true */
    private boolean isRecord;
    /** 该类 class 文件的 class 文件格式次版本号 */
    private int classfileMinorVersion;
    /** 该类 class 文件的 class 文件格式主版本号 */
    private int classfileMajorVersion;
    /** 已解析的类类型签名 */
    private transient com.bingbaihanji.classgraph.type.ClassType typeSignature;
    /** 合成的类类型描述符 */
    private transient com.bingbaihanji.classgraph.type.ClassType typeDescriptor;
    /** 编译该类的源文件名 */
    private String sourceFile;
    /** 匿名内部类的完全限定定义方法名 */
    private String fullyQualifiedDefiningMethodName;
    /**
     * 该类在 class 文件常量池中的类引用和类型签名中所引用的类名称
     */
    private Set<String> referencedClassNames;

    // -------------------------------------------------------------------------------------------------------------
    // 委派字段
    /**
     * 该类所引用的类的 ClassInfo 对象列表由 {@link #referencedClassNames} 在相关
     * {@link ClassInfo} 对象创建时派生而来
     */
    private ClassInfoList referencedClasses;
    /** 完整路径（用于 ClassGraphWorkspaceAdapter 简化扫描） */
    private String fullPath;
    /** 注解信息列表，加载后的缓存 */
    private AnnotationInfoList annotationInfoRef;

    // -------------------------------------------------------------------------------------------------------------
    // 构造函数

    /** 反序列化用的默认构造函数 */
    ClassInfo() {
        super();
    }

    /**
     * 为 ClassGraphWorkspaceAdapter 提供的简化构造函数
     *
     * @param name           类名（内部形式，例如 "java/lang/String"）
     * @param classModifiers 类修饰符
     */
    public ClassInfo(final String name, final int classModifiers) {
        super();
        this.name = name;
        if (name.endsWith(";")) {
            throw new IllegalArgumentException("Bad class name");
        }
        setModifiers(classModifiers);
    }

    /**
     * 构造函数
     *
     * @param name               名称
     * @param classModifiers     类修饰符
     * @param classfileResource  类文件资源
     */
    @SuppressWarnings("null")
    protected ClassInfo(final String name, final int classModifiers, final Resource classfileResource) {
        super();
        this.name = name;
        if (name.endsWith(";")) {
            // 抽查以确保类名是从描述符中正确解析的
            throw new IllegalArgumentException("Bad class name");
        }
        setModifiers(classModifiers);
        this.classfileResource = classfileResource;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 委派访问器

    /**
     * 获取 ClassInfo 对象，如果不存在则创建注意：非线程安全，因此 ClassInfo 对象
     * 应该仅由单个线程构造
     *
     * @param className            类名
     * @param classNameToClassInfo 从类名到 ClassInfo 的映射
     * @return {@link ClassInfo} 对象
     */
    public static ClassInfo getOrCreateClassInfo(final String className,
                                                 final Map<String, ClassInfo> classNameToClassInfo) {
        // 查找数组类名
        int numArrayDims = 0;
        String baseClassName = className;
        while (baseClassName.endsWith("[]")) {
            numArrayDims++;
            baseClassName = baseClassName.substring(0, baseClassName.length() - 2);
        }
        // 容忍使用类描述符而非类名的情况（本不应需要）
        while (baseClassName.startsWith("[")) {
            numArrayDims++;
            baseClassName = baseClassName.substring(1);
        }
        if (baseClassName.endsWith(";")) {
            baseClassName = baseClassName.substring(0, baseClassName.length() - 1);
        }
        baseClassName = baseClassName.replace('/', '.');

        ClassInfo classInfo = classNameToClassInfo.get(className);
        if (classInfo == null) {
            if (numArrayDims == 0) {
                classInfo = new ClassInfo(baseClassName, /* classModifiers = */ 0, /* classfileResource = */ null);
            } else {
                final StringBuilder arrayTypeSigStrBuf = new StringBuilder();
                for (int i = 0; i < numArrayDims; i++) {
                    arrayTypeSigStrBuf.append('[');
                }
                TypeSignature elementTypeSignature;
                final char baseTypeChar = BaseType.getTypeChar(baseClassName);
                if (baseTypeChar != '\0') {
                    // 元素类型是基本（原始）类型
                    arrayTypeSigStrBuf.append(baseTypeChar);
                    elementTypeSignature = new BaseType(baseTypeChar);
                } else {
                    // 元素类型不是基本（原始）类型——为元素类型创建类型签名
                    final String eltTypeSigStr = "L" + baseClassName.replace('.', '/') + ";";
                    arrayTypeSigStrBuf.append(eltTypeSigStr);
                    try {
                        elementTypeSignature = ClassRef.parse(new TypeParser(eltTypeSigStr),
                                // 泛型类型无需解析类型变量
                                /* definingClassName = */ null);
                        if (elementTypeSignature == null) {
                            throw new IllegalArgumentException(
                                    "Could not form array base type signature for class " + baseClassName);
                        }
                    } catch (final ParseException e) {
                        throw new IllegalArgumentException(
                                "Could not form array base type signature for class " + baseClassName);
                    }
                }
                classInfo = new ArrayClassInfo(
                        new ArrayType(elementTypeSignature, numArrayDims, arrayTypeSigStrBuf.toString()));
            }
            classNameToClassInfo.put(className, classInfo);
        }
        return classInfo;
    }

    /**
     * 添加类包含关系信息
     *
     * @param classContainmentEntries 类包含条目列表
     * @param classNameToClassInfo    从类名到 ClassInfo 的映射
     */
    public static void addClassContainment(final List<ClassContainment> classContainmentEntries,
                                           final Map<String, ClassInfo> classNameToClassInfo) {
        for (final ClassContainment classContainment : classContainmentEntries) {
            final ClassInfo innerClassInfo = ClassInfo.getOrCreateClassInfo(classContainment.innerClassName,
                    classNameToClassInfo);
            innerClassInfo.setModifiers(classContainment.innerClassModifierBits);
            final ClassInfo outerClassInfo = ClassInfo.getOrCreateClassInfo(classContainment.outerClassName,
                    classNameToClassInfo);
            innerClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.CONTAINED_WITHIN_OUTER_CLASS, outerClassInfo);
            outerClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.CONTAINS_INNER_CLASS, innerClassInfo);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 静态工厂方法

    /**
     * 添加一个刚刚被扫描到的类（与仅仅被已扫描类引用的类相区别）非线程安全，
     * 应在单线程上下文中运行
     *
     * @param className            类名
     * @param classModifiers       类修饰符
     * @param isExternalClass      如果是外部类则为 true
     * @param classNameToClassInfo 从类名到 ClassInfo 的映射
     * @param Classpath            类路径元素
     * @param classfileResource    类文件资源
     * @return ClassInfo 对象
     */
    public static ClassInfo addScannedClass(final String className, final int classModifiers,
                                            final boolean isExternalClass, final Map<String, ClassInfo> classNameToClassInfo,
                                            final Classpath Classpath, final Resource classfileResource) {
        ClassInfo classInfo = classNameToClassInfo.get(className);
        if (classInfo == null) {
            // 这是第一次遇到该类，添加它
            classNameToClassInfo.put(className,
                    classInfo = new ClassInfo(className, classModifiers, classfileResource));
        } else {
            // 之前已存在一个占位 ClassInfo 对象，因为该类被作为超类、接口或注解引用
            // 在这种情况下 isScannedClass 字段应为 false，因为在此之前尚未遇到实际的类定义
            if (classInfo.isScannedClass) {
                // 由于类路径屏蔽机制，该类不应被多次扫描
                throw new IllegalArgumentException("Class " + className
                        + " should not have been encountered more than once due to classpath masking --"
                        + " please report this bug at: https://github.com/classgraph/classgraph/issues");
            }

            // 为占位类设置 classfileResource
            classInfo.classfileResource = classfileResource;

            // 添加任何额外的修饰符位
            classInfo.modifiers |= classModifiers;
        }

        // 将类标记为已扫描
        classInfo.isScannedClass = true;

        // 如果该类是已接受的类，则将其标记为非外部类
        classInfo.isExternalClass = isExternalClass;

        // 记住该类是在哪个类路径元素（zip 文件 / 类路径根目录 / 模块）中发现的
        classInfo.Classpath = Classpath;

        // 记住用于加载该类的类加载器
        classInfo.classLoader = Classpath.getClassLoader();

        return classInfo;
    }

    /**
     * 根据扫描规范和类类型过滤类
     *
     * @param classes      类集合
     * @param ScanConfig   扫描规范
     * @param strictAccept 如果为 true，则当外部类未启用时排除外部类
     * @param classTypes   类类型数组
     * @return 过滤后的类集合
     */
    static Set<ClassInfo> filterClassInfo(final Collection<ClassInfo> classes, final ScanConfig ScanConfig,
                                          final boolean strictAccept, final ClassType... classTypes) {
        if (classes == null) {
            return Collections.emptySet();
        }
        boolean includeAllTypes = classTypes.length == 0;
        boolean includeStandardClasses = false;
        boolean includeImplementedInterfaces = false;
        boolean includeAnnotations = false;
        boolean includeEnums = false;
        boolean includeRecords = false;
        for (final ClassType classType : classTypes) {
            switch (classType) {
                case ALL:
                    includeAllTypes = true;
                    break;
                case STANDARD_CLASS:
                    includeStandardClasses = true;
                    break;
                case IMPLEMENTED_INTERFACE:
                    includeImplementedInterfaces = true;
                    break;
                case ANNOTATION:
                    includeAnnotations = true;
                    break;
                case INTERFACE_OR_ANNOTATION:
                    includeImplementedInterfaces = includeAnnotations = true;
                    break;
                case ENUM:
                    includeEnums = true;
                    break;
                case RECORD:
                    includeRecords = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown ClassType: " + classType);
            }
        }
        if (includeStandardClasses && includeImplementedInterfaces && includeAnnotations) {
            includeAllTypes = true;
        }
        final Set<ClassInfo> classInfoSetFiltered = new LinkedHashSet<>(classes.size());
        for (final ClassInfo classInfo : classes) {
            // 根据请求的类型检查类类型
            final boolean includeType = includeAllTypes //
                    || includeStandardClasses && classInfo.isStandardClass() //
                    || includeImplementedInterfaces && classInfo.isImplementedInterface() //
                    || includeAnnotations && classInfo.isAnnotation() //
                    || includeEnums && classInfo.isEnum() //
                    || includeRecords && classInfo.isRecord();
            // 如果查看类层次结构"向上"，则返回外部（非接受的）类
            final boolean acceptClass = !classInfo.isExternalClass || ScanConfig.enableExternalClasses
                    || !strictAccept;
            // 如果类是正确的类型，且类被接受，且类/包未被显式拒绝
            if (includeType && acceptClass && !ScanConfig.classOrPackageIsRejected(classInfo.name)) {
                // 类通过了接受条件
                classInfoSetFiltered.add(classInfo);
            }
        }
        return classInfoSetFiltered;
    }

    /**
     * 获取扫描过程中发现的所有类
     */
    public static ClassInfoList getAllClasses(final Collection<ClassInfo> classes, final ScanConfig ScanConfig) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true, ClassType.ALL),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有 {@link Enum} 枚举类
     */
    public static ClassInfoList getAllEnums(final Collection<ClassInfo> classes, final ScanConfig ScanConfig) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true, ClassType.ENUM),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有 {@code record} 记录类
     */
    public static ClassInfoList getAllRecords(final Collection<ClassInfo> classes, final ScanConfig ScanConfig) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true, ClassType.RECORD),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有标准类
     */
    public static ClassInfoList getAllStandardClasses(final Collection<ClassInfo> classes, final ScanConfig ScanConfig) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true, ClassType.STANDARD_CLASS),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有已实现接口（非注解接口）类
     */
    public static ClassInfoList getAllImplementedInterfaceClasses(final Collection<ClassInfo> classes,
                                                                  final ScanConfig ScanConfig) {
        return new ClassInfoList(ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true,
                ClassType.IMPLEMENTED_INTERFACE), /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有注解类
     */
    public static ClassInfoList getAllAnnotationClasses(final Collection<ClassInfo> classes, final ScanConfig ScanConfig) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true, ClassType.ANNOTATION),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有接口或注解类
     */
    public static ClassInfoList getAllInterfacesOrAnnotationClasses(final Collection<ClassInfo> classes,
                                                                    final ScanConfig ScanConfig) {
        return new ClassInfoList(ClassInfo.filterClassInfo(classes, ScanConfig, /* strictAccept = */ true,
                ClassType.INTERFACE_OR_ANNOTATION), /* sortByName = */ true);
    }

    /**
     * 从完全限定类名获取简单名称
     */
    public static String getSimpleName(final String className) {
        return className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    }

    /**
     * 获取类层次结构关系图
     *
     * @return ClassHierarchy 实例
     */
    public ClassHierarchy hierarchy() {
        return hierarchy;
    }

    /**
     * 获取类成员查询器
     *
     * @return ClassMembers 实例
     */
    public ClassMembers members() {
        return members;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 构建器方法（内部使用，操作 hierarchy）

    /**
     * 向该类添加超类
     */
    public void addSuperclass(final String superclassName, final Map<String, ClassInfo> classNameToClassInfo) {
        if (superclassName != null && !"java.lang.Object".equals(superclassName)) {
            final ClassInfo superclassClassInfo = getOrCreateClassInfo(superclassName, classNameToClassInfo);
            this.hierarchy.addRelation(ClassHierarchy.RelType.SUPERCLASSES, superclassClassInfo);
            superclassClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.SUBCLASSES, this);
        }
    }

    /**
     * 向该类添加已实现的接口
     */
    public void addImplementedInterface(final String interfaceName, final Map<String, ClassInfo> classNameToClassInfo) {
        final ClassInfo interfaceClassInfo = getOrCreateClassInfo(interfaceName, classNameToClassInfo);
        interfaceClassInfo.setIsInterface(true);
        this.hierarchy.addRelation(ClassHierarchy.RelType.IMPLEMENTED_INTERFACES, interfaceClassInfo);
        interfaceClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.CLASSES_IMPLEMENTING, this);
    }

    /**
     * 添加包含方法名，用于匿名内部类
     */
    public void addFullyQualifiedDefiningMethodName(final String fullyQualifiedDefiningMethodName) {
        this.fullyQualifiedDefiningMethodName = fullyQualifiedDefiningMethodName;
    }

    /**
     * 向该类添加注解
     */
    public void addClassAnnotation(final AnnotationInfo classAnnotationInfo,
                                   final Map<String, ClassInfo> classNameToClassInfo) {
        final ClassInfo annotationClassInfo = getOrCreateClassInfo(classAnnotationInfo.getName(),
                classNameToClassInfo);
        annotationClassInfo.setModifiers(ANNOTATION_CLASS_MODIFIER);
        if (this.annotationInfo == null) {
            this.annotationInfo = new AnnotationInfoList(2);
        }
        this.annotationInfo.add(classAnnotationInfo);

        this.hierarchy.addRelation(ClassHierarchy.RelType.CLASS_ANNOTATIONS, annotationClassInfo);
        annotationClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.CLASSES_WITH_ANNOTATION, this);

        // 记录 @Inherited 元注解的使用
        if (classAnnotationInfo.getName().equals(Inherited.class.getName())) {
            annotationClassInfo.isInherited = true;
        }
    }

    /**
     * 添加字段信息
     */
    public void addFieldInfo(final FieldInfoList fieldInfoList, final Map<String, ClassInfo> classNameToClassInfo) {
        for (final FieldInfo fi : fieldInfoList) {
            // 索引字段注解
            hierarchy.addFieldOrMethodAnnotationInfo(fi.annotationInfo, /* isField = */ true, fi.getModifiers(),
                    classNameToClassInfo);
        }
        if (this.fieldInfo == null) {
            this.fieldInfo = fieldInfoList;
        } else {
            this.fieldInfo.addAll(fieldInfoList);
        }
    }

    /**
     * 添加方法信息
     */
    public void addMethodInfo(final MethodInfoList methodInfoList, final Map<String, ClassInfo> classNameToClassInfo) {
        for (final MethodInfo mi : methodInfoList) {
            // 索引方法注解
            hierarchy.addFieldOrMethodAnnotationInfo(mi.annotationInfo, /* isField = */ false, mi.getModifiers(),
                    classNameToClassInfo);

            // 索引方法参数注解
            if (mi.parameterAnnotationInfo != null) {
                for (int i = 0; i < mi.parameterAnnotationInfo.length; i++) {
                    final AnnotationInfo[] paramAnnotationInfoArr = mi.parameterAnnotationInfo[i];
                    if (paramAnnotationInfoArr != null) {
                        for (final AnnotationInfo methodParamAnnotationInfo : paramAnnotationInfoArr) {
                            final ClassInfo annotationClassInfo = getOrCreateClassInfo(
                                    methodParamAnnotationInfo.getName(), classNameToClassInfo);
                            annotationClassInfo.setModifiers(ANNOTATION_CLASS_MODIFIER);
                            this.hierarchy.addRelation(ClassHierarchy.RelType.METHOD_PARAMETER_ANNOTATIONS, annotationClassInfo);
                            annotationClassInfo.hierarchy().addRelation(ClassHierarchy.RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,
                                    this);
                            // 对于非私有方法/字段，也添加到非私有（可继承）映射中
                            if (!Modifier.isPrivate(mi.getModifiers())) {
                                annotationClassInfo.hierarchy().addRelation(
                                        ClassHierarchy.RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION, this);
                            }
                        }
                    }
                }
            }
        }
        if (this.methodInfo == null) {
            this.methodInfo = methodInfoList;
        } else {
            this.methodInfo.addAll(methodInfoList);
        }
    }

    /**
     * 添加注解默认值
     */
    public void addAnnotationParamDefaultValues(final AnnotationParameterValueList paramNamesAndValues) {
        setIsAnnotation(true);
        if (this.annotationDefaultParamValues == null) {
            this.annotationDefaultParamValues = paramNamesAndValues;
        } else {
            this.annotationDefaultParamValues.addAll(paramNamesAndValues);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 基本 getter / setter

    @Override
    public String getName() {
        return name;
    }

    public String getSimpleName() {
        return getSimpleName(name);
    }

    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    public String getPackageName() {
        return PackageInfo.getParentPackageName(name);
    }

    public boolean isExternalClass() {
        return isExternalClass;
    }

    public void setExternalClass(final boolean externalClass) {
        this.isExternalClass = externalClass;
    }

    public void setScannedClass(final boolean scannedClass) {
        this.isScannedClass = scannedClass;
    }

    public String getFullPath() {
        return fullPath;
    }

    public void setFullPath(final String fullPath) {
        this.fullPath = fullPath;
    }

    public int getClassfileMinorVersion() {
        return classfileMinorVersion;
    }

    public int getClassfileMajorVersion() {
        return classfileMajorVersion;
    }

    public int getModifiers() {
        return modifiers;
    }

    public void setModifiers(final int modifiers) {
        this.modifiers |= modifiers;
    }

    public String getModifiersStr() {
        final StringBuilder buf = new StringBuilder();
        TypeUtils.modifiersToString(modifiers, TypeUtils.ModifierType.CLASS, /* ignored */ false, buf);
        return buf.toString();
    }

    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    public boolean isPrivate() {
        return Modifier.isPrivate(modifiers);
    }

    public boolean isProtected() {
        return Modifier.isProtected(modifiers);
    }

    public boolean isPackageVisible() {
        return !isPublic() && !isPrivate() && !isProtected();
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(modifiers);
    }

    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    public boolean isAnnotation() {
        return (modifiers & ANNOTATION_CLASS_MODIFIER) != 0;
    }

    public boolean isInterface() {
        return isInterfaceOrAnnotation() && !isAnnotation();
    }

    public boolean isInterfaceOrAnnotation() {
        return (modifiers & Modifier.INTERFACE) != 0;
    }

    public boolean isEnum() {
        return (modifiers & 0x4000) != 0;
    }

    public boolean isRecord() {
        return isRecord;
    }

    public boolean isStandardClass() {
        return !(isAnnotation() || isInterface());
    }

    public boolean isArrayClass() {
        return this instanceof ArrayClassInfo;
    }

    public void setClassfileVersion(final int minorVersion, final int majorVersion) {
        this.classfileMinorVersion = minorVersion;
        this.classfileMajorVersion = majorVersion;
    }

    public void setIsInterface(final boolean isInterface) {
        if (isInterface) {
            this.modifiers |= Modifier.INTERFACE;
        }
    }

    public void setIsAnnotation(final boolean isAnnotation) {
        if (isAnnotation) {
            this.modifiers |= ANNOTATION_CLASS_MODIFIER;
        }
    }

    public void setIsRecord(final boolean isRecord) {
        if (isRecord) {
            this.isRecord = isRecord;
        }
    }

    public void addTypeDecorators(final List<ClassTypeAnnotationDecorator> classTypeAnnotationDecorators) {
        if (typeAnnotationDecorators == null) {
            typeAnnotationDecorators = new ArrayList<>();
        }
        typeAnnotationDecorators.addAll(classTypeAnnotationDecorators);
    }

    public boolean extendsSuperclass(final Class<?> superclass) {
        return extendsSuperclass(superclass.getName());
    }

    public boolean extendsSuperclass(final String superclassName) {
        return ("java.lang.Object".equals(superclassName) && isStandardClass())
                || hierarchy().getSuperclasses().containsName(superclassName);
    }

    public boolean isInnerClass() {
        return !hierarchy().getOuterClasses().isEmpty();
    }

    public boolean isOuterClass() {
        return !hierarchy().getInnerClasses().isEmpty();
    }

    public boolean isAnonymousInnerClass() {
        return fullyQualifiedDefiningMethodName != null;
    }

    public boolean isImplementedInterface() {
        return hierarchy.getRelatedClasses().get(ClassHierarchy.RelType.CLASSES_IMPLEMENTING) != null || isInterface();
    }

    public boolean implementsInterface(final Class<?> interfaceClazz) {
        Assert.isInterface(interfaceClazz);
        return implementsInterface(interfaceClazz.getName());
    }

    public boolean implementsInterface(final String interfaceName) {
        return hierarchy().getInterfaces().containsName(interfaceName);
    }

    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    public boolean hasAnnotation(final String annotationName) {
        return hierarchy().getAnnotations().containsName(annotationName);
    }

    public String getFullyQualifiedDefiningMethodName() {
        return fullyQualifiedDefiningMethodName;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 适配器桥接方法

    public void addSubclass(final ClassInfo subclass) {
        if (subclass != null) {
            this.hierarchy.addRelation(ClassHierarchy.RelType.SUBCLASSES, subclass);
            subclass.hierarchy().addRelation(ClassHierarchy.RelType.SUPERCLASSES, this);
        }
    }

    public void addImplementingClass(final ClassInfo implementingClass) {
        if (implementingClass != null) {
            this.hierarchy.addRelation(ClassHierarchy.RelType.CLASSES_IMPLEMENTING, implementingClass);
            implementingClass.hierarchy().addRelation(ClassHierarchy.RelType.IMPLEMENTED_INTERFACES, this);
        }
    }

    public ClassInfoList getSubclasses() {
        return hierarchy.getSubclasses();
    }

    public ClassInfoList getSuperclasses() {
        return hierarchy.getSuperclasses();
    }

    public ClassInfo getSuperclass() {
        return hierarchy.getSuperclass();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 委派方法 —— 类层次结构

    public void setSuperclass(final ClassInfo superclass) {
        if (superclass != null) {
            this.hierarchy.addRelation(ClassHierarchy.RelType.SUPERCLASSES, superclass);
            superclass.hierarchy().addRelation(ClassHierarchy.RelType.SUBCLASSES, this);
        }
    }

    public ClassInfoList getOuterClasses() {
        return hierarchy.getOuterClasses();
    }

    public ClassInfoList getInnerClasses() {
        return hierarchy.getInnerClasses();
    }

    public ClassInfoList getInterfaces() {
        return hierarchy.getInterfaces();
    }

    public void setInterfaces(final ClassInfoList interfaces) {
        if (interfaces != null) {
            for (final ClassInfo iface : interfaces) {
                iface.setIsInterface(true);
                this.hierarchy.addRelation(ClassHierarchy.RelType.IMPLEMENTED_INTERFACES, iface);
                iface.hierarchy().addRelation(ClassHierarchy.RelType.CLASSES_IMPLEMENTING, this);
            }
        }
    }

    public ClassInfoList getClassesImplementing() {
        return hierarchy.getClassesImplementing();
    }

    public ClassInfoList getAnnotations() {
        return hierarchy.getAnnotations();
    }

    public ClassInfoList getClassesWithAnnotation() {
        return hierarchy.getClassesWithAnnotation();
    }

    public ClassInfoList getClassesWithAnnotationDirectOnly() {
        return hierarchy.getClassesWithAnnotationDirectOnly();
    }

    public ClassInfoList getFieldAnnotations() {
        return hierarchy.getFieldAnnotations();
    }

    public ClassInfoList getMethodAnnotations() {
        return hierarchy.getMethodAnnotations();
    }

    public ClassInfoList getMethodParameterAnnotations() {
        return hierarchy.getMethodParameterAnnotations();
    }

    public ClassInfoList getClassesWithFieldAnnotation() {
        return hierarchy.getClassesWithFieldAnnotation();
    }

    public ClassInfoList getClassesWithFieldAnnotationDirectOnly() {
        return hierarchy.getClassesWithFieldAnnotationDirectOnly();
    }

    public ClassInfoList getClassesWithMethodAnnotation() {
        return hierarchy.getClassesWithMethodAnnotation();
    }

    public ClassInfoList getClassesWithMethodAnnotationDirectOnly() {
        return hierarchy.getClassesWithMethodAnnotationDirectOnly();
    }

    public ClassInfoList getClassesWithMethodParameterAnnotation() {
        return hierarchy.getClassesWithMethodParameterAnnotation();
    }

    public void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames) {
        hierarchy.handleRepeatableAnnotations(allRepeatableAnnotationNames);
    }

    public FieldInfoList getDeclaredFieldInfo() {
        return members.getDeclaredFieldInfo();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 委派方法 —— 类成员

    public FieldInfoList getFieldInfo() {
        return members.getFieldInfo();
    }

    public FieldInfo getDeclaredFieldInfo(final String fieldName) {
        return members.getDeclaredFieldInfo(fieldName);
    }

    public FieldInfo getFieldInfo(final String fieldName) {
        return members.getFieldInfo(fieldName);
    }

    public MethodInfoList getDeclaredMethodInfo() {
        return members.getDeclaredMethodInfo();
    }

    public MethodInfoList getMethodInfo() {
        return members.getMethodInfo();
    }

    public MethodInfoList getDeclaredConstructorInfo() {
        return members.getDeclaredConstructorInfo();
    }

    public MethodInfoList getConstructorInfo() {
        return members.getConstructorInfo();
    }

    public MethodInfoList getDeclaredMethodAndConstructorInfo() {
        return members.getDeclaredMethodAndConstructorInfo();
    }

    public MethodInfoList getMethodAndConstructorInfo() {
        return members.getMethodAndConstructorInfo();
    }

    public MethodInfoList getDeclaredMethodInfo(final String methodName) {
        return members.getDeclaredMethodInfo(methodName);
    }

    public MethodInfoList getMethodInfo(final String methodName) {
        return members.getMethodInfo(methodName);
    }

    public boolean hasDeclaredField(final String fieldName) {
        return members.hasDeclaredField(fieldName);
    }

    public boolean hasField(final String fieldName) {
        return members.hasField(fieldName);
    }

    public boolean hasDeclaredFieldAnnotation(final Class<? extends Annotation> annotation) {
        return members.hasDeclaredFieldAnnotation(annotation);
    }

    public boolean hasDeclaredFieldAnnotation(final String fieldAnnotationName) {
        return members.hasDeclaredFieldAnnotation(fieldAnnotationName);
    }

    public boolean hasFieldAnnotation(final Class<? extends Annotation> fieldAnnotation) {
        return members.hasFieldAnnotation(fieldAnnotation);
    }

    public boolean hasFieldAnnotation(final String fieldAnnotationName) {
        return members.hasFieldAnnotation(fieldAnnotationName);
    }

    public boolean hasDeclaredMethod(final String methodName) {
        return members.hasDeclaredMethod(methodName);
    }

    public boolean hasMethod(final String methodName) {
        return members.hasMethod(methodName);
    }

    public boolean hasDeclaredMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        return members.hasDeclaredMethodAnnotation(methodAnnotation);
    }

    public boolean hasDeclaredMethodAnnotation(final String methodAnnotationName) {
        return members.hasDeclaredMethodAnnotation(methodAnnotationName);
    }

    public boolean hasMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        return members.hasMethodAnnotation(methodAnnotation);
    }

    public boolean hasMethodAnnotation(final String methodAnnotationName) {
        return members.hasMethodAnnotation(methodAnnotationName);
    }

    public boolean hasDeclaredMethodParameterAnnotation(
            final Class<? extends Annotation> methodParameterAnnotation) {
        return members.hasDeclaredMethodParameterAnnotation(methodParameterAnnotation);
    }

    public boolean hasDeclaredMethodParameterAnnotation(final String methodParameterAnnotationName) {
        return members.hasDeclaredMethodParameterAnnotation(methodParameterAnnotationName);
    }

    public boolean hasMethodParameterAnnotation(final Class<? extends Annotation> methodParameterAnnotation) {
        return members.hasMethodParameterAnnotation(methodParameterAnnotation);
    }

    public boolean hasMethodParameterAnnotation(final String methodParameterAnnotationName) {
        return members.hasMethodParameterAnnotation(methodParameterAnnotationName);
    }

    public FieldInfoList getEnumConstants() {
        return members.getEnumConstants();
    }

    public List<Object> getEnumConstantObjects() {
        return members.getEnumConstantObjects();
    }

    public AnnotationInfoList getAnnotationInfo() {
        synchronized (this) {
            if (annotationInfoRef != null) {
                return annotationInfoRef;
            }

            if (!scanResult.ScanConfig.enableAnnotationInfo) {
                throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
            }

            annotationInfoRef = AnnotationInfoList.getIndirectAnnotations(annotationInfo, this);
            return annotationInfoRef;
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 注解信息（保留在 ClassInfo 中，属于核心属性查询）

    public void setAnnotationInfo(final AnnotationInfoList annotationInfo) {
        this.annotationInfo = annotationInfo;
    }

    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        for (final AnnotationInfo ai : getAnnotationInfo()) {
            if (ai.getName().equals(annotationName)) {
                return ai;
            }
        }
        return null;
    }

    public AnnotationInfoList getAnnotationInfoRepeatable(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfoRepeatable(annotation.getName());
    }

    public AnnotationInfoList getAnnotationInfoRepeatable(final String annotationName) {
        return getAnnotationInfo().getRepeatable(annotationName);
    }

    public AnnotationParameterValueList getAnnotationDefaultParameterValues() {
        if (!scanResult.ScanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
        }
        if (!isAnnotation()) {
            throw new IllegalArgumentException("Class is not an annotation: " + getName());
        }
        synchronized (this) {
            if (annotationDefaultParamValues == null) {
                return AnnotationParameterValueList.EMPTY_LIST;
            }
            if (!annotationDefaultParamValuesHasBeenConvertedToPrimitive) {
                annotationDefaultParamValues.convertWrapperArraysToPrimitiveArrays(this);
                annotationDefaultParamValuesHasBeenConvertedToPrimitive = true;
            }
            return annotationDefaultParamValues;
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类型签名

    public com.bingbaihanji.classgraph.type.ClassType getTypeSignature() {
        synchronized (this) {
            if (typeSignatureStr == null) {
                return null;
            }
            if (typeSignature == null) {
                try {
                    typeSignature = com.bingbaihanji.classgraph.type.ClassType.parse(typeSignatureStr, this);
                    typeSignature.setScanResult(scanResult);
                    if (typeAnnotationDecorators != null) {
                        for (final ClassTypeAnnotationDecorator decorator : typeAnnotationDecorators) {
                            decorator.decorate(typeSignature);
                        }
                    }
                } catch (final ParseException e) {
                    throw new IllegalArgumentException("Invalid type signature for class " + getName()
                            + " in classpath element " + getClasspathURI() + " : " + typeSignatureStr, e);
                }
            }
        }
        return typeSignature;
    }

    public void setTypeSignature(final String typeSignatureStr) {
        this.typeSignatureStr = typeSignatureStr;
    }

    public String getTypeSignatureStr() {
        return typeSignatureStr;
    }

    public com.bingbaihanji.classgraph.type.ClassType getTypeSignatureOrTypeDescriptor() {
        com.bingbaihanji.classgraph.type.ClassType typeSig = null;
        try {
            typeSig = getTypeSignature();
            if (typeSig != null) {
                return typeSig;
            }
        } catch (final Exception e) {
            // 忽略
        }
        return getTypeDescriptor();
    }

    public com.bingbaihanji.classgraph.type.ClassType getTypeDescriptor() {
        synchronized (this) {
            if (typeDescriptor == null) {
                typeDescriptor = new com.bingbaihanji.classgraph.type.ClassType(this, hierarchy().getSuperclass(), hierarchy().getInterfaces());
                typeDescriptor.setScanResult(scanResult);
                if (typeAnnotationDecorators != null) {
                    for (final ClassTypeAnnotationDecorator decorator : typeAnnotationDecorators) {
                        decorator.decorate(typeDescriptor);
                    }
                }
            }
        }
        return typeDescriptor;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 源文件 / 类路径

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(final String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public URI getClasspathURI() {
        if (classfileResource == null) {
            throw new IllegalArgumentException("ClassParser resource is null for " + getName());
        }
        return classfileResource.getClasspathURI();
    }

    public URL getClasspathURL() {
        try {
            return getClasspathURI().toURL();
        } catch (final IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("Could not get classpath element URL", e);
        }
    }

    public File getClasspathFile() {
        if (Classpath == null) {
            throw new IllegalArgumentException("Classpath element is not known for this classpath element");
        }
        return Classpath.getFile();
    }

    public ModuleRef getModuleRef() {
        if (Classpath == null) {
            throw new IllegalArgumentException("Classpath element is not known for this classpath element");
        }
        return Classpath instanceof ModuleClasspath
                ? ((ModuleClasspath) Classpath).getModuleRef()
                : null;
    }

    public Resource getResource() {
        return classfileResource;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类加载

    @Override
    public <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType, final boolean ignoreExceptions) {
        return super.loadClass(superclassOrInterfaceType, ignoreExceptions);
    }

    @Override
    public <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType) {
        return super.loadClass(superclassOrInterfaceType, /* ignoreExceptions = */ false);
    }

    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        return super.loadClass(ignoreExceptions);
    }

    @Override
    public Class<?> loadClass() {
        return super.loadClass(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------
    // MetadataNode 覆写

    @Override
    public String getClassName() {
        return name;
    }

    @Override
    public ClassInfo getClassInfo() {
        return this;
    }

    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
        if (annotationInfo != null) {
            for (final AnnotationInfo ai : annotationInfo) {
                ai.setScanResult(scanResult);
            }
        }
        if (fieldInfo != null) {
            for (final FieldInfo fi : fieldInfo) {
                fi.setScanResult(scanResult);
            }
        }
        if (methodInfo != null) {
            for (final MethodInfo mi : methodInfo) {
                mi.setScanResult(scanResult);
            }
        }
        if (annotationDefaultParamValues != null) {
            for (final AnnotationParameterValue apv : annotationDefaultParamValues) {
                apv.setScanResult(scanResult);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 引用类

    public void addReferencedClassNames(final Set<String> refdClassNames) {
        if (this.referencedClassNames == null) {
            this.referencedClassNames = refdClassNames;
        } else {
            this.referencedClassNames.addAll(refdClassNames);
        }
    }

    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        // 将此类添加到引用集合中
        super.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        if (this.referencedClassNames != null) {
            for (final String refdClassName : this.referencedClassNames) {
                final ClassInfo classInfo = ClassInfo.getOrCreateClassInfo(refdClassName, classNameToClassInfo);
                classInfo.setScanResult(scanResult);
                refdClassInfo.add(classInfo);
            }
        }
        members().getMethodInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        members().getFieldInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        getAnnotationInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        if (annotationDefaultParamValues != null) {
            annotationDefaultParamValues.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
        try {
            final com.bingbaihanji.classgraph.type.ClassType classSig = getTypeSignature();
            if (classSig != null) {
                classSig.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("Illegal type signature for class " + getClassName() + ": " + getTypeSignatureStr());
            }
        }
    }

    public void setReferencedClasses(final ClassInfoList refdClasses) {
        this.referencedClasses = refdClasses;
    }

    public ClassInfoList getClassDependencies() {
        if (!scanResult.ScanConfig.enableInterClassDependencies) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableInterClassDependencies() before #scan()");
        }
        return referencedClasses == null ? ClassInfoList.EMPTY_LIST : referencedClasses;
    }

    // -------------------------------------------------------------------------------------------------------------
    // equals / hashCode / compareTo / toString

    @Override
    public int compareTo(final ClassInfo o) {
        return this.name.compareTo(o.name);
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof ClassInfo)) {
            return false;
        }
        final ClassInfo other = (ClassInfo) obj;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name == null ? 0 : name.hashCode();
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        final boolean initialBufEmpty = buf.length() == 0;
        if (annotationInfo != null) {
            for (final AnnotationInfo annotation : annotationInfo) {
                if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' '
                        && buf.charAt(buf.length() - 1) != '(') {
                    buf.append(' ');
                }
                annotation.toString(useSimpleNames, buf);
            }
        }
        com.bingbaihanji.classgraph.type.ClassType typeSig = null;
        try {
            typeSig = getTypeSignature();
        } catch (final Exception e) {
            // 忽略
        }
        if (typeSig != null) {
            // 泛型类
            typeSig.toStringInternal(useSimpleNames ? ClassInfo.getSimpleName(name) : name,
                    /* useSimpleNames = */ false, modifiers, isAnnotation(), isInterface(),
                    /* annotationsToExclude = */ annotationInfo, buf);
        } else {
            // 非泛型类
            TypeUtils.modifiersToString(modifiers, TypeUtils.ModifierType.CLASS, /* ignored */ false, buf);
            if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' ' && buf.charAt(buf.length() - 1) != '(') {
                buf.append(' ');
            }
            // 不将类类型放在 extends/implements 子句中
            if (initialBufEmpty) {
                buf.append(isRecord() ? "record " //
                        : isEnum() ? "enum " //
                        : isAnnotation() ? "@interface " //
                        : isInterface() ? "interface " //
                        : "class ");
            }
            buf.append(useSimpleNames ? ClassInfo.getSimpleName(name) : name);
            if (isRecord) {
                // 如果这是记录类，添加参数
                buf.append('(');
                boolean isFirstParam = true;
                for (final FieldInfo fieldInfo : members().getFieldInfo()) {
                    if (!isFirstParam) {
                        buf.append(", ");
                    } else {
                        isFirstParam = false;
                    }
                    fieldInfo.toString(/* useModifiers = */ false, /* useSimpleNames = */ false, buf);
                }
                buf.append(')');
            }
            final ClassInfo superclass = hierarchy().getSuperclass();
            if (superclass != null && !"java.lang.Object".equals(superclass.getName())) {
                buf.append(" extends ");
                superclass.toString(useSimpleNames, buf);
            }
            final Set<ClassInfo> interfaces = this.hierarchy.filterClassInfo(ClassHierarchy.RelType.IMPLEMENTED_INTERFACES,
                    /* strictAccept = */ false).directlyRelatedClasses;
            if (!interfaces.isEmpty()) {
                buf.append(isInterface() ? " extends " : " implements ");
                boolean first = true;
                for (final ClassInfo iface : interfaces) {
                    if (first) {
                        first = false;
                    } else {
                        buf.append(", ");
                    }
                    iface.toString(useSimpleNames, buf);
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 内部类型

    /** 要返回的类类型 */
    public enum ClassType {
        /** 获取所有类类型 */
        ALL,
        /** 标准类（非接口或注解） */
        STANDARD_CLASS,
        /**
         * 接口（此处命名为"已实现接口"而非简单的"接口"，以区别于注解）
         */
        IMPLEMENTED_INTERFACE,
        /** 注解 */
        ANNOTATION,
        /** 接口或注解（因为实际上可以实现一个注解） */
        INTERFACE_OR_ANNOTATION,
        /** 枚举 */
        ENUM,
        /** 记录类型 */
        RECORD
    }
}
