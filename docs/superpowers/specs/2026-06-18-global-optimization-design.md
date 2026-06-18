# FxDecompiler 全局优化设计文档

**日期**: 2026-06-18
**状态**: 已确认
**依据**: `rules/Java开发规范.md`

---

## 目标

对 FxDecompiler 项目（85 源文件）进行全面优化：
- 修复已知 Bug 和逻辑缺陷
- 补全类/字段/方法 Javadoc 注释及复杂调用行内注释
- 使用 Logback + SLF4J 替换 System.getLogger
- 确保全部代码严格符合 `Java开发规范.md` 红线条款

## 方案：按规范条目分批执行

---

### 第 1 批：死代码删除

删除零引用的文件、方法和死 UI。

**文件级删除** (6 个):
| 文件 | 原因 |
|------|------|
| `path/PackagePathNode.java` | 零 import/实例化 |
| `path/MemberPathNode.java` | 零 import/实例化 |
| `events/AppEvent.java` | 零实现者 |
| `events/EventBus.java` | 零 subscribe/publish 调用 |
| `di/ServiceRegistry.java` | 注册但零 get/create 调用 |

**方法级删除**:
| 文件 | 删除内容 |
|------|---------|
| `MainWindow.java` | `collectResourceBytes()` 方法（零调用） |
| `PathNode.java` | `getPathOfType()`、`isDescendantOf()` 方法（零调用） |
| `InheritancePane.java` | `load(String)` 单参重载（只查 BytecodeCache 不查 WorkspaceIndex） |
| `MainMenuBar.java` | `createThemeMenu()` 方法（唯一项 disabled） |

**级联清理**:
- `FxDecompilerApp.java`: 删除 `ServiceRegistry registry = new ServiceRegistry()` 和 `registry.registerSingleton(...)` 
- `MainWindow.java`: 删除 `serviceRegistry` 字段和构造函数参数

---

### 第 2 批：Logback + SLF4J 日志改造

**pom.xml 新增**:
```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.17</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.18</version>
</dependency>
```

**新增 `src/main/resources/logback.xml`**:

```xml

<configuration>
    <!-- 控制台彩色输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} [%thread] %highlight(%-5level) %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- 项目包 -->
    <logger name="com.bingbaihanji.fxdecomplie" level="DEBUG"/>

    <!-- 第三方库 -->
    <logger name="org.jd" level="WARN"/>
    <logger name="org.benf" level="WARN"/>
    <logger name="org.jetbrains.java.decompiler" level="WARN"/>
    <logger name="com.strobel" level="WARN"/>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**日志调用替换规则**:

| 改前 | 改后 |
|------|------|
| `System.getLogger(Xxx.class.getName())` | `LoggerFactory.getLogger(Xxx.class)` |
| `logger.log(Level.WARNING, msg, e)` | `logger.warn(msg, e)` |
| `logger.log(Level.ERROR, msg, e)` | `logger.error(msg, e)` |
| `logger.log(Level.INFO, msg)` | `logger.info(msg)` |
| `logger.log(Level.DEBUG, msg)` | `logger.debug(msg)` |
| `System.err.println(...)` | `logger.error(...)` |
| `e.printStackTrace()` | `logger.error("xxx", e)` |

**涉及文件**（约 25 个）:
`FxDecompilerApp`, `MainWindow`, `ClassTabOpener`, `BackgroundTasks`, `ProcessService`, `ExportService`, `ClassDiscoverer`, `FileTreeBuilder`, `BytecodeCache`, `DecompilerFactory`, `CfrDecompiler`, `VineflowerDecompiler`, `ProcyonDecompiler`, `JdDecompiler`, `DecompilerContext`, `BytecodeViewTab`, `ClassInfoView`, `InheritanceService`, `OutlineParser`, `RegexHighlighter`, `VsCodeThemeLoader`, `AppTheme`, `I18nUtil`, `DiskCodeCache`, `SearchService`, `SearchDialog`

`LOG` 字段声明统一为:
```java
private static final Logger logger = LoggerFactory.getLogger(Xxx.class);
```

---

### 第 3 批：工具类规范合规

依据规范 3.6 检查全部工具类，确保满足：
- `final class`
- `private` 构造器 `throw new AssertionError("utility class")`
- 只含 `static` 成员

**检查清单**（约 18 个工具类）:

| 文件 | 当前状态 | 修复 |
|------|---------|------|
| `BackgroundTasks` | ✅ 已合规 | — |
| `ClassDiscoverer` | ✅ 已合规 | — |
| `FileTreeBuilder` | ✅ 已合规 | — |
| `ExportService` | ✅ 已合规 | — |
| `BytecodeCache` | ✅ 已合规 | — |
| `DecompilerFactory` | ✅ 已合规 | — |
| `BytecodeViewTab` | ✅ 已合规 | — |
| `ClassInfoView` | ✅ 已合规 | — |
| `TextFileDecorator` | ✅ 已合规 | — |
| `OutlineParser` | ✅ 已合规 | — |
| `InheritanceService` | ✅ 已合规 | — |
| `AppTheme` | ✅ 已合规 | — |
| `CodeLinkHandler` | ✅ 已合规 | — |
| `DiskCodeCache` | ✅ 已合规 | — |
| `SettingsDialog` | ✅ 已合规 | — |
| `ProcessService` | ✅ 已合规 | — |
| `LineNumberGutter` | ✅ 已合规 | — |
| `RegexHighlighter` | 非工具类（SyntaxDecorator 实现），跳过 |

**结论**: 当前工具类均已合规，本轮无改动或仅微调。

---

### 第 4 批：注释与命名

**类级 Javadoc**（全部 85 文件）:
```java
/**
 * 一句话描述类职责。
 *
 * @author bingbaihanji
 * @date 2026-06-18
 */
