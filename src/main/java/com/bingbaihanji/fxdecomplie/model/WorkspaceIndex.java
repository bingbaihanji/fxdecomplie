package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import javafx.scene.control.TreeItem;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Workspace-wide index built from the file tree.
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspaceIndex {

    /** 空占位索引，用于 Workspace 异步索引构建完成前的过渡期 */
    public static final WorkspaceIndex EMPTY = new WorkspaceIndex(
            List.of(), List.of(), Map.of());
    private static final Logger logger = LoggerFactory.getLogger(WorkspaceIndex.class);
    private final List<ClassIndexEntry> classes;
    private final List<ResourceIndexEntry> resources;
    private final Map<String, byte[]> classBytesByInternalName;

    private WorkspaceIndex(List<ClassIndexEntry> classes, List<ResourceIndexEntry> resources,
                           Map<String, byte[]> classBytesByInternalName) {
        this.classes = List.copyOf(classes);
        this.resources = List.copyOf(resources);
        this.classBytesByInternalName = Collections.unmodifiableMap(new LinkedHashMap<>(classBytesByInternalName));
    }

    public static WorkspaceIndex build(TreeItem<FileTreeNode> root) {
        List<ClassIndexEntry> classes = new ArrayList<>();
        List<ResourceIndexEntry> resources = new ArrayList<>();
        Map<String, byte[]> classBytes = new LinkedHashMap<>();

        // ---- Breadth-first tree walk over file tree ----
        ArrayDeque<TreeItem<FileTreeNode>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeItem<FileTreeNode> item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null) {
                if (node.isClassFile() && node.getCachedBytes() != null) {
                    // ---- Class scan: index bytecode, extract methods/fields via ASM ----
                    ClassIndexEntry entry = indexClass(node);
                    classes.add(entry);
                    classBytes.put(entry.internalName(), entry.bytes());
                } else if (node.isTextFile() && node.getCachedBytes() != null) {
                    // ---- Resource scan: index text resources (XML, JSON, YAML, etc.) ----
                    resources.add(new ResourceIndexEntry(node.getFullPath(), node.getCachedBytes(), true));
                }
            }
            queue.addAll(item.getChildren());
        }
        return new WorkspaceIndex(classes, resources, classBytes);
    }

    private static ClassIndexEntry indexClass(FileTreeNode node) {
        byte[] bytes = node.getCachedBytes();
        String internalName = node.getFullPath().replace(".class", "");
        String simpleName = simpleName(internalName);
        List<MemberIndexEntry> methods = new ArrayList<>();
        List<MemberIndexEntry> fields = new ArrayList<>();

        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isPresent()) {
            ClassFileMetadata meta = metadata.get();
            internalName = meta.internalName();
            simpleName = simpleName(internalName);
            for (ClassFileMetadata.MemberInfo field : meta.fields()) {
                fields.add(new MemberIndexEntry(node.getFullPath(), field.name(), field.descriptor()));
            }
            for (ClassFileMetadata.MemberInfo method : meta.methods()) {
                methods.add(new MemberIndexEntry(node.getFullPath(), method.name(), method.descriptor()));
            }
        } else {
            logger.warn("Failed to parse class metadata: {}", node.getFullPath());
        }

        return new ClassIndexEntry(node.getFullPath(), internalName, simpleName, bytes,
                methods, fields, bytecodeText(bytes));
    }

    private static String bytecodeText(byte[] bytes) {
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ClassReader reader = new ClassReader(bytes);
            Textifier textifier = new Textifier();
            TraceClassVisitor visitor = new TraceClassVisitor(null, textifier, pw);
            reader.accept(visitor, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            pw.flush();
            return sw.toString();
        } catch (Exception e) {
            return ClassFileParser.summary(bytes);
        }
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    public List<ClassIndexEntry> classes() {
        return classes;
    }

    public List<ResourceIndexEntry> resources() {
        return resources;
    }

    public Map<String, byte[]> classBytesByInternalName() {
        return classBytesByInternalName;
    }

    public byte[] getClassBytes(String internalName) {
        return classBytesByInternalName.get(internalName);
    }

    public List<String> classPaths() {
        return classes.stream().map(ClassIndexEntry::fullPath).toList();
    }

    public Map<String, byte[]> resourceBytesByPath() {
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (ResourceIndexEntry resource : resources) {
            map.put(resource.fullPath(), resource.bytes());
        }
        return map;
    }

    public Map<String, String> bytecodeTextByPath() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ClassIndexEntry cls : classes) {
            map.put(cls.fullPath(), cls.bytecodeText());
        }
        return map;
    }
}
