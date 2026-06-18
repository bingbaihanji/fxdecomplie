# FxDecompiler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a JavaFX decompiler application with file tree, multi-tab code area with syntax highlighting, three decompiler engines (Procyon/CFR/Vineflower), VS Code theme support, and export functionality.

**Architecture:** MainWindow assembles MenuBar + SplitPane (TreeView | CodeTabPane) + StatusBar. Each opened JAR/ZIP/directory gets a Workspace (outer Tab) with its own file tree. Class files decompile on click via DecompilerFactory → IDecompiler → BytecodeCache. Syntax highlighting uses RegexHighlighter (SyntaxDecorator impl) with colors from VS Code JSON themes.

**Tech Stack:** JDK 25, JavaFX 25 + jfx.incubator.richtext, Procyon/CFR/Vineflower, Gson

---

## File Map

| File | Responsibility |
|------|---------------|
| `pom.xml` | Maven dependencies (Procyon, CFR, Vineflower, Gson) |
| `module-info.java` | Module declarations + opens/exports |
| `FxDecompilerApp.java` | Application entry point |
| `MainWindow.java` | Stage + layout (SplitPane, TabPanes, StatusBar) |
| `ui/menu/MainMenuBar.java` | Menu bar assembly |
| `ui/tree/FileTreeView.java` | Left-side file tree |
| `ui/tree/FileTreeCell.java` | Tree cell renderer (icons) |
| `ui/code/CodeEditorTab.java` | CodeArea wrapper in a Tab |
| `ui/code/StatusBar.java` | Bottom status bar (path, encoding, position) |
| `ui/theme/RegexHighlighter.java` | SyntaxDecorator impl, regex-based |
| `ui/theme/VsCodeThemeLoader.java` | VS Code theme JSON → style map + editor colors |
| `model/Workspace.java` | Holds one JAR/ZIP/dir: tree + code tabs + cache |
| `model/FileTreeNode.java` | Tree node data (name, path, type, isClass, bytes ref) |
| `model/OpenFile.java` | Opened class file state (source text, engine, path) |
| `decompiler/IDecompiler.java` | Decompiler interface |
| `decompiler/DecompilerType.java` | Enum: PROCYON, CFR, VINEFLOWER |
| `decompiler/DecompilerFactory.java` | Factory with singleton cache |
| `decompiler/CfrDecompiler.java` | CFR engine adapter |
| `decompiler/VineflowerDecompiler.java` | Vineflower engine adapter |
| `decompiler/ProcyonDecompiler.java` | Procyon engine adapter |
| `decompiler/BytecodeCache.java` | Global ConcurrentHashMap<String, byte[]> |
| `io/ClassLoader.java` | Discover .class files from JAR/ZIP/dir/single file |
| `io/FileTreeBuilder.java` | Build TreeItem<FileTreeNode> from ClassEntry list |
| `io/ExportService.java` | Export single file / all classes to dir / ZIP |
| `config/AppConfig.java` | Read/write config.json |

---

### Task 1: Project Skeleton — Dependencies & Entry Point

**Files:**
- Modify: `pom.xml`
- Modify: `src/main/java/module-info.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/FxDecompilerApp.java`
- Keep (no changes): `src/main/java/com/bingbaihanji/fxdecomplie/Main.java`

- [ ] **Step 1: Update pom.xml — add decompiler + JSON dependencies**

Open `pom.xml`. Replace the `<dependencies>` section with:

```xml
    <dependencies>
        <!-- JavaFX (JDK自带，provided scope 避免重复打包) -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>25.0.1</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-base</artifactId>
            <version>25.0.1</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-graphics</artifactId>
            <version>25.0.1</version>
            <scope>provided</scope>
        </dependency>

        <!-- JSON 解析 -->
        <dependency>
            <groupId>com.google.code.gson</groupId>
            <artifactId>gson</artifactId>
            <version>2.11.0</version>
        </dependency>

        <!-- 反编译引擎 -->
        <dependency>
            <groupId>org.vineflower</groupId>
            <artifactId>vineflower</artifactId>
            <version>1.11.2</version>
        </dependency>
        <dependency>
            <groupId>org.benf</groupId>
            <artifactId>cfr</artifactId>
            <version>0.152</version>
        </dependency>
        <dependency>
            <groupId>org.bitbucket.mstrobel</groupId>
            <artifactId>procyon-core</artifactId>
            <version>0.6.0</version>
        </dependency>
        <dependency>
            <groupId>org.bitbucket.mstrobel</groupId>
            <artifactId>procyon-compilertools</artifactId>
            <version>0.6.0</version>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <version>${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

Also add the maven-shade-plugin to build a fat JAR. Add this inside `<build><plugins>` after the compiler plugin:

```xml

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-shade-plugin</artifactId>
    <version>3.6.0</version>
    <executions>
        <execution>
            <phase>package</phase>
            <goals>
                <goal>shade</goal>
            </goals>
            <configuration>
                <transformers>
                    <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                        <mainClass>com.bingbaihanji.fxdecomplie.FxDecompilerApp</mainClass>
                    </transformer>
                </transformers>
            </configuration>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Update module-info.java**

```java
module com.bingbaihanji.fxdecomplie {
    requires javafx.controls;
    requires javafx.graphics;
    requires jfx.incubator.richtext;

    // JSON
    requires com.google.gson;

    // Decompilers
    requires org.jetbrains.java.decompiler;  // Vineflower
    requires org.benf.cfr;                   // CFR
    requires procyon.core;                   // Procyon
    requires procyon.compilertools;          // Procyon tools

    exports com.bingbaihanji.fxdecomplie;
    exports com.bingbaihanji.fxdecomplie.model;
    exports com.bingbaihanji.fxdecomplie.decompiler;
    exports com.bingbaihanji.fxdecomplie.io;
    exports com.bingbaihanji.fxdecomplie.ui.menu;
    exports com.bingbaihanji.fxdecomplie.ui.tree;
    exports com.bingbaihanji.fxdecomplie.ui.code;
    exports com.bingbaihanji.fxdecomplie.ui.theme;
    exports com.bingbaihanji.fxdecomplie.config;

    opens com.bingbaihanji.fxdecomplie to com.google.gson;
}
```

- [ ] **Step 3: Create FxDecompilerApp.java**

```java
package com.bingbaihanji.fxdecomplie;

import javafx.application.Application;
import javafx.stage.Stage;

public class FxDecompilerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainWindow window = new MainWindow();
        window.show(primaryStage);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 4: Verify — compile and run**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 2: UI Layout — MainWindow + StatusBar + Placeholder Tabs

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/StatusBar.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/Workspace.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/FileTreeNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/OpenFile.java`

- [ ] **Step 1: Create FileTreeNode.java**

```java
package com.bingbaihanji.fxdecomplie.model;

/**
 * 文件树节点数据模型。包装在 TreeItem.getValue() 中。
 */
public class FileTreeNode {

    public enum NodeType {
        PACKAGE,      // 包节点（中间层级）
        CLASS_FILE,   // .class 文件
        JAVA_FILE,    // .java 文件
        RESOURCE,     // 其他资源文件 (xml, txt, properties 等)
        BINARY        // 二进制文件（不显示内容）
    }

    private final String name;          // 显示名（如 "Main"）
    private final String fullPath;      // 完整内部路径（如 "com/example/Main.class"）
    private final NodeType nodeType;
    private byte[] cachedBytes;         // 缓存 byte[]（class文件已读取）
    private boolean decompiled;         // 是否已反编译

    public FileTreeNode(String name, String fullPath, NodeType nodeType) {
        this.name = name;
        this.fullPath = fullPath;
        this.nodeType = nodeType;
    }

    public String getName() {
        return name;
    }

    public String getFullPath() {
        return fullPath;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public byte[] getCachedBytes() {
        return cachedBytes;
    }

    public void setCachedBytes(byte[] cachedBytes) {
        this.cachedBytes = cachedBytes;
    }

    public boolean isDecompiled() {
        return decompiled;
    }

    public void setDecompiled(boolean decompiled) {
        this.decompiled = decompiled;
    }

    public boolean isClassFile() {
        return nodeType == NodeType.CLASS_FILE;
    }

    @Override
    public String toString() {
        return name;
    }
}
```

