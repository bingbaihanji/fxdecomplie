# jadx-gui 功能迁移设计文档

## 概述

将 jadx-gui 中以下功能迁移到 FxDecompiler，基于 JavaFX 技术栈重新实现：

1. **代码区底部标签页** — Code / Smali / Bytecode / Simple 四标签切换查看
2. **Split View** — Code + Bytecode 左右并排视图，方法级光标同步
3. **主工具栏** — 菜单栏下方工具按钮（打开/重新加载/导出/定位/搜索）
4. **代码区右键菜单** — 跳转声明 / 查看继承图 / 查看方法图 / 添加注释
5. **注释系统** — 添加、持久化、导出保留注释

## 审查结论与修订原则（2026-06-21）

整体方向可行，但不能按 jadx-gui 的 Swing/DEX 实现逐类搬迁。jadx 的对应实现主要在：

- `jadx-gui/ui/codearea/ClassCodeContentPanel.java`：底部 Code/Smali/Bytecode/Simple/Fallback 标签与 Split view。
- `jadx-gui/ui/codearea/CodeArea.java`：右键菜单挂载跳转、注释、图形相关 action。
- `jadx-gui/ui/codearea/CommentAction.java`、`ui/dialog/CommentDialog.java`：注释锚点和项目数据更新。
- `jadx-gui/ui/graphs/*GraphDialog.java`、`GraphPanel.java`：DOT 图生成和图形查看器。
- `jadx-gui/ui/MainWindow.java`：工具栏、reload、selectNodeInTree 等全局动作。

FxDecompiler 当前是 JavaFX + JVM class 反编译软件，已有 `CodeEditorTab`、`BytecodeViewTab`、`InheritanceService`、`NavigationService`、`UsageSearchService`、`MainMenuBar.Actions`、`WorkspaceTabManager` 等基础能力。因此迁移策略应是“复刻交互模型 + 复用当前服务 + JVM 语义重写”，而不是复制 jadx 的数据模型。

必须修订的点：

1. **Smali 语义边界**：jadx 的 Smali 来自 Android DEX。FxDecompiler 只处理 JVM class 时无法生成真实 smali。UI 可保留 `Smali` 标签以贴近 jadx，但内部应命名和文档化为 “JVM instruction view / ASM 指令视图”。未来如果加入 DEX 输入，再用 dexlib2/jadx-core 生成真实 smali。
2. **底部标签控件不要继续嵌套 TabPane**：当前 `CodeEditorTab` 已是主代码标签，内部再放 `TabPane` 会影响 Split、拖拽、搜索栏和右键菜单。建议抽出 `CodeContentDeck`，底部用 `HBox + ToggleButton ToggleGroup + Split toggle` 做紧凑标签栏。
3. **Split view 要按双 deck 设计**：jadx 是左右两套视图，右侧默认 Smali。FxDecompiler 首版可默认左 Code、右 Bytecode，但数据结构不要写死右侧只能 Bytecode，避免后续返工。
4. **同步能力分层降级**：优先用 `CodeMetadata` / ASM `LineNumberTable` / 方法签名；失败后回退到文本签名搜索；再失败只保持当前滚动，不弹异常。Java class 通常只能做到方法级或行级近似同步，不能承诺 jadx 的 DEX 指令级同步。
5. **注释不能只按行号保存**：反编译结果会随引擎、版本和配置变化。注释锚点至少包含 `classInternalName + memberSignature + lineNumber + sourceHash/optionsHash`，导出时先按成员和源码 hash 匹配，失败再按原行号降级。
6. **图形查看器先走 JavaFX 原生边界**：jadx 使用 Swing + graphviz-java + SVG 渲染。FxDecompiler 当前使用 JavaFX 原生控件渲染应用生成的 DOT 子集，避免 `WebView + Viz.js` 的 WASM 初始化兼容性、包体和许可证问题。
7. **工具栏定位当前文件需要新增树定位 API**：当前 `FileTreeView` 只是薄封装，没有 `selectPath`。需要在 `WorkspaceTabManager` 或 `FileTreeView` 增加 `selectTreeNodeByPath(String fullPath)`，用于 toolbar localizer。
8. **高版本 class 和大文件必须后台生成**：Smali/Bytecode/Graph 都要走 `BackgroundTasks`、取消令牌、过期结果丢弃和输出大小保护；不能在 FX 线程运行 ASM 大扫描。

## 架构总览

### 新建文件清单（~18 个 Java 文件）

