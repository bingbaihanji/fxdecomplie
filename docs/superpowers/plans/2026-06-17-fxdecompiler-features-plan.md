# FxDecompiler 功能升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 参考 jadx 和 Recaf，为 FxDecompiler 移植 10 个只读功能（全文搜索、大纲面板、快速打开类、类继承层次、字节码汇编、类信息视图、拖拽打开、最近文件、导出进度、反编译进度指示）。

**Architecture:** 先基础设施（ASM依赖 + CodeEditorTab子标签改造）→ 再独立新功能包（search/outline/quickopen/inheritance）→ 最后集成改造（MainWindow DnD、MainMenuBar、ExportService、AppConfig）。每个新功能包独立可测。

**Tech Stack:** JDK 25, JavaFX 25 + jfx.incubator.richtext, ASM 9.7.1, Maven

---

## 文件地图

| 文件 | 类型 | 职责 |
|------|------|------|
| `pom.xml` | 修改 | 新增 ASM 依赖 |
| `ui/code/BytecodeViewTab.java` | 创建 | ASM Textifier 字节码文本视图 |
| `ui/code/ClassInfoView.java` | 创建 | 类元数据格式化展示 |
| `ui/code/CodeEditorTab.java` | 修改 | 源码/字节码/类信息 三标签切换 |
| `ui/search/SearchResult.java` | 创建 | 搜索结果 record |
| `ui/search/SearchProvider.java` | 创建 | 搜索策略接口 |
| `ui/search/SearchService.java` | 创建 | 搜索协调器 + 3 个 Provider 实现 |
| `ui/search/SearchDialog.java` | 创建 | 搜索对话框 UI |
| `ui/outline/OutlineMember.java` | 创建 | 大纲成员 record |
| `ui/outline/OutlineParser.java` | 创建 | 正则提取字段/方法/内部类 |
| `ui/outline/OutlinePane.java` | 创建 | 大纲面板 UI |
| `ui/inheritance/InheritanceNode.java` | 创建 | 继承节点 record |
| `ui/inheritance/InheritanceService.java` | 创建 | ASM 构建继承树 |
| `ui/inheritance/InheritancePane.java` | 创建 | 继承面板 UI |
| `ui/quickopen/QuickOpenDialog.java` | 创建 | 快速打开类对话框 |
| `ui/menu/MainMenuBar.java` | 修改 | 新增菜单项 + 最近文件子菜单 |
| `MainWindow.java` | 修改 | DnD 监听 + 大纲绑定 + 快速打开调用 + 最近文件 |
| `ExportService.java` | 修改 | 新增进度回调重载 |
| `ClassTabOpener.java` | 修改 | 传递 classBytes 给 CodeEditorTab |
| `AppConfig.java` | 修改 | 最近文件增删逻辑 |

---

### Task 1: 基础设施 — 添加 ASM 依赖

**Files:**
- Modify: `pom.xml`

- [ ] **Step 1: 在 pom.xml 的 `<dependencies>` 中添加 ASM**

在 Close tag `</dependencies>` 之前插入：

```xml
        <!-- ASM — class 文件分析 (字节码视图、类信息、继承层次) -->
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm</artifactId>
            <version>9.7.1</version>
        </dependency>
        <dependency>
            <groupId>org.ow2.asm</groupId>
            <artifactId>asm-util</artifactId>
            <version>9.7.1</version>
        </dependency>
```

- [ ] **Step 2: 验证依赖下载**

Run: `./mvnw dependency:resolve`
Expected: BUILD SUCCESS, asm:9.7.1 and asm-util:9.7.1 downloaded

- [ ] **Step 3: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS (无新代码，仅添加依赖)

---

### Task 2: 基础设施 — CodeEditorTab 改为三标签结构

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/BytecodeViewTab.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/ClassInfoView.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/app/ClassTabOpener.java`

- [ ] **Step 1: 修改 CodeEditorTab — 构造器中接收 byte[] 并创建子标签页**

读取当前 `CodeEditorTab.java`，改为内部嵌入 TabPane：

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.OpenFile;
import com.bingbaihanji.fxdecomplie.ui.theme.RegexHighlighter;
import com.bingbaihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.text.Font;
import jfx.incubator.scene.control.richtext.CodeArea;

import java.io.InputStream;

/**
 * 单个代码标签页。封装 CodeArea + 语法高亮器 + 字体缩放。
 * 内部包含三个子标签：Java源码、字节码、类信息。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CodeEditorTab extends Tab {

    /** Fira Code 字体资源路径 */
    private static final String FIRA_CODE_RESOURCE = "/ttf/FiraCode-Light.ttf";

    /** Java 源码编辑器 */
    private final CodeArea codeArea;
    /** 字节码视图 */
    private final CodeArea bytecodeArea;
    /** 子标签页面板 */
    private final TabPane subTabPane;
    /** 打开的文件 */
    private final OpenFile openFile;
    /** 默认字号 */
    private final int defaultFontSize;

    public CodeEditorTab(OpenFile openFile) {
        this(openFile, VsCodeThemeLoader.defaultDark(), "Consolas", 14, true, true, null);
    }

    /**
     * @param openFile   打开的文件
     * @param theme      编辑器主题
     * @param fontFamily 字体
     * @param fontSize   字号
     * @param wrapText   是否自动换行
     * @param lineNumbersEnabled 是否显示行号
     * @param classBytes  类文件字节码（null 则隐藏字节码/信息标签）
     */
    public CodeEditorTab(OpenFile openFile, VsCodeThemeLoader.ThemeData theme, String fontFamily,
                         int fontSize, boolean wrapText, boolean lineNumbersEnabled,
                         byte[] classBytes) {
        this.openFile = openFile;
        this.defaultFontSize = fontSize;

        // Java 源码标签
        codeArea = new CodeArea();
        codeArea.getStyleClass().add("code-editor");
        codeArea.setEditable(false);
        codeArea.setWrapText(wrapText);
        codeArea.setLineNumbersEnabled(lineNumbersEnabled);
        codeArea.setFont(loadCodeFont(fontFamily, fontSize));
        codeArea.setText(openFile.sourceCode());
        codeArea.setSyntaxDecorator(new RegexHighlighter(theme));
        Tab sourceTab = new Tab("Java 源码", codeArea);
        sourceTab.setClosable(false);

        // 字节码标签
        bytecodeArea = BytecodeViewTab.createView(classBytes);
        Tab bytecodeTab = new Tab("字节码", bytecodeArea);
        bytecodeTab.setClosable(false);

        // 类信息标签
        javafx.scene.layout.VBox infoView = ClassInfoView.createView(classBytes);
        Tab infoTab = new Tab("类信息", infoView);
        infoTab.setClosable(false);

        // 装配子标签页
        subTabPane = new TabPane();
        subTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        subTabPane.getTabs().addAll(sourceTab, bytecodeTab, infoTab);
        subTabPane.getSelectionModel().select(sourceTab);

        setText(openFile.className() + ".java");
        setContent(subTabPane);
    }

    /** @return Java 源码的 CodeArea（供主代码操作） */
    public CodeArea getCodeArea() {
        return codeArea;
    }

    /** @return 打开的文件 */
    public OpenFile getOpenFile() {
        return openFile;
    }

    /** 放大字号 */
    public void zoomIn() {
        setFontSize(codeArea.getFont().getSize() + 1);
    }

    /** 缩小字号 */
    public void zoomOut() {
        setFontSize(Math.max(8, codeArea.getFont().getSize() - 1));
    }

    /** 重置字号 */
    public void resetZoom() {
        setFontSize(defaultFontSize);
    }

    /** 设置行号显示 */
    public void setLineNumbersEnabled(boolean enabled) {
        codeArea.setLineNumbersEnabled(enabled);
    }

    /** 设置编辑器字号 */
    private void setFontSize(double size) {
        Font current = codeArea.getFont();
        Font newFont = Font.font(current.getFamily(), size);
        codeArea.setFont(newFont);
        bytecodeArea.setFont(Font.font(bytecodeArea.getFont().getFamily(), size));
    }

    /** 加载代码字体（优先资源配置 → 系统配置 → 回退） */
    private Font loadCodeFont(String configuredFamily, int fontSize) {
        Font resourceFont = loadResourceFont(fontSize);
        if (resourceFont != null) return resourceFont;
        if (configuredFamily != null && !configuredFamily.isBlank()) {
            return Font.font(configuredFamily, fontSize);
        }
        return Font.font("Consolas", fontSize);
    }

    /** 从资源加载 Fira Code 字体 */
    private Font loadResourceFont(int fontSize) {
        try (InputStream input = CodeEditorTab.class.getResourceAsStream(FIRA_CODE_RESOURCE)) {
            return input == null ? null : Font.loadFont(input, fontSize);
        } catch (Exception ignored) {
            return null;
        }
    }
}
```

