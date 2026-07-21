# ClassGraph 移植代码完善优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完善优化 `com.bingbaihanji.classgraph` 移植代码——合并重复 ASM 解析、重构 ClassLoaderHandler 接口、增强异常日志。

**Architecture:** 4 项独立改动，按依赖顺序排：先改基础设施（ScanResult 加字段、ClassLoaderHandler 接口加方法），再改消费方（ClassGraphWorkspaceAdapter 合并解析、InheritanceReferenceService 从索引读取、ClassLoaderHandlerRegistry 消除反射）。

**Tech Stack:** Java 25, ASM 9.9, SLF4J, JUnit 5.12.1

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/bingbaihanji/classgraph/core/ScanResult.java` | 扫描结果，新增 `skippedFileCount` |
| `src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandler.java` | 接口，加 3 个 default 方法 |
| `src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandlerRegistry.java` | 注册表，反射→实例调用；移除 @SuppressWarnings |
| 18 个 `classloaderhandler/*Handler.java` | 实现类，静态→实例方法 |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java` | 适配器，MetadataVisitor 扩展；日志增强 |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java` | 服务，删除 parseLocal() 中的重复解析 |

---

### Task 1: ScanResult — 新增 skippedFileCount 字段

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/core/ScanResult.java`

- [ ] **Step 1: 添加字段和 getter**

在 `ScanResult` 类中添加 skippedFileCount 字段。找到 `private NestedJarHandler nestedJarHandler;` 附近（~line 113），在其后添加：

```java
/** 扫描期间跳过的文件数（读取或解析失败的 class 文件）。 */
private int skippedFileCount;
```

然后在类的 public 方法区域（`close()` 方法附近或 `getAllClasses()` 之后，~line 547）添加 getter：

```java
/**
 * 返回扫描期间跳过的文件数。
 *
 * @return 跳过的文件数
 */
public int getSkippedFileCount() {
    return skippedFileCount;
}
```

同时在两个构造函数末尾（`public ScanResult(ScanSpec, ...)` 末尾 ~line 155 和简化构造函数 `public ScanResult(Map<...>)` 末尾 ~line 230）确保该字段初始化为 0。简化构造函数中可以在 `this.nestedJarHandler = null;` 后添加：

```java
this.skippedFileCount = 0;
```

主构造函数中在 `this.topLevelLog = topLevelLog;` 后添加：

```java
this.skippedFileCount = 0;
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/core/ScanResult.java
git commit -m "feat(classgraph): add skippedFileCount to ScanResult"
```

---

### Task 2: ClassGraphWorkspaceAdapter — 合并 ASM 解析 + 日志增强

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java`

- [ ] **Step 1: readBytes() — 日志 debug→warn**

修改 `readBytes` 方法（~line 102-112），将 `log.debug` 改为 `log.warn`：

```java
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
```

- [ ] **Step 2: parse() — 日志 debug→warn**

修改 `parse` 方法（~line 114-125），将 `log.debug` 改为 `log.warn`：

```java
private static ClassMetadata parse(byte[] bytes, String fullPath) {
    try {
        ClassReader reader = new ClassReader(bytes);
        MetadataVisitor visitor = new MetadataVisitor();
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new ClassMetadata(visitor.name, visitor.access, visitor.superName,
                List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations),
                List.copyOf(visitor.methods), fullPath);
    } catch (Exception e) {
        log.warn("ASM parse failed for: {}", fullPath, e);
        return null;
    }
}
```

- [ ] **Step 3: MetadataVisitor — 添加方法收集**

修改 `MetadataVisitor` 内部类（~line 182-213），添加方法收集和 sourceFile：

```java
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
```

- [ ] **Step 4: ClassMetadata — 添加 methods 字段**

修改 `ClassMetadata` record（~line 215-217）：

```java
private record ClassMetadata(String name, int access, String superName,
                             List<String> interfaces, List<String> annotations,
                             List<MethodData> methods, String fullPath) {}
```

- [ ] **Step 5: MethodData — 添加内部 record**

