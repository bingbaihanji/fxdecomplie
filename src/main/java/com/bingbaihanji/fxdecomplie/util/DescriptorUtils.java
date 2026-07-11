package com.bingbaihanji.fxdecomplie.util;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM 方法/字段描述符解析工具。
 * <p>
 * 提供了从原始描述符字符串中逐步读取参数类型的能力。
 */
public final class DescriptorUtils {

    private DescriptorUtils() {
        throw new AssertionError("utility class");
    }

    /**
     * 获取从 pos 位置开始的类型描述符在字符串中所占的长度。
     * <ul>
     *   <li>基本类型 (V, Z, C, B, S, I, F, J, D) → 1</li>
     *   <li>对象类型 (L...;) → 到下一个 ';' 的长度</li>
     *   <li>数组类型 ([[[...) → 每个 '[' 递归 1 + 下个元素的长度</li>
     * </ul>
     *
     * @param descriptor 方法/字段描述符
     * @param pos        起始位置
     * @return 该类型在 descriptor 中占据的字符数
     */
    public static int getTypeLength(String descriptor, int pos) {
        if (descriptor == null || pos >= descriptor.length()) {
            return 0;
        }
        char ch = descriptor.charAt(pos);
        if (ch == '[') {
            return 1 + getTypeLength(descriptor, pos + 1);
        }
        if (ch == 'L') {
            int semi = descriptor.indexOf(';', pos);
            if (semi == -1) {
                return descriptor.length() - pos;
            }
            return semi - pos + 1;
        }
        return 1; // primitive
    }

    /**
     * 将方法描述符拆解为参数类型列表和返回类型。
     *
     * @param descriptor 方法描述符，例如 "(II)Ljava/lang/String;"
     * @return [参数类型列表, 返回类型] — 元素 0..N-1 为参数，最后一个为返回类型；
     *         若 descriptor 无效则返回空列表
     */
    public static List<String> splitMethodDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            return List.of();
        }
        int closeParen = descriptor.indexOf(')');
        if (closeParen == -1) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int pos = 1;
        while (pos < closeParen) {
            int len = getTypeLength(descriptor, pos);
            parts.add(descriptor.substring(pos, pos + len));
            pos += len;
        }
        parts.add(descriptor.substring(closeParen + 1)); // return type
        return parts;
    }

    /** 判断描述符字符是否为基本类型。 */
    public static boolean isPrimitiveDescriptor(char ch) {
        return switch (ch) {
            case 'V', 'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> true;
            default -> false;
        };
    }
}
