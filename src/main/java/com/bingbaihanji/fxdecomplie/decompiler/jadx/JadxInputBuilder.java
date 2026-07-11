package com.bingbaihanji.fxdecomplie.decompiler.jadx;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaClassReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaLoadResult;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.util.ClassNameUtil;

import java.util.*;

/**
 * 将项目工作区上下文转换为 jadx 可消费的 class reader 集合。
 */
public final class JadxInputBuilder {

    private static final Set<String> PLATFORM_PREFIXES = Set.of(
            "java/", "javax/", "jdk/", "sun/", "com/sun/", "org/w3c/", "org/xml/");

    private static boolean addClass(Map<String, byte[]> classes, String internalName, byte[] bytes) {
        String normalized = ClassNameUtil.normalizeInternalName(internalName);
        if (normalized.isBlank() || classes.containsKey(normalized)) {
            return false;
        }
        classes.put(normalized, bytes.clone());
        return true;
    }

    private static void enqueueReferences(Queue<DependencyRef> queue,
                                          Set<String> queued,
                                          ClassFileMetadata metadata,
                                          int depth) {
        enqueue(queue, queued, metadata.superName(), depth);
        for (String iface : metadata.interfaces()) {
            enqueue(queue, queued, iface, depth);
        }
        for (String ref : metadata.referencedClasses()) {
            enqueue(queue, queued, ref, depth);
        }
        for (ClassFileMetadata.MemberInfo field : metadata.fields()) {
            enqueueDescriptorReferences(queue, queued, field.descriptor(), depth);
        }
        for (ClassFileMetadata.MemberInfo method : metadata.methods()) {
            enqueueDescriptorReferences(queue, queued, method.descriptor(), depth);
        }
    }

    private static void enqueueOuterClasses(Queue<DependencyRef> queue,
                                            Set<String> queued,
                                            String internalName,
                                            int depth) {
        String normalized = ClassNameUtil.normalizeInternalName(internalName);
        int slash = normalized.lastIndexOf('/');
        String pkg = slash >= 0 ? normalized.substring(0, slash + 1) : "";
        String simple = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        int dollar = simple.lastIndexOf('$');
        while (dollar > 0) {
            enqueue(queue, queued, pkg + simple.substring(0, dollar), depth);
            dollar = simple.lastIndexOf('$', dollar - 1);
        }
    }

    private static void enqueueDescriptorReferences(Queue<DependencyRef> queue,
                                                    Set<String> queued,
                                                    String descriptor,
                                                    int depth) {
        if (descriptor == null || descriptor.isBlank()) {
            return;
        }
        int index = 0;
        while (index < descriptor.length()) {
            int start = descriptor.indexOf('L', index);
            if (start < 0) {
                return;
            }
            int end = descriptor.indexOf(';', start + 1);
            if (end < 0) {
                return;
            }
            String name = descriptor.substring(start + 1, end);
            int generic = name.indexOf('<');
            if (generic >= 0) {
                name = name.substring(0, generic);
            }
            enqueue(queue, queued, name, depth);
            index = end + 1;
        }
    }

    private static void enqueue(Queue<DependencyRef> queue, Set<String> queued,
                                String internalName, int depth) {
        String normalized = ClassNameUtil.normalizeInternalName(internalName);
        if (!normalized.isBlank() && queued.add(normalized)) {
            queue.add(new DependencyRef(normalized, depth));
        }
    }

    private static boolean isWorkspaceDependencyCandidate(String internalName,
                                                          String targetType,
                                                          Map<String, byte[]> loaded) {
        if (internalName.isBlank()
                || internalName.equals(targetType)
                || loaded.containsKey(internalName)) {
            return false;
        }
        for (String prefix : PLATFORM_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }

    public JadxInputPlan build(JadxDecompilerRequest request) {
        byte[] targetBytes = request.classBytes();
        if (targetBytes == null || targetBytes.length == 0) {
            throw new IllegalArgumentException("class bytes is empty");
        }

        Optional<ClassFileMetadata> targetMetadata = ClassFileParser.tryParse(targetBytes);
        String targetType = targetMetadata.map(ClassFileMetadata::internalName)
                .filter(name -> !name.isBlank())
                .orElseGet(request::typeName);
        if (targetType == null || targetType.isBlank()) {
            targetType = ClassNameUtil.normalizeInternalName(request.classFilePath());
        }
        if (targetType.isBlank()) {
            targetType = "Unknown";
        }

        Map<String, byte[]> classes = new LinkedHashMap<>();
        addClass(classes, targetType, targetBytes);

        JadxAdapterOptions adapterOptions = JadxAdapterOptions.from(request.options());
        if (adapterOptions.loadWorkspaceDependencies()
                && adapterOptions.workspaceDependencyLimit() > 0
                && adapterOptions.workspaceDependencyDepth() > 0
                && targetMetadata.isPresent()) {
            addWorkspaceDependencies(classes, targetType, targetMetadata.get(),
                    request.context(), adapterOptions);
        }

        List<JavaClassReader> readers = new ArrayList<>(classes.size());
        int id = 1;
        for (var entry : classes.entrySet()) {
            readers.add(new JavaClassReader(id++, entry.getKey() + ".class", entry.getValue()));
        }
        return new JadxInputPlan(new JavaLoadResult(readers), targetType,
                readers.size(), Math.max(0, readers.size() - 1));
    }

    private void addWorkspaceDependencies(Map<String, byte[]> classes,
                                          String targetType,
                                          ClassFileMetadata targetMetadata,
                                          DecompilerContext context,
                                          JadxAdapterOptions options) {
        Queue<DependencyRef> queue = new ArrayDeque<>();
        Set<String> queued = new LinkedHashSet<>();
        enqueueReferences(queue, queued, targetMetadata, 1);
        enqueueOuterClasses(queue, queued, targetType, 1);

        int loadedDependencies = 0;
        while (!queue.isEmpty() && loadedDependencies < options.workspaceDependencyLimit()) {
            DependencyRef ref = queue.remove();
            String internalName = ClassNameUtil.normalizeInternalName(ref.internalName());
            if (!isWorkspaceDependencyCandidate(internalName, targetType, classes)) {
                continue;
            }
            byte[] bytes = context.resolveClassBytes(internalName);
            if (bytes == null || bytes.length == 0) {
                continue;
            }

            Optional<ClassFileMetadata> metadata = ClassFileParser.tryParse(bytes);
            String actualName = metadata.map(ClassFileMetadata::internalName)
                    .filter(name -> !name.isBlank())
                    .orElse(internalName);
            if (!addClass(classes, actualName, bytes)) {
                continue;
            }
            loadedDependencies++;

            int nextDepth = ref.depth() + 1;
            if (nextDepth <= options.workspaceDependencyDepth()) {
                metadata.ifPresent(meta -> enqueueReferences(queue, queued, meta, nextDepth));
                enqueueOuterClasses(queue, queued, actualName, nextDepth);
            }
        }
    }

    private record DependencyRef(String internalName, int depth) {
    }
}
