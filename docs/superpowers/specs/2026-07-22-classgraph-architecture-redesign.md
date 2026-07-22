# ClassGraph 架构重新设计 — 设计文档

> 创建日期: 2026-07-22
> 状态: 待审核

## 目标

对 `com.bingbaihanji.classgraph` 包进行全面架构重构：领域驱动分包、统一命名规范、纯 JDK 实现、合理应用设计模式。

## 背景

当前 classgraph 包从 ClassGraph 4.8.184（Luke Hutchison, MIT License）完整移植，~44,560 行 / 101 文件 / 13 个子包。存在以下问题：

- `core/` 包过于庞大（50+ 文件），职责不清
- 命名不统一：`ObjectTypedValueWrapper`、`PotentiallyUnmodifiableList` 等冗长命名；方法名前缀混乱（`enable`/`ignore`/`accept`/`reject` 混用）
- 单文件过大：ClassInfo 3512 行、ClassFile 2035 行、ClassGraph 1723 行
- `Parser.java` 通过 `com.bingbaihanji.utils.json.JSONUtils` 间接依赖 Jackson
- 大量未被 fxdecomplie 使用的子包和功能
- 设计模式隐含但不显式，代码结构不清晰

## 优先级

用户确定的优化优先级（从高到低）：

1. **命名规范统一** — 类名、方法名遵循一致命名风格
2. **包结构清晰** — 每个子包职责单一、内聚
3. **性能/代码简洁** — 消除冗余代码、减少文件数、大文件拆分
4. **设计模式应用** — 仅在自然适合处应用，不强制

## 不改动范围

- **fxdecomplie 其余代码** — 仅更新 `ClassGraphWorkspaceAdapter` 的 import
- **Adapter 公开 API 兼容** — `ScanResult`、`ClassInfo`、`ClassInfoList`、`AnnotationInfo`、`AnnotationInfoList` 的公开方法签名保持兼容
- **功能完整性** — 保留 ClassGraph 全部功能（类路径扫描、字节码解析、类型系统、反射工具、资源扫描、模块系统）

---

## 第一章：领域划分与顶层包结构

```text
com.bingbaihanji.classgraph
│
├── ClassGraph.java           # 建造者入口（组装三个子系统）
├── ScanResult.java           # 查询门面
│
├── metadata/     (1) 数据模型 —— 类元数据的图结构，纯 POJO
├── bytecode/     (2) 字节码解析 —— 纯 JDK .class 文件二进制解析器
├── type/         (3) 类型系统 —— JVM 类型签名的 PEG 解析与类型树
├── classpath/    (4) 类路径发现 —— 目录/JAR/模块的遍历与 ClassLoader 内省
├── resource/     (5) 资源 IO —— 快速 ZIP 读取、文件切片
├── scan/         (6) 扫描引擎 —— 编排调度、并发控制、过滤规则
├── reflect/      (7) 反射工具 —— 标准反射 + Narcissus 封装穿透
│
└── util/         (8) 内部工具 —— 路径、字符串、文件、日志（模块内可见）
```

### 子系统关系

```
ClassGraph (建造者) → Scanner (扫描引擎)
  Scanner → classpath/ (类路径发现)
  Scanner → bytecode/ (字节码解析)
  Scanner → scan/ (过滤 & 并发)
  classpath/ → resource/ (资源IO)
  bytecode/ → type/ (类型系统)
  Scanner → ScanResult (查询门面)
  ScanResult → metadata/ (数据模型)
  scan/ → metadata/
```

### 三个子系统各自独立可测

| 子系统 | 输入 | 输出 | 依赖 |
|--------|------|------|------|
| `bytecode/` | `byte[]` | `ParsedClass` | `type/` |
| `metadata/` | `ParsedClass` 列表 | `ClassInfo` 关系图 | 无 |
| `classpath/` | 路径/ClassLoader 配置 | `Resource` 列表 | `resource/` |

---

## 第二章：命名规范

### 2.1 通用命名规则

| 类别 | 规则 | 示例 |
|------|------|------|
| 布尔查询 | `isXxx` — 自身状态；`hasXxx` — 是否拥有某物 | `isInterface()`、`hasAnnotation(name)` |
| 查找查询 | `findXxx` — 可能返回 null/空 | `findClass(name)`、`findSubclasses()` |
| 属性获取 | `getXxx` — 非 null 返回值 | `getName()`、`getModifiers()` |
| 集合获取 | `getXxx` 复数形式 | `getFields()`、`getMethods()` |
| Builder | `withXxx` 返回 this | `withClassLoader(cls)`、`withPackages(pkgs)` |
| 工厂方法 | `of` / `from` 静态工厂 | `ScanResult.of(classes)` |

### 2.2 核心类重命名

