package com.bingbaihanji.fxdecomplie.ui.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析 {@link GraphService} 生成的 DOT 子集,产出结构化图形数据
 *
 * <p>支持的 DOT 语法子集：</p>
 * <ul>
 *   <li>digraph 声明、rankdir（LR / BT）</li>
 *   <li>含引号或裸标识符的节点（id [key=value, ...]）</li>
 *   <li>边（a -> b）</li>
 *   <li>属性：label、fillcolor、fontcolor</li>
 *   <li>全局 label（用于方法图截断提示等）</li>
 * </ul>
 *
 * <p>不支持的语法（会被忽略）：subgraph、HTML label、record shape、port 等</p>
 *
 * @author bingbaihanji
 * @date 2026-06-22
 */
final class DotGraphParser {

    /**
     * DOT 标识符：双引号字符串（支持反斜杠转义）或裸字母数字标识符
     * group(1)=引号内容, group(2)=裸标识符
     */
    private static final String ID = "(?:\"((?:\\\\.|[^\"\\\\])*)\"|([A-Za-z_][A-Za-z0-9_]*))";

    /** 提取 rankdir 方向（LR / BT）,缺省为 LR */
    private static final Pattern RANK_DIR =
            Pattern.compile("\\brankdir\\s*=\\s*([A-Za-z]+)");

    /** 匹配边语句：a -> b */
    private static final Pattern EDGE =
            Pattern.compile("^\\s*" + ID + "\\s*->\\s*" + ID + ".*$");

    /** 匹配节点语句：id [attr1=val1, attr2=val2, ...]用非贪婪 (.*?) 避免跨多个方括号误匹配 */
    private static final Pattern NODE =
            Pattern.compile("^\\s*" + ID + "\\s*\\[(.*?)]\\s*;?\\s*$");

    /** 提取方括号内的 key=value 属性对 */
    private static final Pattern ATTR =
            Pattern.compile("(\\w+)\\s*=\\s*(\"((?:\\\\.|[^\"\\\\])*)\"|([^,\\]\\s]+))");

    /** 提取全局 label,用于方法图截断提示等信息 */
    private static final Pattern GRAPH_LABEL =
            Pattern.compile("\\blabel\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

    private DotGraphParser() {
        throw new AssertionError("utility class");
    }

    /**
     * 解析 DOT 字符串为结构化图形数据
     *
     * @param dot DOT 格式字符串,可为空
     * @return 解析后的图形对象,空输入返回无节点的默认图
     */
    static DotGraph parse(String dot) {
        if (dot == null || dot.isBlank()) {
            return new DotGraph("LR", List.of(), List.of(), "");
        }

        String rankDir = findRankDir(dot);
        String graphLabel = "";
        Map<String, DotNode> nodes = new LinkedHashMap<>();
        List<DotEdge> edges = new ArrayList<>();

        for (String rawLine : dot.split("\\R")) {
            String line = rawLine.strip();
            // 跳过空行、注释、digraph 声明、大括号
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("digraph")
                    || "{".equals(line) || "}".equals(line)) {
                continue;
            }

            // 尝试匹配边语句
            Matcher edgeMatcher = EDGE.matcher(line);
            if (edgeMatcher.matches()) {
                String from = unescapeId(edgeMatcher.group(1), edgeMatcher.group(2));
                String to = unescapeId(edgeMatcher.group(3), edgeMatcher.group(4));
                // 边引用的节点若未声明,用默认样式补一个隐式节点
                nodes.putIfAbsent(from, DotNode.defaultNode(from));
                nodes.putIfAbsent(to, DotNode.defaultNode(to));
                // 提取边属性（label、style）
                String edgeLabel = "";
                String edgeStyle = "";
                int bracketStart = line.indexOf('[');
                if (bracketStart >= 0) {
                    int bracketEnd = line.indexOf(']', bracketStart);
                    if (bracketEnd < 0) {
                        bracketEnd = line.length();
                    }
                    String attrText = line.substring(bracketStart + 1, bracketEnd);
                    Map<String, String> edgeAttrs = parseAttrs(attrText);
                    edgeLabel = edgeAttrs.getOrDefault("label", "");
                    edgeStyle = edgeAttrs.getOrDefault("style", "");
                }
                edges.add(new DotEdge(from, to, edgeLabel, edgeStyle));
                continue;
            }

            // 尝试匹配节点语句
            Matcher nodeMatcher = NODE.matcher(line);
            if (nodeMatcher.matches()) {
                String id = unescapeId(nodeMatcher.group(1), nodeMatcher.group(2));
                // 跳过全局属性设置（node/edge/graph 关键字）
                if ("node".equals(id) || "edge".equals(id) || "graph".equals(id)) {
                    continue;
                }
                Map<String, String> attrs = parseAttrs(nodeMatcher.group(3));
                String label = attrs.getOrDefault("label", id);
                String fill = attrs.getOrDefault("fillcolor", "#3c3c3c");
                String font = attrs.getOrDefault("fontcolor", "");
                nodes.put(id, new DotNode(id, label, fill, font));
                continue;
            }

            // 非方括号行尝试提取全局 label
            if (!line.contains("[") && !line.contains("->")) {
                Matcher labelMatcher = GRAPH_LABEL.matcher(line);
                if (labelMatcher.find()) {
                    graphLabel = unescape(labelMatcher.group(1));
                }
            }
        }

        // 二次扫描：若仍未找到 graphLabel,检查整段 DOT 开头的 label（方法图场景）
        if (graphLabel.isBlank()) {
            Matcher labelMatcher = GRAPH_LABEL.matcher(dot);
            if (labelMatcher.find() && !dot.substring(0, labelMatcher.start()).contains("[")) {
                graphLabel = unescape(labelMatcher.group(1));
            }
        }
        return new DotGraph(rankDir, List.copyOf(nodes.values()), List.copyOf(edges), graphLabel);
    }

