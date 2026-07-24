package com.bingbaihanji.classgraph.metadata.query;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 元数据查询服务 — CQRS 读侧的统一入口。
 *
 * <p>封装对 {@link ClassMetadataReader} 集合的常见查询操作，
 * 消除调用方散布的 null 检查和手动过滤逻辑。</p>
 */
public final class MetadataQueryService {

    private final Map<String, ClassMetadataReader> readers;

    /**
     * 使用类名到读取器的映射创建查询服务。
     */
    public MetadataQueryService(Map<String, ? extends ClassMetadataReader> readers) {
        this.readers = readers == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(readers));
    }

    // ─── 基本查询 ───

    /** 按名称查找，可能为空 */
    public Optional<ClassMetadataReader> findByClass(String className) {
        return Optional.ofNullable(readers.get(className));
    }

    /** 检查类是否存在 */
    public boolean containsClass(String className) {
        return readers.containsKey(className);
    }

    /** 返回类总数 */
    public int classCount() {
        return readers.size();
    }

    /** 返回所有读取器 */
    public Collection<ClassMetadataReader> allClasses() {
        return readers.values();
    }

    // ─── 过滤查询 ───

    /** 查找匹配谓词的所有类 */
    public List<ClassMetadataReader> filter(Predicate<ClassMetadataReader> predicate) {
        return readers.values().stream()
            .filter(predicate)
            .toList();
    }

    /** 查找所有接口 */
    public List<ClassMetadataReader> findAllInterfaces() {
        return filter(ClassMetadataReader::isInterface);
    }

    /** 查找所有注解 */
    public List<ClassMetadataReader> findAllAnnotations() {
        return filter(ClassMetadataReader::isAnnotation);
    }

    /** 查找所有枚举 */
    public List<ClassMetadataReader> findAllEnums() {
        return filter(ClassMetadataReader::isEnum);
    }

    /** 查找所有标准类（非接口、非注解、非枚举） */
    public List<ClassMetadataReader> findAllStandardClasses() {
        return filter(r -> !r.isInterface() && !r.isAnnotation() && !r.isEnum());
    }

    /** 查找直接实现指定接口的所有类 */
    public List<ClassMetadataReader> findImplementationsOf(String interfaceName) {
        return filter(r -> r.interfaceNames().contains(interfaceName));
    }

    /** 查找直接继承指定父类的所有类 */
    public List<ClassMetadataReader> findSubclassesOf(String superclassName) {
        return filter(r -> superclassName.equals(r.superclassName()));
    }

    /** 查找被指定注解标注的所有类 */
    public List<ClassMetadataReader> findAnnotatedWith(String annotationName) {
        return filter(r -> r.hasAnnotation(annotationName));
    }

    // ─── 聚合查询 ───

    /** 统计具有指定修饰符的类数量 */
    public int countByModifier(int modifierMask) {
        return (int) readers.values().stream()
            .filter(r -> (r.modifiers() & modifierMask) != 0)
            .count();
    }

    /** 按包名分组 */
    public Map<String, List<ClassMetadataReader>> groupByPackage() {
        return readers.values().stream()
            .collect(Collectors.groupingBy(r -> {
                String name = r.className();
                int idx = name.lastIndexOf('/');
                return idx > 0 ? name.substring(0, idx) : "";
            }));
    }

    /** 收集所有引用的外部类名 */
    public Set<String> collectAllReferencedClasses() {
        Set<String> result = new LinkedHashSet<>();
        for (ClassMetadataReader r : readers.values()) {
            result.addAll(r.referencedClassNames());
        }
        return Collections.unmodifiableSet(result);
    }

    // ─── 统计 ───

    /** 计算基础统计信息 */
    public MetadataStatistics statistics() {
        int total = readers.size();
        int interfaces = (int) readers.values().stream()
            .filter(ClassMetadataReader::isInterface).count();
        int annotations = (int) readers.values().stream()
            .filter(ClassMetadataReader::isAnnotation).count();
        int enums = (int) readers.values().stream()
            .filter(ClassMetadataReader::isEnum).count();
        int standard = total - interfaces - annotations - enums;

        int totalFields = readers.values().stream()
            .mapToInt(r -> r.fields().size()).sum();
        int totalMethods = readers.values().stream()
            .mapToInt(r -> r.methods().size()).sum();
        int totalAnnotations = readers.values().stream()
            .mapToInt(r -> r.annotations().size()).sum();

        return new MetadataStatistics(total, interfaces, annotations, enums,
            standard, totalFields, totalMethods, totalAnnotations);
    }

    /**
     * 元数据统计 DTO。
     */
    public record MetadataStatistics(
        int totalClasses,
        int interfaceCount,
        int annotationCount,
        int enumCount,
        int standardClassCount,
        int totalFields,
        int totalMethods,
        int totalAnnotations
    ) {}
}
