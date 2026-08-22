package com.bingbaihanji.fxdecomplie.ui.uml;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;
import javafx.scene.transform.Scale;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纯 JavaFX 渲染 {@link UmlDiagram}:分栏类框(标题 + 包名 + 字段 + 方法),
 * 按关系类型区分的箭头,Ctrl+滚轮缩放.层级采用入度拓扑分层,继承根在上,子类在下.
 */
public final class UmlDiagramRenderer {

    private static final double MARGIN = 48;

    private static final double LEVEL_GAP = 72;

    private static final double ROW_GAP = 34;

    private static final double MIN_BOX_W = 190;

    private static final double MIN_BOX_H = 60;

    private static final double MAX_NODE_W = 360;

    private static final double MAX_NODE_H = 380;

    private static final double CHAR_W = 7.0;

    private static final double LINE_H = 18.0;

    private static final double NODE_PAD_W = 48;

    private static final double NODE_PAD_H = 36;

    private static final double MIN_ZOOM = 0.2;

    private static final double MAX_ZOOM = 3.0;

    private static final double ZOOM_STEP = 0.1;

    private static final double EDGE_ARROW = 9;

    private static final int MAX_COLS = 7;

    private static final int MAX_VISIBLE_FIELDS = 10;

    private static final int MAX_VISIBLE_METHODS = 16;

    private static final int MAX_MEMBER_CHARS = 96;

    private UmlDiagramRenderer() {
        throw new AssertionError("utility class");
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
            return new UmlView(scroll, canvas, () -> {}, () -> 1.0, () -> 0, () -> 0);
        }

        Map<String, LayoutBox> layout = layout(diagram, theme);
        double contentW = 0;
        double contentH = 0;
        for (LayoutBox lb : layout.values()) {
            contentW = Math.max(contentW, lb.x() + lb.width());
            contentH = Math.max(contentH, lb.y() + lb.height());
        }
        contentW += MARGIN;
        contentH += MARGIN;

        Pane edgeLayer = new Pane();
        edgeLayer.setMouseTransparent(true);
        edgeLayer.setPickOnBounds(false);
        Pane nodeLayer = new Pane();
        nodeLayer.setPickOnBounds(false);
        int relationIndex = 0;
        for (UmlRelation r : diagram.relations()) {
            LayoutBox from = layout.get(r.from());
            LayoutBox to = layout.get(r.to());
            if (from == null || to == null || from == to) {
                continue;
            }
            drawRelation(edgeLayer, from, to, r, theme, relationIndex++);
        }
        for (LayoutBox lb : layout.values()) {
            nodeLayer.getChildren().add(lb.box());
        }

        Group group = new Group(edgeLayer, nodeLayer);
        Pane canvas = new Pane(group);
        canvas.setStyle("-fx-background-color:" + theme.background() + ";");
        canvas.setMinSize(contentW, contentH);
        canvas.setPrefSize(contentW, contentH);

        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background:" + theme.background() + ";-fx-background-color:" + theme.background() + ";");

