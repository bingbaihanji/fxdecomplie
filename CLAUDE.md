# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建/运行

- **编译:** `./mvnw clean compile`（跳过测试: `-DskipTests`）
- **打包 fat JAR:** `./mvnw clean package -DskipTests`（输出到 `bin/fxdecomplie.jar`）
- **运行:** `./mvnw exec:java`
- **运行单个测试:** `./mvnw test -Dtest="TestClassName"`
- **运行全部测试:** `./mvnw test`

启动参数：`--open <path>` 可在启动时自动打开指定 JAR/ZIP/目录。

Windows 上可用 `.\mvnw.cmd` 替代 `./mvnw`。

## 技术栈

- **JDK 25**（源码/目标 25）+ JavaFX 25.0.1
- **jfx.incubator.richtext** CodeArea 用于代码展示 — 编译需要 `--add-modules javafx.controls,javafx.graphics,javafx.base,javafx.fxml,javafx.swing,jfx.incubator.richtext`
- **四个反编译引擎:** Vineflower (1.11.2), CFR (0.152), Procyon (0.6.0 core + compilertools), JD-Core (1.1.3)
- **ASM 9.9**（asm + asm-util）用于字节码视图、类信息视图、继承分析
- **Caffeine 3.2.0** 用于 `BytecodeCache`（5000 条目, 30 分钟 TTL, 软引用值）
- **SLF4J 2.0.17 + Logback 1.5.18**（`src/main/resources/logback.xml` 配置，DEBUG 级日志对 `com.bingbaihanji` 生效）
- **Gson 2.12.1** 用于配置 JSON 和 VS Code 主题 JSON 解析
- **JNA 5.17.0**（jna + jna-platform）用于 Windows 平台原生窗口特性（自定义标题栏等）
- **JUnit 5.12.1** 测试框架
- **Maven Shade 3.6.0** 插件生成 fat JAR，主类 `AppLauncher`，排除 `module-info.class`；exec-maven-plugin 用于开发期运行

## 模块系统

项目使用 `module-info.java`（模块名 `fxdecomplie`）：
- `exports com.bingbaihanji.fxdecomplie.decompiler` — 导出反编译器包
- `opens ... to com.google.gson` — `config`、`model`、`decompiler` 包对 Gson 开放反射访问
- `opens ... to com.sun.jna` — `platform.win32` 包对 JNA 开放（Windows 原生 Structure 反射）
- Shade 打包时排除所有 `module-info.class`（fat JAR 运行在 classpath 模式）

## 包结构

| 包 | 职责 |
|---|---|
| `config/` | `AppConfig` POJO：窗口几何、主题、反编译器偏好、导出/搜索默认值、语言、最近文件。JSON 持久化到 `<appDir>/config/config.json` |
| `decompiler/` | `Decompiler` 接口 + 四个引擎实现 + `DecompilerFactory`（单例缓存）+ `BytecodeCache`（Caffeine 全局缓存）+ `DecompilerContext`（携带 `WorkspaceIndex`） |
| `model/` | 数据对象：`FileTreeNode`、`Workspace`（含 `WorkspaceIndex`）、`OpenFile`、`CodeMetadata`、PathNode 体系（`ClassPathNode`/`ResourcePathNode`/`WorkspacePathNode`/`AbstractPathNode`）、`DecompilerProject`、`ExportConfig`/`ExportResult` 等 |
| `service/` | 业务逻辑：`ClassDiscoverer`、`FileTreeBuilder`、`ExportService`、`BackgroundTasks`（线程池）、`ClassTabOpener`（三级缓存反编译流水线）、`SearchService`、`NavigationService`、`DecompileCache`（L2 内存）、`DiskCodeCache`（L3 磁盘）、`UsageSearchService`、`ProcessService`（启动新窗口） |
| `ui/` | JavaFX 视图：`MainMenuBar`、`CodeEditorTab`、`FileTreeView`、`SearchDialog`、`SettingsDialog`、`ExportDialog`、`QuickOpenDialog`、`FindUsageDialog`、`WorkspaceTabManager`、`StatusBar`、主题系统（`AppTheme`、`VsCodeThemeLoader`、`RegexHighlighter`）、大纲/继承工具窗口 |
| `utils/` | `I18nUtil`（国际化，动态加载外部或 classpath 语言文件）、`CodeLinkHandler`（Ctrl+Click 导航） |