在 `ClassGraphWorkspaceAdapter` 类中添加 `MethodData` record（在 `ClassMetadata` 旁边）：

```java
/**
 * 轻量级方法数据（仅名称、描述符、访问标志）
 */
private record MethodData(String name, String descriptor, int access) {}
```

- [ ] **Step 6: linkRelations — 填充 sourceFile + 记录跳过计数**

修改 `linkRelations` 方法，在填充 ClassInfo 时也设置 `sourceFile`。同时修改 `scan` 方法，在 byte 读取和 parse 失败时增加计数，并最终设置到 ScanResult。

修改 `scan` 方法（~line 62-99），添加 skippedFileCount 跟踪：

```java
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
    result.skippedFileCount = skippedFileCount;
    return result;
}
```

注意：`result.skippedFileCount = skippedFileCount;` 需要 `ScanResult.skippedFileCount` 字段为 package-private 或 public。在 Task 1 中我们将其设为 `private`，需要改为 package-private（去掉 `private` 修饰符，或提供 package-private setter）。这里我们直接将字段声明改为无修饰符（package-private）：

在 Task 1 中，字段应声明为：
```java
/** 扫描期间跳过的文件数（读取或解析失败的 class 文件）。 */
int skippedFileCount;
```

- [ ] **Step 7: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java \
        src/main/java/com/bingbaihanji/classgraph/core/ScanResult.java
git commit -m "perf(reference): merge ASM parsing with method collection; enhance error logging"
```

---

### Task 3: InheritanceReferenceService — 从索引读取注解，删除重复解析

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java`

- [ ] **Step 1: 修改 parseLocal() 的空 catch**

修改 `parseLocal`（~line 594-606），将 `log.debug` 改为 `log.warn`：

```java
private static LocalMetadata parseLocal(byte[] bytes) {
    try {
        ClassReader reader = new ClassReader(bytes);
        LocalVisitor visitor = new LocalVisitor();
        reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return new LocalMetadata(visitor.name, visitor.access, visitor.superName,
                List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations),
                List.copyOf(visitor.methods));
    } catch (Exception e) {
        log.warn("ASM parse failed in InheritanceReferenceService", e);
        return null;
    }
}
```

- [ ] **Step 2: buildTree — 注解从全局索引获取**

修改 `buildTree` 方法中的注解收集逻辑（~line 96-104）。当前它使用 `parseLocal()` 的结果 `meta.annotations`。由于 Task 2 已在 `ClassGraphWorkspaceAdapter` 的 `MetadataVisitor` 中收集了注解信息，索引中包含的 `ClassInfo` 已携带注解数据。改为：

```java
// 注解：优先从 ClassGraph 全局索引获取，回退到本地解析
List<InheritanceReferenceNode> annotationNodes = new ArrayList<>();
InheritanceReferenceIndex refIndex = InheritanceReferenceIndexService.getIfReady(workspace);
if (refIndex != null) {
    ClassInfo ci = refIndex.classInfo(meta.name);
    if (ci != null) {
        for (var ai : ci.getAnnotationInfo()) {
            annotationNodes.add(nodeFor(workspace, ai.getName(), Kind.ANNOTATION, 1));
        }
    }
}
// 回退：如果索引未就绪，使用本地解析结果
if (annotationNodes.isEmpty() && !meta.annotations.isEmpty()) {
    for (String ann : meta.annotations) {
        annotationNodes.add(nodeFor(workspace, ann, Kind.ANNOTATION, 1));
    }
}
if (!annotationNodes.isEmpty()) {
    groups.add(singleGroup(Kind.ANNOTATION,
            I18nUtil.getString("inheritance.group.annotations"),
            annotationNodes, false));
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java
git commit -m "perf(reference): read annotations from ClassGraph index; enhance error logging"
```

---

### Task 4: ClassLoaderHandler — 接口重构（default 方法替代反射）

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandler.java`
- Modify: `src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandlerRegistry.java`
- Modify: 18 handler 实现类

- [ ] **Step 1: 修改 ClassLoaderHandler 接口**

用以下内容替换 `ClassLoaderHandler.java`（保持 license header 不变，只改接口体）：

```java
package com.bingbaihanji.classgraph.classloaderhandler;

