# FxDecompiler 生产级优化 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 FxDecompiler 从功能完备的原型升级为可长期维护、可验证、可发布的生产级工程

**Architecture:** 双线并行 — 基础线（质量基线/生命周期/配置/发布）提供安全网，重构线（拆分/协调/模型/治理）提升架构质量。阶段 0 必须最先执行。

**Tech Stack:** Java 25, JavaFX 25.0.1, Maven, JUnit 5, ASM 9.9, Jackson 2.19.4

---

## 文件结构概览

本轮优化新增/修改的关键文件：

```
src/main/java/com/bingbaihanji/fxdecomplie/
├── app/                          # 新增: 应用装配层
│   ├── ApplicationServices.java
│   └── ServiceRegistry.java
├── service/
│   ├── task/                     # 新增: 任务执行器抽象
│   │   ├── TaskKind.java
│   │   ├── TaskSnapshot.java
│   │   ├── TaskExecutorService.java
│   │   └── ThreadPoolTaskExecutor.java
│   ├── decompile/                # 新增: 反编译用例层
│   │   ├── CodeCacheStore.java
│   │   ├── CodeCacheKey.java
│   │   ├── CodeCacheScope.java
│   │   ├── DecompilerExecutionService.java
│   │   ├── DecompileRequest.java
│   │   ├── DecompileResult.java
│   │   ├── DecompileOutcome.java
│   │   ├── FailureKind.java
│   │   ├── OpenClassUseCase.java
│   │   ├── DecompileCancellation.java
│   │   └── DecompileCacheService.java
│   ├── workspace/                # 新增: 工作区服务
│   │   ├── WorkspaceLoadResult.java
│   │   └── WorkspaceLoadService.java
│   └── BackgroundTasks.java      # 修改: 委托给TaskExecutorService
├── controller/
│   ├── command/                  # 新增: 命令总线
│   │   ├── WindowCommand.java
│   │   ├── WindowCommandBus.java
│   │   ├── OpenFileCommand.java
│   │   └── SwitchEngineCommand.java
│   └── WindowContext.java        # 新增
├── config/
│   ├── ConfigRepository.java     # 新增
│   ├── ConfigMigrator.java       # 新增
│   └── ConfigValidator.java      # 新增
├── decompiler/
│   ├── DecompileOutcome.java     # 新增 sealed interface
│   └── FailureKind.java          # 新增 enum
└── ui/code/
    ├── CodeTabFactory.java       # 新增
    ├── CodeTabState.java         # 新增
    └── ClassTabOpener.java       # 修改: 瘦身

src/test/java/com/bingbaihanji/fxdecomplie/
├── architecture/                 # 新增
│   └── ArchitectureBoundaryTest.java
├── service/
│   ├── task/TaskExecutorServiceTest.java
│   ├── decompile/
│   │   ├── OpenClassUseCaseTest.java
│   │   ├── DecompileCacheServiceTest.java
│   │   └── DecompilerExecutionServiceTest.java
├── config/
│   ├── ConfigRepositoryTest.java
│   ├── ConfigMigratorTest.java
│   └── ConfigValidatorTest.java
└── smoke/SmokeTest.java
```

---

## 阶段 0：建立质量基线

### Task 0.1: 架构边界文档

**Files:**
- Create: `docs/architecture-boundaries.md`

- [ ] **编写架构边界文档**

```markdown
# FxDecompiler 架构边界规则

## 允许的依赖方向

```
controller → ui, service, model, config, util, decompiler
ui → service, model, config, util, decompiler
service → model, util, decompiler, classgraph (via classscan/)
decompiler → model, util, core/jadx (via jadx/ adapter)
core/jadx → util, model (轻量级,仅限 constants/io/text 等基础工具)
classgraph → 独立,不依赖主应用任何包
model → 不依赖任何项目包 (纯数据结构)
config → 不依赖 controller/ui (纯配置POJO)
```

## 禁止的依赖方向

| 规则 | 说明 |
|------|------|
| service → controller | 业务层不能依赖控制器 |
| service → ui | 业务层不能依赖UI(已有例外需白名单) |
| core/jadx → controller | 内核不能依赖控制器 |
| core/jadx → ui | 内核不能依赖UI |
| core/jadx → service | 内核不能依赖业务服务 |
| classgraph → 主应用任何包 | 扫描内核隔离 |
| UI 直接引用 classgraph | 必须通过 service/classscan/ |

## 白名单（现有违规,待后续阶段消除）

(执行架构测试扫描后填充)
```

- [ ] **提交**

```bash
git add docs/architecture-boundaries.md
git commit -m "docs: 架构边界规则文档"
```

---

### Task 0.2: 架构边界测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/architecture/ArchitectureBoundaryTest.java`

- [ ] **编写架构测试**

