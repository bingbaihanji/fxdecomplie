package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.reference.ClassGraphWorkspaceAdapterTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OutlineServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void findsAllInterfaceMethodImplementationsByDescriptor() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService { void save(String name); void save(int id); }");
        sources.put("com/example/AFileService.java",
                "package com.example; public class AFileService implements FileService {"
                        + " public void save(String name) {} public void save(int id) {} }");
        sources.put("com/example/BFileService.java",
                "package com.example; public class BFileService implements FileService {"
                        + " public void save(String name) {} public void save(int id) {} }");

        Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);

        var results = OutlineService.findImplementations(
                "com/example/FileService", "save", "(Ljava/lang/String;)V", workspace.getIndex());

        assertEquals(2, results.size());
        assertEquals("(Ljava/lang/String;)V", results.get(0).descriptor());
        assertEquals("(Ljava/lang/String;)V", results.get(1).descriptor());
    }
}
