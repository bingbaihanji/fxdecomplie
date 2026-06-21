# jadx-gui 功能迁移设计文档

## 概述

将 jadx-gui 中以下功能迁移到 FxDecompiler，基于 JavaFX 技术栈重新实现：

1. **代码区底部标签页** — Code / Smali / Bytecode / Simple 四标签切换查看
2. **Split View** — Code + Bytecode 左右并排视图，方法级光标同步
3. **主工具栏** — 菜单栏下方工具按钮（打开/重新加载/导出/定位/搜索）
4. **代码区右键菜单** — 跳转声明 / 查看继承图 / 查看方法图 / 添加注释
5. **注释系统** — 添加、持久化、导出保留注释

## 架构总览

### 新建文件清单（~14 个 Java 文件 + 1 个 JS 资源）

| # | 文件 | 包 | 职责 |
|---|------|-----|------|
| 1 | `CodeViewPanel.java` | `ui.code` | 代码视图顶层容器，管理底部 4 标签切换 + Split View 分屏 |
| 2 | `AbstractCodeContentPanel.java` | `ui.code` | 代码内容面板抽象基类，统一生命周期（懒加载/刷新/销毁） |
| 3 | `SourceContentPanel.java` | `ui.code` | 源码面板（Code tab），复用现有 RegexHighlighter + CodeArea |
| 4 | `SmaliContentPanel.java` | `ui.code` | Smali 面板（ASM InstructionPrinter 生成 javap 风格助记符） |
| 5 | `BytecodeContentPanel.java` | `ui.code` | 字节码面板（ASM Textifier，从 BytecodeViewTab 抽取） |
| 6 | `SimpleContentPanel.java` | `ui.code` | 简化代码面板（正则去注释、简化泛型） |
| 7 | `CodeSyncHelper.java` | `ui.code` | Split View 同步工具（LineNumberTable 方法级映射） |
| 8 | `CodeAreaContextMenu.java` | `ui.code` | 右键上下文菜单（4 项） |
| 9 | `MainToolBar.java` | `ui.toolbar` | 工具栏组件 |
| 10 | `GraphDialog.java` | `ui.graph` | 图形弹窗（WebView + Viz.js 渲染 DOT → SVG） |
| 11 | `GraphService.java` | `ui.graph` | DOT 字符串生成工具类 |
| 12 | `CommentDialog.java` | `ui.comment` | 注释输入弹窗 |
| 13 | `CommentManager.java` | `service` | 注释 JSON 文件读写 |
| 14 | `CommentData.java` | `model` | 注释数据模型 record |

### 资源文件

| # | 文件 | 说明 |
|---|------|------|
| 15 | `src/main/resources/web/viz.js` | Graphviz JS 移植（约 2MB，用于图形渲染） |
| 16 | `src/main/resources/web/graph-template.html` | WebView 加载的 HTML 模板，引用 viz.js |

### 修改文件清单（~8 个）

| # | 文件 | 改动内容 |
|---|------|---------|
| 17 | `CodeEditorTab.java` | 内部改为委托给 `CodeViewPanel`；保留 Tab 接口兼容性 |
| 18 | `MainWindow.java` | `topBars` 中添加 `MainToolBar`；暴露定位/刷新等回调 |
| 19 | `WorkspaceTabManager.java` | 创建 `CodeViewPanel` 替代内层 TabPane |
| 20 | `ClassTabOpener.java` | `applyDecompiledContent()` 适配 `CodeViewPanel` |
| 21 | `module-info.java` | 添加 `javafx.web` 模块 |
| 22 | `theme/dark.css` | 底部标签栏、工具栏、Split View 样式 |
| 23 | `language_zh_CN.properties` | 新增 ~15 个 i18n key |
| 24 | `language_en.properties` | 新增 ~15 个 i18n key |

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
  - `TabPane bottomTabs` — 底部标签栏 (`Side.BOTTOM`)，4 个标签
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

