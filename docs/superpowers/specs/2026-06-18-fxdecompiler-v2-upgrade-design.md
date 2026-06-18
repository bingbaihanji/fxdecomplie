# FxDecompiler V2 升级设计文档

**日期**: 2026-06-18
**状态**: 已确认
**参考项目**: jadx, Recaf, decompiler (moe.sota)

---

## 目标

修复 6 个 Bug，移植三大参考项目的架构模式和功能，将 FxDecompiler 从单体应用升级为模块化、可扩展的反编译 GUI。

## 方案：架构先行（方案 B）

```
Phase 1: Bug 修复（无架构变更）
Phase 2: 架构重构（EventBus + PathNode + Workspace + DI）
Phase 3: 功能移植（缓存 + 搜索 + 导航 + 设置 + i18n）
Phase 4: 新实例（并排对比）
```

---

## Phase 1 — Bug 修复

### Bug 1: DecompilerFactory 线程安全
- **文件**: `decompiler/DecompilerFactory.java`
- **改法**: `EnumMap` → `ConcurrentHashMap`，`computeIfAbsent` 保证原子性

### Bug 2: BytecodeCache 全局清理
- **根因**: 全局静态 `ConcurrentHashMap`，关闭任一工作区清空全部缓存
- **改法**: `BytecodeCache` 从全局单例改为 `Workspace` 实例成员。在 Phase 2 中完整解决；Phase 1 先删除 `cleanupClosedWorkspace` 中的 `BytecodeCache.clear()` 调用，让缓存保持可用

### Bug 3: InheritanceService.visited 静态共享
- **文件**: `ui/inheritance/InheritanceService.java`
- **改法**: `static Set<String> visited` → 方法内 `new HashSet<>()`

### Bug 4: Vineflower 临时目录残留
- **文件**: `decompiler/VineflowerDecompiler.java`
- **改法**: `Files.deleteIfExists` → 递归删除（`Files.walk` + 倒序删除）；临时文件名加入包路径前缀

### Bug 5: 异常静默吞掉
- **涉及文件**: `FxDecompilerApp.java`, `InheritanceService.java`, `AppTheme.java`
- **改法**: 空 `catch (Exception ignored) {}` → 至少 `LOG.log(WARNING, msg, e)`

### Bug 6: 后台任务取消机制
- **文件**: `app/BackgroundTasks.java`
- **改法**: `run()` 返回 `Future<?>`，新增 `cancel(Future)` 方法；关闭标签页时取消对应任务

---

## Phase 2 — 架构重构

### 新增包和类

```
com.bingbaihanji.fxdecomplie
├── di/
│   └── ServiceRegistry.java         简易 DI 容器（单例 + 工厂注册）
├── events/
│   ├── AppEvent.java                事件标记接口
│   ├── EventBus.java                线程安全的发布/订阅总线
│   └── WorkspaceEvent.java          工作区生命周期事件类型
├── workspace/
│   ├── Workspace.java               工作区实例（id, sourceFile, classCache, rootNode）
│   └── WorkspaceManager.java        工作区生命周期管理
├── path/
│   ├── PathNode.java                树节点接口（getParent, getValueOfType, getChildren）
│   ├── WorkspacePathNode.java       工作区层级根节点
│   ├── PackagePathNode.java         包层级节点
│   ├── ClassPathNode.java           类层级节点
│   ├── FieldPathNode.java           字段节点
│   ├── MethodPathNode.java          方法节点
│   └── FilePathNode.java            普通文件节点
└── navigation/
    └── Navigable.java               标签内容追踪接口（getPath, requestFocus, disable）
```

### EventBus 设计

```java
public class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T extends AppEvent> void subscribe(Class<T> type, Consumer<T> listener);
    public <T extends AppEvent> void publish(T event);
}
```

事件类型：
- `WorkspaceOpenedEvent(Workspace)` — 打开新文件
- `WorkspaceClosedEvent(Workspace)` — 关闭工作区
- `ClassSelectedEvent(ClassPathNode, byte[])` — 选中类文件
- `EngineSwitchedEvent(DecompilerTypeEnum)` — 切换反编译引擎
- `ThemeChangedEvent(ThemeData)` — 主题变更
- `LocaleChangedEvent(Locale)` — 语言切换

### PathNode 层级

```
WorkspacePathNode
  └── PackagePathNode
       ├── ClassPathNode
       │    ├── FieldPathNode
       │    ├── MethodPathNode
       │    └── ClassPathNode (内部类)
       └── FilePathNode (.xml/.json/.properties/.java)
```

核心方法：
- `getValueOfType(Class<T>)` — 沿父链向上查找指定类型节点
- `getPathOfType(Class<T>)` — 返回匹配节点的路径链
- `isDescendantOf(PathNode<?>)` — 判断祖先关系

### Workspace 模型

```java
public class Workspace implements AutoCloseable {
    private final String id;                           // UUID
    private final File sourceFile;                     // JAR/ZIP/目录
    private final Map<String, byte[]> classCache;      // internalName → bytes
    private final PathNode<?> rootNode;
    private final List<ClassPathNode> flatClassList;   // 搜索用

    public byte[] getClassBytes(String internalName);
    public void addClass(String internalName, byte[] bytes);
    public void dispose();                              // 清理缓存 + 通知监听器
}
```

### ServiceRegistry（简易 DI）