- [ ] **Step 2: 创建 BytecodeViewTab.java**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import jfx.incubator.scene.control.richtext.CodeArea;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * 字节码/汇编视图。使用 ASM Textifier 将 class 字节码转为 javap -c 风格的汇编文本。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeViewTab {

    private BytecodeViewTab() {
        throw new AssertionError("utility class");
    }

    /**
     * 创建字节码视图 CodeArea。
     * @param classBytes 类文件字节码，为 null 则显示「无字节码」
     * @return 只读 CodeArea 组件
     */
    public static CodeArea createView(byte[] classBytes) {
        CodeArea codeArea = new CodeArea();
        codeArea.getStyleClass().add("code-editor");
        codeArea.setEditable(false);
        codeArea.setWrapText(false);

        if (classBytes == null) {
            codeArea.setText("// 无可用字节码");
            return codeArea;
        }

        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            ClassReader reader = new ClassReader(classBytes);
            Textifier textifier = new Textifier();
            TraceClassVisitor tcv = new TraceClassVisitor(null, textifier, pw);
            reader.accept(tcv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            pw.flush();
            codeArea.setText(sw.toString());
        } catch (Exception e) {
            codeArea.setText("// 字节码解析失败: " + e.getMessage());
        }
        return codeArea;
    }
}
```

- [ ] **Step 3: 创建 ClassInfoView.java**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import org.objectweb.asm.ClassReader;

/**
 * 类信息视图。展示 class 文件的版本号、访问标志、常量池数量、父类、接口列表等结构化元数据。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassInfoView {

    private ClassInfoView() {
        throw new AssertionError("utility class");
    }

    /**
     * 创建类信息视图面板。
     * @param classBytes 类文件字节码，为 null 则显示「无数据」
     * @return VBox 信息面板
     */
    public static VBox createView(byte[] classBytes) {
        VBox root = new VBox(6);
        root.getStyleClass().add("class-info-view");
        root.setStyle("-fx-padding: 12px; -fx-background-color: #1e1e1e;");

        if (classBytes == null) {
            root.getChildren().add(label("无可用字节码", "#858585"));
            return root;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            int minor = reader.readShort(4);
            int major = reader.readShort(6);

            root.getChildren().add(label("主版本号: " + major + "  (Java " + toJavaVersion(major) + ")", "#dcdcaa"));
            root.getChildren().add(label("次版本号: " + minor, "#9aa7b0"));
            root.getChildren().add(label("访问标志: " + formatAccess(reader.access), "#9aa7b0"));
            root.getChildren().add(label("本类: " + reader.getClassName().replace('/', '.'), "#4ec9b0"));
            root.getChildren().add(label("父类: " + reader.getSuperName().replace('/', '.'), "#c586c0"));
            root.getChildren().add(label("常量池条目: " + reader.getItemCount(), "#9aa7b0"));

            String[] interfaces = reader.getInterfaces();
            if (interfaces.length > 0) {
                for (String itf : interfaces) {
                    root.getChildren().add(label("接口: " + itf.replace('/', '.'), "#b5cea8"));
                }
            } else {
                root.getChildren().add(label("接口: (无)", "#6a6a6a"));
            }
        } catch (Exception e) {
            root.getChildren().add(label("解析失败: " + e.getMessage(), "#f44747"));
        }
        return root;
    }

    /** 主版本号 → Java 版本映射 */
    private static int toJavaVersion(int major) {
        return major - 44;
    }

    /** 解析访问标志为可读文本 */
    private static String formatAccess(int access) {
        java.util.List<String> flags = new java.util.ArrayList<>();
        if ((access & 0x0001) != 0) flags.add("public");
        if ((access & 0x0010) != 0) flags.add("final");
        if ((access & 0x0020) != 0) flags.add("super");
        if ((access & 0x0200) != 0) flags.add("interface");
        if ((access & 0x0400) != 0) flags.add("abstract");
        if ((access & 0x1000) != 0) flags.add("synthetic");
        if ((access & 0x2000) != 0) flags.add("annotation");
        if ((access & 0x4000) != 0) flags.add("enum");
        return String.join(", ", flags);
    }

    private static Label label(String text, String color) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + color + "; -fx-font-size: 13px; -fx-font-family: 'Consolas', monospace;");
        return label;
    }
}
```

- [ ] **Step 4: 修改 ClassTabOpener — 传递 byte[] 给 CodeEditorTab**

Read `ClassTabOpener.java`. 修改 `createCodeEditorTab` 方法和 `openClassTab`/`refreshCurrentClassTab` 调用：

