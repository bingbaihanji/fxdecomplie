package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfoList;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * ClassGraph 实现的扫描服务 — 将 ClassGraph 内部 API 适配为 ClassScanService。
 *
 * <p>这是 classgraph 包的唯一切入点，所有业务代码通过此实现访问扫描能力。</p>
 *
 * @author bingbaihanji
 */
public class ClassGraphClassScanService implements ClassScanService {

    private static final Logger log = LoggerFactory.getLogger(ClassGraphClassScanService.class);

    @Override
    public ClassScanResult scan(ClassScanRequest request) {
        if (request == null || request.workspace() == null) {
            return new ClassScanResult(Map.of());
        }

        Workspace workspace = request.workspace();
        Predicate<String> classFilter = request.classFilter();
        ClassScanOptions options = request.options();

        WorkspaceIndex index = workspace.getIndex();
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return new ClassScanResult(Map.of(), new ScanStatistics(0, 0, 0, 0, 0));
        }

        long start = System.currentTimeMillis();
        int skippedFileCount = 0;

        // Phase 1: ASM 解析所有类，提取元数据
        List<RawClassMetadata> rawMetadatas = new ArrayList<>();
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
            RawClassMetadata raw = parseClassBytes(bytes, entry.fullPath());
            if (raw == null) {
                skippedFileCount++;
                continue;
            }
            rawMetadatas.add(raw);
        }

        // Phase 2: 构建 ClassInfo 图（用于继承/接口/注解关系链接）
        Map<String, ClassInfo> classInfoByName = new LinkedHashMap<>();
        for (RawClassMetadata raw : rawMetadatas) {
            ClassInfo ci = getOrCreateClassInfo(raw.name, raw.access, classInfoByName);
            ci.setScannedClass(true);
            ci.setExternalClass(false);
            ci.setFullPath(raw.fullPath);
            if (raw.sourceFile != null) {
                ci.setSourceFile(raw.sourceFile);
            }
        }

        // Phase 3: 链接继承/接口关系
        for (RawClassMetadata raw : rawMetadatas) {
            ClassInfo ci = classInfoByName.get(raw.name);
            if (ci == null) continue;
            linkRelations(ci, raw, classInfoByName);
        }

        // Phase 4: 转换为项目 DTO（合并 ClassInfo 关系 + RawClassMetadata 字段/方法细节）
        Map<String, ClassMetadata> result = new LinkedHashMap<>();
        Map<String, RawClassMetadata> rawByName = new LinkedHashMap<>();
        for (RawClassMetadata raw : rawMetadatas) {
            rawByName.put(raw.name, raw);
        }

        for (ClassInfo ci : classInfoByName.values()) {
            RawClassMetadata raw = rawByName.get(ci.getName());
            ClassMetadata metadata;
            if (raw != null) {
                // 扫描到的类：从 RawClassMetadata 获取字段/方法/注解细节
                metadata = convertToMetadata(ci, raw, options);
            } else {
                // 外部类（仅作为超类/接口被引用）：只有基本信息
                metadata = convertExternalToMetadata(ci);
            }
            result.put(ci.getName(), metadata);
        }

        long elapsed = System.currentTimeMillis() - start;
        int scannedCount = rawMetadatas.size();
        int totalCount = result.size();
        int externalCount = totalCount - scannedCount;

        ScanStatistics stats = new ScanStatistics(
            totalCount, scannedCount, externalCount, skippedFileCount, elapsed
        );

        return new ClassScanResult(result, stats);
    }

    @Override
    public CompletableFuture<ClassScanResult> scanAsync(ClassScanRequest request) {
        CompletableFuture<ClassScanResult> future = new CompletableFuture<>();
        BackgroundTasks.run(BackgroundTasks.PoolType.IO,
            "ClassScan-" + request.workspace().getName(), () -> {
                try {
                    ClassScanResult result = scan(request);
                    future.complete(result);
                } catch (Exception e) {
                    future.completeExceptionally(e);
                }
            });
        return future;
    }

    @Override
    public void cancel(ClassScanRequest request) {
        // 当前实现不支持取消单个请求
        // 保留此方法供将来实现（Phase 4 Pipeline 拆分后可支持）
    }

    // ─── 内部实现 ───

    private static byte[] readBytes(ClassIndexEntry entry) {
        try {
            return entry.bytes();
        } catch (Exception e) {
            log.warn("Failed to read class bytes: {}", entry.fullPath(), e);
            return null;
        }
    }

    private static RawClassMetadata parseClassBytes(byte[] bytes, String fullPath) {
        try {
            ClassReader reader = new ClassReader(bytes);
            RawVisitor visitor = new RawVisitor();
            reader.accept(visitor,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new RawClassMetadata(
                visitor.name, visitor.access, visitor.superName,
                List.copyOf(visitor.interfaces),
                List.copyOf(visitor.fieldMetas),
                List.copyOf(visitor.methodMetas),
                List.copyOf(visitor.annotations),
                visitor.sourceFile, fullPath
            );
        } catch (Exception e) {
            log.warn("ASM parse failed for: {}", fullPath, e);
            return null;
        }
    }

    private static ClassInfo getOrCreateClassInfo(String name, int modifiers,
                                                   Map<String, ClassInfo> map) {
        return map.computeIfAbsent(name, n -> new ClassInfo(n, modifiers));
    }

    private static void linkRelations(ClassInfo ci, RawClassMetadata raw,
                                       Map<String, ClassInfo> classInfoByName) {
        // superclass
        if (raw.superName != null && !"java/lang/Object".equals(raw.superName)) {
            ClassInfo superClass = classInfoByName.computeIfAbsent(raw.superName,
                n -> {
                    ClassInfo ext = new ClassInfo(n, 0);
                    ext.setExternalClass(true);
                    return ext;
                });
            ci.setSuperclass(superClass);
            superClass.addSubclass(ci);
        }

        // interfaces
        ClassInfoList interfaceList = new ClassInfoList();
        for (String itfName : raw.interfaces) {
            ClassInfo itfInfo = classInfoByName.computeIfAbsent(itfName, n -> {
                ClassInfo ext = new ClassInfo(n, Opcodes.ACC_INTERFACE);
                ext.setExternalClass(true);
                return ext;
            });
            interfaceList.add(itfInfo);
            itfInfo.addImplementingClass(ci);
        }
        ci.setInterfaces(interfaceList);
    }

    private static ClassMetadata convertToMetadata(ClassInfo ci, RawClassMetadata raw,
                                                    ClassScanOptions options) {
        List<FieldMetadata> fields = List.of();
        List<MethodMetadata> methods = List.of();
        List<AnnotationMetadata> annotations = List.of();

        if (options.enableFieldInfo()) {
            fields = new ArrayList<>();
            for (RawFieldData fd : raw.fields) {
                fields.add(new FieldMetadata(
                    fd.name, fd.descriptor, fd.signature,
                    fd.access, fd.constantValue, List.of()
                ));
            }
            fields = List.copyOf(fields);
        }

        if (options.enableMethodInfo()) {
            methods = new ArrayList<>();
            for (RawMethodData md : raw.methods) {
                methods.add(new MethodMetadata(
                    md.name, md.descriptor, md.signature,
                    md.access,
                    parseParameterTypes(md.descriptor),
                    parseReturnType(md.descriptor),
                    List.of()
                ));
            }
            methods = List.copyOf(methods);
        }

        if (options.enableAnnotationInfo()) {
            annotations = new ArrayList<>();
            for (String annName : raw.annotations) {
                annotations.add(new AnnotationMetadata(annName, Map.of()));
            }
            annotations = List.copyOf(annotations);
        }

        // 使用 raw metadata 获取接口和父类关系（避免 ClassInfo.getInterfaces()/getSuperclass()
        // 内部依赖 ScanResult 导致 NPE）
        List<String> interfaceNames = raw.interfaces != null
            ? raw.interfaces : List.of();

        return new ClassMetadata(
            ci.getName(), ci.getModifiers(),
            raw.superName, // 直接使用 raw metadata 中的父类名
            interfaceNames, fields, methods, annotations,
            ci.getFullPath(), ci.isExternalClass()
        );
    }

    private static ClassMetadata convertExternalToMetadata(ClassInfo ci) {
        // 外部类只有基本信息，没有字段/方法/注解/关系细节
        return new ClassMetadata(
            ci.getName(), ci.getModifiers(),
            null, // 外部类的父类名无法从 ClassInfo 获取（需要 ScanResult）
            List.of(), List.of(), List.of(), List.of(),
            ci.getFullPath(), true
        );
    }

    /**
     * 从方法描述符中解析参数类型列表。
     * 例如 "(ILjava/lang/String;)V" → ["I", "java/lang/String"]
     */
    private static List<String> parseParameterTypes(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return List.of();
        org.objectweb.asm.Type methodType = org.objectweb.asm.Type.getMethodType(descriptor);
        org.objectweb.asm.Type[] argTypes = methodType.getArgumentTypes();
        List<String> result = new ArrayList<>(argTypes.length);
        for (org.objectweb.asm.Type t : argTypes) {
            result.add(t.getInternalName());
        }
        return List.copyOf(result);
    }

    /**
     * 从方法描述符中解析返回类型。
     * 例如 "(ILjava/lang/String;)V" → "V"
     */
    private static String parseReturnType(String descriptor) {
        if (descriptor == null || descriptor.isBlank()) return "V";
        org.objectweb.asm.Type methodType = org.objectweb.asm.Type.getMethodType(descriptor);
        return methodType.getReturnType().getInternalName();
    }

    // ─── 内部类型 ───

    private record RawClassMetadata(String name, int access, String superName,
                                     List<String> interfaces,
                                     List<RawFieldData> fields,
                                     List<RawMethodData> methods,
                                     List<String> annotations,
                                     String sourceFile, String fullPath) {}

    private record RawFieldData(String name, String descriptor, String signature,
                                 int access, Object constantValue) {}

    private record RawMethodData(String name, String descriptor, String signature,
                                  int access) {}

    private static final class RawVisitor extends ClassVisitor {
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();
        final List<RawFieldData> fieldMetas = new ArrayList<>();
        final List<RawMethodData> methodMetas = new ArrayList<>();
        String name;
        int access;
        String superName;
        String sourceFile;

        RawVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String sig,
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
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            org.objectweb.asm.Type t = org.objectweb.asm.Type.getType(desc);
            String n = t.getInternalName();
            if (n != null && !n.isBlank()) {
                annotations.add(n);
            }
            return null;
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                          String desc, String sig,
                                                          Object value) {
            fieldMetas.add(new RawFieldData(name, desc, sig, access, value));
            return null;
        }

        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                            String desc, String sig,
                                                            String[] exceptions) {
            methodMetas.add(new RawMethodData(name, desc, sig, access));
            return null;
        }
    }
}