| 当前名称 | 新名称 | 理由 |
|----------|--------|------|
| `ObjectTypedValueWrapper` | `TypedValue` | 去掉冗余前缀后缀 |
| `PotentiallyUnmodifiableList` | `LazyList` | 简洁，表达"延迟冻结"语义 |
| `MappableInfoList` | 合并到 `InfoList` | 两个类功能重叠，保留一个 |
| `ClassGraphClassLoader` | `ScanClassLoader` | 去掉 `ClassGraph` 前缀，表达职责 |
| `ClassGraphException` | `ScanException` | 同上 |
| `ScanResultObject` | `MetadataNode` | 表达"图中节点"的语义 |
| `CloseableByteBuffer` | `ByteBuffer` | 已是 JDK 类的简单 wrapper |
| `ClassRefOrTypeVariableSignature` | `TypeRef` | 省略无信息量的长尾 |
| `ClassRefTypeSignature` | `ClassRef` | 同上 |
| `HasName` | `Named` | 接口命名约定 |
| `ClasspathElementZip` | `JarClasspath` | 表达"JAR 类路径" |
| `ClasspathElementDir` | `DirClasspath` | 表达"目录类路径" |
| `ClasspathElementModule` | `ModuleClasspath` | 表达"模块类路径" |
| `ClasspathElement` | `Classpath` | 抽象基类 |
| `Recycler` | `Pool` | 更通用易懂 |
| `NestedJarHandler` | `JarReader` | 表达职责 |

### 2.3 方法命名示例

```java
// 新风格
ClassGraph.create()
    .withClassInfo()
    .withMethodInfo()
    .withoutFieldVisibilityFilter()
    .filterPackages("com.foo")
    .filterJars(jar -> !jar.endsWith("-sources.jar"))
    .scan();
```

---

## 第三章：各包详细内容

### 3.1 `metadata/` — 数据模型（~10 个文件）

```text
metadata/
├── Named.java                  # 接口：可命名实体
├── ClassInfo.java              # 核心：类元数据 + 关系图节点 (~600行)
├── ClassHierarchy.java         # 从 ClassInfo 拆出：关系图遍历 (~500行)
├── FieldInfo.java              # 字段信息
├── MethodInfo.java             # 方法信息
├── MethodParam.java            # 方法参数信息
├── AnnotationInfo.java         # 注解信息 + 参数值
├── PackageInfo.java            # 包信息
├── ModuleInfo.java             # 模块信息
├── InfoList.java               # 通用列表基类（合并原 MappableInfoList）
```

**ClassInfo 拆分方案（3512行 → 3个文件）：**

| 类 | 职责 | 行数估算 |
|----|------|---------|
| `ClassInfo` | 核心属性：name、modifiers、flags、source、classLoader、字段/方法/注解集合 | ~600 |
| `ClassHierarchy` | EnumMap 关系图 + BFS 传递闭包 + 循环断路算法 | ~500 |
| `ClassMembers` | 字段/方法查询：getDeclared+getAll（含 override order） | ~400 |

`ClassInfo` 持有 `ClassHierarchy` 和 `ClassMembers` 引用，对外暴露委托方法保持 API 简洁。

### 3.2 `bytecode/` — 字节码解析（~4 个文件）

```text
bytecode/
├── ClassFileReader.java       # 二进制缓冲区（当前 ClassfileReader）
├── ClassParser.java           # 按 JVM 规范阶段拆分解析（当前 ClassFile）
├── ConstantPool.java          # 常量池解析（从 ClassFile 拆出）
├── AttributeReader.java       # 属性解析：注解、签名、内部类、模块、Record
```

**ClassParser 解析流程：**
```java
ParsedClass parse(byte[] bytes) {
    parseHeader();           // magic, version
    parseConstantPool();     // constant pool entries
    parseFields();           // field definitions
    parseMethods();          // method definitions  
    parseAttributes();       // class-level attributes
}
```

### 3.3 `type/` — 类型系统（~8 个文件）

```text
type/
├── TypeSignature.java         # 抽象基类
├── ClassType.java             # 类类型签名
├── ArrayType.java             # 数组类型签名
├── BaseType.java              # 基本类型签名
├── TypeVar.java               # 类型变量
├── MethodType.java            # 方法类型签名
├── TypeArg.java               # 类型参数
├── TypeParser.java            # PEG 解析器（当前 Parser.java）
```

### 3.4 `classpath/` — 类路径发现（~25 个文件）

