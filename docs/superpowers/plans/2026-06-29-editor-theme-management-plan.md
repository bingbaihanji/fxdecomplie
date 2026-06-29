# 编辑器配色管理 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在设置"界面"标签页新增编辑器配色管理：内置 Dark+ 默认暗色，支持导入/导出/删除/切换 VS Code 主题 JSON 文件，外部主题存储在 `<appDir>/themes/` 目录。

**Architecture:** 新增 `ThemeManager` 作为主题发现/加载/导入/导出中枢；`AppConfig.Theme` 新增 `editorTheme` 字段存储当前选中主题名；`SettingsDialog` "界面"标签页新增下拉框和操作按钮；`MainWindow.applySettings()` 检测主题变更后遍历所有已打开标签页重新应用高亮。

**Tech Stack:** Java 25, JavaFX 25.0.1, Gson 2.12.1, SLF4J, JUnit 5.12.1

**Spec:** `docs/superpowers/specs/2026-06-29-editor-theme-management-design.md`

---

### Task 1: AppConfig.Theme 新增 editorTheme 字段

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java`

- [ ] **Step 1: 在 Theme 类中添加 editorTheme 字段和访问器**

在 `Theme` 类的 `fontSize` 字段之后 (约 L387) 添加:

```java
/** 编辑器配色主题名称，"" 或 "Dark+" 表示内置默认 */
private String editorTheme = "";

public String editorTheme() {
    return editorTheme;
}

public void editorTheme(String v) {
    editorTheme = v;
}
```

- [ ] **Step 2: 更新 Theme.toString() 包含新字段**

修改 `Theme.toString()` (约 L415-417):

```java
@Override
public String toString() {
    return "Theme{path='" + path + "', editorTheme='" + editorTheme
            + "', fontFamily='" + fontFamily + "', fontSize=" + fontSize + "}";
}
```

- [ ] **Step 3: 在 normalize() 中添加 editorTheme 默认值**

在 `normalize()` 方法中 `theme.path` 的空值检查之后 (L285 之后) 添加:

```java
if (theme.editorTheme == null || theme.editorTheme.isBlank()) {
    theme.editorTheme = "Dark+";
}
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java
git commit -m "feat: add editorTheme field to AppConfig.Theme"
```

---

### Task 2: 创建 ThemeManager 工具类

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/ThemeManager.java`

- [ ] **Step 1: 创建 ThemeManager.java**

完整内容:

