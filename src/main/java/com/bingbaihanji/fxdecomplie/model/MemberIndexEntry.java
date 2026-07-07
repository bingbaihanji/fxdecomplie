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

    /** @return 用于 UI 展示的成员名称,若描述符为空则仅返回名称,否则拼接描述符 */
    public String displayName() {
        return descriptor.isBlank() ? name : name + descriptor;
    }
}