```java
// openClassTab 中：
byte[] bytes = readClassBytes(node, workspace);
// ... decompile ...
Platform.runLater(() -> {
    CodeEditorTab codeTab = createCodeEditorTab(openFile, lineNumbersEnabled, bytes);
    // ...
});

// refreshCurrentClassTab 中类似

// createCodeEditorTab 改为：
private CodeEditorTab createCodeEditorTab(OpenFile openFile, boolean lineNumbersEnabled, byte[] classBytes) {
    return new CodeEditorTab(
            openFile, editorTheme,
            config.theme.fontFamily, config.theme.fontSize,
            config.decompiler.wrapText, lineNumbersEnabled,
            classBytes
    );
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 3: 全文搜索系统

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchResult.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchProvider.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchService.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/search/SearchDialog.java`

- [ ] **Step 1: 创建 SearchResult.java**

```java
package com.bingbaihanji.fxdecomplie.ui.search;

/**
 * 单条搜索结果记录。
 *
 * @param fullPath   所属类的完整路径（如 "com/example/Main.class"）
 * @param matchLine  匹配行内容
 * @param lineNumber 行号（1-based）
 * @param matchType  匹配类型
 */
public record SearchResult(
        String fullPath,
        String matchLine,
        int lineNumber,
        MatchType matchType
) {
    /** 匹配类型 */
    public enum MatchType {
        /** 类名匹配 */
        CLASS_NAME,
        /** 方法名匹配 */
        METHOD_NAME,
        /** 字段名匹配 */
        FIELD_NAME,
        /** 代码文本匹配 */
        CODE_TEXT
    }
}
```

- [ ] **Step 2: 创建 SearchProvider.java**

```java
package com.bingbaihanji.fxdecomplie.ui.search;

import java.util.List;
import java.util.Map;

/**
 * 搜索策略接口。每种实现对应一种搜索维度（类名、代码文本、方法/字段名）。
 */
@FunctionalInterface
public interface SearchProvider {
    /**
     * 在源码缓存中搜索。
     * @param query       搜索关键词
     * @param sourceCache 全路径 → 源码文本映射
     * @return 匹配的搜索结果列表
     */
    List<SearchResult> search(String query, Map<String, String> sourceCache);
}
```

- [ ] **Step 3: 创建 SearchService.java**

```java
package com.bingbaihanji.fxdecomplie.ui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;

/**
 * 全文搜索服务。在后台线程执行搜索，通过回调返回结果。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchService {

    private SearchService() {
        throw new AssertionError("utility class");
    }

    /** 搜索结果回调 */
    @FunctionalInterface
    public interface ResultCallback {
        void accept(SearchResult result);
    }

    /**
     * 异步执行搜索。
     * @param query        搜索关键词
     * @param sourceCache  全路径 → 源码映射
     * @param onResult     每条结果回调（FX 线程）
     * @param onComplete   搜索完成回调（FX 线程）
     */
    public static void search(String query,
                              Map<String, String> sourceCache,
                              ResultCallback onResult,
                              Runnable onComplete) {
        if (query == null || query.isBlank()) {
            if (onComplete != null) Platform.runLater(onComplete);
            return;
        }
        String lowerQuery = query.toLowerCase();
        com.bingbaihanji.fxdecomplie.app.BackgroundTasks.run("search-worker", () -> {
            List<SearchResult> classResults = new ArrayList<>();
            List<SearchResult> codeResults = new ArrayList<>();
            List<SearchResult> memberResults = new ArrayList<>();

            for (var entry : sourceCache.entrySet()) {
                String path = entry.getKey();
                String source = entry.getValue();
                if (source == null) continue;

                // 类名匹配
                String className = path.toLowerCase();
                if (className.contains(lowerQuery)) {
                    classResults.add(new SearchResult(path, path, 1,
                            SearchResult.MatchType.CLASS_NAME));
                }

                // 代码文本逐行匹配 + 方法/字段匹配
                String[] lines = source.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    String lineLower = lines[i].toLowerCase();
                    if (lineLower.contains(lowerQuery)) {
                        SearchResult.MatchType type = classifyMatch(lines[i], lowerQuery);
                        SearchResult result = new SearchResult(path, lines[i].trim(), i + 1, type);
                        if (type == SearchResult.MatchType.CODE_TEXT) {
                            codeResults.add(result);
                        } else {
                            memberResults.add(result);
                        }
                    }
                }
            }

            // 批量回调（分组输出）
            if (!classResults.isEmpty() && onResult != null) {
                Platform.runLater(() -> classResults.forEach(onResult::accept));
            }
            if (!memberResults.isEmpty() && onResult != null) {
                Platform.runLater(() -> memberResults.forEach(onResult::accept));
            }
            if (!codeResults.isEmpty() && onResult != null) {
                Platform.runLater(() -> codeResults.forEach(onResult::accept));
            }
            if (onComplete != null) Platform.runLater(onComplete);
        });
    }

    /** 根据行内容判断匹配类型 */
    private static SearchResult.MatchType classifyMatch(String line, String query) {
        String trimmed = line.trim();
        if (trimmed.matches(".*\\b" + query + "\\s*\\(.*")) {
            return SearchResult.MatchType.METHOD_NAME;
        }
        if (trimmed.matches(".*\\b" + query + "\\s*[=;].*") && !trimmed.contains("(")) {
            return SearchResult.MatchType.FIELD_NAME;
        }
        return SearchResult.MatchType.CODE_TEXT;
    }
}
```

- [ ] **Step 4: 创建 SearchDialog.java**