```java
package com.bingbaihanji.fxdecomplie.ui.theme;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * 编辑器主题管理器。
 *
 * <p>内置主题从 classpath 加载（当前仅 "Dark+" 对应 dark-plus.json），
 * 外部主题以 JSON 文件形式存储在 {@code <appDir>/themes/} 目录下，
 * 由用户通过设置界面导入管理。</p>
 *
 * @author bingbaihanji
 * @date 2026-06-29
 */
public final class ThemeManager {

    private static final Logger logger = LoggerFactory.getLogger(ThemeManager.class);

    /** 内置暗色主题 classpath 资源路径 */
    private static final String DARK_PLUS_RESOURCE =
            "/com/bingbaihanji/fxdecomplie/themes/dark-plus.json";

    /** 内置主题名列表（不可变） */
    private static final List<String> BUILTIN_THEMES = List.of("Dark+");

    private ThemeManager() {
        throw new AssertionError("utility class");
    }

    /** @return 内置主题名列表 */
    public static List<String> getBuiltinThemes() {
        return BUILTIN_THEMES;
    }

    /** @return themes 目录路径，首次调用时自动创建 */
    public static Path themesDir() {
        Path dir = AppConfig.appDir().resolve("themes");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            logger.warn("创建 themes 目录失败: {}", dir, e);
        }
        return dir;
    }

    /**
     * 扫描 {@code <appDir>/themes/} 目录返回外部主题名称列表。
     * 每个 JSON 文件名去掉 .json 后缀即为主题名。
     */
    public static List<String> getExternalThemes() {
        Path dir = themesDir();
        if (!Files.isDirectory(dir)) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().endsWith(".json"))
                    .map(f -> {
                        String name = f.getFileName().toString();
                        return name.substring(0, name.length() - 5);
                    })
                    .forEach(names::add);
        } catch (IOException e) {
            logger.warn("扫描外部主题目录失败: {}", dir, e);
        }
        Collections.sort(names);
        return names;
    }

    /** @return 所有可用主题名：内置在前，外部在后 */
    public static List<String> getAllThemes() {
        List<String> all = new ArrayList<>(BUILTIN_THEMES);
        all.addAll(getExternalThemes());
        return all;
    }

    /**
     * 按名称解析 ThemeData：内置走 classpath，外部从 themes 目录加载。
     * 加载失败时回退到 Dark+。
     */
    public static VsCodeThemeLoader.ThemeData resolveThemeData(String themeName) {
        if (themeName == null || themeName.isBlank() || "Dark+".equals(themeName)) {
            return loadBuiltinDarkPlus();
        }
        Path file = themesDir().resolve(themeName + ".json");
        if (Files.isRegularFile(file)) {
            try {
                return VsCodeThemeLoader.load(file);
            } catch (IOException | RuntimeException e) {
                logger.warn("加载外部主题失败 [{}]，回退到 Dark+", themeName, e);
            }
        } else {
            logger.warn("外部主题文件不存在 [{}]，回退到 Dark+", file);
        }
        return loadBuiltinDarkPlus();
    }

    /**
     * 导入外部主题：先验证 JSON 有效性，再复制到 themes 目录。
     * 重名时自动追加序号 "name (2)", "name (3)" ...
     *
     * @param sourceFile 源 JSON 文件
     * @return 导入后的主题名（不含 .json 扩展名）
     * @throws IOException 读取/写入失败，或 JSON 解析失败
     */
    public static String importTheme(Path sourceFile) throws IOException {
        VsCodeThemeLoader.ThemeData themeData = VsCodeThemeLoader.load(sourceFile);
        String baseName = themeData.name();
        if (baseName == null || baseName.isBlank()) {
            String fileName = sourceFile.getFileName().toString();
            baseName = fileName.endsWith(".json")
                    ? fileName.substring(0, fileName.length() - 5)
                    : fileName;
        }

        String resolvedName = baseName;
        Path dest = themesDir().resolve(resolvedName + ".json");
        int seq = 2;
        while (Files.exists(dest)) {
            resolvedName = baseName + " (" + seq + ")";
            dest = themesDir().resolve(resolvedName + ".json");
            seq++;
        }

        Files.copy(sourceFile, dest, StandardCopyOption.REPLACE_EXISTING);
        logger.info("已导入外部主题: {} → {}", sourceFile, dest);
        return resolvedName;
    }

    /**
     * 导出主题为 VS Code 格式 JSON 文件。
     * 内置主题直接复制 classpath 资源；外部主题直接复制源文件。
     */
    public static void exportTheme(String themeName, Path targetFile) throws IOException {
        if ("Dark+".equals(themeName)) {
            try (var in = ThemeManager.class.getResourceAsStream(DARK_PLUS_RESOURCE)) {
                if (in == null) {
                    throw new IOException("内置主题资源未找到: " + DARK_PLUS_RESOURCE);
                }
                Files.copy(in, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Path source = themesDir().resolve(themeName + ".json");
            if (!Files.isRegularFile(source)) {
                throw new IOException("主题文件不存在: " + source);
            }
            Files.copy(source, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        logger.info("已导出主题 [{}] 到: {}", themeName, targetFile);
    }

    /**
     * 删除外部主题文件。内置主题不可删除。
     *
     * @return true 如果删除成功
     */
    public static boolean deleteExternalTheme(String themeName) {
        if (themeName == null || themeName.isBlank() || "Dark+".equals(themeName)) {
            logger.warn("不允许删除内置主题: {}", themeName);
            return false;
        }
        Path file = themesDir().resolve(themeName + ".json");
        try {
            boolean deleted = Files.deleteIfExists(file);
            if (deleted) {
                logger.info("已删除外部主题: {}", themeName);
            }
            return deleted;
        } catch (IOException e) {
            logger.warn("删除外部主题失败: {}", themeName, e);
            return false;
        }
    }

    private static VsCodeThemeLoader.ThemeData loadBuiltinDarkPlus() {
        try {
            return VsCodeThemeLoader.loadResource(DARK_PLUS_RESOURCE);
        } catch (IOException | RuntimeException e) {
            logger.warn("加载内置 Dark+ 失败，使用硬编码默认值", e);
            return VsCodeThemeLoader.defaultDark();
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/ThemeManager.java
git commit -m "feat: add ThemeManager for editor theme discovery, import, export, and deletion"
```

---