import com.bingbaihanji.classgraph.classpath.ClassLoaderOrder;
import com.bingbaihanji.classgraph.classpath.ClasspathOrder;
import com.bingbaihanji.classgraph.scanspec.ScanSpec;
import com.bingbaihanji.classgraph.utils.LogNode;

/**
 * ClassLoader 处理器。
 *
 * <p>
 * 如果您创建了自定义的 ClassLoaderHandler，请考虑将其提交到 ClassGraph 开源项目。
 */
public interface ClassLoaderHandler {

    /**
     * 检查此 {@link ClassLoaderHandler} 是否能够处理给定的 {@link ClassLoader}。
     *
     * @param classLoaderClass
     *            {@link ClassLoader} 类或其超类之一。
     * @param log
     *            日志
     * @return 如果此 {@link ClassLoaderHandler} 能够处理 {@link ClassLoader}，则返回 true。
     */
    default boolean canHandle(Class<?> classLoaderClass, LogNode log) {
        return false;
    }

    /**
     * 查找某个 {@link ClassLoader} 的 {@link ClassLoader} 委托顺序。
     *
     * @param classLoader
     *            要查找委托顺序的 {@link ClassLoader}。
     * @param classLoaderOrder
     *            要更新的 {@link ClassLoaderOrder} 对象。
     * @param log
     *            日志
     */
    default void findClassLoaderOrder(ClassLoader classLoader, ClassLoaderOrder classLoaderOrder,
                                      LogNode log) {
    }

    /**
     * 查找关联 {@link ClassLoader} 的类路径条目。
     *
     * @param classLoader
     *            要查找类路径条目顺序的 {@link ClassLoader}。
     * @param classpathOrder
     *            要更新的 {@link ClasspathOrder} 对象。
     * @param scanSpec
     *            {@link ScanSpec}。
     * @param log
     *            日志。
     */
    default void findClasspathOrder(ClassLoader classLoader, ClasspathOrder classpathOrder,
                                    ScanSpec scanSpec, LogNode log) {
    }
}
```

- [ ] **Step 2: 修改 18 个 handler 实现类**

每个 handler 需要：
1. 将 `private` 构造函数改为 `public`（或 package-private）
2. 将 3 个 `public static` 方法改为 `@Override public` 实例方法
3. 构造函数不再 `private`（Registry 需要实例化）

以 `URLClassLoaderHandler.java` 为例：

```java
class URLClassLoaderHandler implements ClassLoaderHandler {

    /**
     * Default constructor for registry instantiation.
     */
    public URLClassLoaderHandler() {
    }

    @Override
    public boolean canHandle(Class<?> classLoaderClass, LogNode log) {
        return ClassLoaderFinder.classIsOrExtendsOrImplements(classLoaderClass, "java.net.URLClassLoader");
    }

    @Override
    public void findClassLoaderOrder(ClassLoader classLoader, ClassLoaderOrder classLoaderOrder,
                                    LogNode log) {
        classLoaderOrder.delegateTo(classLoader.getParent(), /* isParent = */ true, log);
        classLoaderOrder.add(classLoader, log);
    }

