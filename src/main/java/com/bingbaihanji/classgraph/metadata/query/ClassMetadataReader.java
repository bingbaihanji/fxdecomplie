package com.bingbaihanji.classgraph.metadata.query;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 类元数据只读接口 — CQRS 读侧。
 *
 * <p>所有对扫描结果的查询操作必须通过此接口进行。
 * 实现可以是可变的（构建阶段）或不可变的（冻结后）。</p>
 */
public interface ClassMetadataReader {

    /** 获取类名 */
    String className();

    /** 获取修饰符位掩码 */
    int modifiers();

    /** 获取父类名（可为 null） */
    String superclassName();

    /** 获取直接实现的接口名列表 */
    List<String> interfaceNames();

    /** 获取字段列表 */
    List<FieldInfo> fields();

    /** 获取方法列表 */
    List<MethodInfo> methods();

    /** 获取类级别注解 */
    List<AnnotationInfo> annotations();

    /** 获取引用该类的外部类集合 */
    Set<String> referencedClassNames();

    // ─── 派生查询 ───

    /** 是否接口 */
    default boolean isInterface() { return (modifiers() & 0x0200) != 0; }

    /** 是否注解 */
    default boolean isAnnotation() { return (modifiers() & 0x2000) != 0; }

    /** 是否枚举 */
    default boolean isEnum() { return (modifiers() & 0x4000) != 0; }

    /** 是否有父类（非 Object） */
    default boolean hasSuperclass() {
        String sup = superclassName();
        return sup != null && !"java/lang/Object".equals(sup);
    }

    /** 查找指定名称的字段 */
    default Optional<FieldInfo> findField(String name) {
        return fields().stream()
            .filter(f -> f.getName().equals(name))
            .findFirst();
    }

    /** 查找指定名称和描述符的方法 */
    default Optional<MethodInfo> findMethod(String name, String descriptor) {
        return methods().stream()
            .filter(m -> m.getName().equals(name)
                && m.getTypeDescriptorStr().equals(descriptor))
            .findFirst();
    }

    /** 查找指定名称的所有方法（支持重载） */
    default List<MethodInfo> findMethodsByName(String name) {
        return methods().stream()
            .filter(m -> m.getName().equals(name))
            .toList();
    }

    /** 是否有指定名称的注解 */
    default boolean hasAnnotation(String annotationName) {
        return annotations().stream()
            .anyMatch(a -> a.getName().equals(annotationName));
    }

    /** 转换为 ClassInfo（如果可用） */
    default Optional<ClassInfo> asClassInfo() {
        return Optional.empty();
    }
}
