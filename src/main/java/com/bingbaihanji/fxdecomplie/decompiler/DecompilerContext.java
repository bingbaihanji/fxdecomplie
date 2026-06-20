package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.Map;
import java.util.Objects;

/**
 * 每次反编译的上下文在解析依赖类时，应优先使用此上下文而非旧版全局 BytecodeCache
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DecompilerContext {

    public static final DecompilerContext EMPTY = new DecompilerContext(
            internalName -> null, Map.of(), false);
    public static final DecompilerContext LEGACY_GLOBAL = new DecompilerContext(
            internalName -> null, Map.of(), true);

    private final ClassBytecodeProvider bytecodeProvider;
    private final Map<String, String> options;
    private final boolean globalFallbackEnabled;

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider,
                              Map<String, String> options,
                              boolean globalFallbackEnabled) {
        this.bytecodeProvider = Objects.requireNonNull(bytecodeProvider, "bytecodeProvider");
        this.options = options == null ? Map.of() : Map.copyOf(options);
        this.globalFallbackEnabled = globalFallbackEnabled;
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider) {
        return of(bytecodeProvider, Map.of());
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                       Map<String, String> options) {
        return bytecodeProvider == null
                ? withOptions(options)
                : new DecompilerContext(bytecodeProvider, options, false);
    }

    public static DecompilerContext fromWorkspaceIndex(WorkspaceIndex index) {
        return fromWorkspaceIndex(index, Map.of());
    }

    public static DecompilerContext fromWorkspaceIndex(WorkspaceIndex index,
                                                       Map<String, String> options) {
        if (index == null) {
            return withOptions(options);
        }
        return of(index::getClassBytes, options);
    }

    public static DecompilerContext withOptions(Map<String, String> options) {
        return new DecompilerContext(internalName -> null, options, false);
    }

    public static DecompilerContext legacyGlobalWithOptions(Map<String, String> options) {
        return new DecompilerContext(internalName -> null, options, true);
    }

    public static String normalizeInternalName(String internalName) {
        String normalized = internalName.replace('\\', '/');
        return normalized.endsWith(".class")
                ? normalized.substring(0, normalized.length() - 6)
                : normalized;
    }

    /** 从内部名称中提取简单类名(例如 "com/example/Foo" → "Foo") */
    public static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    public byte[] getClassBytes(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return null;
        }
        String normalized = normalizeInternalName(internalName);
        return bytecodeProvider.getClassBytes(normalized);
    }

    public byte[] resolveClassBytes(String internalName) {
        byte[] bytes = getClassBytes(internalName);
        if (bytes != null) {
            return bytes;
        }
        return globalFallbackEnabled ? BytecodeCache.get(normalizeInternalName(internalName)) : null;
    }

    public Map<String, String> options() {
        return options;
    }

    public String option(String key, String defaultValue) {
        return options.getOrDefault(key, defaultValue);
    }

    public boolean hasOptions() {
        return !options.isEmpty();
    }
}
