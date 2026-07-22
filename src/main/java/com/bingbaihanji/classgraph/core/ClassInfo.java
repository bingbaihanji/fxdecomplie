/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.core;

import com.bingbaihanji.classgraph.core.ClassFile.ClassContainment;
import com.bingbaihanji.classgraph.core.ClassFile.ClassTypeAnnotationDecorator;
import com.bingbaihanji.classgraph.core.FieldInfoList.FieldInfoFilter;

import com.bingbaihanji.classgraph.reflection.ReflectionUtils;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.types.ParseException;
import com.bingbaihanji.classgraph.types.Parser;
import com.bingbaihanji.classgraph.types.TypeUtils;
import com.bingbaihanji.classgraph.types.TypeUtils.ModifierType;
import com.bingbaihanji.classgraph.utils.Assert;
import com.bingbaihanji.classgraph.utils.LogNode;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;

/** 保存在扫描过程中遇到的类的元数据 */
public class ClassInfo extends ScanResultObject implements Comparable<ClassInfo>, HasName {
    /** 注解的修饰符位 */
    private static final int ANNOTATION_CLASS_MODIFIER = 0x2000;
    /** 当没有任何类可达时返回的空常量值 */
    private static final ReachableAndDirectlyRelatedClasses NO_REACHABLE_CLASSES = //
            new ReachableAndDirectlyRelatedClasses(Collections.<ClassInfo>emptySet(),
                    Collections.<ClassInfo>emptySet());
    /** 类的名称 */

    protected String name;
    /** 类类型签名字符串 */
    protected String typeSignatureStr;
    /**
     * 如果为 true，则该类仅作为超类 / 已实现接口 / 注解被其他类的 class 文件引用，
     * 但该类本身不是已接受(未被拒绝)的类，也不在已接受(未被拒绝)的包中
     *
     * 如果为 false，则该 class 文件在扫描过程中已匹配(即已读取其 class 文件内容)，
     * 即该类是已接受(且未被拒绝)的包中的已接受(且未被拒绝)的类
     */
    protected boolean isExternalClass = true;
    /**
     * 当类实际被扫描到时设置为 true(与仅仅作为超类、接口或注解被引用的类相区别)
     */
    protected boolean isScannedClass;
    /** 该类 class 文件对应的 {@link Resource} */
    protected transient Resource classfileResource;
    /**
     * 此注解具有 {@link Inherited} 元注解，这意味着任何被该注解标注的类，
     * 也会隐式地将该注解应用于所有子类
     */
    boolean isInherited;
    /** 发现该类的类路径元素 */
    transient ClasspathElement classpathElement;
    /** 获取该类的类加载器 */
    transient ClassLoader classLoader;
    /** 类模块的信息 */
    ModuleInfo moduleInfo;
    /** 包含该类的包的信息 */
    PackageInfo packageInfo;
    /** 类注解信息，包括可选的注解参数值 */
    AnnotationInfoList annotationInfo;
    /** 字段信息 */
    FieldInfoList fieldInfo;
    /** 字段信息 */
    MethodInfoList methodInfo;
    /** 对于注解类，参数的默认值 */
    AnnotationParameterValueList annotationDefaultParamValues;
    /** {@link ClassTypeSignature} 实例的类型注解装饰器 */
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
    private transient ClassTypeSignature typeSignature;
    /** 合成的类类型描述符 */
    private transient ClassTypeSignature typeDescriptor;
    /** 编译该类的源文件名 */
    private String sourceFile;
    /** 匿名内部类的完全限定定义方法名 */
    private String fullyQualifiedDefiningMethodName;
    /**
     * 该类在 class 文件常量池中的类引用和类型签名中所引用的类名称
     */
    private Set<String> referencedClassNames;
    /**
     * 该类所引用的类的 ClassInfo 对象列表由 {@link #referencedClassNames} 在相关
     * {@link ClassInfo} 对象创建时派生而来
     */
    private ClassInfoList referencedClasses;
    /** 与该类相关联的类集合 */
    private Map<RelType, Set<ClassInfo>> relatedClasses;
    /**
     * 类字段或方法的覆盖顺序(基类优先，然后是接口，然后是超类)
     */
    private transient volatile List<ClassInfo> overrideOrder;
    /**
     * 类方法的覆盖顺序(基类优先，然后是超类，然后是接口)
     */
    private transient volatile List<ClassInfo> methodOverrideOrder;

    // -------------------------------------------------------------------------------------------------------------
    /** 注解列表，加载后的缓存 */
    private ClassInfoList annotationsRef;
    /** 注解信息列表，加载后的缓存 */
    private AnnotationInfoList annotationInfoRef;

    // -------------------------------------------------------------------------------------------------------------
    /** 完整路径(用于 ClassGraphWorkspaceAdapter 简化扫描) */
    private String fullPath;

    /** 反序列化用的默认构造函数 */
    ClassInfo() {
        super();
    }

    /**
     * 为 ClassGraphWorkspaceAdapter 提供的简化构造函数
     *
     * @param name
     *            类名(内部形式，例如 "java/lang/String")
     * @param classModifiers
     *            类修饰符
     */
    public ClassInfo(final String name, final int classModifiers) {
        super();
        this.name = name;
        if (name.endsWith(";")) {
            throw new IllegalArgumentException("Bad class name");
        }
        setModifiers(classModifiers);
        this.relatedClasses = new EnumMap<>(RelType.class);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param name
     *            名称
     * @param classModifiers
     *            类修饰符
     * @param classfileResource
     *            类文件资源
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
        this.relatedClasses = new EnumMap<>(RelType.class);
    }

    /**
     * 获取 ClassInfo 对象，如果不存在则创建注意：非线程安全，因此 ClassInfo 对象
     * 应该仅由单个线程构造
     *
     * @param className
     *            类名
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     * @return {@link ClassInfo} 对象
     */
    static ClassInfo getOrCreateClassInfo(final String className,
                                          final Map<String, ClassInfo> classNameToClassInfo) {
        // 查找数组类名
        int numArrayDims = 0;
        String baseClassName = className;
        while (baseClassName.endsWith("[]")) {
            numArrayDims++;
            baseClassName = baseClassName.substring(0, baseClassName.length() - 2);
        }
        // 容忍使用类描述符而非类名的情况(本不应需要)
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
                final char baseTypeChar = BaseTypeSignature.getTypeChar(baseClassName);
                if (baseTypeChar != '\0') {
                    // 元素类型是基本(原始)类型
                    arrayTypeSigStrBuf.append(baseTypeChar);
                    elementTypeSignature = new BaseTypeSignature(baseTypeChar);
                } else {
                    // 元素类型不是基本(原始)类型——为元素类型创建类型签名
                    final String eltTypeSigStr = "L" + baseClassName.replace('.', '/') + ";";
                    arrayTypeSigStrBuf.append(eltTypeSigStr);
                    try {
                        elementTypeSignature = ClassRefTypeSignature.parse(new Parser(eltTypeSigStr),
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
                        new ArrayTypeSignature(elementTypeSignature, numArrayDims, arrayTypeSigStrBuf.toString()));
            }
            classNameToClassInfo.put(className, classInfo);
        }
        return classInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加类包含关系信息
     *
     * @param classContainmentEntries
     *            类包含条目列表
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    static void addClassContainment(final List<ClassContainment> classContainmentEntries,
                                    final Map<String, ClassInfo> classNameToClassInfo) {
        for (final ClassContainment classContainment : classContainmentEntries) {
            final ClassInfo innerClassInfo = ClassInfo.getOrCreateClassInfo(classContainment.innerClassName,
                    classNameToClassInfo);
            innerClassInfo.setModifiers(classContainment.innerClassModifierBits);
            final ClassInfo outerClassInfo = ClassInfo.getOrCreateClassInfo(classContainment.outerClassName,
                    classNameToClassInfo);
            innerClassInfo.addRelatedClass(RelType.CONTAINED_WITHIN_OUTER_CLASS, outerClassInfo);
            outerClassInfo.addRelatedClass(RelType.CONTAINS_INNER_CLASS, innerClassInfo);
        }
    }

    /**
     * 添加一个刚刚被扫描到的类(与仅仅被已扫描类引用的类相区别)非线程安全，
     * 应在单线程上下文中运行
     *
     * @param className
     *            类名
     * @param classModifiers
     *            类修饰符
     * @param isExternalClass
     *            如果是外部类则为 true
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     * @param classpathElement
     *            类路径元素
     * @param classfileResource
     *            类文件资源
     * @return ClassInfo 对象
     */
    static ClassInfo addScannedClass(final String className, final int classModifiers,
                                     final boolean isExternalClass, final Map<String, ClassInfo> classNameToClassInfo,
                                     final ClasspathElement classpathElement, final Resource classfileResource) {
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

        // 记住该类是在哪个类路径元素(zip 文件 / 类路径根目录 / 模块)中发现的
        classInfo.classpathElement = classpathElement;

        // 记住用于加载该类的类加载器
        classInfo.classLoader = classpathElement.getClassLoader();

        return classInfo;
    }

    /**
     * 根据扫描规范和类类型过滤类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @param strictAccept
     *            如果为 true，则当外部类未启用时排除外部类
     * @param classTypes
     *            类类型数组
     * @return 过滤后的类集合
     */
    private static Set<ClassInfo> filterClassInfo(final Collection<ClassInfo> classes, final ScanSpec scanSpec,
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
            // 如果查看类层次结构"向上"，则返回外部(非接受的)类
            final boolean acceptClass = !classInfo.isExternalClass || scanSpec.enableExternalClasses
                    || !strictAccept;
            // 如果类是正确的类型，且类被接受，且类/包未被显式拒绝
            if (includeType && acceptClass && !scanSpec.classOrPackageIsRejected(classInfo.name)) {
                // 类通过了接受条件
                classInfoSetFiltered.add(classInfo);
            }
        }
        return classInfoSetFiltered;
    }

