package com.bingbaihanji.classgraph.metadata.query;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Guard clause 工具 — 消除散布的 null 检查和空集合处理。
 *
 * <p>提供安全的 null 处理包装器，减少调用方的防御性代码。</p>
 */
public final class MetadataGuards {

    private MetadataGuards() {}

    // ─── Null 安全值访问 ───

    /** 如果值为 null 则返回默认值 */
    public static <T> T requireNonNullElse(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }

    /** 返回 Optional 包裹的值 */
    public static <T> Optional<T> optionalOf(T value) {
        return Optional.ofNullable(value);
    }

    // ─── 集合安全访问 ───

    /** 返回非 null 的不可变列表 */
    public static <T> List<T> safeList(Collection<T> collection) {
        return collection == null ? List.of()
            : List.copyOf(collection);
    }

    /** 返回非 null 的 Stream */
    public static <T> Stream<T> safeStream(Collection<T> collection) {
        return collection == null ? Stream.empty()
            : collection.stream();
    }

    /** 检查集合是否为 null 或空 */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /** 检查集合是否非 null 且非空 */
    public static boolean isNotEmpty(Collection<?> collection) {
        return collection != null && !collection.isEmpty();
    }

    // ─── 条件执行 ───

    /** 如果值非 null 则执行消费操作 */
    public static <T> void ifPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    /** 如果值非 null 则映射，否则返回默认值 */
    public static <T, R> R mapOrElse(T value, Function<T, R> mapper, R defaultValue) {
        return value != null ? mapper.apply(value) : defaultValue;
    }

    /** 如果集合非空则映射，否则返回默认值 */
    public static <T, R> R mapNonEmpty(Collection<T> collection,
                                        Function<Collection<T>, R> mapper,
                                        Supplier<R> defaultSupplier) {
        return isNotEmpty(collection) ? mapper.apply(collection)
            : defaultSupplier.get();
    }

    // ─── 类名处理 ───

    /** 安全地获取简单类名（去掉包前缀） */
    public static String simpleName(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return "";
        }
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    /** 安全地获取包名 */
    public static String packageName(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return "";
        }
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(0, idx) : "";
    }

    // ─── ClassMetadataReader 专用 ───

    /** 创建包含单个读取器的查询服务 */
    public static MetadataQueryService queryServiceFor(
            ClassMetadataReader reader) {
        return new MetadataQueryService(
            Collections.singletonMap(reader.className(), reader));
    }

    /** 从 ClassMetadataReader 映射创建查询服务 */
    public static MetadataQueryService queryServiceFrom(
            Collection<ClassMetadataReader> readers) {
        java.util.Map<String, ClassMetadataReader> map = new java.util.LinkedHashMap<>();
        for (ClassMetadataReader r : safeList(readers)) {
            map.put(r.className(), r);
        }
        return new MetadataQueryService(map);
    }
}