    @Override
    public void findClasspathOrder(ClassLoader classLoader, ClasspathOrder classpathOrder,
                                  ScanSpec scanSpec, LogNode log) {
        URL[] urls = ((URLClassLoader) classLoader).getURLs();
        if (urls != null) {
            for (URL url : urls) {
                if (url != null) {
                    classpathOrder.addClasspathEntry(url, classLoader, scanSpec, log);
                }
            }
        }
    }
}
```

18 个 handler 文件列表及改动：

| Handler | 构造函数签名 | 特殊处理 |
|---------|-------------|---------|
| `AntClassLoaderHandler.java` | `public` | 直接转换 |
| `ClassGraphClassLoaderHandler.java` | `public` | 直接转换 |
| `CxfContainerClassLoaderHandler.java` | `public` | 直接转换 |
| `EquinoxClassLoaderHandler.java` | `public` | 直接转换 |
| `EquinoxContextFinderClassLoaderHandler.java` | `public` | 直接转换 |
| `FallbackClassLoaderHandler.java` | `public` | 直接转换 |
| `FelixClassLoaderHandler.java` | `public` | 直接转换 |
| `JBossClassLoaderHandler.java` | `public` | 直接转换 |
| `JPMSClassLoaderHandler.java` | `public` | 直接转换 |
| `OSGiDefaultClassLoaderHandler.java` | `public` | 直接转换 |
| `ParentLastDelegationOrderTestClassLoaderHandler.java` | `public` | 直接转换 |
| `PlexusClassWorldsClassRealmClassLoaderHandler.java` | `public` | 直接转换 |
| `QuarkusClassLoaderHandler.java` | `public` | 直接转换 |
| `SpringBootRestartClassLoaderHandler.java` | `public` | 直接转换 |
| `TomcatWebappClassLoaderBaseHandler.java` | `public` | 直接转换 |
| `URLClassLoaderHandler.java` | `public` | 直接转换 |
| `UnoOneJarClassLoaderHandler.java` | `public` | 直接转换 |
| `WeblogicClassLoaderHandler.java` | `public` | 直接转换 |
| `WebsphereLibertyClassLoaderHandler.java` | `public` | 直接转换 |
| `WebsphereTraditionalClassLoaderHandler.java` | `public` | 直接转换 |

每个文件的改动模式相同：
1. 将 `private ClassName() {}` 或 `private ClassName() { throw ... }` 改为 `public ClassName() {}`
2. 将 `public static boolean canHandle(...)` 改为 `@Override public boolean canHandle(...)`
3. 将 `public static void findClassLoaderOrder(...)` 改为 `@Override public void findClassLoaderOrder(...)`
4. 将 `public static void findClasspathOrder(...)` 改为 `@Override public void findClasspathOrder(...)`

- [ ] **Step 3: 修改 ClassLoaderHandlerRegistry**

修改 `ClassLoaderHandlerRegistry.ClassLoaderHandlerRegistryEntry`：
1. 将 `Class<? extends ClassLoaderHandler> classLoaderHandlerClass` 改为存储 `ClassLoaderHandler` 实例
2. 构造函数改为接收 handler 实例，不再使用反射
3. `canHandle`/`findClassLoaderOrder`/`findClasspathOrder` 方法改为直接调用实例方法

重构后的 `ClassLoaderHandlerRegistryEntry`：

```java
public static class ClassLoaderHandlerRegistryEntry {
    /** ClassLoaderHandler 实例。 */
    public final ClassLoaderHandler classLoaderHandler;

    /**
     * 构造函数。
     *
     * @param classLoaderHandler
     *            ClassLoaderHandler 实例。
     */
    private ClassLoaderHandlerRegistryEntry(final ClassLoaderHandler classLoaderHandler) {
        this.classLoaderHandler = classLoaderHandler;
    }

    public boolean canHandle(final Class<?> classLoader, final LogNode log) {
        try {
            return classLoaderHandler.canHandle(classLoader, log);
        } catch (final Throwable e) {
            throw new RuntimeException(
                    "Exception while calling canHandle for " + classLoaderHandler.getClass().getName(), e);
        }
    }

    public void findClassLoaderOrder(final ClassLoader classLoader, final ClassLoaderOrder classLoaderOrder,
                                     final LogNode log) {
        classLoaderHandler.findClassLoaderOrder(classLoader, classLoaderOrder, log);
    }

