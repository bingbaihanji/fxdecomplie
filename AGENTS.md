# FxDecompiler 项目指南

本文件面向 AI 编码助手，汇总项目架构、构建方式、代码组织与开发约定。以下信息基于当前仓库实际内容整理，优先以代码和配置文件为准。

## 1. 项目概述

FxDecompiler（ artifactId: `fxdecomplie`）是一款基于 **JDK 25 + JavaFX 25.0.1** 的 Java 字节码反编译桌面工具。支持打开 JAR、ZIP、目录和单个 class 文件，提供多引擎反编译、字节码视图、全局搜索、用法查找、继承引用分析、代码导出等功能。

主要入口：

- `com.bingbaihanji.fxdecomplie.AppLauncher#main` —— fat JAR 启动主类。
- `com.bingbaihanji.fxdecomplie.FxDecompilerApp#main` —— 实际启动逻辑，启用 JavaFX 预览特性后进入 `FxApplication`。
- `com.bingbaihanji.fxdecomplie.controller.MainWindow` —— 主窗口控制器。

## 2. 技术栈

| 类别 | 技术/版本 |
|------|-----------|
| 语言 / 运行时 | Java 25（源码/目标均为 25），JavaFX 25.0.1 |
| 代码编辑器 | `jfx.incubator.richtext` CodeArea |
| 反编译引擎 | Vineflower 1.12.0、CFR 0.152、Procyon 0.6.0、JD-Core 1.1.3、自维护 JADX 内核、自研 bdec 1.0.0 |
| 字节码分析 | ASM 9.9（`asm` + `asm-util`） |
| 类扫描（vendored） | ClassGraph 4.8.184 源码迁移至 `com.bingbaihanji.classgraph` |
| JSON | Jackson 2.19.4（`jackson-databind`） |
| 日志 | SLF4J 2.0.17 + Logback 1.5.18 |
| 原生窗口 | JNA 5.17.0（Windows DWM 边框/圆角/标题栏） |
| 测试 | JUnit Jupiter 5.12.1 |
| 构建 | Maven 3.9+（内置 `mvnw` / `mvnw.cmd`）+ Shade Plugin 3.6.0 |

注意：项目中不再依赖 Caffeine、Gson；CLAUDE.md 中提到的 Caffeine/Gson 内容已过时。Jackson 负责配置与主题 JSON 解析。

## 3. 项目结构

```text
src/main/java/
├── com/bingbaihanji/fxdecomplie/          # 主应用
│   ├── AppLauncher.java                   # fat JAR 入口
│   ├── FxDecompilerApp.java               # JavaFX Application 引导
│   ├── ClassNodeResolver.java             # 类名/路径解析
│   ├── bytecode/                          # 字节码签名/分析辅助
│   ├── config/                            # AppConfig POJO 与 JSON 持久化
│   ├── constants/                         # 应用级目录/常量约定
│   ├── context/                           # AppContext 全局生命周期
│   ├── controller/                        # 主窗口、导航、编辑器动作、工作区控制器
│   ├── core/jadx/                         # 自维护 JADX 反编译内核（源码级 vendored）
│   ├── decompiler/                        # 反编译引擎接口、实现、参数、工厂
│   │   └── jadx/                          # JADX 适配层（Facade / Input / Output / Rename）
│   ├── model/                             # 数据模型：Workspace、FileTreeNode、索引、PathNode、导出等
│   │   └── reference/                     # 继承引用视图模型
│   ├── rename/                            # 项目级重命名服务
│   ├── service/                           # 业务逻辑
│   │   ├── classscan/                     # ClassGraph 扫描适配（ClassScanService / ClassGraphClassScanService）
│   │   └── reference/                     # 继承引用索引与服务
│   ├── ui/                                # JavaFX 视图
│   │   ├── code/                          # 代码编辑器、ClassTabOpener、状态栏、独立窗口
│   │   ├── comment/                       # 注释 UI
│   │   ├── export/                        # 导出对话框
│   │   ├── graph/                         # DOT 图解析/展示
│   │   ├── hex/                           # 十六进制/字节码视图
│   │   ├── inheritance/                   # 继承引用面板
│   │   ├── menu/                          # 菜单栏
│   │   ├── outline/                       # 结构大纲
│   │   ├── quickopen/                     # 快速打开
│   │   ├── search/                        # 7 种搜索提供器
│   │   ├── settings/                      # 设置对话框
│   │   ├── theme/                         # VS Code 主题加载、正则高亮
│   │   ├── toolbar/                       # 工具栏
│   │   ├── tree/                          # 文件树视图
│   │   ├── uml/                           # UML 类图（ASM 提取 + JavaFX 渲染 + PlantUML 导出）
│   │   ├── usage/                         # Find Usages 对话框
│   │   ├── view/                          # 通用视图组件
│   │   ├── window/                        # 多窗口拖拽支持
│   │   └── workspace/                     # 工作区标签页管理
│   └── util/                              # 工具类
│       ├── asm/、cache/、collection/、concurrent/、i18n/、io/、jadx/、jvm/、
│       ├── lookup/、math/、reference/、reflect/、structure/、text/、tree/、value/
├── com/bingbaihanji/classgraph/           # ClassGraph 4.8.184 迁移源码（MIT License）
│   ├── bytecode/、classpath/、metadata/、reflect/、resource/、scan/、type/、util/
└── com/bingbaihanji/windows/              # Windows 原生窗口支持
    ├── jfx/、platform/、platform/win32/

src/main/resources/
├── language/language_zh_CN.properties     # 简体中文语言包
├── language/language_en.properties        # 英文语言包
├── logback.xml                            # 日志配置
├── com/bingbaihanji/fxdecomplie/themes/   # 内置主题 CSS/JSON
├── export/                                # 导出 Gradle 模板
├── icon/、ttf/                            # 图标与字体
└── META-INF/licenses/LICENSE-ClassGraph.txt

src/test/java/                             # JUnit 5 测试
config/config.json                         # 运行时配置示例（开发期）
docs/                                      # 设计文档与重构计划
themes/                                    # 用户可放外部主题 JSON
```

