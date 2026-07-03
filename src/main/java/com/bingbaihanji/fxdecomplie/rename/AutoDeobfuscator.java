package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 自动反混淆：扫描工作区中所有类，为混淆的短名称生成可读新名称。
 *
 * <p>借鉴 jadx 的 deobfuscation 策略：
 * <ul>
 *   <li>名称长度 ≤ 3 视为混淆名</li>
 *   <li>跳过 java.*, javax.*, kotlin.*, android.* 等标准库</li>
 *   <li>按字母序分配新名称 (A, B, ..., Z, AA, AB, ...)</li>
 * </ul>
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class AutoDeobfuscator {

    private static final int MIN_OBFUSCATED_LENGTH = 1;
    private static final int MAX_OBFUSCATED_LENGTH = 3;

    private AutoDeobfuscator() {
        throw new AssertionError("utility class");
    }

    /**
     * 扫描工作区并生成反混淆建议。
     *
     * @param workspace 目标工作区
     * @return 建议重命名列表（旧名 → 新名）
     */
    public static List<RenameEntry> scan(Workspace workspace) {
        if (workspace == null) {
            return Collections.emptyList();
        }
        List<RenameEntry> suggestions = new ArrayList<>();
        List<String> obfuscatedClasses = new ArrayList<>();
        collectObfuscatedClasses(workspace.getTreeRoot().getValue(), "", obfuscatedClasses);

        // 生成新名称：C0001, C0002, ...
        for (int i = 0; i < obfuscatedClasses.size(); i++) {
            String oldSimpleName = obfuscatedClasses.get(i);
            String newName = indexToName(i);
            String className = findClassName(workspace.getTreeRoot().getValue(), oldSimpleName);
            if (className != null) {
                suggestions.add(new RenameEntry("class", className, oldSimpleName, newName, ""));
            }
        }
        return suggestions;
    }

    /** 递归收集混淆类名 */
    private static void collectObfuscatedClasses(FileTreeNode node, String pkg,
                                                  List<String> result) {
        if (node == null) {
            return;
        }
        if (node.isClassFile()) {
            String simpleName = node.getName();
            if (simpleName.endsWith(".class")) {
                simpleName = simpleName.substring(0, simpleName.length() - 6);
            }
            // 跳过内部类 ($分隔)
            if (simpleName.contains("$")) {
                return;
            }
            // 跳过标准库包
            String fullPath = node.getFullPath();
            if (isStandardLibrary(fullPath)) {
                return;
            }
            // 混淆名判断：长度在阈值范围内的纯字母/数字组合
            if (isObfuscated(simpleName)) {
                result.add(simpleName);
            }
        }
        // 递归子节点（对于 FileTreeNode，需要遍历树）
        // FileTreeNode 不直接有 children，通过 Workspace.getTreeRoot() 的 TreeItem 遍历
        // 此方法需要配合 TreeItem 使用
    }

    /** 判断是否为混淆名 */
    public static boolean isObfuscated(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        int len = name.length();
        if (len < MIN_OBFUSCATED_LENGTH || len > MAX_OBFUSCATED_LENGTH) {
            return false;
        }
        return name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_');
    }

    /** 是否为标准库包 */
    private static boolean isStandardLibrary(String fullPath) {
        String p = fullPath.replace('\\', '/').toLowerCase();
        return p.startsWith("java/") || p.startsWith("javax/")
                || p.startsWith("kotlin/") || p.startsWith("android/")
                || p.startsWith("sun/") || p.startsWith("jdk/");
    }

    /** 索引 → 名称：0→A, 25→Z, 26→AA, ... */
    static String indexToName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index;
        do {
            sb.insert(0, (char) ('A' + n % 26));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    /** 从树中查找类名对应的全路径 */
    private static String findClassName(FileTreeNode node, String simpleName) {
        if (node == null) {
            return null;
        }
        if (node.isClassFile()) {
            String name = node.getName();
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - 6);
            }
            if (name.equals(simpleName)) {
                String fp = node.getFullPath();
                return fp.endsWith(".class") ? fp.substring(0, fp.length() - 6) : fp;
            }
        }
        return null;
    }

    /**
     * 使用 TreeItem 扫描（从 UI 树中获取子节点）。
     */
    public static List<RenameEntry> scanFromTree(javafx.scene.control.TreeItem<FileTreeNode> root) {
        if (root == null) {
            return Collections.emptyList();
        }
        List<String> obfuscated = new ArrayList<>();
        collectFromTree(root, obfuscated);

        List<RenameEntry> suggestions = new ArrayList<>();
        for (int i = 0; i < obfuscated.size(); i++) {
            String oldName = obfuscated.get(i);
            String className = findClassNameInTree(root, oldName);
            if (className != null) {
                suggestions.add(new RenameEntry("class", className, oldName,
                        indexToName(i), ""));
            }
        }
        return suggestions;
    }

    private static void collectFromTree(javafx.scene.control.TreeItem<FileTreeNode> item,
                                         List<String> result) {
        FileTreeNode node = item.getValue();
        if (node != null && node.isClassFile()) {
            String simpleName = node.getName();
            if (simpleName.endsWith(".class")) {
                simpleName = simpleName.substring(0, simpleName.length() - 6);
            }
            if (!simpleName.contains("$") && !isStandardLibrary(node.getFullPath())
                    && isObfuscated(simpleName)) {
                result.add(simpleName);
            }
        }
        for (javafx.scene.control.TreeItem<FileTreeNode> child : item.getChildren()) {
            collectFromTree(child, result);
        }
    }

    private static String findClassNameInTree(
            javafx.scene.control.TreeItem<FileTreeNode> item, String simpleName) {
        FileTreeNode node = item.getValue();
        if (node != null && node.isClassFile()) {
            String name = node.getName();
            if (name.endsWith(".class")) {
                name = name.substring(0, name.length() - 6);
            }
            if (name.equals(simpleName)) {
                String fp = node.getFullPath();
                return fp.endsWith(".class") ? fp.substring(0, fp.length() - 6) : fp;
            }
        }
        for (javafx.scene.control.TreeItem<FileTreeNode> child : item.getChildren()) {
            String found = findClassNameInTree(child, simpleName);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
