package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * 基于文件树构建的工作区全局索引
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class WorkspaceIndex {

    /** 空占位索引,用于 Workspace 异步索引构建完成前的过渡期 */
    public static final WorkspaceIndex EMPTY = new WorkspaceIndex(
            List.of(), List.of(), Map.of());

    private static final Logger log = LoggerFactory.getLogger(WorkspaceIndex.class);
    private static final long MAX_INDEXED_RESOURCE_BYTES = 5L * 1024 * 1024;

    private final List<ClassIndexEntry> classes;
    private final List<ResourceIndexEntry> resources;
    private final Map<String, ClassIndexEntry> classesByInternalName;
    private volatile Map<String, byte[]> resourceBytesByPathCache;
    private volatile Map<String, List<String>> subclassesByParentCache;

    private WorkspaceIndex(List<ClassIndexEntry> classes, List<ResourceIndexEntry> resources,
                           Map<String, ClassIndexEntry> classesByInternalName) {
        this.classes = List.copyOf(classes);
        this.resources = List.copyOf(resources);
        this.classesByInternalName = Collections.unmodifiableMap(new LinkedHashMap<>(classesByInternalName));
    }

    /**
     * 从文件树的根节点出发遍历并构建工作区索引
     *
     * @param root 文件树的根 FileTreeModel
     * @return 构建完成的工作区索引
     */
    public static WorkspaceIndex build(FileTreeModel root) {
        List<FileTreeNode> nodes = new ArrayList<>();
        ArrayDeque<FileTreeModel> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            FileTreeModel item = queue.removeFirst();
            FileTreeNode node = item.getValue();
            if (node != null) {
                nodes.add(node);
            }
            queue.addAll(item.getChildren());
        }
        return build(nodes);
    }

    /**
     * 从已提取的 FileTreeNode 列表构建索引(线程安全,可在后台线程调用)
     *
     * @param nodes class 和资源 FileTreeNode 列表
     * @return 工作区索引
     */
    public static WorkspaceIndex build(List<FileTreeNode> nodes) {
        List<ClassIndexEntry> classes = new ArrayList<>();
        List<ResourceIndexEntry> resources = new ArrayList<>();
        Map<String, ClassIndexEntry> classEntries = new LinkedHashMap<>();

        for (FileTreeNode node : nodes) {
            if (node.isClassFile() && node.hasByteSource()) {
                ClassIndexEntry entry = indexClass(node);
                if (entry != null) {
                    classes.add(entry);
                    putClassEntry(classEntries, entry);
                }
            } else if (node.isTextFile() && node.hasByteSource()
                    && shouldIndexResource(node)) {
                byte[] bytes = readNodeBytes(node);
                if (bytes != null) {
                    resources.add(new ResourceIndexEntry(node.getFullPath(), bytes, true));
                }
            }
        }
        return new WorkspaceIndex(classes, resources, classEntries);
    }

    private static ClassIndexEntry indexClass(FileTreeNode node) {
        byte[] bytes = readNodeBytes(node);
        if (bytes == null) {
            return null;
        }

        String fullPath = node.getFullPath();
        String internalName = ClassNameUtil.normalizeInternalName(fullPath);
        String simpleName = ClassNameUtil.simpleName(internalName);
        String superName = null;
        List<String> interfaces = List.of();
        List<MemberIndexEntry> methods = new ArrayList<>();
        List<MemberIndexEntry> fields = new ArrayList<>();

        Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
        if (metadata.isPresent()) {
            ClassFileMetadata meta = metadata.get();
            internalName = meta.internalName();
            simpleName = ClassNameUtil.simpleName(internalName);
            superName = meta.superName();
            interfaces = meta.interfaces();
            for (ClassFileMetadata.MemberInfo field : meta.fields()) {
                fields.add(new MemberIndexEntry(node.getFullPath(), field.name(), field.descriptor()));
            }
            for (ClassFileMetadata.MemberInfo method : meta.methods()) {
                methods.add(new MemberIndexEntry(node.getFullPath(), method.name(), method.descriptor()));
            }
        } else {
            log.warn("解析类元数据失败: {}", node.getFullPath());
        }

        // 使用 node::resolveBytes 延迟加载：索引构建时只解析元数据,
        // 字节码在首次打开 class 时才缓存到节点,避免大型 JAR 预热阶段占用过多内存
        return new ClassIndexEntry(node.getFullPath(), internalName, simpleName,
                node::resolveBytes, methods, fields, superName, interfaces);
    }

    private static boolean shouldIndexResource(FileTreeNode node) {
        long size = node.getSize();
        if (size <= 0) {
            log.debug("跳过大小未知的资源文件: {}", node.getFullPath());
            return false;
        }
        if (size > MAX_INDEXED_RESOURCE_BYTES) {
            log.info("跳过过大资源文件,不加入工作区索引: {} ({} 字节)",
                    node.getFullPath(), size);
            return false;
        }
        return true;
    }

    private static byte[] readNodeBytes(FileTreeNode node) {
        try {
            return node.readBytes();
        } catch (IOException e) {
            log.warn("读取索引文件失败: {}", node.getFullPath(), e);
            return null;
        }
    }

    private static void putClassEntry(Map<String, ClassIndexEntry> classEntries,
                                      ClassIndexEntry entry) {
        putClassEntryAlias(classEntries, entry.internalName(), entry);
        putClassEntryAlias(classEntries, entry.fullPath(), entry);
        putClassEntryAlias(classEntries,
                ClassNameUtil.stripContainerClassPrefix(entry.fullPath()), entry);
    }

    private static void putClassEntryAlias(Map<String, ClassIndexEntry> classEntries,
                                           String className, ClassIndexEntry entry) {
        String normalized = ClassNameUtil.normalizeInternalName(className);
        if (!normalized.isBlank()) {
            classEntries.putIfAbsent(normalized, entry);
            String stripped = ClassNameUtil.stripContainerClassPrefix(normalized);
            if (!stripped.isBlank()) {
                classEntries.putIfAbsent(stripped, entry);
            }
        }
    }

    /** @return 该工作区中所有类的索引条目(不可变列表) */
    public List<ClassIndexEntry> classes() {
        return classes;
    }

    /** @return 该工作区中所有资源的索引条目(不可变列表) */
    public List<ResourceIndexEntry> resources() {
        return resources;
    }

    /**
     * 根据内部名称获取类的字节码数据
     *
     * @param internalName 类的内部名称
     * @return 该类对应的字节码字节数组,若未找到则返回 null
     */
    public byte[] getClassBytes(String internalName) {
        ClassIndexEntry entry = findClass(internalName);
        return entry == null ? null : entry.bytes();
    }

    /**
     * 根据内部名称获取类索引条目,支持容器前缀和 nested jar 前缀剥离后的别名
     *
     * @param internalName 类内部名称或完整 class 路径
     * @return 匹配的类索引条目,未找到返回 null
     */
    public ClassIndexEntry findClass(String internalName) {
        String normalized = ClassNameUtil.normalizeInternalName(internalName);
        ClassIndexEntry entry = classesByInternalName.get(normalized);
        if (entry == null) {
            entry = classesByInternalName.get(ClassNameUtil.stripContainerClassPrefix(normalized));
        }
        return entry;
    }

    /** @return 该工作区中所有类的完整路径列表 */
    public List<String> classPaths() {
        return classes.stream().map(ClassIndexEntry::fullPath).toList();
    }

    /**
     * 延迟懒加载并返回按路径索引的资源字节数据
     * 首次调用时构建缓存,后续调用直接返回不可变缓存
     *
     * @return 资源路径到字节数组的不可变映射
     */
    public Map<String, byte[]> resourceBytesByPath() {
        Map<String, byte[]> cached = resourceBytesByPathCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (resourceBytesByPathCache == null) {
                Map<String, byte[]> map = new LinkedHashMap<>();
                for (ResourceIndexEntry resource : resources) {
                    map.put(resource.fullPath(), resource.bytes());
                }
                resourceBytesByPathCache = Collections.unmodifiableMap(map);
            }
            return resourceBytesByPathCache;
        }
    }

    /**
     * 懒构建父类/接口到子类的反向映射,继承视图多次打开时可复用
     *
     * @return parent internal name -> child internal names
     */
    public Map<String, List<String>> subclassesByParent() {
        Map<String, List<String>> cached = subclassesByParentCache;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (subclassesByParentCache == null) {
                Map<String, List<String>> map = new LinkedHashMap<>();
                for (ClassIndexEntry cls : classes) {
                    addSubclass(map, cls.superName(), cls.internalName());
                    for (String itf : cls.interfaces()) {
                        addSubclass(map, itf, cls.internalName());
                    }
                }
                Map<String, List<String>> frozen = new LinkedHashMap<>();
                for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                    frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
                }
                subclassesByParentCache = Collections.unmodifiableMap(frozen);
            }
            return subclassesByParentCache;
        }
    }

    private static void addSubclass(Map<String, List<String>> map,
                                    String parentName, String childName) {
        if (parentName == null || parentName.isBlank()
                || childName == null || childName.isBlank()
                || parentName.equals(childName)) {
            return;
        }
        List<String> children = map.computeIfAbsent(parentName, key -> new ArrayList<>());
        if (!children.contains(childName)) {
            children.add(childName);
        }
    }

    /**
     * 构建并返回按路径索引的字节码文本
     * 每次调用都会重新生成(不缓存),因为在反编译视图与字节码视图间切换时需要最新数据
     *
     * @return 类路径到字节码文本字符串的映射
     */
    public Map<String, String> bytecodeTextByPath() {
        Map<String, String> map = new LinkedHashMap<>();
        for (ClassIndexEntry cls : classes) {
            map.put(cls.fullPath(), cls.bytecodeText());
        }
        return map;
    }
}