| # | 文件 | 包 | 职责 |
|---|------|-----|------|
| 1 | `CodeViewPanel.java` | `ui.code` | 代码视图顶层容器，管理底部 4 标签切换 + Split View 分屏 |
| 2 | `AbstractCodeContentPanel.java` | `ui.code` | 代码内容面板抽象基类，统一生命周期（懒加载/刷新/销毁） |
| 3 | `SourceContentPanel.java` | `ui.code` | 源码面板（Code tab），复用现有 RegexHighlighter + CodeArea |
| 4 | `SmaliContentPanel.java` | `ui.code` | JVM 指令视图；UI 标签可叫 Smali，但不是 Android smali |
| 5 | `BytecodeContentPanel.java` | `ui.code` | 完整 ASM Textifier 字节码文本，从 `BytecodeViewTab` 抽取 |
| 6 | `SimpleContentPanel.java` | `ui.code` | 简化代码面板（轻量词法状态机去注释/压缩空行） |
| 7 | `CodeSyncHelper.java` | `ui.code` | Split View 同步工具（LineNumberTable 方法级映射） |
| 8 | `CodeAreaContextMenu.java` | `ui.code` | 右键上下文菜单（4 项） |
| 9 | `MainToolBar.java` | `ui.toolbar` | 工具栏组件 |
| 10 | `GraphDialog.java` | `ui.graph` | 图形弹窗（JavaFX 原生渲染项目生成的 DOT 子集） |
| 11 | `GraphService.java` | `ui.graph` | DOT 字符串生成工具类 |
| 11-1 | `DotGraphParser.java` | `ui.graph` | 解析项目生成的 DOT 子集 |
| 11-2 | `DotGraphRenderer.java` | `ui.graph` | JavaFX 原生节点/边布局渲染 |
| 12 | `CommentDialog.java` | `ui.comment` | 注释输入弹窗 |
| 13 | `CommentManager.java` | `service` | 注释 JSON 文件读写 |
| 14 | `CommentData.java` | `model` | 注释数据模型 record |
| 15 | `CodeContentDeck.java` | `ui.code` | 管理四种内容视图的懒加载、激活状态和销毁 |
| 16 | `CommentExportDecorator.java` | `service` | 导出源码时插入/合并持久化注释 |

### 修改文件清单（~8 个）

| # | 文件 | 改动内容 |
|---|------|---------|
| 19 | `CodeEditorTab.java` | 内部改为委托给 `CodeViewPanel`；保留 Tab 兼容方法 |
| 20 | `MainWindow.java` | `topBars` 中添加 `MainToolBar`；实现刷新/定位等工具栏回调 |
| 21 | `WorkspaceTabManager.java` | 暴露当前工作区树定位 API；当前代码标签仍由 `CodeEditorTab` 承载 |
| 22 | `ClassTabOpener.java` | 创建 `CodeEditorTab` 时传入完整 `CodeViewContext` |
| 23 | `module-info.java` | 无需 `javafx.web`，图形弹窗使用 JavaFX 原生控件 |
| 24 | `pom.xml` | 无需 `javafx-web`，避免 WebView/WASM 运行时依赖 |
| 25 | `theme/dark.css` | 底部标签栏、工具栏、Split View 样式 |
| 26 | `language_zh_CN.properties` | 新增 i18n key |
| 27 | `language_en.properties` | 新增 i18n key |

### 核心上下文对象

`CodeViewPanel` 只负责显示和编辑器交互，不直接持有 `MainWindow`。主窗口、独立代码窗口、未来多窗口共享同一套代码视图时，统一通过上下文和 action handler 交互。

```java
public record CodeViewContext(
    Workspace workspace,
    FileTreeNode node,
    OpenFile openFile,
    byte[] classBytes,
    CodeMetadata metadata,
    WorkspaceIndex workspaceIndex,
    String workspaceHash,
    String sourceHash,
    String optionsHash
) {}

public interface CodeActionHandler {
    void goToDeclaration(CodeMetadata.Reference reference);
    void openClass(String fullPath, int line);
    void showInheritanceGraph(CodeViewContext context);
    void showMethodGraph(CodeViewContext context);
    void addOrUpdateComment(CodeViewContext context, TextPos caretPosition);
    void searchInWorkspace(String selectedText);
}
```

约束：
- `classBytes` 在 context 构造时 clone，视图内部只读使用。
- `workspaceIndex` 可能尚未构建完成；图、跳转、搜索相关动作必须支持异步等待或禁用状态。
- `workspaceHash` 基于输入文件绝对路径、mtime、size 等稳定信息，用于隔离不同工作区的注释数据。
- `sourceHash` 基于反编译源码文本；`optionsHash` 复用当前统一的反编译选项 hash，注释和缓存都用同一口径。
- `CodeEditorTab` 保留 `getCodeArea()`、`zoomIn()`、`setLineNumbersEnabled()` 等兼容方法，内部转发给 `CodeViewPanel` 的当前 Code 视图。

## 模块一：底部标签页（CodeViewPanel）

### 布局结构

```
┌──────────────────────────────────────────────────────────┐
│  EditorSearchBar (Ctrl+F 时显示)                          │
├──────────────────────────────────────────────────────────┤
│                                                          │
│                    代码内容区                             │
│            (根据当前选中 tab 切换显示)                     │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  [Code] [Smali] [Bytecode] [Simple]               │ ☐ Split │
└──────────────────────────────────────────────────────────┘
```

### 类设计

**`CodeViewPanel`** — `VBox` 子类，顶层容器
- **构造参数**: `OpenFile`, `byte[] classBytes`, `CodeMetadata`, `onNavigate`, 配置（theme/fontFamily/fontSize/wrapText/lineNumbers）
- **内部组件**:
  - `EditorSearchBar` — 现有组件，Ctrl+F 激活
  - `StackPane contentArea` — 当前活动的内容面板
  - `CodeContentDeck leftDeck` — 左侧/单视图内容 deck
  - `CodeContentDeck rightDeck` — Split view 右侧内容 deck，按需创建
  - `HBox bottomBar` — 底部自定义标签栏，包含 4 个 `ToggleButton`
  - `CheckBox splitToggle` — "Split View" 切换
  - `SplitPane splitPane` — 分屏容器（split 激活时替换 contentArea）