**`SmaliContentPanel`** — Smali 面板
- 使用 ASM `Textifier` + `TraceClassVisitor`（`ClassReader.SKIP_DEBUG | SKIP_FRAMES`）
- 生成类似 `javap -c` 的字节码助记符输出
- 内容为只读 `CodeArea`，使用简单 `SyntaxDecorator`

**`BytecodeContentPanel`** — 字节码面板
- 复用现有 `BytecodeViewTab.createView()` 逻辑
- ASM `Textifier`（`ClassReader.SKIP_DEBUG | SKIP_FRAMES | SKIP_CODE`）
- 只读 `CodeArea`，`TextFileDecorator`

**`SimpleContentPanel`** — 简化代码面板
- 对反编译源码文本做后处理：
  - 移除行注释（`//...`）和块注释（`/*...*/`）
  - 移除空行
  - 简化泛型签名（正则替换去除 `<...>` 中的复杂嵌套）
- 只读 `CodeArea`

### Tab 懒加载策略

```
Code tab:         构造时立即加载（同步，无额外开销）
Smali tab:        首次选中时异步加载（BackgroundTasks）
Bytecode tab:     首次选中时异步加载
Simple tab:       首次选中时同步处理（源码后处理，轻量）
```

切换 tab 不触发重新反编译。Smali/Bytecode 使用已缓存的 `classBytes` 生成；Simple 使用已有的反编译源码文本。

### 底部标签栏样式

- `TabPane.side = BOTTOM`
- 标签高度紧凑（约 28px）
- 字体大小 12px
- 右侧 Split View checkbox 用 `HBox` 作为 tab 的 trailing graphic

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

- Split 激活时：底部标签控制**左侧**面板；右侧固定为 Bytecode
- Split 关闭时：恢复单面板，底部标签控制主视图
- 初始分割比例 0.5，用户可拖拽调整
- `SplitPane.setDividerPosition()` 保持分屏比例

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
1. 左侧 CodeArea 光标移动 → 提取当前行所在方法签名 → 在字节码文本中搜索该方法 → 右侧跳转
2. 右侧 Bytecode 光标移动 → 提取方法签名 → 在左侧源码中搜索 → 左侧跳转
3. 通过 `LineNumberTable` 属性构建方法→行号映射（ASM `LineNumberVisitor`）
4. 若精确映射失败（如行号表不可用），回退到方法签名文本搜索

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
| `/icon/tool/localizer.png` | `toolbar.localizer` | 定位文件 | `actions.selectInTree()` | 无当前 tab |
| `/icon/tool/search.png` | `toolbar.search` | 搜索 | `actions.openSearch()` | 无当前 tab |

- 按钮尺寸：图标 16×16，内边距 4px，间距 2px
- tooltip 通过 `I18nUtil.getString()` 国际化
- 编辑相关按钮（reload/save/localizer/search）需有当前 tab 才可用

### 集成点

`MainWindow` 中将 `MainToolBar` 添加到 `topBars`（VBox），位于 `AppHeaderBar` 下方：

```java
// MainWindow 构造函数中
toolbar = new MainToolBar(this); // this implements Actions
topBars.getChildren().add(toolbar);
```

需要在 `MainMenuBar.Actions` 接口中新增 `selectInTree()`:
```java
void selectInTree(); // 在文件树中定位当前类文件
```

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
- **构造参数**: `CodeArea`, `String currentClassName`, `WorkspaceIndex index`, `Consumer<String> onNavigate`, `Consumer<CommentData> onCommentSaved`
- **关键方法**:
  - `show(double screenX, double screenY)` — 显示菜单
  - `setupActions()` — 绑定各菜单项动作

### 各功能实现

#### 跳转声明

