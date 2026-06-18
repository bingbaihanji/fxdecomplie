# FxDecompiler 功能升级设计文档

> 参考 jadx 和 Recaf 项目，移植实用功能到 FxDecompiler。仅做反编译和导出，不做字节码修改。

## 整体架构变更

新增 4 个功能包，扩展现有菜单栏和 MainWindow：

```
com.bingbihanji.fxdecomplie/
├── decompiler/           # 已有
├── io/                   # 已有 (ExportService 扩展进度回调)
├── model/                # 已有 (新增 OutlineMember, SearchResult 等数据类)
├── config/               # 已有 (AppConfig.recentFiles 激活)
├── app/                  # 已有的 BackgroundTasks, ClassTabOpener
├── ui/
│   ├── menu/MainMenuBar  # 扩展: 搜索/快速打开菜单项, 最近文件子菜单
│   ├── code/StatusBar    # 扩展: 显示反编译进度
│   ├── search/           # 🔴 新增: 全文搜索 (4个类)
│   │   ├── SearchResult.java
│   │   ├── SearchProvider.java
│   │   ├── SearchService.java
│   │   └── SearchDialog.java
│   ├── outline/          # 🔴 新增: 字段/方法大纲面板 (3个类)
│   │   ├── OutlineMember.java
│   │   ├── OutlineParser.java
│   │   └── OutlinePane.java
│   ├── inheritance/      # 🔴 新增: 类继承层次 (3个类)
│   │   ├── InheritanceNode.java
│   │   ├── InheritanceService.java
│   │   └── InheritancePane.java
│   └── quickopen/        # 🔴 新增: 快速打开类 (1个类)
│       └── QuickOpenDialog.java
├── MainWindow.java       # 扩展: DnD 监听, 最近文件管理, 大纲绑定
└── pom.xml               # 新增依赖: org.ow2.asm:asm:9.7.1
```

### 设计模式

| 模式 | 应用位置 | 用途 |
|------|----------|------|
| 策略模式 | `SearchProvider` → 3 种实现 | 搜索维度独立扩展 |
| 模板方法 | `SearchDialog` 骨架 | 搜索 UI 复用 |
| 备忘录 | `QuickOpenDialog` 导航栈 | 暂存即将跳转位置 |
| 观察者 | `SearchService` → UI 回调 | 异步搜索结果推送 |
| 简单工厂 | `OutlineParser` 静态方法 | 从源码文本提取成员 |

### 新增依赖

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm</artifactId>
    <version>9.7.1</version>
</dependency>
```

---

## 模块 1：全文搜索

**目标:** 在当前 workspace 的所有反编译代码中搜索关键词，支持类名、方法名、代码文本三种维度。

### 文件

#### `ui/search/SearchResult.java` — 搜索结果记录

```java
package com.bingbihanji.fxdecomplie.ui.search;

/** 单条搜索结果 */
public record SearchResult(
    /** 所属类完整路径 */
    String fullPath,
    /** 匹配行内容 */
    String matchLine,
    /** 行号 (1-based) */
    int lineNumber,
    /** 匹配类型 */
    MatchType matchType
) {
    public enum MatchType { CLASS_NAME, METHOD_NAME, FIELD_NAME, CODE_TEXT }
}
```

#### `ui/search/SearchProvider.java` — 搜索提供者接口

```java
package com.bingbihanji.fxdecomplie.ui.search;

import java.util.List;

