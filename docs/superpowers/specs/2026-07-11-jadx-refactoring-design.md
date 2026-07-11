# jadx 引擎适配性优化与工具类统一重构 — 设计文档

**日期**: 2026-07-11
**状态**: 已批准
**来源**: jadx 迁移后适配性差、存在大量工具类重复、集合框架未统一、状态不一致

---

## 1. 目标

将 jadx 引擎深度集成到 fxdecomplie 项目中，消除迁移后的"水土不服"：

1. 统一反混淆、日志等状态管理入口，使 jadx 与项目其他 4 个引擎行为一致
2. 消除 jadx 侧与 `util/` 包之间的工具类重复（4 个核心文件 + 多个小工具）
3. 提取 jadx 中具有通用价值的代码到项目 `util/` 包
4. 反编译热路径中的 `HashMap`/`HashSet` 替换为 `ArrayMap`/`ArraySet`
5. 清理冗余代码，删除完全重复的类

## 2. 执行策略

**策略一：按五阶段顺序执行**（已选定）

每个阶段完成后 `./mvnw clean compile -DskipTests` 验证通过再进入下一阶段。

| 阶段 | 内容 | 预估影响面 |
|---|---|---|
| 1. 状态统一 | 修复 `createJadxArgs()` 从 AppConfig 读取全部参数 | ~5 文件 |
| 2. 工具类替换 | 删除/瘦身 jadx 侧 4 个重复工具类 | ~120 文件 |
| 3. 通用抽取 | 新增 `SmaliNameUtils` 等工具类 | ~30 文件 |
| 4. 集合优化 | 热路径 `HashMap`→`ArrayMap`、`HashSet`→`ArraySet` | ~35 文件 |
| 5. 冗余清理 | 删除 `ImmutableList`、`GsonUtils`、jadx `Pair` 等 | ~15 文件 |

## 3. 改动范围总览

```
项目根
├── config/AppConfig.java              ← [改] jadx 参数读取入口
├── decompiler/
│   ├── JadxDecompiler.java            ← [改] 核心：从 AppConfig 读取参数
│   ├── JadxParameters.java            ← [改] 参数映射表对齐
│   └── DecompilerOptions.java         ← [改] 为 jadx 新增选项标准化方法
├── util/                               ← [新增/扩展]
│   ├── ByteUtils.java                 ← [扩] 合并 md5Sum、byteToHex 等
│   ├── SmaliNameUtils.java            ← [新] Smali↔Java 名称转换
│   ├── JadxOptionsBridge.java         ← [新] AppConfig → JadxArgs 映射桥
│   ├── JadxConsts.java               ← [新] JVM 类常量（来自原 Consts）
│   ├── DescriptorUtils.java           ← [新] JVM 描述符解析
│   └── NamedThreadFactory.java        ← [新] 从 jadx Utils 提取
└── core/jadx/
    ├── core/Consts.java               ← [删] 合并到 JadxConsts
    ├── core/utils/Utils.java          ← [瘦身] 只保留 jadx 专属方法
    ├── core/utils/ListUtils.java      ← [删] 重定向到 Stream API/util ListUtils
    ├── core/utils/StringUtils.java    ← [删] 代码生成部分移入 codegen/utils
    ├── core/utils/files/FileUtils.java ← [删] 重定向到 JDK API/util/ByteUtils
    ├── core/utils/ImmutableList.java  ← [删] 用 List.of() 替代
    ├── core/utils/GsonUtils.java      ← [删] 合并到项目级 Gson 配置
    ├── core/utils/Pair.java           ← [删] 用 util/collection/Pair 替代
    ├── api/plugins/utils/Utils.java   ← [删] 与 core/utils/Utils 合并
    └── **/*.java (热路径)             ← [改] HashMap/HashSet → ArrayMap/ArraySet
```

### 核心原则

