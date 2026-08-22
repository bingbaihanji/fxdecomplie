package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 类型名判定工具(移植自 class-diagram-gen 的 {@code UMLHelper}).
 * 输入为点分格式的可读类型名,例如 {@code java.util.List<com.example.Order>}.
 */
public final class UmlTypeNameUtil {

    private static final Set<String> JAVA_CORE = new HashSet<>(Arrays.asList(
            // 基本类型
            "boolean", "byte", "char", "short", "int", "long", "float", "double", "void",
            // java.lang
            "Object", "String", "Integer", "Long", "Double", "Float", "Short", "Byte",
            "Boolean", "Character", "Void", "Number", "Math", "StringBuilder", "StringBuffer",
            "Class", "Enum", "Comparable", "Iterable", "Runnable", "CharSequence", "Serializable",
            "Throwable", "Exception", "RuntimeException", "Error",
            // java.math
            "BigDecimal", "BigInteger",
            // java.time
            "Date", "LocalDate", "LocalDateTime", "LocalTime", "OffsetDateTime", "ZonedDateTime",
            "Duration", "Instant",
            // java.util / java.util.stream
            "List", "ArrayList", "LinkedList", "Set", "HashSet", "LinkedHashSet", "TreeSet",
            "Map", "HashMap", "LinkedHashMap", "TreeMap", "Collection", "Queue", "Deque",
            "Optional", "Stream", "Iterator", "Arrays", "Collections",
            "UUID", "Locale", "Calendar", "TimeUnit", "URL", "Timer", "TimeZone",
            // java.io / java.nio / java.sql
            "File", "Path", "URI", "InputStream", "OutputStream", "Reader", "Writer", "Timestamp",
            // java.util.function
            "Supplier", "Consumer", "Function", "Predicate", "BiConsumer", "BiFunction"));

    private UmlTypeNameUtil() {
        throw new AssertionError("utility class");
    }

    /**
     * 提取数组或泛型类型的最内层元素类型.
     * {@code List<Order>} → {@code Order};{@code Order[]} → {@code Order};
     * {@code Map<String,List<Order>>} → {@code Order}(取最后一个顶层类型参数并递归取内层元素).
     * 通配符边界({@code ? extends X} / {@code ? super X})归一化为 {@code X}.
     */
    public static String getElementType(String type) {
        if (type == null) {
            return "";
        }
        String t = type.trim();
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2).trim();
        }
        int lt = t.indexOf('<');
        if (lt < 0) {
            return t;
        }
        int lastGt = t.lastIndexOf('>');
        if (lastGt <= lt) {
            return t.substring(0, lt).trim();
        }
        String inner = t.substring(lt + 1, lastGt).trim();
        String lastArg = splitLastTopLevelArg(inner).trim();
        if (lastArg.startsWith("? extends ")) {
            lastArg = lastArg.substring("? extends ".length()).trim();
        } else if (lastArg.startsWith("? super ")) {
            lastArg = lastArg.substring("? super ".length()).trim();
        } else if (lastArg.equals("?")) {
            lastArg = "";
        }
        return getElementType(lastArg);
    }

    /** 返回泛型参数字符串中最后一个嵌套深度为 0 的逗号之后的部分;无顶层逗号则返回整个字符串 */
    private static String splitLastTopLevelArg(String inner) {
        int depth = 0;
        int lastComma = -1;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                lastComma = i;
            }
        }
        return lastComma < 0 ? inner : inner.substring(lastComma + 1);
    }

    /** 当类型是数组或泛型(集合/映射)时返回 true */
    public static boolean isCollectionOrArray(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim();
        return t.endsWith("[]") || t.indexOf('<') >= 0;
    }

    /** 去掉包名前缀,保留元素类型的简单名称 */
    public static String getSimpleName(String type) {
        String base = getElementType(type);
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    /**
     * 当元素类型是非 JDK 的类,值得建立关系时返回 true.
     * 注意:JAVA_CORE 简单名白名单意味着用户自定义的名为 String/List/Date 等的类也会被排除
     * (继承自参考项目的启发式判定).
     */
    public static boolean isUMLClassType(String type) {
        String simple = getSimpleName(type);
        if (simple.isEmpty()) {
            return false;
        }
        if (JAVA_CORE.contains(simple)) {
            return false;
        }
        String base = getElementType(type);
        return !isJdk(base);
    }

    /** 判定是否 JDK 内部类型;同时接受内部名(java/lang/Object)与点分名(java.util.ArrayList) */
    public static boolean isJdk(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim().replace('.', '/');
        return n.startsWith("java/") || n.startsWith("javax/")
                || n.startsWith("sun/") || n.startsWith("com/sun/")
                || n.startsWith("jdk/");
    }
}