```java
package com.bingbaihanji.fxdecomplie.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 扫描所有 main 源码的 import 语句,验证包依赖边界不违反架构规则
 */
@DisplayName("架构边界验证")
final class ArchitectureBoundaryTest {

    private static final Path MAIN_SRC = Paths.get("src/main/java");
    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^import\\s+(com\\.bingbaihanji\\.[\\w.]+);");

    private static final String BASE = "com.bingbaihanji.fxdecomplie";
    private static final String CG = "com.bingbaihanji.classgraph";

    /**
     * 每条禁止规则: {源包前缀, 禁止import的包前缀, 规则描述}
     */
    record ForbiddenRule(String srcPkg, String forbiddenImport, String description) {}

    private static final List<ForbiddenRule> RULES = List.of(
            new ForbiddenRule(BASE + ".service", BASE + ".controller",
                    "service 不能依赖 controller"),
            new ForbiddenRule(BASE + ".service", BASE + ".ui",
                    "service 不能依赖 ui"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".controller",
                    "core/jadx 不能依赖 controller"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".ui",
                    "core/jadx 不能依赖 ui"),
            new ForbiddenRule(BASE + ".core.jadx", BASE + ".service",
                    "core/jadx 不能依赖 service"),
            new ForbiddenRule(BASE + ".model", BASE + ".",
                    "model 不能依赖任何项目包")
    );

    /**
     * 白名单: 已知违规,后续阶段逐步消除。格式 "源文件路径::违规import"
     */
    private static final Set<String> WHITELIST = Set.of(
            // 阶段1-2会重构这些,先列入白名单
    );

    @Test
    @DisplayName("禁止的包依赖方向")
    void shouldNotViolateDependencyBoundaries() throws IOException {
        Map<String, String> sourceFiles = readAllJavaSources(MAIN_SRC);

        List<String> violations = new ArrayList<>();
        int totalImports = 0;

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();
            String pkg = extractPackage(content);
            if (pkg == null) continue;

            Matcher m = IMPORT_PATTERN.matcher(content);
            while (m.find()) {
                totalImports++;
                String imported = m.group(1);

                for (ForbiddenRule rule : RULES) {
                    if (pkg.startsWith(rule.srcPkg)
                            && imported.startsWith(rule.forbiddenImport)
                            && !isSelfImport(pkg, imported)) {
                        String violationKey = relPath + "::" + imported;
                        if (!WHITELIST.contains(violationKey)) {
                            violations.add(relPath + " imports " + imported
                                    + " — " + rule.description);
                        }
                    }
                }
            }
        }

        // 额外检查: UI不能直接引用 classgraph
        checkClassGraphIsolation(sourceFiles, violations);

        System.out.println("扫描 " + sourceFiles.size() + " 个文件, "
                + totalImports + " 条 import, 违规 " + violations.size() + " 条");
        if (!violations.isEmpty()) {
            violations.forEach(System.out::println);
        }
        assertTrue(violations.isEmpty(),
                "发现 " + violations.size() + " 条架构违规:\n"
                        + String.join("\n", violations));
    }

    @Test
    @DisplayName("ClassGraph 内核隔离")
    void shouldIsolateClassGraph() throws IOException {
        Map<String, String> sourceFiles = readAllJavaSources(MAIN_SRC);
        List<String> violations = new ArrayList<>();

        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String relPath = entry.getKey();
            String content = entry.getValue();

            // classgraph 包内文件不能 import 主应用
            if (relPath.contains("com/bingbaihanji/classgraph/")) {
                Matcher m = IMPORT_PATTERN.matcher(content);
                while (m.find()) {
                    String imported = m.group(1);
                    if (imported.startsWith(BASE) || imported.startsWith("com.bingbaihanji.windows")) {
                        violations.add(relPath + " (classgraph) imports " + imported);
                    }
                }
            }

            // 主应用文件不能直接 import classgraph (除非在 service/classscan/ 或 service/reference/)
            if (relPath.contains("com/bingbaihanji/fxdecomplie/")
                    && !relPath.contains("service/classscan/")
                    && !relPath.contains("service/reference/")) {
                Matcher m = IMPORT_PATTERN.matcher(content);
                while (m.find()) {
                    String imported = m.group(1);
                    if (imported.startsWith(CG)) {
                        violations.add(relPath + " imports " + imported
                                + " — UI/controller 禁止直接引用 ClassGraph");
                    }
                }
            }
        }

        assertTrue(violations.isEmpty(),
                "ClassGraph 隔离违规:\n" + String.join("\n", violations));
    }

    // === helpers ===

    private Map<String, String> readAllJavaSources(Path root) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            String rel = root.relativize(p).toString().replace('\\', '/');
                            result.put(rel, content);
                        } catch (IOException ignored) {}
                    });
        }
        return result;
    }

    private String extractPackage(String source) {
        Matcher m = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE)
                .matcher(source);
        return m.find() ? m.group(1) : null;
    }

    private boolean isSelfImport(String pkg, String imported) {
        return imported.startsWith(pkg + ".");
    }

    private void checkClassGraphIsolation(Map<String, String> files,
                                           List<String> violations) {
        // 已在 shouldIsolateClassGraph 独立测试中验证
    }
}
```

- [ ] **运行架构测试,获取基线违规数**

```bash
./mvnw test -Dtest="ArchitectureBoundaryTest" -pl .
```

- [ ] **如有违规,填充白名单并提交**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/architecture/ArchitectureBoundaryTest.java
git commit -m "test: 新增架构边界测试"
```

---

### Task 0.3: Maven Quality Profile

**Files:**
- Modify: `pom.xml`

- [ ] **在 pom.xml 添加 quality profile**

在 `</build>` 之后、`</project>` 之前插入:

```xml
<profiles>
    <profile>
        <id>quality</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-enforcer-plugin</artifactId>
                    <version>3.5.0</version>
                    <executions>
                        <execution>
                            <id>enforce-java-maven</id>
                            <goals><goal>enforce</goal></goals>
                            <configuration>
                                <rules>
                                    <requireJavaVersion>
                                        <version>[25,)</version>
                                    </requireJavaVersion>
                                    <requireMavenVersion>
                                        <version>[3.9,)</version>
                                    </requireMavenVersion>
                                    <dependencyConvergence/>
                                    <banDuplicatePomDependencyVersions/>
                                </rules>
                            </configuration>
                        </execution>
                    </executions>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.2</version>
                    <configuration>
                        <argLine>-Xmx1g</argLine>
                        <includes>
                            <include>**/*Test.java</include>
                            <include>**/*Tests.java</include>
                        </includes>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.jacoco</groupId>
                    <artifactId>jacoco-maven-plugin</artifactId>
                    <version>0.8.12</version>
                    <executions>
                        <execution>
                            <id>prepare-agent</id>
                            <goals><goal>prepare-agent</goal></goals>
                        </execution>
                        <execution>
                            <id>report</id>
                            <phase>test</phase>
                            <goals><goal>report</goal></goals>
                        </execution>
                    </executions>
                    <!-- 先只生成报告,不卡覆盖率阈值 -->
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

- [ ] **验证 quality profile 可运行**

```bash
./mvnw verify -Pquality -DskipTests
```

- [ ] **运行全量测试**

```bash
./mvnw test -Pquality
```

- [ ] **提交**

```bash
git add pom.xml
git commit -m "build: 新增 quality profile (enforcer + surefire + jacoco)"
```

---

### Task 0.4: Smoke Test

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/smoke/SmokeTest.java`

- [ ] **编写核心路径 smoke test**

```java
package com.bingbaihanji.fxdecomplie.smoke;

import com.bingbaihanji.fxdecomplie.config.AppConfig;
import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.context.AppContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.service.*;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 核心路径冒烟测试
 * 不启动 JavaFX,只验证关键对象的创建和销毁
 */
@DisplayName("应用冒烟测试")
final class SmokeTest {

    @Test
    @DisplayName("应用上下文启动和关闭")
    void shouldStartAndShutdownAppContext() {
        AppContext ctx = AppContext.getInstance();
        AppConfig config = ctx.start();
        assertNotNull(config, "配置不能为空");
        assertNotNull(config.getSchemaVersion(), "schema version 不能为空");

        ctx.shutdown();
        // 关闭后不应抛异常
    }

    @Test
    @DisplayName("所有反编译引擎可创建")
    void shouldCreateAllDecompilerEngines() {
        for (DecompilerTypeEnum engine : DecompilerTypeEnum.values()) {
            assertNotNull(DecompilerFactory.getDecompiler(engine),
                    engine + " 引擎不应为空");
        }
    }

    @Test
    @DisplayName("后台任务池可正常工作")
    void shouldExecuteBackgroundTask() throws Exception {
        java.util.concurrent.Future<Boolean> future = BackgroundTasks.run(
                "smoke-test",
                () -> { /* no-op, 只验证任务可提交 */ }
        );
        future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertTrue(future.isDone());
    }