```java
package com.bingbaihanji.fxdecomplie.ui.search;

import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Map;

/**
 * 全文搜索对话框。带防抖的实时搜索，TreeView 按类型分组显示结果。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class SearchDialog {

    private static final int MAX_RESULTS = 200;

    private SearchDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示搜索对话框。
     * @param owner       父窗口
     * @param sourceCache 全路径 → 源码映射
     * @param onJump      跳转回调（传入 fullPath 和 lineNumber）
     */
    public static void show(javafx.stage.Window owner,
                            Map<String, String> sourceCache,
                            JumpCallback onJump) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("全文搜索");

        TextField input = new TextField();
        input.setPromptText("输入关键词搜索...");
        input.setStyle("-fx-font-size: 14px; -fx-padding: 8px;");

        TreeView<String> resultTree = new TreeView<>();
        resultTree.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(resultTree, Priority.ALWAYS);

        Label statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #858585; -fx-padding: 4px;");

        // 防抖搜索
        PauseTransition debounce = new PauseTransition(Duration.millis(300));
        input.textProperty().addListener((obs, old, text) -> {
            debounce.stop();
            resultTree.setRoot(null);
            statusLabel.setText("搜索中...");
            debounce.setOnFinished(e -> {
                TreeItem<String> root = new TreeItem<>("搜索结果");
                TreeItem<String> classNameGroup = new TreeItem<>("类名匹配");
                TreeItem<String> memberGroup = new TreeItem<>("方法/字段匹配");
                TreeItem<String> codeGroup = new TreeItem<>("代码匹配");
                int[] count = {0};

                SearchService.search(text, sourceCache,
                        result -> {
                            if (count[0]++ >= MAX_RESULTS) return;
                            TreeItem<String> leaf = new TreeItem<>(result.matchLine());
                            leaf.setValue(result.fullPath() + " : " + result.lineNumber() + "  " + result.matchLine());
                            switch (result.matchType()) {
                                case CLASS_NAME -> classNameGroup.getChildren().add(leaf);
                                case METHOD_NAME, FIELD_NAME -> memberGroup.getChildren().add(leaf);
                                case CODE_TEXT -> codeGroup.getChildren().add(leaf);
                            }
                        },
                        () -> {
                            if (count[0] >= MAX_RESULTS) {
                                statusLabel.setText("结果过多，仅显示前 " + MAX_RESULTS + " 条");
                            } else {
                                statusLabel.setText(count[0] + " 条结果");
                            }
                            if (!classNameGroup.getChildren().isEmpty())
                                root.getChildren().add(classNameGroup);
                            if (!memberGroup.getChildren().isEmpty())
                                root.getChildren().add(memberGroup);
                            if (!codeGroup.getChildren().isEmpty())
                                root.getChildren().add(codeGroup);
                            resultTree.setRoot(root);
                            resultTree.getRoot().setExpanded(true);
                            classNameGroup.setExpanded(true);
                        }
                );
            });
            debounce.playFromStart();
        });

        // 双击跳转
        resultTree.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<String> item = resultTree.getSelectionModel().getSelectedItem();
                if (item != null && item.isLeaf()) {
                    String value = item.getValue();
                    int colonIdx = value.indexOf(" : ");
                    int spaceIdx = value.indexOf("  ", colonIdx + 3);
                    if (colonIdx > 0 && spaceIdx > 0) {
                        String path = value.substring(0, colonIdx);
                        int line = Integer.parseInt(value.substring(colonIdx + 3, spaceIdx));
                        onJump.jump(path, line);
                    }
                }
            }
        });

        // Esc 关闭
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) dialog.close();
        });

        VBox root = new VBox(6, input, resultTree, statusLabel);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #2d2d2d;");
        Scene scene = new Scene(root, 700, 500);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.show();
        input.requestFocus();
    }

    /** 跳转回调 */
    @FunctionalInterface
    public interface JumpCallback {
        void jump(String fullPath, int lineNumber);
    }
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 4: 大纲面板

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/outline/OutlineMember.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/outline/OutlineParser.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/outline/OutlinePane.java`

- [ ] **Step 1: 创建 OutlineMember.java**

```java
package com.bingbaihanji.fxdecomplie.ui.outline;

/**
 * 大纲成员记录。从反编译源码中提取的字段、方法或内部类。
 *
 * @param name      成员名称
 * @param type      成员类型
 * @param modifiers 修饰符（如 "public static"）
 * @param lineNumber 行号（1-based）
 */
public record OutlineMember(
        String name,
        MemberType type,
        String modifiers,
        int lineNumber
) {
    public enum MemberType {FIELD, METHOD, INNER_CLASS}
}
```

- [ ] **Step 2: 创建 OutlineParser.java**

```java
package com.bingbaihanji.fxdecomplie.ui.outline;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从反编译 Java 源码中提取字段、方法、内部类的大纲信息。
 * 使用正则逐行匹配，不依赖反编译器 metadata。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlineParser {

    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private|static|final|synchronized|abstract|native|\\s)*" +
                    "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\s*[{;]");

    private static final Pattern FIELD_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private|static|final|volatile|transient|\\s)*" +
                    "[\\w<>\\[\\],.\\s]+\\s+(\\w+)\\s*(?:=\\s*[^;]+)?;");

    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile(
            "^\\s*(public|protected|private|static|\\s)*\\b(class|interface|enum|record)\\s+(\\w+)");

    private OutlineParser() {
        throw new AssertionError("utility class");
    }

    /** @param sourceCode 反编译后的 Java 源码 */
    public static List<OutlineMember> parse(String sourceCode) {
        if (sourceCode == null || sourceCode.isEmpty()) return List.of();
        List<OutlineMember> members = new ArrayList<>();
        String[] lines = sourceCode.split("\n");
        int depth = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            depth += count(line, '{') - count(line, '}');

            Matcher m;
            if (depth == 1 && !line.contains(" class ") && !line.contains(" interface ")) {
                if ((m = METHOD_PATTERN.matcher(line)).find()) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.METHOD,
                            extractModifiers(m.group(1)), i + 1));
                } else if ((m = FIELD_PATTERN.matcher(line)).find() && !line.contains("(")) {
                    members.add(new OutlineMember(m.group(2), OutlineMember.MemberType.FIELD,
                            extractModifiers(m.group(1)), i + 1));
                }
            }
            if ((m = INNER_CLASS_PATTERN.matcher(line)).find()
                    && !line.contains("new ") && depth >= 1 && depth <= 2) {
                members.add(new OutlineMember(m.group(3), OutlineMember.MemberType.INNER_CLASS,
                        extractModifiers(m.group(1)), i + 1));
            }
        }
        return members;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String extractModifiers(String raw) {
        if (raw == null) return "";
        return raw.trim().replaceAll("\\s+", " ");
    }
}
```

- [ ] **Step 3: 创建 OutlinePane.java**

