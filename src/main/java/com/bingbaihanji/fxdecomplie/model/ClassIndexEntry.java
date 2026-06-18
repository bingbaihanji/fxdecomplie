package com.bingbaihanji.fxdecomplie.model;

import java.util.List;
import java.util.Objects;

/**
 * Indexed class metadata for workspace-wide search and analysis.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ClassIndexEntry(
        String fullPath,
        String internalName,
        String simpleName,
        byte[] bytes,
        List<MemberIndexEntry> methods,
        List<MemberIndexEntry> fields,
        String bytecodeText
) {

    public ClassIndexEntry {
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(simpleName, "simpleName");
        Objects.requireNonNull(bytes, "bytes");
        methods = List.copyOf(methods);
        fields = List.copyOf(fields);
        bytecodeText = bytecodeText == null ? "" : bytecodeText;
    }
}