1. **jadx 专属方法留在原地**：`BlockUtils`、`RegionUtils`、`InsnRemover` 等反编译器特有逻辑不动
2. **通用工具移入 util 包**：jadx 内部通过 import 重定向调用
3. **每阶段编译通过**：`./mvnw clean compile -DskipTests` 成功后进入下一阶段

---

## 4. 阶段 1：状态管理与依赖统一

### 4.1 问题

`decompiler/JadxDecompiler.java` 的 `createJadxArgs()` 硬编码了 8 个参数（如 `setDeobfuscationOn(false)` 被写死），其余 14 个 `JadxParameters` 中可配置的参数被完全忽略。用户在 SettingsDialog 中调整 jadx 设置后反编译结果不变。

`Consts.java` 中的独立 Debug 开关与项目 SLF4J 日志体系不一致。

### 4.2 方案

新增 `JadxOptionsBridge` 作为桥接类：

```
AppConfig.Decompiler.engineOptions()  ← 用户通过 SettingsDialog 配置
              │
              ▼
DecompilerOptions.forEngine("JADX")   ← 提取 jadx 专属选项 Map
              │
              ▼
JadxOptionsBridge.apply(args, options) ← [新] 将 Map 映射到 JadxArgs setter
              │
              ▼
JadxDecompiler.createJadxArgs(ctx)    ← [改] 接收 DecompilerContext 读取选项
```

### 4.3 具体改动

1. **新增 `util/JadxOptionsBridge.java`**：将 `Map<String, String>` 映射到 `JadxArgs` 的 22 个 setter
2. **修改 `decompiler/JadxDecompiler.java`**：`createJadxArgs()` → `createJadxArgs(DecompilerContext ctx)`，通过 `DecompilerOptions.forEngine()` 获取选项后调用 `JadxOptionsBridge.apply()`
3. **删除 `core/jadx/core/Consts.java`**：
   - `DEBUG_*` 开关 → 删除（项目用 SLF4J 日志级别控制）
   - `CLASS_*` 常量 → 移至 `util/JadxConsts.java`
   - `DEFAULT_PACKAGE_NAME`、`ANONYMOUS_CLASS_PREFIX` → 移至 `util/JadxConsts.java`

---

## 5. 阶段 2：存量工具类复用

### 5.1 `jadx/core/utils/ListUtils.java` → 完全删除

| jadx 方法 | 替换为 |
|---|---|
| `map()` | `stream().map().toList()` |
| `first()`/`last()`/`firstOrNull()` | jadx 内部已有的 `Utils.first()/last()` |
| `filter()` | `stream().filter().toList()` |
| `anyMatch()`/`allMatch()`/`noneMatch()` | `stream().anyMatch()`/`.allMatch()`/`.noneMatch()` |
| `mutableListOf()` | `List.of()` 或 `new ArrayList<>()` |
| `distinctList()` | `stream().distinct().toList()` |
| `concat()` | `Stream.concat()` |
| `safeReplace()`/`safeAdd()`/`safeRemove()` | 内联到调用处（每个方法 4-5 行） |

### 5.2 `jadx/core/utils/Utils.java` → 瘦身保留

**删除以下通用方法**（替换为项目 util 包或 JDK 标准 API）：

| 删除的方法 | 替换为 |
|---|---|
| `isEmpty(Collection)` / `isEmpty(Map)` / `isEmpty(T[])` | `ListUtils.isNullOrEmpty()` / `MapUtils.isNullOrEmpty()` |
| `notEmpty(...)` | `!isNullOrEmpty()` |
| `collectionMap()` / `collectionMapNoNull()` | `stream().map().toList()` |
| `strRepeat()` | JDK 11 `String.repeat()` |
| `safeParseInt()` / `safeParseInteger()` | `NumberUtil` 扩展 |
| `listToString()` 系列 | `String.join()` 或 `Collectors.joining()` |
| `mergeLists()` / `mergeSets()` / `mergeMaps()` | `Stream.concat()` / `SetUtils.union()` / `MapUtils.merge()` |
| `groupBy()` | `stream().collect(Collectors.toMap())` |
| `newConstStringMap()` | `Map.of()` 或 `MapUtils` |
| `getEnvVarBool()` / `getEnvVarInt()` | 直接删除（已标记 @Deprecated） |
| `simpleThreadFactory()` | 提取到 `util/NamedThreadFactory.java` |

