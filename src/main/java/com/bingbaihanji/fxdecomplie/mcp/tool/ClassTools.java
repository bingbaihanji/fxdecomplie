package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.CommentData;
import com.bingbaihanji.fxdecomplie.model.CommentScope;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.rename.RenameService;
import com.bingbaihanji.fxdecomplie.service.CommentManager;
import com.bingbaihanji.fxdecomplie.service.WorkspaceByteReader;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceRequest;
import com.bingbaihanji.fxdecomplie.service.decompile.ClassSourceResult;
import com.bingbaihanji.fxdecomplie.service.workspace.WorkspaceSession;
import com.bingbaihanji.fxdecomplie.util.io.ByteUtils;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 类读取相关 MCP 工具：列表、元数据、源码、字节码/十六进制、资源。
 *
 * @author bingbaihanji
 */
public final class ClassTools {

    private static final int MAX_SOURCE_CHARS = 200_000;
    private static final int MAX_BYTECODE_CHARS = 200_000;
    private static final int MAX_HEX_BYTES = 65_536;

    private ClassTools() {
        throw new AssertionError("utility class");
    }

    public static List<McpToolDefinition> definitions() {
        return List.of(listClasses(), classInfo(), classSource(), classBytecode(), resource());
    }

    private static McpToolDefinition listClasses() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "packagePrefix", Map.of("type", "string",
                        "description", "Package prefix, e.g. com/example or com.example"),
                "query", Map.of("type", "string",
                        "description", "Case-insensitive substring over class/display name"),
                "offset", Map.of("type", "integer", "minimum", 0),
                "limit", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "list_classes",
                "List classes in a workspace, filtered by package prefix or name substring.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    String prefix = McpArgs.optionalString(args, "packagePrefix", "")
                            .replace('\\', '/').replace('.', '/');
                    if (prefix.endsWith(".class")) {
                        prefix = prefix.substring(0, prefix.length() - 6);
                    }
                    String query = McpArgs.optionalString(args, "query", "")
                            .toLowerCase(Locale.ROOT);
                    int offset = McpArgs.intValue(args, "offset", 0, 0, Integer.MAX_VALUE - 1);
                    int limit = McpArgs.intValue(args, "limit", 100, 1, 500);
                    List<FileTreeNode> nodes = ClassResolver.classNodes(workspace);
                    List<FileTreeNode> filtered = new ArrayList<>();
                    String hash = CommentScope.workspaceHash(workspace);
                    String prefixBoundary = prefix.isBlank() || prefix.endsWith("/")
                            ? prefix : prefix + "/";
                    for (FileTreeNode node : nodes) {
                        String internal = ClassNameUtil.normalizeInternalName(node.getFullPath());
                        if (!prefix.isBlank()
                                && !internal.startsWith(prefixBoundary)
                                && !internal.equals(prefix)) {
                            continue;
                        }
                        if (!query.isBlank()) {
                            String simple = internal.substring(internal.lastIndexOf('/') + 1);
                            String display = RenameService.displayClassName(internal, hash);
                            if (!internal.toLowerCase(Locale.ROOT).contains(query)
                                    && !simple.toLowerCase(Locale.ROOT).contains(query)
                                    && !display.toLowerCase(Locale.ROOT).contains(query)) {
                                continue;
                            }
                        }
                        filtered.add(node);
                    }
                    filtered.sort(java.util.Comparator.comparing(
                            node -> ClassNameUtil.normalizeInternalName(node.getFullPath())));
                    int total = filtered.size();
                    int end = Math.min(total, offset + limit);
                    List<Map<String, Object>> items = offset >= total
                            ? List.of() : ClassResolver.toItems(filtered.subList(offset, end), workspace);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("items", items);
                    data.put("total", total);
                    data.put("offset", offset);
                    data.put("limit", limit);
                    data.put("hasMore", end < total);
                    data.put("nextOffset", end < total ? end : null);
                    data.put("indexReady", workspace.isIndexReady());
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition classInfo() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "class", Map.of("type", "string",
                        "description", "Class path, internal name, simple name or display name"),
                "includeMembers", Map.of("type", "boolean"),
                "includeDependencies", Map.of("type", "boolean"),
                "includeComments", Map.of("type", "boolean"),
                "includeAnnotations", Map.of("type", "boolean"),
                "maxItems", Map.of("type", "integer", "minimum", 1, "maximum", 500)));
        schema.put("required", List.of("class"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_class_info",
                "Return class metadata, superclass/interfaces, fields, methods, optional dependencies and comments.",
                schema,
                (context, args) -> {
                    ClassResolver.ResolvedClass resolved = ClassResolver.resolve(context, args);
                    byte[] bytes = readClassBytes(resolved);
                    ClassFileMetadata meta = ClassFileParser.tryParse(bytes)
                            .orElseThrow(() -> new McpToolException("CLASS_NOT_FOUND",
                                    "Unable to parse class: " + resolved.fullPath(), Map.of()));
                    int maxItems = McpArgs.intValue(args, "maxItems", 200, 1, 500);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("fullPath", resolved.fullPath());
                    data.put("internalName", meta.internalName());
                    data.put("displayName", resolved.displayName());
                    data.put("superName", meta.superName());
                    data.put("interfaces", meta.interfaces());
                    data.put("majorVersion", meta.majorVersion());
                    data.put("javaVersion", ClassFileParser.javaVersion(meta.majorVersion()));
                    data.put("accessFlags", meta.accessFlags());
                    data.put("constantPoolCount", meta.constantPoolCount());
                    if (McpArgs.booleanValue(args, "includeMembers", true)) {
                        data.put("fields", members(meta.fields(), maxItems));
                        data.put("methods", members(meta.methods(), maxItems));
                        data.put("fieldsTruncated", meta.fields().size() > maxItems);
                        data.put("methodsTruncated", meta.methods().size() > maxItems);
                    }
                    if (McpArgs.booleanValue(args, "includeDependencies", false)) {
                        data.put("dependencies", dependencies(resolved.workspace(),
                                meta.referencedClasses(), maxItems));
                    }
                    if (McpArgs.booleanValue(args, "includeComments", false)) {
                        data.put("comments", comments(resolved, maxItems));
                    }
                    if (McpArgs.booleanValue(args, "includeAnnotations", true)
                            && resolved.workspace().isIndexReady()) {
                        try {
                            var scan = new com.bingbaihanji.fxdecomplie.service.classscan
                                    .ClassGraphClassScanService().scan(
                                    new com.bingbaihanji.fxdecomplie.service.classscan
                                            .ClassScanRequest(
                                            resolved.workspace(),
                                            name -> name.equals(resolved.internalName()),
                                            com.bingbaihanji.fxdecomplie.service.classscan
                                                    .ClassScanOptions.CLASS_LEVEL_ONLY));
                            var metadata = scan.getClass(resolved.internalName());
                            if (metadata.isPresent()) {
                                data.put("annotations", metadata.get().annotations().stream()
                                        .map(annotation -> annotation.className())
                                        .toList());
                            }
                        } catch (RuntimeException ignored) {
                            // 注释解析失败不应让整个类信息查询失败
                        }
                    }
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition classSource() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "class", Map.of("type", "string"),
                "engine", Map.of("type", "string",
                        "description", "PROCYON, CFR, VINEFLOWER, JD, JADX or BDEC"),
                "options", Map.of("type", "object",
                        "description", "Optional engine option overrides"),
                "includeComments", Map.of("type", "boolean"),
                "startLine", Map.of("type", "integer", "minimum", 1),
                "lineCount", Map.of("type", "integer", "minimum", 1, "maximum", 1000),
                "maxChars", Map.of("type", "integer", "minimum", 1000, "maximum", MAX_SOURCE_CHARS)));
        schema.put("required", List.of("class"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_class_source",
                "Decompile a class and return a paginated source slice. Renames are already applied.",
                schema,
                (context, args) -> {
                    ClassResolver.ResolvedClass resolved = ClassResolver.resolve(context, args);
                    byte[] bytes = readClassBytes(resolved);
                    DecompilerTypeEnum engine = parseEngine(args,
                            context.config().decompiler().defaultEngine());
                    Map<String, String> options = McpArgs.stringMap(args, "options");
                    boolean includeComments = McpArgs.booleanValue(args, "includeComments", true);
                    int startLine = McpArgs.intValue(args, "startLine", 1, 1, Integer.MAX_VALUE);
                    int lineCount = McpArgs.intValue(args, "lineCount", 200, 1, 1000);
                    int maxChars = McpArgs.intValue(args, "maxChars", 100_000, 1000,
                            MAX_SOURCE_CHARS);
                    ClassSourceResult result = context.classSource().load(
                            new ClassSourceRequest(resolved.workspace(), resolved.fullPath(), bytes,
                                    engine, options, includeComments, 30, () -> true));
                    if (result.failure()) {
                        return McpToolResult.error("DECOMPILE_FAILED",
                                "Decompiler returned failure output",
                                Map.of("engine", result.engine().name(),
                                        "output", truncate(result.sourceCode(), 20_000)));
                    }
                    Map<String, Object> slice = sourceSlice(result.sourceCode(), startLine,
                            lineCount, maxChars);
                    Map<String, Object> data = new LinkedHashMap<>(slice);
                    data.put("fullPath", resolved.fullPath());
                    data.put("internalName", resolved.internalName());
                    data.put("displayName", result.displayName());
                    data.put("engine", result.engine().name());
                    data.put("cacheHit", result.cacheHit());
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition classBytecode() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "class", Map.of("type", "string"),
                "format", Map.of("type", "string", "enum", List.of("asm", "hex")),
                "offset", Map.of("type", "integer", "minimum", 0),
                "length", Map.of("type", "integer", "minimum", 1, "maximum", MAX_HEX_BYTES),
                "startLine", Map.of("type", "integer", "minimum", 1),
                "lineCount", Map.of("type", "integer", "minimum", 1, "maximum", 1000),
                "maxChars", Map.of("type", "integer", "minimum", 1000,
                        "maximum", MAX_BYTECODE_CHARS)));
        schema.put("required", List.of("class"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_class_bytecode",
                "Return ASM text or a hex dump for a class.",
                schema,
                (context, args) -> {
                    ClassResolver.ResolvedClass resolved = ClassResolver.resolve(context, args);
                    byte[] bytes = readClassBytes(resolved);
                    String format = McpArgs.optionalString(args, "format", "asm")
                            .toLowerCase(Locale.ROOT);
                    if (!format.equals("asm") && !format.equals("hex")) {
                        throw new McpToolException("INVALID_ARGUMENT",
                                "format must be asm or hex", Map.of("format", format));
                    }
                    if ("hex".equals(format)) {
                        int offset = McpArgs.intValue(args, "offset", 0, 0,
                                Math.max(0, bytes.length));
                        int length = McpArgs.intValue(args, "length", 8192, 1, MAX_HEX_BYTES);
                        int end = Math.min(bytes.length, offset + length);
                        String dump = hexDump(bytes, offset, end);
                        Map<String, Object> data = new LinkedHashMap<>();
                        data.put("fullPath", resolved.fullPath());
                        data.put("format", "hex");
                        data.put("totalBytes", bytes.length);
                        data.put("offset", offset);
                        data.put("length", end - offset);
                        data.put("hasMore", end < bytes.length);
                        data.put("nextOffset", end < bytes.length ? end : null);
                        data.put("content", dump);
                        return McpToolResult.ok(data);
                    }
                    int startLine = McpArgs.intValue(args, "startLine", 1, 1, Integer.MAX_VALUE);
                    int lineCount = McpArgs.intValue(args, "lineCount", 200, 1, 1000);
                    int maxChars = McpArgs.intValue(args, "maxChars", 100_000, 1000,
                            MAX_BYTECODE_CHARS);
                    ClassIndexEntry entry = new ClassIndexEntry(resolved.fullPath(),
                            resolved.internalName(),
                            resolved.internalName().substring(
                                    resolved.internalName().lastIndexOf('/') + 1),
                            bytes, List.of(), List.of());
                    Map<String, Object> data = new LinkedHashMap<>(
                            sourceSlice(entry.bytecodeText(), startLine, lineCount, maxChars));
                    data.put("fullPath", resolved.fullPath());
                    data.put("format", "asm");
                    return McpToolResult.ok(data);
                });
    }

    private static McpToolDefinition resource() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "workspaceId", Map.of("type", "string"),
                "path", Map.of("type", "string",
                        "description", "Resource path inside the workspace"),
                "maxChars", Map.of("type", "integer", "minimum", 1000,
                        "maximum", MAX_SOURCE_CHARS),
                "includeBinary", Map.of("type", "boolean")));
        schema.put("required", List.of("path"));
        schema.put("additionalProperties", false);
        return new McpToolDefinition(
                "get_resource",
                "Read a text resource, or return metadata/base64 for a binary resource.",
                schema,
                (context, args) -> {
                    WorkspaceSession session = ClassResolver.session(context, args);
                    Workspace workspace = session.workspace();
                    String path = McpArgs.requireString(args, "path");
                    FileTreeNode node = workspace.findNodeByPath(path);
                    if (node == null) {
                        throw new McpToolException("RESOURCE_NOT_FOUND",
                                "Resource not found: " + path, Map.of("path", path));
                    }
                    byte[] bytes = readNodeBytes(workspace, node);
                    int maxChars = McpArgs.intValue(args, "maxChars", 100_000, 1000,
                            MAX_SOURCE_CHARS);
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("path", node.getFullPath());
                    data.put("size", bytes.length);
                    data.put("textFile", node.isTextFile());
                    if (node.isTextFile()) {
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        boolean truncated = text.length() > maxChars;
                        if (truncated) {
                            text = text.substring(0, maxChars);
                        }
                        data.put("content", text);
                        data.put("truncated", truncated);
                        data.put("totalChars", bytes.length);
                    } else if (McpArgs.booleanValue(args, "includeBinary", false)
                            && bytes.length <= MAX_HEX_BYTES) {
                        data.put("base64", Base64.getEncoder().encodeToString(bytes));
                    }
                    return McpToolResult.ok(data);
                });
    }

    private static byte[] readClassBytes(ClassResolver.ResolvedClass resolved) {
        byte[] bytes = readNodeBytes(resolved.workspace(), resolved.node());
        if (bytes == null || bytes.length == 0) {
            throw new McpToolException("CLASS_NOT_FOUND",
                    "Unable to read class bytes: " + resolved.fullPath(), Map.of());
        }
        return bytes;
    }

    private static byte[] readNodeBytes(Workspace workspace, FileTreeNode node) {
        try {
            byte[] bytes = WorkspaceByteReader.readNodeBytes(workspace, node, true);
            return bytes == null ? new byte[0] : bytes;
        } catch (IOException e) {
            throw new McpToolException("RESOURCE_NOT_FOUND",
                    "Unable to read resource: " + node.getFullPath(), Map.of());
        }
    }

    private static DecompilerTypeEnum parseEngine(Map<String, Object> args,
                                                   DecompilerTypeEnum fallback) {
        String value = McpArgs.optionalString(args, "engine", "");
        if (value.isBlank()) {
            return fallback == null ? DecompilerTypeEnum.JADX : fallback;
        }
        try {
            return DecompilerTypeEnum.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new McpToolException("INVALID_ARGUMENT",
                    "Unknown engine: " + value, Map.of("engine", value));
        }
    }

    private static List<Map<String, Object>> members(List<ClassFileMetadata.MemberInfo> members,
                                                     int maxItems) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassFileMetadata.MemberInfo member : members.subList(0, Math.min(maxItems,
                members.size()))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", member.name());
            item.put("descriptor", member.descriptor());
            item.put("accessFlags", member.accessFlags());
            result.add(item);
        }
        return result;
    }

    private static List<Map<String, Object>> dependencies(Workspace workspace,
                                                         List<String> referenced, int maxItems) {
        List<Map<String, Object>> result = new ArrayList<>();
        WorkspaceIndex index = workspace.getIndex();
        for (String internalName : referenced.subList(0, Math.min(maxItems, referenced.size()))) {
            boolean present = workspace.findNodeByPath(internalName + ".class") != null
                    || (index != WorkspaceIndex.EMPTY && index.findClass(internalName) != null);
            result.add(Map.of("internalName", internalName, "inWorkspace", present));
        }
        return result;
    }

    private static List<Map<String, Object>> comments(ClassResolver.ResolvedClass resolved,
                                                      int maxItems) {
        String hash = CommentScope.workspaceHash(resolved.workspace());
        List<CommentData> comments = CommentManager.load(hash, resolved.fullPath());
        List<Map<String, Object>> result = new ArrayList<>();
        for (CommentData comment : comments.subList(0, Math.min(maxItems, comments.size()))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("line", comment.line());
            item.put("memberSignature", comment.memberSignature());
            item.put("style", comment.style().name());
            item.put("text", comment.text());
            item.put("author", comment.author());
            item.put("time", comment.time());
            result.add(item);
        }
        return result;
    }

    private static Map<String, Object> sourceSlice(String source, int startLine,
                                                   int lineCount, int maxChars) {
        if (source == null) {
            source = "";
        }
        String[] lines = source.split("\n", -1);
        int total = lines.length;
        int start = Math.min(Math.max(1, startLine), Math.max(1, total));
        int end = Math.min(total, start + lineCount - 1);
        String slice = String.join("\n", java.util.Arrays.copyOfRange(lines, start - 1, end));
        boolean charsTruncated = slice.length() > maxChars;
        if (charsTruncated) {
            slice = slice.substring(0, maxChars);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("content", slice);
        data.put("startLine", start);
        data.put("endLine", end);
        data.put("totalLines", total);
        data.put("truncated", charsTruncated);
        data.put("hasMore", end < total || charsTruncated);
        data.put("nextStartLine", end < total ? end + 1 : null);
        return data;
    }

    private static String hexDump(byte[] bytes, int start, int end) {
        StringBuilder builder = new StringBuilder();
        for (int offset = start; offset < end; offset += 16) {
            builder.append(String.format("%08x  ", offset));
            for (int i = 0; i < 16; i++) {
                if (offset + i < end) {
                    builder.append(String.format("%02x ", bytes[offset + i]));
                } else {
                    builder.append("   ");
                }
                if (i == 7) {
                    builder.append(' ');
                }
            }
            builder.append(" |");
            for (int i = 0; i < 16 && offset + i < end; i++) {
                int value = bytes[offset + i] & 0xFF;
                builder.append(value >= 0x20 && value <= 0x7E ? (char) value : '.');
            }
            builder.append("|\n");
        }
        return builder.toString();
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n...[truncated]";
    }
}
