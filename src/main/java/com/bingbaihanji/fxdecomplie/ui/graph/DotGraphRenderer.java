package com.bingbaihanji.fxdecomplie.ui.graph;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.*;

/**
 * 用 JavaFX 原生控件渲染 {@link DotGraphParser} 解析后的图形
 *
 * <p>布局算法：</p>
 * <ol>
 *   <li>拓扑排序计算节点层级（BFS 入度归零）</li>
 *   <li>按层级和 rankdir 方向分配坐标</li>
 *   <li>连线端点自动截断在节点矩形边框上</li>
 *   <li>箭头使用小三角形 Polygon</li>
 * </ol>
 *
 * <p>交互：Ctrl+滚轮缩放（0.35x–2.5x）,ScrollPane 原生拖拽平移</p>
 *
 * @author bingbaihanji
 * @date 2026-06-22
 */
final class DotGraphRenderer {

    // ---- 布局常量 ----
    /** 画布四周留白 */
    private static final double MARGIN = 48;
    /** 相邻层级之间的水平间距（rankdir=LR 时） */
    private static final double LEVEL_GAP = 110;
    /** 同层级相邻节点之间的垂直间距 */
    private static final double ROW_GAP = 34;

    // ---- 节点尺寸估算常量 ----
    /** 节点最小宽度（保证短标签可读） */
    private static final double MIN_NODE_W = 120;
    /** 节点最大宽度（超过则换行） */
    private static final double MAX_NODE_W = 280;
    /** 节点最小高度 */
    private static final double MIN_NODE_H = 42;
    /** 节点最大高度（避免超高） */
    private static final double MAX_NODE_H = 120;
    /** 单字符近似宽度（px）,用于根据标签长度估算节点宽 */
    private static final double CHAR_W = 7.0;
    /** 节点内边距 + 边框宽度（宽度方向） */
    private static final double NODE_PAD_W = 34;
    /** 每行标签高度（px） */
    private static final double LINE_H = 18.0;
    /** 节点内边距 + 边框宽度（高度方向） */
    private static final double NODE_PAD_H = 20;
    /** 标签换行阈值（字符数）,超过则节点内折行 */
    private static final int WRAP_THRESHOLD = 30;
    /**
     * 拓扑排序后未分层节点（环内节点）按声明顺序补层级时,每组的节点数
     * 将相邻未分层节点分成小组,避免全部挤在同一层级造成重叠
     */
    private static final int FALLBACK_PER_LEVEL = 8;

    // ---- 连线常量 ----
    /** 箭头三角形边长（px） */
    private static final double EDGE_ARROW = 9;
    /** 连线颜色 */
    private static final String EDGE_COLOR = "#808080";

    // ---- 缩放常量 ----
    /** 最小缩放倍数 */
    private static final double MIN_ZOOM = 0.35;
    /** 最大缩放倍数 */
    private static final double MAX_ZOOM = 2.5;
    /** 每档滚轮缩放步长 */
    private static final double ZOOM_STEP = 0.1;

    // ---- 文本颜色常量 ----
    /**
     * 相对亮度阈值（BT.601 公式）
     * 背景亮度 > 阈值时用深色文字,否则用浅色文字
     */
    private static final double LUMINANCE_THRESHOLD = 0.58;
    /** 深色文字（用于浅色背景） */
    private static final String DARK_TEXT = "#1e1e1e";
    /** 浅色文字（用于深色背景,缺省） */
    private static final String LIGHT_TEXT = "#e0e0e0";
    /** 缺省填充色 */
    private static final String DEFAULT_FILL = "#3c3c3c";
    /** 画布背景色 */
    private static final String BG_COLOR = "#1e1e1e";

    private DotGraphRenderer() {
        throw new AssertionError("utility class");
    }