    /**
     * 获取扫描过程中发现的所有类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllClasses(final Collection<ClassInfo> classes, final ScanSpec scanSpec) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true, ClassType.ALL),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有 {@link Enum} 枚举类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有 {@link Enum} 枚举类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllEnums(final Collection<ClassInfo> classes, final ScanSpec scanSpec) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true, ClassType.ENUM),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有 {@code record} 记录类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有 {@code record} 记录类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllRecords(final Collection<ClassInfo> classes, final ScanSpec scanSpec) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true, ClassType.RECORD),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有标准类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有标准类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllStandardClasses(final Collection<ClassInfo> classes, final ScanSpec scanSpec) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true, ClassType.STANDARD_CLASS),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有已实现接口(非注解接口)类
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有注解类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllImplementedInterfaceClasses(final Collection<ClassInfo> classes,
                                                           final ScanSpec scanSpec) {
        return new ClassInfoList(ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true,
                ClassType.IMPLEMENTED_INTERFACE), /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取扫描过程中发现的所有注解类另请参见
     * {@link #getAllInterfacesOrAnnotationClasses(Collection, ScanSpec, ScanResult)}
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有注解类的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllAnnotationClasses(final Collection<ClassInfo> classes, final ScanSpec scanSpec) {
        return new ClassInfoList(
                ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true, ClassType.ANNOTATION),
                /* sortByName = */ true);
    }

    /**
     * 获取扫描过程中发现的所有接口或注解类(注解在技术上是接口，并且可以被实现)
     *
     * @param classes
     *            类集合
     * @param scanSpec
     *            扫描规范
     * @return 扫描过程中发现的所有已接受接口的列表，如果没有则返回空列表
     */
    static ClassInfoList getAllInterfacesOrAnnotationClasses(final Collection<ClassInfo> classes,
                                                             final ScanSpec scanSpec) {
        return new ClassInfoList(ClassInfo.filterClassInfo(classes, scanSpec, /* strictAccept = */ true,
                ClassType.INTERFACE_OR_ANNOTATION), /* sortByName = */ true);
    }

    /**
     * 从完全限定类名获取简单名称返回类名中最后一个 '.' 或最后一个 '$' 之后的部分，
     * 如果类在根包中则返回整个字符串(注意：这与 {@link Class#getSimpleName()} 的结果不同，
     * 后者对于匿名类返回 "")
     *
     * @param className
     *            类名
     * @return 类的简单名称
     */
    static String getSimpleName(final String className) {
        return className.substring(Math.max(className.lastIndexOf('.'), className.lastIndexOf('$')) + 1);
    }

    /**
     * 添加具有给定关系类型的类返回调用是否导致集合发生变化
     *
     * @param relType
     *            {@link RelType} 关系类型
     * @param classInfo
     *            {@link ClassInfo} 对象
     * @return 如果成功添加则返回 true
     */
    boolean addRelatedClass(final RelType relType, final ClassInfo classInfo) {
        Set<ClassInfo> classInfoSet = relatedClasses.get(relType);
        if (classInfoSet == null) {
            relatedClasses.put(relType, classInfoSet = new LinkedHashSet<>(4));
        }
        return classInfoSet.add(classInfo);
    }

    /**
     * 设置 class 文件版本
     *
     * @param minorVersion
     *            该类 class 文件的 class 文件格式次版本号
     * @param majorVersion
     *            该类 class 文件的 class 文件格式主版本号
     */
    void setClassfileVersion(final int minorVersion, final int majorVersion) {
        this.classfileMinorVersion = minorVersion;
        this.classfileMajorVersion = majorVersion;
    }

    /**
     * 设置 isInterface 接口状态
     *
     * @param isInterface
     *            如果为 true 则表示这是接口
     */
    void setIsInterface(final boolean isInterface) {
        if (isInterface) {
            this.modifiers |= Modifier.INTERFACE;
        }
    }

    /**
     * 设置 isAnnotation 注解状态
     *
     * @param isAnnotation
     *            如果为 true 则表示这是注解
     */
    void setIsAnnotation(final boolean isAnnotation) {
        if (isAnnotation) {
            this.modifiers |= ANNOTATION_CLASS_MODIFIER;
        }
    }

    /**
     * 设置 isRecord 记录状态
     *
     * @param isRecord
     *            如果为 true 则表示这是记录类
     */
    void setIsRecord(final boolean isRecord) {
        if (isRecord) {
            this.isRecord = isRecord;
        }
    }

    /**
     * 添加 {@link ClassTypeAnnotationDecorator} 实例
     *
     * @param classTypeAnnotationDecorators
     *            {@link ClassTypeAnnotationDecorator} 实例列表
     */
    void addTypeDecorators(final List<ClassTypeAnnotationDecorator> classTypeAnnotationDecorators) {
        if (typeAnnotationDecorators == null) {
            typeAnnotationDecorators = new ArrayList<>();
        }
        typeAnnotationDecorators.addAll(classTypeAnnotationDecorators);
    }

    /**
     * 向该类添加超类
     *
     * @param superclassName
     *            超类名
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    void addSuperclass(final String superclassName, final Map<String, ClassInfo> classNameToClassInfo) {
        if (superclassName != null && !"java.lang.Object".equals(superclassName)) {
            final ClassInfo superclassClassInfo = getOrCreateClassInfo(superclassName, classNameToClassInfo);
            this.addRelatedClass(RelType.SUPERCLASSES, superclassClassInfo);
            superclassClassInfo.addRelatedClass(RelType.SUBCLASSES, this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 向该类添加已实现的接口
     *
     * @param interfaceName
     *            接口名
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    void addImplementedInterface(final String interfaceName, final Map<String, ClassInfo> classNameToClassInfo) {
        final ClassInfo interfaceClassInfo = getOrCreateClassInfo(interfaceName, classNameToClassInfo);
        interfaceClassInfo.setIsInterface(true);
        this.addRelatedClass(RelType.IMPLEMENTED_INTERFACES, interfaceClassInfo);
        interfaceClassInfo.addRelatedClass(RelType.CLASSES_IMPLEMENTING, this);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加包含方法名，用于匿名内部类
     *
     * @param fullyQualifiedDefiningMethodName
     *            完全限定定义方法名
     */
    void addFullyQualifiedDefiningMethodName(final String fullyQualifiedDefiningMethodName) {
        this.fullyQualifiedDefiningMethodName = fullyQualifiedDefiningMethodName;
    }

    /**
     * 向该类添加注解
     *
     * @param classAnnotationInfo
     *            类注解信息
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    void addClassAnnotation(final AnnotationInfo classAnnotationInfo,
                            final Map<String, ClassInfo> classNameToClassInfo) {
        final ClassInfo annotationClassInfo = getOrCreateClassInfo(classAnnotationInfo.getName(),
                classNameToClassInfo);
        annotationClassInfo.setModifiers(ANNOTATION_CLASS_MODIFIER);
        if (this.annotationInfo == null) {
            this.annotationInfo = new AnnotationInfoList(2);
        }
        this.annotationInfo.add(classAnnotationInfo);

        this.addRelatedClass(RelType.CLASS_ANNOTATIONS, annotationClassInfo);
        annotationClassInfo.addRelatedClass(RelType.CLASSES_WITH_ANNOTATION, this);

        // 记录 @Inherited 元注解的使用
        if (classAnnotationInfo.getName().equals(Inherited.class.getName())) {
            annotationClassInfo.isInherited = true;
        }
    }

    /**
     * 添加字段或方法注解的交叉链接
     *
     * @param annotationInfoList
     *            注解信息列表
     * @param isField
     *            是否为字段
     * @param modifiers
     *            字段或方法修饰符
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    private void addFieldOrMethodAnnotationInfo(final AnnotationInfoList annotationInfoList, final boolean isField,
                                                final int modifiers, final Map<String, ClassInfo> classNameToClassInfo) {
        if (annotationInfoList != null) {
            for (final AnnotationInfo fieldAnnotationInfo : annotationInfoList) {
                final ClassInfo annotationClassInfo = getOrCreateClassInfo(fieldAnnotationInfo.getName(),
                        classNameToClassInfo);
                annotationClassInfo.setModifiers(ANNOTATION_CLASS_MODIFIER);
                // 将此类标记为具有带有此注解的字段或方法
                this.addRelatedClass(isField ? RelType.FIELD_ANNOTATIONS : RelType.METHOD_ANNOTATIONS,
                        annotationClassInfo);
                annotationClassInfo.addRelatedClass(
                        isField ? RelType.CLASSES_WITH_FIELD_ANNOTATION : RelType.CLASSES_WITH_METHOD_ANNOTATION,
                        this);
                // 对于非私有方法/字段，也添加到非私有(可继承)映射中
                if (!Modifier.isPrivate(modifiers)) {
                    annotationClassInfo.addRelatedClass(isField ? RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION
                            : RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION, this);
                }
            }
        }
    }

    /**
     * 添加字段信息
     *
     * @param fieldInfoList
     *            字段信息列表
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    void addFieldInfo(final FieldInfoList fieldInfoList, final Map<String, ClassInfo> classNameToClassInfo) {
        for (final FieldInfo fi : fieldInfoList) {
            // 索引字段注解
            addFieldOrMethodAnnotationInfo(fi.annotationInfo, /* isField = */ true, fi.getModifiers(),
                    classNameToClassInfo);
        }
        if (this.fieldInfo == null) {
            this.fieldInfo = fieldInfoList;
        } else {
            this.fieldInfo.addAll(fieldInfoList);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加方法信息
     *
     * @param methodInfoList
     *            方法信息列表
     * @param classNameToClassInfo
     *            从类名到 ClassInfo 的映射
     */
    void addMethodInfo(final MethodInfoList methodInfoList, final Map<String, ClassInfo> classNameToClassInfo) {
        for (final MethodInfo mi : methodInfoList) {
            // 索引方法注解
            addFieldOrMethodAnnotationInfo(mi.annotationInfo, /* isField = */ false, mi.getModifiers(),
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
                            this.addRelatedClass(RelType.METHOD_PARAMETER_ANNOTATIONS, annotationClassInfo);
                            annotationClassInfo.addRelatedClass(RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,
                                    this);
                            // 对于非私有方法/字段，也添加到非私有(可继承)映射中
                            if (!Modifier.isPrivate(mi.getModifiers())) {
                                annotationClassInfo.addRelatedClass(
                                        RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION, this);
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
     * 添加注解默认值(仅在注解类定义时调用，当注解具有默认参数值时)
     *
     * @param paramNamesAndValues
     *            默认参数名和值，如果这是注解的话
     */
    void addAnnotationParamDefaultValues(final AnnotationParameterValueList paramNamesAndValues) {
        setIsAnnotation(true);
        if (this.annotationDefaultParamValues == null) {
            this.annotationDefaultParamValues = paramNamesAndValues;
        } else {
            this.annotationDefaultParamValues.addAll(paramNamesAndValues);
        }
    }

    /**
     * 获取与给定关系类型相关联的类(传递闭包)以及直接关联的类
     *
     * @param relType
     *            关系类型
     * @param strictAccept
     *            如果为 true，则当外部类未启用时排除外部类
     * @param classTypes
     *            要接受的类类型
     * @return 可达的类和直接关联的类
     */
    private ReachableAndDirectlyRelatedClasses filterClassInfo(final RelType relType, final boolean strictAccept,
                                                               final ClassType... classTypes) {
        Set<ClassInfo> directlyRelatedClasses = this.relatedClasses.get(relType);
        if (directlyRelatedClasses == null) {
            return NO_REACHABLE_CLASSES;
        } else {
            // 克隆集合以防止用户意外或故意修改内容
            directlyRelatedClasses = new LinkedHashSet<>(directlyRelatedClasses);
        }
        final Set<ClassInfo> reachableClasses = new LinkedHashSet<>(directlyRelatedClasses);
        if (relType == RelType.METHOD_ANNOTATIONS || relType == RelType.METHOD_PARAMETER_ANNOTATIONS
                || relType == RelType.FIELD_ANNOTATIONS) {
            // 对于方法和字段注解，在查找元注解时需要更改 RelType
            for (final ClassInfo annotation : directlyRelatedClasses) {
                reachableClasses.addAll(
                        annotation.filterClassInfo(RelType.CLASS_ANNOTATIONS, strictAccept).reachableClasses);
            }
        } else if (relType == RelType.CLASSES_WITH_METHOD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION
                || relType == RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION
                || relType == RelType.CLASSES_WITH_FIELD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION) {
            // 如果查找元注解标注的方法或字段，需要找到所有元注解的注解，然后
            // 查找它们标注的方法或字段
            for (final ClassInfo subAnnotation : this.filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, strictAccept,
                    ClassType.ANNOTATION).reachableClasses) {
                final Set<ClassInfo> annotatedClasses = subAnnotation.relatedClasses.get(relType);
                if (annotatedClasses != null) {
                    reachableClasses.addAll(annotatedClasses);
                }
            }
        } else {
            // 对于其他关系类型，可达类型在传递闭包中保持不变查找
            // 传递闭包，在必要时打破循环
            final LinkedList<ClassInfo> queue = new LinkedList<>(directlyRelatedClasses);
            while (!queue.isEmpty()) {
                final ClassInfo head = queue.removeFirst();
                final Set<ClassInfo> headRelatedClasses = head.relatedClasses.get(relType);
                if (headRelatedClasses != null) {
                    for (final ClassInfo directlyReachableFromHead : headRelatedClasses) {
                        // 避免循环
                        if (reachableClasses.add(directlyReachableFromHead)) {
                            queue.add(directlyReachableFromHead);
                        }
                    }
                }
            }
        }
        if (reachableClasses.isEmpty()) {
            return NO_REACHABLE_CLASSES;
        }

        if (relType == RelType.CLASS_ANNOTATIONS || relType == RelType.METHOD_ANNOTATIONS
                || relType == RelType.METHOD_PARAMETER_ANNOTATIONS || relType == RelType.FIELD_ANNOTATIONS) {
            // 特殊情况——不将 java.lang.annotation.* 元注解作为相关元注解继承
            // (但仍将它们作为注解类上的直接元注解返回)
            Set<ClassInfo> reachableClassesToRemove = null;
            for (final ClassInfo reachableClassInfo : reachableClasses) {
                // 移除所有不直接与此类关联的 java.lang.annotation 注解
                if (reachableClassInfo.getName().startsWith("java.lang.annotation.")
                        && !directlyRelatedClasses.contains(reachableClassInfo)) {
                    if (reachableClassesToRemove == null) {
                        reachableClassesToRemove = new LinkedHashSet<>();
                    }
                    reachableClassesToRemove.add(reachableClassInfo);
                }
            }
            if (reachableClassesToRemove != null) {
                reachableClasses.removeAll(reachableClassesToRemove);
            }
        }

        return new ReachableAndDirectlyRelatedClasses(
                filterClassInfo(reachableClasses, scanResult.scanSpec, strictAccept, classTypes),
                filterClassInfo(directlyRelatedClasses, scanResult.scanSpec, strictAccept, classTypes));

    }

    /**
     * 获取类的名称
     *
     * @return 类的名称
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取类的简单名称返回类名中最后一个 '.' 之后的部分，如果类在根包中则返回整个字符串
     * (注意：这与 {@link Class#getSimpleName()} 的结果不同，后者对于匿名类返回 "")
     *
     * @return 类的简单名称
     */
    public String getSimpleName() {
        return getSimpleName(name);
    }

    /**
     * 获取该类的 {@link ModuleInfo} 对象
     *
     * @return 该类的 {@link ModuleInfo} 对象，如果类不属于命名模块则返回 null
     */
    public ModuleInfo getModuleInfo() {
        return moduleInfo;
    }

    /**
     * 获取该类的 {@link PackageInfo} 对象
     *
     * @return 包含该类的包的 {@link PackageInfo} 对象
     */
    public PackageInfo getPackageInfo() {
        return packageInfo;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 谓词方法

    /**
     * 获取类所在包的名称
     *
     * @return 类所在包的名称
     */
    public String getPackageName() {
        return PackageInfo.getParentPackageName(name);
    }

    /**
     * 检查是否为外部类
     *
     * @return 如果此类是外部类则返回 true，即被一个已接受的类引用为超类、接口或注解，
     *         但本身不是已接受的类
     */
    public boolean isExternalClass() {
        return isExternalClass;
    }

    /**
     * 设置是否为外部类
     *
     * @param externalClass
     *            如果为 true 则表示外部类
     */
    public void setExternalClass(final boolean externalClass) {
        this.isExternalClass = externalClass;
    }

    /**
     * 设置此类是否已被扫描
     *
     * @param scannedClass
     *            如果为 true 则表示已扫描
     */
    public void setScannedClass(final boolean scannedClass) {
        this.isScannedClass = scannedClass;
    }

    /**
     * 获取该类的完整路径
     *
     * @return 完整路径，或 null
     */
    public String getFullPath() {
        return fullPath;
    }

    /**
     * 设置该类的完整路径
     *
     * @param fullPath
     *            完整路径
     */
    public void setFullPath(final String fullPath) {
        this.fullPath = fullPath;
    }

    /**
     * 添加该类的子类
     *
     * @param subclass
     *            子类 ClassInfo
     */
    public void addSubclass(final ClassInfo subclass) {
        if (subclass != null) {
            this.addRelatedClass(RelType.SUBCLASSES, subclass);
            subclass.addRelatedClass(RelType.SUPERCLASSES, this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 为 ClassGraphWorkspaceAdapter 提供的向后兼容 setter

    /**
     * 添加实现此接口的类
     *
     * @param implementingClass
     *            实现类的 ClassInfo
     */
    public void addImplementingClass(final ClassInfo implementingClass) {
        if (implementingClass != null) {
            this.addRelatedClass(RelType.CLASSES_IMPLEMENTING, implementingClass);
            implementingClass.addRelatedClass(RelType.IMPLEMENTED_INTERFACES, this);
        }
    }

    /**
     * 获取该类 class 文件的 class 文件格式次版本号
     *
     * @return 该类 class 文件的 class 文件格式次版本号，如果此 {@link ClassInfo}
     *         对象是在扫描中未找到或未被接受的被引用类的占位符，则返回 0
     */
    public int getClassfileMinorVersion() {
        return classfileMinorVersion;
    }

    /**
     * 获取该类 class 文件的 class 文件格式主版本号
     *
     * @return 该类 class 文件的 class 文件格式主版本号，如果此 {@link ClassInfo}
     *         对象是在扫描中未找到或未被接受的被引用类的占位符，则返回 0
     */
    public int getClassfileMajorVersion() {
        return classfileMajorVersion;
    }

    /**
     * 获取类修饰符位
     *
     * @return 类修饰符位，例如 {@link Modifier#PUBLIC}
     */
    public int getModifiers() {
        return modifiers;
    }

    /**
     * 设置类修饰符
     *
     * @param modifiers
     *            类修饰符
     */
    void setModifiers(final int modifiers) {
        this.modifiers |= modifiers;
    }

    /**
     * 获取类修饰符的字符串表示
     *
     * @return 类修饰符字符串，例如 "public static final"要获取修饰符位，请调用
     *         {@link #getModifiers()}
     */
    public String getModifiersStr() {
        final StringBuilder buf = new StringBuilder();
        TypeUtils.modifiersToString(modifiers, ModifierType.CLASS, /* ignored */ false, buf);
        return buf.toString();
    }

    /**
     * 检查类是否为 public
     *
     * @return 如果此类是 public 类则返回 true
     */
    public boolean isPublic() {
        return Modifier.isPublic(modifiers);
    }

    /**
     * 检查类是否为 private
     *
     * @return 如果此类是 private 类则返回 true
     */
    public boolean isPrivate() {
        return Modifier.isPrivate(modifiers);
    }

    /**
     * 检查类是否为 protected
     *
     * @return 如果此类是 protected 类则返回 true
     */
    public boolean isProtected() {
        return Modifier.isProtected(modifiers);
    }

    /**
     * 检查类是否具有默认(包)可见性
     *
     * @return 如果此类仅在其包内可见则返回 true
     */
    public boolean isPackageVisible() {
        return !isPublic() && !isPrivate() && !isProtected();
    }

    /**
     * 检查类是否为抽象类
     *
     * @return 如果此类是抽象类则返回 true
     */
    public boolean isAbstract() {
        return Modifier.isAbstract(modifiers);
    }

    /**
     * 检查类是否为合成类
     *
     * @return 如果此类是合成类则返回 true
     */
    public boolean isSynthetic() {
        return (modifiers & 0x1000) != 0;
    }

    /**
     * 检查类是否为 final
     *
     * @return 如果此类是 final 类则返回 true
     */
    public boolean isFinal() {
        return Modifier.isFinal(modifiers);
    }

    /**
     * 检查类是否为 static
     *
     * @return 如果此类是 static 则返回 true
     */
    public boolean isStatic() {
        return Modifier.isStatic(modifiers);
    }

    /**
     * 检查类是否为注解
     *
     * @return 如果此类是注解类则返回 true
     */
    public boolean isAnnotation() {
        return (modifiers & ANNOTATION_CLASS_MODIFIER) != 0;
    }

    /**
     * 检查类是否为接口且不是注解
     *
     * @return 如果此类是接口且不是注解则返回 true(注解是接口，可以被实现)
     */
    public boolean isInterface() {
        return isInterfaceOrAnnotation() && !isAnnotation();
    }

    /**
     * 检查是否为接口或注解
     *
     * @return 如果此类是接口或注解则返回 true(注解是接口，可以被实现)
     */
    public boolean isInterfaceOrAnnotation() {
        return (modifiers & Modifier.INTERFACE) != 0;
    }

    /**
     * 检查类是否为 {@link Enum} 枚举
     *
     * @return 如果此类是 {@link Enum} 枚举则返回 true
     */
    public boolean isEnum() {
        return (modifiers & 0x4000) != 0;
    }

    /**
     * 检查类是否为记录类(JDK 14+)
     *
     * @return 如果此类是记录类则返回 true
     */
    public boolean isRecord() {
        return isRecord;
    }

    /**
     * 检查此类是否为标准类
     *
     * @return 如果此类是标准类(即不是注解或接口)则返回 true
     */
    public boolean isStandardClass() {
        return !(isAnnotation() || isInterface());
    }

    /**
     * 检查此类是否为数组类除非此 {@link ClassInfo} 是 {@link ArrayClassInfo} 的实例，
     * 否则返回 false
     *
     * @return 如果这是数组类则返回 true
     */
    public boolean isArrayClass() {
        return this instanceof ArrayClassInfo;
    }

    /**
     * 检查此类是否继承自指定超类
     *
     * @param superclass
     *            一个超类
     * @return 如果此类继承自该超类则返回 true
     */
    public boolean extendsSuperclass(final Class<?> superclass) {
        return extendsSuperclass(superclass.getName());
    }

    /**
     * 检查此类是否继承自指定的超类名称
     *
     * @param superclassName
     *            超类的名称
     * @return 如果此类继承自指定名称的超类则返回 true
     */
    public boolean extendsSuperclass(final String superclassName) {
        return ("java.lang.Object".equals(superclassName) && isStandardClass())
                || getSuperclasses().containsName(superclassName);
    }

    /**
     * 检查此类是否为内部类
     *
     * @return 如果这是内部类则返回 true(调用 {@link #isAnonymousInnerClass()} 可测试是否为匿名
     *         内部类)如果为 true，可以通过调用 {@link #getOuterClasses()} 来确定包含类
     */
    public boolean isInnerClass() {
        return !getOuterClasses().isEmpty();
    }

    /**
     * 检查此类是否为外部类
     *
     * @return 如果此类包含内部类则返回 true如果为 true，可以通过调用
     *         {@link #getInnerClasses()} 来确定内部类
     */
    public boolean isOuterClass() {
        return !getInnerClasses().isEmpty();
    }

    /**
     * 检查此类是否为匿名内部类
     *
     * @return 如果这是匿名内部类则返回 true如果为 true，可以通过调用
     *         {@link #getFullyQualifiedDefiningMethodName()} 获取包含方法的名称
     */
    public boolean isAnonymousInnerClass() {
        return fullyQualifiedDefiningMethodName != null;
    }

    /**
     * 检查此类是否为已实现的接口(即标准的、非注解的接口，或者已被某个类作为接口实现的注解)
     *
     * <p>
     * 注解是接口，但也可以实现注解，因此我们返回接口(即使是注解)是否被类实现或被子接口扩展，
     * 或者(如果没有被实现)它是否不是注解但仍然是接口
     *
     * @return 如果此类是已实现的接口则返回 true
     */
    public boolean isImplementedInterface() {
        return relatedClasses.get(RelType.CLASSES_IMPLEMENTING) != null || isInterface();
    }

    /**
     * 检查此类是否实现了指定的接口
     *
     * @param interfaceClazz
     *            一个接口
     * @return 如果此类实现了该接口则返回 true
     */
    public boolean implementsInterface(final Class<?> interfaceClazz) {
        Assert.isInterface(interfaceClazz);
        return implementsInterface(interfaceClazz.getName());
    }

    /**
     * 检查此类是否实现了指定名称的接口
     *
     * @param interfaceName
     *            接口的名称
     * @return 如果此类实现了指定名称的接口则返回 true
     */
    public boolean implementsInterface(final String interfaceName) {
        return getInterfaces().containsName(interfaceName);
    }

    /**
     * 检查此类是否具有指定的注解
     *
     * @param annotation
     *            一个注解
     * @return 如果此类具有该注解则返回 true
     */
    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    /**
     * 检查此类是否具有指定名称的注解
     *
     * @param annotationName
     *            注解的名称
     * @return 如果此类具有指定名称的注解则返回 true
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotations().containsName(annotationName);
    }

    /**
     * 检查此类是否声明了指定名称的字段
     *
     * @param fieldName
     *            字段的名称
     * @return 如果此类声明了给定名称的字段则返回 true
     */
    public boolean hasDeclaredField(final String fieldName) {
        return getDeclaredFieldInfo().containsName(fieldName);
    }

    /**
     * 检查此类或其超类之一是否具有指定名称的字段
     *
     * @param fieldName
     *            字段的名称
     * @return 如果此类或其超类之一声明了给定名称的字段则返回 true
     */
    public boolean hasField(final String fieldName) {
        for (final ClassInfo ci : getFieldOverrideOrder()) {
            if (ci.hasDeclaredField(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的字段
     *
     * @param annotation
     *            一个字段注解
     * @return 如果此类声明了带有该注解的字段则返回 true
     */
    public boolean hasDeclaredFieldAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasDeclaredFieldAnnotation(annotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的字段
     *
     * @param fieldAnnotationName
     *            字段注解的名称
     * @return 如果此类声明了带有指定名称注解的字段则返回 true
     */
    public boolean hasDeclaredFieldAnnotation(final String fieldAnnotationName) {
        for (final FieldInfo fi : getDeclaredFieldInfo()) {
            if (fi.hasAnnotation(fieldAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类或其超类之一是否声明了带有指定注解的字段
     *
     * @param fieldAnnotation
     *            一个字段注解
     * @return 如果此类或其超类之一声明了带有该注解的字段则返回 true
     */
    public boolean hasFieldAnnotation(final Class<? extends Annotation> fieldAnnotation) {
        Assert.isAnnotation(fieldAnnotation);
        return hasFieldAnnotation(fieldAnnotation.getName());
    }

    /**
     * 检查此类或其超类之一是否声明了带有指定名称注解的字段
     *
     * @param fieldAnnotationName
     *            字段注解的名称
     * @return 如果此类或其超类之一声明了带有指定名称注解的字段则返回 true
     */
    public boolean hasFieldAnnotation(final String fieldAnnotationName) {
        for (final ClassInfo ci : getFieldOverrideOrder()) {
            if (ci.hasDeclaredFieldAnnotation(fieldAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了给定名称的方法
     *
     * @param methodName
     *            方法的名称
     * @return 如果此类声明了给定名称的方法则返回 true
     */
    public boolean hasDeclaredMethod(final String methodName) {
        return getDeclaredMethodInfo().containsName(methodName);
    }

    /**
     * 检查此类或其超类或接口之一是否声明了给定名称的方法
     *
     * @param methodName
     *            方法的名称
     * @return 如果此类或其超类或接口之一声明了给定名称的方法则返回 true
     */
    public boolean hasMethod(final String methodName) {
        for (final ClassInfo ci : getMethodOverrideOrder()) {
            if (ci.hasDeclaredMethod(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的方法
     *
     * @param methodAnnotation
     *            一个方法注解
     * @return 如果此类声明了带有该注解的方法则返回 true
     */
    public boolean hasDeclaredMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        Assert.isAnnotation(methodAnnotation);
        return hasDeclaredMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的方法
     *
     * @param methodAnnotationName
     *            方法注解的名称
     * @return 如果此类声明了带有指定名称注解的方法则返回 true
     */
    public boolean hasDeclaredMethodAnnotation(final String methodAnnotationName) {
        for (final MethodInfo mi : getDeclaredMethodInfo()) {
            if (mi.hasAnnotation(methodAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类或其超类或接口之一是否声明了带有指定注解的方法
     *
     * @param methodAnnotation
     *            一个方法注解
     * @return 如果此类或其超类或接口之一声明了带有该注解的方法则返回 true
     */
    public boolean hasMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        Assert.isAnnotation(methodAnnotation);
        return hasMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 检查此类或其超类或接口之一是否声明了带有指定名称注解的方法
     *
     * @param methodAnnotationName
     *            方法注解的名称
     * @return 如果此类或其超类或接口之一声明了带有指定名称注解的方法则返回 true
     */
    public boolean hasMethodAnnotation(final String methodAnnotationName) {
        for (final ClassInfo ci : getMethodOverrideOrder()) {
            if (ci.hasDeclaredMethodAnnotation(methodAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的方法参数
     *
     * @param methodParameterAnnotation
     *            一个方法参数注解
     * @return 如果此类声明了带有该注解的方法参数则返回 true
     */
    public boolean hasDeclaredMethodParameterAnnotation(
            final Class<? extends Annotation> methodParameterAnnotation) {
        Assert.isAnnotation(methodParameterAnnotation);
        return hasDeclaredMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的方法参数
     *
     * @param methodParameterAnnotationName
     *            方法参数注解的名称
     * @return 如果此类声明了带有指定名称注解的方法参数则返回 true
     */
    public boolean hasDeclaredMethodParameterAnnotation(final String methodParameterAnnotationName) {
        for (final MethodInfo mi : getDeclaredMethodInfo()) {
            if (mi.hasParameterAnnotation(methodParameterAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类或其超类或接口之一是否具有带有指定注解的方法参数
     *
     * @param methodParameterAnnotation
     *            一个方法参数注解
     * @return 如果此类或其超类或接口之一具有带有该注解的方法参数则返回 true
     */
    public boolean hasMethodParameterAnnotation(final Class<? extends Annotation> methodParameterAnnotation) {
        Assert.isAnnotation(methodParameterAnnotation);
        return hasMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 检查此类或其超类或接口之一是否具有带有指定名称注解的方法参数
     *
     * @param methodParameterAnnotationName
     *            方法参数注解的名称
     * @return 如果此类或其超类或接口之一具有带有指定名称注解的方法参数则返回 true
     */
    public boolean hasMethodParameterAnnotation(final String methodParameterAnnotationName) {
        for (final ClassInfo ci : getMethodOverrideOrder()) {
            if (ci.hasDeclaredMethodParameterAnnotation(methodParameterAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 递归遍历接口和超类，获取字段被覆盖的顺序
     *
     * @param visited
     *            已访问的类集合
     * @param overrideOrderOut
     *            输出的覆盖顺序列表
     * @return 覆盖顺序列表
     */
    private List<ClassInfo> getFieldOverrideOrder(final Set<ClassInfo> visited,
                                                  final List<ClassInfo> overrideOrderOut) {
        if (visited.add(this)) {
            overrideOrderOut.add(this);
            for (final ClassInfo iface : getInterfaces()) {
                iface.getFieldOverrideOrder(visited, overrideOrderOut);
            }
            final ClassInfo superclass = getSuperclass();
            if (superclass != null) {
                superclass.getFieldOverrideOrder(visited, overrideOrderOut);
            }
        }
        return overrideOrderOut;
    }

    /**
     * 获取字段覆盖的顺序(基类优先)
     *
     * @return 覆盖顺序列表
     */
    private List<ClassInfo> getFieldOverrideOrder() {
        if (overrideOrder == null) {
            synchronized (this) {
                if (overrideOrder == null) {
                    overrideOrder = getFieldOverrideOrder(new HashSet<ClassInfo>(), new ArrayList<ClassInfo>());
                }
            }
        }
        return overrideOrder;
    }

    /**
     * 递归收集类和接口，按照方法覆盖的优先级降序排列
     * <p>
     * 首先收集所有直接超类，因为它们的方法始终比接口声明的方法具有更高的优先级
     * 然后遍历接口，将扩展已找到接口的接口插入到输出中该接口之前
     * 不相关接口之间的顺序未定义
     * <p>
     * 详见 Java 语言规范 8.4.8
     *
     * @param visited
     *            已访问 ClassInfo 的非空集合
     * @param overrideOrderOut
     *            按覆盖优先级降序排列的 ClassInfo 输出列表
     * @return overrideOrderOut 实例
     */
    private List<ClassInfo> getMethodOverrideOrder(final Set<ClassInfo> visited,
                                                   final List<ClassInfo> overrideOrderOut) {
        if (!visited.add(this)) {
            return overrideOrderOut;
        }
        // 首先收集具体超类，直接添加到 overrideOrder
        if (!isInterfaceOrAnnotation()) {
            overrideOrderOut.add(this);
            // 首先遍历直接超类，它们在方法覆盖方面具有最高优先级
            final ClassInfo superclass = getSuperclass();
            if (superclass != null) {
                superclass.getMethodOverrideOrder(visited, overrideOrderOut);
            }
            for (final ClassInfo iface : getInterfaces()) {
                iface.getMethodOverrideOrder(visited, overrideOrderOut);
            }
            return overrideOrderOut;
        }
        // overrideOrderOut 现在已包含所有具体类
        // 这是一个接口如果某个被扩展的接口已在输出中，则需要将其插入到该接口之前
        // 否则，此接口与目前收集的所有 ClassInfo 无关，可以直接添加到结果中
        // 编译器应该防止继承具有相同签名方法的不相关接口
        // 但由于在运行时动态链接了不同的接口，这种情况仍可能发生，此时返回的顺序未定义
        final ClassInfoList interfaces = getInterfaces();
        int minIndex = Integer.MAX_VALUE;
        for (final ClassInfo iface : interfaces) {
            if (!visited.contains(iface)) {
                continue;
            }
            final int currIdx = overrideOrderOut.indexOf(iface);
            minIndex = currIdx >= 0 && currIdx < minIndex ? currIdx : minIndex;
        }
        if (minIndex == Integer.MAX_VALUE) {
            overrideOrderOut.add(this);
        } else {
            overrideOrderOut.add(minIndex, this);
        }
        // 将接口添加到覆盖顺序的末尾
        for (final ClassInfo iface : interfaces) {
            iface.getMethodOverrideOrder(visited, overrideOrderOut);
        }
        return overrideOrderOut;
    }

    /**
     * 获取方法覆盖的顺序
     *
     * @return 覆盖顺序列表
     */
    private List<ClassInfo> getMethodOverrideOrder() {
        if (methodOverrideOrder == null) {
            synchronized (this) {
                if (methodOverrideOrder == null) {
                    methodOverrideOrder = getMethodOverrideOrder(new HashSet<ClassInfo>(), new ArrayList<ClassInfo>());
                }
            }
        }
        return methodOverrideOrder;
    }

    /**
     * 获取此类的子类，按名称排序调用 {@link ClassInfoList#directOnly()} 可获取直接子类
     *
     * 如果此类表示 {@link Object}，则只返回标准类而非接口，因为接口不继承 {@link Object}
     *
     * @return 此类子类的列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses() {
        if ("java.lang.Object".equals(getName())) {
            // 对查询 java.lang.Object 的所有子类做特殊处理
            return scanResult.getAllStandardClasses();
        } else {
            return new ClassInfoList(
                    this.filterClassInfo(RelType.SUBCLASSES, /* strictAccept = */ !isExternalClass),
                    /* sortByName = */ true);
        }
    }

    /**
     * 获取此类的所有超类，按类层次结构升序排列，为简单起见不包括 {@link Object}，
     * 因为它是所有类的超类
     *
     * 也不包括超接口，如果这是接口的话(使用 {@link #getInterfaces()} 获取接口的超接口)
     *
     * @return 此类所有超类的列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses() {
        return new ClassInfoList(this.filterClassInfo(RelType.SUPERCLASSES, /* strictAccept = */ false),
                /* sortByName = */ false);
    }

    /**
     * 获取此类的单个直接超类，如果没有则返回 null不返回超接口，如果这是接口的话
     * (使用 {@link #getInterfaces()} 获取接口的超接口)
     *
     * @return 此类的超类，如果没有则返回 null
     */
    public ClassInfo getSuperclass() {
        final Set<ClassInfo> superClasses = relatedClasses.get(RelType.SUPERCLASSES);
        if (superClasses == null || superClasses.isEmpty()) {
            return null;
        } else if (superClasses.size() > 2) {
            throw new IllegalArgumentException("More than one superclass: " + superClasses);
        } else {
            ClassInfo nonObjectSuperclass = null;
            for (final ClassInfo superclass : superClasses) {
                if (!"java.lang.Object".equals(superclass.getName())) {
                    nonObjectSuperclass = superclass;
                }
            }
            return nonObjectSuperclass;
        }
    }

    /**
     * 设置此类的超类
     *
     * @param superclass
     *            超类 ClassInfo
     */
    public void setSuperclass(final ClassInfo superclass) {
        if (superclass != null) {
            this.addRelatedClass(RelType.SUPERCLASSES, superclass);
            superclass.addRelatedClass(RelType.SUBCLASSES, this);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取包含此外部类，如果这是内部类的话
     *
     * @return 包含外部类的列表，如果这是内部类的话；否则返回空列表注意，返回所有
     *         包含外部类，而不仅仅是最内层的包含外部类
     */
    public ClassInfoList getOuterClasses() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CONTAINED_WITHIN_OUTER_CLASS, /* strictAccept = */ false),
                /* sortByName = */ false);
    }

    /**
     * 获取包含在此类中的内部类，如果这是外部类的话
     *
     * @return 包含在此类中的内部类列表，如果没有则返回空列表
     */
    public ClassInfoList getInnerClasses() {
        return new ClassInfoList(this.filterClassInfo(RelType.CONTAINS_INNER_CLASS, /* strictAccept = */ false),
                /* sortByName = */ true);
    }

    /**
     * 获取定义方法的完全限定方法名(即完全限定类名，后跟点号，再跟方法名)，
     * 如果这是匿名内部类的话
     *
     * @return 定义方法的完全限定方法名(即完全限定类名，后跟点号，再跟方法名)，
     *         如果这是匿名内部类的话；否则返回 null
     */
    public String getFullyQualifiedDefiningMethodName() {
        return fullyQualifiedDefiningMethodName;
    }

    /**
     * 获取此类或其超类实现的接口，如果这是标准类的话；或者此接口扩展的超接口，如果这是接口的话
     *
     * @return 此类或其超类实现的接口列表，如果这是标准类的话；或者此接口扩展的超接口，
     *         如果这是接口的话如果没有则返回空列表
     */
    public ClassInfoList getInterfaces() {
        // 类也会继承其超类实现的接口
        final ReachableAndDirectlyRelatedClasses implementedInterfaces = this
                .filterClassInfo(RelType.IMPLEMENTED_INTERFACES, /* strictAccept = */ false);
        final Set<ClassInfo> allInterfaces = new LinkedHashSet<>(implementedInterfaces.reachableClasses);
        for (final ClassInfo superclass : this.filterClassInfo(RelType.SUPERCLASSES,
                /* strictAccept = */ false).reachableClasses) {
            final Set<ClassInfo> superclassImplementedInterfaces = superclass
                    .filterClassInfo(RelType.IMPLEMENTED_INTERFACES, /* strictAccept = */ false).reachableClasses;
            allInterfaces.addAll(superclassImplementedInterfaces);
        }
        // 不能按名称排序接口，因为它们在继承定义中的顺序是重要的
        return new ClassInfoList(allInterfaces, implementedInterfaces.directlyRelatedClasses,
                /* sortByName = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 标准类

    /**
     * 设置此类的接口
     *
     * @param interfaces
     *            接口 ClassInfoList
     */
    public void setInterfaces(final ClassInfoList interfaces) {
        if (interfaces != null) {
            for (final ClassInfo iface : interfaces) {
                iface.setIsInterface(true);
                this.addRelatedClass(RelType.IMPLEMENTED_INTERFACES, iface);
                iface.addRelatedClass(RelType.CLASSES_IMPLEMENTING, this);
            }
        }
    }

    /**
     * 获取实现此接口的类(及其子类)，如果这是接口的话
     *
     * @return 实现此接口的类(及其子类)的列表，如果这是接口的话；否则返回空列表
     */
    public ClassInfoList getClassesImplementing() {
        // 实现类的子类也会实现该接口
        final ReachableAndDirectlyRelatedClasses implementingClasses = this
                .filterClassInfo(RelType.CLASSES_IMPLEMENTING, /* strictAccept = */ !isExternalClass);
        final Set<ClassInfo> allImplementingClasses = new LinkedHashSet<>(implementingClasses.reachableClasses);
        for (final ClassInfo implementingClass : implementingClasses.reachableClasses) {
            final Set<ClassInfo> implementingSubclasses = implementingClass.filterClassInfo(RelType.SUBCLASSES,
                    /* strictAccept = */ !implementingClass.isExternalClass).reachableClasses;
            allImplementingClasses.addAll(implementingSubclasses);
        }
        return new ClassInfoList(allImplementingClasses, implementingClasses.directlyRelatedClasses,
                /* sortByName = */ true);
    }

    /**
     * 获取此类上的注解和元注解(如果需要注解的参数值而不仅仅是注解类，
     * 请调用 {@link #getAnnotationInfo()} 替代此方法)
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 过滤掉 {@code java.lang.annotation} 包中的元注解
     *
     * @return 此类上的注解和元注解列表
     */
    public ClassInfoList getAnnotations() {
        synchronized (this) {
            if (annotationsRef != null) {
                return annotationsRef;
            }

            if (!scanResult.scanSpec.enableAnnotationInfo) {
                throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
            }

            // 获取此类上的所有注解
            final ReachableAndDirectlyRelatedClasses annotationClasses = this
                    .filterClassInfo(RelType.CLASS_ANNOTATIONS, /* strictAccept = */ false);
            // 检查超类上是否有任何 @Inherited 注解
            Set<ClassInfo> inheritedSuperclassAnnotations = null;
            for (final ClassInfo superclass : getSuperclasses()) {
                for (final ClassInfo superclassAnnotation : superclass.filterClassInfo(RelType.CLASS_ANNOTATIONS,
                        /* strictAccept = */ false).reachableClasses) {
                    // 检查此注解上的任何元注解是否包含 @Inherited，
                    // @Inherited 元注解使得注解可以标注一个类及其所有子类
                    if (superclassAnnotation != null && superclassAnnotation.isInherited) {
                        // superclassAnnotation 具有 @Inherited 元注解
                        if (inheritedSuperclassAnnotations == null) {
                            inheritedSuperclassAnnotations = new LinkedHashSet<>();
                        }
                        inheritedSuperclassAnnotations.add(superclassAnnotation);
                    }
                }
            }

            if (inheritedSuperclassAnnotations == null) {
                // 没有可继承的超类注解
                annotationsRef = new ClassInfoList(annotationClasses, /* sortByName = */ true);
            } else {
                // 合并可继承的超类注解和此类上的注解
                inheritedSuperclassAnnotations.addAll(annotationClasses.reachableClasses);
                annotationsRef = new ClassInfoList(inheritedSuperclassAnnotations,
                        annotationClasses.directlyRelatedClasses, /* sortByName = */ true);
            }
            return annotationsRef;
        }
    }

    /**
     * 获取此类声明的字段、方法或方法参数上的注解或元注解
     * (不包括此类的接口或超类声明的字段、方法或方法参数)
     *
     * @param relType
     *            {@link RelType#FIELD_ANNOTATIONS}、{@link RelType#METHOD_ANNOTATIONS} 或
     *            {@link RelType#METHOD_PARAMETER_ANNOTATIONS} 之一
     * @return 此类声明的字段或方法上的注解或元注解列表(不包括此类的接口或超类声明的
     *         字段或方法)，作为 {@link ClassInfo} 对象列表，如果没有则返回空列表
     */
    private ClassInfoList getFieldOrMethodAnnotations(final RelType relType) {
        final boolean isField = relType == RelType.FIELD_ANNOTATIONS;
        if (!(isField ? scanResult.scanSpec.enableFieldInfo : scanResult.scanSpec.enableMethodInfo)
                || !scanResult.scanSpec.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enable" + (isField ? "Field" : "Method")
                    + "Info() and " + "#enableAnnotationInfo() before #scan()");
        }
        final ReachableAndDirectlyRelatedClasses fieldOrMethodAnnotations = this.filterClassInfo(relType,
                /* strictAccept = */ false, ClassType.ANNOTATION);
        final Set<ClassInfo> fieldOrMethodAnnotationsAndMetaAnnotations = new LinkedHashSet<>(
                fieldOrMethodAnnotations.reachableClasses);
        return new ClassInfoList(fieldOrMethodAnnotationsAndMetaAnnotations,
                fieldOrMethodAnnotations.directlyRelatedClasses, /* sortByName = */ true);
    }

    /**
     * 获取以此类作为字段、方法或方法参数注解的类
     *
     * @param relType
     *            {@link RelType#CLASSES_WITH_FIELD_ANNOTATION}、
     *            {@link RelType#CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION}、
     *            {@link RelType#CLASSES_WITH_METHOD_ANNOTATION}、
     *            {@link RelType#CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION}、
     *            {@link RelType#CLASSES_WITH_METHOD_PARAMETER_ANNOTATION} 或
     *            {@link RelType#CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION} 之一
     * @return 声明了带有此注解或元注解的方法或字段的类的列表，如果没有则返回空列表
     */
    private ClassInfoList getClassesWithFieldOrMethodAnnotation(final RelType relType) {
        final boolean isField = relType == RelType.CLASSES_WITH_FIELD_ANNOTATION
                || relType == RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION;
        if (!(isField ? scanResult.scanSpec.enableFieldInfo : scanResult.scanSpec.enableMethodInfo)
                || !scanResult.scanSpec.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enable" + (isField ? "Field" : "Method")
                    + "Info() and " + "#enableAnnotationInfo() before #scan()");
        }
        final ReachableAndDirectlyRelatedClasses classesWithDirectlyAnnotatedFieldsOrMethods = this
                .filterClassInfo(relType, /* strictAccept = */ !isExternalClass);
        final ReachableAndDirectlyRelatedClasses annotationsWithThisMetaAnnotation = this.filterClassInfo(
                RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !isExternalClass, ClassType.ANNOTATION);
        if (annotationsWithThisMetaAnnotation.reachableClasses.isEmpty()) {
            // 此注解未元标注其他标注了方法的注解
            return new ClassInfoList(classesWithDirectlyAnnotatedFieldsOrMethods, /* sortByName = */ true);
        } else {
            // 取所有被此注解直接标注了字段或方法的类的并集，
            // 以及被此注解元标注了字段或方法的类
            final Set<ClassInfo> allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods = new LinkedHashSet<>(
                    classesWithDirectlyAnnotatedFieldsOrMethods.reachableClasses);
            for (final ClassInfo metaAnnotatedAnnotation : annotationsWithThisMetaAnnotation.reachableClasses) {
                allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods
                        .addAll(metaAnnotatedAnnotation.filterClassInfo(relType,
                                /* strictAccept = */ !metaAnnotatedAnnotation.isExternalClass).reachableClasses);
            }
            return new ClassInfoList(allClassesWithAnnotatedOrMetaAnnotatedFieldsOrMethods,
                    classesWithDirectlyAnnotatedFieldsOrMethods.directlyRelatedClasses, /* sortByName = */ true);
        }
    }

    /**
     * 获取此类上的注解列表，如果没有则返回空列表
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * @return 此类上注解的 {@link AnnotationInfo} 对象列表，如果没有则返回空列表
     */
    public AnnotationInfoList getAnnotationInfo() {
        synchronized (this) {
            if (annotationInfoRef != null) {
                return annotationInfoRef;
            }

            if (!scanResult.scanSpec.enableAnnotationInfo) {
                throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
            }

            annotationInfoRef = AnnotationInfoList.getIndirectAnnotations(annotationInfo, this);
            return annotationInfoRef;
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // 接口

    /**
     * 设置此类的注解信息
     *
     * @param annotationInfo
     *            AnnotationInfoList 注解信息列表
     */
    public void setAnnotationInfo(final AnnotationInfoList annotationInfo) {
        this.annotationInfo = annotationInfo;
        // 同时链接注解 ClassInfo 对象
        if (annotationInfo != null) {
            for (final AnnotationInfo ai : annotationInfo) {
                // 查找或创建注解 ClassInfo
                final String annName = ai.getName();
                // 这里无法方便地访问 classNameToClassInfo，所以只存储注解信息
            }
        }
    }

    /**
     * 获取此类上的非 {@link Repeatable} 注解，如果类没有该注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(String)})
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 注意，如果需要获取多个注解，更快的做法是先调用 {@link #getAnnotationInfo()}，
     * 然后从返回的 {@link AnnotationInfoList} 中获取注解，这样返回的列表就不需要被多次构建
     *
     * @param annotation
     *            注解
     * @return 表示此类上该注解的 {@link AnnotationInfo} 对象，如果类没有该注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    // -------------------------------------------------------------------------------------------------------------
    // 注解

    /**
     * 获取此类上的指定名称的非 {@link Repeatable} 注解，如果类没有指定名称的注解则返回 null
     * (对于 {@link Repeatable} 注解，请使用 {@link #getAnnotationInfoRepeatable(String)})
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 注意，如果需要获取多个指定名称的注解，更快的做法是先调用 {@link #getAnnotationInfo()}，
     * 然后从返回的 {@link AnnotationInfoList} 中获取指定名称的注解，这样返回的列表就不需要被多次构建
     *
     * @param annotationName
     *            注解名称
     * @return 表示此类上指定名称注解的 {@link AnnotationInfo} 对象，如果类没有指定名称的注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        return getAnnotationInfo().get(annotationName);
    }

    /**
     * 获取此类上的 {@link Repeatable} 注解，如果类没有该注解则返回空列表
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 注意，如果需要获取多个注解，更快的做法是先调用 {@link #getAnnotationInfo()}，
     * 然后从返回的 {@link AnnotationInfoList} 中获取注解，这样返回的列表就不需要被多次构建
     *
     * @param annotation
     *            注解
     * @return 此类上该注解所有实例的 {@link AnnotationInfoList}，如果类没有该注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfoRepeatable(annotation.getName());
    }

    /**
     * 获取此类上的指定名称的 {@link Repeatable} 注解，如果类没有指定名称的注解则返回空列表
     *
     * <p>
     * 还处理 {@link Inherited} 元注解，该元注解使得注解可以标注一个类及其所有子类
     *
     * <p>
     * 注意，如果需要获取多个指定名称的注解，更快的做法是先调用 {@link #getAnnotationInfo()}，
     * 然后从返回的 {@link AnnotationInfoList} 中获取指定名称的注解，这样返回的列表就不需要被多次构建
     *
     * @param annotationName
     *            注解名称
     * @return 此类上指定名称注解所有实例的 {@link AnnotationInfoList}，如果类没有指定名称的注解则返回空列表
     */
    public AnnotationInfoList getAnnotationInfoRepeatable(final String annotationName) {
        return getAnnotationInfo().getRepeatable(annotationName);
    }

    /**
     * 获取此注解的默认参数值，如果这是注解类的话
     *
     * @return 此注解每个默认参数值的 {@link AnnotationParameterValue} 对象列表，
     *         如果这是具有默认参数值的注解类的话；否则返回空列表
     */
    public AnnotationParameterValueList getAnnotationDefaultParameterValues() {
        if (!scanResult.scanSpec.enableAnnotationInfo) {
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

    /**
     * 获取以此类作为注解的类
     *
     * @return 被此类标注的标准类和非注解接口的列表，如果这是注解类的话；如果没有则返回空列表
     *         还处理 {@link Inherited} 元注解，该元注解使得类上的注解可以被其所有子类继承
     */
    public ClassInfoList getClassesWithAnnotation() {
        if (!scanResult.scanSpec.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableAnnotationInfo() before #scan()");
        }

        // 获取拥有此注解的类
        final ReachableAndDirectlyRelatedClasses classesWithAnnotation = this
                .filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !isExternalClass);

        if (isInherited) {
            // 如果这是可继承的注解，将所有被注解类的子类也加入结果中
            final Set<ClassInfo> classesWithAnnotationAndTheirSubclasses = new LinkedHashSet<>(
                    classesWithAnnotation.reachableClasses);
            for (final ClassInfo classWithAnnotation : classesWithAnnotation.reachableClasses) {
                classesWithAnnotationAndTheirSubclasses.addAll(classWithAnnotation.getSubclasses());
            }
            return new ClassInfoList(classesWithAnnotationAndTheirSubclasses,
                    classesWithAnnotation.directlyRelatedClasses, /* sortByName = */ true);
        } else {
            // 如果不可继承，只返回被注解的类
            return new ClassInfoList(classesWithAnnotation, /* sortByName = */ true);
        }
    }

    /**
     * 获取以此类作为直接注解的类
     *
     * @return 被请求的注解直接标注(即非元注解标注)的类的列表，如果没有则返回空列表
     */
    ClassInfoList getClassesWithAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_ANNOTATION, /* strictAccept = */ !isExternalClass),
                /* sortByName = */ true);
    }

    /**
     * 获取类中声明的方法、构造函数和/或静态初始化方法
     *
     * @param methodName
     *            方法名
     * @param getNormalMethods
     *            是否获取普通方法
     * @param getConstructorMethods
     *            是否获取构造函数
     * @param getStaticInitializerMethods
     *            是否获取静态初始化方法
     * @return 声明的方法信息
     */
    private MethodInfoList getDeclaredMethodInfo(final String methodName, final boolean getNormalMethods,
                                                 final boolean getConstructorMethods, final boolean getStaticInitializerMethods) {
        if (!scanResult.scanSpec.enableMethodInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableMethodInfo() before #scan()");
        }
        if (methodInfo == null) {
            return MethodInfoList.EMPTY_LIST;
        }
        if (methodName == null) {
            // 如果没有提供方法名，则按方法类型筛选(普通方法/构造函数/静态初始化方法)
            final MethodInfoList methodInfoList = new MethodInfoList();
            for (final MethodInfo mi : methodInfo) {
                final String miName = mi.getName();
                final boolean isConstructor = "<init>".equals(miName);
                // (目前静态初始化方法永远不会被公共方法返回)
                final boolean isStaticInitializer = "<clinit>".equals(miName);
                if ((isConstructor && getConstructorMethods) || (isStaticInitializer && getStaticInitializerMethods)
                        || (!isConstructor && !isStaticInitializer && getNormalMethods)) {
                    methodInfoList.add(mi);
                }
            }
            return methodInfoList;
        } else {
            // 如果提供了方法名，则筛选名称匹配的方法，忽略方法类型
            boolean hasMethodWithName = false;
            for (final MethodInfo f : methodInfo) {
                if (f.getName().equals(methodName)) {
                    hasMethodWithName = true;
                    break;
                }
            }
            if (!hasMethodWithName) {
                return MethodInfoList.EMPTY_LIST;
            }
            final MethodInfoList methodInfoList = new MethodInfoList();
            for (final MethodInfo mi : methodInfo) {
                if (mi.getName().equals(methodName)) {
                    methodInfoList.add(mi);
                }
            }
            return methodInfoList;
        }
    }

    /**
     * 获取类的方法、构造函数和/或静态初始化方法
     *
     * @param methodName
     *            方法名
     * @param getNormalMethods
     *            是否获取普通方法
     * @param getConstructorMethods
     *            是否获取构造函数
     * @param getStaticInitializerMethods
     *            是否获取静态初始化方法
     * @return 方法信息
     */
    private MethodInfoList getMethodInfo(final String methodName, final boolean getNormalMethods,
                                         final boolean getConstructorMethods, final boolean getStaticInitializerMethods) {
        if (!scanResult.scanSpec.enableMethodInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableMethodInfo() before #scan()");
        }
        // 实现方法/构造函数覆盖
        final MethodInfoList methodInfoList = new MethodInfoList();
        final Set<Entry<String, String>> nameAndTypeDescriptorSet = new HashSet<>();
        for (final ClassInfo ci : getMethodOverrideOrder()) {
            // 构造函数不从超类继承
            boolean shouldGetConstructorMethods = ci == this && getConstructorMethods;
            for (final MethodInfo mi : ci.getDeclaredMethodInfo(methodName, getNormalMethods, shouldGetConstructorMethods,
                    getStaticInitializerMethods)) {
                // 如果方法尚未被具有相同名称和类型描述符的方法覆盖
                if (nameAndTypeDescriptorSet.add(new SimpleEntry<>(mi.getName(), mi.getTypeDescriptorStr()))) {
                    // 将方法添加到输出顺序中
                    methodInfoList.add(mi);
                }
            }
        }
        return methodInfoList;
    }

    /**
     * 返回此类声明(但不包括其接口或超类声明)的可见方法(非构造函数)的信息另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的方法
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * @return 此类声明的可见方法的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getDeclaredMethodInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ false, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类或其接口或超类声明的可见方法(非构造函数)的信息另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的方法
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * @return 此类、其接口和超类的可见方法的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getMethodInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ false, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明(但不包括其接口或超类声明)的可见构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的构造函数
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共构造函数的信息，除非在扫描前调用了
     * {@link ClassGraph#ignoreMethodVisibility()}
     *
     * @return 此类声明的可见构造函数的 {@link MethodInfo} 对象列表，如果没有找到或可见的构造函数则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getDeclaredConstructorInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ false,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法

    /**
     * 返回此类或其接口或超类声明的可见构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的方法
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * @return 此类及其超类的可见构造函数的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getConstructorInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ false,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明(但不包括其接口或超类声明)的可见方法和构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}，静态初始化块的方法名为 {@code "<clinit>"}另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的方法或构造函数
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法和构造函数的信息，除非在扫描前调用了
     * {@link ClassGraph#ignoreMethodVisibility()}如果忽略了方法可见性，则结果中
     * 可能包含对私有静态类初始化块的引用，其方法名为 {@code "<clinit>"}
     *
     * @return 此类可见方法和构造函数的 {@link MethodInfo} 对象列表，如果没有找到或可见的方法或构造函数则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getDeclaredMethodAndConstructorInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类或其接口或超类声明的可见构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}，静态初始化块的方法名为 {@code "<clinit>"}另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 由于重载，可能有多个同名但类型签名不同的方法
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * @return 此类、其接口和超类的可见方法和构造函数的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getMethodAndConstructorInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明(但不包括其接口或超类声明)的指定名称的方法或构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}另请参见：
     *
     * <ul>
     * <li>{@link #getMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * <p>
     * 可能返回多个同名方法的信息(具有不同的类型签名)
     *
     * @param methodName
     *            要查询的方法名
     * @return 指定名称方法的 {@link MethodInfo} 对象列表，如果在此类中未找到该方法(或不可见)则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getDeclaredMethodInfo(final String methodName) {
        return getDeclaredMethodInfo(methodName, /* ignored */ false, /* ignored */ false, /* ignored */ false);
    }

    /**
     * 返回此类或其接口或超类声明的指定名称的方法或构造函数的信息
     * 构造函数的方法名为 {@code "<init>"}另请参见：
     *
     * <ul>
     * <li>{@link #getDeclaredMethodInfo(String)}
     * <li>{@link #getMethodInfo()}
     * <li>{@link #getDeclaredMethodInfo()}
     * <li>{@link #getConstructorInfo()}
     * <li>{@link #getDeclaredConstructorInfo()}
     * <li>{@link #getMethodAndConstructorInfo()}
     * <li>{@link #getDeclaredMethodAndConstructorInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableMethodInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共方法的信息，除非在扫描前调用了 {@link ClassGraph#ignoreMethodVisibility()}
     *
     * <p>
     * 可能返回多个同名方法的信息(具有不同的类型签名)
     *
     * @param methodName
     *            要查询的方法名
     * @return 指定名称方法的 {@link MethodInfo} 对象列表，如果在此类中未找到该方法(或不可见)则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableMethodInfo()}
     */
    public MethodInfoList getMethodInfo(final String methodName) {
        return getMethodInfo(methodName, /* ignored */ false, /* ignored */ false, /* ignored */ false);
    }

    /**
     * 获取所有方法注解
     *
     * @return 此类声明的方法上的所有注解或元注解列表(不包括此类的接口或超类声明的方法)，
     *         作为 {@link ClassInfo} 对象列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link MethodInfo#getAnnotationInfo()}
     *         获取具体方法注解实例的详细信息
     */
    public ClassInfoList getMethodAnnotations() {
        return getFieldOrMethodAnnotations(RelType.METHOD_ANNOTATIONS);
    }

    /**
     * 获取所有方法参数注解
     *
     * @return 此类声明的方法上的所有注解或元注解列表(不包括此类的接口或超类声明的方法)，
     *         作为 {@link ClassInfo} 对象列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link MethodInfo#getAnnotationInfo()}
     *         获取具体方法注解实例的详细信息
     */
    public ClassInfoList getMethodParameterAnnotations() {
        return getFieldOrMethodAnnotations(RelType.METHOD_PARAMETER_ANNOTATIONS);
    }

    /**
     * 获取以此类作为方法注解的所有类及其子类(如果方法是非私有的)
     *
     * @return 声明了带有此注解或元注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation() {
        // 获取所有具有被此注解标注或元标注的方法的类
        final Set<ClassInfo> classesWithMethodAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_METHOD_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的方法的类的子类
        // (非私有方法会被继承)
        for (final ClassInfo classWithNonprivateMethodAnnotationOrMetaAnnotation : //
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION)) {
            classesWithMethodAnnotation.addAll(classWithNonprivateMethodAnnotationOrMetaAnnotation.getSubclasses());
        }
        return new ClassInfoList(classesWithMethodAnnotation,
                new HashSet<>(getClassesWithMethodAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为方法参数注解的所有类及其子类(如果方法是非私有的)
     *
     * @return 声明了带有被此注解或元注解标注的参数的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation() {
        // 获取所有具有被此注解标注或元标注的方法的类
        final Set<ClassInfo> classesWithMethodParameterAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的方法的类的子类
        // (非私有方法会被继承)
        for (final ClassInfo classWithNonprivateMethodParameterAnnotationOrMetaAnnotation : //
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION)) {
            classesWithMethodParameterAnnotation
                    .addAll(classWithNonprivateMethodParameterAnnotationOrMetaAnnotation.getSubclasses());
        }
        return new ClassInfoList(classesWithMethodParameterAnnotation,
                new HashSet<>(getClassesWithMethodParameterAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接方法注解的类
     *
     * @return 声明了被请求的方法注解直接标注(即非元注解标注)的方法的类的列表，如果没有则返回空列表
     */
    ClassInfoList getClassesWithMethodAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_METHOD_ANNOTATION, /* strictAccept = */ !isExternalClass),
                /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接方法参数注解的类
     *
     * @return 声明了被请求的方法注解直接标注(即非元注解标注)的方法参数的类的列表，如果没有则返回空列表
     */
    ClassInfoList getClassesWithMethodParameterAnnotationDirectOnly() {
        return new ClassInfoList(this.filterClassInfo(RelType.CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,
                /* strictAccept = */ !isExternalClass), /* sortByName = */ true);
    }

    /**
     * 返回此类声明(但不包括其超类声明)的所有可见字段的信息另请参见：
     *
     * <ul>
     * <li>{@link #getFieldInfo(String)}
     * <li>{@link #getDeclaredFieldInfo(String)}
     * <li>{@link #getFieldInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableFieldInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共字段的信息，除非在扫描前调用了 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @return 此类声明的可见字段的 FieldInfo 对象列表，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableFieldInfo()}
     */
    public FieldInfoList getDeclaredFieldInfo() {
        if (!scanResult.scanSpec.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        return fieldInfo == null ? FieldInfoList.EMPTY_LIST : fieldInfo;
    }

    /**
     * 返回此类或其超类声明的所有可见字段的信息另请参见：
     *
     * <ul>
     * <li>{@link #getFieldInfo(String)}
     * <li>{@link #getDeclaredFieldInfo(String)}
     * <li>{@link #getDeclaredFieldInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableFieldInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共字段的信息，除非在扫描前调用了 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @return 此类或其超类的可见字段的 FieldInfo 对象列表，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableFieldInfo()}
     */
    public FieldInfoList getFieldInfo() {
        if (!scanResult.scanSpec.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        // 实现字段覆盖
        final FieldInfoList fieldInfoList = new FieldInfoList();
        final Set<String> fieldNameSet = new HashSet<>();
        for (final ClassInfo ci : getFieldOverrideOrder()) {
            for (final FieldInfo fi : ci.getDeclaredFieldInfo()) {
                // 如果字段尚未被同名字段覆盖
                if (fieldNameSet.add(fi.getName())) {
                    // 将字段添加到输出顺序中
                    fieldInfoList.add(fi);
                }
            }
        }
        return fieldInfoList;
    }

    /**
     * 获取枚举类的枚举常量
     *
     * @return 枚举类的所有枚举常量，作为 {@link FieldInfo} 对象列表(枚举常量在 Java 类中存储为字段)
     */
    public FieldInfoList getEnumConstants() {
        if (!isEnum()) {
            throw new IllegalArgumentException("Class " + getName() + " is not an enum");
        }
        return getFieldInfo().filter(new FieldInfoFilter() {
            @Override
            public boolean accept(final FieldInfo fieldInfo) {
                return fieldInfo.isEnum();
            }
        });
    }

    /**
     * 获取枚举类的枚举常量对象
     *
     * @return 枚举类的所有枚举常量，作为与枚举类型相同类型的对象列表
     */
    public List<Object> getEnumConstantObjects() {
        if (!isEnum()) {
            throw new IllegalArgumentException("Class " + getName() + " is not an enum");
        }
        final Class<?> enumClass = loadClass();
        final FieldInfoList consts = getEnumConstants();
        final List<Object> constObjs = new ArrayList<>(consts.size());
        final ReflectionUtils reflectionUtils = scanResult == null ? new ReflectionUtils()
                : scanResult.reflectionUtils;
        for (final FieldInfo constFieldInfo : consts) {
            final Object constObj = reflectionUtils.getStaticFieldVal(true, enumClass, constFieldInfo.getName());
            if (constObj == null) {
                throw new IllegalArgumentException("Could not read enum constant objects");
            }
            constObjs.add(constObj);
        }
        return constObjs;
    }

    /**
     * 返回此类声明(但不包括其超类声明)的指定名称字段的信息另请参见：
     *
     * <ul>
     * <li>{@link #getFieldInfo(String)}
     * <li>{@link #getFieldInfo()}
     * <li>{@link #getDeclaredFieldInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableFieldInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共字段的信息，除非在扫描前调用了 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @param fieldName
     *            字段名
     * @return 此类声明的指定名称字段的 {@link FieldInfo} 对象，如果在此类中未找到该字段(或不可见)则返回 null
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableFieldInfo()}
     */
    public FieldInfo getDeclaredFieldInfo(final String fieldName) {
        if (!scanResult.scanSpec.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        if (fieldInfo == null) {
            return null;
        }
        for (final FieldInfo fi : fieldInfo) {
            if (fi.getName().equals(fieldName)) {
                return fi;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段

    /**
     * 返回此类或其超类声明的指定名称字段的信息另请参见：
     *
     * <ul>
     * <li>{@link #getDeclaredFieldInfo(String)}
     * <li>{@link #getFieldInfo()}
     * <li>{@link #getDeclaredFieldInfo()}
     * </ul>
     *
     * <p>
     * 需要在扫描前调用 {@link ClassGraph#enableFieldInfo()}，否则抛出
     * {@link IllegalArgumentException}
     *
     * <p>
     * 默认只返回公共字段的信息，除非在扫描前调用了 {@link ClassGraph#ignoreFieldVisibility()}
     *
     * @param fieldName
     *            字段名
     * @return 此类或其超类指定名称字段的 {@link FieldInfo} 对象，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException
     *             如果在启动扫描前未调用 {@link ClassGraph#enableFieldInfo()}
     */
    public FieldInfo getFieldInfo(final String fieldName) {
        if (!scanResult.scanSpec.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        // 实现字段覆盖
        for (final ClassInfo ci : getFieldOverrideOrder()) {
            final FieldInfo fi = ci.getDeclaredFieldInfo(fieldName);
            if (fi != null) {
                return fi;
            }
        }
        return null;
    }

    /**
     * 获取所有字段注解
     *
     * @return 此类字段上的所有注解列表，如果没有则返回空列表
     *         注意：这些注解不包含具体的注解参数——调用 {@link FieldInfo#getAnnotationInfo()}
     *         获取具体字段注解实例的详细信息
     */
    public ClassInfoList getFieldAnnotations() {
        return getFieldOrMethodAnnotations(RelType.FIELD_ANNOTATIONS);
    }

    /**
     * 获取以此类作为字段注解或元注解的类
     *
     * @return 具有带有此注解或元注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation() {
        // 获取所有具有被此注解标注或元标注的字段的类
        final Set<ClassInfo> classesWithMethodAnnotation = new HashSet<>(
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_FIELD_ANNOTATION));
        // 添加所有具有非私有的被此注解标注或元标注的字段的类的子类
        // (非私有字段会被继承)
        for (final ClassInfo classWithNonprivateMethodAnnotationOrMetaAnnotation : //
                getClassesWithFieldOrMethodAnnotation(RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION)) {
            classesWithMethodAnnotation.addAll(classWithNonprivateMethodAnnotationOrMetaAnnotation.getSubclasses());
        }
        return new ClassInfoList(classesWithMethodAnnotation,
                new HashSet<>(getClassesWithMethodAnnotationDirectOnly()), /* sortByName = */ true);
    }

    /**
     * 获取以此类作为直接字段注解的类
     *
     * @return 声明了被请求的方法注解直接标注(即非元注解标注)的字段的类的列表，如果没有则返回空列表
     */
    ClassInfoList getClassesWithFieldAnnotationDirectOnly() {
        return new ClassInfoList(
                this.filterClassInfo(RelType.CLASSES_WITH_FIELD_ANNOTATION, /* strictAccept = */ !isExternalClass),
                /* sortByName = */ true);
    }

    /**
     * 获取该类的已解析类型签名
     *
     * @return 该类的已解析类型签名，包括任何泛型类型参数，如果不可用则返回 null
     *         (可能表示该类不是泛型类)
     * @throws IllegalArgumentException
     *             如果类类型签名无法解析(这通常只在 class 文件损坏或编译器 bug
     *             导致无效的类型签名被写入 class 文件时才应抛出)
     */
    public ClassTypeSignature getTypeSignature() {
        synchronized (this) {
            if (typeSignatureStr == null) {
                return null;
            }
            if (typeSignature == null) {
                try {
                    typeSignature = ClassTypeSignature.parse(typeSignatureStr, this);
                    typeSignature.setScanResult(scanResult);
                    if (typeAnnotationDecorators != null) {
                        for (final ClassTypeAnnotationDecorator decorator : typeAnnotationDecorators) {
                            decorator.decorate(typeSignature);
                        }
                    }
                } catch (final ParseException e) {
                    throw new IllegalArgumentException("Invalid type signature for class " + getName()
                            + " in classpath element " + getClasspathElementURI() + " : " + typeSignatureStr, e);
                }
            }
        }
        return typeSignature;
    }

    /**
     * 设置类类型签名，包括任何类型参数
     *
     * @param typeSignatureStr
     *            类型签名字符串
     */
    void setTypeSignature(final String typeSignatureStr) {
        this.typeSignatureStr = typeSignatureStr;
    }

    /**
     * 获取该类的类型签名字符串
     *
     * @return 该类的类型签名字符串，包括任何泛型类型参数，如果不可用则返回 null
     *         (可能表示该类不是泛型类)
     */
    public String getTypeSignatureStr() {
        return typeSignatureStr;
    }

    /**
     * 返回该类的已解析类型签名，可能包含类型参数如果该类不存在类型签名(表示这不是泛型类)，
     * 则将合成并返回一个类型描述符，就好像存在类型描述符一样(class 文件可能有类型签名但不包含类型描述符)
     * 可能包含超类或接口上的类型注解
     *
     * @return 该类的已解析泛型类型签名，如果不可用则返回该类的合成类型描述符
     */
    public ClassTypeSignature getTypeSignatureOrTypeDescriptor() {
        ClassTypeSignature typeSig = null;
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

    /**
     * 返回该类的合成类型描述符，由类名、超类名和实现的接口创建
     * 可能包含超类或接口上的类型注解
     *
     * @return 该类的合成类型描述符
     */
    public ClassTypeSignature getTypeDescriptor() {
        synchronized (this) {
            if (typeDescriptor == null) {
                typeDescriptor = new ClassTypeSignature(this, getSuperclass(), getInterfaces());
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

    /**
     * 返回编译该类的源文件名，例如 {@code ClassInfo.java} 或 {@code KClass.kt}
     *
     * <p>
     * 此字段可能为 {@code null}
     *
     * @return 该类的源文件名，如果不可用则返回 {@code null}
     */
    public String getSourceFile() {
        return sourceFile;
    }

    /**
     * 设置源文件
     *
     * @param sourceFile
     *            源文件
     */
    public void setSourceFile(final String sourceFile) {
        this.sourceFile = sourceFile;
    }

    /**
     * 获取发现该类的类路径元素的 {@link URI}
     *
     * @return 发现该类的类路径元素的 {@link URI}
     * @throws IllegalArgumentException
     *             如果类路径元素没有有效的 URI(例如对于位置 URI 为 null 的模块)
     */
    public URI getClasspathElementURI() {
        // 调用 classfileResource.getClasspathElementURI() 而非 classpathElement.getURI()
        // 将自动追加任何被自动剥离的包根前缀
        if (classfileResource == null) {
            throw new IllegalArgumentException("Classfile resource is null for " + getName());
        }
        return classfileResource.getClasspathElementURI();
    }

    /**
     * 获取发现该类的类路径元素或模块的 {@link URL}如果资源可能来自系统模块或
     * jlink 运行时镜像，请改用 {@link #getClasspathElementURI()}，因为系统模块和
     * jlink 运行时镜像使用的 "jrt:" URI 方案不被 {@link URL} 支持，会导致抛出
     * {@link IllegalArgumentException}
     *
     * @return 发现该类的类路径元素的 {@link URL}
     * @throws IllegalArgumentException
     *             如果类路径元素 URI 无法转换为 {@link URL}
     *             (特别是当 URI 具有 {@code jrt:/} 方案时)
     */
    public URL getClasspathElementURL() {
        try {
            return getClasspathElementURI().toURL();
        } catch (final IllegalArgumentException | MalformedURLException e) {
            throw new IllegalArgumentException("Could not get classpath element URL", e);
        }
    }

    /**
     * 获取发现该类的类路径元素包根目录或 jar 的 {@link File}，
     * 如果该类在模块中发现则返回 null(另请参见 {@link #getModuleRef})
     *
     * @return 发现该类的类路径元素包根目录或 jar 的 {@link File}，
     *         如果该类在模块中发现则返回 null(参见 {@link #getModuleRef})
     *         如果类路径元素是 http/https URL 且 jar 直接下载到 RAM 而非磁盘上的临时文件
     *         (例如临时目录不可写)，也可能返回 null
     */
    public File getClasspathElementFile() {
        if (classpathElement == null) {
            throw new IllegalArgumentException("Classpath element is not known for this classpath element");
        }
        return classpathElement.getFile();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取发现该类的模块，作为 {@link ModuleRef}，如果该类在类路径中的目录或 jar 中发现则返回 null
     * (另请参见 {@link #getClasspathElementFile()})
     *
     * @return 发现该类的模块，作为 {@link ModuleRef}，如果该类在类路径中的目录或 jar 中发现则返回 null
     *         (另请参见 {@link #getClasspathElementFile()})
     */
    public ModuleRef getModuleRef() {
        if (classpathElement == null) {
            throw new IllegalArgumentException("Classpath element is not known for this classpath element");
        }
        return classpathElement instanceof ClasspathElementModule
                ? ((ClasspathElementModule) classpathElement).getModuleRef()
                : null;
    }

    /**
     * 该类 class 文件的 {@link Resource}
     *
     * @return 该类 class 文件的 {@link Resource}如果该类的 class 文件在扫描期间实际未被读取，
     *         例如因为该类本身未被接受但被一个已接受的类引用，则返回 null
     */
    public Resource getResource() {
        return classfileResource;
    }

    /**
     * 获取此 {@link ClassInfo} 对象所命名类的 {@code Class<?>} 引用，并将其转换为
     * 请求的接口或超类类型如果类尚未加载，则使 ClassLoader 加载该类
     *
     * <p>
     * <b>重要提示：</b>由于 {@code superclassOrInterfaceType} 是已加载类的类引用，
     * 因此至关重要的是 {@code superclassOrInterfaceType} 必须由与此 {@code ClassInfo}
     * 对象引用的类相同的类加载器加载，否则类转换将失败
     *
     * @param <T>
     *            超类或接口类型
     * @param superclassOrInterfaceType
     *            要将加载的类转换到的类型的 {@link Class} 引用
     * @param ignoreExceptions
     *            如果为 true，在类加载期间抛出任何异常或错误，或尝试将结果
     *            {@code Class<?>} 引用转换为请求的超类或接口类型失败时返回 null
     *            如果为 false，当类无法加载或无法转换为请求的类型时抛出
     *            {@link IllegalArgumentException}
     * @return 类引用，如果 ignoreExceptions 为 true 且加载类时发生异常或错误则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false 且加载类或将其转换为请求的类型时出现问题
     */
    @Override
    public <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType, final boolean ignoreExceptions) {
        return super.loadClass(superclassOrInterfaceType, ignoreExceptions);
    }

    /**
     * 获取此 {@link ClassInfo} 对象所命名类的 {@code Class<?>} 引用，并将其转换为
     * 请求的接口或超类类型如果类尚未加载，则使 ClassLoader 加载该类
     *
     * <p>
     * <b>重要提示：</b>由于 {@code superclassOrInterfaceType} 是已加载类的类引用，
     * 因此至关重要的是 {@code superclassOrInterfaceType} 必须由与此 {@code ClassInfo}
     * 对象引用的类相同的类加载器加载，否则类转换将失败
     *
     * @param <T>
     *            超类或接口类型
     * @param superclassOrInterfaceType
     *            要将加载的类转换到的类型
     * @return 类引用
     * @throws IllegalArgumentException
     *             如果加载类或将其转换为请求的类型时出现问题
     */
    @Override
    public <T> Class<T> loadClass(final Class<T> superclassOrInterfaceType) {
        return super.loadClass(superclassOrInterfaceType, /* ignoreExceptions = */ false);
    }

    /**
     * 获取此 {@link ClassInfo} 对象所命名类的 {@code Class<?>} 引用
     * 如果类尚未加载，则使 ClassLoader 加载该类
     *
     * @param ignoreExceptions
     *            是否忽略异常
     * @return 类引用，如果 ignoreExceptions 为 true 且加载类时发生异常或错误则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false 且加载类时出现问题
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        return super.loadClass(ignoreExceptions);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此 {@link ClassInfo} 对象所命名类的 {@code Class<?>} 引用
     * 如果类尚未加载，则使 ClassLoader 加载该类
     *
     * @return 类引用
     * @throws IllegalArgumentException
     *             如果加载类时出现问题
     */
    @Override
    public Class<?> loadClass() {
        return super.loadClass(/* ignoreExceptions = */ false);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassName()
     */
    @Override
    protected String getClassName() {
        return name;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#getClassInfo()
     */
    @Override
    protected ClassInfo getClassInfo() {
        return this;
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ScanResultObject#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    void setScanResult(final ScanResult scanResult) {
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

    /**
     * 处理 {@link Repeatable} 注解
     *
     * @param allRepeatableAnnotationNames
     *            所有可重复注解的名称集合
     */
    void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames) {
        if (annotationInfo != null) {
            annotationInfo.handleRepeatableAnnotations(allRepeatableAnnotationNames, this,
                    RelType.CLASS_ANNOTATIONS, RelType.CLASSES_WITH_ANNOTATION, null);
        }
        if (fieldInfo != null) {
            for (final FieldInfo fi : fieldInfo) {
                fi.handleRepeatableAnnotations(allRepeatableAnnotationNames);
            }
        }
        if (methodInfo != null) {
            for (final MethodInfo mi : methodInfo) {
                mi.handleRepeatableAnnotations(allRepeatableAnnotationNames);
            }
        }
    }

    /**
     * 添加此类引用的类名
     *
     * @param refdClassNames
     *            引用的类名集合
     */
    void addReferencedClassNames(final Set<String> refdClassNames) {
        if (this.referencedClassNames == null) {
            this.referencedClassNames = refdClassNames;
        } else {
            this.referencedClassNames.addAll(refdClassNames);
        }
    }

    /**
     * 获取此类类型描述符或字段、方法、注解类型描述符中引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息集合
     * @param log
     *            日志
     */
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
        getMethodInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        getFieldInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        getAnnotationInfo().findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        if (annotationDefaultParamValues != null) {
            annotationDefaultParamValues.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
        try {
            final ClassTypeSignature classSig = getTypeSignature();
            if (classSig != null) {
                classSig.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("Illegal type signature for class " + getClassName() + ": " + getTypeSignatureStr());
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 设置此类引用的类的 ClassInfo 对象列表
     *
     * @param refdClasses
     *            引用的类列表
     */
    void setReferencedClasses(final ClassInfoList refdClasses) {
        this.referencedClasses = refdClasses;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类依赖
     *
     * @return 此类引用的所有类的 {@link ClassInfoList} 列表注意，需要在
     *         {@link ClassGraph#scan()} 前调用 {@link ClassGraph#enableInterClassDependencies()}
     *         此方法才能生效如果希望未接受的类也出现在结果中，还应在此之前调用
     *         {@link ClassGraph#enableExternalClasses()}
     */
    public ClassInfoList getClassDependencies() {
        if (!scanResult.scanSpec.enableInterClassDependencies) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableInterClassDependencies() before #scan()");
        }
        return referencedClasses == null ? ClassInfoList.EMPTY_LIST : referencedClasses;
    }

    /**
     * 基于类名进行比较
     *
     * @param o
     *            另一个对象
     * @return 比较结果
     */
    @Override
    public int compareTo(final ClassInfo o) {
        return this.name.compareTo(o.name);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 对 equals() 使用类名
     *
     * @param obj
     *            另一个对象
     * @return 对象是否相等
     */
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

    /**
     * 使用类名的哈希码
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return name == null ? 0 : name.hashCode();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 转换为字符串
     *
     * @param useSimpleNames
     *            是否使用简单名称
     * @param buf
     *            字符串缓冲区
     */
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
        ClassTypeSignature typeSig = null;
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
            TypeUtils.modifiersToString(modifiers, ModifierType.CLASS, /* ignored */ false, buf);
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
                for (final FieldInfo fieldInfo : getFieldInfo()) {
                    if (!isFirstParam) {
                        buf.append(", ");
                    } else {
                        isFirstParam = false;
                    }
                    fieldInfo.toString(/* useModifiers = */ false, /* useSimpleNames = */ false, buf);
                }
                buf.append(')');
            }
            final ClassInfo superclass = getSuperclass();
            if (superclass != null && !"java.lang.Object".equals(superclass.getName())) {
                buf.append(" extends ");
                superclass.toString(useSimpleNames, buf);
            }
            final Set<ClassInfo> interfaces = this.filterClassInfo(RelType.IMPLEMENTED_INTERFACES,
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

    /** 类之间的关系类型 */
    enum RelType {

        // 类关系：

        /**
         * 此类的超类，如果这是普通类的话
         *
         * <p>
         * (应该只包含一个条目，如果超类是 java.lang.Object 或未知则为 null)
         */
        SUPERCLASSES,

        /** 此类的子类，如果这是普通类的话 */
        SUBCLASSES,

        /** 表示一个内部类包含在此类中 */
        CONTAINS_INNER_CLASS,

        /** 表示一个外部类包含此类(应该只有零个或一个条目) */
        CONTAINED_WITHIN_OUTER_CLASS,

        // 接口关系：

        /**
         * 此类实现的接口(如果这是普通类的话)，或超接口(如果这是接口的话)
         *
         * <p>
         * (也可能包含注解，因为注解是接口，所以可以实现一个注解)
         */
        IMPLEMENTED_INTERFACES,

        /**
         * 实现此接口的类(包括子接口)，如果这是接口的话
         */
        CLASSES_IMPLEMENTING,

        // 类注解关系：

        /**
         * 此类上的注解(如果这是普通类的话)，或此注解上的元注解(如果这是注解的话)
         */
        CLASS_ANNOTATIONS,

        /** 被此注解标注的类，如果这是注解的话 */
        CLASSES_WITH_ANNOTATION,

        // 方法注解关系：

        /** 此类一个或多个方法上的注解 */
        METHOD_ANNOTATIONS,

        /**
         * 具有一个或多个被此注解标注的方法的类，如果这是注解的话
         */
        CLASSES_WITH_METHOD_ANNOTATION,

        /**
         * 具有一个或多个被此注解标注的非私有(可继承)方法的类，如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_METHOD_ANNOTATION,

        /** 此类一个或多个方法参数上的注解 */
        METHOD_PARAMETER_ANNOTATIONS,

        /**
         * 具有一个或多个方法，其一个或多个参数被此注解标注的类，如果这是注解的话
         */
        CLASSES_WITH_METHOD_PARAMETER_ANNOTATION,

        /**
         * 具有一个或多个非私有(可继承)方法，其一个或多个参数被此注解标注的类，
         * 如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_METHOD_PARAMETER_ANNOTATION,

        // 字段注解关系：

        /** 此类一个或多个字段上的注解 */
        FIELD_ANNOTATIONS,

        /**
         * 具有一个或多个被此注解标注的字段的类，如果这是注解的话
         */
        CLASSES_WITH_FIELD_ANNOTATION,

        /**
         * 具有一个或多个被此注解标注的非私有(可继承)字段的类，如果这是注解的话
         */
        CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION,
    }

    /** 要返回的类类型 */
    private enum ClassType {
        /** 获取所有类类型 */
        ALL,
        /** 标准类(非接口或注解) */
        STANDARD_CLASS,
        /**
         * 接口(此处命名为"已实现接口"而非简单的"接口"，以区别于注解)
         */
        IMPLEMENTED_INTERFACE,
        /** 注解 */
        ANNOTATION,
        /** 接口或注解(因为实际上可以实现一个注解) */
        INTERFACE_OR_ANNOTATION,
        /** 枚举 */
        ENUM,
        /** 记录类型 */
        RECORD
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 对于给定关系类型，通过有向路径间接可达的类集合，以及直接关联
     * (仅一步关系距离)的类集合
     */
    static class ReachableAndDirectlyRelatedClasses {

        /** 可达的类 */
        final Set<ClassInfo> reachableClasses;

        /** 直接关联的类 */
        final Set<ClassInfo> directlyRelatedClasses;

        /**
         * 构造函数
         *
         * @param reachableClasses
         *            可达的类
         * @param directlyRelatedClasses
         *            直接关联的类
         */
        private ReachableAndDirectlyRelatedClasses(final Set<ClassInfo> reachableClasses,
                                                   final Set<ClassInfo> directlyRelatedClasses) {
            this.reachableClasses = reachableClasses;
            this.directlyRelatedClasses = directlyRelatedClasses;
        }
    }
}
