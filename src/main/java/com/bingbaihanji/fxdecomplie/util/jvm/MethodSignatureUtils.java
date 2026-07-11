package com.bingbaihanji.fxdecomplie.util.jvm;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Java 反射 {@link Method} 与 JVM 方法描述符之间的签名工具
 */
public final class MethodSignatureUtils {

    private MethodSignatureUtils() {
        throw new AssertionError("utility class");
    }

    /**
     * 获取方法所属类的全限定名
     */
    public static String getDeclaringClassName(Method method) {
        Objects.requireNonNull(method, "method");
        return method.getDeclaringClass().getName();
    }

    /**
     * 获取单纯的方法名
     */
    public static String getMethodName(Method method) {
        Objects.requireNonNull(method, "method");
        return method.getName();
    }

    /**
     * 获取方法的完整标识：类全限定名#方法名(参数类型全限定名, ...)
     * 例如：com.example.MyClass#doSomething(java.lang.String, int)
     */
    public static String buildFullMethodSignature(Method method) {
        Objects.requireNonNull(method, "method");
        String className = getDeclaringClassName(method);
        String methodName = getMethodName(method);
        String params = buildParametersString(method);
        return className + "#" + methodName + params;
    }

    /**
     * 获取 JVM 字节码方法描述符
     * 例如：(Ljava/lang/String;I)V
     */
    public static String getJvmMethodDescriptor(Method method) {
        Objects.requireNonNull(method, "method");
        return Type.getMethodDescriptor(method);
    }

    /**
     * 将方法参数列表拼接为 (参数1, 参数2, ...) 形式的字符串
     */
    private static String buildParametersString(Method method) {
        StringJoiner joiner = new StringJoiner(", ", "(", ")");
        for (Class<?> paramType : method.getParameterTypes()) {
            joiner.add(paramType.getName());
        }
        return joiner.toString();
    }

}
