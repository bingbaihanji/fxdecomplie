package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link UmlDiagram} 生成 PlantUML 源码（.puml 文本），
 * 移植自 class-diagram-gen 的 {@code GenerateUML.buildSource}。
 * 应用内不渲染 .puml，仅供用户用 PlantUML 工具二次生成/编辑。
 */
public final class PumlExporter {

    private static final String LIGHT_SKIN =
            "skinparam {\n"
            + "  linetype ortho\n"
            + "  shadowing false\n"
            + "  roundCorner 10\n"
            + "  backgroundColor #FFFFFF\n"
            + "  defaultFontName \"Segoe UI\"\n"
            + "  defaultFontSize 12\n"
            + "  classAttributeIconSize 0\n"
            + "  classFontSize 13\n"
            + "  classFontStyle bold\n"
            + "  classFontColor #1F2430\n"
            + "  classHeaderFontSize 14\n"
            + "  classHeaderFontStyle bold\n"
            + "  classHeaderFontColor #1F2430\n"
            + "  classHeaderBackgroundColor #EDF2FA\n"
            + "  classBackgroundColor #FFFFFF\n"
            + "  classBorderColor #A0A7B3\n"
            + "  classAttributeFontSize 11\n"
            + "  classAttributeFontStyle normal\n"
            + "  classAttributeFontColor #2E3440\n"
            + "  classStereotypeFontSize 11\n"
            + "  classStereotypeFontColor #5A6572\n"
            + "  classArrowColor #44546A\n"
            + "}\n";

    private static final String DARK_SKIN =
            "skinparam {\n"
            + "  linetype ortho\n"
            + "  shadowing false\n"
            + "  roundCorner 10\n"
            + "  backgroundColor #2B2B2B\n"
            + "  defaultFontName \"Segoe UI\"\n"
            + "  defaultFontSize 12\n"
            + "  classAttributeIconSize 0\n"
            + "  classFontSize 13\n"
            + "  classFontStyle bold\n"
            + "  classFontColor #E6E6E6\n"
            + "  classHeaderFontSize 14\n"
            + "  classHeaderFontStyle bold\n"
            + "  classHeaderFontColor #FFFFFF\n"
            + "  classHeaderBackgroundColor #4B5053\n"
            + "  classBackgroundColor #3C3F41\n"
            + "  classBorderColor #6A6F73\n"
            + "  classAttributeFontSize 11\n"
            + "  classAttributeFontStyle normal\n"
            + "  classAttributeFontColor #BBBBBB\n"
            + "  classStereotypeFontSize 11\n"
            + "  classStereotypeFontColor #9A9A9A\n"
            + "  classArrowColor #9A9A9A\n"
            + "}\n";

    private PumlExporter() {
        throw new AssertionError("utility class");
    }

    public static String buildSource(UmlDiagram diagram, UmlTheme theme) {
        if (diagram == null || diagram.classes().isEmpty()) {
            return "@startuml\n@enduml";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("top to bottom direction\n");
        sb.append(theme.dark() ? DARK_SKIN : LIGHT_SKIN);

        Map<String, String> alias = new LinkedHashMap<>();
        int i = 0;
        for (UmlClass c : diagram.classes()) {
            alias.put(c.internalName(), "C" + i);
            i++;
        }

        i = 0;
        for (UmlClass c : diagram.classes()) {
            String a = "C" + i;
            sb.append(kindKeyword(c)).append(' ')
              .append(title(c, theme)).append(" as ").append(a)
              .append(nodeColor(c, theme)).append(" {\n");
            appendMembers(c, sb);
            sb.append("}\n\n");
            i++;
        }

        for (UmlRelation r : diagram.relations()) {
            String child = alias.get(r.from());
            String parent = alias.get(r.to());
            if (child == null || parent == null) {
                continue;
            }
            sb.append(relationshipLine(r, child, parent, theme));
        }

        // 注解应用边（黄点线）。当前 builder 不会把注解类型入队成图节点，
        // 因此 alias.get(ann) 通常为 null；保留此块以对齐参考项目，属前瞻性代码。
        String annotationSpec = theme.dark() ? "[#C9B45A,dotted]" : "[#999900,dotted]";
        for (UmlClass c : diagram.classes()) {
            for (String ann : c.annotationNames()) {
                String annAlias = alias.get(ann);
                if (annAlias != null && !annAlias.equals(alias.get(c.internalName()))) {
                    sb.append(alias.get(c.internalName()))
                      .append(" -").append(annotationSpec).append("- ")
                      .append(annAlias).append('\n');
                }
            }
        }
        sb.append("@enduml");
        return sb.toString();
    }

    private static String kindKeyword(UmlClass c) {
        return switch (c.kind()) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case ANNOTATION -> "annotation";
            case CLASS -> c.isAbstract() ? "abstract class" : "class";
        };
    }

