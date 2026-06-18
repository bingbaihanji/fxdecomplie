package com.bingbihanji.fxdecomplie.ui.code;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.objectweb.asm.ClassReader;

import java.util.ArrayList;
import java.util.List;

/**
 * 类信息视图。展示 class 文件的版本号、访问标志、常量池数量、父类、接口列表等结构化元数据。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassInfoView {

    private ClassInfoView() { throw new AssertionError("utility class"); }

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
}
