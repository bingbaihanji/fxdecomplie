package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/**
 * 图中一个类型节点:类 / 接口 / 枚举 / 注解
 *
 * @param parsed 是否从字节码完整解析;false 表示「仅类名」标记框
 */
public record UmlClass(String internalName, String packageName, String simpleName,
                       ClassKind kind, boolean isAbstract, boolean isFinal, boolean parsed,
                       List<UmlField> fields, List<UmlMethod> methods,
                       String superName, List<String> interfaces,
                       List<String> annotationNames) {
}
