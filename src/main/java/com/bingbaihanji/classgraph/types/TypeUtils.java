/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.types;

import java.lang.reflect.Modifier;

/**
 * 解析 Java 类型描述符和类型签名的工具类
 *
 * @author lukehutch
 */
public final class TypeUtils {

    /**
     * 构造函数
     */
    private TypeUtils() {
        // 不可构造
    }

    /**
     * 解析 Java 标识符，将 '/' 替换为 '.'将标识符追加到解析器的 token 缓冲区中
     *
     * @param parser
     *            解析器
     * @param stopAtDollarSign
     *            如果为 true，遇到第一个 '$' 时停止解析
     * @param stopAtDot
     *            如果为 true，遇到第一个 '.' 时停止解析
     * @return 如果至少解析了一个标识符字符则返回 true
     */
    public static boolean getIdentifierToken(final Parser parser, final boolean stopAtDollarSign,
                                             final boolean stopAtDot) {
        boolean consumedChar = false;
        while (parser.hasMore()) {
            final char c = parser.peek();
            if (c == '/') {
                parser.appendToToken('.');
                parser.next();
                consumedChar = true;
            } else if (c != ';' && c != '[' && c != '<' && c != '>' && c != ':' && (!stopAtDollarSign || c != '$')
                    && (!stopAtDot || c != '.')) {
                parser.appendToToken(c);
                parser.next();
                consumedChar = true;
            } else {
                break;
            }
        }
        return consumedChar;
    }

    /**
     * 必要时追加空格(如果不在缓冲区开头，且最后一个字符还不是空格)，然后追加修饰符关键字
     *
     * @param buf
     *            缓冲区
     * @param modifierKeyword
     *            修饰符关键字
     */
    private static void appendModifierKeyword(final StringBuilder buf, final String modifierKeyword) {
        if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' ') {
            buf.append(' ');
        }
        buf.append(modifierKeyword);
    }

    /**
     * 将修饰符转换为字符串表示形式，例如 "public static final"
     *
     * @param modifiers
     *            字段或方法的修饰符
     * @param modifierType
     *            这些修饰符适用的{@link ModifierType}类型
     * @param isDefault
     *            对于方法，如果这是默认方法则为 true(否则忽略)
     * @param buf
     *            用于写入结果的缓冲区
     */
    public static void modifiersToString(final int modifiers, final ModifierType modifierType,
                                         final boolean isDefault, final StringBuilder buf) {
        if ((modifiers & Modifier.PUBLIC) != 0) {
            appendModifierKeyword(buf, "public");
        } else if ((modifiers & Modifier.PRIVATE) != 0) {
            appendModifierKeyword(buf, "private");
        } else if ((modifiers & Modifier.PROTECTED) != 0) {
            appendModifierKeyword(buf, "protected");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & Modifier.ABSTRACT) != 0) {
            appendModifierKeyword(buf, "abstract");
        }
        if ((modifiers & Modifier.STATIC) != 0) {
            appendModifierKeyword(buf, "static");
        }
        if (modifierType == ModifierType.FIELD) {
            if ((modifiers & Modifier.VOLATILE) != 0) {
                // "bridge" 和 "volatile" 在位 0x40 上重叠
                appendModifierKeyword(buf, "volatile");
            }
            if ((modifiers & Modifier.TRANSIENT) != 0) {
                appendModifierKeyword(buf, "transient");
            }
        }
        if ((modifiers & Modifier.FINAL) != 0) {
            appendModifierKeyword(buf, "final");
        }
        if (modifierType == ModifierType.METHOD) {
            if ((modifiers & Modifier.SYNCHRONIZED) != 0) {
                appendModifierKeyword(buf, "synchronized");
            }
            if (isDefault) {
                appendModifierKeyword(buf, "default");
            }
        }
        if ((modifiers & 0x1000) != 0) {
            appendModifierKeyword(buf, "synthetic");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & 0x40) != 0) {
            // "bridge" 和 "volatile" 在位 0x40 上重叠
            appendModifierKeyword(buf, "bridge");
        }
        if (modifierType == ModifierType.METHOD && (modifiers & Modifier.NATIVE) != 0) {
            appendModifierKeyword(buf, "native");
        }
        if (modifierType != ModifierType.FIELD && (modifiers & Modifier.STRICT) != 0) {
            appendModifierKeyword(buf, "strictfp");
        }
        // 已忽略：
        // ACC_SUPER (0x0020)：当通过 invokespecial 指令调用时，对超类方法进行特殊处理
    }

    /** 修饰符位的来源 */
    public enum ModifierType {
        /** 修饰符位适用于类 */
        CLASS,
        /** 修饰符位适用于方法 */
        METHOD,
        /** 修饰符位适用于字段 */
        FIELD
    }
}
