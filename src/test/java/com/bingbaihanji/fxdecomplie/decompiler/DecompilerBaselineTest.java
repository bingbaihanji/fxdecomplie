package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

class DecompilerBaselineTest {

    private static final String SIMPLE_CLASS = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello, World!");
                }
            }
            """;

    @Test
    void allEnginesDecompileSimpleClass(@TempDir Path tmp) throws Exception {
        // Compile a simple class
        Path srcFile = tmp.resolve("HelloWorld.java");
        Files.writeString(srcFile, SIMPLE_CLASS);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                srcFile.toString(), "-d", tmp.toString());
        assertEquals(0, result, "Compilation should succeed");

        byte[] classBytes = Files.readAllBytes(tmp.resolve("HelloWorld.class"));

        for (DecompilerTypeEnum type : DecompilerTypeEnum.values()) {
            Decompiler engine = DecompilerFactory.getDecompiler(type);
            String output = engine.decompile("HelloWorld.class", classBytes);
            assertNotNull(output, type + " output should not be null");
            assertFalse(output.isBlank(), type + " output should not be blank");
            // Every engine should produce "Hello" somewhere in the output
            assertTrue(output.contains("Hello"),
                    type + " output should contain 'Hello': " + output.substring(0, Math.min(100, output.length())));
        }
    }
}