**smali 名称转换方法**提取到 `util/SmaliNameUtils.java`（见阶段 3）。

**保留**：`currentStackTrace()`、`getStackTrace()`、`lockList()`、`containsInListByRef()`、`treeDfsVisit()`、`checkThreadInterrupt()` 等 jadx 专属方法。

### 5.3 `jadx/core/utils/files/FileUtils.java` → 完全删除

| jadx 方法 | 替换为 |
|---|---|
| `bytesToHex()` / `byteToHex()` / `intToHex()` | `ByteUtils.bytesToHex()`（扩展 `ByteUtils` 添加单字节/整型的 hex 方法） |
| `md5Sum()` | 扩展到 `ByteUtils` |
| `copyStream()` | JDK 9 `InputStream.transferTo()` |
| `streamToByteArray()` | JDK 9 `InputStream.readAllBytes()` |
| `streamToString()` | `new String(input.readAllBytes(), UTF_8)` |
| `close(Closeable)` | try-with-resources |
| `readFile()` / `writeFile()` | `Files.readString()` / `Files.writeString()` |
| `makeDirs()` / `makeDirsForFile()` | `Files.createDirectories()` |
| `isZipFile()` | 扩展到 `ByteUtils` |
| `renameFile()` | `Files.move()` |
| `hasExtension()` / `getPathBaseName()` | 内联到调用处 |

### 5.4 `jadx/core/utils/StringUtils.java` → 完全删除

| jadx 方法 | 替换为 |
|---|---|
| `isEmpty()` / `notEmpty()` / `notBlank()` | JDK 11 `String.isBlank()` + 内联 null 检查 |
| `countMatches()` / `containsChar()` / `removeChar()` | 内联到调用处 |
| `escape()` / `escapeXML()` / `escapeResValue()` 等 | 移入 `core/jadx/core/codegen/utils/` |
| `getDateText()` | `java.time` API |
| `unescapeString()` / `unescapeChar()` | 保留在 jadx codegen 侧 |

### 5.5 新增/扩展的 util 文件

| 文件 | 内容 |
|---|---|
| `util/SmaliNameUtils.java`（新） | Smali↔Java 类型名互转 |
| `util/NamedThreadFactory.java`（新） | 从 jadx `Utils.simpleThreadFactory()` 提取 |
| `util/ByteUtils.java`（扩） | 新增 `md5Sum()`、`byteToHex(int)`、`intToHex(int)` |

---

## 6. 阶段 3：通用能力抽取

### 6.1 新工具类

| 新文件 | 来源 | 内容 |
|---|---|---|
| `util/SmaliNameUtils.java` | jadx `Utils.java` | `smaliToJava()`、`javaToSmali()`、`cleanObjectName()`、`isPrimitiveDesc()` 等 |
| `util/JadxConsts.java` | jadx `Consts.java` | `CLASS_OBJECT`、`CLASS_STRING` 等 JVM 标准类常量 |
| `util/DescriptorUtils.java` | jadx `DescriptorParser.java` | JVM 方法描述符解析器 |

### 6.2 `SmaliNameUtils` 接口

```java
public final class SmaliNameUtils {
    public static String smaliToJava(String smaliName);       // "Ljava/lang/String;" → "java.lang.String"
    public static String javaToSmali(String javaName);        // "java.lang.String" → "Ljava/lang/String;"
    public static String smaliToInternal(String smaliName);   // "Ljava/lang/String;" → "java/lang/String"
    public static String descriptorToJava(String descriptor); // "I" → "int", "[[B" → "byte[][]"
    public static String javaToDescriptor(String javaType);   // "int" → "I"
    public static boolean isPrimitiveDescriptor(String desc);
}
```