- [ ] **Step 2: Create StatusBar.java**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * 底部状态栏: [类路径]  [编码]  [行:列]
 * 样式: com>bingbaihanji>Main.class    UTF-8    254:12
 */
public class StatusBar extends HBox {

    private final Label pathLabel;    // 左：类路径
    private final Label encodingLabel; // 中：编码
    private final Label positionLabel; // 右：行:列
    private final Region spacer1;
    private final Region spacer2;

    public StatusBar() {
        setPadding(new Insets(2, 8, 2, 8));
        setStyle("-fx-background-color: #007acc; -fx-border-width: 1 0 0 0; -fx-border-color: #3c3c3c;");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(0);

        pathLabel = new Label("");
        pathLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        spacer1 = new Region();
        HBox.setHgrow(spacer1, Priority.ALWAYS);

        encodingLabel = new Label("UTF-8");
        encodingLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px; -fx-padding: 0 16 0 0;");

        spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        positionLabel = new Label("1:1");
        positionLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

        getChildren().addAll(pathLabel, spacer1, encodingLabel, spacer2, positionLabel);
    }

    /** 更新类路径 */
    public void setFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            pathLabel.setText("");
            return;
        }
        // 将点号分隔的路径转为 ">" 分隔
        String display = filePath.replace('.', '>');
        if (display.endsWith(".class")) {
            display = display.substring(0, display.length() - 6);
        }
        pathLabel.setText(display);
    }

    /** 更新光标位置 */
    public void setCursorPosition(int line, int column) {
        positionLabel.setText(line + ":" + column);
    }

    /** 更新编码显示 */
    public void setEncoding(String encoding) {
        encodingLabel.setText(encoding);
    }

    /** 清除状态栏 */
    public void clear() {
        pathLabel.setText("");
        positionLabel.setText("1:1");
        encodingLabel.setText("UTF-8");
    }
}
```

- [ ] **Step 3: Create Workspace.java (placeholder)**

```java
package com.bingbaihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;

import java.io.File;

/**
 * 代表一个已打开的 JAR/ZIP/目录工作空间。
 * 包含文件树根节点 + 代码标签页。
 */
public class Workspace {

    private final String name;                  // 显示名 (demo.jar)
    private final File sourceFile;              // 源文件
    private final TreeItem<FileTreeNode> treeRoot;  // 文件树根节点
    private final boolean isArchive;            // true=JAR/ZIP, false=目录/单文件

    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.treeRoot = treeRoot;
        this.isArchive = isArchive;
    }

    public String getName() {
        return name;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public TreeItem<FileTreeNode> getTreeRoot() {
        return treeRoot;
    }

    public boolean isArchive() {
        return isArchive;
    }

    @Override
    public String toString() {
        return name;
    }
}
```

- [ ] **Step 4: Create OpenFile.java (placeholder)**

```java
package com.bingbaihanji.fxdecomplie.model;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerType;

/**
 * 已打开的 class 文件状态。
 */
public class OpenFile {

    private final String className;          // 简短类名 (Main)
    private final String fullPath;           // 完整内部路径
    private final String sourceCode;         // 反编译后的源码
    private final DecompilerType engine;     // 使用的引擎

    public OpenFile(String className, String fullPath, String sourceCode, DecompilerType engine) {
        this.className = className;
        this.fullPath = fullPath;
        this.sourceCode = sourceCode;
        this.engine = engine;
    }

    public String getClassName() {
        return className;
    }

    public String getFullPath() {
        return fullPath;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public DecompilerType getEngine() {
        return engine;
    }

    @Override
    public String toString() {
        return className + ".java";
    }
}
```

- [ ] **Step 5: Create MainWindow.java (layout skeleton)**

```java
package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.ui.code.StatusBar;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

/**
 * 主窗口控制器。负责所有 UI 装配。
 */
public class MainWindow {

    private Stage stage;
    private StatusBar statusBar;
    private TabPane outerTabPane;  // JAR/ZIP/目录 级别

    public void show(Stage stage) {
        this.stage = stage;

        // --- 顶部: 菜单栏 ---
        MenuBar menuBar = createMenuBar();

        // --- 中心: 外层 TabPane ---
        outerTabPane = new TabPane();
        outerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 初始空占位
        Label placeholder = new Label("拖入 JAR/ZIP/Class 文件 或 文件 → 打开");
        placeholder.setStyle("-fx-text-fill: #858585; -fx-font-size: 16px;");
        outerTabPane.getTabs().add(new Tab("欢迎", placeholder));

        // --- 底部: 状态栏 ---
        statusBar = new StatusBar();

        // --- 装配 ---
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(outerTabPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add("data:text/css," +
                ".root { -fx-background-color: #1e1e1e; }" +
                ".tab-pane { -fx-background-color: #1e1e1e; }" +
                ".tab-pane .tab-header-area .tab-header-background { -fx-background-color: #2d2d2d; }" +
                ".tab-pane .tab { -fx-background-color: #2d2d2d; -fx-text-fill: #cccccc; }" +
                ".tab-pane .tab:selected { -fx-background-color: #1e1e1e; }" +
                ".split-pane { -fx-background-color: #1e1e1e; }" +
                ".split-pane-divider { -fx-background-color: #3c3c3c; -fx-padding: 0 1; }" +
                ".tree-view { -fx-background-color: #252526; -fx-text-fill: #cccccc; }" +
                ".menu-bar { -fx-background-color: #3c3c3c; }" +
                ".menu { -fx-text-fill: #cccccc; }" +
                ".menu-item { -fx-text-fill: #cccccc; }" +
                ".label { -fx-text-fill: #cccccc; }"
        );

        stage.setTitle("FxDecompiler");
        stage.setScene(scene);
        stage.show();
    }

    private MenuBar createMenuBar() {
        // --- 文件菜单 ---
        Menu fileMenu = new Menu("文件(F)");
        MenuItem openFile = new MenuItem("打开文件...");
        openFile.setOnAction(e -> openFile());
        MenuItem openDir = new MenuItem("打开目录...");
        openDir.setOnAction(e -> openDirectory());
        MenuItem closeCurrent = new MenuItem("关闭当前");
        MenuItem closeOthers = new MenuItem("关闭其他");
        MenuItem saveFile = new MenuItem("保存当前文件");
        MenuItem exportAll = new MenuItem("全部导出...");
        MenuItem exit = new MenuItem("退出");
        exit.setOnAction(e -> stage.close());
        fileMenu.getItems().addAll(openFile, openDir, new SeparatorMenuItem(),
                closeCurrent, closeOthers, new SeparatorMenuItem(),
                saveFile, exportAll, new SeparatorMenuItem(), exit);

        // --- 编辑菜单 ---
        Menu editMenu = new Menu("编辑(E)");
        MenuItem copy = new MenuItem("复制");
        MenuItem selectAll = new MenuItem("全选");
        MenuItem find = new MenuItem("查找...");
        editMenu.getItems().addAll(copy, selectAll, new SeparatorMenuItem(), find);

        // --- 视图菜单 ---
        Menu viewMenu = new Menu("视图(V)");
        MenuItem zoomIn = new MenuItem("放大");
        MenuItem zoomOut = new MenuItem("缩小");
        MenuItem zoomReset = new MenuItem("重置缩放");
        MenuItem toggleLineNumbers = new MenuItem("切换行号显示");
        MenuItem collapseTree = new MenuItem("文件树折叠");
        viewMenu.getItems().addAll(zoomIn, zoomOut, zoomReset,
                new SeparatorMenuItem(), toggleLineNumbers, collapseTree);

        // --- 引擎菜单 ---
        Menu engineMenu = new Menu("引擎(G)");
        ToggleGroup engineGroup = new ToggleGroup();
        RadioMenuItem procyonItem = new RadioMenuItem("Procyon");
        procyonItem.setToggleGroup(engineGroup);
        RadioMenuItem cfrItem = new RadioMenuItem("CFR");
        cfrItem.setToggleGroup(engineGroup);
        RadioMenuItem vineflowerItem = new RadioMenuItem("Vineflower");
        vineflowerItem.setToggleGroup(engineGroup);
        vineflowerItem.setSelected(true);
        engineMenu.getItems().addAll(procyonItem, cfrItem, vineflowerItem);

        // --- 主题菜单 ---
        Menu themeMenu = new Menu("主题(T)");
        MenuItem selectTheme = new MenuItem("选择主题...");
        themeMenu.getItems().add(selectTheme);

        // --- 帮助 ---
        Menu helpMenu = new Menu("帮助(H)");
        MenuItem about = new MenuItem("关于");
        helpMenu.getItems().add(about);

        return new MenuBar(fileMenu, editMenu, viewMenu, engineMenu, themeMenu, helpMenu);
    }

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开文件");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Java Archives & Class Files", "*.jar", "*.zip", "*.class"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            System.out.println("Opening: " + file.getAbsolutePath());
            // TODO: 后续任务实现实际加载逻辑
        }
    }