    @Test
    @DisplayName("应用路径目录可解析")
    void shouldResolveAppPaths() {
        assertNotNull(AppPaths.appDir(), "应用目录不能为空");
        assertNotNull(AppPaths.cacheDir(), "缓存目录不能为空");
        assertNotNull(AppPaths.configDir(), "配置目录不能为空");
    }

    @Test
    @DisplayName("缓存可读写")
    void shouldReadWriteCache() {
        DecompileCache cache = new DecompileCache();
        cache.put("test-ws", "com/example/Test", DecompilerTypeEnum.VINEFLOWER,
                "default", "public class Test {}");
        String result = cache.get("test-ws", "com/example/Test",
                DecompilerTypeEnum.VINEFLOWER, "default");
        assertEquals("public class Test {}", result);
        cache.invalidate("test-ws", "com/example/Test");
        assertNull(cache.get("test-ws", "com/example/Test",
                DecompilerTypeEnum.VINEFLOWER, "default"));
    }
}
```

- [ ] **运行 smoke test**

```bash
./mvnw test -Dtest="SmokeTest"
```

- [ ] **提交**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/smoke/SmokeTest.java
git commit -m "test: 新增核心路径冒烟测试"
```

**阶段 0 验收:**
```bash
./mvnw test -Pquality  # 全部测试通过
./mvnw verify -Pquality  # enforcer + jacoco report 生成
```

---

## 阶段 1：生命周期与静态全局状态收敛

### Task 1.1: TaskKind + TaskSnapshot + TaskExecutorService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/task/TaskKind.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/task/TaskSnapshot.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/task/TaskExecutorService.java`

- [ ] **创建 TaskKind 枚举**

```java
package com.bingbaihanji.fxdecomplie.service.task;

/** 任务类型，用于路由到合适的线程池 */
public enum TaskKind {
    /** 搜索、打开类、Ctrl+Click 等 UI 触发的交互操作 */
    INTERACTIVE,
    /** 磁盘缓存写入、索引构建等磁盘/CPU 密集型批处理 */
    IO,
    /** 导出任务专用 */
    EXPORT,
    /** 反编译任务 (独立池, 避免阻塞短任务) */
    DECOMPILE
}
```

- [ ] **创建 TaskSnapshot 记录**

```java
package com.bingbaihanji.fxdecomplie.service.task;

/** 任务池快照 */
public record TaskSnapshot(int activeCount, int queueLength, int poolSize) {
    public static final TaskSnapshot EMPTY = new TaskSnapshot(0, 0, 0);
}
```

- [ ] **创建 TaskExecutorService 接口**

```java
package com.bingbaihanji.fxdecomplie.service.task;

import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.concurrent.RejectedExecutionException;

public interface TaskExecutorService extends AutoCloseable {
    Future<?> submit(TaskKind kind, String name, Runnable task);

    Future<?> submit(TaskKind kind, String name, Runnable task,
                     Consumer<RejectedExecutionException> onRejected);

    void cancel(Future<?> future);

    TaskSnapshot snapshot(TaskKind kind);

    int totalPending();

    @Override
    void close();
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/task/
git commit -m "feat(task): 新增 TaskExecutorService 接口和类型"
```

---

