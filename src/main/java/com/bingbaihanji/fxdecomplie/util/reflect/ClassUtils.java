package com.bingbaihanji.fxdecomplie.util.reflect;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

/**
 * Class 工具类,借鉴 iBATIS Io 包的 ResolverUtil 类,提供包扫描、类查找、注解查找等功能
 *
 * @author bingbaihanji
 * @date 2026-06-10
 */

public class ClassUtils {

    private final static Logger log = LoggerFactory.getLogger(ClassUtils.class);

    /** 扫描结果匹配集合 */
    private final Set<Class<?>> matches = new HashSet<>();

    /** 查找类时使用的 ClassLoader,为 null 时使用当前线程上下文 ClassLoader */
    private ClassLoader classLoader;

    /**
     * 查询指定接口/类的实现类/子类(非自身、非抽象)
     * @param parent 父接口或父类
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
     * @param annotation 目标注解类型
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

    /**
     * @return 已发现的类集合
     */
    public Set<Class<?>> getClasses() {
        return matches;
    }

    /**
     * @return 扫描类时使用的 ClassLoader,若未设置则返回当前线程上下文 ClassLoader
     */
    public ClassLoader getClassLoader() {
        return classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
    }

    /**
     * 设置扫描类时使用的 ClassLoader
     * @param classLoader 用于加载类的 ClassLoader
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * 从指定包开始递归扫描类,通过 {@link Test} 过滤匹配的类
     * @param test 类匹配过滤器
     * @param packageName 起始包名
     * @return 当前 ClassUtils 实例(支持链式调用)
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

    /**
     * 将 Java 包名转换为路径形式(点号替换为斜杠)
     */
    protected String getPackagePath(String packageName) {
        return packageName == null ? null : packageName.replace('.', '/');
    }

    /**
     * 如果指定类通过了 Test 过滤,则添加到匹配结果中
     * @param test 类匹配过滤器
     * @param qualifiedName 类全限定名(路径形式,以 / 分隔)
     */
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

    /**
     * 类匹配测试接口,用于判断某个类是否满足条件
     */
    public interface Test {

        /**
         * @param type 待检测的类
         * @return 如果该类应被包含在结果中返回 true
         */
        boolean matches(Class<?> type);

    }

    /**
     * 查询实现类：匹配继承自 parentType 且非自身、非抽象的类
     */
    public static class IsA implements Test {

        private final Class<?> parent;

        public IsA(Class<?> parentType) {
            this.parent = parentType;
        }

        @Override
        public boolean matches(Class<?> type) {
            return type != null && parent.isAssignableFrom(type) && !type.getName().equals(parent.getName())
                    && !Modifier.isAbstract(type.getModifiers());
        }

        @Override
        public String toString() {
            return "is assignable to " + parent.getSimpleName();
        }

    }

    /**
     * 查询带有指定注解的类
     */
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

/**
 * 虚拟文件系统抽象,提供应用服务器内的类资源扫描能力
 *
 * @author bingbaihanji
 * @date 2026-06-10
 */
abstract class VFS {

    /** 内置 VFS 实现类列表,按优先级排列：JBoss6 → Default */
    public static final Class<?>[] IMPLEMENTATIONS = {JBoss6VFS.class, DefaultVFS.class};

    /** 用户自定义 VFS 实现类列表,优先级高于内置实现 */
    public static final List<Class<? extends VFS>> USER_IMPLEMENTATIONS = new ArrayList<>();

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(VFS.class);

    /** VFS 单例实例,首次调用 getInstance() 时初始化 */
    private static VFS instance;