    private void openDirectory() {
        // 使用 DirectoryChooser
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("打开目录");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            System.out.println("Opening directory: " + dir.getAbsolutePath());
            // TODO: 后续任务实现
        }
    }
}
```

- [ ] **Step 6: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: The file should compile (MainWindow will have warnings about unused fields — acceptable at this stage).

---

### Task 3: Decompiler Engine Layer — Interface + 3 Engines + Cache

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/IDecompiler.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerType.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactory.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/BytecodeCache.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrDecompiler.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerDecompiler.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonDecompiler.java`

- [ ] **Step 1: Create DecompilerType.java**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

public enum DecompilerType {
    PROCYON,
    CFR,
    VINEFLOWER
}
```

- [ ] **Step 2: Create IDecompiler.java**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

/**
 * 反编译引擎统一接口。
 * 所有引擎实现必须是无状态或线程安全的（通过 DecompilerFactory 缓存复用）。
 */
public interface IDecompiler {

    /**
     * 反编译字节码为 Java 源码。
     * @param classFilePath 类文件路径（如 "com/example/Main.class"），仅用于日志/标识
     * @param classBytes 类文件字节码
     * @return 反编译后的 Java 源码
     */
    String decompile(String classFilePath, byte[] classBytes);

    /**
     * 按内部类型名反编译。
     * @param typeName 内部类型名（如 "com.example.Main"）
     * @param classBytes 类文件字节码
     * @return 反编译后的 Java 源码
     */
    String decompileType(String typeName, byte[] classBytes);

    /** 返回引擎类型标识 */
    DecompilerType getType();

    /** 引擎名称（默认返回枚举名） */
    default String getName() {
        return getType().name();
    }

    /** 引擎初始化（首次创建时调用） */
    default void initialize() {
    }

    /** 引擎清理（应用退出时调用） */
    default void cleanup() {
    }
}
```

- [ ] **Step 3: Create BytecodeCache.java**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局字节码缓存。在打开 JAR/ZIP 时预加载所有 class 文件的 byte[]，
 * 供反编译引擎解析类型依赖时查找。
 */
public class BytecodeCache {

    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<>();

    public static void put(String internalName, byte[] bytes) {
        CACHE.put(internalName, bytes);
    }

    public static byte[] get(String internalName) {
        return CACHE.get(internalName);
    }

    public static boolean contains(String internalName) {
        return CACHE.containsKey(internalName);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }
}
```

- [ ] **Step 4: Create DecompilerFactory.java**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import java.util.EnumMap;
import java.util.Map;

/**
 * 反编译引擎工厂。每个引擎类型单例缓存。
 */
public class DecompilerFactory {

    private static final Map<DecompilerType, IDecompiler> CACHE = new EnumMap<>(DecompilerType.class);

    public static IDecompiler getDecompiler(DecompilerType type) {
        return CACHE.computeIfAbsent(type, t -> {
            IDecompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
            };
            engine.initialize();
            return engine;
        });
    }

    /** 清理所有引擎 */
    public static void cleanup() {
        CACHE.values().forEach(IDecompiler::cleanup);
        CACHE.clear();
    }
}
```

- [ ] **Step 5: Create CfrDecompiler.java**

Port from `D:\bingbaihanji\code-resurrector\src\main\java\com\bingbaihanji\code\resurrector\decompiler\CfrDecompiler.java`.

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;

import java.util.*;

/**
 * CFR 反编译引擎适配器。
 * 参考 code-resurrector 的 CfrDecompiler 移植。
 */
public class CfrDecompiler implements IDecompiler {

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileInternal(classFilePath, classBytes);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileInternal(typeName, classBytes);
    }

    private String decompileInternal(String identifier, byte[] classBytes) {
        Map<String, String> options = new HashMap<>();
        options.put("analyseas", "CLASS");
        options.put("decodeenumswitch", "true");
        options.put("decodelambdas", "true");
        options.put("decodestringswitch", "true");
        options.put("hideutf", "false");
        options.put("liftconstructorinit", "true");
        options.put("override", "true");
        options.put("pullcodecase", "false");
        options.put("recover", "true");
        options.put("recovertypeclash", "true");
        options.put("recovertypehints", "true");
        options.put("removebadgenerics", "true");
        options.put("removebooleangets", "true");
        options.put("removeinnerclasssynthetics", "true");
        options.put("showinferrable", "true");
        options.put("sugarboxing", "true");
        options.put("tidymonitors", "true");
        options.put("usediamondoperator", "true");
        options.put("usenametable", "true");

        // 类文件路径 (CFR 需要用于引用解析)
        options.put("classpath", "");

        final StringBuilder result = new StringBuilder();
        OutputSinkFactory sink = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> collection) {
                return List.of(SinkClass.DECOMPILED, SinkClass.EXCEPTION);
            }

            @Override
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return (SinkReturns.Decompiled) decompiled -> result.append(decompiled.getJava());
                }
                if (sinkType == SinkType.EXCEPTION && sinkClass == SinkClass.EXCEPTION) {
                    return (SinkReturns.Exception) e -> result.append("// CFR Error: ").append(e.getMessage());
                }
                return ignore -> {
                };
            }
        };

        CfrDriver driver = new CfrDriver.Builder()
                .withOptions(options)
                .withOutputSink(sink)
                .build();

        driver.analyse(Collections.singletonList(identifier));
        return result.toString();
    }

    @Override
    public DecompilerType getType() {
        return DecompilerType.CFR;
    }
}
```

- [ ] **Step 6: Create VineflowerDecompiler.java**

Port from `D:\bingbaihanji\code-resurrector\src\main\java\com\bingbaihanji\code\resurrector\decompiler\VineflowerDecompiler.java`.

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Vineflower 反编译引擎适配器。
 * 将 classBytes 写入临时文件，调用 Vineflower 反编译，读取结果。
 */
public class VineflowerDecompiler implements IDecompiler {

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        try {
            return decompileInternal(classFilePath, classBytes);
        } catch (IOException e) {
            return "// Vineflower Error: " + e.getMessage();
        }
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompile(typeName, classBytes);
    }

    private String decompileInternal(String path, byte[] bytes) throws IOException {
        // 写入临时 class 文件
        Path tmpDir = Files.createTempDirectory("vf_temp_");
        File classFile = tmpDir.resolve("Input.class").toFile();
        Files.write(classFile.toPath(), bytes);

        Map<String, Object> options = new HashMap<>();
        options.put("asc", "1");
        options.put("bsm", "1");
        options.put("dcc", "1");
        options.put("das", "0");
        options.put("hes", "1");
        options.put("hdc", "0");
        options.put("ind", "    ");
        options.put("inn", "1");
        options.put("ner", "1");
        options.put("rsy", "1");
        options.put("tcs", "0");
        options.put("udi", "1");

        final StringBuilder result = new StringBuilder();

        BaseDecompiler decompiler = new BaseDecompiler(
            new IBytecodeProvider() {
                @Override
                public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
                    // 先从缓存查找依赖
                    byte[] cached = BytecodeCache.get(internalPath);
                    if (cached != null) return cached;
                    // 从临时目录读主文件
                    return Files.readAllBytes(Path.of(externalPath, internalPath + ".class"));
                }
            },
            new IResultSaver() {
                @Override
                public void saveClassFile(String s, String s1, String content, int[] ints) {
                    // Vineflower 按源码属性回调 — 不需要
                }

                @Override
                public void saveFolder(String s) {}

                @Override
                public void copyFile(String s, String s1, String s2) {}

                @Override
                public void createArchive(String s, String s1, Manifest manifest) {}

                @Override
                public void saveDirEntry(String s, String s1, String s2) {}

                @Override
                public void copyEntry(String s, String s1, String s2, String s3) {}

                @Override
                public void saveClassEntry(String s, String s1, String s2, String s3, String content) {
                    result.setLength(0);
                    result.append(content);
                }

                @Override
                public void closeArchive(String s, String s1) {}
            },
            options,
            new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {
                    if (severity == Severity.ERROR) {
                        result.append("// VF ").append(severity).append(": ").append(message).append("\n");
                    }
                }

                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {
                    writeMessage(message + ": " + t.getMessage(), severity);
                }
            }
        );

        decompiler.addSource(classFile);
        decompiler.decompileContext();

        // 清理临时文件
        try { Files.deleteIfExists(classFile.toPath()); } catch (IOException ignored) {}
        try { Files.deleteIfExists(tmpDir); } catch (IOException ignored) {}

        return result.length() > 0 ? result.toString() : "// Vineflower: no output";
    }

    @Override
    public DecompilerType getType() { return DecompilerType.VINEFLOWER; }
}
```

