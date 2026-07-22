package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.classgraph.core.ClassInfo;
import com.bingbaihanji.classgraph.core.ScanResult;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClassGraphWorkspaceAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void scansInterfaceAndImplementation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; public class FileServiceImpl implements FileService {}");

        Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
        ScanResult result = ClassGraphWorkspaceAdapter.scan(workspace);

        ClassInfo fileService = result.getClassInfo("com/example/FileService");
        assertNotNull(fileService);
        assertTrue(fileService.isInterface());

        ClassInfo impl = result.getClassInfo("com/example/FileServiceImpl");
        assertNotNull(impl);
        assertFalse(impl.isInterface());
        assertEquals(fileService, impl.getInterfaces().get(0));
        assertEquals(1, fileService.getClassesImplementing().size());
        assertEquals(impl, fileService.getClassesImplementing().get(0));
    }

    @Test
    void scansAnnotation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/Service.java",
                "package com.example; import java.lang.annotation.*; " +
                "@Retention(RetentionPolicy.RUNTIME) @interface Service {}");
        sources.put("com/example/UserService.java",
                "package com.example; @Service public class UserService {}");

        Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
        ScanResult result = ClassGraphWorkspaceAdapter.scan(workspace);

        ClassInfo ann = result.getClassInfo("com/example/Service");
        assertNotNull(ann);
        assertTrue(ann.isAnnotation());

        ClassInfo userService = result.getClassInfo("com/example/UserService");
        assertNotNull(userService);
        // getAnnotationInfo() returns direct annotations plus meta-annotations
        assertTrue(userService.getAnnotationInfo().size() >= 1,
                "Expected at least 1 annotation, got " + userService.getAnnotationInfo().size());
        // Verify the @Service annotation is present (may not be first due to meta-annotations)
        boolean found = false;
        for (var ai : userService.getAnnotationInfo()) {
            if ("com/example/Service".equals(ai.getName())) {
                found = true;
                break;
            }
        }
        assertTrue(found, "@Service annotation should be present");
    }
}
