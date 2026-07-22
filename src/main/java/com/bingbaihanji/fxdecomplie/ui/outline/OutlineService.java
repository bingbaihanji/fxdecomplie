package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 大纲方法实现查询服务 —— 查找接口/抽象类方法的所有实现/重写
 *
 * <p>利用 {@link WorkspaceIndex} 的父子类关系索引和字节码方法描述符匹配,
 * 为大纲视图提供接口方法的实现类列表</p>
 *
 * @author bingbaihanji
 * @date 2026-07-21
 */
public final class OutlineService {

    private static final Logger log = LoggerFactory.getLogger(OutlineService.class);
    private static final int MAX_IMPLEMENTATIONS = 50;

    private OutlineService() {
        throw new AssertionError("utility class");
    }

    /**
     * 查找指定接口/抽象类中某个方法的所有实现
     *
     * @param targetInternalName 目标接口/抽象类的内部名称(如 com/example/UserService)
     * @param methodName         方法名称
     * @param paramCount         参数个数(从源码解析得到,用于辅助匹配;-1 表示仅按方法名匹配)
     * @param index              工作区索引(可为 null,此时返回空列表)
     * @return 实现类列表,按类名排序
     */
    public static List<ImplementationResult> findImplementations(
            String targetInternalName, String methodName, int paramCount,
            WorkspaceIndex index) {
        return findImplementations(targetInternalName, methodName, null, paramCount, index);
    }

    /**
     * 查找指定接口/抽象类中某个方法的所有实现,优先使用 JVM 描述符精确匹配
     *
     * @param targetInternalName 目标接口/抽象类的内部名称
     * @param methodName         方法名称
     * @param descriptor         JVM 方法描述符,为空时退化到名称匹配
     * @param index              工作区索引
     * @return 实现类列表
     */
    public static List<ImplementationResult> findImplementations(
            String targetInternalName, String methodName, String descriptor,
            WorkspaceIndex index) {
        return findImplementations(targetInternalName, methodName, descriptor, -1, index);
    }

    private static List<ImplementationResult> findImplementations(
            String targetInternalName, String methodName, String descriptor, int paramCount,
            WorkspaceIndex index) {
        if (index == null || targetInternalName == null || methodName == null) {
            return List.of();
        }

        // 获取所有直接和间接子类/实现类
        Set<String> allImplementors = new LinkedHashSet<>();
        collectImplementors(targetInternalName, index, allImplementors,
                new LinkedHashSet<>(), 0);

        if (allImplementors.isEmpty()) {
            return List.of();
        }

        // 对每个实现类,检查是否有匹配的方法
        List<ImplementationResult> results = new ArrayList<>();
        for (String implName : allImplementors) {
            if (results.size() >= MAX_IMPLEMENTATIONS) {
                break;
            }
            findMatchingMethod(implName, methodName, descriptor, paramCount, index)
                    .ifPresent(results::add);
        }

        return List.copyOf(results);
    }

    /**
     * 递归收集所有实现类/子类
     * <p>使用 BFS 避免深层继承链导致的栈溢出,并用 visited 集合防止循环</p>
     */
    private static void collectImplementors(String parentName, WorkspaceIndex index,
                                            Set<String> results, Set<String> visited,
                                            int depth) {
        if (depth > 20 || !visited.add(parentName)) {
            return;
        }
        // 跳过 JDK 标准库类
        if (parentName.startsWith("java/") || parentName.startsWith("javax/")
                || parentName.startsWith("jdk/") || parentName.startsWith("sun/")) {
            return;
        }

        Map<String, List<String>> subclasses = index.subclassesByParent();
        List<String> direct = subclasses.getOrDefault(parentName, List.of());
        for (String child : direct) {
            if (results.size() >= MAX_IMPLEMENTATIONS) {
                return;
            }
            if (results.add(child)) {
                // 递归收集子类的子类(处理多层继承链)
                collectImplementors(child, index, results, visited, depth + 1);
            }
        }
    }