/** 搜索策略接口。每种子类实现一种搜索维度 */
@FunctionalInterface
public interface SearchProvider {
    List<SearchResult> search(String query, java.util.Map<String, String> sourceCache);
}
```

三个实现（均作为 `SearchService` 的私有静态内部类）：
- `ClassNameSearchProvider` — 对 sourceCache 的 key（全路径）做前缀/子串匹配
- `CodeTextSearchProvider` — 对源码逐行搜索
- `MethodFieldSearchProvider` — 用正则 `\b(query)\s*\(` 或 `\b(query)\s*[=;]` 匹配

#### `ui/search/SearchService.java` — 搜索协调器

```java
package com.bingbihanji.fxdecomplie.ui.search;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;

/**
 * 全文搜索服务。在后台线程池执行搜索，通过 Platform.runLater 返回结果。
 */
public final class SearchService {

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "search-worker");
        t.setDaemon(true);
        return t;
    });

    private SearchService() { throw new AssertionError("utility class"); }

    /**
     * 异步执行搜索。结果通过回调返回。
     * @param query    搜索关键词
     * @param sourceCache  全路径 → 源码映射
     * @param onResult     每条结果回调（在 FX 线程）
     * @param onComplete   搜索完成回调（在 FX 线程）
     */
    public static void search(String query,
                               Map<String, String> sourceCache,
                               ResultCallback onResult,
                               Runnable onComplete) {
        if (query == null || query.isBlank()) {
            if (onComplete != null) Platform.runLater(onComplete);
            return;
        }
        POOL.execute(() -> {
            List<SearchProvider> providers = List.of(
                new ClassNameSearchProvider(),
                new CodeTextSearchProvider(),
                new MethodFieldSearchProvider()
            );
            for (SearchProvider provider : providers) {
                List<SearchResult> results = provider.search(query, sourceCache);
                if (!results.isEmpty() && onResult != null) {
                    Platform.runLater(() -> results.forEach(onResult::accept));
                }
            }
            if (onComplete != null) Platform.runLater(onComplete);
        });
    }

    @FunctionalInterface
    public interface ResultCallback {
        void accept(SearchResult result);
    }

    // --- 内部 Provider 实现 ---
    private static class ClassNameSearchProvider implements SearchProvider { ... }
    private static class CodeTextSearchProvider implements SearchProvider { ... }
    private static class MethodFieldSearchProvider implements SearchProvider { ... }
}
```

#### `ui/search/SearchDialog.java` — 搜索对话框

JavaFX Stage: TextField（带防抖 300ms）+ TreeView（按类型分组：类名匹配/方法字段匹配/代码匹配）+ 关闭按钮。

- 防抖: `PauseTransition(Duration.millis(300))`
- 双击结果行 → 打开对应类 + 滚动到行号
- 结果限制 200 条，超出提示"结果过多，请缩小搜索范围"

---

## 模块 2：拖拽打开

**目标:** 支持从操作系统拖拽 JAR/ZIP/目录/class 文件到窗口直接打开。

### 实现

在 `MainWindow.show()` 中为根 BorderPane 注册 DnD 事件：

```java
root.setOnDragOver(event -> {
    if (event.getDragboard().hasFiles()) {
        event.acceptTransferModes(TransferMode.COPY);
    }
    event.consume();
});

root.setOnDragDropped(event -> {
    List<File> files = event.getDragboard().getFiles();
    for (File file : files) {
        loadFile(file);
    }
    event.setDropCompleted(true);
    event.consume();
});
```

无需新建类。`loadFile(File)` 已支持自动检测文件类型。

---

## 模块 3：大纲面板

**目标:** 在代码编辑区下方显示当前类的字段、方法、内部类列表，支持过滤和点击跳转。

### 策略

从反编译后的 Java 源码中用正则提取结构信息，不依赖反编译器 metadata。

### 文件

#### `ui/outline/OutlineMember.java`

```java
package com.bingbihanji.fxdecomplie.ui.outline;

/** 大纲成员 */
public record OutlineMember(
    String name,        // 方法名/字段名/内部类名
    MemberType type,    // FIELD / METHOD / INNER_CLASS
    String modifiers,   // "public static" / "private" 等
    int lineNumber      // 1-based 行号
) {
    public enum MemberType { FIELD, METHOD, INNER_CLASS }
}
```

#### `ui/outline/OutlineParser.java`

```java
package com.bingbihanji.fxdecomplie.ui.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从反编译源码中解析字段、方法、内部类。
 * 使用正则逐行匹配，不依赖反编译器 metadata。
 */
public final class OutlineParser {

    // 方法声明: 修饰符* 返回类型 方法名 (参数)
    private static final Pattern METHOD_PATTERN = Pattern.compile(
        "^\\s*(public|protected|private|static|final|synchronized|abstract|native|\\s)*" +
        "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*[{;]"
    );

    // 字段声明: 修饰符* 类型 名称 [= 初始值];
    private static final Pattern FIELD_PATTERN = Pattern.compile(
        "^\\s*(public|protected|private|static|final|volatile|transient|\\s)*" +
        "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*(?:=\\s*[^;]+)?;"
    );

