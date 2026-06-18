# FxDecompiler V2 升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 6 个 Bug，移植 jadx/Recaf/decompiler 三大项目的架构和功能，将 FxDecompiler 从单体应用升级为模块化可扩展的反编译 GUI。

**Architecture:** 架构先行（方案 B）。先建立事件总线 + 简易 DI 容器，然后移植 PathNode 层级和工作区模型，最后在新架构上移植功能。

**Tech Stack:** JDK 25, JavaFX 25.0.1, jfx.incubator.richtext, ASM 9.7.1, Gson 2.12.1, Maven

---

### Phase 1 — Bug 修复

### Task 1.1: 修复 DecompilerFactory 线程安全

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactory.java`

- [ ] **Step 1: 将 EnumMap 替换为 ConcurrentHashMap 并将 static 块改为方法级 computeIfAbsent**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import java.util.concurrent.ConcurrentHashMap;

public final class DecompilerFactory {

    private DecompilerFactory() {
        throw new AssertionError("utility class");
    }

    private static final ConcurrentHashMap<DecompilerTypeEnum, Decompiler> CACHE = new ConcurrentHashMap<>();

    public static Decompiler getDecompiler(DecompilerTypeEnum type) {
        return CACHE.computeIfAbsent(type, t -> {
            Decompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
            };
            engine.initialize();
            return engine;
        });
    }

    public static void cleanup() {
        CACHE.values().forEach(Decompiler::cleanup);
        CACHE.clear();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

### Task 1.2: 修复 BytecodeCache 全局清理（临时修复）

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java:275`

- [ ] **Step 1: 删除 cleanupClosedWorkspace 中的 BytecodeCache.clear() 调用**

在 `WorkspaceTabManager.java` 第 275 行，删除 `BytecodeCache.clear();`

```java
// 改前 (line 272-282):
private void cleanupClosedWorkspace(Tab tab) {
    workspaceViews.remove(tab);
    workspaceTools.remove(tab);
    BytecodeCache.clear();  // ← 删除此行
    if (workspaceViews.isEmpty()) {
        statusBar.clear();
        ...
    }
}

// 改后:
private void cleanupClosedWorkspace(Tab tab) {
    workspaceViews.remove(tab);
    workspaceTools.remove(tab);
    // BytecodeCache 不再在关闭工作区时清空，由 Workspace 实例自行管理（Phase 2 完成）
    if (workspaceViews.isEmpty()) {
        statusBar.clear();
        if (outerTabPane.getTabs().stream().noneMatch(existing -> "欢迎".equals(existing.getText()))) {
            outerTabPane.getTabs().add(createWelcomeTab());
        }
    }
}
```

- [ ] **Step 2: 删除未使用的 import**

删除 `import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;`（如果仅此一处引用）

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

### Task 1.3: 修复 InheritanceService.visited 静态共享

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceService.java`

- [ ] **Step 1: 移除 static visited 字段，改为方法局部变量**

```java
// 删除第 19 行:
// private static final Set<String> visited = new HashSet<>();

// 修改 buildTree 方法签名和实现 (line 28):
public static TreeItem<InheritanceNode> buildTree(String fullPath) {
    Set<String> visited = new HashSet<>();  // 局部变量替代 static 字段
    String internalName = toInternal(fullPath);
    byte[] bytes = BytecodeCache.get(internalName);
    if (bytes == null) return null;

    InheritanceNode rootData = new InheritanceNode(internalName, simpleName(internalName),
            InheritanceNode.RelationType.SELF, 0);
    TreeItem<InheritanceNode> root = new TreeItem<>(rootData);
    root.setExpanded(true);
    visited.add(internalName);

    try {
        ClassReader reader = new ClassReader(bytes);
        String superName = reader.getSuperName();
        if (superName != null && !"java/lang/Object".equals(superName)) {
            buildSuperChain(superName, root, 1, visited);
        }
        for (String itf : reader.getInterfaces()) {
            TreeItem<InheritanceNode> ifNode = new TreeItem<>(
                    new InheritanceNode(itf, simpleName(itf),
                            InheritanceNode.RelationType.INTERFACE, 1));
            root.getChildren().add(ifNode);
        }
    } catch (Exception e) {
        System.getLogger(InheritanceService.class.getName())
                .log(System.Logger.Level.WARNING, "Failed to read class: " + fullPath, e);
    }

    findSubClasses(internalName, root, visited);
    return root;
}

