# FxDecompiler 代码优化设计文档

> 基于 Java 开发规范（`rules/Java开发规范.md`）对代码进行合规优化，从底层模块到上层模块逐步推进。本轮仅做优化，不新增功能。

## 优化范围总览

| 模块 | 文件数 | 主要问题 |
|------|--------|----------|
| decompiler/ | 7 | 命名合规、工具类合规、通配符导入、options常量化、资源泄漏 |
| io/ | 3 | 工具类合规、正则预编译、静默异常吞、重复代码提取 |
| config/ | 1 | 静默异常吞、POJO缺toString、字段无注释 |
| model/ | 3 | 枚举命名、Javadoc缺失、死代码字段、缺equals/hashCode |
| ui/ + app/ + 入口 | 13 | 工具类合规、Javadoc、MainWindow拆分、懒加载 |

---

## 模块 1：decompiler/ — 反编译引擎层

### 1.1 命名合规

| 当前 | 改为 | 规范条款 |
|------|------|----------|
| `IDecompiler` | `Decompiler` | 接口禁止 `I` 前缀（通用命名 1.1） |
| `DecompilerType` | `DecompilerTypeEnum` | 枚举类名加 `Enum` 后缀（通用命名 1.1） |

影响范围：`IDecompiler` → `Decompiler` 需全局替换引用（约 15 处 import + 类型声明）。

### 1.2 工具类合规

**`DecompilerFactory`** — 加 `final` + 私有构造器：

```java
public final class DecompilerFactory {
    private DecompilerFactory() { throw new AssertionError(); }
    // ...
}
```

**`BytecodeCache`** — 同上，public 方法加防御性校验，删除别名方法 `getBytecode`（统一用 `get`）：

```java
public final class BytecodeCache {
    private BytecodeCache() { throw new AssertionError(); }

    public static void put(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        CACHE.put(internalName, bytes);
    }
    
    public static byte[] get(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return CACHE.get(internalName);
    }
    
    // 删除 getBytecode — 统一用 get
}
```

影响范围：`CfrDecompiler`, `VineflowerDecompiler`, `ProcyonDecompiler`, `ExportService` 中的 `BytecodeCache.getBytecode(...)` → `BytecodeCache.get(...)`。

### 1.3 `Decompiler` (原 `IDecompiler`)

接口方法保留不变。已有 3 个 default 方法（`getName`, `initialize`, `cleanup`），其中 `initialize` 被 CfrDecompiler 和 VineflowerDecompiler 覆写，合理保留。

### 1.4 `CfrDecompiler`

- **通配符导入**: `import java.util.*` → 显式导入 `Arrays, Collection, Collections, HashMap, List, Map`
- **options 常量化**: `options` 在构造函数中重复构建相同 Map，提取为 `private static final Map<String, String> DEFAULT_OPTIONS`
- **Javadoc**: 添加 `@author` / `@date`

### 1.5 `VineflowerDecompiler`

- **options 常量化**: 同上
- **资源清理**: `tempDir.delete()` 改用 `Files.deleteIfExists`，清理失败时通过 `System.Logger` 记录
- **Javadoc**: 添加 `@author` / `@date`

### 1.6 `ProcyonDecompiler`

- **资源泄漏修复**: `StringWriter writer = new StringWriter()` 用后未关闭，改为 try-with-resources
- **`CachedTypeLoader`**: 内部类方法添加缺失的 `@Override`

### 1.7 不变更

- 反编译 API 调用逻辑
- `DecompilerFactory` 的 EnumMap 单例缓存策略
- 不引入 SPI 插件机制

---

## 模块 2：io/ — 文件 IO 层

### 2.1 工具类合规

`ClassDiscoverer`, `FileTreeBuilder`, `ExportService` — 全部加 `final` + 私有构造器：

```java
public final class ClassDiscoverer {
    private ClassDiscoverer() { throw new AssertionError(); }
    // ...
}
```