### 6.3 不提取的代码及理由

| 代码 | 理由 |
|---|---|
| `EncodedValueUtils` | jadx 专属 Dex 值编码 |
| `InsnUtils.formatOffset()` | 反编译器内部指令偏移格式化 |
| `BlockUtils` / `RegionUtils` / `InsnRemover` | 反编译 Pass 专用 |
| `AndroidResourcesUtils` / `AndroidManifestParser` | Android 资源解析，仅 jadx 使用 |
| `ModifiedUTF8Decoder` | JVM class 文件专用 |
| `DebugUtils` / `DebugChecks` | 开发调试验证 |

---

## 7. 阶段 4：数据结构与底层库高性能优化

### 7.1 集合替换：热路径优先

| 层级 | 说明 | 处理方式 |
|---|---|---|
| 热路径 | 类型推断、finally 处理、控制流、SSA、region maker、try-catch | `HashMap`→`ArrayMap`、`HashSet`→`ArraySet` |
| 温路径 | 类加载初始化、属性存储、XML 解析 | 评估后选择性替换 |
| 冷路径 | 一次性配置、CLI 参数、插件管理 | 保留原生 JDK 集合 |

具体替换规则：
- `HashMap<K,V>` → `new ArrayMap<>()`（中小型 <1000 条）
- `HashSet<E>` → `new ArraySet<>()`（同上）
- `ArrayList<T>` → 热路径小列表用 `List.of()`（不可变）或保持 `ArrayList`（需可变）
- `ImmutableList` → `List.copyOf()` 或 `List.of()` 替代后删除

### 7.2 ASM/反射统一收口

jadx 对 ASM 的使用通过其自身的 `org.objectweb.asm` 依赖（树 API：`ClassNode`、`MethodNode` 等），属于反编译引擎核心数据结构，**不需要**重定向到 `util/reflect/asm/`。

需要统一的点：
- 独立反射调用 → 用 `ReflectUtils` 替代
- 废弃引用 `UtilConfig`/`ArrayUtils` → 替换为项目 `util/` 对应工具

---

## 8. 阶段 5：冗余代码清理

### 8.1 删除清单

| 文件 | 原因 |
|---|---|
| `core/utils/ImmutableList.java` | `List.of()` / `List.copyOf()` 替代 |
| `core/utils/GsonUtils.java` | 项目统一 Gson 配置 |
| `core/utils/Pair.java` | `util/collection/Pair.java` 替代 |
| `api/plugins/utils/Utils.java` | 与 `core/utils/Utils.java` 合并后删除 |
| `core/utils/CacheStorage.java` | 内联到调用方 |

### 8.2 保留但不动

| 文件 | 理由 |
|---|---|
| `core/utils/exceptions/JadxRuntimeException.java` | jadx 专用异常类型 |
| `core/utils/exceptions/CodegenException.java` 等 | jadx 专用异常 |
| `core/utils/EmptyBitSet.java` | jadx 内部实现 |

---

## 9. 统计预估

| 阶段 | 新增 | 修改 | 删除 | 影响面 |
|---|---|---|---|---|
| 1. 状态统一 | 1 | 3 | 1 | ~5 文件 |
| 2. 工具类替换 | 3 | ~120 | 4 | ~120 文件 |
| 3. 通用抽取 | 2 | ~30 | 0 | ~30 文件 |
| 4. 集合优化 | 0 | ~35 | 1 | ~35 文件 |
| 5. 冗余清理 | 0 | ~15 | 3 | ~15 文件 |

---

## 10. 验证标准

- 每阶段编译通过：`./mvnw clean compile -DskipTests`
- 全部测试通过：`./mvnw test`
- 反编译结果与重构前一致（用 Vineflower 和 jadx 分别反编译标准 Java 类库验证）
- jadx 反混淆开关可正常控制 jadx 引擎行为
- SettingsDialog 中 jadx 参数修改生效
