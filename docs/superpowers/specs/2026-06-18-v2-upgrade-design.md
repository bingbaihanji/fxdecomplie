# FxDecompiler V2 升级设计文档

**日期:** 2026-06-18
**状态:** 草案
**审查范围:** 全部 80+ Java 源文件、6 测试文件、资源文件、pom.xml

---

## 审查概要

| 级别 | 数量 | 说明 |
|------|------|------|
| P0 — 致命缺陷 | 7 | 内存泄漏、数据竞争、数据损坏风险 |
| P1 — 高优先级 | 15 | 关键功能缺失、显著性能问题 |
| P2 — 中优先级 | 12 | 架构债务、代码重复、可测试性差 |
| P3 — 低优先级 | 10 | 代码风格、命名、死代码 |
| 测试空白 | — | UI 0%、反编译器 <10%、模型 <5%、服务约 30% |

---

## P0 — 致命缺陷（应立即修复）

### P0-1. BytecodeCache 无上限内存增长

**位置:** `decompiler/BytecodeCache.java:15`
**问题:** 全局 `ConcurrentHashMap<String, byte[]>` 没有大小限制、无 TTL、无 LRU 驱逐。每次 `ClassDiscoverer.discover()` 将所有 `.class` 字节码注入此 Map。用户打开多个大型 JAR（如 `rt.jar` 含 20,000+ 类）后，内存无限增长直至 OOM。
**影响:** 打开 3-4 个大 JAR 后应用崩溃。
**方案:**
- 替换为 Caffeine Cache（软引用 + 最大条目数 5000 + 30 分钟 TTL）
- 或将缓存作用域限定为每个工作区，`Workspace.close()` 时清理

### P0-2. SearchService 跨线程数据竞争

**位置:** `service/SearchService.java:19`
**问题:** `providers` 字段为 `ArrayList`（非线程安全）。`addProvider()` 在 JavaFX 线程调用，`searchAll()` 在后台线程（`BackgroundTasks.run`）读取。同时发生时抛 `ConcurrentModificationException` 或读到过期数据。
**影响:** 搜索时偶发崩溃。
**方案:** 替换为 `CopyOnWriteArrayList` 或在 `addProvider`/`clearProviders`/`searchAll` 上加 `synchronized`。

### P0-3. Vineflower 日志完全为空实现

**位置:** `decompiler/VineflowerDecompiler.java:175-183`
**问题:** `IFernflowerLogger` 匿名实现中所有日志级别（INFO/WARN/ERROR 等）均为空方法体。类上已声明 `SLF4J Logger` 但从未使用。Vineflower 反编译过程中的所有警告、错误、诊断信息完全丢失。
**影响:** 反编译结果有问题时无法诊断，开发调试盲区。
**方案:** 将各日志级别转发到 `logger.debug/warn/error`（建议 level≤INFO → debug, WARN→warn, ERROR→error）。

### P0-4. findOrRemoveOpenClassTab 迭代中修改集合

**位置:** `service/ClassTabOpener.java:330-339`
**问题:** 第 331 行用 enhanced for 遍历 `codeTabPane.getTabs()`，第 337 行用 `removeIf` 修改同一 ObservableList。可能触发 `ConcurrentModificationException`。
**影响:** 切换反编译引擎时可能崩溃。
**方案:** 先收集待删除项到临时列表，循环外再 `removeAll`。

### P0-5. DecompilerFactory.cleanup() 异常不隔离

**位置:** `decompiler/DecompilerFactory.java:40-43`
**问题:** `CACHE.values().forEach()` 中若第一个引擎 `cleanup()` 抛异常，后续引擎不会清理，`CACHE.clear()` 也不执行。
**影响:** 引擎清理不彻底，资源泄漏。
**方案:** 将 `forEach` 内逻辑包装为 `try { engine.cleanup(); } catch (Exception e) { logger.warn(...) }`。

### P0-6. JdDecompiler 可能产生重复 package 声明

**位置:** `decompiler/JdDecompiler.java:40-44`
**问题:** 代码在反编译结果前手动插入 `package` 声明。若 JD-Core 内部也输出 `package` 声明（反编译器通常如此），最终源码将包含两个 `package` 语句，是语法错误的 Java 代码。
**影响:** JD 引擎反编译结果可能是非法源码。
**方案:** 检测 JD-Core 输出是否已含 `package` 声明，若已有则跳过重复插入。

### P0-7. CfrDecompiler.replace(".class") 错误替换路径中所有匹配

