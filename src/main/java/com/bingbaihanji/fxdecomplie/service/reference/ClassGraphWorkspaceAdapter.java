package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.classgraph.core.AnnotationInfo;
import com.bingbaihanji.classgraph.core.AnnotationInfoList;
import com.bingbaihanji.classgraph.core.ClassInfo;
import com.bingbaihanji.classgraph.core.ClassInfoList;
import com.bingbaihanji.classgraph.core.ScanResult;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 将 Workspace / WorkspaceIndex 中的 class bytes 转换为 ScanResult
 *
 * <p>基于 ClassGraph 4.8.184</p>
 * <p>Copyright Luke Hutchison</p>
 * <p>MIT License</p>
 *
 * @author bingbaihanji
 * @date 2026-07-20
 */
public final class ClassGraphWorkspaceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClassGraphWorkspaceAdapter.class);

    private ClassGraphWorkspaceAdapter() {
        throw new AssertionError("utility class");
    }

    /**
     * 扫描工作区所有类并构建 ScanResult
     *
     * @param workspace 目标工作区
     * @return 扫描结果
     */
    public static ScanResult scan(Workspace workspace) {
        return scan(workspace, null);
    }

    /**
     * 扫描工作区类并构建 ScanResult,可按类名过滤
     *
     * @param workspace   目标工作区
     * @param classFilter 类名过滤器,null 表示不过滤
     * @return 扫描结果
     */
    public static ScanResult scan(Workspace workspace, Predicate<String> classFilter) {
        if (workspace == null) {
            return new ScanResult(Map.of());
        }
        WorkspaceIndex index = workspace.getIndex();
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return new ScanResult(Map.of());
        }

        Map<String, ClassInfo> classInfoByName = new LinkedHashMap<>();
        List<ClassMetadata> metadatas = new ArrayList<>();
        int skippedFileCount = 0;

        for (ClassIndexEntry entry : index.classes()) {
            String internalName = entry.internalName();
            if (classFilter != null && !classFilter.test(internalName)) {
                continue;
            }
            byte[] bytes = readBytes(entry);
            if (bytes == null) {
                skippedFileCount++;
                continue;
            }
            ClassMetadata metadata = parse(bytes, entry.fullPath());
            if (metadata == null) {
                skippedFileCount++;
                continue;
            }
            metadatas.add(metadata);
        }

        for (ClassMetadata metadata : metadatas) {
            ClassInfo ci = toClassInfo(metadata, classInfoByName);
            ci.setScannedClass(true);
            ci.setExternalClass(false);
            ci.setFullPath(metadata.fullPath);
            if (metadata.sourceFile != null) {
                ci.setSourceFile(metadata.sourceFile);
            }
        }

        linkRelations(classInfoByName, metadatas);

        ScanResult result = new ScanResult(classInfoByName);
        result.setSkippedFileCount(skippedFileCount);
        return result;
    }

    private static byte[] readBytes(ClassIndexEntry entry) {
        FileTreeNode node = entry.node();
        if (node != null) {
            try {
                return node.readBytes();
            } catch (IOException e) {
                log.warn("Failed to read class bytes: {}", node.getFullPath(), e);
            }
        }
        return entry.bytes();
    }

    private static ClassMetadata parse(byte[] bytes, String fullPath) {
        try {
            ClassReader reader = new ClassReader(bytes);
            MetadataVisitor visitor = new MetadataVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new ClassMetadata(visitor.name, visitor.access, visitor.superName,
                    List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations),
                    List.copyOf(visitor.methods), visitor.sourceFile, fullPath);
        } catch (Exception e) {
            log.warn("ASM parse failed for: {}", fullPath, e);
            return null;
        }
    }

    private static ClassInfo toClassInfo(ClassMetadata metadata,
                                         Map<String, ClassInfo> classInfoByName) {
        return classInfoByName.computeIfAbsent(metadata.name,
                name -> new ClassInfo(name, metadata.access));
    }

    private static void linkRelations(Map<String, ClassInfo> classInfoByName,
                                      List<ClassMetadata> metadatas) {
        for (ClassMetadata metadata : metadatas) {
            ClassInfo ci = classInfoByName.get(metadata.name);
            if (ci == null) {
                continue;
            }

            // superclass
            if (metadata.superName != null) {
                ClassInfo superClass = classInfoByName.get(metadata.superName);
                if (superClass == null) {
                    superClass = new ClassInfo(metadata.superName, 0);
                    superClass.setExternalClass(true);
                    classInfoByName.put(metadata.superName, superClass);
                }
                ci.setSuperclass(superClass);
                superClass.addSubclass(ci);
            }

            // interfaces
            ClassInfoList interfaces = new ClassInfoList();
            for (String itf : metadata.interfaces) {
                ClassInfo itfInfo = classInfoByName.get(itf);
                if (itfInfo == null) {
                    itfInfo = new ClassInfo(itf, Opcodes.ACC_INTERFACE);
                    itfInfo.setExternalClass(true);
                    classInfoByName.put(itf, itfInfo);
                }
                interfaces.add(itfInfo);
                itfInfo.addImplementingClass(ci);
            }
            ci.setInterfaces(interfaces);

            // annotations
            AnnotationInfoList annotations = new AnnotationInfoList();
            for (String ann : metadata.annotations) {
                annotations.add(new AnnotationInfo(ann));
                ClassInfo annInfo = classInfoByName.get(ann);
                if (annInfo == null) {
                    annInfo = new ClassInfo(ann, Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | 0x00002000);
                    annInfo.setExternalClass(true);
                    classInfoByName.put(ann, annInfo);
                }
            }
            ci.setAnnotationInfo(annotations);
        }
    }

    private static final class MetadataVisitor extends ClassVisitor {
        String name;
        int access;
        String superName;
        String sourceFile;
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();
        final List<MethodData> methods = new ArrayList<>();

        MetadataVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;
            if (interfaces != null) {
                this.interfaces.addAll(List.of(interfaces));
            }
        }

        @Override
        public void visitSource(String source, String debug) {
            this.sourceFile = source;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            Type type = Type.getType(descriptor);
            String internalName = type.getInternalName();
            if (internalName != null && !internalName.isBlank()) {
                annotations.add(internalName);
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                        String signature, String[] exceptions) {
            methods.add(new MethodData(name, descriptor, access));
            return null;
        }
    }

    /**
     * 轻量级方法数据（仅名称、描述符、访问标志）
     */
    private record MethodData(String name, String descriptor, int access) {}

    private record ClassMetadata(String name, int access, String superName,
                                 List<String> interfaces, List<String> annotations,
                                 List<MethodData> methods, String sourceFile,
                                 String fullPath) {}
}