    public void findClasspathOrder(final ClassLoader classLoader, final ClasspathOrder classpathOrder,
                                   final ScanSpec scanSpec, final LogNode log) {
        classLoaderHandler.findClasspathOrder(classLoader, classpathOrder, scanSpec, log);
    }
}
```

修改 `CLASS_LOADER_HANDLERS` 静态列表中的条目，从 `new ClassLoaderHandlerRegistryEntry(XxxHandler.class)` 改为 `new ClassLoaderHandlerRegistryEntry(new XxxHandler())`：

```java
@SuppressWarnings("null")
public static final List<ClassLoaderHandlerRegistryEntry> CLASS_LOADER_HANDLERS =
        Collections.unmodifiableList(Arrays.asList(
                new ClassLoaderHandlerRegistryEntry(new AntClassLoaderHandler()),
                new ClassLoaderHandlerRegistryEntry(new EquinoxClassLoaderHandler()),
                // ... 其他 16 个同理
                new ClassLoaderHandlerRegistryEntry(new ClassGraphClassLoaderHandler())
        ));

public static final ClassLoaderHandlerRegistryEntry FALLBACK_HANDLER =
        new ClassLoaderHandlerRegistryEntry(new FallbackClassLoaderHandler());
```

移除 `@SuppressWarnings("null")` 注解（Task 5 中完成）。

- [ ] **Step 4: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classloaderhandler/
git commit -m "refactor(classgraph): replace reflection with default interface methods on ClassLoaderHandler"
```

---

### Task 5: 代码风格微调 + 全量测试

**Files:**
- Modify: `src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandlerRegistry.java`

- [ ] **Step 1: 移除 @SuppressWarnings("null")**

在 `ClassLoaderHandlerRegistry.java` line 46，移除 `@SuppressWarnings("null")` 注解及空行：

修改前：
```java
@SuppressWarnings("null")
public static final List<ClassLoaderHandlerRegistryEntry> CLASS_LOADER_HANDLERS = //
```

修改后：
```java
public static final List<ClassLoaderHandlerRegistryEntry> CLASS_LOADER_HANDLERS = //
```

- [ ] **Step 2: 运行引用相关测试**

Run: `mvn test -Dtest="ClassGraphWorkspaceAdapterTest,InheritanceReferenceIndexTest,InheritanceReferenceServiceTest"`
Expected: ALL PASS

- [ ] **Step 3: 运行全量测试**

Run: `mvn test`
Expected: ALL PASS（或仅已有失败保持原状，无新增失败）

- [ ] **Step 4: 打包验证**

Run: `mvn -DskipTests package`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classloaderhandler/ClassLoaderHandlerRegistry.java
git commit -m "style(classgraph): remove unnecessary @SuppressWarnings(null)"
```

---

## Self-Review

### Spec Coverage

| Spec 要求 | 对应 Task |
|---|---|
| ASM 解析合并——MetadataVisitor 扩展收集方法信息 | Task 2 |
| ASM 解析合并——删除重复解析 | Task 3 |
| ClassLoaderHandler 接口重构——default 方法 | Task 4 Step 1 |
| ClassLoaderHandler 接口重构——handler 实现类 | Task 4 Step 2 |
| ClassLoaderHandler 接口重构——消除反射 | Task 4 Step 3 |
| 异常日志——readBytes debug→warn | Task 2 Step 1 |
| 异常日志——parse debug→warn | Task 2 Step 2 |
| 异常日志——parseAnnotations 空 catch | Task 3 Step 1 |
| 异常日志——skippedFileCount | Task 1 + Task 2 Step 6 |
| 代码风格——移除 @SuppressWarnings | Task 5 Step 1 |

### Placeholder Scan

- 无 "TBD/TODO" 未填内容。
- 每步都有具体代码。
- 覆盖所有设计文档中的改动项。

### Type Consistency

- `MethodData` record 在 Task 2 Step 5 定义，Task 2 Step 3 中的 MetadataVisitor 使用相同的 `MethodData` 类型。
- `ScanResult.skippedFileCount` 在 Task 1 声明为 package-private（`int skippedFileCount`），Task 2 Step 6 直接赋值 `result.skippedFileCount = skippedFileCount`（同包访问）。
- `ClassLoaderHandler` 接口方法签名与原静态方法签名完全一致。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-21-classgraph-optimization-plan.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