    // 内部类/接口声明
    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
        "^\\s*(public|protected|private|static|\\s)*(class|interface|enum|record)\\s+(\\w+)"
    );

    private OutlineParser() { throw new AssertionError("utility class"); }

    public static List<OutlineMember> parse(String sourceCode) {
        List<OutlineMember> members = new ArrayList<>();
        String[] lines = sourceCode.split("\\n");
        int classDepth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            classDepth += countChar(line, '{') - countChar(line, '}');

            // 只在类体顶层提取成员（跳过内部类内部的方法）
            Matcher m;
            if (classDepth == 1) {
                if ((m = METHOD_PATTERN.matcher(line)).find()
                        && !line.contains(" class ") && !line.contains(" interface ")) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.METHOD,
                            extractModifiers(m.group(1)), i + 1));
                } else if ((m = FIELD_PATTERN.matcher(line)).find()
                        && !line.contains("(")) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.FIELD,
                            extractModifiers(m.group(1)), i + 1));
                }
            }
            if ((m = INNER_CLASS_PATTERN.matcher(line)).find()
                    && !line.contains("new ") && classDepth >= 1 && classDepth <= 2) {
                members.add(new OutlineMember(m.group(3), OutlineMember.MemberType.INNER_CLASS,
                        extractModifiers(m.group(1)), i + 1));
            }
        }
        return members;
    }

    private static int countChar(String s, char c) { /* ... */ }
    private static String extractModifiers(String s) { /* ... */ }
}
```

#### `ui/outline/OutlinePane.java`

VBox 组件：顶部 TextField 过滤器 + ListView\<OutlineMember\> + 点击跳转到代码行。

- 用图标符号区分: M(方法) / F(字段) / C(内部类)
- 过滤支持前缀/子串匹配

### 集成

`MainWindow` 绑定：监听代码 tab 切换事件 → 提取当前源码 → `OutlineParser.parse()` → `OutlinePane.update()`。

可在 SplitPane 中将代码区+大纲面板放在一个 VBox 中。

---

## 模块 4：最近文件

**目标:** 菜单栏显示最近打开的文件列表，支持快速重新打开。

### 变更

`AppConfig.recentFiles` 已有字段，直接激活：

```java
// AppConfig
/** 最近文件列表 (最多20条, 去重, 最新的在前) */
public List<String> recentFiles = new ArrayList<>();
private static final int MAX_RECENT_FILES = 20;

public void addRecentFile(String path) {
    recentFiles.remove(path);
    recentFiles.add(0, path);
    while (recentFiles.size() > MAX_RECENT_FILES) {
        recentFiles.removeLast();
    }
}
```

`MainMenuBar` 添加 "最近打开" 子菜单项，动态从 `AppConfig.recentFiles` 读取。

`MainWindow.loadFile()` 成功后调用 `config.addRecentFile(path)` (Platform.runLater 中)。

---

## 模块 5：导出进度

**目标:** 批量导出时在状态栏显示实时进度。

### 变更

`ExportService` 新增回调重载：

```java
public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                   Path outputDir, java.util.function.IntConsumer onProgress)
        throws IOException { ... }
```

`MainWindow.exportAllFiles()` 在状态栏显示：

```java
statusBar.setFilePath("导出中... 0%");
// ...
BackgroundTasks.run("ExportAll", () -> {
    ExportService.exportAllToDir(root, engine, dir.toPath(), pct ->
        Platform.runLater(() -> statusBar.setFilePath("导出中... " + pct + "%"))
    );
    Platform.runLater(() -> statusBar.setFilePath("导出完成: " + dir.getAbsolutePath()));
});
```

---

## 模块 6：反编译进度指示

**目标:** 打开 class 文件进行反编译时，在状态栏显示进度反馈。

### 变更

仅修改 `ClassTabOpener.openClassTab()`：反编译前设置状态 "正在反编译..."，遇到较长反编译时（超过 1 秒）显示持续反馈。

```java
statusBar.setFilePath("正在反编译: " + node.getFullPath() + "...");
// ... decompile ...
Platform.runLater(() -> statusBar.setFilePath(formatClassPath(node.getFullPath())));
```

纯状态栏文案变化，无需新建类。

---

## 模块 7：类继承层次

**目标:** 侧边面板显示当前类的父类链和已知子类。

### 文件

#### `ui/inheritance/InheritanceNode.java`

```java
package com.bingbihanji.fxdecomplie.ui.inheritance;

