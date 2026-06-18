# FxDecompiler 审核版升级计划

**日期**: 2026-06-18  
**状态**: 待审核  
**目标**: 基于当前项目、jadx、Recaf、moe.sota/decompiler 的代码阅读结果，整理可实施的功能移植与质量优化计划。  
**原则**: 先补齐当前用户路径上的缺口，再做架构升级；避免一次性照搬大型项目。

---

## 1. 阅读范围

### 当前项目

重点阅读的模块：

- `MainWindow`：应用入口、菜单动作、工作区加载、导出、搜索、设置、新窗口。
- `WorkspaceTabManager`：工作区 Tab、文件树、代码 Tab、大纲/继承工具窗口。
- `ClassTabOpener`：class 读取、反编译、缓存、标签页刷新、文本资源打开。
- `ExportService`：当前源码保存、批量导出目录、ZIP 导出服务。
- `ClassDiscoverer` / `FileTreeBuilder`：JAR/ZIP/目录/class 发现、树构建、字节码缓存填充。
- `Decompiler` 及 `CfrDecompiler` / `VineflowerDecompiler` / `ProcyonDecompiler` / `JdDecompiler`：多引擎适配。
- `SearchService` / `SearchDialog` / 各 SearchProvider：搜索聚合与 UI。
- `CodeEditorTab` / `CodeMetadata` / `CodeLinkHandler`：源码、字节码、类信息与 Ctrl+Click 导航。
- `SettingsDialog` / `AppConfig` / `I18nUtil`：配置、设置窗口、国际化。
- `InheritanceService` / `OutlinePane`：继承关系、大纲。

### 参考项目

- jadx：重点阅读 `jobs`、`search`、`cache`、`settings`、`ui/export`、`treemodel`、项目文件相关代码。
- Recaf：重点阅读 `WorkspaceManager`、`PathNode`、`NavigationManager`、`PathExportingManager`、配置/快捷键/上下文菜单体系。
- moe.sota/decompiler：重点阅读轻量 GUI、Transformer 枚举、多语言、拖放、新实例、Tab 异步加载。

---

## 2. 当前项目现状判断

### 已具备能力

- 可以打开 JAR/ZIP/class/目录，并在文件树中展示 class、java、资源文件、二进制文件。
- 已支持 CFR、Vineflower、Procyon、JD-Core 四个 Java class 反编译引擎。
- 已支持源码 Tab、字节码 Tab、类信息 Tab。
- 已有大纲、继承关系、快速打开类、最近文件、新窗口、拖放文件、设置窗口、基础 i18n。
- 已有 L2 内存源码缓存和 L3 磁盘源码缓存。
- `DecompilerFactory` 已使用 `ConcurrentHashMap.computeIfAbsent`，线程安全问题已修复。
- `BackgroundTasks.run()` 已返回 `Future<?>`，并支持取消当前反编译任务。

### 与用户描述的导出缺口核对

当前 `MainWindow.exportAllFiles()` 已经会弹出目录选择器，并调用：

```java
ExportService.exportAllToDir(view.workspace().getTreeRoot(), currentEngine, dir.toPath(), ...)
```

也就是说，当前代码已经“指定目录”，并且使用菜单当前选中的 `currentEngine`。但它没有一个独立的“导出设置对话框”，用户在导出动作里不能显式选择：

- 导出引擎。
- 导出目标类型：目录 / ZIP。
- 是否导出资源文件。
- 是否覆盖已有文件。
- 失败时继续还是中断。
- 导出完成后的错误汇总。
- 可取消进度。

所以升级计划中应将“增强导出体验与可靠性”放在第一优先级，而不是简单补一个 DirectoryChooser。

### 主要问题

1. **全局 `BytecodeCache` 仍会跨工作区污染**
   - `FileTreeBuilder` 打开任意工作区时会把 class 写入全局缓存。
   - `InheritanceService`、各反编译器依赖全局缓存查找依赖类。
   - 多个 JAR 同时打开且包含同名类时，后打开的字节码可能影响前一个工作区的反编译/继承分析。

