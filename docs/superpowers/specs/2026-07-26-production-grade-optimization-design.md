# FxDecompiler 生产级优化 — 架构设计文档

日期: 2026-07-26
来源: `docs/2026-07-26-production-grade-optimization-plan.md`
状态: 已确认

## 1. 执行策略

采用**双线并行**策略，8 个阶段分为两条线交叉推进：

- **基础线**（低风险工程）：阶段 0（质量基线）、阶段 1（生命周期收敛）、阶段 4（配置生产化）、阶段 8（发布工程）
- **重构线**（中高架构收益）：阶段 2（ClassTabOpener 拆分）、阶段 3（控制器命令协调）、阶段 5（反编译模型结构化）、阶段 6（流水线治理）、阶段 7（大类拆分）

依赖关系：
- 阶段 0 必须最先执行，为后续所有重构提供架构测试护城河
- 阶段 1 完成后阶段 2 才能开始（ClassTabOpener 依赖 TaskExecutorService）
- 每轮结束门禁：`mvn -Pquality verify` 通过 + 全部测试通过 + 提交

## 2. 阶段 0：建立质量基线

### 2.1 架构边界文档

`docs/architecture-boundaries.md` 定义：

```
允许的依赖方向：
  controller → ui, service, model, config, util, decompiler
  ui → service, model, config, util, decompiler
  service → model, util, decompiler, classgraph (via classscan/)
  decompiler → model, util, core/jadx (via jadx/ adapter)
  core/jadx → util, model (轻量)
  classgraph → 独立，不依赖主应用任何包

禁止的依赖方向：
  service → controller, ui
  core/jadx → controller, ui, service
  classgraph → 主应用任何包（被 UI 直接引用）
  model → 任何其他项目包
```

### 2.2 架构测试

- `ArchitectureBoundaryTest` — JUnit 5 + 正则扫描 import，验证禁止方向
- 现有违规列入白名单，后续阶段逐步消除
- 新增违规触发 CI 失败

### 2.3 Maven Quality Profile

`pom.xml` 新增 `<profile id="quality">`:
- `maven-enforcer-plugin`: JDK 25+、Maven 3.9+、禁止依赖冲突
- `maven-surefire-plugin`: 固定版本、-Xmx1g
- `jacoco-maven-plugin`: 仅生成报告，不卡阈值

### 2.4 Smoke Test

`SmokeTest.java` — headless 模式验证核心路径

## 3. 阶段 1：生命周期与静态全局状态收敛

### 3.1 新增接口

```java
// service/task/TaskExecutorService.java
public interface TaskExecutorService extends AutoCloseable {
    Future<?> submit(TaskKind kind, String name, Runnable task);
    void cancel(Future<?> future);
    TaskSnapshot snapshot();
}

// service/decompile/CodeCacheStore.java
public interface CodeCacheStore {
    Optional<String> load(CodeCacheKey key);
    void save(CodeCacheKey key, String source);
    void invalidate(CodeCacheScope scope);
}

// service/decompile/DecompilerExecutionService.java
public interface DecompilerExecutionService {
    DecompileOutcome execute(DecompileRequest request);
    void cancel(String requestId);
}

// config/ConfigRepository.java
public interface ConfigRepository {
    AppConfig load();
    void save(AppConfig config);
    Path configFile();
}
```

### 3.2 ApplicationServices

新增 `app/ApplicationServices.java`，作为所有服务实例的 owner 和生命周期管理者。旧静态入口保留兼容层，内部委托给实例。

### 3.3 迁移策略

- `BackgroundTasks` → 保留静态方法，内部委托给 `TaskExecutorService` 实例
- `DecompilerRunner` → 抽出 `DecompilerExecutionService`，静态方法变为兼容门面
- `DiskCodeCache` → 实现 `CodeCacheStore`；`DecompileCache` 组合为 `CachingCodeCacheStore`
- `AppConfig.getInstance()` → `ConfigRepository.load()`

## 4. 阶段 2：ClassTabOpener 拆分

### 4.1 拆分目标

```
当前:  ClassTabOpener (1312行, ~30+ 方法, 混合 UI+业务+缓存)

拆分后:
  ui/code/
  ├── ClassTabOpener          → ~200行，纯 UI 编排
  ├── CodeTabFactory           → 创建 CodeEditorTab / loading tab / resource tab
  └── CodeTabState             → tab 状态对象 (requestId, generation, 状态机)

  service/decompile/
  ├── OpenClassUseCase         → 打开 class 的业务入口，不碰 JavaFX
  ├── DecompileRequest          → record
  ├── DecompileResult           → record
  ├── DecompileCacheService     → L2 + L3 协调
  ├── CodeMetadataService       → metadata / outline / 导航引用
  └── DecompileCancellation     → 取消 + 过期丢弃 + generation 管理
```

### 4.2 CodeTabState 状态机

```
IDLE → LOADING → SUCCESS
              → CANCELLED
              → ERROR
```

### 4.3 测试

`OpenClassUseCaseTest` 覆盖：缓存命中、超时、取消、过期结果丢弃、失败不写缓存

## 5. 阶段 3：控制器命令协调

### 5.1 WindowContext

