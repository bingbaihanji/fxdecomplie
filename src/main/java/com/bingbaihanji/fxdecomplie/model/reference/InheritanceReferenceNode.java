package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.Objects;

public record InheritanceReferenceNode(
        String className,
        String displayName,
        Kind kind,
        String fullPath,
        int depth,
        boolean navigable
) {
    public InheritanceReferenceNode {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
    }
}
