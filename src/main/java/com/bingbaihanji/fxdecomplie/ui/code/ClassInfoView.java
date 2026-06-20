package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 类信息视图展示 class 文件的版本号、访问标志、常量池数量、父类、接口列表等结构化元数据
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassInfoView {

    private ClassInfoView() {
        throw new AssertionError("utility class");
    }

    public static VBox createView(byte[] classBytes) {
        VBox root = new VBox(6);
        root.getStyleClass().add("class-info-view");
        root.setStyle("-fx-padding: 12px; -fx-background-color: #1e1e1e;");

        if (classBytes == null) {
            root.getChildren().add(label(I18nUtil.getString("classinfo.noBytecode"), "#858585"));
            return root;
        }

        try {
            Optional<ClassFileMetadata> parsed = ClassFileParser.tryParse(classBytes);
            if (parsed.isEmpty()) {
                root.getChildren().add(label(I18nUtil.getString("classinfo.parseFailed"), "#f44747"));
                return root;
            }

            ClassFileMetadata metadata = parsed.get();
            int minor = metadata.minorVersion();
            int major = metadata.majorVersion();

            root.getChildren().add(label(I18nUtil.getString("classinfo.major",
                    major, ClassFileParser.javaVersion(major)), "#dcdcaa"));
            root.getChildren().add(label(I18nUtil.getString("classinfo.minor", minor), "#9aa7b0"));
            root.getChildren().add(label(I18nUtil.getString("classinfo.access",
                    formatAccess(metadata.accessFlags())), "#9aa7b0"));
            root.getChildren().add(label(I18nUtil.getString("classinfo.thisClass",
                    metadata.internalName().replace('/', '.')), "#4ec9b0"));
            String superName = metadata.superName();
            root.getChildren().add(label(I18nUtil.getString("classinfo.superClass",
                            superName != null ? superName.replace('/', '.') : I18nUtil.getString("classinfo.noneInline")),
                    "#c586c0"));
            root.getChildren().add(label(I18nUtil.getString("classinfo.constantPool",
                    metadata.constantPoolCount()), "#9aa7b0"));

            if (!metadata.interfaces().isEmpty()) {
                for (String itf : metadata.interfaces()) {
                    root.getChildren().add(label(I18nUtil.getString("classinfo.interface",
                            itf.replace('/', '.')), "#b5cea8"));
                }
            } else {
                root.getChildren().add(label(I18nUtil.getString("classinfo.interface",
                        I18nUtil.getString("classinfo.noneInline")), "#6a6a6a"));
            }

            // ---- 通过 ASM 访问者提取方法和字段 ----
            List<String> methods = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            for (ClassFileMetadata.MemberInfo field : metadata.fields()) {
                String accessStr = formatMemberAccess(field.accessFlags());
                String typeStr = descriptorToJava(field.descriptor());
                fields.add(accessStr + typeStr + " " + field.name());
            }
            for (ClassFileMetadata.MemberInfo method : metadata.methods()) {
                String accessStr = formatMemberAccess(method.accessFlags());
                String returnType = extractReturnType(method.descriptor());
                String params = extractParams(method.descriptor());
                methods.add(accessStr + returnType + " " + method.name() + "(" + params + ")");
            }

            // ---- 方法区域 ----
            root.getChildren().add(sectionLabel(I18nUtil.getString("classinfo.methods", methods.size())));
            if (methods.isEmpty()) {
                root.getChildren().add(label("  " + I18nUtil.getString("classinfo.noneInline"), "#6a6a6a"));
            } else {
                for (String m : methods) {
                    root.getChildren().add(label("  " + m, "#dcdcaa"));
                }
            }

            // ---- 字段区域 ----
            root.getChildren().add(sectionLabel(I18nUtil.getString("classinfo.fields", fields.size())));
            if (fields.isEmpty()) {
                root.getChildren().add(label("  " + I18nUtil.getString("classinfo.noneInline"), "#6a6a6a"));
            } else {
                for (String f : fields) {
                    root.getChildren().add(label("  " + f, "#9cdcfe"));
                }
            }
        } catch (Exception e) {
            root.getChildren().add(label(I18nUtil.getString("classinfo.parseFailedWithMessage",
                    e.getMessage()), "#f44747"));
        }
        return root;
    }

    private static String formatAccess(int access) {
        List<String> flags = new ArrayList<>();
        if ((access & 0x0001) != 0) flags.add("public");
        if ((access & 0x0002) != 0) flags.add("private");
        if ((access & 0x0004) != 0) flags.add("protected");
        if ((access & 0x0010) != 0) flags.add("final");
        if ((access & 0x0020) != 0) flags.add("super");
        if ((access & 0x0200) != 0) flags.add("interface");
        if ((access & 0x0400) != 0) flags.add("abstract");
        if ((access & 0x1000) != 0) flags.add("synthetic");
        if ((access & 0x2000) != 0) flags.add("annotation");
        if ((access & 0x4000) != 0) flags.add("enum");
        return flags.isEmpty() ? String.valueOf(access) : String.join(", ", flags);
    }

    private static Label label(String text, String color) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-family: 'Consolas', monospace;");
        return l;
    }

    private static Label sectionLabel(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #569cd6; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8 0 2 0;");
        return l;
    }

    private static String formatMemberAccess(int access) {
        StringBuilder sb = new StringBuilder();
        if ((access & 0x0001) != 0) sb.append("public ");
        else if ((access & 0x0002) != 0) sb.append("private ");
        else if ((access & 0x0004) != 0) sb.append("protected ");
        if ((access & 0x0008) != 0) sb.append("static ");
        if ((access & 0x0010) != 0) sb.append("final ");
        if ((access & 0x0400) != 0) sb.append("abstract ");
        return sb.toString();
    }

    private static String descriptorToJava(String descriptor) {
        return descriptor.replace('/', '.').replace(";", "");
    }

    private static String extractReturnType(String descriptor) {
        int paren = descriptor.lastIndexOf(')');
        if (paren < 0 || paren + 1 >= descriptor.length()) return "void";
        String ret = descriptor.substring(paren + 1);
        if ("V".equals(ret)) return "void";
        if ("I".equals(ret)) return "int";
        if ("J".equals(ret)) return "long";
        if ("Z".equals(ret)) return "boolean";
        if ("F".equals(ret)) return "float";
        if ("D".equals(ret)) return "double";
        if ("C".equals(ret)) return "char";
        if ("B".equals(ret)) return "byte";
        if ("S".equals(ret)) return "short";
        if (ret.startsWith("L")) return ret.substring(1, ret.length() - 1).replace('/', '.');
        if (ret.startsWith("[")) return ret.replace('/', '.');
        return ret;
    }

    private static String extractParams(String descriptor) {
        int paren = descriptor.indexOf('(');
        int endParen = descriptor.lastIndexOf(')');
        if (paren < 0 || endParen <= paren) return "";
        String params = descriptor.substring(paren + 1, endParen);
        if (params.isEmpty()) return "";
        // Simplify: just show the raw descriptor for params
        return params;
    }
}