```text
classpath/
├── ClasspathFinder.java       # 类路径发现编排
├── ClassLoaderFinder.java     # ClassLoader 树遍历
├── ClasspathOrder.java        # 类路径顺序
│
├── Classpath.java             # 抽象基类（当前 ClasspathElement）
├── DirClasspath.java          # 目录类路径
├── JarClasspath.java          # JAR 类路径
├── ModuleClasspath.java       # JPMS 模块类路径
│
├── handler/                   # ClassLoaderHandler 体系（21 个 handler）
│   ├── ClassLoaderHandler.java
│   ├── HandlerRegistry.java
│   ├── UrlHandler.java
│   ├── JpmsHandler.java
│   ├── TomcatHandler.java
│   └── ... (其余 17 个)
│
├── ModuleFinder.java          # JPMS 模块发现
└── SystemJarFinder.java       # 系统 JAR 发现
```

### 3.5 `resource/` — 资源 IO（~7 个文件）

```text
resource/
├── Slice.java                 # 抽象文件切片
├── ArraySlice.java            # 内存切片
├── FileSlice.java             # 文件切片
├── PathSlice.java             # 路径切片
├── SliceReader.java           # 切片顺序/随机读取器
├── JarReader.java             # ZIP 快速读取（当前 NestedJarHandler + LogicalZipFile）
└── Resource.java              # 扫描到的资源
```

### 3.6 `scan/` — 扫描引擎（~8 个文件）

```text
scan/
├── ClassGraph.java            # 建造者入口（1723行 → ~500行）
├── ScanConfig.java            # 扫描配置对象（当前 ScanSpec）
├── Scanner.java               # 扫描编排（1188行，基本保持）
├── Filter.java                # 过滤规则（当前 AcceptReject）
├── FilterBuilder.java         # 建造者模式的过滤规则构建
├── ScanResult.java            # 结果查询门面（1597行 → ~600行）
├── ResourceQuery.java         # ScanResult 中的资源查询子集
├── ClassQuery.java            # ScanResult 中的类/继承查询子集
```

**ScanResult 拆分（1597行 → 3个文件）：**
```java
ScanResult
├── classes()  → ClassQuery   (持有 className → ClassInfo 索引)
└── resources() → ResourceQuery (持有 path → Resource 索引)
```

### 3.7 `reflect/` — 反射工具（~3 个文件）

```text
reflect/
├── ReflectionDriver.java      # 接口
├── StandardDriver.java        # 标准反射
├── NarcissusDriver.java       # 封装穿透
```

### 3.8 `util/` — 内部工具（~10 个文件，模块内可见）

```text
util/
├── Paths.java                 # 路径解析（当前 FastPathResolver + FileUtils 中路径相关）
├── Strings.java               # 字符串工具（含 escapeJson）
├── Collections.java           # 集合工具
├── IoStreams.java             # IO 工具
├── VersionFinder.java         # JDK 版本检测
├── Log.java                   # 日志门面（当前 LogNode）
├── Concurrency.java           # 并发工具（合并 concurrency/ 子包 5 个文件）
├── Assert.java                # 断言
```

### 文件数对比

| 维度 | 当前 | 新设计 |
|------|------|--------|
| 总文件数 | ~101 | ~75 |
| core/ 包文件数 | 50+ | 0（拆分到 8 个包） |
| 最大单文件行数 | 3512 (ClassInfo) | ~800 |
| 子包数 | 13 | 8 |

---

## 第四章：设计模式应用

### 4.1 建造者模式 `Builder` — `ClassGraph`

规范化 Fluent API 方法名：

```java
ScanResult result = ClassGraph.create()
    .withClassInfo()
    .withMethodInfo()
    .filterPackages("com.myapp")
    .filterJars(jar -> !jar.endsWith("-sources.jar"))
    .scan();
```

### 4.2 门面模式 `Facade` — `ScanResult`

```java
// 外部只看到这些查询方法，内部实现完全隐藏
scanResult.classes().all();                    // → ClassQuery
scanResult.classes().implementing(itfName);    // → ClassQuery
scanResult.classes().annotatedWith(annName);   // → ClassQuery
scanResult.resources().withExtension(".xml"); // → ResourceQuery
scanResult.resources().matching("glob:**");    // → ResourceQuery
```

### 4.3 策略模式 `Strategy` — `Classpath`

```java
public abstract class Classpath {
    abstract void open();
    abstract void scan();
    abstract Resource resolve(String path);
}

class DirClasspath extends Classpath { ... }
class JarClasspath extends Classpath { ... }
class ModuleClasspath extends Classpath { ... }
```

### 4.4 访问者模式 `Visitor` — `ClassParser`

```java
interface AttributeVisitor {
    void visitAnnotation(AnnotationInfo info);
    void visitInnerClass(ClassInfo inner, ClassInfo outer);
    void visitRecord(RecordComponent... components);
    void visitModule(ModuleInfo module);
}
```

### 4.5 责任链模式 `Chain of Responsibility` — `HandlerRegistry`

