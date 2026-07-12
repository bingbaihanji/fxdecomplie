package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.rename.RenameEntry;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClassNodeResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void packageTokenResolvesPackageNodeBeforeSameNamedClass() {
        Workspace workspace = workspaceWithPackages();

        FileTreeNode node = ClassNodeResolver.findPackageNodeForToken(workspace,
                "com.example.a", "com/example/a/Foo", """
                        package com.example.a;
                        public class Foo {}
                        """, 1);

        assertNotNull(node);
        assertEquals(FileTreeNode.NodeTypeEnum.PACKAGE, node.getNodeType());
        assertEquals("com/example/a", node.getFullPath());
    }

    @Test
    void renamedDisplayNameResolvesOriginalClassNode() {
        RenameService.setRootDir(tempDir);
        Workspace workspace = workspaceWithClass("com/example/Obf.class");
        String wsHash = CommentScope.workspaceHash(workspace);
        RenameService.save(wsHash, new RenameEntry("class",
                "com/example/Obf", "Obf", "Readable", ""));

        FileTreeNode node = ClassNodeResolver.findNodeForToken(workspace, WorkspaceIndex.EMPTY,
                "Readable", "com/example/Current", "package com.example;", true);

        assertNotNull(node);
        assertEquals("com/example/Obf.class", node.getFullPath());
    }

    private Workspace workspaceWithPackages() {
        FileTreeModel root = new FileTreeModel(new FileTreeNode("root", "",
                FileTreeNode.NodeTypeEnum.PACKAGE));
        FileTreeModel com = pkg("com", "com");
        FileTreeModel example = pkg("example", "com/example");
        FileTreeModel pkgA = pkg("a", "com/example/a");
        FileTreeModel classA = new FileTreeModel(new FileTreeNode("a.class",
                "com/example/a.class", FileTreeNode.NodeTypeEnum.CLASS_FILE));
        FileTreeModel foo = new FileTreeModel(new FileTreeNode("Foo.class",
                "com/example/a/Foo.class", FileTreeNode.NodeTypeEnum.CLASS_FILE));
        root.getChildren().add(com);
        com.getChildren().add(example);
        example.getChildren().add(pkgA);
        example.getChildren().add(classA);
        pkgA.getChildren().add(foo);
        return new Workspace("test", new File("."), root, false);
    }

    private Workspace workspaceWithClass(String fullPath) {
        FileTreeModel root = new FileTreeModel(new FileTreeNode("root", "",
                FileTreeNode.NodeTypeEnum.PACKAGE));
        root.getChildren().add(new FileTreeModel(new FileTreeNode("Obf.class",
                fullPath, FileTreeNode.NodeTypeEnum.CLASS_FILE)));
        return new Workspace("resolver-rename", new File("."), root, false);
    }

    private FileTreeModel pkg(String name, String path) {
        return new FileTreeModel(new FileTreeNode(name, path,
                FileTreeNode.NodeTypeEnum.PACKAGE));
    }
}
