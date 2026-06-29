# 编辑器配色管理 — 设计规格

> **日期:** 2026-06-29
> **状态:** 已批准

## 目标

在设置窗口"界面"标签页新增编辑器配色管理功能：内置 Dark+ 作为默认暗色配色，支持导入外部 VS Code 主题 JSON 文件、导出当前配色、通过下拉框切换配色。外部配色文件存放在应用目录下（非 user.home）。

## 架构

```
SettingsDialog "界面" 标签页
  ├── 下拉框: 列出所有可用主题（内置 + 外部）
  ├── 导入按钮: FileChooser → 验证 JSON → 复制到 <appDir>/themes/
  ├── 导出按钮: 将当前选中主题序列化为 VS Code JSON → 用户选择保存路径
  ├── 删除按钮: 删除外部主题文件（内置不可删除）
  └── 所有操作仅修改 UI 状态，点击"应用"后持久化并刷新所有已打开标签页

ThemeManager (新增)
  ├── 内置主题: dark-plus.json (classpath 资源, 不可删除)
  ├── 外部主题: <appDir>/themes/*.json (文件系统)
  ├── resolveThemeData(name) → ThemeData (委托 VsCodeThemeLoader)
  ├── importTheme(sourcePath) → 复制到 themes/ 目录
  └── exportTheme(name, targetPath) → 写 VS Code JSON
```

## 文件变更

### 新增

| 文件 | 职责 |
|------|------|
| `ui/theme/ThemeManager.java` | 主题发现、加载、导入、导出、删除 |

### 修改

| 文件 | 变更 |
|------|------|
| `config/AppConfig.java` | `Theme` 类新增 `editorTheme` 字段 (默认 "Dark+") |
| `ui/theme/AppTheme.java` | `loadEditorTheme()` 改为通过 ThemeManager 按名称加载 |
| `ui/settings/SettingsDialog.java` | "界面"标签页新增配色下拉框 + 导入/导出/删除按钮 |
| `resources/language/language_zh_CN.properties` | 新增 8 个 i18n key |
| `resources/language/language_en.properties` | 新增 8 个 i18n key |

## 数据模型

### AppConfig.Theme 新增字段

```java
private String editorTheme = "";  // "" 或 "Dark+" = 内置默认
```

normalize() 中: `if (editorTheme == null || editorTheme.isBlank()) editorTheme = "Dark+";`

### config.json 格式示例

```json
{
  "theme": {
    "path": "",
    "fontFamily": "Consolas",
    "fontSize": 14,
    "editorTheme": "Solarized Dark"
  }
}
```

向后兼容: 旧配置缺少 `editorTheme` 字段时，normalize() 自动填充 "Dark+"。

### themes 目录结构

```
<appDir>/
  themes/
    ├── Solarized Dark.json
    └── Monokai.json
-- config/
    └── config.json
```

## ThemeManager 接口

```java
public final class ThemeManager {

    /** 内置主题名列表（当前仅 "Dark+"） */
    public static List<String> getBuiltinThemes();

    /** 从 <appDir>/themes/ 扫描外部主题名列表 */
    public static List<String> getExternalThemes();

    /** 所有可用主题名：内置在前，外部在后 */
    public static List<String> getAllThemes();

    /** 按名称解析 ThemeData：内置走 classpath，外部走文件 */
    public static VsCodeThemeLoader.ThemeData resolveThemeData(String themeName);

    /** 导入外部 JSON → 复制到 <appDir>/themes/，重名追加序号，返回实际主题名 */
    public static String importTheme(Path sourceJsonFile) throws IOException;

    /** 导出主题为 VS Code JSON 文件 */
    public static void exportTheme(String themeName, Path targetFile) throws IOException;

    /** 删除外部主题文件，内置不可删除 */
    public static boolean deleteExternalTheme(String themeName);

    /** themes 目录路径 */
    public static Path themesDir();
}
```

## UI 布局

