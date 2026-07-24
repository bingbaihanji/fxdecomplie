 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.metadata.FieldInfoList.FieldInfoFilter;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.util.*;

/** 管理类的字段和方法查询逻辑，包括覆盖顺序解析 */
public class ClassMembers {

    /** 所属的 ClassInfo 对象 */
    private final ClassInfo owner;
    /**
     * 类字段或方法的覆盖顺序（基类优先，然后是接口，然后是超类）
     */
    private volatile List<ClassInfo> overrideOrder;
    /**
     * 类方法的覆盖顺序（基类优先，然后是超类，然后是接口）
     */
    private volatile List<ClassInfo> methodOverrideOrder;

    /**
     * 构造函数
     *
     * @param owner 所属的 ClassInfo 对象
     */
    ClassMembers(final ClassInfo owner) {
        this.owner = owner;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 覆盖顺序

    /**
     * 递归遍历接口和超类，获取字段被覆盖的顺序
     *
     * @param visited          已访问的类集合
     * @param overrideOrderOut 输出的覆盖顺序列表
     * @return 覆盖顺序列表
     */
    private List<ClassInfo> getFieldOverrideOrder(final Set<ClassInfo> visited,
                                                  final List<ClassInfo> overrideOrderOut) {
        if (visited.add(owner)) {
            overrideOrderOut.add(owner);
            for (final ClassInfo iface : owner.hierarchy().getInterfaces()) {
                iface.members().getFieldOverrideOrder(visited, overrideOrderOut);
            }
            final ClassInfo superclass = owner.hierarchy().getSuperclass();
            if (superclass != null) {
                superclass.members().getFieldOverrideOrder(visited, overrideOrderOut);
            }
        }
        return overrideOrderOut;
    }

    /**
     * 获取字段覆盖的顺序（基类优先）
     *
     * @return 覆盖顺序列表
     */
    private List<ClassInfo> getFieldOverrideOrder() {
        if (overrideOrder == null) {
            synchronized (this) {
                if (overrideOrder == null) {
                    overrideOrder = owner.members().getFieldOverrideOrder(new HashSet<ClassInfo>(), new ArrayList<ClassInfo>());
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
     * @param visited          已访问 ClassInfo 的非空集合
     * @param overrideOrderOut 按覆盖优先级降序排列的 ClassInfo 输出列表
     * @return overrideOrderOut 实例
     */
    private List<ClassInfo> getMethodOverrideOrder(final Set<ClassInfo> visited,
                                                   final List<ClassInfo> overrideOrderOut) {
        if (!visited.add(owner)) {
            return overrideOrderOut;
        }
        // 首先收集具体超类，直接添加到 overrideOrder
        if (!owner.isInterfaceOrAnnotation()) {
            overrideOrderOut.add(owner);
            // 首先遍历直接超类，它们在方法覆盖方面具有最高优先级
            final ClassInfo superclass = owner.hierarchy().getSuperclass();
            if (superclass != null) {
                superclass.members().getMethodOverrideOrder(visited, overrideOrderOut);
            }
            for (final ClassInfo iface : owner.hierarchy().getInterfaces()) {
                iface.members().getMethodOverrideOrder(visited, overrideOrderOut);
            }
            return overrideOrderOut;
        }
        // overrideOrderOut 现在已包含所有具体类
        // 这是一个接口如果某个被扩展的接口已在输出中，则需要将其插入到该接口之前
        // 否则，此接口与目前收集的所有 ClassInfo 无关，可以直接添加到结果中
        // 编译器应该防止继承具有相同签名方法的不相关接口
        // 但由于在运行时动态链接了不同的接口，这种情况仍可能发生，此时返回的顺序未定义
        final ClassInfoList interfaces = owner.hierarchy().getInterfaces();
        int minIndex = Integer.MAX_VALUE;
        for (final ClassInfo iface : interfaces) {
            if (!visited.contains(iface)) {
                continue;
            }
            final int currIdx = overrideOrderOut.indexOf(iface);
            minIndex = currIdx >= 0 && currIdx < minIndex ? currIdx : minIndex;
        }
        if (minIndex == Integer.MAX_VALUE) {
            overrideOrderOut.add(owner);
        } else {
            overrideOrderOut.add(minIndex, owner);
        }
        // 将接口添加到覆盖顺序的末尾
        for (final ClassInfo iface : interfaces) {
            iface.members().getMethodOverrideOrder(visited, overrideOrderOut);
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
                    methodOverrideOrder = owner.members().getMethodOverrideOrder(new HashSet<ClassInfo>(), new ArrayList<ClassInfo>());
                }
            }
        }
        return methodOverrideOrder;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段信息

    /**
     * 返回此类声明（但不包括其超类声明）的所有可见字段的信息
     *
     * @return 此类声明的可见字段的 FieldInfo 对象列表，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableFieldInfo()
     */
    public FieldInfoList getDeclaredFieldInfo() {
        if (!owner.scanResult.ScanConfig.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        return owner.fieldInfo == null ? FieldInfoList.EMPTY_LIST : owner.fieldInfo;
    }

    /**
     * 返回此类或其超类声明的所有可见字段的信息
     *
     * @return 此类或其超类的可见字段的 FieldInfo 对象列表，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableFieldInfo()
     */
    public FieldInfoList getFieldInfo() {
        if (!owner.scanResult.ScanConfig.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        // 实现字段覆盖
        final FieldInfoList fieldInfoList = new FieldInfoList();
        final Set<String> fieldNameSet = new HashSet<>();
        for (final ClassInfo ci : owner.members().getFieldOverrideOrder()) {
            for (final FieldInfo fi : ci.members().getDeclaredFieldInfo()) {
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
     * 返回此类声明（但不包括其超类声明）的指定名称字段的信息
     *
     * @param fieldName 字段名
     * @return 此类声明的指定名称字段的 {@link FieldInfo} 对象，如果在此类中未找到该字段（或不可见）则返回 null
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableFieldInfo()
     */
    public FieldInfo getDeclaredFieldInfo(final String fieldName) {
        if (!owner.scanResult.ScanConfig.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        if (owner.fieldInfo == null) {
            return null;
        }
        for (final FieldInfo fi : owner.fieldInfo) {
            if (fi.getName().equals(fieldName)) {
                return fi;
            }
        }
        return null;
    }

    /**
     * 返回此类或其超类声明的指定名称字段的信息
     *
     * @param fieldName 字段名
     * @return 此类或其超类指定名称字段的 {@link FieldInfo} 对象，如果没有找到或可见的字段则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableFieldInfo()
     */
    public FieldInfo getFieldInfo(final String fieldName) {
        if (!owner.scanResult.ScanConfig.enableFieldInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableFieldInfo() before #scan()");
        }
        // 实现字段覆盖
        for (final ClassInfo ci : owner.members().getFieldOverrideOrder()) {
            final FieldInfo fi = ci.members().getDeclaredFieldInfo(fieldName);
            if (fi != null) {
                return fi;
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 字段谓词

    /**
     * 检查此类是否声明了指定名称的字段
     *
     * @param fieldName 字段的名称
     * @return 如果此类声明了给定名称的字段则返回 true
     */
    public boolean hasDeclaredField(final String fieldName) {
        return getDeclaredFieldInfo().containsName(fieldName);
    }

    /**
     * 检查此类或其超类之一是否具有指定名称的字段
     *
     * @param fieldName 字段的名称
     * @return 如果此类或其超类之一声明了给定名称的字段则返回 true
     */
    public boolean hasField(final String fieldName) {
        for (final ClassInfo ci : owner.members().getFieldOverrideOrder()) {
            if (ci.members().hasDeclaredField(fieldName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的字段
     *
     * @param annotation 一个字段注解
     * @return 如果此类声明了带有该注解的字段则返回 true
     */
    public boolean hasDeclaredFieldAnnotation(final Class<? extends Annotation> annotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(annotation);
        return hasDeclaredFieldAnnotation(annotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的字段
     *
     * @param fieldAnnotationName 字段注解的名称
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
     * @param fieldAnnotation 一个字段注解
     * @return 如果此类或其超类之一声明了带有该注解的字段则返回 true
     */
    public boolean hasFieldAnnotation(final Class<? extends Annotation> fieldAnnotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(fieldAnnotation);
        return hasFieldAnnotation(fieldAnnotation.getName());
    }

    /**
     * 检查此类或其超类之一是否声明了带有指定名称注解的字段
     *
     * @param fieldAnnotationName 字段注解的名称
     * @return 如果此类或其超类之一声明了带有指定名称注解的字段则返回 true
     */
    public boolean hasFieldAnnotation(final String fieldAnnotationName) {
        for (final ClassInfo ci : owner.members().getFieldOverrideOrder()) {
            if (ci.members().hasDeclaredFieldAnnotation(fieldAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法信息（内部）

    /**
     * 获取类中声明的方法、构造函数和/或静态初始化方法
     *
     * @param methodName                  方法名
     * @param getNormalMethods            是否获取普通方法
     * @param getConstructorMethods       是否获取构造函数
     * @param getStaticInitializerMethods 是否获取静态初始化方法
     * @return 声明的方法信息
     */
    private MethodInfoList getDeclaredMethodInfo(final String methodName, final boolean getNormalMethods,
                                                 final boolean getConstructorMethods, final boolean getStaticInitializerMethods) {
        if (!owner.scanResult.ScanConfig.enableMethodInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableMethodInfo() before #scan()");
        }
        if (owner.methodInfo == null) {
            return MethodInfoList.EMPTY_LIST;
        }
        if (methodName == null) {
            // 如果没有提供方法名，则按方法类型筛选（普通方法/构造函数/静态初始化方法）
            final MethodInfoList methodInfoList = new MethodInfoList();
            for (final MethodInfo mi : owner.methodInfo) {
                final String miName = mi.getName();
                final boolean isConstructor = "<init>".equals(miName);
                // （目前静态初始化方法永远不会被公共方法返回）
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
            for (final MethodInfo f : owner.methodInfo) {
                if (f.getName().equals(methodName)) {
                    hasMethodWithName = true;
                    break;
                }
            }
            if (!hasMethodWithName) {
                return MethodInfoList.EMPTY_LIST;
            }
            final MethodInfoList methodInfoList = new MethodInfoList();
            for (final MethodInfo mi : owner.methodInfo) {
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
     * @param methodName                  方法名
     * @param getNormalMethods            是否获取普通方法
     * @param getConstructorMethods       是否获取构造函数
     * @param getStaticInitializerMethods 是否获取静态初始化方法
     * @return 方法信息
     */
    private MethodInfoList getMethodInfo(final String methodName, final boolean getNormalMethods,
                                         final boolean getConstructorMethods, final boolean getStaticInitializerMethods) {
        if (!owner.scanResult.ScanConfig.enableMethodInfo) {
            throw new IllegalArgumentException("Please call ClassGraph#enableMethodInfo() before #scan()");
        }
        // 实现方法/构造函数覆盖
        final MethodInfoList methodInfoList = new MethodInfoList();
        final Set<Map.Entry<String, String>> nameAndTypeDescriptorSet = new HashSet<>();
        for (final ClassInfo ci : owner.members().getMethodOverrideOrder()) {
            // 构造函数不从超类继承
            boolean shouldGetConstructorMethods = ci == owner && getConstructorMethods;
            for (final MethodInfo mi : ci.members().getDeclaredMethodInfo(methodName, getNormalMethods, shouldGetConstructorMethods,
                    getStaticInitializerMethods)) {
                // 如果方法尚未被具有相同名称和类型描述符的方法覆盖
                if (nameAndTypeDescriptorSet.add(new AbstractMap.SimpleEntry<>(mi.getName(), mi.getTypeDescriptorStr()))) {
                    // 将方法添加到输出顺序中
                    methodInfoList.add(mi);
                }
            }
        }
        return methodInfoList;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法信息（公开）

    /**
     * 返回此类声明（但不包括其接口或超类声明）的可见方法（非构造函数）的信息
     *
     * @return 此类声明的可见方法的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getDeclaredMethodInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ false, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类或其接口或超类声明的可见方法（非构造函数）的信息
     *
     * @return 此类、其接口和超类的可见方法的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getMethodInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ false, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明（但不包括其接口或超类声明）的可见构造函数的信息
     *
     * @return 此类声明的可见构造函数的 {@link MethodInfo} 对象列表，如果没有找到或可见的构造函数则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getDeclaredConstructorInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ false,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类或其接口或超类声明的可见构造函数的信息
     *
     * @return 此类及其超类的可见构造函数的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getConstructorInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ false,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明（但不包括其接口或超类声明）的可见方法和构造函数的信息
     *
     * @return 此类可见方法和构造函数的 {@link MethodInfo} 对象列表，如果没有找到或可见的方法或构造函数则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getDeclaredMethodAndConstructorInfo() {
        return getDeclaredMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类或其接口或超类声明的可见构造函数的信息
     *
     * @return 此类、其接口和超类的可见方法和构造函数的 {@link MethodInfo} 对象列表，如果没有找到方法则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getMethodAndConstructorInfo() {
        return getMethodInfo(/* methodName = */ null, /* getNormalMethods = */ true,
                /* getConstructorMethods = */ true, /* getStaticInitializerMethods = */ false);
    }

    /**
     * 返回此类声明（但不包括其接口或超类声明）的指定名称的方法或构造函数的信息
     *
     * @param methodName 要查询的方法名
     * @return 指定名称方法的 {@link MethodInfo} 对象列表，如果在此类中未找到该方法（或不可见）则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getDeclaredMethodInfo(final String methodName) {
        return getDeclaredMethodInfo(methodName, /* ignored */ false, /* ignored */ false, /* ignored */ false);
    }

    /**
     * 返回此类或其接口或超类声明的指定名称的方法或构造函数的信息
     *
     * @param methodName 要查询的方法名
     * @return 指定名称方法的 {@link MethodInfo} 对象列表，如果在此类中未找到该方法（或不可见）则返回空列表
     * @throws IllegalArgumentException 如果在启动扫描前未调用 ClassGraph#enableMethodInfo()
     */
    public MethodInfoList getMethodInfo(final String methodName) {
        return getMethodInfo(methodName, /* ignored */ false, /* ignored */ false, /* ignored */ false);
    }

    // -------------------------------------------------------------------------------------------------------------
    // 方法谓词

    /**
     * 检查此类是否声明了给定名称的方法
     *
     * @param methodName 方法的名称
     * @return 如果此类声明了给定名称的方法则返回 true
     */
    public boolean hasDeclaredMethod(final String methodName) {
        return getDeclaredMethodInfo().containsName(methodName);
    }

    /**
     * 检查此类或其超类或接口之一是否声明了给定名称的方法
     *
     * @param methodName 方法的名称
     * @return 如果此类或其超类或接口之一声明了给定名称的方法则返回 true
     */
    public boolean hasMethod(final String methodName) {
        for (final ClassInfo ci : owner.members().getMethodOverrideOrder()) {
            if (ci.members().hasDeclaredMethod(methodName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的方法
     *
     * @param methodAnnotation 一个方法注解
     * @return 如果此类声明了带有该注解的方法则返回 true
     */
    public boolean hasDeclaredMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(methodAnnotation);
        return hasDeclaredMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的方法
     *
     * @param methodAnnotationName 方法注解的名称
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
     * @param methodAnnotation 一个方法注解
     * @return 如果此类或其超类或接口之一声明了带有该注解的方法则返回 true
     */
    public boolean hasMethodAnnotation(final Class<? extends Annotation> methodAnnotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(methodAnnotation);
        return hasMethodAnnotation(methodAnnotation.getName());
    }

    /**
     * 检查此类或其超类或接口之一是否声明了带有指定名称注解的方法
     *
     * @param methodAnnotationName 方法注解的名称
     * @return 如果此类或其超类或接口之一声明了带有指定名称注解的方法则返回 true
     */
    public boolean hasMethodAnnotation(final String methodAnnotationName) {
        for (final ClassInfo ci : owner.members().getMethodOverrideOrder()) {
            if (ci.members().hasDeclaredMethodAnnotation(methodAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查此类是否声明了带有指定注解的方法参数
     *
     * @param methodParameterAnnotation 一个方法参数注解
     * @return 如果此类声明了带有该注解的方法参数则返回 true
     */
    public boolean hasDeclaredMethodParameterAnnotation(
            final Class<? extends Annotation> methodParameterAnnotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(methodParameterAnnotation);
        return hasDeclaredMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 检查此类是否声明了带有指定名称注解的方法参数
     *
     * @param methodParameterAnnotationName 方法参数注解的名称
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
     * @param methodParameterAnnotation 一个方法参数注解
     * @return 如果此类或其超类或接口之一具有带有该注解的方法参数则返回 true
     */
    public boolean hasMethodParameterAnnotation(final Class<? extends Annotation> methodParameterAnnotation) {
        com.bingbaihanji.classgraph.util.Assert.isAnnotation(methodParameterAnnotation);
        return hasMethodParameterAnnotation(methodParameterAnnotation.getName());
    }

    /**
     * 检查此类或其超类或接口之一是否具有带有指定名称注解的方法参数
     *
     * @param methodParameterAnnotationName 方法参数注解的名称
     * @return 如果此类或其超类或接口之一具有带有指定名称注解的方法参数则返回 true
     */
    public boolean hasMethodParameterAnnotation(final String methodParameterAnnotationName) {
        for (final ClassInfo ci : owner.members().getMethodOverrideOrder()) {
            if (ci.members().hasDeclaredMethodParameterAnnotation(methodParameterAnnotationName)) {
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 枚举常量

    /**
     * 获取枚举类的枚举常量
     *
     * @return 枚举类的所有枚举常量，作为 {@link FieldInfo} 对象列表（枚举常量在 Java 类中存储为字段）
     */
    public FieldInfoList getEnumConstants() {
        if (!owner.isEnum()) {
            throw new IllegalArgumentException("Class " + owner.getName() + " is not an enum");
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
        if (!owner.isEnum()) {
            throw new IllegalArgumentException("Class " + owner.getName() + " is not an enum");
        }
        final Class<?> enumClass = owner.loadClass();
        final FieldInfoList consts = getEnumConstants();
        final List<Object> constObjs = new ArrayList<>(consts.size());
        final ReflectionUtils reflectionUtils = owner.scanResult == null ? new ReflectionUtils()
                : owner.scanResult.reflectionUtils;
        for (final FieldInfo constFieldInfo : consts) {
            final Object constObj = reflectionUtils.getStaticFieldVal(true, enumClass, constFieldInfo.getName());
            if (constObj == null) {
                throw new IllegalArgumentException("Could not read enum constant objects");
            }
            constObjs.add(constObj);
        }
        return constObjs;
    }
}
