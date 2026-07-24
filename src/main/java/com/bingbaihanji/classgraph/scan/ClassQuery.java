 
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfoList;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Facade for class-related queries on a ScanResult.
 */
public class ClassQuery {
    private final Map<String, ClassInfo> classNameToClassInfo;
    private final ScanConfig scanConfig;
    private final AtomicBoolean closed;

    ClassQuery(final Map<String, ClassInfo> classNameToClassInfo,
               final ScanConfig scanConfig,
               final AtomicBoolean closed) {
        this.classNameToClassInfo = classNameToClassInfo;
        this.scanConfig = scanConfig;
        this.closed = closed;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类

    /**
     * 获取扫描期间找到的所有类、接口和注解
     *
     * @return 扫描期间找到的所有被接受类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllClasses() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllClasses(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取扫描期间找到的所有 {@link Enum} 类
     *
     * @return 扫描期间找到的所有 {@link Enum} 类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllEnums() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllEnums(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取扫描期间找到的所有 {@code record} 类(JDK 14+)
     *
     * @return 扫描期间找到的所有 {@code record} 类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllRecords() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllRecords(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取从类名到 {@link ClassInfo} 对象的映射，包含扫描期间找到的所有类、接口和注解
     *
     * @return 从类名到 {@link ClassInfo} 对象的映射，包含扫描期间找到的所有类、接口和注解
     */
    public Map<String, ClassInfo> getAllClassesAsMap() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return classNameToClassInfo;
    }

    /**
     * 获取扫描期间找到的所有标准(非接口/非注解)类
     *
     * @return 扫描期间找到的所有被接受标准类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllStandardClasses() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllStandardClasses(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取命名类的 {@link ClassInfo} 对象，如果扫描期间在被接受/未被拒绝的包中未找到请求名称的类则返回 null
     *
     * @param className
     *            类名称
     * @return 命名类的 {@link ClassInfo} 对象，如果未找到该类则返回 null
     */
    public ClassInfo getClassInfo(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return classNameToClassInfo.get(className);
    }

    /**
     * 获取超类的所有子类
     *
     * @param superclass
     *            超类
     * @return 超类的子类列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses(final Class<?> superclass) {
        return getSubclasses(superclass.getName());
    }

    /**
     * 获取命名超类的所有子类
     *
     * @param superclassName
     *            超类的名称
     * @return 命名超类的子类列表，如果没有则返回空列表
     */
    public ClassInfoList getSubclasses(final String superclassName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        if ("java.lang.Object".equals(superclassName)) {
            // 返回所有标准类(接口不继承 Object)
            return getAllStandardClasses();
        } else {
            final ClassInfo superclass = classNameToClassInfo.get(superclassName);
            return superclass == null ? ClassInfoList.EMPTY_LIST : superclass.getSubclasses();
        }
    }

    /**
     * 获取命名子类的超类
     *
     * @param subclassName
     *            子类的名称
     * @return 命名子类的超类列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses(final String subclassName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo subclass = classNameToClassInfo.get(subclassName);
        return subclass == null ? ClassInfoList.EMPTY_LIST : subclass.getSuperclasses();
    }

    /**
     * 获取子类的超类
     *
     * @param subclass
     *            子类
     * @return 命名子类的超类列表，如果没有则返回空列表
     */
    public ClassInfoList getSuperclasses(final Class<?> subclass) {
        return getSuperclasses(subclass.getName());
    }

    // -------------------------------------------------------------------------------------------------------------
    // 接口

    /**
     * 获取扫描期间找到的所有接口类(不包括注解，注解在技术上也是接口)
     * 另请参阅 {@link #getAllInterfacesAndAnnotations()}
     *
     * @return 扫描期间找到的所有被接受接口的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllInterfaces() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        return ClassInfo.getAllImplementedInterfaceClasses(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取由命名类或其超类之一实现的所有接口(如果命名类是标准类)，
     * 或由此接口扩展的超接口(如果它是接口)
     *
     * @param className
     *            类名称
     * @return 由命名类实现的接口(或由命名接口扩展的超接口)列表，如果没有则返回空列表
     */
    public ClassInfoList getInterfaces(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getInterfaces();
    }

    /**
     * 获取由给定类或其超类之一实现的所有接口(如果给定类是标准类)，
     * 或由此接口扩展的超接口(如果它是接口)
     *
     * @param classRef
     *            类
     * @return 由给定类实现的接口(或由给定接口扩展的超接口)列表，如果没有则返回空列表
     */
    public ClassInfoList getInterfaces(final Class<?> classRef) {
        return getInterfaces(classRef.getName());
    }