2. **磁盘源码缓存缺少内容版本校验**
   - `ClassTabOpener.computeWorkspaceKey()` 使用绝对路径字符串。
   - 如果同一路径的 JAR 被替换，旧反编译结果仍可能命中。
   - 需要加入文件 mtime + size 作为输入级校验，避免对每个 class 算 hash 的开销。

3. **搜索当前只覆盖已打开源码**
   - `CodeSearchProvider` / `MethodSearchProvider` 基于 `sourceCache`，而 `sourceCache` 由当前打开的 `CodeEditorTab` 构建。
   - 这不是 jadx 意义上的“全项目搜索”。
   - `BytecodeSearchProvider` 的 `bytecodeCache` 当前 TODO 未填充。

4. **导出只导出 class 的反编译源码**
   - `ExportService.exportTree*` 只处理 `data.isClassFile()`。
   - 资源文件、原始 `.java` 文件、二进制文件不会导出。
   - ZIP 导出服务存在，但菜单没有入口。

5. **引擎选项硬编码**
   - CFR、Vineflower、Procyon 的选项写死在适配器中。
   - `SettingsDialog` 只设置默认引擎，不能按引擎配置选项。
   - 缓存的 `optionsHash` 目前固定为 `"default"`，选项变更后无法准确失效。

6. **导航元数据偏弱**
   - `CodeMetadata` 目前按行保存引用，`CodeLinkHandler` 只能取当前行第一个引用。
   - 不能精确到 token，也不支持方法/字段跳转、查找使用、返回上一个位置。

7. **后台任务体系仍是轻量工具类**
   - 当前只有静态 `BackgroundTasks`，缺少任务队列、任务状态、进度模型、统一取消、内存保护、失败汇总。
   - 导出、搜索、反编译、索引构建会逐步需要统一任务管理。

8. **UI 状态与配置持久化不足**
   - 窗口位置、打开标签页、文件树展开状态、搜索历史、导出目录、最后选择的搜索范围等没有完整项目级持久化。
   - i18n 仍有不少硬编码中文。

9. **设计边界还不清晰**
   - `MainWindow` 仍负责过多动作协调。
   - `WorkspaceTabManager` 同时管理工作区 Tab、代码 Tab、工具窗口、树点击、上下文菜单、状态栏更新。
   - `ClassTabOpener` 同时承担导航、反编译、缓存、UI 创建。

---

## 3. 参考项目可移植能力

### 3.1 jadx 可借鉴点

1. **导出任务模型**
   - `ExportTask` 会把导出作为可取消后台任务处理。
   - 导出期间会限制缓存策略，避免批量反编译把 UI 内存缓存撑爆。
   - 结束后会根据状态提示取消、超时、内存不足、错误等不完整原因。

2. **统一后台任务执行器**
   - `BackgroundExecutor` 支持任务队列、进度、取消、超时、内存检查、线程数控制。
   - 当前项目可以先做轻量版，不必完整移植。

3. **全项目搜索**
   - `SearchTask` 将多个 Provider 并行执行，并有结果上限、进度、取消。
   - `SearchSettings` 支持忽略大小写、正则、包范围、资源过滤、资源大小限制。
   - 可直接转化为本项目的 `SearchOptions` + `SearchTask`。

4. **项目文件与缓存管理**
   - `JadxProject` 保存输入文件、打开标签、树展开、搜索历史、映射、缓存目录。
   - `CacheManager` 维护项目到缓存目录的映射，并可清理缓存。
   - 当前项目可先做 `.fxdproj` 或 `.fxdecompiler/project.json`，不用一次做完整工程系统。

5. **代码注释/重命名/映射数据**
   - jadx 的 code data 可保存注释、重命名、映射。
   - 对本项目属于长期能力，适合在 PathNode/Workspace 稳定后实现。

### 3.2 Recaf 可借鉴点

1. **PathNode 模型**
   - `PathNode` 用父链表达 Workspace -> Resource -> Bundle -> Directory -> Class/File -> Member。
   - `getValueOfType()` / `getPathOfType()` 让任何节点能反查工作区上下文。
   - 这是解决当前全局缓存、导航、上下文菜单混乱的关键抽象。

