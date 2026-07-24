package com.bingbaihanji.classgraph.bytecode.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataLinker")
class MetadataLinkerTest {

    @Test
    @DisplayName("links superclass relationships")
    void linksSuperclassRelationships() {
        ParsedClassFile parent = createClass("com/example/Parent",
            "java/lang/Object", List.of());
        ParsedClassFile child = createClass("com/example/Child",
            "com/example/Parent", List.of());

        MetadataLinker.LinkResult result = MetadataLinker.link(
            List.of(parent, child));

        assertEquals(2, result.classByName().size());
        assertTrue(result.superclassToSubclasses()
            .getOrDefault("com/example/Parent", java.util.Set.of())
            .contains("com/example/Child"));
    }

    @Test
    @DisplayName("links interface implementations")
    void linksInterfaceImplementations() {
        ParsedClassFile itf = createClass("com/example/Itf",
            "java/lang/Object", List.of());
        ParsedClassFile impl = createClass("com/example/Impl",
            "java/lang/Object", List.of("com/example/Itf"));

        MetadataLinker.LinkResult result = MetadataLinker.link(
            List.of(itf, impl));

        assertTrue(result.interfaceToImplementations()
            .getOrDefault("com/example/Itf", java.util.Set.of())
            .contains("com/example/Impl"));
    }

    @Test
    @DisplayName("links annotation to annotated classes")
    void linksAnnotationToAnnotatedClasses() {
        ParsedAnnotation ann = new ParsedAnnotation("com/example/Ann", true);
        ParsedClassFile target = new ParsedClassFile(0, 52, 0x0001,
            "com/example/Target", "java/lang/Object", List.of(),
            List.of(), List.of(), List.of(ann), null, null, List.of(), 1);

        MetadataLinker.LinkResult result = MetadataLinker.link(List.of(target));

        assertTrue(result.annotationToAnnotated()
            .getOrDefault("com/example/Ann", java.util.Set.of())
            .contains("com/example/Target"));
    }

    @Test
    @DisplayName("empty input produces empty result")
    void emptyInputProducesEmptyResult() {
        MetadataLinker.LinkResult result = MetadataLinker.link(List.of());
        assertTrue(result.classByName().isEmpty());
        assertTrue(result.superclassToSubclasses().isEmpty());
        assertTrue(result.interfaceToImplementations().isEmpty());
    }

    @Test
    @DisplayName("Object superclass is not linked")
    void objectSuperclassNotLinked() {
        ParsedClassFile cls = createClass("com/example/MyClass",
            "java/lang/Object", List.of());

        MetadataLinker.LinkResult result = MetadataLinker.link(List.of(cls));
        // java/lang/Object should NOT appear as a key
        assertFalse(result.superclassToSubclasses()
            .containsKey("java/lang/Object"));
    }

    private static ParsedClassFile createClass(String name, String superName,
                                                List<String> interfaces) {
        return new ParsedClassFile(0, 52, 0x0001, name, superName,
            interfaces, List.of(), List.of(), List.of(), null, null, List.of(), 1);
    }
}