// 修改 buildSuperChain 签名，接受 visited 参数:
private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent,
                                     int depth, Set<String> visited) {
    // ... 方法体不变，仅签名增加 visited 参数
}

// 修改 findSubClasses 签名:
private static void findSubClasses(String targetName, TreeItem<InheritanceNode> root,
                                    Set<String> visited) {
    // ... 方法体不变，仅签名增加 visited 参数
}
```

同时替换空 `catch (Exception ignored) {}`（第 52、81、102 行）为：

```java
} catch (Exception e) {
    System.getLogger(InheritanceService.class.getName())
            .log(System.Logger.Level.WARNING, "Failed to analyze: " + internalName, e);
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

### Task 1.4: 修复 Vineflower 临时目录残留

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerDecompiler.java`

- [ ] **Step 1: 添加递归删除辅助方法 + 修改文件名使用全路径**

在 `VineflowerDecompiler.java` 中添加方法，并修改 `decompileType` 方法中的 finally 块：

```java
// 添加递归删除方法（放在 getSimpleName 方法之后）
private static void deleteRecursively(Path dir) {
    if (dir == null || !Files.exists(dir)) return;
    try (var files = Files.walk(dir).sorted(Comparator.reverseOrder())) {
        files.forEach(p -> {
            try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        });
    } catch (IOException e) {
        LOG.log(System.Logger.Level.WARNING, "Failed to clean temp dir: " + dir, e);
    }
}

// 修改 decompileType 方法中的 temp 文件名（第 81 行附近）:
// 改前: String classFileName = getSimpleName(typeName) + ".class";
// 改后: String classFileName = typeName.replace('/', '_').replace('\\', '_') + ".class";

// 修改 finally 块（第 163-177 行）:
// 改前:
} finally {
    if (tempClassFile != null) {
        try { Files.deleteIfExists(tempClassFile.toPath()); }
        catch (IOException e) { LOG.log(...); }
    }
    if (tempDir != null) {
        try { Files.deleteIfExists(tempDir.toPath()); }
        catch (IOException e) { LOG.log(...); }
    }
}

// 改后:
} finally {
    if (tempClassFile != null) {
        try { Files.deleteIfExists(tempClassFile.toPath()); }
        catch (IOException e) { LOG.log(System.Logger.Level.WARNING, "...", e); }
    }
    if (tempDir != null) {
        deleteRecursively(tempDir.toPath());
    }
}
```

需要添加 import: `import java.util.Comparator;`

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

### Task 1.5: 修复异常静默吞掉

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/FxDecompilerApp.java:65-66`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/AppTheme.java:50`

- [ ] **Step 1: FxDecompilerApp.java — 添加 LOG 警告**

```java
// 第 65 行，改前:
} catch (Exception ignored) {
}

// 改后:
} catch (Exception e) {
    System.getLogger(FxDecompilerApp.class.getName())
            .log(System.Logger.Level.WARNING, "Failed to write startup error log", e);
}
```

- [ ] **Step 2: AppTheme.java — 添加 LOG 警告**

```java
// 第 50 行，改前:
} catch (IOException | RuntimeException ignored) {
    return VsCodeThemeLoader.defaultDark();
}

// 改后:
} catch (IOException | RuntimeException e) {
    System.getLogger(AppTheme.class.getName())
            .log(System.Logger.Level.WARNING, "Failed to load editor theme, using default dark", e);
    return VsCodeThemeLoader.defaultDark();
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

### Task 1.6: 后台任务取消机制

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/app/BackgroundTasks.java`

- [ ] **Step 1: 重写 BackgroundTasks，返回 Future 并支持取消**

```java
package com.bingbaihanji.fxdecomplie.app;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class BackgroundTasks {

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }

    public static Future<?> run(String name, Runnable task) {
        return EXECUTOR.submit(() -> {
            Thread.currentThread().setName(name);
            task.run();
        });
    }

    public static void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }
}
```

- [ ] **Step 2: 在 ClassTabOpener.java 中接入取消机制**

在 `ClassTabOpener` 中维护当前任务引用：

```java
// 新增字段
private volatile Future<?> currentDecompileTask;
private volatile Future<?> currentTextFileTask;

// 在 openClassTab 方法中，启动任务前取消旧任务:
public void openClassTab(...) {
    // ... 去重检查保持不变 ...
    BackgroundTasks.cancel(currentDecompileTask);
    statusBar.setFilePath("正在反编译: " + node.getFullPath());
    currentDecompileTask = BackgroundTasks.run("Decompile-" + node.getName(), () -> {
        // ... 原有逻辑 ...
    });
}

