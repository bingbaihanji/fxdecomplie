package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.reference.ClassGraphWorkspaceAdapterTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassGraphClassScanService")
class ClassGraphClassScanServiceTest {

    @TempDir
    Path tempDir;

    private ClassScanService service;

    @BeforeEach
    void setUp() {
        service = new ClassGraphClassScanService();
    }

    @Nested
    @DisplayName("scan() with null/empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("returns empty result for null request")
        void nullRequest() {
            ClassScanResult result = service.scan(null);
            assertEquals(0, result.size());
            assertTrue(result.getAllClasses().isEmpty());
        }

        @Test
        @DisplayName("returns empty result for workspace with EMPTY index")
        void emptyIndex() {
            Workspace ws = new Workspace("empty", tempDir.toFile(),
                new com.bingbaihanji.fxdecomplie.model.FileTreeModel(
                    new com.bingbaihanji.fxdecomplie.model.FileTreeNode(
                        "root", "", com.bingbaihanji.fxdecomplie.model.FileTreeNode.NodeTypeEnum.PACKAGE)),
                false);
            assertSame(WorkspaceIndex.EMPTY, ws.getIndex());

            ClassScanResult result = service.scan(ClassScanRequest.of(ws));
            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("scan() with interface and implementation")
    class InterfaceAndImplementation {

        @Test
        @DisplayName("scans interface and recognizing it implements relationships")
        void scansInterfaceAndImplementation() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
            sources.put("com/example/FileServiceImpl.java",
                "package com.example; public class FileServiceImpl implements FileService {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            assertTrue(result.size() >= 2, "Should have at least 2 classes");

            ClassMetadata fileService = result.getClass("com/example/FileService").orElse(null);
            assertNotNull(fileService, "FileService should be found");
            assertTrue(fileService.isInterface());
            assertFalse(fileService.isAnnotation());
            assertFalse(fileService.isEnum());

            ClassMetadata impl = result.getClass("com/example/FileServiceImpl").orElse(null);
            assertNotNull(impl, "FileServiceImpl should be found");
            assertFalse(impl.isInterface());
            assertTrue(impl.interfaceNames().contains("com/example/FileService"),
                "FileServiceImpl should implement FileService");
        }
    }

    @Nested
    @DisplayName("scan() with annotations")
    class AnnotationScanning {

        @Test
        @DisplayName("scans annotation and annotated class")
        void scansAnnotation() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Service.java",
                "package com.example; import java.lang.annotation.*; " +
                "@Retention(RetentionPolicy.RUNTIME) @interface Service {}");
            sources.put("com/example/UserService.java",
                "package com.example; @Service public class UserService {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            ClassMetadata ann = result.getClass("com/example/Service").orElse(null);
            assertNotNull(ann, "Service annotation should be found");
            assertTrue(ann.isAnnotation());

            ClassMetadata userService = result.getClass("com/example/UserService").orElse(null);
            assertNotNull(userService, "UserService should be found");
            assertFalse(userService.isAnnotation());
        }
    }

    @Nested
    @DisplayName("scan() metadata conversion")
    class MetadataConversion {

        @Test
        @DisplayName("correctly maps superclass relationship")
        void superclassRelationship() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Parent.java",
                "package com.example; public class Parent {}");
            sources.put("com/example/Child.java",
                "package com.example; public class Child extends Parent {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            ClassMetadata child = result.getClassOrThrow("com/example/Child");
            assertEquals("com/example/Parent", child.superclassName(),
                "Child should have Parent as superclass");
        }

        @Test
        @DisplayName("correctly detects isExternalClass for scanned vs referenced classes")
        void externalClassDetection() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/MyClass.java",
                "package com.example; public class MyClass extends java.util.ArrayList {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            ClassMetadata myClass = result.getClassOrThrow("com/example/MyClass");
            assertFalse(myClass.isExternalClass(),
                "MyClass was scanned, should not be external");
        }

        @Test
        @DisplayName("scan statistics are populated")
        void statisticsArePopulated() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/A.java",
                "package com.example; public class A {}");
            sources.put("com/example/B.java",
                "package com.example; public class B extends A {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            ScanStatistics stats = result.statistics();
            assertNotNull(stats);
            assertTrue(stats.totalClasses() >= 2);
            assertTrue(stats.scannedClasses() >= 2);
            assertTrue(stats.elapsedMs() >= 0);
        }

        @Test
        @DisplayName("fullPath is set for scanned classes")
        void fullPathIsSet() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Test.java",
                "package com.example; public class Test {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);
            ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

            ClassMetadata testClass = result.getClassOrThrow("com/example/Test");
            assertNotNull(testClass.fullPath());
            assertTrue(testClass.fullPath().contains("com/example/Test"));
        }
    }

    @Nested
    @DisplayName("scan() with class filter")
    class ClassFilter {

        @Test
        @DisplayName("filters classes by internal name predicate")
        void filtersByClassFilter() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Keep.java",
                "package com.example; public class Keep {}");
            sources.put("com/example/Skip.java",
                "package com.example; public class Skip {}");

            Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(
                tempDir.toFile(), sources);

            ClassScanResult result = service.scan(
                ClassScanRequest.withFilter(workspace,
                    name -> name.contains("Keep")));

            assertTrue(result.containsClass("com/example/Keep"),
                "Keep should be in results");
            assertFalse(result.containsClass("com/example/Skip"),
                "Skip should be filtered out");
        }
    }
}
