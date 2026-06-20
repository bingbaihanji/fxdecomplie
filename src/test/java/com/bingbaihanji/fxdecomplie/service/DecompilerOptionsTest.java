package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecompilerOptionsTest {

    @Test
    void resolvesImmutableEngineOptions() {
        AppConfig config = new AppConfig();
        config.decompiler().engineOptions().put(DecompilerTypeEnum.CFR.name(),
                new LinkedHashMap<>(Map.of("decodeenumswitch", "true")));

        Map<String, String> options = DecompilerOptions.forEngine(config, DecompilerTypeEnum.CFR);

        assertEquals("true", options.get("decodeenumswitch"));
        assertThrows(UnsupportedOperationException.class,
                () -> options.put("new", "value"));
    }

    @Test
    void hashIsStableAcrossMapOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("b", "2");
        first.put("a", "1");
        Map<String, String> second = new LinkedHashMap<>();
        second.put("a", "1");
        second.put("b", "2");

        assertEquals("a=1,b=2", DecompilerOptions.hash(first));
        assertEquals(DecompilerOptions.hash(first), DecompilerOptions.hash(second));
        assertEquals("default", DecompilerOptions.hash(Map.of()));
    }
}