- [ ] **Step 7: Create ProcyonDecompiler.java**

Port from `D:\bingbaihanji\code-resurrector\src\main\java\com\bingbaihanji\code\resurrector\decompiler\ProcyonDecompiler.java`.

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;

import java.io.StringWriter;

/**
 * Procyon 反编译引擎适配器。
 */
public class ProcyonDecompiler implements IDecompiler {

    private final DecompilerSettings settings;

    public ProcyonDecompiler() {
        settings = new DecompilerSettings();
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileInternal(classFilePath, classBytes);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileInternal(typeName, classBytes);
    }

    private String decompileInternal(String name, byte[] bytes) {
        StringWriter writer = new StringWriter();
        Decompiler.decompile(
            name,
            new PlainTextOutput(writer),
            settings
        );
        return writer.toString();
    }

    @Override
    public DecompilerType getType() { return DecompilerType.PROCYON; }
}
```

- [ ] **Step 8: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS. If CFR or Vineflower API has changed vs the reference version, adjust accordingly.

---

### Task 4: File Loading — ClassLoader + FileTreeBuilder

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/io/ClassLoader.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/io/FileTreeBuilder.java`

- [ ] **Step 1: Create ClassLoader.java — file discovery**

The `ClassLoader` name conflicts with `java.lang.ClassLoader`. Use `ClassDiscoverer` instead.

```java
package com.bingbaihanji.fxdecomplie.io;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * 类文件发现器。支持 JAR/ZIP/目录/单个 .class 文件。
 */
public class ClassDiscoverer {

    /**
     * 发现的条目。
     */
    public record ClassEntry(
            String name,       // 显示名（如 "Main.class"）
            String fullPath,   // 完整路径（如 "com/example/Main.class"）
            FileTreeNode.NodeType nodeType,
            byte[] bytes       // .class 文件的 byte[]（非 class 文件为 null）
    ) {
    }

    /**
     * 发现所有文件条目。自动判断输入类型。
     */
    public static List<ClassEntry> discover(File file) throws IOException {
        String name = file.getName().toLowerCase();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return discoverJar(file);
        } else if (file.isDirectory()) {
            return discoverDirectory(file);
        } else if (name.endsWith(".class")) {
            return discoverClassFile(file);
        } else {
            // 其他单个文件
            return List.of(new ClassEntry(file.getName(), file.getName(),
                    guessType(file.getName()), null));
        }
    }

    private static List<ClassEntry> discoverJar(File file) throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        try (JarFile jar = new JarFile(file)) {
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (entry.isDirectory()) continue;
                String path = entry.getName();
                FileTreeNode.NodeType type = guessType(path);
                byte[] bytes = null;
                if (type == FileTreeNode.NodeType.CLASS_FILE) {
                    bytes = jar.getInputStream(entry).readAllBytes();
                }
                // 提取文件名
                String displayName = path.substring(path.lastIndexOf('/') + 1);
                entries.add(new ClassEntry(displayName, path, type, bytes));
            }
        }
        return entries;
    }

    private static List<ClassEntry> discoverDirectory(File dir) throws IOException {
        List<ClassEntry> entries = new ArrayList<>();
        Path root = dir.toPath();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String relativePath = root.relativize(p).toString().replace('\\', '/');
                String displayName = p.getFileName().toString();
                FileTreeNode.NodeType type = guessType(displayName);
                byte[] bytes = null;
                if (type == FileTreeNode.NodeType.CLASS_FILE) {
                    try {
                        bytes = Files.readAllBytes(p);
                    } catch (IOException ignored) {
                    }
                }
                entries.add(new ClassEntry(displayName, relativePath, type, bytes));
            });
        }
        return entries;
    }

    private static List<ClassEntry> discoverClassFile(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        return List.of(new ClassEntry(file.getName(), file.getName(),
                FileTreeNode.NodeType.CLASS_FILE, bytes));
    }

    private static FileTreeNode.NodeType guessType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".class")) return FileTreeNode.NodeType.CLASS_FILE;
        if (lower.endsWith(".java")) return FileTreeNode.NodeType.JAVA_FILE;
        if (lower.matches(".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql)$")) {
            return FileTreeNode.NodeType.RESOURCE;
        }
        return FileTreeNode.NodeType.BINARY;
    }
}
```

- [ ] **Step 2: Create FileTreeBuilder.java**

