# FxDecompiler 设计文档

> 基于 JavaFX + CodeArea (incubator richtext) 的 Java 反编译工具

## 一、项目定位

将基于 Swing 的 [code-resurrector](D:\bingbaihanji\code-resurrector) 用 JavaFX 重写，保留其三引擎反编译核心，升级 UI 为现代化的 JavaFX 界面。

### 参考项目核心资产（可直接复用/移植）
- **反编译引擎层**：`IDecompiler` 接口 + `DecompilerFactory` + 3 个实现（Procyon/CFR/Vineflower）
- **BytecodeCache**：全局 `ConcurrentHashMap<String, byte[]>` 缓存
- **文件发现逻辑**：JAR/ZIP 遍历、目录递归等

---

## 二、技术栈

| 层面 | 技术 | 版本 |
|------|------|------|
| JDK | JDK 25 full（自带 JavaFX） | 25.0.3 |
| UI | JavaFX + jfx.incubator.richtext | 25.0.3 |
| 代码编辑器 | CodeArea (incubator richtext) | — |
| 反编译引擎 | Procyon / CFR / Vineflower | 最新稳定版 |
| JSON 解析 | Gson 或 Jackson | 最新 |
| 构建 | Maven | — |

---

## 三、项目架构

### 包结构

```
com.bingbaihanji.fxdecomplie/
├── FxDecompilerApp.java            # 入口，Application.launch()
├── MainWindow.java                  # 主窗口：Stage + 布局装配
├── decompiler/
│   ├── IDecompiler.java             # 反编译引擎接口（从参考项目移植）
│   ├── DecompilerType.java          # 枚举: PROCYON / CFR / VINEFLOWER
│   ├── DecompilerFactory.java       # 工厂 + 缓存
│   ├── CfrDecompiler.java           # CFR 实现
│   ├── VineflowerDecompiler.java    # Vineflower 实现
│   ├── ProcyonDecompiler.java       # Procyon 实现
│   └── BytecodeCache.java           # 全局 byte[] 缓存（ConcurrentHashMap）
├── model/
│   ├── Workspace.java               # 单个 JAR/ZIP/目录 的工作空间
│   ├── FileTreeNode.java            # 文件树节点数据模型
│   └── OpenFile.java                # 已打开的 class 文件状态
├── ui/
│   ├── menu/
│   │   └── MainMenuBar.java         # 菜单栏
│   ├── tree/
│   │   ├── FileTreeView.java        # 左侧文件树（TreeView 封装）
│   │   └── FileTreeCell.java        # 文件树单元格渲染器（图标）
│   ├── code/
│   │   ├── CodeEditorTab.java       # 单个代码标签页（CodeArea 封装）
│   │   └── StatusBar.java           # 底部状态栏
│   └── theme/
│       ├── Highlighter.java         # 语法高亮接口
│       ├── RegexHighlighter.java    # 内置正则高亮器
│       └── VsCodeThemeLoader.java   # 加载 VS Code 主题 JSON → 高亮颜色映射
├── io/
│   ├── ClassLoader.java             # 类文件发现（JAR/ZIP/目录/单文件迭代）
│   ├── FileTreeBuilder.java         # 构建 TreeView 数据结构
│   └── ExportService.java           # 导出功能（单文件 / 全部 ZIP）
└── config/
    ├── AppConfig.java               # 应用配置（JSON 文件）
    └── ThemeConfig.java             # 高亮主题配置
```

---

## 四、UI 布局

```
┌─────────────────────────────────────────────────────────┐
│  MenuBar: 文件 | 编辑 | 视图 | 引擎 | 主题 | 帮助         │
├─────────────────────────────────────────────────────────┤
│  TabPane (外层: JAR/ZIP/目录 级别)                        │
│  ┌─ Tab: demo.jar ─────────────────────────────── [x] ┐ │
│  │  SplitPane (水平分割)                                │ │
│  │  ┌────────────┬──────────────────────────────────┐  │ │
│  │  │ TreeView   │  TabPane (内层: class 级别)       │  │ │
│  │  │            │  ┌─ Main.java ── [x] ──────────┐ │  │ │
│  │  │  [+]com   │  │  CodeArea (只读，语法高亮)     │ │  │ │
│  │  │   [+]demo │  │                              │ │  │ │
│  │  │     Main  │  └──────────────────────────────┘ │  │ │
│  │  └───────────┴──────────────────────────────────┘  │ │
│  └─────────────────────────────────────────────────────┘ │
├─────────────────────────────────────────────────────────┤
│  StatusBar:  com>demo>Main.class    UTF-8    254:12      │
└─────────────────────────────────────────────────────────┘
```

