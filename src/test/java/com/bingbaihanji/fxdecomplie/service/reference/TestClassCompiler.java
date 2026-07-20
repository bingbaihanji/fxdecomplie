package com.bingbaihanji.fxdecomplie.service.reference;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试辅助：编译 Java 源码并补丁 major version 到 69（Java 25）
 */
public final class TestClassCompiler {

    private TestClassCompiler() {}

    public static Map<String, byte[]> compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        Iterable<? extends JavaFileObject> units = sources.entrySet().stream()
                .map(e -> new SourceJavaFileObject(e.getKey(), e.getValue()))
                .toList();

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics,
                null, null, units);
        boolean ok = task.call();
        if (!ok) {
            throw new RuntimeException("编译失败: " + diagnostics.getDiagnostics());
        }
        Map<String, byte[]> result = fileManager.getOutputs();
        result.replaceAll((k, v) -> patchMajorVersion(v, 69));
        return result;
    }

    public static byte[] patchMajorVersion(byte[] bytes, int major) {
        if (bytes == null || bytes.length < 8) {
            return bytes;
        }
        byte[] copy = bytes.clone();
        copy[6] = (byte) ((major >> 8) & 0xFF);
        copy[7] = (byte) (major & 0xFF);
        return copy;
    }

    private static final class SourceJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        SourceJavaFileObject(String name, String source) {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class InMemoryFileManager extends javax.tools.ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, byte[]> outputs = new LinkedHashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
            return new ClassJavaFileObject(className, outputs);
        }

        Map<String, byte[]> getOutputs() {
            return outputs;
        }
    }

    private static final class ClassJavaFileObject extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, byte[]> outputs;

        ClassJavaFileObject(String className, Map<String, byte[]> outputs) {
            super(URI.create("mem:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
            this.className = className;
            this.outputs = outputs;
        }

        @Override
        public OutputStream openOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    outputs.put(className.replace('.', '/') + ".class", toByteArray());
                }
            };
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(outputs.getOrDefault(className, new byte[0]));
        }
    }
}