- **关键方法**:
  - `switchToTab(int index)` — 切换底部标签
  - `setSplitMode(boolean)` — 切换分屏模式
  - `getSourceCodeArea()` — 返回当前源码 CodeArea（供外部引用）
  - `refreshWithNewSource(String newSource)` — 更新源码内容
  - `getLineCount()` — 获取总行数
  - `zoomIn() / zoomOut() / resetZoom()` — 缩放代理
  - `setLineNumbersEnabled(boolean)` — 行号开关
  - `dispose()` — 释放资源

**`AbstractCodeContentPanel`** — 内容面板抽象基类
- **构造参数**: `byte[] classBytes`
- **抽象方法**:
  - `Node getContent()` — 返回渲染后的内容节点
  - `String getContentType()` — 返回类型标识（"source"/"smali"/"bytecode"/"simple"）
  - `void loadAsync()` — 异步加载内容
  - `boolean isLoaded()` — 是否已加载完成
  - `void dispose()` — 释放资源
- **默认实现**:
  - `showLoading()` — 显示 ProgressIndicator
  - `showError(String msg)` — 显示错误提示
  - `showContent(Node node)` — 切换为实际内容

**`SourceContentPanel`** — 源码面板（对应 Code tab）
- 复用现有 `CodeEditorTab` 中的 `CodeArea` 构建逻辑
- 包含 `RegexHighlighter`、`LineNumberGutter`、`CodeLinkHandler`
- 将 `CodeArea` 暴露为 `getCodeArea()`

**`SmaliContentPanel`** — JVM 指令视图
- UI 标签可显示 `Smali`，但实现必须说明它是 JVM class 指令文本，不是 Android smali。
- 使用 ASM `Textifier` / `TraceClassVisitor`（`ClassReader.SKIP_DEBUG | SKIP_FRAMES`）生成类似 `javap -c` 的指令输出。
- 若未来支持 DEX 输入，再引入独立 `DexSmaliContentPanel`。
- 内容为只读 `CodeArea`，使用简单 `SyntaxDecorator`

**`BytecodeContentPanel`** — 字节码面板
- 复用现有 `BytecodeViewTab.createView()` 逻辑
- ASM `Textifier`（`ClassReader.SKIP_DEBUG | SKIP_FRAMES`），不能使用 `SKIP_CODE`，否则方法指令会被跳过
- 只读 `CodeArea`，`TextFileDecorator`

**`SimpleContentPanel`** — 简化代码面板
- 对反编译源码文本做后处理：
  - 用轻量词法状态机移除行注释（`//...`）和块注释（`/*...*/`），不能误删字符串/字符字面量中的 `//` 或 `/*`
  - 移除空行
  - 泛型简化作为 P2，不在首版用正则处理嵌套 `<...>`，避免破坏合法源码
- 只读 `CodeArea`

### Tab 懒加载策略

```
Code tab:         构造时立即加载（沿用当前首屏体验）
Smali tab:        首次选中时异步加载（BackgroundTasks）
Bytecode tab:     首次选中时异步加载
Simple tab:       首次选中时后台处理，完成后回到 FX 线程设置文本
```

切换 tab 不触发重新反编译。Smali/Bytecode 使用已缓存的 `classBytes` 生成；Simple 使用已有的反编译源码文本。
所有后台任务都必须携带 tab 级 generation/cancel token，用户快速切换或关闭 tab 后丢弃过期结果。

### 底部标签栏样式

- 不使用内嵌 `TabPane`；使用 `HBox` + `ToggleButton ToggleGroup`，避免主代码 `TabPane`、Split view、拖拽标签之间互相干扰
- 标签高度紧凑（约 28px），固定最小宽度，文本不挤压
- 字体大小 12px
- 右侧 Split View 使用 `CheckBox` 或 `ToggleButton` 放在同一个 `bottomBar` 的 trailing 区域

---

## 模块二：Split View

### 布局

```
┌───────────────────────────────┬───────────────────────────────┐
│      左侧 CodeArea            │     右侧 BytecodeArea         │
│      (源码 - RegexHighlighter) │     (ASM Textifier 字节码)     │
│                               │                               │
│  public class Foo {           │  public class Foo              │
│    private int x;             │    minor version: 0            │
│    public void bar() {        │    major version: 69           │
│      ...                      │    flags: ACC_PUBLIC ...       │
│    }                          │  }                             │
│  }                            │                               │
├───────────────────────────────┴───────────────────────────────┤
│  [Code] [Smali] [Bytecode] [Simple]                    │ ☑ Split │
└──────────────────────────────────────────────────────────────┘
```

### 行为规范

- Split 激活时：底部标签控制**左侧**面板；右侧默认 Bytecode，可在后续扩展为独立选择
- Split 关闭时：恢复单面板，底部标签控制主视图
- 初始分割比例 0.5，用户可拖拽调整
- `SplitPane.setDividerPosition()` 保持分屏比例
- 由于 JavaFX `Node` 不能同时存在于两个 parent，左右视图必须是两个独立 `CodeContentDeck` 实例，不能把同一个 `CodeArea` 同时放进左右两侧。

### 同步机制 (`CodeSyncHelper`)