### 2.2 `ClassDiscoverer`

- **正则预编译**: `guessType()` 中 `lower.matches("...")` 每次编译 Pattern，提取：
  ```java
  private static final Pattern RESOURCE_PATTERN =
      Pattern.compile(".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql)$");
  ```
- **异常处理**: `discoverDirectory()` 中 `catch (IOException ignored)` → `LOG.log(WARNING, ...)`
- **Javadoc**: 补充

### 2.3 `FileTreeBuilder`

- **注释清理**: `getOrCreateParent()` 上的"避免 ConcurrentModificationException"注释已过时，删除
- **sortTree 迭代化**: 递归无深度限制 → 改为 `ArrayDeque` BFS 迭代，避免极深目录树栈溢出
- **Javadoc**: 补充

### 2.4 `ExportService`

- **防御性校验**: 3 个 public 方法加 `Objects.requireNonNull`
- **重复代码提取**: `exportTree` 和 `exportTreeToZip` 中的字节码获取逻辑提取为：
  ```java
  private static byte[] resolveClassBytes(FileTreeNode data) {
      byte[] bytes = data.getCachedBytes();
      if (bytes == null) {
          bytes = BytecodeCache.get(data.getFullPath().replace(".class", ""));
      }
      return bytes;
  }
  ```

### 2.5 不变更

- `ClassEntry` record 定义
- 文件发现和导出逻辑

---

## 模块 3：config/ — 配置层

### 3.1 异常处理合规

`load()` 和 `save()` 当前静默吞异常，改为使用 `System.Logger`：

```java
private static final System.Logger LOG = System.getLogger(AppConfig.class.getName());

public static AppConfig load() {
    try {
        if (Files.exists(CONFIG_FILE)) {
            String json = Files.readString(CONFIG_FILE);
            return GSON.fromJson(json, AppConfig.class);
        }
    } catch (IOException e) {
        LOG.log(System.Logger.Level.WARNING, "Failed to load config, using defaults", e);
    }
    return new AppConfig();
}
```

### 3.2 POJO 合规

内部类 `Window`、`Theme`、`Decompiler` 补充 `toString()`：

```java
@Override
public String toString() {
    return "Window{width=" + width + ", height=" + height + ", x=" + x + ", y=" + y + "}";
}
```

### 3.3 字段注释

- `recentFiles` 添加用途注释
- `Decompiler.defaultEngine` 注释有效枚举值

### 3.4 不变更

- JSON 路径、格式、Gson 序列化
- public 字段访问（Gson 反序列化需要）

---

## 模块 4：model/ — 数据模型层

### 4.1 枚举命名合规

```java
// FileTreeNode.java
// public enum NodeType { ... }
// →
public enum NodeTypeEnum { PACKAGE, CLASS_FILE, JAVA_FILE, RESOURCE, BINARY }
```

影响范围：全局替换 `FileTreeNode.NodeType` → `FileTreeNode.NodeTypeEnum`（约 10 处）。

### 4.2 删除死代码

`FileTreeNode.decompiled` 字段在代码中无任何读取点（`isDecompiled()` 从未被调用），删除：
- 删除 `decompiled` 字段
- 删除 `isDecompiled()` 方法
- 删除 `setDecompiled()` 方法

### 4.3 Javadoc 补充

所有 3 个类添加类级别 Javadoc + `@author` / `@date`。

### 4.4 补充 equals/hashCode

`Workspace` 和 `OpenFile` 添加 `equals()` / `hashCode()`：
- `Workspace`: 基于 `sourceFile` 判断相等
- `OpenFile`: 基于 `fullPath` + `engine` 判断相等（简化 `MainWindow.findOpenClassTab` 逻辑）

### 4.5 `toString()` 改进

- `FileTreeNode`: `"FileTreeNode{name='...', type=..., path='...'}"`（当前仅返回 name）
- `Workspace`: 添加 `sourceFile` 信息

