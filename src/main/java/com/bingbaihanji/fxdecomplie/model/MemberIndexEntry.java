package com.bingbaihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * 索引化的类成员元数据
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