**方法签名**:
```java
public final class CodeSyncHelper {
    private CodeSyncHelper() { throw new AssertionError(); }

    /** 从 classBytes 构建 方法签名 → 字节码起始行号 映射 */
    public static Map<String, Integer> buildMethodLineMap(byte[] classBytes);

    /** 从源码文本中提取光标所在行的方法签名 */
    public static String findMethodAtLine(String sourceCode, int lineNumber);

    /** 在字节码文本中查找目标方法的行号 */
    public static int findMethodLineInBytecode(String bytecodeText, String methodSignature);
}
```

同步流程：
1. 左侧 CodeArea 光标移动 → 优先从 `CodeMetadata` 或源码行定位当前成员 → 在右侧字节码文本中搜索方法签名 → 右侧跳转
2. 右侧 Bytecode 光标移动 → 从 ASM 文本提取方法签名 → 在左侧源码中搜索 → 左侧跳转
3. 通过 `LineNumberTable` 属性构建方法/行号映射（ASM `LineNumberVisitor`）
4. 若精确映射失败（如行号表不可用），回退到方法签名文本搜索
5. 若仍失败，忽略本次同步并记录 debug 日志，不弹窗，不阻塞 UI

**注意**: Java class 文件的 `LineNumberTable` 只能做到方法级映射，无法像 jadx（有 Android debug info）那样实现指令级精确同步。

---

## 模块三：主工具栏（MainToolBar）

### 布局位置

```
┌──────────────────────────────────────────────────────┐
│  MenuBar (File | Edit | View | Engine | Window | Help) │
├──────────────────────────────────────────────────────┤
│  [📂] [🔄] [💾] [📍] [🔍]                            │
├──────────────────────────────────────────────────────┤
│  FileTree │              CodeViewPanel               │
```

### 类设计

**`MainToolBar`** — `ToolBar` 子类
- **构造参数**: `Actions` 回调接口（复用 `MainMenuBar.Actions`，不引入新接口）
- **5 个按钮**:

| 图标资源 | tooltip key | 功能 | 回调 | 禁用条件 |
|----------|-------------|------|------|---------|
| `/icon/tool/open.png` | `toolbar.open` | 打开文件 | `actions.openFile()` | 无 |
| `/icon/tool/ref.png` | `toolbar.reload` | 重新加载 | `actions.refreshCurrentTab()` | 无当前 tab |
| `/icon/tool/save.png` | `toolbar.save` | 导出当前文件 | `actions.saveCurrentFile()` | 无当前 tab |
| `/icon/tool/localizer.png` | `toolbar.localizer` | 定位文件 | `actions.locateCurrentFileInTree()` | 无当前 tab |
| `/icon/tool/search.png` | `toolbar.search` | 搜索 | `actions.openSearch()` | 无当前 workspace |

- 按钮尺寸：图标 16×16，内边距 4px，间距 2px
- tooltip 通过 `I18nUtil.getString()` 国际化
- 编辑相关按钮（reload/save/localizer）需有当前 `CodeEditorTab` 才可用；搜索只需要当前 workspace
- `MainToolBar` 暴露 `refreshState(boolean hasWorkspace, boolean hasCodeTab)`，由 `MainWindow` 在 outer/workspace/code tab 切换时更新状态

### 集成点

`MainWindow` 中将 `MainToolBar` 添加到 `topBars`（VBox），位于 `AppHeaderBar` 下方：

```java
// MainWindow 构造函数中
toolbar = new MainToolBar(this); // this implements Actions
topBars.getChildren().add(toolbar);
```

需要在 `MainMenuBar.Actions` 接口中新增工具栏动作，不必显示到菜单项：
```java
void refreshCurrentTab();       // 用当前引擎重新反编译当前类
void locateCurrentFileInTree(); // 在文件树中定位当前类文件
```

`refreshCurrentTab()` 复用 `ClassTabOpener.refreshCurrentClassTab(...)`；`locateCurrentFileInTree()` 通过当前 `CodeEditorTab.getOpenFile().fullPath()` 调用 `WorkspaceTabManager.selectTreeNodeByPath(...)`。

需要补充的树定位 API：

```java
// WorkspaceTabManager
public boolean selectTreeNodeByPath(String fullPath);

// 或 FileTreeView
public boolean selectPath(String fullPath);
```

实现要求：递归展开父节点，选中目标 `TreeItem<FileTreeNode>`，调用 `scrollTo(row)`，并在失败时通过 `StatusBar` 显示 `status.locateFailed`。

---

## 模块四：右键菜单（CodeAreaContextMenu）

### 菜单结构

```
┌───────────────────┐
│  跳转声明          │
│  查看继承图        │
│  查看方法图        │
│  ───────────────  │
│  添加注释          │
└───────────────────┘
```

### 菜单项详情

| 菜单项 | i18n key | 行为 |
|--------|----------|------|
| 跳转声明 | `context.gotoDeclaration` | 获取光标处标识符 → 在文件树中查找 → 打开对应类文件 |
| 查看继承图 | `context.inheritanceGraph` | 弹窗显示当前类的继承层级图（父类→当前→子类+接口） |
| 查看方法图 | `context.methodGraph` | 弹窗显示当前类内部方法调用关系图 |
| 添加注释 | `context.addComment` | 弹窗输入注释 → 保存到 `fxdecomplie/comments/` |

### 类设计

