package com.bingbaihanji.fxdecomplie.model;

import java.util.List;
import java.util.Map;

/**
 * 代码元数据 — 记录每行的引用信息，支持 Ctrl+Click 导航。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CodeMetadata {

    /** Line number (1-based) to list of navigable references found on that line */
    private final Map<Integer, List<Reference>> refsByLine;

    public CodeMetadata(Map<Integer, List<Reference>> refsByLine) {
        this.refsByLine = refsByLine;
    }

    /** @param lineNumber 1-based line number
     *  @return all references on the given line, or empty list */
    public List<Reference> getRefsAtLine(int lineNumber) {
        return refsByLine.getOrDefault(lineNumber, List.of());
    }

    /** @return true if no references were extracted */
    public boolean isEmpty() {
        return refsByLine.isEmpty();
    }

    /** Type of navigable reference found in source code */
    public enum RefType {CLASS_REF, METHOD_REF, FIELD_REF}

    /**
     * A single navigable reference in decompiled source.
     *
     * @param type         type of reference
     * @param targetClass  fully qualified target class name (e.g. "com.example.Foo")
     * @param targetMember target member name, or null for class-only refs
     * @param lineNumber   1-based line number in the source
     */
    public record Reference(RefType type, String targetClass, String targetMember, int lineNumber) {
    }
}