2. **WorkspaceManager**
   - 统一管理当前工作区、打开/关闭监听器、关闭条件、默认修改监听器。
   - 当前项目多工作区 Tab 模型可以改成 `WorkspaceManager` 管多个 `WorkspaceSession`。

3. **NavigationManager**
   - 跟踪所有打开的可导航 UI 内容。
   - 工作区关闭时统一 disable/关闭相关 Tab，避免泄漏。
   - 支持路径更新后通知打开的 UI。

4. **PathExportingManager**
   - 导出路径选择、最近导出目录、文件/目录输出、压缩选项、错误对话框集中管理。
   - 当前项目的导出入口适合拆到 `ExportController` 或 `ExportManager`。

5. **配置与快捷键**
   - Recaf 的 `KeybindingConfig` 将快捷键作为可编辑配置项。
   - 当前项目可先做固定快捷键表，再升级为可配置。

6. **上下文菜单构建器**
   - Recaf 按 Workspace/Resource/Directory/Class/Member 构建上下文菜单。
   - 当前项目可优先给文件树节点添加：导出当前节点、复制路径、在新窗口打开、搜索当前包、查看字节码、查看类信息。

### 3.3 moe.sota/decompiler 可借鉴点

1. **轻量 Transformer 枚举**
   - `Transformer` 枚举通过 class 创建新实例，结构简单。
   - 当前项目已有 `DecompilerFactory`，无需照搬，但可以借鉴“每次反编译新实例”的隔离策略，用于非线程安全引擎。

2. **简单清晰的新实例**
   - `ProcessService` 使用当前 Java 命令和 classpath 启动新进程。
   - 当前项目已有 `ProcessService`，应补充 CLI `--open` 验收，确保新窗口能自动打开当前文件。

3. **拖放与空状态**
   - 空页面只提示拖放打开，交互简单。
   - 当前欢迎 Tab 可以加强为可点击“打开文件/打开目录/最近文件”的工作台。

4. **多语言资源**
   - 语言文件覆盖多语言。
   - 当前项目先完成中英全量覆盖，再考虑更多语言。

---

## 4. 升级路线

### Phase 0：导出补强（优先实施）

**目标**: 让“导出”成为完整、可控、可反馈的用户流程。

#### 0.1 新增导出设置对话框

新增：`ui/export/ExportDialog.java`

字段：

- 反编译引擎：`PROCYON` / `CFR` / `VINEFLOWER` / `JD`，默认当前菜单引擎。
- 导出目标：目录 / ZIP。
- 输出路径：目录选择器或保存文件选择器。
- 导出内容：
  - 仅反编译源码。
  - 源码 + 资源文件。
  - 原始 class + 源码 + 资源文件。
- 冲突策略：
  - 覆盖。
  - 跳过。
  - 自动改名。
- 失败策略：
  - 继续并汇总。
  - 首个错误即停止。

#### 0.2 重构导出服务

新增模型（精简为 2 个 record）：

- `ExportConfig` — engine, format(DIR/ZIP), conflictPolicy(SKIP/OVERWRITE/RENAME), exportResources
- `ExportResult` — totalFiles, successCount, `List<String> errors`

调整：

- `ExportService.exportAllToDir(...)` 改为接收 `ExportConfig`，返回 `ExportResult`。
- `exportAllToZip(...)` 暴露到 UI（通过保存文件选择器选 `.zip` 路径触发，无需在对话框内显式选择"目录 vs ZIP"）。
- 支持导出资源文件和原始 `.java` 文件。
- 对每个 class 的反编译异常做单项错误记录（追加到 `ExportResult.errors`），不让整个导出直接失败。
- 路径写入前做 Zip Slip / 路径穿越保护。

#### 0.3 导出进度与取消

短期实现：

- `BackgroundTasks.run()` 返回的 `Future` 绑定到导出对话框的取消按钮。
- `ExportService` 每处理一个条目回调进度。
- 状态栏显示：当前文件、百分比、失败数。

