package com.bingbaihanji.fxdecomplie.ui.outline;

import com.bingbaihanji.fxdecomplie.bytecode.BytecodeSignatureParser;
import com.bingbaihanji.fxdecomplie.model.CodeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从反编译 Java 源码中提取字段 方法 内部类的大纲信息使用正则逐行匹配
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlineParser {

    private static final Logger log = LoggerFactory.getLogger(OutlineParser.class);

    /**
     * 方法签名匹配模式
     * 使用简单括号计数扫描参数列表,避免正则嵌套量词导致灾难性回溯或
     * 深层嵌套泛型(如 {@code Map<String, Map<String, List<Integer>>>})匹配失败
     * 匹配策略：定位开括号后逐字符扫描,跳过字符串/注释,括号计数归零时结束
     */
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|synchronized|abstract|native|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*\\(");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|final|volatile|transient|\\s)*)"
                    + "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*(?:=\\s*[^;]+)?;");

    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
            "^\\s*((?:public|protected|private|static|\\s)*)\\b(class|interface|enum|record)\\s+(\\w+)");

    /** 匹配全限定类引用：包路径(小写段)+ 类名/内部类(大小写均可,混淆后类名常为小写单字母) */
    private static final Pattern CLASS_REF_PATTERN = Pattern.compile(
            "\\b([a-z][a-z0-9_]*(?:\\.[a-z][a-z0-9_]*)+\\.[A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\b");

    /** 匹配接口声明的模式 */
    private static final Pattern INTERFACE_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+)?interface\\s+\\w+");

    /** 匹配抽象类的模式 */
    private static final Pattern ABSTRACT_CLASS_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+)?(?:abstract\\s+)\\s*class\\s+\\w+");

    private OutlineParser() {
        throw new AssertionError("utility class");
    }

    /**
     * 检测源码是否为接口声明
     *
     * @param sourceCode 反编译后的 Java 源码
     * @return 如果源码声明了 interface(非 @interface)则返回 true
     */
    public static boolean isInterface(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("*") || trimmed.startsWith("@")) {
                continue;
            }
            return INTERFACE_PATTERN.matcher(line).find()
                    && !trimmed.contains("@interface");
        }
        return false;
    }

    /**
     * 检测源码是否为抽象类
     *
     * @param sourceCode 反编译后的 Java 源码
     * @return 如果源码声明了 abstract class 则返回 true
     */
    public static boolean isAbstractClass(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return false;
        }
        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*")
                    || trimmed.startsWith("*") || trimmed.startsWith("@")) {
                continue;
            }
            return ABSTRACT_CLASS_PATTERN.matcher(line).find();
        }
        return false;
    }

    /**
     * 从源码中提取方法签名列表,用于与字节码中的方法描述符匹配
     *
     * <p>返回的方法签名包含方法名和参数个数,用于在实现类字节码中进行模糊匹配</p>
     *
     * @param sourceCode 反编译后的 Java 源码
     * @return 方法签名列表,每项为 {方法名, 参数个数}
     */
    public static List<MethodSignature> extractMethodSignatures(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return List.of();
        }
        List<MethodSignature> signatures = new ArrayList<>();
        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int depth = 0;

        for (String line : lines) {
            int prevDepth = depth;
            depth += count(line, '{') - count(line, '}');

            if (prevDepth == 1 && !line.contains(" class ") && !line.contains(" interface ")
                    && !line.contains(" enum ") && !line.contains(" record ")) {
                Matcher m = METHOD_PATTERN.matcher(line);
                if (m.find()) {
                    int openParen = m.end() - 1;
                    if (openParen >= 0 && hasMatchingCloseParen(line, openParen)) {
                        String methodName = m.group(2);
                        int paramCount = countParameters(line, openParen);
                        signatures.add(new MethodSignature(methodName, paramCount));
                    }
                }
            }
        }
        return signatures;
    }

    /**
     * 统计方法参数个数(通过逗号分隔)
     *
     * @param line      包含方法声明的行
     * @param openParen 开括号位置
     * @return 参数个数
     */
    private static int countParameters(String line, int openParen) {
        int closeParen = findMatchingCloseParenIndex(line, openParen);
        if (closeParen < 0 || closeParen <= openParen + 1) {
            return 0;
        }
        String params = line.substring(openParen + 1, closeParen).strip();
        if (params.isEmpty()) {
            return 0;
        }
        // 简单逗号计数(不处理嵌套泛型中的逗号,仅用于近似匹配)
        int depth = 0, count = 1;
        for (int i = 0; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '<') {
                depth++;
            } else if (c == '>') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }
        return count;
    }

    /** 查找与指定开括号匹配的闭括号索引 */
    private static int findMatchingCloseParenIndex(String line, int openParen) {
        int depth = 0;
        for (int j = openParen; j < line.length(); j++) {
            char c = line.charAt(j);
            if (c == '"' || c == '\'') {
                j = skipQuoted(line, j, c) - 1;
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return j;
                }
            }
        }
        return -1;
    }

    /**
     * 从反编译 Java 源码提取大纲成员(字段 方法 内部类)
     *
     * <p>逐行扫描源码,用大括号计数跟踪嵌套深度方法匹配采用两步策略：
     * 先用 {@link #METHOD_PATTERN} 匹配方法名前缀,再从开括号位置逐字符扫描
     * 找到匹配的闭括号(跳过字符串和注释),避免正则嵌套量词的深度限制和
     * 灾难性回溯问题</p>
     *
     * @param sourceCode 反编译后的 Java 源码
     * @return 大纲成员列表,按源码行号排序
     */
    public static List<OutlineMember> parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) {
            return List.of();
        }
        List<OutlineMember> members = new ArrayList<>();
        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        int depth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 先用上一轮的 depth 做成员检查(避免当前行自身的 { 干扰判断),
            // 再统计本行大括号更新 depth 供后续行使用
            int prevDepth = depth;
            depth += count(line, '{') - count(line, '}');

            Matcher m;
            if (prevDepth == 1 && !line.contains(" class ") && !line.contains(" interface ")
                    && !line.contains(" enum ") && !line.contains(" record ")) {
                if ((m = METHOD_PATTERN.matcher(line)).find()) {
                    // 从开括号位置扫描匹配闭括号,处理深层嵌套泛型参数
                    int openParen = m.end() - 1; // pattern 以 "\(" 结尾,开括号在最后一字符
                    if (openParen >= 0 && hasMatchingCloseParen(line, openParen)) {
                        members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.METHOD,
                                extractModifiers(m.group(1)), i + 1));
                    }
                } else if ((m = FIELD_PATTERN.matcher(line)).find() && !line.contains("(")) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.FIELD,
                            extractModifiers(m.group(1)), i + 1));
                }
            }
            if ((m = INNER_CLASS_PATTERN.matcher(line)).find()
                    && !line.contains("new ") && prevDepth >= 1 && prevDepth <= 2) {
                members.add(new OutlineMember(m.group(3), OutlineMember.MemberType.INNER_CLASS,
                        extractModifiers(m.group(1)), i + 1));
            }
        }
        return members;
    }

    /**
     * 从指定位置的开括号开始,逐字符扫描找到匹配的闭括号
     * 跳过字符串字面量和注释,支持任意深度的泛型嵌套
     *
     * @param line      当前行文本
     * @param openParen 开括号 '(' 的索引
     * @return 找到匹配闭括号且后跟 '{' 或 ';' 时返回 true
     */
    private static boolean hasMatchingCloseParen(String line, int openParen) {
        int depth = 0;
        for (int j = openParen; j < line.length(); j++) {
            char c = line.charAt(j);
            // 跳过字符串字面量
            if (c == '"' || c == '\'') {
                j = skipQuoted(line, j, c) - 1;
                continue;
            }
            // 跳过单行注释
            if (c == '/' && j + 1 < line.length() && line.charAt(j + 1) == '/') {
                return false; // 注释到行尾,不可能有闭括号和 {/;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    // 闭括号后应跟 { 或 ; 或 throws
                    int after = j + 1;
                    while (after < line.length() && Character.isWhitespace(line.charAt(after))) {
                        after++;
                    }
                    if (after < line.length()) {
                        char next = line.charAt(after);
                        if (next == '{' || next == ';') {
                            return true;
                        }
                        // 检查 throws 子句
                        if (after + 5 < line.length()
                                && "throws".equals(line.substring(after, after + 6))) {
                            // 继续扫描到 { 或 ;
                            for (int k = after + 6; k < line.length(); k++) {
                                if (line.charAt(k) == '{' || line.charAt(k) == ';') {
                                    return true;
                                }
                            }
                            return false;
                        }
                    }
                    return false;
                }
            }
        }
        return false;
    }

    /** 跳过字符串/字符字面量,处理转义字符 */
    private static int skipQuoted(String s, int start, char quote) {
        int i = start + 1;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            i++;
            if (c == quote) {
                break;
            }
        }
        return Math.min(i, s.length());
    }

    /**
     * 统计字符在行中的出现次数,跳过字符串字面量和注释
     * 避免 {@code "hello { world }"} 或 {@code // { } } 中的括号被误计
     */
    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            // 跳过字符串/字符字面量
            if (ch == '"' || ch == '\'') {
                i = skipQuoted(s, i, ch) - 1;
                continue;
            }
            // 跳过单行注释
            if (ch == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                break; // 注释到行尾,本行后续不再有代码级括号
            }
            if (ch == c) {
                n++;
            }
        }
        return n;
    }

    /**
     * 从反编译源码中提取代码元数据,用于 Ctrl+Click 导航
     * 逐行扫描类引用 方法调用和字段访问
     */
    public static CodeMetadata extractMetadata(String sourceCode) {
        Map<Integer, List<CodeMetadata.Reference>> refsByLine = new HashMap<>();
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new CodeMetadata(refsByLine);
        }

        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            List<CodeMetadata.Reference> refs = new ArrayList<>();

            // 跳过注释行(跟踪多行块注释状态)
            String trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed.startsWith("* ")) {
                continue;
            }

            Matcher m = CLASS_REF_PATTERN.matcher(line);
            while (m.find()) {
                String match = m.group(1);
                // 包含 '.' 且最后一段是有效 Java 标识符则视为类引用
                // 混淆后的类名通常为小写单字母(如 a, b, c),不能再依赖首字母大写判断
                if (match.contains(".") && looksLikeClassReference(match)) {
                    refs.add(new CodeMetadata.Reference(
                            CodeMetadata.RefType.CLASS_REF, match, null, lineNum, m.start()));
                }
            }

            if (!refs.isEmpty()) {
                refsByLine.put(lineNum, refs);
            }
        }

        return new CodeMetadata(refsByLine);
    }

    /**
     * 从反编译源码中提取代码元数据(带字节码增强)
     *
     * <p>在正则提取的基础上,额外解析 class 字节码的泛型签名,将反编译源码中
     * 泛型类型参数的简单名(如 {@code ServiceImpl<AjglMapper, a>} 中的 {@code a})
     * 映射到字节码中的全限定名(如 {@code com.pig4cloud.domain.a}) </p>
     *
     * <p>此方法解决了混淆场景下多个同名类的导航歧义问题：
     * 字节码的 Signature 属性存储了无歧义的全限定类型引用,将其与反编译源码
     * 中的泛型位置关联后,Ctrl+Click 可以精确跳转到正确的类 </p>
     *
     * @param sourceCode 反编译后的 Java 源码
     * @param classBytes 类文件原始字节码(可为 null,此时退化为纯正则提取)
     * @return 代码元数据(含字节码增强的泛型类引用)
     */
    public static CodeMetadata extractMetadata(String sourceCode, byte[] classBytes) {
        Map<Integer, List<CodeMetadata.Reference>> refsByLine = new HashMap<>();
        if (sourceCode == null || sourceCode.isEmpty()) {
            return new CodeMetadata(refsByLine);
        }

        String[] lines = sourceCode.replace("\r\n", "\n").replace("\r", "\n").split("\n");

        // ---- 第一阶段：正则提取全限定类引用(原有逻辑) ----
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            List<CodeMetadata.Reference> refs = new ArrayList<>();

            String trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed.startsWith("* ")) {
                continue;
            }

            Matcher m = CLASS_REF_PATTERN.matcher(line);
            while (m.find()) {
                String match = m.group(1);
                if (match.contains(".") && looksLikeClassReference(match)) {
                    refs.add(new CodeMetadata.Reference(
                            CodeMetadata.RefType.CLASS_REF, match, null, lineNum));
                }
            }

            if (!refs.isEmpty()) {
                refsByLine.put(lineNum, refs);
            }
        }

        // ---- 第二阶段：字节码泛型签名增强 ----
        if (classBytes != null && classBytes.length > 10) {
            try {
                enhanceWithBytecodeSignatures(lines, classBytes, refsByLine);
            } catch (Exception e) {
                log.debug("字节码签名增强失败,保留纯正则结果", e);
            }
        }

        return new CodeMetadata(refsByLine);
    }

    /**
     * 从字节码泛型签名中提取类型参数,与反编译源码的泛型位置关联
     *
     * <p>扫描源码中 extends/implements 子句和泛型参数中的简单类名,
     * 用字节码签名中的对应位置的全限定名创建精确的导航引用 </p>
     */
    private static void enhanceWithBytecodeSignatures(String[] lines, byte[] classBytes,
                                                      Map<Integer, List<CodeMetadata.Reference>> refsByLine) {
        List<List<String>> typeArgsByOwner = BytecodeSignatureParser
                .extractTypeArgumentsByOwner(classBytes);
        if (typeArgsByOwner.isEmpty()) {
            return;
        }

        // 按 extends/implements 子句匹配源码中的类型参数
        // 关键：同一行可能同时有 extends 和 implements,需要逐个关键字处理
        int ownerIndex = 0;
        boolean inBlockComment = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNum = i + 1;
            String trimmed = line.trim();

            if (inBlockComment) {
                if (trimmed.contains("*/")) {
                    inBlockComment = false;
                }
                continue;
            }
            if (trimmed.startsWith("//")) {
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) {
                    inBlockComment = true;
                }
                continue;
            }
            if (trimmed.startsWith("* ")) {
                continue;
            }

            // 按关键字位置从左到右处理 extends 和 implements
            // 同一行可能两者都有(如 extends X<A> implements Y<B>)
            int searchFrom = 0;
            while (searchFrom < line.length() && ownerIndex < typeArgsByOwner.size()) {
                // 查找下一个 extends 或 implements 关键字
                int extendsIdx = line.indexOf("extends ", searchFrom);
                int implementsIdx = line.indexOf("implements ", searchFrom);

                // 选择最近的关键字
                int keywordIdx;
                boolean isExtends;
                if (extendsIdx >= 0 && (implementsIdx < 0 || extendsIdx < implementsIdx)) {
                    keywordIdx = extendsIdx;
                    isExtends = true;
                } else if (implementsIdx >= 0) {
                    keywordIdx = implementsIdx;
                    isExtends = false;
                } else {
                    break; // 本行无更多关键字
                }

                // 跳过 "extends {" 这种内部类/匿名类模式
                if (isExtends) {
                    String afterExtends = line.substring(keywordIdx + 8).stripLeading();
                    if (afterExtends.startsWith("{")) {
                        searchFrom = keywordIdx + 8;
                        continue;
                    }
                }

                // 确定该关键字对应的 ownerIndex
                // extends 总是对应 ownerIndex 0(父类)
                // implements 对应 ownerIndex > 0(接口)
                int currentOwnerIndex;
                if (isExtends) {
                    if (ownerIndex != 0) {
                        // 已经处理过 extends,跳过
                        searchFrom = keywordIdx + 8;
                        continue;
                    }
                    currentOwnerIndex = ownerIndex;
                    ownerIndex++;
                } else {
                    // implements
                    if (ownerIndex == 0) {
                        // 还没处理 extends,先跳过(不应发生)
                        searchFrom = keywordIdx + 11;
                        continue;
                    }
                    currentOwnerIndex = ownerIndex;
                    ownerIndex++;
                }
                // 提取该关键字后的泛型参数(从关键字位置开始扫描尖括号)
                String fromKeyword = line.substring(keywordIdx);
                List<TypeArgWithPosition> sourceTypeArgs = extractGenericTypeArgumentsWithPosition(fromKeyword);
                // 修正列号偏移(相对于整行而非关键字后子串)
                List<TypeArgWithPosition> adjustedArgs = new ArrayList<>();
                for (TypeArgWithPosition arg : sourceTypeArgs) {
                    adjustedArgs.add(new TypeArgWithPosition(arg.name(), arg.columnStart() + keywordIdx));
                }

                if (adjustedArgs.isEmpty() || currentOwnerIndex >= typeArgsByOwner.size()) {
                    searchFrom = keywordIdx + (isExtends ? 8 : 11);
                    continue;
                }

                List<String> bytecodeArgs = typeArgsByOwner.get(currentOwnerIndex);
                if (bytecodeArgs.isEmpty()) {
                    searchFrom = keywordIdx + (isExtends ? 8 : 11);
                    continue;
                }

                // 按位置匹配字节码类型参数与源码类型参数
                List<CodeMetadata.Reference> refs = refsByLine.computeIfAbsent(
                        lineNum, k -> new ArrayList<>());

                for (int j = 0; j < adjustedArgs.size() && j < bytecodeArgs.size(); j++) {
                    String sourceArg = adjustedArgs.get(j).name;
                    int argColumn = adjustedArgs.get(j).columnStart;
                    String bytecodeArg = bytecodeArgs.get(j);

                    // 仅处理简单名(非全限定名),且字节码提供了有效的全限定名
                    if (!sourceArg.contains(".") && bytecodeArg != null
                            && !bytecodeArg.isBlank() && bytecodeArg.contains(".")) {
                        String dotName = bytecodeArg;

                        // 避免重复添加(如果正则已提取了相同的全限定名)
                        boolean alreadyExists = refs.stream().anyMatch(r ->
                                r.targetClass() != null && r.targetClass().equals(dotName));
                        if (!alreadyExists) {
                            refs.add(new CodeMetadata.Reference(
                                    CodeMetadata.RefType.CLASS_REF, dotName, null, lineNum, argColumn));
                        }
                    }
                }

                searchFrom = keywordIdx + (isExtends ? 8 : 11);
            }
        }
    }

    /**
     * 从源码行中提取泛型类型参数的简单名列表
     *
     * <p>例如从 {@code public class Foo extends ServiceImpl<AjglMapper, a> implements Bar<b, c>}
     * 提取 ["AjglMapper", "a", "b", "c"] </p>
     *
     * <p>使用尖括号深度跟踪,跳过嵌套泛型和方法调用中的括号 </p>
     */
    static List<String> extractGenericTypeArguments(String line) {
        List<String> args = new ArrayList<>();
        for (TypeArgWithPosition arg : extractGenericTypeArgumentsWithPosition(line)) {
            args.add(arg.name);
        }
        return args;
    }

    /**
     * 从源码行中提取泛型类型参数及其在行中的起始列号
     *
     * <p>与 {@link #extractGenericTypeArguments(String)} 类似,但额外返回每个参数
     * 在源码行中的字符偏移量,用于创建精确的列位置引用 </p>
     */
    static List<TypeArgWithPosition> extractGenericTypeArgumentsWithPosition(String line) {
        List<TypeArgWithPosition> args = new ArrayList<>();
        if (line == null) {
            return args;
        }

        int depth = 0; // 尖括号深度
        StringBuilder current = new StringBuilder();
        int argStart = -1; // 当前参数的起始列号

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            // 跳过字符串和字符字面量
            if (c == '"' || c == '\'') {
                i = skipQuoted(line, i, c) - 1;
                continue;
            }

            if (c == '<') {
                if (depth == 0) {
                    current.setLength(0);
                    argStart = -1;
                }
                depth++;
                if (depth > 1) {
                    current.append(c);
                }
            } else if (c == '>') {
                depth--;
                if (depth == 0) {
                    String arg = current.toString().strip();
                    if (!arg.isEmpty() && isSimpleTypeArg(arg) && argStart >= 0) {
                        args.add(new TypeArgWithPosition(arg, argStart));
                    }
                    current.setLength(0);
                    argStart = -1;
                } else if (depth > 0) {
                    current.append(c);
                }
            } else if (c == ',' && depth == 1) {
                String arg = current.toString().strip();
                if (!arg.isEmpty() && isSimpleTypeArg(arg) && argStart >= 0) {
                    args.add(new TypeArgWithPosition(arg, argStart));
                }
                current.setLength(0);
                argStart = -1;
            } else if (depth >= 1) {
                if (depth == 1 && argStart < 0 && !Character.isWhitespace(c)) {
                    argStart = i;
                }
                current.append(c);
            }
        }
        return args;
    }

    /**
     * 判断是否为可导航的简单类型参数
     * 排除通配符(?) 类型变量(T, E 等大写单字母) 基本类型包装类
     */
    private static boolean isSimpleTypeArg(String arg) {
        if (arg.isEmpty() || "?".equals(arg)) {
            return false;
        }
        // 带有 extends/super 的通配符
        if (arg.startsWith("?")) {
            return false;
        }
        // 全限定名不需要增强(正则已处理)
        if (arg.contains(".")) {
            return false;
        }
        // 有效 Java 标识符
        if (!Character.isJavaIdentifierStart(arg.charAt(0))) {
            return false;
        }
        for (int i = 1; i < arg.length(); i++) {
            if (!Character.isJavaIdentifierPart(arg.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 判断匹配片段是否为有效的类引用
     *
     * <p>混淆后的类名通常为小写单字母(如 a/b/c),若只按首字母大写过滤
     * 会漏掉所有混淆类引用,导致 Ctrl+Click 导航失效</p>
     *
     * <p>简单规则：最后一个 '.' 后面的简单类名必须是有效 Java 标识符,
     * 且至少包含两个 '.' 段(至少一级包 + 类名),避免误匹配单个变量</p>
     */
    private static boolean looksLikeClassReference(String match) {
        if (match == null || match.isBlank()) {
            return false;
        }
        int lastDot = match.lastIndexOf('.');
        if (lastDot < 0) {
            return false;
        }
        String simpleName = match.substring(lastDot + 1);
        if (simpleName.isEmpty() || !Character.isJavaIdentifierStart(simpleName.charAt(0))) {
            return false;
        }
        for (int i = 1; i < simpleName.length(); i++) {
            if (!Character.isJavaIdentifierPart(simpleName.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String extractModifiers(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().replaceAll("\\s+", " ");
    }

    /** 方法签名记录：包含方法名和参数个数 */
    public record MethodSignature(String name, int paramCount) {
    }

    /** 泛型类型参数及其在源码行中的起始列号 */
    record TypeArgWithPosition(String name, int columnStart) {
    }
}
