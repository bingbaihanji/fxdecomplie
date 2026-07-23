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
package com.bingbaihanji.classgraph.bytecode;

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.type.*;
import com.bingbaihanji.classgraph.resource.*;
import com.bingbaihanji.classgraph.classpath.*;
import com.bingbaihanji.classgraph.scan.*;
import com.bingbaihanji.classgraph.util.*;
import com.bingbaihanji.classgraph.reflect.*;
import com.bingbaihanji.classgraph.bytecode.*;

import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.util.WorkQueue;
import com.bingbaihanji.classgraph.scan.Scanner.ClassfileScanWorkUnit;
import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.type.ParseException;
import com.bingbaihanji.classgraph.util.CollectionUtils;
import com.bingbaihanji.classgraph.util.JarUtils;
import com.bingbaihanji.classgraph.util.LogNode;
import com.bingbaihanji.classgraph.util.StringUtils;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClassParser 二进制格式解析器实现了自己的缓冲机制以避免使用 DataInputStream 的开销
 * 此类在同一时间仅应由单线程使用，但可以重复用于按顺序扫描多个 ClassParser，以避免重新分配缓冲区内存
 *
 * <p>
 * 参见 <a href="https://docs.oracle.com/javase/specs/jvms/se16/html/jvms-4.html">class 文件格式规范</a>
 */
public class ClassParser {
    /** 当没有注解时使用的空数组 */
    private static final AnnotationInfo[] NO_ANNOTATIONS = new AnnotationInfo[0];
    /** 包含此 ClassParser 的类路径元素 */
    private final Classpath Classpath;

    /** 类路径顺序 */
    private final List<Classpath> classpathOrder;

    /** ClassParser 的相对路径(应对应于 className) */
    private final String relativePath;

    /** ClassParser 资源 */
    private final Resource classfileResource;

    /** 字符串内部化映射 */
    private final ConcurrentHashMap<String, String> stringInternMap;
    /** 此是否为外部类 */
    private final boolean isExternalClass;
    /** 在类路径元素内扫描路径时发现的已接受类名集合 */
    private final Set<String> acceptedClassNamesFound;
    /**
     * 已安排进行扩展扫描的外部(非接受)类名集合(扫描向上扩展到超类、接口和注解)
     */
    private final Set<String> classNamesScheduledForExtendedScanning;
    /** 扫描规格 */
    private final ScanConfig ScanConfig;
    /** 当前 ClassParser 的 {@link ClassFileReader} */
    private ClassFileReader reader;
    /** 类名 */
    private String className;
    /** ClassParser 格式的次版本号 */
    private int minorVersion;
    /** ClassParser 格式的主版本号 */
    private int majorVersion;
    /** 类修饰符 */
    private int classModifiers;
    /** 此类是否为接口 */
    private boolean isInterface;
    /** 此类是否为记录类(record) */
    private boolean isRecord;
    /** 此类是否为注解 */
    private boolean isAnnotation;
    /** 超类名(如果无超类或超类被拒绝，则可为 null) */
    private String superclassName;
    /** 实现的接口 */
    private List<String> implementedInterfaces;
    /** 类注解 */
    private AnnotationInfoList classAnnotations;
    /** 定义方法的完全限定名称 */
    private String fullyQualifiedDefiningMethodName;
    /** 类包含关系条目 */
    private List<ClassContainment> classContainmentEntries;
    /** 注解默认参数值 */
    private AnnotationParameterValueList annotationParamDefaultValues;
    /** 引用的类名 */
    private Set<String> refdClassNames;
    /** 字段信息列表 */
    private FieldInfoList fieldInfoList;
    /** 方法信息列表 */
    private MethodInfoList methodInfoList;
    /** 类型签名 */
    private String typeSignatureStr;
    /** 源文件，如 ClassParser.java */
    private String sourceFile;
    /** {@link ClassType} 实例的类型注解装饰器 */
    private List<ClassTypeAnnotationDecorator> classTypeAnnotationDecorators;

    // -------------------------------------------------------------------------------------------------------------
    /** 任何已安排扫描的额外工作单元 */
    private List<ClassfileScanWorkUnit> additionalWorkUnits;
    /** 常量池条目数加一 */
    private int cpCount;
    /** 常量池中每个条目起始位置的字节偏移量 */
    private int[] entryOffset;
    /** 常量池中每个条目的标签(类型) */
    private int[] entryTag;