入口点：`AppLauncher.main()` → `FxDecompilerApp.main()` → `Application.launch(FxApplication.class)`。

## 架构要点

### 线程模型

所有耗时操作（文件发现、反编译、搜索、导出）通过 `BackgroundTasks.run(name, task)` 提交到守护线程池（0-core, 8-max, 60s 存活, `LinkedBlockingQueue`）执行。UI 更新通过 `Platform.runLater()` 回到 JavaFX 线程。`BackgroundTasks.cancel(future)` 安全取消任务。

切换类文件时先取消当前反编译任务再提交新任务。`ClassTabOpener` 在反编译前调用 `Thread.interrupted()` 清除线程池复用的残留中断标志，并在 catch 中通过 `isInterruptRelated()`（递归检查异常链 + 当前线程中断标志）过滤中断相关异常，避免弹窗报错。

### 三级缓存

反编译结果走三级缓存（`ClassTabOpener.decompileWithCache()`）：L2 内存缓存（`DecompileCache`，跨工作区共享）→ L3 磁盘持久化缓存（`DiskCodeCache`）→ 实际反编译。缓存键包含工作区路径 + mtime + size 防止同路径替换文件命中旧缓存。L3 命中时回填 L2；反编译结果同时写入 L2 和 L3（L3 异步写入不阻塞）。

另外 `BytecodeCache`（Caffeine）在打开 JAR/ZIP 时预加载 class 字节码，供反编译器解析类型依赖时查找。

### 搜索系统

`SearchService` 聚合 7 个 `SearchProvider`：`ClassSearchProvider`（类名）、`IndexedMemberSearchProvider`（索引成员）、`MethodSearchProvider`（方法签名正则）、`CodeSearchProvider`（源码全文）、`CommentSearchProvider`（注释）、`ResourceSearchProvider`（资源文件名）、`BytecodeSearchProvider`（字节码文本）。`SearchDialog` 提供 200ms 防抖输入和组合框过滤，结果上限由 `AppConfig.Search.resultLimit` 配置。

### Ctrl+Click 导航

反编译后 `OutlineParser.extractMetadata()` 从源码提取行号→引用映射（`CodeMetadata`）。`CodeLinkHandler.install()` 在 `CodeArea` 注册鼠标事件，Ctrl+Click 时通过 `findNodeByPath()` 在文件树中查找引用目标类节点并递归打开。

### 配置

`<appDir>/config/config.json` 自动加载/保存，`appDir` 在打包运行时为 JAR 所在目录，开发期回退到 `user.dir`。窗口关闭时保存窗口状态、引擎选择、最近文件（最多 20 个）。`FxApplication.stop()` 中调用 `DecompilerFactory.cleanup()` 和 `config.save()`。

### 国际化

`I18nUtil` 支持动态语言切换（`switchLocale()`），语言文件优先级：JAR 同级目录 external language 文件夹 → classpath `resources/language/`。目前有 `language_zh_CN.properties` 和 `language_en.properties`，150+ 个 key 覆盖全部 UI 文本。语言变更通过 `localeChangeListeners` 通知观察者。

### 日志

Logback 配置在 `src/main/resources/logback.xml`：控制台 appender，`%highlight` 彩色输出。应用日志写入 `<appDir>/logs/`，`com.bingbaihanji` 设为 DEBUG。启动失败时额外写入 `<appDir>/logs/startup-error.log`。

## 维护约定

- JavaFX 线程只做 UI 更新，文件解析、索引、搜索和反编译一律放入 `BackgroundTasks`。
- 对 class 字节使用懒加载，避免打开大型 JAR 时一次性占用大量内存。
- 新增 UI 文本需同步更新 `language_zh_CN.properties` 和 `language_en.properties`。
- 新增功能优先补 JUnit 5 单元测试；涉及工作区索引、搜索、导出、项目文件解析的变更必须覆盖异常路径。
- `bin/`、`target/`、运行日志和 shade 生成副产物不应提交到仓库。