### Task 1.2: ThreadPoolTaskExecutor 实现

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/task/ThreadPoolTaskExecutor.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/BackgroundTasks.java`

- [ ] **编写 ThreadPoolTaskExecutor — 从 BackgroundTasks 提取线程池管理逻辑**

```java
package com.bingbaihanji.fxdecomplie.service.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ThreadPoolTaskExecutor implements TaskExecutorService {

    private static final Logger log = LoggerFactory.getLogger(ThreadPoolTaskExecutor.class);
    private final Map<TaskKind, ThreadPoolExecutor> pools;

    public ThreadPoolTaskExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int interactiveThreads = Math.clamp(cores, 4, 8);
        int ioThreads = 2;
        int exportThreads = Math.clamp(cores, 2, 4);
        int decompileCores = Math.max(1, cores / 2);
        int decompileMax = Math.min(decompileCores + 1, 4);

        pools = Map.of(
                TaskKind.INTERACTIVE, createPool(interactiveThreads, 200, "bg-int"),
                TaskKind.IO, createPool(ioThreads, Integer.MAX_VALUE, "bg-io"),
                TaskKind.EXPORT, createPool(exportThreads, 50, "bg-exp"),
                TaskKind.DECOMPILE, createPool(decompileCores, 4, "bg-decomp")
        );
    }

    private static ThreadPoolExecutor createPool(int threads, int queueSize,
                                                  String prefix) {
        ThreadPoolExecutor ex = new ThreadPoolExecutor(
                threads, threads, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueSize),
                r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName(prefix + "-" + t.threadId());
                    return t;
                });
        ex.allowCoreThreadTimeOut(true);
        return ex;
    }

    @Override
    public Future<?> submit(TaskKind kind, String name, Runnable task) {
        return submit(kind, name, task, null);
    }

    @Override
    public Future<?> submit(TaskKind kind, String name, Runnable task,
                            Consumer<RejectedExecutionException> onRejected) {
        ThreadPoolExecutor pool = pools.get(kind);
        try {
            log.debug("提交任务[{}]: {} (队列: {})", kind, name, pool.getQueue().size());
            return pool.submit(() -> {
                String prev = Thread.currentThread().getName();
                Thread.currentThread().setName(name);
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        log.debug("任务启动前被取消: {}", name);
                        return;
                    }
                    task.run();
                } catch (Exception e) {
                    log.error("任务异常: {}", name, e);
                    throw e;
                } finally {
                    Thread.currentThread().setName(prev);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("任务被拒绝(队列满)[{}]: {}", kind, name);
            if (onRejected != null) {
                try { onRejected.accept(e); } catch (RuntimeException ignored) {}
            }
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(e);
            return failed;
        }
    }

    @Override
    public void cancel(Future<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    @Override
    public TaskSnapshot snapshot(TaskKind kind) {
        ThreadPoolExecutor pool = pools.get(kind);
        return new TaskSnapshot(pool.getActiveCount(),
                pool.getQueue().size(), pool.getPoolSize());
    }

    @Override
    public int totalPending() {
        return pools.values().stream()
                .mapToInt(p -> p.getQueue().size() + p.getActiveCount())
                .sum();
    }

    @Override
    public void close() {
        for (ThreadPoolExecutor pool : pools.values()) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(3, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

- [ ] **修改 BackgroundTasks — 内部委托给 ThreadPoolTaskExecutor**

```java
// 在 BackgroundTasks 类顶部添加:
private static final ThreadPoolTaskExecutor DELEGATE = new ThreadPoolTaskExecutor();

// 将所有方法内部实现委托给 DELEGATE,例如:
public static Future<?> run(PoolType type, String name, Runnable task,
                            Consumer<RejectedExecutionException> onRejected) {
    return DELEGATE.submit(mapType(type), name, task, onRejected);
}

private static TaskKind mapType(PoolType type) {
    return switch (type) {
        case INTERACTIVE -> TaskKind.INTERACTIVE;
        case IO -> TaskKind.IO;
        case EXPORT -> TaskKind.EXPORT;
    };
}
```

- [ ] **运行测试验证兼容性**

```bash
./mvnw test -Dtest="BackgroundTasksTest,TaskPoolsTest"
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/task/ThreadPoolTaskExecutor.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/BackgroundTasks.java
git commit -m "refactor(task): BackgroundTasks 委托给 ThreadPoolTaskExecutor"
```

---

### Task 1.3: CodeCacheStore 接口 + CodeCacheKey + CodeCacheScope

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheStore.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheKey.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheScope.java`

- [ ] **创建 CodeCacheKey**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

public record CodeCacheKey(String workspaceKey, String internalName,
                           DecompilerTypeEnum engine, String optionsHash) {
    private static final String SEP = "\0";

    /** 构建用于精确匹配的复合键 */
    public String compositeKey() {
        return workspaceKey + SEP + internalName + SEP + SEP
                + engine.name() + SEP + optionsHash;
    }

    /** 构建用于前缀失效的键 */
    public String invalidationPrefix() {
        return workspaceKey + SEP + internalName + SEP + SEP;
    }
}
```

- [ ] **创建 CodeCacheScope**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

public enum CodeCacheScope {
    /** 失效单个类在所有引擎下的缓存 */
    CLASS,
    /** 失效整个工作区的所有缓存 */
    WORKSPACE,
    /** 清空全部缓存 */
    ALL
}
```

- [ ] **创建 CodeCacheStore 接口**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import java.util.Optional;

public interface CodeCacheStore {
    Optional<String> load(CodeCacheKey key);
    void save(CodeCacheKey key, String source);
    void invalidate(CodeCacheKey key, CodeCacheScope scope);
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheKey.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheScope.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeCacheStore.java
git commit -m "feat(decompile): 新增 CodeCacheStore 接口和缓存模型"
```

---

### Task 1.4: DecompileCache + DiskCodeCache 实现 CodeCacheStore

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/DecompileCache.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/DiskCodeCache.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileCacheService.java`

- [ ] **DecompileCache 实现 CodeCacheStore (L2)**

```java
// 添加 implements CodeCacheStore
public class DecompileCache implements CodeCacheStore {

    @Override
    public Optional<String> load(CodeCacheKey key) {
        return Optional.ofNullable(get(key.workspaceKey(), key.internalName(),
                key.engine(), key.optionsHash()));
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        put(key.workspaceKey(), key.internalName(),
                key.engine(), key.optionsHash(), source);
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        switch (scope) {
            case CLASS -> invalidate(key.workspaceKey(), key.internalName());
            case WORKSPACE, ALL -> clear();
        }
    }
    // ... 保留原有方法作兼容
}
```

- [ ] **DiskCodeCache 实现 CodeCacheStore (L3)**

```java
// 将 static 方法改为实例方法,添加 implements CodeCacheStore
public final class DiskCodeCache implements CodeCacheStore {

    private final Path cacheRoot;

    public DiskCodeCache(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    // 保留静态工厂方法作兼容
    private static DiskCodeCache defaultInstance;

    public static DiskCodeCache getDefault() {
        if (defaultInstance == null) {
            defaultInstance = new DiskCodeCache(AppPaths.cacheDir());
        }
        return defaultInstance;
    }

    @Override
    public Optional<String> load(CodeCacheKey key) { ... }

    @Override
    public void save(CodeCacheKey key, String source) { ... }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) { ... }
    // ... 保留原有 static 兼容方法
}
```

- [ ] **创建 DecompileCacheService — L2+L3 协调**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.service.DecompileCache;
import com.bingbaihanji.fxdecomplie.service.DiskCodeCache;

import java.util.Optional;

/**
 * L2 (内存) + L3 (磁盘) 两级缓存协调:
 * 先查 L2 → 命中返回; 未命中查 L3 → 命中后回填 L2; 均未命中返回 empty
 */
public class DecompileCacheService implements CodeCacheStore {
    private final DecompileCache l2;
    private final DiskCodeCache l3;

    public DecompileCacheService(DecompileCache l2, DiskCodeCache l3) {
        this.l2 = l2;
        this.l3 = l3;
    }

    @Override
    public Optional<String> load(CodeCacheKey key) {
        Optional<String> result = l2.load(key);
        if (result.isPresent()) return result;

        result = l3.load(key);
        result.ifPresent(source -> l2.save(key, source)); // 回填 L2
        return result;
    }

    @Override
    public void save(CodeCacheKey key, String source) {
        l2.save(key, source);
        // L3 异步写入,不阻塞调用方
        BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                "cache-l3-write", () -> l3.save(key, source));
    }

    @Override
    public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
        l2.invalidate(key, scope);
        l3.invalidate(key, scope);
    }
}
```

- [ ] **运行测试**

```bash
./mvnw test -Dtest="DecompileCacheTest"
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/DecompileCache.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/DiskCodeCache.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileCacheService.java
git commit -m "refactor(decompile): DecompileCache + DiskCodeCache 实现 CodeCacheStore"
```

---

### Task 1.5: DecompilerExecutionService 接口 + 实现

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileRequest.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileResult.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompilerExecutionService.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/service/DecompilerRunner.java`

- [ ] **创建 DecompileRequest**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;

import java.util.concurrent.TimeUnit;

public record DecompileRequest(
        Workspace workspace,
        FileTreeNode classNode,
        DecompilerTypeEnum preferredEngine,
        String optionsHash,
        String requestId,
        long timeout,
        TimeUnit timeoutUnit) {

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Workspace workspace;
        private FileTreeNode classNode;
        private DecompilerTypeEnum preferredEngine = DecompilerTypeEnum.JADX;
        private String optionsHash = "default";
        private String requestId = java.util.UUID.randomUUID().toString();
        private long timeout = 30;
        private TimeUnit timeoutUnit = TimeUnit.SECONDS;

        public Builder workspace(Workspace v) { workspace = v; return this; }
        public Builder classNode(FileTreeNode v) { classNode = v; return this; }
        public Builder preferredEngine(DecompilerTypeEnum v) { preferredEngine = v; return this; }
        public Builder optionsHash(String v) { optionsHash = v; return this; }
        public Builder requestId(String v) { requestId = v; return this; }
        public Builder timeout(long v) { timeout = v; return this; }
        public Builder timeoutUnit(TimeUnit v) { timeoutUnit = v; return this; }
        public DecompileRequest build() {
            if (workspace == null) throw new IllegalStateException("workspace is required");
            if (classNode == null) throw new IllegalStateException("classNode is required");
            return new DecompileRequest(workspace, classNode, preferredEngine,
                    optionsHash, requestId, timeout, timeoutUnit);
        }
    }
}
```

- [ ] **创建 DecompileResult**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;

public record DecompileResult(
        DecompileOutcome outcome,
        String requestId,
        long generation,
        long elapsedMs) {

    public boolean isSuccess() {
        return outcome instanceof DecompileOutcome.Success;
    }
}
```

- [ ] **创建 DecompilerExecutionService 接口**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import java.util.concurrent.Future;

public interface DecompilerExecutionService {
    Future<DecompileResult> submit(DecompileRequest request);
    void cancel(String requestId);
    int activeCount();
    int timeoutCount();
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileRequest.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileResult.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompilerExecutionService.java
git commit -m "feat(decompile): 新增 DecompilerExecutionService 接口和 DTO"
```

---

### Task 1.6: ApplicationServices 装配类

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/app/ApplicationServices.java`

- [ ] **创建 ApplicationServices**

```java
package com.bingbaihanji.fxdecomplie.app;

import com.bingbaihanji.fxdecomplie.config.ConfigRepository;
import com.bingbaihanji.fxdecomplie.constants.AppPaths;
import com.bingbaihanji.fxdecomplie.service.*;
import com.bingbaihanji.fxdecomplie.service.decompile.*;
import com.bingbaihanji.fxdecomplie.service.task.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 应用服务装配和生命周期管理
 * 所有服务的创建、依赖注入、关闭都在此处统一处理
 */
public final class ApplicationServices implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApplicationServices.class);
    private static volatile ApplicationServices instance;

    private final Path appDir;
    private final TaskExecutorService taskExecutor;
    private final DecompileCache l2Cache;
    private final DiskCodeCache l3Cache;
    private final DecompileCacheService cacheService;
    private final DecompilerExecutionService decompilerExecution;

    private ApplicationServices(Path appDir) {
        this.appDir = appDir;
        this.taskExecutor = new ThreadPoolTaskExecutor();
        this.l2Cache = new DecompileCache();
        this.l3Cache = new DiskCodeCache(AppPaths.cacheDir());
        this.cacheService = new DecompileCacheService(l2Cache, l3Cache);
        this.decompilerExecution = new DecompilerRunnerAdapter(taskExecutor);
        log.info("应用服务已初始化: {}", appDir);
    }

    public static ApplicationServices create(Path appDir) {
        if (instance == null) {
            synchronized (ApplicationServices.class) {
                if (instance == null) {
                    instance = new ApplicationServices(appDir);
                }
            }
        }
        return instance;
    }

    public static ApplicationServices getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ApplicationServices 尚未初始化");
        }
        return instance;
    }

    // --- 服务访问器 ---

    public TaskExecutorService taskExecutor() { return taskExecutor; }
    public CodeCacheStore codeCache() { return cacheService; }
    public DecompilerExecutionService decompilerExecution() { return decompilerExecution; }
    public Path appDir() { return appDir; }

    @Override
    public void close() {
        log.info("关闭应用服务");
        shutdownQuietly("decompiler", () -> {/* DecompilerFactory.cleanup() */});
        shutdownQuietly("taskExecutor", taskExecutor);
        instance = null;
    }

    private static void shutdownQuietly(String name, AutoCloseable closeable) {
        try { closeable.close(); } catch (Exception e) {
            log.warn("关闭失败: {}", name, e);
        }
    }

    private static void shutdownQuietly(String name, Runnable action) {
        try { action.run(); } catch (Exception e) {
            log.warn("关闭失败: {}", name, e);
        }
    }
}
```

- [ ] **暂不修改 AppContext（阶段 2 统一改），先编译验证**

```bash
./mvnw compile -DskipTests
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/app/ApplicationServices.java
git commit -m "feat(app): 新增 ApplicationServices 服务装配"
```

---

### Task 1.7: ConfigRepository 接口

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigRepository.java`

