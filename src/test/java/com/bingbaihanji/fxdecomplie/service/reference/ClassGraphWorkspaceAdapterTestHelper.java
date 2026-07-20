package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

/**
 * 测试辅助：从编译后的字节码构建 Workspace
 */
final class ClassGraphWorkspaceAdapterTestHelper {
    private ClassGraphWorkspaceAdapterTestHelper() {}

    static Workspace buildWorkspace(File dir, Map<String, String> sources) throws Exception {
        Map<String, byte[]> compiled = TestClassCompiler.compile(sources);
        for (Map.Entry<String, byte[]> e : compiled.entrySet()) {
            File f = new File(dir, e.getKey());
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), e.getValue());
        }

        FileTreeNode rootNode = new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootNode);
        for (Map.Entry<String, byte[]> e : compiled.entrySet()) {
            FileTreeNode node = new FileTreeNode(
                    e.getKey().substring(e.getKey().lastIndexOf('/') + 1),
                    e.getKey(), FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setByteLoader(() -> e.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }

        Workspace workspace = new Workspace("test", dir, root, false);
        workspace.setIndex(WorkspaceIndex.build(root));
        return workspace;
    }
}
