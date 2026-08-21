package com.bingbaihanji.fxdecomplie.ui.uml;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纯 JavaFX 渲染 {@link UmlDiagram}：分栏类框（标题 + 包名 + 字段 + 方法）、
 * 按关系类型区分的箭头、Ctrl+滚轮缩放。层级采用入度拓扑分层，继承根在上、子类在下。
 */
public final class UmlDiagramRenderer {

    private static final double MARGIN = 48;
    private static final double LEVEL_GAP = 60;
    private static final double ROW_GAP = 40;
    private static final double MIN_BOX_W = 150;
    private static final double MIN_BOX_H = 60;
    private static final double MAX_NODE_W = 260;
    private static final double CHAR_W = 7.0;
    private static final double LINE_H = 16.0;
    private static final double NODE_PAD_W = 36;
    private static final double NODE_PAD_H = 22;
    private static final double MIN_ZOOM = 0.02;
    private static final double MAX_ZOOM = 3.0;
    private static final double ZOOM_STEP = 0.1;
    private static final double EDGE_ARROW = 9;

    private UmlDiagramRenderer() {
        throw new AssertionError("utility class");
    }

    /** 渲染结果：可交互 ScrollPane + 画布 Pane（用于快照导出）+ 适配窗口回调 */
    public record UmlView(ScrollPane scrollPane, Pane canvas, Runnable fitToViewport) {
    }

    public static UmlView create(UmlDiagram diagram, UmlTheme theme) {
        if (diagram == null || diagram.classes().isEmpty()) {
            Pane canvas = new Pane();
            canvas.setStyle("-fx-background-color:" + theme.background() + ";");
            Label label = new Label("无 UML 数据");
            label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
            StackPane center = new StackPane(label);
            center.setStyle("-fx-background-color:" + theme.background() + ";");
            ScrollPane scroll = new ScrollPane(center);
            scroll.setPannable(true);
            return new UmlView(scroll, canvas, () -> {});
        }

        Map<String, LayoutBox> layout = layout(diagram, theme);
        Bounds bounds = computeBounds(layout);

        Pane edgeLayer = new Pane();
        edgeLayer.setMouseTransparent(true);
        edgeLayer.setPickOnBounds(false);

        Pane nodeLayer = new Pane();
        nodeLayer.setPickOnBounds(false);

        for (UmlRelation r : diagram.relations()) {
            LayoutBox from = layout.get(r.from());
            LayoutBox to = layout.get(r.to());
            if (from == null || to == null || from == to) {
                continue;
            }
            drawRelation(edgeLayer, from, to, r, theme);
        }
        for (LayoutBox lb : layout.values()) {
            nodeLayer.getChildren().add(lb.box());
        }

        Group group = new Group(edgeLayer, nodeLayer);
        Pane canvas = new Pane(group);
        canvas.setStyle("-fx-background-color:" + theme.background() + ";");
        canvas.setMinSize(bounds.width(), bounds.height());
        canvas.setPrefSize(bounds.width(), bounds.height());

        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background:" + theme.background() + ";-fx-background-color:" + theme.background() + ";");
        final double[] zoom = {1.0};
        final boolean[] fitted = {false};
        installZoom(scroll, group, canvas, bounds, zoom, fitted);
        Runnable fit = () -> fitToViewport(scroll, group, canvas, bounds, zoom);
        return new UmlView(scroll, canvas, fit);
    }

    // ======================== 布局 ========================

