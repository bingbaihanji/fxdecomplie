package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 字节码内容面板，复用 ASM Textifier 生成完整字节码文本
 *
 * <p>使用 {@code ClassReader.SKIP_DEBUG | SKIP_FRAMES} 展开常量池等元数据输出，
 * 不使用 SKIP_CODE 以确保方法指令被包含</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class BytecodeContentPanel extends AbstractCodeContentPanel {

    private final byte[] classBytes;
    private CodeArea codeArea;

    public BytecodeContentPanel(byte[] classBytes) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
    }

    @Override
    public String getContentType() {
        return "bytecode";
    }

    @Override
    protected Object buildContentAsync(Object cancelToken) throws Exception {
        if (classBytes == null || classBytes.length == 0) {
            return "// 无字节码数据";
        }

        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            Textifier textifier = new Textifier();
            TraceClassVisitor visitor = new TraceClassVisitor(null, textifier, pw);
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            return "// ASM 字节码视图解析失败: " + e.getMessage()
                    + System.lineSeparator() + System.lineSeparator()
                    + ClassFileParser.summary(classBytes);
        }
    }

    @Override
    protected javafx.scene.Node createContent(Object contentData) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setEditable(false);
        area.setText(contentData == null ? "" : contentData.toString());
        area.setSyntaxDecorator(TextFileDecorator.instance());
        this.codeArea = area;
        return area;
    }

    /** @return 字节码编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    @Override
    public void dispose() {
        super.dispose();
        codeArea = null;
    }
}
