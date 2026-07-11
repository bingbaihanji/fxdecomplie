package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 类继承关系分析服务
 *
 * <p>使用 {@link ClassFileParser} 快速读取常量池中的 super_class 和 interfaces,
 * 无需完整解析 class 文件向上追溯父类链,向下查找子类树,构建可展示的
 * {@link TreeItem} 继承层次</p>
 *
 * <p>子类查找通过 {@link SubclassIndex} 预建索引实现 O(1) 查询,索引构建
 * 有超时和上限保护({@link #SUBCLASS_TIMEOUT_MS} / {@link #MAX_SUBCLASSES}),
 * 避免大 workspace 下长时间阻塞</p>
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritanceService {

    private static final Logger log = LoggerFactory.getLogger(InheritanceService.class);

    /** 父类链追溯最大深度,防止无限循环 */
    private static final int MAX_DEPTH = 20;
    /** 子类树展示最大数量上限 */
    private static final int MAX_SUBCLASSES = 200;
    /** 子类索引构建超时时间(毫秒) */
    private static final long SUBCLASS_TIMEOUT_MS = 3000;

    private InheritanceService() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建继承树
     *
     * @param fullPath 目标类全路径,如 "com/example/MyClass.class"
     * @return 继承树根节点,失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath) {
        return buildTree(fullPath, null);
    }

    /**
     * 构建继承树
     *
     * @param fullPath 目标类全路径,如 "com/example/MyClass.class"
     * @param index 当前工作区索引,优先用于读取字节码
     * @return 继承树根节点,失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath, WorkspaceIndex index) {
        return buildTree(fullPath, index, null);
    }

    /**
     * 构建继承树rootBytes 用于当前打开 class 的即时构图,避免为了一个弹窗等待完整工作区索引
     *
     * @param fullPath 目标类全路径,如 "com/example/MyClass.class"
     * @param index 当前工作区索引,可为空；有索引时补充父类链和完整子类树
     * @param rootBytes 当前目标类字节码,优先于 index 读取
     * @return 继承树根节点,失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath, WorkspaceIndex index,
                                                      byte[] rootBytes) {
        // 防循环访问记录
        Set<String> visited = new ArraySet<>();
        String internalName = toInternal(fullPath);
        // EMPTY 哨兵等效于无索引
        WorkspaceIndex usableIndex = index == WorkspaceIndex.EMPTY ? null : index;
        // 优先使用传入的字节码,否则从索引中读取
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

        // 用 ASM 快速解析常量池,获取父类和接口信息
        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isPresent()) {
            ClassFileMetadata meta = metadata.get();
            String superName = meta.superName();
            if (superName != null) {
                // 递归追溯父类链
                buildSuperChain(superName, root, 1, visited, usableIndex);
            }
            // 添加当前类直接实现的接口
            for (String itf : meta.interfaces()) {
                TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                        new InheritanceNode(itf, simpleName(itf),
                                InheritanceNode.RelationType.INTERFACE, 1));
                root.getChildren().add(ifNode);
            }
        } else {
            log.warn("解析类元数据用于继承树失败: {}", fullPath);
        }

        // 补充子类树(需要工作区索引)
        appendSubClassTree(internalName, root, visited, usableIndex);
        return root;
    }

    /**
     * 递归向上追溯父类链,将每个父类节点插入到父节点子列表的最前面(保持层级顺序)
     * 遇到 java/lang/Object 或超过最大深度时停止
     */
    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                        int depth, Set<String> visited, WorkspaceIndex index) {
        if (depth > MAX_DEPTH || visited.contains(internalName)) {
            return;
        }
        visited.add(internalName);

        InheritanceNode data = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, depth);
        TreeItem<InheritanceNode> node = new TreeItem<>(data);
        // 插入到索引 0 位置,使父类链按层级从上到下排列
        parent.getChildren().add(0, node);

        if ("java/lang/Object".equals(internalName)) {
            return; // 到达继承链顶端,停止追溯
        }

        byte[] bytes = getBytes(index, internalName);
        if (bytes == null) {
            return;
        }

        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isEmpty()) {
            log.warn("解析类元数据用于继承树失败: {}", internalName);
            return;
        }

        ClassFileMetadata meta = metadata.get();
        String superName = meta.superName();
        if (superName != null) {
            buildSuperChain(superName, node, depth + 1, visited, index);
        }
        // 将父类实现的接口也展示出来
        for (String itf : meta.interfaces()) {
            TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                    new InheritanceNode(itf, simpleName(itf),
                            InheritanceNode.RelationType.INTERFACE, depth + 1));
            node.getChildren().add(ifNode);
        }
    }

    /** 构建子类反向索引,并递归展开子类树无索引时跳过 */
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

    /**
     * 递归展开子类树,受深度、总数、超时三重限制
     * 通过 Counter 跨递归层级共享计数,避免装箱开销
     */
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

    /** 从工作区索引中按内部名称获取类字节码,索引为空时返回 null */
    private static byte[] getBytes(WorkspaceIndex index, String internalName) {
        return index == null ? null : index.getClassBytes(internalName);
    }

    /** 将文件路径转为 JVM 内部名称格式(去除 .class 后缀,反斜杠替换为正斜杠) */
    private static String toInternal(String path) {
        if (path.endsWith(".class")) {
            return path.substring(0, path.length() - 6).replace("\\", "/");
        }
        return path.replace("\\", "/");
    }

    /** 从内部名称中提取简短类名(最后一个 / 之后的部分) */
    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    /**
     * 子类反向索引：parent → children 的映射,O(1) 查询子类
     *
     * <p>构建时遍历 workspace 中所有 class,提取每个 class 的父类和接口,
     * 建立反向引用注意：同一 child 可能多次出现(实现多接口的类),
     * 但每个 parent→child 对只记录一次</p>
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
            // 遍历工作区中所有类,建立父类→子类的反向映射
            for (var cls : index.classes()) {
                if (System.currentTimeMillis() > deadline) {
                    log.debug("构建子类索引超时,已扫描 {} 个类", scanned);
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

        /**
         * 向反向索引中添加一条父子关系过滤空值和自引用
         * 同一 parent→child 对只记录一次
         */
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

        /** 查询指定父类的所有直接子类/实现类 */
        List<String> childrenOf(String parentName) {
            return childrenByParent.getOrDefault(parentName, List.of());
        }
    }

    /** 可变计数器,用于在递归过程中跨调用栈共享计数(避免装箱开销) */
    private static final class Counter {
        int value;
    }
}
