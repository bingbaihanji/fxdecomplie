package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.classscan.*;
import com.bingbaihanji.fxdecomplie.service.reference.TestClassCompiler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Scan pipeline 集成测试 — 端到端目录扫描 → 类信息完整性验证。
 */
@DisplayName("Scan Pipeline Integration")
class ScanPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    private static Workspace buildWorkspace(Path dir, Map<String, String> sources) throws Exception {
        Map<String, byte[]> compiled = TestClassCompiler.compile(sources);
        for (var entry : compiled.entrySet()) {
            Path classFile = dir.resolve(entry.getKey());
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, entry.getValue());
        }

        FileTreeNode rootNode = new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootNode);
        for (var entry : compiled.entrySet()) {
            FileTreeNode node = new FileTreeNode(
                entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1),
                entry.getKey(), FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setByteLoader(() -> entry.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }

        Workspace ws = new Workspace("test", dir.toFile(), root, false);
        ws.setIndex(WorkspaceIndex.build(root));
        return ws;
    }

    @Nested
    @DisplayName("End-to-end scan")
    class EndToEndScan {

        @Test
        @DisplayName("complete scan returns all class metadata")
        void completeScanReturnsAllMetadata() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/App.java", """
                package com.example;
                public class App {
                    private String name;
                    public String getName() { return name; }
                }
                """);
            sources.put("com/example/Config.java", """
                package com.example;
                public interface Config {
                    int getPort();
                }
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            // 类级别校验
            assertEquals(2, result.statistics().scannedClasses());
            assertTrue(result.containsClass("com/example/App"));
            assertTrue(result.containsClass("com/example/Config"));

            ClassMetadata app = result.getClassOrThrow("com/example/App");
            assertFalse(app.isInterface());
            assertEquals("java/lang/Object", app.superclassName());

            ClassMetadata config = result.getClassOrThrow("com/example/Config");
            assertTrue(config.isInterface());
        }
    }

    @Nested
    @DisplayName("Field and method info completeness")
    class FieldAndMethodInfo {

        @Test
        @DisplayName("returns field info when enableFieldInfo is true")
        void returnsFieldInfo() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Model.java", """
                package com.example;
                public class Model {
                    private int id;
                    public String name;
                }
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata model = result.getClassOrThrow("com/example/Model");
            assertEquals(2, model.fields().size());
            assertTrue(model.fields().stream()
                .anyMatch(f -> f.name().equals("id")));
            assertTrue(model.fields().stream()
                .anyMatch(f -> f.name().equals("name")));
        }

        @Test
        @DisplayName("returns method info when enableMethodInfo is true")
        void returnsMethodInfo() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Service.java", """
                package com.example;
                public class Service {
                    public void start() {}
                    public String status() { return "ok"; }
                }
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata svc = result.getClassOrThrow("com/example/Service");
            // <init> + 2 declared methods = 3
            assertEquals(3, svc.methods().size());
            assertTrue(svc.methods().stream()
                .anyMatch(m -> m.name().equals("start")));
            assertTrue(svc.methods().stream()
                .anyMatch(m -> m.name().equals("status")));
        }

        @Test
        @DisplayName("respects CLASS_LEVEL_ONLY option (no field/method details)")
        void classLevelOnlyOption() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Data.java", """
                package com.example;
                public class Data {
                    private int value;
                    public int getValue() { return value; }
                }
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanRequest request = new ClassScanRequest(ws, null,
                ClassScanOptions.CLASS_LEVEL_ONLY);
            ClassScanResult result = service.scan(request);

            ClassMetadata data = result.getClassOrThrow("com/example/Data");
            // CLASS_LEVEL_ONLY disables field and method info
            assertTrue(data.fields().isEmpty(),
                "Fields should be empty with CLASS_LEVEL_ONLY");
            assertTrue(data.methods().isEmpty(),
                "Methods should be empty with CLASS_LEVEL_ONLY");
        }
    }

    @Nested
    @DisplayName("Inheritance hierarchy")
    class InheritanceHierarchy {

        @Test
        @DisplayName("captures superclass relationships")
        void capturesSuperclassRelationships() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Base.java", """
                package com.example;
                public class Base {}
                """);
            sources.put("com/example/Derived.java", """
                package com.example;
                public class Derived extends Base {}
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata derived = result.getClassOrThrow("com/example/Derived");
            assertEquals("com/example/Base", derived.superclassName());
        }

        @Test
        @DisplayName("captures interface implementations")
        void capturesInterfaceImplementations() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Runner.java", """
                package com.example;
                public interface Runner { void run(); }
                """);
            sources.put("com/example/Task.java", """
                package com.example;
                public class Task implements Runner {
                    public void run() {}
                }
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata task = result.getClassOrThrow("com/example/Task");
            assertTrue(task.interfaceNames().contains("com/example/Runner"));
        }

        @Test
        @DisplayName("multi-level inheritance is preserved")
        void multiLevelInheritance() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/A.java",
                "package com.example; public class A {}");
            sources.put("com/example/B.java",
                "package com.example; public class B extends A {}");
            sources.put("com/example/C.java",
                "package com.example; public class C extends B {}");

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata c = result.getClassOrThrow("com/example/C");
            assertEquals("com/example/B", c.superclassName());

            ClassMetadata b = result.getClassOrThrow("com/example/B");
            assertEquals("com/example/A", b.superclassName());
        }
    }

    @Nested
    @DisplayName("External class tracking")
    class ExternalClassTracking {

        @Test
        @DisplayName("classes extending java.util.ArrayList produce external class entries")
        void externalClassFromSuperclass() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/MyList.java", """
                package com.example;
                public class MyList extends java.util.ArrayList {}
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata myList = result.getClassOrThrow("com/example/MyList");
            assertFalse(myList.isExternalClass());

            // 外部类（java/util/ArrayList）也应该在结果中
            assertTrue(result.containsClass("java/util/ArrayList"),
                "java/util/ArrayList should be tracked as external class");
            ClassMetadata arrayList = result.getClassOrThrow("java/util/ArrayList");
            assertTrue(arrayList.isExternalClass(),
                "ArrayList should be marked as external");
        }
    }

    @Nested
    @DisplayName("Annotation scanning")
    class AnnotationScanning {

        @Test
        @DisplayName("detects class-level annotations")
        void detectsClassLevelAnnotations() throws Exception {
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/Marker.java", """
                package com.example;
                import java.lang.annotation.*;
                @Retention(RetentionPolicy.RUNTIME)
                @interface Marker {}
                """);
            sources.put("com/example/Annotated.java", """
                package com.example;
                @Marker
                public class Annotated {}
                """);

            Workspace ws = buildWorkspace(tempDir, sources);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ClassMetadata annotated = result.getClassOrThrow("com/example/Annotated");
            assertFalse(annotated.annotations().isEmpty(),
                "Should have at least @Marker annotation");
            assertTrue(annotated.annotations().stream()
                .anyMatch(a -> a.className().equals("com/example/Marker")));
        }
    }
}
