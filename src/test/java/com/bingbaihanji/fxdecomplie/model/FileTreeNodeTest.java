package com.bingbaihanji.fxdecomplie.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FileTreeNodeTest {

    @Test
    void identifiesClassFile() {
        FileTreeNode node = new FileTreeNode("Test.class", "com/example/Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        assertTrue(node.isClassFile());
        assertFalse(node.isTextFile());
        assertEquals("Test.class", node.getName());
        assertEquals("com/example/Test.class", node.getFullPath());
    }

    @Test
    void identifiesJavaFile() {
        FileTreeNode node = new FileTreeNode("Main.java", "Main.java",
                FileTreeNode.NodeTypeEnum.JAVA_FILE);
        assertTrue(node.isTextFile());
        assertFalse(node.isClassFile());
    }

    @Test
    void identifiesResourceFile() {
        FileTreeNode node = new FileTreeNode("config.xml", "config.xml",
                FileTreeNode.NodeTypeEnum.RESOURCE);
        assertTrue(node.isTextFile());
    }

    @Test
    void cachedBytesRoundTrip() {
        FileTreeNode node = new FileTreeNode("Test.class", "Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        byte[] data = {1, 2, 3, 4};
        node.setCachedBytes(data);
        assertArrayEquals(data, node.getCachedBytes());
    }

    @Test
    void resolveBytesLoadsAndCachesLazySource() throws Exception {
        FileTreeNode node = new FileTreeNode("Test.class", "Test.class",
                FileTreeNode.NodeTypeEnum.CLASS_FILE);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();
        node.setByteLoader(() -> {
            reads.incrementAndGet();
            return new byte[]{5, 6, 7};
        });

        assertTrue(node.hasByteSource());
        assertArrayEquals(new byte[]{5, 6, 7}, node.resolveBytes());
        assertArrayEquals(new byte[]{5, 6, 7}, node.resolveBytes());
        assertEquals(1, reads.get());
    }
}
