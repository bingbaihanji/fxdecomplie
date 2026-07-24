# ClassGraph 包重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 classgraph 迁移代码重构为边界清晰、低耦合、可替换的模块化架构，通过项目级适配层隔离业务代码。

**Architecture:** 8 个阶段。Phase 1 建业务适配层（Facade + Adapter），Phase 2 补回归测试 + 修 Bug + 命名规范化，Phase 3 配置不可变化（Builder + Immutable），Phase 4 Scanner Pipeline 拆分（Chain of Responsibility + Context），Phase 5 ClassParser DTO 化（DTO + Strategy），Phase 6 Classpath 封装 + Handler 可插拔化（State + Registry），Phase 7 Metadata 读写分离（CQRS-lite），Phase 8 日志解耦 + 杂项清理（Adapter + Null Object）。Phase 5/6 可并行推进。

**Tech Stack:** JDK 25, JUnit 5.12.1, ASM 9.9, JavaFX 25.0.1

---

## 文件结构总览

```
新增文件:
  src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/
    ClassScanService.java          # 扫描服务接口
    ClassGraphClassScanService.java # ClassGraph 实现
    ClassScanRequest.java           # 扫描请求 DTO
    ClassScanResult.java            # 扫描结果 DTO
    ClassScanOptions.java           # 扫描选项 DTO
    ClassMetadata.java              # 类元数据 DTO
    MethodMetadata.java             # 方法元数据 DTO
    FieldMetadata.java              # 字段元数据 DTO
    AnnotationMetadata.java         # 注解元数据 DTO
    ScanStatistics.java             # 扫描统计 DTO

  src/test/java/com/bingbaihanji/fxdecomplie/service/classscan/
    ClassGraphClassScanServiceTest.java  # 适配层测试

修改文件:
  src/main/java/com/bingbaihanji/fxdecomplie/service/reference/
    ClassGraphWorkspaceAdapter.java       # 改用 ClassScanService
    InheritanceReferenceIndex.java        # 改用 ClassScanResult
    InheritanceReferenceIndexService.java # 改用 ClassScanService
    InheritanceReferenceService.java      # 改用 ClassMetadata

后续阶段文件见各 Task 的 Files 章节。
```

---

## Phase 1: 业务隔离适配层（2-3 天）

### Task 1.1: 创建 DTO 类

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassMetadata.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/MethodMetadata.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/FieldMetadata.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/AnnotationMetadata.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ScanStatistics.java`

- [ ] **Step 1: 创建 ClassMetadata record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

/**
 * 项目级不可变类元数据 — 不暴露 ClassGraph 内部 ClassInfo。
 */
public record ClassMetadata(
    String name,
    int modifiers,
    String superclassName,
    List<String> interfaceNames,
    List<FieldMetadata> fields,
    List<MethodMetadata> methods,
    List<AnnotationMetadata> annotations,
    String fullPath,
    boolean isExternalClass
) {
    public boolean isInterface() {
        return (modifiers & 0x0200) != 0; // ACC_INTERFACE
    }

    public boolean isAnnotation() {
        return (modifiers & 0x2000) != 0; // ACC_ANNOTATION
    }

    public boolean isEnum() {
        return (modifiers & 0x4000) != 0; // ACC_ENUM
    }

    public boolean isRecord() {
        return (modifiers & 0x00000010) != 0; // ACC_RECORD
    }
}
```

- [ ] **Step 2: 创建 MethodMetadata record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

/**
 * 项目级不可变方法元数据 — 不暴露 ClassGraph 内部 MethodInfo。
 */
public record MethodMetadata(
    String name,
    String descriptor,
    String signature,   // 泛型签名，可为 null
    int modifiers,
    List<String> parameterTypes,
    String returnType,
    List<AnnotationMetadata> annotations
) {
    public boolean isStatic() {
        return (modifiers & 0x0008) != 0;
    }

    public boolean isPublic() {
        return (modifiers & 0x0001) != 0;
    }

    public boolean isPrivate() {
        return (modifiers & 0x0002) != 0;
    }

    public boolean isProtected() {
        return (modifiers & 0x0004) != 0;
    }
}
```

- [ ] **Step 3: 创建 FieldMetadata record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

/**
 * 项目级不可变字段元数据 — 不暴露 ClassGraph 内部 FieldInfo。
 */
public record FieldMetadata(
    String name,
    String typeDescriptor,
    String typeSignature,  // 泛型签名，可为 null
    int modifiers,
    Object constantValue,  // 常量初始值，可为 null
    List<AnnotationMetadata> annotations
) {
    public boolean isStatic() {
        return (modifiers & 0x0008) != 0;
    }

    public boolean isFinal() {
        return (modifiers & 0x0010) != 0;
    }

    public boolean isPublic() {
        return (modifiers & 0x0001) != 0;
    }
}
```

- [ ] **Step 4: 创建 AnnotationMetadata record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.Map;

/**
 * 项目级不可变注解元数据。
 */