中期替换：

- 引入 `TaskManager`，统一导出、搜索、索引、反编译后台任务。

#### 0.4 验收标准

- 未打开工作区时点击导出，显示明确提示。
- 打开 JAR 后点击导出，可以在导出动作内选择引擎和目录。
- 目录导出生成包路径下的 `.java` 文件。
- ZIP 导出生成合法 zip，内部路径不含绝对路径。
- 可选择导出资源文件。
- 反编译单个类失败不会导致全部结果丢失，最终显示失败汇总。
- 导出过程中可以取消。

---

### Phase 1：搜索与索引升级

**目标**: 从“已打开 Tab 搜索”升级为“工作区搜索”。

#### 1.1 建立 WorkspaceIndex

新增：

- `workspace/WorkspaceIndex.java`
- `workspace/ClassIndexEntry.java`
- `workspace/ResourceIndexEntry.java`

索引内容：

- class 内部名、短名、包名。
- class bytes hash。
- 方法/字段签名（ASM 扫描）。
- 资源文件名、扩展名、大小。
- 可选：字节码文本缓存。

#### 1.2 搜索服务改造

新增：

- `SearchOptions`
- `SearchTask`
- `SearchScopeEnum`

支持：

- 忽略大小写。
- 正则。
- 当前包/全工作区。
- 资源扩展名过滤。
- 资源大小限制。
- 结果分页。
- 可取消搜索。

#### 1.3 全项目代码搜索策略

两种模式：

- 快速模式：只搜索已反编译缓存和已打开源码。
- 完整模式：后台按需反编译所有 class 并写入缓存后搜索。

默认采用快速模式，用户可显式启动完整搜索，避免打开大 JAR 后 UI 卡顿。

#### 1.4 验收标准

- 不打开任何 class Tab，也能按类名搜索。
- 可搜索当前工作区所有资源文件内容。
- 完整搜索能搜索未打开类的反编译源码。
- 搜索中可取消，旧搜索结果不会覆盖新搜索结果。

---

### Phase 2：工作区与缓存隔离

**目标**: 解决全局缓存污染，为后续导航、导出、搜索打基础。

#### 2.1 WorkspaceSession

新增：

- `workspace/WorkspaceSession`
- `workspace/WorkspaceManager`
- `workspace/WorkspaceCloseListener`
- `workspace/WorkspaceOpenListener`

`WorkspaceSession` 持有：

- `id`
- `sourceFile`
- `treeRoot`
- `classBytesCache`
- `resourceBytesCache`
- `decompileCache`
- `index`
- `openedAt`

#### 2.2 移除全局 BytecodeCache 依赖

改造方向：

- `BytecodeCache` 从静态全局工具改为 `WorkspaceBytecodeStore`。
- 反编译器适配器不要直接访问全局静态缓存，而是通过 `DecompilerContext` 获取依赖类。

新增：

- `DecompilerContext`
- `ClassBytecodeProvider`
- `DecompilerOptions`

接口建议：

```java
String decompile(String classFilePath, byte[] classBytes, DecompilerContext context);
```

兼容期可保留旧方法，内部转调新方法。

#### 2.3 缓存 key 修复

缓存 key 必须包含：

- 工作区输入文件 mtime + size（替代 class bytes hash，避免每个 class 额外计算开销）。
- class internalName。
- engine。
- engine options hash。
- app/cache schema version。

#### 2.4 验收标准

- 同时打开两个包含同名类的 JAR，反编译结果互不污染。
- 替换同路径 JAR 后不会命中旧源码缓存。
- 关闭工作区后其内存缓存可释放。

---

### Phase 3：导航、上下文菜单与代码理解

**目标**: 从“行级 Ctrl+Click”升级到“路径驱动的导航体验”。

#### 3.1 引入 PathNode

新增：

- `path/PathNode`
- `path/WorkspacePathNode`
- `path/PackagePathNode`
- `path/ClassPathNode`
- `path/ResourcePathNode`
- `path/MemberPathNode`