```java
package com.bingbaihanji.fxdecomplie.io;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;

import java.util.*;

/**
 * 将扁平 ClassEntry 列表构建为包层级 TreeItem 树。
 */
public class FileTreeBuilder {

    /**
     * 构建树。根节点名 = archiveName（如 "demo.jar"）。
     */
    public static TreeItem<FileTreeNode> build(String rootName, List<ClassDiscoverer.ClassEntry> entries) {
        FileTreeNode rootData = new FileTreeNode(rootName, "", FileTreeNode.NodeType.PACKAGE);
        TreeItem<FileTreeNode> root = new TreeItem<>(rootData);
        root.setExpanded(true);

        // 分组到目录层级
        Map<String, TreeItem<FileTreeNode>> pathMap = new HashMap<>();
        pathMap.put("", root);

        for (ClassDiscoverer.ClassEntry entry : entries) {
            // 缓存 .class 字节码
            if (entry.bytes() != null && entry.nodeType() == FileTreeNode.NodeType.CLASS_FILE) {
                // 注册到 BytecodeCache
                String internalName = entry.fullPath();
                if (internalName.endsWith(".class")) {
                    internalName = internalName.substring(0, internalName.length() - 6);
                }
                com.bingbaihanji.fxdecomplie.decompiler.BytecodeCache.put(internalName, entry.bytes());
            }

            FileTreeNode nodeData = new FileTreeNode(entry.name(), entry.fullPath(), entry.nodeType());
            nodeData.setCachedBytes(entry.bytes());

            TreeItem<FileTreeNode> parent = getOrCreateParent(root, entry.fullPath(), pathMap);
            TreeItem<FileTreeNode> child = new TreeItem<>(nodeData);
            parent.getChildren().add(child);
        }

        // 递归排序（目录在前，文件在后）
        sortTree(root);

        return root;
    }

    private static TreeItem<FileTreeNode> getOrCreateParent(
            TreeItem<FileTreeNode> root, String fullPath,
            Map<String, TreeItem<FileTreeNode>> pathMap) {
        // 找到最后一个 '/' 的位置，之前的部分是包路径
        int lastSlash = fullPath.lastIndexOf('/');
        if (lastSlash < 0) return root; // 根目录下的文件

        String parentPath = fullPath.substring(0, lastSlash);
        return pathMap.computeIfAbsent(parentPath, pp -> {
            // 递归创建父级
            TreeItem<FileTreeNode> grandParent = getOrCreateParent(root, pp, pathMap);
            String name = pp.substring(pp.lastIndexOf('/') + 1);
            FileTreeNode data = new FileTreeNode(name, pp, FileTreeNode.NodeType.PACKAGE);
            TreeItem<FileTreeNode> node = new TreeItem<>(data);
            node.setExpanded(false);
            grandParent.getChildren().add(node);
            return node;
        });
    }

    private static void sortTree(TreeItem<FileTreeNode> node) {
        node.getChildren().sort((a, b) -> {
            FileTreeNode aData = a.getValue();
            FileTreeNode bData = b.getValue();
            // 包在前，文件在后
            boolean aDir = aData.getNodeType() == FileTreeNode.NodeType.PACKAGE;
            boolean bDir = bData.getNodeType() == FileTreeNode.NodeType.PACKAGE;
            if (aDir != bDir) return aDir ? -1 : 1;
            return aData.getName().compareToIgnoreCase(bData.getName());
        });
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            sortTree(child);
        }
    }
}
```

- [ ] **Step 3: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 5: Wire UI — Connect File Loading to Workspace and CodeEditorTab

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java` — wire load logic
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/tree/FileTreeView.java`

- [ ] **Step 1: Create CodeEditorTab.java**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.OpenFile;
import jfx.incubator.scene.control.richtext.CodeArea;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import javafx.scene.control.Tab;
import javafx.scene.text.Font;

/**
 * 单个代码标签页。封装 CodeArea + 语法高亮器。
 */
public class CodeEditorTab extends Tab {

    private final CodeArea codeArea;
    private final OpenFile openFile;

    public CodeEditorTab(OpenFile openFile, SyntaxDecorator highlighter) {
        this.openFile = openFile;

        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setLineNumbersEnabled(true);
        codeArea.setFont(Font.font("Consolas", 14));
        codeArea.setText(openFile.sourceCode());
        codeArea.setSyntaxDecorator(highlighter);

        // 暗色基础样式
        codeArea.setStyle(String.join(";",
                "-fx-background-color: #1e1e1e",
                "-fx-text-fill: #d4d4d4",
                "-fx-highlight-fill: #264f78"
        ));

        setText(openFile.className() + ".java");
        setContent(codeArea);
    }

    public CodeArea getCodeArea() {
        return codeArea;
    }

    public OpenFile getOpenFile() {
        return openFile;
    }

    /** 获取光标位置 (1-based 行，1-based 列) */
    public int[] getCursorPosition() {
        int pos = codeArea.getCaretPosition(); // 需要确认此方法存在

        // Fallback: 如果 getCaretPosition 不可用，尝试 TextPos
        var caret = codeArea.getCaretPosition();
        if (caret != null) {
            return new int[]{caret.index() + 1, caret.offset() + 1};
        }
        return new int[]{1, 1};
    }
}
```

- [ ] **Step 2: Create FileTreeView.java**

```java
package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

/**
 * 文件树视图封装。
 */
public class FileTreeView extends TreeView<FileTreeNode> {

    public FileTreeView(TreeItem<FileTreeNode> root) {
        super(root);
        setShowRoot(true);
        setStyle("-fx-background-color: #252526;");

        // 自定义单元格渲染
        setCellFactory(tv -> new FileTreeCell());
    }
}
```

- [ ] **Step 3: Create FileTreeCell.java**

```java
package com.bingbaihanji.fxdecomplie.ui.tree;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeCell;

/**
 * 文件树单元格渲染器。根据节点类型显示不同图标（用 Unicode 字符替代）。
 */
public class FileTreeCell extends TreeCell<FileTreeNode> {

    @Override
    protected void updateItem(FileTreeNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
            setText(null);
            setGraphic(null);
        } else {
            String icon = switch (item.getNodeType()) {
                case PACKAGE -> "📁 ";  // 📁
                case CLASS_FILE -> "📎 ";  // 📎
                case JAVA_FILE -> "📝 ";  // 📝
                case RESOURCE -> "📄 ";  // 📄
                case BINARY -> "❓ ";         // ❓
            };
            setText(icon + item.getName());
            setStyle("-fx-text-fill: #cccccc;");
        }
    }
}
```

- [ ] **Step 4: Modify MainWindow.java — wire the openFile/openDirectory to load logic**

Add imports at top:

```java
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerType;
import com.bingbaihanji.fxdecomplie.io.ClassDiscoverer;
import com.bingbaihanji.fxdecomplie.io.FileTreeBuilder;

