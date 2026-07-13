package com.bingbaihanji.javafx.cache;


import com.bingbaihanji.fxdecomplie.decompiler.VineflowerDecompiler;
import com.bingbaihanji.fxdecomplie.util.cache.LruCache;
import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 *
 * @author bingbaihanji
 * @date 2026-07-13 09:03:33
 * @description //TODO
 */
public class CacheTest {
    @Test
    public void cache() {
        VineflowerDecompiler vineflowerDecompiler = new VineflowerDecompiler();
        LruCache<String, Object> cache = new LruCache<>(500);

        Map<String, Object> map = new ArrayMap<>();
        map.put("1", 1);
        map.put("2", 2);


        cache.set("12", vineflowerDecompiler);

        cache.set("Hello", "world");
        cache.set("map", map);


        System.out.println(cache);


        Object map1 = cache.get("map");

        if (map1 instanceof ArrayMap<?, ?> arrayMap) {
            arrayMap.forEach((key, value) -> System.out.println(key + " = " + value));

        }

    }
}
