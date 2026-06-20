package com.bingbaihanji.fxdecomplie.service;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Map;

/**
 * Shared helpers for resolving decompiler-specific options and cache keys.
 */
public final class DecompilerOptions {

    private DecompilerOptions() {
        throw new AssertionError("utility class");
    }

    public static Map<String, String> forEngine(AppConfig appConfig,
                                                DecompilerTypeEnum engine) {
        if (appConfig == null || appConfig.decompiler().engineOptions() == null || engine == null) {
            return Map.of();
        }
        var options = appConfig.decompiler().engineOptions().get(engine.name());
        return options == null ? Map.of() : Map.copyOf(options);
    }

    public static String hash(Map<String, String> options) {
        if (options == null || options.isEmpty()) {
            return "default";
        }
        return options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining(","));
    }
}