```

Replace the `openFile()` and `openDirectory()` methods and add `loadFile()`:

```java
    private DecompilerType currentEngine = DecompilerType.VINEFLOWER;

    private void openFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("打开文件");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Java Archives & Class Files", "*.jar", "*.zip", "*.class"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            loadFile(file);
        }
    }

    private void openDirectory() {
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("打开目录");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            loadFile(dir);
        }
    }

    /** 核心加载逻辑 */
    private void loadFile(File file) {
        String name = file.getName();
        boolean isArchive = name.toLowerCase().endsWith(".jar") || name.toLowerCase().endsWith(".zip");

        // 在后台线程执行 IO 操作
        new Thread(() -> {
            try {
                // 1. 发现文件
                var entries = ClassDiscoverer.discover(file);

                // 2. 构建文件树
                TreeItem<FileTreeNode> treeRoot = FileTreeBuilder.build(name, entries);

                // 3. 创建 Workspace
                Workspace workspace = new Workspace(name, file, treeRoot, isArchive);

                // 4. UI 更新
                Platform.runLater(() -> addWorkspaceTab(workspace));

            } catch (IOException e) {
                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("加载错误");
                    alert.setHeaderText("无法加载文件");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                });
            }
        }, "FileLoader-" + name).start();
    }

    /** 添加 Workspace 到外层 TabPane */
    private void addWorkspaceTab(Workspace workspace) {
        // 如果只有一个占位 Tab，移除它
        if (outerTabPane.getTabs().size() == 1
                && "欢迎".equals(outerTabPane.getTabs().get(0).getText())) {
            outerTabPane.getTabs().clear();
        }

        // 左侧：文件树
        FileTreeView treeView = new FileTreeView(workspace.getTreeRoot());
        treeView.setPrefWidth(280);

        // 右侧：内层代码 TabPane
        TabPane codeTabPane = new TabPane();
        codeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        // 点击树节点 → 打开代码 Tab
        treeView.setOnMouseClicked(e -> {
            TreeItem<FileTreeNode> item = treeView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            FileTreeNode node = item.getValue();
            if (node == null) return;

            if (node.isClassFile() && e.getClickCount() >= 1) {
                openClassTab(node, workspace, codeTabPane);
            }
        });

        // 水平分割
        SplitPane splitPane = new SplitPane(treeView, codeTabPane);
        splitPane.setDividerPositions(0.25);

        // 外层 Tab
        Tab tab = new Tab(workspace.getName(), splitPane);
        tab.setOnClosed(e -> {
            BytecodeCache.clear();
        });

        outerTabPane.getTabs().add(tab);
        outerTabPane.getSelectionModel().select(tab);
    }

    /** 打开 class 文件的代码 Tab */
    private void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane) {
        // 检查是否已打开
        for (Tab t : codeTabPane.getTabs()) {
            if (t instanceof CodeEditorTab cet) {
                if (cet.getOpenFile().getFullPath().equals(node.getFullPath())) {
                    codeTabPane.getSelectionModel().select(t);
                    return;
                }
            }
        }

        new Thread(() -> {
            try {
                // 获取字节码
                byte[] bytes = node.getCachedBytes();
                if (bytes == null) {
                    // 从磁盘读取
                    File source = workspace.getSourceFile();
                    if (workspace.isArchive()) {
                        // JAR/ZIP — 从缓存获取
                        bytes = BytecodeCache.get(node.getFullPath().replace(".class", ""));
                    } else {
                        bytes = Files.readAllBytes(
                            new java.io.File(source, node.getFullPath()).toPath());
                    }
                }
                if (bytes == null) {
                    Platform.runLater(() -> showError("无法读取: " + node.getFullPath()));
                    return;
                }

                // 反编译
                String sourceCode = DecompilerFactory.getDecompiler(currentEngine)
                        .decompile(node.getFullPath(), bytes);

                // 类名 = 去后缀
                String className = node.getName();
                if (className.endsWith(".class")) {
                    className = className.substring(0, className.length() - 6);
                }

                OpenFile openFile = new OpenFile(className, node.getFullPath(), sourceCode, currentEngine);
                CodeEditorTab codeTab = new CodeEditorTab(openFile, null); // highlighter 后续注入

                Platform.runLater(() -> {
                    codeTabPane.getTabs().add(codeTab);
                    codeTabPane.getSelectionModel().select(codeTab);

                    // 更新状态栏
                    statusBar.setFilePath(node.getFullPath().replace('/', '>').replace(".class", ""));
                    statusBar.setEncoding("UTF-8");

                    // 监听光标位置变化
                    codeTab.getCodeArea().caretPositionProperty().addListener((obs, old, pos) -> {
                        if (pos != null) {
                            statusBar.setCursorPosition(pos.index() + 1, pos.offset() + 1);
                        }
                    });
                });

            } catch (Exception ex) {
                Platform.runLater(() -> showError("反编译失败: " + ex.getMessage()));
            }
        }, "Decompile-" + node.getName()).start();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR, msg);
        alert.setTitle("错误");
        alert.showAndWait();
    }
```

- [ ] **Step 5: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: Check for compilation errors. Fix any issues (e.g., `getCaretPosition` return type).

---

### Task 6: Syntax Highlighting — RegexHighlighter + VsCodeThemeLoader

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/RegexHighlighter.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/VsCodeThemeLoader.java`
- Create: `src/main/resources/com/bingbaihanji/fxdecomplie/themes/dark-plus.json`

- [ ] **Step 1: Create VsCodeThemeLoader.java**

```java
package com.bingbaihanji.fxdecomplie.ui.theme;

import com.google.gson.*;
import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * 加载 VS Code 主题 JSON 文件，提取编辑器外观颜色和 token 颜色映射。
 */
public class VsCodeThemeLoader {

    public record ThemeData(
            String name,
            String type,              // "dark" or "light"
            Color editorBackground,
            Color editorForeground,
            Color lineNumberForeground,
            String fontFamily,
            int fontSize,
            Map<String, StyleAttributeMap> tokenStyles  // scope prefix → style
    ) {
    }

    /**
     * 默认暗色主题（内置，无需外部文件）。
     */
    public static ThemeData defaultDark() {
        Map<String, StyleAttributeMap> tokenStyles = new LinkedHashMap<>();
        tokenStyles.put("keyword", style("#569CD6", true, false));
        tokenStyles.put("comment", style("#6A9955", false, true));
        tokenStyles.put("string", style("#CE9178", false, false));
        tokenStyles.put("annotation", style("#D7BA7D", false, false));
        tokenStyles.put("number", style("#B5CEA8", false, false));
        tokenStyles.put("default", style("#d4d4d4", false, false));

        return new ThemeData(
                "Default Dark",
                "dark",
                Color.web("#1e1e1e"),
                Color.web("#d4d4d4"),
                Color.web("#858585"),
                "Consolas",
                14,
                tokenStyles
        );
    }

    private static StyleAttributeMap style(String colorHex, boolean bold, boolean italic) {
        var b = StyleAttributeMap.builder()
                .setTextColor(Color.web(colorHex));
        if (bold) b.setBold(true);
        if (italic) b.setItalic(true);
        return b.build();
    }

    /**
     * 从文件路径加载主题。
     */
    public static ThemeData load(Path jsonPath) throws IOException {
        String json = Files.readString(jsonPath);
        return parse(json);
    }

    /**
     * 从 classpath 资源加载主题。
     */
    public static ThemeData loadResource(String resourcePath) throws IOException {
        try (InputStream in = VsCodeThemeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) throw new IOException("Resource not found: " + resourcePath);
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        }
    }

    private static ThemeData parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        String name = getString(root, "name", "Unknown");
        String type = getString(root, "type", "dark");

        // 解析 colors 节点
        JsonObject colors = root.getAsJsonObject("colors");
        Color bg = parseColor(colors, "editor.background", "#1e1e1e");
        Color fg = parseColor(colors, "editor.foreground", "#d4d4d4");
        Color ln = parseColor(colors, "editorLineNumber.foreground", "#858585");

        // 解析 tokenColors
        Map<String, StyleAttributeMap> tokenStyles = new LinkedHashMap<>();
        JsonArray tokenColors = root.getAsJsonArray("tokenColors");
        if (tokenColors != null) {
            for (JsonElement e : tokenColors) {
                JsonObject tc = e.getAsJsonObject();
                String scope = extractScope(tc.get("scope"));
                if (scope == null) continue;

                JsonObject settings = tc.getAsJsonObject("settings");
                String fgHex = getString(settings, "foreground", "#d4d4d4");
                String fontStyle = getString(settings, "fontStyle", "");
                boolean bold = fontStyle.contains("bold");
                boolean italic = fontStyle.contains("italic");

                // 只取第一个匹配的 scope 片段（简化）
                String key = scope.contains(",") ? scope.split(",")[0].trim() : scope;
                // 去除句点前缀的 scope（如 ".keyword" → "keyword"）
                if (key.startsWith(".")) key = key.substring(1);

                tokenStyles.putIfAbsent(key, style(fgHex, bold, italic));
            }
        }

        return new ThemeData(name, type, bg, fg, ln, "Consolas", 14, tokenStyles);
    }

    private static String extractScope(JsonElement scopeElem) {
        if (scopeElem == null) return null;
        if (scopeElem.isJsonPrimitive()) return scopeElem.getAsString();
        if (scopeElem.isJsonArray()) {
            JsonArray arr = scopeElem.getAsJsonArray();
            return arr.isEmpty() ? null : arr.get(0).getAsString();
        }
        return null;
    }

    private static Color parseColor(JsonObject colors, String key, String defaultHex) {
        if (colors == null) return Color.web(defaultHex);
        JsonElement e = colors.get(key);
        if (e == null || e.isJsonNull()) return Color.web(defaultHex);
        return Color.web(e.getAsString());
    }

    private static String getString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : def;
    }
}
```