public record InheritanceNode(
    String className,     // 全路径
    String displayName,   // 简短类名
    RelationType type,    // SUPER_CLASS / SUBCLASS / INTERFACE
    int depth             // 相对当前类的深度
) {
    public enum RelationType { SUPER_CLASS, SUBCLASS, INTERFACE }
}
```

#### `ui/inheritance/InheritanceService.java`

```java
package com.bingbihanji.fxdecomplie.ui.inheritance;

import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import org.objectweb.asm.ClassReader;

/**
 * 类继承关系分析服务。使用 ASM 读取常量池中的 super_class 和 interfaces。
 */
public final class InheritanceService {

    private InheritanceService() { throw new AssertionError("utility class"); }

    /**
     * 构建继承树。
     * @param fullPath 目标类全路径（如 "com/example/MyClass.class"）
     * @return 树根节点
     */
    public static javafx.scene.control.TreeItem<InheritanceNode> buildTree(String fullPath) {
        String internalName = fullPath.replace(".class", "").replace("\\", "/");
        InheritanceNode rootData = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, 0);
        javafx.scene.control.TreeItem<InheritanceNode> root =
                new javafx.scene.control.TreeItem<>(rootData);
        root.setExpanded(true);

        // 向上: 查找父类和接口
        findSuperClasses(internalName, root, 1);
        // 向下: 在 BytecodeCache 中查找子类
        findSubClasses(internalName, root, 1);

        return root;
    }

    private static void findSuperClasses(String internalName,
            javafx.scene.control.TreeItem<InheritanceNode> parent, int depth) { ... }
    private static void findSubClasses(String internalName,
            javafx.scene.control.TreeItem<InheritanceNode> parent, int depth) { ... }
    private static String simpleName(String fullName) { ... }
}
```

#### `ui/inheritance/InheritancePane.java`

侧边 VBox 面板：标题 "类继承层次" + TreeView\<InheritanceNode\> + 刷新按钮。

- 父类显示在上方（带 ↑ 图标），子类显示在下方（带 ↓ 图标）
- 点击继承树中的类 → 在代码编辑区打开该类

---

## 模块 8：快速打开类

**目标:** Ctrl+N 弹出浮层，输入类名实时过滤，回车打开。

### 文件

#### `ui/quickopen/QuickOpenDialog.java`

```java
package com.bingbihanji.fxdecomplie.ui.quickopen;

/**
 * 快速打开类对话框。类似 IntelliJ Ctrl+N。
 * 输入类名实时过滤 workspace 所有 class，回车打开。
 */
public final class QuickOpenDialog {

    private QuickOpenDialog() { throw new AssertionError("utility class"); }

    /**
     * 显示快速打开对话框。
     * @param owner     父窗口
     * @param classNames workspace 中所有类的全路径列表
     * @param onSelect  选中回调（传入选中的全路径）
     */
    public static void show(javafx.stage.Window owner,
                            List<String> classNames,
                            java.util.function.Consumer<String> onSelect) {
        javafx.stage.Stage dialog = new javafx.stage.Stage();
        dialog.initOwner(owner);
        dialog.initModality(javafx.stage.Modality.NONE);
        dialog.initStyle(javafx.stage.StageStyle.UNDECORATED);
        dialog.setTitle("快速打开类");

        javafx.scene.control.TextField input = new javafx.scene.control.TextField();
        input.setPromptText("输入类名...");
        // ... 防抖过滤, ListView 结果列表, ↑↓ Enter Esc 键盘导航 ...

        dialog.show();
    }
}
```

### 集成

- `MainMenuBar` 编辑菜单 "查找..." (`Ctrl+N`) → 调用 `QuickOpenDialog.show()`
- 从 `WorkspaceTabManager` 收集当前 workspace 全量 class 名
- 选中后调用 `MainWindow` 的类打开逻辑

---

## 模块 9：字节码/汇编视图

**目标:** 在类标签页中提供 JVM 字节码指令的文本视图，类似 `javap -c`。

### 策略

使用 ASM `Textifier` + `TraceClassVisitor` 将字节码转换为标准汇编文本。ASM 依赖已通过模块 7 引入。

### 输出格式

```
// class version 52.0 (Java 8)
public class com.example.MyClass {

  public com.example.MyClass();
    Code:
       0: aload_0
       1: invokespecial #8  // Method java/lang/Object."<init>":()V
       4: return

