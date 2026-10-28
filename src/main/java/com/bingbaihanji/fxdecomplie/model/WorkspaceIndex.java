package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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

    /** 进度上报间隔（纳秒），避免并行构建时高频刷新 UI */
    private static final long PROGRESS_REPORT_INTERVAL_NANOS = 100_000_000L;

    /** 中断检查间隔（每处理多少个节点检查一次） */
    private static final int INTERRUPT_CHECK_INTERVAL = 64;

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
        return build(root, null);
    }

    public static WorkspaceIndex build(FileTreeModel root,
                                       Consumer<BuildProgress> progressListener) {
        List<FileTreeNode> nodes = collectNodes(root);
        return build(nodes, progressListener);
    }

    /**
     * 按阶段构建索引:先 phase1(BOOT-INF/classes),后 phase2(BOOT-INF/lib)
     *
     * @param root 文件树根节点
     * @param phase 要构建的阶段(1 或 2)
     * @param progressListener 进度回调
     * @return 该阶段的索引
     */
    public static WorkspaceIndex buildPhase(FileTreeModel root, int phase,
                                            Consumer<BuildProgress> progressListener) {
        List<FileTreeNode> nodes = collectNodes(root);
        List<FileTreeNode> filtered = new ArrayList<>();
        for (FileTreeNode node : nodes) {
            if (node != null && node.getPhase() == phase) {
                filtered.add(node);
            }
        }
        return build(filtered, progressListener);
    }

    /**
     * 从缓存数据构建索引(不读取字节码,不含 ByteLoader)
     *
     * @param classes 缓存的 class 索引条目
     * @return 重建的索引(资源列表为空,需从文件树重新索引)
     */
    public static WorkspaceIndex buildFromCache(List<ClassIndexEntry> classes) {
        Map<String, ClassIndexEntry> classEntries = new LinkedHashMap<>();
        for (ClassIndexEntry entry : classes) {
            putClassEntry(classEntries, entry);
        }
        return new WorkspaceIndex(classes, List.of(), classEntries);
    }

    public static WorkspaceIndex buildFromCache(List<ClassIndexEntry> classes,
                                                List<ResourceIndexEntry> resources) {
        Map<String, ClassIndexEntry> classEntries = new LinkedHashMap<>();
        List<ClassIndexEntry> safeClasses = classes == null ? List.of() : classes;
        for (ClassIndexEntry entry : safeClasses) {
            if (entry != null) {
                putClassEntry(classEntries, entry);
            }
        }
        List<ClassIndexEntry> validClasses = safeClasses.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        return new WorkspaceIndex(validClasses,
                resources == null ? List.of() : resources, classEntries);
    }

    /**
     * 合并两个索引,返回合并后的新索引(this 的条目在前,other 的条目在后)
     */
    public static WorkspaceIndex merge(WorkspaceIndex a, WorkspaceIndex b) {
        List<ClassIndexEntry> mergedClasses = new ArrayList<>(a.classes());
        mergedClasses.addAll(b.classes());
        List<ResourceIndexEntry> mergedResources = new ArrayList<>(a.resources());
        mergedResources.addAll(b.resources());
        Map<String, ClassIndexEntry> mergedMap = new LinkedHashMap<>(a.classesByInternalName);
        mergedMap.putAll(b.classesByInternalName);
        return new WorkspaceIndex(mergedClasses, mergedResources, mergedMap);
    }

    private static List<FileTreeNode> collectNodes(FileTreeModel root) {
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
        return nodes;
    }

    /**
     * 从已提取的 FileTreeNode 列表构建索引(线程安全,可在后台线程调用)
     *
     * @param nodes class 和资源 FileTreeNode 列表
     * @return 工作区索引
     */
    public static WorkspaceIndex build(List<FileTreeNode> nodes) {
        return build(nodes, null);
    }

    /**
     * 从已提取的 FileTreeNode 列表并行构建索引.
     *
     * <p>进度回调可能在工作线程上执行；通知会串行化并保证单调递增，频率限制为约 100ms 一次，
     * 完成时发送一次 phase="done" 的终态通知.构建被中断时不会发布半成品索引.</p>
     *
     * @param nodes class 和资源 FileTreeNode 列表
     * @param progressListener 构建进度回调,可为空
     * @return 工作区索引
     */
    public static WorkspaceIndex build(List<FileTreeNode> nodes,
                                       Consumer<BuildProgress> progressListener) {
        List<FileTreeNode> classNodes = new ArrayList<>();
        List<FileTreeNode> resourceNodes = new ArrayList<>();
        for (FileTreeNode node : nodes) {
            if (node == null) {
                continue;
            }
            if (node.isClassFile() && node.hasByteSource()) {
                classNodes.add(node);
            } else if (node.isTextFile() && node.hasByteSource()
                    && !node.isImageFile() && shouldIndexResource(node)) {
                resourceNodes.add(node);
            }
        }

        int total = classNodes.size() + resourceNodes.size();
        if (total == 0) {
            notifyProgress(progressListener, 1, 1, "done");
            return new WorkspaceIndex(List.of(), List.of(), Map.of());
        }

        ClassIndexEntry[] classResults = new ClassIndexEntry[classNodes.size()];
        ResourceIndexEntry[] resourceResults = new ResourceIndexEntry[resourceNodes.size()];
        ProgressReporter reporter = new ProgressReporter(progressListener, total);

        int workers = Math.max(2, Math.min(16,
                Runtime.getRuntime().availableProcessors() - 1));
        boolean interrupted = false;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = new ArrayList<>(workers * 2);
            tasks.addAll(strideTasks(classNodes.size(), workers, index -> {
                try {
                    classResults[index] = indexClass(classNodes.get(index));
                } catch (RuntimeException e) {
                    log.warn("索引 class 失败: {}", classNodes.get(index).getFullPath(), e);
                } finally {
                    reporter.advance("class");
                }
            }));
            tasks.addAll(strideTasks(resourceNodes.size(), workers, index -> {
                try {
                    resourceResults[index] = indexResource(resourceNodes.get(index));
                } catch (RuntimeException e) {
                    log.warn("索引资源失败: {}", resourceNodes.get(index).getFullPath(), e);
                } finally {
                    reporter.advance("resource");
                }
            }));
            List<Future<Void>> futures;
            try {
                futures = executor.invokeAll(tasks);
            } catch (InterruptedException e) {
                interrupted = true;
                futures = List.of();
            }
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (CancellationException e) {
                    interrupted = true;
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    if (cause instanceof RuntimeException runtime) {
                        throw runtime;
                    }
                    throw new IllegalStateException("索引任务失败", cause);
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }

        // 构建线程被中断时不要发布半成品索引;
        // 清除中断标志,避免复用 IO 线程池的线程因残留标志跳过后续任务
        if (interrupted || reporter.processed() < total) {
            Thread.interrupted();
            throw new CancellationException("工作区索引构建被中断");
        }

        List<ClassIndexEntry> classes = new ArrayList<>(classNodes.size());
        Map<String, ClassIndexEntry> classEntries = new LinkedHashMap<>();
        for (ClassIndexEntry entry : classResults) {
            if (entry != null) {
                classes.add(entry);
                putClassEntry(classEntries, entry);
            }
        }
        List<ResourceIndexEntry> resources = new ArrayList<>(resourceNodes.size());
        for (ResourceIndexEntry entry : resourceResults) {
            if (entry != null) {
                resources.add(entry);
            }
        }
        reporter.finish();
        return new WorkspaceIndex(classes, resources, classEntries);
    }

    private static List<Callable<Void>> strideTasks(int size, int maxWorkers, IntConsumer body) {
        if (size <= 0) {
            return List.of();
        }
        int workerCount = Math.min(maxWorkers, size);
        List<Callable<Void>> tasks = new ArrayList<>(workerCount);
        for (int worker = 0; worker < workerCount; worker++) {
            int start = worker;
            tasks.add(() -> {
                int sinceCheck = 0;
                for (int index = start; index < size; index += workerCount) {
                    if (++sinceCheck >= INTERRUPT_CHECK_INTERVAL) {
                        sinceCheck = 0;
                        if (Thread.currentThread().isInterrupted()) {
                            return null;
                        }
                    }
                    body.accept(index);
                }
                return null;
            });
        }
        return tasks;
    }

    private static ResourceIndexEntry indexResource(FileTreeNode node) {
        byte[] bytes = readNodeBytes(node);
        return bytes == null ? null
                : new ResourceIndexEntry(node.getFullPath(), bytes, true);
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

        // 使用 node::resolveBytes 延迟加载:索引构建时只解析元数据,
        // 字节码在首次打开 class 时才缓存到节点,避免大型 JAR 预热阶段占用过多内存
        return new ClassIndexEntry(node.getFullPath(), internalName, simpleName,
                node::resolveBytes, methods, fields, superName, interfaces, node);
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

    private static void notifyProgress(Consumer<BuildProgress> listener, int processed,
                                       int total, String phase) {
        if (listener != null && total > 0) {
            listener.accept(new BuildProgress(processed, total, phase));
        }
    }

    private static final class ProgressReporter {

        private final Consumer<BuildProgress> listener;
        private final int total;
        private final AtomicInteger processed = new AtomicInteger();
        private final AtomicLong lastReportNanos = new AtomicLong(System.nanoTime());
        private int lastEmitted = -1;

        private ProgressReporter(Consumer<BuildProgress> listener, int total) {
            this.listener = listener;
            this.total = total;
        }

        private int processed() {
            return processed.get();
        }

        private void advance(String phase) {
            int done = processed.incrementAndGet();
            if (done >= total) {
                return;
            }
            long now = System.nanoTime();
            long last = lastReportNanos.get();
            if (now - last >= PROGRESS_REPORT_INTERVAL_NANOS
                    && lastReportNanos.compareAndSet(last, now)) {
                emit(done, phase);
            }
        }

        private synchronized void emit(int done, String phase) {
            if (done <= lastEmitted) {
                return;
            }
            lastEmitted = done;
            notifyProgress(listener, done, total, phase);
        }

        private void finish() {
            synchronized (this) {
                lastEmitted = total;
                notifyProgress(listener, total, total, "done");
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

    public record BuildProgress(int processed, int total, String phase) {

        public double progress() {
            if (total <= 0) {
                return 1.0;
            }
            return Math.min(1.0, Math.max(0.0, processed / (double) total));
        }
    }
}