```

**字段 Javadoc**（全部字段）:
```java
/** 字段简要说明 */
private final String xxx;
```

**方法 Javadoc**（全部 public/protected 方法）:
```java
/**
 * 方法功能说明。
 * @param xxx 参数说明
 * @return 返回值说明
 */
public String doSomething(String xxx) { ... }
```

**enum 值注释**（全部 enum）:
```java
public enum XxxEnum {
    /** 选项说明 */
    VALUE_A,
    /** 选项说明 */
    VALUE_B
}
```

**复杂调用行内注释**:
- `ClassTabOpener.decompileWithCache()` — 三级缓存查询管线（L2→L3→反编译→L2存→L3异步存）
- `ClassTabOpener.openClassTab()` — 反编译 → 元数据提取 → UI 创建 → 导航回调链
- `NavigationService.openPath()` — PathNode 值提取 → Tab 去重 → 反编译调度
- `WorkspaceIndex.build()` — 树遍历 → class/资源/成员提取 → ASM 扫描
- `SearchService.searchAll()` — 多 Provider 并行 → 合并排序 → 截断
- `ExportService.exportAll()` — 树遍历 → 反编译 → 路径安全校验 → 写入

---

### 第 5 批：并发规范

| 文件 | 违规 | 修复 |
|------|------|------|
| `BackgroundTasks.java` | `Executors.newCachedThreadPool()` 违反规范 3.4 强制条款 | 改为 `new ThreadPoolExecutor(0, 8, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), daemonThreadFactory)` |

修复后代码：
```java
private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
        0, 8,
        60L, TimeUnit.SECONDS,
        new SynchronousQueue<>(),
        r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });
```

---

### 第 6 批：代码格式

**通配符导入检查**: 全局扫描 `import xxx.*`，替换为显式单类导入。

**switch default 检查**: 确保所有 `switch` 有 `default` 分支或 `default -> {}`。

**空 catch 块**: 所有 `catch (Exception ignored) {}` → 至少 `logger.debug("xxx", e)` 或 `logger.warn("xxx", e)`。

**涉及文件**: `DecompilerFactory`（switch 已有全覆盖但建议加 default）、`SearchDialog`、`MainMenuBar`

---

### 第 7 批：逻辑 Bug 修复

| # | 位置 | 问题 | 修复 |
|---|------|------|------|
| 1 | `SearchDialog.java` | ComboBox 切换触发全量重新搜索 | 切换到已获取结果的内存过滤 |
| 2 | `ClassTabOpener.java` | `findOpenClassTab` 命名不反映删除副作用 | 重命名 `findOrRemoveOpenClassTab` |
| 3 | `ClassTabOpener.java` | `computeWorkspaceKey` 在 L3 加载和保存时各算一遍 | 算一次存到局部变量复用 |
| 4 | `ExportService.java` | `exportAll()` 内部重建 WorkspaceIndex | 改为接收 `WorkspaceIndex` 参数或用 `Workspace.getIndex()` |
| 5 | `ResourceSearchProvider.java` | 构造函数未 null-guard | 加 `Objects.requireNonNull` |
| 6 | `BytecodeSearchProvider.java` | 构造函数未 null-guard | 加 `Objects.requireNonNull` |
| 7 | `CodeEditorTab.java` | `metadata` 字段存 null | 存 `new CodeMetadata(Map.of())` 代替 null |

---

### 第 8 批：最终验证

```bash
cd D:/bingbaihanji/fxdecomplie/fxdecomplie
mvn clean compile -DskipTests
mvn clean package -DskipTests
```

验收标准：
- 85 源文件编译零错误
- Fat JAR 打包成功
- Logback 启动日志正常输出（彩色）
- 项目功能无回归

---

## 文件统计

| 批次 | 删除 | 新增 | 修改 |
|------|------|------|------|
| 1 | 7 | 0 | 3 |
| 2 | 0 | 2 (pom.xml变更 + logback.xml) | ~25 |
| 3 | 0 | 0 | ~5 (仅微调) |
| 4 | 0 | 0 | ~85 |
| 5 | 0 | 0 | 1 |
| 6 | 0 | 0 | ~5 |
| 7 | 0 | 0 | ~6 |
| 8 | 0 | 0 | 0 |
