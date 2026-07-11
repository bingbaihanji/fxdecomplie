package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import java.util.Map;

/**
 * 项目层 jadx 适配选项。
 * <p>
 * 这些选项不直接映射到 {@code JadxArgs},而是控制 fxdecomplie 如何向 jadx 内核提供输入。
 */
public record JadxAdapterOptions(
        boolean loadWorkspaceDependencies,
        int workspaceDependencyLimit,
        int workspaceDependencyDepth
) {

    public static final String LOAD_WORKSPACE_DEPENDENCIES = "loadWorkspaceDependencies";
    public static final String WORKSPACE_DEPENDENCY_LIMIT = "workspaceDependencyLimit";
    public static final String WORKSPACE_DEPENDENCY_DEPTH = "workspaceDependencyDepth";

    private static final int DEFAULT_DEPENDENCY_LIMIT = 96;
    private static final int DEFAULT_DEPENDENCY_DEPTH = 1;
    private static final int MAX_DEPENDENCY_LIMIT = 512;
    private static final int MAX_DEPENDENCY_DEPTH = 3;

    public static JadxAdapterOptions from(Map<String, String> options) {
        return new JadxAdapterOptions(
                bool(options, LOAD_WORKSPACE_DEPENDENCIES, true),
                intRange(options, WORKSPACE_DEPENDENCY_LIMIT,
                        DEFAULT_DEPENDENCY_LIMIT, 0, MAX_DEPENDENCY_LIMIT),
                intRange(options, WORKSPACE_DEPENDENCY_DEPTH,
                        DEFAULT_DEPENDENCY_DEPTH, 0, MAX_DEPENDENCY_DEPTH));
    }

    private static boolean bool(Map<String, String> options, String key, boolean defaultValue) {
        if (options == null) {
            return defaultValue;
        }
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    private static int intRange(Map<String, String> options, String key,
                                int defaultValue, int min, int max) {
        if (options == null) {
            return defaultValue;
        }
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return Math.clamp(parsed, min, max);
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}
