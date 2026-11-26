package com.bingbaihanji.fxdecomplie.service;

import java.util.Objects;

/**
 * 单个缓存分类的统计快照.
 *
 * @param name      分类名称
 * @param entries   条目/文件数
 * @param bytes     占用字节数（估算）
 * @param hitCount  命中次数
 * @param missCount 未命中次数
 */
public record CacheStats(String name, long entries, long bytes, long hitCount, long missCount) {

    public CacheStats {
        Objects.requireNonNull(name, "name");
    }
}
