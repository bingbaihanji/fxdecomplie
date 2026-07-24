# ClassGraph 迁移代码重构优化方案（完整版）

日期: 2026-07-23
范围: `src/main/java/com/bingbaihanji/classgraph`（130 个 Java 文件）

## 变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| v1.0 | 2026-07-23 | 初版：总体目标、架构建议、模块重构方案、分阶段计划 |
| v2.0 | 2026-07-23 | 完整版：补充代码审查发现的 20+ 具体缺陷、每模块接口定义与设计模式论证、依赖分析重排阶段、改动清单、验收标准、回滚策略 |

---

## 目录

1. [背景与总体目标](#1-背景与总体目标)
2. [代码审查发现](#2-代码审查发现)
3. [总体架构建议](#3-总体架构建议)
4. [模块级重构方案（含接口定义）](#4-模块级重构方案)
5. [type 系统修复](#5-type-系统修复)
6. [命名和代码规范](#6-命名和代码规范)
7. [测试策略](#7-测试策略)
8. [分阶段实施计划（依赖分析重排）](#8-分阶段实施计划)
9. [风险和控制措施](#9-风险和控制措施)
10. [推荐最终结构](#10-推荐最终结构)
11. [优先级结论](#11-优先级结论)

---

## 1. 背景与总体目标

### 1.1 背景

- `classgraph` 包迁移自 ClassGraph 4.8.184 源码（MIT License）
- 承担类路径扫描、模块扫描、jar/目录资源读取、class 文件解析、元数据构建、类关系查询等能力
- 代码功能完整，但保留上游库的复杂兼容实现，维护成本高、职责边界不清

### 1.2 总体目标

1. **降低维护成本** — 大文件拆成边界清晰的小模块
2. **隔离第三方迁移代码** — 业务层通过项目自己的服务接口访问扫描能力
3. **保持扫描行为稳定** — 重构以行为保持为前提，不一次性重写核心算法
4. **提升代码规范性** — 统一命名、收敛 public 可变字段、减少静态全局状态
5. **形成可替换架构** — 后续可替换为 Maven 原版 ClassGraph 或自研扫描器

---

## 2. 代码审查发现

基于对 130 个 Java 文件的深度分析，以下问题需要纳入重构方案。

### 2.1 Bug / 运行时缺陷

| # | 文件 | 行号 | 问题 | 严重度 |
|---|------|------|------|--------|
| B1 | `ClassGraphClassLoaderHandler.java` | 全文 | `canHandle()` 检查 `com.bingbaihanji.classgraph.core.ScanClassLoader`，但 ScanClassLoader 实际在 `com.bingbaihanji.classgraph.scan` 包下，**此 handler 永远不会匹配** | 🔴 高 |
| B2 | `FallbackClassLoaderHandler.java` | 100-103 | `reflectionUtils.getFieldVal(false, classLoader, "paths")` 重复调用两次，参数完全相同（复制粘贴错误） | 🟡 中 |
| B3 | `EquinoxClassLoaderHandler.java` | 全文 | `alreadyReadSystemBundles` 为 static boolean，一次扫描失败会导致后续所有扫描跳过系统 bundle | 🟡 中 |
| B4 | `WeblogicClassLoaderHandler.java` | 全文 | `JspClassLoader` 和 `TagFileClassLoader` 的反射逻辑明确标注"未测试"，字段/方法名可能不匹配 | 🟡 中 |

### 2.2 设计缺陷

| # | 位置 | 问题 | 严重度 |
|---|------|------|--------|
| D1 | `ScanConfig.java` | 所有字段 public，无 getter/setter，无冻结机制 | 🔴 高 |
| D2 | `ClassGraph.java` (1844 行) | God Class：Builder + Executor + Query + 内部接口 四合一 | 🔴 高 |
| D3 | `Scanner.java` (1161 行) | 构造函数做模块发现（不应在构造中做 IO），call() 异常处理嵌套 50+ 行 | 🔴 高 |
| D4 | `ClassParser.java` (1978 行) | 单体解析器：常量池 + 字段 + 方法 + 注解 + 链接 + 外部扫描调度 | 🔴 高 |
| D5 | `ClasspathFinder.java` | 构造函数 ~200 行：模块发现、类加载器发现、override 处理、JRE jar 扫描全在一个方法 | 🟡 中 |
| D6 | `Classpath.java` | 抽象基类暴露 public 可变字段：`acceptedResources`、`acceptedClassfileResources`、`skipClasspath` 等 | 🔴 高 |
| D7 | `HandlerRegistry.java` | handler 列表静态硬编码为 unmodifiable list，无运行时注册/替换能力 | 🟡 中 |
| D8 | `ClassInfo.java` (~1427 行) | 虽已拆分 ClassHierarchy + ClassMembers，但仍保留 ~120 行纯委托方法，同时承担构造（工厂方法）和查询双重职责 | 🟡 中 |
| D9 | `ClassQuery.java` | 22 个公开方法各有相同的 2-3 行 guard clause（`closed.get()` + `enableClassInfo`），~150 行纯重复 | 🟢 低 |

### 2.3 架构异味

| # | 位置 | 问题 | 严重度 |
|---|------|------|--------|
| A1 | `ClassGraph` ↔ `ScanConfig` | 双向依赖：ScanConfig 导入 ClassGraph.ClasspathFilter；ClassGraph 直接修改 ScanConfig public 字段 | 🟡 中 |
| A2 | `GraphvizDotfileGenerator.java` | 放在 `resource/` 包，但职责是类关系图可视化，应属于 `scan/` 或独立 `viz/` 包 | 🟢 低 |
| A3 | `TypedValue.java` (658 行) | Tagged union of ~20 类型，注释说"移除 JSON 序列化后删除此类" — 是遗留 workaround | 🟢 低 |
| A4 | `TypeArg/TypeParam/MethodType` | 继承 MetadataNode 但 `getClassName()`/`getClassInfo()` 抛 IllegalArgumentException — 接口违约 | 🟡 中 |
| A5 | `PrivilegedActionInvocationHandler` | 在 `StandardDriver` 和 `ReflectionUtils` 中各有一份几乎相同的实现（~10 行重复） | 🟢 低 |
| A6 | `FileSlice` vs `PathSlice` | 结构几乎相同（File+RAF+mmap vs Path+FileChannel+无mmap），差异仅 mmap 支持 | 🟢 低 |
| A7 | `ScanClassLoader.findClass()` | 内联 Windows 特定的大小写不敏感文件名检测 hack（~20 行），应在工具类中 | 🟢 低 |
| A8 | `ClasspathOrder.reflectionUtils` | public 非 final 字段，被所有 handler 直接访问，但应通过构造注入后不可变 | 🟢 低 |

### 2.4 命名不规范（疑似机器翻译 artifact）

全局性问题：大量局部变量和参数使用 PascalCase 首字母大写，违反 Java 命名规范：

- `ScanConfig` → 应为 `scanConfig`
- `Classpath` → 应为 `classpath`
- `ClassParser` → 应为 `classParser`
- `JarReader` → 应为 `jarReader`
- `MethodType` → 应为 `methodType`
- `ClassType` → 应为 `classType`
- `TypeVar` → 应为 `typeVar`
- `TypeArg` → 应为 `typeArg`

影响范围：~100 个文件，属于低风险高收益的批量规范化工作。

---

## 3. 总体架构建议

### 3.1 项目级适配层（Facade + Adapter）

**目标**：UI、service、controller 不直接依赖 `com.bingbaihanji.classgraph` 内部 API。

```
src/main/java/com/bingbaihanji/fxdecomplie/service/classscan/
├── ClassScanService          # 接口：项目唯一扫描入口
├── ClassGraphClassScanService # 实现：内部委托给 ClassGraph
├── ClassScanRequest           # 扫描请求 DTO
├── ClassScanResult            # 扫描结果 DTO（不暴露 ScanResult）
├── ClassScanOptions           # 扫描选项 DTO
├── ClassMetadata              # 类元数据 DTO（不暴露 ClassInfo）
├── MethodMetadata             # 方法元数据 DTO
├── FieldMetadata              # 字段元数据 DTO
└── AnnotationMetadata         # 注解元数据 DTO
```

**接口定义**：

```java
public interface ClassScanService {
    ClassScanResult scan(ClassScanRequest request);
    CompletableFuture<ClassScanResult> scanAsync(ClassScanRequest request);
    void cancel(String scanId);
}

public record ClassScanRequest(
    List<Path> scanPaths,           // 要扫描的 jar/目录
    List<Path> classpath,            // 额外 classpath
    ClassScanOptions options,        // 扫描选项
    String scanId                    // 幂等标识
) {}

public record ClassScanResult(
    String scanId,
    List<ClassMetadata> classes,
    List<ResourceMetadata> resources,
    ScanStatistics statistics
) {}
```

**设计模式**：Facade（ClassScanService 隐藏扫描复杂性）+ Adapter（ClassGraphClassScanService 将 ClassGraph API 适配为项目接口）+ DTO（跨层传输不可变数据）

### 3.2 保留 ClassGraph 作为兼容门面

```
scan/
├── ClassGraph                  # 兼容门面，保留链式 API，内部委托
├── ClassGraphBuilder           # 配置构建（Builder 模式）
├── ScanOptions                 # 不可变扫描配置（record / final class）
├── ScanExecutor                # 同步扫描执行
├── AsyncScanExecutor           # 异步扫描执行
└── ClasspathQueryService       # classpath/module 查询
```

**设计模式**：Builder（构建不可变 ScanOptions）+ Strategy（ScanExecutor 可选择不同扫描策略）+ Facade（ClassGraph 隐藏内部拆分）

---

## 4. 模块级重构方案

### 4.1 ScanConfig → ScanOptions（不可变配置）

**当前问题**：所有字段 public，配置项 + 运行时状态 + 过滤器混杂，扫描中途可被外部修改。

**目标结构**：

```
scan/config/
├── ScanOptions                 # 不可变扫描配置（扫描期间冻结）
├── ScanOptionsBuilder          # Builder，从 ClassGraphBuilder 调用
├── ScanFilters                 # package/path/class/module/jar 接受拒绝规则
├── ScanRuntimeOptions          # 线程数、临时文件清理、内存映射、buffer 大小
├── ScanClasspathOptions        # 是否扫描 jar、目录、模块、嵌套 jar
└── ScanMetadataOptions         # 是否启用 class/field/method/annotation 信息
```

**核心接口定义**：

```java
// 不可变配置对象 — 扫描开始后不可修改
public final class ScanOptions {
    private final ScanFilters filters;
    private final ScanRuntimeOptions runtime;
    private final ScanClasspathOptions classpath;
    private final ScanMetadataOptions metadata;
    private final List<Object> overrideClasspath;     // 不可变副本
    private final List<Object> overrideClassLoaders;  // 不可变副本

    // 包私有构造，只通过 Builder 创建
    ScanOptions(ScanOptionsBuilder builder) { ... }

    // 只读 getter
    public ScanFilters filters() { return filters; }
    public ScanRuntimeOptions runtime() { return runtime; }
    ...
}

// Builder — 包私有，由 ClassGraphBuilder 创建
class ScanOptionsBuilder {
    ScanFiltersBuilder filtersBuilder();
    ScanRuntimeOptionsBuilder runtimeBuilder();
    ScanOptions build();  // 调用后返回不可变 ScanOptions
}
```

**设计模式论证**：
- **Builder**：64+ 配置项，Builder 分步构建避免构造函数参数爆炸
- **Immutable Object**：扫描期间不可变，消除并发修改风险和时序 bug
- **为什么不是 record**：ScanFilters 包含 Pattern 对象（非简单值类型），record 的 equals/hashCode 对 Pattern 不适用

**迁移策略**（四步走，每步可独立提交）：
1. 给 ScanConfig 字段加 getter/setter 方法，不改变调用方
2. 将调用方从字段访问改为方法访问
3. 引入 ScanOptions + Builder，ScanConfig 成为兼容适配器（delegate 到 ScanOptions）
4. 删除 ScanConfig 可变字段，全部改为委托

### 4.2 ClassGraph 拆分

**当前问题**：链式 API + 扫描执行 + classpath 查询 + 内部接口（~1844 行）

**目标拆分**：

```
scan/
├── ClassGraph                  # 兼容门面，保留原有 public API
├── ClassGraphBuilder           # 链式配置构建（~800 行）
├── ScanExecutor                # 同步扫描（~150 行）
├── AsyncScanExecutor           # 异步扫描（~100 行）
└── ClasspathQueryService       # classpath/module/URI 查询（~200 行）
```

**类职责与接口**：

```java
// 兼容门面 — 保持原有用户 API 不变
public class ClassGraph {
    private final ClassGraphBuilder builder;
    private final ScanExecutor scanExecutor;
    private final ClasspathQueryService queryService;

    // 所有 withXxx() 方法委托给 builder
    public ClassGraph withPackages(String... packages) {
        builder.acceptPackages(packages);
        return this;
    }

    // scan() 委托给 scanExecutor
    public ScanResult scan() {
        return scanExecutor.execute(builder.build());
    }

    // getClasspathFiles() 等查询委托给 queryService
    public List<File> getClasspathFiles() {
        return queryService.getClasspathFiles(builder.build());
    }
}

// Builder — 只负责配置收集
class ClassGraphBuilder {
    private final ScanOptionsBuilder optionsBuilder;

    ClassGraphBuilder acceptPackages(String... packages) { ... }
    ClassGraphBuilder enableClassInfo() { ... }
    ScanOptions build() { return optionsBuilder.build(); }

    // deprecated API 集中放在此区块，标记 @Deprecated
    // === Deprecated compatibility ===
    @Deprecated ClassGraphBuilder whitelistPackages(String... p) { return acceptPackages(p); }
    ...
}

// 扫描执行 — 不修改配置
class ScanExecutor {
    private final InterruptionChecker interruptionChecker;

    ScanResult execute(ScanOptions options) {
        // 1. 冻结 options（防止后续修改）
        // 2. 创建 Scanner
        // 3. 执行扫描
        // 4. 返回 ScanResult
    }
}
```

**设计模式论证**：
- **Builder**：64+ 配置项的链式构建，Builder 是标准方案
- **Command**：ScanExecutor 封装扫描执行为独立命令，支持重试/超时/取消
- **Facade**：ClassGraph 门面保持 API 兼容，内部委托给拆分后的组件
- **为什么不是 Factory**：ClassGraph 不仅是创建 Scanner 的工厂，它还提供 classpath 查询能力，Facade 更合适

**Deprecated API 处理策略**：
- 所有 `enableXxx()`/`whitelistXxx()`/`blacklistXxx()` 等 deprecated 方法集中放在 ClassGraphBuilder 的 `// === Deprecated compatibility ===` 区块
- 标记 `@Deprecated(since = "2.0", forRemoval = true)`
- 内部全部委托给对应的 `withXxx()`/`acceptXxx()`/`rejectXxx()` 方法
- 目标：~200 行 deprecated boilerplate 不干扰主逻辑

### 4.3 Scanner → Pipeline 模式

**当前问题**：扫描全流程集中在 Scanner.call()（~89 行）+ 构造函数（~100 行）+ 多个 ~100 行私有方法

**目标 Pipeline 结构**：

```
scan/pipeline/
├── ScanPipeline                # 编排器：串联所有 step
├── ScanContext                 # 管道上下文（携带共享状态）
├── ClasspathDiscoveryStep      # Step 1: 发现所有 classpath 元素
├── ClasspathOpenStep           # Step 2: 打开并验证 classpath 元素
├── NestedClasspathPreprocessor # Step 3: 处理嵌套 classpath（jar-in-jar、Class-Path manifest）
├── ClasspathMaskingStep        # Step 4: 应用屏蔽规则，确定最终 classpath 顺序
├── ResourceScanStep            # Step 5: 并行扫描资源（class 文件 + 非 class 文件）
├── ClassfileParseStep          # Step 6: 并行解析 class 文件 → ParsedClassFile
├── MetadataLinkStep            # Step 7: 链接 ParsedClassFile → ClassInfo 图
└── ScanResultAssembler         # Step 8: 组装 ScanResult
```

**接口定义**：

```java
// Pipeline 编排器
public class ScanPipeline implements Callable<ScanResult> {
    private final List<ScanStep> steps;

    @Override
    public ScanResult call() throws Exception {
        ScanContext ctx = new ScanContext(options);
        try {
            for (ScanStep step : steps) {
                ctx = step.execute(ctx);
                if (ctx.isCancelled()) throw new InterruptedException();
            }
            return ctx.getScanResult();
        } catch (Exception e) {
            ctx.cleanup();   // 统一资源清理
            throw e;
        }
    }
}

// Step 接口 — 每个 step 是独立可测试单元
@FunctionalInterface
interface ScanStep {
    ScanContext execute(ScanContext ctx) throws Exception;
}

// 管道上下文 — 携带共享状态，每个 step 可读取/写入
class ScanContext {
    private final ScanOptions options;           // 不可变
    private final ExecutorService executor;       // 线程池
    private final JarReader jarReader;            // jar 读取器
    private final InterruptionChecker interruptionChecker;
    private final ScanLogger logger;

    // 各 step 产生的中间结果
    private List<ClasspathElement> classpathElements;
    private List<Resource> acceptedResources;
    private List<Resource> classfileResources;
    private Map<String, ParsedClassFile> parsedClasses;
    private Map<String, ClassInfo> classInfoMap;

    // 资源注册（用于 cleanup）
    void registerResource(AutoCloseable resource) { ... }
    void cleanup() { ... }
}
```

**设计模式论证**：
- **Pipeline / Chain of Responsibility**：每个 step 是独立处理单元，顺序可配置，step 可独立测试
- **Context Object**：ScanContext 避免 step 之间通过大量参数传递共享状态
- **Template Method**：不采用，因为步骤间无需继承关系；Pipeline 编排比 Template Method 更灵活

**为什么不是 Visitor**：Pipeline 的 step 是线性顺序的，不需要双重分发。Visitor 增加不必要的复杂度。

**从 Scanner 提取 Step 的拆分策略**：

| 当前 Scanner 方法 | 行数 | 提取目标 |
|---|---|---|
| 构造函数中的模块发现部分 | ~50 | `ClasspathDiscoveryStep` |
| `normalizeClasspathEntry()` | ~108 | `ClasspathOpenStep`（路径规范化子组件） |
| `openClasspathsThenScan()` | ~96 | `ClasspathOpenStep` + `NestedClasspathPreprocessor` |
| `performScan()` | ~100 | `ClassfileParseStep` |
| `call()` 中的结果处理 | ~89 | `ScanResultAssembler` |
| `ClassfileScannerWorkUnitProcessor`（内部类） | ~107 | 独立顶级类，注入到 `ResourceScanStep` |

### 4.4 ClassParser 拆分（DTO 模式）

**当前问题**：1978 行单体解析器，解析 + 外部扫描调度 + 元数据链接耦合

**目标结构**：

```
bytecode/
├── ClassFileParser             # 编排器：协调各 reader，返回 ParsedClassFile
├── ClassFileReader             # 保留，大端序缓冲读取
├── ParsedClassFile             # DTO：解析后的纯数据对象
├── ConstantPoolReader          # 常量池读取（tag 1-20）
├── ClassHeaderReader           # magic/version/className/superClass
├── FieldSectionReader          # 字段区：name + descriptor + attributes
├── MethodSectionReader         # 方法区：name + descriptor + attributes + params
├── AnnotationReader            # 注解读取：RuntimeVisible + RuntimeInvisible
├── TypeAnnotationReader        # 类型注解读取 + targetType 分发
├── ClassAttributeReader        # class 属性：Record/InnerClasses/Signature/SourceFile/EnclosingMethod/Module
├── ExternalClassReferenceCollector  # 收集被引用但未扫描的外部类名
├── ClassfileFormatException    # 格式异常
└── SkipClassException          # 跳过的类异常
```

**核心 DTO 定义**：

```java
// 解析后的纯数据对象 — 不引用 ClassInfo、ScanResult 等动态对象
public class ParsedClassFile {
    // 头部
    private final int minorVersion, majorVersion;
    private final int modifiers;
    private final String className, superclassName;
    private final String[] interfaceNames;

    // 常量池引用
    private final List<String> referencedClassNames;

    // 字段和方法
    private final List<ParsedField> fields;
    private final List<ParsedMethod> methods;

    // 注解
    private final List<ParsedAnnotation> classAnnotations;
    private final List<ParsedTypeAnnotation> classTypeAnnotations;

    // 属性
    private final String sourceFile;
    private final String enclosingClassName, enclosingMethodName;
    private final List<InnerClassEntry> innerClasses;
    private final ParsedModuleInfo moduleInfo;
    private final String signature;
    private final List<RecordComponentEntry> recordComponents;

    // 外部引用（供 ExternalClassReferenceCollector 使用）
    private final Set<String> externalReferencedClasses;

    // 构造参数过多，使用 Builder
    public static class Builder { ... }
}

// 解析后的字段数据
public record ParsedField(
    String name,
    String typeDescriptor,
    String typeSignature,
    int modifiers,
    Object constantValue,
    List<ParsedAnnotation> annotations,
    List<ParsedTypeAnnotation> typeAnnotations
) {}

// 解析后的方法数据
public record ParsedMethod(
    String name,
    String typeDescriptor,
    String typeSignature,
    int modifiers,
    String[] exceptionTypeNames,
    List<ParsedMethodParam> params,
    List<ParsedAnnotation> annotations,
    List<List<ParsedAnnotation>> paramAnnotations,
    List<ParsedTypeAnnotation> typeAnnotations
) {}

// 解析后的注解数据
public record ParsedAnnotation(
    String className,
    Map<String, Object> parameterValues  // 值类型：String, Integer, Long, Float, Double, Boolean, byte[], ParsedEnumValue, ParsedAnnotation (嵌套)
) {}
```

**ClassParser → ClassFileParser 编排器**：

```java
public class ClassFileParser {
    public ParsedClassFile parse(ClassFileReader reader, ScanOptions options, ScanLogger logger) {
        ClassHeaderReader headerReader = new ClassHeaderReader(reader);
        ConstantPoolReader cpReader = new ConstantPoolReader(reader, headerReader);
        AnnotationReader annotationReader = new AnnotationReader(reader, cpReader, options);
        TypeAnnotationReader typeAnnotationReader = new TypeAnnotationReader(reader, cpReader);
        FieldSectionReader fieldReader = new FieldSectionReader(reader, cpReader, annotationReader, typeAnnotationReader);
        MethodSectionReader methodReader = new MethodSectionReader(reader, cpReader, annotationReader, typeAnnotationReader);
        ClassAttributeReader attrReader = new ClassAttributeReader(reader, cpReader, annotationReader);

        return new ParsedClassFile.Builder()
            .version(headerReader.minorVersion(), headerReader.majorVersion())
            .modifiers(headerReader.modifiers())
            .className(headerReader.className())
            .superclassName(headerReader.superclassName())
            .interfaceNames(headerReader.interfaceNames())
            .referencedClassNames(cpReader.referencedClassNames())
            .fields(fieldReader.readAll())
            .methods(methodReader.readAll())
            .classAnnotations(attrReader.readAnnotations())
            .classTypeAnnotations(attrReader.readTypeAnnotations())
            .sourceFile(attrReader.sourceFile())
            .enclosingClassName(attrReader.enclosingClassName())
            .innerClasses(attrReader.innerClasses())
            .moduleInfo(attrReader.moduleInfo())
            .signature(attrReader.signature())
            .recordComponents(attrReader.recordComponents())
            .externalReferencedClasses(collectExternalRefs(cpReader, headerReader))
            .build();
    }
}
```

**设计模式论证**：
- **DTO（Data Transfer Object）**：ParsedClassFile 是解析阶段的纯数据输出，不引用 ClassInfo 等动态对象。这使得字节码解析成为完全独立、可单测的模块
- **Strategy**：各 Reader 可按需替换（如未来支持不同 class 文件版本）
- **为什么不继续用 Visitor**：ClassParser 当前的 TypeAnnotationDecorator 闭包模式实际上是 ad-hoc Visitor。保守建议保留现有装饰器模式，等 bytecode 拆分完成后再评估是否需要正式 Visitor

**MetadataLinker — 从 ClassParser 中分离**：

```java
// 将 ParsedClassFile 链接到 ClassInfo 图 — 独立于解析逻辑
public class MetadataLinker {
    public void link(
        ParsedClassFile parsed,
        Map<String, ClassInfo> classInfoMap,
        Map<String, PackageInfo> packageInfoMap,
        Map<String, ModuleInfo> moduleInfoMap,
        ScanOptions options
    ) {
        ClassInfo classInfo = ClassInfo.getOrCreateClassInfo(
            parsed.className(), parsed.modifiers(), classInfoMap
        );
        classInfo.setClassfileVersion(parsed.majorVersion(), parsed.minorVersion());
        classInfo.setSourceFile(parsed.sourceFile());
        classInfo.setEnclosingInfo(parsed.enclosingClassName(), parsed.enclosingMethodName());

        // 委托给 ClassHierarchy 处理关系
        parsed.superclassName().ifPresent(s -> classInfo.hierarchy().addSuperclass(s, classInfoMap));
        parsed.interfaceNames().forEach(i -> classInfo.hierarchy().addImplementedInterface(i, classInfoMap));

        // 字段和方法链接
        parsed.fields().forEach(f -> linkField(f, classInfo, classInfoMap));
        parsed.methods().forEach(m -> linkMethod(m, classInfo, classInfoMap));

        // 注解链接
        parsed.classAnnotations().forEach(a -> linkAnnotation(a, classInfo, classInfoMap));

        // 模块和包链接
        linkModule(parsed.moduleInfo(), classInfo, moduleInfoMap);
        linkPackage(parsed.className(), classInfo, packageInfoMap);
    }
}
```

**优先拆分顺序（基于依赖关系）**：
1. `ConstantPoolReader` — 无依赖，所有其他 reader 需要它
2. `AnnotationReader` — 只依赖 ConstantPoolReader
3. `FieldSectionReader` — 依赖 ConstantPoolReader + AnnotationReader
4. `MethodSectionReader` — 依赖 ConstantPoolReader + AnnotationReader
5. `ClassAttributeReader` — 依赖 ConstantPoolReader + AnnotationReader
6. `ExternalClassReferenceCollector` — 依赖 ConstantPoolReader + ClassHeaderReader
7. `MetadataLinker` — 依赖 ParsedClassFile（前 6 步完成后）

### 4.5 Classpath 状态封装

**当前问题**：抽象类暴露 public 可变字段，子类直接修改基类状态

**目标结构**：

```
classpath/
├── ClasspathElement            # 接口：所有 classpath 元素的契约
├── AbstractClasspathElement    # 抽象基类：共享状态管理
├── DirectoryClasspathElement   # 目录 classpath
├── JarClasspathElement         # JAR classpath
├── ModuleClasspathElement      # JPMS module classpath
├── ClasspathElementState       # 状态枚举：UNOPENED → OPEN → SCANNED → CLOSED
└── ClasspathElementFactory     # 工厂：根据 URI 类型创建对应实现
```

**接口定义**：

```java
// 接口契约
public interface ClasspathElement extends Comparable<ClasspathElement>, AutoCloseable {
    // 生命周期
    void open(WorkQueue<Resource> workQueue, ScanLogger logger) throws Exception;
    void scanPaths(ScanLogger logger) throws Exception;
    void maskClassfiles(int classfileMaskSize);  // pre-scan 完成后只执行一次

    // 资源访问
    Resource getResource(String relativePath);
    List<Resource> acceptedResources();
    List<Resource> acceptedClassfileResources();

    // 元数据
    URI getURI();
    List<URI> getAllURIs();
    Path getFilePath();       // null for jrt modules
    String getModuleName();   // null for non-modules
    ClasspathElementState state();
}

// 抽象基类 — 所有字段 private，子类通过 protected 方法访问
abstract class AbstractClasspathElement implements ClasspathElement {
    private ClasspathElementState state = ClasspathElementState.UNOPENED;
    private int classpathElementIdx;
    private final List<Resource> acceptedResources = new ArrayList<>();
    private List<Resource> acceptedClassfileResources = new ArrayList<>();
    private final Set<String> relativePathIndex = new HashSet<>();
    private final ScanOptions options;
    private final List<ClasspathElement> childElements = new ArrayList<>();

    // Protected 访问器 — 子类通过方法而非字段访问状态
    protected void markState(ClasspathElementState newState) {
        // 状态转换校验
        this.state = Objects.requireNonNull(newState);
    }

    protected void addAcceptedResource(Resource r) {
        if (relativePathIndex.add(r.getPath())) {  // 去重
            this.acceptedResources.add(r);
        }
    }

    protected void addAcceptedClassfile(Resource r) {
        this.acceptedClassfileResources.add(r);
    }

    protected void addChildElement(ClasspathElement child) {
        this.childElements.add(child);
    }

    protected ScanLogger logger() { return logger; }
    protected ScanOptions options() { return options; }
    ...

    // Public 只读查询
    @Override public List<Resource> acceptedResources() {
        return Collections.unmodifiableList(acceptedResources);
    }
    @Override public ClasspathElementState state() { return state; }
    ...
}
```

**设计模式论证**：
- **Template Method**：AbstractClasspathElement 定义骨架（open/scan/close 流程），子类实现差异化逻辑
- **Factory**：ClasspathElementFactory 根据 URI schema（`file:`/`jar:`/`jrt:`）创建对应实现
- **State**：ClasspathElementState 枚举控制生命周期转换，防止非法状态跳转

**封装改造路径**：
1. 字段改为 private，添加 protected getter/setter 供子类使用
2. 迁移子类从字段直接访问改为方法调用
3. 添加状态转换校验（UNOPENED → OPEN → SCANNED → CLOSED）
4. 公共查询方法返回不可变集合

### 4.6 HandlerRegistry 可插拔化

**当前问题**：handler 列表静态硬编码 unmodifiable list，测试无法替换

**目标结构**：

```
classpath/handler/
├── ClassLoaderHandler          # 接口（保持现有 default 方法模式）
├── ClassLoaderHandlerRegistry  # 可注册的注册表接口
├── DefaultHandlerRegistry      # 默认实现，保持现有顺序
├── ClassLoaderHandlerDescriptor # handler 元数据（名称、优先级、适用环境）
```

**接口定义**：

```java
// 可注册的注册表
public interface ClassLoaderHandlerRegistry {
    void register(ClassLoaderHandler handler);
    void registerFirst(ClassLoaderHandler handler);  // 插到最前面
    boolean unregister(ClassLoaderHandler handler);

    List<ClassLoaderHandler> findHandlers(Class<?> classLoaderClass, ScanLogger logger);
    List<ClassLoaderHandler> getAllHandlers();

    // 工厂方法：创建默认注册表
    static ClassLoaderHandlerRegistry createDefault() {
        return new DefaultHandlerRegistry();
    }
}

// 默认实现 — 保持现有 handler 顺序和 FallbackHandler 行为
class DefaultHandlerRegistry implements ClassLoaderHandlerRegistry {
    private final List<ClassLoaderHandler> handlers = new CopyOnWriteArrayList<>();

    DefaultHandlerRegistry() {
        // 按现有顺序注册（顺序敏感！）
        // 1. 容器特定 handler（Equinox, Felix, JBoss, Tomcat...）
        // 2. 通用 handler（URLClassLoader, JPMS）
        // 3. Fallback（始终匹配，必须最后）
        register(new EquinoxClassLoaderHandler());
        register(new EquinoxContextFinderClassLoaderHandler());
        register(new FelixClassLoaderHandler());
        register(new JBossClassLoaderHandler());
        register(new TomcatWebappClassLoaderBaseHandler());
        // ... 其余 15 个 handler
        register(new FallbackClassLoaderHandler());  // 最末
    }
}
```

**设计模式论证**：
- **Strategy**：每个 ClassLoaderHandler 是处理特定容器 ClassLoader 的策略
- **Registry**：允许运行时注册/查找 handler，支持测试注入
- **Chain of Responsibility**：handler 按顺序尝试匹配 ClassLoader，FallbackHandler 作为链尾

**为什么不引入 ServiceLoader**：ServiceLoader 依赖 classpath 扫描，会导致：
1. 启动性能下降（尤其 fat jar 场景）
2. 不可控的扩展加载（第三方 jar 可能意外注册 handler）
3. 顺序难以保证（ServiceLoader 排序依赖 jar 名称）

保留显式注册 + 可选 ServiceLoader 支持（通过 `registerFromServiceLoader()` 方法）。

### 4.7 Metadata 读写分离

**当前问题**：ClassInfo 同时承担构建（工厂方法 addScannedClass）+ 查询（getSubclasses 等）+ API（ClassQuery 委托）

**目标结构**：

```
metadata/
├── model/                      # 只读元数据接口
│   ├── ClassMetadata           # 只读接口：类名、修饰符、版本等
│   ├── FieldMetadata           # 只读接口：字段名、类型、修饰符、注解
│   ├── MethodMetadata          # 只读接口：方法名、签名、参数、注解
│   └── AnnotationMetadata      # 只读接口：注解类型、参数值
│
├── build/                      # 构建阶段 mutable 对象
│   ├── MutableClassMetadata    # 可变的 ClassMetadata 实现（扫描期间使用）
│   ├── MetadataGraphBuilder    # 类关系图构建器
│   └── MetadataLinker          # ParsedClassFile → ClassInfo 链接
│
├── query/                      # 查询服务
│   ├── ClassQueryService       # 类查询：按条件查找类
│   ├── HierarchyQueryService   # 层级查询：子类、父类、接口、注解关系
│   └── AnnotationQueryService  # 注解查询：按注解查找类/方法/字段
│
├── ClassInfo                   # 保留作为兼容层，委托给 model + hierarchy + members
├── ClassHierarchy              # 保留，类关系图管理（已在 7cfb29b 拆分）
└── ClassMembers                # 保留，字段/方法重载顺序（已在 7cfb29b 拆分）
```

**只读接口定义**：

```java
// 项目级不可变元数据接口 — 暴露给 ClassScanResult
public interface ClassMetadata {
    String name();
    int modifiers();
    String superclassName();
    List<String> interfaceNames();
    List<FieldMetadata> fields();
    List<MethodMetadata> methods();
    List<AnnotationMetadata> annotations();
    boolean isInterface();
    boolean isAnnotation();
    boolean isEnum();
    boolean isRecord();
}

// 项目级不可变方法元数据
public interface MethodMetadata {
    String name();
    String descriptor();
    String signature();
    int modifiers();
    List<String> parameterNames();
    List<String> parameterTypes();
    String returnType();
    List<AnnotationMetadata> annotations();
}
```

**设计模式论证**：
- **CQRS-lite（读写分离）**：MutableClassMetadata 用于构建阶段（写），ClassMetadata 接口用于查询阶段（读）。比完整的 CQRS 轻量，不需要事件溯源
- **Builder**：MutableClassMetadata 作为 immutable ClassMetadata 的 builder
- **为什么需要 Mutable 阶段**：扫描过程中 ClassInfo 的关系是逐步建立（先创建，后加 superclass、加接口、加注解），直接构建不可变对象需要分多阶段传递不完整数据

**ClassQuery 重复 guard clause 消除**：

```java
// 提取到私有方法，消除 ~150 行重复
public class ClassQuery {
    private final AtomicBoolean closed;
    private final ScanConfig scanConfig;

    private void checkValid() {
        if (closed.get()) {
            throw new IllegalStateException(
                "Cannot use a ScanResult after it has been closed"
            );
        }
        if (!scanConfig.enableClassInfo) {
            throw new IllegalStateException(
                "Please call ClassGraph#enableClassInfo() before #scan()"
            );
        }
    }

    private void checkAnnotations() {
        checkValid();
        if (!scanConfig.enableAnnotationInfo) {
            throw new IllegalStateException(
                "Please call ClassGraph#enableAnnotationInfo() before #scan()"
            );
        }
    }
}
```

### 4.8 日志解耦（Adapter + Null Object）

**当前问题**：LogNode 直接渗透到扫描、资源读取、字节码解析全流程。测试中难以替换。

**设计**：

```java
// 扫描日志抽象
public interface ScanLogger {
    ScanLogger child(String message);
    void info(String message);
    void warn(String message);
    void error(String message, Throwable throwable);

    // 便捷工厂
    static ScanLogger noop() { return NoopScanLogger.INSTANCE; }
}

// LogNode 适配器 — 保持现有日志行为
class LogNodeScanLogger implements ScanLogger {
    private final LogNode delegate;

    @Override
    public ScanLogger child(String message) {
        return new LogNodeScanLogger(delegate.child(message));
    }
    ...
}

// 空实现 — 测试或无日志场景
final class NoopScanLogger implements ScanLogger {
    static final NoopScanLogger INSTANCE = new NoopScanLogger();

    @Override public ScanLogger child(String msg) { return this; }
    @Override public void info(String msg) {}
    @Override public void warn(String msg) {}
    @Override public void error(String msg, Throwable t) {}
}

// 测试用 — 收集日志事件供断言
class ListScanLogger implements ScanLogger {
    private final List<LogEvent> events = new ArrayList<>();
    record LogEvent(Level level, String message, Throwable throwable) {}
    ...
}
```

**设计模式论证**：
- **Adapter**：LogNodeScanLogger 将现有 LogNode 适配为 ScanLogger 接口，不破坏现有日志行为
- **Null Object**：NoopScanLogger 消除 `log != null` 判断，单测直接使用

**迁移策略**：
1. 新增 ScanLogger 接口和三个实现
2. 在所有接受 `LogNode` 参数的方法上增加 `ScanLogger` 重载
3. 逐步迁移内部调用方使用 ScanLogger
4. 最后将 ScanLogger 作为唯一日志参数，移除 LogNode 直接依赖

---

## 5. Type 系统修复

这些问题不影响功能但违反设计契约，应在重构过程中修复。

### 5.1 TypeArg/TypeParam/MethodType 接口违约

**问题**：这些类继承 MetadataNode 但 `getClassName()`/`getClassInfo()` 抛 IllegalArgumentException。

**修复方案**：

```java
// 方案：将不适用于所有子类的方法从 MetadataNode 下移到具体子类
// MetadataNode 只保留所有子类都适用的方法
public abstract class MetadataNode {
    protected ScanResult scanResult;

    public void setScanResult(ScanResult scanResult) { ... }
    public ScanResult getScanResult() { ... }

    // 移除 getClassName() / getClassInfo()
    // 这些方法只适用于 ClassInfo, MethodInfo, FieldInfo 等 Named 类型
}
```

实际上 `getClassName()` 已在 `Named` 接口上定义，`MetadataNode` 不应重复定义。检查现有代码：如果 `MetadataNode` 只有 abstract 声明而没有实现，建议移除，让具体类直接实现 `Named`。

### 5.2 TypeParser 缺乏回溯能力

**问题**：PEG 解析器没有 `mark()/reset()`，依赖 `peek()` 前瞻。当前 JVM 类型语法足够简单所以没问题，但脆弱。

**修复方案**：

```java
// 可选增强 — 仅当需要支持更复杂语法时
class TypeParser {
    private int savedPos;  // 新增

    void mark() { savedPos = position; }
    void reset() { position = savedPos; }

    // 保持现有方法不变，新方法使用 mark/reset 实现更安全的解析
}
```

此修复优先级低，当前语法确实不需要回溯，仅作为防御性改进。

---

## 6. 命名和代码规范

### 6.1 全局 PascalCase → camelCase 重命名

影响范围：~100 个文件。**建议在第二阶段（补测试之后）批量执行**，避免与其他重构冲突。

| 当前 | 修正后 |
|------|--------|
| `ScanConfig`（局部变量） | `scanConfig` |
| `Classpath`（局部变量） | `classpath` |
| `ClassParser`（局部变量） | `classParser` |
| `JarReader`（局部变量） | `jarReader` |
| `MethodType`（局部变量） | `methodType` |
| `ClassType`（局部变量） | `classType` |
| `TypeVar`（局部变量） | `typeVar` |
| `TypeArg`（局部变量） | `typeArg` |
| `TypeParser`（局部变量） | `typeParser` |
| `LogNode`（局部变量） | `logNode` 或 `logger` |

实施方法：使用 IDE 的 Rename 重构（不要查找替换），每次一个变量名，确保只重命名局部变量/参数而不影响类名。

### 6.2 通用规范

1. 常量使用 `UPPER_SNAKE_CASE`
2. 除必要公开 API 外，类和构造函数降低可见性（package-private）
3. DTO 和不可变模型优先使用 `record`（项目使用 JDK 25）
4. 集合返回值优先返回 `Collections.unmodifiableList()` / `List.copyOf()`
5. 不在领域对象里做 I/O、线程调度、日志树构造
6. 注释说明"为什么"，不重复描述代码

---

## 7. 测试策略

### 7.1 测试金字塔（自底向上）

```
       ┌──────────────────┐
       │  E2E: 完整扫描    │  3 个场景
       │  (adapter tests)  │
       ├──────────────────┤
       │  Integration:      │
       │  Scan Pipeline     │  6 个场景
       ├──────────────────┤
       │  Unit: bytecode    │
       │  + classpath       │  20+ 个场景
       │  + metadata query  │
       └──────────────────┘
```

### 7.2 必须覆盖的场景（重构前置条件）

**bytecode 层（单元测试）**：
1. 基本 class 解析（类名、修饰符、版本号）
2. 常量池——String/Class/Field/Method/InterfaceMethod/NameAndType 引用
3. 字段解析——基本类型 + 对象类型 + 泛型签名 + 常量值
4. 方法解析——参数名/类型、异常表、泛型签名
5. 注解解析——运行时可见 + 不可见 + 参数值（基本类型、String、Enum、数组、嵌套注解）
6. 类型注解读取——class/field/method/parameter 级别的类型注解
7. Record 属性（JDK 16+）、Module 属性（JDK 9+）
8. 损坏的 class 文件 → ClassfileFormatException
9. 被过滤的 class → SkipClassException

**classpath 层（单元测试）**：
1. 目录 classpath——嵌套目录、符号链接
2. JAR classpath——普通 jar + multi-release jar
3. 嵌套 JAR——Spring Boot fat jar（BOOT-INF/lib/）、jar-in-jar
4. Module classpath——exploded module + jrt module
5. Class-Path manifest 条目
6. 重复 class 遮蔽规则（先找到的优先）

**scan pipeline 层（集成测试）**：
1. 目录扫描端到端 → ClassInfoList 正确
2. JAR 扫描端到端 → ClassInfoList 正确
3. 外部类扩展扫描（extends/implements 链扩展到未扫描的类）
4. classfile 过滤（accept/reject 规则正确生效）
5. 中断取消（中断后资源正确关闭、临时文件清理）
6. 多次扫描隔离（前后两次扫描不互相影响）

**adapter 层（集成测试）**：
1. ClassScanService.scan() 返回项目 DTO 格式正确
2. ClassMetadata 字段完整（类名、方法、字段、注解）
3. 异步扫描正常完成和取消

### 7.3 测试辅助工具

```java
// 小型测试用 class 生成器
class TestClassGenerator {
    static Path createSimpleJar(Path dir, String className, byte[] classBytes) { ... }
    static Path createFatJar(Path dir) { ... }  // 嵌套 jar
    static Path createMultiReleaseJar(Path dir) { ... }  // META-INF/versions/
}

// 测试 class 资源
class TestClassFiles {
    static final byte[] SIMPLE_CLASS = { (byte)0xCA, (byte)0xFE, ... };
    static final byte[] ANNOTATED_CLASS = { ... };
    static final byte[] GENERIC_CLASS = { ... };
}
```

---

## 8. 分阶段实施计划（依赖分析重排）

基于模块间依赖关系的分析，重新排序版本方案中的阶段：

```
依赖图：
Phase 1 (适配层)     ← 无依赖，与其他所有阶段并行
Phase 2 (测试)        ← 依赖 Phase 1 的适配接口定义，可与 Phase 1 并行
Phase 5' (命名规范)   ← 低风险，可在任意阶段执行，建议 Phase 2 后立即做
Phase 3 (ScanConfig)  ← 依赖 Phase 2 的测试保护
Phase 4 (Scanner)     ← 依赖 Phase 3（配置冻结后才能安全提取 pipeline）
Phase 7 (ClassParser) ← 与 Phase 4/6 独立，可并行
Phase 6 (Classpath)   ← 与 Phase 4/7 独立，可并行
Phase 8 (Metadata)    ← 依赖 Phase 4（ClassParser 拆分后才有 DTO 可链接）
Phase 9 (Handler)     ← 依赖 Phase 6（classpath 接口稳定后）
Phase 10 (日志解耦)   ← 依赖 Phase 4/7（Scanner 和 ClassParser 接口稳定后）
```

### 第一阶段：隔离业务依赖（2-3 天）

**目标**：项目级 ClassScanService 上线，UI 和业务服务不直接依赖 classgraph 内部 API

**改动清单**：
1. 新建 `com.bingbaihanji.fxdecomplie.service.classscan` 包
2. 定义 `ClassScanService` 接口
3. 定义 DTO：`ClassScanRequest`、`ClassScanResult`、`ClassScanOptions`、`ClassMetadata`、`MethodMetadata`、`FieldMetadata`、`AnnotationMetadata`
4. 实现 `ClassGraphClassScanService`（内部委托给现有 ClassGraph）
5. 查找所有直接引用 `ClassGraph`/`ScanResult`/`ClassInfo` 的业务代码
6. 逐一迁移到 `ClassScanService`
7. 补 adapter 层单元测试

**验收标准**：
- `grep -r "import com.bingbaihanji.classgraph" src/main/java/com/bingbaihanji/fxdecomplie/` 只在 `service/classscan/` 包内有结果
- 对外扫描结果使用项目自己的 DTO
- adapter 层测试通过

### 第二阶段：补回归测试 + 命名规范化（3-5 天）

**目标**：固化当前行为，清理命名，为后续拆分提供保护

**改动清单**：
1. 准备测试资源：生成小型 test jar（普通、嵌套、multi-release）、测试用 class 文件（注解、泛型、继承）
2. 补 bytecode 层单元测试（9 个场景，见 7.2）
3. 补 classpath 层单元测试（6 个场景）
4. 补 scan pipeline 集成测试（6 个场景）
5. 使用 IDE Rename 批量修正 ~100 个文件中的 PascalCase 局部变量
6. 修复已发现的 Bug：B1（ClassGraphClassLoaderHandler 包路径）、B2（FallbackClassLoaderHandler 重复调用）

**验收标准**：
- 所有测试通过（作为后续重构的基线）
- 命名规范化完成，IDE 不再出现 PascalCase 局部变量 warning
- B1、B2 修复验证通过

### 第三阶段：配置模型收口（2-3 天）

**目标**：ScanConfig 从 public mutable 字段 → getter/setter → 不可变 ScanOptions

**改动清单**：
1. 给 ScanConfig 所有 public 字段加 getter/setter
2. 全局替换字段直接访问为 getter/setter 调用（IDE: Encapsulate Fields）
3. 新建 `ScanOptions`（不可变）+ `ScanOptionsBuilder`
4. 新建 `ScanFilters`、`ScanRuntimeOptions`、`ScanClasspathOptions`、`ScanMetadataOptions`
5. 将 ScanConfig 改为兼容适配器（委托给 ScanOptions）
6. 修改 ClassGraphBuilder → build() 返回 ScanOptions
7. 修改 Scanner 接收 ScanOptions 而非直接读 ScanConfig 字段
8. 添加冻结检查：扫描开始后对 ScanOptions 的修改抛 IllegalStateException

**验收标准**：
- 扫描流程只读取不可变 ScanOptions
- 外部无法在扫描中途修改配置
- Phase 2 的回归测试全部通过

### 第四阶段：拆分扫描 Pipeline（3-5 天）

**目标**：Scanner 从 1161 行大流程类变成 ~150 行编排类

**改动清单**：
1. 新建 `scan/pipeline/` 包和 `ScanContext`、`ScanStep` 接口
2. 提取 `ClasspathDiscoveryStep`（从 ClasspathFinder 构造函数 → 独立 step）
3. 提取 `ClasspathOpenStep`（从 Scanner.openClasspathsThenScan() → 独立 step）
4. 提取 `NestedClasspathPreprocessor`
5. 提取 `ClasspathMaskingStep`
6. 提取 `ResourceScanStep`（从 Scanner.performScan() → 独立 step）
7. 提取 `ClassfileParseStep`
8. 提取 `MetadataLinkStep`
9. 提取 `ScanResultAssembler`
10. 将 `ClassfileScannerWorkUnitProcessor` 内部类提取为独立顶级类
11. 重写 `ScanPipeline.call()` 为 step 串联 + 统一异常处理 + 资源清理

**验收标准**：
- `Scanner.call()` 只负责流程编排、异常转换和资源清理
- 每个 step 可以独立测试
- Phase 2 的回归测试全部通过
- `ClasspathFinder` 构造函数不再包含 200 行业务逻辑

### 第五阶段：拆分字节码解析（4-6 天）

**目标**：ClassParser 从 1978 行单体 → ClassFileParser 编排器 + 7 个 Reader + ParsedClassFile DTO

**改动清单**：
1. 定义 `ParsedClassFile` 及 `ParsedField`/`ParsedMethod`/`ParsedAnnotation` DTO（record）
2. 提取 `ConstantPoolReader`（tag 1-20 的读取和索引）
3. 提取 `ClassHeaderReader`（magic/version/className/superClass/interfaces）
4. 提取 `AnnotationReader`（RuntimeVisible + RuntimeInvisible 注解）
5. 提取 `TypeAnnotationReader`（类型注解 + targetType 分发）
6. 提取 `FieldSectionReader`
7. 提取 `MethodSectionReader`
8. 提取 `ClassAttributeReader`
9. 提取 `ExternalClassReferenceCollector`
10. 提取 `MetadataLinker`（ParsedClassFile → ClassInfo 图）
11. 将内部类 ClassfileFormatException / SkipClassException 提升为顶级类
12. 重写 `ClassFileParser` 为编排器

**验收标准**：
- 解析阶段只产出 ParsedClassFile
- 元数据链接和外部类扫描调度不再混在 parser 内部
- bytecode 层单元测试全部通过（Phase 2 中已建立）
- 新增 MetadataLinker 单元测试

### 第六阶段：Classpath 和 Handler 治理（3-4 天，可与 Phase 5 并行）

**目标**：私有化 Classpath 状态；HandlerRegistry 可注册、可替换、可测试

**改动清单**：
1. Classpath 抽象类字段 → private + protected getter/setter
2. 迁移 DirClasspath/JarClasspath/ModuleClasspath 子类
3. 添加 `ClasspathElementState` 枚举 + 状态转换校验
4. 引入 `ClasspathElement` 接口 + `ClasspathElementFactory`
5. 新建 `ClassLoaderHandlerRegistry` 接口 + `DefaultHandlerRegistry`
6. 将 `HandlerRegistry` 的静态列表迁移到 `DefaultHandlerRegistry`
7. 修复 B3（EquinoxClassLoaderHandler 静态状态 → 实例字段）
8. 修复 B4（WeblogicClassLoaderHandler 增加异常处理，标记未测试路径）

**验收标准**：
- Classpath 元素状态只能通过明确方法修改
- 测试可以注入自定义 HandlerRegistry
- Phase 2 的回归测试全部通过

### 第七阶段：Metadata 模型分离（2-3 天）

**目标**：区分构建模型和查询模型，classQuery 消除重复 guard clause

**改动清单**：
1. 引入 MutableClassMetadata（构建阶段使用）
2. 引入只读接口 ClassMetadata/FieldMetadata/MethodMetadata（对外暴露）
3. 提取 ClassQueryService / HierarchyQueryService / AnnotationQueryService
4. ClassQuery 提取 checkValid()/checkAnnotations() 消除 ~150 行重复
5. 保留 ClassInfo 作为兼容层（委托给新接口 + hierarchy + members）

**验收标准**：
- 对外暴露的元数据不可变
- 查询服务和数据模型职责分离
- Phase 2 的回归测试全部通过

### 第八阶段：日志解耦 + 杂项清理（1-2 天）

**目标**：日志接口化，清理剩余设计异味

**改动清单**：
1. 引入 ScanLogger 接口 + LogNodeScanLogger/NoopScanLogger/ListScanLogger
2. 逐模块迁移：Scanner → ClassParser → Classpath → Resource
3. 移除 `TypedValue`（如果 JSON 序列化不再需要）
4. 将 `GraphvizDotfileGenerator` 移到 `scan/` 或独立 `viz/` 包
5. 消除 `PrivilegedActionInvocationHandler` 重复（提取到共享位置）
6. 检查 `FileSlice`/`PathSlice` 是否可合并（评估 mmap 差异是否可以策略化）
7. 修复 A4（TypeArg/TypeParam 接口违约）

**验收标准**：
- 核心流程不直接依赖 LogNode（只依赖 ScanLogger）
- 测试可以断言日志事件（ListScanLogger）
- 所有已知 Bug 和设计异味已修复或记录为已知限制

---

## 9. 风险和控制措施

### 9.1 核心算法风险

ClassGraph 兼容逻辑覆盖面广，一次性重写容易破坏：
- nested jar（BOOT-INF/lib/、jar-in-jar）
- multi-release jar（META-INF/versions/N/）
- JPMS module（exploded + jrt + module layer 遍历）
- classloader parent delegation（parent-first / parent-last）
- Spring Boot / Tomcat / OSGi 特殊路径
- 重复 class 遮蔽规则（classpath 顺序决定谁优先）

**控制措施**：
- 每次只拆一个职责
- 每步跑 Phase 2 回归测试
- 保持输入输出行为一致
- 每个 phase 单独分支，合并前通过 CI

### 9.2 业务依赖风险

如果业务层大量直接使用 ClassInfo、ScanResult，后续替换成本高。

**控制措施**：
- Phase 1 先做项目级适配层
- 所有新业务只依赖 ClassScanService
- 旧业务在 Phase 1 中完成迁移

### 9.3 并发风险

重构过程中 ScanConfig → ScanOptions 不可变化可能暴露隐藏的并发问题。

**控制措施**：
- Phase 3 通过 getter/setter 过渡，保持行为不变
- 引入冻结检查时添加详细日志
- 设 `-Dclassgraph.strictMode=true` 系统属性启用严格模式

### 9.4 许可证和上游差异

来自 MIT License 的 ClassGraph。

**控制措施**：
- 保留 LICENSE 文件
- 保留迁移来源说明（`classgraph/readme.txt`）
- 重大修改记录在 `docs/classgraph-upstream-diff.md`

### 9.5 每阶段回滚策略

| 阶段 | 回滚方式 |
|------|---------|
| Phase 1 | 删除 `service/classscan/` 包，恢复业务代码 import |
| Phase 2 | 测试是增量添加，无需回滚；命名可通过 `git revert` |
| Phase 3 | `git revert`；ScanConfig 兼容层期间新旧路径并存 |
| Phase 4 | `git revert`；如果部分 step 有问题可单独回退 |
| Phase 5 | `git revert`；ParsedClassFile 是新引入，不影响现有路径 |
| Phase 6 | `git revert`；ClasspathElement 接口可以共存 |
| Phase 7 | `git revert`；兼容层保留 |
| Phase 8 | `git revert`；ScanLogger 是新增接口，LogNode 保留不删除 |

---

## 10. 推荐最终结构

```
com.bingbaihanji.classgraph/
├── api/                            # 公开 API（兼容门面）
│   ├── ClassGraph.java             # 链式入口，委托给内部组件
│   └── ScanResult.java             # 扫描结果，委托给 ClassQuery + ResourceQuery
│
├── scan/
│   ├── ClassGraphBuilder.java      # 配置构建
│   ├── ScanOptions.java            # 不可变配置
│   ├── ScanExecutor.java           # 同步扫描
│   ├── AsyncScanExecutor.java      # 异步扫描
│   ├── ClasspathQueryService.java  # classpath 查询
│   ├── ScanConfig.java             # @Deprecated 兼容适配器
│   │
│   ├── config/
│   │   ├── ScanFilters.java
│   │   ├── ScanRuntimeOptions.java
│   │   ├── ScanClasspathOptions.java
│   │   └── ScanMetadataOptions.java
│   │
│   ├── pipeline/
│   │   ├── ScanPipeline.java
│   │   ├── ScanContext.java
│   │   ├── ScanStep.java
│   │   ├── ClasspathDiscoveryStep.java
│   │   ├── ClasspathOpenStep.java
│   │   ├── NestedClasspathPreprocessor.java
│   │   ├── ClasspathMaskingStep.java
│   │   ├── ResourceScanStep.java
│   │   ├── ClassfileParseStep.java
│   │   ├── MetadataLinkStep.java
│   │   └── ScanResultAssembler.java
│   │
│   ├── query/
│   │   ├── ClassQuery.java         # 兼容 API（委托给 query service）
│   │   └── ResourceQuery.java      # 兼容 API
│   │
│   └── viz/
│       └── GraphvizDotfileGenerator.java  # 从 resource/ 移入
│
├── bytecode/
│   ├── ClassFileParser.java        # 编排器
│   ├── ClassFileReader.java        # 大端序缓冲读取
│   ├── ParsedClassFile.java        # DTO
│   ├── ConstantPoolReader.java
│   ├── ClassHeaderReader.java
│   ├── FieldSectionReader.java
│   ├── MethodSectionReader.java
│   ├── AnnotationReader.java
│   ├── TypeAnnotationReader.java
│   ├── ClassAttributeReader.java
│   ├── ExternalClassReferenceCollector.java
│   ├── MetadataLinker.java
│   ├── ClassfileFormatException.java
│   └── SkipClassException.java
│
├── classpath/
│   ├── ClasspathElement.java       # 接口
│   ├── AbstractClasspathElement.java
│   ├── DirectoryClasspathElement.java
│   ├── JarClasspathElement.java
│   ├── ModuleClasspathElement.java
│   ├── ClasspathElementState.java  # 状态枚举
│   ├── ClasspathElementFactory.java
│   ├── ClasspathFinder.java        # 精简后的编排器
│   ├── ClasspathOrder.java
│   ├── ModuleFinder.java
│   ├── ModulePathInfo.java
│   ├── SystemJarFinder.java
│   ├── CallStackReader.java
│   │
│   └── handler/
│       ├── ClassLoaderHandler.java
│       ├── ClassLoaderHandlerRegistry.java
│       ├── DefaultHandlerRegistry.java
│       ├── FallbackClassLoaderHandler.java
│       ├── JPMSClassLoaderHandler.java
│       ├── URLClassLoaderHandler.java
│       ├── EquinoxClassLoaderHandler.java
│       ├── EquinoxContextFinderClassLoaderHandler.java
│       ├── FelixClassLoaderHandler.java
│       ├── JBossClassLoaderHandler.java
│       ├── TomcatWebappClassLoaderBaseHandler.java
│       ├── WebsphereLibertyClassLoaderHandler.java
│       ├── WebsphereTraditionalClassLoaderHandler.java
│       ├── WeblogicClassLoaderHandler.java
│       ├── SpringBootRestartClassLoaderHandler.java
│       ├── ParentLastDelegationOrderTestClassLoaderHandler.java
│       ├── PlexusClassWorldsClassRealmClassLoaderHandler.java
│       ├── AntClassLoaderHandler.java
│       ├── CxfContainerClassLoaderHandler.java
│       ├── QuarkusClassLoaderHandler.java
│       ├── UnoOneJarClassLoaderHandler.java
│       ├── ClassGraphClassLoaderHandler.java
│       └── OSGiDefaultClassLoaderHandler.java
│
├── metadata/
│   ├── model/
│   │   ├── ClassMetadata.java      # 只读接口
│   │   ├── FieldMetadata.java
│   │   ├── MethodMetadata.java
│   │   ├── AnnotationMetadata.java
│   │   ├── ModuleMetadata.java
│   │   └── PackageMetadata.java
│   │
│   ├── build/
│   │   ├── MutableClassMetadata.java
│   │   ├── MetadataGraphBuilder.java
│   │   └── MetadataLinker.java
│   │
│   ├── query/
│   │   ├── ClassQueryService.java
│   │   ├── HierarchyQueryService.java
│   │   └── AnnotationQueryService.java
│   │
│   ├── ClassInfo.java              # 兼容层
│   ├── ClassHierarchy.java         # 已在 7cfb29b 拆分
│   ├── ClassMembers.java           # 已在 7cfb29b 拆分
│   ├── ClassInfoList.java
│   ├── MethodInfo.java
│   ├── MethodInfoList.java
│   ├── MethodParam.java
│   ├── FieldInfo.java
│   ├── FieldInfoList.java
│   ├── AnnotationInfo.java
│   ├── AnnotationInfoList.java
│   ├── AnnotationParameterValue.java
│   ├── AnnotationParameterValueList.java
│   ├── AnnotationClassRef.java
│   ├── AnnotationEnumValue.java
│   ├── ModuleInfo.java
│   ├── ModuleInfoList.java
│   ├── ModuleRef.java
│   ├── PackageInfo.java
│   ├── PackageInfoList.java
│   ├── ClassMemberInfo.java
│   ├── InfoList.java
│   ├── TypedValue.java             # @Deprecated（JSON 序列化移除后删除）
│   ├── Named.java
│   └── MetadataNode.java
│
├── type/
│   ├── TypeSignature.java
│   ├── TypeParser.java
│   ├── ClassType.java
│   ├── MethodType.java
│   ├── ArrayType.java
│   ├── BaseType.java
│   ├── ClassRef.java
│   ├── TypeRef.java
│   ├── ReferenceType.java
│   ├── TypeVar.java
│   ├── TypeArg.java
│   ├── TypeParam.java
│   ├── HierarchicalType.java
│   ├── TypeUtils.java
│   └── ParseException.java
│
├── resource/
│   ├── Resource.java
│   ├── ResourceList.java
│   ├── Slice.java
│   ├── ArraySlice.java
│   ├── FileSlice.java
│   ├── PathSlice.java
│   ├── ZipFileSlice.java
│   ├── PhysicalZipFile.java
│   ├── LogicalZipFile.java
│   ├── FastZipEntry.java
│   ├── JarReader.java
│   ├── ModuleReaderProxy.java
│   ├── ByteBuffer.java
│   ├── Pool.java
│   ├── RecycleOnClose.java
│   ├── Resettable.java
│   │
│   └── reader/
│       ├── RandomAccessReader.java
│       ├── SequentialReader.java
│       ├── RandomAccessArrayReader.java
│       ├── RandomAccessByteBufferReader.java
│       └── RandomAccessFileChannelReader.java
│
├── reflect/
│   ├── ReflectionDriver.java
│   ├── StandardDriver.java
│   ├── NarcissusDriver.java
│   └── ReflectionUtils.java
│
├── logging/
│   ├── ScanLogger.java             # 日志接口
│   ├── LogNodeScanLogger.java      # LogNode 适配器
│   ├── NoopScanLogger.java
│   ├── ListScanLogger.java         # 测试用
│   └── LogNode.java                # 保留原实现
│
└── util/
    ├── Assert.java
    ├── AutoCloseableExecutorService.java
    ├── CollectionUtils.java
    ├── FastPathResolver.java
    ├── FileUtils.java
    ├── InterruptionChecker.java
    ├── JarUtils.java
    ├── LazyList.java
    ├── ProxyingInputStream.java
    ├── SimpleThreadFactory.java
    ├── SingletonMap.java
    ├── StringUtils.java
    ├── Strings.java
    ├── URLPathEncoder.java
    ├── VersionFinder.java
    └── WorkQueue.java
```

项目业务侧：

```
com.bingbaihanji.fxdecomplie.service.classscan/
├── ClassScanService.java
├── ClassGraphClassScanService.java
├── ClassScanRequest.java
├── ClassScanResult.java
├── ClassScanOptions.java
├── ClassMetadata.java
├── MethodMetadata.java
├── FieldMetadata.java
├── AnnotationMetadata.java
└── ScanStatistics.java
```

---

## 11. 优先级结论

### 推荐实施顺序

```
Phase 1 (适配层)  ──────────────────────┐
Phase 2 (测试+命名) ────────────────────┤  ← 可并行
Phase 3 (ScanConfig)                     │
Phase 4 (Scanner Pipeline)               │
Phase 5 (ClassParser)  ───┐              │  ← Phase 5/6 可并行
Phase 6 (Classpath+Handler) ─┘           │
Phase 7 (Metadata)                       │
Phase 8 (日志+杂项)                      │
```

### 如果只做 3 天（最小可行重构）

1. Phase 1：建 ClassScanService → 业务隔离（最重要的架构改进）
2. Phase 2 中的 Bug 修复：B1 + B2 + B3（3 个影响正确性的 bug）
3. 命名规范化（低风险高收益）

### 如果只做 2 周（推荐范围）

Phase 1-4：适配层 + 测试 + 配置收口 + Scanner pipeline 拆分。这四个阶段是整个重构的核心骨架。

### 长期方向

如果项目只是需要稳定使用 ClassGraph 能力且无强定制需求，最终形态：

```
业务代码 → ClassScanService → 官方 Maven ClassGraph
```

当前本地迁移代码在 ClassScanService 适配层隔离后，可以随时替换后端实现，无需修改业务代码。