**`CodeAreaContextMenu`** — `ContextMenu` 子类
- **构造参数**: `CodeArea`, `CodeViewContext`, `WorkspaceIndex`, `CodeActionHandler`
- **关键方法**:
  - `show(double screenX, double screenY)` — 显示菜单
  - `setupActions()` — 绑定各菜单项动作
  - `refreshState()` — 每次显示前根据当前 token、classBytes、workspaceIndex 更新禁用状态

显示菜单前应参考 jadx `CodeArea.appendCodeMenuItems(...)` 的行为：如果没有选中文本，先把 caret 移动到鼠标右键位置，再计算跳转/注释目标。JavaFX `CodeArea` 需要封装一个坐标到 `TextPos` 的工具方法；如果当前 RichText API 无法精确命中，退化为使用当前 caret，不阻塞菜单显示。

### 各功能实现

#### 跳转声明

复用并扩展 `CodeLinkHandler` 的引用提取逻辑。当前 `CodeMetadata.Reference` 只有行级引用，首版按行匹配；后续再扩展到 token 区间：
```java
// 从光标位置提取引用
CodeMetadata.Reference ref = extractReference(codeArea.getCaretPosition());
// 在索引中查找
PathNode target = workspaceIndex.findNodeByPath(ref.fullPath());
// 若找到则打开
if (target != null) onNavigate.accept(target.fullPath());
```

降级策略：
- 行上有 `CodeMetadata.Reference`：优先打开目标类，成员跳转可先打开类后定位声明行。
- 无 metadata：从当前 token 推断类名，先查同包，再查 workspace index 的简单名。
- 找不到声明：菜单项禁用或状态栏提示，不弹错误框。

#### 查看继承图

```java
// 构建继承树
TreeItem<InheritanceNode> tree = InheritanceService.buildTree(className, index);
// 生成 DOT 字符串
String dot = GraphService.toInheritanceDOT(tree);
// 弹窗展示
GraphDialog.show("继承图 - " + className, dot);
```

#### 查看方法图

```java
// 从 classBytes 解析方法调用图
MethodGraph graph = GraphService.parseMethodCalls(classBytes);
// 生成 DOT
String dot = GraphService.toMethodDOT(graph);
// 弹窗展示
GraphDialog.show("方法图 - " + className, dot);
```

#### 添加注释

```java
CommentAnchor anchor = CommentAnchorResolver.resolve(context, codeArea.getCaretPosition());
CommentDialog.show(owner, anchor, existingComment, saved -> {
    CommentManager.save(context.workspaceHash(), saved);
    commentLayer.refresh(CommentManager.load(context.workspaceHash(), saved.className()));
});
```

注释目标优先级参考 jadx 的 `CommentAction`：优先绑定到当前方法/字段/类声明；如果无法定位成员，再绑定到当前源码行。首版不需要支持指令 offset 注释，因为 JVM class 当前没有稳定的反编译 IR offset 映射。

---

## 模块五：注释系统

### 数据模型

```java
/**
 * 注释数据记录
 *
 * @param className       类全限定路径（内部形式, 如 "com/example/Foo.class"）
 * @param memberSignature 成员签名，例如 "foo(Ljava/lang/String;)V"，类级注释为空
 * @param line            行号（1-based），作为降级定位
 * @param sourceHash      保存注释时源码文本 hash
 * @param optionsHash     反编译引擎和选项 hash
 * @param style           注释样式，首版支持 LINE/BLOCK
 * @param text            注释文本
 * @param author          作者
 * @param time            创建/更新时间（ISO-8601）
 */
public record CommentData(
    String className,
    String memberSignature,
    int line,
    String sourceHash,
    String optionsHash,
    CommentStyle style,
    String text,
    String author,
    String time
) {}
```

```java
public enum CommentStyle {
    LINE,   // // comment
    BLOCK   // /* comment */
}
```

`CommentAnchor` 不必直接持久化为单独文件，可作为 `CommentData` 的构造输入：

```java
public record CommentAnchor(
    String className,
    String memberSignature,
    int line,
    String sourceHash,
    String optionsHash
) {}
```

### 存储方案

- **存储路径**: `<appDir>/fxdecomplie/comments/<workspaceHash>/<classInternalName>.json`
- class 路径写入文件名时必须做安全编码，不能直接拼接 `/`、`\`、`..`。
- **文件格式**:

```json
{
  "className": "com/example/Foo.class",
  "schemaVersion": 1,
  "comments": [
    {
      "memberSignature": "bar()V",
      "line": 12,
      "sourceHash": "sha256:...",
      "optionsHash": "vineflower:...",
      "style": "LINE",
      "text": "核心方法",
      "author": "bingbaihanji",
      "time": "2026-06-21T10:30:00"
    }
  ]
}
```

### 类设计

**`CommentManager`** — 工具类（`final class`，私有构造器）

```java
public final class CommentManager {
    private CommentManager() { throw new AssertionError(); }

    /** 保存注释 */
    public static void save(String workspaceHash, CommentData comment);

    /** 加载某个类的全部注释 */
    public static List<CommentData> load(String workspaceHash, String className);

    /** 删除某个注释（按类+成员+行号+时间戳匹配） */
    public static boolean delete(String workspaceHash, String className, String memberSignature, int line, String time);

