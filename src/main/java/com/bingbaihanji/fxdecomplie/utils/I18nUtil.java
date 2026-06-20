package com.bingbaihanji.fxdecomplie.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.*;

/**
 * 国际化工具类
 * <p>
 * 支持动态加载语言文件,优先级：
 * 1. JAR 包同目录下的 language 文件夹(外部文件)
 * 2. JAR 包内部的 resources/language 文件夹(内部资源)
 * <p>
 * 注意：为了兼容 Java 模块化系统,不使用 ResourceBundle.Control
 *
 * @author bingbaihanji
 * @date 2025-12-20
 * @updated 2025-12-31 添加外部语言文件支持,兼容模块化系统
 */
public class I18nUtil {
    private static final Logger logger = LoggerFactory.getLogger(I18nUtil.class);
    private static final String BASE_NAME = "language/language";
    // 语言变化监听器列表
    private static final List<Runnable> localeChangeListeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static volatile ResourceBundle resourceBundle;
    private static volatile Locale currentLocale;

    static {
        // 默认使用系统语言,如果不支持则使用简体中文
        currentLocale = Locale.getDefault();
        try {
            resourceBundle = loadResourceBundle(currentLocale);
        } catch (Exception e) {
            // 如果系统语言不支持,默认使用简体中文
            currentLocale = Locale.SIMPLIFIED_CHINESE;
            try {
                resourceBundle = loadResourceBundle(currentLocale);
            } catch (Exception e2) {
                // 如果简体中文也加载失败,尝试使用默认 Locale
                logger.error("无法加载语言资源文件,使用默认配置", e2);
            }
        }
    }

    /** 实例级 bundle，用于独立于全局状态的场景（如测试）。 */
    private final ResourceBundle bundle;

    /**
     * 私有构造函数，创建具有指定语言环境的 I18nUtil 实例。
     * 使用 {@link #createInstance(Locale)} 工厂方法获取实例。
     */
    private I18nUtil(Locale locale) {
        try {
            this.bundle = loadResourceBundle(locale);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load bundle for locale: " + locale, e);
        }
    }

