package com.bingbihanji.fxdecomplie.navigation;

import java.util.List;
import java.util.Map;

/**
 * 代码元数据 — 记录每行的引用信息，支持 Ctrl+Click 导航。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class CodeMetadata {

    public enum RefType { CLASS_REF, METHOD_REF, FIELD_REF }

    public record Reference(RefType type, String targetClass, String targetMember, int lineNumber) {}

    private final Map<Integer, List<Reference>> refsByLine;

    public CodeMetadata(Map<Integer, List<Reference>> refsByLine) {
        this.refsByLine = refsByLine;
    }

    public List<Reference> getRefsAtLine(int lineNumber) {
        return refsByLine.getOrDefault(lineNumber, List.of());
    }

    public boolean isEmpty() {
        return refsByLine.isEmpty();
    }
}