    /**
     * 在指定类的字节码中检查是否包含匹配的方法
     *
     * @param internalName 类内部名称
     * @param methodName   方法名
     * @param paramCount   预期参数个数(-1 表示仅按名称匹配,不检查参数个数)
     * @param index        工作区索引
     * @return 匹配时的实现结果,否则为空
     */
    private static Optional<ImplementationResult> findMatchingMethod(
            String internalName, String methodName, String descriptor, int paramCount,
            WorkspaceIndex index) {
        boolean descriptorOnly = descriptor != null && !descriptor.isBlank();
        boolean nameOnly = !descriptorOnly && paramCount < 0;

        // 优先从索引中获取已解析的方法列表(快速路径)
        ClassIndexEntry entry = index.findClass(internalName);
        if (entry != null) {
            for (MemberIndexEntry m : entry.methods()) {
                if (m.name().equals(methodName)
                        && (!descriptorOnly || descriptor.equals(m.descriptor()))
                        && (descriptorOnly || nameOnly || paramCountMatches(m.descriptor(), paramCount))) {
                    return Optional.of(new ImplementationResult(
                            internalName, entry.fullPath(), m.name(), m.descriptor()));
                }
            }
            return Optional.empty();
        }

        // 回退:用 ClassFileParser 解析字节码
        byte[] bytes = index.getClassBytes(internalName);
        if (bytes == null) {
            return Optional.empty();
        }
        Optional<ClassFileMetadata> metaOpt = ClassFileParser.tryParse(bytes);
        if (metaOpt.isEmpty()) {
            return Optional.empty();
        }
        ClassFileMetadata meta = metaOpt.get();
        ClassIndexEntry fallbackEntry = index.findClass(internalName);
        String classPath = fallbackEntry != null ? fallbackEntry.fullPath() : internalName + ".class";

        for (ClassFileMetadata.MemberInfo m : meta.methods()) {
            if (m.name().equals(methodName)
                    && (!descriptorOnly || descriptor.equals(m.descriptor()))
                    && (descriptorOnly || nameOnly || paramCountMatches(m.descriptor(), paramCount))) {
                return Optional.of(new ImplementationResult(
                        internalName, classPath, m.name(), m.descriptor()));
            }
        }
        return Optional.empty();
    }

    /**
     * 检查 JVM 方法描述符中的参数个数是否与预期匹配
     *
     * <p>描述符格式: {@code (参数类型)返回类型}, 如 {@code (Ljava/lang/String;I)V}
     * 表示两个参数(String 和 int),返回 void</p>
     *
     * @param descriptor      JVM 方法描述符
     * @param expectedParams  预期参数个数
     * @return 是否匹配
     */
    static boolean paramCountMatches(String descriptor, int expectedParams) {
        if (descriptor == null || descriptor.isEmpty() || !descriptor.startsWith("(")) {
            return expectedParams == 0;
        }
        int closeParen = descriptor.indexOf(')');
        if (closeParen <= 1) {
            return expectedParams == 0; // 无参数: "()V"
        }
        String params = descriptor.substring(1, closeParen);
        // 解析 JVM 类型描述符中的参数个数
        int count = 0;
        int i = 0;
        while (i < params.length()) {
            char c = params.charAt(i);
            switch (c) {
                case 'B', 'C', 'D', 'F', 'I', 'J', 'S', 'Z' -> {
                    i++;
                    count++;
                }
                case 'L' -> { // 对象类型: Lfully/qualified/Name;
                    int semi = params.indexOf(';', i);
                    if (semi < 0) {
                        return false;
                    }
                    i = semi + 1;
                    count++;
                }
                case '[' -> { // 数组类型: [[[L...;
                    while (i < params.length() && params.charAt(i) == '[') {
                        i++;
                    }
                    if (i < params.length() && params.charAt(i) == 'L') {
                        int semi = params.indexOf(';', i);
                        if (semi < 0) {
                            return false;
                        }
                        i = semi + 1;
                    } else {
                        i++; // 基本类型数组
                    }
                    count++;
                }
                default -> {
                    return false;
                } // 非法描述符
            }
        }
        return count == expectedParams;
    }

    /**
     * 结果记录：某个方法在特定实现类中的位置信息
     *
     * @param implementingClass 实现类内部名称(如 com/example/UserServiceImpl)
     * @param classPath         实现类文件路径
     * @param methodName        方法名称
     * @param descriptor        方法 JVM 描述符
     */
    public record ImplementationResult(String implementingClass, String classPath,
                                       String methodName, String descriptor) {
    }
}
