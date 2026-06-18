package com.bingbihanji.fxdecomplie.ui.code;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

/**
 * 类信息视图。展示 class 文件的版本号、访问标志、常量池数量、父类、接口列表等结构化元数据。
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
            root.getChildren().add(label("无可用字节码", "#858585"));
            return root;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            int minor = reader.readShort(4);
            int major = reader.readShort(6);

            root.getChildren().add(label("主版本号: " + major + "  (Java " + toJavaVersion(major) + ")", "#dcdcaa"));
            root.getChildren().add(label("次版本号: " + minor, "#9aa7b0"));
            root.getChildren().add(label("访问标志: " + formatAccess(reader.getAccess()), "#9aa7b0"));
            root.getChildren().add(label("本类: " + reader.getClassName().replace('/', '.'), "#4ec9b0"));
            String superName = reader.getSuperName();
            root.getChildren().add(label("父类: " + (superName != null ? superName.replace('/', '.') : "(无)"), "#c586c0"));
            root.getChildren().add(label("常量池条目: " + reader.getItemCount(), "#9aa7b0"));

            String[] interfaces = reader.getInterfaces();
            if (interfaces.length > 0) {
                for (String itf : interfaces) {
                    root.getChildren().add(label("接口: " + itf.replace('/', '.'), "#b5cea8"));
                }
            } else {
                root.getChildren().add(label("接口: (无)", "#6a6a6a"));
            }

            // ---- Extract methods and fields via ASM visitor ----
            List<String> methods = new ArrayList<>();
            List<String> fields = new ArrayList<>();
            try {
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor,
                                                   String signature, Object value) {
                        String accessStr = formatMemberAccess(access);
                        String typeStr = descriptorToJava(descriptor);
                        fields.add(accessStr + typeStr + " " + name);
                        return super.visitField(access, name, descriptor, signature, value);
                    }

                    @Override
                    public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                     String signature, String[] exceptions) {
                        String accessStr = formatMemberAccess(access);
                        String returnType = extractReturnType(descriptor);
                        String params = extractParams(descriptor);
                        methods.add(accessStr + returnType + " " + name + "(" + params + ")");
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Exception ignored) { }

            // ---- Methods section ----
            root.getChildren().add(sectionLabel("方法 (" + methods.size() + ")"));
            if (methods.isEmpty()) {
                root.getChildren().add(label("  (无)", "#6a6a6a"));
            } else {
                for (String m : methods) {
                    root.getChildren().add(label("  " + m, "#dcdcaa"));
                }
            }

            // ---- Fields section ----
            root.getChildren().add(sectionLabel("字段 (" + fields.size() + ")"));
            if (fields.isEmpty()) {
                root.getChildren().add(label("  (无)", "#6a6a6a"));
            } else {
                for (String f : fields) {
                    root.getChildren().add(label("  " + f, "#9cdcfe"));
                }
            }
        } catch (Exception e) {
            root.getChildren().add(label("解析失败: " + e.getMessage(), "#f44747"));
        }
        return root;
    }

    private static int toJavaVersion(int major) {
        return major - 44;
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
        if ((access & Opcodes.ACC_PUBLIC) != 0) sb.append("public ");
        else if ((access & Opcodes.ACC_PRIVATE) != 0) sb.append("private ");
        else if ((access & Opcodes.ACC_PROTECTED) != 0) sb.append("protected ");
        if ((access & Opcodes.ACC_STATIC) != 0) sb.append("static ");
        if ((access & Opcodes.ACC_FINAL) != 0) sb.append("final ");
        if ((access & Opcodes.ACC_ABSTRACT) != 0) sb.append("abstract ");
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
