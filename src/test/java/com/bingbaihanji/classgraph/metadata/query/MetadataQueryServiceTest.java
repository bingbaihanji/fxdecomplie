package com.bingbaihanji.classgraph.metadata.query;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MetadataQueryService")
class MetadataQueryServiceTest {

    private MetadataQueryService service;
    private List<ClassMetadataReader> readers;

    @BeforeEach
    void setUp() {
        readers = new ArrayList<>();

        // 接口
        readers.add(new MutableClassMetadata("com/example/Service")
            .modifiers(0x0201).freeze());
        readers.add(new MutableClassMetadata("com/example/Repository")
            .modifiers(0x0201).freeze());

        // 注解
        readers.add(new MutableClassMetadata("com/example/Component")
            .modifiers(0x2001).freeze());

        // 枚举
        readers.add(new MutableClassMetadata("com/example/Color")
            .modifiers(0x4001).freeze());

        // 标准类
        readers.add(new MutableClassMetadata("com/example/ServiceImpl")
            .modifiers(0x0001)
            .addInterface("com/example/Service")
            .freeze());
        readers.add(new MutableClassMetadata("com/example/RepoImpl")
            .modifiers(0x0001)
            .addInterface("com/example/Repository")
            .freeze());
        readers.add(new MutableClassMetadata("com/example/AnnotatedClass")
            .modifiers(0x0001)
            .addAnnotation(new AnnotationInfo("com/example/Component"))
            .freeze());

        // 子类
        readers.add(new MutableClassMetadata("com/example/Child")
            .modifiers(0x0001)
            .superclassName("com/example/ServiceImpl")
            .freeze());

        service = MetadataGuards.queryServiceFrom(readers);
    }

    @Nested
    @DisplayName("Filtering queries")
    class FilteringQueries {

        @Test
        @DisplayName("findAllInterfaces returns only interfaces")
        void findAllInterfaces() {
            List<ClassMetadataReader> interfaces = service.findAllInterfaces();
            assertEquals(2, interfaces.size());
            assertTrue(interfaces.stream().allMatch(ClassMetadataReader::isInterface));
        }

        @Test
        @DisplayName("findAllAnnotations returns only annotations")
        void findAllAnnotations() {
            List<ClassMetadataReader> annotations = service.findAllAnnotations();
            assertEquals(1, annotations.size());
            assertEquals("com/example/Component", annotations.get(0).className());
        }

        @Test
        @DisplayName("findAllEnums returns only enums")
        void findAllEnums() {
            List<ClassMetadataReader> enums = service.findAllEnums();
            assertEquals(1, enums.size());
        }

        @Test
        @DisplayName("findAllStandardClasses excludes interfaces/annotations/enums")
        void findAllStandardClasses() {
            List<ClassMetadataReader> std = service.findAllStandardClasses();
            assertEquals(4, std.size());
            assertTrue(std.stream().noneMatch(ClassMetadataReader::isInterface));
            assertTrue(std.stream().noneMatch(ClassMetadataReader::isAnnotation));
            assertTrue(std.stream().noneMatch(ClassMetadataReader::isEnum));
        }

        @Test
        @DisplayName("findImplementationsOf returns implementors")
        void findImplementationsOf() {
            List<ClassMetadataReader> impls = service.findImplementationsOf(
                "com/example/Service");
            assertEquals(1, impls.size());
            assertEquals("com/example/ServiceImpl", impls.get(0).className());
        }

        @Test
        @DisplayName("findSubclassesOf returns subclasses")
        void findSubclassesOf() {
            List<ClassMetadataReader> subs = service.findSubclassesOf(
                "com/example/ServiceImpl");
            assertEquals(1, subs.size());
            assertEquals("com/example/Child", subs.get(0).className());
        }

        @Test
        @DisplayName("findAnnotatedWith returns annotated classes")
        void findAnnotatedWith() {
            List<ClassMetadataReader> annotated = service.findAnnotatedWith(
                "com/example/Component");
            assertEquals(1, annotated.size());
            assertEquals("com/example/AnnotatedClass", annotated.get(0).className());
        }
    }

    @Nested
    @DisplayName("Aggregation queries")
    class AggregationQueries {

        @Test
        @DisplayName("groupByPackage groups correctly")
        void groupByPackage() {
            Map<String, List<ClassMetadataReader>> groups = service.groupByPackage();
            assertTrue(groups.containsKey("com/example"));
            assertEquals(8, groups.get("com/example").size());
        }

        @Test
        @DisplayName("statistics are correct")
        void statistics() {
            MetadataQueryService.MetadataStatistics stats = service.statistics();
            assertEquals(8, stats.totalClasses());
            assertEquals(2, stats.interfaceCount());
            assertEquals(1, stats.annotationCount());
            assertEquals(1, stats.enumCount());
            assertEquals(4, stats.standardClassCount());
        }
    }

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty service returns empty results")
        void emptyService() {
            MetadataQueryService empty = MetadataGuards.queryServiceFrom(List.of());
            assertEquals(0, empty.classCount());
            assertTrue(empty.findAllInterfaces().isEmpty());
            assertTrue(empty.findAllAnnotations().isEmpty());
        }

        @Test
        @DisplayName("findByClass returns empty for missing class")
        void findByClassMissing() {
            assertTrue(service.findByClass("com/example/DoesNotExist").isEmpty());
        }

        @Test
        @DisplayName("findByClass returns present for existing class")
        void findByClassExists() {
            assertTrue(service.findByClass("com/example/Service").isPresent());
        }
    }
}