```java
public final class WindowContext {
    private final Stage stage;
    private final WorkspaceSelectionModel workspaceSelection;
    private final EngineSelectionModel engineSelection;
    private final EditorPreferenceModel editorPreference;
    private final WindowCommandBus commandBus;
}
```

### 5.2 命令总线

菜单栏、工具栏、快捷键触发同一 `WindowCommand<T>`，消除重复业务逻辑。子控制器构造参数从 `MainWindow` 逐步改为 `WindowContext + 需要的 Service`。

## 6. 阶段 4：配置系统生产化

### 6.1 组件

- `ConfigRepository` — 配置读写的接口抽象，实现用 `AtomicFile`
- `ConfigMigrator` — schemaVersion 迁移集中管理，每次迁移前备份
- `ConfigValidator` — 窗口尺寸、主题路径、引擎枚举、搜索上限、字体大小范围
- 设置对话框编辑 `AppConfig.copy()`，保存走 repository

## 7. 阶段 5：反编译模型结构化

### 7.1 DecompileOutcome

```java
public sealed interface DecompileOutcome {
    record Success(String source, CodeMetadata metadata) implements DecompileOutcome {}
    record Failure(DecompilerTypeEnum engine, FailureKind kind,
                   String message, Throwable cause) implements DecompileOutcome {}
}

public enum FailureKind {
    CANCELLED, TIMEOUT, BUSY,
    CLASS_NOT_FOUND, ENGINE_ERROR,
    UNSUPPORTED_CLASS_VERSION, IO_ERROR
}
```

### 7.2 规则

- 底层返回 `DecompileOutcome`，不靠字符串匹配判断
- 缓存层只缓存 `Success`
- UI 层再把 `Failure` 转为本地化字符串
- 日志包含 engine、workspace、class、duration、failureKind

## 8. 阶段 6：工作区/搜索/导出流水线治理

### 8.1 WorkspaceLoadService

- `WorkspaceLoader` 静态工具 → `WorkspaceLoadService` 实例
- 返回 `WorkspaceLoadResult`（结构化错误）
- 最大深度/文件数可配置
- `Workspace.close()` 释放 ZipFile、缓存、索引、引用服务

### 8.2 SearchService

- `SearchCancellationToken` — 替代 `Thread.isInterrupted()`
- `SearchResultKey` — 去重逻辑独立
- `SearchDiagnostic` — provider 失败诊断摘要

### 8.3 ExportService

拆分为 `ExportPlanner` / `ExportWriter` / `ExportConflictResolver` / `ExportDecompiler`。写入阶段：路径穿越防护、冲突策略、部分失败处理。

## 9. 阶段 7：大类拆分与边界冻结

### 9.1 优先级

| 优先级 | 文件 | 行数 | 拆分方向 |
|--------|------|------|----------|
| P0 | RenameService | 2941 | RenameRepository + RenameIndex + RenameOperations + RenameSerialization |
| P1 | WorkspaceTabManager | 1321 | welcome、drag-drop、tab lifecycle、split view |
| P1 | SettingsDialog | ~800+ | 各设置页独立类 + 配置绑定 |
| P1 | BytecodeTextBuilder | ~600+ | class summary、constant pool、methods、attributes |

### 9.2 Vendored 内核边界

- `classgraph/` 和 `core/jadx/` 标记为「内核维护区」
- UI/controller 禁止直接修改
- 业务需求在适配层解决
- 内核变更附带最小可复现测试

## 10. 阶段 8：发布生产化

### 10.1 Maven Profiles

```
dev      → 快速编译 + exec:java
quality  → test + jacoco + enforcer + 架构测试
release  → clean package + 许可证清单 + SHA-256 checksum
```

### 10.2 发布产物

- `THIRD-PARTY-NOTICES.txt` — 覆盖所有第三方依赖和迁移源码
- `SHA256SUMS` — fat JAR checksum
- 启动脚本固定 JVM 参数

## 11. 执行顺序

```
第1轮: 阶段 0 (基础线)                          — 架构测试 + Maven门禁
第2轮: 阶段 1 (基础线) + 阶段 2 (重构线)        — 生命周期收敛 + ClassTabOpener拆分
第3轮: 阶段 4 (基础线) + 阶段 3 (重构线)        — 配置生产化 + 控制器命令协调
第4轮: (观察固化) + 阶段 5 (重构线)              — 反编译模型结构化
第5轮: 阶段 8 (基础线) + 阶段 6 (重构线)        — 发布工程 + 流水线治理
第6轮: (收尾验证) + 阶段 7 (重构线)             — 大类拆分 + 边界冻结
```

## 12. 不做的事

1. 不新增反编译引擎
2. 不新增 UI 功能入口
3. 不重做主题系统视觉设计
4. 不改用户可见工作流
5. 不一次性替换 JADX 或 ClassGraph 内核
6. 不为指标强行格式 vendored 代码
7. 不把 JavaFX 应用改成模块化运行

## 13. 最终验收

1. `mvn -Pquality verify` 是日常合并门禁
2. 主应用包依赖方向由架构测试保护
3. 核心路径有可回归测试
4. 反编译失败不依赖字符串判断
5. 配置支持 schema 迁移、校验、备份
6. UI 层不直接持有核心异步流程
7. 第三方内核有清晰边界
8. release 产物包含 jar、许可证、checksum