    /**
     * 渲染图形为可交互的 ScrollPane
     *
     * @param graph 解析后的 DOT 图形,不可为 null
     * @return 包含图形画布的 ScrollPane,空图形返回提示文本
     */
    static Node create(DotGraphParser.DotGraph graph) {
        if (graph == null || graph.nodes().isEmpty()) {
            return messagePanel(graph == null ? "" : graph.graphLabel());
        }

        Map<String, LayoutNode> layout = layout(graph);
        Bounds bounds = computeBounds(layout);

        // 连线层（置于节点层下方,鼠标穿透避免遮挡节点交互）
        Pane edgeLayer = new Pane();
        edgeLayer.setMouseTransparent(true);
        edgeLayer.setPickOnBounds(false);

        // 节点层
        Pane nodeLayer = new Pane();
        nodeLayer.setPickOnBounds(false);

        for (DotGraphParser.DotEdge edge : graph.edges()) {
            LayoutNode from = layout.get(edge.from());
            LayoutNode to = layout.get(edge.to());
            if (from == null || to == null || from == to) {
                continue;
            }
            drawEdge(edgeLayer, from, to);
        }

        for (LayoutNode ln : layout.values()) {
            nodeLayer.getChildren().add(createNodeView(ln));
        }

        Group group = new Group(edgeLayer, nodeLayer);
        Pane canvas = new Pane(group);
        canvas.setStyle("-fx-background-color:" + BG_COLOR + ";");
        canvas.setMinSize(bounds.width(), bounds.height());
        canvas.setPrefSize(bounds.width(), bounds.height());

        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background:" + BG_COLOR + ";-fx-background-color:" + BG_COLOR + ";");
        installZoom(scroll, group, canvas, bounds);
        return scroll;
    }

    /**
     * 空图形或错误时的占位面板
     *
     * @param text 提示文本,为空时显示"无图形数据"
     * @return 居中显示提示文本的 StackPane
     */
    private static Node messagePanel(String text) {
        String display = text == null || text.isBlank() ? "无图形数据" : text;
        Label label = new Label(display);
        label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
        StackPane pane = new StackPane(label);
        pane.setStyle("-fx-background-color:" + BG_COLOR + ";");
        pane.setAlignment(Pos.CENTER);
        return pane;
    }

    // ======================== 布局算法 ========================

