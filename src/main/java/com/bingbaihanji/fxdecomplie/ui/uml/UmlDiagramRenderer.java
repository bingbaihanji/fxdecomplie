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
    private static final double CHAR_W = 7.0;
    private static final double LINE_H = 16.0;
    private static final double NODE_PAD_W = 36;
    private static final double NODE_PAD_H = 22;
    private static final double WRAP_THRESHOLD = 40;
    private static final double MIN_ZOOM = 0.35;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 0.1;
    private static final double EDGE_ARROW = 9;

    private UmlDiagramRenderer() {
        throw new AssertionError("utility class");
    }

    /** 渲染结果：可交互 ScrollPane + 画布 Pane（用于快照导出） */
    public record UmlView(ScrollPane scrollPane, Pane canvas) {
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
            return new UmlView(scroll, canvas);
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
        installZoom(scroll, group, canvas, bounds);
        return new UmlView(scroll, canvas);
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
            int mid = levelClasses.size() / 2;
            for (int i = 0; i < levelClasses.size(); i++) {
                UmlClass c = levelClasses.get(i);
                double w = boxWidth(c);
                double h = boxHeight(c);
                // 层级垂直向下，同层水平居中排列
                double x = MARGIN + (i - mid) * rowStep + (maxW - w) / 2.0;
                double y = MARGIN + (maxLevel - level) * levelStep;
                Node box = createClassBox(c, theme);
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

    private static Node createClassBox(UmlClass cls, UmlTheme theme) {
        VBox box = new VBox();
        box.setStyle("-fx-background-color:" + theme.nodeBackground() + ";"
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
        header.setStyle("-fx-background-color:" + headerColor(cls, theme) + ";"
                + "-fx-background-radius:6 6 0 0;-fx-padding:5 8 5 8;");
        box.getChildren().add(header);

        if (cls.parsed()) {
            VBox members = new VBox();
            members.setStyle("-fx-padding:4 8 6 8;");
            boolean hasFields = false;
            for (UmlField f : cls.fields()) {
                members.getChildren().add(fieldLabel(f, theme));
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
                members.getChildren().add(methodLabel(m, theme));
            }
            box.getChildren().add(members);
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

    private static Label fieldLabel(UmlField f, UmlTheme theme) {
        String text = f.visibility().symbol() + " " + f.name() + ": " + f.type();
        Label label = new Label(text);
        String style = "-fx-text-fill:" + theme.memberText()
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (f.isStatic()) {
            style += "-fx-underline:true;";
        }
        label.setStyle(style);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    private static Label methodLabel(UmlMethod m, UmlTheme theme) {
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
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    // ======================== 尺寸估算 ========================

    private static double boxWidth(UmlClass cls) {
        int longest = 0;
        for (String line : boxTextLines(cls)) {
            longest = Math.max(longest, line.length());
        }
        return Math.max(longest * CHAR_W + NODE_PAD_W, MIN_BOX_W);
    }

    private static double boxHeight(UmlClass cls) {
        int visualLines = 0;
        for (String line : boxTextLines(cls)) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() / (double) WRAP_THRESHOLD));
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

        Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
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

    private static void installZoom(ScrollPane scroll, Group group, Pane canvas, Bounds bounds) {
        final double[] zoom = {1.0};
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown()) {
                return;
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
            canvas.setPrefSize(bounds.width() * next, bounds.height() * next);
            canvas.setMinSize(bounds.width() * next, bounds.height() * next);
            event.consume();
        });
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