核心能力：

- `getParent()`
- `getValue()`
- `getValueOfType(Class<T>)`
- `getPathOfType(Class<T>)`
- `isDescendantOf(PathNode<?>)`

#### 3.2 NavigationService

替代 `ClassTabOpener` 中的部分职责：

- `openPath(PathNode<?>)`
- `openClass(ClassPathNode, OpenOptions)`
- `openResource(ResourcePathNode)`
- `revealInTree(PathNode<?>)`
- `goBack()` / `goForward()` — 基于 `Deque<PathNode<?>>` 的线性历史，每次导航 push 当前路径，后退/前进不创建分支

#### 3.3 精确代码引用

升级 `CodeMetadata`：

- 从 line -> references 改为 token span -> reference。
- Reference 支持 class/method/field。
- 点击时按 caret offset 找最近 token。

中期方案：

- 先用 ASM 扫描常量池和反编译源码文本做启发式映射。

长期方案：

- 使用 JavaParser 或引擎提供的 line mapping/metadata。

#### 3.4 上下文菜单

文件树右键菜单：

- 打开。
- 导出当前节点。
- 复制内部路径。
- 复制类名。
- 在新窗口打开当前工作区。
- 搜索当前包。
- 展开/折叠。

代码区右键菜单：

- 复制。
- 全选。
- 跳转声明。
- 查找使用。
- 复制引用。
- 导出当前源码。

#### 3.5 验收标准

- Ctrl+Click 点击同一行多个类名时能跳转到正确类。
- 可返回上一个导航位置。
- 关闭工作区后相关打开标签全部关闭或禁用，不留失效引用。

---

### Phase 4：设置、i18n、快捷键与项目文件

**目标**: 让常用偏好可保存、可恢复。

#### 4.1 设置窗口扩展

分类：

- 反编译器
  - 默认引擎。
  - 每个引擎的常用选项。
  - 单类超时时间。
- 编辑器
  - 字体、字号、行号、换行、Tab 宽度。
  - 字节码视图开关。
- 搜索
  - 默认搜索范围。
  - 结果上限。
  - 资源大小限制。
- 导出
  - 默认导出格式。
  - 默认导出目录。
  - 默认是否导出资源。
  - 冲突策略。
- 缓存
  - 缓存目录。
  - 最大缓存大小。
  - 清理缓存。
- 快捷键
  - 快速打开。
  - 搜索。
  - 导出。
  - 关闭 Tab。
  - 切换引擎。
- 语言
  - 简体中文。
  - English。

#### 4.2 项目文件

新增项目保存格式：

- `.fxdproj` 或 `.fxdecompiler-project.json`

保存：

- 输入文件路径。
- 当前引擎与引擎选项。
- 打开的标签页。
- 当前选中标签页。
- 树展开路径。
- 搜索历史。
- 导出目录。
- 窗口大小与分割比例。

#### 4.3 i18n 清理

- 扫描所有硬编码中文/英文。
- 新增 key 命名规范：`menu.file.open`、`dialog.export.title`、`status.decompile.running`。
- 中英文资源文件必须同 key。

#### 4.4 验收标准

- 关闭再打开应用后，最近文件、窗口大小、默认引擎、编辑器配置保留。
- 切换语言后新打开的菜单/对话框使用新语言。
- 设置中修改默认导出格式后，下次导出自动带出。

---

### Phase 5：查找使用

**目标**: 实现 class/method/field 级别的引用搜索，向专业逆向体验靠近。

唯一功能：

1. **查找使用（Find Usages）**
   - class/method/field 级别引用搜索。
   - 基于 ASM 常量池扫描 + 方法指令中 `invoke*` / `get*` / `put*` / `new` / `anewarray` 等指令的操作数匹配。
   - 结果按类型分组（类引用、方法调用、字段读写）。
   - 双击结果跳转到对应位置。

以下功能降到长期规划，本轮不实施：
- 调用图、CFG 导出（需要图形库，依赖太重）
- 反混淆辅助（映射加载、重命名）
- 注释与书签
- 插件接口