    private static Map<String, LayoutBox> layout(UmlDiagram diagram, UmlTheme theme) {
        Map<String, Integer> levels = computeLevels(diagram);
        Map<Integer, List<UmlClass>> byLevel = new LinkedHashMap<>();
        for (UmlClass c : diagram.classes()) {
            byLevel.computeIfAbsent(levels.getOrDefault(c.internalName(), 0),
                    key -> new ArrayList<>()).add(c);
        }
        double maxW = 0;
        double maxH = 0;
        for (UmlClass c : diagram.classes()) {
            maxW = Math.max(maxW, boxWidth(c));
            maxH = Math.max(maxH, boxHeight(c));
        }
        double levelStep = maxH + LEVEL_GAP;
        double rowStep = maxW + ROW_GAP;
        int maxLevel = byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        Map<String, LayoutBox> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<UmlClass>> entry : byLevel.entrySet()) {
            int level = entry.getKey();
            List<UmlClass> levelClasses = entry.getValue();
            double mid = (levelClasses.size() - 1) / 2.0;
            for (int i = 0; i < levelClasses.size(); i++) {
                UmlClass c = levelClasses.get(i);
                double w = boxWidth(c);
                double h = boxHeight(c);
                // 层级垂直向下，同层水平居中排列
                double x = MARGIN + (i - mid) * rowStep + (maxW - w) / 2.0;
                double y = MARGIN + (maxLevel - level) * levelStep;
                Node box = createClassBox(c, theme, w);
                box.setLayoutX(x);
                box.setLayoutY(y);
                if (box instanceof Region region) {
                    region.setPrefSize(w, h);
                    region.setMinSize(w, h);
                    region.setMaxSize(w, h);
                }
                result.put(c.internalName(), new LayoutBox(box, x, y, w, h));
            }
        }
        // 平移:确保最左节点不越界(左右各留 MARGIN),否则被视口/快照裁剪
        double minX = Double.POSITIVE_INFINITY;
        for (LayoutBox lb : result.values()) {
            minX = Math.min(minX, lb.x());
        }
        double shift = minX < MARGIN ? MARGIN - minX : 0;
        if (shift > 0) {
            Map<String, LayoutBox> shifted = new LinkedHashMap<>();
            for (Map.Entry<String, LayoutBox> e : result.entrySet()) {
                LayoutBox lb = e.getValue();
                lb.box().setLayoutX(lb.x() + shift);
                shifted.put(e.getKey(), new LayoutBox(lb.box(), lb.x() + shift, lb.y(), lb.width(), lb.height()));
            }
            result = shifted;
        }
        return result;
    }

    /** 入度拓扑分层：入度为 0 的节点（叶子/无被引用的类）在 level 0；layout 中 level 越高越靠上，使父类位于画布上方 */
    private static Map<String, Integer> computeLevels(UmlDiagram diagram) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (UmlClass c : diagram.classes()) {
            indegree.put(c.internalName(), 0);
            outgoing.put(c.internalName(), new ArrayList<>());
        }
        for (UmlRelation r : diagram.relations()) {
            if (!indegree.containsKey(r.from()) || !indegree.containsKey(r.to())) {
                continue;
            }
            outgoing.get(r.from()).add(r.to());
            indegree.compute(r.to(), (k, v) -> v == null ? 1 : v + 1);
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> queued = new HashSet<>();
        Set<String> processed = new HashSet<>();
        for (UmlClass c : diagram.classes()) {
            if (indegree.getOrDefault(c.internalName(), 0) == 0) {
                queue.add(c.internalName());
                queued.add(c.internalName());
                levels.put(c.internalName(), 0);
            }
        }
        if (queue.isEmpty() && !diagram.classes().isEmpty()) {
            String first = diagram.classes().getFirst().internalName();
            queue.add(first);
            queued.add(first);
            levels.put(first, 0);
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!processed.add(current)) {
                continue;
            }
            int nextLevel = levels.getOrDefault(current, 0) + 1;
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (processed.contains(next)) {
                    continue;
                }
                if (nextLevel > levels.getOrDefault(next, -1)) {
                    levels.put(next, nextLevel);
                }
                int degree = indegree.computeIfPresent(next, (k, v) -> Math.max(0, v - 1));
                if (degree == 0 && queued.add(next)) {
                    queue.add(next);
                }
            }
        }
        int fallback = 0;
        for (UmlClass c : diagram.classes()) {
            levels.putIfAbsent(c.internalName(), fallback++ / 8);
        }
        return levels;
    }

    // ======================== 类框渲染 ========================

    private static Node createClassBox(UmlClass cls, UmlTheme theme, double width) {
        VBox box = new VBox();
        String boxBg = cls.parsed() ? theme.nodeBackground() : theme.markerBackground();
        box.setStyle("-fx-background-color:" + boxBg + ";"
                + "-fx-border-color:" + theme.nodeBorder() + ";"
                + "-fx-border-width:1;-fx-background-radius:6;-fx-border-radius:6;");

        Label title = new Label(cls.simpleName());
        title.setStyle("-fx-text-fill:" + theme.titleText() + ";-fx-font-size:13px;-fx-font-weight:bold;");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        if (cls.packageName() != null && !cls.packageName().isEmpty()) {
            Label pkg = new Label(cls.packageName());
            pkg.setStyle("-fx-text-fill:" + theme.packageText() + ";-fx-font-size:10px;");
            pkg.setMaxWidth(Double.MAX_VALUE);
            pkg.setAlignment(Pos.CENTER);
            header.getChildren().add(pkg);
        }
        String headerBg = cls.parsed() ? headerColor(cls, theme) : theme.markerBackground();
        header.setStyle("-fx-background-color:" + headerBg + ";"
                + "-fx-background-radius:6 6 0 0;-fx-padding:5 8 5 8;");
        box.getChildren().add(header);

        if (cls.parsed()) {
            VBox members = new VBox();
            members.setStyle("-fx-padding:4 8 6 8;");
            boolean hasFields = false;
            for (UmlField f : cls.fields()) {
                members.getChildren().add(fieldLabel(f, theme, width));
                hasFields = true;
            }
            if (hasFields && !cls.methods().isEmpty()) {
                Region sep = new Region();
                sep.setMaxWidth(Double.MAX_VALUE);
                sep.setStyle("-fx-background-color:" + theme.nodeBorder()
                        + ";-fx-pref-height:1;-fx-min-height:1;-fx-max-height:1;");
                members.getChildren().add(sep);
            }
            for (UmlMethod m : cls.methods()) {
                members.getChildren().add(methodLabel(m, theme, width));
            }
            box.getChildren().add(members);
        }
        if (width > 0) {
            Rectangle clip = new Rectangle(width, boxHeight(cls));
            box.setClip(clip);
        }
        return box;
    }

    private static String headerColor(UmlClass cls, UmlTheme theme) {
        return switch (cls.kind()) {
            case CLASS -> theme.classHeader();
            case INTERFACE -> theme.interfaceHeader();
            case ENUM -> theme.enumHeader();
            case ANNOTATION -> theme.annotationHeader();
        };
    }

    private static Label fieldLabel(UmlField f, UmlTheme theme, double boxWidth) {
        String text = f.visibility().symbol() + " " + f.name() + ": " + f.type();
        Label label = new Label(text);
        String style = "-fx-text-fill:" + theme.memberText()
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (f.isStatic()) {
            style += "-fx-underline:true;";
        }
        label.setStyle(style);
        label.setWrapText(true);
        label.setMaxWidth(boxWidth - 18);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    private static Label methodLabel(UmlMethod m, UmlTheme theme, double boxWidth) {
        String ret = m.isConstructor() ? "" : ": " + m.returnType();
        String text = m.visibility().symbol() + " " + m.name() + "("
                + String.join(", ", m.params()) + ")" + ret;
        Label label = new Label(text);
        String style = "-fx-text-fill:" + theme.memberText()
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (m.isAbstract()) {
            style += "-fx-font-style:italic;";
        }
        if (m.isStatic()) {
            style += "-fx-underline:true;";
        }
        label.setStyle(style);
        label.setWrapText(true);
        label.setMaxWidth(boxWidth - 18);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    // ======================== 尺寸估算 ========================

    private static double boxWidth(UmlClass cls) {
        int longest = 0;
        for (String line : boxTextLines(cls)) {
            longest = Math.max(longest, line.length());
        }
        return Math.clamp(longest * CHAR_W + NODE_PAD_W, MIN_BOX_W, MAX_NODE_W);
    }

    private static double boxHeight(UmlClass cls) {
        double w = boxWidth(cls);
        double usable = Math.max(w - NODE_PAD_W, MIN_BOX_W - NODE_PAD_W);
        int visualLines = 0;
        for (String line : boxTextLines(cls)) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() * CHAR_W / usable));
        }
        return Math.max(visualLines * LINE_H + NODE_PAD_H, MIN_BOX_H);
    }

    private static List<String> boxTextLines(UmlClass cls) {
        List<String> lines = new ArrayList<>();
        lines.add(cls.simpleName());
        if (cls.packageName() != null && !cls.packageName().isEmpty()) {
            lines.add(cls.packageName());
        }
        if (cls.parsed()) {
            for (UmlField f : cls.fields()) {
                lines.add(f.visibility().symbol() + " " + f.name() + ": " + f.type());
            }
            if (!cls.fields().isEmpty() && !cls.methods().isEmpty()) {
                lines.add("-");
            }
            for (UmlMethod m : cls.methods()) {
                lines.add(m.visibility().symbol() + " " + m.name() + "("
                        + String.join(", ", m.params()) + ")"
                        + (m.isConstructor() ? "" : ": " + m.returnType()));
            }
        }
        return lines;
    }

    // ======================== 关系渲染 ========================

    private static void drawRelation(Pane pane, LayoutBox from, LayoutBox to,
                                     UmlRelation r, UmlTheme theme) {
        Point2D fromCenter = from.center();
        Point2D toCenter = to.center();
        Point2D start = borderPoint(from, toCenter);
        Point2D end = borderPoint(to, fromCenter);

        String color = switch (r.type()) {
            case GENERALIZATION -> theme.generalizationColor();
            case REALIZATION -> theme.realizationColor();
            case ASSOCIATION -> theme.associationColor();
            case DEPENDENCY -> theme.dependencyColor();
        };
        boolean dashed = r.type() == RelationType.REALIZATION || r.type() == RelationType.DEPENDENCY;

        Point2D lineEnd = end;
        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
            lineEnd = new Point2D(end.getX() - 11 * Math.cos(angle), end.getY() - 11 * Math.sin(angle));
        }
        Line line = new Line(start.getX(), start.getY(), lineEnd.getX(), lineEnd.getY());
        line.setStroke(Color.web(color));
        line.setStrokeWidth(1.2);
        if (dashed) {
            line.getStrokeDashArray().addAll(8.0, 4.0);
        }
        pane.getChildren().add(line);

        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            hollowArrow(pane, start, end, color);
        } else if (r.type() == RelationType.DEPENDENCY) {
            filledArrow(pane, start, end, color);
        }

        if (r.toCardinality() != null && !r.toCardinality().isBlank()) {
            double midX = (start.getX() + end.getX()) / 2.0;
            double midY = (start.getY() + end.getY()) / 2.0;
            Label card = new Label(r.toCardinality());
            card.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;");
            card.setLayoutX(midX + 4);
            card.setLayoutY(midY - 10);
            pane.getChildren().add(card);
        }
    }

    /** 空心三角（继承/实现箭头） */
    private static void hollowArrow(Pane pane, Point2D start, Point2D end, String color) {
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double arrow = 11;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Polygon tri = new Polygon(
                end.getX(), end.getY(),
                end.getX() - arrow * cos + arrow * 0.5 * sin,
                end.getY() - arrow * sin - arrow * 0.5 * cos,
                end.getX() - arrow * cos - arrow * 0.5 * sin,
                end.getY() - arrow * sin + arrow * 0.5 * cos);
        tri.setFill(Color.TRANSPARENT);
        tri.setStroke(Color.web(color));
        tri.setStrokeWidth(1.2);
        pane.getChildren().add(tri);
    }

    /** 实心小三角（依赖箭头） */
    private static void filledArrow(Pane pane, Point2D start, Point2D end, String color) {
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Polygon tri = new Polygon(
                end.getX(), end.getY(),
                end.getX() - EDGE_ARROW * cos + EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin - EDGE_ARROW * 0.45 * cos,
                end.getX() - EDGE_ARROW * cos - EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin + EDGE_ARROW * 0.45 * cos);
        tri.setFill(Color.web(color));
        pane.getChildren().add(tri);
    }

    /** 射线与节点矩形边框的交点（连线端点截断） */
    private static Point2D borderPoint(LayoutBox node, Point2D target) {
        Point2D center = node.center();
        double dx = target.getX() - center.getX();
        double dy = target.getY() - center.getY();
        if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) {
            return center;
        }
        double scaleX = Math.abs(dx) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.width() / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.height() / 2.0) / Math.abs(dy);
        double scale = Math.min(scaleX, scaleY);
        return new Point2D(center.getX() + dx * scale, center.getY() + dy * scale);
    }

    // ======================== 交互 ========================

    private static void installZoom(ScrollPane scroll, Group group, Pane canvas, Bounds bounds,
                                    double[] zoom, boolean[] fitted) {
        // 视口首次就绪后自动适配整图（事件驱动，布局完成时必然触发）
        scroll.viewportBoundsProperty().addListener((obs, oldV, viewport) -> {
            if (fitted[0]) {
                return;
            }
            if (viewport.getWidth() <= 0 || viewport.getHeight() <= 0) {
                return;
            }
            fitted[0] = true;
            fitToViewport(scroll, group, canvas, bounds, zoom);
        });

        // Ctrl/⌘+滚轮：指针居中缩放
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown()) {
                return; // 不拦截普通滚轮，让 ScrollPane 正常滚动
            }
            double next = Math.clamp(
                    zoom[0] + (event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP),
                    MIN_ZOOM, MAX_ZOOM);
            if (Math.abs(next - zoom[0]) < 0.0001) {
                event.consume();
                return;
            }
            zoomAround(scroll, group, canvas, bounds, zoom, next, event.getX(), event.getY());
            event.consume();
        });
    }

    /** 适配窗口：缩放到整图可见并居中 */
    private static void fitToViewport(ScrollPane scroll, Group group, Pane canvas, Bounds bounds,
                                      double[] zoom) {
        double vw = scroll.getViewportBounds().getWidth();
        double vh = scroll.getViewportBounds().getHeight();
        if (vw <= 0 || vh <= 0 || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        double fit = Math.min(vw / bounds.width(), vh / bounds.height());
        if (!Double.isFinite(fit) || fit <= 0) {
            return;
        }
        fit = Math.min(fit, 1.0); // 初始不放大
        fit = Math.max(fit, MIN_ZOOM);
        applyZoom(group, canvas, bounds, zoom, fit);
        scroll.setHvalue(0.5);
        scroll.setVvalue(0.5);
    }

    /** 设置缩放并同步画布尺寸 */
    private static void applyZoom(Group group, Pane canvas, Bounds bounds, double[] zoom, double next) {
        if (!Double.isFinite(next) || next <= 0) {
            return;
        }
        zoom[0] = next;
        group.setScaleX(next);
        group.setScaleY(next);
        canvas.setPrefSize(bounds.width() * next, bounds.height() * next);
        canvas.setMinSize(bounds.width() * next, bounds.height() * next);
    }

    /** 指针居中缩放：保持鼠标下的内容点不动 */
    private static void zoomAround(ScrollPane scroll, Group group, Pane canvas, Bounds bounds,
                                   double[] zoom, double next, double mouseX, double mouseY) {
        double vw = scroll.getViewportBounds().getWidth();
        double vh = scroll.getViewportBounds().getHeight();
        double old = zoom[0];
        double cw = bounds.width() * old;
        double ch = bounds.height() * old;
        double hscroll = scroll.getHvalue() * Math.max(0, cw - vw);
        double vscroll = scroll.getVvalue() * Math.max(0, ch - vh);
        // 鼠标指向的未缩放内容坐标
        double contentX = (hscroll + mouseX) / old;
        double contentY = (vscroll + mouseY) / old;

        applyZoom(group, canvas, bounds, zoom, next);

        double nw = bounds.width() * next;
        double nh = bounds.height() * next;
        if (nw > vw) {
            double nhscroll = contentX * next - mouseX;
            scroll.setHvalue(Math.clamp(nhscroll / (nw - vw), 0, 1));
        }
        if (nh > vh) {
            double nvscroll = contentY * next - mouseY;
            scroll.setVvalue(Math.clamp(nvscroll / (nh - vh), 0, 1));
        }
    }

    private static Bounds computeBounds(Map<String, LayoutBox> layout) {
        double maxX = MARGIN * 2;
        double maxY = MARGIN * 2;
        for (LayoutBox lb : layout.values()) {
            maxX = Math.max(maxX, lb.x() + lb.width() + MARGIN);
            maxY = Math.max(maxY, lb.y() + lb.height() + MARGIN);
        }
        return new Bounds(maxX, maxY);
    }

    private record LayoutBox(Node box, double x, double y, double width, double height) {
        Point2D center() {
            return new Point2D(x + width / 2.0, y + height / 2.0);
        }
    }

    private record Bounds(double width, double height) {
    }
}
