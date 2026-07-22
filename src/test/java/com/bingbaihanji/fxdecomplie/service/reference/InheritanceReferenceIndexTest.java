package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceReferenceIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void indexProvidesImplementations() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertTrue(index.implementationsOf("com/example/FileService").contains("com/example/FileServiceImpl"));
    }

    @Test
    void indexProvidesAnnotatedClasses() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertTrue(index.annotatedBy("com/example/Service").contains("com/example/UserService"));
    }

    @Test
    void fullPathMappingWorks() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertNotNull(index.fullPathOf("com/example/FileService"));
        assertTrue(index.fullPathOf("com/example/FileService").endsWith("FileService.class"));
    }

    @Test
    void indexServiceWaitsForWorkspaceIndexBeforeScanning() throws Exception {
        Workspace readyWorkspace = sampleWorkspace();
        Workspace workspace = new Workspace("lazy", tempDir.toFile(),
                readyWorkspace.getTreeRoot(), false);

        assertNull(InheritanceReferenceIndexService.getOrStart(workspace));
        var future = InheritanceReferenceIndexService.getFuture(workspace);
        assertNotNull(future);

        InheritanceReferenceIndex index = future.join();
        assertFalse(index.scanResult().getAllClasses().isEmpty());
        assertTrue(index.implementationsOf("com/example/FileService")
                .contains("com/example/FileServiceImpl"));
    }

    private Workspace sampleWorkspace() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/Service.java",
                "package com.example; import java.lang.annotation.*; " +
                "@Retention(RetentionPolicy.RUNTIME) @interface Service {}");
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; @Service public class FileServiceImpl implements FileService {}");
        sources.put("com/example/UserService.java",
                "package com.example; @Service public class UserService {}");
        return ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
    }

    private InheritanceReferenceIndex buildIndex(Workspace workspace) {
        var scanResult = ClassGraphWorkspaceAdapter.scan(workspace);
        java.util.LinkedHashMap<String, String> pathMap = new java.util.LinkedHashMap<>();
        for (var ci : scanResult.getAllClasses()) {
            if (ci.getFullPath() != null) {
                pathMap.put(ci.getName(), ci.getFullPath());
            }
        }
        return new InheritanceReferenceIndex(scanResult, pathMap);
    }
}
