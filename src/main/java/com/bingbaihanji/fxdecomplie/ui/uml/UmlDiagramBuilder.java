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
 * 从目标类出发 BFS 构建 UML 类图闭包。
 *
 * <p>展开方向：父类（GENERALIZATION）、接口（REALIZATION）、字段元素类型（ASSOCIATION，
 * 集合/数组带 0..* 多重性）、方法参数元素类型（DEPENDENCY）、子类（反向 GENERALIZATION）。
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
                continue; // 仅显示，不再展开关系
            }

            // 继承：父类
            if (uml.superName() != null && !UmlTypeNameUtil.isJdk(uml.superName())) {
                addRelation(relations, seenRelations, name, uml.superName(), RelationType.GENERALIZATION, null, null);
                enqueue(uml.superName(), d + 1, queued, queue, depth);
            }
            // 实现：接口
            for (String itf : uml.interfaces()) {
                if (UmlTypeNameUtil.isJdk(itf)) {
                    continue;
                }
                addRelation(relations, seenRelations, name, itf, RelationType.REALIZATION, null, null);
                enqueue(itf, d + 1, queued, queue, depth);
            }
            // 关联：字段元素类型
            for (UmlField f : uml.fields()) {
                addTypeRelation(relations, seenRelations, queue, queued, depth, name, d,
                        f.type(), RelationType.ASSOCIATION, true);
            }
            // 依赖：方法参数元素类型
            for (UmlMethod m : uml.methods()) {
                for (String p : m.params()) {
                    addTypeRelation(relations, seenRelations, queue, queued, depth, name, d,
                            p, RelationType.DEPENDENCY, false);
                }
            }
            // 子类：反向继承
            if (index != null) {
                for (String child : index.subclassesByParent().getOrDefault(name, List.of())) {
                    if (UmlTypeNameUtil.isJdk(child) || child.equals(name)) {
                        continue;
                    }
                    addRelation(relations, seenRelations, child, name, RelationType.GENERALIZATION, null, null);
                    enqueue(child, d + 1, queued, queue, depth);
                }
            }
        }
        return new UmlDiagram(target, List.copyOf(byName.values()), List.copyOf(relations));
    }

    /** 按可读类型名建立 ASSOCIATION/DEPENDENCY 关系 */
    private static void addTypeRelation(List<UmlRelation> relations, Set<String> seenRelations,
                                        Deque<String> queue, Set<String> queued, Map<String, Integer> depth,
                                        String from, int fromDepth, String readableType,
                                        RelationType type, boolean isField) {
        String element = UmlTypeNameUtil.getElementType(readableType);
        if (!UmlTypeNameUtil.isUMLClassType(element)) {
            return;
        }
        // 类型变量（如 T/E/K/V）不建立关系，避免生成无意义的标记框
        if (element.length() == 1 && Character.isUpperCase(element.charAt(0))) {
            return;
        }
        String target = normalize(element);
        if (target.equals(from)) {
            return; // 自引用不建边
        }
        String cardinality = isField && UmlTypeNameUtil.isCollectionOrArray(readableType) ? "0..*" : null;
        addRelation(relations, seenRelations, from, target, type, null, cardinality);
        enqueue(target, fromDepth + 1, queued, queue, depth);
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
