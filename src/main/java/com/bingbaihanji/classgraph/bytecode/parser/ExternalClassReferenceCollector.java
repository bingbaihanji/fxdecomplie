package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.*;

/**
 * 外部类引用收集器 — 从解析后的 DTO 中提取所有被引用的外部类名。
 *
 * <p>收集来源：超类、接口、字段类型、方法参数/返回类型、注解、异常类型。</p>
 */
public final class ExternalClassReferenceCollector {

    private ExternalClassReferenceCollector() {}

    /**
     * 从 ParsedClassFile 中收集所有引用的外部类。
     *
     * @param classFile   解析后的类文件
     * @param knownClasses 已知（已扫描）的类名集合，这些不会被包含在结果中
     * @return 引用的外部类名集合（去重，不可变）
     */
    public static Set<String> collect(ParsedClassFile classFile,
                                       Set<String> knownClasses) {
        Set<String> refs = new LinkedHashSet<>();
        Set<String> known = knownClasses != null ? knownClasses : Set.of();

        // 超类
        if (classFile.superName() != null
            && !"java/lang/Object".equals(classFile.superName())
            && !known.contains(classFile.superName())) {
            refs.add(classFile.superName());
        }

        // 接口
        for (String itf : classFile.interfaceNames()) {
            if (!known.contains(itf)) {
                refs.add(itf);
            }
        }

        // 字段类型
        for (ParsedField field : classFile.fields()) {
            collectTypeReferences(field.descriptor(), refs, known);
            collectTypeReferences(field.signature(), refs, known);
            for (ParsedAnnotation ann : field.annotations()) {
                refs.add(ann.className());
            }
        }

        // 方法参数/返回类型/异常
        for (ParsedMethod method : classFile.methods()) {
            collectTypeReferences(method.descriptor(), refs, known);
            collectTypeReferences(method.signature(), refs, known);
            for (String exc : method.exceptionTypeNames()) {
                if (!known.contains(exc)) refs.add(exc);
            }
            for (ParsedAnnotation ann : method.annotations()) {
                refs.add(ann.className());
            }
        }

        // 类级别注解
        for (ParsedAnnotation ann : classFile.annotations()) {
            refs.add(ann.className());
        }

        return Collections.unmodifiableSet(refs);
    }

    /**
     * 从类型描述符或签名中提取对象类型引用。
     * 例如 "Ljava/lang/String;" → "java/lang/String"
     */
    private static void collectTypeReferences(String descriptor,
                                               Set<String> refs,
                                               Set<String> known) {
        if (descriptor == null) return;
        int i = 0;
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            if (c == 'L') {
                int end = descriptor.indexOf(';', i);
                if (end > i) {
                    String internalName = descriptor.substring(i + 1, end);
                    if (!known.contains(internalName)) {
                        refs.add(internalName);
                    }
                    i = end;
                }
            }
            i++;
        }
    }
}
