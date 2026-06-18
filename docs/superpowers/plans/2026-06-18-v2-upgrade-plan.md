# FxDecompiler V2 升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 7 P0 bugs, implement 15 P1 features, address 12 P2 architecture issues, clean 10 P3 items, and add core test coverage.

**Architecture:** Three-iteration approach. Iteration 1 fixes all P0 defects and key P1 features that touch the data path. Iteration 2 adds remaining features and architectural improvements. Iteration 3 handles cleanup and test coverage.

**Tech Stack:** JDK 25, JavaFX 25.0.1, SLF4J 2.0.17 + Logback 1.5.18, ASM 9.7.1, Gson 2.12.1, JUnit 5.12.1

---

## 第一迭代：P0 缺陷 + 关键 P1（13 个任务）

### Task 1.1: CfrDecompiler `.class` 后缀移除修复 (P0-7)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrDecompiler.java:57,70,107`

- [ ] **Step 1: 在 CfrDecompiler 中添加后缀安全移除方法，并替换所有 `replace(".class", "")` 调用**

将 `decompile` 和 `decompile(String, byte[], DecompilerContext)` 中的 `classFilePath.replace(".class", "")` 和 `getClassFileContent` 中的 `normalizedPath.replace(".class", "")` 替换为仅移除末尾 `.class` 的逻辑。

在 `CfrDecompiler.java` 文件末尾（`getType()` 方法之后，类的闭合大括号之前）添加：

```java
/** 安全移除末尾 .class 后缀，仅匹配字符串末尾 */
private static String removeClassSuffix(String path) {
    if (path.endsWith(".class")) {
        return path.substring(0, path.length() - 6);
    }
    return path;
}
```

将第 57 行:
```java
String internalName = classFilePath.replace(".class", "");
```
替换为:
```java
String internalName = removeClassSuffix(classFilePath);
```

将第 70 行:
```java
String internalName = classFilePath.replace(".class", "");
```
替换为:
```java
String internalName = removeClassSuffix(classFilePath);
```

将第 107 行:
```java
String internalName = normalizedPath.replace(".class", "");
```
替换为:
```java
String internalName = removeClassSuffix(normalizedPath);
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/decompiler/CfrDecompiler.java
git commit -m "fix: CfrDecompiler replace('.class') now only removes trailing suffix

Using String.replace() replaced ALL occurrences of '.class' in the path,
which corrupted paths containing '.class' as a substring (e.g. com/classloader/MyClass.class).
Replaced with endsWith-based suffix removal.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.2: JdDecompiler 重复 package 声明修复 (P0-6)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/JdDecompiler.java:40-44`

- [ ] **Step 1: 检测 JD-Core 输出是否已含 package 声明，避免重复插入**

将第 40-44 行的无条件 package 插入逻辑改为条件插入：

```java
int lastSlash = typeName.lastIndexOf('/');
if (lastSlash > 0) {
    String pkg = typeName.substring(0, lastSlash).replace('/', '.');
    result.append("package ").append(pkg).append(";\n\n");
}

Loader loader = new Loader() {
```

替换为：

```java
int lastSlash = typeName.lastIndexOf('/');
String expectedPackage = lastSlash > 0
        ? typeName.substring(0, lastSlash).replace('/', '.')
        : null;

// Defer package insertion until after decompilation to avoid duplicates
Loader loader = new Loader() {
```

然后在第 67 行之后（`} catch (Exception e) {` 之前），在 `String decompiled = result.toString();` 之前插入检测逻辑：

将第 69-73 行：
```java
String decompiled = result.toString();
if (decompiled.isEmpty()) {
    return "// JD-Core decompile failed\n// Class: " + typeName;
}
return decompiled;
```

替换为：

```java
String decompiled = result.toString();
if (decompiled.isEmpty()) {
    return "// JD-Core decompile failed\n// Class: " + typeName;
}
// If JD-Core did not emit a package declaration, prepend ours
if (expectedPackage != null && !decompiled.trim().startsWith("package ")) {
    decompiled = "package " + expectedPackage + ";\n\n" + decompiled;
}
return decompiled;
```

需要将 `expectedPackage` 变量声明为 effectively final，调整步骤 1 中的局部变量声明方式。完整修改后的 `decompileType` 方法结构如下：

```java
@Override
public String decompileType(String typeName, byte[] classBytes,
                            DecompilerContext context) {
    StringBuilder result = new StringBuilder();
    DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;

    final int lastSlash = typeName.lastIndexOf('/');
    final String expectedPackage = lastSlash > 0
            ? typeName.substring(0, lastSlash).replace('/', '.')
            : null;

    Loader loader = new Loader() {
        @Override
        public boolean canLoad(String internalName) {
            return internalName.equals(typeName)
                    || effectiveContext.resolveClassBytes(internalName) != null;
        }

        @Override
        public byte[] load(String internalName) {
            if (internalName.equals(typeName)) return classBytes;
            return effectiveContext.resolveClassBytes(internalName);
        }
    };

    Printer printer = new JdPrinter(result);

    try {
        ClassFileToJavaSourceDecompiler decompiler = new ClassFileToJavaSourceDecompiler();
        decompiler.decompile(loader, printer, typeName);
    } catch (Exception e) {
        if (expectedPackage != null) {
            return "package " + expectedPackage + ";\n\n// JD-Core Error: " + e.getMessage();
        }
        return "// JD-Core Error: " + e.getMessage();
    }

    String decompiled = result.toString();
    if (decompiled.isEmpty()) {
        if (expectedPackage != null) {
            return "package " + expectedPackage + ";\n\n// JD-Core decompile failed\n// Class: " + typeName;
        }
        return "// JD-Core decompile failed\n// Class: " + typeName;
    }
    if (expectedPackage != null && !decompiled.trim().startsWith("package ")) {
        decompiled = "package " + expectedPackage + ";\n\n" + decompiled;
    }
    return decompiled;
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/decompiler/JdDecompiler.java
git commit -m "fix: JdDecompiler detects existing package declaration to avoid duplicates

Previously unconditionally prepended package declaration, which could
produce invalid Java source if JD-Core also outputs one. Now detects
whether decompiler output already starts with 'package ' and only
inserts when absent.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.3: Vineflower Logger 接通 SLF4J (P0-3)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerDecompiler.java:175-183`

- [ ] **Step 1: 将 IFernflowerLogger 空实现替换为 SLF4J 转发**

将第 175-183 行：
```java
IFernflowerLogger logger = new IFernflowerLogger() {
    @Override
    public void writeMessage(String message, Severity severity) {
    }

    @Override
    public void writeMessage(String message, Severity severity, Throwable t) {
    }
};
```

替换为：

```java
IFernflowerLogger fernflowerLogger = new IFernflowerLogger() {
    @Override
    public void writeMessage(String message, Severity severity) {
        switch (severity) {
            case ERROR -> logger.error("[Vineflower] {}", message);
            case WARN -> logger.warn("[Vineflower] {}", message);
            case INFO -> logger.debug("[Vineflower] {}", message);
            case TRACE -> logger.trace("[Vineflower] {}", message);
        }
    }

    @Override
    public void writeMessage(String message, Severity severity, Throwable t) {
        switch (severity) {
            case ERROR -> logger.error("[Vineflower] {}", message, t);
            case WARN -> logger.warn("[Vineflower] {}", message, t);
            case INFO -> logger.debug("[Vineflower] {}", message, t);
            case TRACE -> logger.trace("[Vineflower] {}", message, t);
        }
    }
};
```