    // -------------------------------------------------------------------------------------------------------------
    /** 常量池中 String/Class 条目的间接引用索引 */
    private int[] indirectStringRefs;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 直接检查 ClassParser 二进制头内容以确定注解、实现的接口、超类等
     * 创建一个新的 ClassInfo 对象，并将其添加到 classNameToClassInfoOut 中
     * 假设类路径掩码已执行，因此只会添加一个给定名称的类
     *
     * @param Classpath 类路径元素
     * @param classpathOrder  类路径顺序
     * @param acceptedClassNamesFound 在类路径元素内扫描路径时发现的已接受类名集合
     * @param classNamesScheduledForExtendedScanning 已安排进行扩展扫描的外部(非接受)类名集合(扫描向上扩展到超类、接口和注解)
     * @param relativePath 相对路径
     * @param classfileResource  ClassParser 资源
     * @param isExternalClass  此是否为外部类
     * @param stringInternMap 字符串内部化映射
     * @param workQueue 工作队列
     * @param ScanConfig 扫描规格
     * @param log 日志
     * @throws IOException  如果发生 I/O 异常
     * @throws ClassfileFormatException 如果解析 ClassParser 时发生问题
     * @throws SkipClassException 如果 ClassParser 需要被跳过(例如类为非公开的，且 ignoreClassVisibility 为 false)
     */
    public ClassParser(final Classpath Classpath, final List<Classpath> classpathOrder,
              final Set<String> acceptedClassNamesFound, final Set<String> classNamesScheduledForExtendedScanning,
              final String relativePath, final Resource classfileResource, final boolean isExternalClass,
              final ConcurrentHashMap<String, String> stringInternMap,
              final WorkQueue<ClassfileScanWorkUnit> workQueue, final ScanConfig ScanConfig, final LogNode log)
            throws IOException, ClassfileFormatException, SkipClassException {
        this.Classpath = Classpath;
        this.classpathOrder = classpathOrder;
        this.relativePath = relativePath;
        this.acceptedClassNamesFound = acceptedClassNamesFound;
        this.classNamesScheduledForExtendedScanning = classNamesScheduledForExtendedScanning;
        this.classfileResource = classfileResource;
        this.isExternalClass = isExternalClass;
        this.stringInternMap = stringInternMap;
        this.ScanConfig = ScanConfig;

        // 为 ClassParser 打开一个 BufferedSequentialReader
        try (ClassFileReader ClassFileReader = classfileResource.openClassfile()) {
            reader = ClassFileReader;

            // 检查魔数
            if (reader.readInt() != 0xCAFEBABE) {
                throw new ClassfileFormatException("ClassParser does not have correct magic number");
            }

            // 读取 ClassParser 次版本号和主版本号
            minorVersion = reader.readUnsignedShort();
            majorVersion = reader.readUnsignedShort();

            // 读取常量池
            readConstantPoolEntries(log);

            // 读取基本类信息
            readBasicClassInfo();

            // 读取接口
            readInterfaces();

            // 读取字段
            readFields();

            // 读取方法
            readMethods();

            // 读取类属性
            readClassAttributes();

            reader = null;
        }

        // 将类信息写入日志
        final LogNode subLog = log == null ? null
                : log.log("Found " //
                + (isAnnotation ? "annotation class" : isInterface ? "interface class" : "class") //
                + " " + className);
        if (subLog != null) {
            if (superclassName != null) {
                subLog.log(
                        "Super" + (isInterface && !isAnnotation ? "interface" : "class") + ": " + superclassName);
            }
            if (implementedInterfaces != null) {
                subLog.log("Interfaces: " + StringUtils.join(", ", implementedInterfaces));
            }
            if (classAnnotations != null) {
                subLog.log("Class annotations: " + StringUtils.join(", ", classAnnotations));
            }
            if (annotationParamDefaultValues != null) {
                for (final AnnotationParameterValue apv : annotationParamDefaultValues) {
                    subLog.log("Annotation default param value: " + apv);
                }
            }
            if (fieldInfoList != null) {
                for (final FieldInfo fieldInfo : fieldInfoList) {
                    final String modifierStr = fieldInfo.getModifiersStr();
                    subLog.log("Field: " + modifierStr + (modifierStr.isEmpty() ? "" : " ") + fieldInfo.getName());
                }
            }
            if (methodInfoList != null) {
                for (final MethodInfo methodInfo : methodInfoList) {
                    final String modifierStr = methodInfo.getModifiersStr();
                    subLog.log(
                            "Method: " + modifierStr + (modifierStr.isEmpty() ? "" : " ") + methodInfo.getName());
                }
            }
            if (typeSignatureStr != null) {
                subLog.log("Class type signature: " + typeSignatureStr);
            }
            if (refdClassNames != null) {
                final List<String> refdClassNamesSorted = new ArrayList<>(refdClassNames);
                CollectionUtils.sortIfNotEmpty(refdClassNamesSorted);
                subLog.log("Additional referenced class names: " + StringUtils.join(", ", refdClassNamesSorted));
            }
        }

        // 检查是否有任何超类、接口或注解是外部(非接受)类，需要安排扫描，
        // 以便为任何接受类扫描类图的整个"向上"方向，即使超类/接口/注解本身不是接受类
        if (ScanConfig.extendScanningUpwardsToExternalClasses) {
            extendScanningUpwards(subLog);
            // 如果发现任何外部类，安排它们进行扫描
            if (additionalWorkUnits != null) {
                workQueue.addWorkUnits(additionalWorkUnits);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将扫描扩展到超类、接口或注解
     *
     * @param className
     *            类名
     * @param relationship
     *            关系类型
     * @param log
     *            日志
     */
    private void scheduleScanningIfExternalClass(final String className, final String relationship,
                                                 final LogNode log) {
        // 不扫描 Object
        if (className != null && !"java.lang.Object".equals(className)
                // 不安排已经发现为接受类的类进行扫描
                && !acceptedClassNamesFound.contains(className)
                // 在所有线程中，每个外部类只安排一次扫描
                && classNamesScheduledForExtendedScanning.add(className)) {
            if (ScanConfig.classAcceptReject.isRejected(className)) {
                if (log != null) {
                    log.log("Cannot extend scanning upwards to external " + relationship + " " + className
                            + ", since it is rejected");
                }
            } else {
                // 在类路径元素中按类路径顺序搜索指定类的 ClassParser(这对每个类是 O(N)，
                // 但向上扩展扫描的情况不应太多)
                final String classfilePath = JarUtils.classNameToClassfilePath(className);
                // 首先检查当前类路径元素，避免遍历其他类路径元素
                Resource classResource = Classpath.getResource(classfilePath);
                Classpath foundInClasspathElt = null;
                if (classResource != null) {
                    // 在当前类路径元素中找到了 ClassParser
                    foundInClasspathElt = Classpath;
                } else {
                    // 在当前类路径元素中未找到 ClassParser -- 遍历其他元素
                    for (final Classpath classpathOrderElt : classpathOrder) {
                        if (classpathOrderElt != Classpath) {
                            classResource = classpathOrderElt.getResource(classfilePath);
                            if (classResource != null) {
                                foundInClasspathElt = classpathOrderElt;
                                break;
                            }
                        }
                    }
                }
                if (classResource != null) {
                    // 找到了类资源
                    if (log != null) {
                        // 将扩展扫描记录为当前类扫描日志的子 LogNode，因为外部类不是在
                        // 类路径元素层次结构遍历中的常规位置扫描的
                        classResource.scanLog = log
                                .log("Extending scanning to external " + relationship
                                        + (foundInClasspathElt == Classpath ? " in same classpath element"
                                        : " in classpath element " + foundInClasspathElt)
                                        + ": " + className);
                    }
                    if (additionalWorkUnits == null) {
                        additionalWorkUnits = new ArrayList<>();
                    }
                    // 安排类资源进行扫描
                    additionalWorkUnits.add(new ClassfileScanWorkUnit(foundInClasspathElt, classResource,
                            /* isExternalClass = */ true));
                } else {
                    if (log != null) {
                        log.log("External " + relationship + " " + className + " was not found in "
                                + "non-rejected packages -- cannot extend scanning to this class");
                    }
                }
            }
        }
    }

    /**
     * 检查是否需要从注解参数值向上扩展扫描
     *
     * @param annotationParamVal
     *            注解或注解参数值的 {@link AnnotationInfo} 对象
     * @param log
     *            日志
     */
    private void extendScanningUpwardsFromAnnotationParameterValues(final Object annotationParamVal,
                                                                    final LogNode log) {
        if (annotationParamVal == null) {
            // 不应发生 -- 忽略
        } else if (annotationParamVal instanceof AnnotationInfo) {
            final AnnotationInfo annotationInfo = (AnnotationInfo) annotationParamVal;
            scheduleScanningIfExternalClass(annotationInfo.getClassName(), "annotation class", log);
            for (final AnnotationParameterValue apv : annotationInfo.getParameterValues()) {
                extendScanningUpwardsFromAnnotationParameterValues(apv.getValue(), log);
            }
        } else if (annotationParamVal instanceof AnnotationEnumValue) {
            scheduleScanningIfExternalClass(((AnnotationEnumValue) annotationParamVal).getClassName(), "enum class",
                    log);
        } else if (annotationParamVal instanceof AnnotationClassRef) {
            scheduleScanningIfExternalClass(((AnnotationClassRef) annotationParamVal).getClassName(), "class ref",
                    log);
        } else if (annotationParamVal.getClass().isArray()) {
            for (int i = 0, n = Array.getLength(annotationParamVal); i < n; i++) {
                extendScanningUpwardsFromAnnotationParameterValues(Array.get(annotationParamVal, i), log);
            }
        } else {
            // String 等 -- 忽略
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查是否需要向上扩展到外部超类、接口或注解进行扫描
     *
     * @param log
     *            日志
     */
    private void extendScanningUpwards(final LogNode log) {
        // 检查超类
        if (superclassName != null) {
            scheduleScanningIfExternalClass(superclassName, "superclass", log);
        }
        // 检查实现的接口
        if (implementedInterfaces != null) {
            for (final String interfaceName : implementedInterfaces) {
                scheduleScanningIfExternalClass(interfaceName, "interface", log);
            }
        }
        // 检查类注解
        if (classAnnotations != null) {
            for (final AnnotationInfo annotationInfo : classAnnotations) {
                scheduleScanningIfExternalClass(annotationInfo.getName(), "class annotation", log);
                extendScanningUpwardsFromAnnotationParameterValues(annotationInfo, log);
            }
        }
        // 检查注解默认参数值
        if (annotationParamDefaultValues != null) {
            for (final AnnotationParameterValue apv : annotationParamDefaultValues) {
                extendScanningUpwardsFromAnnotationParameterValues(apv.getValue(), log);
            }
        }
        // 检查方法注解和方法参数注解
        if (methodInfoList != null) {
            for (final MethodInfo methodInfo : methodInfoList) {
                if (methodInfo.annotationInfo != null) {
                    for (final AnnotationInfo methodAnnotationInfo : methodInfo.annotationInfo) {
                        scheduleScanningIfExternalClass(methodAnnotationInfo.getName(), "method annotation", log);
                        extendScanningUpwardsFromAnnotationParameterValues(methodAnnotationInfo, log);
                    }
                    if (methodInfo.parameterAnnotationInfo != null
                            && methodInfo.parameterAnnotationInfo.length > 0) {
                        for (final AnnotationInfo[] paramAnnInfoArr : methodInfo.parameterAnnotationInfo) {
                            if (paramAnnInfoArr != null && paramAnnInfoArr.length > 0) {
                                for (final AnnotationInfo paramAnnInfo : paramAnnInfoArr) {
                                    scheduleScanningIfExternalClass(paramAnnInfo.getName(),
                                            "method parameter annotation", log);
                                    extendScanningUpwardsFromAnnotationParameterValues(paramAnnInfo, log);
                                }
                            }
                        }
                    }
                }
                if (methodInfo.getThrownExceptionNames() != null) {
                    for (final String thrownExceptionName : methodInfo.getThrownExceptionNames()) {
                        scheduleScanningIfExternalClass(thrownExceptionName, "method throws", log);
                    }
                }
            }
        }
        // 检查字段注解
        if (fieldInfoList != null) {
            for (final FieldInfo fieldInfo : fieldInfoList) {
                if (fieldInfo.annotationInfo != null) {
                    for (final AnnotationInfo fieldAnnotationInfo : fieldInfo.annotationInfo) {
                        scheduleScanningIfExternalClass(fieldAnnotationInfo.getName(), "field annotation", log);
                        extendScanningUpwardsFromAnnotationParameterValues(fieldAnnotationInfo, log);
                    }
                }
            }
        }
        // 检查此类是否为内部类，如果是，则将扫描扩展到外部类
        if (classContainmentEntries != null) {
            for (final ClassContainment classContainmentEntry : classContainmentEntries) {
                if (classContainmentEntry.innerClassName.equals(className)) {
                    scheduleScanningIfExternalClass(classContainmentEntry.outerClassName, "outer class", log);
                }
            }
        }
    }

    /**
     * 链接类不是线程安全的，应在单线程上下文中运行
     *
     * @param classNameToClassInfo
     *            类名到类信息的映射
     * @param packageNameToPackageInfo
     *            包名到包信息的映射
     * @param moduleNameToModuleInfo
     *            模块名到模块信息的映射
     */
    public void link(final Map<String, ClassInfo> classNameToClassInfo,
              final Map<String, PackageInfo> packageNameToPackageInfo,
              final Map<String, ModuleInfo> moduleNameToModuleInfo) {
        boolean isModuleDescriptor = false;
        boolean isPackageDescriptor = false;
        ClassInfo classInfo = null;
        if ("module-info".equals(className)) {
            isModuleDescriptor = true;

        } else if ("package-info".equals(className) || className.endsWith(".package-info")) {
            isPackageDescriptor = true;

        } else {
            // 处理常规 ClassParser
            classInfo = ClassInfo.addScannedClass(className, classModifiers, isExternalClass, classNameToClassInfo,
                    Classpath, classfileResource);
            classInfo.setClassfileVersion(minorVersion, majorVersion);
            classInfo.setModifiers(classModifiers);
            classInfo.setIsInterface(isInterface);
            classInfo.setIsAnnotation(isAnnotation);
            classInfo.setIsRecord(isRecord);
            classInfo.setSourceFile(sourceFile);
            if (superclassName != null) {
                classInfo.addSuperclass(superclassName, classNameToClassInfo);
            }
            if (implementedInterfaces != null) {
                for (final String interfaceName : implementedInterfaces) {
                    classInfo.addImplementedInterface(interfaceName, classNameToClassInfo);
                }
            }
            if (classAnnotations != null) {
                for (final AnnotationInfo classAnnotation : classAnnotations) {
                    classInfo.addClassAnnotation(classAnnotation, classNameToClassInfo);
                }
            }
            if (classContainmentEntries != null) {
                ClassInfo.addClassContainment(classContainmentEntries, classNameToClassInfo);
            }
            if (annotationParamDefaultValues != null) {
                classInfo.addAnnotationParamDefaultValues(annotationParamDefaultValues);
            }
            if (fullyQualifiedDefiningMethodName != null) {
                classInfo.addFullyQualifiedDefiningMethodName(fullyQualifiedDefiningMethodName);
            }
            if (fieldInfoList != null) {
                classInfo.addFieldInfo(fieldInfoList, classNameToClassInfo);
            }
            if (methodInfoList != null) {
                classInfo.addMethodInfo(methodInfoList, classNameToClassInfo);
            }
            if (typeSignatureStr != null) {
                classInfo.setTypeSignature(typeSignatureStr);
            }
            if (refdClassNames != null) {
                classInfo.addReferencedClassNames(refdClassNames);
            }
            if (classTypeAnnotationDecorators != null) {
                classInfo.addTypeDecorators(classTypeAnnotationDecorators);
            }
        }

        // 获取或创建 PackageInfo，如果这不是模块描述符(模块描述符的包是 "")
        PackageInfo packageInfo = null;
        if (!isModuleDescriptor) {
            // 获取此类或包描述符的包
            final String packageName = PackageInfo.getParentPackageName(className);
            packageInfo = PackageInfo.getOrCreatePackage(packageName, packageNameToPackageInfo, ScanConfig);
            if (isPackageDescriptor) {
                // 将 package-info.class 文件上的任何类注解添加到 ModuleInfo
                packageInfo.addAnnotations(classAnnotations);
            } else if (classInfo != null) {
                // 将 ClassInfo 添加到 PackageInfo，反之亦然
                packageInfo.addClassInfo(classInfo);
                classInfo.packageInfo = packageInfo;
            }
        }

        // 获取或创建 ModuleInfo，如果有模块名
        final String moduleName = Classpath.getModuleName();
        if (moduleName != null) {
            // 获取或创建此模块的 ModuleInfo 对象
            ModuleInfo moduleInfo = moduleNameToModuleInfo.get(moduleName);
            if (moduleInfo == null) {
                moduleNameToModuleInfo.put(moduleName,
                        moduleInfo = new ModuleInfo(classfileResource.getModuleRef(), Classpath));
            }
            if (isModuleDescriptor) {
                // 将 module-info.class 文件上的任何类注解添加到 ModuleInfo
                moduleInfo.addAnnotations(classAnnotations);
            }
            if (classInfo != null) {
                // 将 ClassInfo 添加到 ModuleInfo，反之亦然
                moduleInfo.addClassInfo(classInfo);
                classInfo.moduleInfo = moduleInfo;
            }
            if (packageInfo != null) {
                // 将 PackageInfo 添加到 ModuleInfo
                moduleInfo.addPackageInfo(packageInfo);
            }
        }
    }

    /**
     * 内部化字符串
     *
     * @param str
     *            字符串
     * @return 内部化后的字符串
     */
    private String intern(final String str) {
        if (str == null) {
            return null;
        }
        final String interned = stringInternMap.putIfAbsent(str, str);
        if (interned != null) {
            return interned;
        }
        return str;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取缓冲区中常量池字符串的字节偏移量，对于空字符串返回 0
     *
     * @param cpIdx
     *            常量池索引
     * @param subFieldIdx
     *            对于 CONSTANT_Utf8、CONSTANT_Class 和 CONSTANT_String 应为 0；
     *            对于 CONSTANT_NameAndType_info，值为 0 时获取名称，值为 1 时获取类型描述符
     * @return 常量池字符串偏移量
     * @throws ClassfileFormatException
     *             如果检测到问题
     */
    private int getConstantPoolStringOffset(final int cpIdx, final int subFieldIdx)
            throws ClassfileFormatException {
        if (cpIdx < 1 || cpIdx >= cpCount) {
            throw new ClassfileFormatException("Constant pool index " + cpIdx + ", should be in range [1, "
                    + (cpCount - 1) + "] -- cannot continue reading class. "
                    + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
        final int t = entryTag[cpIdx];
        if ((t != 12 && subFieldIdx != 0) || (t == 12 && subFieldIdx != 0 && subFieldIdx != 1)) {
            throw new ClassfileFormatException(
                    "Bad subfield index " + subFieldIdx + " for tag " + t + ", cannot continue reading class. "
                            + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
        int cpIdxToUse;
        if (t == 0) {
            // 假设这意味着 null
            return 0;
        } else if (t == 1) {
            // CONSTANT_Utf8
            cpIdxToUse = cpIdx;
        } else if (t == 7 || t == 8 || t == 19) {
            // t == 7 => CONSTANT_Class，例如 "[[I", "[Ljava/lang/Thread;";
            // t == 8 => CONSTANT_String;
            // t == 19 => CONSTANT_Method_Info
            final int indirIdx = indirectStringRefs[cpIdx];
            if (indirIdx == -1) {
                // 不应发生
                throw new ClassfileFormatException("Bad string indirection index, cannot continue reading class. "
                        + "Please report this at https://github.com/classgraph/classgraph/issues");
            }
            if (indirIdx == 0) {
                // 假设这表示空字符串，因为第零个条目未被使用
                return 0;
            }
            cpIdxToUse = indirIdx;
        } else if (t == 12) {
            // CONSTANT_NameAndType_info
            final int compoundIndirIdx = indirectStringRefs[cpIdx];
            if (compoundIndirIdx == -1) {
                // 不应发生
                throw new ClassfileFormatException("Bad string indirection index, cannot continue reading class. "
                        + "Please report this at https://github.com/classgraph/classgraph/issues");
            }
            final int indirIdx = (subFieldIdx == 0 ? (compoundIndirIdx >> 16) : compoundIndirIdx) & 0xffff;
            if (indirIdx == 0) {
                // 不应发生
                throw new ClassfileFormatException("Bad string indirection index, cannot continue reading class. "
                        + "Please report this at https://github.com/classgraph/classgraph/issues");
            }
            cpIdxToUse = indirIdx;
        } else {
            throw new ClassfileFormatException("Wrong tag number " + t + " at constant pool index " + cpIdx + ", "
                    + "cannot continue reading class. Please report this at "
                    + "https://github.com/classgraph/classgraph/issues");
        }
        if (cpIdxToUse < 1 || cpIdxToUse >= cpCount) {
            throw new ClassfileFormatException("Constant pool index " + cpIdx + ", should be in range [1, "
                    + (cpCount - 1) + "] -- cannot continue reading class. "
                    + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
        return entryOffset[cpIdxToUse];
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从常量池获取字符串，可选择将 '/' 替换为 '.'
     *
     * @param cpIdx
     *            常量池索引
     * @param replaceSlashWithDot
     *            如果为 true，将结果中的斜杠替换为点号
     * @param stripLSemicolon
     *            如果为 true，在返回之前从开头去掉 'L' 并从末尾去掉 ';'(用于类引用常量)
     * @return 常量池字符串
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private String getConstantPoolString(final int cpIdx, final boolean replaceSlashWithDot,
                                         final boolean stripLSemicolon) throws ClassfileFormatException, IOException {
        final int constantPoolStringOffset = getConstantPoolStringOffset(cpIdx, /* subFieldIdx = */ 0);
        if (constantPoolStringOffset == 0) {
            return null;
        }
        final int utfLen = reader.readUnsignedShort(constantPoolStringOffset);
        if (utfLen == 0) {
            return "";
        }
        return intern(
                reader.readString(constantPoolStringOffset + 2L, utfLen, replaceSlashWithDot, stripLSemicolon));
    }

    /**
     * 从常量池获取字符串
     *
     * @param cpIdx
     *            常量池索引
     * @param subFieldIdx
     *            对于 CONSTANT_Utf8、CONSTANT_Class 和 CONSTANT_String 应为 0；
     *            对于 CONSTANT_NameAndType_info，值为 0 时获取名称，值为 1 时获取类型描述符
     * @return 常量池字符串
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private String getConstantPoolString(final int cpIdx, final int subFieldIdx)
            throws ClassfileFormatException, IOException {
        final int constantPoolStringOffset = getConstantPoolStringOffset(cpIdx, subFieldIdx);
        if (constantPoolStringOffset == 0) {
            return null;
        }
        final int utfLen = reader.readUnsignedShort(constantPoolStringOffset);
        if (utfLen == 0) {
            return "";
        }
        return intern(reader.readString(constantPoolStringOffset + 2L, utfLen, /* replaceSlashWithDot = */ false,
                /* stripLSemicolon = */ false));
    }

    /**
     * 从常量池获取字符串
     *
     * @param cpIdx
     *            常量池索引
     * @return 常量池字符串
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private String getConstantPoolString(final int cpIdx) throws ClassfileFormatException, IOException {
        return getConstantPoolString(cpIdx, /* subFieldIdx = */ 0);
    }

    /**
     * 获取常量池中字符串的第一个 UTF8 字节，如果字符串为 null 或空则返回 '\0'
     *
     * @param cpIdx
     *            常量池索引
     * @return 常量池字符串的第一个字节
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private byte getConstantPoolStringFirstByte(final int cpIdx) throws ClassfileFormatException, IOException {
        final int constantPoolStringOffset = getConstantPoolStringOffset(cpIdx, /* subFieldIdx = */ 0);
        if (constantPoolStringOffset == 0) {
            return '\0';
        }
        final int utfLen = reader.readUnsignedShort(constantPoolStringOffset);
        if (utfLen == 0) {
            return '\0';
        }
        return reader.readByte(constantPoolStringOffset + 2L);
    }

    /**
     * 从常量池获取字符串，并通过将 '/' 替换为 '.' 来解释为类名
     *
     * @param cpIdx
     *            常量池索引
     * @return 常量池类名
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private String getConstantPoolClassName(final int cpIdx) throws ClassfileFormatException, IOException {
        return getConstantPoolString(cpIdx, /* replaceSlashWithDot = */ true, /* stripLSemicolon = */ false);
    }

    /**
     * 从常量池获取表示类名内部字符串描述符("Lcom/xyz/MyClass;")的字符串，并通过将 '/' 替换为 '.'，
     * 并移除开头的 "L" 和末尾的 ";" 来解释为类名
     *
     * @param cpIdx
     *            常量池索引
     * @return 常量池类描述符
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private String getConstantPoolClassDescriptor(final int cpIdx) throws ClassfileFormatException, IOException {
        return getConstantPoolString(cpIdx, /* replaceSlashWithDot = */ true, /* stripLSemicolon = */ true);
    }

    /**
     * 将常量池中的字符串与给定的 ASCII 字符串进行比较，而不构造常量池 String 对象
     *
     * @param cpIdx
     *            常量池索引
     * @param asciiStr
     *            要比较的 ASCII 字符串
     * @return 如果相等则返回 true
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private boolean constantPoolStringEquals(final int cpIdx, final String asciiStr)
            throws ClassfileFormatException, IOException {
        final int cpStrOffset = getConstantPoolStringOffset(cpIdx, /* subFieldIdx = */ 0);
        if (cpStrOffset == 0) {
            return asciiStr == null;
        } else if (asciiStr == null) {
            return false;
        }
        final int cpStrLen = reader.readUnsignedShort(cpStrOffset);
        final int asciiStrLen = asciiStr.length();
        if (cpStrLen != asciiStrLen) {
            return false;
        }
        final int cpStrStart = cpStrOffset + 2;
        reader.bufferTo(cpStrStart + cpStrLen);
        final byte[] buf = reader.buf();
        for (int i = 0; i < cpStrLen; i++) {
            if ((char) (buf[cpStrStart + i] & 0xff) != asciiStr.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从常量池读取 int
     *
     * @param cpIdx
     *            常量池索引
     * @return int 值
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private int cpReadInt(final int cpIdx) throws IOException {
        if (cpIdx < 1 || cpIdx >= cpCount) {
            throw new ClassfileFormatException("Constant pool index " + cpIdx + ", should be in range [1, "
                    + (cpCount - 1) + "] -- cannot continue reading class. "
                    + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
        return reader.readInt(entryOffset[cpIdx]);
    }

    /**
     * 从常量池读取 long
     *
     * @param cpIdx
     *            常量池索引
     * @return long 值
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private long cpReadLong(final int cpIdx) throws IOException {
        if (cpIdx < 1 || cpIdx >= cpCount) {
            throw new ClassfileFormatException("Constant pool index " + cpIdx + ", should be in range [1, "
                    + (cpCount - 1) + "] -- cannot continue reading class. "
                    + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
        return reader.readLong(entryOffset[cpIdx]);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从常量池获取字段常量
     *
     * @param tag
     *            标签
     * @param fieldTypeDescriptorFirstChar
     *            字段类型描述符的第一个字符
     * @param cpIdx
     *            常量池索引
     * @return 字段常量池值
     * @throws ClassfileFormatException
     *             如果发生问题
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private Object getFieldConstantPoolValue(final int tag, final char fieldTypeDescriptorFirstChar,
                                             final int cpIdx) throws ClassfileFormatException, IOException {
        switch (tag) {
            case 1: // Modified UTF8
            case 7: // Class -- 注意：未使用？类引用似乎实际上不会作为常量初始化器存储
            case 8: // String
                // 对 Modified UTF8 条目的前向或后向间接引用
                return getConstantPoolString(cpIdx);
            case 3: // int, short, char, byte, boolean 都由 Constant_INTEGER 表示
                final int intVal = cpReadInt(cpIdx);
                switch (fieldTypeDescriptorFirstChar) {
                    case 'I':
                        return intVal;
                    case 'S':
                        return (short) intVal;
                    case 'C':
                        return (char) intVal;
                    case 'B':
                        return (byte) intVal;
                    case 'Z':
                        return intVal != 0;
                    default:
                        // 穿透
                }
                throw new ClassfileFormatException("Unknown Constant_INTEGER type " + fieldTypeDescriptorFirstChar
                        + ", " + "cannot continue reading class. Please report this at "
                        + "https://github.com/classgraph/classgraph/issues");
            case 4: // float
                return Float.intBitsToFloat(cpReadInt(cpIdx));
            case 5: // long
                return cpReadLong(cpIdx);
            case 6: // double
                return Double.longBitsToDouble(cpReadLong(cpIdx));
            default:
                // ClassGraph 不需要其他类型
                // (注意：特别地，枚举值不存储在常量池中，因此不需要处理)
                throw new ClassfileFormatException("Unknown field constant pool tag " + tag + ", "
                        + "cannot continue reading class. Please report this at "
                        + "https://github.com/classgraph/classgraph/issues");
        }
    }

    /**
     * 从 ClassParser 读取注解条目
     *
     * @return 注解，作为 {@link AnnotationInfo} 对象
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private AnnotationInfo readAnnotation() throws IOException {
        // Lcom/xyz/Annotation; -> Lcom.xyz.Annotation;
        final String annotationClassName = getConstantPoolClassDescriptor(reader.readUnsignedShort());
        final int numElementValuePairs = reader.readUnsignedShort();
        AnnotationParameterValueList paramVals = null;
        if (numElementValuePairs > 0) {
            paramVals = new AnnotationParameterValueList(numElementValuePairs);
            for (int i = 0; i < numElementValuePairs; i++) {
                final String paramName = getConstantPoolString(reader.readUnsignedShort());
                final Object paramValue = readAnnotationElementValue();
                paramVals.add(new AnnotationParameterValue(paramName, paramValue));
            }
        }
        return new AnnotationInfo(annotationClassName, paramVals);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 从 ClassParser 读取注解元素值
     *
     * @return 注解元素值
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private Object readAnnotationElementValue() throws IOException {
        final int tag = (char) reader.readUnsignedByte();
        switch (tag) {
            case 'B':
                return (byte) cpReadInt(reader.readUnsignedShort());
            case 'C':
                return (char) cpReadInt(reader.readUnsignedShort());
            case 'D':
                return Double.longBitsToDouble(cpReadLong(reader.readUnsignedShort()));
            case 'F':
                return Float.intBitsToFloat(cpReadInt(reader.readUnsignedShort()));
            case 'I':
                return cpReadInt(reader.readUnsignedShort());
            case 'J':
                return cpReadLong(reader.readUnsignedShort());
            case 'S':
                return (short) cpReadInt(reader.readUnsignedShort());
            case 'Z':
                return cpReadInt(reader.readUnsignedShort()) != 0;
            case 's':
                return getConstantPoolString(reader.readUnsignedShort());
            case 'e': {
                // 返回类型为 AnnotationEnumVal
                final String annotationClassName = getConstantPoolClassDescriptor(reader.readUnsignedShort());
                final String annotationConstName = getConstantPoolString(reader.readUnsignedShort());
                return new AnnotationEnumValue(annotationClassName, annotationConstName);
            }
            case 'c':
                // 返回类型为 AnnotationClassRef(用于注解中的类引用)
                final String classRefTypeDescriptor = getConstantPoolString(reader.readUnsignedShort());
                return new AnnotationClassRef(classRefTypeDescriptor);
            case '@':
                // 复杂(嵌套)注解返回类型为 AnnotationInfo
                return readAnnotation();
            case '[':
                // 返回类型为 Object[](嵌套注解元素值)
                final int count = reader.readUnsignedShort();
                final Object[] arr = new Object[count];
                for (int i = 0; i < count; ++i) {
                    // 嵌套注解元素值
                    arr[i] = readAnnotationElementValue();
                }
                return arr;
            default:
                throw new ClassfileFormatException("Class " + className + " has unknown annotation element type tag '"
                        + ((char) tag) + "': element size unknown, cannot continue reading class. "
                        + "Please report this at https://github.com/classgraph/classgraph/issues");
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    private List<TypePathNode> readTypePath() throws IOException {
        final int typePathLength = reader.readUnsignedByte();
        if (typePathLength == 0) {
            return Collections.emptyList();
        } else {
            final List<TypePathNode> list = new ArrayList<>(typePathLength);
            for (int i = 0; i < typePathLength; i++) {
                final int typePathKind = reader.readUnsignedByte();
                final int TypeArgIdx = reader.readUnsignedByte();
                list.add(new TypePathNode(typePathKind, TypeArgIdx));
            }
            return list;
        }
    }

    /**
     * 读取常量池条目
     *
     * @param log
     *            日志
     * @throws IOException
     *             表示发生了 I/O 异常
     */
    private void readConstantPoolEntries(final LogNode log) throws IOException {
        // 仅在启用了类间依赖时才记录类依赖信息
        List<Integer> classNameCpIdxs = null;
        List<Integer> typeSignatureIdxs = null;
        if (ScanConfig.enableInterClassDependencies) {
            classNameCpIdxs = new ArrayList<>();
            typeSignatureIdxs = new ArrayList<>();
        }

        // 读取常量池大小
        cpCount = reader.readUnsignedShort();

        // 为常量池分配存储空间
        entryOffset = new int[cpCount];
        entryTag = new int[cpCount];
        indirectStringRefs = new int[cpCount];
        Arrays.fill(indirectStringRefs, 0, cpCount, -1);

        // 读取常量池条目
        for (int i = 1, skipSlot = 0; i < cpCount; i++) {
            if (skipSlot == 1) {
                // 跳过一个槽位(让 Scrutinizer 满意 -- 它不喜欢 case 6 中的 i++)
                skipSlot = 0;
                continue;
            }
            entryTag[i] = reader.readUnsignedByte();
            entryOffset[i] = reader.currPos();
            switch (entryTag[i]) {
                case 0: // 不可能，可能是缓冲区欠载
                    throw new ClassfileFormatException("Invalid constant pool tag 0 in ClassParser " + relativePath
                            + " (possible buffer underflow issue). Please report this at "
                            + "https://github.com/classgraph/classgraph/issues");
                case 1: // Modified UTF8
                    final int strLen = reader.readUnsignedShort();
                    reader.skip(strLen);
                    break;
                // 没有常量池标签类型 2
                case 3: // int, short, char, byte, boolean 都由 Constant_INTEGER 表示
                case 4: // float
                    reader.skip(4);
                    break;
                case 5: // long
                case 6: // double
                    reader.skip(8);
                    skipSlot = 1; // 双槽位
                    break;
                case 7: // 类引用(格式如 "java/lang/String")
                    // 对 Modified UTF8 条目的前向或后向间接引用
                    indirectStringRefs[i] = reader.readUnsignedShort();
                    if (classNameCpIdxs != null) {
                        // 如果这是类引用，且启用了类间依赖，则记录依赖关系
                        classNameCpIdxs.add(indirectStringRefs[i]);
                    }
                    break;
                case 8: // String
                    // 对 Modified UTF8 条目的前向或后向间接引用
                    indirectStringRefs[i] = reader.readUnsignedShort();
                    break;
                case 9: // 字段引用
                    // 引用一个类引用(case 7)，然后是一个名称和类型(case 12)
                    reader.skip(4);
                    break;
                case 10: // 方法引用
                    // 引用一个类引用(case 7)，然后是一个名称和类型(case 12)
                    reader.skip(4);
                    break;
                case 11: // 接口方法引用
                    // 引用一个类引用(case 7)，然后是一个名称和类型(case 12)
                    reader.skip(4);
                    break;
                case 12: // 名称和类型
                    final int nameRef = reader.readUnsignedShort();
                    final int typeRef = reader.readUnsignedShort();
                    if (typeSignatureIdxs != null) {
                        typeSignatureIdxs.add(typeRef);
                    }
                    indirectStringRefs[i] = (nameRef << 16) | typeRef;
                    break;
                // 没有常量池标签类型 13 或 14
                case 15: // 方法句柄
                    reader.skip(3);
                    break;
                case 16: // 方法类型
                    reader.skip(2);
                    break;
                case 17: // 动态
                    reader.skip(4);
                    break;
                case 18: // 调用动态
                    reader.skip(4);
                    break;
                case 19: // 模块(用于 JDK9+ 中的 module-info.class)
                    // 参见 https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4
                    indirectStringRefs[i] = reader.readUnsignedShort();
                    break;
                case 20: // 包(用于 JDK9+ 中的 module-info.class)
                    // 参见 https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.4
                    reader.skip(2);
                    break;
                default:
                    throw new ClassfileFormatException("Unknown constant pool tag " + entryTag[i]
                            + " (element size unknown, cannot continue reading class). Please report this at "
                            + "https://github.com/classgraph/classgraph/issues");
            }
        }

        // 查找常量池中引用的类注意，有些类引用无法通过此方式找到，
        // 例如枚举类和注解参数值中的类引用，因为它们被引用为字符串(标签 1)
        // 而非类(标签 7)或类型签名(标签 12 的一部分)
        // 因此，需要采用混合方法，从 ClassInfo 图中提取这些其他类引用，
        // 并将其与此处从常量池中提取的类名结合
        if (classNameCpIdxs != null) {
            refdClassNames = new HashSet<>();
            // 从常量池中的直接类引用获取类名
            for (final int cpIdx : classNameCpIdxs) {
                final String refdClassName = getConstantPoolString(cpIdx, /* replaceSlashWithDot = */ true,
                        /* stripLSemicolon = */ false);
                if (refdClassName != null) {
                    if (refdClassName.startsWith("[")) {
                        // 解析数组类型签名，例如 "[Ljava.lang.String;" -- 使用 '.' 而非 '/'
                        try {
                            final TypeSignature typeSig = TypeSignature.parse(refdClassName.replace('.', '/'),
                                    /* definingClass = */ null);
                            typeSig.findReferencedClassNames(refdClassNames);
                        } catch (final ParseException e) {
                            // 不应发生
                            throw new ClassfileFormatException("Could not parse class name: " + refdClassName, e);
                        }
                    } else {
                        refdClassNames.add(refdClassName);
                    }
                }
            }
        }
        if (typeSignatureIdxs != null) {
            // 从常量池中"名称和类型"条目的类型签名获取类名
            for (final int cpIdx : typeSignatureIdxs) {
                final String typeSigStr = getConstantPoolString(cpIdx);
                if (typeSigStr != null) {
                    try {
                        if (typeSigStr.startsWith("L") && typeSigStr.endsWith(";")) {
                            // 解析类名
                            final TypeSignature typeSig = TypeSignature.parse(typeSigStr,
                                    /* definingClassName = */ null);
                            // 从类型签名中提取类名
                            typeSig.findReferencedClassNames(refdClassNames);
                        } else if (typeSigStr.indexOf('(') >= 0 || "<init>".equals(typeSigStr)) {
                            // 解析类型签名
                            final MethodType typeSig = MethodType.parse(typeSigStr,
                                    /* definingClassName = */ null);
                            // 从类型签名中提取类名
                            typeSig.findReferencedClassNames(refdClassNames);
                        } else {
                            if (log != null) {
                                log.log("Could not extract referenced class names from constant pool string: "
                                        + typeSigStr);
                            }
                        }
                    } catch (final ParseException e) {
                        if (log != null) {
                            log.log("Could not extract referenced class names from constant pool string: "
                                    + typeSigStr + " : " + e);
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 读取基本类信息
     *
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws ClassfileFormatException
     *             如果 ClassParser 格式不正确
     * @throws SkipClassException
     *             如果 ClassParser 需要被跳过(例如类为非公开的，且 ignoreClassVisibility 为 false)
     */
    private void readBasicClassInfo() throws IOException, ClassfileFormatException, SkipClassException {
        // 修饰符标志
        classModifiers = reader.readUnsignedShort();

        isInterface = (classModifiers & 0x0200) != 0;
        isAnnotation = (classModifiers & 0x2000) != 0;

        // 此类的完全限定类名，斜杠替换为点号
        final String classNamePath = getConstantPoolString(reader.readUnsignedShort());
        if (classNamePath == null) {
            throw new ClassfileFormatException("Class name is null");
        }
        className = classNamePath.replace('/', '.');
        if ("java.lang.Object".equals(className)) {
            // 不处理 java.lang.Object(它有空超类)，但你仍然可以搜索作为
            // java.lang.Object 子类的类(作为外部类)
            throw new SkipClassException("No need to scan java.lang.Object");
        }

        // 检查类可见性修饰符
        final boolean isModule = (classModifiers & 0x8000) != 0; // 等同于文件名为 "module-info.class"
        final boolean isPackage = relativePath.regionMatches(relativePath.lastIndexOf('/') + 1,
                "package-info.class", 0, 18);
        if (!ScanConfig.ignoreClassVisibility && !Modifier.isPublic(classModifiers) && !isModule && !isPackage) {
            throw new SkipClassException("Class is not public, and ignoreClassVisibility() was not called");
        }

        // 确保类名与相对路径匹配
        if (!relativePath.endsWith(".class")) {
            // 不应发生
            throw new SkipClassException("ClassParser filename " + relativePath + " does not end in \".class\"");
        }
        final int len = classNamePath.length();
        if (relativePath.length() != len + 6 || !classNamePath.regionMatches(0, relativePath, 0, len)) {
            throw new SkipClassException(
                    "Relative path " + relativePath + " does not match class name " + className);
        }

        // 超类名，斜杠替换为点号
        final int superclassNameCpIdx = reader.readUnsignedShort();
        if (superclassNameCpIdx > 0) {
            superclassName = getConstantPoolClassName(superclassNameCpIdx);
        }
    }

    /**
     * 读取类的接口
     *
     * @throws IOException
     *             如果发生 I/O 异常
     */
    private void readInterfaces() throws IOException {
        // 接口
        final int interfaceCount = reader.readUnsignedShort();
        for (int i = 0; i < interfaceCount; i++) {
            final String interfaceName = getConstantPoolClassName(reader.readUnsignedShort());
            if (implementedInterfaces == null) {
                implementedInterfaces = new ArrayList<>();
            }
            implementedInterfaces.add(interfaceName);
        }
    }

    /**
     * 读取类的字段
     *
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws ClassfileFormatException
     *             如果 ClassParser 格式不正确
     */
    private void readFields() throws IOException, ClassfileFormatException {
        // 字段
        final int fieldCount = reader.readUnsignedShort();
        for (int i = 0; i < fieldCount; i++) {
            // 修饰符标志信息：http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.5
            final int fieldModifierFlags = reader.readUnsignedShort();
            final boolean isPublicField = ((fieldModifierFlags & 0x0001) == 0x0001);
            final boolean fieldIsVisible = isPublicField || ScanConfig.ignoreFieldVisibility;
            final boolean getStaticFinalFieldConstValue = ScanConfig.enableStaticFinalFieldConstantInitializerValues
                    && fieldIsVisible;
            List<TypeAnnotationDecorator> fieldTypeAnnotationDecorators = null;
            if (!fieldIsVisible || (!ScanConfig.enableFieldInfo && !getStaticFinalFieldConstValue)) {
                // 跳过字段
                reader.readUnsignedShort(); // fieldNameCpIdx
                reader.readUnsignedShort(); // fieldTypeDescriptorCpIdx
                final int attributesCount = reader.readUnsignedShort();
                for (int j = 0; j < attributesCount; j++) {
                    reader.readUnsignedShort(); // attributeNameCpIdx
                    final int attributeLength = reader.readInt(); // == 2
                    reader.skip(attributeLength);
                }
            } else {
                final int fieldNameCpIdx = reader.readUnsignedShort();
                final String fieldName = getConstantPoolString(fieldNameCpIdx);
                final int fieldTypeDescriptorCpIdx = reader.readUnsignedShort();
                final char fieldTypeDescriptorFirstChar = (char) getConstantPoolStringFirstByte(
                        fieldTypeDescriptorCpIdx);
                String fieldTypeDescriptor;
                String fieldTypeSignatureStr = null;
                fieldTypeDescriptor = getConstantPoolString(fieldTypeDescriptorCpIdx);

                Object fieldConstValue = null;
                AnnotationInfoList fieldAnnotationInfo = null;
                final int attributesCount = reader.readUnsignedShort();
                for (int j = 0; j < attributesCount; j++) {
                    final int attributeNameCpIdx = reader.readUnsignedShort();
                    final int attributeLength = reader.readInt(); // == 2
                    // 查看字段名是否匹配此类的请求名称之一，如果匹配，检查是否用常量值初始化
                    if ((getStaticFinalFieldConstValue)
                            && constantPoolStringEquals(attributeNameCpIdx, "ConstantValue")) {
                        // http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.7.2
                        final int cpIdx = reader.readUnsignedShort();
                        if (cpIdx < 1 || cpIdx >= cpCount) {
                            throw new ClassfileFormatException("Constant pool index " + cpIdx
                                    + ", should be in range [1, " + (cpCount - 1)
                                    + "] -- cannot continue reading class. "
                                    + "Please report this at https://github.com/classgraph/classgraph/issues");
                        }
                        fieldConstValue = getFieldConstantPoolValue(entryTag[cpIdx], fieldTypeDescriptorFirstChar,
                                cpIdx);
                    } else if (fieldIsVisible && constantPoolStringEquals(attributeNameCpIdx, "Signature")) {
                        fieldTypeSignatureStr = getConstantPoolString(reader.readUnsignedShort());
                    } else if (ScanConfig.enableAnnotationInfo //
                            && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleAnnotations")
                            || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                            attributeNameCpIdx, "RuntimeInvisibleAnnotations")))) {
                        // 读取注解名称
                        final int fieldAnnotationCount = reader.readUnsignedShort();
                        if (fieldAnnotationCount > 0) {
                            if (fieldAnnotationInfo == null) {
                                fieldAnnotationInfo = new AnnotationInfoList(1);
                            }
                            for (int k = 0; k < fieldAnnotationCount; k++) {
                                final AnnotationInfo fieldAnnotation = readAnnotation();
                                fieldAnnotationInfo.add(fieldAnnotation);
                            }
                        }
                    } else if (ScanConfig.enableAnnotationInfo //
                            && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleTypeAnnotations")
                            || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                            attributeNameCpIdx, "RuntimeInvisibleTypeAnnotations")))) {
                        final int annotationCount = reader.readUnsignedShort();
                        if (annotationCount > 0) {
                            fieldTypeAnnotationDecorators = new ArrayList<>();
                            for (int m = 0; m < annotationCount; m++) {
                                final int targetType = reader.readUnsignedByte();
                                if (targetType != 0x13) {
                                    throw new ClassfileFormatException(
                                            "Class " + className + " has unknown field type annotation target 0x"
                                                    + Integer.toHexString(targetType)
                                                    + ": element size unknown, cannot continue reading class. "
                                                    + "Please report this at "
                                                    + "https://github.com/classgraph/classgraph/issues");
                                }
                                final List<TypePathNode> typePath = readTypePath();
                                final AnnotationInfo annotationInfo = readAnnotation();
                                fieldTypeAnnotationDecorators.add(new TypeAnnotationDecorator() {
                                    @Override
                                    public void decorate(final TypeSignature typeSignature) {
                                        typeSignature.addTypeAnnotation(typePath, annotationInfo);
                                    }
                                });
                            }
                        }
                    } else {
                        // 不匹配，直接跳过属性
                        reader.skip(attributeLength);
                    }
                }
                if (ScanConfig.enableFieldInfo && fieldIsVisible) {
                    if (fieldInfoList == null) {
                        fieldInfoList = new FieldInfoList();
                    }
                    fieldInfoList.add(new FieldInfo(className, fieldName, fieldModifierFlags, fieldTypeDescriptor,
                            fieldTypeSignatureStr, fieldConstValue, fieldAnnotationInfo,
                            fieldTypeAnnotationDecorators));
                }
            }
        }
    }

    /**
     * 读取类的方法
     *
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws ClassfileFormatException
     *             如果 ClassParser 格式不正确
     */
    private void readMethods() throws IOException, ClassfileFormatException {
        // 方法
        final int methodCount = reader.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            // 修饰符标志信息：http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6
            final int methodModifierFlags = reader.readUnsignedShort();
            final boolean isPublicMethod = ((methodModifierFlags & 0x0001) == 0x0001);
            final boolean methodIsVisible = isPublicMethod || ScanConfig.ignoreMethodVisibility;
            List<MethodTypeAnnotationDecorator> methodTypeAnnotationDecorators = null;
            String methodName = null;
            String methodTypeDescriptor = null;
            String MethodTypeStr = null;
            // 始终为注解启用 MethodInfo(这是注解常量定义的方式)
            final boolean enableMethodInfo = ScanConfig.enableMethodInfo || isAnnotation;
            if (enableMethodInfo || isAnnotation) { // 注解在 method_info 中存储默认值
                final int methodNameCpIdx = reader.readUnsignedShort();
                methodName = getConstantPoolString(methodNameCpIdx);
                final int methodTypeDescriptorCpIdx = reader.readUnsignedShort();
                methodTypeDescriptor = getConstantPoolString(methodTypeDescriptorCpIdx);
            } else {
                reader.skip(4); // name_index, descriptor_index
            }
            final int attributesCount = reader.readUnsignedShort();
            String[] methodParameterNames = null;
            String[] thrownExceptionNames = null;
            int[] methodParameterModifiers = null;
            AnnotationInfo[][] methodParameterAnnotations = null;
            AnnotationInfoList methodAnnotationInfo = null;
            boolean methodHasBody = false;
            int minLineNum = 0;
            int maxLineNum = 0;
            if (!methodIsVisible || (!enableMethodInfo && !isAnnotation)) {
                // 跳过方法属性
                for (int j = 0; j < attributesCount; j++) {
                    reader.skip(2); // attribute_name_index
                    final int attributeLength = reader.readInt();
                    reader.skip(attributeLength);
                }
            } else {
                // 查找方法注解
                for (int j = 0; j < attributesCount; j++) {
                    final int attributeNameCpIdx = reader.readUnsignedShort();
                    final int attributeLength = reader.readInt();
                    if (ScanConfig.enableAnnotationInfo
                            && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleAnnotations")
                            || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                            attributeNameCpIdx, "RuntimeInvisibleAnnotations")))) {
                        final int methodAnnotationCount = reader.readUnsignedShort();
                        if (methodAnnotationCount > 0) {
                            if (methodAnnotationInfo == null) {
                                methodAnnotationInfo = new AnnotationInfoList(1);
                            }
                            for (int k = 0; k < methodAnnotationCount; k++) {
                                final AnnotationInfo annotationInfo = readAnnotation();
                                methodAnnotationInfo.add(annotationInfo);
                            }
                        }
                    } else if (ScanConfig.enableAnnotationInfo
                            && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleParameterAnnotations")
                            || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                            attributeNameCpIdx, "RuntimeInvisibleParameterAnnotations")))) {
                        // 将运行时可见和运行时不可见注解合并到每个方法参数的单个注解数组中
                        // (运行时可见和运行时不可见注解在单独的属性中给出，因此如果两个属性都存在，
                        // 在遇到第二个属性时需要扩大参数注解数组)
                        final int numParams = reader.readUnsignedByte();
                        if (methodParameterAnnotations == null) {
                            methodParameterAnnotations = new AnnotationInfo[numParams][];
                        } else if (methodParameterAnnotations.length != numParams) {
                            throw new ClassfileFormatException(
                                    "Mismatch in number of parameters between RuntimeVisibleParameterAnnotations "
                                            + "and RuntimeInvisibleParameterAnnotations");
                        }
                        for (int paramIdx = 0; paramIdx < numParams; paramIdx++) {
                            final int numAnnotations = reader.readUnsignedShort();
                            if (numAnnotations > 0) {
                                int annStartIdx = 0;
                                if (methodParameterAnnotations[paramIdx] != null) {
                                    annStartIdx = methodParameterAnnotations[paramIdx].length;
                                    methodParameterAnnotations[paramIdx] = Arrays.copyOf(
                                            methodParameterAnnotations[paramIdx], annStartIdx + numAnnotations);
                                } else {
                                    methodParameterAnnotations[paramIdx] = new AnnotationInfo[numAnnotations];
                                }
                                for (int annIdx = 0; annIdx < numAnnotations; annIdx++) {
                                    methodParameterAnnotations[paramIdx][annStartIdx + annIdx] = readAnnotation();
                                }
                            } else if (methodParameterAnnotations[paramIdx] == null) {
                                methodParameterAnnotations[paramIdx] = NO_ANNOTATIONS;
                            }
                        }
                    } else if (ScanConfig.enableAnnotationInfo //
                            && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleTypeAnnotations")
                            || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                            attributeNameCpIdx, "RuntimeInvisibleTypeAnnotations")))) {
                        final int annotationCount = reader.readUnsignedShort();
                        if (annotationCount > 0) {
                            methodTypeAnnotationDecorators = new ArrayList<>(annotationCount);
                            for (int m = 0; m < annotationCount; m++) {
                                final int targetType = reader.readUnsignedByte();
                                final int TypeParamIndex;
                                final int boundIndex;
                                final int formalParameterIndex;
                                final int throwsTypeIndex;
                                if (targetType == 0x01) {
                                    // 泛型方法或构造函数的类型参数声明
                                    TypeParamIndex = reader.readUnsignedByte();
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x10) {
                                    // 此 target_type 不应添加方法，它用于 ClassParser 注解，
                                    // 但 Google 的 Java 编译器出于某种原因将此类注解添加到 guava 的方法中
                                    // 直接忽略这些注解(#861)
                                    reader.readUnsignedShort();
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x12) {
                                    // 泛型方法或构造函数类型参数声明的边界中的类型
                                    TypeParamIndex = reader.readUnsignedByte();
                                    boundIndex = reader.readUnsignedByte();
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x13) {
                                    // 字段或记录组件声明中的类型
                                    // (空目标)
                                    // 此 target_type 不应添加到方法中，但似乎 JDK 17 编译器有 bug，
                                    // 仍然将此 target_type 添加到记录的方法中(#797)因此接受它，
                                    // 但忽略它(相同的 target_type 也应该添加到记录的字段中)
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x14) {
                                    // 方法的返回类型，或新构造对象的类型
                                    // (空目标)
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x15) {
                                    // 方法或构造函数的接收者类型
                                    // (空目标)
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x16) {
                                    // 方法、构造函数或 Lambda 表达式的形式参数声明中的类型
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = reader.readUnsignedByte();
                                    throwsTypeIndex = -1;
                                } else if (targetType == 0x17) {
                                    // 方法或构造函数 throws 子句中的类型
                                    TypeParamIndex = -1;
                                    boundIndex = -1;
                                    formalParameterIndex = -1;
                                    throwsTypeIndex = reader.readUnsignedShort();
                                } else {
                                    throw new ClassfileFormatException(
                                            "Class " + className + " has unknown method type annotation target 0x"
                                                    + Integer.toHexString(targetType)
                                                    + ": element size unknown, cannot continue reading class. "
                                                    + "Please report this at "
                                                    + "https://github.com/classgraph/classgraph/issues");
                                }
                                final List<TypePathNode> typePath = readTypePath();
                                final AnnotationInfo annotationInfo = readAnnotation();
                                methodTypeAnnotationDecorators.add(new MethodTypeAnnotationDecorator() {
                                    @Override
                                    public void decorate(final MethodType MethodType) {
                                        if (targetType == 0x01) {
                                            // 泛型方法或构造函数的类型参数声明
                                            final List<TypeParam> TypeParams = MethodType
                                                    .getTypeParams();
                                            if (TypeParams != null
                                                    && TypeParamIndex < TypeParams.size()) {
                                                TypeParams.get(TypeParamIndex).addTypeAnnotation(typePath,
                                                        annotationInfo);
                                            }
                                            // 否则这是方法类型描述符，而不是方法类型签名，因此没有类型参数
                                        } else if (targetType == 0x12) {
                                            // 泛型方法或构造函数类型参数声明的边界中的类型
                                            final List<TypeParam> TypeParams = MethodType
                                                    .getTypeParams();
                                            if (TypeParams != null
                                                    && TypeParamIndex < TypeParams.size()) {
                                                final TypeParam TypeParam = TypeParams
                                                        .get(TypeParamIndex);
                                                // boundIndex == 0 => 类边界；boundIndex > 0 => 接口边界
                                                if (boundIndex == 0) {
                                                    final ReferenceType classBound = TypeParam
                                                            .getClassBound();
                                                    if (classBound != null) {
                                                        classBound.addTypeAnnotation(typePath, annotationInfo);
                                                    }
                                                } else {
                                                    final List<ReferenceType> interfaceBounds = //
                                                            TypeParam.getInterfaceBounds();
                                                    if (interfaceBounds != null
                                                            && boundIndex - 1 < interfaceBounds.size()) {
                                                        interfaceBounds.get(boundIndex - 1)
                                                                .addTypeAnnotation(typePath, annotationInfo);
                                                    }
                                                }
                                            }
                                            // 否则这是方法类型描述符，而不是方法类型签名，因此没有类型参数
                                        } else if (targetType == 0x14) {
                                            // 方法的返回类型，或新构造对象的类型
                                            MethodType.getResultType().addTypeAnnotation(typePath,
                                                    annotationInfo);
                                        } else if (targetType == 0x15) {
                                            // 方法或构造函数的接收者类型(显式接收者参数)
                                            MethodType.addRecieverTypeAnnotation(annotationInfo);
                                        } else if (targetType == 0x16) {
                                            // 方法、构造函数或 Lambda 表达式的形式参数声明中的类型
                                            // 注意：形式参数索引不可靠，因为并非所有编译器以相同方式索引参数 --
                                            // 所以这里需要健壮处理
                                            // ClassParser 规范说："A formal_parameter_index value of i may,
                                            // but is not required to, correspond to the i'th parameter descriptor
                                            // in the method descriptor"还有：
                                            // "The formal_parameter_target item records that a formal parameter's
                                            // type is annotated, but does not record the type itself. The type
                                            // may be found by inspecting the method descriptor, although a
                                            // formal_parameter_index value of 0 does not always indicate the
                                            // first parameter descriptor in the method descriptor."
                                            // 真是够了，伙计们
                                            final List<TypeSignature> parameterTypeSignatures = MethodType
                                                    .getParameterTypeSignatures();
                                            if (formalParameterIndex < parameterTypeSignatures.size()) {
                                                parameterTypeSignatures.get(formalParameterIndex)
                                                        .addTypeAnnotation(typePath, annotationInfo);
                                            }
                                        } else if (targetType == 0x17) {
                                            // 方法或构造函数 throws 子句中的类型
                                            final List<TypeRef> throwsSignatures = //
                                                    MethodType.getThrowsSignatures();
                                            if (throwsSignatures != null
                                                    && throwsTypeIndex < throwsSignatures.size()) {
                                                throwsSignatures.get(throwsTypeIndex).addTypeAnnotation(typePath,
                                                        annotationInfo);
                                            }
                                        }
                                    }
                                });
                            }
                        }
                    } else if (constantPoolStringEquals(attributeNameCpIdx, "MethodParameters")) {
                        // 读取方法参数对于 Java，这些仅在 JDK8+ 中生成，且仅在编译时
                        // 提供了命令行开关 `-parameters` 时才生成
                        final int paramCount = reader.readUnsignedByte();
                        methodParameterNames = new String[paramCount];
                        methodParameterModifiers = new int[paramCount];
                        for (int k = 0; k < paramCount; k++) {
                            final int cpIdx = reader.readUnsignedShort();
                            // 如果常量池索引为零，则参数是未命名的 => 使用 null
                            methodParameterNames[k] = cpIdx == 0 ? null : getConstantPoolString(cpIdx);
                            methodParameterModifiers[k] = reader.readUnsignedShort();
                        }
                    } else if (constantPoolStringEquals(attributeNameCpIdx, "Signature")) {
                        // 将类型参数添加到方法类型签名
                        MethodTypeStr = getConstantPoolString(reader.readUnsignedShort());
                    } else if (constantPoolStringEquals(attributeNameCpIdx, "AnnotationDefault")) {
                        if (annotationParamDefaultValues == null) {
                            annotationParamDefaultValues = new AnnotationParameterValueList();
                        }
                        this.annotationParamDefaultValues.add(new AnnotationParameterValue(methodName,
                                // 获取注解参数默认值
                                readAnnotationElementValue()));
                    } else if (constantPoolStringEquals(attributeNameCpIdx, "Exceptions")) {
                        final int exceptionCount = reader.readUnsignedShort();
                        thrownExceptionNames = new String[exceptionCount];
                        for (int k = 0; k < exceptionCount; k++) {
                            final int cpIdx = reader.readUnsignedShort();
                            thrownExceptionNames[k] = getConstantPoolClassName(cpIdx);
                        }
                    } else if (constantPoolStringEquals(attributeNameCpIdx, "Code")) {
                        methodHasBody = true;
                        reader.skip(4); // max_stack, max_locals
                        final int codeLength = reader.readInt();
                        reader.skip(codeLength);
                        final int exceptionTableLength = reader.readUnsignedShort();
                        reader.skip(8 * exceptionTableLength);
                        final int codeAttrCount = reader.readUnsignedShort();
                        for (int k = 0; k < codeAttrCount; k++) {
                            final int codeAttrCpIdx = reader.readUnsignedShort();
                            final int codeAttrLen = reader.readInt();
                            if (constantPoolStringEquals(codeAttrCpIdx, "LineNumberTable")) {
                                final int lineNumTableLen = reader.readUnsignedShort();
                                for (int l = 0; l < lineNumTableLen; l++) {
                                    reader.skip(2); // start_pc
                                    final int lineNum = reader.readUnsignedShort();
                                    minLineNum = minLineNum == 0 ? lineNum : Math.min(minLineNum, lineNum);
                                    maxLineNum = maxLineNum == 0 ? lineNum : Math.max(maxLineNum, lineNum);
                                }
                            } else {
                                reader.skip(codeAttrLen);
                            }
                        }
                    } else {
                        reader.skip(attributeLength);
                    }
                }
                // 创建 MethodInfo
                if (enableMethodInfo) {
                    if (methodInfoList == null) {
                        methodInfoList = new MethodInfoList();
                    }
                    methodInfoList.add(new MethodInfo(className, methodName, methodAnnotationInfo,
                            methodModifierFlags, methodTypeDescriptor, MethodTypeStr, methodParameterNames,
                            methodParameterModifiers, methodParameterAnnotations, methodHasBody, minLineNum,
                            maxLineNum, methodTypeAnnotationDecorators, thrownExceptionNames));
                }
            }
        }
    }

    /**
     * 读取类属性
     *
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws ClassfileFormatException
     *             如果 ClassParser 格式不正确
     */
    private void readClassAttributes() throws IOException, ClassfileFormatException {
        // 类属性(包括类注解、类类型变量、模块信息等)
        final int attributesCount = reader.readUnsignedShort();
        for (int i = 0; i < attributesCount; i++) {
            final int attributeNameCpIdx = reader.readUnsignedShort();
            final int attributeLength = reader.readInt();
            if (ScanConfig.enableAnnotationInfo //
                    && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleAnnotations")
                    || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                    attributeNameCpIdx, "RuntimeInvisibleAnnotations")))) {
                final int annotationCount = reader.readUnsignedShort();
                if (annotationCount > 0) {
                    if (classAnnotations == null) {
                        classAnnotations = new AnnotationInfoList();
                    }
                    for (int m = 0; m < annotationCount; m++) {
                        classAnnotations.add(readAnnotation());
                    }
                }
            } else if (ScanConfig.enableAnnotationInfo //
                    && (constantPoolStringEquals(attributeNameCpIdx, "RuntimeVisibleTypeAnnotations")
                    || (!ScanConfig.disableRuntimeInvisibleAnnotations && constantPoolStringEquals(
                    attributeNameCpIdx, "RuntimeInvisibleTypeAnnotations")))) {
                final int annotationCount = reader.readUnsignedShort();
                if (annotationCount > 0) {
                    classTypeAnnotationDecorators = new ArrayList<>(annotationCount);
                    for (int m = 0; m < annotationCount; m++) {
                        final int targetType = reader.readUnsignedByte();
                        final int TypeParamIndex;
                        final int supertypeIndex;
                        final int boundIndex;
                        if (targetType == 0x00) {
                            // 泛型类或接口的类型参数声明
                            TypeParamIndex = reader.readUnsignedByte();
                            supertypeIndex = -1;
                            boundIndex = -1;
                        } else if (targetType == 0x10) {
                            // 类声明的 extends 或 implements 子句中的类型(包括匿名类声明的
                            // 直接超类或直接超接口)，或接口声明的 extends 子句中的类型
                            supertypeIndex = reader.readUnsignedShort();
                            TypeParamIndex = -1;
                            boundIndex = -1;
                        } else if (targetType == 0x11) {
                            // 泛型类或接口类型参数声明的边界中的类型
                            TypeParamIndex = reader.readUnsignedByte();
                            boundIndex = reader.readUnsignedByte();
                            supertypeIndex = -1;
                        } else {
                            throw new ClassfileFormatException("Class " + className
                                    + " has unknown class type annotation target 0x"
                                    + Integer.toHexString(targetType)
                                    + ": element size unknown, cannot continue reading class. "
                                    + "Please report this at https://github.com/classgraph/classgraph/issues");
                        }
                        final List<TypePathNode> typePath = readTypePath();
                        final AnnotationInfo annotationInfo = readAnnotation();
                        classTypeAnnotationDecorators.add(new ClassTypeAnnotationDecorator() {
                            @Override
                            public void decorate(final ClassType ClassType) {
                                if (targetType == 0x00) {
                                    // 泛型类或接口的类型参数声明
                                    final List<TypeParam> TypeParams = ClassType
                                            .getTypeParams();
                                    if (TypeParams != null && TypeParamIndex < TypeParams.size()) {
                                        TypeParams.get(TypeParamIndex).addTypeAnnotation(typePath,
                                                annotationInfo);
                                    }
                                } else if (targetType == 0x10) {
                                    // 类声明的 extends 或 implements 子句中的类型(包括匿名类声明的
                                    // 直接超类或直接超接口)，或接口声明的 extends 子句中的类型
                                    if (supertypeIndex == 65535) {
                                        // 类声明的 extends 子句中的类型
                                        ClassType.getSuperclassSignature().addTypeAnnotation(typePath,
                                                annotationInfo);
                                    } else {
                                        // 接口声明的 implements 子句中的类型
                                        ClassType.getSuperinterfaceSignatures().get(supertypeIndex)
                                                .addTypeAnnotation(typePath, annotationInfo);
                                    }
                                } else if (targetType == 0x11) {
                                    // 泛型类或接口类型参数声明的边界中的类型
                                    final List<TypeParam> TypeParams = ClassType
                                            .getTypeParams();
                                    if (TypeParams != null && TypeParamIndex < TypeParams.size()) {
                                        final TypeParam TypeParam = TypeParams.get(TypeParamIndex);
                                        // boundIndex == 0 => 类边界；boundIndex > 0 => 接口边界
                                        if (boundIndex == 0) {
                                            final ReferenceType classBound = TypeParam.getClassBound();
                                            if (classBound != null) {
                                                classBound.addTypeAnnotation(typePath, annotationInfo);
                                            }
                                        } else {
                                            final List<ReferenceType> interfaceBounds = TypeParam
                                                    .getInterfaceBounds();
                                            if (interfaceBounds != null
                                                    && boundIndex - 1 < interfaceBounds.size()) {
                                                TypeParam.getInterfaceBounds().get(boundIndex - 1)
                                                        .addTypeAnnotation(typePath, annotationInfo);
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    }
                }
            } else if (constantPoolStringEquals(attributeNameCpIdx, "Record")) {
                isRecord = true;
                // 不需要读取 record_components_info 条目 -- 记录组件与同名的字段和方法
                // 存在一一对应关系，因此我们可以依赖字段和方法的读取代码正确处理记录
                reader.skip(attributeLength);
            } else if (constantPoolStringEquals(attributeNameCpIdx, "InnerClasses")) {
                final int numInnerClasses = reader.readUnsignedShort();
                for (int j = 0; j < numInnerClasses; j++) {
                    final int innerClassInfoCpIdx = reader.readUnsignedShort();
                    final int outerClassInfoCpIdx = reader.readUnsignedShort();
                    reader.skip(2); // inner_name_idx
                    final int innerClassAccessFlags = reader.readUnsignedShort();
                    if (innerClassInfoCpIdx != 0 && outerClassInfoCpIdx != 0) {
                        final String innerClassName = getConstantPoolClassName(innerClassInfoCpIdx);
                        final String outerClassName = getConstantPoolClassName(outerClassInfoCpIdx);
                        if (innerClassName == null || outerClassName == null) {
                            // 不应发生(修复静态分析器警告)
                            throw new ClassfileFormatException("Inner and/or outer class name is null");
                        }
                        if (innerClassName.equals(outerClassName)) {
                            // 根据规范这是无效的
                            throw new ClassfileFormatException("Inner and outer class name cannot be the same");
                        }
                        // JDK 14 中记录类型有一个用于引导方法的 Lookup 内部类 -- 丢弃它
                        if (!("java.lang.invoke.MethodHandles$Lookup".equals(innerClassName)
                                && "java.lang.invoke.MethodHandles".equals(outerClassName))) {
                            // 存储内部类和外部类之间的关系
                            if (classContainmentEntries == null) {
                                classContainmentEntries = new ArrayList<>();
                            }
                            classContainmentEntries.add(
                                    new ClassContainment(innerClassName, innerClassAccessFlags, outerClassName));
                        }
                    }
                }
            } else if (constantPoolStringEquals(attributeNameCpIdx, "Signature")) {
                // 获取类类型签名，包括类型变量
                typeSignatureStr = getConstantPoolString(reader.readUnsignedShort());
            } else if (constantPoolStringEquals(attributeNameCpIdx, "SourceFile")) {
                sourceFile = getConstantPoolString(reader.readUnsignedShort());
            } else if (constantPoolStringEquals(attributeNameCpIdx, "EnclosingMethod")) {
                final String innermostEnclosingClassName = getConstantPoolClassName(reader.readUnsignedShort());
                final int enclosingMethodCpIdx = reader.readUnsignedShort();
                String definingMethodName;
                if (enclosingMethodCpIdx == 0) {
                    // cpIdx 为 0(无效值)用于在类初始化代码中声明的匿名内部类，例如赋值给类字段
                    definingMethodName = "<clinit>";
                } else {
                    definingMethodName = getConstantPoolString(enclosingMethodCpIdx, /* subFieldIdx = */ 0);
                    // 如果需要，也可以使用 subFieldIdx = 1 获取方法类型签名
                }
                // 将匿名内部类链接到其包含方法所在的类
                if (classContainmentEntries == null) {
                    classContainmentEntries = new ArrayList<>();
                }
                classContainmentEntries
                        .add(new ClassContainment(className, classModifiers, innermostEnclosingClassName));
                // 同时存储包含方法的完全限定名称，以标记此为匿名内部类
                this.fullyQualifiedDefiningMethodName = innermostEnclosingClassName + "." + definingMethodName;
            } else if (constantPoolStringEquals(attributeNameCpIdx, "Module")) {
                final int moduleNameCpIdx = reader.readUnsignedShort();
                Classpath.moduleNameFromModuleDescriptor = getConstantPoolString(moduleNameCpIdx);
                // (未来工作)：解析模块描述符字段的其余部分，并添加到 ModuleInfo：
                // https://docs.oracle.com/javase/specs/jvms/se9/html/jvms-4.html#jvms-4.7.25
                reader.skip(attributeLength - 2);
            } else {
                reader.skip(attributeLength);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    public interface ClassTypeAnnotationDecorator {
        void decorate(ClassType ClassType);
    }

    // -------------------------------------------------------------------------------------------------------------

    public interface MethodTypeAnnotationDecorator {
        void decorate(MethodType MethodType);
    }

    // -------------------------------------------------------------------------------------------------------------

    public interface TypeAnnotationDecorator {
        void decorate(TypeSignature typeSignature);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 类包含关系
     */
    public static class ClassContainment {
        /** 内部类名 */
        public final String innerClassName;

        /** 内部类修饰符位 */
        public final int innerClassModifierBits;

        /** 外部类名 */
        public final String outerClassName;

        /**
         * 构造函数
         *
         * @param innerClassName
         *            内部类名
         * @param innerClassModifierBits
         *            内部类修饰符位
         * @param outerClassName
         *            外部类名
         */
        public ClassContainment(final String innerClassName, final int innerClassModifierBits,
                                final String outerClassName) {
            this.innerClassName = innerClassName;
            this.innerClassModifierBits = innerClassModifierBits;
            this.outerClassName = outerClassName;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 当 ClassParser 的内容格式不正确时抛出 */
    public static class ClassfileFormatException extends IOException {
        /** serialVersionUID */
        static final long serialVersionUID = 1L;

        /**
         * 构造函数
         *
         * @param message
         *            消息
         */
        public ClassfileFormatException(final String message) {
            super(message);
        }

        /**
         * 构造函数
         *
         * @param message
         *            消息
         * @param cause
         *            原因
         */
        public ClassfileFormatException(final String message, final Throwable cause) {
            super(message, cause);
        }

        /**
         * 加速异常(此异常不需要堆栈跟踪)
         *
         * @return this
         */
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 当 ClassParser 需要被跳过时抛出 */
    public static class SkipClassException extends IOException {
        /** serialVersionUID */
        static final long serialVersionUID = 1L;

        /**
         * 构造函数
         *
         * @param message
         *            消息
         */
        public SkipClassException(final String message) {
            super(message);
        }

        /**
         * 加速异常(此异常不需要堆栈跟踪)
         *
         * @return this
         */
        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    public static class TypePathNode {
        public short typePathKind;
        public short TypeArgIdx;

        public TypePathNode(final int typePathKind, final int TypeArgIdx) {
            this.typePathKind = (short) typePathKind;
            this.TypeArgIdx = (short) TypeArgIdx;
        }

        @Override
        public String toString() {
            return "(" + typePathKind + "," + TypeArgIdx + ")";
        }
    }
}
