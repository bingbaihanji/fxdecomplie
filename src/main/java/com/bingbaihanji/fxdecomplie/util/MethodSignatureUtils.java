package com.bingbaihanji.fxdecomplie.util;

import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.StringJoiner;

/**
 * 方法签名工具类
 * 提供获取方法全限定名、JVM 描述符等常用操作
 */
public class MethodSignatureUtils {

    private MethodSignatureUtils() {
        // 工具类,禁止实例化
    }

    /**
     * 获取方法所属类的全限定名
     */
    public static String getDeclaringClassName(Method method) {
        return method.getDeclaringClass().getName();
    }

    /**
     * 获取单纯的方法名
     */
    public static String getMethodName(Method method) {
        return method.getName();
    }

    /**
     * 获取方法的完整标识：类全限定名#方法名(参数类型全限定名, ...)
     * 例如：com.example.MyClass#doSomething(java.lang.String, int)
     */
    public static String buildFullMethodSignature(Method method) {
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