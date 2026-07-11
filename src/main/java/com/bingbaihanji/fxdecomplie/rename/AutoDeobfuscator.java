package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.model.*;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;
import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 自动反混淆：扫描工作区中所有类、字段和方法,为混淆名称生成可读新名称
 *
 * <p>实现取 jadx 的核心策略：先用条件筛选需要重命名的节点,再用稳定的 alias
 * provider 生成类 C、字段 f、方法 m 前缀的新名称</p>
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class AutoDeobfuscator {

    private static final Logger log = LoggerFactory.getLogger(AutoDeobfuscator.class);

    /** 混淆名长度范围：1-3 字符为典型的混淆名(如 a, bb, cCd) */
    private static final int MIN_OBFUSCATED_LENGTH = 1;
    private static final int MAX_OBFUSCATED_LENGTH = 3;
    /** 标准/第三方库前缀,这些包下的类不需要反混淆 */
    private static final Set<String> STANDARD_PREFIXES = Set.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "kotlin/",
            "kotlinx/", "android/", "androidx/");
    /** 白名单前缀：即使匹配标准前缀也仍然扫描(某些 support 库可能需要反混淆) */
    private static final Set<String> WHITELIST_PREFIXES = Set.of(
            "android/support/v4/", "android/support/v7/", "androidx/core/os/",
            "androidx/annotation/");
    /** 永远不重命名的方法：构造器、静态初始化器、main 方法等 */
    private static final Set<String> NEVER_RENAME_METHODS = Set.of(
            "<init>", "<clinit>", "main", "values", "valueOf");
    /** java.lang.Object 的方法,不应重命名以避免破坏继承链 */
    private static final Set<String> OBJECT_METHODS = Set.of(
            "toString", "hashCode", "equals", "clone", "finalize", "getClass",
            "notify", "notifyAll", "wait");
    /** 常见的短名称(如 id, db, ui, io 等),这些不应被当作混淆名处理 */
    private static final Set<String> COMMON_SHORT_NAMES = Set.of(
            "id", "db", "ui", "io", "ok", "no", "tag", "key", "val", "url", "uri",
            "sql", "xml", "api", "dto", "dao", "vo", "bo", "po", "to", "ctx",
            "cfg", "src", "dst", "min", "max", "len", "idx", "tmp", "buf",
            "msg", "req", "res", "err", "log", "map", "set", "num");

    private AutoDeobfuscator() {
        throw new AssertionError("utility class");
    }

    /**
     * 扫描工作区并生成反混淆建议
     *
     * @param workspace 目标工作区
     * @return 建议重命名列表(旧名 → 新名)
     */
    public static List<RenameEntry> scan(Workspace workspace) {
        if (workspace == null) {
            return Collections.emptyList();
        }
        WorkspaceIndex index = workspace.isIndexReady()
                ? workspace.getIndex()
                : WorkspaceIndex.EMPTY;
        List<FileTreeNode> nodes = new ArrayList<>();
        collectNodes(workspace.getTreeRoot(), nodes);
        List<RenameEntry> suggestions = scan(nodes, index);
        log.info("scan(workspace): indexReady={}, indexClasses={}, treeClassSuggestions={}, totalSuggestions={}",
                index != WorkspaceIndex.EMPTY,
                index == WorkspaceIndex.EMPTY ? 0 : index.classes().size(),
                suggestions.stream().filter(e -> RenameService.TYPE_CLASS.equals(e.type())).count(),
                suggestions.size());
        return suggestions;
    }

    /**
     * 基于已复制的文件树节点快照扫描调用方应在 JavaFX 线程复制 TreeItem,
     * 后台线程只处理普通 FileTreeNode,避免后台访问 UI 树导致空结果或异常
     */
    public static List<RenameEntry> scan(List<FileTreeNode> nodes, WorkspaceIndex index) {
        WorkspaceIndex safeIndex = index == null ? WorkspaceIndex.EMPTY : index;
        List<RenameEntry> suggestions = new ArrayList<>(
                safeIndex == WorkspaceIndex.EMPTY ? List.of() : scan(safeIndex));
        int beforeTreeMerge = suggestions.size();
        mergeTreeClassSuggestions(nodes, safeIndex, suggestions);
        log.info("scan(nodes): indexReady={}, nodes={}, treeClassSuggestions={}, totalSuggestions={}",
                safeIndex != WorkspaceIndex.EMPTY,
                nodes == null ? 0 : nodes.size(),
                suggestions.size() - beforeTreeMerge,
                suggestions.size());
        return suggestions;
    }

    /** 根据完整索引扫描类、字段和方法 */
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
        log.debug("AutoDeobfuscator.scan(index): {} classes in index", classes.size());
        for (ClassIndexEntry cls : classes) {
            if (shouldSkipClass(cls)) {
                log.debug("  skip class {} (shouldSkipClass)", cls.internalName());
                continue;
            }
            String owner = cls.internalName();
            String simple = simpleName(owner);
            if (shouldRenameClass(simple, owner, index)) {
                String visibleName = visibleClassName(simple);
                String newName = aliases.forClass(visibleName);
                log.debug("  rename class: {} -> {} (internal={})", visibleName, newName, owner);
                add(suggestions, emitted, new RenameEntry(
                        RenameService.TYPE_CLASS, owner, visibleName, newName, ""));
            } else {
                log.debug("  skip class {} (shouldRenameClass=false, simple={})", owner, simple);
            }

            Set<String> usedNames = usedByClass.getOrDefault(owner, new ArraySet<>());
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

    /**
     * 判断名称是否为混淆名
     *
     * <p>混淆名的判定条件：长度在 1-3 字符之间、不在常见短名集合中、
     * 且所有字符为字母、数字、下划线或美元符号</p>
     */
    public static boolean isObfuscated(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // 常见短名(如 id、db)不算混淆名
        if (isCommonShortName(name)) {
            return false;
        }
        int len = name.length();
        if (len < MIN_OBFUSCATED_LENGTH || len > MAX_OBFUSCATED_LENGTH) {
            return false;
        }
        return name.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == '_' || c == '$');
    }

    /** 判断名称是否为常见短名(忽略大小写) */
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
     * 使用文件树模型扫描
     */
    public static List<RenameEntry> scanFromTree(FileTreeModel root) {
        if (root == null) {
            return Collections.emptyList();
        }
        List<FileTreeNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        log.debug("scanFromTree: collected {} total nodes from tree", nodes.size());
        long classNodeCount = nodes.stream().filter(FileTreeNode::isClassFile).count();
        log.debug("scanFromTree: {} class-file nodes among them", classNodeCount);
        WorkspaceIndex index = WorkspaceIndex.build(nodes);
        log.debug("scanFromTree: index built with {} classes, {} resources",
                index.classes().size(), index.resources().size());
        List<RenameEntry> suggestions = new ArrayList<>(scan(index));
        log.debug("scanFromTree: index scan produced {} suggestions ({} classes)",
                suggestions.size(),
                suggestions.stream().filter(e -> RenameService.TYPE_CLASS.equals(e.type())).count());
        mergeTreeClassSuggestions(root, index, suggestions);
        log.debug("scanFromTree: after merge, total {} suggestions", suggestions.size());
        return suggestions;
    }

    private static void collectNodes(FileTreeModel item, List<FileTreeNode> result) {
        FileTreeNode node = item.getValue();
        if (node != null) {
            result.add(node);
        }
        for (FileTreeModel child : item.getChildren()) {
            collectNodes(child, result);
        }
    }

    private static void mergeTreeClassSuggestions(FileTreeModel root,
                                                  WorkspaceIndex index,
                                                  List<RenameEntry> suggestions) {
        if (root == null || suggestions == null) {
            return;
        }
        List<FileTreeNode> nodes = new ArrayList<>();
        collectNodes(root, nodes);
        mergeTreeClassSuggestions(nodes, index, suggestions);
    }

    private static void mergeTreeClassSuggestions(List<FileTreeNode> nodes,
                                                  WorkspaceIndex index,
                                                  List<RenameEntry> suggestions) {
        if (nodes == null || suggestions == null) {
            return;
        }
        Set<String> emittedClasses = new ArraySet<>();
        int classAliasIndex = 0;
        for (RenameEntry entry : suggestions) {
            if (RenameService.TYPE_CLASS.equals(entry.type())) {
                emittedClasses.add(ClassNameUtil.stripContainerClassPrefix(entry.className()));
                classAliasIndex++;
            }
        }

        int[] nextClassAlias = {classAliasIndex};
        nodes.stream()
                .filter(FileTreeNode::isClassFile)
                .sorted(Comparator.comparing(FileTreeNode::getFullPath))
                .forEach(node -> {
                    String internalName = internalNameFromPath(node.getFullPath());
                    if (internalName.isBlank() || emittedClasses.contains(internalName)) {
                        if (!internalName.isBlank() && emittedClasses.contains(internalName)) {
                            log.debug("mergeTreeClass: skip already-emitted {}", internalName);
                        }
                        return;
                    }
                    if (shouldSkipClassPath(internalName)) {
                        log.debug("mergeTreeClass: skip shouldSkipClassPath {}", internalName);
                        return;
                    }
                    String simple = simpleName(internalName);
                    if (!shouldRenameClass(simple, internalName,
                            index == null ? WorkspaceIndex.EMPTY : index)) {
                        log.debug("mergeTreeClass: skip shouldRenameClass=false {} (simple={})",
                                internalName, simple);
                        return;
                    }
                    String visibleName = visibleClassName(simple);
                    RenameEntry entry = new RenameEntry(RenameService.TYPE_CLASS,
                            internalName, visibleName,
                            classAlias(nextClassAlias[0]++, visibleName), "");
                    log.debug("mergeTreeClass: adding {} -> {} (className={})",
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
        return "R".equals(simple) || simple.startsWith("R$") || "BuildConfig".equals(simple);
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
        return "R".equals(simple) || simple.startsWith("R$") || "BuildConfig".equals(simple);
    }

    /**
     * 判断某个类是否需要重命名
     *
     * <p>内部类且后半段为全数字的不重命名(如匿名类 $1)此外,简单名是混淆名
     * 或与包名冲突的也需要重命名</p>
     */
    private static boolean shouldRenameClass(String simpleName, String internalName,
                                             WorkspaceIndex index) {
        if (simpleName == null || simpleName.isBlank()) {
            return false;
        }
        // 内部类：提取最内层名称 匿名内部类(全数字)不重命名
        if (simpleName.contains("$")) {
            String inner = simpleName.substring(simpleName.lastIndexOf('$') + 1);
            if (inner.chars().allMatch(Character::isDigit)) {
                return false;
            }
            simpleName = inner;
        }
        return shouldRenameName(simpleName) || collidesWithPackage(simpleName, index);
    }

    /** 字段需要重命名的条件：是混淆名或同名字段出现多次(重载冲突) */
    private static boolean shouldRenameField(MemberIndexEntry field,
                                             Map<String, Integer> fieldNameCount) {
        return shouldRenameName(field.name()) || fieldNameCount.getOrDefault(field.name(), 0) > 1;
    }

    /** 方法需要重命名的条件：是混淆名或同签名方法出现多次(重载冲突),但排除不可重命名的方法 */
    private static boolean shouldRenameMethod(MemberIndexEntry method,
                                              Map<String, Integer> signatureCount) {
        String name = method.name();
        if (NEVER_RENAME_METHODS.contains(name) || OBJECT_METHODS.contains(name)) {
            return false;
        }
        return shouldRenameName(name)
                || signatureCount.getOrDefault(method.name() + method.descriptor(), 0) > 1;
    }

    /** 基础条件：混淆名、非法标识符、或非可打印 ASCII 字符 */
    private static boolean shouldRenameName(String name) {
        return isObfuscated(name) || !RenameService.isValidName(name) || !isPrintableAscii(name);
    }

    /** 检查名称是否全为可打印 ASCII 字符(码点 32-126) */
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

    /** 检查类简单名是否与工作区中的包名片段冲突 */
    private static boolean collidesWithPackage(String simpleName, WorkspaceIndex index) {
        Set<String> packageParts = new ArraySet<>();
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
        Map<String, Set<String>> result = new ArrayMap<>();
        for (ClassIndexEntry cls : index.classes()) {
            Set<String> names = new ArraySet<>();
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

    /**
     * 为成员生成唯一别名
     *
     * <p>如果建议的别名已被同一类的其他成员使用或不是合法 Java 标识符,
     * 则在别名后添加 _2、_3 等后缀直到唯一</p>
     */
    private static String uniqueMemberAlias(String alias, Set<String> usedNames) {
        String result = alias;
        int index = 2;
        while (usedNames.contains(result) || !RenameService.isValidName(result)) {
            result = alias + '_' + index++;
        }
        usedNames.add(result);
        return result;
    }

    /** 判断方法是否为可能的覆写方法(Object 方法或 on/run/call 前缀,通常来自框架回调) */
    private static boolean isLikelyOverride(MemberIndexEntry method) {
        String name = method.name();
        return OBJECT_METHODS.contains(name) || name.startsWith("on")
                || name.startsWith("run") || name.startsWith("call");
    }

    private static String packageName(String internalName) {
        return ClassNameUtil.packageName(internalName);
    }

    private static String simpleName(String internalName) {
        return ClassNameUtil.simpleName(internalName);
    }

    private static String internalNameFromPath(String fullPath) {
        return ClassNameUtil.stripContainerClassPrefix(fullPath);
    }

    private static String stripContainerClassPrefix(String internalName) {
        return ClassNameUtil.stripContainerClassPrefix(internalName);
    }

    private static String visibleClassName(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return "";
        }
        int dollar = simpleName.lastIndexOf('$');
        return dollar >= 0 ? simpleName.substring(dollar + 1) : simpleName;
    }

    /** 生成类反混淆别名：格式为 C0001 + 原始名的合法字符部分 */
    private static String classAlias(int index, String oldName) {
        return "C" + "%04d".formatted(index) + sanitizeNamePart(oldName);
    }

    /** 过滤名称,只保留可打印 ASCII 且为合法 Java 标识符部分的字符 */
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

    /**
     * 别名提供器：为类、字段、方法生成稳定递增的反混淆名称
     *
     * <p>类前缀 C、字段前缀 f、方法前缀 m、覆写方法前缀 mo,
     * 后接递增序号和原始名的合法字符部分</p>
     */
    private static final class AliasProvider {
        private int clsIndex;
        private int fldIndex;
        private int mthIndex;

        /** 生成类别名：C0000 + 原始名可打印部分 */
        String forClass(String oldName) {
            return classAlias(clsIndex++, oldName);
        }

        /** 生成字段别名：f0 + 原始名可打印部分 */
        String forField(String oldName) {
            return "f" + fldIndex++ + sanitizeNamePart(oldName);
        }

        /** 生成方法别名：m0 + 原始名可打印部分 */
        String forMethod(String oldName) {
            return "m" + mthIndex++ + sanitizeNamePart(oldName);
        }

        /** 生成覆写方法别名：mo0 + 原始名可打印部分(用于可能被覆写的方法) */
        String forOverrideMethod(String oldName) {
            return "mo" + mthIndex++ + sanitizeNamePart(oldName);
        }
    }
}
