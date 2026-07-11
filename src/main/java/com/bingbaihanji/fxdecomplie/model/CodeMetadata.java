package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;

import java.util.List;
import java.util.Map;

/**
 * 代码元数据 — 记录每行的引用信息,支持 Ctrl+Click 导航
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CodeMetadata {

    /** 行号(从1开始)到该行上所有可导航引用的映射 */
    private final Map<Integer, List<Reference>> refsByLine;

    public CodeMetadata(Map<Integer, List<Reference>> refsByLine) {
        // 深拷贝：Map 和每个 List 均不可变,防止外部修改破坏内部状态
        Map<Integer, List<Reference>> copy = new ArrayMap<>(refsByLine.size());
        for (var entry : refsByLine.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        this.refsByLine = Map.copyOf(copy);
    }

    /** @param lineNumber 从1开始的行号
     *  @return 该行上所有引用,无引用时返回空列表 */
    public List<Reference> getRefsAtLine(int lineNumber) {
        return refsByLine.getOrDefault(lineNumber, List.of());
    }

    /** @return 如果没有提取到任何引用,返回 true */
    public boolean isEmpty() {
        return refsByLine.isEmpty();
    }

    /** @return 所有行号到引用列表的映射(不可变视图) */
    public Map<Integer, List<Reference>> getAllRefsByLine() {
        return refsByLine;
    }

    /** 反编译源码中发现的可导航引用类型 */
    public enum RefType {CLASS_REF, METHOD_REF, FIELD_REF}

    /**
     * 反编译源码中的单个可导航引用
     *
     * @param type         引用类型
     * @param targetClass  完全限定目标类名(例如 "com.example.Foo")
     * @param targetMember 目标成员名,纯类引用时为 null
     * @param lineNumber   源码中从 1 开始的行号
     * @param columnStart  引用在行中的起始列号(从 0 开始,-1 表示未知)
     * @author bingbaihanji
     * @date 2026-06-18
     */
    public record Reference(RefType type, String targetClass, String targetMember,
                            int lineNumber, int columnStart) {

        /** 向后兼容的构造器(列号未知) */
        public Reference(RefType type, String targetClass, String targetMember, int lineNumber) {
            this(type, targetClass, targetMember, lineNumber, -1);
        }
    }
}