---

## 模块 5：ui/ + app/ + 入口层

### 5.1 工具类合规

`AppTheme`、`AppHeaderBar`、`BackgroundTasks`、`VsCodeThemeLoader` — 加 `final` + 私有构造器。

### 5.2 `VsCodeThemeLoader`

`defaultDark()` 每次调用重建 Map，提取为静态常量：

```java
private static final ThemeData DEFAULT_DARK = buildDefaultDark();

public static ThemeData defaultDark() {
    return DEFAULT_DARK;
}
```

### 5.3 `RegexHighlighter`

`resolveStyle()` 中前缀匹配逻辑有歧义（scope="keyword" 会匹配 "keyword.other"），改为精确逐段匹配。

### 5.4 Javadoc 补充

`StatusBar`、`CodeEditorTab`、`FileTreeView`、`FileTreeCell`、`RegexHighlighter`、`MainMenuBar.Actions` 各接口方法补充 Javadoc。

### 5.5 `MainWindow` 拆分（核心重构）

当前 597 行职责混合，拆为 3 类：

| 类 | 职责 | 预计行数 | 包含方法 |
|---|------|----------|----------|
| `MainWindow` | 协调器 + 菜单入口 | ~250 | `show()`, `openFile()`, `openDirectory()`, `closeCurrentWorkspace()`, `closeOtherWorkspaces()`, `saveCurrentFile()`, `exportAllFiles()`, `exit()`, 所有编辑/视图/引擎 action, `loadFile()`, `currentCodeTab()`, `currentWorkspaceView()`, 状态栏/缩放/行号/引擎切换 |
| `WorkspaceTabManager` | 外层 Tab 生命周期 | ~120 | `addWorkspaceTab()`, `closeWorkspaceTab()`, `cleanupClosedWorkspace()`, `createWelcomeTab()`, `removeWelcomeTab()`, `workspaceViews` Map, `updateStatusForWorkspace()` |
| `ClassTabOpener` | class 反编译+打开 Tab | ~180 | `openClassTab()`, `refreshCurrentClassTab()`, `readClassBytes()`, `findOpenClassTab()`, `findNode()`, `createCodeEditorTab()`, `bindCaretPosition()` |

依赖关系：`MainWindow` → `WorkspaceTabManager` + `ClassTabOpener`。`WorkspaceTabManager` 和 `ClassTabOpener` 互相独立。

### 5.6 入口层

`FxDecompilerApp.FxApplication` — `@SuppressWarnings("deprecation")` 补充注释说明原因（`initStyle(EXTENDED)` 在 JavaFX 中被标记为 deprecated 但功能正常）。

`AppLauncher`、`Main` — 已是 `final` + 私有构造器，仅补 Javadoc。

### 5.7 不变更

- UI 布局和 CSS 样式表
- 菜单栏结构（不新增"最近文件"等功能）
- 快捷键绑定
- 主题加载逻辑

---

## 变更影响汇总

| 指标 | 数值 |
|------|------|
| 涉及文件 | 27 |
| 新增类 | 2 (`WorkspaceTabManager`, `ClassTabOpener`) |
| 重命名 | 2 (`IDecompiler` → `Decompiler`, `DecompilerType` → `DecompilerTypeEnum`, `NodeType` → `NodeTypeEnum`) |
| 删除字段 | 1 (`FileTreeNode.decompiled`) |
| 删除方法 | 3 (`setDecompiled`, `isDecompiled`, `BytecodeCache.getBytecode`) |
| 提取方法 | 1 (`ExportService.resolveClassBytes`) |
| 预估净增行 | ~200 行（拆分新增的结构代码 vs 删除的冗余代码） |

## 不涉及

- 不新增任何用户功能
- 不修改 API 调用逻辑
- 不修改 CSS 或 FXML
- 不添加测试（本轮仅代码质量优化）
- 不升级依赖版本
