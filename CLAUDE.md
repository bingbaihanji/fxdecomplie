# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建/运行

- **编译:** `./mvnw clean compile`（跳过测试: `-DskipTests`）
- **打包 fat JAR:** `./mvnw clean package -DskipTests`
- **运行:** `./mvnw exec:java`
- **运行单个测试:** `./mvnw test -Dtest="TestClassName"`
- **运行全部测试:** `./mvnw test`

启动参数：`--open <path>` 可在启动时自动打开指定 JAR/ZIP/目录。

## 技术栈

- **JDK 25**（源码/目标 25）+ JavaFX 25.0.1（`provided` 作用域）
- **jfx.incubator.richtext** CodeArea 用于代码展示 — 编译需要 `--add-modules javafx.controls,javafx.graphics,javafx.base,jfx.incubator.richtext`
- **四个反编译引擎:** Vineflower (1.11.2), CFR (0.152), Procyon (0.6.0), JD-Core (1.1.3)
- **ASM 9.7.1**（asm + asm-util）用于字节码视图、类信息视图、继承分析
- **SLF4J 2.0.17 + Logback 1.5.18**（`src/main/resources/logback.xml` 配置，DEBUG 级日志仅对 `com.bingbihanji.fxdecomplie` 生效，第三方反编译库设为 WARN）
- **Gson 2.12.1** 用于配置 JSON 和 VS Code 主题 JSON 解析
- **Maven Shade 3.6.0** 插件生成 fat JAR，主类 `AppLauncher`；exec-maven-plugin 用于开发期运行

## 包结构

项目重构为 6 个清晰包，按功能分层：

| 包 | 职责 |
|---|---|
| `config/` | `AppConfig` POJO：窗口几何、主题、反编译器偏好、导出/搜索默认值、语言、最近文件。JSON 持久化到 `~/.fxdecompiler/config.json` |
| `decompiler/` | `Decompiler` 接口 + 四个引擎实现 + `DecompilerFactory`（单例缓存）+ `BytecodeCache`（全局 `ConcurrentHashMap`）+ `DecompilerContext`（携带 `WorkspaceIndex`） |
| `model/` | 数据对象：`FileTreeNode`、`Workspace`（含 `WorkspaceIndex`）、`OpenFile`、`CodeMetadata`、PathNode 体系（`ClassPathNode`/`ResourcePathNode`/`WorkspacePathNode`/`AbstractPathNode`）、`DecompilerProject`、`ExportConfig`/`ExportResult` 等 |
| `service/` | 业务逻辑：`ClassDiscoverer`、`FileTreeBuilder`、`ExportService`、`BackgroundTasks`（线程池）、`ClassTabOpener`（三级缓存反编译流水线）、`SearchService`、`NavigationService`、`DecompileCache`（L2 内存）、`DiskCodeCache`（L3 磁盘）、`UsageSearchService`、`ProcessService`（启动新窗口） |
| `ui/` | JavaFX 视图：`MainMenuBar`、`CodeEditorTab`、`FileTreeView`、`SearchDialog`、`SettingsDialog`、`ExportDialog`、`QuickOpenDialog`、`FindUsageDialog`、`WorkspaceTabManager`、`StatusBar`、主题系统（`AppTheme`、`VsCodeThemeLoader`、`RegexHighlighter`）、大纲/继承工具窗口 |
| `utils/` | `I18nUtil`（国际化，动态加载外部或 classpath 语言文件）、`CodeLinkHandler`（Ctrl+Click 导航） |

入口点：`Main.main()` → `AppLauncher.main()` → `FxDecompilerApp.main()` → `Application.launch(FxApplication.class)`。

## 架构要点

### 线程模型

所有耗时操作（文件发现、反编译、搜索、导出）通过 `BackgroundTasks.run(name, task)` 提交到守护线程池（0-core, 8-max, 60s 存活, `LinkedBlockingQueue`）执行。UI 更新通过 `Platform.runLater()` 回到 JavaFX 线程。`BackgroundTasks.cancel(future)` 安全取消任务。

切换类文件时先取消当前反编译任务再提交新任务。`ClassTabOpener` 在反编译前调用 `Thread.interrupted()` 清除线程池复用的残留中断标志，并在 catch 中通过 `isInterruptRelated()`（递归检查异常链 + 当前线程中断标志）过滤中断相关异常，避免弹窗报错。

### 三级缓存

反编译结果走三级缓存（`ClassTabOpener.decompileWithCache()`）：L2 内存缓存（`DecompileCache`，跨工作区共享）→ L3 磁盘持久化缓存（`DiskCodeCache`）→ 实际反编译。缓存键包含工作区路径 + mtime + size 防止同路径替换文件命中旧缓存。L3 命中时回填 L2；反编译结果同时写入 L2 和 L3（L3 异步写入不阻塞）。

### 搜索系统

`SearchService` 聚合 7 个 `SearchProvider`：`ClassSearchProvider`（类名）、`IndexedMemberSearchProvider`（索引成员）、`MethodSearchProvider`（方法签名正则）、`CodeSearchProvider`（源码全文）、`CommentSearchProvider`（注释）、`ResourceSearchProvider`（资源文件名）、`BytecodeSearchProvider`（字节码文本）。`SearchDialog` 提供 200ms 防抖输入和组合框过滤，结果上限由 `AppConfig.Search.resultLimit` 配置。

### Ctrl+Click 导航

反编译后 `OutlineParser.extractMetadata()` 从源码提取行号→引用映射（`CodeMetadata`）。`CodeLinkHandler.install()` 在 `CodeArea` 注册鼠标事件，Ctrl+Click 时通过 `findNodeByPath()` 在文件树中查找引用目标类节点并递归打开。

### 配置

`~/.fxdecompiler/config.json` 自动加载/保存。窗口关闭时保存窗口状态、引擎选择、最近文件（最多 20 个）。`FxApplication.stop()` 中调用 `DecompilerFactory.cleanup()` 和 `config.save()`。

### 国际化

`I18nUtil` 支持动态语言切换（`switchLocale()`），语言文件优先级：JAR 同级目录 external language 文件夹 → classpath `resources/language/`。目前有 `language_zh_CN.properties` 和 `language_en.properties`，150+ 个 key 覆盖全部 UI 文本。语言变更通过 `localeChangeListeners` 通知观察者。

### 日志

Logback 配置在 `src/main/resources/logback.xml`：控制台 appender，`%highlight` 彩色输出。`com.bingbihanji.fxdecomplie` 设为 DEBUG，反编译库（jd/benf/jetbrains/strobel/objectweb）设为 WARN。启动失败时额外写入 `~/.fxdecompiler/startup-error.log`。