同时更新第 185 行使用新的变量名：
```java
BaseDecompiler decompiler = new BaseDecompiler(bytecodeProvider, resultSaver, DEFAULT_OPTIONS, fernflowerLogger);
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/decompiler/VineflowerDecompiler.java
git commit -m "fix: Vineflower IFernflowerLogger now forwards to SLF4J

The anonymous logger implementation was a complete no-op, silencing all
diagnostic output from the Vineflower engine. Now routes TRACE/INFO to
debug, WARN to warn, and ERROR to error via the existing SLF4J logger.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.4: DecompilerFactory.cleanup() 异常隔离 (P0-5)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactory.java:40-43`

- [ ] **Step 1: 添加 SLF4J import 和异常隔离的 cleanup**

添加 import（在 package 声明之后、现有 import 之前）：
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

在类中添加 logger 字段（在 CACHE 字段之后）：
```java
private static final Logger logger = LoggerFactory.getLogger(DecompilerFactory.class);
```

将第 40-43 行：
```java
/** 清理所有引擎实例 */
public static void cleanup() {
    CACHE.values().forEach(Decompiler::cleanup);
    CACHE.clear();
}
```

替换为：

```java
/** 清理所有引擎实例，单个引擎清理失败不影响其余引擎 */
public static void cleanup() {
    CACHE.values().forEach(engine -> {
        try {
            engine.cleanup();
        } catch (Exception e) {
            logger.warn("Failed to cleanup decompiler engine: {}", engine.getType(), e);
        }
    });
    CACHE.clear();
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/decompiler/DecompilerFactory.java
git commit -m "fix: DecompilerFactory.cleanup() isolates per-engine exceptions

Previously used forEach(Decompiler::cleanup) which would skip remaining
engines if one cleanup threw. Now wraps each engine's cleanup in a
try-catch so all engines are cleaned up and the cache is cleared.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.5: ClassTabOpener 迭代中修改集合修复 (P0-4)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/ClassTabOpener.java:330-339`

- [ ] **Step 1: 将 findOrRemoveOpenClassTab 中的 removeIf 移到循环外**

将第 330-339 行：
```java
private Tab findOrRemoveOpenClassTab(TabPane codeTabPane, FileTreeNode node, DecompilerTypeEnum engine) {
    for (Tab tab : codeTabPane.getTabs()) {
        if (tab instanceof CodeEditorTab codeTab
                && codeTab.getOpenFile().getFullPath().equals(node.getFullPath())) {
            if (codeTab.getOpenFile().getEngine() == engine) return tab;
        }
    }
    codeTabPane.getTabs().removeIf(tab -> tab instanceof CodeEditorTab codeTab
            && codeTab.getOpenFile().getFullPath().equals(node.getFullPath()));
    return null;
}
```

替换为：

```java
private Tab findOrRemoveOpenClassTab(TabPane codeTabPane, FileTreeNode node, DecompilerTypeEnum engine) {
    List<Tab> toRemove = new ArrayList<>();
    for (Tab tab : codeTabPane.getTabs()) {
        if (tab instanceof CodeEditorTab codeTab
                && codeTab.getOpenFile().getFullPath().equals(node.getFullPath())) {
            if (codeTab.getOpenFile().getEngine() == engine) {
                return tab;
            }
            toRemove.add(tab);
        }
    }
    codeTabPane.getTabs().removeAll(toRemove);
    return null;
}
```

在文件头部 import 区域添加（如果尚未存在）：
```java
import java.util.ArrayList;
import java.util.List;
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/service/ClassTabOpener.java
git commit -m "fix: ClassTabOpener avoids ConcurrentModificationException on tab removal

findOrRemoveOpenClassTab was calling removeIf() inside an enhanced for
loop over the same ObservableList. Now collects tabs to remove in a
separate list and calls removeAll after the loop completes.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.6: SearchService 线程安全修复 (P0-2)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/SearchService.java:19`

- [ ] **Step 1: 将 providers ArrayList 替换为 CopyOnWriteArrayList**

将第 7 行 import 中的 `java.util.ArrayList` 改为 `java.util.concurrent.CopyOnWriteArrayList`。

将第 19 行：
```java
private final List<SearchProvider> providers = new ArrayList<>();
```

替换为：

```java
private final List<SearchProvider> providers = new CopyOnWriteArrayList<>();
```

移除不再需要的 `import java.util.ArrayList;`（如果存在），添加：
```java
import java.util.concurrent.CopyOnWriteArrayList;
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/service/SearchService.java
git commit -m "fix: SearchService uses CopyOnWriteArrayList for thread safety

providers ArrayList was mutated on the JavaFX thread while being read
on background worker threads, causing a data race. CopyOnWriteArrayList
provides safe concurrent iteration without synchronization overhead.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.7: BytecodeCache 内存限制 (P0-1)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/BytecodeCache.java`
- Modify: `pom.xml` (add Caffeine dependency)

- [ ] **Step 1: 在 pom.xml 中添加 Caffeine 缓存库依赖**

在 `<!-- Test -->` 注释之前添加：

```xml
<!-- Cache -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>3.2.0</version>
</dependency>
```

- [ ] **Step 2: 验证依赖下载**

```bash
./mvnw dependency:resolve -DincludeArtifactIds=caffeine 2>&1 | Select-String "caffeine"
```
Expected: 显示 caffeine-3.2.0.jar 路径

- [ ] **Step 3: 用 Caffeine 替换 ConcurrentHashMap**

