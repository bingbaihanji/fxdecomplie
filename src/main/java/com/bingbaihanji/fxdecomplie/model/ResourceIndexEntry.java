package com.bingbaihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * 索引化的资源元数据
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ResourceIndexEntry(String fullPath, byte[] bytes, boolean text) {

    public ResourceIndexEntry {
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(bytes, "bytes");
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