**位置:** `decompiler/CfrDecompiler.java:57`
**问题:** `classFilePath.replace(".class", "")` 替换字符串中**所有**出现的 `.class`。若路径含 `.class` 字串（如 `com/classloader/MyClass.class`），结果被错误截断。
**影响:** 特定路径的类无法正确反编译。
**方案:** 使用 `classFilePath.replaceAll("\\.class$", "")` 或 `endsWith(".class") ? substring(...) : classFilePath`。

---

## P1 — 高优先级（本迭代应完成）

### P1-1. 缺失：文件内搜索（Ctrl+F）

**问题:** 无编辑器内搜索栏。用户只能在打开的 5000 行反编译结果中手动滚动。
**方案:** 在 `CodeEditorTab` 子 TabPane 上方添加搜索栏，支持前进/后退跳转。CodeArea 自身如有搜索 API 则直接对接。

### P1-2. 缺失：行跳转（Ctrl+G）

**问题:** 无 Go to Line 功能。
**方案:** 简单对话框输入行号，`codeArea.moveDocumentStart()` + 循环 `moveParagraphDown()`（或用 CodeArea 原生 API 直接定位）。

### P1-3. 缺失：搜索高级选项

**问题:** 全局搜索对话框缺少正则模式、大小写敏感、全词匹配三个开关。7 个 Provider 都只做简单的 `toLowerCase().contains()` 匹配。
**方案:**
- SearchDialog 添加三个 ToggleButton
- SearchProvider 接口增加 `search(String query, SearchOptions options)` 方法
- SearchOptions 包含 `regex: boolean, caseSensitive: boolean, wholeWord: boolean`

### P1-4. 缺失：反编译器高级选项

**问题:** Settings 中对每个引擎只有名称选择，无任何引擎特定配置。CFR 有 28 个选项（异常处理、字符串缓冲、记录类型等），Vineflower 有 24 个选项（线程数、模式匹配、密封类等）。目前全部用硬编码默认值。
**方案:**
- `AppConfig.Decompiler` 增加 `Map<String, Map<String, String>> engineOptions`
- SettingsDialog 增加引擎高级选项标签页（动态表单，从引擎元数据生成）
- 各 Decompiler 实现的 constructor 接受选项 Map

### P1-5. ClassDiscoverer 急切加载导致 3 倍内存

**位置:** `service/ClassDiscoverer.java:70-74, 92-99`
**问题:** `discoverJar` 和 `discoverDirectory` 将所有 `.class` 文件字节码一次性读入 `List<ClassEntry>`。这些字节码随后被复制到 `FileTreeNode.cachedBytes` 和 `BytecodeCache` 中，同一份字节码在内存中存在 3 份。
**方案:** ClassEntry 改用 `Supplier<byte[]>` 延迟加载，或 ClassDiscoverer 只收集路径元数据，字节码在首次访问时按需读取。

### P1-6. WorkspaceIndex.build() 阻塞构造函数

**位置:** `model/Workspace.java:40`
**问题:** `Workspace` 构造函数同步调用 `WorkspaceIndex.build()`，遍历整棵文件树并用 ASM 解析每个 class 文件。对于 `rt.jar`（20,000+ 类），这会导致数秒的 UI 冻结。
**方案:** 将 index.build() 移到后台线程，Workspace 构造完成后通过回调或 Future 返回索引。

### P1-7. ExportService.resolveClassBytes 无磁盘回退

**位置:** `service/ExportService.java:136-142`
**问题:** 只从 `node.getCachedBytes()` 和 `BytecodeCache` 查找字节码。若两者都无（从未打开过的类、缓存被清理），导出直接抛 `IllegalStateException`。`ClassTabOpener.readClassBytes` 有四层回退（节点缓存→索引→BytecodeCache→磁盘），而导出只有两层。
**方案:** 将 `ClassTabOpener.readClassBytes` 提取为公共工具方法，导出共用同一路径。

### P1-8. 导出代码重复 (exportAllFiles vs exportTreeItem)

**位置:** `MainWindow.java:294-417`
**问题:** `exportAllFiles()` 和 `exportTreeItem()` 方法体几乎完全相同（~60 行重复），仅入口节点不同。包括 ProgressHandle、Future、AtomicBoolean、三层异常处理、Platform.runLater 全部重复。
**方案:** 提取 `doExport(TreeItem<FileTreeNode> root, String taskName)` 私有方法，两个公开方法仅传不同参数。