public record AnnotationMetadata(
    String className,
    Map<String, Object> parameterValues  // 参数名 → 值
) {}
```

- [ ] **Step 5: 创建 ScanStatistics record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

/**
 * 扫描统计信息。
 */
public record ScanStatistics(
    int totalClasses,
    int scannedClasses,
    int externalClasses,
    int skippedFiles,
    long elapsedMs
) {}
```

- [ ] **Step 6: 验证编译**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassMetadata.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/MethodMetadata.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/FieldMetadata.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/AnnotationMetadata.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ScanStatistics.java
git commit -m "feat(classscan): add project-level metadata DTOs (ClassMetadata, MethodMetadata, etc.)

Phase 1 step 1: immutable record types that do not expose ClassGraph internals.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.2: 创建扫描请求/结果/选项 DTO

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanRequest.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanResult.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanOptions.java`

- [ ] **Step 1: 创建 ClassScanOptions record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.nio.file.Path;
import java.util.List;

/**
 * 扫描选项 — 控制扫描行为和返回的信息粒度。
 */
public record ClassScanOptions(
    boolean enableClassInfo,
    boolean enableFieldInfo,
    boolean enableMethodInfo,
    boolean enableAnnotationInfo,
    List<Path> extraClasspath,
    int maxThreads
) {
    public static final ClassScanOptions DEFAULT = new ClassScanOptions(
        true, true, true, true, List.of(), 0
    );

    /** 仅类级别信息（不含字段和方法细节） */
    public static final ClassScanOptions CLASS_LEVEL_ONLY = new ClassScanOptions(
        true, false, false, true, List.of(), 0
    );

    public ClassScanOptions {
        if (extraClasspath == null) extraClasspath = List.of();
        if (maxThreads < 0) maxThreads = 0;
    }
}
```

- [ ] **Step 2: 创建 ClassScanRequest record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import java.util.function.Predicate;

/**
 * 扫描请求 — 不可变的扫描输入参数。
 */
public record ClassScanRequest(
    Workspace workspace,
    Predicate<String> classFilter,     // 按类名过滤（internal name），null = 全扫描
    ClassScanOptions options
) {
    public ClassScanRequest {
        if (options == null) {
            options = ClassScanOptions.DEFAULT;
        }
    }

    /** 便捷构造：扫描全部类，使用默认选项 */
    public static ClassScanRequest of(Workspace workspace) {
        return new ClassScanRequest(workspace, null, ClassScanOptions.DEFAULT);
    }

    /** 便捷构造：按过滤条件扫描 */
    public static ClassScanRequest withFilter(Workspace workspace,
                                               Predicate<String> classFilter) {
        return new ClassScanRequest(workspace, classFilter, ClassScanOptions.DEFAULT);
    }
}
```

- [ ] **Step 3: 创建 ClassScanResult record**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 项目级扫描结果 — 不暴露 ClassGraph ScanResult。
 */
public final class ClassScanResult {

    private final Map<String, ClassMetadata> classes; // internalName -> metadata
    private final ScanStatistics statistics;

    public ClassScanResult(Map<String, ClassMetadata> classes, ScanStatistics statistics) {
        this.classes = Collections.unmodifiableMap(
            new LinkedHashMap<>(classes != null ? classes : Map.of())
        );
        this.statistics = statistics != null ? statistics
            : new ScanStatistics(classes != null ? classes.size() : 0, 0, 0, 0, 0);
    }

    public ClassScanResult(Map<String, ClassMetadata> classes) {
        this(classes, new ScanStatistics(classes != null ? classes.size() : 0, 0, 0, 0, 0));
    }

    public Optional<ClassMetadata> getClass(String internalName) {
        return Optional.ofNullable(classes.get(internalName));
    }

    public ClassMetadata getClassOrThrow(String internalName) {
        ClassMetadata c = classes.get(internalName);
        if (c == null) {
            throw new IllegalArgumentException("Class not found: " + internalName);
        }
        return c;
    }

    public boolean containsClass(String internalName) {
        return classes.containsKey(internalName);
    }

    public List<ClassMetadata> getAllClasses() {
        return List.copyOf(classes.values());
    }

    public int size() {
        return classes.size();
    }

    public ScanStatistics statistics() {
        return statistics;
    }

    public Map<String, ClassMetadata> classMap() {
        return classes; // already unmodifiable
    }
}
```

- [ ] **Step 4: 验证编译**

```bash
./mvnw clean compile -DskipTests
```

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanOptions.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanRequest.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanResult.java
git commit -m "feat(classscan): add ClassScanRequest, ClassScanResult, ClassScanOptions DTOs

Phase 1 step 2: request/result/options types for the adapter layer.
ClassScanResult wraps ClassMetadata map with immutable accessors.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.3: 创建 ClassScanService 接口 + ClassGraph 实现

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanService.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassGraphClassScanService.java`

- [ ] **Step 1: 创建 ClassScanService 接口**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.concurrent.CompletableFuture;

/**
 * 项目唯一扫描入口 — 所有 class 扫描必须通过此接口。
 *
 * <p>实现类负责将内部扫描引擎（当前为 ClassGraph）适配为此接口。</p>
 */
public interface ClassScanService {

    /** 同步扫描 */
    ClassScanResult scan(ClassScanRequest request);

    /** 异步扫描 */
    CompletableFuture<ClassScanResult> scanAsync(ClassScanRequest request);

    /** 取消指定请求的扫描 */
    void cancel(ClassScanRequest request);
}
```

