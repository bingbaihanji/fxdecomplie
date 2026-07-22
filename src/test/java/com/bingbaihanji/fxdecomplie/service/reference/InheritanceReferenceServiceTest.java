package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceReferenceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void partialResultWithoutIndex() throws Exception {
        Workspace workspace = sampleWorkspace();
        byte[] implBytes = readBytes(workspace, "com/example/FileServiceImpl.class");
        InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                workspace, "com/example/FileServiceImpl.class", implBytes);
        assertEquals("FileServiceImpl", tree.root().displayName());
        assertTrue(tree.partial() || tree.statusMessage().contains("正在") || tree.statusMessage().contains("Building"));
        assertTrue(hasKind(tree, Kind.INTERFACE));
        assertTrue(hasKind(tree, Kind.OVERRIDES));
    }

    @Test
    void fullResultWithIndex() throws Exception {
        Workspace workspace = sampleWorkspace();
        workspace.setIndex(com.bingbaihanji.fxdecomplie.model.WorkspaceIndex.build(workspace.getTreeRoot()));
        InheritanceReferenceIndexService.getOrStart(workspace);
        var future = InheritanceReferenceIndexService.getFuture(workspace);
        if (future != null) {
            future.join();
        }
        byte[] bytes = readBytes(workspace, "com/example/FileService.class");
        InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                workspace, "com/example/FileService.class", bytes);
        assertTrue(hasKind(tree, Kind.IMPLEMENTATION));
        assertTrue(hasKind(tree, Kind.OVERRIDDEN_BY));
    }

    private Workspace sampleWorkspace() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService { void save(String name); }");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; public class FileServiceImpl implements FileService {"
                        + " public void save(String name) {} }");
        return ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
    }

    private byte[] readBytes(Workspace workspace, String fullPath) throws Exception {
        var entry = workspace.getIndex().findClass(fullPath);
        return entry != null ? entry.bytes() : null;
    }

    private boolean hasKind(InheritanceReferenceTree tree, Kind kind) {
        for (var g : tree.groups()) {
            if (g.kind() == kind && !g.children().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
