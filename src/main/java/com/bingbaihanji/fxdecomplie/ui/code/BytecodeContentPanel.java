package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;

/**
 * 字节码内容面板，使用自定义 BytecodeTextBuilder 生成含常量池、hex 偏移、完整结构信息的字节码文本
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
            BytecodeTextBuilder builder = new BytecodeTextBuilder(classBytes);
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(builder, 0);
            return builder.build();
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
        area.setSyntaxDecorator(new BytecodeHighlighter());
        area.setText(contentData == null ? "" : contentData.toString());
        applyFontAndLineNumbers(area);
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
