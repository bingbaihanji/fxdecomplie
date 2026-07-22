package com.bingbaihanji.fxdecomplie.util.reflect.asm;


import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.util.CheckClassAdapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 对 {@link org.objectweb.asm.Type} 的封装
 *
 * @author Matt Coley
 */
public class Types {
    public static final Type OBJECT_TYPE = Type.getObjectType("java/lang/Object");
    public static final Type CLASS_TYPE = Type.getObjectType("java/lang/Class");
    public static final Type STRING_TYPE = Type.getObjectType("java/lang/String");
    public static final Type BOX_VOID = Type.getObjectType("java/lang/Void");
    public static final Type BOX_BOOLEAN = Type.getObjectType("java/lang/Boolean");
    public static final Type BOX_CHAR = Type.getObjectType("java/lang/Character");
    public static final Type BOX_BYTE = Type.getObjectType("java/lang/Byte");
    public static final Type BOX_SHORT = Type.getObjectType("java/lang/Short");
    public static final Type BOX_INT = Type.getObjectType("java/lang/Integer");
    public static final Type BOX_FLOAT = Type.getObjectType("java/lang/Float");
    public static final Type BOX_LONG = Type.getObjectType("java/lang/Long");
    public static final Type BOX_DOUBLE = Type.getObjectType("java/lang/Double");
    public static final Type METHOD_HANDLE_TYPE = Type.getObjectType("java/lang/invoke/MethodHandle");
    public static final Type ARRAY_1D_BOOLEAN = Type.getObjectType("[Z");
    public static final Type ARRAY_1D_CHAR = Type.getObjectType("[C");
    public static final Type ARRAY_1D_BYTE = Type.getObjectType("[B");
    public static final Type ARRAY_1D_SHORT = Type.getObjectType("[S");
    public static final Type ARRAY_1D_INT = Type.getObjectType("[I");
    public static final Type ARRAY_1D_FLOAT = Type.getObjectType("[F");
    public static final Type ARRAY_1D_DOUBLE = Type.getObjectType("[D");
    public static final Type ARRAY_1D_LONG = Type.getObjectType("[J");
    public static final Type ARRAY_1D_OBJECT = Type.getObjectType("[Ljava/lang/Object;");
    public static final Type ARRAY_1D_STRING = Type.getObjectType("[Ljava/lang/String;");
    public static final Type[] PRIMITIVES = new Type[]{
            Type.VOID_TYPE,
            Type.BOOLEAN_TYPE,
            Type.BYTE_TYPE,
            Type.CHAR_TYPE,
            Type.SHORT_TYPE,
            Type.INT_TYPE,
            Type.FLOAT_TYPE,
            Type.DOUBLE_TYPE,
            Type.LONG_TYPE
    };
    public static final Collection<String> PRIMITIVE_BOXES = Arrays.asList(
            "Ljava/lang/Boolean;",
            "Ljava/lang/Byte;",
            "Ljava/lang/Character;",
            "Ljava/lang/Short;",
            "Ljava/lang/Integer;",
            "Ljava/lang/Float;",
            "Ljava/lang/Double;",
            "Ljava/lang/Long;"
    );

    /**
     * @param type
     * 		待检查的类型
     *
     * @return 若为基本类型则返回 {@code true}
     */
    public static boolean isPrimitive(Type type) {
        return type != null && type.getSort() <= Type.DOUBLE;
    }

    /**
     * @param desc
     * 		某个内部类型描述符
     *
     * @return 若匹配保留的基本类型则返回 {@code true}
     */
    public static boolean isPrimitive(String desc) {
        if (desc == null || desc.length() != 1) {
            return false;
        }
        char c = desc.charAt(0);
        return switch (c) {
            case 'V', 'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> true;
            default -> false;
        };
    }

    /**
     * @param name
     * 		必须是基本类型的类名参见 {@link #isPrimitiveClassName(String)}
     *
     * @return 基本类型的内部名称
     *
     * @throws IllegalArgumentException
     * 		当该描述符不是基本类型时抛出
     */

    public static String classToPrimitive(String name) {
        for (Type prim : PRIMITIVES) {
            String className = prim.getClassName();
            if (className.equals(name)) {
                return prim.getInternalName();
            }
        }
        throw new IllegalArgumentException("Descriptor was not a primitive class name!");
    }