- [ ] **Step 2: 创建 ClassGraphClassScanService 实现**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfo;
import com.bingbaihanji.classgraph.metadata.ClassInfoList;
import com.bingbaihanji.classgraph.metadata.FieldInfo;
import com.bingbaihanji.classgraph.metadata.MethodInfo;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
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

        // Phase 1: ASM 解析所有类
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

        // Phase 2: 构建 ClassInfo 图（通过 ClassGraph 内部 API）
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

        // Phase 3: 链接继承/接口/注解关系
        for (RawClassMetadata raw : rawMetadatas) {
            ClassInfo ci = classInfoByName.get(raw.name);
            if (ci == null) continue;
            linkRelations(ci, raw, classInfoByName);
        }

        // Phase 4: 转换为项目 DTO
        Map<String, ClassMetadata> result = new LinkedHashMap<>();
        for (ClassInfo ci : classInfoByName.values()) {
            ClassMetadata metadata = convertToMetadata(ci, options);
            result.put(ci.getName(), metadata);
        }

        long elapsed = System.currentTimeMillis() - start;
        int scannedCount = (int) rawMetadatas.stream()
            .filter(r -> r.access != 0 || !r.name.contains("/"))
            .count();
        int externalCount = result.size() - scannedCount;

        ScanStatistics stats = new ScanStatistics(
            result.size(), scannedCount, externalCount, skippedFileCount, elapsed
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
        // 当前实现不支持取消单个请求，取消所有后台任务
        BackgroundTasks.cancelAll();
    }

    // ─── 内部实现 ───

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

    private static RawClassMetadata parseClassBytes(byte[] bytes, String fullPath) {
        try {
            ClassReader reader = new ClassReader(bytes);
            RawVisitor visitor = new RawVisitor();
            reader.accept(visitor,
                ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new RawClassMetadata(visitor.name, visitor.access, visitor.superName,
                List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations),
                List.copyOf(visitor.methods), visitor.sourceFile, fullPath);
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
        if (raw.superName != null) {
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

    private static ClassMetadata convertToMetadata(ClassInfo ci, ClassScanOptions options) {
        List<FieldMetadata> fields = List.of();
        List<MethodMetadata> methods = List.of();
        List<AnnotationMetadata> annotations = List.of();

        if (options.enableFieldInfo()) {
            fields = new ArrayList<>();
            for (FieldInfo fi : ci.getFieldInfo()) {
                fields.add(new FieldMetadata(
                    fi.getName(), fi.getTypeDescriptor(), fi.getTypeSignatureOrTypeDescriptor(),
                    fi.getModifiers(), fi.getConstantInitializerValue(), List.of()
                ));
            }
            fields = List.copyOf(fields);
        }

        if (options.enableMethodInfo()) {
            methods = new ArrayList<>();
            for (MethodInfo mi : ci.getMethodInfo()) {
                methods.add(new MethodMetadata(
                    mi.getName(), mi.getTypeDescriptor(), mi.getTypeSignatureOrTypeDescriptor(),
                    mi.getModifiers(),
                    List.of(mi.getParameterTypeDescriptors()),
                    mi.getReturnTypeDescriptor(), List.of()
                ));
            }
            methods = List.copyOf(methods);
        }

        if (options.enableAnnotationInfo()) {
            annotations = new ArrayList<>();
            for (AnnotationInfo ai : ci.getAnnotationInfo()) {
                annotations.add(new AnnotationMetadata(ai.getName(), Map.of()));
            }
            annotations = List.copyOf(annotations);
        }

        List<String> interfaceNames = new ArrayList<>();
        for (ClassInfo iface : ci.getInterfaces()) {
            interfaceNames.add(iface.getName());
        }

        return new ClassMetadata(
            ci.getName(), ci.getModifiers(),
            ci.getSuperclass() != null ? ci.getSuperclass().getName() : null,
            List.copyOf(interfaceNames), fields, methods, annotations,
            ci.getFullPath(), ci.isExternalClass()
        );
    }

    // ─── 内部类型 ───

    private record RawClassMetadata(String name, int access, String superName,
                                     List<String> interfaces, List<String> annotations,
                                     List<RawMethodData> methods, String sourceFile,
                                     String fullPath) {}

    private record RawMethodData(String name, String descriptor, int access) {}

    private static final class RawVisitor extends ClassVisitor {
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();
        final List<RawMethodData> methods = new ArrayList<>();
        String name;
        int access;
        String superName;
        String sourceFile;

        RawVisitor() { super(Opcodes.ASM9); }

        @Override
        public void visit(int version, int access, String name, String sig,
                          String superName, String[] interfaces) {
            this.name = name; this.access = access; this.superName = superName;
            if (interfaces != null) this.interfaces.addAll(List.of(interfaces));
        }

        @Override
        public void visitSource(String source, String debug) { this.sourceFile = source; }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            org.objectweb.asm.Type t = org.objectweb.asm.Type.getType(desc);
            String n = t.getInternalName();
            if (n != null && !n.isBlank()) annotations.add(n);
            return null;
        }

        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                            String desc, String sig,
                                                            String[] exceptions) {
            methods.add(new RawMethodData(name, desc, access));
            return null;
        }
    }
}
```

- [ ] **Step 3: 验证编译**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassScanService.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassGraphClassScanService.java
git commit -m "feat(classscan): add ClassScanService interface + ClassGraphClassScanService impl

Phase 1 step 3: the adapter layer that isolates all classgraph internal API
usage behind a project-level interface. All future business code must
depend on ClassScanService, never on ClassGraph/ScanResult/ClassInfo directly.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.4: 将业务代码迁移到 ClassScanService

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java:3-4,16-79`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java:3,50-51,79-87`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java:3,66-129`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java` → 移除直接对 classgraph 的依赖

- [ ] **Step 1: 重构 InheritanceReferenceIndex 使用 ClassMetadata**

将 `InheritanceReferenceIndex.java` 中的 `ScanResult` 和 `ClassInfo` 引用替换为 `ClassScanResult` 和 `ClassMetadata`：

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.service.classscan.ClassMetadata;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanResult;

import java.util.*;

public final class InheritanceReferenceIndex {

    private final ClassScanResult scanResult;
    private final Map<String, String> internalNameToFullPath;
    private final Map<String, List<String>> interfaceToImplementations;
    private final Map<String, List<String>> superclassToSubclasses;
    private final Map<String, List<String>> annotationToAnnotatedClasses;

    public InheritanceReferenceIndex(ClassScanResult scanResult,
                                     Map<String, String> internalNameToFullPath) {
        this.scanResult = scanResult;
        this.internalNameToFullPath = internalNameToFullPath != null
                ? Map.copyOf(internalNameToFullPath) : Map.of();

        Map<String, List<String>> implMap = new LinkedHashMap<>();
        Map<String, List<String>> subMap = new LinkedHashMap<>();
        Map<String, List<String>> annMap = new LinkedHashMap<>();

        for (ClassMetadata cm : this.scanResult.getAllClasses()) {
            String name = cm.name();
            for (String itf : cm.interfaceNames()) {
                implMap.computeIfAbsent(itf, k -> new ArrayList<>()).add(name);
            }
            if (cm.superclassName() != null) {
                subMap.computeIfAbsent(cm.superclassName(), k -> new ArrayList<>()).add(name);
            }
            for (var ann : cm.annotations()) {
                annMap.computeIfAbsent(ann.className(), k -> new ArrayList<>()).add(name);
            }
        }

        this.interfaceToImplementations = freeze(implMap);
        this.superclassToSubclasses = freeze(subMap);
        this.annotationToAnnotatedClasses = freeze(annMap);
    }

    private static Map<String, List<String>> freeze(Map<String, List<String>> map) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    public ClassMetadata classInfo(String internalName) {
        return scanResult.getClass(internalName).orElse(null);
    }

    public List<String> implementationsOf(String interfaceName) {
        return interfaceToImplementations.getOrDefault(interfaceName, List.of());
    }

    public List<String> subclassesOf(String superclassName) {
        return superclassToSubclasses.getOrDefault(superclassName, List.of());
    }

    public List<String> annotatedBy(String annotationName) {
        return annotationToAnnotatedClasses.getOrDefault(annotationName, List.of());
    }

    public String fullPathOf(String internalName) {
        return internalNameToFullPath.get(internalName);
    }

    public boolean containsClass(String internalName) {
        return scanResult.containsClass(internalName);
    }

    public ClassScanResult scanResult() {
        return scanResult;
    }
}
```

