package com.bingbaihanji.fxdecomplie.decompiler;

import org.jd.core.v1.ClassFileToJavaSourceDecompiler;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.api.printer.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JD-Core 反编译引擎适配器
 * 从 moe.sota.decompiler 项目移植
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public class JdDecompiler implements Decompiler {

    private static final Logger log = LoggerFactory.getLogger(JdDecompiler.class);

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = DecompilerContext.normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        String internalName = DecompilerContext.normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        // 规范化内部名，去除可能的 .class 后缀
        final String normalizedName = DecompilerContext.normalizeInternalName(typeName);
        if (classBytes == null) {
            return "// JD-Core Error: null class bytes for " + normalizedName;
        }
        StringBuilder result = new StringBuilder();
        DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;

        final int lastSlash = normalizedName.lastIndexOf('/');
        final String expectedPackage = lastSlash > 0
                ? normalizedName.substring(0, lastSlash).replace('/', '.')
                : null;

        Loader loader = new Loader() {
            @Override
            public boolean canLoad(String internalName) {
                return internalName.equals(normalizedName)
                        || effectiveContext.resolveClassBytes(internalName) != null;
            }

            @Override
            public byte[] load(String internalName) {
                if (internalName.equals(normalizedName)) return classBytes;
                return effectiveContext.resolveClassBytes(internalName);
            }
        };

        Printer printer = new JdPrinter(result);

        log.debug("JD-Core decompile: class={}", typeName);
        long start = System.currentTimeMillis();
        try {
            ClassFileToJavaSourceDecompiler decompiler = new ClassFileToJavaSourceDecompiler();
            decompiler.decompile(loader, printer, typeName);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("JD-Core decompile error: {} ({}ms): {}", typeName, elapsed, msg);
            if (expectedPackage != null) {
                return "package " + expectedPackage + ";\n\n// JD-Core Error: " + msg;
            }
            return "// JD-Core Error: " + msg;
        }

        String decompiled = result.toString();
        long elapsed = System.currentTimeMillis() - start;
        if (decompiled.isEmpty()) {
            log.warn("JD-Core decompile returned empty: {} ({}ms)", typeName, elapsed);
            if (expectedPackage != null) {
                return "package " + expectedPackage + ";\n\n// JD-Core decompile failed\n// Class: " + typeName;
            }
            return "// JD-Core decompile failed\n// Class: " + typeName;
        }
        log.debug("JD-Core decompile OK: {} ({}ms, {} chars)", typeName, elapsed, decompiled.length());
        if (expectedPackage != null && !decompiled.trim().startsWith("package ")) {
            decompiled = "package " + expectedPackage + ";\n\n" + decompiled;
        }
        return decompiled;
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.JD;
    }

    @Override
    public java.util.Map<String, String> getDefaultOptions() {
        return java.util.Map.of();
    }

    /** JD-Core Printer 实现 — 将反编译输出追加到 StringBuilder */
    private static final class JdPrinter implements Printer {
        private final StringBuilder builder;
        private int indent;

        JdPrinter(StringBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void start(int maxLineNumber, int majorVersion, int minorVersion) {
        }

        @Override
        public void end() {
        }

        @Override
        public void startMarker(int type) {
        }

        @Override
        public void endMarker(int type) {
        }

        @Override
        public void startLine(int lineNumber) {
            builder.append("    ".repeat(Math.max(0, indent)));
        }

        @Override
        public void endLine() {
            builder.append('\n');
        }

        @Override
        public void extraLine(int count) {
            while (count-- > 0) builder.append('\n');
        }

        @Override
        public void indent() {
            indent++;
        }

        @Override
        public void unindent() {
            if (indent > 0) {
                indent--;
            }
        }

        @Override
        public void printText(String text) {
            builder.append(text);
        }

        @Override
        public void printKeyword(String keyword) {
            builder.append(keyword);
        }

        @Override
        public void printStringConstant(String constant, String ownerInternalName) {
            builder.append(constant);
        }

        @Override
        public void printNumericConstant(String constant) {
            builder.append(constant);
        }

        @Override
        public void printDeclaration(int type, String internalTypeName, String name, String descriptor) {
            builder.append(name);
        }

        @Override
        public void printReference(int type, String internalTypeName, String name,
                                   String descriptor, String ownerInternalName) {
            builder.append(name);
        }
    }
}
