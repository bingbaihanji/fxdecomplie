package com.bingbaihanji.classgraph.bytecode.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParsedClassFile DTOs")
class ParsedClassFileTest {

    @Nested
    @DisplayName("ParsedClassFile")
    class ClassFileTests {

        @Test
        @DisplayName("detects interface from access flags")
        void detectsInterface() {
            ParsedClassFile cf = new ParsedClassFile(0, 52, 0x0201, // ACC_PUBLIC | ACC_INTERFACE
                "com/example/Foo", null, List.of(), List.of(), List.of(),
                List.of(), null, null, List.of(), 1);
            assertTrue(cf.isInterface());
            assertFalse(cf.isAnnotation());
            assertFalse(cf.isEnum());
        }

        @Test
        @DisplayName("detects annotation from access flags")
        void detectsAnnotation() {
            ParsedClassFile cf = new ParsedClassFile(0, 52, 0x2001, // ACC_PUBLIC | ACC_ANNOTATION
                "com/example/Ann", "java/lang/Object", List.of(), List.of(),
                List.of(), List.of(), null, null, List.of(), 1);
            assertTrue(cf.isAnnotation());
        }

        @Test
        @DisplayName("null collections default to empty")
        void nullCollectionsAreEmpty() {
            ParsedClassFile cf = new ParsedClassFile(0, 52, 0x0001,
                "com/example/Foo", "java/lang/Object",
                null, null, null, null, null, null, null, 0);
            assertTrue(cf.interfaceNames().isEmpty());
            assertTrue(cf.fields().isEmpty());
            assertTrue(cf.methods().isEmpty());
            assertTrue(cf.annotations().isEmpty());
            assertTrue(cf.referencedClasses().isEmpty());
        }
    }

    @Nested
    @DisplayName("ParsedField")
    class FieldTests {

        @Test
        @DisplayName("detects modifiers correctly")
        void detectsModifiers() {
            ParsedField field = new ParsedField("x", "I", null,
                0x0001 | 0x0008 | 0x0010, // public static final
                null, List.of());
            assertTrue(field.isPublic());
            assertTrue(field.isStatic());
            assertTrue(field.isFinal());
            assertFalse(field.isPrivate());
            assertFalse(field.isTransient());
        }

        @Test
        @DisplayName("null annotations default to empty")
        void nullAnnotationsDefaultEmpty() {
            ParsedField field = new ParsedField("x", "I", null, 0, null, null);
            assertTrue(field.annotations().isEmpty());
        }
    }

    @Nested
    @DisplayName("ParsedMethod")
    class MethodTests {

        @Test
        @DisplayName("detects constructor")
        void detectsConstructor() {
            ParsedMethod m = new ParsedMethod("<init>", "()V", null, 0x0001,
                List.of(), List.of(), null);
            assertTrue(m.isConstructor());
            assertFalse(m.isClassInitializer());
        }

        @Test
        @DisplayName("detects modifiers")
        void detectsModifiers() {
            ParsedMethod m = new ParsedMethod("run", "()V", null,
                0x0001 | 0x0100, // public native
                List.of(), List.of(), null);
            assertTrue(m.isPublic());
            assertTrue(m.isNative());
            assertFalse(m.isStatic());
            assertFalse(m.isAbstract());
        }

        @Test
        @DisplayName("null collections default to empty")
        void nullCollectionsDefaultEmpty() {
            ParsedMethod m = new ParsedMethod("foo", "()V", null, 0,
                null, null, null);
            assertTrue(m.exceptionTypeNames().isEmpty());
            assertTrue(m.annotations().isEmpty());
            assertTrue(m.parameterAnnotations().isEmpty());
        }
    }

    @Nested
    @DisplayName("ParsedAnnotation")
    class AnnotationTests {

        @Test
        @DisplayName("stores parameters correctly")
        void storesParameters() {
            ParsedAnnotation ann = new ParsedAnnotation("com/example/Ann",
                Map.of("value", "hello", "count", 42), true);
            assertTrue(ann.visible());
            assertEquals("hello", ann.parameter("value"));
            assertEquals(42, (Integer) ann.parameter("count"));
        }

        @Test
        @DisplayName("parameterOrDefault returns default when missing")
        void parameterOrDefault() {
            ParsedAnnotation ann = new ParsedAnnotation("com/example/Ann", true);
            assertEquals("fallback", ann.parameterOrDefault("missing", "fallback"));
            assertEquals("fallback", ann.parameterOrDefault("value", "fallback"));
        }

        @Test
        @DisplayName("null params default to empty map")
        void nullParamsDefaultEmpty() {
            ParsedAnnotation ann = new ParsedAnnotation("com/example/Ann",
                null, false);
            assertTrue(ann.parameterValues().isEmpty());
        }
    }
}
