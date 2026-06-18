package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DecompilerFactoryTest {

    @Test
    void returnsSameInstanceForSameType() {
        Decompiler d1 = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        Decompiler d2 = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        assertSame(d1, d2);
    }

    @Test
    void returnsDifferentInstancesForDifferentTypes() {
        Decompiler cfr = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        Decompiler procyon = DecompilerFactory.getDecompiler(DecompilerTypeEnum.PROCYON);
        assertNotSame(cfr, procyon);
    }

    @Test
    void allEngineTypesReturnNonNull() {
        for (DecompilerTypeEnum type : DecompilerTypeEnum.values()) {
            Decompiler d = DecompilerFactory.getDecompiler(type);
            assertNotNull(d, "Engine should not be null: " + type);
            assertEquals(type, d.getType());
        }
    }

    @Test
    void cleanupDoesNotThrow() {
        // Pre-populate the cache
        DecompilerFactory.getDecompiler(DecompilerTypeEnum.VINEFLOWER);
        assertDoesNotThrow(DecompilerFactory::cleanup);
    }
}
