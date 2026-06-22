package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;

/**
 * JVM 指令视图面板，使用自定义 SmaliTextBuilder 生成 smali 风格输出
 *
 * <p>输出格式类似 jadx smali 视图：以 .class/.method/.field/.line/.end method 等
 * 指令组织，操作码使用小写助记符，保留行号和局部变量调试信息</p>
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public class SmaliContentPanel extends AbstractCodeContentPanel {

    private final byte[] classBytes;
    private CodeArea codeArea;

    public SmaliContentPanel(byte[] classBytes) {
        this.classBytes = classBytes == null ? null : classBytes.clone();
    }

    @Override
    public String getContentType() {
        return "smali";
    }

    @Override
    protected Object buildContentAsync(Object cancelToken) throws Exception {
        if (classBytes == null || classBytes.length == 0) {
            return "// 无字节码数据";
        }

        try {
            SmaliTextBuilder builder = new SmaliTextBuilder();
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(builder, ClassReader.SKIP_FRAMES);
            return builder.build();
        } catch (Exception e) {
            return "// JVM 指令视图解析失败: " + e.getMessage()
                    + System.lineSeparator() + System.lineSeparator()
                    + ClassFileParser.summary(classBytes);
        }
    }

    @Override
    protected javafx.scene.Node createContent(Object contentData) {
        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setEditable(false);
        area.setSyntaxDecorator(new SmaliHighlighter());
        area.setText(contentData == null ? "" : contentData.toString());
        applyFontAndLineNumbers(area);
        this.codeArea = area;
        return area;
    }

    /** @return JVM 指令视图编辑器 */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    @Override
    public void dispose() {
        super.dispose();
        codeArea = null;
    }
}
