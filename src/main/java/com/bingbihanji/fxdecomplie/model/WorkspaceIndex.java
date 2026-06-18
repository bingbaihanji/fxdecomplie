package com.bingbihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;
import org.objectweb.asm.*;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

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

        try {
            ClassReader reader = new ClassReader(bytes);
            internalName = reader.getClassName();
            simpleName = simpleName(internalName);
            // ---- Member scan: traverse class structure via ASM visitor (skip code bodies) ----
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor,
                                               String signature, Object value) {
                    // ---- Field index: record name + descriptor for search/outline ----
                    fields.add(new MemberIndexEntry(node.getFullPath(), name, descriptor));
                    return super.visitField(access, name, descriptor, signature, value);
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                 String signature, String[] exceptions) {
                    // ---- Method index: record name + descriptor for search/outline ----
                    methods.add(new MemberIndexEntry(node.getFullPath(), name, descriptor));
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        } catch (Exception ignored) {
            // Keep path-based metadata for malformed classes.
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
            return new String(bytes, StandardCharsets.ISO_8859_1);
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
