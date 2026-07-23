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
package com.bingbaihanji.classgraph.type;


import com.bingbaihanji.classgraph.util.Strings;

/**
 * 通用 PEG 解析器
 */
public class TypeParser {
    /** 当前位置之前显示的上下文字符数 */
    private static final int SHOW_BEFORE = 80;
    /** 当前位置之后显示的上下文字符数 */
    private static final int SHOW_AFTER = 80;
    /** 正在解析的字符串 */
    private final String string;
    /** Token 缓冲区 */
    private final StringBuilder token = new StringBuilder();
    /** 当前位置 */
    private int position;
    /** 额外的解析状态 */
    private Object state;

    /**
     * 构造一个解析器
     *
     * @param string
     *            要解析的字符串
     * @throws ParseException
     *             如果字符串为 null
     */
    public TypeParser(final String string) throws ParseException {
        if (string == null) {
            throw new ParseException(null, "Cannot parse null string");
        }
        this.string = string;
    }

    /**
     * 获取解析上下文字符串，用于调试
     *
     * @return 显示解析上下文的字符串，用于调试
     */
    public String getPositionInfo() {
        final int showStart = Math.max(0, position - SHOW_BEFORE);
        final int showEnd = Math.min(string.length(), position + SHOW_AFTER);
        return "before: \"" + Strings.escapeJson(string.substring(showStart, position)) + "\"; after: \""
                + Strings.escapeJson(string.substring(position, showEnd)) + "\"; position: " + position
                + "; token: \"" + token + "\"";
    }

    /**
     * 设置解析器的"状态对象"(可用于在解析函数之间传递状态)
     *
     * @param state
     *            状态对象
     * @return 状态对象的旧值
     */
    public Object setState(final Object state) {
        final Object oldState = this.state;
        this.state = state;
        return oldState;
    }

    /**
     * 获取解析器的"状态对象"(可用于在解析函数之间传递状态)
     *
     * @return 状态对象的当前值
     */
    public Object getState() {
        return state;
    }

    /**
     * 获取下一个字符
     *
     * @return 下一个字符
     * @throws ParseException
     *             如果字符串中没有更多字符
     */
    public char getc() throws ParseException {
        if (position >= string.length()) {
            throw new ParseException(this, "Ran out of input while parsing");
        }
        return string.charAt(position++);
    }

    /**
     * 期望下一个字符为指定字符
     *
     * @param expectedChar
     *            期望的字符
     * @throws ParseException
     *             如果下一个字符不是期望的字符
     */
    public void expect(final char expectedChar) throws ParseException {
        final int next = getc();
        if (next != expectedChar) {
            throw new ParseException(this, "Expected '" + expectedChar + "'; got '" + (char) next + "'");
        }
    }

    /**
     * 查看下一个字符但不读取它
     *
     * @return 下一个字符，如果已到字符串末尾则返回 '\0'
     */
    public char peek() {
        return position == string.length() ? '\0' : string.charAt(position);
    }

    /**
     * 查看下一个字符，如果下一个字符不是期望的字符则抛出{@link ParseException}
     *
     * @param expectedChar
     *            期望的下一个字符
     * @throws ParseException
     *             如果下一个字符不是期望的字符
     */
    public void peekExpect(final char expectedChar) throws ParseException {
        if (position == string.length()) {
            throw new ParseException(this, "Expected '" + expectedChar + "'; reached end of string");
        }
        final char next = string.charAt(position);
        if (next != expectedChar) {
            throw new ParseException(this, "Expected '" + expectedChar + "'; got '" + next + "'");
        }
    }

    /**
     * 向前查看操作符，可以向前查看多个字符
     *
     * @param strMatch
     *            要比较的字符串，从当前位置开始，作为"向前查看"操作
     * @return 如果 strMatch 与从当前位置开始的剩余字符串的子串匹配，则返回 true
     */
    public boolean peekMatches(final String strMatch) {
        return string.regionMatches(position, strMatch, 0, strMatch.length());
    }

    /**
     * 前进一个字符，不返回该字符的值
     */
    public void next() {
        position++;
    }

    /**
     * 前进 numChars 个字符位置
     *
     * @param numChars
     *            要前进的字符位置数
     * @throws IllegalArgumentException
     *             如果字符串中剩余字符不足
     */
    public void advance(final int numChars) {
        if (position + numChars >= string.length()) {
            throw new IllegalArgumentException("Invalid skip distance");
        }
        position += numChars;
    }

    /**
     * 检查是否还有更多字符可供解析
     *
     * @return 如果输入尚未全部消耗则返回 true
     */
    public boolean hasMore() {
        return position < string.length();
    }

    /**
     * 获取当前位置
     *
     * @return 当前位置
     */
    public int getPosition() {
        return position;
    }

    /**
     * 设置解析器在字符串中的位置
     *
     * @param position
     *            要移动到的位置
     * @throws IllegalArgumentException
     *             如果位置超出范围
     */
    public void setPosition(final int position) {
        if (position < 0 || position >= string.length()) {
            throw new IllegalArgumentException("Invalid position");
        }
        this.position = position;
    }

    /**
     * 返回输入字符串的子序列
     *
     * @param startPosition
     *            起始位置
     * @param endPosition
     *            结束位置
     * @return 子序列
     */
    public CharSequence getSubsequence(final int startPosition, final int endPosition) {
        return string.subSequence(startPosition, endPosition);
    }

    /**
     * 返回输入字符串的子字符串
     *
     * @param startPosition
     *            起始位置
     * @param endPosition
     *            结束位置
     * @return 子字符串
     */
    public String getSubstring(final int startPosition, final int endPosition) {
        return string.substring(startPosition, endPosition);
    }

    /**
     * 将给定字符串追加到 token 缓冲区
     *
     * @param str
     *            要追加的字符串
     */
    public void appendToToken(final String str) {
        token.append(str);
    }

    /**
     * 将给定字符追加到 token 缓冲区
     *
     * @param c
     *            要追加的字符
     */
    public void appendToToken(final char c) {
        token.append(c);
    }

    /**
     * 从当前位置开始跳过空白字符
     */
    public void skipWhitespace() {
        while (position < string.length()) {
            final char c = string.charAt(position);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                position++;
            } else {
                break;
            }
        }
    }

    /**
     * 获取当前 token，并将 token 重置为空
     *
     * @return 当前 token将当前 token 重置为空
     */
    public String currToken() {
        final String tok = token.toString();
        token.setLength(0);
        return tok;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getPositionInfo();
    }
}