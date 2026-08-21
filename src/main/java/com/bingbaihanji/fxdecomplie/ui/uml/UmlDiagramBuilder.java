package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从目标类出发 BFS 构建「聚焦」UML 类图（继承/实现层级）。
 *
 * <p>向上：父类链（GENERALIZATION）+ 接口及其父接口（REALIZATION），均带成员；
 * 向下：仅目标类的直接子类 / 目标接口的实现者，各带成员。
 * 不再展开字段关联（ASSOCIATION）、方法参数依赖（DEPENDENCY）与注解节点，避免图爆炸。
 * JDK 类型不建节点；解析不到的类建「仅类名」标记框。</p>
 */
public final class UmlDiagramBuilder {

    /** 类节点数量上限 */
    public static final int MAX_CLASSES = 100;
    /** 关系展开深度上限 */
    public static final int MAX_DEPTH = 20;

    private UmlDiagramBuilder() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建 UML 类图。
     *
     * @param targetInternalName 目标类内部名称（如 com/example/OrderServiceImpl）
     * @param targetBytes        目标类字节码，优先于索引读取
     * @param index              工作区索引，可为 {@link WorkspaceIndex#EMPTY}
     * @return 类图；目标不可解析返回 null
     */
    public static UmlDiagram build(String targetInternalName, byte[] targetBytes, WorkspaceIndex index) {
        if (targetInternalName == null || targetInternalName.isBlank()) {
            return null;
        }
        String target = normalize(targetInternalName);
        byte[] rootBytes = (targetBytes == null || targetBytes.length == 0)
                ? (index == null ? null : index.getClassBytes(target))
                : targetBytes;
        if (rootBytes == null || rootBytes.length == 0) {
            return null;
        }

        Map<String, UmlClass> byName = new LinkedHashMap<>();
        List<UmlRelation> relations = new ArrayList<>();
        Set<String> seenRelations = new LinkedHashSet<>();
        Set<String> queued = new LinkedHashSet<>();
        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        queued.add(target);
        depth.put(target, 0);
        queue.add(target);

        while (!queue.isEmpty() && byName.size() < MAX_CLASSES) {
            String name = queue.removeFirst();
            if (byName.containsKey(name)) {
                continue;
            }
            int d = depth.getOrDefault(name, 0);

            byte[] bytes = name.equals(target) && rootBytes != null
                    ? rootBytes
                    : (index == null ? null : index.getClassBytes(name));
            UmlClass uml = UmlClassExtractor.extract(name, bytes);
            if (uml == null) {
                byName.put(name, marker(name));
                continue;
            }
            byName.put(name, uml);
            if (d >= MAX_DEPTH) {
                continue; // 仅显示，不再向上/向下展开
            }

            // 向上：父类链（继承）
            if (uml.superName() != null && !UmlTypeNameUtil.isJdk(uml.superName())) {
                addRelation(relations, seenRelations, name, uml.superName(), RelationType.GENERALIZATION, null, null);
                enqueue(uml.superName(), d + 1, queued, queue, depth);
            }
            // 向上：接口（实现，含父接口链）
            for (String itf : uml.interfaces()) {
                if (UmlTypeNameUtil.isJdk(itf)) {
                    continue;
                }
                addRelation(relations, seenRelations, name, itf, RelationType.REALIZATION, null, null);
                enqueue(itf, d + 1, queued, queue, depth);
            }
            // 向下：仅目标类的直接子类/实现者
            if (index != null && d == 0) {
                for (String child : index.subclassesByParent().getOrDefault(name, List.of())) {
                    if (UmlTypeNameUtil.isJdk(child) || child.equals(name)) {
                        continue;
                    }
                    RelationType rt = (uml.kind() == ClassKind.INTERFACE || uml.kind() == ClassKind.ANNOTATION)
                            ? RelationType.REALIZATION
                            : RelationType.GENERALIZATION;
                    addRelation(relations, seenRelations, child, name, rt, null, null);
                    enqueue(child, d + 1, queued, queue, depth);
                }
            }
        }
        return new UmlDiagram(target, List.copyOf(byName.values()), List.copyOf(relations));
    }

    private static void addRelation(List<UmlRelation> relations, Set<String> seenRelations,
                                    String from, String to, RelationType type,
                                    String fromCardinality, String toCardinality) {
        String key = from + "|" + to + "|" + type + "|"
                + (fromCardinality == null ? "" : fromCardinality) + "|"
                + (toCardinality == null ? "" : toCardinality);
        if (seenRelations.add(key)) {
            relations.add(new UmlRelation(from, to, type, fromCardinality, toCardinality));
        }
    }

    private static void enqueue(String name, int d, Set<String> queued,
                                Deque<String> queue, Map<String, Integer> depth) {
        if (queued.add(name)) {
            depth.put(name, d);
            queue.add(name);
        }
    }

    /** 解析不到的类：仅类名标记框 */
    private static UmlClass marker(String internalName) {
        String simple = internalName;
        int idx = internalName.lastIndexOf('/');
        if (idx >= 0) {
            simple = internalName.substring(idx + 1);
        }
        return new UmlClass(internalName, "", simple, ClassKind.CLASS, false, false,
                List.of(), List.of(), null, List.of(), List.of());
    }

    /** 归一化为 JVM 内部名称：反斜杠转斜杠、点转斜杠（点分 FQCN → 内部名） */
    private static String normalize(String name) {
        return name.replace('\\', '/').replace('.', '/');
    }
}
