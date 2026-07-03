package com.bingbaihanji.fxdecomplie.rename;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

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

    private static final Logger logger = LoggerFactory.getLogger(RenameService.class);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<RenameEntry>>() {}.getType();
    private static final Pattern JAVA_ID = Pattern.compile("[a-zA-Z_$][a-zA-Z0-9_$]*");
    private static final Set<String> RESERVED_NAMES = Set.of(
            "_", "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double", "else",
            "enum", "extends", "false", "final", "finally", "float", "for", "goto",
            "if", "implements", "import", "instanceof", "int", "interface", "long",
            "native", "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try", "void", "volatile",
            "while", "var", "record", "sealed", "permits", "yield", "module", "requires",
            "exports", "opens", "to", "uses", "provides", "with");
    private static final Set<String> METHOD_HEADER_KEYWORDS = Set.of(
            "if", "for", "while", "switch", "catch", "try", "return", "throw", "new",
            "synchronized", "do", "else", "assert");
    private static final Set<String> CONSTRUCTOR_PREFIX_WORDS = Set.of(
            "public", "protected", "private", "strictfp");
    private static final ConcurrentMap<String, List<RenameEntry>> MEMORY_CACHE = new ConcurrentHashMap<>();
    private static volatile Path rootDir;

    private RenameService() {
        throw new AssertionError("utility class");
    }

    public static void setRootDir(Path dir) {
        rootDir = dir;
    }

    private static Path getRootDir() {
        return rootDir != null ? rootDir : AppConfig.appDir().resolve("fxdecomplie").resolve("renames");
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
        RenameEntry normalized = normalize(entry);
        if (normalized.oldName().equals(normalized.newName())) {
            delete(workspaceHash, normalized);
            return true;
        }
        List<RenameEntry> list = loadAll(workspaceHash);
        // 替换同一符号的旧条目。方法和参数保留 descriptor，避免重载冲突。
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            RenameEntry e = normalize(list.get(i));
            if (sameKey(e, normalized)) {
                list.set(i, normalized);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            list.add(normalized);
        }
        MEMORY_CACHE.put(workspaceHash, List.copyOf(list));
        return writeFile(workspaceHash, list);
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
            MEMORY_CACHE.put(workspaceHash, List.copyOf(result));
            return result;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            logger.debug("读取重命名文件失败: {}", file, e);
            List<RenameEntry> cached = MEMORY_CACHE.get(workspaceHash);
            return cached == null ? new ArrayList<>() : new ArrayList<>(cached);
        }
    }

    /** 根据旧名称查找重命名 */
    public static String findNewName(String workspaceHash, String className, String oldName) {
        String normalizedClass = normalizeInternalName(className);
        for (RenameEntry e : loadAll(workspaceHash)) {
            if (e.className().equals(normalizedClass) && e.oldName().equals(oldName)) {
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
        RenameEntry normalized = normalize(entry);
        List<RenameEntry> list = loadAll(workspaceHash);
        list.removeIf(e -> sameKey(normalize(e), normalized));
        MEMORY_CACHE.put(workspaceHash, List.copyOf(list));
        writeFile(workspaceHash, list);
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
        return applyEntries(sourceCode, loadAll(wsHash), normalizeInternalName(className));
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
        String result = internalName;
        for (RenameEntry entry : loadAll(workspaceHash)) {
            if (classEntryTargetsInternalName(entry, result)) {
                result = renamedClassInternalName(result, entry.oldName(), entry.newName());
            }
        }
        return result;
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
        return applyEntries(sourceCode, List.of(normalize(entry)), normalizeInternalName(className));
    }

    /** 对当前已显示的别名源码做一次直接替换，并使用已保存映射辅助方法作用域匹配。 */
    public static String applySingleRename(String sourceCode, RenameEntry entry,
                                           String className, String workspaceHash) {
        if (sourceCode == null || entry == null) {
            return sourceCode;
        }
        List<RenameEntry> entries = new ArrayList<>(loadAll(workspaceHash));
        entries.add(normalize(entry));
        return applyEntries(sourceCode, entries, normalizeInternalName(className));
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
                if (TYPE_METHOD.equals(entry.type()) && entry.oldName().equals(oldName)) {
                    return entry.newName();
                }
            }
        } else {
            for (RenameEntry entry : entries) {
                if (TYPE_FIELD.equals(entry.type()) && entry.oldName().equals(oldName)) {
                    return entry.newName();
                }
            }
        }

        String classReplacement = firstClassReplacement(entries, oldName);
        if (classReplacement != null) {
            return classReplacement;
        }

        for (RenameEntry entry : entries) {
            if (TYPE_IDENTIFIER.equals(entry.type()) && entry.oldName().equals(oldName)
                    && appliesToClass(entry, currentClass)) {
                return entry.newName();
            }
        }
        for (RenameEntry entry : entries) {
            if (entry.oldName().equals(oldName)
                    && (TYPE_CLASS.equals(entry.type()) || TYPE_METHOD.equals(entry.type())
                    || TYPE_FIELD.equals(entry.type()) || TYPE_PARAM.equals(entry.type())
                    || TYPE_IDENTIFIER.equals(entry.type()))) {
                return entry.newName();
            }
        }
        return null;
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
        if (!entryClass.isBlank() && entryClass.equals(normalized)) {
            return true;
        }
        return classEntryMatchesToken(entry, simpleClassName(normalized));
    }

    private static boolean classEntryMatchesToken(RenameEntry entry, String token) {
        if (entry.oldName().equals(token)) {
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
                || entry.className().equals(currentClass);
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
            if (entry.className().equals(currentClass)) {
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
                if (match.owner().equals(currentClass)) {
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
                if (match.owner().equals(currentClass)) {
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
            scopes.add(new MethodScope(nameToken.name(), lineStart, bodyEnd + 1, params));
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
            java.util.regex.Matcher matcher = Pattern
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
        java.util.regex.Matcher matcher = Pattern
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

    private static boolean sameKey(RenameEntry left, RenameEntry right) {
        return left.type().equals(right.type())
                && left.className().equals(right.className())
                && left.oldName().equals(right.oldName())
                && left.desc().equals(right.desc());
    }

    private static String normalizeInternalName(String className) {
        if (className == null) {
            return "";
        }
        String normalized = className.replace('\\', '/').strip();
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        }
        if (normalized.endsWith(".java")) {
            normalized = normalized.substring(0, normalized.length() - ".java".length());
        }
        if (!normalized.contains("/")) {
            normalized = normalized.replace('.', '/');
        }
        return normalized;
    }

    private static String simpleClassName(String internalName) {
        if (internalName == null || internalName.isBlank()) {
            return "";
        }
        String normalized = normalizeInternalName(internalName);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String renamedClassInternalName(String internalName, String oldName, String newName) {
        int slash = internalName.lastIndexOf('/');
        String pkg = slash >= 0 ? internalName.substring(0, slash + 1) : "";
        String simple = slash >= 0 ? internalName.substring(slash + 1) : internalName;
        String renamedSimple;
        if (simple.equals(oldName)) {
            renamedSimple = newName;
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
        try {
            Path file = resolveFile(workspaceHash);
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(list), StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            logger.error("保存重命名失败", e);
            return false;
        }
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

    private record MethodScope(String name, int start, int end, Set<String> params) {
        private MethodScope {
            params = Collections.unmodifiableSet(new HashSet<>(params == null ? Set.of() : params));
        }

        private boolean contains(int offset) {
            return offset >= start && offset < end;
        }

        private int paramCount() {
            return params.size();
        }
    }
}
