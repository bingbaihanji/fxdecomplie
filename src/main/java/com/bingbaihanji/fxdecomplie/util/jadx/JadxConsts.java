package com.bingbaihanji.fxdecomplie.util.jadx;

/**
 * jadx 反编译引擎中的 JVM 标准类常量，可供所有反编译引擎复用
 * <p>
 * 本类是一个纯常量容器，不可实例化
 */
public final class JadxConsts {

    /** {@code java.lang.Object} */
    public static final String CLASS_OBJECT = "java.lang.Object";
    /** {@code java.lang.String} */
    public static final String CLASS_STRING = "java.lang.String";
    /** {@code java.lang.Class} */
    public static final String CLASS_CLASS = "java.lang.Class";
    /** {@code java.lang.Throwable} */
    public static final String CLASS_THROWABLE = "java.lang.Throwable";
    /** {@code java.lang.Error} */
    public static final String CLASS_ERROR = "java.lang.Error";
    /** {@code java.lang.Exception} */
    public static final String CLASS_EXCEPTION = "java.lang.Exception";
    /** {@code java.lang.RuntimeException} */
    public static final String CLASS_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
    /** {@code java.lang.Enum} */
    public static final String CLASS_ENUM = "java.lang.Enum";
    /** {@code java.lang.StringBuilder} */
    public static final String CLASS_STRING_BUILDER = "java.lang.StringBuilder";
    /** {@code @Override} 注解的 JVM 类型描述符 */
    public static final String OVERRIDE_ANNOTATION = "Ljava/lang/Override;";
    /** 默认包名 (用于无包名类) */
    public static final String DEFAULT_PACKAGE_NAME = "defpackage";
    /** 匿名类名称前缀 */
    public static final String ANONYMOUS_CLASS_PREFIX = "AnonymousClass";
    /** {@code toString()} 方法的签名 */
    public static final String MTH_TOSTRING_SIGNATURE = "toString()Ljava/lang/String;";

    private JadxConsts() {
        throw new AssertionError("utility class");
    }
}
