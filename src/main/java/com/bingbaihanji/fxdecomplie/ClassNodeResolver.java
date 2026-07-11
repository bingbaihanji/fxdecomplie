package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import com.bingbaihanji.fxdecomplie.util.text.JavaSourceAnalyzer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

/** 类名/令牌 → 文件树节点的多策略解析器 (无状态,从 NavigationController 抽取,便于单测) */
public final class ClassNodeResolver {

    private ClassNodeResolver() {
        throw new AssertionError("utility class");
    }

    /** 根据引用对象在工作区文件树中查找目标节点(含重命名回查) */
    public static FileTreeNode findNodeForReference(Workspace workspace, CodeMetadata.Reference reference) {
        if (workspace == null || reference == null || reference.targetClass() == null) {
            return null;
        }
        FileTreeNode direct = findClassPath(workspace, reference.targetClass());
        if (direct != null) {
            return direct;
        }
        String wsHash = CommentScope.workspaceHash(workspace);
        List<String> originals = RenameService
                .originalInternalNameCandidates(reference.targetClass(), wsHash);
        for (String original : originals) {
            FileTreeNode node = findClassPathRaw(workspace, original);
            if (node != null) {
                return node;
            }
        }
        String original = RenameService
                .originalInternalName(reference.targetClass(), wsHash);
        return findClassPathRaw(workspace, original);
    }

    /** 在工作区中按 token 查找文件树节点(多策略回退：直接查找→同包→import→重命名→索引→遍历) */
    public static FileTreeNode findNodeForToken(Workspace workspace, WorkspaceIndex index,
                                                String token, String currentClassName,
                                                String sourceCode, boolean allowClassLookup) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        if (!allowClassLookup && !JavaSourceAnalyzer.shouldSearchWorkspaceForClassToken(token)) {
            return null;
        }

        String normalized = token.replace('.', '/');
        String currentInternal = JavaSourceAnalyzer.normalizeInternalClassName(currentClassName);
        boolean qualifiedToken = normalized.contains("/");
        FileTreeNode direct = qualifiedToken
                ? findClassPath(workspace, normalized)
                : findClassPathRaw(workspace, normalized);
        if (direct != null && !ClassNameUtil.sameInternalName(direct.getFullPath(), currentClassName)) {
            return direct;
        }

        String currentPackage = JavaSourceAnalyzer.packageName(currentInternal);
        if (JavaSourceAnalyzer.isRelativeClassToken(token) && !currentPackage.isBlank()) {
            FileTreeNode samePackageRelative = findClassPath(workspace,
                    currentPackage + "/" + normalized);
            if (samePackageRelative != null
                    && !ClassNameUtil.sameInternalName(samePackageRelative.getFullPath(), currentClassName)) {
                return samePackageRelative;
            }
        }

        FileTreeNode sourceResolved = findNodeFromSourceImports(workspace, token,
                currentClassName, sourceCode);
        if (sourceResolved != null) {
            return sourceResolved;
        }

        FileTreeNode renamedResolved = findNodeFromRenameDisplayIndex(
                workspace, normalized, currentInternal);
        if (renamedResolved != null
                && !ClassNameUtil.sameInternalName(renamedResolved.getFullPath(), currentClassName)) {
            return renamedResolved;
        }

