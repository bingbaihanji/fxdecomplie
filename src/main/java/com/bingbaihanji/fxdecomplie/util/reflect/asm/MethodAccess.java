package com.bingbaihanji.fxdecomplie.util.reflect.asm;

import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.objectweb.asm.Opcodes.*;

/**
 * 基于 ASM 字节码生成的方法访问器,提供比反射更快的方法调用能力
 *
 * @author bingbaihanji
 */
@SuppressWarnings("rawtypes")
public abstract class MethodAccess {

    private static final Map<Class, MethodAccess> cache = new ConcurrentHashMap<>();

    private String[] methodNames;

    private Class[][] parameterTypes;

    private Class[] returnTypes;

    /**
     * 获取指定类的方法访问器实例
     * @param type 目标类,不能为基本类型或 void
     * @return 方法访问器实例
     */
    public static MethodAccess get(Class type) {
        MethodAccess ma = cache.get(type);
        if (ma != null) {
            return ma;
        }

        boolean isInterface = type.isInterface();
        if (!isInterface && type.getSuperclass() == null && type != Object.class) {
            throw new IllegalArgumentException("类型不能为接口、基本类型或 void");
        }

        ArrayList<Method> methods = new ArrayList<Method>();
        if (!isInterface) {
            Class nextClass = type;
            while (nextClass != Object.class) {
                addDeclaredMethodsToList(nextClass, methods);
                nextClass = nextClass.getSuperclass();
            }
        } else {
            recursiveAddInterfaceMethodsToList(type, methods);
        }

        int n = methods.size();
        String[] methodNames = new String[n];
        Class[][] parameterTypes = new Class[n][];
        Class[] returnTypes = new Class[n];
        for (int i = 0; i < n; i++) {
            Method method = methods.get(i);
            methodNames[i] = method.getName();
            parameterTypes[i] = method.getParameterTypes();
            returnTypes[i] = method.getReturnType();
        }

        String className = type.getName();
        String accessClassName = className + "MethodAccess";
        if (accessClassName.startsWith("java.")) {
            accessClassName = "reflectasm." + accessClassName;
        }

        Class accessClass;
        AccessClassLoader loader = AccessClassLoader.get(type);
        synchronized (loader) {
            accessClass = loader.loadAccessClass(accessClassName);
            if (accessClass == null) {
                String accessClassNameInternal = accessClassName.replace('.', '/');
                String classNameInternal = className.replace('.', '/');

                ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                MethodVisitor mv;
                cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER, accessClassNameInternal, null,
                        "com/bingbaihanji/common/framework/utils/reflect/asm/MethodAccess", null);
                {
                    mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
                    mv.visitCode();
                    mv.visitVarInsn(ALOAD, 0);
                    mv.visitMethodInsn(INVOKESPECIAL,
                            "com/bingbaihanji/common/framework/utils/reflect/asm/MethodAccess", "<init>", "()V", false);
                    mv.visitInsn(RETURN);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
                {
                    mv = cw.visitMethod(ACC_PUBLIC + ACC_VARARGS, "invoke",
                            "(Ljava/lang/Object;I[Ljava/lang/Object;)Ljava/lang/Object;", null, null);
                    mv.visitCode();

                    if (!methods.isEmpty()) {
                        mv.visitVarInsn(ALOAD, 1);
                        mv.visitTypeInsn(CHECKCAST, classNameInternal);
                        mv.visitVarInsn(ASTORE, 4);

                        mv.visitVarInsn(ILOAD, 2);
                        Label[] labels = new Label[n];
                        for (int i = 0; i < n; i++) {
                            labels[i] = new Label();
                        }
                        Label defaultLabel = new Label();
                        mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

                        StringBuilder buffer = new StringBuilder(128);
                        for (int i = 0; i < n; i++) {
                            mv.visitLabel(labels[i]);
                            if (i == 0) {
                                mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{classNameInternal}, 0, null);
                            } else {
                                mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                            }
                            mv.visitVarInsn(ALOAD, 4);

                            buffer.setLength(0);
                            buffer.append('(');

                            Class[] paramTypes = parameterTypes[i];
                            Class returnType = returnTypes[i];
                            for (int paramIndex = 0; paramIndex < paramTypes.length; paramIndex++) {
                                mv.visitVarInsn(ALOAD, 3);
                                mv.visitIntInsn(BIPUSH, paramIndex);
                                mv.visitInsn(AALOAD);
                                Type paramType = Type.getType(paramTypes[paramIndex]);
                                switch (paramType.getSort()) {
                                    case Type.BOOLEAN:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z",
                                                false);
                                        break;
                                    case Type.BYTE:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                                        break;
                                    case Type.CHAR:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C",
                                                false);
                                        break;
                                    case Type.SHORT:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S",
                                                false);
                                        break;
                                    case Type.INT:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I",
                                                false);
                                        break;
                                    case Type.FLOAT:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F",
                                                false);
                                        break;
                                    case Type.LONG:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                                        break;
                                    case Type.DOUBLE:
                                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D",
                                                false);
                                        break;
                                    case Type.ARRAY:
                                        mv.visitTypeInsn(CHECKCAST, paramType.getDescriptor());
                                        break;
                                    case Type.OBJECT:
                                        mv.visitTypeInsn(CHECKCAST, paramType.getInternalName());
                                        break;
                                }
                                buffer.append(paramType.getDescriptor());
                            }

                            buffer.append(')');
                            buffer.append(Type.getDescriptor(returnType));
                            int invoke;
                            if (isInterface) {
                                invoke = INVOKEINTERFACE;
                            } else if (Modifier.isStatic(methods.get(i).getModifiers())) {
                                invoke = INVOKESTATIC;
                            } else {
                                invoke = INVOKEVIRTUAL;
                            }
                            mv.visitMethodInsn(invoke, classNameInternal, methodNames[i], buffer.toString(), false);

                            switch (Type.getType(returnType).getSort()) {
                                case Type.VOID:
                                    mv.visitInsn(ACONST_NULL);
                                    break;
                                case Type.BOOLEAN:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf",
                                            "(Z)Ljava/lang/Boolean;", false);
                                    break;
                                case Type.BYTE:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;",
                                            false);
                                    break;
                                case Type.CHAR:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf",
                                            "(C)Ljava/lang/Character;", false);
                                    break;
                                case Type.SHORT:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf",
                                            "(S)Ljava/lang/Short;", false);
                                    break;
                                case Type.INT:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf",
                                            "(I)Ljava/lang/Integer;", false);
                                    break;
                                case Type.FLOAT:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf",
                                            "(F)Ljava/lang/Float;", false);
                                    break;
                                case Type.LONG:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;",
                                            false);
                                    break;
                                case Type.DOUBLE:
                                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf",
                                            "(D)Ljava/lang/Double;", false);
                                    break;
                            }

                            mv.visitInsn(ARETURN);
                        }

                        mv.visitLabel(defaultLabel);
                        mv.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
                    }
                    mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
                    mv.visitInsn(DUP);
                    mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
                    mv.visitInsn(DUP);
                    mv.visitLdcInsn("Method not found: ");
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V",
                            false);
                    mv.visitVarInsn(ILOAD, 2);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                            "(I)Ljava/lang/StringBuilder;", false);
                    mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;",
                            false);
                    mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>",
                            "(Ljava/lang/String;)V", false);
                    mv.visitInsn(ATHROW);
                    mv.visitMaxs(0, 0);
                    mv.visitEnd();
                }
                cw.visitEnd();
                byte[] data = cw.toByteArray();
                accessClass = loader.defineAccessClass(accessClassName, data);
            }
        }
        try {
            MethodAccess access = (MethodAccess) accessClass.getDeclaredConstructor().newInstance();
            access.methodNames = methodNames;
            access.parameterTypes = parameterTypes;
            access.returnTypes = returnTypes;
            cache.put(type, access);
            return access;
        } catch (Throwable t) {
            throw new RuntimeException("构建方法访问类失败: " + accessClassName, t);
        }
    }

    /**
     * 将类中声明的非私有方法添加到方法列表中
     */
    private static void addDeclaredMethodsToList(Class type, ArrayList<Method> methods) {
        Method[] declaredMethods = type.getDeclaredMethods();
        for (Method method : declaredMethods) {
            int modifiers = method.getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                continue;
            }
            methods.add(method);
        }
    }

    /**
     * 递归收集接口及其父接口的所有非私有方法
     */
    private static void recursiveAddInterfaceMethodsToList(Class interfaceType, ArrayList<Method> methods) {
        addDeclaredMethodsToList(interfaceType, methods);
        for (Class nextInterface : interfaceType.getInterfaces()) {
            recursiveAddInterfaceMethodsToList(nextInterface, methods);
        }
    }

    /** 按方法索引调用方法 */
    abstract public Object invoke(Object object, int methodIndex, Object... args);

    /**
     * 按方法名和参数类型调用方法
     */
    public Object invoke(Object object, String methodName, Class[] paramTypes, Object... args) {
        return invoke(object, getIndex(methodName, paramTypes), args);
    }

    /**
     * 按方法名和参数个数调用方法(取第一个匹配)
     */
    public Object invoke(Object object, String methodName, Object... args) {
        return invoke(object, getIndex(methodName, args == null ? 0 : args.length), args);
    }

    /**
     * 根据方法名获取方法索引 优先查找无参方法,再查找有参方法(未传参类型时的降级策略)
     */
    public int getIndex(String methodName) {
        for (int i = 0, n = methodNames.length; i < n; i++) {
            if (methodNames[i].equals(methodName) && parameterTypes[i].length == 0) {
                return i;
            }
        }
        for (int i = 0, n = methodNames.length; i < n; i++) {
            if (methodNames[i].equals(methodName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到非私有方法: " + methodName);
    }

    /**
     * 根据方法名和参数类型精确获取方法索引
     */
    public int getIndex(String methodName, Class... paramTypes) {
        for (int i = 0, n = methodNames.length; i < n; i++) {
            if (methodNames[i].equals(methodName) && Arrays.equals(paramTypes, parameterTypes[i])) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到非私有方法: " + methodName + " " + Arrays.toString(paramTypes));
    }

    /**
     * 根据方法名和参数个数获取方法索引(取第一个匹配)
     */
    public int getIndex(String methodName, int paramsCount) {
        for (int i = 0, n = methodNames.length; i < n; i++) {
            if (methodNames[i].equals(methodName) && parameterTypes[i].length == paramsCount) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到非私有方法: " + methodName + ",参数个数: " + paramsCount);
    }

    /**
     * @return 所有可访问方法名数组
     */
    public String[] getMethodNames() {
        return methodNames;
    }

    /**
     * @return 所有可访问方法参数类型数组
     */
    public Class[][] getParameterTypes() {
        return parameterTypes;
    }

    /**
     * @return 所有可访问方法返回类型数组
     */
    public Class[] getReturnTypes() {
        return returnTypes;
    }

}