    /**
     * 计算每个节点的屏幕坐标
     *
     * <p>步骤：</p>
     * <ol>
     *   <li>{@link #computeLevels} — 拓扑排序确定各节点层级</li>
     *   <li>按层级分组,同层级节点纵向排列</li>
     *   <li>根据 rankdir 决定主轴方向（LR=水平, BT=垂直倒序）</li>
     * </ol>
     */
    private static Map<String, LayoutNode> layout(DotGraphParser.DotGraph graph) {
        Map<String, Integer> levels = computeLevels(graph);

        // 按层级分组
        Map<Integer, List<DotGraphParser.DotNode>> byLevel = new LinkedHashMap<>();
        for (DotGraphParser.DotNode node : graph.nodes()) {
            byLevel.computeIfAbsent(levels.getOrDefault(node.id(), 0),
                    key -> new ArrayList<>()).add(node);
        }

        // BT 表示自底向上,主轴为垂直；其余按 LR 水平处理
        boolean leftToRight = !"BT".equalsIgnoreCase(graph.rankDir());

        double maxWidth = graph.nodes().stream()
                .mapToDouble(DotGraphRenderer::nodeWidth).max().orElse(MIN_NODE_W);
        double maxHeight = graph.nodes().stream()
                .mapToDouble(DotGraphRenderer::nodeHeight).max().orElse(MIN_NODE_H);
        double levelStep = maxWidth + LEVEL_GAP;
        double rowStep = maxHeight + ROW_GAP;
        int maxLevel = byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        Map<String, LayoutNode> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<DotGraphParser.DotNode>> entry : byLevel.entrySet()) {
            int level = entry.getKey();
            List<DotGraphParser.DotNode> levelNodes = entry.getValue();
            for (int i = 0; i < levelNodes.size(); i++) {
                DotGraphParser.DotNode node = levelNodes.get(i);
                double w = nodeWidth(node);
                double h = nodeHeight(node);
                double x, y;
                if (leftToRight) {
                    x = MARGIN + level * levelStep;
                    y = MARGIN + i * rowStep;
                } else {
                    // BT 模式：层级倒序（高层在上）,节点水平排列
                    x = MARGIN + i * (maxWidth + ROW_GAP);
                    y = MARGIN + (maxLevel - level) * (maxHeight + LEVEL_GAP);
                }
                result.put(node.id(), new LayoutNode(node, x, y, w, h));
            }
        }
        return result;
    }

    /**
     * 拓扑排序 + BFS 计算节点层级
     *
     * <p>入度为 0 的节点为第 0 层每处理一个节点,其后继的入度减 1；
     * 入度归零时加入队列,层级 = 前驱层级 + 1若有环则环内节点按 BFS
     * 实际遍历顺序确定层级</p>
     *
     * <p>若所有节点都有入度（图内无根节点）,取第一个节点为第 0 层起手</p>
     */
    private static Map<String, Integer> computeLevels(DotGraphParser.DotGraph graph) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (DotGraphParser.DotNode node : graph.nodes()) {
            indegree.put(node.id(), 0);
            outgoing.put(node.id(), new ArrayList<>());
        }
        for (DotGraphParser.DotEdge edge : graph.edges()) {
            if (!indegree.containsKey(edge.from()) || !indegree.containsKey(edge.to())) {
                continue;
            }
            outgoing.get(edge.from()).add(edge.to());
            indegree.compute(edge.to(), (key, value) -> value == null ? 1 : value + 1);
        }

        Map<String, Integer> levels = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> queued = new HashSet<>();
        Set<String> processed = new HashSet<>();

        // 入度为 0 的节点作为起始层
        for (DotGraphParser.DotNode node : graph.nodes()) {
            if (indegree.getOrDefault(node.id(), 0) == 0) {
                queue.add(node.id());
                queued.add(node.id());
                levels.put(node.id(), 0);
            }
        }
        // 纯环图场景：取第一个节点作为起点
        if (queue.isEmpty() && !graph.nodes().isEmpty()) {
            String first = graph.nodes().getFirst().id();
            queue.add(first);
            queued.add(first);
            levels.put(first, 0);
        }

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!processed.add(current)) {
                continue; // 跳过重复入队节点
            }
            int nextLevel = levels.getOrDefault(current, 0) + 1;
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (processed.contains(next)) {
                    continue;
                }
                // 取最深层级（多个前驱时走最长路径）
                if (nextLevel > levels.getOrDefault(next, -1)) {
                    levels.put(next, nextLevel);
                }
                // 入度减 1,归零时入队
                int degree = indegree.computeIfPresent(next,
                        (key, value) -> Math.max(0, value - 1));
                if (degree == 0 && queued.add(next)) {
                    queue.add(next);
                }
            }
        }

        // 因环而未分配层级的节点：按声明顺序补层级,每 FALLBACK_PER_LEVEL 个一组
        int fallback = 0;
        for (DotGraphParser.DotNode node : graph.nodes()) {
            levels.putIfAbsent(node.id(), fallback++ / FALLBACK_PER_LEVEL);
        }
        return levels;
    }

    // ======================== 节点渲染 ========================

    /** 创建节点视图：带背景色的 StackPane + 居中 Label */
    private static StackPane createNodeView(LayoutNode layout) {
        DotGraphParser.DotNode dn = layout.node();
        Label label = new Label(dn.label());
        label.setWrapText(true);
        label.setTextAlignment(TextAlignment.CENTER);
        label.setAlignment(Pos.CENTER);
        label.setFont(Font.font("Consolas", 12));
        label.setStyle("-fx-text-fill:" + chooseTextColor(dn.fillColor(), dn.fontColor()) + ";");
        label.setMaxWidth(layout.width() - 18); // 留出内边距

        StackPane box = new StackPane(label);
        box.setLayoutX(layout.x());
        box.setLayoutY(layout.y());
        box.setPrefSize(layout.width(), layout.height());
        box.setMinSize(layout.width(), layout.height());
        box.setMaxSize(layout.width(), layout.height());
        box.setPadding(new Insets(7, 9, 7, 9));
        box.setStyle("-fx-background-color:" + colorOrDefault(dn.fillColor(), DEFAULT_FILL)
                + ";-fx-background-radius:4;-fx-border-radius:4;"
                + "-fx-border-color:#569cd6;-fx-border-width:1;");
        return box;
    }

    // ======================== 连线渲染 ========================

    /**
     * 绘制从 from 到 to 的有向边：直线 + 终点箭头三角形
     * 两端点自动截断到节点矩形边框上,避免线条穿过节点内部
     */
    private static void drawEdge(Pane pane, LayoutNode from, LayoutNode to) {
        Point2D fromCenter = from.center();
        Point2D toCenter = to.center();
        Point2D start = borderPoint(from, toCenter);
        Point2D end = borderPoint(to, fromCenter);

        Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        line.setStroke(Color.web(EDGE_COLOR));
        line.setStrokeWidth(1.2);
        pane.getChildren().add(line);

        // 终点箭头（等腰三角形,底边朝外）
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        Polygon arrow = new Polygon(
                end.getX(), end.getY(),
                end.getX() - EDGE_ARROW * cos + EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin - EDGE_ARROW * 0.45 * cos,
                end.getX() - EDGE_ARROW * cos - EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin + EDGE_ARROW * 0.45 * cos
        );
        arrow.setFill(Color.web(EDGE_COLOR));
        pane.getChildren().add(arrow);
    }

    /**
     * 计算从 node 中心到 target 的连线与 node 矩形边框的交点
     *
     * <p>原理：以 node 中心为原点,向 target 方向做射线,取射线与矩形
     * 的交点中距离中心最近的那个（即先碰到哪条边就在哪里截断）</p>
     */
    private static Point2D borderPoint(LayoutNode node, Point2D target) {
        Point2D center = node.center();
        double dx = target.getX() - center.getX();
        double dy = target.getY() - center.getY();
        if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) {
            return center;
        }
        // 分别计算射线打到左右/上下边框所需的缩放因子,取较小值
        double scaleX = Math.abs(dx) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.width() / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.height() / 2.0) / Math.abs(dy);
        double scale = Math.min(scaleX, scaleY);
        return new Point2D(center.getX() + dx * scale, center.getY() + dy * scale);
    }

    // ======================== 交互 ========================

    /**
     * 安装 Ctrl+滚轮缩放
     *
     * <p>缩放以 Group 的 scaleX/scaleY 实现,同时调整 canvas 的 prefSize
     * 以保持 ScrollPane 滚动条范围与缩放后实际尺寸一致</p>
     */
    private static void installZoom(ScrollPane scroll, Group group,
                                    Pane canvas, Bounds bounds) {
        final double[] zoom = {1.0};
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown()) {
                return; // 不拦截普通滚轮,让 ScrollPane 正常滚动
            }
            double next = Math.clamp(
                    zoom[0] + (event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP),
                    MIN_ZOOM, MAX_ZOOM);
            if (Math.abs(next - zoom[0]) < 0.0001) {
                event.consume();
                return;
            }
            zoom[0] = next;
            group.setScaleX(next);
            group.setScaleY(next);
            // 同步 canvas 尺寸,避免缩放后内容被裁剪
            canvas.setPrefSize(bounds.width() * next, bounds.height() * next);
            canvas.setMinSize(bounds.width() * next, bounds.height() * next);
            event.consume();
        });
    }

    // ======================== 尺寸计算 ========================

    /**
     * 根据标签文本估算节点宽度
     *
     * <p>取最长行字符数 × 单字符宽度 + 内边距,限制在 [{@link #MIN_NODE_W},
     * {@link #MAX_NODE_W}] 范围内label 中的 \\n 导致多行</p>
     */
    private static double nodeWidth(DotGraphParser.DotNode node) {
        String label = node.label() == null ? "" : node.label();
        int longest = 0;
        for (String line : label.split("\\R", -1)) {
            longest = Math.max(longest, line.length());
        }
        return Math.clamp(longest * CHAR_W + NODE_PAD_W, MIN_NODE_W, MAX_NODE_W);
    }

    /**
     * 根据标签文本估算节点高度
     *
     * <p>行数 × 行高 + 内边距,超过 {@link #WRAP_THRESHOLD} 字符的行
     * 按其倍数折算为多行（节点内 wrapText 自动折行）</p>
     */
    private static double nodeHeight(DotGraphParser.DotNode node) {
        String label = node.label() == null ? "" : node.label();
        int visualLines = 0;
        for (String line : label.split("\\R", -1)) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() / (double) WRAP_THRESHOLD));
        }
        return Math.clamp(visualLines * LINE_H + NODE_PAD_H, MIN_NODE_H, MAX_NODE_H);
    }

    // ======================== 颜色工具 ========================

    /**
     * 根据背景色智能选择文字颜色
     *
     * <p>若用户指定了 fontColor 且为合法 CSS 颜色,直接使用；否则根据填充色的
     * BT.601 相对亮度自动选择：亮底黑字 / 暗底白字</p>
     */
    private static String chooseTextColor(String fill, String configured) {
        if (configured != null && !configured.isBlank()) {
            return isValidColor(configured) ? configured : LIGHT_TEXT;
        }
        Color color = parseColor(fill, Color.web(DEFAULT_FILL));
        // BT.601 相对亮度公式：Y = 0.2126R + 0.7152G + 0.0722B
        double luminance = 0.2126 * color.getRed()
                + 0.7152 * color.getGreen()
                + 0.0722 * color.getBlue();
        return luminance > LUMINANCE_THRESHOLD ? DARK_TEXT : LIGHT_TEXT;
    }

    /**
     * 若值为合法 CSS 颜色则返回原值,否则返回缺省值
     *
     * @param value    待检查的颜色字符串
     * @param fallback 非法颜色时的回退值
     * @return 合法的颜色值或回退值
     */
    private static String colorOrDefault(String value, String fallback) {
        return isValidColor(value) ? value : fallback;
    }

    /**
     * 检查字符串是否为合法 CSS 颜色（#rrggbb、命名颜色等）
     *
     * @param value 待检查的颜色字符串
     * @return true 表示可被 {@link Color#web(String)} 解析
     */
    private static boolean isValidColor(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            Color.web(value);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * 安全解析 CSS 颜色,失败返回缺省值
     *
     * @param value    颜色字符串（#rrggbb、命名颜色等）,可为 null
     * @param fallback 解析失败时的回退颜色
     * @return 解析成功的 Color 对象或回退值
     */
    private static Color parseColor(String value, Color fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Color.web(value);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    // ======================== 辅助类型 ========================

    /**
     * 计算所有节点布局后的最大画布尺寸（含四周留白）
     *
     * @param layout 已布局的节点映射（id → LayoutNode）
     * @return 画布边界（宽度 × 高度）
     */
    private static Bounds computeBounds(Map<String, LayoutNode> layout) {
        double maxX = MARGIN * 2;
        double maxY = MARGIN * 2;
        for (LayoutNode ln : layout.values()) {
            maxX = Math.max(maxX, ln.x() + ln.width() + MARGIN);
            maxY = Math.max(maxY, ln.y() + ln.height() + MARGIN);
        }
        return new Bounds(maxX, maxY);
    }

    /**
     * 布局后的节点坐标和尺寸
     *
     * @param node   原始节点数据
     * @param x      左上角 X 坐标
     * @param y      左上角 Y 坐标
     * @param width  渲染宽度
     * @param height 渲染高度
     */
    private record LayoutNode(DotGraphParser.DotNode node, double x, double y,
                              double width, double height) {
        /** 节点矩形中心坐标,用于连线端点计算 */
        Point2D center() {
            return new Point2D(x + width / 2.0, y + height / 2.0);
        }
    }

    /**
     * 画布尺寸（宽度 × 高度）,用于设置 ScrollPane 内容区域大小
     *
     * @param width  画布总宽度（含留白）
     * @param height 画布总高度（含留白）
     */
    private record Bounds(double width, double height) {
    }
}