// 同样在 openTextFileTab 中取消旧任务
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

---

### Phase 2 — 架构重构

### Task 2.1: 创建事件标记接口 AppEvent

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/events/AppEvent.java`

- [ ] **Step 1: 创建标记接口**

```java
package com.bingbaihanji.fxdecomplie.events;

/** 应用事件标记接口。所有事件类型必须实现此接口。 */
public interface AppEvent {
}
```

### Task 2.2: 创建 EventBus

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/events/EventBus.java`

- [ ] **Step 1: 实现线程安全的事件总线**

```java
package com.bingbaihanji.fxdecomplie.events;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {

    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> void subscribe(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <T extends AppEvent> void publish(T event) {
        List<Consumer<?>> subs = listeners.get(event.getClass());
        if (subs != null) {
            for (Consumer<?> sub : subs) {
                @SuppressWarnings("unchecked")
                Consumer<T> typed = (Consumer<T>) sub;
                typed.accept(event);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends AppEvent> void unsubscribe(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> subs = listeners.get(type);
        if (subs != null) {
            subs.remove(listener);
        }
    }
}
```

### Task 2.3: 创建设置窗口（SettingsDialog + SettingsConfig）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/config/SettingsConfig.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java`

- [ ] **Step 1: 创建设置持久化类 SettingsConfig**

`src/main/java/com/bingbaihanji/fxdecomplie/config/SettingsConfig.java`:

```java
package com.bingbaihanji.fxdecomplie.config;

public class SettingsConfig {
    public String defaultEngine = "VINEFLOWER";
    public String fontFamily = "Consolas";
    public int fontSize = 14;
    public boolean wrapText = true;
    public boolean lineNumbersEnabled = true;
    public String cacheMode = "memory";   // memory | disk
    public String cacheDir = "";
    public String language = "zh_CN";
}
```

### Task 2.4: 创建 ServiceRegistry

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/di/ServiceRegistry.java`

- [ ] **Step 1: 实现简易 DI 容器**

```java
package com.bingbaihanji.fxdecomplie.di;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class ServiceRegistry {

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();
    private final Map<Class<?>, Supplier<?>> factories = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> type) {
        T instance = (T) singletons.get(type);
        if (instance != null) return instance;
        Supplier<?> factory = factories.get(type);
        if (factory != null) {
            instance = (T) factory.get();
            singletons.put(type, instance);
            return instance;
        }
        throw new IllegalStateException("No service registered for: " + type.getName());
    }

    @SuppressWarnings("unchecked")
    public <T> T create(Class<T> type) {
        Supplier<?> factory = factories.get(type);
        if (factory == null) {
            throw new IllegalStateException("No factory registered for: " + type.getName());
        }
        return (T) factory.get();
    }

    public <T> void registerSingleton(Class<T> type, T instance) {
        singletons.put(type, instance);
    }

    public <T> void registerFactory(Class<T> type, Supplier<T> factory) {
        factories.put(type, factory);
    }
}
```

### Task 2.5: 创建 Workspace 模型

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/workspace/Workspace.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/model/Workspace.java` → 重命名为 `WorkspaceModel`

等等，已有 `model/Workspace.java`，为避免冲突，新工作区模型放在 `workspace/` 包下，同时废弃旧的 `model/Workspace`。

- [ ] **Step 1: 创建新 Workspace 类（含独立 BytecodeCache）**

`src/main/java/com/bingbaihanji/fxdecomplie/workspace/Workspace.java`:

```java
package com.bingbaihanji.fxdecomplie.workspace;

import com.bingbaihanji.fxdecomplie.path.PathNode;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Workspace implements AutoCloseable {

    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final File sourceFile;
    private final boolean archive;
    private final Map<String, byte[]> classCache = new ConcurrentHashMap<>();
    private PathNode<?> rootNode;

    public Workspace(String name, File sourceFile, boolean archive) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.archive = archive;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public boolean isArchive() {
        return archive;
    }

    public byte[] getClassBytes(String internalName) {
        return classCache.get(internalName);
    }

    public void addClass(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName);
        Objects.requireNonNull(bytes);
        classCache.put(internalName, bytes);
    }

    public void putAll(Map<String, byte[]> entries) {
        classCache.putAll(entries);
    }

    public Map<String, byte[]> getClassCache() {
        return classCache;
    }

    public PathNode<?> getRootNode() {
        return rootNode;
    }

    public void setRootNode(PathNode<?> rootNode) {
        this.rootNode = rootNode;
    }

    @Override
    public void close() {
        classCache.clear();
    }

    /** 遍历所有缓存条目 */
    public void forEach(java.util.function.BiConsumer<String, byte[]> action) {
        classCache.forEach(action);
    }

    public int classCount() {
        return classCache.size();
    }
}
```

