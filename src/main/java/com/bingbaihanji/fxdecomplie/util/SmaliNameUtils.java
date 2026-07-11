package com.bingbaihanji.fxdecomplie.util;

/**
 * Smali 格式与 Java 格式类型/类名互转工具
 * <p>
 * 纯函数风格，不做状态缓存
 */
public final class SmaliNameUtils {

    private SmaliNameUtils() {
        throw new AssertionError("utility class");
    }

    /** "Ljava/lang/String;" → "java.lang.String" */
    public static String smaliToJava(String obj) {
        if (obj == null || obj.isEmpty()) {
            return obj;
        }
        if (obj.charAt(0) == 'L') {
            int last = obj.length() - 1;
            if (obj.charAt(last) == ';') {
                return obj.substring(1, last).replace('/', '.');
            }
        }
        return obj;
    }

    /** "Ljava/lang/String;" → "java/lang/String" (去 L; 前后缀) */
    public static String smaliToInternal(String obj) {
        if (obj == null || obj.isEmpty()) {
            return obj;
        }
        if (obj.charAt(0) == 'L') {
            return obj.substring(1, obj.length() - 1);
        }
        return obj;
    }

    /** "java.lang.String" → "Ljava/lang/String;" */
    public static String javaToSmali(String obj) {
        if (obj == null || obj.isEmpty()) {
            return obj;
        }
        return 'L' + obj.replace('.', '/') + ';';
    }

    /** "I" → "int", "[[B" → "byte[][]", "Ljava/lang/String;" → "java.lang.String" */
    public static String descriptorToJava(String desc) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }
        return switch (desc.charAt(0)) {
            case 'V' -> "void";
            case 'Z' -> "boolean";
            case 'C' -> "char";
            case 'B' -> "byte";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'F' -> "float";
            case 'J' -> "long";
            case 'D' -> "double";
            case 'L' -> smaliToJava(desc).replace('$', '.');
            case '[' -> descriptorToJava(desc.substring(1)) + "[]";
            default -> desc;
        };
    }

    /** "int" → "I", "java.lang.String" → "Ljava/lang/String;", "byte[][]" → "[[B" */
    public static String javaToDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            return desc;
        }
        if (desc.endsWith("[]")) {
            return "[" + javaToDescriptor(desc.substring(0, desc.length() - 2));
        }
        return switch (desc) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "char" -> "C";
            case "byte" -> "B";
            case "short" -> "S";
            case "int" -> "I";
            case "float" -> "F";
            case "long" -> "J";
            case "double" -> "D";
            default -> javaToSmali(desc);
        };
    }

    /** 判断描述符是否代表基本类型 */
    public static boolean isPrimitiveDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) {
            return false;
        }
        return switch (desc.charAt(0)) {
            case 'V', 'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> true;
            default -> false;
        };
    }
}