- [ ] **Step 2: 重构 InheritanceReferenceIndexService**

```java
// 替换：
// import com.bingbaihanji.classgraph.scan.ScanResult;
// 为：
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanResult;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanService;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanRequest;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassGraphClassScanService;

// 添加：
private static final ClassScanService classScanService = new ClassGraphClassScanService();

// 修改 buildPathMap()：
private static Map<String, String> buildPathMap(ClassScanResult scanResult) {
    LinkedHashMap<String, String> map = new LinkedHashMap<>();
    for (var cm : scanResult.getAllClasses()) {
        if (cm.fullPath() != null) {
            map.put(cm.name(), cm.fullPath());
        }
    }
    return map;
}

// 修改 getOrStart() 中的 scanResult 创建：
ClassScanResult scanResult = classScanService.scan(ClassScanRequest.of(workspace));
```

- [ ] **Step 3: 重构 InheritanceReferenceService**

将 `ClassInfo` 引用替换为 `ClassMetadata`：
```java
// 替换 import：
// import com.bingbaihanji.classgraph.metadata.ClassInfo;
// 为：
import com.bingbaihanji.fxdecomplie.service.classscan.ClassMetadata;

// 所有 ClassInfo ci 改为 ClassMetadata cm
// ci.getName() → cm.name()
// ci.isInterface() → cm.isInterface()
// ci.getInterfaces() → cm.interfaceNames()
// ci.getSuperclass().getName() → cm.superclassName()
// ci.getAnnotationInfo() → cm.annotations()
```