  public void doSomething();
    Code:
       0: getstatic     #15  // Field java/lang/System.out:...
       3: ldc           #17  // String Hello
       5: invokevirtual #23  // Method java/io/PrintStream.println:...
       8: return
}
```

### 文件

#### `ui/code/BytecodeViewTab.java`

```java
package com.bingbihanji.fxdecomplie.ui.code;

import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/** 字节码视图。将 .class 字节码转为标准汇编文本显示。 */
public final class BytecodeViewTab {

    private BytecodeViewTab() { throw new AssertionError("utility class"); }

    /** 将 class 字节码转为汇编文本并设置到 CodeArea */
    public static CodeArea createView(byte[] classBytes) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            ClassReader reader = new ClassReader(classBytes);
            Textifier textifier = new Textifier();
            TraceClassVisitor tcv = new TraceClassVisitor(null, textifier, pw);
            reader.accept(tcv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        CodeArea codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(false);
        codeArea.setText(sw.toString());
        codeArea.getStyleClass().add("code-editor");
        return codeArea;
    }
}
```

### 集成

在 `CodeEditorTab` 中新增子标签页 `TabPane`，内部包含 3 个标签：

```
CodeEditorTab
  └── TabPane (子标签栏)
       ├── Tab "Java 源码"  → CodeArea (现有)
       ├── Tab "字节码"     → BytecodeViewTab (新增)
       └── Tab "类信息"     → ClassInfoView (新增，见模块10)
```

---

## 模块 10：类信息视图

**目标:** 展示 class 文件结构化元数据：版本号、访问标志、常量池、接口列表等。

### 使用 ASM ClassReader 读取:

- `ClassReader.readShort(offset)` → 主/次版本号
- `ClassReader.access` → 访问标志 (public/abstract/final/...)
- `ClassReader.readConst(index, buf)` → 常量池逐项
- `ClassReader.interfaces` → 接口列表
- 字段/方法名称列表通过 `ClassReader.items` 获取

### 文件

#### `ui/code/ClassInfoView.java`

```java
package com.bingbihanji.fxdecomplie.ui.code;

import javafx.scene.control.ListView;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.objectweb.asm.ClassReader;
import java.util.*;

/**
 * 类信息视图。展示 class 文件的版本号、访问标志、常量池等结构化元数据。
 */
public final class ClassInfoView {

    private ClassInfoView() { throw new AssertionError("utility class"); }

    public static VBox createView(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        VBox root = new VBox(8);
        root.getStyleClass().add("class-info-view");

        // 版本号
        int minor = reader.readShort(4);
        int major = reader.readShort(6);
        root.getChildren().add(new Label("主版本号: " + major + "  (Java " + toJavaVersion(major) + ")"));
        root.getChildren().add(new Label("次版本号: " + minor));

        // 访问标志
        root.getChildren().add(new Label("访问标志: " + formatAccess(reader.access)));

        // 常量池大小
        root.getChildren().add(new Label("常量池条目: " + reader.getItemCount()));

        // 接口列表
        String[] interfaces = reader.getInterfaces();
        if (interfaces.length > 0) {
            root.getChildren().add(new Label("接口: " + String.join(", ", interfaces)));
        }

        // 父类
        root.getChildren().add(new Label("父类: " + reader.getSuperName()));

        return root;
    }

    private static int toJavaVersion(int major) {
        return major - 44;  // Java 8 = 52 → 52-44=8
    }

    private static String formatAccess(int access) { /* 解析为 public/static/final 等 */ }
}
```

---

## 变更影响汇总

| 指标 | 数值 |
|------|------|
| 新增包 | 4 (`search`, `outline`, `inheritance`, `quickopen`) |
| 新增类 | 13 |
| 修改现有类 | 7 (`MainWindow`, `MainMenuBar`, `ExportService`, `AppConfig`, `ClassTabOpener`, `CodeEditorTab`, `pom.xml`) |
| 新增 Maven 依赖 | 1 (`org.ow2.asm:asm:9.7.1`) |
| 预估新增代码 | ~1600 行 |
| 交互模式 | 菜单项扩展 + 快捷键 + DnD + 代码标签页子标签切换 |

## 不涉及

- 不做字节码修改、重编译
- 不做重命名、反混淆
- 不做插件系统、停靠面板
- 不做跨 workspace 搜索
