package com.bingbaihanji.fxdecomplie.util.reflect.asm;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.WeakHashMap;

/**
 * ASM 字节码生成的类加载器
 * <p>
 * 为 {@link FieldAccess} 和 {@link MethodAccess} 提供运行时字节码定义能力 使用 WeakHashMap 弱引用避免应用服务器中的
 * PermGen 内存泄漏
 * </p>
 *
 * @author bingbaihanji
 * @date 2025-12-08
 */
@SuppressWarnings({"restriction", "rawtypes", "unchecked"})
class AccessClassLoader extends ClassLoader {

    /** 弱引用类加载器缓存,key 为父 ClassLoader,value 为 AccessClassLoader 的弱引用 */
    private static final WeakHashMap<ClassLoader, WeakReference<AccessClassLoader>> accessClassLoaders = new WeakHashMap<>();

    /** 本类所在 ClassLoader 的父加载器,用于快速路径判断 */
    private static final ClassLoader selfContextParentClassLoader = getParentClassLoader(AccessClassLoader.class);

    /** 本类所在 ClassLoader 对应的 AccessClassLoader 实例 */
    private static volatile AccessClassLoader selfContextAccessClassLoader = new AccessClassLoader(
            selfContextParentClassLoader);

    /** 已定义的访问类名称集合 */
    private final HashSet<String> localClassNames = new HashSet<>();

    private AccessClassLoader(ClassLoader parent) {
        super(parent);
    }

    /**
     * 判断两个类是否在同一个运行时 ClassLoader 中 根据 JLS 第 5.3 节,运行时包由包名和定义类加载器共同决定
     */
    static boolean areInSameRuntimeClassLoader(Class type1, Class type2) {
        if (type1.getPackage() != type2.getPackage()) {
            return false;
        }
        ClassLoader loader1 = type1.getClassLoader();
        ClassLoader loader2 = type2.getClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();
        if (loader1 == null) {
            return (loader2 == null || loader2 == systemClassLoader);
        }
        if (loader2 == null) {
            return loader1 == systemClassLoader;
        }
        return loader1 == loader2;
    }

    /**
     * 获取指定类的父 ClassLoader,为 null 时返回系统 ClassLoader
     */
    private static ClassLoader getParentClassLoader(Class type) {
        ClassLoader parent = type.getClassLoader();
        if (parent == null) {
            parent = ClassLoader.getSystemClassLoader();
        }
        return parent;
    }

    /**
     * 获取指定类对应的 AccessClassLoader 实例
     * <ul>
     * <li>快速路径：如果目标类与本类在同一个 ClassLoader,直接返回缓存实例</li>
     * <li>普通查找：从 WeakHashMap 中查找或创建新的 AccessClassLoader</li>
     * </ul>
     */
    static AccessClassLoader get(Class type) {
        ClassLoader parent = getParentClassLoader(type);
        if (selfContextParentClassLoader.equals(parent)) {
            if (selfContextAccessClassLoader == null) {
                synchronized (accessClassLoaders) {
                    if (selfContextAccessClassLoader == null) {
                        selfContextAccessClassLoader = new AccessClassLoader(selfContextParentClassLoader);
                    }
                }
            }
            return selfContextAccessClassLoader;
        }
        synchronized (accessClassLoaders) {
            WeakReference<AccessClassLoader> ref = accessClassLoaders.get(parent);
            if (ref != null) {
                AccessClassLoader accessClassLoader = ref.get();
                if (accessClassLoader != null) {
                    return accessClassLoader;
                }
                accessClassLoaders.remove(parent);
            }
            AccessClassLoader accessClassLoader = new AccessClassLoader(parent);
            accessClassLoaders.put(parent, new WeakReference<>(accessClassLoader));
            return accessClassLoader;
        }
    }

    /**
     * 移除指定 ClassLoader 对应的 AccessClassLoader 缓存
     */
    public static void remove(ClassLoader parent) {
        if (selfContextParentClassLoader.equals(parent)) {
            selfContextAccessClassLoader = null;
        } else {
            synchronized (accessClassLoaders) {
                accessClassLoaders.remove(parent);
            }
        }
    }

    /**
     * @return 当前活跃的 AccessClassLoader 数量
     */
    public static int activeAccessClassLoaders() {
        int sz = accessClassLoaders.size();
        if (selfContextAccessClassLoader != null) {
            sz++;
        }
        return sz;
    }

    /**
     * 加载已定义的访问类如果尚未定义则返回 null
     */
    Class loadAccessClass(String name) {
        if (localClassNames.contains(name)) {
            try {
                return loadClass(name, false);
            } catch (ClassNotFoundException ex) {
                throw new RuntimeException(ex);
            }
        }
        return null;
    }

    /**
     * 定义一个新的访问类
     */
    Class defineAccessClass(String name, byte[] bytes) throws ClassFormatError {
        localClassNames.add(name);
        return defineClass(name, bytes);
    }

    /**
     * 加载类FieldAccess 和 MethodAccess 由本 ClassLoader 的父加载器提供, 其他类由目标类的 ClassLoader 加载
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.equals(FieldAccess.class.getName())) {
            return FieldAccess.class;
        }
        if (name.equals(MethodAccess.class.getName())) {
            return MethodAccess.class;
        }
        return super.loadClass(name, resolve);
    }

    /**
     * 定义字节码类,使用当前加载器的 defineClass
     */
    Class<?> defineClass(String name, byte[] bytes) throws ClassFormatError {
        return defineClass(name, bytes, 0, bytes.length, getClass().getProtectionDomain());
    }

}
