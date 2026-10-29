package com.bingbaihanji.fxdecomplie.service.decompile;

import java.util.Optional;

/**
 * 代码缓存存储抽象 — L2(内存)和 L3(磁盘)的统一接口.
 *
 * @author bingbaihanji
 * @date 2026-07-26
 */
public interface CodeCacheStore {

    /** 从缓存加载反编译源码 */
    Optional<String> load(CodeCacheKey key);

    /**
     * 将反编译源码存入缓存.
     *
     * <p>{@code source} 为 {@code null} 或空白字符串时忽略该次写入 — 空白源码不是有效缓存值,
     * L2 与 L3 实现必须保持一致.</p>
     */
    void save(CodeCacheKey key, String source);

    /** 按范围失效缓存 */
    void invalidate(CodeCacheKey key, CodeCacheScope scope);

    /** 清空该层全部条目；默认无操作，供缓存管理入口调用 */
    default void clearAll() {
    }
}
