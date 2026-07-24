package com.bingbaihanji.classgraph.metadata.query;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.FieldInfoList;
import com.bingbaihanji.classgraph.metadata.MethodInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfoList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassMetadataReader (CQRS Read Model)")
class ClassMetadataReaderTest {

    private MutableClassMetadata builder;
    private ClassMetadataReader frozen;

    @BeforeEach
    void setUp() {
        builder = new MutableClassMetadata("com/example/MyClass")
            .modifiers(0x0001) // public
            .superclassName("com/example/Parent")
            .addInterface("com/example/Serializable")
            .addInterface("com/example/Cloneable");
        frozen = builder.freeze();
    }

    @Nested
    @DisplayName("Basic accessors")
    class BasicAccessors {

        @Test
        @DisplayName("className is correct")
        void className() {
            assertEquals("com/example/MyClass", frozen.className());
        }

        @Test
        @DisplayName("modifiers are correct")
        void modifiers() {
            assertEquals(0x0001, frozen.modifiers());
        }

        @Test
        @DisplayName("superclassName is correct")
        void superclassName() {
            assertEquals("com/example/Parent", frozen.superclassName());
        }

        @Test
        @DisplayName("interfaceNames contains added interfaces")
        void interfaceNames() {
            assertEquals(2, frozen.interfaceNames().size());
            assertTrue(frozen.interfaceNames().contains("com/example/Serializable"));
            assertTrue(frozen.interfaceNames().contains("com/example/Cloneable"));
        }
    }

    @Nested
    @DisplayName("Derived queries")
    class DerivedQueries {

        @Test
        @DisplayName("isInterface detects interface flag")
        void isInterface() {
            assertFalse(frozen.isInterface());
            ClassMetadataReader iface = new MutableClassMetadata("com/example/Foo")
                .modifiers(0x0201).freeze();
            assertTrue(iface.isInterface());
        }

        @Test
        @DisplayName("isAnnotation detects annotation flag")
        void isAnnotation() {
            ClassMetadataReader ann = new MutableClassMetadata("com/example/Ann")
                .modifiers(0x2001).freeze();
            assertTrue(ann.isAnnotation());
            assertFalse(frozen.isAnnotation());
        }

        @Test
        @DisplayName("hasSuperclass returns false for Object subclass")
        void hasSuperclassForObject() {
            ClassMetadataReader obj = new MutableClassMetadata("com/example/Foo")
                .superclassName("java/lang/Object").freeze();
            assertFalse(obj.hasSuperclass());
            assertTrue(frozen.hasSuperclass());
        }

        @Test
        @DisplayName("findField locates by name")
        void findField() {
            FieldInfo field = new FieldInfo("com/example/MyClass", "myField",
                0x0001, "I", null, null, null, null);
            MutableClassMetadata b = new MutableClassMetadata("com/example/MyClass")
                .addField(field);
            ClassMetadataReader reader = b.freeze();

            assertTrue(reader.findField("myField").isPresent());
            assertTrue(reader.findField("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("findMethod locates by name and descriptor")
        void findMethod() {
            MethodInfo method = new MethodInfo("com/example/MyClass", "getValue",
                null, 0x0001, "()I", null, null, null, null, false, 0, 0, null, null);
            MutableClassMetadata b = new MutableClassMetadata("com/example/MyClass")
                .addMethod(method);
            ClassMetadataReader reader = b.freeze();

            assertTrue(reader.findMethod("getValue", "()I").isPresent());
            assertTrue(reader.findMethod("getValue", "()V").isEmpty());
        }

        @Test
        @DisplayName("hasAnnotation checks by name")
        void hasAnnotation() {
            MutableClassMetadata b = new MutableClassMetadata("com/example/MyClass")
                .addAnnotation(new AnnotationInfo("com/example/Deprecated"));
            ClassMetadataReader reader = b.freeze();

            assertTrue(reader.hasAnnotation("com/example/Deprecated"));
            assertFalse(reader.hasAnnotation("com/example/Nullable"));
        }
    }

    @Nested
    @DisplayName("Builder lifecycle")
    class BuilderLifecycle {

        @Test
        @DisplayName("freeze produces immutable snapshot")
        void freezeProducesImmutable() {
            assertTrue(builder.isFrozen());

            List<String> interfaces = frozen.interfaceNames();
            assertThrows(UnsupportedOperationException.class, () ->
                interfaces.add("should fail"));
        }

        @Test
        @DisplayName("cannot modify after freeze")
        void cannotModifyAfterFreeze() {
            assertThrows(IllegalStateException.class, () ->
                builder.addInterface("should/fail"));
        }

        @Test
        @DisplayName("null arguments are safely handled")
        void nullSafe() {
            MutableClassMetadata b = new MutableClassMetadata("test")
                .addInterface(null)
                .addField(null)
                .addMethod(null)
                .addAnnotation(null)
                .addReferencedClass(null);

            ClassMetadataReader reader = b.freeze();
            assertTrue(reader.interfaceNames().isEmpty());
            assertTrue(reader.fields().isEmpty());
            assertTrue(reader.methods().isEmpty());
            assertTrue(reader.annotations().isEmpty());
            assertTrue(reader.referencedClassNames().isEmpty());
        }
    }
}
