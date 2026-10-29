package com.bingbaihanji.fxdecomplie.service;

/**
 * 缓存预算与淘汰参数集中定义.
 *
 * @author bingbaihanji
 * @date 2026-10-29
 */
public final class CacheLimits {

    public static final long L2_BYTES = 50L * 1024 * 1024;
    public static final long L3_BYTES = 500L * 1024 * 1024;
    public static final long INDEX_BYTES = 200L * 1024 * 1024;
    public static final long SNAPSHOT_BYTES = 256L * 1024 * 1024;
    public static final double EVICTION_TARGET_RATIO = 0.7d;
    public static final long QUOTA_CHECK_INTERVAL_NANOS = 60_000_000_000L;
    public static final int QUOTA_WRITE_THRESHOLD = 256;

    private CacheLimits() {
        throw new AssertionError("utility class");
    }
}