## 4. 构建与运行

仓库已内置 Maven Wrapper，JDK 25 为必需。

```bash
# 编译（跳过测试）
./mvnw clean compile -DskipTests

# 运行全部测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest="ClassName"

# 开发期运行
./mvnw exec:java

# 打包 fat JAR（输出到 bin/fxdecomplie.jar）
./mvnw clean package -DskipTests

# 启动时自动打开文件
./mvnw exec:java -Dexec.args="--open /path/to/app.jar"
java -jar bin/fxdecomplie.jar --open /path/to/app.jar
```

Windows PowerShell 请使用 `\.\mvnw.cmd` 替代 `./mvnw`。

构建要点：

- `maven-shade-plugin` 生成单文件 fat JAR，输出到 `bin/fxdecomplie.jar`。
- `maven-antrun-plugin` 在 `initialize` 阶段创建 `bin/` 目录。
- 打包时排除所有 `module-info.class`，fat JAR 运行在 classpath 模式。
- 主类清单属性指向 `com.bingbaihanji.fxdecomplie.AppLauncher`。

## 5. 代码组织原则

### 5.1 包职责

| 包 | 职责 |
|---|---|
| `config/` | `AppConfig` 及子 POJO：窗口、主题、反编译器偏好、导出/搜索/平台配置、最近文件。JSON 持久化到 `<appDir>/config/config.json`。 |
| `constants/` | 应用级目录与常量入口，如 `AppPaths`。 |
| `context/` | `AppContext` 管理配置、缓存、重命名服务、后台任务等全局生命周期。 |
| `controller/` | JavaFX 控制器层：主窗口、工作区、导航、编辑器动作。 |
| `core/jadx/` | 自维护 JADX 反编译内核。后续不再同步上游，按项目需求演进。 |
| `decompiler/` | `Decompiler` 接口 + 五个引擎实现 + `DecompilerFactory` + `DecompilerContext`。 |
| `decompiler/jadx/` | JADX 项目适配层：Facade、输入构建、参数、输出选择、重命名适配、诊断。 |
| `model/` | 不可变/轻量数据对象：Workspace、FileTreeNode、WorkspaceIndex、PathNode、导出配置与结果等。 |
| `model/reference/` | 继承引用视图模型：`InheritanceReferenceTree`、`InheritanceReferenceNode`、`Kind`。 |
| `rename/` | 项目级持久化重命名服务，与 JADX alias 解耦。 |
| `service/` | 业务逻辑：发现、索引、反编译流水线、搜索、导出、导航、后台任务、进程启动。 |
| `service/classscan/` | ClassGraph 扫描适配，对外暴露 `ClassScanService`，不直接暴露 ClassGraph API。 |
| `service/reference/` | 继承引用索引与查询服务。 |
| `ui/` | JavaFX 视图与控件。 |
| `util/` | 各领域工具类。 |
| `com.bingbaihanji.classgraph/` | vendored ClassGraph 源码，保持包隔离，业务层通过 `service/classscan/` 访问。 |
| `com.bingbaihanji.windows/` | Windows 原生窗口外观支持。 |

