package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 项目级扫描结果 — 不暴露 ClassGraph 内部 ScanResult。
 *
 * @author bingbaihanji
 */
public final class ClassScanResult {

    private final Map<String, ClassMetadata> classes;
    private final ScanStatistics statistics;

    public ClassScanResult(Map<String, ClassMetadata> classes, ScanStatistics statistics) {
        this.classes = Collections.unmodifiableMap(
            new LinkedHashMap<>(classes != null ? classes : Map.of())
        );
        this.statistics = statistics != null ? statistics
            : new ScanStatistics(classes != null ? classes.size() : 0, 0, 0, 0, 0);
    }

    public ClassScanResult(Map<String, ClassMetadata> classes) {
        this(classes, new ScanStatistics(classes != null ? classes.size() : 0, 0, 0, 0, 0));
    }

    public Optional<ClassMetadata> getClass(String internalName) {
        return Optional.ofNullable(classes.get(internalName));
    }

    public ClassMetadata getClassOrThrow(String internalName) {
        ClassMetadata c = classes.get(internalName);
        if (c == null) {
            throw new IllegalArgumentException("Class not found: " + internalName);
        }
        return c;
    }

    public boolean containsClass(String internalName) {
        return classes.containsKey(internalName);
    }

    public List<ClassMetadata> getAllClasses() {
        return List.copyOf(classes.values());
    }

    public int size() {
        return classes.size();
    }

    public ScanStatistics statistics() {
        return statistics;
    }

    public Map<String, ClassMetadata> classMap() {
        return classes;
    }
}
