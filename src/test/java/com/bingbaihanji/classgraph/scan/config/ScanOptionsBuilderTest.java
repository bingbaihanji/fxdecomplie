package com.bingbaihanji.classgraph.scan.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScanOptionsBuilder")
class ScanOptionsBuilderTest {

    @Test
    @DisplayName("builds default options")
    void buildsDefaultOptions() {
        ScanOptions opts = new ScanOptionsBuilder().build();
        assertNotNull(opts);
        assertFalse(opts.enableFieldInfo());
        assertFalse(opts.enableMethodInfo());
        assertTrue(opts.scanJars());
        assertTrue(opts.scanDirs());
    }

    @Test
    @DisplayName("builds with direct setters")
    void buildsWithDirectSetters() {
        ScanOptions opts = new ScanOptionsBuilder()
            .enableClassInfo(true)
            .enableFieldInfo(true)
            .enableMethodInfo(true)
            .scanJars(false)
            .maxThreads(4)
            .build();

        assertTrue(opts.enableClassInfo());
        assertTrue(opts.enableFieldInfo());
        assertTrue(opts.enableMethodInfo());
        assertFalse(opts.scanJars());
        assertEquals(4, opts.maxThreads());
    }

    @Test
    @DisplayName("builds with lambda configurator")
    void buildsWithLambdaConfigurator() {
        ScanOptions opts = new ScanOptionsBuilder()
            .metadata(m -> m.withAll())
            .classpath(c -> c.scanDirs(false).scanJars(false))
            .build();

        assertTrue(opts.enableFieldInfo());
        assertTrue(opts.enableAnnotationInfo());
        assertTrue(opts.enableExternalClasses());
        assertFalse(opts.scanDirs());
        assertFalse(opts.scanJars());
    }

    @Test
    @DisplayName("toBuilder preserves values")
    void toBuilderPreservesValues() {
        ScanOptions original = new ScanOptionsBuilder()
            .enableFieldInfo(true)
            .enableAnnotationInfo(true)
            .maxThreads(8)
            .build();

        ScanOptions modified = original.toBuilder()
            .enableFieldInfo(false)
            .build();

        assertTrue(original.enableFieldInfo(), "Original should be unchanged");
        assertFalse(modified.enableFieldInfo(), "Modified should have new value");
        assertTrue(modified.enableAnnotationInfo(), "Unchanged values should persist");
        assertEquals(8, modified.maxThreads());
    }

    @Test
    @DisplayName("predefined constants work")
    void predefinedConstants() {
        assertFalse(ScanOptions.DEFAULT.enableFieldInfo());
        assertTrue(ScanOptions.ALL_METADATA.enableFieldInfo());
        assertTrue(ScanOptions.ALL_METADATA.enableMethodInfo());
        assertTrue(ScanOptions.ALL_METADATA.enableAnnotationInfo());
    }

    @Test
    @DisplayName("immutability — modifying builder does not affect built options")
    void builderIsIndependent() {
        ScanOptionsBuilder builder = new ScanOptionsBuilder()
            .enableFieldInfo(true);

        ScanOptions opts1 = builder.build();
        assertTrue(opts1.enableFieldInfo());

        ScanOptions opts2 = builder.enableFieldInfo(false).build();
        assertTrue(opts1.enableFieldInfo(), "opts1 should be unchanged");
        assertFalse(opts2.enableFieldInfo(), "opts2 should have new value");
    }

    @Test
    @DisplayName("maxThreads clamps negative to zero")
    void maxThreadsClamped() {
        ScanOptions opts = new ScanOptionsBuilder().maxThreads(-5).build();
        assertEquals(0, opts.maxThreads());
    }

    @Nested
    @DisplayName("Sub-builder tests")
    class SubBuilderTests {

        @Test
        @DisplayName("MetadataBuilder.withAll enables everything")
        void metadataWithAll() {
            ScanMetadataOptions m = new ScanOptionsBuilder.MetadataBuilder(
                ScanMetadataOptions.DEFAULT).withAll().build();
            assertTrue(m.enableClassInfo());
            assertTrue(m.enableFieldInfo());
            assertTrue(m.enableMethodInfo());
            assertTrue(m.enableAnnotationInfo());
            assertTrue(m.enableStaticFinalFieldConstantInitializerValues());
            assertTrue(m.enableInterClassDependencies());
            assertTrue(m.enableExternalClasses());
        }

        @Test
        @DisplayName("ClasspathBuilder.addURLScheme works")
        void classpathBuilderURLScheme() {
            ScanClasspathOptions c = new ScanOptionsBuilder.ClasspathBuilder(
                ScanClasspathOptions.DEFAULT)
                .addURLScheme("http")
                .build();
            assertTrue(c.allowedURLSchemes().contains("http"));
        }
    }
}