### P1-9. findNodeByPath 无界递归风险

**位置:** `service/ClassTabOpener.java:359-367`
**问题:** 递归遍历树查找节点。对于深度嵌套的包层级（某些自动生成代码可达 50+ 层），有 `StackOverflowError` 风险。
**方案:** 改用 `ArrayDeque` 显式栈的迭代实现，或使用 `WorkspaceIndex` 的 HashMap O(1) 查找。

### P1-10. FileTreeBuilder 双重存储字节码

**位置:** `service/FileTreeBuilder.java:44, 48`
**问题:** 每个 class 文件的 byte[] 同时存入 `BytecodeCache`（全局）和 `FileTreeNode.setCachedBytes`（节点），占用双倍内存。
**方案:** 只在 `BytecodeCache` 中保存，`FileTreeNode` 改为按需从 BytecodeCache 获取。

### P1-11. 缺失：文件树图标

**位置:** `ui/tree/FileTreeCell.java:26-32`
**问题:** 当前用文本标签（"PKG"、"CLS"、"JAVA"等）代替图标，视觉效果简陋。
**方案:** 使用 Unicode 符号或 SVG 图标（如 📦 包、📄 类），或加载 16x16 PNG 图标文件。

### P1-12. 缺失：搜索结果高亮

**问题:** 搜索对话框只展示匹配行列表，不定位到编辑器中的具体位置。点击结果后打开文件但不跳转到匹配行。
**方案:** 搜索结果点击时调用 `openClassByPath` 并跳转到匹配行号，高亮当前行。

### P1-13. 缺失：代码差异对比

**问题:** 无法对比两个反编译引擎对同一类的输出差异，这在评估引擎质量时很有用。
**方案:** 在 ContextMenu 中添加"对比引擎输出"选项，生成并排差异视图。

### P1-14. 缺失：文本文件编码检测

**位置:** `service/ClassTabOpener.java:271`
**问题:** `openTextFileTab` 硬编码 UTF-8。properties 文件可能为 ISO-8859-1，XML 可能有 BOM 或声明其他编码。
**方案:** 使用 `juniversalchardet` 或手动检测 BOM，至少对常见编码（UTF-8/UTF-16/ISO-8859-1）进行试探。

### P1-15. 缺失：搜索排除过滤

**问题:** 搜索结果可能包含大量无关匹配（如反编译元数据注释、自动生成代码）。无法按路径/模式排除。
**方案:** `AppConfig.Search` 增加 `excludePatterns: List<String>`，搜索时过滤匹配。

---

## P2 — 中优先级（下个迭代）

### P2-1. MainWindow 上帝类重构

**位置:** `MainWindow.java`（840 行）
**问题:** 实现 21 个方法的 `Actions` 接口，管理文件加载、项目、导出、搜索、使用查找、快捷打开、缩放、行号、主题、引擎切换、剪贴板、窗口管理、关于、设置、拖放。
**方案:**
- 提取 `ExportController`：导出流水线（任务、进度、取消、结果展示）
- 提取 `WorkspaceLoader`：文件发现 → 树构建 → 工作区创建
- 将 `WorkspaceView` record 提升为 `model/` 下的顶级类
- `MainMenuBar.Actions` 拆分为 `FileActions`、`EditActions`、`ViewActions` 三个接口

### P2-2. BackgroundTasks 队列无界

**位置:** `service/BackgroundTasks.java:17`
**问题:** `new LinkedBlockingQueue<>()` 无容量限制。注释写的是 "bounded queue" 但实际是无界的。任务提交快于处理时会无限排队，最终 OOM。
**方案:** 设置合理容量（如 100），队列满时调用者收到 `RejectedExecutionException` 并给出用户提示。

### P2-3. DecompileCache 无驱逐策略

**位置:** `service/DecompileCache.java:17`
**问题:** `ConcurrentHashMap` 无大小限制。用户在整个会话中打开的所有类的反编译源码永久保留在内存中。
**方案:** 用 Caffeine 或 Guava Cache 替换，设置最大条目数（如 1000）+ 软引用。

### P2-4. DiskCodeCache cleanAll() 死代码

**位置:** `service/DiskCodeCache.java:50-64`
**问题:** `cleanAll()` 方法存在但从未被调用。磁盘缓存文件无限累积在 `~/.fxdecompiler/cache/`。
**方案:**
- 启动时检查缓存大小，超过阈值（如 500MB）时清理旧文件
- Settings 中添加"清空缓存"按钮
- 或基于 LRU 的自动驱逐