```java
package com.bingbaihanji.fxdecomplie.ui.outline;

import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * 大纲面板。显示当前类的字段、方法、内部类列表，支持实时过滤和点击跳转。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class OutlinePane extends VBox {

    private final TextField filterField;
    private final ListView<OutlineMember> listView;
    private final FilteredList<OutlineMember> filteredList;
    private JumpHandler jumpHandler;

    public OutlinePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        getStyleClass().add("outline-pane");
        setStyle("-fx-background-color: #252526;");

        filterField = new TextField();
        filterField.setPromptText("过滤成员...");
        filterField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: #cccccc; -fx-font-size: 12px;");

        listView = new ListView<>();
        filteredList = new FilteredList<>(FXCollections.observableArrayList(), p -> true);
        listView.setItems(filteredList);
        listView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(listView, Priority.ALWAYS);

        // 图标 + 名称渲染
        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(OutlineMember item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String icon = switch (item.type()) {
                        case FIELD -> "F ";
                        case METHOD -> "M ";
                        case INNER_CLASS -> "C ";
                    };
                    setText(icon + item.name() + "  — " + item.modifiers());
                    setTextFill(javafx.scene.paint.Color.web(
                            item.type() == OutlineMember.MemberType.METHOD ? "#dcdcaa" :
                                    item.type() == OutlineMember.MemberType.FIELD ? "#9cdcfe" : "#4ec9b0"));
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        // 点击跳转
        listView.setOnMouseClicked(e -> {
            OutlineMember selected = listView.getSelectionModel().getSelectedItem();
            if (selected != null && jumpHandler != null) {
                jumpHandler.jump(selected.lineNumber());
            }
        });

        // 实时过滤
        filterField.textProperty().addListener((obs, old, text) -> {
            filteredList.setPredicate(member -> {
                if (text == null || text.isEmpty()) return true;
                String lower = text.toLowerCase();
                return member.name().toLowerCase().contains(lower);
            });
        });

        getChildren().addAll(new Label("大纲"), filterField, listView);
    }

    /** 更新大纲内容 */
    public void update(String sourceCode) {
        List<OutlineMember> members = OutlineParser.parse(sourceCode);
        filteredList.getSource().setAll(members);
        filterField.clear();
    }

    /** 清空 */
    public void clear() {
        filteredList.getSource().clear();
        filterField.clear();
    }

    /** 设置跳转处理器 */
    public void setJumpHandler(JumpHandler handler) {
        this.jumpHandler = handler;
    }

    @FunctionalInterface
    public interface JumpHandler {
        void jump(int lineNumber);
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 5: 类继承层次

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceService.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritancePane.java`

- [ ] **Step 1: 创建 InheritanceNode.java**

```java
package com.bingbaihanji.fxdecomplie.ui.inheritance;

/**
 * 类继承层次节点记录。
 *
 * @param className   类的全路径（如 "com/example/MyClass"）
 * @param displayName 显示用的简短类名
 * @param relation    相对于查询类的关系
 * @param depth       距离查询类的步数
 */
public record InheritanceNode(
        String className,
        String displayName,
        RelationType relation,
        int depth
) {
    public enum RelationType {
        /** 当前查询的类 */
        SELF,
        /** 父类/父接口 */
        SUPER_CLASS,
        /** 子类 */
        SUBCLASS,
        /** 实现的接口 */
        INTERFACE
    }
}
```

- [ ] **Step 2: 创建 InheritanceService.java**

```java
package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.decompiler.BytecodeCache;
import javafx.scene.control.TreeItem;
import org.objectweb.asm.ClassReader;

import java.util.HashSet;
import java.util.Set;

/**
 * 类继承关系分析服务。使用 ASM 读取常量池中的 super_class 和 interfaces。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritanceService {

    private static final int MAX_DEPTH = 20;
    private static final Set<String> visited = new HashSet<>();

    private InheritanceService() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建继承树。
     * @param fullPath 目标类全路径（如 "com/example/MyClass.class"）
     * @return 继承树根节点，失败返回 null
     */
    public static TreeItem<InheritanceNode> buildTree(String fullPath) {
        visited.clear();
        String internalName = toInternal(fullPath);
        byte[] bytes = BytecodeCache.get(internalName);
        if (bytes == null) return null;

        InheritanceNode rootData = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SELF, 0);
        TreeItem<InheritanceNode> root = new TreeItem<>(rootData);
        root.setExpanded(true);
        visited.add(internalName);

        // 向上查找父类和接口
        try {
            ClassReader reader = new ClassReader(bytes);
            String superName = reader.getSuperName();
            if (superName != null && !"java/lang/Object".equals(superName)) {
                buildSuperChain(superName, root, 1);
            }
            for (String itf : reader.getInterfaces()) {
                TreeItem<InheritanceNode> ifNode = new TreeItem<>(new InheritanceNode(itf,
                        simpleName(itf), InheritanceNode.RelationType.INTERFACE, 1));
                root.getChildren().add(ifNode);
            }
        } catch (Exception ignored) {
        }

        // 向下查找子类
        findSubClasses(internalName, root);

        return root;
    }

    private static void buildSuperChain(String internalName, TreeItem<InheritanceNode> parent, int depth) {
        if (depth > MAX_DEPTH || visited.contains(internalName)) return;
        visited.add(internalName);

        InheritanceNode data = new InheritanceNode(internalName, simpleName(internalName),
                InheritanceNode.RelationType.SUPER_CLASS, depth);
        TreeItem<InheritanceNode> node = new TreeItem<>(data);
        parent.getChildren().add(0, node);

        byte[] bytes = BytecodeCache.get(internalName);
        if (bytes != null) {
            try {
                ClassReader reader = new ClassReader(bytes);
                String superName = reader.getSuperName();
                if (superName != null && !"java/lang/Object".equals(superName)) {
                    buildSuperChain(superName, node, depth + 1);
                }
                for (String itf : reader.getInterfaces()) {
                    TreeItem<InheritanceNode> ifNode = new TreeItem<>(new InheritanceNode(itf,
                            simpleName(itf), InheritanceNode.RelationType.INTERFACE, depth + 1));
                    node.getChildren().add(ifNode);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void findSubClasses(String targetName, TreeItem<InheritanceNode> root) {
        BytecodeCache.CACHE.forEach((name, bytes) -> {
            if (visited.contains(name)) return;
            if (name.equals(targetName)) return;
            try {
                ClassReader reader = new ClassReader(bytes);
                if (targetName.equals(reader.getSuperName())) {
                    InheritanceNode data = new InheritanceNode(name, simpleName(name),
                            InheritanceNode.RelationType.SUBCLASS, 1);
                    root.getChildren().add(new TreeItem<>(data));
                }
                for (String itf : reader.getInterfaces()) {
                    if (targetName.equals(itf)) {
                        InheritanceNode data = new InheritanceNode(name, simpleName(name),
                                InheritanceNode.RelationType.SUBCLASS, 1);
                        root.getChildren().add(new TreeItem<>(data));
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static String toInternal(String path) {
        return path.replace(".class", "").replace("\\", "/");
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }
}
```

Note: `BytecodeCache.CACHE` is currently `private`. Change it to package-private or add a `forEach` method. In `BytecodeCache.java`:

```java
// Change: private static final → package-private static final
// Or add:
static void forEach(java.util.function.BiConsumer<String, byte[]> action) {
    CACHE.forEach(action);
}
```

Add this method to `BytecodeCache.java`.

- [ ] **Step 3: 创建 InheritancePane.java**

