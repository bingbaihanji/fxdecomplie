package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 重命名服务：验证、持久化、查询和应用重命名映射。
 *
 * @author bingbaihanji
 * @date 2026-07-03
 */
public final class RenameService {

    public static final String TYPE_CLASS = "class";
    public static final String TYPE_METHOD = "method";
    public static final String TYPE_FIELD = "field";
    public static final String TYPE_PARAM = "param";
    public static final String TYPE_IDENTIFIER = "identifier";

    private static final Logger log = LoggerFactory.getLogger(RenameService.class);
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(RenameEntry.class, new RenameEntryAdapter().nullSafe())
            .setPrettyPrinting()
            .create();
    private static final Type LIST_TYPE = new TypeToken<List<RenameEntry>>() {
    }.getType();
    private static final Pattern JAVA_ID = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");
    private static final Set<String> RESERVED_NAMES = Set.of(
            "_", "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else",
            "enum", "extends", "false", "final", "finally", "float", "for", "goto",
            "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "void", "volatile",
            "while");
    private static final Set<String> METHOD_HEADER_KEYWORDS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "return", "throw", "new",
            "synchronized", "do", "else", "assert");
    private static final Set<String> CONSTRUCTOR_PREFIX_WORDS = Set.of(
            "public", "protected", "private", "strictfp");
    private static final Set<String> LOCAL_DECL_SKIP_WORDS = Set.of(
            "assert", "break", "case", "continue", "default", "do", "else", "for",
            "if", "return", "switch", "throw", "try", "while", "yield");
    private static final Pattern LOCAL_DECL_PATTERN = Pattern.compile(
            "(?m)(^|[;{}]\\s*)(?:final\\s+)?(?:@[\\w.$]+(?:\\([^)]*\\))?\\s*)*"
                    + "((?:[a-zA-Z_$][a-zA-Z0-9_$]*(?:\\s*\\.\\s*[a-zA-Z_$][a-zA-Z0-9_$]*)*"
                    + "(?:\\s*<[^;{}()]*>)?(?:\\s*\\[\\s*\\])?\\s+)+)"
                    + "([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=[=;,:])");
    private static final Pattern COMMA_LOCAL_PATTERN = Pattern.compile(
            ",\\s*([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?=[=,;])");
    private static final Pattern CATCH_PARAM_PATTERN = Pattern.compile(
            "\\bcatch\\s*\\((?:final\\s+)?[^()]*?\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*\\)");
    private static final Pattern FOR_PARAM_PATTERN = Pattern.compile(
            "\\bfor\\s*\\(\\s*(?:final\\s+)?[a-zA-Z_$][a-zA-Z0-9_$.]*(?:\\s*<[^;:()]*>)?"
                    + "(?:\\s*\\[\\s*\\])?\\s+([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*[:=;]");
    private static final Pattern LAMBDA_GROUP_PATTERN = Pattern.compile("\\(([^()\\n]*)\\)\\s*->");
    private static final Pattern LAMBDA_SINGLE_PATTERN = Pattern.compile(
            "(?<![\\w$])([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*->");
    private static final Pattern IMPORT_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+(?:static\\s+)?([\\w.$]+(?:\\.\\*)?)\\s*;\\s*$");
    private static final Pattern PACKAGE_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final ConcurrentMap<String, List<RenameEntry>> MEMORY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Map<String, List<String>>> ORIGINAL_BY_DISPLAY_CACHE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Object> WORKSPACE_LOCKS = new ConcurrentHashMap<>();
    private static volatile Path rootDir;

    private RenameService() {
        throw new AssertionError("utility class");
    }

    private static Path getRootDir() {
        return rootDir != null ? rootDir : AppConfig.appDir().resolve("fxdecomplie").resolve("renames");
    }

    public static void setRootDir(Path dir) {
        rootDir = dir;
    }

    /** 校验 Java 标识符合法性 */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || RESERVED_NAMES.contains(name)) {
            return false;
        }
        int offset = 0;
        int first = name.codePointAt(offset);
        if (!Character.isJavaIdentifierStart(first)) {
            return false;
        }
        offset += Character.charCount(first);
        while (offset < name.length()) {
            int codePoint = name.codePointAt(offset);
            if (!Character.isJavaIdentifierPart(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    /** 保存重命名 */
    public static boolean save(String workspaceHash, RenameEntry entry) {
        if (entry == null || workspaceHash == null || workspaceHash.isBlank()) {
            return false;
        }
        return saveAll(workspaceHash, List.of(entry)) == 1;
    }

    /** 批量保存重命名，单次加锁和落盘，避免批量反混淆时丢写或重复 I/O。 */
    public static int saveAll(String workspaceHash, List<RenameEntry> entries) {
        if (workspaceHash == null || workspaceHash.isBlank() || entries == null || entries.isEmpty()) {
            return 0;
        }
        synchronized (lockFor(workspaceHash)) {
            List<RenameEntry> list = loadAll(workspaceHash);
            int applied = 0;
            for (RenameEntry entry : entries) {
                if (entry == null) {
                    continue;
                }
                RenameEntry normalized = normalizeForStorage(workspaceHash, entry);
                if (normalized.oldName().isBlank() || normalized.newName().isBlank()) {
                    continue;
                }
                if (normalized.oldName().equals(normalized.newName())) {
                    if (list.removeIf(e -> {
                        RenameEntry existing = normalize(e);
                        return sameKey(existing, normalized)
                                || sameClassRenameTarget(existing, normalized);
                    })) {
                        applied++;
                    }
                    continue;
                }
                // 替换同一符号的旧条目。方法和参数保留 descriptor，避免重载冲突。
                boolean replaced = false;
                for (int i = 0; i < list.size(); i++) {
                    RenameEntry e = normalize(list.get(i));
                    if (sameKey(e, normalized) || sameClassRenameTarget(e, normalized)) {
                        list.set(i, normalized);
                        replaced = true;
                        break;
                    }
                }
                if (!replaced) {
                    list.add(normalized);
                }
                applied++;
            }
            if (applied == 0) {
                return 0;
            }
            if (!writeFile(workspaceHash, list)) {
                return 0;
            }
            MEMORY_CACHE.put(workspaceHash, List.copyOf(list));
            ORIGINAL_BY_DISPLAY_CACHE.remove(workspaceHash);
            return applied;
        }
    }

    /** 加载工作区全部重命名 */
    public static List<RenameEntry> loadAll(String workspaceHash) {
        if (workspaceHash == null || workspaceHash.isBlank()) {
            return new ArrayList<>();
        }
        Path file = resolveFile(workspaceHash);
        if (!Files.isRegularFile(file)) {
            Path legacyFile = resolveLegacyFile(workspaceHash);
            if (!Files.isRegularFile(legacyFile)) {
                List<RenameEntry> cached = MEMORY_CACHE.get(workspaceHash);
                return cached == null ? new ArrayList<>() : new ArrayList<>(cached);
            }
            file = legacyFile;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            List<RenameEntry> list = GSON.fromJson(json, LIST_TYPE);
            if (list == null) {
                return new ArrayList<>();
            }
            List<RenameEntry> result = new ArrayList<>(list.size());
            for (RenameEntry entry : list) {
                if (entry != null) {
                    result.add(normalize(entry));
                }
            }
            result = dedupeClassRenames(result);
            MEMORY_CACHE.put(workspaceHash, List.copyOf(result));
            return result;
        } catch (IOException | RuntimeException e) {
            log.debug("读取重命名文件失败: {}", file, e);
            List<RenameEntry> cached = MEMORY_CACHE.get(workspaceHash);
            return cached == null ? new ArrayList<>() : new ArrayList<>(cached);
        }
    }

    /** 根据旧名称查找重命名 */
    public static String findNewName(String workspaceHash, String className, String oldName) {
        String normalizedClass = normalizeInternalName(className);
        for (RenameEntry e : loadAll(workspaceHash)) {
            if (sameInternalClassName(e.className(), normalizedClass) && e.oldName().equals(oldName)) {
                return e.newName();
            }
        }
        return null;
    }

    /** 删除重命名 */
    public static void delete(String workspaceHash, RenameEntry entry) {
        if (entry == null || workspaceHash == null || workspaceHash.isBlank()) {
            return;
        }
        synchronized (lockFor(workspaceHash)) {
            RenameEntry normalized = normalize(entry);
            List<RenameEntry> list = loadAll(workspaceHash);
            list.removeIf(e -> sameKey(normalize(e), normalized));
            if (writeFile(workspaceHash, list)) {
                MEMORY_CACHE.put(workspaceHash, List.copyOf(list));
                ORIGINAL_BY_DISPLAY_CACHE.remove(workspaceHash);
            }
        }
    }

    /** 恢复最近一次保存前快照。 */
    public static boolean restoreLatestBackup(String workspaceHash) {
        if (workspaceHash == null || workspaceHash.isBlank()) {
            return false;
        }
        synchronized (lockFor(workspaceHash)) {
            Path file = resolveFile(workspaceHash);
            Path parent = file.getParent();
            if (parent == null || !Files.isDirectory(parent)) {
                return false;
            }
            String prefix = file.getFileName() + ".bak.";
            try (Stream<Path> stream = Files.list(parent)) {
                Optional<Path> latest = stream
                        .filter(path -> path.getFileName().toString().startsWith(prefix))
                        .max(Comparator.comparing(path -> path.getFileName().toString()));
                if (latest.isEmpty()) {
                    return false;
                }
                Files.copy(latest.get(), file, StandardCopyOption.REPLACE_EXISTING);
                MEMORY_CACHE.remove(workspaceHash);
                ORIGINAL_BY_DISPLAY_CACHE.remove(workspaceHash);
                loadAll(workspaceHash);
                return true;
            } catch (IOException e) {
                log.error("恢复重命名快照失败", e);
                return false;
            }
        }
    }

    /**
     * 将已保存的重命名应用到源码文本。
     *
     * @param sourceCode 原始反编译源码
     * @param wsHash     工作区哈希
     * @param className  类内部名
     * @return 替换后的源码
     */
    public static String applyRenames(String sourceCode, String wsHash, String className) {
        if (sourceCode == null || wsHash == null || wsHash.isBlank()) {
            return sourceCode;
        }
        String currentClass = normalizeInternalName(className);
        List<RenameEntry> entries = loadAll(wsHash);
        String source = applyClassRenames(sourceCode, entries, currentClass);
        return applyEntries(source, entries, currentClass);
    }

    /** 返回应用类重命名后的 .java 导出路径。 */
    public static String renamedJavaPath(String classPath, String workspaceHash) {
        String internalName = normalizeInternalName(classPath);
        String renamed = renamedInternalName(internalName, workspaceHash);
        return renamed + ".java";
    }

    /** 返回应用类重命名后的内部名。 */
    public static String renamedInternalName(String className, String workspaceHash) {
        String internalName = normalizeInternalName(className);
        if (internalName.isBlank() || workspaceHash == null || workspaceHash.isBlank()) {
            return internalName;
        }
        List<RenameEntry> entries = loadAll(workspaceHash);
        return renamedInternalName(internalName, entries);
    }

    /** 将显示出来的反混淆类名反查回真实 class 内部名。 */
    public static String originalInternalName(String className, String workspaceHash) {
        String internalName = normalizeInternalName(className);
        if (internalName.isBlank() || workspaceHash == null || workspaceHash.isBlank()) {
            return internalName;
        }
        List<String> candidates = originalInternalNameCandidates(internalName, workspaceHash);
        if (!candidates.isEmpty()) {
            return candidates.getFirst();
        }
        return internalName;
    }

    public static Map<String, String> originalClassNamesByDisplay(String workspaceHash) {
        if (workspaceHash == null || workspaceHash.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> candidates = originalClassNameCandidatesByDisplay(workspaceHash);
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        candidates.forEach((display, originals) -> {
            if (originals != null && !originals.isEmpty()) {
                result.put(display, originals.getFirst());
            }
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    /** 返回显示类名可能对应的真实 class 内部名，短名冲突时保留所有候选。 */
    public static List<String> originalInternalNameCandidates(String className, String workspaceHash) {
        String internalName = normalizeInternalName(className);
        if (internalName.isBlank() || workspaceHash == null || workspaceHash.isBlank()) {
            return List.of();
        }
        Map<String, List<String>> originalByDisplay = originalClassNameCandidatesByDisplay(workspaceHash);
        List<String> direct = originalByDisplay.get(internalName);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        String slashVariant = internalName.replace('$', '/');
        direct = originalByDisplay.get(slashVariant);
        if (direct != null && !direct.isEmpty()) {
            return direct;
        }
        if (!internalName.contains("/")) {
            direct = originalByDisplay.get(visibleClassLeaf(internalName));
            if (direct != null && !direct.isEmpty()) {
                return direct;
            }
        }
        return List.of();
    }

    private static Map<String, List<String>> originalClassNameCandidatesByDisplay(String workspaceHash) {
        if (workspaceHash == null || workspaceHash.isBlank()) {
            return Map.of();
        }
        return ORIGINAL_BY_DISPLAY_CACHE.computeIfAbsent(
                workspaceHash, RenameService::buildOriginalByDisplayName);
    }

    private static Map<String, List<String>> buildOriginalByDisplayName(String workspaceHash) {
        List<RenameEntry> entries = loadAll(workspaceHash);
        Map<String, List<String>> result = new LinkedHashMap<>();
        Set<String> originals = new LinkedHashSet<>();
        Map<String, List<RenameEntry>> classEntriesByOriginal = new LinkedHashMap<>();
        for (RenameEntry entry : entries) {
            if (!TYPE_CLASS.equals(entry.type()) || entry.className().isBlank()) {
                continue;
            }
            String original = normalizeInternalName(entry.className());
            originals.add(original);
            classEntriesByOriginal.computeIfAbsent(original, key -> new ArrayList<>()).add(entry);
        }
        for (String original : originals) {
            String renamed = normalizeInternalName(renamedInternalName(original, entries));
            putOriginalAlias(result, renamed, original);
            putOriginalAlias(result, renamed.replace('$', '/'), original);
            putOriginalAlias(result, visibleClassLeaf(renamed), original);
            String pkg = packageName(original);
            for (RenameEntry entry : classEntriesByOriginal.getOrDefault(original, List.of())) {
                putOriginalAlias(result, entry.newName(), original);
                if (!pkg.isBlank()) {
                    putOriginalAlias(result, pkg + "/" + entry.newName(), original);
                }
            }
        }
        Map<String, List<String>> immutable = new LinkedHashMap<>();
        result.forEach((alias, candidates) -> immutable.put(alias, List.copyOf(candidates)));
        return Collections.unmodifiableMap(immutable);
    }

    private static void putOriginalAlias(Map<String, List<String>> result, String alias, String original) {
        String normalizedAlias = normalizeInternalName(alias);
        String normalizedOriginal = normalizeInternalName(original);
        if (!normalizedAlias.isBlank() && !normalizedOriginal.isBlank()) {
            result.computeIfAbsent(normalizedAlias, key -> new ArrayList<>());
            List<String> originals = result.get(normalizedAlias);
            if (!originals.contains(normalizedOriginal)) {
                originals.add(normalizedOriginal);
            }
        }
    }

    private static String renamedInternalName(String internalName, List<RenameEntry> entries) {
        String result = normalizeInternalName(internalName);
        if (result.isBlank() || entries == null || entries.isEmpty()) {
            return result;
        }
        Set<String> seen = new HashSet<>();
        for (int step = 0; step < entries.size(); step++) {
            String key = normalizeInternalName(result);
            if (!seen.add(key)) {
                break;
            }
            RenameEntry entry = findLastClassEntryTargeting(entries, result);
            if (entry == null) {
                break;
            }
            String next = normalizeInternalName(renamedClassInternalName(
                    result, entry.oldName(), entry.newName()));
            if (sameInternalClassName(next, result)) {
                break;
            }
            result = next;
        }
        return result;
    }

    private static RenameEntry findLastClassEntryTargeting(List<RenameEntry> entries,
                                                           String internalName) {
        for (int i = entries.size() - 1; i >= 0; i--) {
            RenameEntry entry = entries.get(i);
            if (TYPE_CLASS.equals(entry.type()) && classEntryTargetsInternalName(entry, internalName)) {
                return entry;
            }
        }
        return null;
    }

    /** 返回编辑器标题使用的类短名。 */
    public static String displayClassName(String classPath, String workspaceHash) {
        String renamed = renamedInternalName(classPath, workspaceHash);
        String simple = simpleClassName(renamed);
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 ? simple.substring(dollar + 1) : simple;
    }

    /** 解析光标处符号对应的重命名目标。 */
    public static Optional<RenameTarget> resolveTarget(String sourceCode, int offset,
                                                       String classInternalName, byte[] classBytes,
                                                       WorkspaceIndex index) {
        return resolveTarget(sourceCode, offset, classInternalName, classBytes, index, null);
    }

    /** 解析光标处符号对应的重命名目标，识别已保存别名。 */
    public static Optional<RenameTarget> resolveTarget(String sourceCode, int offset,
                                                       String classInternalName, byte[] classBytes,
                                                       WorkspaceIndex index, String workspaceHash) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return Optional.empty();
        }
        Token token = tokenAt(sourceCode, offset);
        if (token == null || token.name().isBlank()) {
            return Optional.empty();
        }
        String currentClass = normalizeInternalName(classInternalName);
        List<MethodScope> scopes = findMethodScopes(sourceCode, simpleClassName(currentClass));
        boolean methodContext = isMethodContext(sourceCode, token.start(), token.end());
        boolean classContext = isClassContext(sourceCode, token.start(), token.end());
        boolean qualified = isQualifiedIdentifier(sourceCode, token.start());
        List<RenameEntry> savedEntries = loadAll(workspaceHash);

        Optional<RenameEntry> classAlias = findAliasEntry(savedEntries,
                token.name(), TYPE_CLASS, currentClass, null);
        boolean constructorName = isConstructorName(sourceCode, token, currentClass, savedEntries);
        if (classAlias.isPresent()) {
            return Optional.of(new RenameTarget(classAlias.get(), "class", token.name()));
        }
        if (classContext || constructorName
                || (!methodContext && findClassOwner(token.name(), currentClass, index).isPresent())) {
            Optional<String> classOwner = findClassOwner(token.name(), currentClass, index);
            return Optional.of(new RenameTarget(new RenameEntry(
                    TYPE_CLASS, classOwner.orElse(currentClass), token.name(), token.name(), ""),
                    "class", token.name()));
        }

        Optional<MethodScope> scopeAtToken = scopes.stream()
                .filter(scope -> scope.contains(token.start()))
                .findFirst();
        if (scopeAtToken.isPresent() && scopeAtToken.get().params().contains(token.name())
                && !qualified && !methodContext) {
            MethodScope scope = scopeAtToken.get();
            Optional<RenameEntry> alias = findAliasEntry(savedEntries,
                    token.name(), TYPE_PARAM, currentClass, scope);
            if (alias.isPresent()) {
                return Optional.of(new RenameTarget(alias.get(), "parameter", token.name()));
            }
            String desc = methodDesc(currentClass, token.name(), classBytes, index, scope,
                    savedEntries);
            return Optional.of(new RenameTarget(new RenameEntry(
                    TYPE_PARAM, currentClass, token.name(), token.name(), desc),
                    "parameter"));
        }

        if (methodContext || isMethodDeclarationName(sourceCode, token.start(), token.end())) {
            Optional<RenameEntry> alias = findAliasEntry(savedEntries,
                    token.name(), TYPE_METHOD, currentClass, null);
            if (alias.isPresent()) {
                return Optional.of(new RenameTarget(alias.get(), "method", token.name()));
            }
            MemberRef method = findMethodOwnerAndDesc(token.name(), currentClass, classBytes, index,
                    scopeAtToken.map(MethodScope::paramCount).orElse(-1));
            return Optional.of(new RenameTarget(new RenameEntry(
                    TYPE_METHOD, method.owner(), token.name(), token.name(), method.desc()),
                    "method", token.name()));
        }

        Optional<RenameEntry> paramAlias = scopeAtToken.flatMap(scope ->
                findAliasEntry(savedEntries, token.name(), TYPE_PARAM, currentClass, scope));
        if (paramAlias.isPresent()) {
            return Optional.of(new RenameTarget(paramAlias.get(), "parameter", token.name()));
        }

        Optional<RenameEntry> fieldAlias = findAliasEntry(savedEntries,
                token.name(), TYPE_FIELD, currentClass, null);
        if (fieldAlias.isPresent()) {
            return Optional.of(new RenameTarget(fieldAlias.get(), "field", token.name()));
        }

        MemberRef field = findFieldOwnerAndDesc(token.name(), currentClass, classBytes, index);
        if (field != null) {
            return Optional.of(new RenameTarget(new RenameEntry(
                    TYPE_FIELD, field.owner(), token.name(), token.name(), field.desc()),
                    "field", token.name()));
        }

        if (findClassOwner(token.name(), currentClass, index).isPresent()) {
            String owner = findClassOwner(token.name(), currentClass, index).orElse(currentClass);
            return Optional.of(new RenameTarget(new RenameEntry(
                    TYPE_CLASS, owner, token.name(), token.name(), ""),
                    "class", token.name()));
        }

        return Optional.of(new RenameTarget(new RenameEntry(
                TYPE_IDENTIFIER, currentClass, token.name(), token.name(), ""),
                "identifier", token.name()));
    }

    /** 对当前已显示的别名源码做一次直接替换。 */
    public static String applySingleRename(String sourceCode, RenameEntry entry, String className) {
        if (sourceCode == null || entry == null) {
            return sourceCode;
        }
        String currentClass = normalizeInternalName(className);
        List<RenameEntry> entries = List.of(normalize(entry));
        String source = applyClassRenames(sourceCode, entries, currentClass);
        return applyEntries(source, entries, currentClass);
    }

    /** 对当前已显示的别名源码做一次直接替换，并使用已保存映射辅助方法作用域匹配。 */
    public static String applySingleRename(String sourceCode, RenameEntry entry,
                                           String className, String workspaceHash) {
        if (sourceCode == null || entry == null) {
            return sourceCode;
        }
        List<RenameEntry> entries = new ArrayList<>(loadAll(workspaceHash));
        entries.add(normalize(entry));
        String currentClass = normalizeInternalName(className);
        String source = applyClassRenames(sourceCode, entries, currentClass);
        return applyEntries(source, entries, currentClass);
    }

    /** 按 Java 标识符边界强制替换源码中的可见名称，跳过注释和字符串。 */
    public static String replaceVisibleIdentifier(String sourceCode, String oldName, String newName) {
        if (sourceCode == null || oldName == null || oldName.isBlank()
                || newName == null || newName.isBlank() || oldName.equals(newName)) {
            return sourceCode;
        }
        return replaceVisibleIdentifiers(sourceCode, List.of(new RenameEntry(
                TYPE_IDENTIFIER, "", oldName, newName, "")));
    }

    /** 只替换源码中的 class/interface/enum/record 声明名，跳过注释和字符串。 */
    public static String replaceVisibleClassDeclaration(String sourceCode, String oldName, String newName) {
        if (sourceCode == null || oldName == null || oldName.isBlank()
                || newName == null || newName.isBlank() || oldName.equals(newName)) {
            return sourceCode;
        }
        return replaceDeclaredClassName(sourceCode, oldName, newName);
    }

    /** 返回源码 offset 处的 Java 标识符；offset 位于标识符末尾后一位时也能识别。 */
    public static String identifierAt(String sourceCode, int offset) {
        Token token = tokenAt(sourceCode, offset);
        return token == null ? "" : token.name();
    }

    /** 导出为 Simple 格式 */
    public static String exportSimple(String workspaceHash) {
        StringBuilder sb = new StringBuilder();
        for (RenameEntry e : loadAll(workspaceHash)) {
            sb.append(e.className()).append(".").append(e.oldName())
                    .append(" → ").append(e.newName()).append("\n");
        }
        return sb.toString();
    }

    /** 解析 ProGuard/R8 mapping.txt，方向转换为当前工具使用的 混淆名 -> 新名。 */
    public static List<RenameEntry> parseProGuardMapping(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<RenameEntry> entries = new ArrayList<>();
        String currentObfuscatedClass = "";
        for (String rawLine : text.replace("\r\n", "\n").split("\n")) {
            String line = rawLine.strip();
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (line.endsWith(":") && line.contains(" -> ")) {
                String header = line.substring(0, line.length() - 1);
                String[] parts = header.split("\\s+->\\s+", 2);
                if (parts.length != 2) {
                    currentObfuscatedClass = "";
                    continue;
                }
                String originalClass = parts[0].strip();
                currentObfuscatedClass = normalizeInternalName(parts[1].strip());
                String oldName = visibleClassLeaf(currentObfuscatedClass);
                String newName = visibleClassLeaf(normalizeInternalName(originalClass));
                if (!oldName.isBlank() && isValidName(newName)) {
                    entries.add(new RenameEntry(TYPE_CLASS, currentObfuscatedClass,
                            oldName, newName, ""));
                }
                continue;
            }
            if (currentObfuscatedClass.isBlank() || !line.contains(" -> ")) {
                continue;
            }
            RenameEntry member = parseProGuardMemberLine(line, currentObfuscatedClass);
            if (member != null) {
                entries.add(member);
            }
        }
        return entries;
    }

    /** 导出为 ProGuard/R8 mapping.txt 方向：新名 -> 混淆名。 */
    public static String exportProGuard(String workspaceHash) {
        List<RenameEntry> entries = loadAll(workspaceHash);
        if (entries.isEmpty()) {
            return "";
        }
        Map<String, List<RenameEntry>> byClass = new LinkedHashMap<>();
        for (RenameEntry entry : entries) {
            RenameEntry normalized = normalize(entry);
            if (normalized.className().isBlank()) {
                continue;
            }
            byClass.computeIfAbsent(normalized.className(), key -> new ArrayList<>())
                    .add(normalized);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<RenameEntry>> classGroup : byClass.entrySet()) {
            String obfuscatedClass = classGroup.getKey();
            Optional<RenameEntry> classRename = classGroup.getValue().stream()
                    .filter(entry -> TYPE_CLASS.equals(entry.type()))
                    .findFirst();
            String originalClass = classRename
                    .map(entry -> dotted(renamedClassInternalName(obfuscatedClass,
                            entry.oldName(), entry.newName())))
                    .orElseGet(() -> dotted(obfuscatedClass));
            sb.append(originalClass).append(" -> ").append(dotted(obfuscatedClass)).append(":\n");
            for (RenameEntry entry : classGroup.getValue()) {
                if (TYPE_FIELD.equals(entry.type())) {
                    sb.append("    ")
                            .append(fieldDescriptorToType(entry.desc()))
                            .append(' ')
                            .append(entry.newName())
                            .append(" -> ")
                            .append(entry.oldName())
                            .append('\n');
                } else if (TYPE_METHOD.equals(entry.type())) {
                    MethodSignature signature = methodDescriptorToSignature(entry.desc(), entry.newName());
                    sb.append("    ")
                            .append(signature.returnType())
                            .append(' ')
                            .append(entry.newName())
                            .append('(')
                            .append(String.join(",", signature.params()))
                            .append(") -> ")
                            .append(entry.oldName())
                            .append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static RenameEntry parseProGuardMemberLine(String line, String className) {
        String[] parts = line.split("\\s+->\\s+", 2);
        if (parts.length != 2) {
            return null;
        }
        String left = stripProGuardLineNumbers(parts[0].strip());
        String obfuscatedName = parts[1].strip();
        if (left.isBlank() || obfuscatedName.isBlank()) {
            return null;
        }
        if (left.contains("(")) {
            int paren = left.indexOf('(');
            String beforeParen = left.substring(0, paren).strip();
            String originalName = lastMemberName(beforeParen);
            if (!isValidName(originalName)) {
                return null;
            }
            return new RenameEntry(TYPE_METHOD, className, obfuscatedName, originalName, "");
        }
        String originalName = lastMemberName(left);
        if (!isValidName(originalName)) {
            return null;
        }
        return new RenameEntry(TYPE_FIELD, className, obfuscatedName, originalName, "");
    }

    private static String stripProGuardLineNumbers(String text) {
        String result = text;
        while (true) {
            int colon = result.indexOf(':');
            if (colon <= 0) {
                return result.strip();
            }
            String prefix = result.substring(0, colon);
            if (!prefix.chars().allMatch(Character::isDigit)) {
                return result.strip();
            }
            result = result.substring(colon + 1).strip();
        }
    }

    private static String lastMemberName(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('.', ' ').strip();
        int space = normalized.lastIndexOf(' ');
        return space < 0 ? normalized : normalized.substring(space + 1);
    }

    private static String dotted(String internalName) {
        return normalizeInternalName(internalName).replace('/', '.');
    }

    private static String visibleClassLeaf(String internalName) {
        String simple = simpleClassName(internalName);
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 ? simple.substring(dollar + 1) : simple;
    }

    private static String fieldDescriptorToType(String desc) {
        if (desc == null || desc.isBlank()) {
            return "***";
        }
        TypeParse parsed = parseJvmType(desc, 0);
        return parsed == null ? "***" : parsed.name();
    }

    private static MethodSignature methodDescriptorToSignature(String desc, String methodName) {
        if (desc == null || desc.isBlank() || !desc.startsWith("(")) {
            return new MethodSignature("***", List.of("***"));
        }
        List<String> params = new ArrayList<>();
        int offset = 1;
        while (offset < desc.length() && desc.charAt(offset) != ')') {
            TypeParse param = parseJvmType(desc, offset);
            if (param == null || param.nextOffset() <= offset) {
                return new MethodSignature("***", List.of("***"));
            }
            params.add(param.name());
            offset = param.nextOffset();
        }
        if (offset >= desc.length() || desc.charAt(offset) != ')') {
            return new MethodSignature("***", List.of("***"));
        }
        TypeParse returnType = parseJvmType(desc, offset + 1);
        return new MethodSignature(returnType == null ? "***" : returnType.name(), params);
    }

    private static TypeParse parseJvmType(String desc, int offset) {
        if (desc == null || offset < 0 || offset >= desc.length()) {
            return null;
        }
        int arrayDepth = 0;
        int i = offset;
        while (i < desc.length() && desc.charAt(i) == '[') {
            arrayDepth++;
            i++;
        }
        if (i >= desc.length()) {
            return null;
        }
        String typeName;
        char ch = desc.charAt(i);
        switch (ch) {
            case 'B' -> typeName = "byte";
            case 'C' -> typeName = "char";
            case 'D' -> typeName = "double";
            case 'F' -> typeName = "float";
            case 'I' -> typeName = "int";
            case 'J' -> typeName = "long";
            case 'S' -> typeName = "short";
            case 'Z' -> typeName = "boolean";
            case 'V' -> typeName = "void";
            case 'L' -> {
                int end = desc.indexOf(';', i);
                if (end < 0) {
                    return null;
                }
                typeName = desc.substring(i + 1, end).replace('/', '.').replace('$', '.');
                i = end;
            }
            default -> {
                return null;
            }
        }
        StringBuilder result = new StringBuilder(typeName);
        for (int depth = 0; depth < arrayDepth; depth++) {
            result.append("[]");
        }
        return new TypeParse(result.toString(), i + 1);
    }

    private static String applyEntries(String sourceCode, List<RenameEntry> entries,
                                       String currentClass) {
        if (entries.isEmpty()) {
            return sourceCode;
        }
        List<MethodScope> scopes = findMethodScopes(sourceCode, simpleClassName(currentClass));
        StringBuilder out = new StringBuilder(sourceCode.length());
        int i = 0;
        while (i < sourceCode.length()) {
            char ch = sourceCode.charAt(i);
            if (startsWith(sourceCode, i, "//")) {
                int end = sourceCode.indexOf('\n', i + 2);
                if (end < 0) {
                    out.append(sourceCode, i, sourceCode.length());
                    break;
                }
                out.append(sourceCode, i, end + 1);
                i = end + 1;
                continue;
            }
            if (startsWith(sourceCode, i, "/*")) {
                int end = sourceCode.indexOf("*/", i + 2);
                int next = end < 0 ? sourceCode.length() : end + 2;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (startsWith(sourceCode, i, "\"\"\"")) {
                int end = sourceCode.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? sourceCode.length() : end + 3;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                int next = skipQuoted(sourceCode, i, ch);
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int start = i;
                i++;
                while (i < sourceCode.length()
                        && Character.isJavaIdentifierPart(sourceCode.charAt(i))) {
                    i++;
                }
                String oldName = sourceCode.substring(start, i);
                String newName = chooseReplacement(sourceCode, start, i, oldName,
                        entries, currentClass, scopes);
                out.append(newName == null ? oldName : newName);
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String applyClassRenames(String sourceCode, List<RenameEntry> entries,
                                            String currentClass) {
        if (sourceCode == null || sourceCode.isEmpty() || entries == null || entries.isEmpty()) {
            return sourceCode;
        }
        List<RenameEntry> classEntries = entries.stream()
                .map(RenameService::normalize)
                .filter(entry -> TYPE_CLASS.equals(entry.type()))
                .toList();
        if (classEntries.isEmpty()) {
            return sourceCode;
        }
        sourceCode = replaceDeclaredClassNames(sourceCode, classEntries, currentClass);
        StringBuilder out = new StringBuilder(sourceCode.length());
        int i = 0;
        while (i < sourceCode.length()) {
            char ch = sourceCode.charAt(i);
            if (startsWith(sourceCode, i, "//")) {
                int end = sourceCode.indexOf('\n', i + 2);
                if (end < 0) {
                    out.append(sourceCode, i, sourceCode.length());
                    break;
                }
                out.append(sourceCode, i, end + 1);
                i = end + 1;
                continue;
            }
            if (startsWith(sourceCode, i, "/*")) {
                int end = sourceCode.indexOf("*/", i + 2);
                int next = end < 0 ? sourceCode.length() : end + 2;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (startsWith(sourceCode, i, "\"\"\"")) {
                int end = sourceCode.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? sourceCode.length() : end + 3;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                int next = skipQuoted(sourceCode, i, ch);
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int start = i;
                i++;
                while (i < sourceCode.length()
                        && Character.isJavaIdentifierPart(sourceCode.charAt(i))) {
                    i++;
                }
                String token = sourceCode.substring(start, i);
                String replacement = classReplacementForToken(sourceCode, start, i,
                        token, classEntries, currentClass);
                out.append(replacement == null ? token : replacement);
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String replaceDeclaredClassNames(String sourceCode, List<RenameEntry> classEntries,
                                                    String currentClass) {
        String result = sourceCode;
        for (RenameEntry entry : classEntries) {
            if (!classEntryBelongsToCurrentSource(entry, currentClass)) {
                continue;
            }
            result = replaceDeclaredClassName(result, entry.oldName(), entry.newName());
            String ownerSimple = simpleClassName(entry.className());
            if (!ownerSimple.equals(entry.oldName())) {
                result = replaceDeclaredClassName(result, ownerSimple, entry.newName());
            }
            int dollar = ownerSimple.lastIndexOf('$');
            if (dollar >= 0) {
                result = replaceDeclaredClassName(result, ownerSimple.substring(dollar + 1), entry.newName());
            }
            int oldDollar = entry.oldName().lastIndexOf('$');
            if (oldDollar >= 0) {
                result = replaceDeclaredClassName(result, entry.oldName().substring(oldDollar + 1), entry.newName());
            }
        }
        return result;
    }

    private static boolean classEntryBelongsToCurrentSource(RenameEntry entry, String currentClass) {
        String target = normalizeInternalName(entry.className());
        String current = normalizeInternalName(currentClass);
        String strippedTarget = stripContainerClassPrefix(target);
        String strippedCurrent = stripContainerClassPrefix(current);
        return !target.isBlank() && !current.isBlank()
                && (strippedTarget.equals(strippedCurrent)
                || strippedTarget.startsWith(strippedCurrent + "$")
                || strippedCurrent.startsWith(strippedTarget + "$"));
    }

    private static String replaceDeclaredClassName(String sourceCode, String oldName, String newName) {
        if (oldName == null || oldName.isBlank() || newName == null || newName.isBlank()
                || oldName.equals(newName)) {
            return sourceCode;
        }
        StringBuilder out = new StringBuilder(sourceCode.length());
        int i = 0;
        while (i < sourceCode.length()) {
            char ch = sourceCode.charAt(i);
            if (startsWith(sourceCode, i, "//")) {
                int end = sourceCode.indexOf('\n', i + 2);
                if (end < 0) {
                    out.append(sourceCode, i, sourceCode.length());
                    break;
                }
                out.append(sourceCode, i, end + 1);
                i = end + 1;
                continue;
            }
            if (startsWith(sourceCode, i, "/*")) {
                int end = sourceCode.indexOf("*/", i + 2);
                int next = end < 0 ? sourceCode.length() : end + 2;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (startsWith(sourceCode, i, "\"\"\"")) {
                int end = sourceCode.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? sourceCode.length() : end + 3;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                int next = skipQuoted(sourceCode, i, ch);
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int start = i;
                i++;
                while (i < sourceCode.length()
                        && Character.isJavaIdentifierPart(sourceCode.charAt(i))) {
                    i++;
                }
                String token = sourceCode.substring(start, i);
                out.append(oldName.equals(token) && isClassDeclarationName(sourceCode, start, i)
                        ? newName : token);
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String classReplacementForToken(String source, int start, int end,
                                                   String token, List<RenameEntry> classEntries,
                                                   String currentClass) {
        if (isPackageDeclarationToken(source, start)) {
            return null;
        }
        String qualifiedReplacement = qualifiedClassReplacementForToken(source, start, end,
                token, classEntries);
        if (qualifiedReplacement != null) {
            return qualifiedReplacement;
        }
        RenameEntry currentClassEntry = classEntries.stream()
                .filter(entry -> classEntryTargetsInternalName(entry, currentClass))
                .findFirst()
                .orElse(null);
        if (isClassDeclarationName(source, start, end)) {
            for (RenameEntry entry : classEntries) {
                if (entry.oldName().equals(token) || classEntryMatchesToken(entry, token)) {
                    return entry.newName();
                }
            }
        }
        if (isConstructorDeclarationToken(source, start, end)) {
            RenameEntry declarationEntry = classEntryForDeclaredName(classEntries, source);
            if (declarationEntry != null && classEntryMatchesToken(declarationEntry, token)) {
                return declarationEntry.newName();
            }
        }
        if (currentClassEntry != null && classEntryMatchesToken(currentClassEntry, token)) {
            if (isConstructorToken(source, start, end, currentClassEntry)
                    || isClassContext(source, start, end)
                    || isLikelyTypeUsage(source, start, end)
                    || isStaticClassQualifier(source, start, end)) {
                return currentClassEntry.newName();
            }
        }
        if (!isClassContext(source, start, end)
                && !isLikelyTypeUsage(source, start, end)
                && !isStaticClassQualifier(source, start, end)
                && !isAnnotationUsage(source, start)) {
            return null;
        }
        RenameEntry resolved = classEntryForTokenInSource(source, token, classEntries, currentClass);
        return resolved == null ? null : resolved.newName();
    }

    private static RenameEntry classEntryForTokenInSource(String source, String token,
                                                          List<RenameEntry> classEntries,
                                                          String currentClass) {
        RenameEntry imported = importedClassEntryForToken(source, token, classEntries);
        if (imported != null) {
            return imported;
        }
        RenameEntry samePackage = samePackageClassEntryForToken(source, token, classEntries);
        if (samePackage != null) {
            return samePackage;
        }
        RenameEntry current = classEntries.stream()
                .filter(entry -> classEntryTargetsInternalName(entry, currentClass))
                .filter(entry -> classEntryMatchesToken(entry, token))
                .findFirst()
                .orElse(null);
        if (current != null) {
            return current;
        }
        List<RenameEntry> matches = classEntries.stream()
                .filter(entry -> classEntryMatchesToken(entry, token))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static RenameEntry importedClassEntryForToken(String source, String token,
                                                          List<RenameEntry> classEntries) {
        Matcher matcher = IMPORT_LINE_PATTERN.matcher(source);
        while (matcher.find()) {
            String imported = matcher.group(1);
            if (imported == null || imported.endsWith(".*")) {
                continue;
            }
            String importedSimple = visibleClassLeaf(normalizeInternalName(imported));
            if (!importedSimple.equals(token)) {
                continue;
            }
            for (RenameEntry entry : classEntries) {
                if (qualifiedPrefixTargetsClass(imported, entry)) {
                    return entry;
                }
            }
        }
        return null;
    }

    private static RenameEntry samePackageClassEntryForToken(String source, String token,
                                                             List<RenameEntry> classEntries) {
        String pkg = sourcePackageInternal(source);
        if (pkg.isBlank()) {
            return null;
        }
        String candidate = pkg + "/" + token;
        for (RenameEntry entry : classEntries) {
            if (sameInternalClassName(entry.className(), candidate)) {
                return entry;
            }
        }
        return null;
    }

    private static String sourcePackageInternal(String source) {
        Matcher matcher = PACKAGE_LINE_PATTERN.matcher(source);
        return matcher.find() ? matcher.group(1).replace('.', '/') : "";
    }

    private static String qualifiedClassReplacementForToken(String source, int start, int end,
                                                            String token,
                                                            List<RenameEntry> classEntries) {
        String qualifiedPrefix = qualifiedNamePrefixAt(source, start, end);
        if (qualifiedPrefix.isBlank()) {
            return null;
        }
        for (RenameEntry entry : classEntries) {
            if ((entry.oldName().equals(token) || classEntryMatchesToken(entry, token))
                    && qualifiedPrefixTargetsClass(qualifiedPrefix, entry)) {
                return entry.newName();
            }
        }
        return null;
    }

    private static String qualifiedNamePrefixAt(String source, int start, int end) {
        int first = start;
        while (first >= 2 && source.charAt(first - 1) == '.') {
            int segmentEnd = first - 1;
            int segmentStart = segmentEnd - 1;
            while (segmentStart >= 0
                    && Character.isJavaIdentifierPart(source.charAt(segmentStart))) {
                segmentStart--;
            }
            segmentStart++;
            if (segmentStart >= segmentEnd
                    || !Character.isJavaIdentifierStart(source.charAt(segmentStart))) {
                break;
            }
            first = segmentStart;
        }
        if (first == start) {
            return "";
        }
        return source.substring(first, end);
    }

    private static boolean qualifiedPrefixTargetsClass(String qualifiedPrefix, RenameEntry entry) {
        if (qualifiedPrefix == null || qualifiedPrefix.isBlank()
                || entry == null || entry.className().isBlank()) {
            return false;
        }
        String normalizedPrefix = normalizeInternalName(qualifiedPrefix);
        String target = normalizeInternalName(entry.className());
        if (sameInternalClassName(normalizedPrefix, target)) {
            return true;
        }
        return target.indexOf('$') >= 0
                && normalizedPrefix.equals(target.replace('$', '/'));
    }

    private static RenameEntry classEntryForDeclaredName(List<RenameEntry> classEntries, String source) {
        Token declared = declaredClassToken(source);
        if (declared == null) {
            return null;
        }
        for (RenameEntry entry : classEntries) {
            if (entry.oldName().equals(declared.name()) || classEntryMatchesToken(entry, declared.name())) {
                return entry;
            }
        }
        return null;
    }

    private static Token declaredClassToken(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String[] keywords = {"class", "interface", "enum", "record"};
        for (String keyword : keywords) {
            Matcher matcher = Pattern
                    .compile("\\b" + keyword + "\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)")
                    .matcher(source);
            if (matcher.find()) {
                return new Token(matcher.group(1), matcher.start(1), matcher.end(1));
            }
        }
        Matcher annotation = Pattern
                .compile("@\\s*interface\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)")
                .matcher(source);
        return annotation.find() ? new Token(annotation.group(1), annotation.start(1), annotation.end(1)) : null;
    }

    private static boolean isLikelyTypeUsage(String source, int start, int end) {
        if (isQualifiedIdentifier(source, start)) {
            return false;
        }
        int next = nextNonWhitespace(source, end);
        while (next >= 0 && next + 1 < source.length() && source.charAt(next) == '[') {
            int close = nextNonWhitespace(source, next + 1);
            if (close < 0 || close >= source.length() || source.charAt(close) != ']') {
                break;
            }
            next = nextNonWhitespace(source, close + 1);
        }
        if (next >= 0 && next < source.length() && Character.isJavaIdentifierStart(source.charAt(next))) {
            Token nextToken = tokenAt(source, next);
            if (nextToken == null) {
                return false;
            }
            int afterNext = nextNonWhitespace(source, nextToken.end());
            if (afterNext >= 0 && afterNext < source.length()
                    && (source.charAt(afterNext) == '(' || source.charAt(afterNext) == '='
                    || source.charAt(afterNext) == ';' || source.charAt(afterNext) == ','
                    || source.charAt(afterNext) == ')' || source.charAt(afterNext) == '[')) {
                return true;
            }
        }
        int prev = previousNonWhitespace(source, start - 1);
        next = nextNonWhitespace(source, end);
        return prev >= 0 && next >= 0 && source.charAt(prev) == '(' && source.charAt(next) == ')';
    }

    private static boolean isStaticClassQualifier(String source, int start, int end) {
        if (isQualifiedIdentifier(source, start)) {
            return false;
        }
        int next = nextNonWhitespace(source, end);
        if (next < 0 || next >= source.length() || source.charAt(next) != '.') {
            return false;
        }
        int afterDot = nextNonWhitespace(source, next + 1);
        return afterDot >= 0 && afterDot < source.length()
                && Character.isJavaIdentifierStart(source.charAt(afterDot));
    }

    private static boolean isAnnotationUsage(String source, int start) {
        int prev = previousNonWhitespace(source, start - 1);
        return prev >= 0 && source.charAt(prev) == '@';
    }

    private static boolean isPackageDeclarationToken(String source, int start) {
        int lineStart = source.lastIndexOf('\n', start);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        String prefix = source.substring(lineStart, start).stripLeading();
        return prefix.startsWith("package ");
    }

    private static boolean isClassDeclarationName(String source, int start, int end) {
        if (isPreviousKeyword(source, start, "interface")) {
            int prev = previousNonWhitespace(source, start - 1);
            int at = previousNonWhitespace(source, prev - 1);
            return at < 0 || source.charAt(at) != '@';
        }
        int prev = previousIdentifierStart(source, start);
        if (prev >= 0 && source.startsWith("interface", prev)) {
            int at = previousNonWhitespace(source, prev - 1);
            if (at >= 0 && source.charAt(at) == '@') {
                return true;
            }
        }
        return isPreviousKeyword(source, start, "class")
                || isPreviousKeyword(source, start, "interface")
                || isPreviousKeyword(source, start, "enum")
                || isPreviousKeyword(source, start, "record");
    }

    private static int previousIdentifierStart(String source, int beforeOffset) {
        Token token = previousIdentifier(source, beforeOffset);
        return token == null ? -1 : token.start();
    }

    private static boolean isConstructorToken(String source, int start, int end,
                                              RenameEntry currentClassEntry) {
        int next = nextNonWhitespace(source, end);
        if (next < 0 || next >= source.length() || source.charAt(next) != '(') {
            return false;
        }
        if (!classEntryMatchesToken(currentClassEntry, source.substring(start, end))) {
            return false;
        }
        int lineStart = source.lastIndexOf('\n', start);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        String prefix = source.substring(lineStart, start).strip();
        if (prefix.isBlank()) {
            return true;
        }
        String[] words = prefix.split("\\s+");
        for (String word : words) {
            if (!CONSTRUCTOR_PREFIX_WORDS.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isConstructorDeclarationToken(String source, int start, int end) {
        int next = nextNonWhitespace(source, end);
        if (next < 0 || next >= source.length() || source.charAt(next) != '(') {
            return false;
        }
        int lineStart = source.lastIndexOf('\n', start);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        String prefix = source.substring(lineStart, start).strip();
        if (prefix.isBlank()) {
            return true;
        }
        String[] words = prefix.split("\\s+");
        for (String word : words) {
            if (!CONSTRUCTOR_PREFIX_WORDS.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String chooseReplacement(String source, int start, int end, String oldName,
                                            List<RenameEntry> entries, String currentClass,
                                            List<MethodScope> scopes) {
        boolean methodContext = isMethodContext(source, start, end);
        boolean classContext = isClassContext(source, start, end);
        boolean qualified = isQualifiedIdentifier(source, start);

        if (classContext) {
            String classReplacement = firstClassReplacement(entries, oldName);
            if (classReplacement != null) {
                return classReplacement;
            }
        }

        MethodScope scope = scopeFor(scopes, start);
        if (scope != null && !qualified && !methodContext) {
            for (RenameEntry entry : entries) {
                if (TYPE_PARAM.equals(entry.type()) && entry.oldName().equals(oldName)
                        && appliesToClass(entry, currentClass)
                        && scopeMatches(entry, scope, entries, currentClass)) {
                    return entry.newName();
                }
            }
        }

        if (methodContext) {
            for (RenameEntry entry : entries) {
                if (TYPE_METHOD.equals(entry.type()) && entry.oldName().equals(oldName)
                        && appliesToClass(entry, currentClass)) {
                    return entry.newName();
                }
            }
        } else {
            for (RenameEntry entry : entries) {
                if (TYPE_FIELD.equals(entry.type()) && entry.oldName().equals(oldName)
                        && appliesToClass(entry, currentClass)
                        && !fieldBlockedByMethodLocal(scope, qualified, oldName)) {
                    return entry.newName();
                }
            }
        }

        for (RenameEntry entry : entries) {
            if (TYPE_IDENTIFIER.equals(entry.type()) && entry.oldName().equals(oldName)
                    && appliesToClass(entry, currentClass)) {
                return entry.newName();
            }
        }
        for (RenameEntry entry : entries) {
            if (entry.oldName().equals(oldName)
                    && (TYPE_METHOD.equals(entry.type()) || TYPE_FIELD.equals(entry.type())
                    || TYPE_PARAM.equals(entry.type()) || TYPE_IDENTIFIER.equals(entry.type()))
                    && appliesToClass(entry, currentClass)
                    && !(TYPE_FIELD.equals(entry.type())
                    && fieldBlockedByMethodLocal(scope, qualified, oldName))) {
                return entry.newName();
            }
        }
        return null;
    }

    private static boolean fieldBlockedByMethodLocal(MethodScope scope, boolean qualified, String name) {
        return scope != null && !qualified
                && (scope.params().contains(name) || scope.locals().contains(name));
    }

    private static String replaceVisibleIdentifiers(String sourceCode, List<RenameEntry> entries) {
        if (sourceCode == null || sourceCode.isEmpty() || entries == null || entries.isEmpty()) {
            return sourceCode;
        }
        StringBuilder out = new StringBuilder(sourceCode.length());
        int i = 0;
        while (i < sourceCode.length()) {
            char ch = sourceCode.charAt(i);
            if (startsWith(sourceCode, i, "//")) {
                int end = sourceCode.indexOf('\n', i + 2);
                if (end < 0) {
                    out.append(sourceCode, i, sourceCode.length());
                    break;
                }
                out.append(sourceCode, i, end + 1);
                i = end + 1;
                continue;
            }
            if (startsWith(sourceCode, i, "/*")) {
                int end = sourceCode.indexOf("*/", i + 2);
                int next = end < 0 ? sourceCode.length() : end + 2;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (startsWith(sourceCode, i, "\"\"\"")) {
                int end = sourceCode.indexOf("\"\"\"", i + 3);
                int next = end < 0 ? sourceCode.length() : end + 3;
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (ch == '"' || ch == '\'') {
                int next = skipQuoted(sourceCode, i, ch);
                out.append(sourceCode, i, next);
                i = next;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int start = i;
                i++;
                while (i < sourceCode.length()
                        && Character.isJavaIdentifierPart(sourceCode.charAt(i))) {
                    i++;
                }
                String token = sourceCode.substring(start, i);
                String replacement = null;
                for (RenameEntry entry : entries) {
                    if (entry.oldName().equals(token)) {
                        replacement = entry.newName();
                        break;
                    }
                }
                out.append(replacement == null ? token : replacement);
                continue;
            }
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    private static String firstClassReplacement(List<RenameEntry> entries, String oldName) {
        for (RenameEntry entry : entries) {
            if (TYPE_CLASS.equals(entry.type())
                    && classEntryMatchesToken(entry, oldName)) {
                return entry.newName();
            }
        }
        return null;
    }

    private static boolean classEntryTargetsInternalName(RenameEntry entry, String internalName) {
        if (entry == null || !TYPE_CLASS.equals(entry.type())) {
            return false;
        }
        String normalized = normalizeInternalName(internalName);
        String entryClass = normalizeInternalName(entry.className());
        if (!entryClass.isBlank() && sameInternalClassName(entryClass, normalized)) {
            return true;
        }
        return classEntryMatchesToken(entry, simpleClassName(normalized));
    }

    private static boolean classEntryMatchesToken(RenameEntry entry, String token) {
        if (entry.oldName().equals(token)) {
            return true;
        }
        int oldDollar = entry.oldName().lastIndexOf('$');
        if (oldDollar >= 0 && entry.oldName().substring(oldDollar + 1).equals(token)) {
            return true;
        }
        String ownerSimple = simpleClassName(entry.className());
        if (ownerSimple.equals(token)) {
            return true;
        }
        int dollar = ownerSimple.lastIndexOf('$');
        return dollar >= 0 && ownerSimple.substring(dollar + 1).equals(token);
    }

    private static boolean isConstructorName(String source, Token token, String currentClass,
                                             List<RenameEntry> entries) {
        if (source == null || token == null) {
            return false;
        }
        String simple = simpleClassName(currentClass);
        if (!token.name().equals(simple) && !classAliasMatches(entries, currentClass, simple, token.name())) {
            return false;
        }
        int next = nextNonWhitespace(source, token.end());
        if (next < 0 || next >= source.length() || source.charAt(next) != '(') {
            return false;
        }
        int prevLineStart = source.lastIndexOf('\n', token.start());
        prevLineStart = prevLineStart < 0 ? 0 : prevLineStart + 1;
        String prefix = source.substring(prevLineStart, token.start()).strip();
        if (prefix.isBlank()) {
            return true;
        }
        String[] words = prefix.split("\\s+");
        for (String word : words) {
            if (!CONSTRUCTOR_PREFIX_WORDS.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static boolean classAliasMatches(List<RenameEntry> entries, String currentClass,
                                             String oldClassName, String visibleName) {
        for (RenameEntry entry : entries) {
            if (TYPE_CLASS.equals(entry.type())
                    && entry.newName().equals(visibleName)
                    && (entry.oldName().equals(oldClassName)
                    || classEntryTargetsInternalName(entry, currentClass))) {
                return true;
            }
        }
        return false;
    }

    private static boolean appliesToClass(RenameEntry entry, String currentClass) {
        return entry.className().isBlank() || currentClass == null || currentClass.isBlank()
                || sameInternalClassName(entry.className(), currentClass);
    }

    private static boolean scopeMatches(RenameEntry entry, MethodScope scope,
                                        List<RenameEntry> entries, String currentClass) {
        if (entry.desc().isBlank()) {
            return true;
        }
        String descName = methodNameFromDesc(entry.desc());
        if (!descName.isBlank() && !descName.equals(scope.name())
                && !methodAliasMatches(entries, currentClass, descName, scope.name())) {
            return false;
        }
        int count = descriptorParamCount(entry.desc());
        return count < 0 || count == scope.paramCount();
    }

    private static boolean methodAliasMatches(List<RenameEntry> entries, String currentClass,
                                              String oldMethodName, String visibleMethodName) {
        for (RenameEntry entry : entries) {
            if (TYPE_METHOD.equals(entry.type())
                    && entry.oldName().equals(oldMethodName)
                    && entry.newName().equals(visibleMethodName)
                    && appliesToClass(entry, currentClass)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<RenameEntry> findAliasEntry(List<RenameEntry> entries, String visibleName,
                                                        String type, String currentClass,
                                                        MethodScope scope) {
        if (visibleName == null || visibleName.isBlank()) {
            return Optional.empty();
        }
        List<RenameEntry> matches = new ArrayList<>();
        for (RenameEntry entry : entries) {
            if (!entry.type().equals(type) || !entry.newName().equals(visibleName)) {
                continue;
            }
            if ((TYPE_FIELD.equals(type) || TYPE_METHOD.equals(type) || TYPE_IDENTIFIER.equals(type))
                    && !appliesToClass(entry, currentClass)) {
                continue;
            }
            if (TYPE_PARAM.equals(type)
                    && (!appliesToClass(entry, currentClass)
                    || scope == null || !scopeMatches(entry, scope, entries, currentClass))) {
                continue;
            }
            matches.add(entry);
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        for (RenameEntry entry : matches) {
            if (sameInternalClassName(entry.className(), currentClass)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private static MethodScope scopeFor(List<MethodScope> scopes, int offset) {
        for (MethodScope scope : scopes) {
            if (scope.contains(offset)) {
                return scope;
            }
        }
        return null;
    }

    private static Optional<String> findClassOwner(String token, String currentClass,
                                                   WorkspaceIndex index) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        if (matchesClassToken(currentClass, token)) {
            return Optional.of(currentClass);
        }
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return Optional.empty();
        }
        List<String> matches = new ArrayList<>();
        for (ClassIndexEntry cls : index.classes()) {
            if (matchesClassToken(cls.internalName(), token)) {
                matches.add(cls.internalName());
            }
        }
        if (matches.size() == 1) {
            return Optional.of(matches.get(0));
        }
        return Optional.empty();
    }

    private static boolean matchesClassToken(String internalName, String token) {
        if (internalName == null || internalName.isBlank()) {
            return false;
        }
        String simple = simpleClassName(internalName);
        if (simple.equals(token)) {
            return true;
        }
        int dollar = simple.lastIndexOf('$');
        return dollar >= 0 && simple.substring(dollar + 1).equals(token);
    }

    private static MemberRef findMethodOwnerAndDesc(String name, String currentClass,
                                                    byte[] classBytes, WorkspaceIndex index,
                                                    int paramCount) {
        String currentDesc = findMethodDescInBytes(name, classBytes, paramCount);
        if (currentDesc != null) {
            return new MemberRef(currentClass, currentDesc);
        }
        if (index != null && index != WorkspaceIndex.EMPTY) {
            List<MemberRef> matches = new ArrayList<>();
            for (ClassIndexEntry cls : index.classes()) {
                for (MemberIndexEntry method : cls.methods()) {
                    if (method.name().equals(name)
                            && (paramCount < 0 || descriptorParamCount(method.descriptor()) == paramCount)) {
                        matches.add(new MemberRef(cls.internalName(), method.descriptor()));
                    }
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            for (MemberRef match : matches) {
                if (sameInternalClassName(match.owner(), currentClass)) {
                    return match;
                }
            }
        }
        return new MemberRef(currentClass, "");
    }

    private static MemberRef findFieldOwnerAndDesc(String name, String currentClass,
                                                   byte[] classBytes, WorkspaceIndex index) {
        String currentDesc = findFieldDescInBytes(name, classBytes);
        if (currentDesc != null) {
            return new MemberRef(currentClass, currentDesc);
        }
        if (index != null && index != WorkspaceIndex.EMPTY) {
            List<MemberRef> matches = new ArrayList<>();
            for (ClassIndexEntry cls : index.classes()) {
                for (MemberIndexEntry field : cls.fields()) {
                    if (field.name().equals(name)) {
                        matches.add(new MemberRef(cls.internalName(), field.descriptor()));
                    }
                }
            }
            if (matches.size() == 1) {
                return matches.get(0);
            }
            for (MemberRef match : matches) {
                if (sameInternalClassName(match.owner(), currentClass)) {
                    return match;
                }
            }
        }
        return null;
    }

    private static String methodDesc(String currentClass, String paramName, byte[] classBytes,
                                     WorkspaceIndex index, MethodScope scope,
                                     List<RenameEntry> entries) {
        RenameEntry methodAlias = findMethodAliasForVisible(entries, currentClass, scope.name());
        String rawMethodName = methodAlias == null ? scope.name() : methodAlias.oldName();
        if (methodAlias != null && !methodAlias.desc().isBlank()) {
            return rawMethodName + methodAlias.desc();
        }
        String desc = findMethodDescInBytes(rawMethodName, classBytes, scope.paramCount());
        if (desc != null) {
            return rawMethodName + desc;
        }
        MemberRef method = findMethodOwnerAndDesc(rawMethodName, currentClass,
                classBytes, index, scope.paramCount());
        if (method != null && !method.desc().isBlank()) {
            return rawMethodName + method.desc();
        }
        return rawMethodName;
    }

    private static RenameEntry findMethodAliasForVisible(List<RenameEntry> entries,
                                                         String currentClass,
                                                         String visibleMethodName) {
        if (entries == null || visibleMethodName == null || visibleMethodName.isBlank()) {
            return null;
        }
        for (RenameEntry entry : entries) {
            if (TYPE_METHOD.equals(entry.type())
                    && entry.newName().equals(visibleMethodName)
                    && appliesToClass(entry, currentClass)) {
                return entry;
            }
        }
        return null;
    }

    private static String findMethodDescInBytes(String name, byte[] classBytes, int paramCount) {
        if (classBytes == null || classBytes.length == 0) {
            return null;
        }
        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(classBytes);
        if (metadata.isEmpty()) {
            return null;
        }
        List<ClassFileMetadata.MemberInfo> matches = new ArrayList<>();
        for (ClassFileMetadata.MemberInfo method : metadata.get().methods()) {
            if (method.name().equals(name)
                    && (paramCount < 0 || descriptorParamCount(method.descriptor()) == paramCount)) {
                matches.add(method);
            }
        }
        return matches.size() == 1 ? matches.get(0).descriptor() : null;
    }

    private static String findFieldDescInBytes(String name, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return null;
        }
        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(classBytes);
        if (metadata.isEmpty()) {
            return null;
        }
        for (ClassFileMetadata.MemberInfo field : metadata.get().fields()) {
            if (field.name().equals(name)) {
                return field.descriptor();
            }
        }
        return null;
    }

    static int descriptorParamCount(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) {
            return -1;
        }
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')', start + 1);
        if (start < 0 || end < 0) {
            return -1;
        }
        int count = 0;
        for (int i = start + 1; i < end; i++) {
            char ch = descriptor.charAt(i);
            while (ch == '[' && i + 1 < end) {
                i++;
                ch = descriptor.charAt(i);
            }
            if (ch == 'L') {
                int semi = descriptor.indexOf(';', i);
                if (semi < 0 || semi > end) {
                    return -1;
                }
                i = semi;
            }
            count++;
        }
        return count;
    }

    private static String methodNameFromDesc(String desc) {
        if (desc == null || desc.isBlank() || desc.charAt(0) == '(') {
            return "";
        }
        int paren = desc.indexOf('(');
        if (paren >= 0) {
            return desc.substring(0, paren);
        }
        int hash = desc.indexOf('#');
        if (hash >= 0) {
            return desc.substring(0, hash);
        }
        return desc;
    }

    private static List<MethodScope> findMethodScopes(String source, String classSimpleName) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<MethodScope> scopes = new ArrayList<>();
        int[] lineStarts = lineStarts(source);
        for (int lineStart : lineStarts) {
            int lineEnd = lineEnd(source, lineStart);
            String firstLine = source.substring(lineStart, lineEnd).strip();
            if (firstLine.isBlank() || firstLine.startsWith("//")
                    || firstLine.startsWith("*") || firstLine.startsWith("@")) {
                continue;
            }
            int openParen = source.indexOf('(', lineStart);
            if (openParen < 0 || openParen > Math.min(source.length(), lineStart + 600)) {
                continue;
            }
            int closeParen = findMatchingParen(source, openParen);
            if (closeParen < 0) {
                continue;
            }
            int brace = nextNonWhitespace(source, closeParen + 1);
            while (brace >= 0 && source.charAt(brace) != '{' && source.charAt(brace) != ';') {
                if (source.charAt(brace) == '\n' && brace - closeParen > 500) {
                    break;
                }
                brace++;
                if (brace >= source.length()) {
                    brace = -1;
                    break;
                }
            }
            if (brace < 0 || brace >= source.length() || source.charAt(brace) != '{') {
                continue;
            }
            Token nameToken = previousIdentifier(source, openParen);
            if (nameToken == null || METHOD_HEADER_KEYWORDS.contains(nameToken.name())) {
                continue;
            }
            if (!looksLikeMethodHeader(source.substring(lineStart, openParen),
                    nameToken.name(), classSimpleName)) {
                continue;
            }
            int bodyEnd = findMatchingBrace(source, brace);
            if (bodyEnd < 0) {
                bodyEnd = source.length() - 1;
            }
            Set<String> params = parseParameterNames(source.substring(openParen + 1, closeParen));
            Set<String> locals = parseLocalVariableNames(source, brace + 1, bodyEnd);
            scopes.add(new MethodScope(nameToken.name(), lineStart, bodyEnd + 1, params, locals));
        }
        return scopes;
    }

    private static boolean looksLikeMethodHeader(String beforeParen, String methodName,
                                                 String classSimpleName) {
        String beforeName = beforeParen.substring(0,
                Math.max(0, beforeParen.lastIndexOf(methodName))).strip();
        if (beforeName.isBlank()) {
            return methodName.equals(classSimpleName);
        }
        String normalized = beforeName.replace('\n', ' ').replace('\r', ' ').strip();
        if (normalized.endsWith("=") || normalized.endsWith(".") || normalized.endsWith("->")) {
            return false;
        }
        String lastWord = lastWord(normalized);
        return !METHOD_HEADER_KEYWORDS.contains(lastWord);
    }

    private static Set<String> parseParameterNames(String paramsText) {
        if (paramsText == null || paramsText.isBlank()) {
            return Set.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (String rawParam : splitParams(paramsText)) {
            String param = rawParam
                    .replaceAll("@[\\w.$]+(?:\\([^)]*\\))?", " ")
                    .replace("final ", " ")
                    .replace("...", " ")
                    .strip();
            Matcher matcher = Pattern
                    .compile("([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(?:\\[\\s*\\])?\\s*$")
                    .matcher(param);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (!RESERVED_NAMES.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    private static Set<String> parseLocalVariableNames(String source, int bodyStart, int bodyEnd) {
        if (source == null || source.isEmpty() || bodyStart < 0 || bodyEnd <= bodyStart) {
            return Set.of();
        }
        String body = source.substring(bodyStart, Math.min(bodyEnd, source.length()));
        String masked = maskCommentsAndStrings(body);
        Set<String> names = new LinkedHashSet<>();
        collectLocalDeclarations(masked, names);
        collectCatchAndForVariables(masked, names);
        collectLambdaParameters(masked, names);
        return names;
    }

    private static void collectLocalDeclarations(String body, Set<String> names) {
        Matcher matcher = LOCAL_DECL_PATTERN.matcher(body);
        while (matcher.find()) {
            String firstTypeWord = firstWord(matcher.group(2));
            if (LOCAL_DECL_SKIP_WORDS.contains(firstTypeWord)) {
                continue;
            }
            addLocalName(names, matcher.group(3));
            int statementEnd = findStatementEnd(body, matcher.end(3));
            Matcher commaMatcher = COMMA_LOCAL_PATTERN.matcher(
                    body.substring(matcher.end(3), statementEnd));
            while (commaMatcher.find()) {
                addLocalName(names, commaMatcher.group(1));
            }
        }
    }

    private static void collectCatchAndForVariables(String body, Set<String> names) {
        Matcher catchMatcher = CATCH_PARAM_PATTERN.matcher(body);
        while (catchMatcher.find()) {
            addLocalName(names, catchMatcher.group(1));
        }
        Matcher forMatcher = FOR_PARAM_PATTERN.matcher(body);
        while (forMatcher.find()) {
            addLocalName(names, forMatcher.group(1));
        }
    }

    private static void collectLambdaParameters(String body, Set<String> names) {
        Matcher groupMatcher = LAMBDA_GROUP_PATTERN.matcher(body);
        while (groupMatcher.find()) {
            for (String raw : splitParams(groupMatcher.group(1))) {
                String param = raw.strip();
                if (param.isBlank()) {
                    continue;
                }
                Matcher typed = Pattern
                        .compile("([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*$")
                        .matcher(param);
                if (typed.find()) {
                    addLocalName(names, typed.group(1));
                }
            }
        }
        Matcher singleMatcher = LAMBDA_SINGLE_PATTERN.matcher(body);
        while (singleMatcher.find()) {
            addLocalName(names, singleMatcher.group(1));
        }
    }

    private static String maskCommentsAndStrings(String source) {
        char[] chars = source.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (i + 1 < chars.length && chars[i] == '/' && chars[i + 1] == '/') {
                int end = source.indexOf('\n', i + 2);
                int limit = end < 0 ? chars.length : end;
                for (int j = i; j < limit; j++) {
                    chars[j] = ' ';
                }
                i = limit;
                continue;
            }
            if (i + 1 < chars.length && chars[i] == '/' && chars[i + 1] == '*') {
                int end = source.indexOf("*/", i + 2);
                int limit = end < 0 ? chars.length : Math.min(chars.length, end + 2);
                for (int j = i; j < limit; j++) {
                    if (chars[j] != '\n' && chars[j] != '\r') {
                        chars[j] = ' ';
                    }
                }
                i = limit - 1;
                continue;
            }
            if (chars[i] == '"' || chars[i] == '\'') {
                char quote = chars[i];
                int end = skipQuoted(source, i, quote);
                for (int j = i; j < end; j++) {
                    if (chars[j] != '\n' && chars[j] != '\r') {
                        chars[j] = ' ';
                    }
                }
                i = end - 1;
            }
        }
        return new String(chars);
    }

    private static void addLocalName(Set<String> names, String name) {
        if (name != null && JAVA_ID.matcher(name).matches() && !RESERVED_NAMES.contains(name)) {
            names.add(name);
        }
    }

    private static int findStatementEnd(String text, int start) {
        int depthParen = 0;
        int depthAngle = 0;
        for (int i = Math.max(0, start); i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') {
                depthParen++;
            } else if (ch == ')' && depthParen > 0) {
                depthParen--;
            } else if (ch == '<') {
                depthAngle++;
            } else if (ch == '>' && depthAngle > 0) {
                depthAngle--;
            } else if ((ch == ';' || ch == '\n') && depthParen == 0 && depthAngle == 0) {
                return i;
            }
        }
        return text.length();
    }

    private static String firstWord(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern
                .compile("([a-zA-Z_$][a-zA-Z0-9_$]*)")
                .matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static List<String> splitParams(String paramsText) {
        List<String> result = new ArrayList<>();
        int depthAngle = 0;
        int depthParen = 0;
        int start = 0;
        for (int i = 0; i < paramsText.length(); i++) {
            char ch = paramsText.charAt(i);
            if (ch == '<') {
                depthAngle++;
            } else if (ch == '>' && depthAngle > 0) {
                depthAngle--;
            } else if (ch == '(') {
                depthParen++;
            } else if (ch == ')' && depthParen > 0) {
                depthParen--;
            } else if (ch == ',' && depthAngle == 0 && depthParen == 0) {
                result.add(paramsText.substring(start, i));
                start = i + 1;
            }
        }
        result.add(paramsText.substring(start));
        return result;
    }

    private static boolean isMethodContext(String source, int start, int end) {
        int next = nextNonWhitespace(source, end);
        if (next >= 0 && next < source.length() && source.charAt(next) == '(') {
            return !isPreviousKeyword(source, start, "new");
        }
        int prev = previousNonWhitespace(source, start - 1);
        return prev >= 1 && source.charAt(prev) == ':' && source.charAt(prev - 1) == ':';
    }

    private static boolean isClassContext(String source, int start, int end) {
        if (isPreviousKeyword(source, start, "new")
                || isPreviousKeyword(source, start, "class")
                || isPreviousKeyword(source, start, "interface")
                || isPreviousKeyword(source, start, "enum")
                || isPreviousKeyword(source, start, "record")
                || isPreviousKeyword(source, start, "extends")
                || isPreviousKeyword(source, start, "implements")
                || isPreviousKeyword(source, start, "throws")
                || isPreviousKeyword(source, start, "import")) {
            return true;
        }
        int next = nextNonWhitespace(source, end);
        return next >= 0 && next < source.length() && source.charAt(next) == '.'
                && isPreviousKeyword(source, start, "import");
    }

    private static boolean isMethodDeclarationName(String source, int start, int end) {
        int next = nextNonWhitespace(source, end);
        if (next < 0 || next >= source.length() || source.charAt(next) != '(') {
            return false;
        }
        int lineStart = source.lastIndexOf('\n', start);
        lineStart = lineStart < 0 ? 0 : lineStart + 1;
        String prefix = source.substring(lineStart, start).strip();
        return !prefix.isBlank() && !prefix.endsWith(".") && !prefix.endsWith("new");
    }

    private static boolean isQualifiedIdentifier(String source, int start) {
        int prev = previousNonWhitespace(source, start - 1);
        return prev >= 0 && source.charAt(prev) == '.';
    }

    private static boolean isPreviousKeyword(String source, int start, String keyword) {
        Token token = previousIdentifier(source, start);
        return token != null && token.name().equals(keyword);
    }

    private static Token previousIdentifier(String source, int beforeOffset) {
        int i = Math.min(beforeOffset - 1, source.length() - 1);
        while (i >= 0 && Character.isWhitespace(source.charAt(i))) {
            i--;
        }
        if (i < 0 || !Character.isJavaIdentifierPart(source.charAt(i))) {
            return null;
        }
        int end = i + 1;
        while (i >= 0 && Character.isJavaIdentifierPart(source.charAt(i))) {
            i--;
        }
        int start = i + 1;
        if (start >= end || !Character.isJavaIdentifierStart(source.charAt(start))) {
            return null;
        }
        return new Token(source.substring(start, end), start, end);
    }

    private static Token tokenAt(String source, int offset) {
        if (source == null || source.isEmpty()) {
            return null;
        }
        int pos = Math.clamp(offset, 0, source.length() - 1);
        if (!Character.isJavaIdentifierPart(source.charAt(pos))
                && pos > 0 && Character.isJavaIdentifierPart(source.charAt(pos - 1))) {
            pos--;
        }
        if (!Character.isJavaIdentifierPart(source.charAt(pos))) {
            return null;
        }
        int start = pos;
        while (start > 0 && Character.isJavaIdentifierPart(source.charAt(start - 1))) {
            start--;
        }
        int end = pos + 1;
        while (end < source.length() && Character.isJavaIdentifierPart(source.charAt(end))) {
            end++;
        }
        if (start >= end || !Character.isJavaIdentifierStart(source.charAt(start))) {
            return null;
        }
        return new Token(source.substring(start, end), start, end);
    }

    private static int[] lineStarts(String source) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n' && i + 1 < source.length()) {
                starts.add(i + 1);
            }
        }
        int[] result = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) {
            result[i] = starts.get(i);
        }
        return result;
    }

    private static int lineEnd(String source, int lineStart) {
        int end = source.indexOf('\n', lineStart);
        return end < 0 ? source.length() : end;
    }

    private static int findMatchingParen(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            } else if (ch == '"' || ch == '\'') {
                i = skipQuoted(source, i, ch) - 1;
            }
        }
        return -1;
    }

    private static int findMatchingBrace(String source, int open) {
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            if (startsWith(source, i, "//")) {
                int end = source.indexOf('\n', i + 2);
                i = end < 0 ? source.length() - 1 : end;
                continue;
            }
            if (startsWith(source, i, "/*")) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? source.length() - 1 : end + 1;
                continue;
            }
            char ch = source.charAt(i);
            if (ch == '"' || ch == '\'') {
                i = skipQuoted(source, i, ch) - 1;
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static int skipQuoted(String source, int start, char quote) {
        int i = start + 1;
        while (i < source.length()) {
            char ch = source.charAt(i);
            if (ch == '\\') {
                i += 2;
                continue;
            }
            i++;
            if (ch == quote) {
                break;
            }
        }
        return Math.min(i, source.length());
    }

    private static int nextNonWhitespace(String source, int start) {
        for (int i = Math.max(0, start); i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int previousNonWhitespace(String source, int start) {
        for (int i = Math.min(start, source.length() - 1); i >= 0; i--) {
            if (!Character.isWhitespace(source.charAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private static String lastWord(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        Matcher matcher = Pattern
                .compile("([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*$")
                .matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static boolean startsWith(String text, int offset, String prefix) {
        return offset >= 0 && offset + prefix.length() <= text.length()
                && text.startsWith(prefix, offset);
    }

    private static RenameEntry normalize(RenameEntry entry) {
        String type = safe(entry.type()).toLowerCase(Locale.ROOT);
        String className = normalizeInternalName(entry.className());
        String oldName = safe(entry.oldName());
        String newName = safe(entry.newName());
        String desc = safe(entry.desc());
        return new RenameEntry(type, className, oldName, newName, desc);
    }

    private static RenameEntry normalizeForStorage(RenameEntry entry) {
        return normalizeForStorage("", entry);
    }

    private static RenameEntry normalizeForStorage(String workspaceHash, RenameEntry entry) {
        RenameEntry normalized = normalize(entry);
        if (!TYPE_CLASS.equals(normalized.type()) || normalized.className().isBlank()) {
            return normalized;
        }
        String originalClass = normalized.className();
        if (workspaceHash != null && !workspaceHash.isBlank()) {
            originalClass = originalInternalName(originalClass, workspaceHash);
        }
        originalClass = normalizeInternalName(originalClass);
        String originalLeaf = visibleClassLeaf(originalClass);
        if (originalLeaf.isBlank() || originalLeaf.equals(normalized.oldName())) {
            return originalClass.equals(normalized.className()) ? normalized
                    : new RenameEntry(normalized.type(), originalClass,
                    normalized.oldName(), normalized.newName(), normalized.desc());
        }
        return new RenameEntry(normalized.type(), originalClass,
                originalLeaf, normalized.newName(), normalized.desc());
    }

    private static boolean sameKey(RenameEntry left, RenameEntry right) {
        return left.type().equals(right.type())
                && (left.className().equals(right.className())
                || sameInternalClassName(left.className(), right.className()))
                && left.oldName().equals(right.oldName())
                && left.desc().equals(right.desc());
    }

    private static List<RenameEntry> dedupeClassRenames(List<RenameEntry> entries) {
        List<RenameEntry> result = new ArrayList<>();
        for (RenameEntry entry : entries) {
            if (TYPE_CLASS.equals(entry.type()) && !entry.className().isBlank()) {
                result.removeIf(existing -> sameClassRenameTarget(existing, entry));
            }
            result.add(entry);
        }
        return result;
    }

    private static boolean sameClassRenameTarget(RenameEntry left, RenameEntry right) {
        return TYPE_CLASS.equals(left.type())
                && TYPE_CLASS.equals(right.type())
                && !left.className().isBlank()
                && sameInternalClassName(left.className(), right.className());
    }

    private static String normalizeInternalName(String className) {
        return ClassNameUtil.normalizeInternalName(className);
    }

    private static boolean sameInternalClassName(String left, String right) {
        return ClassNameUtil.sameInternalName(left, right);
    }

    private static String stripContainerClassPrefix(String internalName) {
        return ClassNameUtil.stripContainerClassPrefix(internalName);
    }

    private static String simpleClassName(String internalName) {
        return ClassNameUtil.simpleName(internalName);
    }

    private static String packageName(String internalName) {
        return ClassNameUtil.packageName(internalName);
    }

    private static String renamedClassInternalName(String internalName, String oldName, String newName) {
        int slash = internalName.lastIndexOf('/');
        String pkg = slash >= 0 ? internalName.substring(0, slash + 1) : "";
        String simple = slash >= 0 ? internalName.substring(slash + 1) : internalName;
        String renamedSimple;
        if (simple.equals(oldName)) {
            int dollar = oldName.lastIndexOf('$');
            renamedSimple = dollar >= 0 ? oldName.substring(0, dollar + 1) + newName : newName;
        } else {
            int dollar = simple.lastIndexOf('$');
            if (dollar >= 0 && simple.substring(dollar + 1).equals(oldName)) {
                renamedSimple = simple.substring(0, dollar + 1) + newName;
            } else {
                renamedSimple = newName;
            }
        }
        return pkg + renamedSimple;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean writeFile(String workspaceHash, List<RenameEntry> list) {
        Path file = resolveFile(workspaceHash);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            snapshotRenameFile(file);
            Files.writeString(tmp, GSON.toJson(list), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.error("保存重命名失败", e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException cleanupError) {
                log.debug("清理重命名临时文件失败: {}", tmp, cleanupError);
            }
            return false;
        }
    }

    private static void snapshotRenameFile(Path file) {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Path backup = file.resolveSibling(file.getFileName() + ".bak." + System.currentTimeMillis());
        try {
            Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.debug("创建重命名快照失败: {}", backup, e);
        }
    }

    private static Object lockFor(String workspaceHash) {
        return WORKSPACE_LOCKS.computeIfAbsent(safe(workspaceHash), key -> new Object());
    }

    private static Path resolveFile(String workspaceHash) {
        return getRootDir().resolve(workspaceHashDigest(workspaceHash) + ".json");
    }

    private static Path resolveLegacyFile(String workspaceHash) {
        String safe = (workspaceHash == null ? "" : workspaceHash).replaceAll("[\\\\/:*?\"<>|]", "_");
        return getRootDir().resolve(safe + ".json");
    }

    private static String workspaceHashDigest(String workspaceHash) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe(workspaceHash).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(safe(workspaceHash).hashCode());
        }
    }

    public record RenameTarget(RenameEntry entry, String kind, String currentName) {
        public RenameTarget {
            Objects.requireNonNull(entry, "entry");
            kind = kind == null || kind.isBlank() ? entry.type() : kind;
            currentName = currentName == null || currentName.isBlank()
                    ? entry.oldName() : currentName;
        }

        public RenameTarget(RenameEntry entry, String kind) {
            this(entry, kind, entry == null ? "" : entry.oldName());
        }
    }

    private record Token(String name, int start, int end) {
    }

    private record MemberRef(String owner, String desc) {
        private MemberRef {
            owner = normalizeInternalName(owner);
            desc = desc == null ? "" : desc;
        }
    }

    private record TypeParse(String name, int nextOffset) {
    }

    private record MethodSignature(String returnType, List<String> params) {
    }

    private record MethodScope(String name, int start, int end,
                               Set<String> params, Set<String> locals) {
        private MethodScope {
            params = Collections.unmodifiableSet(new HashSet<>(params == null ? Set.of() : params));
            locals = Collections.unmodifiableSet(new HashSet<>(locals == null ? Set.of() : locals));
        }

        private boolean contains(int offset) {
            return offset >= start && offset < end;
        }

        private int paramCount() {
            return params.size();
        }
    }

    private static final class RenameEntryAdapter extends TypeAdapter<RenameEntry> {
        @Override
        public void write(JsonWriter out, RenameEntry value) throws IOException {
            out.beginObject();
            out.name("type").value(value.type());
            out.name("className").value(value.className());
            out.name("oldName").value(value.oldName());
            out.name("newName").value(value.newName());
            out.name("desc").value(value.desc());
            out.endObject();
        }

        @Override
        public RenameEntry read(JsonReader in) throws IOException {
            String type = "";
            String className = "";
            String oldName = "";
            String newName = "";
            String desc = "";
            in.beginObject();
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "type" -> type = nextString(in);
                    case "className" -> className = nextString(in);
                    case "oldName" -> oldName = nextString(in);
                    case "newName" -> newName = nextString(in);
                    case "desc" -> desc = nextString(in);
                    default -> in.skipValue();
                }
            }
            in.endObject();
            return new RenameEntry(type, className, oldName, newName, desc);
        }

        private String nextString(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return "";
            }
            return in.nextString();
        }
    }
}