### P2-5. ClassDiscoverer 跟随符号链接风险

**位置:** `service/ClassDiscoverer.java:84`
**问题:** `Files.walk(dir)` 默认跟随符号链接。若目录含循环符号链接，导致无限递归和异常。
**方案:** 显式指定 `FileVisitOption.FOLLOW_LINKS` 为预期行为，或使用 `walkFileTree` 并检测循环。

### P2-6. ExportService 传递路径安全

**位置:** `service/ExportService.java:260`
**问题:** `Path.normalize()` 不解引用符号链接。`resolveSafeOutputPath` 可能被符号链接绕过。
**方案:** 检查每个路径组件，或使用 `toRealPath()` 解析符号链接后再做 prefix 检查。

### P2-7. 反编译器无超时保护

**位置:** 所有 4 个 Decompiler 实现
**问题:** 无反编译器调用超时。CFR 在混淆/畸形字节码上已知可能挂起。
**方案:** 在 `ClassTabOpener.decompileWithCache()` 中用 `Future.get(30, SECONDS)` 包装反编译调用，超时后取消并返回错误提示。

### P2-8. NavigationService 无最大历史

**位置:** `service/NavigationService.java:19-21`
**问题:** 前进/后退栈无界。长时间浏览后可能堆积数千条历史记录。
**方案:** 限制 `ArrayDeque` 最大容量（如 100），超出时移除最旧条目。

### P2-9. CodeLinkHandler 无卸载方法

**位置:** `utils/CodeLinkHandler.java:29`
**问题:** `install()` 设置 Lambda 事件处理器后无法移除。若 CodeArea 跨类重用或 metadata 变化，旧处理器残留。
**方案:** 返回 `Subscription` 对象，或提供 `install`/`uninstall` 配对方法。

### P2-10. I18nUtil 全静态状态不可测试

**位置:** `utils/I18nUtil.java`
**问题:** `resourceBundle`、`currentLocale`、`localeChangeListeners` 全为静态字段。测试中无法隔离语言环境，并发测试互相干扰。
**方案:**
- 保留静态便捷方法（向后兼容）
- 提供 `I18nUtil.createInstance(Locale)` 工厂方法返回可注入的实例
- 静态方法内部委托给单例实例

### P2-11. AppConfig 全公共字段无验证

**位置:** `config/AppConfig.java`
**问题:** 所有嵌套 POJO 字段为 `public`，任何代码可随时写入任何值。`save()` 将可能损坏的数据直接持久化。无验证逻辑。
**方案:**
- 字段改为私有 + getter/setter
- setter 中添加验证（如引擎名必须是有效枚举值）
- `schemaVersion` 用于迁移逻辑（当前定义但未使用）

### P2-12. 引擎字符串在配置中使用而非枚举

**问题:** `Decompiler.defaultEngine` 和 `Export.defaultEngine` 为 `String` 类型。`DecompilerTypeEnum` 存在但未在配置中使用。
**方案:** 配置 POJO 直接使用 `DecompilerTypeEnum`（Gson 可通过 `@SerializedName` 或自定义适配器映射）。

---

## P3 — 低优先级（持续改进）

### P3-1. 多处字符串规范化逻辑重复

**位置:** ProcyonDecompiler:85-90, CfrDecompiler:57,97-98, VineflowerDecompiler:84,97,113, JdDecompiler:18,30
**问题:** `replace('\\', '/')` + 移除 `.class` 后缀的逻辑在 5 个文件的 6 个地方重复。`DecompilerContext.normalizeInternalName()` 已存在正确的实现但未统一使用。
**方案:** 所有引擎统一调用 `DecompilerContext.normalizeInternalName()`。

### P3-2. getSimpleName 方法重复

**位置:** `CfrDecompiler.java:116-119`, `VineflowerDecompiler.java:216-219`
**方案:** 提取到共享工具类。

### P3-3. BytecodeViewTab 硬编码中文字符串

**位置:** `ui/code/BytecodeViewTab.java:37`
**问题:** `"// 无可用字节码"` 硬编码，未使用 `I18nUtil`。
**方案:** 改为 `I18nUtil.getString("bytecode.notavailable")`。

### P3-4. ClassInfoView 信息不完整

**位置:** `ui/code/ClassInfoView.java`
**问题:** 仅显示主版本号、访问标志、类名、超类、接口。缺少：方法列表、字段列表、内部类、注解、常量池浏览。
**方案:** 增加 TreeTableView 展示方法/字段详情，常量池面板。

