package com.bingbaihanji.fxdecomplie.util.reflect.asm;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.objectweb.asm.Opcodes.*;

/**
 * 基于 ASM 字节码生成的字段访问器,提供比反射更快的字段读写能力
 *
 * @author bingbaihanji
 */
@SuppressWarnings("rawtypes")
public abstract class FieldAccess {

    private static final Map<Class, FieldAccess> cache = new ConcurrentHashMap<>();

    private String[] fieldNames;

    private Class[] fieldTypes;

    private Field[] fields;

    /**
     * 获取指定类的字段访问器实例
     * @param type 目标类,不能为 Object、接口、基本类型或 void
     * @return 字段访问器实例
     */
    public static FieldAccess get(Class type) {
        FieldAccess fa = cache.get(type);
        if (fa != null) {
            return fa;
        }

        if (type.getSuperclass() == null) {
            throw new IllegalArgumentException("类型不能为 Object、接口、基本类型或 void");
        }

        ArrayList<Field> fields = new ArrayList<Field>();
        Class nextClass = type;
        while (nextClass != Object.class) {
            Field[] declaredFields = nextClass.getDeclaredFields();
            for (Field field : declaredFields) {
                int modifiers = field.getModifiers();
                if (Modifier.isStatic(modifiers)) {
                    continue;
                }
                if (Modifier.isPrivate(modifiers)) {
                    continue;
                }
                fields.add(field);
            }
            nextClass = nextClass.getSuperclass();
        }

        String[] fieldNames = new String[fields.size()];
        Class[] fieldTypes = new Class[fields.size()];
        for (int i = 0, n = fieldNames.length; i < n; i++) {
            fieldNames[i] = fields.get(i).getName();
            fieldTypes[i] = fields.get(i).getType();
        }

        String className = type.getName();
        String accessClassName = className + "FieldAccess";
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

                ClassWriter cw = new ClassWriter(0);
                cw.visit(V1_6, ACC_PUBLIC + ACC_SUPER + ACC_SYNTHETIC, accessClassNameInternal, null,
                        "com/bingbaihanji/common/framework/utils/reflect/asm/FieldAccess", null);
                insertConstructor(cw);
                insertGetObject(cw, classNameInternal, fields);
                insertSetObject(cw, classNameInternal, fields);
                insertGetPrimitive(cw, classNameInternal, fields, Type.BOOLEAN_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.BOOLEAN_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.BYTE_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.BYTE_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.SHORT_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.SHORT_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.INT_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.INT_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.LONG_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.LONG_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.DOUBLE_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.DOUBLE_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.FLOAT_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.FLOAT_TYPE);
                insertGetPrimitive(cw, classNameInternal, fields, Type.CHAR_TYPE);
                insertSetPrimitive(cw, classNameInternal, fields, Type.CHAR_TYPE);
                insertGetString(cw, classNameInternal, fields);
                cw.visitEnd();
                accessClass = loader.defineAccessClass(accessClassName, cw.toByteArray());
            }
        }
        try {
            FieldAccess access = (FieldAccess) accessClass.getDeclaredConstructor().newInstance();
            access.fieldNames = fieldNames;
            access.fieldTypes = fieldTypes;
            access.fields = fields.toArray(new Field[0]);
            cache.put(type, access);
            return access;
        } catch (Throwable t) {
            throw new RuntimeException("构建字段访问类失败: " + accessClassName, t);
        }
    }

    /**
     * 生成默认构造器字节码
     */
    private static void insertConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, "com/bingbaihanji/common/framework/utils/reflect/asm/FieldAccess", "<init>",
                "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
    }

    /**
     * 生成 set(Object, int, Object) 方法字节码
     */
    private static void insertSetObject(ClassWriter cw, String classNameInternal, ArrayList<Field> fields) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "set", "(Ljava/lang/Object;ILjava/lang/Object;)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            for (int i = 0, n = labels.length; i < n; i++) {
                labels[i] = new Label();
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);
                Type fieldType = Type.getType(field.getType());

                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, classNameInternal);
                mv.visitVarInsn(ALOAD, 3);

                switch (fieldType.getSort()) {
                    case Type.BOOLEAN:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Boolean");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Boolean", "booleanValue", "()Z", false);
                        break;
                    case Type.BYTE:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Byte");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Byte", "byteValue", "()B", false);
                        break;
                    case Type.CHAR:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Character");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Character", "charValue", "()C", false);
                        break;
                    case Type.SHORT:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Short");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Short", "shortValue", "()S", false);
                        break;
                    case Type.INT:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Integer");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Integer", "intValue", "()I", false);
                        break;
                    case Type.FLOAT:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Float");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
                        break;
                    case Type.LONG:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Long");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Long", "longValue", "()J", false);
                        break;
                    case Type.DOUBLE:
                        mv.visitTypeInsn(CHECKCAST, "java/lang/Double");
                        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
                        break;
                    case Type.ARRAY:
                        mv.visitTypeInsn(CHECKCAST, fieldType.getDescriptor());
                        break;
                    case Type.OBJECT:
                        mv.visitTypeInsn(CHECKCAST, fieldType.getInternalName());
                        break;
                }

                mv.visitFieldInsn(PUTFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(),
                        fieldType.getDescriptor());
                mv.visitInsn(RETURN);
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 4);
        mv.visitEnd();
    }

    /**
     * 生成 get(Object, int) 方法字节码
     */
    private static void insertGetObject(ClassWriter cw, String classNameInternal, ArrayList<Field> fields) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "get", "(Ljava/lang/Object;I)Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            for (int i = 0, n = labels.length; i < n; i++) {
                labels[i] = new Label();
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);

                mv.visitLabel(labels[i]);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(CHECKCAST, classNameInternal);
                mv.visitFieldInsn(GETFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(),
                        Type.getDescriptor(field.getType()));

                Type fieldType = Type.getType(field.getType());
                switch (fieldType.getSort()) {
                    case Type.BOOLEAN:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Z)Ljava/lang/Boolean;",
                                false);
                        break;
                    case Type.BYTE:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Byte", "valueOf", "(B)Ljava/lang/Byte;", false);
                        break;
                    case Type.CHAR:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Character", "valueOf", "(C)Ljava/lang/Character;",
                                false);
                        break;
                    case Type.SHORT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Short", "valueOf", "(S)Ljava/lang/Short;", false);
                        break;
                    case Type.INT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;",
                                false);
                        break;
                    case Type.FLOAT:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                        break;
                    case Type.LONG:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                        break;
                    case Type.DOUBLE:
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                        break;
                }

                mv.visitInsn(ARETURN);
            }

            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 3);
        mv.visitEnd();
    }

    /**
     * 生成 getString(Object, int) 方法字节码
     */
    private static void insertGetString(ClassWriter cw, String classNameInternal, ArrayList<Field> fields) {
        int maxStack = 6;
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "getString", "(Ljava/lang/Object;I)Ljava/lang/String;", null,
                null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            Label labelForInvalidTypes = new Label();
            boolean hasAnyBadTypeLabel = false;
            for (int i = 0, n = labels.length; i < n; i++) {
                if (fields.get(i).getType().equals(String.class)) {
                    labels[i] = new Label();
                } else {
                    labels[i] = labelForInvalidTypes;
                    hasAnyBadTypeLabel = true;
                }
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                if (!labels[i].equals(labelForInvalidTypes)) {
                    Field field = fields.get(i);
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                    mv.visitFieldInsn(GETFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(),
                            "Ljava/lang/String;");
                    mv.visitInsn(ARETURN);
                }
            }
            // 其余字段：类型不同
            if (hasAnyBadTypeLabel) {
                mv.visitLabel(labelForInvalidTypes);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                insertThrowExceptionForFieldType(mv, "String");
            }
            // 默认：字段未找到
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 3);
        mv.visitEnd();
    }

    /**
     * 生成原始类型 setter 方法字节码(setInt/setBoolean 等)
     */
    private static void insertSetPrimitive(ClassWriter cw, String classNameInternal, ArrayList<Field> fields,
                                           Type primitiveType) {
        int maxStack = 6;
        int maxLocals = 4; // 关于 LLOAD 和 DLOAD 的修正见下方
        final String setterMethodName;
        final String typeNameInternal = primitiveType.getDescriptor();
        final int loadValueInstruction;
        switch (primitiveType.getSort()) {
            case Type.BOOLEAN:
                setterMethodName = "setBoolean";
                loadValueInstruction = ILOAD;
                break;
            case Type.BYTE:
                setterMethodName = "setByte";
                loadValueInstruction = ILOAD;
                break;
            case Type.CHAR:
                setterMethodName = "setChar";
                loadValueInstruction = ILOAD;
                break;
            case Type.SHORT:
                setterMethodName = "setShort";
                loadValueInstruction = ILOAD;
                break;
            case Type.INT:
                setterMethodName = "setInt";
                loadValueInstruction = ILOAD;
                break;
            case Type.FLOAT:
                setterMethodName = "setFloat";
                loadValueInstruction = FLOAD;
                break;
            case Type.LONG:
                setterMethodName = "setLong";
                loadValueInstruction = LLOAD;
                maxLocals++; // （LLOAD 与 DLOAD 实际占用两个槽位）
                break;
            case Type.DOUBLE:
                setterMethodName = "setDouble";
                loadValueInstruction = DLOAD;
                maxLocals++; // （LLOAD 与 DLOAD 实际占用两个槽位）
                break;
            default:
                setterMethodName = "set";
                loadValueInstruction = ALOAD;
                break;
        }
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, setterMethodName,
                "(Ljava/lang/Object;I" + typeNameInternal + ")V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            Label labelForInvalidTypes = new Label();
            boolean hasAnyBadTypeLabel = false;
            for (int i = 0, n = labels.length; i < n; i++) {
                if (Type.getType(fields.get(i).getType()).equals(primitiveType)) {
                    labels[i] = new Label();
                } else {
                    labels[i] = labelForInvalidTypes;
                    hasAnyBadTypeLabel = true;
                }
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                if (!labels[i].equals(labelForInvalidTypes)) {
                    Field field = fields.get(i);
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                    mv.visitVarInsn(loadValueInstruction, 3);
                    mv.visitFieldInsn(PUTFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(),
                            typeNameInternal);
                    mv.visitInsn(RETURN);
                }
            }
            // 其余字段：类型不同
            if (hasAnyBadTypeLabel) {
                mv.visitLabel(labelForInvalidTypes);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                insertThrowExceptionForFieldType(mv, primitiveType.getClassName());
            }
            // 默认：字段未找到
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, maxLocals);
        mv.visitEnd();
    }

    /**
     * 生成原始类型 getter 方法字节码(getInt/getBoolean 等)
     */
    private static void insertGetPrimitive(ClassWriter cw, String classNameInternal, ArrayList<Field> fields,
                                           Type primitiveType) {
        int maxStack = 6;
        final String getterMethodName;
        final String typeNameInternal = primitiveType.getDescriptor();
        final int returnValueInstruction = switch (primitiveType.getSort()) {
            case Type.BOOLEAN -> {
                getterMethodName = "getBoolean";
                yield IRETURN;
            }
            case Type.BYTE -> {
                getterMethodName = "getByte";
                yield IRETURN;
            }
            case Type.CHAR -> {
                getterMethodName = "getChar";
                yield IRETURN;
            }
            case Type.SHORT -> {
                getterMethodName = "getShort";
                yield IRETURN;
            }
            case Type.INT -> {
                getterMethodName = "getInt";
                yield IRETURN;
            }
            case Type.FLOAT -> {
                getterMethodName = "getFloat";
                yield FRETURN;
            }
            case Type.LONG -> {
                getterMethodName = "getLong";
                yield LRETURN;
            }
            case Type.DOUBLE -> {
                getterMethodName = "getDouble";
                yield DRETURN;
            }
            default -> {
                getterMethodName = "get";
                yield ARETURN;
            }
        };
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, getterMethodName, "(Ljava/lang/Object;I)" + typeNameInternal,
                null, null);
        mv.visitCode();
        mv.visitVarInsn(ILOAD, 2);

        if (!fields.isEmpty()) {
            maxStack--;
            Label[] labels = new Label[fields.size()];
            Label labelForInvalidTypes = new Label();
            boolean hasAnyBadTypeLabel = false;
            for (int i = 0, n = labels.length; i < n; i++) {
                if (Type.getType(fields.get(i).getType()).equals(primitiveType)) {
                    labels[i] = new Label();
                } else {
                    labels[i] = labelForInvalidTypes;
                    hasAnyBadTypeLabel = true;
                }
            }
            Label defaultLabel = new Label();
            mv.visitTableSwitchInsn(0, labels.length - 1, defaultLabel, labels);

            for (int i = 0, n = labels.length; i < n; i++) {
                Field field = fields.get(i);
                if (!labels[i].equals(labelForInvalidTypes)) {
                    mv.visitLabel(labels[i]);
                    mv.visitFrame(F_SAME, 0, null, 0, null);
                    mv.visitVarInsn(ALOAD, 1);
                    mv.visitTypeInsn(CHECKCAST, classNameInternal);
                    mv.visitFieldInsn(GETFIELD, field.getDeclaringClass().getName().replace('.', '/'), field.getName(),
                            typeNameInternal);
                    mv.visitInsn(returnValueInstruction);
                }
            }
            // 其余字段：类型不同
            if (hasAnyBadTypeLabel) {
                mv.visitLabel(labelForInvalidTypes);
                mv.visitFrame(F_SAME, 0, null, 0, null);
                insertThrowExceptionForFieldType(mv, primitiveType.getClassName());
            }
            // 默认：字段未找到
            mv.visitLabel(defaultLabel);
            mv.visitFrame(F_SAME, 0, null, 0, null);
        }
        mv = insertThrowExceptionForFieldNotFound(mv);
        mv.visitMaxs(maxStack, 3);
        mv.visitEnd();
    }

    /**
     * 生成"字段未找到"异常抛出字节码
     */
    private static MethodVisitor insertThrowExceptionForFieldNotFound(MethodVisitor mv) {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Field not found: ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V",
                false);
        mv.visitInsn(ATHROW);
        return mv;
    }

    /**
     * 生成"字段类型不匹配"异常抛出字节码
     */
    private static MethodVisitor insertThrowExceptionForFieldType(MethodVisitor mv, String fieldType) {
        mv.visitTypeInsn(NEW, "java/lang/IllegalArgumentException");
        mv.visitInsn(DUP);
        mv.visitTypeInsn(NEW, "java/lang/StringBuilder");
        mv.visitInsn(DUP);
        mv.visitLdcInsn("Field not declared as " + fieldType + ": ");
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "(Ljava/lang/String;)V", false);
        mv.visitVarInsn(ILOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(I)Ljava/lang/StringBuilder;", false);
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
        mv.visitMethodInsn(INVOKESPECIAL, "java/lang/IllegalArgumentException", "<init>", "(Ljava/lang/String;)V",
                false);
        mv.visitInsn(ATHROW);
        return mv;
    }

    /**
     * 根据字段名获取字段索引
     */
    public int getIndex(String fieldName) {
        for (int i = 0, n = fieldNames.length; i < n; i++) {
            if (fieldNames[i].equals(fieldName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到非私有字段: " + fieldName);
    }

    /**
     * 根据字段对象获取字段索引
     */
    public int getIndex(Field field) {
        for (int i = 0, n = fields.length; i < n; i++) {
            if (fields[i].equals(field)) {
                return i;
            }
        }
        throw new IllegalArgumentException("未找到非私有字段: " + field);
    }

    /**
     * 按字段名设置字段值
     */
    public void set(Object instance, String fieldName, Object value) {
        set(instance, getIndex(fieldName), value);
    }

    /**
     * 按字段名获取字段值
     */
    public Object get(Object instance, String fieldName) {
        return get(instance, getIndex(fieldName));
    }

    /**
     * @return 所有可访问字段名数组
     */
    public String[] getFieldNames() {
        return fieldNames;
    }

    /**
     * @return 所有可访问字段类型数组
     */
    public Class[] getFieldTypes() {
        return fieldTypes;
    }

    /**
     * @return 可访问字段数量
     */
    public int getFieldCount() {
        return fieldTypes.length;
    }

    /**
     * @return 所有可访问字段数组
     */
    public Field[] getFields() {
        return fields;
    }

    public void setFields(Field[] fields) {
        this.fields = fields;
    }

    /** 设置 Object 类型字段 */
    abstract public void set(Object instance, int fieldIndex, Object value);

    /** 设置 boolean 类型字段 */
    abstract public void setBoolean(Object instance, int fieldIndex, boolean value);

    /** 设置 byte 类型字段 */
    abstract public void setByte(Object instance, int fieldIndex, byte value);

    /** 设置 short 类型字段 */
    abstract public void setShort(Object instance, int fieldIndex, short value);

    /** 设置 int 类型字段 */
    abstract public void setInt(Object instance, int fieldIndex, int value);

    /** 设置 long 类型字段 */
    abstract public void setLong(Object instance, int fieldIndex, long value);

    /** 设置 double 类型字段 */
    abstract public void setDouble(Object instance, int fieldIndex, double value);

    /** 设置 float 类型字段 */
    abstract public void setFloat(Object instance, int fieldIndex, float value);

    /** 设置 char 类型字段 */
    abstract public void setChar(Object instance, int fieldIndex, char value);

    /** 获取 Object 类型字段 */
    abstract public Object get(Object instance, int fieldIndex);

    /** 获取 String 类型字段 */
    abstract public String getString(Object instance, int fieldIndex);

    /** 获取 char 类型字段 */
    abstract public char getChar(Object instance, int fieldIndex);

    /** 获取 boolean 类型字段 */
    abstract public boolean getBoolean(Object instance, int fieldIndex);

    /** 获取 byte 类型字段 */
    abstract public byte getByte(Object instance, int fieldIndex);

    /** 获取 short 类型字段 */
    abstract public short getShort(Object instance, int fieldIndex);

    /** 获取 int 类型字段 */
    abstract public int getInt(Object instance, int fieldIndex);

    /** 获取 long 类型字段 */
    abstract public long getLong(Object instance, int fieldIndex);

    /** 获取 double 类型字段 */
    abstract public double getDouble(Object instance, int fieldIndex);

    /** 获取 float 类型字段 */
    abstract public float getFloat(Object instance, int fieldIndex);

}