复用 `CodeLinkHandler` 的引用提取逻辑：
```java
// 从光标位置提取引用
CodeMetadata.Reference ref = extractReference(codeArea.getCaretPosition());
// 在索引中查找
PathNode target = workspaceIndex.findNodeByPath(ref.fullPath());
// 若找到则打开
if (target != null) onNavigate.accept(target.fullPath());
```

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
Map<String, Set<String>> calls = GraphService.parseMethodCalls(classBytes);
// 生成 DOT
String dot = GraphService.toMethodDOT(calls);
// 弹窗展示
GraphDialog.show("方法图 - " + className, dot);
```

#### 添加注释

```java
// 获取当前光标行号
int line = codeArea.getCaretPosition().getLineNumber();
// 弹窗输入
CommentDialog.show(line, (comment) -> {
    comment.setClassName(currentClassName);
    CommentManager.save(comment);
    onCommentSaved.accept(comment);
});
```

---

## 模块五：注释系统

### 数据模型

```java
/**
 * 注释数据记录
 *
 * @param className 类全限定路径（内部形式, 如 "com/example/Foo.class"）
 * @param line      行号（1-based）
 * @param text      注释文本
 * @param author    作者
 * @param time      创建时间（ISO-8601）
 */
public record CommentData(
    String className,
    int line,
    String text,
    String author,
    String time
) {}
```

### 存储方案

- **存储路径**: `<appDir>/fxdecomplie/comments/<类全路径>.json`
- **文件格式**:

```json
{
  "className": "com/example/Foo.class",
  "comments": [
    {"line": 12, "text": "核心方法", "author": "bingbaihanji", "time": "2026-06-21T10:30:00"},
    {"line": 25, "text": "TODO: 需要优化", "author": "bingbaihanji", "time": "2026-06-21T10:35:00"}
  ]
}
```

### 类设计

**`CommentManager`** — 工具类（`final class`，私有构造器）

```java
public final class CommentManager {
    private CommentManager() { throw new AssertionError(); }

    /** 保存注释 */
    public static void save(CommentData comment);

    /** 加载某个类的全部注释 */
    public static List<CommentData> load(String className);

    /** 删除某个注释（按行号+时间戳匹配） */
    public static boolean delete(String className, int line, String time);

    /** 获取注释存储根目录 */
    public static Path getCommentDir();
}
```

**`CommentDialog`** — `Dialog<String>` 子类

```java
public final class CommentDialog {
    private CommentDialog() { throw new AssertionError(); }

    /** 显示注释输入弹窗，返回用户输入的注释文本 */
    public static void show(Window owner, int line, String existingText, Consumer<CommentData> onSave);
}
```

- UI 内容：`TextArea`（多行输入）+ 显示行号标签
- 如果已有注释则预填充（"更新注释"模式）
- 保存按钮触发 `onSave` 回调

### 注释在导出中的处理

导出源码时（`ExportService`），遍历目标类的注释列表，按行号倒序插入到源码文本中：

```java
// 伪代码
String[] lines = sourceCode.split("\n", -1);
for (CommentData c : comments) {
    if (c.line() <= lines.length) {
        lines[c.line() - 1] += " // " + c.text();  // 行尾追加注释
    }
}
return String.join("\n", lines);
```

插入格式：`原始代码 // 注释文本`（行尾注释）

### 注释在代码区的显示

- 代码区行号旁显示小圆点指示器（通过自定义 `LineNumberGutter` 增强）
- 鼠标悬停圆点时显示注释内容的 tooltip
- 此功能为 Phase 2 增强，本次迁移仅实现存储+导出

---

## 模块六：图形弹窗（GraphDialog + GraphService）

### 依赖

- `viz.js`（Graphviz JS 移植，约 2MB）→ 放在 `src/main/resources/web/viz.js`
- JavaFX `WebView` 渲染 → 需要在 `module-info.java` 中添加 `requires javafx.web;`

### 类设计

**`GraphDialog`** — `Dialog<Void>` 子类

```java
/**
 * 图形可视化弹窗，使用 WebView + Viz.js 渲染 DOT 格式的图形。
 */
public class GraphDialog extends Dialog<Void> {
    private final WebView webView;

    /** @param title 弹窗标题 */
    /** @param dot   DOT 格式图形描述字符串 */
    public GraphDialog(Window owner, String title, String dot);

    /** 显示图形弹窗 */
    public static void show(Window owner, String title, String dot);
}
```

- 内部加载 `graph-template.html`
- HTML 中内联 `<script>` 加载 `viz.js` 并渲染 DOT → SVG
- WebView 支持：鼠标滚轮缩放（`Ctrl+Wheel`），拖拽平移
- 弹窗尺寸：800×600，可调整大小
- 窗口关闭时释放 WebView 资源

