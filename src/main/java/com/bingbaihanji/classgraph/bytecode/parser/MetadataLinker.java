package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.*;

/**
 * 元数据链接器 — 将解析后的 class 文件 DTO 链接到类图关系中。
 *
 * <p>负责建立超类/子类、接口/实现、注解/被注解类之间的双向关系。</p>
 */
public final class MetadataLinker {

    private MetadataLinker() {}

    /**
     * 链接结果 — 包含链接后的类和关系索引。
     */
    public record LinkResult(
        Map<String, ParsedClassFile> classByName,
        Map<String, Set<String>> superclassToSubclasses,
        Map<String, Set<String>> interfaceToImplementations,
        Map<String, Set<String>> annotationToAnnotated
    ) {}

    /**
     * 将解析后的类列表链接为类图。
     *
     * @param parsedClasses 解析后的类 DTO 列表
     * @return 链接结果
     */
    public static LinkResult link(List<ParsedClassFile> parsedClasses) {
        Map<String, ParsedClassFile> classByName = new LinkedHashMap<>();
        Map<String, Set<String>> superToSubs = new LinkedHashMap<>();
        Map<String, Set<String>> itfToImpls = new LinkedHashMap<>();
        Map<String, Set<String>> annToAnnotated = new LinkedHashMap<>();

        // 第一遍：建立名称索引
        for (ParsedClassFile cf : parsedClasses) {
            classByName.put(cf.internalName(), cf);
        }

        // 第二遍：建立关系
        for (ParsedClassFile cf : parsedClasses) {
            String name = cf.internalName();

            // 超类 → 子类
            if (cf.superName() != null && !"java/lang/Object".equals(cf.superName())) {
                superToSubs.computeIfAbsent(cf.superName(), k -> new LinkedHashSet<>())
                    .add(name);
            }

            // 接口 → 实现类
            for (String itf : cf.interfaceNames()) {
                itfToImpls.computeIfAbsent(itf, k -> new LinkedHashSet<>())
                    .add(name);
            }

            // 注解 → 被注解类
            for (ParsedAnnotation ann : cf.annotations()) {
                annToAnnotated.computeIfAbsent(ann.className(), k -> new LinkedHashSet<>())
                    .add(name);
            }
        }

        // 冻结
        return new LinkResult(
            Collections.unmodifiableMap(classByName),
            freeze(superToSubs),
            freeze(itfToImpls),
            freeze(annToAnnotated)
        );
    }

    private static Map<String, Set<String>> freeze(
            Map<String, Set<String>> map) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) {
            result.put(entry.getKey(),
                Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }
}
