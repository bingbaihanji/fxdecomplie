package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;

/**
 * 索引化的类元数据，用于工作区范围的搜索和分析
 */
public final class ClassIndexEntry {

    private static final Logger log = LoggerFactory.getLogger(ClassIndexEntry.class);

    private final String fullPath;
    private final String internalName;
    private final String simpleName;
    private final FileTreeNode.ByteLoader byteLoader;
    private final List<MemberIndexEntry> methods;
    private final List<MemberIndexEntry> fields;
    private volatile String bytecodeText;

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           byte[] bytes, List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this(fullPath, internalName, simpleName, () -> bytes, methods, fields);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this.fullPath = Objects.requireNonNull(fullPath, "fullPath");
        this.internalName = Objects.requireNonNull(internalName, "internalName");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
        this.byteLoader = byteLoader;
        this.methods = List.copyOf(methods == null ? List.of() : methods);
        this.fields = List.copyOf(fields == null ? List.of() : fields);
    }

    private static String toBytecodeText(byte[] bytes) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ClassReader reader = new ClassReader(bytes);
            Textifier textifier = new Textifier();
            TraceClassVisitor visitor = new TraceClassVisitor(null, textifier, pw);
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            pw.flush();
            return sw.toString();
        } catch (RuntimeException e) {
            return ClassFileParser.summary(bytes);
        }
    }

    public String fullPath() {
        return fullPath;
    }

    public String internalName() {
        return internalName;
    }

    public String simpleName() {
        return simpleName;
    }

    public byte[] bytes() {
        if (byteLoader == null) {
            return null;
        }
        try {
            return byteLoader.load();
        } catch (IOException e) {
            log.debug("加载字节失败: {}", fullPath, e);
            return null;
        }
    }

    public List<MemberIndexEntry> methods() {
        return methods;
    }

    public List<MemberIndexEntry> fields() {
        return fields;
    }

    public String bytecodeText() {
        String current = bytecodeText;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (bytecodeText == null) {
                byte[] bytes = bytes();
                bytecodeText = bytes == null ? "" : toBytecodeText(bytes);
            }
            return bytecodeText;
        }
    }
}
