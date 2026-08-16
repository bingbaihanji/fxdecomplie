package com.bingbaihanji.fxdecomplie.decompiler.bdec;

import com.bingbaihanji.fxdecomplie.decompiler.BdecDecompiler;
import com.bingbaihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BdecDecompiler")
class BdecDecompilerTest {

    /** 用 ASM 生成一个简单类的字节码 */
    private static byte[] simpleClassBytes(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, internalName,
                null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "hello", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("反编译简单类应产出非空源码")
    void shouldDecompileSimpleClass() {
        Decompiler bdec = new BdecDecompiler();
        byte[] bytes = simpleClassBytes("com/example/Simple");

        String source = bdec.decompileType("com.example.Simple", bytes,
                DecompilerContext.EMPTY);

        assertNotNull(source, "源码不应为 null");
        assertFalse(source.isBlank(), "源码不应为空白");
        assertFalse(source.startsWith("// Bdec Error:"),
                "成功反编译不应返回 Bdec Error");
    }

    @Test
    @DisplayName("空字节码应返回 Bdec Error 文本")
    void shouldReturnErrorOnNullBytes() {
        Decompiler bdec = new BdecDecompiler();

        String source = bdec.decompileType("com.example.Missing", null,
                DecompilerContext.EMPTY);

        assertTrue(source.startsWith("// Bdec Error:"), "应以 Bdec Error 开头");
    }

    @Test
    @DisplayName("工厂应能创建 BDEC 引擎")
    void shouldRegisterInFactory() {
        Decompiler engine = DecompilerFactory.getDecompiler(DecompilerTypeEnum.BDEC);
        assertNotNull(engine, "BDEC 引擎不应为 null");
        assertEquals(DecompilerTypeEnum.BDEC, engine.getType());
        assertEquals("bdec", engine.getName());
    }
}
