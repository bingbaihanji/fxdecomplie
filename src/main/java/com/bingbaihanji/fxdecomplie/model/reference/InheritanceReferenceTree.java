package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.List;
import java.util.Objects;

public record InheritanceReferenceTree(
        InheritanceReferenceNode root,
        List<InheritanceReferenceGroup> groups,
        boolean partial,
        String statusMessage
) {
    public InheritanceReferenceTree {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(groups, "groups");
        groups = List.copyOf(groups);
    }
}
