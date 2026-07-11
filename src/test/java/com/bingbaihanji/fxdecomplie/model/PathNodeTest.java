package com.bingbaihanji.fxdecomplie.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertSame;

class PathNodeTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesAncestorValuesByType() {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        Workspace workspace = new Workspace("test", tempDir.toFile(), root, false);
        WorkspacePathNode workspacePath = new WorkspacePathNode(workspace);
        FileTreeNode cls = new FileTreeNode("Demo.class", "com/example/Demo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        ClassPathNode classPath = new ClassPathNode(workspacePath, cls);

        assertSame(workspace, classPath.getValueOfType(Workspace.class));
        assertSame(cls, classPath.getValueOfType(FileTreeNode.class));
    }
}