    /**
     * 获取单例 VFS 实例按顺序尝试 USER_IMPLEMENTATIONS 和 IMPLEMENTATIONS,返回第一个可用的实现 当前环境找不到有效实现时返回
     * null
     * @return 可用的 VFS 实例,若所有实现均无效则返回 null
     */
    @SuppressWarnings("unchecked")
    public static synchronized VFS getInstance() {
        if (instance != null) {
            return instance;
        }

        List<Class<? extends VFS>> impls = new ArrayList<>();
        impls.addAll(USER_IMPLEMENTATIONS);
        impls.addAll(Arrays.asList((Class<? extends VFS>[]) IMPLEMENTATIONS));

        VFS vfs = null;
        for (Class<? extends VFS> impl : impls) {
            try {
                vfs = impl.getDeclaredConstructor().newInstance();
                if (vfs.isValid()) {
                    break;
                }
                if (log.isDebugEnabled()) {
                    log.debug("VFS 实现 {} 在当前环境无效", impl.getName());
                }
            } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
                     | InvocationTargetException | NoSuchMethodException | SecurityException e) {
                log.error("实例化 VFS 失败: {}", impl, e);
                return null;
            }
        }
        if (!vfs.isValid()) {
            log.warn("未找到可用的 VFS 实现");
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("使用 VFS 适配器 {}", vfs.getClass().getName());
        }
        VFS.instance = vfs;
        return vfs;
    }

    /**
     * 添加自定义 VFS 实现类,该实现将优先于内置实现被尝试
     * @param clazz 自定义 VFS 实现类,为 null 时忽略
     */
    public static void addImplClass(Class<? extends VFS> clazz) {
        if (clazz != null) {
            USER_IMPLEMENTATIONS.add(clazz);
        }
    }

    /**
     * 按类名加载 Class,找不到返回 null
     * @param className 类的全限定名
     * @return 加载的 Class 对象,若类不存在则返回 null
     */
    protected static Class<?> getClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug("类未找到: {}", className);
            }
            return null;
        }
    }

    /**
     * 按方法名和参数类型查找 Method,找不到返回 null
     * @param clazz 目标类,为 null 时直接返回 null
     * @param methodName 方法名
     * @param parameterTypes 方法参数类型列表
     * @return 匹配的 Method 对象,若找不到则返回 null
     */
    protected static Method getMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            if (clazz == null) {
                return null;
            }
            return clazz.getMethod(methodName, parameterTypes);
        } catch (SecurityException | NoSuchMethodException e) {
            log.error("查找方法失败: {}.{}", clazz.getName(), methodName, e);
            return null;
        }
    }

    /**
     * 反射调用方法若目标方法抛出 IOException 则原样抛出,其他异常包装为 RuntimeException
     * @param method 要调用的方法
     * @param object 调用目标对象(静态方法时可为 Class 对象)
     * @param parameters 方法参数
     * @param <T> 返回值类型
     * @return 方法调用结果
     * @throws IOException 目标方法抛出 IOException 时原样抛出
     * @throws RuntimeException 其他反射异常包装为 RuntimeException
     */
    @SuppressWarnings("unchecked")
    protected static <T> T invoke(Method method, Object object, Object... parameters) throws IOException {
        try {
            return (T) method.invoke(object, parameters);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            throw new RuntimeException("反射调用方法失败: " + method.getName(), e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof IOException ie) {
                throw ie;
            }
            throw new RuntimeException("反射调用方法失败: " + method.getName(), e);
        }
    }

    /**
     * 获取指定路径的所有资源 URL
     * @param path 资源路径
     * @return 资源 URL 列表
     * @throws IOException 读取资源失败时抛出
     */
    protected static List<URL> getResources(String path) throws IOException {
        return Collections.list(Thread.currentThread().getContextClassLoader().getResources(path));
    }

    /**
     * @return 当前 VFS 实现是否可用
     */
    public abstract boolean isValid();

    /**
     * 递归列出指定 URL 下的所有子资源
     * @param url 资源 URL
     * @param forPath 相对于类路径的扫描路径
     * @return 子资源路径列表
     * @throws IOException 读取资源失败时抛出
     */
    protected abstract List<String> list(URL url, String forPath) throws IOException;

    /**
     * 递归列出指定路径下的所有子资源
     * @param path 相对于类路径的扫描路径
     * @return 子资源路径列表
     * @throws IOException 读取资源失败时抛出
     */
    public List<String> list(String path) throws IOException {
        List<String> names = new ArrayList<>();
        for (URL url : getResources(path)) {
            names.addAll(list(url, path));
        }
        return names;
    }

}