    /**
     * 获取国际化文本
     *
     * @param key 配置文件中的key
     * @return 对应的值
     */
    public static String getString(String key) {
        try {
            return resourceBundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 获取国际化文本(带参数格式化)
     * <p>
     * 支持 MessageFormat 模式,例如：
     * - "Hello, {0}!" + ["World"] -> "Hello, World!"
     * - "复用组({0}个点)" + [3] -> "复用组(3个点)"
     *
     * @param key    配置文件中的key
     * @param params 格式化参数
     * @return 格式化后的文本
     */
    public static String getString(String key, Object... params) {
        try {
            String pattern = resourceBundle.getString(key);
            if (params == null || params.length == 0) {
                return pattern;
            }
            return MessageFormat.format(pattern, params);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 切换语言
     *
     * @param locale 目标语言环境
     */
    public static void switchLocale(Locale locale) {
        currentLocale = locale;
        try {
            resourceBundle = loadResourceBundle(locale);
            // 通知所有监听器
            notifyLocaleChange();
        } catch (Exception e) {
            logger.error("切换语言失败: {}", locale, e);
        }
    }

    /**
     * 加载资源文件
     * 优先级：1. 外部文件 2. classpath 资源
     *
     * @param locale 语言环境
     * @return ResourceBundle
     * @throws IOException 加载失败时抛出异常
     */
    static ResourceBundle loadResourceBundle(Locale locale) throws IOException {
        for (String resourceName : candidateResourceNames(locale)) {
            // 1. 尝试从外部文件加载
            ResourceBundle bundle = loadFromExternalFile(resourceName);

            // 2. 如果外部文件不存在,从 classpath 加载
            if (bundle == null) {
                bundle = loadFromClasspath(resourceName);
            }

            if (bundle != null) {
                return bundle;
            }
        }
        throw new IOException("Cannot load resource bundle for locale: " + locale);
    }

    private static List<String> candidateResourceNames(Locale locale) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (locale != null && locale != Locale.ROOT && !locale.getLanguage().isEmpty()) {
            names.add(toResourceName(toBundleName(BASE_NAME, locale)));
            if (!locale.getCountry().isEmpty() || !locale.getVariant().isEmpty()) {
                names.add(toResourceName(BASE_NAME + '_' + locale.getLanguage()));
            }
        }
        names.add(toResourceName(BASE_NAME + "_zh_CN"));
        names.add(toResourceName(BASE_NAME + "_en"));
        return new ArrayList<>(names);
    }

    /**
     * 从外部文件加载资源
     */
    private static ResourceBundle loadFromExternalFile(String resourceName) {
        try {
            File jarFile = getJarLocation();
            if (jarFile != null) {
                File jarDir = jarFile.getParentFile();
                File externalFile = new File(jarDir, resourceName);

                if (externalFile.exists() && externalFile.isFile()) {
                    try (InputStream stream = new FileInputStream(externalFile);
                         InputStreamReader reader = new InputStreamReader(stream, "UTF-8")) {
                        return new PropertyResourceBundle(reader);
                    }
                }
            }
        } catch (Exception e) {
            // 外部文件加载失败,继续使用内部资源
        }
        return null;
    }

    /**
     * 从 classpath 加载资源
     */
    private static ResourceBundle loadFromClasspath(String resourceName) {
        try {
            // 在模块化环境中,使用 Class.getResourceAsStream() 更可靠
            // 路径需要以 / 开头表示从 classpath 根目录开始
            String path = "/" + resourceName;
            InputStream stream = I18nUtil.class.getResourceAsStream(path);

            if (stream != null) {
                try (InputStream is = stream;
                     InputStreamReader reader = new InputStreamReader(is, "UTF-8")) {
                    return new PropertyResourceBundle(reader);
                }
            }
        } catch (Exception e) {
            // classpath 资源加载失败
            logger.error("Failed to load resource from classpath: {}", resourceName, e);
        }
        return null;
    }

    /**
     * 获取 JAR 包位置
     */
    private static File getJarLocation() {
        try {
            String path = I18nUtil.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()
                    .getPath();

            // 处理 Windows 路径问题(/C:/path/to/file)
            if (path.startsWith("/") && path.contains(":")) {
                path = path.substring(1);
            }

            File file = new File(path);

            // 如果是目录(IDE 开发环境),返回 null 表示不使用外部文件
            if (file.isDirectory()) {
                return null;
            }

            return file;
        } catch (URISyntaxException e) {
            return null;
        }
    }

    /**
     * 将 baseName 和 locale 转换为 bundle 名称
     */
    private static String toBundleName(String baseName, Locale locale) {
        if (locale == Locale.ROOT || locale.getLanguage().isEmpty()) {
            return baseName;
        }

        StringBuilder sb = new StringBuilder(baseName);
        sb.append('_').append(locale.getLanguage());

        if (!locale.getCountry().isEmpty()) {
            sb.append('_').append(locale.getCountry());
        }

        if (!locale.getVariant().isEmpty()) {
            sb.append('_').append(locale.getVariant());
        }

        return sb.toString();
    }

    /**
     * 将 bundle 名称转换为资源文件名
     */
    private static String toResourceName(String bundleName) {
        return bundleName.replace('.', '/') + ".properties";
    }

    /**
     * 创建测试用 ResourceBundle，返回独立实例。
     * 不影响全局静态状态，适合并发测试。
     *
     * @param locale 目标语言环境
     * @return 独立的 ResourceBundle 实例
     * @throws RuntimeException 如果资源加载失败
     */
    public static ResourceBundle createBundleFor(Locale locale) {
        try {
            return loadResourceBundle(locale);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load bundle for locale: " + locale, e);
        }
    }

    /**
     * 创建具有指定语言环境的独立 I18nUtil 实例，用于测试或需要独立 bundle 的场景。
     * 不影响全局静态状态。
     *
     * @param locale 目标语言环境
     * @return 独立的 I18nUtil 实例
     */
    public static I18nUtil createInstance(Locale locale) {
        return new I18nUtil(locale);
    }

    /**
     * 检查指定语言环境是否有可用的资源文件。
     *
     * @param locale 目标语言环境
     * @return true 如果该语言环境有可用的资源文件
     */
    public static boolean isLocaleAvailable(Locale locale) {
        try {
            loadResourceBundle(locale);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 获取当前语言环境
     *
     * @return 当前Locale
     */
    public static Locale getCurrentLocale() {
        return currentLocale;
    }

    /**
     * 添加语言变化监听器
     *
     * @param listener 监听器回调
     */
    public static void addLocaleChangeListener(Runnable listener) {
        if (listener != null && !localeChangeListeners.contains(listener)) {
            localeChangeListeners.add(listener);
        }
    }

    /**
     * 移除语言变化监听器
     *
     * @param listener 监听器回调
     */
    public static void removeLocaleChangeListener(Runnable listener) {
        localeChangeListeners.remove(listener);
    }

    /**
     * 通知所有监听器语言已变化
     */
    private static void notifyLocaleChange() {
        for (Runnable listener : localeChangeListeners) {
            try {
                listener.run();
            } catch (Exception e) {
                logger.error("Locale change listener error", e);
            }
        }
    }

    /**
     * 获取国际化文本（实例方法）
     *
     * @param key 配置文件中的key
     * @return 对应的值
     */
    public String get(String key) {
        try {
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    /**
     * 获取带参数格式化的国际化文本（实例方法）
     *
     * @param key    配置文件中的key
     * @param params 格式化参数
     * @return 格式化后的文本
     */
    public String get(String key, Object... params) {
        try {
            String pattern = bundle.getString(key);
            if (params == null || params.length == 0) return pattern;
            return java.text.MessageFormat.format(pattern, params);
        } catch (Exception e) {
            return key;
        }
    }
}
