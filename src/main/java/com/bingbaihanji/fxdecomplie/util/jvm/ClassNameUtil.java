package com.bingbaihanji.fxdecomplie.util.jvm;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * JVM 内部类名/路径规范化的集中式工具类
 * <p>
 * 提供类名的规范化、路径转换、容器前缀剥离、简单名/包名提取、
 * 以及内部类候选路径生成等功能所有方法均为静态方法,不可实例化
 * </p>
 *
 * @author bingbaihanji
 * @since 1.0
 */
public final class ClassNameUtil {

    /** Spring Boot / Web 容器常见的类路径前缀,用于剥离容器前缀得到纯类名路径 */
    private static final String[] CONTAINER_CLASS_PREFIXES = {
            "BOOT-INF/classes/",
            "WEB-INF/classes/",
            "APP-INF/classes/"
    };

    private ClassNameUtil() {
        throw new AssertionError("utility class");
    }

    /**
     * 规范化类名,统一转换为用 {@code /} 分隔的内部格式
     * <ul>
     *   <li>反斜杠 {@code \} 替换为正斜杠 {@code /}</li>
     *   <li>去除首尾空白</li>
     *   <li>去除开头的 {@code /}</li>
     *   <li>去除 {@code .class} 或 {@code .java} 后缀</li>
     *   <li>若无斜杠则把 {@code .} 替换为 {@code /}</li>
     * </ul>
     *
     * @param className 原始类名,可能包含 {@code \}、{@code .}、后缀等
     * @return 规范化后的内部类名,{@code null} 时返回空字符串
     */
    public static String normalizeInternalName(String className) {
        if (className == null) {
            return "";
        }
        // 统一分隔符为 /,去除首尾空白
        String normalized = className.replace('\\', '/').strip();
        // 去除开头的 /
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        // 去除 .class 或 .java 后缀
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        } else if (normalized.endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - ".java".length());
        }
        // 若无斜杠,说明可能是点分隔的完全限定名,统一转为斜杠格式
        if (!normalized.contains("/")) {
            normalized = normalized.replace('.', '/');
        }
        return normalized;
    }

    /**
     * 将类名转换为 {@code .class} 文件相对路径
     *
     * @param className 类名(任意格式)
     * @return 以 {@code .class} 结尾的文件路径,空输入返回空字符串
     */
    public static String classFilePath(String className) {
        String normalized = normalizeInternalName(className);
        return normalized.isBlank() ? "" : normalized + ".class";
    }

    /**
     * 剥离 Spring Boot / Web 容器类路径前缀(如 {@code BOOT-INF/classes/})
     * <p>
     * 支持前缀直接匹配和嵌套在子目录中的情况(如 {@code /BOOT-INF/classes/})
     * </p>
     *
     * @param className 类名(任意格式)
     * @return 剥离前缀后的规范化类名
     */
    public static String stripContainerClassPrefix(String className) {
        String normalized = normalizeInternalName(className);
        for (String prefix : CONTAINER_CLASS_PREFIXES) {
            // 直接匹配：如 BOOT-INF/classes/com/example/Foo
            if (normalized.startsWith(prefix)) {
                return normalized.substring(prefix.length());
            }
            // 嵌套匹配：如 someDir/BOOT-INF/classes/com/example/Foo
            String nested = "/" + prefix;
            int index = normalized.indexOf(nested);
            if (index >= 0) {
                return normalized.substring(index + nested.length());
            }
        }
        return normalized;
    }

    /**
     * 判断两个类名是否指向同一个类
     * <p>
     * 比较规则：先直接比较规范化的内部名,若不匹配则剥离容器前缀后再比较
     * </p>
     *
     * @param left  第一个类名
     * @param right 第二个类名
     * @return 两者指向同一类时返回 {@code true},任一为空时返回 {@code false}
     */
    public static boolean sameInternalName(String left, String right) {
        String normalizedLeft = normalizeInternalName(left);
        String normalizedRight = normalizeInternalName(right);
        if (normalizedLeft.isBlank() || normalizedRight.isBlank()) {
            return false;
        }
        return normalizedLeft.equals(normalizedRight)
                || stripContainerClassPrefix(normalizedLeft)
                .equals(stripContainerClassPrefix(normalizedRight));
    }

    /**
     * 提取类的简单名(不含包路径)
     * <p>
     * 例如 {@code com/example/Foo} 返回 {@code Foo}
     * </p>
     *
     * @param className 类名(任意格式)
     * @return 简单类名,空输入返回空字符串
     */
    public static String simpleName(String className) {
        String normalized = normalizeInternalName(className);
        if (normalized.isBlank()) {
            return "";
        }
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    /**
     * 提取类的包名(不含类名本身)
     * <p>
     * 例如 {@code com/example/Foo} 返回 {@code com/example}
     * </p>
     *
     * @param className 类名(任意格式)
     * @return 包名路径,无包时返回空字符串
     */
    public static String packageName(String className) {
        String normalized = normalizeInternalName(className);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(0, slash) : "";
    }

    /**
     * 生成类文件路径候选列表,用于在归档中逐级查找
     * <p>
     * 生成策略：从普通路径开始,依次将最深的 {@code /} 替换为 {@code $}(模拟内部类路径),
     * 同时为每个候选生成剥离容器前缀的变体例如 {@code com/example/Outer$Inner.class}
     * </p>
     *
     * @param className 类名(任意格式)
     * @return 有序的 {@code .class} 文件路径候选列表(不可变),空输入返回空列表
     */
    public static List<String> classFilePathCandidates(String className) {
        String normalized = normalizeInternalName(className);
        if (normalized.isBlank()) {
            return List.of();
        }
        Set<String> candidates = new LinkedHashSet<>();
        // 1. 原始规范化路径
        addCandidate(candidates, normalized + ".class");

        // 2. 剥离容器前缀后的路径(如有差异)
        String stripped = stripContainerClassPrefix(normalized);
        if (!stripped.equals(normalized)) {
            addCandidate(candidates, stripped + ".class");
        }

        // 3. 逐级将最后一个 / 替换为 $,生成内部类路径候选
        String inner = normalized;
        int slash = inner.lastIndexOf('/');
        while (slash > 0) {
            inner = inner.substring(0, slash) + "$" + inner.substring(slash + 1);
            addCandidate(candidates, inner + ".class");
            // 内部类路径也尝试剥离容器前缀
            String strippedInner = stripContainerClassPrefix(inner);
            if (!strippedInner.equals(inner)) {
                addCandidate(candidates, strippedInner + ".class");
            }
            slash = inner.lastIndexOf('/');
        }
        return List.copyOf(candidates);
    }

    /**
     * 向候选列表中添加不重复、非空的路径值
     *
     * @param candidates 候选路径列表
     * @param value      待添加的路径值
     */
    private static void addCandidate(Set<String> candidates, String value) {
        if (value != null && !value.isBlank()) {
            candidates.add(value);
        }
    }
}