    /**
     * @param name
     * 		某个类名
     *
     * @return 若匹配某个基本类型的类名则返回 {@code true}
     */
    public static boolean isPrimitiveClassName(String name) {
        if (name == null) {
            return false;
        }
        for (Type prim : PRIMITIVES) {
            if (prim.getClassName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param desc
     * 		类描述符
     *
     * @return 若为 {@link Number} 的某个子类则返回 {@code true}
     */
    public static boolean isBoxedPrimitive(String desc) {
        return PRIMITIVE_BOXES.contains(desc);
    }

    /**
     * @param type
     * 		待检查的类型
     *
     * @return 若为 void 类型则返回 {@code true}
     */
    public static boolean isVoid(Type type) {
        return type != null && type.getSort() == Type.VOID;
    }

    /**
     * @param type
     * 		基础类型
     * @param dimensions
     * 		数组维度
     *
     * @return 指定维度的数组类型
     */

    public static Type array(Type type, int dimensions) {
        return Type.getType("[".repeat(dimensions) + type.getDescriptor());
    }

    /**
     * @param arrayType
     * 		某个数组类型
     *
     * @return 从该数组类型去掉一个维度后的类型
     */

    public static Type undimension(Type arrayType) {
        if (arrayType.getSort() != Type.ARRAY) {
            throw new IllegalStateException("Not an array: " + arrayType);
        }
        return Type.getType(arrayType.getDescriptor().substring(1));
    }

    /**
     * @param type
     * 		某个内部类型名称
     *
     * @return 对于数组的父类型返回 {@code true}
     */
    public static boolean isArraySuperType(String type) {
        return "java/lang/Object".equals(type)
                || "java/lang/Cloneable".equals(type)
                || "java/io/Serializable".equals(type);
    }

    /**
     * @param methodType
     * 		已解析的方法描述符类型
     *
     * @return 参数所占用的变量槽位数
     */
    public static int countParameterSlots(Type methodType) {
        int size = 0;
        Type[] methodArgs = methodType.getArgumentTypes();
        for (Type arg : methodArgs) {
            size += arg.getSize();
        }
        return size;
    }

    /**
     * ASM 在无法解析类型描述符时往往会抛出 {@link IllegalArgumentException}
     * 该方法让我们能够事先检查描述符是否有效
     *
     * @param desc
     * 		待检查的描述符
     *
     * @return 当描述符可解析时返回 {@code true}
     */
    @SuppressWarnings("all")
    public static boolean isValidDesc(String desc) {
        if (desc == null)
            return false;
        if (desc.length() == 0)
            return false;
        char first = desc.charAt(0);
        if (first == '(') {
            try {
                Type methodType = Type.getMethodType(desc);
                methodType.getArgumentTypes();
                methodType.getReturnType();
                return true;
            } catch (Throwable t) {
                return false;
            }
        } else if (first == 'L' || first == '[') {
            try {
                Type type = Type.getType(desc);
                if (type.getSort() == Type.OBJECT && !desc.endsWith(";"))
                    return false;
                else if (type.getSort() == Type.ARRAY && type.getElementType() == null)
                    return false;
                return true;
            } catch (Throwable t) {
                return false;
            }
        }
        return false;
    }

    /**
     * @param type
     * 		待检查的类型
     *
     * @return 若为宽类型(占两个槽位)则返回 {@code true}
     */
    public static boolean isWide(Type type) {
        if (type == null) {
            return false;
        }
        return Type.DOUBLE_TYPE.equals(type) || Type.LONG_TYPE.equals(type);
    }

    /**
     * @param opcode
     * 		某个指令操作码
     *
     * @return 隐含的变量类型；若传入的操作码不隐含类型则返回 {@code null}
     */

    public static Type fromVarOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.IINC, Opcodes.ILOAD, Opcodes.ISTORE -> Type.INT_TYPE;
            case Opcodes.ALOAD, Opcodes.ASTORE -> Types.OBJECT_TYPE;
            case Opcodes.FLOAD, Opcodes.FSTORE -> Type.FLOAT_TYPE;
            case Opcodes.DLOAD, Opcodes.DSTORE -> Type.DOUBLE_TYPE;
            case Opcodes.LLOAD, Opcodes.LSTORE -> Type.LONG_TYPE;
            default -> null;
        };
    }

    /**
     * @param opcode
     * 		某个数组操作码
     *
     * @return 隐含的变量类型；若传入的操作码不隐含类型则返回 {@code null}
     */

    public static Type fromArrayOpcode(int opcode) {
        return switch (opcode) {
            case Opcodes.ARRAYLENGTH, Opcodes.BALOAD, Opcodes.CALOAD, Opcodes.SALOAD, Opcodes.IALOAD,
                 Opcodes.BASTORE, Opcodes.CASTORE, Opcodes.SASTORE, Opcodes.IASTORE -> Type.INT_TYPE;
            case Opcodes.AALOAD, Opcodes.AASTORE -> Types.OBJECT_TYPE;
            case Opcodes.FALOAD, Opcodes.FASTORE -> Type.FLOAT_TYPE;
            case Opcodes.DALOAD, Opcodes.DASTORE -> Type.DOUBLE_TYPE;
            case Opcodes.LALOAD, Opcodes.LASTORE -> Type.LONG_TYPE;
            default -> null;
        };
    }

    /**
     * @param ldc
     * 		常量加载指令
     *
     * @return 所加载常量的类型
     */

    public static Type fromLdc(LdcInsnNode ldc) {
        Object constant = ldc.cst;
        return switch (constant) {
            case Integer ignored -> Type.INT_TYPE;
            case Float ignored -> Type.FLOAT_TYPE;
            case Long ignored -> Type.LONG_TYPE;
            case Double ignored -> Type.DOUBLE_TYPE;
            case String ignored -> STRING_TYPE;
            case Type ignored -> CLASS_TYPE;
            case Handle ignored -> METHOD_HANDLE_TYPE;
            case ConstantDynamic dynamic -> Type.getType(dynamic.getDescriptor());
            default -> OBJECT_TYPE;
        };
    }

    /**
     * @param sort
     * 		类型 sort 值
     *
     * @return 给定 sort 对应的基本类型；若非基本类型则返回 {@link #OBJECT_TYPE}
     */

    public static Type fromSort(int sort) {
        return switch (sort) {
            case Type.VOID -> Type.VOID_TYPE;
            case Type.BOOLEAN -> Type.BOOLEAN_TYPE;
            case Type.CHAR -> Type.CHAR_TYPE;
            case Type.BYTE -> Type.BYTE_TYPE;
            case Type.SHORT -> Type.SHORT_TYPE;
            case Type.INT -> Type.INT_TYPE;
            case Type.FLOAT -> Type.FLOAT_TYPE;
            case Type.LONG -> Type.LONG_TYPE;
            case Type.DOUBLE -> Type.DOUBLE_TYPE;
            default -> OBJECT_TYPE;
        };
    }

    /**
     * @param sort
     * 		某个类型 sort 值
     *
     * @return 归一化后的 sort这是基于运行时预期的语境
     * 任何小于 {@code int} 的类型都被视为 {@code int}
     * 在大多数情况下，数组类型基本可以直接替换为对象类型
     */
    public static int getNormalizedSort(int sort) {
        if (sort == Type.ARRAY) {
            sort = Type.OBJECT;
        } else if (sort > 0 && sort < Type.INT) {
            sort = Type.INT;
        }
        return sort;
    }

    /**
     * @param sort
     *        {@link Type#getSort()}
     *
     * @return sort 的名称
     */

    public static String getSortName(int sort) {
        return switch (sort) {
            case Type.VOID -> "void";
            case Type.BOOLEAN -> "boolean";
            case Type.CHAR -> "char";
            case Type.BYTE -> "byte";
            case Type.SHORT -> "short";
            case Type.INT -> "int";
            case Type.FLOAT -> "float";
            case Type.LONG -> "long";
            case Type.DOUBLE -> "double";
            case Type.ARRAY -> "array";
            case Type.OBJECT -> "object";
            case Type.METHOD -> "method";
            case -1 -> "<undefined>";
            default -> "<unknown>";
        };
    }

    /**
     * @param tag
     *        {@link Handle#getTag()}
     *
     * @return sort 的名称
     */

    public static String getArraySortName(int tag) {
        return switch (tag) {
            case Opcodes.T_BOOLEAN -> "boolean";
            case Opcodes.T_CHAR -> "char";
            case Opcodes.T_FLOAT -> "float";
            case Opcodes.T_DOUBLE -> "double";
            case Opcodes.T_BYTE -> "byte";
            case Opcodes.T_SHORT -> "short";
            case Opcodes.T_INT -> "int";
            case Opcodes.T_LONG -> "long";
            case -1 -> "<undefined>";
            default -> "<unknown>";
        };
    }

    /**
     * @param operand
     *        用于 {@link Opcodes#NEWARRAY} 的 {@link IntInsnNode#operand}
     *
     * @return 数组元素的类型；若操作数不匹配任何基本类型则返回 {@link #OBJECT_TYPE}
     */

    public static Type newArrayElementType(int operand) {
        return switch (operand) {
            case Opcodes.T_BOOLEAN -> Type.BOOLEAN_TYPE;
            case Opcodes.T_CHAR -> Type.CHAR_TYPE;
            case Opcodes.T_FLOAT -> Type.FLOAT_TYPE;
            case Opcodes.T_DOUBLE -> Type.DOUBLE_TYPE;
            case Opcodes.T_BYTE -> Type.BYTE_TYPE;
            case Opcodes.T_SHORT -> Type.SHORT_TYPE;
            case Opcodes.T_INT -> Type.INT_TYPE;
            case Opcodes.T_LONG -> Type.LONG_TYPE;
            default -> OBJECT_TYPE;
        };
    }

    /**
     * @param descriptor
     * 		输入描述符
     *
     * @return 美化打印后的类型
     */

    public static String pretty(String descriptor) {
        try {
            Type type;
            if (descriptor.charAt(0) == '(') {
                type = Type.getMethodType(descriptor);
            } else {
                type = Type.getType(descriptor);
            }
            return pretty(type);

        } catch (Throwable t) {
            // 无效描述符，原样返回
            return descriptor;
        }
    }

    /**
     * @param type
     * 		输入类型
     *
     * @return 美化打印后的类型
     */

    public static String pretty(Type type) {
        int sort = type.getSort();
        String suffix = null;
        String name;
        if (sort == Type.ARRAY) {
            suffix = "[]".repeat(type.getDimensions());
            type = type.getElementType();
            sort = type.getSort();
        } else if (sort == Type.METHOD) {
            List<String> args = Arrays.stream(type.getArgumentTypes())
                    .map(Types::pretty)
                    .toList();
            return "(" + String.join(", ", args) + ") " + pretty(type.getReturnType());
        }
        if (sort <= Type.DOUBLE) {
            name = getSortName(sort);
        } else {
            name = type.getInternalName();
        }
        String pretty = shortenPath(name);
        if (suffix != null) {
            pretty += suffix;
        }
        return pretty;
    }


    public static String shortenPath(String name) {
        int separatorIndex = name.lastIndexOf('/');
        if (separatorIndex > 0) {
            name = name.substring(separatorIndex + 1);
        }
        return name;
    }

    /**
     * @param signature
     * 		类声明签名
     *
     * @return 对于有效签名或 {@code null} 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidClassSignature(String signature) {
        return isValidSignature(signature, SignatureContext.CLASS);
    }

    /**
     * @param signature
     * 		字段或变量签名
     *
     * @return 对于有效签名或 {@code null} 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidFieldSignature(String signature) {
        return isValidSignature(signature, SignatureContext.FIELD);
    }

    /**
     * @param signature
     * 		方法签名
     *
     * @return 对于有效签名或 {@code null} 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidMethodSignature(String signature) {
        return isValidSignature(signature, SignatureContext.METHOD);
    }

    /**
     * @param signature
     * 		签名内容
     * @param context
     * 		签名使用语境
     *
     * @return 对于有效签名或 {@code null} 返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isValidSignature(String signature, SignatureContext context) {
        if (signature == null) {
            return true;
        }
        if (signature.isEmpty()) {
            return false;
        }
        try {
            switch (context) {
                case CLASS -> CheckClassAdapter.checkClassSignature(signature);
                case FIELD -> CheckClassAdapter.checkFieldSignature(signature);
                case METHOD -> CheckClassAdapter.checkMethodSignature(signature);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 签名使用场景的类型
     *
     * @see #isValidSignature(String, SignatureContext)
     */
    public enum SignatureContext {
        /**
         * 类声明
         */
        CLASS,
        /**
         * 字段或变量声明
         */
        FIELD,
        /**
         * 方法声明
         */
        METHOD
    }
}