**`GraphService`** — 工具类

```java
public final class GraphService {
    private GraphService() { throw new AssertionError(); }

    /** 从 InheritanceNode 树生成 DOT（继承关系图） */
    public static String toInheritanceDOT(TreeItem<InheritanceNode> root);

    /** 从方法调用关系生成 DOT（方法关系图）*/
    public static String toMethodDOT(Map<String, Set<String>> methodCalls);

    /** 从 classBytes 解析类内方法调用关系 */
    public static Map<String, Set<String>> parseMethodCalls(byte[] classBytes);
}
```

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

- 使用 ASM `ClassVisitor` 扫描方法体的 `MethodInsnNode` 指令
- 仅显示类内部方法调用（`INVOKESPECIAL`/`INVOKEVIRTUAL`/`INVOKESTATIC` 且 owner 为本类）

---

## 数据流

### 反编译管道适配

```
用户点击文件树节点
  → ClassTabOpener.openClassTab()
    → 查找/创建 CodeViewPanel（新）替代原 CodeEditorTab
    → BackgroundTasks.run()
      → 读取 classBytes
      → decompileWithCache() — 三级缓存
      → Platform.runLater()
        → SourceContentPanel.setContent(sourceCode)  // Code tab 立即显示
        → CodeViewPanel 就绪
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
    └─ 添加注释 → CommentDialog.show() → CommentManager.save()
```

---

## module-info.java 变更

```diff
requires javafx.swing;
requires javafx.graphics;
+requires javafx.web;
```

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
| `context.gotoDeclaration` | 跳转声明 | Go to Declaration |
| `context.inheritanceGraph` | 查看继承图 | View Inheritance Graph |
| `context.methodGraph` | 查看方法图 | View Method Graph |
| `context.addComment` | 添加注释 | Add Comment |
| `comment.title` | 添加注释 | Add Comment |
| `comment.line` | 行号 | Line |
| `comment.save` | 保存 | Save |
| `comment.text` | 注释内容 | Comment Text |
| `graph.title` | 图形视图 | Graph View |

---

## 测试策略

### 单元测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `SmaliContentPanelTest` | ASM Textifier 输出非空、输出格式正确 |
| `SimpleContentPanelTest` | 去注释逻辑、去空行逻辑 |
| `CodeSyncHelperTest` | 方法行号映射构建、方法查找 |
| `GraphServiceTest` | DOT 生成格式、方法调用解析 |
| `CommentManagerTest` | 保存、加载、删除流程 |

### 集成测试

| 测试类 | 覆盖内容 |
|--------|---------|
| `CodeViewPanelTest` | Tab 切换、Split View 切换、内容懒加载 |
| `MainToolBarTest` | 按钮禁用条件 |

---

## 设计决策记录

1. **不迁移 Fallback 标签页**: jadx 中 Fallback 是 DEX→Java 失败时的降级显示，FxDecompiler 的 Java 字节码反编译无此场景。
2. **Smali 改为 javap 风格**: FxDecompiler 不支持 Android DEX，用 ASM InstructionPrinter 生成 Java 字节码助记符替代。
3. **Simple 为源码后处理**: 不依赖反编译引擎的 SIMPLE 模式（各引擎支持不一），直接在反编译结果上正则处理。
4. **Split View 不做指令级同步**: Java class 文件的 LineNumberTable 粒度不如 Android debug info，放弃指令级同步，做方法级同步。
5. **图形渲染用 WebView + Viz.js**: 避免引入 AWT/Swing 依赖（与 JavaFX 渲染冲突），Viz.js 在浏览器环境中成熟稳定。
6. **注释存储独立 JSON**: 不嵌入反编译缓存（会被过期清理），不做完整项目文件（太重），独立 JSON 文件便于跨会话保留和手动编辑。
7. **不新增 Actions 接口**: 工具栏回调复用现有 `MainMenuBar.Actions` 接口，只增加一个 `selectInTree()` 方法。
