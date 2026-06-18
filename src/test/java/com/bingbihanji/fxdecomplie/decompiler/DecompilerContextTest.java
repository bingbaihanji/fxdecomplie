package com.bingbihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class DecompilerContextTest {

    @AfterEach
    void clearGlobalCache() {
        BytecodeCache.clear();
    }

    @Test
    void resolvesWorkspaceBytesBeforeGlobalCache() {
        byte[] workspaceBytes = {1, 2, 3};
        byte[] globalBytes = {9, 9, 9};
        BytecodeCache.put("com/example/Demo", globalBytes);

        DecompilerContext context = DecompilerContext.of(internalName ->
                "com/example/Demo".equals(internalName) ? workspaceBytes : null);

        assertArrayEquals(workspaceBytes, context.resolveClassBytes("com/example/Demo.class"));
    }

    @Test
    void fallsBackToGlobalCacheForLegacyResolution() {
        byte[] globalBytes = {4, 5, 6};
        BytecodeCache.put("com/example/Legacy", globalBytes);

        assertArrayEquals(globalBytes,
                DecompilerContext.EMPTY.resolveClassBytes("com/example/Legacy.class"));
    }
}
