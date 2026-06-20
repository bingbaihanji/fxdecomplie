package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 类继承关系分析服务使用 ASM 读取常量池中的 super_class 和 interfaces
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritanceService {

    private static final Logger logger = LoggerFactory.getLogger(InheritanceService.class);

    private static final int MAX_DEPTH = 20;

    private InheritanceService() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建继承树
     * @param fullPath 目标类全路径(如 "com/example/MyClass.class")
     * @return 继承树根节点,失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath) {
        return buildTree(fullPath, null);
    }

    /**
     * 构建继承树
     * @param fullPath 目标类全路径(如 "com/example/MyClass.class")
     * @param index 当前工作区索引,优先用于读取字节码
     * @return 继承树根节点,失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath, WorkspaceIndex index) {
        Set<String> visited = new HashSet<>();
        String internalName = toInternal(fullPath);
        byte[] bytes = getBytes(index, internalName);
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
            if (superName != null && !"java/lang/Object".equals(superName)) {
                buildSuperChain(superName, root, 1, visited, index);
            }
            for (String itf : meta.interfaces()) {
                TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                        new InheritanceNode(itf, simpleName(itf),
                                InheritanceNode.RelationType.INTERFACE, 1));
                root.getChildren().add(ifNode);
            }
        } else {
            logger.warn("解析类元数据用于继承树失败：{}", fullPath);
        }

        findSubClasses(internalName, root, visited, index);
        return root;
    }

    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                        int depth, Set<String> visited) {
        buildSuperChain(internalName, parent, depth, visited, null);
    }

    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                        int depth, Set<String> visited, WorkspaceIndex index) {
        if (depth > MAX_DEPTH || visited.contains(internalName)) return;
        visited.add(internalName);

        InheritanceNode data = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, depth);
        TreeItem<InheritanceNode> node = new TreeItem<>(data);
        parent.getChildren().add(0, node);

        byte[] bytes = getBytes(index, internalName);
        if (bytes != null) {
            Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
            if (metadata.isPresent()) {
                ClassFileMetadata meta = metadata.get();
                String superName = meta.superName();
                if (superName != null && !"java/lang/Object".equals(superName)) {
                    buildSuperChain(superName, node, depth + 1, visited, index);
                }
                for (String itf : meta.interfaces()) {
                    TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                            new InheritanceNode(itf, simpleName(itf),
                                    InheritanceNode.RelationType.INTERFACE, depth + 1));
                    node.getChildren().add(ifNode);
                }
            } else {
                logger.warn("解析类元数据用于继承树失败：{}", internalName);
            }
        }
    }

    private static void findSubClasses(String targetName, TreeItem<InheritanceNode> root,
                                       Set<String> visited) {
        findSubClasses(targetName, root, visited, null);
    }

    private static void findSubClasses(String targetName, TreeItem<InheritanceNode> root,
                                       Set<String> visited, WorkspaceIndex index) {
        if (index != null) {
            index.classes().forEach(cls -> {
                byte[] bytes = cls.bytes();
                if (bytes != null) {
                    addSubClassIfMatches(targetName, root, visited, cls.internalName(), bytes);
                }
            });
        }
    }

    private static void addSubClassIfMatches(String targetName, TreeItem<InheritanceNode> root,
                                             Set<String> visited, String name, byte[] bytes) {
        if (visited.contains(name) || name.equals(targetName)) return;
        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isEmpty()) {
            logger.warn("解析类元数据用于继承树失败：{}", name);
            return;
        }
        ClassFileMetadata meta = metadata.get();
        if (targetName.equals(meta.superName())) {
            InheritanceNode data = new InheritanceNode(name, simpleName(name),
                    InheritanceNode.RelationType.SUBCLASS, 1);
            root.getChildren().add(new TreeItem<>(data));
            return;
        }
        for (String itf : meta.interfaces()) {
            if (targetName.equals(itf)) {
                InheritanceNode data = new InheritanceNode(name, simpleName(name),
                        InheritanceNode.RelationType.SUBCLASS, 1);
                root.getChildren().add(new TreeItem<>(data));
                return;
            }
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
}
