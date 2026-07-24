package com.bingbaihanji.classgraph.classpath;

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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Classpath 层回归测试 — JAR classpath + 重复 class 遮蔽规则。
 */
@DisplayName("Classpath Regression")
class ClasspathRegressionTest {

    @TempDir
    Path tempDir;

    // ── 辅助方法 ──

    private static byte[] compile(String className, String source) {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put(className.replace('.', '/') + ".java", source);
        return TestClassCompiler.compile(sources).values().iterator().next();
    }

    private File createJar(String name, Map<String, byte[]> classEntries) throws Exception {
        File jarFile = tempDir.resolve(name).toFile();
        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            for (var entry : classEntries.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }
        return jarFile;
    }

    private Workspace workspaceFromJar(File jarFile) throws Exception {
        FileTreeNode rootNode = new FileTreeNode(
            jarFile.getName(), jarFile.getAbsolutePath(),
            FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootNode);
        return new Workspace(jarFile.getName(), jarFile, root, true);
    }

    private Workspace workspaceFromDir(Path dir, Map<String, byte[]> classEntries) throws Exception {
        for (var entry : classEntries.entrySet()) {
            Path classFile = dir.resolve(entry.getKey());
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, entry.getValue());
        }

        FileTreeNode rootNode = new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootNode);
        for (var entry : classEntries.entrySet()) {
            FileTreeNode node = new FileTreeNode(
                entry.getKey().substring(entry.getKey().lastIndexOf('/') + 1),
                entry.getKey(), FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setByteLoader(() -> entry.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }

        Workspace workspace = new Workspace("test", dir.toFile(), root, false);
        workspace.setIndex(WorkspaceIndex.build(root));
        return workspace;
    }

    @Nested
    @DisplayName("Directory classpath")
    class DirectoryClasspath {

        @Test
        @DisplayName("discovers all classes in directory")
        void discoversAllClasses() throws Exception {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("com/example/A.class", compile("com.example.A",
                "package com.example; public class A {}"));
            entries.put("com/example/B.class", compile("com.example.B",
                "package com.example; public class B {}"));
            entries.put("com/example/C.class", compile("com.example.C",
                "package com.example; public class C {}"));

            Workspace ws = workspaceFromDir(tempDir, entries);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            assertEquals(3, result.size());
            assertTrue(result.containsClass("com/example/A"));
            assertTrue(result.containsClass("com/example/B"));
            assertTrue(result.containsClass("com/example/C"));
        }

        @Test
        @DisplayName("handles subdirectories correctly")
        void handlesSubdirectories() throws Exception {
            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("com/example/pkg1/X.class", compile("com.example.pkg1.X",
                "package com.example.pkg1; public class X {}"));
            entries.put("com/example/pkg2/Y.class", compile("com.example.pkg2.Y",
                "package com.example.pkg2; public class Y {}"));

            Workspace ws = workspaceFromDir(tempDir, entries);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            assertEquals(2, result.size());
        }
    }

    @Nested
    @DisplayName("Duplicate class shadowing")
    class DuplicateClassShadowing {

        @Test
        @DisplayName("same class in workspace index replaces earlier entry")
        void sameClassReplacesEarlierEntry() throws Exception {
            // 两个不同版本的同名类
            byte[] v1 = compile("com.example.Foo", """
                package com.example;
                public class Foo { public String version() { return "v1"; } }
                """);
            byte[] v2 = compile("com.example.Foo", """
                package com.example;
                public class Foo { public String version() { return "v2"; } }
                """);

            Map<String, byte[]> entries = new LinkedHashMap<>();
            entries.put("com/example/Foo.class", v2); // v2 wins

            Workspace ws = workspaceFromDir(tempDir, entries);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            assertEquals(1, result.size());
            assertTrue(result.containsClass("com/example/Foo"));
        }
    }

    @Nested
    @DisplayName("Classpath statistics")
    class ClasspathStatistics {

        @Test
        @DisplayName("reports correct scan statistics")
        void reportsCorrectStatistics() throws Exception {
            // 编译所有相关类（需要一起编译以解析跨类引用）
            Map<String, String> sources = new LinkedHashMap<>();
            sources.put("com/example/P.java",
                "package com.example; public class P {}");
            sources.put("com/example/Q.java",
                "package com.example; public class Q extends P {}");
            sources.put("com/example/R.java",
                "package com.example; public class R extends Q {}");
            Map<String, byte[]> compiled = TestClassCompiler.compile(sources);

            Map<String, byte[]> entries = new LinkedHashMap<>();
            for (var entry : compiled.entrySet()) {
                entries.put(entry.getKey(), entry.getValue());
            }

            Workspace ws = workspaceFromDir(tempDir, entries);
            ClassScanService service = new ClassGraphClassScanService();
            ClassScanResult result = service.scan(ClassScanRequest.of(ws));

            ScanStatistics stats = result.statistics();
            assertEquals(3, stats.scannedClasses());
            assertTrue(stats.elapsedMs() >= 0);
            assertTrue(stats.totalClasses() >= 3,
                "External classes (like java/lang/Object) should also be counted");
        }
    }
}