具体修改 `getAnnotationInfo()` 调用处（InheritanceReferenceService.java:97-99）：
```java
if (index != null) {
    ClassMetadata cm = index.classInfo(meta.name);
    if (cm != null) {
        for (var ann : cm.annotations()) {
            annotationNodes.add(
                nodeFor(workspace, ann.className(), Kind.ANNOTATION, 1, index));
        }
    }
}
```

修改 `addImplementationGroup()`（line 416）：
```java
ClassMetadata cm = index.classInfo(internalName);
if (cm == null || !cm.isInterface()) return;
```

修改 `addSubclassGroup()`（line 431）：
```java
ClassMetadata cm = index.classInfo(internalName);
if (cm != null && cm.isInterface()) return;
```

修改 `addAnnotatedClassesGroup()`（line 446）：
```java
if (!cm.isAnnotation()) return;
```

修改 `collectImplementations()`（line 490-491）：
```java
ClassMetadata info = index.classInfo(impl);
if (info != null && info.isInterface()) {
```

- [ ] **Step 4: 将 ClassGraphWorkspaceAdapter 标记为 @Deprecated**

在 `ClassGraphWorkspaceAdapter.java` 顶部添加 `@Deprecated` 注解：
```java
@Deprecated(forRemoval = true)
public final class ClassGraphWorkspaceAdapter {
```

如果 `ClassGraphWorkspaceAdapter` 只在服务内部使用（检查引用），可以将其逻辑合并到 `ClassGraphClassScanService` 中。

- [ ] **Step 5: 验证编译并运行测试**

```bash
./mvnw clean compile -DskipTests
```

Expected: BUILD SUCCESS (确保没有残留的 classgraph import)

验证没有业务代码直接引用 classgraph：
```bash
./mvnw dependency:tree  # 跳过，改为 grep
```
运行：
```bash
# PowerShell
Select-String -Path src/main/java/com/bingbaihanji/fxdecomplie/service/**/*.java -Pattern 'import com\.bingbaihanji\.classgraph\.' | Where-Object { $_.Path -notmatch 'ClassGraphClassScanService' }
```
Expected: 只有 `ClassGraphClassScanService.java` 有 classgraph import

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java
git commit -m "refactor(reference): migrate all business code to ClassScanService

Phase 1 step 4: InheritanceReferenceIndex now wraps ClassScanResult instead of
ScanResult. InheritanceReferenceService uses ClassMetadata instead of ClassInfo.
ClassGraphWorkspaceAdapter marked @Deprecated.

Only ClassGraphClassScanService now imports from com.bingbaihanji.classgraph.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.5: 补适配层单元测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassGraphClassScanServiceTest.java`

- [ ] **Step 1: 创建测试类**

```java
package com.bingbaihanji.fxdecomplie.service.classscan;

import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.MemberIndexEntry;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClassGraphClassScanService")
class ClassGraphClassScanServiceTest {

    private ClassScanService service;

    @BeforeEach
    void setUp() {
        service = new ClassGraphClassScanService();
    }

    @Nested
    @DisplayName("scan() with null/empty input")
    class NullAndEmptyInput {

        @Test
        @DisplayName("returns empty result for null request")
        void nullRequest() {
            ClassScanResult result = service.scan(null);
            assertEquals(0, result.size());
        }

        @Test
        @DisplayName("returns empty result for null workspace")
        void nullWorkspace() {
            ClassScanResult result = service.scan(
                new ClassScanRequest(null, null, ClassScanOptions.DEFAULT));
            assertEquals(0, result.size());
        }
    }

    @Nested
    @DisplayName("scan() with simple class")
    class SimpleClass {