    /** 获取注释存储根目录 */
    public static Path getCommentDir();
}
```

**`CommentDialog`** — `Dialog<String>` 子类

```java
public final class CommentDialog {
    private CommentDialog() { throw new AssertionError(); }

    /** 显示注释输入弹窗，返回用户输入的注释文本 */
    public static void show(Window owner, CommentAnchor anchor, CommentData existing, Consumer<CommentData> onSave);
}
```

- UI 内容：`TextArea`（多行输入）+ 样式下拉框 + 显示行号/成员标签
- 如果已有注释则预填充（"更新注释"模式）
- 保存按钮触发 `onSave` 回调；空内容在更新模式下表示删除，新增模式下直接取消

### 注释在导出中的处理

导出源码时（`ExportService`），先调用 `CommentExportDecorator.apply(sourceCode, comments, metadata)`。定位顺序：

1. `sourceHash + optionsHash` 匹配：直接按保存时行号插入。
2. 成员签名匹配：在当前源码中找到成员声明/方法体起点，再按相对行偏移插入。
3. 以上失败：按原始行号插入，但状态栏/导出结果记录 warning。

```java
// 伪代码
String[] lines = sourceCode.split("\n", -1);
for (CommentData c : comments) {
    int targetLine = CommentExportDecorator.resolveLine(sourceCode, metadata, c);
    if (targetLine >= 1 && targetLine <= lines.length) {
        lines[targetLine - 1] = CommentExportDecorator.insert(lines[targetLine - 1], c);
    }
}
return String.join("\n", lines);
```

插入格式：
- `LINE`: `原始代码 // FXD: 注释文本`
- `BLOCK`: 在目标行上方插入 `/* FXD: 注释文本 */`

导出前必须转义 `*/`、换行和控制字符，避免生成不可编译或注入式源码。

### 注释在代码区的显示

- 代码区行号旁显示小圆点指示器（通过自定义 `LineNumberGutter` 增强）
- 鼠标悬停圆点时显示注释内容的 tooltip
- 此功能为 Phase 2 增强，本次迁移仅实现存储+导出

---

## 模块六：图形弹窗（GraphDialog + GraphService）

### 依赖

- 图形弹窗使用 JavaFX 原生控件渲染应用生成的 DOT 子集，不依赖 `javafx.web`、WebView 或 Viz.js。
- 该方案避免 JavaFX WebView 在部分环境中卡在 WASM 初始化阶段，同时减少打包体积和运行时依赖。
- 若未来需要完整 Graphviz 布局能力，应优先引入服务端/本地 Graphviz 渲染适配层，并保留当前原生渲染作为失败回退。

### 类设计

**`GraphDialog`** — `Dialog<Void>` 子类

```java
/**
 * 图形可视化弹窗，使用 JavaFX 原生控件渲染 DOT 子集。
 */
public class GraphDialog extends Dialog<Void> {
    /** @param title 弹窗标题 */
    /** @param dot   DOT 格式图形描述字符串 */
    public GraphDialog(Window owner, String title, String dot);

    /** 显示图形弹窗 */
    public static void show(Window owner, String title, String dot);
}
```

- 内部调用 `DotGraphParser` 解析节点、边、`rankdir`、`label`、`fillcolor`
- `DotGraphRenderer` 使用 JavaFX `ScrollPane`、`Pane`、`Label`、`Line`、`Polygon` 渲染节点和箭头
- 支持 `Ctrl+Wheel` 缩放、滚动查看、DOT 源码/图形视图切换
- 弹窗尺寸：800×600，可调整大小
- 解析失败时保留 DOT 源码，便于复制到外部工具排查

**`GraphService`** — 工具类

```java
public final class GraphService {
    private GraphService() { throw new AssertionError(); }

    /** 从 InheritanceNode 树生成 DOT（继承关系图） */
    public static String toInheritanceDOT(TreeItem<InheritanceNode> root);

    /** 从方法调用关系生成 DOT（方法关系图）*/
    public static String toMethodDOT(MethodGraph graph);

    /** 从 classBytes 解析类内方法调用关系 */
    public static MethodGraph parseMethodCalls(byte[] classBytes);
}

public record MethodGraph(
    String ownerInternalName,
    List<MethodNode> methods,
    Set<MethodEdge> edges
) {}

public record MethodNode(String name, String descriptor, String displayName) {}
public record MethodEdge(MethodNode from, MethodNode to) {}
```

方法节点必须使用 `name + descriptor` 做唯一 key，避免重载方法合并错误。

### 继承图 DOT 生成规则

```
digraph G {
    rankdir=BT;  // 从上到下（父类在上）
    node [shape=box, style=filled, fillcolor=#...];
    "java.lang.Object" [fillcolor=#...];
    "com.example.Parent" [fillcolor=#...];
    "com.example.Child" [fillcolor=#...]; // current class

    "com.example.Parent" -> "java.lang.Object";
    "com.example.Child" -> "com.example.Parent";
}
```

- 当前类用高亮色，父类/接口用不同色
- 超类连线上行（`rankdir=BT`）
- 复用现有 `InheritanceService.buildTree()` 生成数据

### 方法图 DOT 生成规则

```
digraph G {
    rankdir=LR;
    node [shape=box];
    "methodA()" -> "methodB()";
    "methodA()" -> "methodC()";
    "methodB()" -> "methodD()";
}
```

