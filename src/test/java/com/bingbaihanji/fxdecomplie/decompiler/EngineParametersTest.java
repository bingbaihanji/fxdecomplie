package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineParametersTest {

    @Test
    void forTypeReturnsPerEngineList() {
        assertSame(CfrParameters.PARAMETERS, EngineParameters.forType(DecompilerTypeEnum.CFR));
        assertSame(ProcyonParameters.PARAMETERS, EngineParameters.forType(DecompilerTypeEnum.PROCYON));
        assertSame(VineflowerParameters.PARAMETERS, EngineParameters.forType(DecompilerTypeEnum.VINEFLOWER));
        assertSame(JadxParameters.PARAMETERS, EngineParameters.forType(DecompilerTypeEnum.JADX));
        assertSame(JdParameters.PARAMETERS, EngineParameters.forType(DecompilerTypeEnum.JD));
    }

    @Test
    void forTypeNeverNullForAnyEnum() {
        for (DecompilerTypeEnum type : DecompilerTypeEnum.values()) {
            assertNotNull(EngineParameters.forType(type), "forType 不应返回 null: " + type);
        }
    }

    @Test
    void jdReturnsEmptyList() {
        assertTrue(EngineParameters.forType(DecompilerTypeEnum.JD).isEmpty());
    }
}