        // 见 Step 2
    }
}
```

- [ ] **Step 2: 添加生成测试 class 字节码的辅助方法**

在测试类中添加：
```java
/** 生成最简单的 class 文件: public class TestClass extends Object {} */
private byte[] generateSimpleClass(String internalName, String superName) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    DataOutputStream dos = new DataOutputStream(bos);

    // Magic (0xCAFEBABE)
    dos.writeInt(0xCAFEBABE);
    // Version: minor=0, major=52 (Java 8)
    dos.writeShort(0);
    dos.writeShort(52);

    // Constant pool (manual, minimal)
    // #1 = Class(#2), #2 = Utf8("<name>"), #3 = Class(#4), #4 = Utf8("<superName>")
    // #5 = Utf8("Code"), #6 = Utf8("<init>"), #7 = Utf8("()V")
    // #8 = Methodref(#1,#9), #9 = NameAndType(#6,#7)

    int cpCount = 10;
    dos.writeShort(cpCount);

    // #1 Utf8: internalName
    writeCpUtf8(dos, internalName);
    // #2 Class: #1
    dos.writeByte(7); dos.writeShort(1);
    // #3 Utf8: superName
    writeCpUtf8(dos, superName);
    // #4 Class: #3
    dos.writeByte(7); dos.writeShort(3);
    // #5 Utf8: "<init>"
    writeCpUtf8(dos, "<init>");
    // #6 Utf8: "()V"
    writeCpUtf8(dos, "()V");
    // #7 Utf8: "Code"
    writeCpUtf8(dos, "Code");
    // #8 Methodref: class_index=#4, name_and_type_index=#9
    dos.writeByte(10); dos.writeShort(4); dos.writeShort(9);
    // #9 NameAndType: name_index=#5, descriptor_index=#6
    dos.writeByte(12); dos.writeShort(5); dos.writeShort(6);

    // Access flags: ACC_PUBLIC | ACC_SUPER
    dos.writeShort(0x0001 | 0x0020);
    // This class: #2
    dos.writeShort(2);
    // Super class: #4
    dos.writeShort(4);

    // Interfaces count = 0
    dos.writeShort(0);
    // Fields count = 0
    dos.writeShort(0);

    // Methods count = 1 (default constructor)
    dos.writeShort(1);
    // Method: access=ACC_PUBLIC, name=#5, descriptor=#6
    dos.writeShort(0x0001); dos.writeShort(5); dos.writeShort(6);
    // Attributes count = 1 (Code)
    dos.writeShort(1);
    // Attribute: name=#7
    dos.writeShort(7);
    // Code attribute: aload_0, invokespecial #8, return
    byte[] code = {
        (byte) 0x2a,                      // aload_0
        (byte) 0xb7, 0x00, 0x08,          // invokespecial #8 (Object.<init>)
        (byte) 0xb1                         // return
    };
    int attrLen = 2 + 2 + 4 + code.length + 2; // max_stack+max_locals+code_len+code+exception_table_len
    dos.writeInt(attrLen);
    dos.writeShort(1);  // max_stack
    dos.writeShort(1);  // max_locals
    dos.writeInt(code.length);
    dos.write(code);
    dos.writeShort(0);  // exception_table_length

    // Class attributes count = 0
    dos.writeShort(0);

    return bos.toByteArray();
}

private void writeCpUtf8(DataOutputStream dos, String s) throws IOException {
    dos.writeByte(1); // CONSTANT_Utf8
    byte[] utf = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    dos.writeShort(utf.length);
    dos.write(utf);
}
```

- [ ] **Step 3: 添加 SimpleClass 测试**

```java
@Test
@DisplayName("scans a simple class and returns correct metadata")
void scansSimpleClass() throws Exception {
    Workspace workspace = mockWorkspaceWithClass(
        "com/example/TestClass",
        "java/lang/Object",
        generateSimpleClass("com/example/TestClass", "java/lang/Object")
    );

    ClassScanResult result = service.scan(ClassScanRequest.of(workspace));

    assertEquals(1, result.size());
    ClassMetadata cm = result.getClassOrThrow("com/example/TestClass");
    assertEquals("com/example/TestClass", cm.name());
    assertFalse(cm.isInterface());
    assertFalse(cm.isAnnotation());
    assertTrue(cm.fullPath() != null && cm.fullPath().contains("TestClass"));
}
```

- [ ] **Step 4: 补 mockWorkspaceWithClass 辅助方法**

```java
private Workspace mockWorkspaceWithClass(String internalName, String superName,
                                          byte[] bytes) throws Exception {
    // 使用 NIO 创建临时目录模拟 workspace
    Path tmpDir = Files.createTempDirectory("test-workspace-");
    Path classFile = tmpDir.resolve(internalName + ".class");
    Files.createDirectories(classFile.getParent());
    Files.write(classFile, bytes);

    // 构建 Workspace + WorkspaceIndex
    Workspace ws = new Workspace(tmpDir);
    ClassIndexEntry entry = new ClassIndexEntry(internalName, bytes,
        superName, List.of(), List.of(),
        new com.bingbaihanji.fxdecomplie.model.FileTreeNode(
            classFile, null, internalName + ".class", false, 0L),
        classFile.toString());
    WorkspaceIndex index = new WorkspaceIndex(
        List.of(entry), Set.of(), Set.of(), Map.of(), Map.of());
    ws.setIndex(index);
    return ws;
}
```

注意：`Workspace`、`WorkspaceIndex`、`ClassIndexEntry`、`FileTreeNode` 的构造方式取决于实际实现。如果这些类没有 setter/直接构造访问，需要调整策略——可能需要创建真实的最小 workspace 目录结构。

- [ ] **Step 5: 运行测试**

```bash
./mvnw test -Dtest="ClassGraphClassScanServiceTest"
```

Expected: 如果 mock 不兼容，至少 null/empty input 测试应该通过；调整 mock 方式直到 simpleClass 测试通过。

- [ ] **Step 6: 提交**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/classscan/ClassGraphClassScanServiceTest.java
git commit -m "test(classscan): add unit tests for ClassGraphClassScanService

Phase 1 step 5: covers null/empty input, simple class scanning, and metadata conversion.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Phase 1 完成检查

- [ ] 业务代码不再直接 import `com.bingbaihanji.classgraph.*`（除 `ClassGraphClassScanService` 外）
- [ ] `ClassScanService` 接口定义了完整扫描契约
- [ ] `ClassScanResult`/`ClassMetadata` 等 DTO 不暴露 ClassGraph 内部类型
- [ ] 适配层测试通过（至少 null/empty 路径 + 基本扫描路径）
- [ ] `InheritanceReferenceIndex` 使用 `ClassScanResult` 而非 `ScanResult`
- [ ] `InheritanceReferenceService` 使用 `ClassMetadata` 而非 `ClassInfo`

---

## Phase 2: 回归测试 + Bug 修复 + 命名规范化（3-5 天）

### Task 2.1: 修复 Bug B1 — ClassGraphClassLoaderHandler 包路径错误

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/classpath/handler/ClassGraphClassLoaderHandler.java`