- 使用 ASM `ClassVisitor` + `MethodVisitor.visitMethodInsn(...)` 扫描方法体，不引入 `asm-tree` 依赖
- 仅显示类内部方法调用（owner 等于当前类），首版忽略外部类节点
- 覆盖 `INVOKESPECIAL` / `INVOKEVIRTUAL` / `INVOKESTATIC` / `INVOKEINTERFACE`，包含构造器和静态初始化块，但 UI 可提供隐藏 `<init>` / `<clinit>` 的开关
- DOT node id 使用稳定生成的 `Node_0`、`Node_1`，label 里显示方法名；所有 label 必须做 DOT 转义
- 大类方法数超过阈值（建议 300）时先显示确认提示或只显示当前成员相关子图，避免图形布局和渲染过慢

---

## 数据流

### 反编译管道适配

```
用户点击文件树节点
  → ClassTabOpener.openClassTab()
    → 查找/创建 CodeEditorTab（外壳保留），内部委托 CodeViewPanel
    → BackgroundTasks.run()
      → 读取 classBytes
      → decompileWithCache() — 三级缓存
      → Platform.runLater()
        → new CodeViewContext(workspace, node, openFile, classBytes, metadata, workspaceHash, sourceHash, optionsHash)
        → CodeEditorTab.setContent(CodeViewPanel)  // Code tab 立即显示
```

### 用户切换底部标签

```
用户点击 [Smali] tab
  → SmaliContentPanel.loadAsync()（如未加载）
    → BackgroundTasks.run()
      → ASM TraceClassVisitor 生成助记符文本
      → Platform.runLater() → 设置 CodeArea.text
```

### 右键菜单触发链

```
右键 CodeArea
  → CodeAreaContextMenu.show()
    ├─ 跳转声明 → 光标处提取标识符 → WorkspaceIndex.findNodeByPath() → ClassTabOpener
    ├─ 查看继承图 → InheritanceService.buildTree() → GraphService.toInheritanceDOT() → GraphDialog()
    ├─ 查看方法图 → GraphService.parseMethodCalls() → GraphService.toMethodDOT() → GraphDialog()
    └─ 添加注释 → CommentDialog.show() → CommentManager.save(workspaceHash, comment)
```

---

## 依赖与 module-info.java 变更

图形查看器不依赖 `javafx.web`。当前实现只使用已有的 JavaFX controls/graphics 模块，因此 `pom.xml` 不需要 `javafx-web`，`module-info.java` 不需要 `requires javafx.web;`。

---

## 实施阶段

### P0：结构迁移与无回归

- 保留 `CodeEditorTab` 外壳，抽出 `SourceContentPanel`、`CodeViewPanel`、`CodeContentDeck`。
- Code 标签首屏行为与当前一致：双击 class 后源码仍优先显示，不等待 Bytecode/Graph/Comment 初始化。
- 工具栏接入 `MainWindow.topBars`，实现 open/reload/save/localizer/search。
- 新增 `WorkspaceTabManager.selectTreeNodeByPath(...)`。
- 保留现有源码搜索、Ctrl+F、Ctrl+G、缩放、行号、新窗口和标签拖拽能力。

### P1：多视图与 Split view

- 添加 Code / Smali / Bytecode / Simple 四个底部标签。
- Smali/Bytecode/Simple 首次点击后台生成，支持取消和过期结果丢弃。
- Split view 创建左右两个独立 deck，默认左 Code、右 Bytecode。
- 实现方法级同步和文本搜索降级。
- 为大 class 设置输出大小保护和加载提示。

### P2：右键菜单、图和注释

- 添加右键菜单：跳转声明、查看继承图、查看方法图、添加/更新注释。
- 图形弹窗使用离线 Viz.js 或 JavaFX 原生 fallback，支持缩放、拖拽、导出 SVG。
- 注释按稳定锚点持久化，导出源码时保留注释。
- 行号 gutter 注释标记作为增强项，不阻塞首版闭环。

### P3：体验增强

- Split 右侧视图可独立切换。
- 方法图支持当前方法子图、外部调用折叠、节点点击跳转。
- 注释搜索、注释列表、项目文件中记录注释仓库路径。
- 若引入 DEX 输入，再实现真正的 Smali 生成。

---

## 国际化 Key 清单

| Key | 中文 | 英文 |
|-----|------|------|
| `tab.code` | 代码 | Code |
| `tab.smali` | Smali | Smali |
| `tab.bytecode` | 字节码 | Bytecode |
| `tab.simple` | 简化 | Simple |
| `split.view` | 分屏视图 | Split View |
| `toolbar.open` | 打开文件 | Open File |
| `toolbar.reload` | 重新加载 | Reload |
| `toolbar.save` | 导出当前文件 | Export Current File |
| `toolbar.localizer` | 定位文件 | Locate in Tree |
| `toolbar.search` | 搜索 | Search |
| `toolbar.reload.disabled` | 没有可重新加载的代码标签 | No code tab to reload |
| `toolbar.localizer.failed` | 无法在文件树中定位当前文件 | Failed to locate current file in tree |
| `context.gotoDeclaration` | 跳转声明 | Go to Declaration |
| `context.inheritanceGraph` | 查看继承图 | View Inheritance Graph |
| `context.methodGraph` | 查看方法图 | View Method Graph |
| `context.addComment` | 添加注释 | Add Comment |
| `context.updateComment` | 更新注释 | Update Comment |
| `comment.title` | 添加注释 | Add Comment |
| `comment.updateTitle` | 更新注释 | Update Comment |
| `comment.line` | 行号 | Line |
| `comment.member` | 成员 | Member |
| `comment.style` | 样式 | Style |
| `comment.save` | 保存 | Save |
| `comment.delete` | 删除 | Delete |
| `comment.text` | 注释内容 | Comment Text |
| `graph.title` | 图形视图 | Graph View |
| `graph.renderFailed` | 图形渲染失败 | Failed to render graph |
| `graph.tooLarge` | 图过大，已限制显示范围 | Graph is too large; display range was limited |

