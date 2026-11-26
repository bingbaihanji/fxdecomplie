package com.bingbaihanji.fxdecomplie.mcp.tool;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方法定位,反编译源码切片与单方法字节码提取.
 *
 * @author bingbaihanji
 */
public final class JavaMethodExtractor {

    private static final int ASM_API = Opcodes.ASM9;

    private JavaMethodExtractor() {
        throw new AssertionError("utility class");
    }

    /**
     * 解析方法名或 JVM 方法描述符;重载且无法消歧时抛出 AMBIGUOUS_METHOD.
     */
    public static MethodMatch resolve(ClassFileMetadata metadata, String requested) {
        if (metadata == null) {
            throw new McpToolException("CLASS_NOT_FOUND", "Class metadata is unavailable", Map.of());
        }
        String query = requested == null ? "" : requested.strip();
        if (query.isEmpty()) {
            throw new McpToolException("INVALID_ARGUMENT", "method is required", Map.of());
        }
        if (query.startsWith("(")) {
            MethodMatch match = findByDescriptor(metadata, query);
            if (match != null) {
                return match;
            }
            throw methodNotFound(query);
        }
        int paren = query.indexOf('(');
        String name = paren < 0 ? query : query.substring(0, paren).strip();
        String suffix = paren < 0 ? "" : query.substring(paren);
        List<ClassFileMetadata.MemberInfo> candidates = metadata.methods().stream()
                .filter(member -> member.name().equals(name))
                .toList();
        if (candidates.isEmpty()) {
            throw methodNotFound(query);
        }
        if (paren >= 0 && suffix.startsWith("(") && suffix.contains(")")) {
            boolean jvmDescriptor = suffix.endsWith("V")
                    || suffix.matches(".*\\)[ZBCSIJFDV\\[L].*");
            if (jvmDescriptor) {
                for (ClassFileMetadata.MemberInfo member : candidates) {
                    if (member.descriptor().equals(suffix)) {
                        return new MethodMatch(member.name(), member.descriptor());
                    }
                }
            }
        }
        if (candidates.size() == 1) {
            ClassFileMetadata.MemberInfo member = candidates.get(0);
            return new MethodMatch(member.name(), member.descriptor());
        }
        throw ambiguous(name, candidates);
    }

    // 按 JVM 描述符精确查找方法
    private static MethodMatch findByDescriptor(ClassFileMetadata metadata, String descriptor) {
        for (ClassFileMetadata.MemberInfo member : metadata.methods()) {
            if (member.descriptor().equals(descriptor)) {
                return new MethodMatch(member.name(), member.descriptor());
            }
        }
        return null;
    }

    // 构造方法未找到异常
    private static McpToolException methodNotFound(String method) {
        return new McpToolException("METHOD_NOT_FOUND",
                "Method not found: " + method, Map.of("method", method));
    }