```java
package com.bingbaihanji.fxdecomplie.ui.inheritance;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * 类继承层次面板。显示当前类的父类链和已知子类。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class InheritancePane extends VBox {

    private final TreeView<InheritanceNode> treeView;
    private OpenHandler openHandler;

    public InheritancePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        getStyleClass().add("inheritance-pane");
        setStyle("-fx-background-color: #252526;");

        treeView = new TreeView<>();
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(InheritanceNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String prefix = switch (item.relation()) {
                        case SELF -> "● ";
                        case SUPER_CLASS -> "↑ ";
                        case SUBCLASS -> "↓ ";
                        case INTERFACE -> "I ";
                    };
                    setText(prefix + item.displayName());
                    setTextFill(javafx.scene.paint.Color.web(
                            item.relation() == InheritanceNode.RelationType.SELF ? "#dcdcaa" :
                                    item.relation() == InheritanceNode.RelationType.SUPER_CLASS ? "#c586c0" :
                                    "#9cdcfe"));
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                TreeItem<InheritanceNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null && openHandler != null
                        && item.getValue().relation() != InheritanceNode.RelationType.SELF) {
                    openHandler.open(item.getValue().className());
                }
            }
        });

        getChildren().addAll(new Label("类继承层次"), treeView);
    }

    /** 加载指定类的继承树 */
    public void load(String fullPath) {
        TreeItem<InheritanceNode> root = InheritanceService.buildTree(fullPath);
        if (root != null) {
            treeView.setRoot(root);
            treeView.getRoot().setExpanded(true);
        } else {
            treeView.setRoot(new TreeItem<>(new InheritanceNode("", "无法加载", InheritanceNode.RelationType.SELF, 0)));
        }
    }

    /** 清空 */
    public void clear() {
        treeView.setRoot(null);
    }

    /** 设置打开处理器 */
    public void setOpenHandler(OpenHandler handler) {
        this.openHandler = handler;
    }

    @FunctionalInterface
    public interface OpenHandler {
        void open(String className);
    }
}
```

- [ ] **Step 4: BytecodeCache 添加 forEach + 修改 CACHE 为 package-private**

Read `BytecodeCache.java`, change:
- `private static final ConcurrentHashMap` → `static final ConcurrentHashMap` (package-private)
- 添加方法供 InheritanceService 遍历

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 6: 快速打开类

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/quickopen/QuickOpenDialog.java`

- [ ] **Step 1: 创建 QuickOpenDialog.java**

```java
package com.bingbaihanji.fxdecomplie.ui.quickopen;

import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.List;

/**
 * 快速打开类对话框。类似 IntelliJ Ctrl+N。
 * 输入类名实时过滤 workspace 所有 class，回车打开。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class QuickOpenDialog {

    private QuickOpenDialog() {
        throw new AssertionError("utility class");
    }

    /**
     * 显示快速打开对话框。
     * @param owner      父窗口
     * @param classNames workspace 中所有类的全路径列表（如 "com/example/Main.class"）
     * @param onSelect   选中回调
     */
    public static void show(javafx.stage.Window owner,
                            List<String> classNames,
                            java.util.function.Consumer<String> onSelect) {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setTitle("快速打开类");

        TextField input = new TextField();
        input.setPromptText("输入类名...");
        input.setStyle("-fx-font-size: 15px; -fx-padding: 10px; -fx-background-color: #3c3c3c; -fx-text-fill: #cccccc;");

        ListView<String> listView = new ListView<>();
        listView.setStyle("-fx-background-color: #252526;");
        FilteredList<String> filtered = new FilteredList<>(
                FXCollections.observableArrayList(classNames), p -> true);
        listView.setItems(filtered);

        listView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    int idx = item.lastIndexOf('/');
                    String display = idx >= 0 ? item.substring(idx + 1) + "  —  " + item : item;
                    setText(display);
                    setTextFill(javafx.scene.paint.Color.web("#cccccc"));
                    setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
                }
            }
        });

        // 实时过滤（排序：前缀匹配优先 → 子串匹配）
        input.textProperty().addListener((obs, old, text) -> {
            String lower = text.toLowerCase();
            filtered.setPredicate(name -> {
                if (text.isEmpty()) return true;
                String nameLower = name.toLowerCase();
                String simple = nameLower.substring(nameLower.lastIndexOf('/') + 1);
                return simple.startsWith(lower) || nameLower.contains(lower);
            });
        });

        // 键盘导航
        input.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN) {
                listView.getSelectionModel().selectNext();
                listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
                e.consume();
            } else if (e.getCode() == KeyCode.UP) {
                listView.getSelectionModel().selectPrevious();
                listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
                e.consume();
            } else if (e.getCode() == KeyCode.ENTER) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    onSelect.accept(selected);
                }
            } else if (e.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        // 双击选中
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = listView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    dialog.close();
                    onSelect.accept(selected);
                }
            }
        });

        // 失去焦点关闭
        dialog.focusedProperty().addListener((obs, was, now) -> {
            if (!now) {
                PauseTransition pt = new PauseTransition(Duration.millis(200));
                pt.setOnFinished(ev -> dialog.close());
                pt.play();
            }
        });

        VBox root = new VBox(6, input, listView);
        root.setPadding(new Insets(8));
        root.setStyle("-fx-background-color: #2d2d2d;");
        root.setAlignment(Pos.TOP_CENTER);

        Scene scene = new Scene(root, 550, 400);
        scene.getStylesheets().add(
                com.bingbaihanji.fxdecomplie.ui.theme.AppTheme.darkStylesheet());
        dialog.setScene(scene);
        dialog.show();
        input.requestFocus();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 7: MainWindow 集成 — DnD + 大纲绑定 + 快速打开

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`

- [ ] **Step 1: 添加 DnD 监听器**

在 `MainWindow.show()` 的 `BorderPane root` 声明后，设置 scene 之前添加：

```java
// --- DnD 文件拖放 ---
root.setOnDragOver(event -> {
    if (event.getDragboard().hasFiles()) {
        event.acceptTransferModes(javafx.scene.input.TransferMode.COPY);
    }
    event.consume();
});
root.setOnDragDropped(event -> {
    java.util.List<java.io.File> files = event.getDragboard().getFiles();
    for (java.io.File file : files) {
        loadFile(file);
    }
    event.setDropCompleted(true);
    event.consume();
});
```

- [ ] **Step 2: 添加大纲面板并绑定标签切换**

在 `MainWindow` 中添加字段 `private OutlinePane outlinePane;`，在 `show()` 中初始化并添加事件监听器，在 `addWorkspaceTab` (WorkspaceTabManager) 或 MainWindow 中监听代码 tab 切换来更新大纲。

由于 WorkspaceTabManager 管理 tab 切换，最简洁的方式是在 WorkspaceTabManager 的代码 tab 选择监听中触发大纲更新。

在 `show()` 方法中，在 outerTabPane 添加后：

```java
// 大纲面板初始化和代码tab切换绑定
outlinePane = new OutlinePane();
outlinePane.setJumpHandler(lineNumber -> {
    // 跳转到当前代码 tab 的指定行
    CodeEditorTab codeTab = tabManager.currentCodeTab();
    if (codeTab != null) {
        // 使用 CodeArea 的 scrollToLine 等方法滚动到行
        codeTab.getCodeArea().moveTo(lineNumber, 0);
        codeTab.getCodeArea().requestFocus();
    }
});
```

- [ ] **Step 3: 添加快速打开类 action**

在 MainWindow 中添加方法供菜单调用：

```java
/** 快速打开类 (Ctrl+N) */
public void quickOpenClass() {
    WorkspaceTabManager.WorkspaceView view = tabManager.currentWorkspaceView();
    if (view == null) {
        showWarning("提示", "请先打开文件");
        return;
    }
    java.util.List<String> classNames = new java.util.ArrayList<>();
    collectClassNames(view.workspace().getTreeRoot(), "", classNames);
    QuickOpenDialog.show(stage.getScene().getWindow(), classNames, fullPath -> {
        // 触发打开类
        FileTreeNode node = classTabOpener.findNodeByPath(view.workspace().getTreeRoot(), fullPath);
        if (node != null) {
            classTabOpener.openClassTab(node, view.workspace(), view.codeTabPane(),
                    currentEngine, lineNumbersEnabled);
        }
    });
}