21 个 ClassLoaderHandler 依次尝试直到找到可处理者（已存在，规范化）。

### 4.6 对象池模式 `Object Pool` — `Pool`

```java
Pool<ModuleReaderProxy> pool = Pool.of(ModuleReaderProxy::new);
pool.use(reader -> { ... });
```

### 4.7 不可变/防御性拷贝 — 所有返回集合的方法

`ScanResult` 返回的所有集合做防御性拷贝，防止外部修改内部图结构。

### 模式总览

| 模式 | 应用位置 | 改动量 |
|------|---------|--------|
| Builder | `ClassGraph` | 方法重命名 + 少量接口调整 |
| Facade | `ScanResult` → `ClassQuery` + `ResourceQuery` | 拆分，对调用方透明 |
| Strategy | `Classpath` 体系 | 保持现有结构，规范化 |
| Visitor | `ClassParser` 属性解析 | 抽接口，拆 if-else |
| Chain of Responsibility | `HandlerRegistry` | 已存在，规范化 |
| Object Pool | `Pool`（原 Recycler） | 重命名 + API 简化 |
| Immutable | 所有返回集合的方法 | 已有，规范化 |

---

## 第五章：纯 JDK 实现

### 5.1 当前唯一的外部依赖

`type/TypeParser.java` 第 32 行：`import com.bingbaihanji.utils.json.JSONUtils;`

调用点在异常消息格式化中，用于转义 JSON 字符串。`JSONUtils.escapeJSONString()` 核心逻辑 ~35 行纯字符替换。

### 5.2 清除方案

将 `escapeJSONString` 方法内联到 `util/Strings.java`：

```java
// util/Strings.java（纯 JDK）
public static String escapeJson(String s) {
    if (s == null) return null;
    var buf = new StringBuilder(s.length() * 2);
    for (char c : s.toCharArray()) {
        switch (c) {
            case '"'  -> buf.append("\\\"");
            case '\\' -> buf.append("\\\\");
            case '\b' -> buf.append("\\b");
            case '\f' -> buf.append("\\f");
            case '\n' -> buf.append("\\n");
            case '\r' -> buf.append("\\r");
            case '\t' -> buf.append("\\t");
            default -> {
                if (c < 0x20) buf.append("\\u").append(String.format("%04x", (int) c));
                else buf.append(c);
            }
        }
    }
    return buf.toString();
}
```

### 5.3 验证状态

- classgraph 包内 101 个文件的 953 条 import 中，零直接第三方依赖
- 日志使用 `java.util.logging`（JDK 内置）
- 字节码解析为自实现，不依赖 ASM
- 仅此一处间接依赖 Jackson，重构后彻底消除

---

## 第六章：测试策略与兼容性

### 6.1 测试分层

```text
test/
├── metadata/     # ClassInfo 关系图正确性、ClassHierarchy BFS 遍历
├── bytecode/     # ClassParser 对 JVM 各版本 class 文件的解析正确性
├── type/         # TypeParser 对泛型/数组/通配符签名的解析
├── classpath/    # 目录/JAR/模块扫描正确性（测试 fixture）
├── scan/         # 端到端扫描集成测试
└── adapter/      # fxdecomplie 的 ClassGraphWorkspaceAdapter 兼容性
```

### 6.2 兼容性承诺

`ClassGraphWorkspaceAdapter` 使用的 API 面极窄（仅 5 个类的公开方法）：

```java
// 这些方法的签名在重构后保持兼容
ScanResult(Map<String, ClassInfo>)          // 简化构造函数
ScanResult.getAllClasses()                  // 遍历
ScanResult.getClassInfo(String)            // 按名查找
ClassInfo(String, int)                      // 构造函数
ClassInfo.getName() / getFullPath()        // 属性
ClassInfo.getInterfaces() / getSuperclass() // 继承
ClassInfo.getAnnotationInfo()               // 注解
AnnotationInfo(String)                      // 构造函数
```

内部实现（关系图、索引结构、解析流程）完全自由重构。

### 6.3 验证流程

```
1. mvn -DskipTests compile      → 编译通过
2. mvn test                      → 所有 154 测试通过
3. mvn -DskipTests package       → fat JAR 打包成功
4. 手动启动 → 继承分析面板正常工作
```

---

## 自检清单

- [x] 无 TBD/TODO 未填内容
- [x] 所有改动项有明确的文件和方案描述
- [x] 不改动范围明确
- [x] 测试验证步骤清晰
- [x] 命名规范覆盖所有核心类和方法
- [x] 包划分与架构图中每个文件归属明确
- [x] 设计模式仅在自然适合处应用
- [x] 纯 JDK 依赖清除方案具体可执行
- [x] 与现有 CLAUDE.md 规范不冲突