- [ ] **创建 ConfigRepository 接口**

```java
package com.bingbaihanji.fxdecomplie.config;

import java.nio.file.Path;

public interface ConfigRepository {
    AppConfig load();
    void save(AppConfig config);
    Path configFile();
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigRepository.java
git commit -m "feat(config): 新增 ConfigRepository 接口"
```

**阶段 1 验收:**
```bash
./mvnw test -Pquality  # 全部测试通过,新接口编译通过
```

---

## 阶段 2：ClassTabOpener 拆分

### Task 2.1: CodeTabState + CodeTabFactory

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeTabState.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeTabFactory.java`

- [ ] **创建 CodeTabState**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import java.util.concurrent.atomic.AtomicLong;

/** tab 上挂载的状态对象，跟踪反编译请求生命周期 */
public final class CodeTabState {

    public enum Phase { IDLE, LOADING, SUCCESS, CANCELLED, ERROR }

    private final AtomicLong generation = new AtomicLong(0);
    private volatile Phase phase = Phase.IDLE;
    private volatile String currentRequestId;
    private volatile String errorMessage;

    public long nextGeneration() {
        phase = Phase.LOADING;
        return generation.incrementAndGet();
    }

    public long currentGeneration() {
        return generation.get();
    }

    public boolean isCurrent(long gen) {
        return generation.get() == gen;
    }

    public void setSuccess() { phase = Phase.SUCCESS; }
    public void setCancelled() { phase = Phase.CANCELLED; }
    public void setError(String msg) { phase = Phase.ERROR; errorMessage = msg; }
    public void setIdle() { phase = Phase.IDLE; }

    public Phase phase() { return phase; }
    public String errorMessage() { return errorMessage; }
    public String currentRequestId() { return currentRequestId; }
    public void setCurrentRequestId(String id) { this.currentRequestId = id; }
}
```

- [ ] **创建 CodeTabFactory**

```java
package com.bingbaihanji.fxdecomplie.ui.code;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.Tab;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 创建/查找 CodeEditorTab，管理 tab → state 映射 */
public final class CodeTabFactory {

    private final Map<FileTreeNode, CodeEditorTab> nodeToTab = new ConcurrentHashMap<>();
    private final Map<CodeEditorTab, CodeTabState> tabStates = new ConcurrentHashMap<>();

    public record TabResult(CodeEditorTab tab, CodeTabState state, boolean isNew) {}

    public TabResult findOrCreate(FileTreeNode node) {
        CodeEditorTab existing = nodeToTab.get(node);
        if (existing != null) {
            CodeTabState state = tabStates.get(existing);
            return new TabResult(existing, state, false);
        }
        CodeEditorTab newTab = new CodeEditorTab(node);
        CodeTabState state = new CodeTabState();
        nodeToTab.put(node, newTab);
        tabStates.put(newTab, state);
        return new TabResult(newTab, state, true);
    }

    public CodeTabState stateOf(CodeEditorTab tab) {
        return tabStates.get(tab);
    }

    public void remove(CodeEditorTab tab) {
        tabStates.remove(tab);
        nodeToTab.values().remove(tab);
    }
}
```