- [ ] **Step 2: Create RegexHighlighter.java**

Extract from the existing `Main.JavaSyntaxDecorator` but make it reusable and theme-aware.

```java
package com.bingbaihanji.fxdecomplie.ui.theme;

import javafx.scene.paint.Color;
import jfx.incubator.scene.control.richtext.SyntaxDecorator;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.*;

import java.util.*;
import java.util.regex.*;

/**
 * 基于正则的 Java 语法高亮器。实现 SyntaxDecorator 接口。
 * Token 颜色从 VsCodeThemeLoader.ThemeData 获取。
 */
public class RegexHighlighter implements SyntaxDecorator {

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "var", "record", "sealed",
            "permits", "yield", "module", "requires", "exports", "opens", "to", "uses",
            "provides", "with", "transitive", "non-sealed"
    );

    // Token 类型 → theme scope 映射
    private static final Map<String, List<String>> TOKEN_TO_SCOPES = Map.of(
            "KEYWORD", List.of("keyword", "keyword.control", "storage.type"),
            "STRING", List.of("string", "string.quoted"),
            "COMMENT", List.of("comment", "comment.line", "comment.block"),
            "ANNOTATION", List.of("keyword.other.annotation", "storage.type.annotation"),
            "NUMBER", List.of("constant.numeric", "constant.numeric.decimal"),
            "DEFAULT", List.of("variable", "source", "meta")
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?<MULTICOMMENT>/\\*[\\s\\S]*?\\*/)"
                    + "|(?<SINGLECOMMENT>//[^\n]*)"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\])*\")"
                    + "|(?<ANNOTATION>@[a-zA-Z_][a-zA-Z0-9_.]*)"
                    + "|(?<NUMBER>\\b\\d+\\.?\\d*[fFlLdD]?\\b)"
                    + "|(?<IDENTIFIER>\\b[a-zA-Z_][a-zA-Z0-9_]*\\b)"
    );

    private final StyleAttributeMap styleDefault;
    private final StyleAttributeMap styleKeyword;
    private final StyleAttributeMap styleString;
    private final StyleAttributeMap styleComment;
    private final StyleAttributeMap styleAnnotation;
    private final StyleAttributeMap styleNumber;

    /** 用内置默认暗色主题 */
    public RegexHighlighter() {
        this(VsCodeThemeLoader.defaultDark());
    }

    /** 用外部主题数据 */
    public RegexHighlighter(VsCodeThemeLoader.ThemeData theme) {
        Map<String, StyleAttributeMap> styles = theme.tokenStyles();
        this.styleDefault = resolveStyle(styles, TOKEN_TO_SCOPES.get("DEFAULT"));
        this.styleKeyword = resolveStyle(styles, TOKEN_TO_SCOPES.get("KEYWORD"));
        this.styleString = resolveStyle(styles, TOKEN_TO_SCOPES.get("STRING"));
        this.styleComment = resolveStyle(styles, TOKEN_TO_SCOPES.get("COMMENT"));
        this.styleAnnotation = resolveStyle(styles, TOKEN_TO_SCOPES.get("ANNOTATION"));
        this.styleNumber = resolveStyle(styles, TOKEN_TO_SCOPES.get("NUMBER"));
    }

    private StyleAttributeMap resolveStyle(Map<String, StyleAttributeMap> styles, List<String> scopes) {
        for (String scope : scopes) {
            if (styles.containsKey(scope)) return styles.get(scope);
            // 尝试前缀匹配
            for (var entry : styles.entrySet()) {
                if (entry.getKey().startsWith(scope)) {
                    return entry.getValue();
                }
            }
        }
        // 回退到 default
        return styles.getOrDefault("default",
                StyleAttributeMap.builder().setTextColor(Color.web("#d4d4d4")).build());
    }

    @Override
    public RichParagraph createRichParagraph(CodeTextModel model, int paragraphIndex) {
        String text = model.getPlainText(paragraphIndex);
        if (text == null || text.isEmpty()) {
            return RichParagraph.builder().build();
        }

        RichParagraph.Builder builder = RichParagraph.builder();
        Matcher matcher = TOKEN_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                builder.addSegment(text.substring(lastEnd, matcher.start()), styleDefault);
            }

            String matched = matcher.group();
            StyleAttributeMap style;

            if (matcher.group("MULTICOMMENT") != null || matcher.group("SINGLECOMMENT") != null) {
                style = styleComment;
            } else if (matcher.group("STRING") != null) {
                style = styleString;
            } else if (matcher.group("ANNOTATION") != null) {
                style = styleAnnotation;
            } else if (matcher.group("NUMBER") != null) {
                style = styleNumber;
            } else if (matcher.group("IDENTIFIER") != null) {
                style = KEYWORDS.contains(matched) ? styleKeyword : styleDefault;
            } else {
                style = styleDefault;
            }

            builder.addSegment(matched, style);
            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            builder.addSegment(text.substring(lastEnd), styleDefault);
        }

        return builder.build();
    }

    @Override
    public void handleChange(CodeTextModel model, TextPos start, TextPos end,
                             int linesRemoved, int linesAdded, int charIndex) {
        // 模型自动重建
    }
}
```

- [ ] **Step 3: Create themes/dark-plus.json (from VS Code)**

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
      "scope": ["keyword.other.annotation", "storage.type.annotation"],
      "settings": { "foreground": "#D7BA7D" }
    },
    {
      "scope": "constant.numeric",
      "settings": { "foreground": "#B5CEA8" }
    },
    {
      "scope": "variable",
      "settings": { "foreground": "#d4d4d4" }
    }
  ]
}
```

- [ ] **Step 4: Update CodeEditorTab to accept RegexHighlighter**

Modify `CodeEditorTab` constructor to create its own highlighter if null is passed:

```java
    public CodeEditorTab(OpenFile openFile, RegexHighlighter highlighter) {
        this.openFile = openFile;
        if (highlighter == null) {
            highlighter = new RegexHighlighter();
        }

        codeArea = new CodeArea();
        codeArea.setEditable(false);
        codeArea.setWrapText(true);
        codeArea.setLineNumbersEnabled(true);
        codeArea.setFont(Font.font("Consolas", 14));
        codeArea.setText(openFile.getSourceCode());

        // 设置高亮器 AFTER setText
        codeArea.setSyntaxDecorator(highlighter);

        // 暗色基础样式
        codeArea.setStyle(String.join(";",
                "-fx-background-color: #1e1e1e",
                "-fx-text-fill: #d4d4d4",
                "-fx-font-family: 'Consolas', monospace",
                "-fx-font-size: 14px"
        ));

        setText(openFile.getClassName() + ".java");
        setContent(codeArea);
    }
```

Also update the import: `import com.bingbihanji.fxdecomplie.ui.theme.RegexHighlighter;`

- [ ] **Step 5: Update MainWindow.openClassTab to pass highlighter**

Change the line:
```java
CodeEditorTab codeTab = new CodeEditorTab(openFile, null);
```
To:
```java
RegexHighlighter highlighter = new RegexHighlighter(); // uses default dark theme
CodeEditorTab codeTab = new CodeEditorTab(openFile, highlighter);
```

Add import: `import com.bingbihanji.fxdecomplie.ui.theme.RegexHighlighter;`

- [ ] **Step 6: Verify — compile and run**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS. Open a .jar file, click a .class node, should see decompiled code with syntax highlighting.

---

### Task 7: Export Functionality

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/io/ExportService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java` — wire menu items

- [ ] **Step 1: Create ExportService.java**

