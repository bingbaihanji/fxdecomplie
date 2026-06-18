package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.model.*;
import javafx.scene.control.TreeItem;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

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
        TreeItem<FileTreeNode> root = new TreeItem<>(
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