- [ ] **编译验证**

```bash
./mvnw compile -DskipTests
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeTabState.java
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeTabFactory.java
git commit -m "refactor(ui): 从 ClassTabOpener 抽出 CodeTabState + CodeTabFactory"
```

---

### Task 2.2: OpenClassUseCase + DecompileCancellation + CodeMetadataService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/OpenClassUseCase.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileCancellation.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/CodeMetadataService.java`

- [ ] **创建 DecompileCancellation**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

/** 管理反编译任务的取消与过期结果丢弃 */
public final class DecompileCancellation {
    private final Map<String, Future<?>> activeRequests = new ConcurrentHashMap<>();

    public void register(String requestId, Future<?> future) {
        activeRequests.put(requestId, future);
    }

    public void cancel(String requestId) {
        Future<?> future = activeRequests.remove(requestId);
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
    }

    public void complete(String requestId) {
        activeRequests.remove(requestId);
    }

    public int activeCount() {
        return activeRequests.size();
    }

    public void cancelAll() {
        activeRequests.values().forEach(f -> f.cancel(true));
        activeRequests.clear();
    }
}
```

- [ ] **创建 OpenClassUseCase（核心业务逻辑，不依赖 JavaFX）**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Future;

/**
 * 打开 class 文件的业务用例
 * 不接触 JavaFX,返回纯数据结果供 UI 层消费
 */
public final class OpenClassUseCase {
    private static final Logger log = LoggerFactory.getLogger(OpenClassUseCase.class);

    private final CodeCacheStore cacheStore;
    private final DecompilerExecutionService decompilerExecution;
    private final DecompileCancellation cancellation;

    public OpenClassUseCase(CodeCacheStore cacheStore,
                            DecompilerExecutionService decompilerExecution,
                            DecompileCancellation cancellation) {
        this.cacheStore = cacheStore;
        this.decompilerExecution = decompilerExecution;
        this.cancellation = cancellation;
    }

    /**
     * @param workspace 当前工作区
     * @param node 要打开的类节点
     * @param engine 首选引擎
     * @param optionsHash 反编译选项哈希
     * @return 包含结果和 requestId 的 LoadResult
     */
    public LoadResult load(Workspace workspace, FileTreeNode node,
                           DecompilerTypeEnum engine, String optionsHash) {
        String internalName = node.internalName();
        String wsKey = workspace.workspaceKey();
        String requestId = java.util.UUID.randomUUID().toString();

        CodeCacheKey cacheKey = new CodeCacheKey(wsKey, internalName, engine, optionsHash);

        // 1. 查缓存
        Optional<String> cached = cacheStore.load(cacheKey);
        if (cached.isPresent()) {
            log.debug("缓存命中: {}", internalName);
            return LoadResult.cacheHit(requestId, cached.get());
        }

        // 2. 提交反编译
        DecompileRequest request = DecompileRequest.builder()
                .workspace(workspace)
                .classNode(node)
                .preferredEngine(engine)
                .optionsHash(optionsHash)
                .requestId(requestId)
                .build();

        Future<DecompileResult> future = decompilerExecution.submit(request);
        cancellation.register(requestId, future);

        return LoadResult.submitted(requestId, future, cacheKey);
    }

    public void cancel(String requestId) {
        cancellation.cancel(requestId);
    }

    public void cancelAll() {
        cancellation.cancelAll();
    }

    public CodeCacheStore cacheStore() {
        return cacheStore;
    }

    // --- result types ---

    public sealed interface LoadStatus {}
    public record CacheHit(String requestId, String source) implements LoadStatus {}
    public record Submitted(String requestId, Future<DecompileResult> future,
                            CodeCacheKey cacheKey) implements LoadStatus {}

    public record LoadResult(LoadStatus status) {
        public static LoadResult cacheHit(String requestId, String source) {
            return new LoadResult(new CacheHit(requestId, source));
        }
        public static LoadResult submitted(String requestId, Future<DecompileResult> future,
                                           CodeCacheKey cacheKey) {
            return new LoadResult(new Submitted(requestId, future, cacheKey));
        }

        public boolean isCacheHit() { return status instanceof CacheHit; }
        public CacheHit asCacheHit() { return (CacheHit) status; }
        public Submitted asSubmitted() { return (Submitted) status; }
    }
}
```

- [ ] **编译验证**

```bash
./mvnw compile -DskipTests
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/DecompileCancellation.java
git add src/main/java/com/bingbaihanji/fxdecomplie/service/decompile/OpenClassUseCase.java
git commit -m "feat(decompile): 新增 OpenClassUseCase + DecompileCancellation"
```

---

### Task 2.3: OpenClassUseCase 单元测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/decompile/OpenClassUseCaseTest.java`

- [ ] **编写测试**

```java
package com.bingbaihanji.fxdecomplie.service.decompile;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OpenClassUseCase")
class OpenClassUseCaseTest {

    /** 内存实现，用于测试 */
    static final class InMemoryCacheStore implements CodeCacheStore {
        private final Map<String, String> store = new ConcurrentHashMap<>();

        @Override
        public Optional<String> load(CodeCacheKey key) {
            return Optional.ofNullable(store.get(key.compositeKey()));
        }

        @Override
        public void save(CodeCacheKey key, String source) {
            store.put(key.compositeKey(), source);
        }

        @Override
        public void invalidate(CodeCacheKey key, CodeCacheScope scope) {
            if (scope == CodeCacheScope.ALL) store.clear();
            else store.remove(key.compositeKey());
        }
    }

    private InMemoryCacheStore cache;
    private DecompileCancellation cancellation;
    private OpenClassUseCase useCase;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        cache = new InMemoryCacheStore();
        cancellation = new DecompileCancellation();
        // 使用一个 null-returning execution service 用于测试未命中路径
        DecompilerExecutionService execService = new DecompilerExecutionService() {
            @Override
            public java.util.concurrent.Future<DecompileResult> submit(DecompileRequest r) {
                return CompletableFuture.completedFuture(
                        new DecompileResult(
                                new DecompileOutcome.Success("test source", null),
                                r.requestId(), 1, 0));
            }
            @Override public void cancel(String id) {}
            @Override public int activeCount() { return 0; }
            @Override public int timeoutCount() { return 0; }
        };
        useCase = new OpenClassUseCase(cache, execService, cancellation);
        workspace = new Workspace(null, "test.jar", "test-hash");
    }

    @Nested
    @DisplayName("缓存命中")
    class CacheHit {
        @Test
        @DisplayName("L2/L3 有缓存时直接返回")
        void shouldReturnCachedSource() {
            FileTreeNode node = FileTreeNode.forClass("com.example.Test",
                    "com/example/Test.class", null);
            CodeCacheKey key = new CodeCacheKey("test-hash",
                    "com/example/Test", DecompilerTypeEnum.JADX, "default");
            cache.save(key, "cached source");

            OpenClassUseCase.LoadResult result = useCase.load(
                    workspace, node, DecompilerTypeEnum.JADX, "default");

            assertTrue(result.isCacheHit());
            OpenClassUseCase.CacheHit hit = result.asCacheHit();
            assertEquals("cached source", hit.source());
        }
    }

    @Nested
    @DisplayName("取消")
    class Cancellation {
        @Test
        @DisplayName("取消活跃请求")
        void shouldCancelActiveRequest() {
            FileTreeNode node = FileTreeNode.forClass("com.example.Test",
                    "com/example/Test.class", null);
            OpenClassUseCase.LoadResult result = useCase.load(
                    workspace, node, DecompilerTypeEnum.JADX, "default");

            String requestId;
            if (result.status() instanceof OpenClassUseCase.Submitted s) {
                requestId = s.requestId();
            } else {
                requestId = result.asCacheHit().requestId();
            }
            assertDoesNotThrow(() -> useCase.cancel(requestId));
        }
    }

    @Nested
    @DisplayName("取消全部")
    class CancelAll {
        @Test
        @DisplayName("取消全部活跃请求")
        void shouldCancelAll() {
            assertDoesNotThrow(() -> useCase.cancelAll());
        }
    }
}
```