### Task 3: 修改 AppTheme.loadEditorTheme 委托给 ThemeManager

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/AppTheme.java`

- [ ] **Step 1: 重构 loadEditorTheme()**

将 `AppTheme.loadEditorTheme()` (L48-59) 替换为:

```java
/**
 * 加载编辑器主题。
 *
 * @param config 应用配置
 * @return 主题数据（通过 ThemeManager 按名称加载，失败时回退到硬编码默认值）
 */
public static VsCodeThemeLoader.ThemeData loadEditorTheme(AppConfig config) {
    String themeName = config.theme().editorTheme();
    try {
        if (themeName != null && !themeName.isBlank()) {
            return ThemeManager.resolveThemeData(themeName);
        }
        return ThemeManager.resolveThemeData("Dark+");
    } catch (RuntimeException e) {
        logger.warn("加载编辑器主题失败，使用默认暗色主题", e);
        return VsCodeThemeLoader.defaultDark();
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/theme/AppTheme.java
git commit -m "refactor: AppTheme.loadEditorTheme delegates to ThemeManager"
```

---

### Task 4: 为 CodeEditorTab 和 SourceContentPanel 添加主题重新应用能力

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/SourceContentPanel.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java`

- [ ] **Step 1: 在 SourceContentPanel 添加 reapplyTheme() 方法**

在 `SourceContentPanel` 类中 (约 L90，`setTokenNavigateHandler()` 方法之后) 添加:

```java
/**
 * 用新主题数据重建语法高亮器并应用到 CodeArea。
 * 仅在 codeArea 已创建时生效。
 */
public void reapplyTheme(VsCodeThemeLoader.ThemeData newTheme) {
    if (codeArea == null) {
        return;
    }
    try {
        codeArea.setSyntaxDecorator(new RegexHighlighter(newTheme));
    } catch (Exception e) {
        logger.warn("重新应用编辑器主题失败", e);
    }
}
```

- [ ] **Step 2: 在 CodeEditorTab 添加 reapplyTheme() 方法**

将 `theme` 字段从 `final` 改为非 final:

```java
// 将 L35 的:
// private final VsCodeThemeLoader.ThemeData theme;
// 改为:
private VsCodeThemeLoader.ThemeData theme;
```

然后在 `CodeEditorTab` 类中 (约 L395，`applyFontSettings` 方法之后) 添加:

```java
/** 重新应用编辑器语法高亮主题到源码面板 */
public void reapplyTheme(VsCodeThemeLoader.ThemeData newTheme) {
    this.theme = newTheme;
    SourceContentPanel srcPanel = codeViewPanel != null
            ? codeViewPanel.getDeck() != null
                ? codeViewPanel.getDeck().getSourcePanel()
                : null
            : null;
    if (srcPanel != null) {
        srcPanel.reapplyTheme(newTheme);
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/code/SourceContentPanel.java src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeEditorTab.java
git commit -m "feat: add reapplyTheme() to CodeEditorTab and SourceContentPanel for theme switching"
```

---

### Task 5: 修改 MainWindow 支持主题切换时刷新标签页

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java`

- [ ] **Step 1: 将 editorTheme 字段从 final 改为非 final**

```java
// 将 L66 的:
// private final VsCodeThemeLoader.ThemeData editorTheme;
// 改为:
private VsCodeThemeLoader.ThemeData editorTheme;
```

- [ ] **Step 2: 在 applySettings() 方法中添加主题变更检测和刷新逻辑**

在 `applySettings()` 方法末尾 (L1625-1634，`return engineSwitched;` 之前) 添加:

```java
// 检测编辑器主题变更，重新应用语法高亮
String newEditorTheme = updated.theme().editorTheme();
VsCodeThemeLoader.ThemeData newTheme = AppTheme.loadEditorTheme(updated);
if (!newEditorTheme.equals(config.theme().editorTheme())
        || !newTheme.equals(editorTheme)) {
    editorTheme = newTheme;
    tabManager.getWorkspaceViews().values().forEach(view ->
            view.splitEditorPane().forEachTab(tab ->
                    tab.reapplyTheme(newTheme))
    );
}
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/MainWindow.java
git commit -m "feat: detect editor theme change in applySettings and refresh all tabs"
```

---

### Task 6: 添加国际化 Key

**Files:**
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: 获取当前行号范围**

查看两个文件末尾，确定追加位置。

```bash
wc -l src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
```

- [ ] **Step 2: 在 language_zh_CN.properties 末尾追加**

```properties
settings.editorTheme=编辑器配色:
settings.editorTheme.import=导入外部配色...
settings.editorTheme.export=导出当前配色...
settings.editorTheme.delete=删除选中配色
settings.editorTheme.importTitle=选择配色文件
settings.editorTheme.exportTitle=导出配色文件
settings.editorTheme.deleteConfirm=确定要删除配色 "{0}" 吗？
settings.editorTheme.invalidJson=无效的配色文件：{0}
dialog.confirm.title=确认
```

**注:** 以上 Unicode 转义对应:
- `编辑器配色:` / `导入外部配色...` / `导出当前配色...` / `删除选中配色`
- `选择配色文件` / `导出配色文件` / `确定要删除配色 "{0}" 吗？` / `无效的配色文件：{0}`

- [ ] **Step 3: 在 language_en.properties 末尾追加**

```properties
settings.editorTheme=Editor Theme:
settings.editorTheme.import=Import Theme...
settings.editorTheme.export=Export Theme...
settings.editorTheme.delete=Delete Selected Theme
settings.editorTheme.importTitle=Select Theme File
settings.editorTheme.exportTitle=Export Theme File
settings.editorTheme.deleteConfirm=Delete theme "{0}"?
settings.editorTheme.invalidJson=Invalid theme file: {0}
dialog.confirm.title=Confirm
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
git commit -m "i18n: add editor theme management keys"
```

---

### Task 7: 修改 SettingsDialog "界面"标签页添加配色管理 UI

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java`

- [ ] **Step 1: 在 "界面" 标签页末尾（L180 之前）添加配色管理控件**

替换 `uiTab.setContent(...)` 调用 (L177-180)，在 VBox 中追加编辑器配色区域:

将 L177-180 的:
```java
uiTab.setContent(new VBox(10,
        new Label(I18nUtil.getString("settings.fontSize")), fontSizeSpinner,
        new Label(I18nUtil.getString("settings.fontFamily")), fontFamilyCombo,
        lineNumCheck, wrapCheck));
```

替换为:
```java
// 编辑器配色下拉框
ComboBox<String> editorThemeCombo = new ComboBox<>();
editorThemeCombo.getItems().addAll(ThemeManager.getAllThemes());
String currentEditorTheme = config.theme().editorTheme();
if (currentEditorTheme == null || currentEditorTheme.isBlank()) {
    currentEditorTheme = "Dark+";
}
editorThemeCombo.setValue(currentEditorTheme);

// 导入按钮
Button importThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.import"));
// 导出按钮
Button exportThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.export"));
// 删除按钮
Button deleteThemeBtn = new Button(I18nUtil.getString("settings.editorTheme.delete"));
deleteThemeBtn.setDisable("Dark+".equals(currentEditorTheme));

HBox themeBtnBar = new HBox(8, importThemeBtn, exportThemeBtn, deleteThemeBtn);
VBox themeSection = new VBox(6,
        new Label(I18nUtil.getString("settings.editorTheme")),
        editorThemeCombo,
        themeBtnBar);

uiTab.setContent(new VBox(10,
        new Label(I18nUtil.getString("settings.fontSize")), fontSizeSpinner,
        new Label(I18nUtil.getString("settings.fontFamily")), fontFamilyCombo,
        lineNumCheck, wrapCheck,
        new javafx.scene.control.Separator(),
        themeSection));
```

- [ ] **Step 2: 添加 Dropdown 变化的处理 — 控制删除按钮状态**

```java
editorThemeCombo.valueProperty().addListener((obs, old, val) -> {
    if (val != null) {
        deleteThemeBtn.setDisable("Dark+".equals(val)
                || ThemeManager.getBuiltinThemes().contains(val));
    }
});
```

- [ ] **Step 3: 添加导入按钮事件处理**

```java
importThemeBtn.setOnAction(e -> {
    javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
    fileChooser.setTitle(I18nUtil.getString("settings.editorTheme.importTitle"));
    fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
    Path selected = fileChooser.showOpenDialog(dialog.getDialogPane().getScene().getWindow());
    if (selected != null) {
        try {
            String importedName = ThemeManager.importTheme(selected);
            refreshThemeCombo(editorThemeCombo, importedName);
        } catch (Exception ex) {
            logger.warn("导入主题失败", ex);
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR);
            alert.setTitle(I18nUtil.getString("dialog.error.title"));
            alert.setHeaderText(null);
            alert.setContentText(I18nUtil.getString("settings.editorTheme.invalidJson",
                    ex.getMessage()));
            alert.showAndWait();
        }
    }
});
```

- [ ] **Step 4: 添加导出按钮事件处理**

```java
exportThemeBtn.setOnAction(e -> {
    String selectedTheme = editorThemeCombo.getValue();
    if (selectedTheme == null || selectedTheme.isBlank()) {
        return;
    }
    javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
    fileChooser.setTitle(I18nUtil.getString("settings.editorTheme.exportTitle"));
    fileChooser.setInitialFileName(selectedTheme + ".json");
    fileChooser.getExtensionFilters().add(
            new javafx.stage.FileChooser.ExtensionFilter("JSON Files", "*.json"));
    Path target = fileChooser.showSaveDialog(dialog.getDialogPane().getScene().getWindow());
    if (target != null) {
        try {
            ThemeManager.exportTheme(selectedTheme, target);
        } catch (Exception ex) {
            logger.warn("导出主题失败", ex);
        }
    }
});
```

- [ ] **Step 5: 添加删除按钮事件处理**

```java
deleteThemeBtn.setOnAction(e -> {
    String selectedTheme = editorThemeCombo.getValue();
    if (selectedTheme == null || "Dark+".equals(selectedTheme)
            || ThemeManager.getBuiltinThemes().contains(selectedTheme)) {
        return;
    }
    javafx.scene.control.Alert confirm = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.CONFIRMATION);
    confirm.setTitle(I18nUtil.getString("dialog.confirm.title"));
    confirm.setHeaderText(null);
    confirm.setContentText(I18nUtil.getString("settings.editorTheme.deleteConfirm",
            selectedTheme));
    confirm.showAndWait().ifPresent(response -> {
        if (response == ButtonType.OK) {
            ThemeManager.deleteExternalTheme(selectedTheme);
            refreshThemeCombo(editorThemeCombo, "Dark+");
        }
    });
});
```

- [ ] **Step 6: 添加 refreshThemeCombo 辅助方法**

在 SettingsDialog 类的末尾 (约 L430) 添加私有静态辅助方法:

```java
/** 刷新编辑器配色下拉框：重新扫描主题列表并选中指定主题 */
private static void refreshThemeCombo(ComboBox<String> combo, String selectName) {
    String currentValue = combo.getValue();
    combo.getItems().setAll(ThemeManager.getAllThemes());
    if (selectName != null && combo.getItems().contains(selectName)) {
        combo.setValue(selectName);
    } else if (currentValue != null && combo.getItems().contains(currentValue)) {
        combo.setValue(currentValue);
    } else {
        combo.setValue("Dark+");
    }
}
```

- [ ] **Step 7: 在 APPLY 处理中保存 editorTheme**

在 `ButtonType.APPLY` 分支中 (L280-335，紧接语言设置周围合适位置，约 L331 之后) 添加:

```java
// 保存编辑器配色
if (editorThemeCombo.getValue() != null) {
    config.theme().editorTheme(editorThemeCombo.getValue());
}
```

- [ ] **Step 8: 添加 import (ThemeManager 和 Button)**

在 SettingsDialog 的 import 区域添加:

```java
import com.bingbaihanji.fxdecomplie.ui.theme.ThemeManager;
import javafx.scene.control.Button;
```

`java.nio.file.Path` 可能已通过其他 import 可用（检查现有 import 是否有 `java.nio.file.Path`，Gson 相关的已有 `java.util.*`）。

- [ ] **Step 9: 编译验证**

```bash
./mvnw clean compile -DskipTests
```
Expected: BUILD SUCCESS

- [ ] **Step 10: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java
git commit -m "feat: add editor theme management UI to SettingsDialog UI tab"
```

---

### Task 8: 完整测试与验证

- [ ] **Step 1: 运行全部测试**

```bash
./mvnw test
```
Expected: All tests pass

- [ ] **Step 2: 手动验证**

启动应用，验证以下场景:
1. 设置 → 界面 → "编辑器配色" 下拉框默认选中 "Dark+"
2. 点击"导入" → 选择一个 VS Code 主题 JSON → 下拉框自动选中新主题
3. 点击"应用" → 所有已打开代码标签页高亮颜色更新
4. 重新打开设置 → 下拉框仍显示上次选中的主题
5. 删除外部主题 → 自动回退到 "Dark+"
6. 启动应用 → 默认使用上次选中的主题

- [ ] **Step 3: 提交（如有修正）**

```bash
git add -u
git commit -m "chore: final adjustments for editor theme management"
```
