package com.bingbaihanji.fxdecomplie.util.reflect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class 工具类,借鉴 iBATIS Io 包的 ResolverUtil 类,提供包扫描、类查找、注解查找等功能
 *
 * @author bingbaihanji
 * @date 2026-06-10
 */
public class ClassUtils {

    private static final Logger log = LoggerFactory.getLogger(ClassUtils.class);

    /** 扫描结果匹配集合 */
    private final Set<Class<?>> matches = new HashSet<>();

    /** 查找类时使用的 ClassLoader,为 null 时使用当前线程上下文 ClassLoader */
    private ClassLoader classLoader;

    /**
     * 查询指定接口/类的实现类/子类(非自身、非抽象)
     *
     * @param parent       父接口或父类
     * @param packageNames 要扫描的包名列表(含子包)
     * @return 匹配的类集合
     */
    public static <T> Set<Class<?>> findImplementations(Class<?> parent, String... packageNames) {
        if (packageNames == null) {
            return new HashSet<>();
        }
        ClassUtils scanner = new ClassUtils();
        Test test = new IsA(parent);
        for (String pkg : packageNames) {
            scanner.find(test, pkg);
        }
        return scanner.getClasses();
    }

    /**
     * 查询带有指定注解的类
     *
     * @param annotation   目标注解类型
     * @param packageNames 要扫描的包名列表(含子包)
     * @return 匹配的类集合
     */
    public static Set<Class<?>> findAnnotated(Class<? extends Annotation> annotation, String... packageNames) {
        if (packageNames == null) {
            return new HashSet<>();
        }
        ClassUtils scanner = new ClassUtils();
        Test test = new AnnotatedWith(annotation);
        for (String pkg : packageNames) {
            scanner.find(test, pkg);
        }
        return scanner.getClasses();
    }

    /** @return 已发现的类集合 */
    public Set<Class<?>> getClasses() {
        return matches;
    }

    /** @return 扫描类时使用的 ClassLoader,若未设置则返回当前线程上下文 ClassLoader */
    public ClassLoader getClassLoader() {
        return classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    }

    /** 设置扫描类时使用的 ClassLoader */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 从指定包开始递归扫描类,通过 {@link Test} 过滤匹配的类
     */
    public ClassUtils find(Test test, String packageName) {
        String path = getPackagePath(packageName);
        try {
            VFS vfs = VFS.getInstance();
            if (vfs == null) {
                log.error("VFS 实例获取失败,无法扫描包: {}", packageName);
                return this;
            }
            List<String> children = vfs.list(path);
            for (String child : children) {
                if (child.endsWith(".class")) {
                    addIfMatching(test, child);
                }
            }
        } catch (IOException e) {
            log.error("无法读取包: {}", packageName, e);
        }
        return this;
    }

    /** 将 Java 包名转换为路径形式(点号替换为斜杠) */
    protected String getPackagePath(String packageName) {
        return packageName == null ? null : packageName.replace('.', '/');
    }

    /** 如果指定类通过了 Test 过滤,则添加到匹配结果中 */
    protected void addIfMatching(Test test, String qualifiedName) {
        try {
            String externalName = qualifiedName.substring(0, qualifiedName.indexOf('.')).replace('/', '.');
            ClassLoader loader = getClassLoader();
            if (log.isDebugEnabled()) {
                log.debug("检查类 {} 是否匹配 [{}]", externalName, test);
            }
            Class<?> type = loader.loadClass(externalName);
            if (test.matches(type)) {
                matches.add(type);
            }
        } catch (Exception e) {
            log.warn("无法检查类 '{}'：{} - {}", qualifiedName, e.getClass().getName(), e.getMessage());
        }
    }

    /** 类匹配测试接口 */
    @FunctionalInterface
    public interface Test {
        boolean matches(Class<?> type);
    }

    /** 查询实现类：匹配继承自 parentType 且非自身、非抽象的类 */
    public static class IsA implements Test {
        private final Class<?> parent;

        public IsA(Class<?> parentType) {
            this.parent = parentType;
        }

        @Override
        public boolean matches(Class<?> type) {
            return type != null && parent.isAssignableFrom(type)
                    && !type.getName().equals(parent.getName())
                    && !Modifier.isAbstract(type.getModifiers());
        }

        @Override
        public String toString() {
            return "is assignable to " + parent.getSimpleName();
        }
    }

    /** 查询带有指定注解的类 */
    public static class AnnotatedWith implements Test {
        private final Class<? extends Annotation> annotation;

        public AnnotatedWith(Class<? extends Annotation> annotation) {
            this.annotation = annotation;
        }

        @Override
        public boolean matches(Class<?> type) {
            return type != null && type.isAnnotationPresent(annotation);
        }

        @Override
        public String toString() {
            return "annotated with @" + annotation.getSimpleName();
        }
    }
}
