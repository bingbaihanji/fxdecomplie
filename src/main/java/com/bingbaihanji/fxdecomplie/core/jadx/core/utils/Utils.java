package com.bingbaihanji.fxdecomplie.core.jadx.core.utils;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.DepthTraversal;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;
import com.bingbaihanji.fxdecomplie.util.collection.ListUtils;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 通用工具类，提供字符串处理、集合操作、堆栈跟踪、线程工具等常用功能
 */
public class Utils {

    /** Jadx API 所在的包名，用于在过滤堆栈跟踪时识别并截断 API 层调用帧 */
    private static final String JADX_API_PACKAGE = JadxDecompiler.class.getPackage().getName();
    /** 堆栈跟踪过滤的停止类名，遇到该类时截断后续的调用帧 */
    private static final String STACKTRACE_STOP_CLS_NAME = DepthTraversal.class.getName();

    private Utils() {
    }

    /**
     * 清理对象名称，将 Smali 格式的类名（如 Ljava/lang/String;）转换为 Java 格式（如 java.lang.String）
     *
     * @param obj Smali 格式的对象名称
     * @return 清理后的 Java 格式对象名称
     */
    public static String cleanObjectName(String obj) {
        if (obj.charAt(0) == 'L') {
            int last = obj.length() - 1;
            if (obj.charAt(last) == ';') {
                return obj.substring(1, last).replace('/', '.');
            }
        }
        return obj;
    }

    /**
     * 截取对象名称，移除 Smali 格式中的 'L' 前缀和 ';' 后缀
     *
     * @param obj Smali 格式的对象名称
     * @return 截取后的对象名称
     */
    public static String cutObject(String obj) {
        if (obj.charAt(0) == 'L') {
            return obj.substring(1, obj.length() - 1);
        }
        return obj;
    }

    /**
     * 将 Java 格式的类名转换为 Smali 格式（如 java.lang.String → Ljava/lang/String;）
     *
     * @param obj Java 格式的对象名称
     * @return Smali 格式的限定对象名称
     */
    public static String makeQualifiedObjectName(String obj) {
        return 'L' + obj.replace('.', '/') + ';';
    }

    /**
     * 将 Smali 类型描述符转换为 Java 类型名称
     * <p>
     * 支持基本类型（V→void, Z→boolean, C→char, B→byte, S→short, I→int, F→float, J→long, D→double）、
     * 对象类型（L...;）和数组类型（[...）
     *
     * @param descString Smali 类型描述符
     * @return 对应的 Java 类型名称
     */
    public static String smaliNameToJavaName(String descString) {
        if (descString.isEmpty()) {
            return descString;
        }

        String javaName;
        switch (descString.charAt(0)) {
            case 'V':
                javaName = "void";
                break;
            case 'Z':
                javaName = "boolean";
                break;
            case 'C':
                javaName = "char";
                break;
            case 'B':
                javaName = "byte";
                break;
            case 'S':
                javaName = "short";
                break;
            case 'I':
                javaName = "int";
                break;
            case 'F':
                javaName = "float";
                break;
            case 'J':
                javaName = "long";
                break;
            case 'D':
                javaName = "double";
                break;
            case 'L':
                javaName = cleanObjectNameWithInnerClass(descString);
                break;
            case '[':
                javaName = String.format("%s[]", smaliNameToJavaName(descString.substring(1, descString.length())));
                break;
            default:
                javaName = descString;
                break;
        }
        return javaName;
    }

    /**
     * 清理对象名称并处理内部类，将 Smali 格式转换为 Java 格式，同时将内部类分隔符 '$' 替换为 '.'
     *
     * @param obj Smali 格式的对象名称
     * @return 处理内部类后的 Java 格式对象名称
     */
    private static String cleanObjectNameWithInnerClass(String obj) {
        // 或许可以直接更新 Utils.cleanObjectName 方法？
        String result = Utils.cleanObjectName(obj);
        return result.replace('$', '.');
    }