        ZoomController controller = new ZoomController(scroll, group, canvas, contentW, contentH);
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown()) {
                return;
            }
            controller.zoomBy(event.getDeltaY(), event.getX(), event.getY());
            event.consume();
        });
        final boolean[] fitted = {false};
        scroll.viewportBoundsProperty().addListener((obs, oldV, vp) -> {
            if (fitted[0]) {
                return;
            }
            if (vp.getWidth() <= 0 || vp.getHeight() <= 0) {
                return;
            }
            fitted[0] = true;
            controller.fitToWindow();
        });
        Runnable fit = controller::fitToWindow;
        return new UmlView(scroll, canvas, fit,
                controller::currentZoom, controller::contentWidth, controller::contentHeight);
    }

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
        double rowStep = maxW + ROW_GAP;

        Map<String, LayoutBox> raw = new LinkedHashMap<>();
        double yCursor = MARGIN;
        int maxLevel = byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int level = maxLevel; level >= 0; level--) {
            List<UmlClass> levelClasses = byLevel.get(level);
            if (levelClasses == null) {
                continue;
            }
            int cols = columnsFor(levelClasses.size());
            int rows = (levelClasses.size() + cols - 1) / cols;
            double levelOffset = (MAX_COLS - cols) * rowStep / 2.0;
            double levelTop = yCursor;
            for (int i = 0; i < levelClasses.size(); i++) {
                UmlClass c = levelClasses.get(i);
                double w = boxWidth(c);
                double h = boxHeight(c);
                int col = i % cols;
                int row = i / cols;
                double x = MARGIN + levelOffset + col * rowStep + (maxW - w) / 2.0;
                double y = levelTop + row * (maxH + ROW_GAP);
                Node box = createClassBox(c, theme, w);
                box.setLayoutX(x);
                box.setLayoutY(y);
                if (box instanceof Region region) {
                    region.setPrefSize(w, h);
                    region.setMinSize(w, h);
                    region.setMaxSize(w, h);
                }
                raw.put(c.internalName(), new LayoutBox(box, x, y, w, h));
            }
            yCursor = levelTop + rows * (maxH + ROW_GAP) + LEVEL_GAP;
        }

        // 整体平移:内容对齐左上角(各留 MARGIN),消除负坐标与右侧大量空白
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        for (LayoutBox lb : raw.values()) {
            minX = Math.min(minX, lb.x());
            minY = Math.min(minY, lb.y());
        }
        double shiftX = minX - MARGIN;
        double shiftY = minY - MARGIN;
        Map<String, LayoutBox> result = new LinkedHashMap<>();
        for (Map.Entry<String, LayoutBox> e : raw.entrySet()) {
            LayoutBox lb = e.getValue();
            double nx = lb.x() - shiftX;
            double ny = lb.y() - shiftY;
            lb.box().setLayoutX(nx);
            lb.box().setLayoutY(ny);
            result.put(e.getKey(), new LayoutBox(lb.box(), nx, ny, lb.width(), lb.height()));
        }
        return result;
    }

    // ======================== 布局 ========================

    private static int columnsFor(int count) {
        if (count <= 1) {
            return 1;
        }
        if (count <= 4) {
            return count;
        }
        return Math.min(MAX_COLS, (int) Math.ceil(Math.sqrt(count * 1.8)));
    }

    /** 入度拓扑分层:入度为 0 的节点(叶子/无被引用的类)在 level 0;layout 中 level 越高越靠上,使父类位于画布上方 */
    private static Map<String, Integer> computeLevels(UmlDiagram diagram) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (UmlClass c : diagram.classes()) {
            indegree.put(c.internalName(), 0);
            outgoing.put(c.internalName(), new ArrayList<>());
        }
        for (UmlRelation r : diagram.relations()) {
            if (r.type() != RelationType.GENERALIZATION && r.type() != RelationType.REALIZATION) {
                continue;
            }
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

    private static Node createClassBox(UmlClass cls, UmlTheme theme, double width) {
        VBox box = new VBox();
        String boxBg = cls.parsed() ? theme.nodeBackground() : theme.markerBackground();
        box.setStyle("-fx-background-color:" + boxBg + ";"
                + "-fx-border-color:" + theme.nodeBorder() + ";"
                + "-fx-border-width:1;-fx-background-radius:2;-fx-border-radius:2;");

        Label title = new Label(titleText(cls));
        String titleStyle = "-fx-text-fill:" + theme.titleText() + ";-fx-font-size:13px;-fx-font-weight:bold;";
        if (cls.kind() == ClassKind.CLASS && cls.isAbstract()) {
            titleStyle += "-fx-font-style:italic;";
        }
        title.setStyle(titleStyle);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        Region typeIcon = typeIcon(cls);
        HBox titleRow = new HBox(6, typeIcon, title);
        Label kindBadge = kindBadge(cls, theme);
        if (kindBadge != null) {
            titleRow.getChildren().add(kindBadge);
        }
        titleRow.setAlignment(Pos.CENTER);
        titleRow.setMaxWidth(Double.MAX_VALUE);

        VBox header = new VBox(titleRow);
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
                + "-fx-background-radius:2 2 0 0;-fx-padding:5 8 5 8;");
        box.getChildren().add(header);

        if (cls.parsed()) {
            VBox members = new VBox();
            members.setStyle("-fx-padding:4 0 6 0;");
            boolean hasFields = false;
            int fieldCount = 0;
            for (UmlField f : cls.fields()) {
                if (fieldCount >= MAX_VISIBLE_FIELDS) {
                    break;
                }
                members.getChildren().add(fieldRow(f, theme, width));
                hasFields = true;
                fieldCount++;
            }
            if (hasFields && !cls.methods().isEmpty()) {
                Region sep = new Region();
                sep.setMaxWidth(Double.MAX_VALUE);
                sep.setStyle("-fx-background-color:" + theme.nodeBorder()
                        + ";-fx-opacity:0.75;-fx-pref-height:1;-fx-min-height:1;-fx-max-height:1;"
                        + "-fx-padding:0 0 0 0;");
                members.getChildren().add(sep);
            }
            int methodCount = 0;
            for (UmlMethod m : cls.methods()) {
                if (methodCount >= MAX_VISIBLE_METHODS) {
                    break;
                }
                members.getChildren().add(methodRow(m, theme, width));
                methodCount++;
            }
            int hiddenFields = Math.max(0, cls.fields().size() - MAX_VISIBLE_FIELDS);
            int hiddenMethods = Math.max(0, cls.methods().size() - MAX_VISIBLE_METHODS);
            if (hiddenFields > 0 || hiddenMethods > 0) {
                String text = "... +" + hiddenFields + " fields, +" + hiddenMethods + " methods";
                Label more = new Label(text);
                more.setStyle("-fx-text-fill:" + theme.packageText()
                        + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;"
                        + "-fx-padding:2 8 0 28;");
                more.setMaxWidth(width - 18);
                members.getChildren().add(more);
            }
            box.getChildren().add(members);
        }
        if (width > 0) {
            Rectangle clip = new Rectangle(width, boxHeight(cls));
            box.setClip(clip);
        }
        return box;
    }

    // ======================== 类框渲染 ========================

    private static String titleText(UmlClass cls) {
        return switch (cls.kind()) {
            case INTERFACE -> "I " + cls.simpleName();
            case ENUM -> "E " + cls.simpleName();
            case ANNOTATION -> "@ " + cls.simpleName();
            case RECORD -> "R " + cls.simpleName();
            case CLASS -> cls.isAbstract() ? "A " + cls.simpleName() : cls.simpleName();
        };
    }

    private static Region typeIcon(UmlClass cls) {
        Region icon = new Region();
        icon.setMinSize(10, 10);
        icon.setPrefSize(10, 10);
        icon.setMaxSize(10, 10);
        String color = switch (cls.kind()) {
            case CLASS -> cls.isAbstract() ? "#8AB4F8" : "#5DADE2";
            case INTERFACE -> "#A7C957";
            case ENUM -> "#E6B35A";
            case ANNOTATION -> "#B388FF";
            case RECORD -> "#58D1C9";
        };
        String radius = cls.kind() == ClassKind.INTERFACE ? "5" : "2";
        icon.setStyle("-fx-background-color:" + color + ";-fx-background-radius:" + radius + ";"
                + "-fx-border-color:rgba(255,255,255,0.38);-fx-border-radius:" + radius + ";"
                + "-fx-border-width:1;");
        return icon;
    }

    private static String kindBadgeText(UmlClass cls) {
        return switch (cls.kind()) {
            case RECORD -> "record";
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case ANNOTATION -> "annotation";
            case CLASS -> cls.isAbstract() ? "abstract" : cls.isFinal() ? "final class" : "class";
        };
    }

    private static Label kindBadge(UmlClass cls, UmlTheme theme) {
        String text = kindBadgeText(cls);
        if (text.isEmpty()) {
            return null;
        }
        Label badge = new Label(text);
        String border = theme.dark() ? "rgba(255,255,255,0.24)" : "rgba(0,0,0,0.18)";
        String bg = theme.dark() ? "rgba(255,255,255,0.10)" : "rgba(255,255,255,0.65)";
        badge.setStyle("-fx-text-fill:" + theme.packageText() + ";"
                + "-fx-font-size:9px;-fx-font-family:'Consolas',monospace;"
                + "-fx-padding:1 4 1 4;-fx-background-color:" + bg + ";"
                + "-fx-background-radius:2;-fx-border-color:" + border + ";"
                + "-fx-border-radius:2;-fx-border-width:1;");
        return badge;
    }

    private static String headerColor(UmlClass cls, UmlTheme theme) {
        return switch (cls.kind()) {
            case CLASS -> cls.isAbstract() ? theme.abstractClassHeader() : theme.classHeader();
            case INTERFACE -> theme.interfaceHeader();
            case ENUM -> theme.enumHeader();
            case ANNOTATION -> theme.annotationHeader();
            case RECORD -> theme.recordHeader();
        };
    }

    private static Node fieldRow(UmlField f, UmlTheme theme, double boxWidth) {
        return memberRow(fieldNameText(f), f.type(), fieldText(f), f.visibility(),
                theme, boxWidth, false, f.isStatic());
    }

    private static Node methodRow(UmlMethod m, UmlTheme theme, double boxWidth) {
        String type = m.isConstructor() ? "" : m.returnType();
        return memberRow(methodNameText(m), type, methodText(m), m.visibility(),
                theme, boxWidth, true, m.isStatic(), m.isAbstract());
    }

    private static Node memberRow(String nameText, String typeText, String fullText,
                                  Visibility visibility, UmlTheme theme, double boxWidth,
                                  boolean method, boolean isStatic) {
        return memberRow(nameText, typeText, fullText, visibility, theme, boxWidth, method, isStatic, false);
    }

    private static Node memberRow(String nameText, String typeText, String fullText,
                                  Visibility visibility, UmlTheme theme, double boxWidth,
                                  boolean method, boolean isStatic, boolean isAbstract) {
        Region icon = memberIcon(method, visibility, theme);
        Label visibilityLabel = new Label(String.valueOf(visibility.symbol()));
        visibilityLabel.setStyle("-fx-text-fill:" + visibilityColor(visibility, theme)
                + ";-fx-font-size:10px;-fx-font-family:'Consolas',monospace;");
        visibilityLabel.setMinWidth(10);
        visibilityLabel.setAlignment(Pos.CENTER);

        Label name = new Label(ellipsize(nameText, MAX_MEMBER_CHARS));
        String nameStyle = "-fx-text-fill:" + (method ? methodNameColor(theme) : fieldNameColor(theme))
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (isAbstract) {
            nameStyle += "-fx-font-style:italic;";
        }
        if (isStatic) {
            nameStyle += "-fx-underline:true;";
        }
        name.setStyle(nameStyle);
        name.setMinWidth(0);
        name.setMaxWidth(Double.MAX_VALUE);
        name.setTextAlignment(TextAlignment.LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);

        Label type = new Label(ellipsize(typeText, 32));
        type.setStyle("-fx-text-fill:" + typeColor(theme)
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;");
        type.setAlignment(Pos.CENTER_RIGHT);
        type.setMinWidth(Math.min(104, Math.max(52, boxWidth * 0.28)));
        type.setMaxWidth(Math.min(132, Math.max(64, boxWidth * 0.36)));

        HBox row = new HBox(4, icon, visibilityLabel, name, type);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinHeight(LINE_H);
        row.setPrefHeight(LINE_H);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setStyle("-fx-padding:0 8 0 7;");
        if (!ellipsize(fullText, MAX_MEMBER_CHARS).equals(fullText)
                || (typeText != null && type.getText() != null && !type.getText().equals(typeText))) {
            Tooltip.install(row, new Tooltip(fullText));
        }
        return row;
    }

    private static Region memberIcon(boolean method, Visibility visibility, UmlTheme theme) {
        Region icon = new Region();
        icon.setMinSize(9, 9);
        icon.setPrefSize(9, 9);
        icon.setMaxSize(9, 9);
        String color = method ? methodIconColor(theme) : fieldIconColor(theme);
        String shape = method ? "5" : "2";
        String border = visibility == Visibility.PRIVATE ? "#D66A6A" : "#91C777";
        icon.setStyle("-fx-background-color:" + color + ";-fx-background-radius:" + shape + ";"
                + "-fx-border-color:" + border + ";-fx-border-radius:" + shape + ";"
                + "-fx-border-width:1;");
        return icon;
    }

    private static String fieldText(UmlField f) {
        return f.visibility().symbol() + " " + f.name() + ": " + f.type();
    }

    private static String fieldNameText(UmlField f) {
        return f.name();
    }

    private static String methodText(UmlMethod m) {
        String ret = m.isConstructor() ? "" : ": " + m.returnType();
        return m.visibility().symbol() + " " + m.name() + "("
                + String.join(", ", m.params()) + ")" + ret;
    }

    private static String methodNameText(UmlMethod m) {
        return m.name() + "(" + String.join(", ", m.params()) + ")";
    }

    private static String fieldNameColor(UmlTheme theme) {
        return theme.dark() ? "#F07178" : "#9B1C31";
    }

    private static String methodNameColor(UmlTheme theme) {
        return theme.dark() ? "#FFC66D" : "#7A4D00";
    }

    private static String typeColor(UmlTheme theme) {
        return theme.dark() ? "#C792EA" : "#6F42C1";
    }

    private static String fieldIconColor(UmlTheme theme) {
        return theme.dark() ? "#63C6C8" : "#2F8A8C";
    }

    private static String methodIconColor(UmlTheme theme) {
        return theme.dark() ? "#6EA8FE" : "#2F6FDB";
    }

    private static String visibilityColor(Visibility visibility, UmlTheme theme) {
        return switch (visibility) {
            case PUBLIC -> theme.dark() ? "#A7C957" : "#4D7C0F";
            case PROTECTED -> theme.dark() ? "#E6B35A" : "#946200";
            case PRIVATE -> theme.dark() ? "#F07178" : "#B42318";
            case PACKAGE -> theme.dark() ? "#8C8C8C" : "#6B7280";
        };
    }

    private static String ellipsize(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private static double boxWidth(UmlClass cls) {
        int longest = 0;
        for (String line : visibleBoxTextLines(cls)) {
            longest = Math.max(longest, line.length());
        }
        double textW = longest * CHAR_W + NODE_PAD_W;
        // badge 宽度:9px 字体 × 字符数 + 左右 padding 8 + border 2
        double badgeW = kindBadgeText(cls).length() * 5.4 + 10;
        // 标题行最小宽度 = 图标(10) + gap(6) + 类名 + gap(6) + badge
        String title = titleText(cls);
        double titleW = 10 + 6 + title.length() * 7.5 + 6 + badgeW;
        return Math.clamp(Math.max(textW, titleW), MIN_BOX_W, MAX_NODE_W);
    }

    // ======================== 尺寸估算 ========================

    private static double boxHeight(UmlClass cls) {
        double w = boxWidth(cls);
        double usable = Math.max(w - NODE_PAD_W, MIN_BOX_W - NODE_PAD_W);
        int visualLines = 0;
        for (String line : visibleBoxTextLines(cls)) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() * CHAR_W / usable));
        }
        return Math.clamp(visualLines * LINE_H + NODE_PAD_H, MIN_BOX_H, MAX_NODE_H);
    }

    private static List<String> visibleBoxTextLines(UmlClass cls) {
        List<String> lines = new ArrayList<>();
        lines.add(cls.simpleName());
        if (cls.packageName() != null && !cls.packageName().isEmpty()) {
            lines.add(cls.packageName());
        }
        if (cls.parsed()) {
            int fieldCount = 0;
            for (UmlField f : cls.fields()) {
                if (fieldCount >= MAX_VISIBLE_FIELDS) {
                    break;
                }
                lines.add(ellipsize(fieldText(f), MAX_MEMBER_CHARS));
                fieldCount++;
            }
            if (!cls.fields().isEmpty() && !cls.methods().isEmpty()) {
                lines.add("-");
            }
            int methodCount = 0;
            for (UmlMethod m : cls.methods()) {
                if (methodCount >= MAX_VISIBLE_METHODS) {
                    break;
                }
                lines.add(ellipsize(methodText(m), MAX_MEMBER_CHARS));
                methodCount++;
            }
            int hiddenFields = Math.max(0, cls.fields().size() - MAX_VISIBLE_FIELDS);
            int hiddenMethods = Math.max(0, cls.methods().size() - MAX_VISIBLE_METHODS);
            if (hiddenFields > 0 || hiddenMethods > 0) {
                lines.add("... +" + hiddenFields + " fields, +" + hiddenMethods + " methods");
            }
        }
        return lines;
    }

    private static void drawRelation(Pane pane, LayoutBox from, LayoutBox to,
                                     UmlRelation r, UmlTheme theme, int relationIndex) {
        String color = switch (r.type()) {
            case GENERALIZATION -> theme.generalizationColor();
            case REALIZATION -> theme.realizationColor();
            case ASSOCIATION -> theme.associationColor();
            case DEPENDENCY -> theme.dependencyColor();
        };
        boolean dashed = r.type() == RelationType.REALIZATION || r.type() == RelationType.DEPENDENCY;

        Point2D start;
        Point2D end;
        List<Point2D> points;
        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            start = new Point2D(from.center().getX(), from.y());
            end = new Point2D(to.center().getX(), to.y() + to.height());
            if (from.center().getY() < to.center().getY()) {
                start = new Point2D(from.center().getX(), from.y() + from.height());
                end = new Point2D(to.center().getX(), to.y());
            }
            points = hierarchyPath(start, end, relationIndex);
        } else {
            boolean horizontal = Math.abs(to.center().getX() - from.center().getX())
                    >= Math.abs(to.center().getY() - from.center().getY());
            if (horizontal) {
                boolean toRight = to.center().getX() >= from.center().getX();
                start = new Point2D(toRight ? from.x() + from.width() : from.x(), from.center().getY());
                end = new Point2D(toRight ? to.x() : to.x() + to.width(), to.center().getY());
            } else {
                boolean toBelow = to.center().getY() >= from.center().getY();
                start = new Point2D(from.center().getX(), toBelow ? from.y() + from.height() : from.y());
                end = new Point2D(to.center().getX(), toBelow ? to.y() : to.y() + to.height());
            }
            points = associationPath(start, end, horizontal, relationIndex);
        }

        Point2D lineEnd = end;
        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            Point2D previous = previousPoint(points, end);
            double angle = Math.atan2(end.getY() - previous.getY(), end.getX() - previous.getX());
            lineEnd = new Point2D(end.getX() - 11 * Math.cos(angle), end.getY() - 11 * Math.sin(angle));
        }
        drawPolyline(pane, points, lineEnd, color, dashed);

        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            Point2D previous = previousPoint(points, end);
            hollowArrow(pane, previous, end, color);
        } else if (r.type() == RelationType.DEPENDENCY) {
            Point2D previous = previousPoint(points, end);
            filledArrow(pane, previous, end, color);
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

    // ======================== 关系渲染 ========================

    private static List<Point2D> hierarchyPath(Point2D start, Point2D end, int relationIndex) {
        double offset = laneOffset(relationIndex, 16);
        double midY = (start.getY() + end.getY()) / 2.0 + offset;
        return List.of(start, new Point2D(start.getX(), midY), new Point2D(end.getX(), midY), end);
    }

    private static List<Point2D> associationPath(Point2D start, Point2D end,
                                                 boolean horizontal, int relationIndex) {
        double offset = laneOffset(relationIndex, 18);
        if (horizontal) {
            double midX = (start.getX() + end.getX()) / 2.0 + offset;
            return List.of(start, new Point2D(midX, start.getY()), new Point2D(midX, end.getY()), end);
        }
        double midY = (start.getY() + end.getY()) / 2.0 + offset;
        return List.of(start, new Point2D(start.getX(), midY), new Point2D(end.getX(), midY), end);
    }

    private static double laneOffset(int relationIndex, double step) {
        int lane = relationIndex % 5;
        return (lane - 2) * step;
    }

    private static Point2D previousPoint(List<Point2D> points, Point2D end) {
        if (points.size() < 2) {
            return end;
        }
        return points.get(points.size() - 2);
    }

    private static void drawPolyline(Pane pane, List<Point2D> points, Point2D lineEnd,
                                     String color, boolean dashed) {
        for (int i = 0; i < points.size() - 1; i++) {
            Point2D a = points.get(i);
            Point2D b = i == points.size() - 2 ? lineEnd : points.get(i + 1);
            drawSegment(pane, a, b, color, dashed);
        }
    }

    private static void drawSegment(Pane pane, Point2D start, Point2D end,
                                    String color, boolean dashed) {
        Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        line.setStroke(Color.web(color));
        line.setStrokeWidth(1.2);
        if (dashed) {
            line.getStrokeDashArray().addAll(8.0, 4.0);
        }
        pane.getChildren().add(line);
    }

    /** 空心三角(继承/实现箭头) */
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

    /** 实心小三角(依赖箭头) */
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

    /** 渲染结果:可交互 ScrollPane + 画布 Pane(用于快照导出)+ 适配窗口回调 + 缩放/尺寸查询 */
    public record UmlView(ScrollPane scrollPane, Pane canvas, Runnable fitToViewport,
                          java.util.function.DoubleSupplier zoomSupplier,
                          java.util.function.DoubleSupplier contentWidthSupplier,
                          java.util.function.DoubleSupplier contentHeightSupplier) {

        /** 当前缩放比 */
        public double currentZoom() {
            return zoomSupplier.getAsDouble();
        }

        /** 原始内容宽度(未缩放) */
        public double contentWidth() {
            return contentWidthSupplier.getAsDouble();
        }

        /** 原始内容高度(未缩放) */
        public double contentHeight() {
            return contentHeightSupplier.getAsDouble();
        }
    }

    // ======================== 交互 ========================

    /** 缩放/适配控制器:画布 = max(内容×缩放, 视口),内容居中;大图可滚动 */
    private static final class ZoomController {

        private final ScrollPane scroll;

        private final Group group;

        private final Pane canvas;

        private final double contentW;

        private final double contentH;

        private final Scale scale = new Scale(1.0, 1.0, 0, 0);

        private double zoom = 1.0;

        ZoomController(ScrollPane scroll, Group group, Pane canvas, double contentW, double contentH) {
            this.scroll = scroll;
            this.group = group;
            this.canvas = canvas;
            this.contentW = contentW;
            this.contentH = contentH;
            this.group.getTransforms().setAll(scale);
        }

        double currentZoom() {
            return zoom;
        }

        double contentWidth() {
            return contentW;
        }

        double contentHeight() {
            return contentH;
        }

        /** 整图适配:缩放到整图可见并居中 */
        void fitToWindow() {
            double vw = scroll.getViewportBounds().getWidth();
            double vh = scroll.getViewportBounds().getHeight();
            if (vw <= 0 || vh <= 0 || contentW <= 0 || contentH <= 0) {
                return;
            }
            double fit = Math.min(vw / contentW, vh / contentH);
            if (!Double.isFinite(fit) || fit <= 0) {
                return;
            }
            fit = Math.min(fit, 2.0);       // 允许适度放大,小图也能获得高清导出基础
            fit = Math.max(fit, MIN_ZOOM);  // 保底可读
            applyZoom(fit);
            scroll.setHvalue(0.5);
            scroll.setVvalue(0.5);
        }

        /** Ctrl+滚轮缩放,保持鼠标下方的点不动 */
        void zoomBy(double deltaY, double mouseX, double mouseY) {
            double next = Math.clamp(zoom + (deltaY > 0 ? ZOOM_STEP : -ZOOM_STEP), MIN_ZOOM, MAX_ZOOM);
            if (Math.abs(next - zoom) < 0.0001) {
                return;
            }
            double oldZoom = zoom;
            double oldCw = canvas.getWidth();
            double oldCh = canvas.getHeight();
            double oldGx = group.getLayoutX();
            double oldGy = group.getLayoutY();
            double vw = scroll.getViewportBounds().getWidth();
            double vh = scroll.getViewportBounds().getHeight();

            applyZoom(next);

            // 保持鼠标下方的点不动:计算缩放导致的画布偏移,补偿到滚动位置
            double newCw = canvas.getWidth();
            double newCh = canvas.getHeight();
            double groupDx = group.getLayoutX() - oldGx + (newCw - oldCw) / 2.0;
            double groupDy = group.getLayoutY() - oldGy + (newCh - oldCh) / 2.0;
            double contentFactor = next / oldZoom - 1.0;
            double oldGroupCx = oldGx + mouseX * oldZoom;
            double oldGroupCy = oldGy + mouseY * oldZoom;
            double canvasShiftX = groupDx + oldGroupCx * contentFactor;
            double canvasShiftY = groupDy + oldGroupCy * contentFactor;
            double hRange = Math.max(1, newCw - vw);
            double vRange = Math.max(1, newCh - vh);
            scroll.setHvalue(Math.clamp(scroll.getHvalue() + canvasShiftX / hRange, 0, 1));
            scroll.setVvalue(Math.clamp(scroll.getVvalue() + canvasShiftY / vRange, 0, 1));
        }

        private void applyZoom(double next) {
            zoom = next;
            scale.setX(next);
            scale.setY(next);
            double vw = scroll.getViewportBounds().getWidth();
            double vh = scroll.getViewportBounds().getHeight();
            double cw = Math.max(contentW * next, vw);
            double ch = Math.max(contentH * next, vh);
            canvas.setPrefSize(cw, ch);
            canvas.setMinSize(cw, ch);
            group.setLayoutX((cw - contentW * next) / 2.0);
            group.setLayoutY((ch - contentH * next) / 2.0);
        }
    }

    private record LayoutBox(Node box, double x, double y, double width, double height) {

        Point2D center() {
            return new Point2D(x + width / 2.0, y + height / 2.0);
        }
    }
}
