package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 类继承关系分析服务。
 *
 * <p>使用 {@link ClassFileParser} 快速读取常量池中的 super_class 和 interfaces，
 * 无需完整解析 class 文件。向上追溯父类链，向下查找子类树，构建可展示的
 * {@link TreeItem} 继承层次。</p>
 *
 * <p>子类查找通过 {@link SubclassIndex} 预建索引实现 O(1) 查询，索引构建
 * 有超时和上限保护（{@link #SUBCLASS_TIMEOUT_MS} / {@link #MAX_SUBCLASSES}），
 * 避免大 workspace 下长时间阻塞。</p>
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritanceService {

    private static final Logger logger = LoggerFactory.getLogger(InheritanceService.class);

    private static final int MAX_DEPTH = 20;
    private static final int MAX_SUBCLASSES = 200;
    private static final long SUBCLASS_TIMEOUT_MS = 3000;

    private InheritanceService() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建继承树。
     *
     * @param fullPath 目标类全路径，如 "com/example/MyClass.class"
     * @return 继承树根节点，失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath) {
        return buildTree(fullPath, null);
    }

    /**
     * 构建继承树。
     *
     * @param fullPath 目标类全路径，如 "com/example/MyClass.class"
     * @param index 当前工作区索引，优先用于读取字节码
     * @return 继承树根节点，失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath, WorkspaceIndex index) {
        return buildTree(fullPath, index, null);
    }

    /**
     * 构建继承树。rootBytes 用于当前打开 class 的即时构图，避免为了一个弹窗等待完整工作区索引。
     *
     * @param fullPath 目标类全路径，如 "com/example/MyClass.class"
     * @param index 当前工作区索引，可为空；有索引时补充父类链和完整子类树
     * @param rootBytes 当前目标类字节码，优先于 index 读取
     * @return 继承树根节点，失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath, WorkspaceIndex index,
                                                      byte[] rootBytes) {
        Set<String> visited = new HashSet<>();
        String internalName = toInternal(fullPath);
        WorkspaceIndex usableIndex = index == WorkspaceIndex.EMPTY ? null : index;
        byte[] bytes = rootBytes == null || rootBytes.length == 0
                ? getBytes(usableIndex, internalName)
                : rootBytes.clone();
        if (bytes == null) {
            return null;
        }

        InheritanceNode rootData = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SELF, 0);
        TreeItem<InheritanceNode> root = new TreeItem<>(rootData);
        root.setExpanded(true);
        visited.add(internalName);

        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isPresent()) {
            ClassFileMetadata meta = metadata.get();
            String superName = meta.superName();
            if (superName != null) {
                buildSuperChain(superName, root, 1, visited, usableIndex);
            }
            for (String itf : meta.interfaces()) {
                TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                        new InheritanceNode(itf, simpleName(itf),
                                InheritanceNode.RelationType.INTERFACE, 1));
                root.getChildren().add(ifNode);
            }
        } else {
            logger.warn("解析类元数据用于继承树失败: {}", fullPath);
        }

        appendSubClassTree(internalName, root, visited, usableIndex);
        return root;
    }

    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                        int depth, Set<String> visited, WorkspaceIndex index) {
        if (depth > MAX_DEPTH || visited.contains(internalName)) {
            return;
        }
        visited.add(internalName);

        InheritanceNode data = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, depth);
        TreeItem<InheritanceNode> node = new TreeItem<>(data);
        parent.getChildren().add(0, node);

        if ("java/lang/Object".equals(internalName)) {
            return;
        }

        byte[] bytes = getBytes(index, internalName);
        if (bytes == null) {
            return;
        }

        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isEmpty()) {
            logger.warn("解析类元数据用于继承树失败: {}", internalName);
            return;
        }

        ClassFileMetadata meta = metadata.get();
        String superName = meta.superName();
        if (superName != null) {
            buildSuperChain(superName, node, depth + 1, visited, index);
        }
        for (String itf : meta.interfaces()) {
            TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                    new InheritanceNode(itf, simpleName(itf),
                            InheritanceNode.RelationType.INTERFACE, depth + 1));
            node.getChildren().add(ifNode);
        }
    }

    private static void appendSubClassTree(String targetName, TreeItem<InheritanceNode> root,
                                           Set<String> visited, WorkspaceIndex index) {
        if (index == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + SUBCLASS_TIMEOUT_MS;
        SubclassIndex subclassIndex = SubclassIndex.build(index, deadline);
        Counter counter = new Counter();
        appendSubClasses(targetName, root, visited, subclassIndex, 1, counter, deadline);
    }

    private static void appendSubClasses(String targetName, TreeItem<InheritanceNode> parent,
                                         Set<String> visited, SubclassIndex index,
                                         int depth, Counter counter, long deadline) {
        if (depth > MAX_DEPTH || counter.value >= MAX_SUBCLASSES
                || System.currentTimeMillis() > deadline) {
            return;
        }
        for (String name : index.childrenOf(targetName)) {
            if (counter.value >= MAX_SUBCLASSES || System.currentTimeMillis() > deadline) {
                return;
            }
            if (visited.contains(name) || name.equals(targetName)) {
                continue;
            }
            visited.add(name);
            TreeItem<InheritanceNode> node = new TreeItem<>(
                    new InheritanceNode(name, simpleName(name),
                            InheritanceNode.RelationType.SUBCLASS, depth));
            node.setExpanded(true);
            parent.getChildren().add(node);
            counter.value++;
            appendSubClasses(name, node, visited, index, depth + 1, counter, deadline);
        }
    }

    private static byte[] getBytes(WorkspaceIndex index, String internalName) {
        return index == null ? null : index.getClassBytes(internalName);
    }

    private static String toInternal(String path) {
        return path.replace(".class", "").replace("\\", "/");
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    /**
     * 子类反向索引：parent → children 的映射，O(1) 查询子类。
     *
     * <p>构建时遍历 workspace 中所有 class，提取每个 class 的父类和接口，
     * 建立反向引用。注意：同一 child 可能多次出现（实现多接口的类），
     * 但每个 parent→child 对只记录一次。</p>
     */
    private static final class SubclassIndex {
        /** 父类/接口名 → 子类/实现类名列表 */
        private final Map<String, List<String>> childrenByParent;

        private SubclassIndex(Map<String, List<String>> childrenByParent) {
            this.childrenByParent = childrenByParent;
        }

        static SubclassIndex build(WorkspaceIndex index, long deadline) {
            Map<String, List<String>> childrenByParent = new LinkedHashMap<>();
            if (index == null) {
                return new SubclassIndex(childrenByParent);
            }

            int scanned = 0;
            for (var cls : index.classes()) {
                if (System.currentTimeMillis() > deadline) {
                    logger.debug("构建子类索引超时，已扫描 {} 个类", scanned);
                    break;
                }
                scanned++;
                byte[] bytes = cls.bytes();
                if (bytes == null) {
                    continue;
                }
                Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
                if (metadata.isEmpty()) {
                    continue;
                }
                ClassFileMetadata meta = metadata.get();
                addChild(childrenByParent, meta.superName(), cls.internalName());
                for (String itf : meta.interfaces()) {
                    addChild(childrenByParent, itf, cls.internalName());
                }
            }
            return new SubclassIndex(childrenByParent);
        }

        private static void addChild(Map<String, List<String>> childrenByParent,
                                     String parentName, String childName) {
            if (parentName == null || parentName.isBlank()
                    || childName == null || childName.isBlank()
                    || parentName.equals(childName)) {
                return;
            }
            List<String> children = childrenByParent.computeIfAbsent(parentName,
                    key -> new ArrayList<>());
            if (!children.contains(childName)) {
                children.add(childName);
            }
        }

        List<String> childrenOf(String parentName) {
            return childrenByParent.getOrDefault(parentName, List.of());
        }
    }

    /** 可变计数器，用于在递归过程中跨调用栈共享计数（避免装箱开销） */
    private static final class Counter {
        int value;
    }
}
