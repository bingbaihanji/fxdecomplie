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

        // 哈希应稳定（相同内容 → 相同哈希），且不受插入顺序影响
        String hash1 = DecompilerOptions.hash(first);
        String hash2 = DecompilerOptions.hash(second);
        assertEquals(hash1, hash2, "相同内容的哈希应不受插入顺序影响");
        assertEquals(16, hash1.length(), "SHA-256 截断哈希应为 16 字符");
        assertEquals("default", DecompilerOptions.hash(Map.of()));
        assertEquals("default", DecompilerOptions.hash(null));
    }
}
