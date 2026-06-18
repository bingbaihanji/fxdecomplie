package com.bingbihanji.fxdecomplie.decompiler;

import com.bingbihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.Objects;

/**
 * Per-decompilation context. Prefer this over the legacy global BytecodeCache
 * when resolving dependency classes.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DecompilerContext {

    public static final DecompilerContext EMPTY = new DecompilerContext(internalName -> null);

    private final ClassBytecodeProvider bytecodeProvider;

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider) {
        this.bytecodeProvider = Objects.requireNonNull(bytecodeProvider, "bytecodeProvider");
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider) {
        return bytecodeProvider == null ? EMPTY : new DecompilerContext(bytecodeProvider);
    }

    public static DecompilerContext fromWorkspaceIndex(WorkspaceIndex index) {
        if (index == null) {
            return EMPTY;
        }
        return of(index::getClassBytes);
    }

    private static String normalizeInternalName(String internalName) {
        String normalized = internalName.replace('\\', '/');
        return normalized.endsWith(".class")
                ? normalized.substring(0, normalized.length() - 6)
                : normalized;
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
        return BytecodeCache.get(normalizeInternalName(internalName));
    }
}