    /**
     * 将 Java 类型名称转换为 Smali 类型描述符（{@link #smaliNameToJavaName} 的逆操作）
     *
     * @param descString Java 类型名称
     * @return 对应的 Smali 类型描述符
     */
    public static String javaNameToSmaliName(String descString) {
        if (descString.isEmpty()) {
            return descString;
        }

        if (descString.endsWith("[]")) {
            return String.format("[%s", javaNameToSmaliName(descString.substring(0, descString.length() - 2)));
        }

        String javaName;
        switch (descString) {
            case "void":
                javaName = "V";
                break;
            case "boolean":
                javaName = "Z";
                break;
            case "char":
                javaName = "C";
                break;
            case "byte":
                javaName = "B";
                break;
            case "short":
                javaName = "S";
                break;
            case "int":
                javaName = "I";
                break;
            case "float":
                javaName = "F";
                break;
            case "long":
                javaName = "J";
                break;
            case "double":
                javaName = "D";
                break;
            default:
                javaName = Utils.makeQualifiedObjectName(descString);
                break;
        }
        return javaName;
    }

    /**
     * 重复拼接字符串指定的次数
     *
     * @param str   待重复的字符串
     * @param count 重复次数，小于 1 时返回空字符串
     * @return 重复拼接后的字符串
     */
    @SuppressWarnings("StringRepeatCanBeUsed")
    public static String strRepeat(String str, int count) {
        if (count < 1) {
            return "";
        }
        if (count == 1) {
            return str;
        }
        StringBuilder sb = new StringBuilder(str.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 将可迭代对象拼接为字符串，默认使用 ", " 作为分隔符
     *
     * @param objects 待拼接的可迭代对象
     * @return 拼接后的字符串
     */
    public static String listToString(Iterable<?> objects) {
        return listToString(objects, ", ");
    }

    /**
     * 将可迭代对象按指定分隔符拼接为字符串
     *
     * @param objects 待拼接的可迭代对象
     * @param joiner  分隔符
     * @return 拼接后的字符串
     */
    public static String listToString(Iterable<?> objects, String joiner) {
        if (objects == null) {
            return "";
        }
        return listToString(objects, joiner, Objects::toString);
    }

    /**
     * 将可迭代对象通过转换函数映射后拼接为字符串，默认使用 ", " 作为分隔符
     *
     * @param objects 待拼接的可迭代对象
     * @param toStr   元素到字符串的转换函数
     * @return 拼接后的字符串
     */
    public static <T> String listToString(Iterable<T> objects, Function<T, String> toStr) {
        return listToString(objects, ", ", toStr);
    }

    /**
     * 将可迭代对象通过转换函数映射后按指定分隔符拼接为字符串
     *
     * @param objects 待拼接的可迭代对象
     * @param joiner  分隔符
     * @param toStr   元素到字符串的转换函数
     * @return 拼接后的字符串
     */
    public static <T> String listToString(Iterable<T> objects, String joiner, Function<T, String> toStr) {
        StringBuilder sb = new StringBuilder();
        listToString(sb, objects, joiner, toStr);
        return sb.toString();
    }

    /**
     * 将可迭代对象按指定分隔符拼接并追加到已有的 StringBuilder 中
     *
     * @param sb      结果构建器
     * @param objects 待拼接的可迭代对象
     * @param joiner  分隔符
     */
    public static <T> void listToString(StringBuilder sb, Iterable<T> objects, String joiner) {
        listToString(sb, objects, joiner, Objects::toString);
    }

    /**
     * 将可迭代对象通过转换函数映射后按指定分隔符拼接并追加到已有的 StringBuilder 中
     *
     * @param sb      结果构建器
     * @param objects 待拼接的可迭代对象
     * @param joiner  分隔符
     * @param toStr   元素到字符串的转换函数
     */
    public static <T> void listToString(StringBuilder sb, Iterable<T> objects, String joiner, Function<T, String> toStr) {
        if (objects == null) {
            return;
        }
        Iterator<T> it = objects.iterator();
        if (it.hasNext()) {
            sb.append(toStr.apply(it.next()));
        }
        while (it.hasNext()) {
            sb.append(joiner).append(toStr.apply(it.next()));
        }
    }

    /**
     * 将数组元素以 ", " 分隔拼接为字符串
     *
     * @param arr 待拼接的数组
     * @return 拼接后的字符串，数组为空时返回空字符串
     */
    public static <T> String arrayToStr(T[] arr) {
        int len = arr == null ? 0 : arr.length;
        if (len == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(arr[0]);
        for (int i = 1; i < len; i++) {
            sb.append(", ").append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * 将字符串列表直接连接为一个字符串（无分隔符）
     *
     * @param list 待连接的字符串列表
     * @return 连接后的字符串
     */
    public static String concatStrings(List<String> list) {
        if (isEmpty(list)) {
            return "";
        }
        if (list.size() == 1) {
            return list.get(0);
        }
        StringBuilder sb = new StringBuilder();
        list.forEach(sb::append);
        return sb.toString();
    }

    /**
     * 获取当前调用位置的堆栈跟踪字符串
     *
     * @return 当前堆栈跟踪文本
     */
    public static String currentStackTrace() {
        return getStackTrace(new Exception());
    }

    /**
     * 获取当前调用位置的堆栈跟踪字符串，并跳过开头指定数量的栈帧
     *
     * @param skipFrames 需要跳过的栈帧数量
     * @return 当前堆栈跟踪文本
     */
    public static String currentStackTrace(int skipFrames) {
        Exception e = new Exception();
        StackTraceElement[] stackTrace = e.getStackTrace();
        int len = stackTrace.length;
        if (skipFrames < len) {
            e.setStackTrace(Arrays.copyOfRange(stackTrace, skipFrames, len));
        }
        return getStackTrace(e);
    }

    /**
     * 获取完整的堆栈跟踪字符串（不过滤 jadx 内部调用帧）
     *
     * @param throwable 异常对象
     * @return 完整的堆栈跟踪文本
     */
    public static String getFullStackTrace(Throwable throwable) {
        return getStackTrace(throwable, false);
    }

    /**
     * 获取经过过滤的堆栈跟踪字符串（截断 jadx 内部调用帧）
     *
     * @param throwable 异常对象
     * @return 过滤后的堆栈跟踪文本
     */
    public static String getStackTrace(Throwable throwable) {
        return getStackTrace(throwable, true);
    }

    /**
     * 获取堆栈跟踪字符串
     *
     * @param throwable 异常对象
     * @param filter    是否过滤 jadx 内部调用帧
     * @return 堆栈跟踪文本
     */
    private static String getStackTrace(Throwable throwable, boolean filter) {
        if (throwable == null) {
            return "";
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw, true);
        if (filter) {
            filterRecursive(throwable);
        }
        throwable.printStackTrace(pw);
        return sw.getBuffer().toString();
    }

    /**
     * 将异常的堆栈跟踪逐行追加写入到代码输出器中
     *
     * @param code      代码输出器
     * @param throwable 异常对象
     */
    public static void appendStackTrace(ICodeWriter code, Throwable throwable) {
        if (throwable == null) {
            return;
        }
        code.startLine();
        OutputStream w = new OutputStream() {
            @Override
            public void write(int b) {
                char c = (char) b;
                switch (c) {
                    case '\n':
                        code.startLine();
                        break;

                    case '\r':
                        // 忽略回车符
                        break;

                    default:
                        code.add(c);
                        break;
                }
            }
        };
        try (PrintWriter pw = new PrintWriter(w, true, StandardCharsets.UTF_8)) {
            filterRecursive(throwable);
            throwable.printStackTrace(pw);
            pw.flush();
        }
    }

    /**
     * 递归过滤异常及其原因链的堆栈跟踪，截断 jadx 内部调用帧
     *
     * @param th 待过滤的异常
     */
    private static void filterRecursive(Throwable th) {
        try {
            filter(th);
        } catch (Exception e) {
            // 忽略过滤过程中的异常
        }
        Throwable cause = th.getCause();
        if (cause != null) {
            filterRecursive(cause);
        }
    }

    private static void filter(Throwable th) {
        StackTraceElement[] stackTrace = th.getStackTrace();
        int length = stackTrace.length;
        StackTraceElement prevElement = null;
        for (int i = 0; i < length; i++) {
            StackTraceElement stackTraceElement = stackTrace[i];
            String clsName = stackTraceElement.getClassName();
            if (clsName.equals(STACKTRACE_STOP_CLS_NAME)
                    || clsName.startsWith(JADX_API_PACKAGE)
                    || Objects.equals(prevElement, stackTraceElement)) {
                th.setStackTrace(Arrays.copyOfRange(stackTrace, 0, i));
                return;
            }
            prevElement = stackTraceElement;
        }
        // 未找到停止条件 -> 直接截断到最后一个 jadx 类之后的部分
        for (int i = length - 1; i >= 0; i--) {
            String clsName = stackTrace[i].getClassName();
            if (clsName.startsWith("jadx.")) {
                if (clsName.startsWith("jadx.tests.")) {
                    continue;
                }
                th.setStackTrace(Arrays.copyOfRange(stackTrace, 0, i));
                return;
            }
        }
    }

    /**
     * 将集合中的每个元素通过映射函数转换后收集为新列表
     *
     * @param list    源集合
     * @param mapFunc 元素映射函数
     * @return 映射后的列表，源集合为空时返回空列表
     */
    public static <T, R> List<R> collectionMap(Collection<T> list, Function<T, R> mapFunc) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<R> result = new ArrayList<>(list.size());
        for (T t : list) {
            result.add(mapFunc.apply(t));
        }
        return result;
    }

    /**
     * 将集合中的每个元素通过映射函数转换后收集为新列表，并过滤掉映射结果为 null 的元素
     *
     * @param list    源集合
     * @param mapFunc 元素映射函数
     * @return 映射后的非空元素列表，源集合为空时返回空列表
     */
    public static <T, R> List<R> collectionMapNoNull(Collection<T> list, Function<T, R> mapFunc) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        List<R> result = new ArrayList<>(list.size());
        for (T t : list) {
            R r = mapFunc.apply(t);
            if (r != null) {
                result.add(r);
            }
        }
        return result;
    }

    /**
     * 按引用（==）判断列表中是否包含指定元素
     *
     * @param list    待检查的列表
     * @param element 目标元素
     * @return 若存在引用相同的元素则返回 true
     */
    public static <T> boolean containsInListByRef(List<T> list, T element) {
        if (isEmpty(list)) {
            return false;
        }
        for (T t : list) {
            if (t == element) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按引用（==）查找元素在列表中的索引
     *
     * @param list    待检查的列表
     * @param element 目标元素
     * @return 引用相同元素的索引，未找到时返回 -1
     */
    public static <T> int indexInListByRef(List<T> list, T element) {
        if (list == null || list.isEmpty()) {
            return -1;
        }
        int size = list.size();
        for (int i = 0; i < size; i++) {
            T t = list.get(i);
            if (t == element) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 将列表转换为不可变列表，并对空列表和单元素列表做优化
     *
     * @param list 源列表
     * @return 不可变列表
     */
    public static <T> List<T> lockList(List<T> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        if (list.size() == 1) {
            return Collections.singletonList(list.get(0));
        }
        return List.copyOf(list);
    }

    /**
     * 返回列表从 startIndex（含）到末尾的子列表
     *
     * @param list       源列表
     * @param startIndex 起始索引（包含）
     * @return 子列表，起始索引超出范围时返回空列表
     */
    public static <T> List<T> listTail(List<T> list, int startIndex) {
        if (startIndex == 0) {
            return list;
        }
        int size = list.size();
        if (startIndex >= size) {
            return Collections.emptyList();
        }
        return list.subList(startIndex, size);
    }

    /**
     * 合并两个列表为一个新列表，其中一个为空时直接返回另一个
     *
     * @param first  第一个列表
     * @param second 第二个列表
     * @return 合并后的列表
     */
    public static <T> List<T> mergeLists(List<T> first, List<T> second) {
        if (isEmpty(first)) {
            return second;
        }
        if (isEmpty(second)) {
            return first;
        }
        List<T> result = new ArrayList<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    /**
     * 合并两个集合为一个新集合，其中一个为空时直接返回另一个
     *
     * @param first  第一个集合
     * @param second 第二个集合
     * @return 合并后的集合
     */
    public static <T> Set<T> mergeSets(Set<T> first, Set<T> second) {
        if (isEmpty(first)) {
            return second;
        }
        if (isEmpty(second)) {
            return first;
        }
        Set<T> result = new HashSet<>(first.size() + second.size());
        result.addAll(first);
        result.addAll(second);
        return result;
    }

    /**
     * 由成对的键值参数构建一个不可变的字符串映射
     *
     * @param parameters 键值交替排列的参数（key1, value1, key2, value2, ...）
     * @return 不可变的字符串映射
     * @throws IllegalArgumentException 当参数个数不是偶数时抛出
     */
    public static Map<String, String> newConstStringMap(String... parameters) {
        int len = parameters.length;
        if (len == 0) {
            return Collections.emptyMap();
        }
        if (len % 2 != 0) {
            throw new IllegalArgumentException("Incorrect arguments count: " + len);
        }
        Map<String, String> result = new HashMap<>(len / 2);
        for (int i = 0; i < len - 1; i += 2) {
            result.put(parameters[i], parameters[i + 1]);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 合并两个 Map，返回一个新的 HashMap第二个 Map 的值会覆盖第一个 Map 中的同名键
     *
     * @param first  第一个 Map
     * @param second 第二个 Map
     * @return 合并后的 Map
     */
    public static <K, V> Map<K, V> mergeMaps(Map<K, V> first, Map<K, V> second) {
        if (isEmpty(first)) {
            return second;
        }
        if (isEmpty(second)) {
            return first;
        }
        Map<K, V> result = new HashMap<>(first.size() + second.size());
        result.putAll(first);
        result.putAll(second);
        return result;
    }

    /**
     * 通过“值到键”的映射函数，将值列表构建为 Map
     * <br>
     * 类似于：
     * <br>
     * {@code list.stream().collect(Collectors.toMap(mapKey, Function.identity())); }
     *
     * @param list   值列表
     * @param mapKey 由值提取键的映射函数
     * @return 构建的 Map
     */
    public static <K, V> Map<K, V> groupBy(List<V> list, Function<V, K> mapKey) {
        Map<K, V> map = new HashMap<>(list.size());
        for (V v : list) {
            map.put(mapKey.apply(v), v);
        }
        return map;
    }

    /**
     * 对树结构进行简单的深度优先遍历（不允许存在环）
     *
     * @param root             根节点
     * @param childrenProvider 提供节点子节点列表的函数
     * @param visitor          节点访问器
     */
    public static <T> void treeDfsVisit(T root, Function<T, List<T>> childrenProvider, Consumer<T> visitor) {
        multiRootTreeDfsVisit(Collections.singletonList(root), childrenProvider, visitor);
    }

    /**
     * 对多根树结构进行深度优先遍历（不允许存在环）
     *
     * @param roots            根节点列表
     * @param childrenProvider 提供节点子节点列表的函数
     * @param visitor          节点访问器
     */
    public static <T> void multiRootTreeDfsVisit(List<T> roots, Function<T, List<T>> childrenProvider, Consumer<T> visitor) {
        Deque<T> queue = new ArrayDeque<>(roots);
        while (true) {
            T current = queue.pollLast();
            if (current == null) {
                return;
            }
            visitor.accept(current);
            for (T child : childrenProvider.apply(current)) {
                queue.addLast(child);
            }
        }
    }

    /**
     * 当列表恰好只有一个元素时返回该元素，否则返回 null
     *
     * @param list 源列表
     * @return 唯一元素或 null
     */
    @Nullable
    public static <T> T getOne(@Nullable List<T> list) {
        if (list == null || list.size() != 1) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 当集合恰好只有一个元素时返回该元素，否则返回 null
     *
     * @param collection 源集合
     * @return 唯一元素或 null
     */
    @Nullable
    public static <T> T getOne(@Nullable Collection<T> collection) {
        if (collection == null || collection.size() != 1) {
            return null;
        }
        return collection.iterator().next();
    }

    /**
     * 判断输入集合中是否包含任意一个搜索键
     *
     * @param inputSet   输入集合
     * @param searchKeys 搜索键集合
     * @return 若存在交集则返回 true
     */
    public static <T> boolean isSetContainsAny(Set<T> inputSet, Set<T> searchKeys) {
        for (T t : inputSet) {
            if (searchKeys.contains(t)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 返回列表的第一个元素，列表为空时返回 null
     *
     * @param list 源列表
     * @return 第一个元素或 null
     */
    @Nullable
    public static <T> T first(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    /**
     * 返回可迭代对象的第一个元素，无元素时返回 null
     *
     * @param list 源可迭代对象
     * @return 第一个元素或 null
     */
    @Nullable
    public static <T> T first(Iterable<T> list) {
        Iterator<T> it = list.iterator();
        if (!it.hasNext()) {
            return null;
        }
        return it.next();
    }

    /**
     * 返回列表的最后一个元素，列表为空时返回 null
     *
     * @param list 源列表
     * @return 最后一个元素或 null
     */
    @Nullable
    public static <T> T last(List<T> list) {
        if (list.isEmpty()) {
            return null;
        }
        return list.get(list.size() - 1);
    }

    /**
     * 返回可迭代对象的最后一个元素，无元素时返回 null
     *
     * @param list 源可迭代对象
     * @return 最后一个元素或 null
     */
    @Nullable
    public static <T> T last(Iterable<T> list) {
        Iterator<T> it = list.iterator();
        if (!it.hasNext()) {
            return null;
        }
        while (true) {
            T next = it.next();
            if (!it.hasNext()) {
                return next;
            }
        }
    }

    /**
     * 返回对象本身，若对象为 null 则返回默认值
     *
     * @param obj        目标对象
     * @param defaultObj 默认值
     * @return 非空对象或默认值
     */
    public static <T> T getOrElse(@Nullable T obj, T defaultObj) {
        if (obj == null) {
            return defaultObj;
        }
        return obj;
    }

    /**
     * 判断集合是否为 null 或空
     *
     * @param col 待判断的集合
     * @return 若为 null 或空则返回 true
     */
    public static <T> boolean isEmpty(Collection<T> col) {
        return col == null || col.isEmpty();
    }

    /**
     * 判断集合是否不为 null 且非空
     *
     * @param col 待判断的集合
     * @return 若非空则返回 true
     */
    public static <T> boolean notEmpty(Collection<T> col) {
        return col != null && !col.isEmpty();
    }

    /**
     * 判断 Map 是否为 null 或空
     *
     * @param map 待判断的 Map
     * @return 若为 null 或空则返回 true
     */
    public static <K, V> boolean isEmpty(Map<K, V> map) {
        return map == null || map.isEmpty();
    }

    /**
     * 判断数组是否为 null 或长度为零
     *
     * @param arr 待判断的数组
     * @return 若为 null 或长度为 0 则返回 true
     */
    public static <T> boolean isEmpty(T[] arr) {
        return arr == null || arr.length == 0;
    }

    /**
     * 判断数组是否不为 null 且长度大于零
     *
     * @param arr 待判断的数组
     * @return 若非空则返回 true
     */
    public static <T> boolean notEmpty(T[] arr) {
        return arr != null && arr.length != 0;
    }

    /**
     * 检查当前线程是否被中断，若已中断则抛出运行时异常
     *
     * @throws JadxRuntimeException 当前线程被中断时抛出
     */
    public static void checkThreadInterrupt() {
        if (Thread.currentThread().isInterrupted()) {
            throw new JadxRuntimeException("Thread interrupted");
        }
    }

    /**
     * 读取布尔类型的环境变量
     *
     * @param varName  环境变量名
     * @param defValue 默认值
     * @return 环境变量的布尔值，未设置时返回默认值
     * @deprecated 核心模块中不应使用环境变量
     *             建议在 `app` 中解析（使用 'app-commons' 的 JadxCommonEnv）并通过 jadx 参数设置
     */
    @Deprecated
    public static boolean getEnvVarBool(String varName, boolean defValue) {
        String strValue = System.getenv(varName);
        if (strValue == null) {
            return defValue;
        }
        return "true".equalsIgnoreCase(strValue);
    }

    /**
     * 读取整数类型的环境变量
     *
     * @param varName  环境变量名
     * @param defValue 默认值
     * @return 环境变量的整数值，未设置时返回默认值
     * @deprecated 核心模块中不应使用环境变量
     *             建议在 `app` 中解析（使用 'app-commons' 的 JadxCommonEnv）并通过 jadx 参数设置
     */
    @Deprecated
    public static int getEnvVarInt(String varName, int defValue) {
        String strValue = System.getenv(varName);
        if (strValue == null) {
            return defValue;
        }
        return Integer.parseInt(strValue);
    }

    /**
     * 安全地将字符串解析为 int，解析失败时返回默认值
     *
     * @param value    待解析的字符串
     * @param defValue 默认值
     * @return 解析结果或默认值
     */
    public static int safeParseInt(String value, int defValue) {
        if (value == null || value.isEmpty()) {
            return defValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defValue;
        }
    }

    /**
     * 安全地将字符串解析为 Integer，解析失败时返回 null
     *
     * @param value 待解析的字符串
     * @return 解析结果，失败时返回 null
     */
    public static @Nullable Integer safeParseInteger(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return null;
        }
    }

    /** @see ListUtils#removeLast(List) */
    public static <T> @Nullable T removeLast(@Nullable List<T> list) {
        if (list == null) {
            return null;
        }
        int size = list.size();
        if (size == 0) {
            return null;
        }
        return list.remove(size - 1);
    }

    // ========== Methods relocated from ListUtils ==========

    /** @see ListUtils#safeAdd(List, Object) */
    public static <T> List<T> safeAdd(List<T> list, T obj) {
        if (list == null || list.isEmpty()) {
            List<T> newList = new ArrayList<>(1);
            newList.add(obj);
            return newList;
        }
        list.add(obj);
        return list;
    }

    /** @see ListUtils#safeRemove(List, Object) */
    public static <T> void safeRemove(List<T> list, T obj) {
        if (list != null && !list.isEmpty()) {
            list.remove(obj);
        }
    }

    /** @see ListUtils#safeRemoveAndTrim(List, Object) */
    public static <T> List<T> safeRemoveAndTrim(List<T> list, T obj) {
        if (list == null || list.isEmpty()) {
            return list;
        }
        if (list.remove(obj) && list.isEmpty()) {
            return Collections.emptyList();
        }
        return list;
    }

    /** @see ListUtils#safeReplace(List, Object, Object) */
    public static <T> List<T> safeReplace(List<T> list, T oldObj, T newObj) {
        if (list == null || list.isEmpty()) {
            List<T> newList = new ArrayList<>(1);
            newList.add(newObj);
            return newList;
        }
        int idx = list.indexOf(oldObj);
        if (idx != -1) {
            list.set(idx, newObj);
        } else {
            list.add(newObj);
        }
        return list;
    }

    /** @see ListUtils#unorderedEquals(List, List) */
    public static <T> boolean unorderedEquals(List<T> first, List<T> second) {
        if (first.size() != second.size()) {
            return false;
        }
        return first.containsAll(second);
    }

    /** @see ListUtils#orderedEquals(List, List, BiPredicate) */
    public static <T, U> boolean orderedEquals(List<T> list1, List<U> list2, java.util.function.BiPredicate<T, U> comparer) {
        if (list1 == list2) {
            return true;
        }
        if (list1.size() != list2.size()) {
            return false;
        }
        java.util.Iterator<T> iter1 = list1.iterator();
        java.util.Iterator<U> iter2 = list2.iterator();
        while (iter1.hasNext() && iter2.hasNext()) {
            if (!comparer.test(iter1.next(), iter2.next())) {
                return false;
            }
        }
        return !iter1.hasNext() && !iter2.hasNext();
    }

    public static <T> T filterOnlyOne(List<T> list, java.util.function.Predicate<T> filter) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        T found = null;
        for (T t : list) {
            if (filter.test(t)) {
                if (found == null) {
                    found = t;
                } else {
                    return null; // more than one match
                }
            }
        }
        return found;
    }

    /** @see ListUtils#distinctMergeSortedLists(List, List) */
    public static <T extends Comparable<T>> List<T> distinctMergeSortedLists(List<T> first, List<T> second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }
        Set<T> set = new TreeSet<>(first);
        set.addAll(second);
        return new ArrayList<>(set);
    }

    /** @see ListUtils#concat(Object, Object[]) */
    @SafeVarargs
    public static <T> List<T> concat(T first, T... values) {
        List<T> list = new ArrayList<>(1 + values.length);
        list.add(first);
        list.addAll(Arrays.asList(values));
        return list;
    }

    /** @see ListUtils#distinctList(List) */
    public static <T> List<T> distinctList(List<T> list) {
        return new ArrayList<>(new LinkedHashSet<>(list));
    }

    /** @see ListUtils#isSingleElement(List, Object) */
    public static <T> boolean isSingleElement(@Nullable List<T> list, T obj) {
        if (list == null || list.size() != 1) {
            return false;
        }
        return Objects.equals(list.get(0), obj);
    }
}