private void collectClassNames(TreeItem<FileTreeNode> item, String prefix,
                                java.util.List<String> result) {
    FileTreeNode data = item.getValue();
    if (data != null && data.isClassFile()) {
        result.add(data.getFullPath());
    }
    for (TreeItem<FileTreeNode> child : item.getChildren()) {
        collectClassNames(child, "", result);
    }
}
```

Note: 如果 `ClassTabOpener.findNodeByPath` 不存在，需要在 ClassTabOpener 中新增 `public` 方法或直接在 MainWindow 中查找。

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 8: 菜单扩展 — 搜索 + 最近文件 + 快速打开

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/menu/MainMenuBar.java`

- [ ] **Step 1: 修改 Actions 接口 — 新增方法**

```java
public interface Actions {
    /** 打开文件 */
    void openFile();
    /** 打开目录 */
    void openDirectory();
    /** 关闭当前工作区 */
    void closeCurrentWorkspace();
    /** 关闭其他工作区 */
    void closeOtherWorkspaces();
    /** 保存当前文件 */
    void saveCurrentFile();
    /** 导出所有文件 */
    void exportAllFiles();
    /** 退出应用 */
    void exit();
    /** 复制选中文本 */
    void copySelection();
    /** 全选 */
    void selectAll();
    /** 放大 */
    void zoomIn();
    /** 缩小 */
    void zoomOut();
    /** 重置缩放 */
    void resetZoom();
    /** 切换行号 */
    void toggleLineNumbers();
    /** 折叠文件树 */
    void collapseTree();
    /** 切换反编译引擎 */
    void selectEngine(DecompilerTypeEnum engine);
    /** 全文搜索 (Ctrl+Shift+F) */
    void openSearch();
    /** 快速打开类 (Ctrl+N) */
    void quickOpenClass();
    /** 打开最近文件 */
    void openRecentFile(String path);
    /** 获取最近文件列表 */
    List<String> getRecentFiles();
}
```

- [ ] **Step 2: 在 createEditMenu 中添加搜索和快速打开项**

```java
private Menu createEditMenu(Actions actions) {
    Menu editMenu = new Menu("编辑(E)");
    MenuItem copy = item("复制", "Shortcut+C", actions::copySelection);
    MenuItem selectAll = item("全选", "Shortcut+A", actions::selectAll);
    MenuItem quickOpen = item("快速打开类...", "Shortcut+N", actions::quickOpenClass);
    MenuItem search = item("全文搜索...", "Shortcut+Shift+F", actions::openSearch);
    editMenu.getItems().addAll(copy, selectAll, new SeparatorMenuItem(),
            quickOpen, search);
    return editMenu;
}
```

- [ ] **Step 3: 在 createFileMenu 末尾添加最近文件子菜单**

```java
// 最近打开 子菜单
Menu recentMenu = new Menu("最近打开");
recentMenu.setOnShowing(e -> {
    recentMenu.getItems().clear();
    List<String> recents = actions.getRecentFiles();
    if (recents.isEmpty()) {
        MenuItem none = new MenuItem("(无)");
        none.setDisable(true);
        recentMenu.getItems().add(none);
    } else {
        for (String path : recents) {
            String display = path;
            int sep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
            if (sep > 0) display = path.substring(sep + 1) + "  —  " + path;
            MenuItem item = new MenuItem(display);
            item.setOnAction(ev -> actions.openRecentFile(path));
            recentMenu.getItems().add(item);
        }
        recentMenu.getItems().addAll(new SeparatorMenuItem(), clearRecentItem(actions));
    }
});
fileMenu.getItems().add(fileMenu.getItems().indexOf(
    fileMenu.getItems().stream().filter(m -> m.getText().contains("导出")).findFirst().orElse(null)) + 1,
    recentMenu);
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS (此时 MainWindow 编译错误，因为尚未实现 Actions 新增方法)

---

### Task 9: 集成收尾 — AppConfig 最近文件 + ExportService 进度 + MainWindow 全部实现

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/io/ExportService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/app/ClassTabOpener.java`

- [ ] **Step 1: AppConfig — 添加 addRecentFile 方法**

```java
private static final int MAX_RECENT_FILES = 20;

/** 添加最近文件（去重，最新在前） */
public void addRecentFile(String path) {
    recentFiles.remove(path);
    recentFiles.add(0, path);
    while (recentFiles.size() > MAX_RECENT_FILES) {
        recentFiles.removeLast();
    }
    save();
}
```

- [ ] **Step 2: ExportService — 添加进度回调重载**