- [ ] **运行测试**

```bash
./mvnw test -Dtest="OpenClassUseCaseTest"
```

- [ ] **提交**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/decompile/OpenClassUseCaseTest.java
git commit -m "test(decompile): 新增 OpenClassUseCase 测试"
```

---

## 阶段 3：控制器命令协调

### Task 3.1: WindowCommand + WindowCommandBus

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/controller/command/WindowCommand.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/controller/command/WindowCommandBus.java`

- [ ] **创建 WindowCommand**

```java
package com.bingbaihanji.fxdecomplie.controller.command;

/** UI 命令抽象 — 菜单、工具栏、快捷键复用同一命令 */
@FunctionalInterface
public interface WindowCommand<T> {
    T execute();
}
```

- [ ] **创建 WindowCommandBus**

```java
package com.bingbaihanji.fxdecomplie.controller.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 窗口级命令总线 — 注册和派发命令 */
public final class WindowCommandBus {
    private static final Logger log = LoggerFactory.getLogger(WindowCommandBus.class);

    private final Map<String, WindowCommand<?>> commands = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T dispatch(String commandName) {
        WindowCommand<?> cmd = commands.get(commandName);
        if (cmd == null) {
            log.warn("未注册的命令: {}", commandName);
            return null;
        }
        try {
            return (T) cmd.execute();
        } catch (Exception e) {
            log.error("命令执行失败: {}", commandName, e);
            throw e;
        }
    }

    public void register(String name, WindowCommand<?> command) {
        commands.put(name, command);
    }
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/controller/command/WindowCommand.java
git add src/main/java/com/bingbaihanji/fxdecomplie/controller/command/WindowCommandBus.java
git commit -m "feat(controller): 新增 WindowCommand + WindowCommandBus"
```

---

### Task 3.2: WindowContext

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/controller/WindowContext.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/controller/command/OpenFileCommand.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/controller/command/SwitchEngineCommand.java`

- [ ] **创建 WindowContext**

```java
package com.bingbaihanji.fxdecomplie.controller;

import com.bingbaihanji.fxdecomplie.controller.command.WindowCommandBus;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;

/** 窗口级上下文 — 状态和命令的单一来源 */
public final class WindowContext {
    private final Stage stage;
    private final WindowCommandBus commandBus;

    // 可观察状态
    private final ObjectProperty<Workspace> workspaceSelection =
            new SimpleObjectProperty<>();
    private final ObjectProperty<DecompilerTypeEnum> engineSelection =
            new SimpleObjectProperty<>(DecompilerTypeEnum.JADX);

    public WindowContext(Stage stage) {
        this.stage = stage;
        this.commandBus = new WindowCommandBus();
    }

    public Stage stage() { return stage; }
    public WindowCommandBus commandBus() { return commandBus; }

    public ObjectProperty<Workspace> workspaceProperty() { return workspaceSelection; }
    public Workspace currentWorkspace() { return workspaceSelection.get(); }
    public void setCurrentWorkspace(Workspace ws) { workspaceSelection.set(ws); }