/**
 * 默认 VFS 实现,适用于大多数应用服务器(如 Tomcat、Jetty)
 *
 * @author bingbaihanji
 * @date 2026-06-10
 */
class DefaultVFS extends VFS {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DefaultVFS.class);

    /** JAR 文件魔数(PK..) */
    private static final byte[] JAR_MAGIC = {'P', 'K', 3, 4};

    /**
     * 安静关闭输入流,忽略关闭过程中的异常
     * @param is 待关闭的输入流,可为 null
     */
    private static void closeQuietly(InputStream is) {
        if (is != null) {
            try {
                is.close();
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.debug("关闭输入流异常", e);
                }
            }
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<String> list(URL url, String path) throws IOException {
        InputStream is = null;
        try {
            List<String> resources = new ArrayList<>();

            // 先尝试找到包含目标资源的 JAR 文件
            URL jarUrl = findJarForResource(url);
            if (jarUrl != null) {
                is = jarUrl.openStream();
                if (log.isDebugEnabled()) {
                    log.debug("列出 JAR: {}", url);
                }
                resources = listResources(new JarInputStream(is), path);
            } else {
                List<String> children = listChildren(url, path);
                String prefix = url.toExternalForm();
                if (!prefix.endsWith("/")) {
                    prefix = prefix + "/";
                }
                for (String child : children) {
                    String resourcePath = path + "/" + child;
                    resources.add(resourcePath);
                    URL childUrl = new URL(prefix + child);
                    resources.addAll(list(childUrl, resourcePath));
                }
            }
            return resources;
        } finally {
            closeQuietly(is);
        }
    }

    /**
     * 列出一个 URL 下的直接子资源(文件或目录条目),根据 URL 类型分发到不同的处理逻辑
     * @param url 资源 URL
     * @param path 相对于类路径的扫描路径
     * @return 直接子资源名称列表
     * @throws IOException 读取资源失败时抛出
     */
    private List<String> listChildren(URL url, String path) throws IOException {
        List<String> children = new ArrayList<>();
        try {
            if (isJar(url)) {
                children.addAll(listJarEntries(url));
            } else {
                children.addAll(listDirectoryEntries(url, path, children));
            }
        } catch (FileNotFoundException e) {
            if ("file".equals(url.getProtocol())) {
                children.addAll(listFileSystemDirectory(url));
            } else {
                throw e;
            }
        }
        return children;
    }

    /**
     * 列出 JAR 文件中的所有条目名
     * @param url JAR 文件 URL
     * @return JAR 条目名称列表
     * @throws IOException 读取 JAR 失败时抛出
     */
    private List<String> listJarEntries(URL url) throws IOException {
        List<String> entries = new ArrayList<>();
        try (InputStream is = url.openStream(); JarInputStream jarInput = new JarInputStream(is)) {
            if (log.isDebugEnabled()) {
                log.debug("列出 JAR: {}", url);
            }
            for (JarEntry entry; (entry = jarInput.getNextJarEntry()) != null; ) {
                if (log.isDebugEnabled()) {
                    log.debug("JAR 条目: {}", entry.getName());
                }
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    /**
     * 通过读取资源方式列出目录条目,验证每个条目确实存在子资源
     * @param url 目录 URL
     * @param path 当前扫描路径
     * @param fallback 当无法通过读取方式列出时返回的兜底列表
     * @return 目录条目名称列表,若验证失败则返回 fallback
     * @throws IOException 读取目录失败时抛出
     */
    private List<String> listDirectoryEntries(URL url, String path, List<String> fallback) throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream is = url.openStream(); BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            for (String line; (line = reader.readLine()) != null; ) {
                if (log.isDebugEnabled()) {
                    log.debug("读取条目: {}", line);
                }
                lines.add(line);
                if (getResources(path + "/" + line).isEmpty()) {
                    lines.clear();
                    break;
                }
            }
        }
        if (!lines.isEmpty()) {
            if (log.isDebugEnabled()) {
                log.debug("列出目录: {}", url);
            }
            return lines;
        }
        return fallback;
    }

    /**
     * 列出本地文件系统目录下的文件和子目录名称
     * @param url file 协议的 URL
     * @return 目录内容名称列表,若路径不是目录或读取失败则返回空列表
     */
    private List<String> listFileSystemDirectory(URL url) {
        File file = new File(url.getFile());
        if (log.isDebugEnabled()) {
            log.debug("列出目录: {}", file.getAbsolutePath());
        }
        if (file.isDirectory()) {
            String[] list = file.list();
            if (list != null) {
                return Arrays.asList(list);
            }
        }
        return Collections.emptyList();
    }

    /**
     * 列出 JAR 中指定路径下的所有条目
     * @param jar JAR 文件输入流
     * @param path 要匹配的路径前缀
     * @return 匹配的条目名称列表(去掉首字符 /)
     * @throws IOException 读取 JAR 条目失败时抛出
     */
    protected List<String> listResources(JarInputStream jar, String path) throws IOException {
        // 确保路径以 / 开头和结尾,便于前缀匹配
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }

        List<String> resources = new ArrayList<>();
        for (JarEntry entry; (entry = jar.getNextJarEntry()) != null; ) {
            if (!entry.isDirectory()) {
                String name = entry.getName();
                if (!name.startsWith("/")) {
                    name = "/" + name;
                }
                if (name.startsWith(path)) {
                    if (log.isDebugEnabled()) {
                        log.debug("找到资源: {}", name);
                    }
                    resources.add(name.substring(1));
                }
            }
        }
        return resources;
    }

    /**
     * 尝试从 URL 中提取包含目标资源的 JAR 文件 URL逐层解开嵌套 URL 直到找到 .jar 后缀, 再通过魔数校验确认是否为有效的 JAR 文件
     * @param url 原始资源 URL(可能是嵌套的 jar:file:... URL)
     * @return JAR 文件 URL,若无法提取则返回 null
     * @throws MalformedURLException URL 格式异常时抛出
     */
    protected URL findJarForResource(URL url) throws MalformedURLException {
        if (log.isDebugEnabled()) {
            log.debug("查找 JAR URL: {}", url);
        }
        // 逐层解开嵌套 URL,直到不能再解
        while (true) {
            try {
                url = new URL(url.getFile());
                if (log.isDebugEnabled()) {
                    log.debug("内层 URL: {}", url);
                }
            } catch (MalformedURLException e) {
                break;
            }
        }

        StringBuilder jarUrl = new StringBuilder(url.toExternalForm());
        int index = jarUrl.lastIndexOf(".jar");
        if (index < 0) {
            if (log.isDebugEnabled()) {
                log.debug("非 JAR: {}", jarUrl);
            }
            return null;
        }
        jarUrl.setLength(index + 4);
        if (log.isDebugEnabled()) {
            log.debug("提取的 JAR URL: {}", jarUrl);
        }

        try {
            URL testUrl = new URL(jarUrl.toString());
            if (isJar(testUrl)) {
                return testUrl;
            }
            // 尝试作为本地文件路径
            if (log.isDebugEnabled()) {
                log.debug("非 JAR: {}", jarUrl);
            }
            jarUrl.replace(0, jarUrl.length(), testUrl.getFile());
            File file = new File(jarUrl.toString());
            if (!file.exists()) {
                file = new File(URLEncoder.encode(jarUrl.toString(), StandardCharsets.UTF_8));
            }
            if (file.exists()) {
                if (log.isDebugEnabled()) {
                    log.debug("尝试实际文件: {}", file.getAbsolutePath());
                }
                testUrl = file.toURI().toURL();
                if (isJar(testUrl)) {
                    return testUrl;
                }
            }
        } catch (MalformedURLException e) {
            log.warn("无效的 JAR URL: {}", jarUrl);
        }
        if (log.isDebugEnabled()) {
            log.debug("非 JAR: {}", jarUrl);
        }
        return null;
    }

    /**
     * 通过魔数判断指定 URL 是否为 JAR 文件
     * @param url 待判断的资源 URL
     * @return true 表示该 URL 指向一个 JAR 文件
     */
    protected boolean isJar(URL url) {
        return isJar(url, new byte[JAR_MAGIC.length]);
    }

    /**
     * 通过魔数判断指定 URL 是否为 JAR 文件,使用外部传入的缓冲区以避免重复分配
     * @param url 待判断的资源 URL
     * @param buffer 读取魔数用的缓冲区
     * @return true 表示该 URL 指向一个 JAR 文件
     */
    protected boolean isJar(URL url, byte[] buffer) {
        InputStream is = null;
        try {
            is = url.openStream();
            is.read(buffer, 0, JAR_MAGIC.length);
            if (Arrays.equals(buffer, JAR_MAGIC)) {
                if (log.isDebugEnabled()) {
                    log.debug("发现 JAR: {}", url);
                }
                return true;
            }
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("判断 JAR 格式异常: {}", url, e);
            }
        } finally {
            closeQuietly(is);
        }
        return false;
    }

}

