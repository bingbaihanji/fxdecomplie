package com.bingbihanji.fxdecomplie.model;

import java.util.Objects;

/**
 * Indexed resource metadata.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record ResourceIndexEntry(String fullPath, byte[] bytes, boolean text) {

    public ResourceIndexEntry {
        Objects.requireNonNull(fullPath, "fullPath");
        Objects.requireNonNull(bytes, "bytes");
    }
}
