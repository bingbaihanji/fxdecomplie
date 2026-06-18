package com.bingbihanji.fxdecomplie.service;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DecompileCacheTest {

    @Test
    void separatesSameClassAcrossWorkspaces() {
        DecompileCache cache = new DecompileCache();

        cache.put("workspace-a", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-a");
        cache.put("workspace-b", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-b");

        assertEquals("source-a", cache.get("workspace-a", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
        assertEquals("source-b", cache.get("workspace-b", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
    }

    @Test
    void invalidatesOnlyTargetWorkspaceClass() {
        DecompileCache cache = new DecompileCache();
        cache.put("workspace-a", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-a");
        cache.put("workspace-b", "com/example/App", DecompilerTypeEnum.CFR,
                "default", "source-b");

        cache.invalidate("workspace-a", "com/example/App");

        assertNull(cache.get("workspace-a", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
        assertEquals("source-b", cache.get("workspace-b", "com/example/App",
                DecompilerTypeEnum.CFR, "default"));
    }
}