```java
public class ServiceRegistry {
    public <T> void registerSingleton(Class<T> type, T instance);
    public <T> void registerFactory(Class<T> type, Supplier<T> factory);
    public <T> T get(Class<T> type);         // 获取单例
    public <T> T create(Class<T> type);       // 工厂创建新实例
}
```

注册的服务：`EventBus`, `WorkspaceManager`, `DecompilerService`, `NavigationService`, `SearchService`, `ExportService`, `ThemeService`

### 现有文件改动

| 文件 | 改动 |
|------|------|
| `MainWindow.java` | 发布事件替代直接调用；通过 ServiceRegistry 获取服务 |
| `WorkspaceTabManager.java` | 拆分为 `WorkspaceView` + `TabManager` + `ToolWindowManager` |
| `ClassTabOpener.java` | 重构为 `NavigationService`，基于 PathNode |
| `BytecodeCache.java` | 删除，逻辑移入 `Workspace.classCache` |
| `DecompilerFactory.java` | 重构为 `DecompilerService`（注册到 ServiceRegistry） |
| `BackgroundTasks.java` | 增加取消机制，注册到 ServiceRegistry |
| 其余文件 | `BytecodeCache.get()` → `workspace.getClassBytes()` |

---

## Phase 3 — 功能移植

### 3.1 三层缓存架构（移植 jadx）

```
L1: Workspace.classCache (ConcurrentHashMap<String, byte[]>)
    → ClassDiscoverer 阶段填充
L2: DecompileCache (ConcurrentHashMap<CacheKey, String>)
    → key = internalName + engine + optionsHash
    → 引擎切换/选项变更自动失效
L3: DiskCodeCache (~/.fxdecompiler/cache/<workspace-hash>/)
    → 序列化反编译结果到磁盘
    → 应用版本变更自动清理
```

新增：`cache/DecompileCache.java`, `cache/DiskCodeCache.java`, `cache/CacheConfig.java`

### 3.2 增强搜索（移植 jadx + 改进现有）

7 种搜索类型：

| 类型 | 说明 |
|------|------|
| CLASS | 按类名搜索（短名/全名/内部类名） |
| METHOD | 按方法名搜索 |
| FIELD | 按字段名搜索 |
| CODE | 跨所有已反编译源码全文搜索 |
| RESOURCE | 搜索非 class 资源文件内容 |
| COMMENT | 搜索注释行 |
| BYTECODE | 搜索字节码指令文本 |

搜索架构：
```
SearchService → 并行 SearchProvider[]
  → ClassSearchProvider / MethodSearchProvider / CodeSearchProvider
  → ResourceSearchProvider / CommentSearchProvider / BytecodeSearchProvider
  → CombinedSearchProvider 合并排序
  → SearchResult → SearchDialog UI（增量渲染 + 分页）
```

改进：防抖 200ms，结果上限 500，按类型分组 + 相关度排序

### 3.3 代码元数据 + Ctrl+Click 导航（移植 jadx）

```
反编译输出 → OutlineParser → CodeMetadata
  → Map<lineNumber, List<Reference>>
    - CLASS_REF / METHOD_REF / FIELD_REF
    - 每行包含引用目标信息
CodeLinkHandler → Ctrl+Click → 解析光标行 → 查找引用 → NavigationService.openPath()
```

新增：`navigation/CodeMetadata.java`, `navigation/CodeLinkHandler.java`

### 3.4 设置窗口（移植 jadx + Recaf）

分类标签页：
- 反编译器：默认引擎 + 各引擎选项
- 界面：字体、字号、换行、行号
- 缓存：内存/磁盘模式、缓存目录
- 快捷键：可定制键绑定
- 语言：切换语言（触发 `LocaleChangedEvent`）

新增：`ui/settings/SettingsDialog.java`, `config/SettingsConfig.java`

### 3.5 i18n 全量集成

- 所有硬编码字符串替换为 `I18nUtil.getString(key)`
- 新增 `resources/language/language_en.properties` (English)
- 语言切换通过 `LocaleChangedEvent` 刷新所有 UI

---

## Phase 4 — 新实例（并排对比）

```java
public class ProcessService {
    public static void launchNewInstance(String filePath) {
        String jarPath = /* 获取当前 JAR 路径 */;
        new ProcessBuilder("java", "-jar", jarPath, "--open", filePath).start();
    }
}
```

- 窗口菜单新增 "新窗口打开当前文件"
- CLI 参数 `--open <path>` 在启动时自动打开文件

---

## 文件统计

| Phase | 新增 | 修改/删除 |
|-------|------|-----------|
| Phase 1 | 0 | 8 |
| Phase 2 | 15 | 12 |
| Phase 3 | 15 | 10 |
| Phase 4 | 1 | 2 |
| **合计** | **31** | **32** |

---

## 删除清单

| 文件 | 原因 |
|------|------|
| `decompiler/BytecodeCache.java` | 全局缓存 → Workspace 实例成员 |
| `ui/WorkspaceTabManager.java` | 拆分为 WorkspaceView + TabManager + ToolWindowManager |
| `app/ClassTabOpener.java` | 重构为 NavigationService |

---

## 不变部分

以下组件在新架构下保持工作机制不变：
- 三个反编译引擎适配器（Procyon/CFR/Vineflower）
- 语法高亮和主题系统（RegexHighlighter + VsCodeThemeLoader）
- FileTreeView + FileTreeCell（适配 PathNode 数据源）
- BytecodeViewTab + ClassInfoView
- OutlinePane + InheritancePane
- AppHeaderBar + StatusBar
- 导出服务（ExportService）