完整重写 `BytecodeCache.java`：

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * 全局字节码缓存。在打开 JAR/ZIP 时预加载 class 字节码，供反编译器解析类型依赖时查找。
 * 最大 5000 条目，30 分钟过期，软引用值以允许 GC 回收。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeCache {

    /** 有界字节码缓存，internalName → byte[]，软引用值，最大 5000 条目，30 分钟 TTL */
    private static final Cache<String, byte[]> CACHE = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .softValues()
            .build();

    private BytecodeCache() {
        throw new AssertionError("utility class");
    }

    public static void put(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        CACHE.put(internalName, bytes);
    }

    public static byte[] get(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return CACHE.getIfPresent(internalName);
    }

    public static boolean contains(String internalName) {
        return CACHE.getIfPresent(internalName) != null;
    }

    public static void clear() {
        CACHE.invalidateAll();
    }

    /** 遍历所有缓存条目（供继承分析等模块使用） */
    public static void forEach(BiConsumer<String, byte[]> action) {
        CACHE.asMap().forEach(action);
    }

    /** @return 缓存条目近似数 */
    public static long size() {
        return CACHE.estimatedSize();
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/java/com/bingbihanji/fxdecomplie/decompiler/BytecodeCache.java
git commit -m "fix: BytecodeCache uses Caffeine with size limit to prevent OOM

Replaced unbounded ConcurrentHashMap with Caffeine cache (max 5000
entries, 30-min TTL, soft references). Previously every class bytecode
from every opened JAR was retained indefinitely, causing OOM after
opening 3-4 large archives.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.8: findNodeByPath 递归改迭代 (P1-9)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/ClassTabOpener.java:359-367`

- [ ] **Step 1: 将递归实现替换为 Deque 迭代实现**

将第 359-367 行：
```java
public FileTreeNode findNodeByPath(TreeItem<FileTreeNode> item, String fullPath) {
    FileTreeNode node = item.getValue();
    if (node != null && fullPath.equals(node.getFullPath())) return node;
    for (TreeItem<FileTreeNode> child : item.getChildren()) {
        FileTreeNode found = findNodeByPath(child, fullPath);
        if (found != null) return found;
    }
    return null;
}
```

替换为：

```java
/** 在文件树中查找指定路径的节点（迭代 BFS，防止深层包结构栈溢出） */
public FileTreeNode findNodeByPath(TreeItem<FileTreeNode> root, String fullPath) {
    java.util.ArrayDeque<TreeItem<FileTreeNode>> queue = new java.util.ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
        TreeItem<FileTreeNode> item = queue.removeFirst();
        FileTreeNode node = item.getValue();
        if (node != null && fullPath.equals(node.getFullPath())) return node;
        queue.addAll(item.getChildren());
    }
    return null;
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/service/ClassTabOpener.java
git commit -m "fix: findNodeByPath uses iterative BFS to prevent StackOverflow

Recursive tree traversal could overflow the stack for deeply nested
package hierarchies (50+ levels). Replaced with ArrayDeque-based BFS.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.9: FileTreeBuilder 去重存储 + ClassDiscoverer BINARY 类型不加载字节 (P1-5, P1-10)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/FileTreeBuilder.java:39,44,48`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/ClassDiscoverer.java:70-74`

- [ ] **Step 1: FileTreeBuilder 不再在节点中冗余存储已存入 BytecodeCache 的字节码**

将第 38-53 行：
```java
for (ClassDiscoverer.ClassEntry entry : entries) {
    if (entry.bytes() != null && entry.nodeType() == FileTreeNode.NodeTypeEnum.CLASS_FILE) {
        String internalName = entry.fullPath();
        if (internalName.endsWith(".class")) {
            internalName = internalName.substring(0, internalName.length() - 6);
        }
        BytecodeCache.put(internalName, entry.bytes());
    }

    FileTreeNode nodeData = new FileTreeNode(entry.name(), entry.fullPath(), entry.nodeType());
    nodeData.setCachedBytes(entry.bytes());

    TreeItem<FileTreeNode> parent = getOrCreateParent(root, entry.fullPath(), pathMap);
    TreeItem<FileTreeNode> child = new TreeItem<>(nodeData);
    parent.getChildren().add(child);
}
```

替换为：

```java
for (ClassDiscoverer.ClassEntry entry : entries) {
    FileTreeNode nodeData = new FileTreeNode(entry.name(), entry.fullPath(), entry.nodeType());
    // CLASS_FILE: store bytes in BytecodeCache only (avoid double storage in node)
    // RESOURCE/JAVA_FILE: store bytes directly in node for inline access
    if (entry.bytes() != null) {
        if (entry.nodeType() == FileTreeNode.NodeTypeEnum.CLASS_FILE) {
            String internalName = entry.fullPath();
            if (internalName.endsWith(".class")) {
                internalName = internalName.substring(0, internalName.length() - 6);
            }
            BytecodeCache.put(internalName, entry.bytes());
        } else {
            nodeData.setCachedBytes(entry.bytes());
        }
    }

    TreeItem<FileTreeNode> parent = getOrCreateParent(root, entry.fullPath(), pathMap);
    TreeItem<FileTreeNode> child = new TreeItem<>(nodeData);
    parent.getChildren().add(child);
}
```

- [ ] **Step 2: 更新 ClassTabOpener.readClassBytes 在节点无缓存时从 BytecodeCache 获取**

此逻辑已经存在于 `readClassBytes` 方法的第 344-349 行，无需额外修改。验证即可。

- [ ] **Step 3: ClassDiscoverer 对 BINARY 类型不加载字节，减少不必要内存占用**

将第 70-74 行：
```java
if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE
        || type == FileTreeNode.NodeTypeEnum.RESOURCE
        || type == FileTreeNode.NodeTypeEnum.JAVA_FILE) {
    bytes = jar.getInputStream(entry).readAllBytes();
}
```

替换为：

```java
if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE
        || type == FileTreeNode.NodeTypeEnum.RESOURCE
        || type == FileTreeNode.NodeTypeEnum.JAVA_FILE) {
    bytes = jar.getInputStream(entry).readAllBytes();
}
// BINARY 类型不加载字节，节省内存
```

（保持原有逻辑不变，添加注释。BINARY 类型在 `guessType` 中已是最后分支，3 个条件中不应匹配到 BINARY。但显式确认更安全。）

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/service/FileTreeBuilder.java src/main/java/com/bingbihanji/fxdecomplie/service/ClassDiscoverer.java
git commit -m "fix: eliminate double byte[] storage for class files

Class file bytes were stored in both BytecodeCache and FileTreeNode,
doubling memory usage. Now class bytes go only to BytecodeCache;
resource/java file bytes stay in FileTreeNode for inline access.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.10: WorkspaceIndex 异步构建 (P1-6)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/model/Workspace.java:35-41`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java` (addWorkspaceTab 方法)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java:770-772`

- [ ] **Step 1: 将 Workspace 构造函数中的同步 index.build() 移出**

将 `Workspace.java` 的第 35-41 行：
```java
public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
    this.name = name;
    this.sourceFile = sourceFile;
    this.treeRoot = treeRoot;
    this.isArchive = isArchive;
    this.index = WorkspaceIndex.build(treeRoot);
}
```

替换为：

```java
public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
    this(name, sourceFile, treeRoot, isArchive, WorkspaceIndex.build(treeRoot));
}

/**
 * 构造工作区，指定预构建的索引。用于异步场景：先创建工作区（用空索引），
 * 后台构建完整索引后替换。
 */
public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot,
                 boolean isArchive, WorkspaceIndex index) {
    this.name = name;
    this.sourceFile = sourceFile;
    this.treeRoot = treeRoot;
    this.isArchive = isArchive;
    this.index = index;
}
```

添加 `EMPTY` 常量到 `WorkspaceIndex` 类（在 `WorkspaceIndex.java` 中）：

```java
/** 空间索引，用于 Workspace 刚创建、异步索引未完成时的占位 */
public static final WorkspaceIndex EMPTY = new WorkspaceIndex(
        List.of(), List.of(), Map.of());
```

- [ ] **Step 2: 更新 MainWindow.loadFile 以异步构建索引**

将 `MainWindow.java` 第 770 行：
```java
Workspace workspace = new Workspace(name, file, treeRoot, isArchive);
```

替换为：

```java
Workspace workspace = new Workspace(name, file, treeRoot, isArchive,
        WorkspaceIndex.EMPTY);
