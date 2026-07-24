package com.bingbaihanji.classgraph.metadata.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataGuards")
class MetadataGuardsTest {

    @Nested
    @DisplayName("Null-safe value access")
    class NullSafeValues {

        @Test
        @DisplayName("requireNonNullElse returns default for null")
        void returnsDefaultForNull() {
            assertEquals("default", MetadataGuards.requireNonNullElse(null, "default"));
        }

        @Test
        @DisplayName("requireNonNullElse returns value when not null")
        void returnsValue() {
            assertEquals("hello", MetadataGuards.requireNonNullElse("hello", "default"));
        }

        @Test
        @DisplayName("optionalOf wraps nullable")
        void optionalOf() {
            assertTrue(MetadataGuards.optionalOf(null).isEmpty());
            assertTrue(MetadataGuards.optionalOf("value").isPresent());
        }
    }

    @Nested
    @DisplayName("Collection safety")
    class CollectionSafety {

        @Test
        @DisplayName("safeList returns empty list for null")
        void safeListForNull() {
            List<String> result = MetadataGuards.safeList(null);
            assertTrue(result.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> result.add("x"));
        }

        @Test
        @DisplayName("safeList returns copy for non-null")
        void safeListForNonNull() {
            List<String> input = new ArrayList<>(List.of("a", "b"));
            List<String> result = MetadataGuards.safeList(input);
            assertEquals(2, result.size());
            // 原始列表修改不影响结果
            input.add("c");
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("safeStream returns empty stream for null")
        void safeStreamForNull() {
            assertEquals(0, MetadataGuards.safeStream(null).count());
        }

        @Test
        @DisplayName("isEmpty is true for null or empty")
        void isEmpty() {
            assertTrue(MetadataGuards.isEmpty(null));
            assertTrue(MetadataGuards.isEmpty(List.of()));
            assertFalse(MetadataGuards.isEmpty(List.of("a")));
        }

        @Test
        @DisplayName("isNotEmpty is false for null or empty")
        void isNotEmpty() {
            assertFalse(MetadataGuards.isNotEmpty(null));
            assertFalse(MetadataGuards.isNotEmpty(List.of()));
            assertTrue(MetadataGuards.isNotEmpty(List.of("a")));
        }
    }

    @Nested
    @DisplayName("Conditional execution")
    class ConditionalExecution {

        @Test
        @DisplayName("ifPresent executes for non-null")
        void ifPresentExecutes() {
            AtomicBoolean executed = new AtomicBoolean();
            MetadataGuards.ifPresent("value", v -> executed.set(true));
            assertTrue(executed.get());
        }

        @Test
        @DisplayName("ifPresent skips for null")
        void ifPresentSkips() {
            AtomicBoolean executed = new AtomicBoolean();
            MetadataGuards.ifPresent(null, v -> executed.set(true));
            assertFalse(executed.get());
        }

        @Test
        @DisplayName("mapOrElse returns default for null")
        void mapOrElseForNull() {
            int result = MetadataGuards.mapOrElse(null, String::length, -1);
            assertEquals(-1, result);
        }

        @Test
        @DisplayName("mapOrElse maps non-null")
        void mapOrElseForNonNull() {
            int result = MetadataGuards.mapOrElse("hello", String::length, -1);
            assertEquals(5, result);
        }
    }

    @Nested
    @DisplayName("Name utilities")
    class NameUtilities {

        @Test
        @DisplayName("simpleName extracts class part")
        void simpleName() {
            assertEquals("MyClass", MetadataGuards.simpleName("com/example/MyClass"));
            assertEquals("MyClass", MetadataGuards.simpleName("MyClass"));
            assertEquals("", MetadataGuards.simpleName(null));
            assertEquals("", MetadataGuards.simpleName(""));
        }

        @Test
        @DisplayName("packageName extracts package part")
        void packageName() {
            assertEquals("com/example", MetadataGuards.packageName("com/example/MyClass"));
            assertEquals("", MetadataGuards.packageName("MyClass"));
            assertEquals("", MetadataGuards.packageName(null));
        }
    }

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        @DisplayName("queryServiceFor creates single-reader service")
        void queryServiceFor() {
            ClassMetadataReader reader = new MutableClassMetadata("test")
                .freeze();
            MetadataQueryService svc = MetadataGuards.queryServiceFor(reader);
            assertEquals(1, svc.classCount());
        }

        @Test
        @DisplayName("queryServiceFrom handles null")
        void queryServiceFromNull() {
            MetadataQueryService svc = MetadataGuards.queryServiceFrom(null);
            assertEquals(0, svc.classCount());
        }
    }
}