### P3-5. WorkspaceIndex.indexClass() 宽泛异常捕获

**位置:** `model/WorkspaceIndex.java:88`
**问题:** `catch (Exception ignored)` 吞掉所有 ASM 解析错误，调用方无法判断是"类无成员"还是"类解析失败"。
**方案:** 至少用 `logger.warn` 记录，或抛出带上下文的 `DecompileException`。

### P3-6. IndexedMemberSearchProvider 死代码 subList

**位置:** `ui/search/IndexedMemberSearchProvider.java:54`
**方案:** 移除死代码。

### P3-7. FileTreeView cell factory 被覆盖（死代码）

**位置:** `ui/tree/FileTreeView.java:22` vs `ui/WorkspaceTabManager.java:115`
**问题:** `FileTreeView` 构造函数中设置了 `FileTreeCell` 工厂，但 `WorkspaceTabManager.installTreeContextMenu` 随后覆盖为新的工厂。
**方案:** 统一 cell factory 设置位置。

### P3-8. CommentSearchProvider 遗漏部分注释模式

**位置:** `ui/search/CommentSearchProvider.java:27-28`
**问题:** 无法检测行内 `/* 注释 */`（非行首的块注释），也不匹配独立的 `*` 行（Javadoc 中间行）。
**方案:** 扩展正则以覆盖行内块注释。

### P3-9. MethodSearchProvider 正则遗漏包级私有方法和注解

**位置:** `ui/search/MethodSearchProvider.java:26-31`
**方案:** 放宽访问修饰符匹配为可选，支持注解行前缀。

### P3-10. OpenFile.equals() 排除 sourceCode 可能导致语义错误

**位置:** `model/OpenFile.java:60-69`
**问题:** `equals()` 只基于 `fullPath` + `engine`。两个相同类名但反编译结果不同的 OpenFile 被视为相等。
**方案:** 评估是否应使用仅基于 `fullPath`+`engine` 的相等性（缓存键语义），若如此则文档说明即可。否则加入 `sourceCode`。

---

## 测试补充计划

| 模块 | 当前覆盖 | 目标 | 优先补充 |
|------|----------|------|----------|
| `config/` | 0% | 80% | AppConfig 加载/保存/迁移/去重 |
| `decompiler/` | <10% | 60% | DecompilerFactory 引擎选择/清理、各引擎基本反编译 |
| `model/` | <5% | 60% | WorkspaceIndex 构建/查询、PathNode 层级解析、CodeMetadata |
| `service/` | ~30% | 70% | BackgroundTasks 提交/取消、ClassTabOpener 三级缓存、SearchService 并发安全、ClassDiscoverer 各输入类型 |
| `ui/` | 0% | 20% | 关键对话框（Search/Settings/Export）基本交互 |
| `utils/` | 0% | 50% | I18nUtil 加载/切换/回退、CodeLinkHandler 引用解析 |

---

## 建议迭代计划

### 第一迭代（P0 + 关键 P1，预计 3-5 天）
1. P0-1: BytecodeCache 内存限制
2. P0-2: SearchService 数据竞争修复
3. P0-4: findOrRemoveOpenClassTab 迭代修复
4. P0-5: DecompilerFactory.cleanup 异常隔离
5. P0-6: JdDecompiler package 重复修复
6. P0-7: CfrDecompiler.replace 修复
7. P0-3: Vineflower Logger 接通
8. P1-1: 文件内搜索（Ctrl+F）
9. P1-2: 行跳转（Ctrl+G）
10. P1-5: ClassDiscoverer 延迟加载
11. P1-6: WorkspaceIndex 异步构建
12. P1-9: findNodeByPath 迭代化
13. P2-1 (部分): MainWindow 导出逻辑提取

### 第二迭代（P1 剩余，预计 3-5 天）
1. P1-3: 搜索高级选项
2. P1-4: 反编译器高级选项
3. P1-7: ExportService 字节码回退
4. P1-10: FileTreeBuilder 去重存储
5. P1-11: 文件树图标
6. P1-12: 搜索结果高亮定位
7. P1-13: 引擎输出对比
8. P1-14: 文本编码检测
9. P1-15: 搜索排除过滤
10. P2-2 ~ P2-12: 架构优化

### 第三迭代（P3 + 测试补充，预计 2-3 天）
1. P3-1 ~ P3-10: 代码清理
2. 测试补充：config + service + decompiler 核心路径