- [ ] **Step 1: 修复 canHandle() 中的包路径**

读取当前文件确认：
```java
// 当前 (错误):
return classIsOrExtendsOrImplements(classLoaderClass,
    "com.bingbaihanji.classgraph.core.ScanClassLoader");

// 修改为:
return classIsOrExtendsOrImplements(classLoaderClass,
    "com.bingbaihanji.classgraph.scan.ScanClassLoader");
```

- [ ] **Step 2: 验证编译**

```bash
./mvnw clean compile -DskipTests
```

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classpath/handler/ClassGraphClassLoaderHandler.java
git commit -m "fix(classgraph): fix ClassGraphClassLoaderHandler canHandle() package path

Bug B1: checked for com.bingbaihanji.classgraph.core.ScanClassLoader
but the actual ScanClassLoader is in scan/ package. Handler would
never match at runtime.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.2: 修复 Bug B2 — FallbackClassLoaderHandler 重复调用

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/classpath/handler/FallbackClassLoaderHandler.java`

- [ ] **Step 1: 移除重复的 getFieldVal("paths") 调用**

读取文件找到两处相同调用，删除其中一处。

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classpath/handler/FallbackClassLoaderHandler.java
git commit -m "fix(classgraph): remove duplicate getFieldVal(\"paths\") call in FallbackClassLoaderHandler

Bug B2: duplicate identical invocation at lines ~100-103.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.3: 修复 Bug B3 — EquinoxClassLoaderHandler 静态可变状态

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/classpath/handler/EquinoxClassLoaderHandler.java`

- [ ] **Step 1: 将 static alreadyReadSystemBundles 改为实例字段**

```java
// 当前: private static boolean alreadyReadSystemBundles;
// 改为: private boolean alreadyReadSystemBundles;
```

- [ ] **Step 2: 提交**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classpath/handler/EquinoxClassLoaderHandler.java
git commit -m "fix(classgraph): convert EquinoxClassLoaderHandler static state to instance field

Bug B3: static alreadyReadSystemBundles caused one scan failure to
permanently skip system bundles for all subsequent scans.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.4: 全局命名规范化（PascalCase → camelCase）

**Files:**
- Modify: ~100 个文件在 `src/main/java/com/bingbaihanji/classgraph/` 下

- [ ] **Step 1: 使用 IDE Rename 逐文件修正**

重点文件列表（按优先级）：
1. `scan/ClassGraph.java` — `ScanConfig` → `scanConfig`, `LogNode` → `log`
2. `scan/Scanner.java` — `Classpath` → `classpath`, `JarReader` → `jarReader`, `ScanConfig` → `scanConfig`
3. `scan/ScanConfig.java` — `LogNode` → `log`
4. `bytecode/ClassParser.java` — `Classpath` → `classpath`, `ScanConfig` → `scanConfig`, `MethodType` → `methodType`, `ClassType` → `classType`, `TypeVar` → `typeVar`, `TypeArg` → `typeArg`, `TypeParser` → `typeParser`
5. `classpath/*.java` — `Classpath` → `classpath`, `ScanConfig` → `scanConfig`
6. `metadata/*.java` — 所有 PascalCase 局部变量
7. `resource/*.java` — 所有 PascalCase 局部变量
8. `type/*.java` — 所有 PascalCase 局部变量
9. `reflect/*.java` — 所有 PascalCase 局部变量
10. `util/*.java` — 所有 PascalCase 局部变量

**关键规则**：
- 只重命名局部变量和参数，不重命名类名
- 使用 IDE Rename（Shift+F6 in IntelliJ），不查找替换
- 每个文件重命名后立即编译验证

- [ ] **Step 2: 分批提交（每 10-15 个文件一组）**