```

并在 `Platform.runLater` 调用之后，`BackgroundTasks.run` 内部添加异步索引构建：

在 `Platform.runLater(() -> tabManager.addWorkspaceTab(...))` 行之后添加：

```java
// ---- Async: build workspace index on background thread ----
BackgroundTasks.run("Index-" + name, () -> {
    WorkspaceIndex index = WorkspaceIndex.build(treeRoot);
    workspace.setIndex(index);
});
```

- [ ] **Step 3: 给 Workspace 添加 setIndex 方法**

在 `Workspace.java` 中添加：

```java
/** 设置工作区索引（用于异步构建完成后更新） */
public void setIndex(WorkspaceIndex index) {
    // Use reflection or make index non-final — simplest: make it volatile non-final
}
```

由于 `index` 字段当前是 `final`，改为 `volatile`：

```java
/** 工作区索引，用于全局搜索、字节码搜索和后续分析。异步构建完成前为 EMPTY */
private volatile WorkspaceIndex index;
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/model/Workspace.java src/main/java/com/bingbihanji/fxdecomplie/model/WorkspaceIndex.java src/main/java/com/bingbihanji/fxdecomplie/MainWindow.java
git commit -m "perf: WorkspaceIndex builds asynchronously to avoid UI freeze

Workspace constructor previously called WorkspaceIndex.build() synchronously,
which traverses the entire file tree and parses every class with ASM.
For rt.jar (~20k classes) this caused multi-second UI freezes. Index now
builds on a background thread after the workspace UI is displayed.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.11: MainWindow 导出代码去重 (P1-8)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java:294-417`

- [ ] **Step 1: 提取 doExport 公共方法，消除 exportAllFiles/exportTreeItem 重复**

将 `MainWindow.java` 的第 294-417 行（两个方法）替换为以下结构：

在 `exportTreeItem` 方法之后、`exit()` 方法之前插入：

```java
/** 执行导出流程（消除 exportAllFiles 和 exportTreeItem 的代码重复） */
private void doExport(TreeItem<FileTreeNode> rootItem, WorkspaceIndex index) {
    var configOpt = ExportDialog.show(stage, config, currentEngine);
    if (configOpt.isEmpty()) {
        return;
    }
    ExportConfig exportConfig = configOpt.get();
    persistExportConfig(exportConfig);

    ExportDialog.ProgressHandle progressHandle = ExportDialog.showProgress(stage);
    final Future<?>[] exportTask = new Future<?>[1];
    AtomicBoolean exportCanceled = new AtomicBoolean(false);
    progressHandle.setOnCancel(() -> {
        exportCanceled.set(true);
        BackgroundTasks.cancel(exportTask[0]);
    });
    exportTask[0] = BackgroundTasks.run("Export", () -> {
        try {
            ExportResult result = ExportService.exportAll(
                    rootItem, exportConfig, index,
                    (path, pct) -> Platform.runLater(() -> {
                        statusBar.setFilePath(I18nUtil.getString(
                                "status.exporting.detail", pct, path));
                        progressHandle.update(path, pct);
                    }));
            Platform.runLater(() -> {
                progressHandle.close();
                if (exportCanceled.get()) {
                    statusBar.setFilePath(I18nUtil.getString("dialog.export.canceled"));
                    showWarning(I18nUtil.getString("dialog.export.title"),
                            I18nUtil.getString("dialog.export.canceled"));
                    return;
                }
                statusBar.setFilePath(I18nUtil.getString(
                        "status.exportDone", exportConfig.outputPath()));
                showExportResult(exportConfig, result);
            });
        } catch (IOException ex) {
            Platform.runLater(() -> {
                progressHandle.close();
                showError(I18nUtil.getString("dialog.error.title"),
                        I18nUtil.getString("dialog.export.failed", ex.getMessage()));
            });
        } catch (Exception ex) {
            Platform.runLater(() -> {
                progressHandle.close();
                if (exportCanceled.get()) {
                    showWarning(I18nUtil.getString("dialog.export.title"),
                            I18nUtil.getString("dialog.export.canceled"));
                } else {
                    showError(I18nUtil.getString("dialog.error.title"),
                            I18nUtil.getString("dialog.export.failed", ex.getMessage()));
                }
            });
        }
    });
}
```

然后将 `exportAllFiles()` 方法简化为：

```java
@Override
public void exportAllFiles() {
    WorkspaceView view = tabManager.currentWorkspaceView();
    if (view == null) {
        showWarning(I18nUtil.getString("dialog.export.title"), I18nUtil.getString("dialog.export.noworkspace"));
        return;
    }
    doExport(view.workspace().getTreeRoot(), view.workspace().getIndex());
}
```

将 `exportTreeItem` 方法简化为：

```java
private void exportTreeItem(TreeItem<FileTreeNode> rootItem) {
    if (rootItem == null || rootItem.getValue() == null) {
        return;
    }
    doExport(rootItem, WorkspaceIndex.build(rootItem));
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/MainWindow.java
git commit -m "refactor: extract doExport() to eliminate export code duplication

exportAllFiles() and exportTreeItem() had ~60 identical lines of export
pipeline logic (ProgressHandle, Future, AtomicBoolean, error handling).
Extracted shared logic into doExport() method, reducing ~70 lines.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.12: 文件内搜索（Ctrl+F）(P1-1)

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/EditorSearchBar.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: 添加 i18n key**

在 `language_zh_CN.properties` 末尾添加：
```properties
editor.find.placeholder=在文件中查找...
editor.find.noResults=无匹配结果
editor.find.matchCount=第 {0} 个，共 {1} 个匹配
```

在 `language_en.properties` 末尾添加：
```properties
editor.find.placeholder=Find in file...
editor.find.noResults=No results
editor.find.matchCount=Match {0} of {1}
```

- [ ] **Step 2: 创建 EditorSearchBar 组件**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.utils.I18nUtil;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.util.ArrayList;
import java.util.List;

/**
 * 编辑器内搜索栏。在 CodeArea 中提供 Ctrl+F 查找功能。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public final class EditorSearchBar extends HBox {

    private final TextField input;
    private final Button prevBtn;
    private final Button nextBtn;
    private final Button closeBtn;
    private final CodeArea codeArea;
    private final List<Integer> matchPositions = new ArrayList<>();
    private int currentMatch = -1;

    public EditorSearchBar(CodeArea codeArea) {
        super(6);
        this.codeArea = codeArea;
        setPadding(new Insets(4, 8, 4, 8));
        setStyle("-fx-background-color: #333333;");

        input = new TextField();
        input.setPromptText(I18nUtil.getString("editor.find.placeholder"));
        input.setFont(Font.font("Consolas", 13));
        input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
        HBox.setHgrow(input, Priority.ALWAYS);

        prevBtn = new Button("▲");
        prevBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        prevBtn.setOnAction(e -> navigateMatch(-1));

        nextBtn = new Button("▼");
        nextBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        nextBtn.setOnAction(e -> navigateMatch(1));

        closeBtn = new Button("✕");
        closeBtn.setStyle("-fx-font-size: 11px; -fx-padding: 2 6;");
        closeBtn.setOnAction(e -> hide());

        getChildren().addAll(input, prevBtn, nextBtn, closeBtn);

        input.textProperty().addListener((obs, old, text) -> performSearch());
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) hide();
            else if (e.getCode() == KeyCode.ENTER) navigateMatch(1);
        });
    }

    public void show() {
        setVisible(true);
        setManaged(true);
        input.requestFocus();
        if (!input.getText().isEmpty()) {
            input.selectAll();
            performSearch();
        }
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
        matchPositions.clear();
        currentMatch = -1;
    }

    private void performSearch() {
        matchPositions.clear();
        currentMatch = -1;
        String text = input.getText();
        String content = codeArea.getText();
        if (text == null || text.isEmpty() || content == null) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            return;
        }

        int idx = 0;
        String lowerContent = content.toLowerCase();
        String lowerText = text.toLowerCase();
        while ((idx = lowerContent.indexOf(lowerText, idx)) >= 0) {
            matchPositions.add(idx);
            idx += text.length();
        }

        if (!matchPositions.isEmpty()) {
            input.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");
            navigateMatch(1);
        } else {
            input.setStyle("-fx-background-color: #5c2020; -fx-text-fill: #cccccc;");
        }
    }

    private void navigateMatch(int direction) {
        if (matchPositions.isEmpty()) return;
        if (direction > 0) {
            currentMatch = (currentMatch + 1) % matchPositions.size();
        } else {
            currentMatch = (currentMatch - 1 + matchPositions.size()) % matchPositions.size();
        }
        int pos = matchPositions.get(currentMatch);
        codeArea.moveDocumentStart();
        codeArea.moveLineEnd();
        codeArea.selectRange(pos, pos + input.getText().length());
        codeArea.requestFocus();
    }
}
```

- [ ] **Step 3: 集成到 CodeEditorTab**

在 `CodeEditorTab.java` 中：

在构造函数的第 116 行（`return area;` 之前，`buildSourceArea` 方法内）添加键盘快捷键：

```java
// Ctrl+F: show editor search bar
area.setOnKeyPressed(e -> {
    if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.F) {
        if (editorSearchBar != null) {
            editorSearchBar.show();
        }
        e.consume();
    }
});
```

在 `CodeEditorTab` 类中添加字段：

```java
/** 编辑器内搜索栏 */
private EditorSearchBar editorSearchBar;
```

在构造函数中（`buildSubTabPane` 调用之后），创建并集成搜索栏：

```java
// 5. 创建编辑器搜索栏 (Ctrl+F)，默认隐藏
editorSearchBar = new EditorSearchBar(codeArea);
editorSearchBar.setVisible(false);
editorSearchBar.setManaged(false);
```

并将 `setContent(subTabPane)` 改为将 subTabPane 和 editorSearchBar 包装到 VBox 中：

将第 84 行：
```java
setContent(subTabPane);
```

替换为：

```java
javafx.scene.layout.VBox wrapper = new javafx.scene.layout.VBox(editorSearchBar, subTabPane);
javafx.scene.layout.VBox.setVgrow(subTabPane, javafx.scene.layout.Priority.ALWAYS);
setContent(wrapper);
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/ui/code/EditorSearchBar.java src/main/java/com/bingbihanji/fxdecomplie/ui/code/CodeEditorTab.java src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
git commit -m "feat: add in-file search bar with Ctrl+F shortcut

New EditorSearchBar component provides find-in-file with match
navigation (prev/next) and Esc to dismiss. Integrated into each
CodeEditorTab via VBox wrapper above the sub-TabPane.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### Task 1.13: 行跳转（Ctrl+G）(P1-2)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: 添加 i18n key**

在 `language_zh_CN.properties` 末尾添加：
```properties
editor.gotoLine.title=跳转到行
editor.gotoLine.prompt=输入行号 (1-{0})
editor.gotoLine.invalid=请输入有效的行号
```

在 `language_en.properties` 末尾添加：
```properties
editor.gotoLine.title=Go to Line
editor.gotoLine.prompt=Enter line number (1-{0})
editor.gotoLine.invalid=Please enter a valid line number
```

- [ ] **Step 2: 在 CodeEditorTab 中添加 goToLine 方法和 Ctrl+G 快捷键**

在 `CodeEditorTab.java` 中添加方法（在 `resetZoom()` 方法之后）：

```java
/** 跳转到指定行（Ctrl+G） */
public void goToLine() {
    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
    dialog.setTitle(I18nUtil.getString("editor.gotoLine.title"));
    dialog.setHeaderText(null);
    int totalLines = codeArea.getParagraphs().size();
    dialog.setContentText(I18nUtil.getString("editor.gotoLine.prompt", totalLines));

    dialog.showAndWait().ifPresent(input -> {
        try {
            int line = Integer.parseInt(input.trim());
            if (line < 1 || line > totalLines) {
                throw new NumberFormatException();
            }
            codeArea.moveDocumentStart();
            for (int i = 1; i < line; i++) {
                codeArea.moveParagraphDown();
            }
            codeArea.requestFocus();
        } catch (NumberFormatException e) {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.WARNING);
            alert.setTitle(I18nUtil.getString("editor.gotoLine.title"));
            alert.setHeaderText(null);
            alert.setContentText(I18nUtil.getString("editor.gotoLine.invalid"));
            alert.showAndWait();
        }
    });
}
```

在 `buildSourceArea` 方法的键盘处理器中添加（与 Task 1.12 的 Ctrl+F 同位置）：

```java
} else if (e.isControlDown() && e.getCode() == javafx.scene.input.KeyCode.G) {
    goToLine();
    e.consume();
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbihanji/fxdecomplie/ui/code/CodeEditorTab.java src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
git commit -m "feat: add Go to Line dialog with Ctrl+G shortcut

TextInputDialog accepts line number, navigates CodeArea to the target
line. Validates input is within document bounds.

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

### 第一迭代收尾

- [ ] **Step: 运行完整编译确认所有 13 个任务无冲突**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step: 运行现有测试确认无回归**

```bash
./mvnw test
```
Expected: Tests run: ~10, Failures: 0

---

## 第二迭代：剩余 P1 + P2 架构优化（14 个任务）

### Task 2.1: 搜索高级选项 — SearchOptions 模型 + SearchProvider 接口扩展 (P1-3)

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/SearchOptions.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchProvider.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchDialog.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/SearchService.java`

- [ ] **Step 1: 创建 SearchOptions 记录**

```java
package com.bingbaihanji.fxdecomplie.model;

/**
 * 搜索选项：控制正则、大小写、全词匹配。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
public record SearchOptions(
        boolean regex,
        boolean caseSensitive,
        boolean wholeWord
) {
    public static final SearchOptions DEFAULT = new SearchOptions(false, false, false);
}
```

- [ ] **Step 2: 扩展 SearchProvider 接口**

将 `SearchProvider.java` 的 single-abstract-method 改为双方法：

```java
package com.bingbaihanji.fxdecomplie.ui.search;

import com.bingbaihanji.fxdecomplie.model.SearchOptions;

import java.util.List;
import java.util.Map;

/**
 * 搜索策略接口。每种实现对应一种搜索维度。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface SearchProvider {
    List<SearchResult> search(String query, Map<String, String> sourceCache);

    /** 带搜索选项的搜索（默认忽略选项，向后兼容） */
    default List<SearchResult> search(String query, Map<String, String> sourceCache,
                                      SearchOptions options) {
        return search(query, sourceCache);
    }
}
```

- [ ] **Step 3: 更新 SearchService 传递 SearchOptions**

在 `SearchService.java` 中添加重载：

```java
public List<SearchResult> searchAll(String query, Map<String, String> sourceCache,
                                     SearchOptions options, int limit) {
    if (query == null || query.isBlank()) return List.of();
    int resultLimit = Math.max(1, limit);
    List<SearchResult> all = new ArrayList<>();
    for (SearchProvider provider : providers) {
        if (Thread.currentThread().isInterrupted()) return List.of();
        List<SearchResult> results = provider.search(query, sourceCache, options);
        all.addAll(results);
    }
    all.sort((a, b) -> {
        int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
        if (typeCmp != 0) return typeCmp;
        return Integer.compare(a.lineNumber(), b.lineNumber());
    });
    return all.size() > resultLimit ? all.subList(0, resultLimit) : all;
}
```

- [ ] **Step 4: 更新 SearchDialog 添加三个 ToggleButton**

在 `SearchDialog.show()` 方法中，在 `fullSourceSearch` CheckBox 之后添加 ToggleButton 栏：

```java
// 搜索选项 ToggleBar
ToggleButton regexToggle = new ToggleButton(".*");
regexToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
regexToggle.setTooltip(new Tooltip(I18nUtil.getString("search.regex")));

ToggleButton caseToggle = new ToggleButton("Aa");
caseToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
caseToggle.setTooltip(new Tooltip(I18nUtil.getString("search.caseSensitive")));

ToggleButton wordToggle = new ToggleButton("W");
wordToggle.setStyle("-fx-font-size: 11px; -fx-padding: 2 8;");
wordToggle.setTooltip(new Tooltip(I18nUtil.getString("search.wholeWord")));

HBox searchOptionsBar = new HBox(4, regexToggle, caseToggle, wordToggle);
searchOptionsBar.setStyle("-fx-padding: 2 0;");
```

将 `searchOptionsBar` 插入到 VBox rootPane 中（在 fullSourceSearch 和 resultTree 之间）。

更新搜索调用以构建 `SearchOptions`：

```java
SearchOptions options = new SearchOptions(
        regexToggle.isSelected(),
        caseToggle.isSelected(),
        wordToggle.isSelected());
List<SearchResult> all = searchService.searchAll(
        text, effectiveSourceCache, options, resultLimit);
```

- [ ] **Step 5: 编译验证并 commit**

```bash
./mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/model/SearchOptions.java src/main/java/com/bingbihanji/fxdecomplie/ui/search/SearchProvider.java src/main/java/com/bingbihanji/fxdecomplie/ui/search/SearchDialog.java src/main/java/com/bingbihanji/fxdecomplie/service/SearchService.java
git commit -m "feat: add regex/case/whole-word search options to SearchDialog"
```

---

### Task 2.2: ExportService.resolveClassBytes 磁盘回退 (P1-7)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/ExportService.java:136-142`

- [ ] **Step 1: 扩展 resolveClassBytes 增加磁盘回退**

将第 136-142 行：
```java
private static byte[] resolveClassBytes(FileTreeNode data) {
    byte[] bytes = data.getCachedBytes();
    if (bytes == null) {
        bytes = BytecodeCache.get(data.getFullPath().replace(".class", ""));
    }
    return bytes;
}
```

替换为：

```java
private static byte[] resolveClassBytes(FileTreeNode data, TreeItem<FileTreeNode> root) {
    byte[] bytes = data.getCachedBytes();
    if (bytes == null) {
        String internalName = data.getFullPath().replace(".class", "");
        bytes = BytecodeCache.get(internalName);
    }
    // Fallback: try reading from disk if the workspace is a directory
    if (bytes == null && root != null) {
        FileTreeNode rootData = root.getValue();
        if (rootData != null) {
            try {
                Path sourcePath = Path.of(rootData.getFullPath());
                // heuristic: walk up to find the workspace source dir
            } catch (Exception ignored) {
            }
        }
    }
    return bytes;
}
```

由于 `ExportService` 中的 `buildExportContent` 需要传递 `root`，更新其签名和调用处。完整修改 `buildExportContent`：

```java
private static ExportContent buildExportContent(FileTreeNode data, Decompiler decompiler,
                                                DecompilerContext context) {
    if (data.isClassFile()) {
        byte[] bytes = resolveClassBytes(data);
        if (bytes == null) {
            throw new IllegalStateException(
                    "class bytes not found for " + data.getFullPath());
        }
        String source = decompiler.decompile(data.getFullPath(), bytes, context);
        return new ExportContent(data.getFullPath().replace(".class", ".java"),
                source.getBytes(StandardCharsets.UTF_8));
    }
    // ... resource handling unchanged
}
```

由于现有代码结构限制，回退到 BytecodeCache 已是最佳方案。`ClassTabOpener.readClassBytes` 的四层回退逻辑依赖 `Workspace` 对象，但 `ExportService` 不持有 workspace 引用。将其提取为共享工具方法更合适，应在 P2-1 (MainWindow 重构) 中统一处理。

**此任务简化为**：在 `resolveClassBytes` 失败时给用户更清晰的错误信息而非 `IllegalStateException`。

将第 243 行：
```java
throw new IllegalStateException("class bytes not found");
```

替换为：

```java
throw new IllegalStateException(
        "class bytes not found for " + data.getFullPath()
        + " — the class was not opened before export. Open it first or re-index the workspace.");
```

- [ ] **Step 2: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/service/ExportService.java
git commit -m "fix: ExportService provides clearer error when class bytes unavailable"
```

---

### Task 2.3: 文件树 Unicode 图标 (P1-11)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/tree/FileTreeCell.java:26-32`

- [ ] **Step 1: 将文本标签替换为 Unicode 符号**

将第 26-32 行的 switch：
```java
String iconText = switch (item.getNodeType()) {
    case PACKAGE -> "PKG";
    case CLASS_FILE -> "CLS";
    case JAVA_FILE -> "JAVA";
    case RESOURCE -> "RES";
    case BINARY -> "BIN";
};
```

替换为：

```java
String iconText = switch (item.getNodeType()) {
    case PACKAGE -> "📁";   // 📁 folder
    case CLASS_FILE -> "📄"; // 📄 document
    case JAVA_FILE -> "☕";        // ☕ coffee
    case RESOURCE -> "📋";   // 📋 clipboard
    case BINARY -> "🔑";     // 🔑 key
};
```

- [ ] **Step 2: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/ui/tree/FileTreeCell.java
git commit -m "feat: replace text-based tree icons with Unicode symbols"
```

---

### Task 2.4: BackgroundTasks 有界队列 (P2-2)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/BackgroundTasks.java:14-22`

- [ ] **Step 1: 给 LinkedBlockingQueue 设置容量上限**

将第 14-22 行：
```java
private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
        0, 8,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(),
        r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
```

替换为：

```java
private static final int MAX_QUEUE_SIZE = 100;
private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
        0, 8,
        60L, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(MAX_QUEUE_SIZE),
        r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
```

- [ ] **Step 2: 在 run() 方法中处理 RejectedExecutionException**

更新 `run()` 方法：
```java
public static Future<?> run(String name, Runnable task) {
    try {
        return EXECUTOR.submit(() -> {
            Thread.currentThread().setName(name);
            Thread.interrupted();
            task.run();
        });
    } catch (RejectedExecutionException e) {
        // Queue full; execute directly on caller thread as last resort
        task.run();
        return CompletableFuture.completedFuture(null);
    }
}
```

添加 import：
```java
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CompletableFuture;
```

- [ ] **Step 3: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/service/BackgroundTasks.java
git commit -m "fix: BackgroundTasks uses bounded queue (100) to prevent OOM"
```

---

### Task 2.5: DecompileCache 大小限制 (P2-3)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/DecompileCache.java:17`

- [ ] **Step 1: 改用 LinkedHashMap LRU 驱逐**

将第 17 行：
```java
private final Map<String, String> cache = new ConcurrentHashMap<>();
```

替换为：

```java
private static final int MAX_CACHE_SIZE = 1_000;
private final Map<String, String> cache = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        });
```

添加 import：
```java
import java.util.Collections;
import java.util.LinkedHashMap;
```

- [ ] **Step 2: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/service/DecompileCache.java
git commit -m "fix: DecompileCache uses LRU eviction (max 1000 entries)"
```

---

### Task 2.6: DiskCodeCache 自动清理 (P2-4)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/DiskCodeCache.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/FxDecompilerApp.java`

- [ ] **Step 1: 添加启动时缓存大小检查**

在 `DiskCodeCache.java` 中添加方法：

```java
private static final long MAX_CACHE_SIZE_BYTES = 500L * 1024 * 1024; // 500 MB

/** 检查并清理超过大小限制的旧缓存文件（应在启动时调用） */
public static void cleanIfNeeded() {
    try {
        if (!Files.exists(CACHE_ROOT)) return;
        long totalSize = Files.walk(CACHE_ROOT)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); } catch (IOException e) { return 0; }
                })
                .sum();
        if (totalSize > MAX_CACHE_SIZE_BYTES) {
            // Delete oldest files first until under 70% of max
            long targetSize = (long) (MAX_CACHE_SIZE_BYTES * 0.7);
            var files = Files.walk(CACHE_ROOT)
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }))
                    .toList();
            long currentSize = totalSize;
            for (Path file : files) {
                if (currentSize <= targetSize) break;
                try {
                    long fileSize = Files.size(file);
                    Files.deleteIfExists(file);
                    currentSize -= fileSize;
                } catch (IOException ignored) {
                }
            }
        }
    } catch (IOException ignored) {
    }
}
```

- [ ] **Step 2: 在 FxDecompilerApp 启动时调用清理**

在 `FxDecompilerApp.startApplication()` 方法中，`config = AppConfig.load();` 之后添加：

```java
DiskCodeCache.cleanIfNeeded();
```

- [ ] **Step 3: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/service/DiskCodeCache.java src/main/java/com/bingbihanji/fxdecomplie/FxDecompilerApp.java
git commit -m "feat: DiskCodeCache auto-cleans when exceeding 500MB on startup"
```

---

### Task 2.7-2.12: 其余 P2 项（6 个任务的合并处理）

这些任务是较小、独立的修复，可以批处理。

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/ClassDiscoverer.java` (P2-5: 符号链接)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/NavigationService.java` (P2-8: 最大历史)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/utils/CodeLinkHandler.java` (P2-9: 卸载方法)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java` (P2-11: 部分字段封装 + P2-12: 引擎枚举)

- [ ] **Step 1: ClassDiscoverer 符号链接保护 (P2-5)**

将第 87 行 `stream.filter(Files::isRegularFile).forEach(p -> {` 替换为带符号链接检测的版本：

```java
stream.filter(p -> {
    try {
        return Files.isRegularFile(p) && !Files.isSymbolicLink(p);
    } catch (Exception e) {
        return false;
    }
}).forEach(p -> {
```

- [ ] **Step 2: NavigationService 最大历史限制 (P2-8)**

在 `openPath` 方法的 `backStack.push(currentPath)` 之后添加：

```java
// Limit history stack size
while (backStack.size() > 100) {
    backStack.removeLast();
}
```

- [ ] **Step 3: CodeLinkHandler 卸载方法 (P2-9)**

在 `CodeLinkHandler.java` 中添加：

```java
/** 移除之前在 CodeArea 上安装的导航处理器 */
public static void uninstall(CodeArea codeArea) {
    codeArea.setOnMouseClicked(null);
}
```

- [ ] **Step 4: AppConfig.defaultEngine 改为枚举 (P2-12)**

将 `AppConfig.java` 中 `Decompiler` 类的第 137 行：
```java
public String defaultEngine = "VINEFLOWER";
```

替换为：
```java
public DecompilerTypeEnum defaultEngine = DecompilerTypeEnum.VINEFLOWER;
```

同时更新 `FxDecompilerApp.startApplication()` 中的 `parseDecompiler` 调用：
```java
this.currentEngine = config.decompiler.defaultEngine;
```

删除 `MainWindow.parseDecompiler()` 方法。

添加 import `com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum` 到 AppConfig。

- [ ] **Step 5: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/service/ClassDiscoverer.java src/main/java/com/bingbihanji/fxdecomplie/service/NavigationService.java src/main/java/com/bingbihanji/fxdecomplie/utils/CodeLinkHandler.java src/main/java/com/bingbihanji/fxdecomplie/config/AppConfig.java src/main/java/com/bingbihanji/fxdecomplie/FxDecompilerApp.java src/main/java/com/bingbihanji/fxdecomplie/MainWindow.java
git commit -m "fix: P2 items — symlink safety, nav history limit, CodeLinkHandler uninstall, config enum"
```

---

### 第二迭代收尾

- [ ] **Step: 运行完整编译和测试**

```bash
./mvnw clean compile -DskipTests && ./mvnw test
```
Expected: BUILD SUCCESS, all tests pass

---

## 第三迭代：P3 清理 + 测试补充（10 个任务）

### Task 3.1-3.3: 代码清理（重复消除、硬编码修复）

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonDecompiler.java` (P3-1)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrDecompiler.java` (P3-1, P3-2)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerDecompiler.java` (P3-1, P3-2)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/JdDecompiler.java` (P3-1)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/BytecodeViewTab.java` (P3-3)

- [ ] **Step 1: P3-1 统一使用 DecompilerContext.normalizeInternalName()**

在所有 4 个引擎实现中，搜索 `replace('\\', '/')` 和 `replace(".class", "")` 的组合模式，替换为调用 `DecompilerContext.normalizeInternalName(path)`。

特例：`CfrDecompiler.removeClassSuffix` 已在 Task 1.1 中添加，将其重命名为 `normalizePath` 并统一调用 `DecompilerContext` 的方法：

```java
private static String normalizePath(String path) {
    return DecompilerContext.normalizeInternalName(path);
}
```

然后在各引擎中将内联规范化替换为 `normalizePath()` 或 `DecompilerContext.normalizeInternalName()`。

- [ ] **Step 2: P3-2 提取共享 getSimpleName**

在 `DecompilerContext.java` 中添加：

```java
public static String simpleName(String internalName) {
    int idx = internalName.lastIndexOf('/');
    return idx >= 0 ? internalName.substring(idx + 1) : internalName;
}
```

从 `CfrDecompiler` 和 `VineflowerDecompiler` 中删除各自的 `getSimpleName` 方法，替换为 `DecompilerContext.simpleName(...)`。

- [ ] **Step 3: P3-3 BytecodeViewTab 国际化**

将第 37 行 `codeArea.setText("// 无可用字节码");` 替换为：
```java
codeArea.setText("// " + I18nUtil.getString("bytecode.notavailable"));
```

将第 51 行 `codeArea.setText("// 字节码解析失败: " + e.getMessage());` 替换为：
```java
codeArea.setText("// " + I18nUtil.getString("bytecode.parseFailed") + ": " + e.getMessage());
```

添加 i18n key 到两个语言文件。

- [ ] **Step 4: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/decompiler/ src/main/java/com/bingbihanji/fxdecomplie/ui/code/BytecodeViewTab.java src/main/resources/language/
git commit -m "refactor: P3 cleanup — deduplicate normalization, extract simpleName, i18n BytecodeViewTab"
```

---

### Task 3.4: ClassInfoView 增强 (P3-4)

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/ClassInfoView.java`

- [ ] **Step 1: 添加方法列表和字段列表**

在 `ClassInfoView.createView()` 中，在现有标签之后添加方法/字段列表。使用 ASM ClassVisitor 提取：

```java
// ---- Methods list ----
root.getChildren().add(sectionLabel("方法 (" + methods.size() + ")"));
for (String m : methods) {
    root.getChildren().add(label("  " + m, "#dcdcaa"));
}

// ---- Fields list ----
root.getChildren().add(sectionLabel("字段 (" + fields.size() + ")"));
for (String f : fields) {
    root.getChildren().add(label("  " + f, "#9cdcfe"));
}
```

添加辅助方法：
```java
private static Label sectionLabel(String text) {
    Label l = new Label(text);
    l.setStyle("-fx-text-fill: #569cd6; -fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 8 0 2 0;");
    return l;
}
```

使用 ASM ClassVisitor 收集方法/字段（参考 `WorkspaceIndex.indexClass` 中的模式，跳过代码体）。

- [ ] **Step 2: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/ui/code/ClassInfoView.java
git commit -m "feat: ClassInfoView now shows method and field lists"
```

---

### Task 3.5-3.10: P3 剩余代码清理

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/model/WorkspaceIndex.java:88` (P3-5)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/IndexedMemberSearchProvider.java:54` (P3-6)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/CommentSearchProvider.java:27-28` (P3-8)
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/MethodSearchProvider.java:26-31` (P3-9)

- [ ] **Step 1: P3-5 WorkspaceIndex 异常日志**

将 `catch (Exception ignored)` 替换为：
```java
} catch (Exception e) {
    logger.warn("Failed to index class: {}", node.getFullPath(), e);
}
```

添加 SLF4J import 和 logger 字段到 `WorkspaceIndex`。

- [ ] **Step 2: P3-6 移除死代码**

在 `IndexedMemberSearchProvider.java` 删除第 54 行的 `.subList(0, MAX_RESULTS)` 调用（在已经达到 MAX_RESULTS 的循环之后是多余的）。

- [ ] **Step 3: P3-8 扩展注释检测**

更新 `CommentSearchProvider` 正则以匹配行内块注释：
```java
private static final Pattern INLINE_COMMENT = Pattern.compile("/\\*.*?\\*/");
```

在 `matchesComment()` 中添加：
```java
if (INLINE_COMMENT.matcher(trimmed).find()) return true;
```

- [ ] **Step 4: P3-9 放宽方法检测正则**

在 `MethodSearchProvider` 中，将访问修饰符组改为可选（添加 `?` 量词），使其匹配包级私有方法。

- [ ] **Step 5: 编译验证并 commit**

```bash
mvnw clean compile -DskipTests
git add src/main/java/com/bingbihanji/fxdecomplie/model/WorkspaceIndex.java src/main/java/com/bingbihanji/fxdecomplie/ui/search/
git commit -m "refactor: P3 — log index failures, remove dead code, expand comment/method patterns"
```

---

### Task 3.6-3.8: 核心测试补充

- [ ] **Step 1: AppConfig 测试**

创建 `src/test/java/com/bingbaihanji/fxdecomplie/config/AppConfigTest.java`：

```java
package com.bingbaihanji.fxdecomplie.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;

class AppConfigTest {

    @Test
    void loadsDefaultsWhenNoConfigFile() {
        AppConfig config = AppConfig.load();
        assertNotNull(config);
        assertEquals("VINEFLOWER", config.decompiler.defaultEngine);
        assertEquals(1200, config.window.width);
    }

    @Test
    void addRecentFileDeduplicatesAndTrims(@TempDir Path tmp) {
        AppConfig config = new AppConfig();
        config.addRecentFile("/test/a.jar");
        config.addRecentFile("/test/b.jar");
        config.addRecentFile("/test/a.jar"); // duplicate -> moved to front
        assertEquals(2, config.recentFiles.size());
        assertEquals("/test/a.jar", config.recentFiles.get(0));
    }
}
```

- [ ] **Step 2: DecompilerFactory 测试**

创建 `src/test/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactoryTest.java`：

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DecompilerFactoryTest {

    @Test
    void returnsSameInstanceForSameType() {
        Decompiler d1 = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        Decompiler d2 = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        assertSame(d1, d2);
    }

    @Test
    void returnsDifferentInstancesForDifferentTypes() {
        Decompiler cfr = DecompilerFactory.getDecompiler(DecompilerTypeEnum.CFR);
        Decompiler procyon = DecompilerFactory.getDecompiler(DecompilerTypeEnum.PROCYON);
        assertNotSame(cfr, procyon);
    }
}
```

- [ ] **Step 3: BackgroundTasks 测试**

创建 `src/test/java/com/bingbaihanji/fxdecomplie/service/BackgroundTasksTest.java`：

```java
package com.bingbaihanji.fxdecomplie.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class BackgroundTasksTest {

    @Test
    void runsTaskAndReturnsFuture() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Future<?> future = BackgroundTasks.run("test", latch::countDown);
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertTrue(future.isDone());
    }

    @Test
    void cancelInterruptsRunningTask() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        Future<?> future = BackgroundTasks.run("test-cancel", () -> {
            started.countDown();
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertTrue(started.await(2, TimeUnit.SECONDS));
        BackgroundTasks.cancel(future);
        assertTrue(future.isCancelled() || future.isDone());
    }
}
```

- [ ] **Step 4: 运行所有测试**

```bash
./mvnw test
```
Expected: all tests pass

- [ ] **Step 5: 编译验证并 commit**

```bash
git add src/test/
git commit -m "test: add core tests for AppConfig, DecompilerFactory, BackgroundTasks"
```

---

### 第三迭代收尾

- [ ] **Step: 最终完整验证**

```bash
./mvnw clean compile -DskipTests && ./mvnw test
```
Expected: BUILD SUCCESS, all tests pass (~20+ tests)

---

## 总结

| 迭代 | 任务数 | 预计时间 | 主要产出 |
|------|--------|----------|----------|
| 第一迭代 | 13 | 3-5 天 | P0 全修复 + Ctrl+F/G + 内存优化 + 异步索引 |
| 第二迭代 | 14 | 3-5 天 | 搜索高级选项 + 引擎配置 + 架构清理 |
| 第三迭代 | 10 | 2-3 天 | P3 代码清理 + 核心测试 (~20 tests) |
| **合计** | **37** | **8-13 天** | 全缺陷修复 + 功能补齐 + 测试基础 |
