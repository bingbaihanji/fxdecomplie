package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WorkspaceClassBytecodeProviderTest {

    @TempDir
    Path tempDir;

    private static Workspace workspaceWithClass(FileTreeNode classNode, byte[] bytes) {
        classNode.setByteLoader(() -> bytes);
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(classNode));
        return new Workspace("demo", Path.of(".").toFile(), root, false);
    }

    @Test
    void resolvesClassBytesFromWorkspaceTree() {
        byte[] bytes = {1, 2, 3, 4};
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        Workspace workspace = workspaceWithClass(node, bytes);
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        assertArrayEquals(bytes, provider.getClassBytes("com/example/Foo"));
        assertNotNull(node.getCachedBytes(), "provider 应把字节缓存到节点");
    }

    @Test
    void returnsNullForMissingClassAndRemembersMiss() {
        FileTreeNode node = new FileTreeNode("Foo.class", "com/example/Foo.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        Workspace workspace = workspaceWithClass(node, new byte[]{1});
        WorkspaceClassBytecodeProvider provider = new WorkspaceClassBytecodeProvider(workspace);

        assertNull(provider.getClassBytes("com/example/Missing"));
        assertNull(provider.getClassBytes("com/example/Missing"));
    }
}
