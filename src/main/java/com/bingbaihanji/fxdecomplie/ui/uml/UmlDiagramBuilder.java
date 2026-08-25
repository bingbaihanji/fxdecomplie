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
 * 从目标类出发 BFS 构建「聚焦」UML 类图(继承/实现层级).
 *
 * <p>向上:父类链(GENERALIZATION)+ 接口及其父接口(REALIZATION),均带成员;
 * 向下:仅目标类的直接子类 / 目标接口的实现者,各带成员.
 * 字段类型补充为关联(ASSOCIATION),方法参数/返回值与注解补充为依赖(DEPENDENCY).
 * JDK 类型不建节点;解析不到的类建「仅类名」标记框.</p>
 */
public final class UmlDiagramBuilder {

    /** 类节点数量上限 */
    public static final int MAX_CLASSES = 100;

    /** 关系展开深度上限 */
    public static final int MAX_DEPTH = 20;

    /** 非继承关系只从目标附近展开,避免大工程图爆炸 */
    private static final int MAX_MEMBER_RELATION_DEPTH = 1;

    private UmlDiagramBuilder() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建 UML 类图.
     *
     * @param targetInternalName 目标类内部名称(如 com/example/OrderServiceImpl)
     * @param targetBytes        目标类字节码,优先于索引读取
     * @param index              工作区索引,可为 {@link WorkspaceIndex#EMPTY}
     * @return 类图;目标不可解析返回 null
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
                continue; // 仅显示,不再向上/向下展开
            }