    /**
     * 从 DOT 字符串中提取 rankdir 方向
     *
     * @param dot DOT 格式字符串
     * @return 布局方向（"LR" 或 "BT"）,未声明时缺省为 "LR"
     */
    private static String findRankDir(String dot) {
        Matcher matcher = RANK_DIR.matcher(dot);
        return matcher.find() ? matcher.group(1).toUpperCase() : "LR";
    }

    /**
     * 从方括号内的属性字符串中提取 key=value 对
     * value 可能是双引号字符串（支持转义）或裸值（颜色代码、数字等）
     *
     * @param text 方括号内的属性文本,不可为 null
     * @return 属性键值对映射（保持声明顺序）
     */
    private static Map<String, String> parseAttrs(String text) {
        Map<String, String> attrs = new LinkedHashMap<>();
        Matcher matcher = ATTR.matcher(text);
        while (matcher.find()) {
            // group(3) 为引号内的值,group(4) 为裸值
            String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            attrs.put(matcher.group(1), unescape(value));
        }
        return attrs;
    }

    /**
     * 统一处理标识符的两种表示（引号字符串或裸标识符）,并反转义
     *
     * @param quoted 双引号内的标识符内容（含转义序列）,可为 null
     * @param bare   裸标识符（不含引号）,可为 null
     * @return 反转义后的标识符字符串
     */
    private static String unescapeId(String quoted, String bare) {
        return quoted != null ? unescape(quoted) : bare;
    }

    /**
     * 反转义 DOT 字符串中的反斜杠转义序列
     * 处理 \\n、\\"、\\\\ 等常见转义,未识别的转义字符保留原样
     *
     * @param text 可能包含转义序列的原始字符串,不可为 null
     * @return 反转义后的字符串
     */
    private static String unescape(String text) {
        if (text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        boolean escaping = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaping) {
                // 仅处理 n → 换行,其余字符（包括 "、\\ 等）直接保留
                sb.append(c == 'n' ? '\n' : c);
                escaping = false;
            } else if (c == '\\') {
                escaping = true;
            } else {
                sb.append(c);
            }
        }
        // 末尾孤立反斜杠保留
        if (escaping) {
            sb.append('\\');
        }
        return sb.toString();
    }

    /**
     * 解析后的 DOT 图形数据结构
     *
     * @param rankDir    布局方向（"LR" 从左到右,"BT" 从下到上）
     * @param nodes      节点列表（保持声明顺序）
     * @param edges      边列表
     * @param graphLabel 图形全局标签（截断提示等）,可为空字符串
     */
    record DotGraph(String rankDir, List<DotNode> nodes, List<DotEdge> edges,
                    String graphLabel) {
    }

    /**
     * 图形节点
     *
     * @param id        节点唯一标识（DOT 中的 id）
     * @param label     显示文本
     * @param fillColor 填充颜色（CSS 颜色值）,缺省 #3c3c3c
     * @param fontColor 字体颜色,为空则由渲染器根据背景亮度自动选择
     */
    record DotNode(String id, String label, String fillColor, String fontColor) {

        /** 为边语句中隐含的未声明节点创建默认样式节点 */
        static DotNode defaultNode(String id) {
            return new DotNode(id, id, "#3c3c3c", "");
        }
    }

    /**
     * 有向边
     *
     * @param from  起始节点 id
     * @param to    目标节点 id
     * @param label 边标签（如 "true"/"false"/"default"）,可为空字符串
     * @param style 边样式（如 "dashed"/"dotted"/"solid"）,可为空字符串
     */
    record DotEdge(String from, String to, String label, String style) {
    }
}