### 5.2 线程模型

- JavaFX Application Thread 只做 UI 更新。
- 所有耗时操作（文件发现、索引、反编译、搜索、导出、ClassGraph 扫描）通过 `BackgroundTasks` 提交到守护线程池执行。
- 结果通过 `Platform.runLater()` 回到 UI 线程。
- 切换类文件时先取消当前反编译任务，再提交新任务，避免过期结果覆盖。

### 5.3 缓存策略

反编译结果走三级流水线：

1. **L2 内存缓存**（`DecompileCache`）—— 跨工作区共享。
2. **L3 磁盘缓存**（`DiskCodeCache`）—— 异步写入，不阻塞 UI。
3. **实际反编译** —— 多引擎统一入口。

缓存键由 `service/cache/CacheKeys` 统一生成：workspaceId（绝对路径 + 内容戳）、SHA-256 类文件指纹（前 16 位十六进制）、内部名、引擎、选项 hash（JADX 反混淆时追加项目重命名状态版本）和键版本。磁盘布局由 `service/CacheLayout` 统一：`cache/decompile/v1/<workspaceId>/<engine>/<package>/<Simple>.<hash>.<optionsId>.<ver>.java`（500 MB 配额）与 `cache/index/v1/<fingerprint>.idx.gz`（200 MB 配额），按 mtime LRU 节流淘汰到 70%，不触碰 `cache/jadx/runtime/`。失败结果（超时/错误/取消/目标未找到）不进入任何一级缓存。

工作区索引由虚拟线程并行构建，图片等二进制资源不进入索引与缓存；打开工作区后延迟 500ms 预热（仅当仍为当前活动工作区），关闭中的工作区会丢弃构建/缓存命中结果，且不写缓存。索引持久化到 `<appDir>/cache/index/v1/<指纹>.idx.gz`（GZIP 二进制，带格式版本号，200 MB 配额与 mtime LRU，唯一临时文件 + 原子替换，指纹为「路径 + 大小 + mtime」（目录为汇总信息））。按节点导出通过 `WorkspaceClassBytecodeProvider` 懒加载依赖，不等待完整索引。索引构建使用独立单线程池，不占用共享 IO 池。

### 5.4 导出流水线

导出采用有序有界流水线：按原始顺序提交反编译，并按原始顺序写盘；在途窗口为 `max(1, 反编译并发数 - 1)`（反编译并发数 = `max(2, min(核数/2, 8))`），为交互式反编译保留一个共享池线程。JADX 恒为 1，因为适配层用单许可信号量串行化 JADX 调用；等待许可每轮 5s、总预算约 20s，超过则返回 busy，持有超过 120s 视为卡死并强制释放。窗口大于 1 会让等待时间计入 30s 超时预算，因此导出窗口固定为 1。每项轮到其顺序时立即写目录或 ZIP，不再把全部源码驻留内存；ZIP 条目顺序与 RENAME/SKIP 冲突策略保持原语义。路径不安全/冲突策略失败按条目跳过（ZIP 下以 `UnsafeExportPathException` 区分），真正的 ZIP 流 IO 错误才停止写入后续条目（已写出的部分保留）；30s 单类超时与引擎回退顺序不变。

### 5.5 配置与数据目录

应用数据默认写入 JAR 所在目录；开发期 classpath 在 `target/classes/` 时回退到项目根目录，避免 `mvn clean` 删除配置。

```text
<appDir>/
├── config/config.json        # 窗口/主题/引擎/导出/搜索/平台偏好
├── cache/                    # L3 反编译缓存、jadx runtime 临时目录
├── logs/                     # application.log / error.log / startup-error.log
└── fxdecomplie/              # 工作区级数据
    ├── comments/             # 注释持久化
    └── renames/              # 重命名映射
```

## 6. 开发约定

### 6.1 代码风格