---

## 测试策略

### 单元测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `SmaliContentPanelTest` | ASM Textifier 输出非空、输出格式正确 |
| `SimpleContentPanelTest` | 去注释逻辑、去空行逻辑、字符串字面量中的 `//` 和 `/*` 不被误删 |
| `CodeSyncHelperTest` | 方法行号映射构建、方法查找 |
| `GraphServiceTest` | DOT 生成格式、DOT 转义、重载方法调用解析 |
| `CommentManagerTest` | 保存、加载、删除、路径安全编码、schemaVersion 兼容 |
| `CommentExportDecoratorTest` | hash 命中、成员签名降级、行号降级、注释转义 |
| `BytecodeContentPanelTest` | Java 25 major 69 class 解析、ASM 失败时 fallback 文本 |

### 集成测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `CodeViewPanelTest` | Tab 切换、Split View 切换、内容懒加载 |
| `MainToolBarTest` | 按钮禁用条件、reload/localizer/search 回调 |
| `CodeAreaContextMenuTest` | 菜单启用状态、右键移动 caret、无 metadata 降级 |

### 手工验收用例

- 打开大 JAR，首次双击 class 后 Code 标签优先出现，不等待 Smali/Bytecode/Simple 生成。
- 快速连续切换多个 class，旧后台任务不会覆盖当前标签内容，窗口不无响应。
- 点击 Smali/Bytecode/Simple 首次加载有明确 loading 状态；再次切换使用缓存。
- 开启 Split view 后左右各自可滚动，拖动分割条不影响底部标签栏布局。
- 光标在源码方法内移动时，右侧 Bytecode 至少能跳到对应方法附近；无映射时不弹错误。
- 工具栏 localizer 能展开左侧树并选中当前 class；当前文件不在树中时只显示状态提示。
- 右键跳转声明能复用当前 Ctrl+Click 的类跳转能力；找不到声明时菜单禁用或状态栏提示。
- 继承图和方法图能打开、缩放、拖拽；大图有保护提示；DOT 特殊字符不会导致渲染失败。
- 添加注释后关闭重开项目仍可加载；导出当前文件和批量导出均保留注释。
- 新窗口/标签拖拽/搜索/缩放/行号开关等既有功能无回归。

---

## 风险与防护

| 风险 | 影响 | 防护 |
|------|------|------|
| ASM 解析高版本 class 失败 | Bytecode/Graph/Inheritance 视图不可用 | 统一 tryParse/fallback，不在 FX 线程抛异常 |
| 大 class 生成视图过慢 | UI 卡顿或内存上涨 | 后台任务、输出大小阈值、取消令牌、懒加载 |
| 注释行号漂移 | 导出注释贴错位置 | `sourceHash + optionsHash + memberSignature + line` 多级定位 |
| Split view 节点复用 | JavaFX parent 冲突 | 左右独立 `CodeContentDeck`，共享只读数据不共享 Node |
| 图形布局不是完整 Graphviz | 复杂 DOT 语法无法完全复刻外部 Graphviz 输出 | 只承诺渲染应用生成的 DOT 子集，保留 DOT 源码切换和外部工具复制路径 |
| 右键菜单 token 命中不准 | 跳转/注释目标错误 | 优先 metadata，失败禁用或提示，不做猜测式强跳转 |

---

## 设计决策记录

1. **不迁移 Fallback 标签页**: jadx 中 Fallback 是 DEX→Java 失败时的降级显示，FxDecompiler 的 Java 字节码反编译无此场景。
2. **Smali 标签保留但语义降级**: FxDecompiler 当前不支持 Android DEX，首版 Smali 标签显示 JVM/ASM 指令视图；真实 smali 等未来 DEX 输入支持后再实现。
3. **Simple 为只读源码简化视图**: 不依赖反编译引擎的 SIMPLE 模式（各引擎支持不一），首版用轻量词法状态机去注释和压缩空行，不做破坏性泛型正则化。
4. **Split View 不做指令级同步**: Java class 文件的 LineNumberTable 粒度不如 Android debug info，放弃指令级同步，做方法级同步。
5. **图形渲染用 JavaFX 原生 DOT 子集渲染器**: 避免引入 AWT/Swing、WebView、WASM 和大体积 JS 资源；当前只承诺渲染应用自身生成的继承图/方法图 DOT。
6. **注释存储独立 JSON**: 不嵌入反编译缓存（会被过期清理），不做完整项目文件（太重），独立 JSON 文件便于跨会话保留和手动编辑。
7. **不新增独立 Toolbar Actions 接口**: 工具栏回调复用现有 `MainMenuBar.Actions`，只补 `refreshCurrentTab()` 和 `locateCurrentFileInTree()`。
