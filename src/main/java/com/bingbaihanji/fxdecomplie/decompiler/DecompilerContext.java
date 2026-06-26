package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 每次反编译的上下文,通过 WorkspaceIndex 按工作区隔离地解析依赖类字节码
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class DecompilerContext implements AutoCloseable {

    public static final DecompilerContext EMPTY = new DecompilerContext(
            internalName -> null, Map.of());

    private final ClassBytecodeProvider bytecodeProvider;
    private final Map<String, String> options;
    private final AutoCloseable closeable;
    private final boolean closeAfterUse;
    private final AtomicBoolean closed = new AtomicBoolean();

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider,
                              Map<String, String> options) {
        this(bytecodeProvider, options, null, false);
    }

    private DecompilerContext(ClassBytecodeProvider bytecodeProvider,
                              Map<String, String> options,
                              AutoCloseable closeable,
                              boolean closeAfterUse) {
        this.bytecodeProvider = Objects.requireNonNull(bytecodeProvider, "bytecodeProvider");
        this.options = options == null ? Map.of() : Map.copyOf(options);
        this.closeable = closeable;
        this.closeAfterUse = closeAfterUse;
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider) {
        return of(bytecodeProvider, Map.of());
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                       Map<String, String> options) {
        return of(bytecodeProvider, options, null);
    }

    public static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                       Map<String, String> options,
                                       AutoCloseable closeable) {
        return of(bytecodeProvider, options, closeable, true);
    }

    public static DecompilerContext singleUse(ClassBytecodeProvider bytecodeProvider,
                                              Map<String, String> options,
                                              AutoCloseable closeable) {
        return of(bytecodeProvider, options, closeable, true);
    }

    private static DecompilerContext of(ClassBytecodeProvider bytecodeProvider,
                                        Map<String, String> options,
                                        AutoCloseable closeable,
                                        boolean closeAfterUse) {
        if (bytecodeProvider == null) {
            if (closeable != null) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "bytecodeProvider 为 null 时无法持有 closeable", e);
                }
            }
            return withOptions(options);
        }
        return new DecompilerContext(bytecodeProvider, options, closeable, closeAfterUse);
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
        return new DecompilerContext(internalName -> null, options);
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
        return getClassBytes(internalName);
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

    public boolean closeAfterUse() {
        return closeAfterUse;
    }

    @Override
    public void close() throws Exception {
        if (closeAfterUse && closeable != null && closed.compareAndSet(false, true)) {
            closeable.close();
        }
    }
}
