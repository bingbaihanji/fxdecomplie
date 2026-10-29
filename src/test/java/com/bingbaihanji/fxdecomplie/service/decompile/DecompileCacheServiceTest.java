package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DecompileCacheServiceTest {

    private static CodeCacheKey key(String workspace) {
        return new CodeCacheKey(workspace, "fp", "com/example/A",
                DecompilerTypeEnum.CFR, "default", 1);
    }

    /** 同步记录型 L3 */
    static final class MapStore implements CodeCacheStore {
        final Map<CodeCacheKey, String> data = new HashMap<>();
        int loads;

        @Override
        public Optional<String> load(CodeCacheKey key) {
            loads++;
            return Optional.ofNullable(data.get(key));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            data.put(key, source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            data.remove(key);
        }
    }

    @Test
    void l2HitSkipsL3() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);
        l2.save(key("w"), "l2-source");

        assertEquals("l2-source", service.load(key("w")).orElse(null));
        assertEquals(0, l3.loads);
    }

    @Test
    void l3HitRefillsL2() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        l3.data.put(key("w"), "l3-source");
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);

        assertEquals("l3-source", service.load(key("w")).orElse(null));
        assertEquals("l3-source", l2.load(key("w")).orElse(null));
    }

    @Test
    void saveWritesL2ImmediatelyAndL3ThroughExecutor() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);

        service.save(key("w"), "source");

        assertEquals("source", l2.load(key("w")).orElse(null));
        assertEquals("source", l3.data.get(key("w")));
    }

    @Test
    void invalidateHitsBothLevels() {
        DecompileCache l2 = new DecompileCache();
        MapStore l3 = new MapStore();
        DecompileCacheService service = new DecompileCacheService(l2, l3, Runnable::run);
        service.save(key("w"), "source");

        service.invalidate(key("w"), CodeCacheScope.CLASS);

        assertNull(l2.load(key("w")).orElse(null));
        assertNull(l3.data.get(key("w")));
    }
}