    private static String title(UmlClass c, UmlTheme theme) {
        String pkgColor = theme.packageText();
        String title = "**" + c.simpleName() + "**";
        if (c.packageName() != null && !c.packageName().isEmpty()) {
            title += "\\n<size:10><color:" + pkgColor + ">" + c.packageName() + "</color></size>";
        }
        return "\"" + title + "\"";
    }

    private static String nodeColor(UmlClass c, UmlTheme theme) {
        if (!c.parsed()) {
            return " " + theme.markerBackground();
        }
        String header = switch (c.kind()) {
            case CLASS -> theme.classHeader();
            case INTERFACE -> theme.interfaceHeader();
            case ENUM -> theme.enumHeader();
            case ANNOTATION -> theme.annotationHeader();
        };
        return " #back:" + hexNoHash(theme.nodeBackground()) + ";header:" + hexNoHash(header);
    }

    private static void appendMembers(UmlClass c, StringBuilder sb) {
        if (!c.parsed()) {
            return;
        }
        List<UmlField> fields = c.fields();
        List<UmlMethod> methods = c.methods();
        for (UmlField f : fields) {
            sb.append("  ").append(fieldLine(f)).append('\n');
        }
        if (!fields.isEmpty() && !methods.isEmpty()) {
            sb.append("  --\n");
        }
        for (UmlMethod m : methods) {
            sb.append("  ").append(methodLine(m)).append('\n');
        }
    }

    private static String fieldLine(UmlField f) {
        StringBuilder s = new StringBuilder();
        s.append(f.visibility().symbol());
        if (f.isStatic()) {
            s.append(" {static}");
        }
        s.append(f.name()).append(": ").append(f.type());
        return s.toString();
    }

    private static String methodLine(UmlMethod m) {
        StringBuilder s = new StringBuilder();
        s.append(m.visibility().symbol());
        if (m.isStatic()) {
            s.append(" {static}");
        }
        if (m.isAbstract()) {
            s.append(" {abstract}");
        }
        s.append(m.name()).append('(').append(String.join(", ", m.params())).append(')');
        if (!m.isConstructor()) {
            s.append(": ").append(m.returnType());
        }
        return s.toString();
    }

    private static String relationshipLine(UmlRelation r, String child, String parent, UmlTheme theme) {
        String spec = arrowSpec(r.type(), theme);
        String head = switch (r.type()) {
            case GENERALIZATION, REALIZATION -> "^";
            case DEPENDENCY -> ">";
            case ASSOCIATION -> "";
        };
        String fromCard = r.fromCardinality() == null ? "" : "\"" + r.fromCardinality() + "\" ";
        String toCard = r.toCardinality() == null ? "" : " \"" + r.toCardinality() + "\"";
        return child + " " + fromCard + "-" + spec + "-" + head + toCard + " " + parent + "\n";
    }

    private static String arrowSpec(RelationType type, UmlTheme theme) {
        return switch (type) {
            case GENERALIZATION -> "[" + theme.generalizationColor() + ",plain]";
            case REALIZATION -> "[" + theme.realizationColor() + ",dashed]";
            case ASSOCIATION -> "[" + theme.associationColor() + ",plain]";
            case DEPENDENCY -> "[" + theme.dependencyColor() + ",dashed]";
        };
    }

    /** 去掉十六进制颜色的前导 #（PlantUML 扩展色 #back:..;header:.. 内不接受 #） */
    private static String hexNoHash(String color) {
        return color != null && color.startsWith("#") ? color.substring(1) : color;
    }
}
