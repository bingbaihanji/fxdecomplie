package com.bingbaihanji.classgraph.metadata.query;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;

import java.util.*;

/**
 * 可变类元数据构建器 — CQRS 写侧。
 *
 * <p>用于扫描阶段逐步构建元数据。构建完成后调用 {@link #freeze()}
 * 获取不可变的 {@link ClassMetadataReader} 快照。</p>
 *
 * <p>线程不安全 — 假设在单线程上下文中构建。</p>
 */
public final class MutableClassMetadata implements ClassMetadataReader {

    private String className;
    private int modifiers;
    private String superclassName;
    private final List<String> interfaceNames = new ArrayList<>();
    private final List<FieldInfo> fields = new ArrayList<>();
    private final List<MethodInfo> methods = new ArrayList<>();
    private final List<AnnotationInfo> annotations = new ArrayList<>();
    private final Set<String> referencedClassNames = new LinkedHashSet<>();
    private boolean frozen;

    public MutableClassMetadata(String className) {
        this.className = Objects.requireNonNull(className, "className");
    }

    // ─── 构建方法 ───

    public MutableClassMetadata modifiers(int modifiers) {
        checkNotFrozen();
        this.modifiers = modifiers;
        return this;
    }

    public MutableClassMetadata superclassName(String superclassName) {
        checkNotFrozen();
        this.superclassName = superclassName;
        return this;
    }

    public MutableClassMetadata addInterface(String interfaceName) {
        checkNotFrozen();
        if (interfaceName != null && !interfaceName.isBlank()) {
            interfaceNames.add(interfaceName);
        }
        return this;
    }

    public MutableClassMetadata addInterfaces(Collection<String> names) {
        checkNotFrozen();
        if (names != null) {
            names.forEach(this::addInterface);
        }
        return this;
    }

    public MutableClassMetadata addField(FieldInfo field) {
        checkNotFrozen();
        if (field != null) fields.add(field);
        return this;
    }

    public MutableClassMetadata addFields(Collection<FieldInfo> fieldList) {
        checkNotFrozen();
        if (fieldList != null) fieldList.forEach(this::addField);
        return this;
    }

    public MutableClassMetadata addMethod(MethodInfo method) {
        checkNotFrozen();
        if (method != null) methods.add(method);
        return this;
    }

    public MutableClassMetadata addMethods(Collection<MethodInfo> methodList) {
        checkNotFrozen();
        if (methodList != null) methodList.forEach(this::addMethod);
        return this;
    }

    public MutableClassMetadata addAnnotation(AnnotationInfo annotation) {
        checkNotFrozen();
        if (annotation != null) annotations.add(annotation);
        return this;
    }

    public MutableClassMetadata addAnnotations(Collection<AnnotationInfo> annList) {
        checkNotFrozen();
        if (annList != null) annList.forEach(this::addAnnotation);
        return this;
    }

    public MutableClassMetadata addReferencedClass(String className) {
        checkNotFrozen();
        if (className != null && !className.isBlank()) {
            referencedClassNames.add(className);
        }
        return this;
    }

    // ─── 生命周期 ───

    /** 冻结为不可变快照 */
    public FrozenMetadata freeze() {
        frozen = true;
        return new FrozenMetadata(
            className, modifiers, superclassName,
            List.copyOf(interfaceNames),
            List.copyOf(fields),
            List.copyOf(methods),
            List.copyOf(annotations),
            Set.copyOf(referencedClassNames)
        );
    }

    /** 是否已冻结 */
    public boolean isFrozen() { return frozen; }

    private void checkNotFrozen() {
        if (frozen) {
            throw new IllegalStateException(
                "MutableClassMetadata is frozen and cannot be modified");
        }
    }

    // ─── 读取访问器 ───

    @Override public String className() { return className; }
    @Override public int modifiers() { return modifiers; }
    @Override public String superclassName() { return superclassName; }
    @Override public List<String> interfaceNames() { return Collections.unmodifiableList(interfaceNames); }
    @Override public List<FieldInfo> fields() { return Collections.unmodifiableList(fields); }
    @Override public List<MethodInfo> methods() { return Collections.unmodifiableList(methods); }
    @Override public List<AnnotationInfo> annotations() { return Collections.unmodifiableList(annotations); }
    @Override public Set<String> referencedClassNames() { return Collections.unmodifiableSet(referencedClassNames); }

    // ─── 不可变实现 ───

    /**
     * 冻结的不可变元数据快照。
     */
    public record FrozenMetadata(
        String className,
        int modifiers,
        String superclassName,
        List<String> interfaceNames,
        List<FieldInfo> fields,
        List<MethodInfo> methods,
        List<AnnotationInfo> annotations,
        Set<String> referencedClassNames
    ) implements ClassMetadataReader {
        @Override
        public Optional<ClassInfo> asClassInfo() { return Optional.empty(); }
    }
}