    public ObjectProperty<DecompilerTypeEnum> engineProperty() { return engineSelection; }
    public DecompilerTypeEnum currentEngine() { return engineSelection.get(); }
    public void setCurrentEngine(DecompilerTypeEnum engine) { engineSelection.set(engine); }
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/controller/WindowContext.java
git commit -m "feat(controller): 新增 WindowContext 窗口级上下文"
```

---

## 阶段 4：配置系统生产化

### Task 4.1: ConfigMigrator + ConfigValidator

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigMigrator.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigValidator.java`

- [ ] **创建 ConfigMigrator**

```java
package com.bingbaihanji.fxdecomplie.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** schemaVersion 迁移集中管理 */
public final class ConfigMigrator {
    private static final Logger log = LoggerFactory.getLogger(ConfigMigrator.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ConfigMigrator() { throw new AssertionError("utility"); }

    public static JsonNode migrate(JsonNode root, int fromVersion, int toVersion) {
        JsonNode current = root;
        int v = fromVersion;
        while (v < toVersion) {
            current = applyMigration(current, v, v + 1);
            v++;
        }
        return current;
    }

    private static JsonNode applyMigration(JsonNode root, int from, int to) {
        // schema 1→2: 预留,当前最新 schema
        log.info("配置迁移: {} → {}", from, to);
        return root;
    }

    /** 迁移前备份原文件 */
    public static Path backup(Path configFile) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path backup = configFile.resolveSibling("config-" + ts + ".json.bak");
        Files.copy(configFile, backup);
        log.info("配置已备份到: {}", backup);
        return backup;
    }
}
```

- [ ] **创建 ConfigValidator**

```java
package com.bingbaihanji.fxdecomplie.config;

import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 配置合法性校验 */
public final class ConfigValidator {
    private static final int MIN_WINDOW_WIDTH = 400;
    private static final int MIN_WINDOW_HEIGHT = 300;
    private static final int MAX_SEARCH_LIMIT = 100_000;
    private static final int MIN_FONT_SIZE = 8;
    private static final int MAX_FONT_SIZE = 72;

    private ConfigValidator() { throw new AssertionError("utility"); }

    public static List<String> validate(AppConfig config) {
        List<String> errors = new ArrayList<>();

        // 窗口尺寸
        if (config.getWindowWidth() < MIN_WINDOW_WIDTH) {
            errors.add("窗口宽度不能小于 " + MIN_WINDOW_WIDTH);
        }
        if (config.getWindowHeight() < MIN_WINDOW_HEIGHT) {
            errors.add("窗口高度不能小于 " + MIN_WINDOW_HEIGHT);
        }

        // 引擎合法性
        String engine = config.getDefaultDecompiler();
        if (engine != null && !engine.isBlank()) {
            try {
                DecompilerTypeEnum.valueOf(engine.toUpperCase());
            } catch (IllegalArgumentException e) {
                errors.add("无效的默认引擎: " + engine);
            }
        }

        // 字体大小
        if (config.getEditorFontSize() < MIN_FONT_SIZE
                || config.getEditorFontSize() > MAX_FONT_SIZE) {
            errors.add("字体大小必须在 " + MIN_FONT_SIZE + "-" + MAX_FONT_SIZE + " 之间");
        }

        return errors;
    }
}
```

- [ ] **编写测试**

```java
// src/test/java/.../config/ConfigMigratorTest.java
@Test void shouldCreateBackup() { ... }
@Test void shouldMigrateSchemaVersion() { ... }

// src/test/java/.../config/ConfigValidatorTest.java
@Test void shouldRejectTooSmallWindow() { ... }
@Test void shouldRejectInvalidEngine() { ... }
@Test void shouldAcceptValidConfig() { ... }
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigMigrator.java
git add src/main/java/com/bingbaihanji/fxdecomplie/config/ConfigValidator.java
git add src/test/java/com/bingbaihanji/fxdecomplie/config/ConfigMigratorTest.java
git add src/test/java/com/bingbaihanji/fxdecomplie/config/ConfigValidatorTest.java
git commit -m "feat(config): 新增 ConfigMigrator + ConfigValidator + 测试"
```

---

## 阶段 5：反编译执行模型统一

### Task 5.1: DecompileOutcome + FailureKind

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompileOutcome.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/FailureKind.java`

- [ ] **创建 FailureKind**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

public enum FailureKind {
    CANCELLED,
    TIMEOUT,
    BUSY,
    CLASS_NOT_FOUND,
    ENGINE_ERROR,
    UNSUPPORTED_CLASS_VERSION,
    IO_ERROR
}
```

- [ ] **创建 DecompileOutcome**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.CodeMetadata;

public sealed interface DecompileOutcome {
    record Success(String source, CodeMetadata metadata) implements DecompileOutcome {
        public Success(String source) { this(source, null); }
    }

    record Failure(DecompilerTypeEnum engine, FailureKind kind,
                   String message, Throwable cause) implements DecompileOutcome {
        public Failure(DecompilerTypeEnum engine, FailureKind kind, String message) {
            this(engine, kind, message, null);
        }

        /** 生成便于日志的结构化描述 */
        public String diagnostic() {
            return String.format("engine=%s kind=%s message=%s", engine, kind, message);
        }
    }

    default boolean isSuccess() { return this instanceof Success; }
    default boolean isFailure() { return this instanceof Failure; }
}
```

- [ ] **提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/FailureKind.java
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompileOutcome.java
git commit -m "feat(decompiler): 结构化反编译结果 DecompileOutcome + FailureKind"
```

---

## 阶段 6：流水线治理

### Task 6.1: WorkspaceLoadService + WorkspaceLoadResult

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/workspace/WorkspaceLoadResult.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/workspace/WorkspaceLoadService.java`

- [ ] **创建 WorkspaceLoadResult**

```java
package com.bingbaihanji.fxdecomplie.service.workspace;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import java.util.Collections;
import java.util.List;

public record WorkspaceLoadResult(
        Workspace workspace,
        Status status,
        List<String> warnings,
        List<String> errors,
        long elapsedMs) {

    public enum Status { OK, PARTIAL, FAILED }

    public static WorkspaceLoadResult ok(Workspace ws, long elapsedMs) {
        return new WorkspaceLoadResult(ws, Status.OK, Collections.emptyList(),
                Collections.emptyList(), elapsedMs);
    }

    public static WorkspaceLoadResult failed(List<String> errors) {
        return new WorkspaceLoadResult(null, Status.FAILED, Collections.emptyList(),
                errors, 0);
    }
}
```

- [ ] **创建 WorkspaceLoadService 接口**

```java
package com.bingbaihanji.fxdecomplie.service.workspace;

import java.nio.file.Path;

public interface WorkspaceLoadService {
    WorkspaceLoadResult load(Path fileOrDir);
    void cancel();

    /** 可配置的保护参数 */
    record Limits(int maxDepth, int maxFiles) {
        public static final Limits DEFAULT = new Limits(50, 100_000);
    }
}
```

- [ ] **提交**

---

### Task 6.2: ExportPlanner + ExportWriter 拆分

重构 `ExportService` → `ExportPlanner` (生成文件清单) + `ExportWriter` (写入 + 路径安全) + `ExportConflictResolver` (冲突策略)

---

## 阶段 7：大类拆分

### Task 7.1: RenameService 拆分

`RenameService` (2941行) → `RenameRepository` + `RenameIndex` + `RenameOperations` + `RenameSerialization`

### Task 7.2: WorkspaceTabManager 拆分

`WorkspaceTabManager` (1321行) → welcome、drag-drop、tab lifecycle、split view

### Task 7.3: BytecodeTextBuilder 拆分

拆为 class summary、constant pool、methods、attributes 各子构建器

---

## 阶段 8：发布生产化

### Task 8.1: Release Profile + 许可证清单

- 新增 `<profile id="release">` — shade + checksum 生成
- 生成 `THIRD-PARTY-NOTICES.txt`
- 生成 `SHA256SUMS`

### Task 8.2: 启动脚本

固定 JVM 参数: `--enable-preview`, `--add-opens java.base/java.lang=ALL-UNNAMED`

---

## 执行顺序

```
第1轮: Task 0.1 → 0.2 → 0.3 → 0.4      (阶段 0 全部)
第2轮: Task 1.1 → 1.2 → 1.3 → 1.4       (阶段 1 部分)
      + Task 2.1 → 2.2 → 2.3             (阶段 2 全部)
第3轮: Task 1.5 → 1.6 → 1.7              (阶段 1 完成)
      + Task 3.1 → 3.2                    (阶段 3 全部)
      + Task 4.1                          (阶段 4 开始)
第4轮: Task 5.1                          (阶段 5 全部)
第5轮: Task 6.1 → 6.2                    (阶段 6 全部)
      + Task 8.1 → 8.2                    (阶段 8 全部)
第6轮: Task 7.1 → 7.2 → 7.3             (阶段 7 全部)
```

每轮结束门禁: `mvn -Pquality verify` 通过 + 全部测试通过
