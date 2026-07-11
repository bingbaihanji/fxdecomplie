package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.PathNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NavigationServiceTest {

    @Test
    void initiallyCannotGoBackOrForward() {
        NavigationService nav = new NavigationService();
        assertFalse(nav.canGoBack());
        assertFalse(nav.canGoForward());
    }

    @Test
    void classPathCreatesValidPathNode() {
        NavigationService nav = new NavigationService();
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        Workspace ws = new Workspace("test", new java.io.File("."), root, false);
        FileTreeNode node = new FileTreeNode("Test.class", "Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);

        PathNode<?> path = nav.classPath(ws, node);
        assertNotNull(path);
        assertNotNull(path.getValueOfType(Workspace.class));
        assertNotNull(path.getValueOfType(FileTreeNode.class));
    }

    @Test
    void backForwardStacksEmptyInitially() {
        NavigationService nav = new NavigationService();
        assertFalse(nav.canGoBack());
        assertFalse(nav.canGoForward());
    }
}