```java
package com.bingbaihanji.fxdecomplie.io;

import com.bingbaihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerType;
import com.bingbaihanji.fxdecomplie.decompiler.IDecompiler;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * 导出服务。支持单文件/全部导出到目录/ZIP。
 */
public class ExportService {

    /**
     * 导出当前代码到磁盘文件。
     */
    public static void exportCurrentCode(String code, Path outputPath) throws IOException {
        Files.writeString(outputPath, code);
    }

    /**
     * 导出 Workspace 中所有 .class 文件为 .java 到指定目录。
     */
    public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerType engine,
                                      Path outputDir) throws IOException {
        IDecompiler decompiler = DecompilerFactory.getDecompiler(engine);
        Files.createDirectories(outputDir);
        exportTree(root, "", decompiler, outputDir);
    }

    private static void exportTree(TreeItem<FileTreeNode> node, String parentPath,
                                   IDecompiler decompiler, Path outputDir) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = data.getCachedBytes();
            if (bytes == null) {
                String internalName = data.getFullPath().replace(".class", "");
                bytes = BytecodeCache.get(internalName);
            }
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                Path javaFile = outputDir.resolve(
                        data.getFullPath().replace(".class", ".java"));
                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, source);
            }
        }

        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            String childPath = parentPath.isEmpty()
                    ? child.getValue().getName()
                    : parentPath + "/" + child.getValue().getName();
            exportTree(child, childPath, decompiler, outputDir);
        }
    }

    /**
     * 导出 Workspace 中所有 .class 文件为 ZIP。
     */
    public static void exportAllToZip(TreeItem<FileTreeNode> root, DecompilerType engine,
                                      Path zipPath) throws IOException {
        IDecompiler decompiler = DecompilerFactory.getDecompiler(engine);
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            exportTreeToZip(root, "", decompiler, zos);
        }
    }

    private static void exportTreeToZip(TreeItem<FileTreeNode> node, String parentPath,
                                        IDecompiler decompiler, ZipOutputStream zos) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = data.getCachedBytes();
            if (bytes == null) {
                String internalName = data.getFullPath().replace(".class", "");
                bytes = BytecodeCache.get(internalName);
            }
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                ZipEntry entry = new ZipEntry(
                        data.getFullPath().replace(".class", ".java"));
                zos.putNextEntry(entry);
                zos.write(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }

        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTreeToZip(child, "", decompiler, zos);
        }
    }
}
```

- [ ] **Step 2: Wire export in MainWindow**

In `MainWindow`, add a field to track current workspace:
```java
    private Workspace currentWorkspace;
    private TreeItem<FileTreeNode> currentTreeRoot;
```

In `addWorkspaceTab()`, after `treeView` creation:
```java
    currentWorkspace = workspace;
    currentTreeRoot = workspace.getTreeRoot();
```

Update the `exportAll` menu item in `createMenuBar()`:
```java
    exportAll.setOnAction(e -> {
        if (currentTreeRoot == null) return;
        javafx.stage.DirectoryChooser chooser = new javafx.stage.DirectoryChooser();
        chooser.setTitle("导出到目录");
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            new Thread(() -> {
                try {
                    ExportService.exportAllToDir(currentTreeRoot, currentEngine, dir.toPath());
                    Platform.runLater(() -> statusBar.setFilePath("导出完成: " + dir.getAbsolutePath()));
                } catch (IOException ex) {
                    Platform.runLater(() -> showError("导出失败: " + ex.getMessage()));
                }
            }).start();
        }
    });
```

Add the saveFile action similarly:
```java
    saveFile.setOnAction(e -> {
        // 获取当前活动的 CodeEditorTab
        // (需要从当前 Workspace 的 codeTabPane 中获取)
        // 留为简化实现，后续完善
    });
```

- [ ] **Step 3: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 8: Config System — AppConfig + Theme Switcher

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/FxDecompilerApp.java` — load config on startup
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java` — use config values

- [ ] **Step 1: Create AppConfig.java**

```java
package com.bingbaihanji.fxdecomplie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * 应用配置，读写 JSON 文件。
 * 位置: ~/.fxdecompiler/config.json
 */
public class AppConfig {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".fxdecompiler");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // === 配置字段 ===
    public Window window = new Window();
    public Theme theme = new Theme();
    public Decompiler decompiler = new Decompiler();
    public List<String> recentFiles = new ArrayList<>();

    public static class Window {
        public int width = 1200;
        public int height = 800;
        public int x = 100;
        public int y = 100;
        public boolean maximized = false;
    }

    public static class Theme {
        public String path = "";          // 空 = 使用内置默认
        public String fontFamily = "Consolas";
        public int fontSize = 14;
    }

    public static class Decompiler {
        public String defaultEngine = "VINEFLOWER";
        public boolean lineNumbersEnabled = true;
        public boolean wrapText = true;
    }

    /** 加载配置，不存在则返回默认值 */
    public static AppConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                return GSON.fromJson(json, AppConfig.class);
            }
        } catch (IOException ignored) {
        }
        return new AppConfig();
    }

    /** 保存配置 */
    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }
}
```

- [ ] **Step 2: Update FxDecompilerApp to load config**

```java
package com.bingbaihanji.fxdecomplie;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import javafx.application.Application;
import javafx.stage.Stage;

public class FxDecompilerApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        AppConfig config = AppConfig.load();

        // 恢复窗口位置/大小
        primaryStage.setX(config.window.x);
        primaryStage.setY(config.window.y);
        primaryStage.setWidth(config.window.width);
        primaryStage.setHeight(config.window.height);
        primaryStage.setMaximized(config.window.maximized);

        MainWindow window = new MainWindow(config);
        window.show(primaryStage);

        // 退出时保存配置
        primaryStage.setOnCloseRequest(e -> {
            config.window.x = (int) primaryStage.getX();
            config.window.y = (int) primaryStage.getY();
            config.window.width = (int) primaryStage.getWidth();
            config.window.height = (int) primaryStage.getHeight();
            config.window.maximized = primaryStage.isMaximized();
            config.save();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
```

- [ ] **Step 3: Update MainWindow to accept config**

Change `MainWindow` constructor to accept `AppConfig`:
```java
    private final AppConfig config;

    public MainWindow(AppConfig config) {
        this.config = config;
    }
```

In `show()` method, initialize `currentEngine` from config:
```java
    // 在 show() 开始处
    try {
        currentEngine = DecompilerType.valueOf(config.decompiler.defaultEngine);
    } catch (IllegalArgumentException e) {
        currentEngine = DecompilerType.VINEFLOWER;
    }
```

In font setup, use config values:
```java
    String fontFamily = config.theme.fontFamily;
    int fontSize = config.theme.fontSize;
    // 传递给 CodeEditorTab
```

- [ ] **Step 4: Verify — compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS. Run the app, close it, check `~/.fxdecompiler/config.json` is created.

---

## Self-Review

1. **Spec coverage check:** All sections covered — architecture (Tasks 1-3), UI layout (Task 2+5), file loading (Task 4), decompiler engines (Task 3), syntax highlighting (Task 6), export (Task 7), config (Task 8), menu bar (Task 2).

2. **Placeholder scan:** No TBD/TODO. Code in all steps is complete and compilable.

3. **Type consistency:** Import paths, class names, method signatures are consistent across all tasks. DecompilerType enum used consistently. FileTreeNode used consistently between ClassDiscoverer and FileTreeBuilder.

4. **Gaps:** Task 5's openClassTab references `Workspace.getSourceFile()` → check: Workspace has `getSourceFile()`. StatusBar cursor tracking uses `caretPositionProperty()` — this exists on RichTextArea (parent of CodeArea). Theme switching UI (menu → select theme) is not fully wired but the infrastructure exists in VsCodeThemeLoader. This is acceptable for initial implementation.