    // 构造方法重载歧义异常
    private static McpToolException ambiguous(String name,
                                              List<ClassFileMetadata.MemberInfo> candidates) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ClassFileMetadata.MemberInfo member : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", member.name());
            item.put("descriptor", member.descriptor());
            item.put("accessFlags", member.accessFlags());
            items.add(item);
        }
        return new McpToolException("AMBIGUOUS_METHOD",
                "Method name is ambiguous: " + name,
                Map.of("method", name, "candidates", items));
    }

    /** 从完整反编译源码中切出目标方法,返回绝对行号. */
    public static SourceSlice extractSource(String source, ClassFileMetadata metadata,
                                            MethodMatch match) {
        return extractSource(source, metadata, match, null);
    }

    /** 从完整反编译源码中切出目标方法,可传入重命名后的可见方法名. */
    public static SourceSlice extractSource(String source, ClassFileMetadata metadata,
                                            MethodMatch match, String visibleName) {
        if (source == null || source.isEmpty()) {
            throw new McpToolException("METHOD_NOT_FOUND",
                    "Source is empty for method: " + match.name(),
                    Map.of("method", match.name()));
        }
        String normalized = source.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = normalized.split("\n", -1);
        String targetName = visibleName != null && !visibleName.isBlank()
                ? visibleName : sourceMethodName(metadata, match);
        int expectedArgs = Type.getArgumentTypes(match.descriptor()).length;
        int declarationLine = findDeclarationLine(lines, targetName, expectedArgs,
                match.name().equals("<clinit>"));
        if (declarationLine < 0) {
            throw methodNotFound(match.name() + match.descriptor());
        }
        int startOffset = lineStartOffset(lines, declarationLine);
        int opaqueEnd = scanDeclarationEnd(normalized, startOffset);
        if (opaqueEnd < 0) {
            throw new McpToolException("METHOD_SOURCE_FAILED",
                    "Unable to find method body end: " + match.name(),
                    Map.of("method", match.name(), "descriptor", match.descriptor()));
        }
        int endLine = lineNumberAt(normalized, opaqueEnd);
        String content = String.join("\n",
                java.util.Arrays.copyOfRange(lines, declarationLine, endLine));
        return new SourceSlice(content, declarationLine + 1, endLine);
    }

    /** 只提取目标方法的 ASM 文本. */
    public static String extractBytecode(byte[] classBytes, MethodMatch match) {
        if (classBytes == null || classBytes.length == 0) {
            throw new McpToolException("METHOD_NOT_FOUND",
                    "Class bytes are unavailable", Map.of("method", match.name()));
        }
        try {
            Textifier textifier = new Textifier();
            ClassVisitor visitor = new ClassVisitor(ASM_API) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    if (name.equals(match.name()) && descriptor.equals(match.descriptor())) {
                        textifier.visitMethod(access, name, descriptor, signature, exceptions);
                        return new TraceMethodVisitor(textifier);
                    }
                    return null;
                }
            };
            new ClassReader(classBytes).accept(visitor, 0);
            StringWriter buffer = new StringWriter();
            textifier.print(new PrintWriter(buffer));
            String text = buffer.toString();
            if (text.isBlank()) {
                throw methodNotFound(match.name() + match.descriptor());
            }
            return text;
        } catch (McpToolException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new McpToolException("METHOD_BYTECODE_FAILED",
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(),
                    Map.of("method", match.name(), "descriptor", match.descriptor()));
        }
    }

    // 计算源码中可见的方法名
    private static String sourceMethodName(ClassFileMetadata metadata, MethodMatch match) {
        if ("<init>".equals(match.name())) {
            String internal = metadata.internalName();
            int slash = internal.lastIndexOf('/');
            return slash < 0 ? internal : internal.substring(slash + 1);
        }
        return match.name();
    }

    // 定位方法声明所在行
    private static int findDeclarationLine(String[] lines, String name, int expectedArgs,
                                           boolean staticInitializer) {
        Pattern pattern = Pattern.compile("(?<![\\p{javaJavaIdentifierPart}$])"
                + Pattern.quote(name) + "\\s*\\(");
        int fallback = -1;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String stripped = line.strip();
            if (stripped.startsWith("*") || stripped.startsWith("//")
                    || stripped.startsWith("@") || stripped.contains("new ")) {
                continue;
            }
            if (staticInitializer) {
                if (stripped.contains("static") && stripped.contains("{")) {
                    return i;
                }
                continue;
            }
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            int paren = line.indexOf('(', matcher.start());
            int count = sourceParameterCount(line, paren);
            if (count == expectedArgs) {
                return i;
            }
            if (fallback < 0) {
                fallback = i;
            }
        }
        return fallback;
    }

    // 统计源码方法参数个数
    private static int sourceParameterCount(String line, int openParen) {
        int close = findMatching(line, openParen, '(', ')');
        if (close < 0) {
            return -1;
        }
        String params = line.substring(openParen + 1, close).strip();
        if (params.isEmpty()) {
            return 0;
        }
        int count = 1;
        int generic = 0;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') {
                generic++;
            } else if (c == '>') {
                generic = Math.max(0, generic - 1);
            } else if (c == ',' && generic == 0) {
                count++;
            }
        }
        return count;
    }

    // 查找匹配的成对字符
    private static int findMatching(String line, int open, char openChar, char closeChar) {
        int depth = 0;
        for (int i = open; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    // 计算指定行的字符起始偏移
    private static int lineStartOffset(String[] lines, int lineIndex) {
        int offset = 0;
        for (int i = 0; i < lineIndex; i++) {
            offset += lines[i].length() + 1;
        }
        return offset;
    }

    // 计算字符偏移对应的行号
    private static int lineNumberAt(String source, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    /** 扫描方法声明后的分号或配对大括号,跳过字符串和注释. */
    // 扫描方法声明的分号或配对右括号
    private static int scanDeclarationEnd(String source, int start) {
        int depth = 0;
        boolean seenBrace = false;
        for (int i = start; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < source.length()) {
                char next = source.charAt(i + 1);
                if (next == '/') {
                    i = source.indexOf('\n', i + 2);
                    if (i < 0) {
                        return -1;
                    }
                    continue;
                }
                if (next == '*') {
                    i = source.indexOf("*/", i + 2);
                    if (i < 0) {
                        return -1;
                    }
                    i++;
                    continue;
                }
            }
            if (c == '"' || c == '\'') {
                i = skipQuoted(source, i, c);
                if (i < 0) {
                    return -1;
                }
                continue;
            }
            if (c == '{') {
                seenBrace = true;
                depth++;
            } else if (c == '}') {
                if (seenBrace) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            } else if (c == ';' && !seenBrace) {
                return i;
            }
        }
        return -1;
    }

    // 跳过字符串或字符字面量
    private static int skipQuoted(String source, int start, char quote) {
        for (int i = start + 1; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i;
            }
        }
        return -1;
    }

    /** 一次方法匹配结果. */
    public record MethodMatch(String name, String descriptor) {
    }

    /** 源码切片与绝对行号. */
    public record SourceSlice(String content, int startLine, int endLine) {
    }
}
