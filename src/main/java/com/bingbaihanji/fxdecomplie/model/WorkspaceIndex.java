package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import javafx.scene.control.TreeItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 基于文件树构建的工作区全局索引
 */
public final class WorkspaceIndex {

    /** 空占位索引,用于 Workspace 异步索引构建完成前的过渡期 */
    public static final WorkspaceIndex EMPTY = new WorkspaceIndex(
            List.of(), List.of(), Map.of());

    private static final Logger logger = LoggerFactory.getLogger(WorkspaceIndex.class);
    private static final long MAX_INDEXED_RESOURCE_BYTES = 5L * 1024 * 1024;

    private final List<ClassIndexEntry> classes;
    private final List<ResourceIndexEntry> resources;
    private final Map<String, ClassIndexEntry> classesByInternalName;

    private WorkspaceIndex(List<ClassIndexEntry> classes, List<ResourceIndexEntry> resources,
                           Map<String, ClassIndexEntry> classesByInternalName) {
        this.classes = List.copyOf(classes);
        this.resources = List.copyOf(resources);
        this.classesByInternalName = Collections.unmodifiableMap(new LinkedHashMap<>(classesByInternalName));
    }

    public static WorkspaceIndex build(TreeItem<FileTreeNode> root) {
        List<ClassIndexEntry> classes = new ArrayList<>();
        List<ResourceIndexEntry> resources = new ArrayList<>();
        Map<String, ClassIndexEntry> classEntries = new LinkedHashMap<>();

        ArrayDeque<TreeItem<FileTreeNode>> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            TreeItem<FileTreeNode> item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null) {
                if (node.isClassFile() && node.hasByteSource()) {
                    ClassIndexEntry entry = indexClass(node);
                    if (entry != null) {
                        classes.add(entry);
                        classEntries.put(entry.internalName(), entry);
                    }
                } else if (node.isTextFile() && node.hasByteSource()
                        && shouldIndexResource(node)) {
                    byte[] bytes = readNodeBytes(node);
                    if (bytes != null) {
                        resources.add(new ResourceIndexEntry(node.getFullPath(), bytes, true));
                    }
                }
            }
            queue.addAll(item.getChildren());
        }
        return new WorkspaceIndex(classes, resources, classEntries);
    }

    private static ClassIndexEntry indexClass(FileTreeNode node) {
        byte[] bytes = readNodeBytes(node);
        if (bytes == null) {
            return null;
        }

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
            logger.warn("解析类元数据失败: {}", node.getFullPath());
        }

        return new ClassIndexEntry(node.getFullPath(), internalName, simpleName,
                node::resolveBytes, methods, fields);
    }

    private static boolean shouldIndexResource(FileTreeNode node) {
        long size = node.getSize();
        if (size > MAX_INDEXED_RESOURCE_BYTES) {
            logger.info("跳过过大资源文件，不加入工作区索引: {} ({} 字节)",
                    node.getFullPath(), size);
            return false;
        }
        return true;
    }

    private static byte[] readNodeBytes(FileTreeNode node) {
        try {
            return node.readBytes();
        } catch (IOException e) {
            logger.warn("读取索引文件失败: {}", node.getFullPath(), e);
            return null;
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
        Map<String, byte[]> map = new LinkedHashMap<>();
        for (ClassIndexEntry cls : classes) {
            byte[] bytes = cls.bytes();
            if (bytes != null) {
                map.put(cls.internalName(), bytes);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public byte[] getClassBytes(String internalName) {
        ClassIndexEntry entry = classesByInternalName.get(internalName);
        return entry == null ? null : entry.bytes();
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
