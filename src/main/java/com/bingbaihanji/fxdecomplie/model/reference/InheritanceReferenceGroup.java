package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.List;
import java.util.Objects;

public record InheritanceReferenceGroup(
        Kind kind,
        String title,
        List<InheritanceReferenceNode> children,
        boolean collapsible
) {
    public InheritanceReferenceGroup {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