- 源码与注释主要使用**简体中文**；新增 UI 文本必须同步更新 `language_zh_CN.properties` 和 `language_en.properties`。
- 类、方法、变量命名遵循 Java 规范；局部变量使用 camelCase（ClassGraph 迁移代码中仍有部分 PascalCase 局部变量，属于历史遗留，重构中逐步修正）。
- 常量使用 `UPPER_SNAKE_CASE`。
- 优先使用 `record` / `final` /不可变集合表达 DTO；集合返回值尽量返回 `List.copyOf()` / `Collections.unmodifiableList()`。
- 工具类构造方法私有化，并抛 `AssertionError` 或类似断言防止实例化。

### 6.2 依赖与模块边界

- `core/jadx/` 可以依赖项目 `util`、`constants`、轻量 `model`，但**不能依赖 UI、controller**。
- `core/jadx/` 内部不直接读写 `AppConfig`；配置由 `decompiler/jadx/` 适配层注入。
- `com.bingbaihanji.classgraph` 保持包隔离；业务层通过 `service/classscan/` 的 DTO 访问扫描能力，禁止 UI 直接引用 ClassGraph API。
- 不允许出现 UI/controller 依赖倒灌到内核或扫描器。

### 6.3 第三方源码 vendored 说明

- **JADX 内核**：迁移至 `com.bingbaihanji.fxdecomplie.core.jadx`，作为项目自维护默认反编译引擎。后续不再以同步上游为主要目标，详见 `docs/2026-07-12-jadx-engine-upgrade-plan.md`。
- **ClassGraph**：迁移自 ClassGraph 4.8.184（MIT License），包路径改为 `com.bingbaihanji.classgraph`，许可证文件位于 `src/main/resources/META-INF/licenses/LICENSE-ClassGraph.txt`。详见 `docs/2026-07-20-classgraph-inheritance-reference-plan.md` 和 `docs/2026-07-23-classgraph-refactor-plan.md`。
- **bdec**：自研反编译引擎，通过 **Git subtree** 从 `github.com/bingbaihanji/bdec` 的 `main-src` 分支 vendored 到 `com.bingbaihanji.bdec`。bdec 更新后执行 `git subtree pull --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src` 拉取。适配层为 `decompiler/BdecDecompiler.java`，详见 `docs/superpowers/specs/2026-08-17-bdec-engine-integration-design.md`。

## 7. 测试

- 测试框架：**JUnit 5**。
- 当前全量测试 **468** 个，全部通过。
- 新增功能优先补充单元测试；涉及索引、搜索、导出、项目文件解析、ClassGraph 适配、JADX 适配的变更必须覆盖异常路径。
- ClassGraph 相关测试位于 `src/test/java/com/bingbaihanji/classgraph/` 与 `src/test/java/com/bingbaihanji/fxdecomplie/service/classscan/`、`service/reference/`。
- JADX 相关测试位于 `src/test/java/com/bingbaihanji/fxdecomplie/decompiler/jadx/`。

常用命令：

```bash
./mvnw test
./mvnw test -Dtest="InheritanceReferenceIndexTest,ClassGraphWorkspaceAdapterTest"
./mvnw test -Dtest="JadxDecompilerFacadeTest"
```

## 8. 安全与运行注意事项

- 项目使用 JDK 25 预览特性与内部 API（如 `sun.reflect.ReflectionFactory`），未来 JDK 版本可能需要迁移。
- `ClassTabOpener` 等代码使用 `Thread.interrupted()` 清理线程池复用的残留中断标志；处理中断相关异常时需通过 `isInterruptRelated()` 递归检查异常链与当前线程中断标志。
- 日志文件按天滚动，位于 `<appDir>/logs/`；启动失败额外写入 `<appDir>/logs/startup-error.log`。
- `config/config.json`、`cache/`、`logs/`、`bin/`、`target/`、运行副产物不应提交到仓库（已配置在 `.gitignore`）。

## 9. 已知文档/代码不一致点

以下旧文档中的描述已与当前代码不一致，修改时应以本文件为准：

- CLAUDE.md 提到存在 `module-info.java` —— 实际仓库中没有该文件。
- CLAUDE.md / README 提到 Caffeine、Gson —— 当前 `pom.xml` 已不再依赖，缓存与 JSON 分别由自定义实现和 Jackson 处理。
- README 中 Vineflower 版本写为 1.11.2 —— `pom.xml` 中实际为 1.12.0。
- README 的项目结构图未包含 `core/jadx/`、`service/classscan/`、`service/reference/`、`windows/` 等近期新增模块。


