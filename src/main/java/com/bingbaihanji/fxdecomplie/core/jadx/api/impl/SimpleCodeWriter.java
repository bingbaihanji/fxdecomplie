package com.bingbaihanji.fxdecomplie.core.jadx.api.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

/**
 * 不包含元信息支持的 CodeWriter 实现
 * <p>
 * 这是一个简化的代码写入器，不支持附加元数据注解 (如定义位置 源码行映射等)
 * 适用于仅需要生成纯文本代码 不关心代码导航和元信息的场景
 * </p>
 */
public class SimpleCodeWriter implements ICodeWriter {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleCodeWriter.class);
    protected final boolean insertLineNumbers;
    protected final String singleIndentStr;
    protected final String newLineStr;
    protected StringBuilder buf = new StringBuilder();
    protected String indentStr = "";
    protected int indent = 0;

    public SimpleCodeWriter(JadxArgs args) {
        this.insertLineNumbers = args.isInsertDebugLines();
        this.singleIndentStr = args.getCodeIndentStr();
        this.newLineStr = args.getCodeNewLineStr();
        if (insertLineNumbers) {
            incIndent(3);
            add(indentStr);
        }
    }

    /**
     * 应当使用带 JadxArgs 参数的构造函数
     */
    @Deprecated
    public SimpleCodeWriter() {
        this.insertLineNumbers = false;
        this.singleIndentStr = JadxArgs.DEFAULT_INDENT_STR;
        this.newLineStr = JadxArgs.DEFAULT_NEW_LINE_STR;
    }

    /**
     * 该实现不支持元数据
     *
     * @return 始终返回 false
     */
    @Override
    public boolean isMetadataSupported() {
        return false;
    }

    /**
     * 开始新的一行：先换行，再添加当前缩进
     *
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter startLine() {
        addLine();
        addLineIndent();
        return this;
    }

    /**
     * 开始新的一行并追加一个字符
     *
     * @param c 要追加的字符
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter startLine(char c) {
        startLine();
        add(c);
        return this;
    }

    /**
     * 开始新的一行并追加一个字符串
     *
     * @param str 要追加的字符串
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter startLine(String str) {
        startLine();
        add(str);
        return this;
    }

    /**
     * 开始新的一行，并按需附带源码行号
     * <p>
     * 当启用了行号插入 ({@code insertLineNumbers})时，会以注释形式写入源码行号 
     * 否则仅记录源码行映射
     * </p>
     *
     * @param sourceLine 源码行号，为 0 时等同于普通 {@link #startLine()}
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter startLineWithNum(int sourceLine) {
        if (sourceLine == 0) {
            startLine();
            return this;
        }
        if (this.insertLineNumbers) {
            newLine();
            attachSourceLine(sourceLine);
            int start = getLength();
            add("/* ").add(Integer.toString(sourceLine)).add(" */ ");
            int len = getLength() - start;
            if (indentStr.length() > len) {
                add(indentStr.substring(len));
            }
        } else {
            startLine();
            attachSourceLine(sourceLine);
        }
        return this;
    }

    /**
     * 添加可能包含多行的字符串，并为其中的每个换行补齐当前缩进
     *
     * @param str 要添加的 (可能多行的)字符串
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter addMultiLine(String str) {
        if (str.contains(newLineStr)) {
            buf.append(str.replace(newLineStr, newLineStr + indentStr));
        } else {
            buf.append(str);
        }
        return this;
    }

    /**
     * 追加一个字符串
     *
     * @param str 要追加的字符串
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter add(String str) {
        buf.append(str);
        return this;
    }

    /**
     * 追加一个字符
     *
     * @param c 要追加的字符
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter add(char c) {
        buf.append(c);
        return this;
    }

    /**
     * 追加另一个写入器中已有的代码字符串
     *
     * @param cw 源代码写入器
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public ICodeWriter add(ICodeWriter cw) {
        buf.append(cw.getCodeStr());
        return this;
    }

    /**
     * 追加一个换行符
     *
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter newLine() {
        addLine();
        return this;
    }

    /**
     * 追加一个单位缩进
     *
     * @return 当前写入器 (便于链式调用)
     */
    @Override
    public SimpleCodeWriter addIndent() {
        add(singleIndentStr);
        return this;
    }

    protected void addLine() {
        buf.append(newLineStr);
    }

    protected SimpleCodeWriter addLineIndent() {
        buf.append(indentStr);
        return this;
    }

    private void updateIndent() {
        this.indentStr = singleIndentStr.repeat(indent);
    }

    /**
     * 增加一级缩进
     */
    @Override
    public void incIndent() {
        incIndent(1);
    }

    /**
     * 减少一级缩进
     */
    @Override
    public void decIndent() {
        decIndent(1);
    }

    private void incIndent(int c) {
        this.indent += c;
        updateIndent();
    }

    private void decIndent(int c) {
        this.indent -= c;
        if (this.indent < 0) {
            LOG.warn("Indent < 0");
            this.indent = 0;
        }
        updateIndent();
    }

    /**
     * 获取当前缩进级别
     *
     * @return 当前缩进级别
     */
    @Override
    public int getIndent() {
        return indent;
    }

    /**
     * 设置当前缩进级别
     *
     * @param indent 新的缩进级别
     */
    @Override
    public void setIndent(int indent) {
        this.indent = indent;
        updateIndent();
    }

    /**
     * 获取当前行号
     * <p>
     * 该简化实现不支持行号跟踪
     * </p>
     *
     * @return 始终返回 0
     */
    @Override
    public int getLine() {
        return 0;
    }

    /**
     * 获取当前行的起始位置
     * <p>
     * 该简化实现不支持行起始位置跟踪
     * </p>
     *
     * @return 始终返回 0
     */
    @Override
    public int getLineStartPos() {
        return 0;
    }

    /**
     * 附加定义引用
     * <p>
     * 该简化实现不支持元数据，此操作为空操作
     * </p>
     *
     * @param obj 代码节点引用
     */
    @Override
    public void attachDefinition(ICodeNodeRef obj) {
        // 空操作
    }

    /**
     * 附加注解元数据
     * <p>
     * 该简化实现不支持元数据，此操作为空操作
     * </p>
     *
     * @param obj 代码注解
     */
    @Override
    public void attachAnnotation(ICodeAnnotation obj) {
        // 空操作
    }

    /**
     * 附加行级注解元数据
     * <p>
     * 该简化实现不支持元数据，此操作为空操作
     * </p>
     *
     * @param obj 代码注解
     */
    @Override
    public void attachLineAnnotation(ICodeAnnotation obj) {
        // 空操作
    }

    /**
     * 附加源码行映射
     * <p>
     * 该简化实现不支持元数据，此操作为空操作
     * </p>
     *
     * @param sourceLine 源码行号
     */
    @Override
    public void attachSourceLine(int sourceLine) {
        // 空操作
    }

    /**
     * 完成代码写入，去除首空行后返回代码信息对象
     * <p>
     * 调用此方法后内部缓冲区被清空，此后不应继续使用该写入器
     * </p>
     *
     * @return 生成的代码信息对象
     */
    @Override
    public ICodeInfo finish() {
        String code = getStringWithoutFirstEmptyLine();
        buf = null;
        return new SimpleCodeInfo(code);
    }

    private String getStringWithoutFirstEmptyLine() {
        int len = newLineStr.length();
        if (buf.length() > len && buf.substring(0, len).equals(newLineStr)) {
            return buf.substring(len);
        }
        return buf.toString();
    }

    /**
     * 获取当前缓冲区中的代码长度
     *
     * @return 已写入字符数
     */
    @Override
    public int getLength() {
        return buf.length();
    }

    /**
     * 获取底层的原始 StringBuilder 缓冲区
     *
     * @return 内部缓冲区
     */
    @Override
    public StringBuilder getRawBuf() {
        return buf;
    }

    /**
     * 获取原始注解映射
     * <p>
     * 该简化实现不支持元数据，始终返回空映射
     * </p>
     *
     * @return 空的不可变映射
     */
    @Override
    public Map<Integer, ICodeAnnotation> getRawAnnotations() {
        return Collections.emptyMap();
    }

    /**
     * 获取当前已写入的代码字符串
     *
     * @return 代码字符串
     */
    @Override
    public String getCodeStr() {
        return buf.toString();
    }

    @Override
    public String toString() {
        return getCodeStr();
    }
}