---

## 5. 代码质量与架构优化清单

### 必做

- `MainWindow` 保留为装配/协调层，导出、搜索、导航、设置应用逻辑拆到独立 controller/service。
- `WorkspaceTabManager` 拆分：
  - `WorkspaceViewFactory`
  - `WorkspaceTabController`
  - `ToolWindowController`
  - `CodeTabController`
- `ClassTabOpener` 拆分：
  - `DecompileService`
  - `NavigationService`
  - `CodeTabFactory`
  - `ResourceOpenService`
- 所有静默 `catch ignored` 改为日志记录，至少 DEBUG/WARNING。
- 所有后台任务异常要进入 UI 可见错误摘要。
- 公共方法补齐 `Objects.requireNonNull`。
- 增加单元测试覆盖：
  - `ExportService`
  - `ClassDiscoverer`
  - `FileTreeBuilder`
  - `DiskCodeCache`
  - `SearchService`
  - `PathNode`

### 应做

- 引入包级架构：

```text
app/          应用生命周期、任务、命令
workspace/    工作区、索引、缓存隔离
path/         PathNode 层级
decompiler/   引擎适配与选项
export/       导出模型与控制器
search/       搜索模型与任务
navigation/   导航服务与代码引用
ui/           JavaFX 控件与视图
config/       配置与项目文件
io/           文件读取/写入底层工具
```

- 增加 `logback`  + `slf4j`日志门面，不在业务代码里吞异常。
- `AppConfig` 增加 schema version，后续可迁移配置。
- Maven 配置中统一 `java.version`、`maven.compiler.source/target`、插件 `<source>/<target>`，避免 17/25 混用(项目使用jdk25)。

### 暂缓

- 完整插件系统。
- 字节码编辑与重新编译。
- Android APK/DEX 支持。
- 调试器。
- 大规模 UI 框架替换。

---

## 6. 推荐实施顺序

### 第一批：立即提升可用性

1. 导出设置对话框。
2. 导出目录/ZIP/资源/失败汇总/取消。
3. 导出配置持久化。
4. 补齐导出相关 i18n。
5. 增加 `ExportService` 单元测试。

### 第二批：搜索与缓存可靠性

1. `WorkspaceIndex`。
2. 搜索选项与完整工作区搜索。
3. bytecode 搜索补齐。
4. 磁盘缓存 key 加 class bytes hash。
5. 缓存清理 UI。

### 第三批：架构隔离

1. `WorkspaceSession`。
2. 工作区内 bytecode cache。
3. `DecompilerContext`。
4. `WorkspaceManager`。
5. 拆分 `MainWindow` / `WorkspaceTabManager` / `ClassTabOpener`。

### 第四批：导航与专业能力

1. `PathNode`。
2. `NavigationService`（含线性 Deque 历史）。
3. 精确 Ctrl+Click。
4. 上下文菜单增强。

### 第五批：查找使用与项目化

1. 查找使用（Find Usages — ASM 常量池 + 指令扫描）。
2. `.fxdproj`。
3. 打开状态恢复。

---

## 7. 风险与取舍

1. **不要先上大架构再补导出**
   - 用户当前最明确的痛点是导出。
   - 导出增强改动范围可控，适合作为第一批。

2. **完整工作区搜索会带来性能压力**
   - 必须分快速模式和完整模式。
   - 完整模式必须可取消。

3. **移除全局 BytecodeCache 会影响所有引擎**
   - 需要兼容期。
   - 先引入 `DecompilerContext`，再逐步替换静态访问。

4. **PathNode 很有价值，但不应过早泛化**
   - 先覆盖 workspace/package/class/resource/member。
   - 不要一开始就引入 Recaf 那种完整资源/Bundle 层级。

5. **引擎选项必须参与缓存 key**
   - 否则设置窗口做得越多，缓存错误越隐蔽。

---

## 8. 本轮不实施的内容

这份文档仅作为审核计划，不修改业务代码。审核通过后，建议从 Phase 0 开始实施，并为每个 Phase 单独建立 implementation plan。

