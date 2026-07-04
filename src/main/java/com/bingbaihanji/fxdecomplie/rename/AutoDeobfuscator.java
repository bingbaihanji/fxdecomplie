package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.model.*;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自动反混淆：扫描工作区中所有类、字段和方法，为混淆名称生成可读新名称。
 *
 * <p>实现取 jadx 的核心策略：先用条件筛选需要重命名的节点，再用稳定的 alias
 * provider 生成类 C、字段 f、方法 m 前缀的新名称。</p>
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class AutoDeobfuscator {

    private static final Logger logger = LoggerFactory.getLogger(AutoDeobfuscator.class);

    private static final int MIN_OBFUSCATED_LENGTH = 1;
    private static final int MAX_OBFUSCATED_LENGTH = 3;
    private static final Set<String> STANDARD_PREFIXES = Set.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "kotlin/",
            "kotlinx/", "android/", "androidx/");
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "android/support/v4/", "android/support/v7/", "androidx/core/os/",
            "androidx/annotation/");
    private static final Set<String> NEVER_RENAME_METHODS = Set.of(
            "<init>", "<clinit>", "main", "values", "valueOf");
    private static final Set<String> OBJECT_METHODS = Set.of(
            "toString", "hashCode", "equals", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait");
    private static final Set<String> COMMON_SHORT_NAMES = Set.of(
            "id", "db", "ui", "io", "ok", "no", "tag", "key", "val", "url", "uri",
            "sql", "xml", "api", "dto", "dao", "vo", "bo", "po", "to", "ctx",
            "cfg", "src", "dst", "min", "max", "len", "idx", "tmp", "buf",
            "msg", "req", "res", "err", "log", "map", "set", "num");

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
        WorkspaceIndex index = workspace.getOrBuildIndex();
        logger.info("scan(workspace): index has {} classes", index.classes().size());
        List<RenameEntry> suggestions = new ArrayList<>(scan(index));
        logger.info("scan(workspace): after scan(index), {} suggestions", suggestions.size());
        mergeTreeClassSuggestions(workspace.getTreeRoot(), index, suggestions);
        logger.info("scan(workspace): after mergeTreeClassSuggestions, {} suggestions total",
                suggestions.size());
        return suggestions;
    }

    /** 根据完整索引扫描类、字段和方法。 */
    public static List<RenameEntry> scan(WorkspaceIndex index) {
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return Collections.emptyList();
        }
        AliasProvider aliases = new AliasProvider();
        List<RenameEntry> suggestions = new ArrayList<>();
        Set<String> emitted = new LinkedHashSet<>();
        Map<String, Set<String>> usedByClass = collectExistingMemberNames(index);

        List<ClassIndexEntry> classes = index.classes().stream()
                .sorted(Comparator.comparing(ClassIndexEntry::internalName))
                .toList();
        logger.info("AutoDeobfuscator.scan(index): {} classes in index", classes.size());
        for (ClassIndexEntry cls : classes) {
            if (shouldSkipClass(cls)) {
                logger.debug("  skip class {} (shouldSkipClass)", cls.internalName());
                continue;
            }
            String owner = cls.internalName();
            String simple = simpleName(owner);
            if (shouldRenameClass(simple, owner, index)) {
                String visibleName = visibleClassName(simple);
                String newName = aliases.forClass(visibleName);
                logger.info("  rename class: {} -> {} (internal={})", visibleName, newName, owner);
                add(suggestions, emitted, new RenameEntry(
                        RenameService.TYPE_CLASS, owner, visibleName, newName, ""));
            } else {
                logger.debug("  skip class {} (shouldRenameClass=false, simple={})", owner, simple);
            }

            Set<String> usedNames = usedByClass.getOrDefault(owner, new HashSet<>());
            Map<String, Integer> fieldNameCount = countNames(cls.fields());
            for (MemberIndexEntry field : cls.fields()) {
                if (shouldRenameField(field, fieldNameCount)) {
                    String alias = uniqueMemberAlias(aliases.forField(field.name()), usedNames);
                    add(suggestions, emitted, new RenameEntry(
                            RenameService.TYPE_FIELD, owner, field.name(), alias, field.descriptor()));
                }
            }

            Map<String, Integer> methodSignatureCount = countMethodSignatures(cls.methods());
            for (MemberIndexEntry method : cls.methods()) {
                if (shouldRenameMethod(method, methodSignatureCount)) {
                    String prefix = isLikelyOverride(method) ? aliases.forOverrideMethod(method.name())
                            : aliases.forMethod(method.name());
                    String alias = uniqueMemberAlias(prefix, usedNames);
                    add(suggestions, emitted, new RenameEntry(
                            RenameService.TYPE_METHOD, owner, method.name(), alias, method.descriptor()));
                }
            }
        }
        return suggestions;
    }

    /** 判断是否为混淆名 */
    public static boolean isObfuscated(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (isCommonShortName(name)) {
            return false;
        }
        int len = name.length();
        if (len < MIN_OBFUSCATED_LENGTH || len > MAX_OBFUSCATED_LENGTH) {
            return false;
        }
        return name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '$');
    }

    public static boolean isCommonShortName(String name) {
        return name != null && COMMON_SHORT_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }

    /** 是否为标准库包 */
    private static boolean isStandardLibrary(String fullPath) {
        String p = fullPath.replace('\\', '/').toLowerCase();
        for (String prefix : STANDARD_PREFIXES) {
            if (p.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 使用 TreeItem 扫描（从 UI 树中获取子节点）。
     */
    public static List<RenameEntry> scanFromTree(TreeItem<FileTreeNode> root) {
        if (root == null) {
            return Collections.emptyList();
        }
        List<FileTreeNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        logger.info("scanFromTree: collected {} total nodes from tree", nodes.size());
        long classNodeCount = nodes.stream().filter(FileTreeNode::isClassFile).count();
        logger.info("scanFromTree: {} class-file nodes among them", classNodeCount);
        WorkspaceIndex index = WorkspaceIndex.build(nodes);
        logger.info("scanFromTree: index built with {} classes, {} resources",
                index.classes().size(), index.resources().size());
        List<RenameEntry> suggestions = new ArrayList<>(scan(index));
        logger.info("scanFromTree: index scan produced {} suggestions ({} classes)",
                suggestions.size(),
                suggestions.stream().filter(e -> RenameService.TYPE_CLASS.equals(e.type())).count());
        mergeTreeClassSuggestions(root, index, suggestions);
        logger.info("scanFromTree: after merge, total {} suggestions", suggestions.size());
        for (RenameEntry e : suggestions) {
            if (RenameService.TYPE_CLASS.equals(e.type())) {
                logger.info("  class: {} -> {} (className={})", e.oldName(), e.newName(), e.className());
            }
        }
        return suggestions;
    }

    private static void collectNodes(TreeItem<FileTreeNode> item, List<FileTreeNode> result) {
        FileTreeNode node = item.getValue();
        if (node != null) {
            result.add(node);
        }
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            collectNodes(child, result);
        }
    }

    private static void mergeTreeClassSuggestions(TreeItem<FileTreeNode> root,
                                                  WorkspaceIndex index,
                                                  List<RenameEntry> suggestions) {
        if (root == null || suggestions == null) {
            return;
        }
        Set<String> emittedClasses = new HashSet<>();
        int classAliasIndex = 0;
        for (RenameEntry entry : suggestions) {
            if (RenameService.TYPE_CLASS.equals(entry.type())) {
                emittedClasses.add(entry.className());
                classAliasIndex++;
            }
        }

        List<FileTreeNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        int[] nextClassAlias = {classAliasIndex};
        nodes.stream()
                .filter(FileTreeNode::isClassFile)
                .sorted(Comparator.comparing(FileTreeNode::getFullPath))
                .forEach(node -> {
                    String internalName = internalNameFromPath(node.getFullPath());
                    if (internalName.isBlank() || emittedClasses.contains(internalName)) {
                        if (!internalName.isBlank() && emittedClasses.contains(internalName)) {
                            logger.debug("mergeTreeClass: skip already-emitted {}", internalName);
                        }
                        return;
                    }
                    if (shouldSkipClassPath(internalName)) {
                        logger.debug("mergeTreeClass: skip shouldSkipClassPath {}", internalName);
                        return;
                    }
                    String simple = simpleName(internalName);
                    if (!shouldRenameClass(simple, internalName,
                            index == null ? WorkspaceIndex.EMPTY : index)) {
                        logger.debug("mergeTreeClass: skip shouldRenameClass=false {} (simple={})",
                                internalName, simple);
                        return;
                    }
                    String visibleName = visibleClassName(simple);
                    RenameEntry entry = new RenameEntry(RenameService.TYPE_CLASS,
                            internalName, visibleName,
                            classAlias(nextClassAlias[0]++, visibleName), "");
                    logger.info("mergeTreeClass: adding {} -> {} (className={})",
                            visibleName, entry.newName(), internalName);
                    emittedClasses.add(internalName);
                    suggestions.add(entry);
                });
    }

    private static boolean shouldSkipClassPath(String internalName) {
        String path = internalName.toLowerCase(Locale.ROOT);
        if (isStandardLibrary(path)) {
            return true;
        }
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String simple = simpleName(internalName);
        return simple.equals("R") || simple.startsWith("R$") || simple.equals("BuildConfig");
    }

    private static boolean shouldSkipClass(ClassIndexEntry cls) {
        String path = cls.internalName().toLowerCase(Locale.ROOT);
        if (isStandardLibrary(path)) {
            return true;
        }
        for (String prefix : WHITELIST_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        String simple = simpleName(cls.internalName());
        return simple.equals("R") || simple.startsWith("R$") || simple.equals("BuildConfig");
    }

    private static boolean shouldRenameClass(String simpleName, String internalName,
                                             WorkspaceIndex index) {
        if (simpleName == null || simpleName.isBlank()) {
            return false;
        }
        if (simpleName.contains("$")) {
            String inner = simpleName.substring(simpleName.lastIndexOf('$') + 1);
            if (inner.chars().allMatch(Character::isDigit)) {
                return false;
            }
            simpleName = inner;
        }
        return shouldRenameName(simpleName) || collidesWithPackage(simpleName, index);
    }

    private static boolean shouldRenameField(MemberIndexEntry field,
                                             Map<String, Integer> fieldNameCount) {
        return shouldRenameName(field.name()) || fieldNameCount.getOrDefault(field.name(), 0) > 1;
    }

    private static boolean shouldRenameMethod(MemberIndexEntry method,
                                              Map<String, Integer> signatureCount) {
        String name = method.name();
        if (NEVER_RENAME_METHODS.contains(name) || OBJECT_METHODS.contains(name)) {
            return false;
        }
        return shouldRenameName(name)
                || signatureCount.getOrDefault(method.name() + method.descriptor(), 0) > 1;
    }

    private static boolean shouldRenameName(String name) {
        return isObfuscated(name) || !RenameService.isValidName(name) || !isPrintableAscii(name);
    }

    private static boolean isPrintableAscii(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch < 32 || ch > 126) {
                return false;
            }
        }
        return true;
    }

    private static boolean collidesWithPackage(String simpleName, WorkspaceIndex index) {
        Set<String> packageParts = new HashSet<>();
        for (ClassIndexEntry cls : index.classes()) {
            String pkg = packageName(cls.internalName());
            if (!pkg.isBlank()) {
                for (String part : pkg.split("/")) {
                    if (!part.isBlank()) {
                        packageParts.add(part);
                    }
                }
            }
        }
        return packageParts.contains(simpleName);
    }

    private static Map<String, Set<String>> collectExistingMemberNames(WorkspaceIndex index) {
        Map<String, Set<String>> result = new HashMap<>();
        for (ClassIndexEntry cls : index.classes()) {
            Set<String> names = new HashSet<>();
            cls.fields().forEach(field -> names.add(field.name()));
            cls.methods().forEach(method -> names.add(method.name()));
            result.put(cls.internalName(), names);
        }
        return result;
    }

    private static Map<String, Integer> countNames(List<MemberIndexEntry> members) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MemberIndexEntry member : members) {
            counts.merge(member.name(), 1, Integer::sum);
        }
        return counts;
    }

    private static Map<String, Integer> countMethodSignatures(List<MemberIndexEntry> methods) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MemberIndexEntry method : methods) {
            counts.merge(method.name() + method.descriptor(), 1, Integer::sum);
        }
        return counts;
    }

    private static void add(List<RenameEntry> suggestions, Set<String> emitted,
                            RenameEntry entry) {
        if (entry.oldName().equals(entry.newName())) {
            return;
        }
        String key = entry.type() + '\0' + entry.className() + '\0'
                + entry.oldName() + '\0' + entry.desc();
        if (emitted.add(key)) {
            suggestions.add(entry);
        }
    }

    private static String uniqueMemberAlias(String alias, Set<String> usedNames) {
        String result = alias;
        int index = 2;
        while (usedNames.contains(result) || !RenameService.isValidName(result)) {
            result = alias + '_' + index++;
        }
        usedNames.add(result);
        return result;
    }

    private static boolean isLikelyOverride(MemberIndexEntry method) {
        String name = method.name();
        return OBJECT_METHODS.contains(name) || name.startsWith("on")
                || name.startsWith("run") || name.startsWith("call");
    }

    private static String packageName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static String simpleName(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? internalName : internalName.substring(slash + 1);
    }

    private static String internalNameFromPath(String fullPath) {
        if (fullPath == null || fullPath.isBlank()) {
            return "";
        }
        String normalized = fullPath.replace('\\', '/');
        return normalized.endsWith(".class")
                ? normalized.substring(0, normalized.length() - ".class".length())
                : normalized;
    }

    private static String visibleClassName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return "";
        }
        int dollar = simpleName.lastIndexOf('$');
        return dollar >= 0 ? simpleName.substring(dollar + 1) : simpleName;
    }

    private static String classAlias(int index, String oldName) {
        return "C" + "%04d".formatted(index) + sanitizeNamePart(oldName);
    }

    private static String sanitizeNamePart(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch >= 32 && ch <= 126 && Character.isJavaIdentifierPart(ch)) {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static final class AliasProvider {
        private int clsIndex;
        private int fldIndex;
        private int mthIndex;

        String forClass(String oldName) {
            return classAlias(clsIndex++, oldName);
        }

        String forField(String oldName) {
            return "f" + fldIndex++ + sanitizeNamePart(oldName);
        }

        String forMethod(String oldName) {
            return "m" + mthIndex++ + sanitizeNamePart(oldName);
        }

        String forOverrideMethod(String oldName) {
            return "mo" + mthIndex++ + sanitizeNamePart(oldName);
        }
    }
}