设置 → 界面标签页，在现有控件（字体大小、字体、行号、自动换行）之后新增：

```
编辑器配色:
┌──────────────────────┐  ┌──────────┐  ┌──────────┐
│ Dark+            ▾   │  │ 导入...  │  │ 导出...  │
└──────────────────────┘  └──────────┘  └──────────┘
                          [删除选中配色]  (仅外部主题可用)
```

## 行为规格

### 导入

1. 点击"导入外部配色..."→ FileChooser（扩展名过滤器: *.json）
2. ThemeManager.importTheme() 尝试解析 JSON（通过 VsCodeThemeLoader.parse() 验证）
3. 解析失败 → 弹出错误提示 "无效的配色文件: {reason}"
4. 解析成功 → 复制到 `<appDir>/themes/<name>.json`
5. 目标已存在 → 自动追加序号: `name (2).json`
6. 刷新下拉框列表，自动选中新导入的主题
7. 仅修改 UI 状态，不立即持久化（等待用户点击"应用"）

### 导出

1. 选中下拉框中的任一主题（内置或外部均可）
2. 点击"导出当前配色..."→ FileChooser（建议文件名: `<themeName>.json`）
3. ThemeManager.exportTheme() 将 ThemeData 序列化为完整 VS Code 格式 JSON
4. 写入用户选择的路径

### 删除

1. 仅当选中外部主题时按钮可用
2. 点击 → 确认对话框 "确定要删除配色 "{name}" 吗?"
3. 确认 → ThemeManager.deleteExternalTheme() 删除文件
4. 下拉框回退选中 "Dark+"

### 应用

1. 用户点击"应用"按钮（和现有 SettingsDialog 行为一致）
2. config.theme().editorTheme = 下拉框选中值
3. config.save() 持久化
4. 如果 editorTheme 发生变化 → MainWindow 遍历所有已打开标签页 → 重新创建 RegexHighlighter(newThemeData) → 刷新高亮

### 启动加载

1. AppTheme.loadEditorTheme(config) 读取 config.theme().editorTheme()
2. 空或 "Dark+" → 加载内置 dark-plus.json
3. 其他 → ThemeManager.resolveThemeData(name) → 外部文件加载
4. 外部文件不存在或损坏 → 日志 warn，回退到 Dark+

## 边界情况

| 场景 | 处理 |
|------|------|
| 外部主题文件被手动删除 | resolveThemeData() 捕获 IOException，回退 Dark+ |
| 导入非 JSON 文件 | VsCodeThemeLoader 解析失败 → 弹错误提示 |
| 导入无效主题 JSON (缺少必需字段) | 允许导入（仅验证 JSON 语法），缺少字段在加载时回退默认值 |
| 删除当前选中主题 | 自动回退选中 "Dark+" |
| themes 目录不存在 | 首次导入时自动创建 |
| 名称冲突 | 追加序号: "name (N).json"，N 从 2 开始递增 |
| 导出当前使用的主题 | 允许，不影响运行中状态 |
| 旧配置无 editorTheme 字段 | normalize() 自动填充 "Dark+" |

## 国际化 Key

| Key | zh_CN | en |
|-----|-------|----|
| `settings.editorTheme` | 编辑器配色: | Editor Theme: |
| `settings.editorTheme.import` | 导入外部配色... | Import Theme... |
| `settings.editorTheme.export` | 导出当前配色... | Export Theme... |
| `settings.editorTheme.delete` | 删除选中配色 | Delete Selected Theme |
| `settings.editorTheme.importTitle` | 选择配色文件 | Select Theme File |
| `settings.editorTheme.exportTitle` | 导出配色文件 | Export Theme File |
| `settings.editorTheme.deleteConfirm` | 确定要删除配色 "{0}" 吗？ | Delete theme "{0}"? |
| `settings.editorTheme.invalidJson` | 无效的配色文件：{0} | Invalid theme file: {0} |