            // 向上:父类链(继承)
            if (uml.superName() != null && !UmlTypeNameUtil.isJdk(uml.superName())) {
                addRelation(relations, seenRelations, name, uml.superName(), RelationType.GENERALIZATION, null, null);
                enqueue(uml.superName(), d + 1, queued, queue, depth);
            }
            // 向上:接口(实现,含父接口链)
            for (String itf : uml.interfaces()) {
                if (UmlTypeNameUtil.isJdk(itf)) {
                    continue;
                }
                addRelation(relations, seenRelations, name, itf, RelationType.REALIZATION, null, null);
                enqueue(itf, d + 1, queued, queue, depth);
            }
            if (d <= MAX_MEMBER_RELATION_DEPTH) {
                addMemberRelations(uml, d, target, index, relations, seenRelations, queued, queue, depth);
            }
            // 向下:仅目标类的直接子类/实现者
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
        /*
         * 节点上限、字节码读取失败或索引发生变化时，队列中可能仍有尚未落
         * 入 byName 的关系目标。渲染器无法为这类关系定位端点，不能把它们
         * 作为悬空连线输出。
         */
        Set<String> nodeNames = byName.keySet();
        List<UmlRelation> validRelations = relations.stream()
                .filter(r -> nodeNames.contains(r.from()) && nodeNames.contains(r.to()))
                .toList();
        return new UmlDiagram(target, List.copyOf(byName.values()), validRelations);
    }

    private static void addMemberRelations(UmlClass uml, int d, String target, WorkspaceIndex index,
                                           List<UmlRelation> relations, Set<String> seenRelations,
                                           Set<String> queued, Deque<String> queue,
                                           Map<String, Integer> depth) {
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return;
        }
        String source = uml.internalName();
        for (UmlField field : uml.fields()) {
            for (String related : relationTargets(field.type(), index)) {
                if (related.equals(source)) {
                    continue;
                }
                String cardinality = UmlTypeNameUtil.isCollectionOrArray(field.type()) ? "*" : null;
                addRelation(relations, seenRelations, source, related,
                        RelationType.ASSOCIATION, null, cardinality);
                enqueueRelated(related, d + 1, target, queued, queue, depth);
            }
        }
        for (UmlMethod method : uml.methods()) {
            for (String param : method.params()) {
                addDependency(source, param, d, target, index, relations, seenRelations, queued, queue, depth);
            }
            if (!method.isConstructor()) {
                addDependency(source, method.returnType(), d, target, index, relations, seenRelations, queued, queue, depth);
            }
        }
        for (String annotation : uml.annotationNames()) {
            String related = resolveType(annotation, index);
            if (related != null && !related.equals(source)) {
                addRelation(relations, seenRelations, source, related,
                        RelationType.DEPENDENCY, null, null);
                enqueueRelated(related, d + 1, target, queued, queue, depth);
            }
        }
    }

    private static void addDependency(String source, String type, int d, String target, WorkspaceIndex index,
                                      List<UmlRelation> relations, Set<String> seenRelations,
                                      Set<String> queued, Deque<String> queue,
                                      Map<String, Integer> depth) {
        for (String related : relationTargets(type, index)) {
            if (related.equals(source)) {
                continue;
            }
            addRelation(relations, seenRelations, source, related,
                    RelationType.DEPENDENCY, null, null);
            enqueueRelated(related, d + 1, target, queued, queue, depth);
        }
    }

    private static List<String> relationTargets(String type, WorkspaceIndex index) {
        if (type == null || type.isBlank()) {
            return List.of();
        }
        Set<String> targets = new LinkedHashSet<>();
        for (String candidate : typeCandidates(type)) {
            String resolved = resolveType(candidate, index);
            if (resolved != null && !UmlTypeNameUtil.isJdk(resolved)) {
                targets.add(resolved);
            }
        }
        return List.copyOf(targets);
    }

    private static List<String> typeCandidates(String type) {
        String normalized = type.replace("[]", "");
        List<String> result = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (Character.isJavaIdentifierPart(ch) || ch == '.' || ch == '/' || ch == '$') {
                token.append(ch);
            } else {
                addTypeCandidate(result, token);
            }
        }
        addTypeCandidate(result, token);
        return result;
    }

    private static void addTypeCandidate(List<String> result, StringBuilder token) {
        if (token.isEmpty()) {
            return;
        }
        String value = token.toString();
        token.setLength(0);
        /*
         * 单字符大写标识符是 Java 泛型类型变量的常见形式(T/E/K/V/X)，
         * 不应因为工作区恰好存在同名 class 而创建幽灵关系。其它裸名称仍
         * 保留，兼容默认包中的真实类。
         */
        if (!isTypeVariableName(value) && UmlTypeNameUtil.isUMLClassType(value)) {
            result.add(value);
        }
    }

    private static boolean isTypeVariableName(String value) {
        return value.length() == 1 && Character.isUpperCase(value.charAt(0));
    }

    private static String resolveType(String type, WorkspaceIndex index) {
        if (index == null || type == null || type.isBlank()) {
            return null;
        }
        String internal = normalize(UmlTypeNameUtil.getElementType(type));
        if (internal.isBlank() || UmlTypeNameUtil.isJdk(internal)) {
            return null;
        }
        var entry = index.findClass(internal);
        return entry == null ? null : entry.internalName();
    }

    private static void enqueueRelated(String name, int d, String target, Set<String> queued,
                                       Deque<String> queue, Map<String, Integer> depth) {
        if (queued.size() >= MAX_CLASSES || name.equals(target)) {
            return;
        }
        enqueue(name, d, queued, queue, depth);
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

    /** 解析不到的类:仅类名标记框 */
    private static UmlClass marker(String internalName) {
        String simple = internalName;
        int idx = internalName.lastIndexOf('/');
        if (idx >= 0) {
            simple = internalName.substring(idx + 1);
        }
        return new UmlClass(internalName, "", simple, ClassKind.CLASS, false, false, false,
                List.of(), List.of(), null, List.of(), List.of());
    }

    /** 归一化为 JVM 内部名称:反斜杠转斜杠,点转斜杠(点分 FQCN → 内部名) */
    private static String normalize(String name) {
        return name.replace('\\', '/').replace('.', '/');
    }
}
