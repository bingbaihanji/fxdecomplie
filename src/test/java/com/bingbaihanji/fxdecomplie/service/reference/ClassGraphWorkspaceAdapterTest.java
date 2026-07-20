package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.ScanResult;
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
        assertEquals(1, fileService.getImplementingClasses().size());
        assertEquals(impl, fileService.getImplementingClasses().get(0));
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
        assertEquals(1, userService.getAnnotationInfo().size());
        assertEquals("com/example/Service", userService.getAnnotationInfo().get(0).getName());
    }
}
