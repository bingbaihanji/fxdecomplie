 
package com.bingbaihanji.classgraph.classpath;

import com.bingbaihanji.classgraph.reflect.ReflectionUtils;
import com.bingbaihanji.classgraph.util.LogNode;
import com.bingbaihanji.classgraph.util.VersionFinder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/** 用于查找唯一有序类路径元素的类 */
class CallStackReader {
    ReflectionUtils reflectionUtils;

    /**
     * 构造函数
     */
    public CallStackReader(final ReflectionUtils reflectionUtils) {
        this.reflectionUtils = reflectionUtils;
    }

    /**
     * 通过 StackWalker API 获取调用栈(JRE 9+)
     *
     * @return 调用栈，如果无法获取则返回 null
     */
    private static Class<?>[] getCallStackViaStackWalker() {
        try {
            //    // 通过反射实现以下内容，以保证 JDK7 兼容性：
            //    List<Class<?>> stackFrameClasses = new ArrayList<>();
            //    StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
            //            .forEach(sf -> stackFrameClasses.add(sf.getDeclaringClass()));

            final Class<?> consumerClass = Class.forName("java.util.function.Consumer");
            final List<Class<?>> stackFrameClasses = new ArrayList<>();
            final Class<?> stackWalkerOptionClass = Class.forName("java.lang.StackWalker$Option");
            final Object retainClassReference = Class.forName("java.lang.Enum")
                    .getMethod("valueOf", Class.class, String.class)
                    .invoke(null, stackWalkerOptionClass, "RETAIN_CLASS_REFERENCE");
            final Class<?> stackWalkerClass = Class.forName("java.lang.StackWalker");
            final Object stackWalkerInstance = stackWalkerClass.getMethod("getInstance", stackWalkerOptionClass)
                    .invoke(null, retainClassReference);
            final Method stackFrameGetDeclaringClassMethod = Class.forName("java.lang.StackWalker$StackFrame")
                    .getMethod("getDeclaringClass");
            stackWalkerClass.getMethod("forEach", consumerClass).invoke(stackWalkerInstance, //
                    // Consumer<StackFrame> 的 InvocationHandler 代理
                    Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class<?>[]{consumerClass},
                            new InvocationHandler() {
                                @Override
                                public Object invoke(final Object proxy, final Method method, final Object[] args)
                                        throws Throwable {
                                    // Consumer<StackFrame> 只有一个方法：void accept(StackFrame)
                                    final Class<?> declaringClass = (Class<?>) stackFrameGetDeclaringClassMethod
                                            .invoke(args[0]);
                                    stackFrameClasses.add(declaringClass);
                                    return null;
                                }
                            }));
            return stackFrameClasses.toArray(new Class<?>[0]);
        } catch (Exception | LinkageError e) {
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 通过 SecurityManager.getClassContext() 原生方法获取调用栈
     *
     * @param log
     *            日志
     * @return 调用栈
     */
    private static Class<?>[] getCallStackViaSecurityManager(final LogNode log) {
        try {
            // 通过反射调用方法，因为 SecurityManager 在 JDK 17 中已被弃用
            final Class<?> securityManagerClass = Class.forName("java.lang.SecurityManager");
            Object securityManager = null;
            for (final Constructor<?> constructor : securityManagerClass.getDeclaredConstructors()) {
                if (constructor.getParameterTypes().length == 0) {
                    securityManager = constructor.newInstance();
                    break;
                }
            }
            if (securityManager != null) {
                final Method getClassContext = securityManager.getClass().getDeclaredMethod("getClassContext");
                getClassContext.setAccessible(true);
                return (Class<?>[]) getClassContext.invoke(securityManager);
            } else {
                return null;
            }
        } catch (final Throwable t) {
            // 如果当前 SecurityManager 不允许 RuntimePermission("createSecurityManager")，
            // 则创建 SecurityManager 可能会失败
            if (log != null) {
                log.log("Exception while trying to obtain call stack via SecurityManager", t);
            }
            return null;
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类上下文
     *
     * @param log
     *            日志
     * @return 调用栈中的类
     */
    Class<?>[] getClassContext(final LogNode log) {
        Class<?>[] callStack = null;

        // 对于 JRE 9+，使用 StackWalker 获取调用栈
        if (VersionFinder.JAVA_MAJOR_VERSION == 9 //
                || VersionFinder.JAVA_MAJOR_VERSION == 10 //
                || (VersionFinder.JAVA_MAJOR_VERSION == 11 //
                && VersionFinder.JAVA_MINOR_VERSION == 0
                && (VersionFinder.JAVA_SUB_VERSION < 4
                || (VersionFinder.JAVA_SUB_VERSION == 4 && VersionFinder.JAVA_IS_EA_VERSION)))
                || (VersionFinder.JAVA_MAJOR_VERSION == 12 && VersionFinder.JAVA_MINOR_VERSION == 0
                && (VersionFinder.JAVA_SUB_VERSION < 2
                || (VersionFinder.JAVA_SUB_VERSION == 2 && VersionFinder.JAVA_IS_EA_VERSION)))) {
            // 不要触发导致 JVM 崩溃的 StackWalker 错误，该错误在 JDK 13 中修复，
            // 并向后移植到 12.0.2 和 11.0.4(可能在 JDK 9 引入 StackWalker 时引入)：
            // https://github.com/classgraph/classgraph/issues/341
            // https://bugs.openjdk.java.net/browse/JDK-8210457
            // -- 穿透处理
        } else {
            // 通过 StackWalker 获取调用栈
            // 使用 doPrivileged 调用 -- 参见：
            // http://mail.openjdk.java.net/pipermail/jigsaw-dev/2018-October/013974.html
            try {
                callStack = reflectionUtils.doPrivileged(new Callable<Class<?>[]>() {
                    @Override
                    public Class<?>[] call() throws Exception {
                        return getCallStackViaStackWalker();
                    }
                });
            } catch (final Throwable e) {
                // 穿透处理
            }
        }

        // 对于 JRE 7 和 8，使用 SecurityManager 获取调用栈(不要在 JDK 9+ 上使用此方法，
        // 因为它会导致反射非法访问警告，参见 #663)
        if (VersionFinder.JAVA_MAJOR_VERSION < 9 && (callStack == null || callStack.length == 0)) {
            try {
                callStack = reflectionUtils.doPrivileged(new Callable<Class<?>[]>() {
                    @Override
                    public Class<?>[] call() throws Exception {
                        return getCallStackViaSecurityManager(log);
                    }
                });
            } catch (final Throwable e) {
                // 穿透处理
            }
        }

        // 作为回退方案，使用 getStackTrace() 尝试获取调用栈
        if (callStack == null || callStack.length == 0) {
            StackTraceElement[] stackTrace = null;
            try {
                stackTrace = Thread.currentThread().getStackTrace();
            } catch (final SecurityException e) {
                // 穿透处理
            }
            if (stackTrace == null || stackTrace.length == 0) {
                try {
                    // 尝试通过抛出异常来获取堆栈跟踪
                    throw new Exception();
                } catch (final Exception e) {
                    stackTrace = e.getStackTrace();
                }
            }
            final List<Class<?>> stackClassesList = new ArrayList<>();
            for (final StackTraceElement elt : stackTrace) {
                try {
                    stackClassesList.add(Class.forName(elt.getClassName()));
                } catch (final ClassNotFoundException | LinkageError ignored) {
                    // 忽略
                }
            }
            if (!stackClassesList.isEmpty()) {
                callStack = stackClassesList.toArray(new Class<?>[0]);
            }
        }

        // 最后的努力 -- 仅将此类包含在调用栈中
        if (callStack == null || callStack.length == 0) {
            callStack = new Class<?>[]{CallStackReader.class};
        }

        return callStack;
    }
}
