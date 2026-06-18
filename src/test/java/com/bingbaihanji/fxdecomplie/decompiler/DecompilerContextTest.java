package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void emptyContextDoesNotFallbackToGlobalCache() {
        byte[] globalBytes = {4, 5, 6};
        BytecodeCache.put("com/example/Legacy", globalBytes);

        assertNull(DecompilerContext.EMPTY.resolveClassBytes("com/example/Legacy.class"));
    }

    @Test
    void legacyContextCanFallbackToGlobalCache() {
        byte[] globalBytes = {4, 5, 6};
        BytecodeCache.put("com/example/Legacy", globalBytes);

        assertArrayEquals(globalBytes, DecompilerContext.LEGACY_GLOBAL
                .resolveClassBytes("com/example/Legacy.class"));
    }

    @Test
    void carriesImmutableDecompilerOptions() {
        Map<String, String> options = new java.util.HashMap<>();
        options.put("silent", "true");
        DecompilerContext context = DecompilerContext.withOptions(options);
        options.put("silent", "false");

        assertEquals("true", context.option("silent", "false"));
    }
}