### 状态栏（HBox，3 段）
- 左：类路径（`com>b>c>Main.class`，点号替换为 `>`）
- 中：文件编码（`UTF-8`）
- 右：光标位置（`行:列`，从 1 开始）

### 菜单栏结构

```
文件(F)          编辑(E)          视图(V)          引擎(G)          主题(T)          帮助(H)
─────            ─────           ─────           ─────            ─────            ─────
打开文件...      复制            放大             Procyon          🔍 选择主题...    关于
打开目录...      全选            缩小             CFR              (主题列表)
关闭当前        (Ctrl+F)        重置缩放          Vineflower
关闭其他                        切换行号显示
保存当前文件                    文件树折叠
全部导出...
退出
```

---

## 五、文件加载流程

### 入口：MainWindow.openFile(File)

```
用户打开文件(JAR/ZIP/目录/单个class)
        │
        ▼
┌─── ClassLoader.discover() ───────────┐
│  判断输入类型:                         │
│  - .jar / .zip  → JarFile 遍历 entry  │
│  - 目录          → Files.walk() 递归   │
│  - .class 文件   → 直接读取            │
│  输出: List<ClassEntry>               │
└───────────────────────────────────────┘
        │
        ▼
┌─── FileTreeBuilder.build(entries) ────┐
│  按包路径/目录构建树结构                │
│  → TreeItem<FileTreeNode>             │
└───────────────────────────────────────┘
        │
        ▼
┌─── Workspace 创建 ────────────────────┐
│  - 持有 TreeItem 树根                 │
│  - 持有 BytecodeCache                 │
│  - 创建外层 Tab                       │
│  - 装配 TreeView + CodeArea TabPane   │
└───────────────────────────────────────┘
```

### 树节点点击 → 反编译

```
用户点击 .class 叶子节点
        │
        ▼
┌─── 读取 byte[] ───────────────────────┐
│  - JAR/ZIP: jarFile.getInputStream()   │
│  - 磁盘: Files.readAllBytes()          │
│  - 优先查 BytecodeCache                │
└───────────────────────────────────────┘
        │
        ▼
┌─── DecompilerFactory.decompile() ─────┐
│  调 IDecompiler.decompile(bytes)       │
│  返回 Java 源码 String                 │
└───────────────────────────────────────┘
        │
        ▼
┌─── 创建代码 Tab ───────────────────────┐
│  - setText(源码)                       │
│  - applySyntaxHighlighting()           │
└───────────────────────────────────────┘
```

### 文件类型处理

| 后缀 | 处理 |
|------|------|
| `.class` | 反编译 → 代码 Tab |
| `.java` | UTF-8 文本 → 代码 Tab（高亮） |
| `.xml`, `.json`, `.properties` 等 | UTF-8 文本 → 只读 Tab |
| 其他二进制 | 跳过或显示 "Binary" |

---

## 六、语法高亮系统

### 设计原则：渐进式

- **阶段 1（当前）**：内置正则引擎 + VS Code JSON 主题加载
- **阶段 2（未来）**：完整 TextMate 语法引擎（`.tmLanguage.json`）

### 架构

```
VS Code 主题文件 (JSON)
        │
        ▼
┌─── VsCodeThemeLoader ──────────────────┐
│  解析:                                  │
│  - colors (编辑器外观)                   │
│  - tokenColors (scope → color 映射)     │
│  输出:                                  │
│  - ColorScheme (编辑器底色/行号色等)     │
│  - Map<String, StyleAttributeMap>       │
│     "keyword" → {color, bold}           │
│     "string"  → {color}                 │
│     "comment" → {color, italic}         │
│     ...                                 │
└─────────────────────────────────────────┘
        │
        ▼
┌─── RegexHighlighter ────────────────────┐
│  实现 SyntaxDecorator 接口               │
│  正则分词 → 查颜色映射 → RichParagraph    │
└─────────────────────────────────────────┘
        │
        ▼
┌─── CodeArea ───────────────────────────┐
│  setSyntaxDecorator(regexHighlighter)    │
│  背景色 / 字体 由主题 colors 决定         │
└─────────────────────────────────────────┘
```

