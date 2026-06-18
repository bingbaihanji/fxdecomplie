package com.bingbihanji.fxdecomplie.ui.inheritance;

import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import javafx.scene.control.TreeItem;
import org.objectweb.asm.ClassReader;

import java.util.HashSet;
import java.util.Set;

/**
 * 类继承关系分析服务。使用 ASM 读取常量池中的 super_class 和 interfaces。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritanceService {

    private static final int MAX_DEPTH = 20;

    private InheritanceService() { throw new AssertionError("utility class"); }

    /**
     * 构建继承树。
     * @param fullPath 目标类全路径（如 "com/example/MyClass.class"）
     * @return 继承树根节点，失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath) {
        Set<String> visited = new HashSet<>();
        String internalName = toInternal(fullPath);
        byte[] bytes = BytecodeCache.get(internalName);
        if (bytes == null) return null;

        InheritanceNode rootData = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SELF, 0);
        TreeItem<InheritanceNode> root = new TreeItem<>(rootData);
        root.setExpanded(true);
        visited.add(internalName);

        try {
            ClassReader reader = new ClassReader(bytes);
            String superName = reader.getSuperName();
            if (superName != null && !"java/lang/Object".equals(superName)) {
                buildSuperChain(superName, root, 1, visited);
            }
            for (String itf : reader.getInterfaces()) {
                TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                        new InheritanceNode(itf, simpleName(itf),
                                InheritanceNode.RelationType.INTERFACE, 1));
                root.getChildren().add(ifNode);
            }
        } catch (Exception e) {
            System.getLogger(InheritanceService.class.getName())
                    .log(System.Logger.Level.WARNING, "Failed to analyze class in inheritance tree", e);
        }

        findSubClasses(internalName, root, visited);
        return root;
    }

    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                         int depth, Set<String> visited) {
        if (depth > MAX_DEPTH || visited.contains(internalName)) return;
        visited.add(internalName);

        InheritanceNode data = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, depth);
        TreeItem<InheritanceNode> node = new TreeItem<>(data);
        parent.getChildren().add(0, node);

        byte[] bytes = BytecodeCache.get(internalName);
        if (bytes != null) {
            try {
                ClassReader reader = new ClassReader(bytes);
                String superName = reader.getSuperName();
                if (superName != null && !"java/lang/Object".equals(superName)) {
                    buildSuperChain(superName, node, depth + 1, visited);
                }
                for (String itf : reader.getInterfaces()) {
                    TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                            new InheritanceNode(itf, simpleName(itf),
                                    InheritanceNode.RelationType.INTERFACE, depth + 1));
                    node.getChildren().add(ifNode);
                }
            } catch (Exception e) {
                System.getLogger(InheritanceService.class.getName())
                        .log(System.Logger.Level.WARNING, "Failed to analyze class in inheritance tree", e);
            }
        }
    }

    private static void findSubClasses(String targetName, TreeItem<InheritanceNode> root,
                                        Set<String> visited) {
        BytecodeCache.forEach((name, bytes) -> {
            if (visited.contains(name) || name.equals(targetName)) return;
            try {
                ClassReader reader = new ClassReader(bytes);
                if (targetName.equals(reader.getSuperName())) {
                    InheritanceNode data = new InheritanceNode(name, simpleName(name),
                            InheritanceNode.RelationType.SUBCLASS, 1);
                    root.getChildren().add(new TreeItem<>(data));
                }
                for (String itf : reader.getInterfaces()) {
                    if (targetName.equals(itf)) {
                        InheritanceNode data = new InheritanceNode(name, simpleName(name),
                                InheritanceNode.RelationType.SUBCLASS, 1);
                        root.getChildren().add(new TreeItem<>(data));
                    }
                }
            } catch (Exception e) {
                System.getLogger(InheritanceService.class.getName())
                        .log(System.Logger.Level.WARNING, "Failed to analyze class in inheritance tree", e);
            }
        });
    }

    private static String toInternal(String path) {
        return path.replace(".class", "").replace("\\", "/");
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }
}
