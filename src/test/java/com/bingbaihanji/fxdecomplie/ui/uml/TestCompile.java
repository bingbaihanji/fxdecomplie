package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 测试助手：内存编译源码 → 字节映射，并构造 FileTreeModel */
final class TestCompile {

    private TestCompile() {
        throw new AssertionError("utility class");
    }

    /** 编译多段源码，返回「com/example/Test.class」→ 字节 映射。键用完整路径（含 .class）。 */
    static Map<String, byte[]> compile(Map<String, String> classNameToSource) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        MemFileManager fm = new MemFileManager(
                compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8));
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> e : classNameToSource.entrySet()) {
            String className = e.getKey();
            units.add(new SimpleJavaFileObject(
                    URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return e.getValue();
                }
            });
        }
        boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
        if (!ok) {
            throw new IllegalStateException("测试源码编译失败");
        }
        return fm.outputBytes;
    }

    /** 由编译产物构建文件树根（PACKAGE 根 + CLASS_FILE 子节点） */
    static FileTreeModel treeFromClasses(Map<String, byte[]> classBytes) {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        for (Map.Entry<String, byte[]> e : classBytes.entrySet()) {
            String path = e.getKey();
            int idx = path.lastIndexOf('/');
            String name = idx >= 0 ? path.substring(idx + 1) : path;
            FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setCachedBytes(e.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }
        return root;
    }

    /** 内存文件管理器：捕获编译输出的 class 字节 */
    private static final class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        final Map<String, byte[]> outputBytes = new HashMap<>();

        MemFileManager(JavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(
                    URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            outputBytes.put(className.replace('.', '/') + ".class", toByteArray());
                        }
                    };
                }
            };
        }
    }
}
