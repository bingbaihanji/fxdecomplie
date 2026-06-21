package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.scene.Node;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * JVM 指令视图面板，使用 ASM Textifier + TraceClassVisitor 生成 javap -c 风格输出
 *
 * <p>UI 标签标注为 "Smali" 以贴近 jadx 交互习惯，但实际生成的是 JVM class 指令文本，
 * 不是 Android smali 格式。未来若支持 DEX 输入，再引入独立的 DexSmaliContentPanel</p>
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
    protected Node buildContentAsync(Object cancelToken) throws Exception {
        if (classBytes == null || classBytes.length == 0) {
            CodeArea empty = new CodeArea();
            empty.setText("// 无字节码数据");
            empty.setEditable(false);
            this.codeArea = empty;
            return empty;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        Textifier textifier = new Textifier();
        TraceClassVisitor visitor = new TraceClassVisitor(null, textifier, pw);
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        pw.flush();

        CodeArea area = new CodeArea();
        area.getStyleClass().add("code-editor");
        area.setEditable(false);
        area.setText(sw.toString());
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
