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
 * 索引化的类元数据,用于工作区范围的搜索和分析
 *
 * @author bingbaihanji
 * @date 2026-06-21
 */
public final class ClassIndexEntry {

    private static final Logger log = LoggerFactory.getLogger(ClassIndexEntry.class);

    private final String fullPath;
    private final String internalName;
    private final String simpleName;
    private final FileTreeNode.ByteLoader byteLoader;
    private final List<MemberIndexEntry> methods;
    private final List<MemberIndexEntry> fields;
    private final String superName;
    private final List<String> interfaces;
    private final FileTreeNode node;
    private volatile String bytecodeText;

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           byte[] bytes, List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this(fullPath, internalName, simpleName, () -> bytes, methods, fields, null, List.of(), null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this(fullPath, internalName, simpleName, byteLoader, methods, fields, null, List.of(), null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields,
                           String superName,
                           List<String> interfaces) {
        this(fullPath, internalName, simpleName, byteLoader, methods, fields, superName, interfaces, null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields,
                           String superName,
                           List<String> interfaces,
                           FileTreeNode node) {
        this.fullPath = Objects.requireNonNull(fullPath, "fullPath");
        this.internalName = Objects.requireNonNull(internalName, "internalName");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
        this.byteLoader = byteLoader;
        this.methods = List.copyOf(methods == null ? List.of() : methods);
        this.fields = List.copyOf(fields == null ? List.of() : fields);
        this.superName = superName;
        this.interfaces = List.copyOf(interfaces == null ? List.of() : interfaces);
        this.node = node;
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

    /** @return 类的完全限定路径(内部形式,如 com/example/Foo.class) */
    public String fullPath() {
        return fullPath;
    }

    /** @return 类的内部名称(如 com/example/Foo) */
    public String internalName() {
        return internalName;
    }

    /** @return 类的简单名称(如 Foo) */
    public String simpleName() {
        return simpleName;
    }

    /**
     * 懒加载类字节码
     *
     * @return 类文件的原始字节数组,加载失败时返回 null
     */
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

    /** @return 已索引的方法成员列表(不可变) */
    public List<MemberIndexEntry> methods() {
        return methods;
    }

    /** @return 已索引的字段成员列表(不可变) */
    public List<MemberIndexEntry> fields() {
        return fields;
    }

    /** @return 父类内部名称,未知或 Object 顶层时可能为 null */
    public String superName() {
        return superName;
    }

    /** @return 当前类直接实现的接口内部名称列表 */
    public List<String> interfaces() {
        return interfaces;
    }

    /** @return 构造索引时对应的文件树节点,可能为 null */
    public FileTreeNode node() {
        return node;
    }

    /**
     * 懒加载并缓存字节码文本表示
     *
     * <p>使用双重检查锁定保证线程安全仅缓存成功结果 
     * 加载失败时不缓存(返回回退摘要),允许后续调用重试</p>
     *
     * @return ASM Textifier 格式的字节码文本,失败时返回 ClassFileParser 摘要
     */
    public String bytecodeText() {
        String current = bytecodeText;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (bytecodeText == null) {
                byte[] bytes = bytes();
                if (bytes == null) {
                    // 不缓存失败结果,后续调用可重试
                    return ClassFileParser.summary(new byte[0]);
                }
                bytecodeText = toBytecodeText(bytes);
            }
            return bytecodeText;
        }
    }
}
