package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 字节码/汇编视图使用 ASM Textifier 将 class 字节码转为 javap -c 风格的汇编文本
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeViewTab {

    /** 工具类私有构造器,禁止实例化 */
    private BytecodeViewTab() {
        throw new AssertionError("utility class");
    }

    /**
     * 创建字节码视图 CodeArea
     * @param classBytes 类文件字节码,为 null 则显示「无字节码」
     * @return 只读 CodeArea 组件
     */
    public static CodeArea createView(byte[] classBytes) {
        CodeArea codeArea = new CodeArea();
        codeArea.getStyleClass().add("code-editor");
        codeArea.setSyntaxDecorator(TextFileDecorator.instance());
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        LineNumberGutter.setEnabled(codeArea, true);

        if (classBytes == null) {
            codeArea.setText("// " + I18nUtil.getString("bytecode.notavailable"));
            return codeArea;
        }

        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ClassReader reader = new ClassReader(classBytes);
            Textifier textifier = new Textifier();
            TraceClassVisitor tcv = new TraceClassVisitor(null, textifier, pw);
            reader.accept(tcv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            pw.flush();
            codeArea.setText(sw.toString());
        } catch (Exception e) {
            codeArea.setText("// " + I18nUtil.getString("bytecode.parseFailed") + ": " + e.getMessage()
                    + "\n// Falling back to class metadata because ASM cannot parse this bytecode version.\n\n"
                    + ClassFileParser.summary(classBytes));
        }
        return codeArea;
    }
}
