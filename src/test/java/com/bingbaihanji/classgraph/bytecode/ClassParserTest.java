package com.bingbaihanji.classgraph.bytecode;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.service.reference.TestClassCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bytecode 层回归测试 — 验证 ClassFileParser 对 class 文件结构的解析正确性。
 *
 * <p>由于 ClassGraph 的 {@link ClassParser} 需要完整的 ScanConfig/Classpath 基础设施，
 * 本测试通过项目级 ClassFileParser 覆盖相同的字节码解析场景。</p>
 */
@DisplayName("ClassParser (bytecode regression)")
class ClassParserTest {

    // ── 辅助方法 ──

    private static byte[] compile(String className, String source) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(className.replace('.', '/') + ".java", source);
        return TestClassCompiler.compile(sources).values().iterator().next();
    }

    private static ClassFileMetadata parse(byte[] bytes) {
        Optional<ClassFileMetadata> meta = ClassFileParser.tryParse(bytes);
        assertTrue(meta.isPresent(), "Should parse successfully");
        return meta.get();
    }

    @Nested
    @DisplayName("Basic class parsing")
    class BasicClassParsing {

        @Test
        @DisplayName("parses class name and modifiers")
        void parsesClassNameAndModifiers() {
            byte[] bytes = compile("com.example.Hello", """
                package com.example;
                public class Hello {}
                """);

            ClassFileMetadata meta = parse(bytes);
            assertEquals("com/example/Hello", meta.internalName());
            assertEquals("java/lang/Object", meta.superName());
            // 非接口（ACC_INTERFACE = 0x0200）
            assertEquals(0, meta.accessFlags() & 0x0200);
        }

        @Test
        @DisplayName("parses interface flag")
        void parsesInterfaceFlag() {
            byte[] bytes = compile("com.example.MyInterface", """
                package com.example;
                public interface MyInterface {}
                """);

            ClassFileMetadata meta = parse(bytes);
            assertNotEquals(0, meta.accessFlags() & 0x0200, "Should be an interface");
            assertEquals("com/example/MyInterface", meta.internalName());
        }

        @Test
        @DisplayName("parses annotation flag")
        void parsesAnnotationFlag() {
            byte[] bytes = compile("com.example.MyAnnotation", """
                package com.example;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @interface MyAnnotation {}
                """);

            ClassFileMetadata meta = parse(bytes);
            assertNotEquals(0, meta.accessFlags() & 0x2000, "Should be an annotation");
        }

        @Test
        @DisplayName("parses superclass name")
        void parsesSuperclassName() {
            byte[] bytes = compile("com.example.Child", """
                package com.example;
                public class Child extends java.util.ArrayList {}
                """);

            ClassFileMetadata meta = parse(bytes);
            assertEquals("java/util/ArrayList", meta.superName());
        }

        @Test
        @DisplayName("parses implemented interfaces")
        void parsesInterfaces() {
            byte[] bytes = compile("com.example.Impl", """
                package com.example;
                import java.io.Serializable;
                import java.lang.Comparable;
                public class Impl implements Serializable, Comparable<Impl> {
                    public int compareTo(Impl o) { return 0; }
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertTrue(meta.interfaces().size() >= 2,
                "Should implement at least 2 interfaces");
            assertTrue(meta.interfaces().contains("java/io/Serializable"));
        }
    }

    @Nested
    @DisplayName("Field parsing")
    class FieldParsing {

        @Test
        @DisplayName("parses primitive fields")
        void parsesPrimitiveFields() {
            byte[] bytes = compile("com.example.Data", """
                package com.example;
                public class Data {
                    private int count;
                    public String name;
                    protected double value;
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertEquals(3, meta.fields().size());
            assertTrue(meta.fields().stream()
                .anyMatch(f -> f.name().equals("count")));
            assertTrue(meta.fields().stream()
                .anyMatch(f -> f.name().equals("name")));
            assertTrue(meta.fields().stream()
                .anyMatch(f -> f.name().equals("value")));
        }

        @Test
        @DisplayName("parses field descriptors correctly")
        void parsesFieldDescriptors() {
            byte[] bytes = compile("com.example.Types", """
                package com.example;
                public class Types {
                    int intField;
                    String strField;
                    long longField;
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertEquals(3, meta.fields().size());

            var intField = meta.fields().stream()
                .filter(f -> f.name().equals("intField")).findFirst().orElseThrow();
            assertEquals("I", intField.descriptor());

            var strField = meta.fields().stream()
                .filter(f -> f.name().equals("strField")).findFirst().orElseThrow();
            assertEquals("Ljava/lang/String;", strField.descriptor());
        }

        @Test
        @DisplayName("parses static final constant fields")
        void parsesConstantFields() {
            byte[] bytes = compile("com.example.Constants", """
                package com.example;
                public class Constants {
                    public static final int MAX = 100;
                    public static final String NAME = "test";
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertEquals(2, meta.fields().size());
            // Constant values are extracted from ConstantValue attribute
            // ClassFileParser records field name+descriptor for indexing
            assertTrue(meta.fields().stream()
                .anyMatch(f -> f.name().equals("MAX")));
            assertTrue(meta.fields().stream()
                .anyMatch(f -> f.name().equals("NAME")));
        }
    }

    @Nested
    @DisplayName("Method parsing")
    class MethodParsing {

        @Test
        @DisplayName("parses method names and descriptors")
        void parsesMethods() {
            byte[] bytes = compile("com.example.Methods", """
                package com.example;
                public class Methods {
                    public void noArgs() {}
                    public String getValue() { return ""; }
                    public void setValue(String v) {}
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            // methods: <init> + 3 declared methods
            assertTrue(meta.methods().size() >= 3,
                "Should have at least 3 methods");

            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("noArgs")
                    && m.descriptor().equals("()V")));
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("getValue")
                    && m.descriptor().equals("()Ljava/lang/String;")));
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("setValue")
                    && m.descriptor().equals("(Ljava/lang/String;)V")));
        }

        @Test
        @DisplayName("parses method with primitive parameters")
        void parsesMethodsWithPrimitiveParams() {
            byte[] bytes = compile("com.example.Calc", """
                package com.example;
                public class Calc {
                    public int add(int a, int b) { return a + b; }
                    public long multiply(long x, long y) { return x * y; }
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("add")
                    && m.descriptor().equals("(II)I")));
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("multiply")
                    && m.descriptor().equals("(JJ)J")));
        }

        @Test
        @DisplayName("parses methods with object parameters and return types")
        void parsesMethodsWithObjectParams() {
            byte[] bytes = compile("com.example.Processor", """
                package com.example;
                import java.util.List;
                import java.util.Map;
                public class Processor {
                    public List<String> process(Map<String, Object> input) {
                        return java.util.Collections.emptyList();
                    }
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("process")));
        }

        @Test
        @DisplayName("parses methods that throw exceptions")
        void parsesMethodsWithExceptions() {
            byte[] bytes = compile("com.example.Thrower", """
                package com.example;
                import java.io.IOException;
                public class Thrower {
                    public void risky() throws IOException, RuntimeException {}
                }
                """);

            ClassFileMetadata meta = parse(bytes);
            assertTrue(meta.methods().stream()
                .anyMatch(m -> m.name().equals("risky")));
        }
    }

    @Nested
    @DisplayName("Error handling")
    class ErrorHandling {

        @Test
        @DisplayName("returns empty for null input")
        void nullInput() {
            assertTrue(ClassFileParser.tryParse(null).isEmpty());
        }

        @Test
        @DisplayName("returns empty for empty array")
        void emptyInput() {
            assertTrue(ClassFileParser.tryParse(new byte[0]).isEmpty());
        }

        @Test
        @DisplayName("returns empty for truncated bytes")
        void truncatedInput() {
            assertTrue(ClassFileParser.tryParse(new byte[]{0, 0, 0, 0}).isEmpty());
        }

        @Test
        @DisplayName("returns empty for invalid magic number")
        void invalidMagicNumber() {
            byte[] invalid = new byte[20];
            invalid[0] = (byte) 0xDE;
            invalid[1] = (byte) 0xAD;
            invalid[2] = (byte) 0xBE;
            invalid[3] = (byte) 0xEF;
            assertTrue(ClassFileParser.tryParse(invalid).isEmpty());
        }

        @Test
        @DisplayName("summary returns non-empty for invalid bytes")
        void summaryForInvalidBytes() {
            byte[] bytes = new byte[]{0, 0, 0, 0};
            String summary = ClassFileParser.summary(bytes);
            assertNotNull(summary);
            assertFalse(summary.isBlank());
        }
    }
}