### VS Code 主题 JSON 格式（支持解析的部分）

```jsonc
{
  "name": "Dark+",
  "type": "dark",
  "colors": {
    "editor.background": "#1e1e1e",
    "editor.foreground": "#d4d4d4",
    "editorLineNumber.foreground": "#858585"
  },
  "tokenColors": [
    {
      "scope": "keyword",
      "settings": { "foreground": "#569CD6", "fontStyle": "bold" }
    },
    {
      "scope": ["string", "string.quoted"],
      "settings": { "foreground": "#CE9178" }
    },
    {
      "scope": "comment",
      "settings": { "foreground": "#6A9955", "fontStyle": "italic" }
    },
    {
      "scope": "constant.numeric",
      "settings": { "foreground": "#B5CEA8" }
    }
  ]
}
```

### 内置 Token → scope 映射表

| 内置 Token 类型 | 对应 scope |
|----------------|-----------|
| 关键字 | `keyword`, `keyword.control` |
| 字符串 | `string`, `string.quoted` |
| 注释 | `comment`, `comment.line`, `comment.block` |
| 注解 | `keyword.other.annotation`, `storage.type.annotation` |
| 数字 | `constant.numeric` |
| 默认 | `variable`, `source` |

---

## 七、导出功能

```
┌─── ExportService ──────────────────────┐
│                                         │
│  exportCurrentFile(OpenFile, path)       │
│    → 写入单个 .java 文件到磁盘            │
│                                         │
│  exportAllClasses(Workspace, outputDir)  │
│    → 遍历 workspace 所有 class 条目      │
│    → 逐个反编译 → 按包路径写入目录        │
│    → 保持结构: output/com/.../X.java    │
│                                         │
│  exportAsZip(Workspace, zipPath)        │
│    → 同 exportAllClasses，输出为 ZIP     │
│    → 带进度对话框                        │
│                                         │
└─────────────────────────────────────────┘
```

### 触发方式

| 方式 | 快捷键 |
|------|--------|
| 菜单 → 文件 → 保存当前文件 | `Ctrl+S` |
| 菜单 → 文件 → 全部导出到目录 | `Ctrl+Shift+E` |
| 菜单 → 文件 → 全部导出为 ZIP | — |

---

## 八、配置系统

```jsonc
// ~/.fxdecompiler/config.json
{
  "window": {
    "width": 1200,
    "height": 800,
    "x": 100,
    "y": 100,
    "maximized": false
  },
  "theme": {
    "path": "themes/dark-plus.json",
    "fontFamily": "Consolas",
    "fontSize": 14
  },
  "decompiler": {
    "defaultEngine": "VINEFLOWER",
    "lineNumbersEnabled": true,
    "wrapText": true
  },
  "recentFiles": [
    "/path/to/demo.jar"
  ]
}
```

### 配置加载优先级
1. 命令行参数
2. `config.json`
3. 硬编码默认值

---

## 九、核心接口定义

### IDecompiler（从参考项目移植）

```java
public interface IDecompiler {
    String decompile(String classFilePath, byte[] classBytes);
    String decompileType(String typeName, byte[] classBytes);
    DecompilerType getType();
    default String getName() { return getType().name(); }
    default void initialize() {}
    default void cleanup() {}
}
```

### SyntaxDecorator（JavaFX 已有）

```java
public interface SyntaxDecorator {
    RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex);
    void handleChange(CodeTextModel model, TextPos start, TextPos end,
                      int linesRemoved, int linesAdded, int charIndex);
}
```

---

## 十、实现阶段划分

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| 1 | 项目骨架：pom.xml、模块依赖、Application 入口 | P0 |
| 2 | 菜单栏 + 文件树 + 外层/内层 TabPane + 状态栏 布局 | P0 |
| 3 | 反编译引擎层移植（IDecompiler + 3 引擎 + BytecodeCache） | P0 |
| 4 | 文件加载（ClassLoader + FileTreeBuilder） | P0 |
| 5 | 代码区（CodeEditorTab + RegexHighlighter） | P0 |
| 6 | VS Code 主题加载器 | P1 |
| 7 | 导出功能（ExportService + 菜单/快捷键绑定） | P1 |
| 8 | 配置系统（config.json 读写） | P1 |
| 9 | 搜索功能、Ctrl+滚轮缩放、快捷键等 | P2 |
| 10 | 完整 TextMate 引擎（.tmLanguage.json） | P3 |