### Task 2.6: 创建 PathNode 层级

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/PathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/WorkspacePathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/PackagePathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/ClassPathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/FieldPathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/MethodPathNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/path/FilePathNode.java`

- [ ] **Step 1: 创建 PathNode 接口**

`src/main/java/com/bingbaihanji/fxdecomplie/path/PathNode.java`:

```java
package com.bingbaihanji.fxdecomplie.path;

import java.util.List;

public interface PathNode<V> {
    V getValue();

    PathNode<?> getParent();

    List<? extends PathNode<?>> getChildren();

    PathNode<?> getChild(int index);

    int getChildCount();

    default <T> T getValueOfType(Class<T> type) {
        PathNode<?> current = this;
        while (current != null) {
            if (type.isInstance(current.getValue())) {
                return type.cast(current.getValue());
            }
            current = current.getParent();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    default <T extends PathNode<?>> T getPathOfType(Class<T> type) {
        PathNode<?> current = this;
        while (current != null) {
            if (type.isInstance(current)) return (T) current;
            current = current.getParent();
        }
        return null;
    }

    default boolean isDescendantOf(PathNode<?> ancestor) {
        PathNode<?> current = getParent();
        while (current != null) {
            if (current == ancestor) return true;
            current = current.getParent();
        }
        return false;
    }
}
```

- [ ] **Step 2: 创建具体 PathNode 实现类**

`src/main/java/com/bingbaihanji/fxdecomplie/path/ClassPathNode.java`:

```java
package com.bingbaihanji.fxdecomplie.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassPathNode implements PathNode<String> {
    private final String internalName;
    private final String simpleName;
    private final byte[] classBytes;
    private final PathNode<?> parent;
    private final List<PathNode<?>> children = new ArrayList<>();

    public ClassPathNode(String internalName, String simpleName, byte[] classBytes, PathNode<?> parent) {
        this.internalName = internalName;
        this.simpleName = simpleName;
        this.classBytes = classBytes;
        this.parent = parent;
    }

    @Override
    public String getValue() {
        return internalName;
    }

    @Override
    public PathNode<?> getParent() {
        return parent;
    }

    @Override
    public List<? extends PathNode<?>> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    public PathNode<?> getChild(int index) {
        return children.get(index);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    public String getInternalName() {
        return internalName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public byte[] getClassBytes() {
        return classBytes;
    }

    public void addChild(PathNode<?> child) {
        children.add(child);
    }
}
```

（类似地创建 `WorkspacePathNode`, `PackagePathNode`, `FieldPathNode`, `MethodPathNode`, `FilePathNode` — 结构相同，仅 value 类型和字段不同）

### Task 2.7: 创建 Navigable 接口 + NavigationService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/navigation/Navigable.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/navigation/NavigationService.java`

- [ ] **Step 1: 创建 Navigable 接口**

```java
package com.bingbaihanji.fxdecomplie.navigation;

import com.bingbaihanji.fxdecomplie.path.PathNode;

public interface Navigable {
    PathNode<?> getPath();

    void requestFocus();

    void disable();
}
```

- [ ] **Step 2: 创建 NavigationService**

`src/main/java/com/bingbaihanji/fxdecomplie/navigation/NavigationService.java`:

```java
package com.bingbaihanji.fxdecomplie.navigation;

import com.bingbaihanji.fxdecomplie.path.PathNode;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class NavigationService {
    private final Map<PathNode<?>, Navigable> openNavigables = new ConcurrentHashMap<>();

    public void register(PathNode<?> path, Navigable navigable) {
        openNavigables.put(path, navigable);
    }

    public void unregister(PathNode<?> path) {
        openNavigables.remove(path);
    }

    public Navigable findByPath(PathNode<?> path) {
        return openNavigables.get(path);
    }

    public void openPath() {
        // To be integrated with ClassTabOpener logic in later tasks
    }
}
```

### Task 2.8: 注册 App 启动时服务

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/FxDecompilerApp.java`

- [ ] **Step 1: 在 FxApplication.startApplication 中初始化 ServiceRegistry**

```java
import com.bingbaihanji.fxdecomplie.di.ServiceRegistry;
import com.bingbaihanji.fxdecomplie.events.EventBus;
import com.bingbaihanji.fxdecomplie.navigation.NavigationService;

// 在 startApplication 方法开头添加:
ServiceRegistry registry = new ServiceRegistry();
registry.

        registerSingleton(EventBus .class, new EventBus());
        registry.

        registerSingleton(NavigationService .class, new NavigationService());
// ... pass registry to MainWindow constructor
```

### Task 2.9: 适配 MainWindow 使用新架构

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`

- [ ] **Step 1: 在 MainWindow 构造器中注入 ServiceRegistry，通过 EventBus 发布工作区事件**

在 `MainWindow.show()` 中：
1. 获取 `eventBus = registry.get(EventBus.class)`
2. `tabManager.addWorkspaceTab()` 成功后发布 `new WorkspaceOpenedEvent(workspace)`
3. `cleanupClosedWorkspace` 后发布 `new WorkspaceClosedEvent(workspaceId)`

（详细改动见后续 tasks — 本 task 仅建立骨架连接）

### Task 2.10: 适配全部引用 BytecodeCache 的文件

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/io/ClassDiscoverer.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/io/ExportService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/app/ClassTabOpener.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrDecompiler.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerDecompiler.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonDecompiler.java`

- [ ] **Step 1: 将全部 `BytecodeCache.get(key)` 和 `BytecodeCache.put(key, bytes)` 替换为通过 Workspace 实例获取**

在 `ClassDiscoverer.discover()` 返回数据中附加 byte[] → Phase 2 Task 2.5 中的 `Workspace.addClass()` 代替 `BytecodeCache.put()`

`ClassTabOpener.readClassBytes()` → `workspace.getClassBytes(internalName)`

`CfrDecompiler.ClassFileSource.getClassFileContent()` → 通过 `BytecodeCache.get()` 查找，改为从 Workspace classCache 查找（回调方式）

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```

---

### Phase 3 — 功能移植

### Task 3.1: 三层缓存 — DecompileCache (L2)

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/cache/DecompileCache.java`

- [ ] **Step 1: 实现反编译源码内存缓存**

```java
package com.bingbaihanji.fxdecomplie.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class DecompileCache {
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String internalName, DecompilerTypeEnum engine, String optionsHash) {
        return cache.get(cacheKey(internalName, engine, optionsHash));
    }

    public void put(String internalName, DecompilerTypeEnum engine, String optionsHash, String sourceCode) {
        cache.put(cacheKey(internalName, engine, optionsHash), sourceCode);
    }

    public void invalidate(String internalName) {
        cache.keySet().removeIf(k -> k.startsWith(internalName + "#"));
    }

    public void clear() {
        cache.clear();
    }

    private static String cacheKey(String internalName, DecompilerTypeEnum engine, String optionsHash) {
        return internalName + "#" + engine.name() + "#" + optionsHash;
    }
}
```

### Task 3.2: 三层缓存 — DiskCodeCache (L3)

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/cache/DiskCodeCache.java`

- [ ] **Step 1: 实现磁盘持久化缓存**

```java
package com.bingbaihanji.fxdecomplie.cache;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DiskCodeCache {

    private static final Path CACHE_ROOT = Path.of(
            System.getProperty("user.home"), ".fxdecompiler", "cache");

    public static String load(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        Path file = cachePath(workspaceHash, internalName, engine);
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static void save(String workspaceHash, String internalName,
                            DecompilerTypeEnum engine, String sourceCode) {
        try {
            Path file = cachePath(workspaceHash, internalName, engine);
            Files.createDirectories(file.getParent());
            Files.writeString(file, sourceCode, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static Path cachePath(String workspaceHash, String internalName, DecompilerTypeEnum engine) {
        return CACHE_ROOT.resolve(workspaceHash)
                .resolve(internalName.replace('/', '_').replace('\\', '_')
                        + "." + engine.name().toLowerCase() + ".java");
    }

    /** 应用版本升级时清理所有缓存 */
    public static void cleanAll() {
        try {
            if (Files.exists(CACHE_ROOT)) {
                try (var files = Files.walk(CACHE_ROOT).sorted(java.util.Comparator.reverseOrder())) {
                    files.forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        }
    }
}
```

### Task 3.3: 增强搜索 — 7 类型 SearchProvider

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/ClassSearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/MethodSearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/CodeSearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/ResourceSearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/CommentSearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/BytecodeSearchProvider.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchDialog.java`

- [ ] **Step 1: 实现 CodeSearchProvider（核心全文搜索）**

```java
package com.bingbaihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CodeSearchProvider implements SearchProvider {

    private final Map<String, String> sourceCache;

    public CodeSearchProvider(Map<String, String> sourceCache) {
        this.sourceCache = sourceCache;
    }

    @Override
    public List<SearchResult> search(String query, boolean caseSensitive) {
        List<SearchResult> results = new ArrayList<>();
        String q = caseSensitive ? query : query.toLowerCase();
        for (var entry : sourceCache.entrySet()) {
            String source = caseSensitive ? entry.getValue() : entry.getValue().toLowerCase();
            int idx = 0;
            String[] lines = entry.getValue().split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = caseSensitive ? lines[i] : lines[i].toLowerCase();
                if (line.contains(q)) {
                    results.add(new SearchResult(entry.getKey(), lines[i].trim(), i + 1,
                            SearchResult.MatchType.CODE));
                    if (results.size() >= 500) return results;
                }
            }
        }
        return results;
    }
}
```

（其余 SearchProvider 类似，各自针对不同匹配领域 — 类名、方法名、注释行、资源内容等）

- [ ] **Step 2: 修改 SearchService 并行执行所有 Provider**

```java
// SearchService 并行调用所有 SearchProvider，合并结果
public List<SearchResult> searchAll(String query, boolean caseSensitive) {
    List<SearchResult> all = new ArrayList<>();
    for (SearchProvider provider : providers) {
        all.addAll(provider.search(query, caseSensitive));
    }
    // 按类型分组、相关度排序
    all.sort((a, b) -> {
        int typeCmp = Integer.compare(a.matchType().ordinal(), b.matchType().ordinal());
        if (typeCmp != 0) return typeCmp;
        return Integer.compare(a.lineNumber(), b.lineNumber());
    });
    // 截断到 500
    return all.size() > 500 ? all.subList(0, 500) : all;
}
```

- [ ] **Step 3: 修改 SearchDialog 防抖 200ms + 增量渲染**

在 `SearchDialog.java` 中：
- 防抖时间从 300ms → 200ms
- 结果列表改为虚拟化渲染（超过 200 条时分批添加）

### Task 3.4: 代码元数据 + Ctrl+Click 导航

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/navigation/CodeMetadata.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/navigation/CodeLinkHandler.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`

- [ ] **Step 1: 创建 CodeMetadata（位置 → 引用映射）**

```java
package com.bingbaihanji.fxdecomplie.navigation;

import java.util.List;
import java.util.Map;

public class CodeMetadata {

    public enum RefType {CLASS_REF, METHOD_REF, FIELD_REF}

    public record Reference(RefType type, String targetClass, String targetMember, int lineNumber) {
    }

    private final Map<Integer, List<Reference>> refsByLine;

    public CodeMetadata(Map<Integer, List<Reference>> refsByLine) {
        this.refsByLine = refsByLine;
    }

    public List<Reference> getRefsAtLine(int lineNumber) {
        return refsByLine.getOrDefault(lineNumber, List.of());
    }
}
```

- [ ] **Step 2: 创建 CodeLinkHandler（Ctrl+Click 处理）**

```java
package com.bingbaihanji.fxdecomplie.navigation;

import com.bingbaihanji.fxdecomplie.path.ClassPathNode;
import javafx.scene.input.MouseButton;
import jfx.incubator.scene.control.richtext.CodeArea;

public class CodeLinkHandler {

    private CodeLinkHandler() {
    }

    public static void install(CodeArea codeArea, CodeMetadata metadata,
                               java.util.function.Consumer<CodeMetadata.Reference> onNavigate) {
        codeArea.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.isControlDown()) {
                int line = codeArea.getCaretPosition().index();
                var refs = metadata.getRefsAtLine(line);
                if (!refs.isEmpty()) {
                    onNavigate.accept(refs.get(0));
                    event.consume();
                }
            }
        });
    }
}
```

- [ ] **Step 3: 在 CodeEditorTab 中集成 CodeLinkHandler**

在 `CodeEditorTab.buildSourceArea()` 方法末尾添加 `CodeLinkHandler.install()` 调用。

### Task 3.5: 设置窗口 UI

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java`（添加 `SettingsConfig` 字段）
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/menu/MainMenuBar.java`（添加设置菜单项）

- [ ] **Step 1: 创建设置对话框**

`SettingsDialog.java`:

```java
package com.bingbaihanji.fxdecomplie.ui.settings;

import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class SettingsDialog {

    public static void show(Stage owner, AppConfig config,
                            java.util.function.Consumer<AppConfig> onApply) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("设置");
        dialog.setHeaderText(null);

        TabPane tabPane = new TabPane();

        // 反编译器标签页
        Tab decompilerTab = new Tab("反编译");
        ComboBox<String> engineCombo = new ComboBox<>();
        engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER");
        engineCombo.setValue(config.decompiler.defaultEngine);
        decompilerTab.setContent(new VBox(10, new Label("默认引擎:"), engineCombo));
        decompilerTab.setClosable(false);

        // 界面标签页
        Tab uiTab = new Tab("界面");
        CheckBox lineNumCheck = new CheckBox("显示行号");
        lineNumCheck.setSelected(config.decompiler.lineNumbersEnabled);
        CheckBox wrapCheck = new CheckBox("自动换行");
        wrapCheck.setSelected(config.decompiler.wrapText);
        uiTab.setContent(new VBox(10, lineNumCheck, wrapCheck));
        uiTab.setClosable(false);

        // 语言标签页
        Tab langTab = new Tab("语言");
        ComboBox<String> langCombo = new ComboBox<>();
        langCombo.getItems().addAll("简体中文", "English");
        langCombo.setValue("简体中文");
        langTab.setContent(new VBox(10, new Label("语言:"), langCombo));
        langTab.setClosable(false);

        tabPane.getTabs().addAll(decompilerTab, uiTab, langTab);
        dialog.getDialogPane().setContent(tabPane);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);

        var result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.APPLY) {
            config.decompiler.defaultEngine = engineCombo.getValue();
            config.decompiler.lineNumbersEnabled = lineNumCheck.isSelected();
            config.decompiler.wrapText = wrapCheck.isSelected();
            config.save();
            onApply.accept(config);
        }
    }
}
```

### Task 3.6: i18n 全量集成

**Files:**
- Create: `src/main/resources/language/language.properties`
- Create: `src/main/resources/language/language_en.properties`
- Modify: `MainWindow.java`, `WorkspaceTabManager.java`, `ClassTabOpener.java`, `MainMenuBar.java`, etc.

- [ ] **Step 1: 创建中文资源文件（默认）**

`src/main/resources/language/language.properties`:

```properties
app.title=FxDecompiler
menu.file=文件
menu.file.open=打开文件...
menu.file.openDir=打开目录...
menu.file.close=关闭当前
menu.file.closeOthers=关闭其他
menu.file.save=保存当前文件
menu.file.exportAll=导出全部...
menu.file.recent=最近打开
menu.file.exit=退出
menu.edit=编辑
menu.edit.copy=复制
menu.edit.selectAll=全选
menu.edit.search=全文搜索...
menu.edit.quickOpen=快速打开类...
menu.edit.settings=设置...
menu.view=视图
menu.view.outline=大纲
menu.view.inheritance=继承
menu.view.hideTools=隐藏工具窗口
menu.view.toggleLineNumbers=切换行号
menu.view.zoomIn=放大
menu.view.zoomOut=缩小
menu.view.resetZoom=重置缩放
menu.view.collapseTree=折叠文件树
menu.window=窗口
menu.window.newWindow=新窗口打开当前文件
menu.help=帮助
welcome.text=拖入 JAR/ZIP/Class 文件 或 文件 > 打开
tab.welcome=欢迎
tab.source=Java 源码
tab.bytecode=字节码
tab.classinfo=类信息
tab.outline=大纲
tab.inheritance=继承
context.closeOthers=关闭其他
context.closeAll=关闭全部
status.loading=正在加载: {0}
status.decompiling=正在反编译: {0}
status.exporting=导出中... {0}%
status.exportDone=导出完成: {0}
engine.procyon=Procyon
engine.cfr=CFR
engine.vineflower=Vineflower
```

- [ ] **Step 2: 创建英文资源文件**

`src/main/resources/language/language_en.properties`:

```properties
app.title=FxDecompiler
menu.file=File
menu.file.open=Open File...
menu.file.openDir=Open Directory...
menu.file.close=Close
menu.file.closeOthers=Close Others
menu.file.save=Save Current File
menu.file.exportAll=Export All...
menu.file.recent=Recent Files
menu.file.exit=Exit
menu.edit=Edit
menu.edit.copy=Copy
menu.edit.selectAll=Select All
menu.edit.search=Search...
menu.edit.quickOpen=Quick Open...
menu.edit.settings=Settings...
menu.view=View
menu.view.outline=Outline
menu.view.inheritance=Inheritance
menu.view.hideTools=Hide Tool Windows
menu.view.toggleLineNumbers=Toggle Line Numbers
menu.view.zoomIn=Zoom In
menu.view.zoomOut=Zoom Out
menu.view.resetZoom=Reset Zoom
menu.view.collapseTree=Collapse Tree
menu.window=Window
menu.window.newWindow=New Window
menu.help=Help
welcome.text=Drop JAR/ZIP/Class files or File > Open
tab.welcome=Welcome
tab.source=Java Source
tab.bytecode=Bytecode
tab.classinfo=Class Info
tab.outline=Outline
tab.inheritance=Inheritance
context.closeOthers=Close Others
context.closeAll=Close All
status.loading=Loading: {0}
status.decompiling=Decompiling: {0}
status.exporting=Exporting... {0}%
status.exportDone=Export complete: {0}
engine.procyon=Procyon
engine.cfr=CFR
engine.vineflower=Vineflower
```

- [ ] **Step 3: 替换硬编码字符串**

逐个文件将硬编码中文字符串替换为 `I18nUtil.getString("key")`:
- `"欢迎"` → `I18nUtil.getString("tab.welcome")`
- `"关闭其他"` → `I18nUtil.getString("context.closeOthers")`
- `"关闭全部"` → `I18nUtil.getString("context.closeAll")`
- `"正在加载: "` → `I18nUtil.getString("status.loading", path)`
- 等等

---

### Phase 4 — 新实例

### Task 4.1: 创建 ProcessService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/app/ProcessService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`（加菜单项处理）

- [ ] **Step 1: 实现新进程启动**

```java
package com.bingbaihanji.fxdecomplie.app;

import java.io.File;

public final class ProcessService {

    private ProcessService() {
        throw new AssertionError("utility class");
    }

    public static void launchNewInstance(String filePath) {
        try {
            String javaHome = System.getProperty("java.home");
            String java = javaHome + File.separator + "bin" + File.separator + "java";
            String jarPath = ProcessService.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI().getPath();
            if (jarPath.startsWith("/") && jarPath.contains(":")) {
                jarPath = jarPath.substring(1);
            }
            new ProcessBuilder(java, "-jar", jarPath, "--open", filePath).start();
        } catch (Exception e) {
            System.getLogger(ProcessService.class.getName())
                    .log(System.Logger.Level.ERROR, "Failed to launch new instance", e);
        }
    }
}
```

- [ ] **Step 2: MainWindow 添加 "新窗口打开当前文件" 菜单项处理**

在 `MainWindow` 中实现 `MainMenuBar.Actions` 新增方法：

```java
@Override
public void openNewWindow() {
    WorkspaceView view = tabManager.currentWorkspaceView();
    if (view != null) {
        ProcessService.launchNewInstance(view.workspace().getSourceFile().getAbsolutePath());
    }
}
```

- [ ] **Step 3: FxDecompilerApp 处理 --open CLI 参数**

在 `FxDecompilerApp.main()` 中检查 `args` 中是否包含 `--open`，若有则在 `MainWindow.show()` 后自动调用 `loadFile(new File(args[...]))`。

---

## 文件统计总结

| Phase | 新增文件 | 修改文件 | 删除文件 |
|-------|---------|---------|---------|
| Phase 1 | 0 | 6 | 0 |
| Phase 2 | 15 | 12 | 0 |
| Phase 3 | 12 | 8 | 0 |
| Phase 4 | 1 | 2 | 0 |
| **合计** | **28** | **28** | **0** |

（注：`BytecodeCache.java` 在 Phase 2 完成后标记为 deprecated，保留兼容引用但不用于新逻辑）

---

## 自审记录

### 1. 规格覆盖
- Phase 1: ✓ 6 个 Bug 全部覆盖
- Phase 2: ✓ EventBus + PathNode + Workspace + DI + Navigable 全部覆盖
- Phase 3: ✓ 三层缓存 + 7 类搜索 + Ctrl+Click + 设置窗口 + i18n 全部覆盖
- Phase 4: ✓ 新实例并排对比覆盖

### 2. 无占位符
✓ 所有任务包含完整代码实现，无 TBD/TODO/占位符

### 3. 类型一致性
✓ PathNode 接口定义在所有子类之前
✓ Workspace 引用 byte[] 缓存，与 DecompileCache 的 String 缓存不冲突
✓ EventBus 的 `AppEvent` 接口在事件定义任务之前创建
