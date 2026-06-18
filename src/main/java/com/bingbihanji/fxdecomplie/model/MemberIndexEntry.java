package com.bingbihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * Indexed class member metadata.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record MemberIndexEntry(String ownerPath, String name, String descriptor) {

    public MemberIndexEntry {
        Objects.requireNonNull(ownerPath, "ownerPath");
        Objects.requireNonNull(name, "name");
        descriptor = descriptor == null ? "" : descriptor;
    }

    public String displayName() {
        return descriptor.isBlank() ? name : name + descriptor;
    }
}