        FileTreeNode indexResolved = findNodeBySimpleNameInIndex(workspace, index, token, currentInternal);
        if (indexResolved != null) {
            return indexResolved;
        }
        if (index != null && index != WorkspaceIndex.EMPTY) {
            return null;
        }
        return findNodeBySimpleNameInTree(workspace, token, currentClassName);
    }

    /** 在工作区索引中按简单类名查找节点(优先同包匹配,回退到首个匹配) */
    private static FileTreeNode findNodeBySimpleNameInIndex(Workspace workspace, WorkspaceIndex index,
                                                            String token, String currentInternal) {
        if (workspace == null || index == null || index == WorkspaceIndex.EMPTY
                || token == null || token.isBlank()) {
            return null;
        }
        String normalized = token.replace('.', '/');
        String simpleToken = JavaSourceAnalyzer.tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        FileTreeNode firstMatch = null;
        for (var cls : index.classes()) {
            if (ClassNameUtil.sameInternalName(cls.internalName(), currentInternal)) {
                continue;
            }
            String indexedSimple = cls.simpleName();
            indexedSimple = indexedSimple.endsWith(".class") ? indexedSimple.substring(0, indexedSimple.length() - 6) : indexedSimple;
            if (ClassNameUtil.sameInternalName(cls.internalName(), normalized)
                    || ClassNameUtil.sameInternalName(cls.fullPath(), normalized)
                    || indexedSimple.equals(simpleToken)
                    || indexedSimple.endsWith("$" + simpleToken)) {
                FileTreeNode node = workspace.findNodeByPath(cls.fullPath());
                if (node == null) {
                    continue;
                }
                if (firstMatch == null) {
                    firstMatch = node;
                }
                if (JavaSourceAnalyzer.samePackage(currentInternal, cls.internalName())) {
                    return node;
                }
            }
        }
        return firstMatch;
    }

    /** 通过重命名/反混淆映射反向查找原类名对应的树节点 */
    private static FileTreeNode findNodeFromRenameDisplayIndex(Workspace workspace, String token,
                                                               String currentInternalName) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String workspaceHash = CommentScope.workspaceHash(workspace);
        String normalized = JavaSourceAnalyzer.normalizeInternalClassName(token);
        String simple = JavaSourceAnalyzer.tokenSimpleName(normalized);
        String currentPackage = JavaSourceAnalyzer.packageName(currentInternalName);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);
        if (!currentPackage.isBlank() && !simple.isBlank()) {
            candidates.add(currentPackage + "/" + simple);
        }
        candidates.add(simple);
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            List<String> originals = RenameService
                    .originalInternalNameCandidates(candidate, workspaceHash);
            FileTreeNode node = bestNodeForOriginalCandidates(workspace, originals, currentInternalName);
            if (node != null) {
                return node;
            }
        }
        return null;
    }

    /** 从原始类名候选列表中选择最佳匹配的树节点(优先同包) */
    private static FileTreeNode bestNodeForOriginalCandidates(Workspace workspace, List<String> originals,
                                                              String currentInternalName) {
        if (workspace == null || originals == null || originals.isEmpty()) {
            return null;
        }
        String current = JavaSourceAnalyzer.normalizeInternalClassName(currentInternalName);
        FileTreeNode best = null;
        int bestScore = Integer.MAX_VALUE;
        for (String original : originals) {
            FileTreeNode node = findClassPathRaw(workspace, original);
            if (node == null || ClassNameUtil.sameInternalName(node.getFullPath(), current)) {
                continue;
            }
            String normalizedOriginal = JavaSourceAnalyzer.normalizeInternalClassName(original);
            int score = JavaSourceAnalyzer.samePackage(current, normalizedOriginal) ? 0 : 1;
            if (score < bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }

    /** 通过解析源码中的 import 语句解析 token 对应的类节点 */
    private static FileTreeNode findNodeFromSourceImports(Workspace workspace, String token,
                                                          String currentClassName, String sourceCode) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String simpleToken = JavaSourceAnalyzer.tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return null;
        }
        String currentInternal = JavaSourceAnalyzer.normalizeInternalClassName(currentClassName);
        String currentPackage = JavaSourceAnalyzer.packageName(currentInternal);
        FileTreeNode samePackage = findClassPath(workspace,
                currentPackage.isBlank() ? simpleToken : currentPackage + "/" + simpleToken);
        if (samePackage != null && !ClassNameUtil.sameInternalName(samePackage.getFullPath(), currentClassName)) {
            return samePackage;
        }

        if (sourceCode == null || sourceCode.isBlank()) {
            return null;
        }
        for (String line : sourceCode.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            Matcher matcher = JavaSourceAnalyzer.IMPORT_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String imported = matcher.group(2);
            if (imported == null || imported.isBlank()) {
                continue;
            }
            if (imported.endsWith(".*")) {
                String packagePath = imported.substring(0, imported.length() - 2).replace('.', '/');
                FileTreeNode wildcard = findClassPath(workspace, packagePath + "/" + simpleToken);
                if (wildcard != null) {
                    return wildcard;
                }
                continue;
            }

            String importedSimple = JavaSourceAnalyzer.tokenSimpleName(imported);
            importedSimple = importedSimple.endsWith(".class") ? importedSimple.substring(0, importedSimple.length() - 6) : importedSimple;
            if (importedSimple.equals(simpleToken) || imported.endsWith("." + token)) {
                FileTreeNode importedNode = findClassPath(workspace, imported.replace('.', '/'));
                if (importedNode != null) {
                    return importedNode;
                }
                FileTreeNode innerNode = findClassPath(workspace, JavaSourceAnalyzer.toInnerClassPath(imported));
                if (innerNode != null) {
                    return innerNode;
                }
            }
        }
        return null;
    }

    /** BFS 遍历整个文件树按简单类名查找节点(最慢的回退策略) */
    public static FileTreeNode findNodeBySimpleNameInTree(Workspace workspace, String token,
                                                          String currentClassName) {
        if (workspace == null || token == null || token.isBlank()) {
            return null;
        }
        String simpleToken = JavaSourceAnalyzer.tokenSimpleName(token);
        simpleToken = simpleToken.endsWith(".class") ? simpleToken.substring(0, simpleToken.length() - 6) : simpleToken;
        if (simpleToken.isBlank()) {
            return null;
        }
        String expectedClassFile = simpleToken + ".class";
        String currentInternal = JavaSourceAnalyzer.normalizeInternalClassName(currentClassName);
        String currentPackage = JavaSourceAnalyzer.packageName(currentInternal);
        List<FileTreeNode> matches = new ArrayList<>();
        FileTreeModel root = workspace.getTreeRoot();
        if (root == null) {
            return null;
        }
        ArrayDeque<FileTreeModel> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            FileTreeModel item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null && node.isClassFile()
                    && !ClassNameUtil.sameInternalName(node.getFullPath(), currentClassName)
                    && JavaSourceAnalyzer.matchesSimpleClassName(node, simpleToken, expectedClassFile)) {
                matches.add(node);
            }
            queue.addAll(item.getChildren());
        }
        return matches.stream()
                .min(Comparator.comparingInt(node ->
                        JavaSourceAnalyzer.samePackage(currentInternal, JavaSourceAnalyzer.normalizeInternalClassName(node.getFullPath())) ? 0
                                : JavaSourceAnalyzer.packageName(JavaSourceAnalyzer.normalizeInternalClassName(node.getFullPath()))
                                .equals(currentPackage) ? 1 : 2))
                .orElse(null);
    }

    /** 在工作区文件树中按内部名查找类节点(含重命名回退查找) */
    private static FileTreeNode findClassPath(Workspace workspace, String internalName) {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        FileTreeNode raw = findClassPathRaw(workspace, internalName);
        if (raw != null) {
            return raw;
        }
        String wsHash = CommentScope.workspaceHash(workspace);
        String original = RenameService
                .originalInternalName(internalName, wsHash);
        if (!JavaSourceAnalyzer.normalizeInternalClassName(original).equals(JavaSourceAnalyzer.normalizeInternalClassName(internalName))) {
            return findClassPathRaw(workspace, original);
        }
        return null;
    }

    /** 直接在工作区文件树中按内部名查找类节点(不含重命名回退) */
    private static FileTreeNode findClassPathRaw(Workspace workspace, String internalName) {
        if (workspace == null || internalName == null || internalName.isBlank()) {
            return null;
        }
        for (String candidate : ClassNameUtil.classFilePathCandidates(internalName)) {
            FileTreeNode node = workspace.findNodeByPath(candidate);
            if (node != null) {
                return node;
            }
        }
        return null;
    }
}