```bash
git add <batch-1-files>
git commit -m "style(classgraph): fix PascalCase local variables in scan/ package

Phase 2 naming: ScanConfig→scanConfig, Log→log (local vars only).

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.5: 写 bytecode 层回归测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/classgraph/bytecode/ClassParserTest.java`

- [ ] **Step 1-5**: 根据设计文档 7.2 节场景写测试

聚焦 3 个核心场景（优先级最高）：
1. 基本 class 解析（类名、修饰符、主版本号）
2. 字段解析（基本类型 + 对象类型 + 常量值）
3. 方法解析（参数类型 + 返回类型 + 异常表）

测试代码使用真实 class 文件（从 test resources 加载）或手动拼接简单 class 字节码。

- [ ] **Step 6: 提交测试相关代码**

```bash
git add src/test/java/com/bingbaihanji/classgraph/
git commit -m "test(classgraph): add bytecode-level regression tests for ClassParser

Phase 2 testing: basic class parsing, field parsing, method parsing.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.6: 写 classpath 层回归测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/classgraph/classpath/`

- [ ] **Step 1-4**: 根据设计文档 7.2 节场景写测试

优先：JAR classpath + 重复 class 遮蔽规则

---

### Task 2.7: 写 scan pipeline 集成测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/classgraph/scan/`

- [ ] **Step 1-4**: 根据设计文档 7.2 节场景写测试

优先：目录扫描端到端 → 类信息完整

---

## Phase 3: 配置模型收口（2-3 天）

### Task 3.1: 给 ScanConfig 增加 getter/setter

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/scan/ScanConfig.java`

- [ ] **Step 1: 为所有 public 字段生成 getter/setter**

使用 IDE "Encapsulate Fields" 重构（IntelliJ: Refactor → Encapsulate Fields）

- [ ] **Step 2: 替换直接字段访问为 getter/setter 调用**

全局替换所有引用 `ScanConfig.<field>` 的代码。

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/classgraph/
git commit -m "refactor(classgraph): encapsulate ScanConfig fields with getters/setters

Phase 3 step 1: all public fields now accessed via methods. No behavior change.

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3.2: 引入不可变 ScanOptions + Builder

**Files:**
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanOptions.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanOptionsBuilder.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanFilters.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanRuntimeOptions.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanClasspathOptions.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/config/ScanMetadataOptions.java`

- [ ] **Step 1-6**: 按设计文档 §4.1 接口定义实现各配置类

- [ ] **Step 7: 将 ScanConfig 改为委托给 ScanOptions 的兼容适配器**

- [ ] **Step 8: 修改 Scanner 接收 ScanOptions**

- [ ] **Step 9: 添加冻结检查**

- [ ] **Step 10: 提交**

---

## Phase 4: Scanner Pipeline 拆分（3-5 天）

### Task 4.1: 创建 Pipeline 框架

**Files:**
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/pipeline/ScanStep.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/pipeline/ScanContext.java`
- Create: `src/main/java/com/bingbaihanji/classgraph/scan/pipeline/ScanPipeline.java`

- [ ] **Step 1-3**: 按设计文档 §4.3 接口定义实现

### Task 4.2-4.8: 提取各 Step

按设计文档中的映射表逐 step 提取。每个 step 提取完成后提交。

---

## Phase 5: ClassParser DTO 化（4-6 天，可与 Phase 6 并行）

### Task 5.1-5.11: 按设计文档 §4.4 拆分

优先顺序严格按照设计文档的依赖分析：
1. `ParsedClassFile` DTO
2. `ConstantPoolReader`
3. `AnnotationReader`
4. `FieldSectionReader`
5. `MethodSectionReader`
6. `ClassAttributeReader`
7. `ExternalClassReferenceCollector`
8. `MetadataLinker`
9. `ClassFileParser` 编排器

---

## Phase 6: Classpath + Handler 治理（3-4 天，可与 Phase 5 并行）

### Task 6.1-6.7: 按设计文档 §4.5, §4.6 执行

包括私有化 Classpath 字段、引入 ClasspathElementState、HandlerRegistry 可插拔化。

---

## Phase 7: Metadata 读写分离（2-3 天）

### Task 7.1-7.4: 按设计文档 §4.7 执行

引入 MutableClassMetadata → 只读接口 → Query Service → ClassQuery guard clause 消除。

---

## Phase 8: 日志解耦 + 杂项清理（1-2 天）

### Task 8.1-8.7: 按设计文档 §4.8, §5 执行

ScanLogger 接口化、TypedValue 移除、GraphvizDotfileGenerator 移包、PrivilegedActionInvocationHandler 去重、TypeArg 接口违约修复。

---

## 并行执行策略

```
Phase 1 ──→ Phase 2 ──→ Phase 3 ──→ Phase 4 ──→ Phase 7 ──→ Phase 8
                                            └─→ Phase 5 ──┘  (可并行)
                                            └─→ Phase 6 ──┘  (可并行)
```

Phase 5 和 Phase 6 无相互依赖，可在 Phase 4 完成后并行推进。