/**
 * JBoss 6 VFS 实现,用于适配 JBoss/WildFly 应用服务器的虚拟文件系统
 *
 * @author bingbaihanji
 * @date 2026-06-10
 */
class JBoss6VFS extends VFS {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JBoss6VFS.class);

    /** JBoss VFS API 在当前环境是否可用,null 表示尚未检测,Boolean.TRUE 表示可用 */
    private static Boolean valid;

    /** 类加载时触发 JBoss VFS API 可用性检测 */
    static {
        initialize();
    }

    /**
     * 初始化 JBoss 6 VFS API 所需的类和方法引用若任意一项检测失败则将 valid 置为 false
     */
    protected static synchronized void initialize() {
        if (valid == null) {
            valid = true;
            JbossVFS.vfsClass = checkNotNull(getClass("org.jboss.vfs.VFS"));
            JbossVirtualFile.virtualFileClass = checkNotNull(getClass("org.jboss.vfs.VirtualFile"));
            JbossVFS.getChild = checkNotNull(getMethod(JbossVFS.vfsClass, "getChild", URL.class));
            JbossVirtualFile.getChildrenRecursively = checkNotNull(
                    getMethod(JbossVirtualFile.virtualFileClass, "getChildrenRecursively"));
            JbossVirtualFile.getPathNameRelativeTo = checkNotNull(getMethod(JbossVirtualFile.virtualFileClass,
                    "getPathNameRelativeTo", JbossVirtualFile.virtualFileClass));
            checkReturnType(JbossVFS.getChild, JbossVirtualFile.virtualFileClass);
            checkReturnType(JbossVirtualFile.getChildrenRecursively, List.class);
            checkReturnType(JbossVirtualFile.getPathNameRelativeTo, String.class);
        }
    }

    /**
     * 检查对象是否为 null,若为 null 则标记当前 VFS 实现不可用
     * @param object 待检查的对象
     * @param <T> 对象类型
     * @return 原对象(若为 null 则已标记无效并返回 null)
     */
    protected static <T> T checkNotNull(T object) {
        if (object == null) {
            setInvalid();
        }
        return object;
    }

    /**
     * 检查方法的返回类型是否与预期一致,不一致则标记当前 VFS 实现不可用
     * @param method 待检查的方法
     * @param expected 期望的返回类型
     */
    protected static void checkReturnType(Method method, Class<?> expected) {
        if (method != null && !expected.isAssignableFrom(method.getReturnType())) {
            log.error("方法 {}.{} 应返回 {} 但返回了 {}", method.getDeclaringClass().getName(), method.getName(),
                    expected.getName(), method.getReturnType().getName());
            setInvalid();
        }
    }

    /**
     * 标记 JBoss 6 VFS API 在当前环境不可用仅在首次检测时生效,避免重复日志
     */
    protected static void setInvalid() {
        if (JBoss6VFS.valid != null && JBoss6VFS.valid) {
            if (log.isDebugEnabled()) {
                log.debug("JBoss 6 VFS API 在当前环境不可用");
            }
            JBoss6VFS.valid = false;
        }
    }

    @Override
    public boolean isValid() {
        return valid;
    }

    @Override
    public List<String> list(URL url, String path) throws IOException {
        JbossVirtualFile directory = JbossVFS.getChild(url);
        if (directory == null) {
            return Collections.emptyList();
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        List<JbossVirtualFile> children = directory.getChildren();
        List<String> names = new ArrayList<>(children.size());
        for (JbossVirtualFile vf : children) {
            names.add(path + vf.getPathNameRelativeTo(directory));
        }
        return names;
    }

    /**
     * JBoss VirtualFile 的简化包装类,封装反射调用细节
     */
    static class JbossVirtualFile {

        /** org.jboss.vfs.VirtualFile 的 Class 对象 */
        static Class<?> virtualFileClass;

        /** VirtualFile.getPathNameRelativeTo(VirtualFile) 方法引用 */
        static Method getPathNameRelativeTo;

        /** VirtualFile.getChildrenRecursively() 方法引用 */
        static Method getChildrenRecursively;

        /** 被包装的原始 VirtualFile 实例 */
        private final Object virtualFile;

        /**
         * @param virtualFile JBoss VirtualFile 原始对象
         */
        JbossVirtualFile(Object virtualFile) {
            this.virtualFile = virtualFile;
        }

        /**
         * 获取当前文件相对于父目录的路径名
         * @param parent 父目录
         * @return 相对路径名,失败时返回 null
         */
        String getPathNameRelativeTo(JbossVirtualFile parent) {
            try {
                return invoke(getPathNameRelativeTo, virtualFile, parent.virtualFile);
            } catch (IOException e) {
                log.error("VirtualFile.getPathNameRelativeTo 意外抛出 IOException", e);
                return null;
            }
        }

        /**
         * 递归获取当前文件的所有子文件
         * @return 子文件列表
         * @throws IOException 反射调用失败时抛出
         */
        List<JbossVirtualFile> getChildren() throws IOException {
            List<?> objects = invoke(getChildrenRecursively, virtualFile);
            List<JbossVirtualFile> children = new ArrayList<>(objects.size());
            for (Object object : objects) {
                children.add(new JbossVirtualFile(object));
            }
            return children;
        }

    }

    /**
     * JBoss VFS 的简化包装类,封装 VFS.getChild(URL) 的反射调用
     */
    static class JbossVFS {

        /** org.jboss.vfs.VFS 的 Class 对象 */
        static Class<?> vfsClass;

        /** VFS.getChild(URL) 方法引用 */
        static Method getChild;

        /**
         * 通过 URL 获取对应的 VirtualFile
         * @param url 资源 URL
         * @return 包装后的 VirtualFile,若 URL 无对应文件则返回 null
         * @throws IOException 反射调用失败时抛出
         */
        static JbossVirtualFile getChild(URL url) throws IOException {
            Object o = invoke(getChild, vfsClass, url);
            return o == null ? null : new JbossVirtualFile(o);
        }

    }

}