    /**
     * 获取实现(或有超类实现)该接口(或其子接口之一)的所有类
     *
     * @param interfaceClass
     *            接口类
     * @return 实现该接口的所有类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesImplementing(final Class<?> interfaceClass) {
        Assert.isInterface(interfaceClass);
        return getClassesImplementing(interfaceClass.getName());
    }

    /**
     * 获取实现(或有超类实现)命名接口(或其子接口之一)的所有类
     *
     * @param interfaceName
     *            接口名称
     * @return 实现命名接口的所有类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesImplementing(final String interfaceName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(interfaceName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesImplementing();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 注解

    /**
     * 获取扫描期间找到的所有注解类另请参阅 {@link #getAllInterfacesAndAnnotations()}
     *
     * @return 扫描期间找到的所有注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllAnnotations() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        return ClassInfo.getAllAnnotationClasses(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取扫描期间找到的所有接口或注解类(注解在技术上是接口，并且它们可以被实现)
     *
     * @return 扫描期间找到的所有被接受接口的列表，如果没有则返回空列表
     */
    public ClassInfoList getAllInterfacesAndAnnotations() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        return ClassInfo.getAllInterfacesOrAnnotationClasses(classNameToClassInfo.values(), scanConfig);
    }

    /**
     * 获取具有类注解或元注解的类
     *
     * @param annotation
     *            类注解或元注解
     * @return 在扫描期间找到的具有该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getClassesWithAnnotation(annotation.getName());
    }

    /**
     * 获取具有所有指定类注解或元注解的类
     *
     * @param annotations
     *            类注解或元注解
     * @return 在扫描期间找到的具有任何该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    @SuppressWarnings("unchecked")
    public ClassInfoList getClassesWithAllAnnotations(final Class<? extends Annotation>... annotations) {
        final List<String> annotationNames = new ArrayList<>();
        for (final Class<?> cls : annotations) {
            Assert.isAnnotation(cls);
            annotationNames.add(cls.getName());
        }
        return getClassesWithAllAnnotations(annotationNames.toArray(new String[0]));
    }

    /**
     * 获取具有任意指定类注解或元注解的类
     *
     * @param annotations
     *            类注解或元注解
     * @return 在扫描期间找到的具有任何该类注解的所有非注解类的列表，如果没有则返回空列表
     */
    @SuppressWarnings("unchecked")
    public ClassInfoList getClassesWithAnyAnnotation(final Class<? extends Annotation>... annotations) {
        final List<String> annotationNames = new ArrayList<>();
        for (final Class<?> cls : annotations) {
            Assert.isAnnotation(cls);
            annotationNames.add(cls.getName());
        }
        return getClassesWithAnyAnnotation(annotationNames.toArray(new String[0]));
    }

    /**
     * 获取具有命名类注解或元注解的类
     *
     * @param annotationName
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnnotation(final String annotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(annotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithAnnotation();
    }

    /**
     * 获取具有所有命名类注解或元注解的类
     *
     * @param annotationNames
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有所有命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAllAnnotations(final String... annotationNames) {
        ClassInfoList foundClassInfo = null;
        for (final String annotationName : annotationNames) {
            final ClassInfoList classInfoList = getClassesWithAnnotation(annotationName);
            if (foundClassInfo == null) {
                foundClassInfo = classInfoList;
            } else {
                foundClassInfo = foundClassInfo.intersect(classInfoList);
            }
        }
        CollectionUtils.sortIfNotEmpty(foundClassInfo);
        return foundClassInfo == null ? ClassInfoList.EMPTY_LIST : foundClassInfo;
    }

    /**
     * 获取具有任意命名类注解或元注解的类
     *
     * @param annotationNames
     *            类注解或元注解的名称
     * @return 在扫描期间找到的具有任意命名类注解的所有非注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithAnyAnnotation(final String... annotationNames) {
        ClassInfoList foundClassInfo = null;
        for (final String annotationName : annotationNames) {
            final ClassInfoList classInfoList = getClassesWithAnnotation(annotationName);
            if (foundClassInfo == null) {
                foundClassInfo = classInfoList;
            } else {
                foundClassInfo = foundClassInfo.union(classInfoList);
            }
        }
        CollectionUtils.sortIfNotEmpty(foundClassInfo);
        return foundClassInfo == null ? ClassInfoList.EMPTY_LIST : foundClassInfo;
    }

    /**
     * 获取命名类上的注解这仅返回注解类；要读取注解参数，请调用
     * {@link #getClassInfo(String)} 获取命名类的 {@link ClassInfo} 对象，
     * 然后如果 {@link ClassInfo} 对象非 null，调用 {@link ClassInfo#getAnnotationInfo()} 获取详细的注解信息
     *
     * @param className
     *            类的名称
     * @return 在扫描期间找到的具有命名类注解的所有注解类的列表，如果没有则返回空列表
     */
    public ClassInfoList getAnnotationsOnClass(final String className) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableClassInfo() and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(className);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getAnnotations();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法注解

    /**
     * 获取具有带有命名类型注解的方法的类
     *
     * @param methodAnnotation
     *            方法注解
     * @return 具有带有命名类型注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        Assert.isAnnotation(methodAnnotation);
        return getClassesWithMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的方法的类
     *
     * @param methodAnnotationName
     *            方法注解的名称
     * @return 具有带有命名类型注解的方法的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodAnnotation(final String methodAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableMethodInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableMethodInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(methodAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithMethodAnnotation();
    }

    /**
     * 获取具有带有命名类型注解的方法参数的类
     *
     * @param methodParameterAnnotation
     *            方法参数注解
     * @return 具有带有命名类型注解的方法参数的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation(
            final Class<? extends Annotation> methodParameterAnnotation) {
        Assert.isAnnotation(methodParameterAnnotation);
        return getClassesWithMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的方法参数的类
     *
     * @param methodParameterAnnotationName
     *            方法参数注解的名称
     * @return 具有带有命名类型注解的方法参数的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithMethodParameterAnnotation(final String methodParameterAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableMethodInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableMethodInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(methodParameterAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithMethodParameterAnnotation();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段注解

    /**
     * 获取具有带有命名类型注解的字段的类
     *
     * @param fieldAnnotation
     *            字段注解
     * @return 具有带有命名类型注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation(final Class<? extends Annotation> fieldAnnotation) {
        Assert.isAnnotation(fieldAnnotation);
        return getClassesWithFieldAnnotation(fieldAnnotation.getName());
    }

    /**
     * 获取具有带有命名类型注解的字段的类
     *
     * @param fieldAnnotationName
     *            字段注解的名称
     * @return 具有带有命名类型注解的字段的类的列表，如果没有则返回空列表
     */
    public ClassInfoList getClassesWithFieldAnnotation(final String fieldAnnotationName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        if (!scanConfig.enableClassInfo || !scanConfig.enableFieldInfo || !scanConfig.enableAnnotationInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableClassInfo(), #enableFieldInfo(), "
                    + "and #enableAnnotationInfo() before #scan()");
        }
        final ClassInfo classInfo = classNameToClassInfo.get(fieldAnnotationName);
        return classInfo == null ? ClassInfoList.EMPTY_LIST : classInfo.getClassesWithFieldAnnotation();
    }

    // -------------------------------------------------------------------------------------------------------------
    // 类依赖

    /**
     * 获取从每个被接受类的 {@link ClassInfo} 对象到该类引用的类列表的映射
     * (即返回从依赖者到依赖项的映射)注意，你需要调用
     * {@link ClassGraph#withInterClassDependencies()} 然后再调用 {@link ClassGraph#scan()}，此方法才能工作
     * 如果你希望未被接受的类出现在结果中，你还应该在 {@link ClassGraph#scan()} 之前调用
     * {@link ClassGraph#withExternalClasses()}
     * 另请参阅 {@link #getReverseClassDependencyMap()}，它将映射反转
     *
     * @return 从每个被接受类的 {@link ClassInfo} 对象到该类引用的类列表的映射
     *         (即返回从依赖者到依赖项的映射)每个映射值是在对应键上调用
     *         {@link ClassInfo#getClassDependencies()} 的结果
     */
    public Map<ClassInfo, ClassInfoList> getClassDependencyMap() {
        final Map<ClassInfo, ClassInfoList> map = new HashMap<>();
        for (final ClassInfo ci : getAllClasses()) {
            map.put(ci, ci.getClassDependencies());
        }
        return map;
    }

    /**
     * 获取反向类依赖映射，即从每个依赖类(无论是否被接受)的 {@link ClassInfo} 对象到
     * 将该类作为依赖引用的被接受类的列表的映射(即返回从依赖项到依赖者的映射)
     * 注意，你需要调用 {@link ClassGraph#withInterClassDependencies()} 然后再调用
     * {@link ClassGraph#scan()}，此方法才能工作如果你希望未被接受的类出现在结果中，
     * 你还应该在 {@link ClassGraph#scan()} 之前调用 {@link ClassGraph#withExternalClasses()}
     * 另请参阅 {@link #getClassDependencyMap()}
     *
     * @return 从每个依赖类(无论是否被接受)的 {@link ClassInfo} 对象到
     *         将该类作为依赖引用的被接受类的列表的映射(即返回从依赖项到依赖者的映射)
     */
    public Map<ClassInfo, ClassInfoList> getReverseClassDependencyMap() {
        final Map<ClassInfo, Set<ClassInfo>> revMapSet = new HashMap<>();
        for (final ClassInfo ci : getAllClasses()) {
            for (final ClassInfo dep : ci.getClassDependencies()) {
                Set<ClassInfo> set = revMapSet.get(dep);
                if (set == null) {
                    revMapSet.put(dep, set = new HashSet<>());
                }
                set.add(ci);
            }
        }
        final Map<ClassInfo, ClassInfoList> revMapList = new HashMap<>();
        for (final Entry<ClassInfo, Set<ClassInfo>> ent : revMapSet.entrySet()) {
            revMapList.put(ent.getKey(), new ClassInfoList(ent.getValue(), /* sortByName = */ true));
        }
        return revMapList;
    }
}