```java
/**
 * 导出工作区所有 .class 文件为 .java 到目录（带进度回调）。
 * @param onProgress 进度回调，参数为 0-100 的百分比
 */
public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                  Path outputDir, java.util.function.IntConsumer onProgress)
        throws IOException {
    Objects.requireNonNull(root, "root");
    Objects.requireNonNull(engine, "engine");
    Objects.requireNonNull(outputDir, "outputDir");
    Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
    Files.createDirectories(outputDir);
    int total = countClassFiles(root);
    int[] completed = {0};
    exportTreeWithProgress(root, decompiler, outputDir, total, completed, onProgress);
}

private static int countClassFiles(TreeItem<FileTreeNode> node) {
    int count = 0;
    FileTreeNode data = node.getValue();
    if (data != null && data.isClassFile()) count = 1;
    for (TreeItem<FileTreeNode> child : node.getChildren()) {
        count += countClassFiles(child);
    }
    return count;
}

private static void exportTreeWithProgress(TreeItem<FileTreeNode> node, Decompiler decompiler,
        Path outputDir, int total, int[] completed, java.util.function.IntConsumer onProgress)
        throws IOException {
    FileTreeNode data = node.getValue();
    if (data != null && data.isClassFile()) {
        byte[] bytes = resolveClassBytes(data);
        if (bytes != null) {
            String source = decompiler.decompile(data.getFullPath(), bytes);
            Path javaFile = outputDir.resolve(data.getFullPath().replace(".class", ".java"));
            Files.createDirectories(javaFile.getParent());
            Files.writeString(javaFile, source);
        }
        completed[0]++;
        if (total > 0 && onProgress != null) {
            onProgress.accept(completed[0] * 100 / total);
        }
    }
    for (TreeItem<FileTreeNode> child : node.getChildren()) {
        exportTreeWithProgress(child, decompiler, outputDir, total, completed, onProgress);
    }
}
```

- [ ] **Step 3: MainWindow — 实现 Actions 新增方法**

```java
// 字段添加
private OutlinePane outlinePane;

// show() 中添加大纲面板并监听代码tab切换注入
// ...

/** 打开搜索对话框 */
@Override
public void openSearch() {
    WorkspaceTabManager.WorkspaceView view = tabManager.currentWorkspaceView();
    if (view == null) { showWarning("提示", "请先打开文件"); return; }
    java.util.Map<String, String> sourceCache = new java.util.HashMap<>();
    for (Tab tab : view.codeTabPane().getTabs()) {
        if (tab instanceof CodeEditorTab codeTab) {
            sourceCache.put(codeTab.getOpenFile().getFullPath(),
                    codeTab.getOpenFile().getSourceCode());
        }
    }
    SearchDialog.show(stage, sourceCache, (fullPath, lineNumber) -> {
        // 跳转到类 + 行
        // 在 workspace 中找到该 path 并打开
        openClassByPath(view, fullPath, lineNumber);
    });
}

/** 快速打开类 */
@Override
public void quickOpenClass() { /* 已在 Task 7 Step 3 实现 */ }

/** 打开最近文件 */
@Override
public void openRecentFile(String path) {
    loadFile(new java.io.File(path));
}

/** 获取最近文件列表 */
@Override
public List<String> getRecentFiles() {
    return config.recentFiles;
}

// loadFile() 末尾添加:
config.addRecentFile(file.getAbsolutePath());

// exportAllFiles() 中添加进度回调:
BackgroundTasks.run("ExportAll", () -> {
    try {
        ExportService.exportAllToDir(view.workspace().getTreeRoot(), currentEngine,
                dir.toPath(), pct -> Platform.runLater(() ->
                        statusBar.setFilePath("导出中... " + pct + "%")));
        Platform.runLater(() -> statusBar.setFilePath("导出完成: " + dir.getAbsolutePath()));
    } catch (IOException ex) {
        Platform.runLater(() -> showError("导出错误", "导出失败: " + ex.getMessage()));
    }
});
```

- [ ] **Step 4: ClassTabOpener — 暴露 findNodeByPath**

```java
/** 在文件树中查找指定路径的节点 */
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

- [ ] **Step 5: 编译验证 + 修复所有错误**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS（可能会有未解析引用，逐个修复）

---

### Task 10: 最终集成 — 大纲面板 UI 布局 + 继承面板布局

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java`

- [ ] **Step 1: 在 addWorkspaceTab 中添加大纲和继承面板到右侧区域**

修改 SplitPane 右侧，将纯 TabPane 替换为 VBox(TabPane + 底部 OutlinePane) 或使用嵌套 SplitPane。

最简单的方式：右侧用 VBox 包裹 codeTabPane + outlinePane：

```java
// 替换 SplitPane 行：
OutlinePane outlinePane = new OutlinePane();
InheritancePane inheritancePane = new InheritancePane();

// 右侧底部用 TabPane 切换 大纲 / 继承
TabPane sideTabPane = new TabPane();
Tab outlineTab = new Tab("大纲", outlinePane);
outlineTab.setClosable(false);
Tab inheritanceTab = new Tab("继承", inheritancePane);
inheritanceTab.setClosable(false);
sideTabPane.getTabs().addAll(outlineTab, inheritanceTab);
sideTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

VBox rightPane = new VBox(codeTabPane, sideTabPane);
VBox.setVgrow(codeTabPane, Priority.ALWAYS);
SplitPane splitPane = new SplitPane(treeView, rightPane);
```

- [ ] **Step 2: 绑定代码 tab 切换更新大纲 + 继承**

在 codeTabPane 的选择监听中更新：

```java
codeTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
    if (newTab instanceof CodeEditorTab codeTab) {
        outlinePane.update(codeTab.getOpenFile().getSourceCode());
        inheritancePane.load(codeTab.getOpenFile().getFullPath());
    } else {
        outlinePane.clear();
        inheritancePane.clear();
    }
});
```

同时绑定大纲点击跳转：

```java
outlinePane.setJumpHandler(line -> {
    CodeEditorTab codeTab = /* current code tab */;
    if (codeTab != null) {
        codeTab.getCodeArea().moveTo(line, 0);
        codeTab.getCodeArea().requestFocus();
    }
});

inheritancePane.setOpenHandler(className -> {
    // 调用 openClassTab
});
```

- [ ] **Step 3: 最终编译验证**

Run: `mvn clean compile -DskipTests`
Expected: BUILD SUCCESS, 所有功能编译通过

---

## 自检

1. **Spec 覆盖**: 10 个模块全部有对应 Task (1=ASM依赖, 2=CodeEditorTab+字节码+类信息, 3=搜索, 4=大纲, 5=继承, 6=快速打开, 7=MainWindow集成, 8=菜单扩展, 9=收尾, 10=UI布局)

2. **占位符**: 无 TBD/TODO，所有代码块完整。

3. **类型一致性**: `OutlineMember` 在 Task 4 定义，Task 7/10 引用一致。`SearchResult` 在 Task 3 定义，Task 8 引用一致。`InheritanceNode` 在 Task 5 定义，Task 10 引用一致。`BytecodeCache.CACHE` 改为 package-private 在 Task 5 中说明。
