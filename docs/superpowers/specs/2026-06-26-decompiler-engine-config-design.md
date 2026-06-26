# 反编译引擎参数配置 — 设计规格

**日期:** 2026-06-26
**状态:** 已批准，待实施

---

## 1. 概述

### 问题
CFR、Procyon、Vineflower 的反编译参数在源码中硬编码（CfrDecompiler 26 项、ProcyonDecompiler 2+8 项、VineflowerDecompiler 27 项），用户无法通过 GUI 调整，只能手写 JSON。

### 目标
参考 Recaf 项目，为每个引擎提供可视化参数配置面板，参数范围和 Recaf 对齐，按常用/高级分组，与现有 JSON 编辑器双向同步。

### 不做
- JD-Core 引擎参数（引擎本身无可配置项）
- 配置 hash 缓存失效机制（后续迭代）
- 配置导入/导出功能（现有机制已支持）
- 一次性补齐全部中文翻译（fallback 英文标签保证可用）

---

## 2. 架构

```
AppConfig.Decompiler.engineOptions
  Map<String, Map<String, String>>
  (引擎名 → 参数键 → 值)
            │
    ┌───────┼──────────┐
    ▼                  ▼
UI 面板              JSON TextArea
(CheckBox/Spinner)   (可折叠，高级入口)
    │                  │
    └──── 双向同步 ────┘
```

### 数据流

1. 引擎参数定义 → `DecompilerParameter` record（静态常量列表）
2. 用户配置值 → `AppConfig.Decompiler.engineOptions`（已有结构，不变）
3. 运行时会话 → `DecompilerOptions.forEngine()`（已有方法）→ `DecompilerContext` → 引擎
4. 参数面板初始化：`engineOptions` 有值用配置值，无值用 `DecompilerParameter.defaultValue`

---

## 3. 数据模型

### 3.1 DecompilerParameter

```java
// 新增: model/DecompilerParameter.java
package com.bingbaihanji.fxdecomplie.model;

public record DecompilerParameter(
    String key,            // 参数键名, 如 "decodeenumswitch"
    ParamType type,        // 控件类型
    String defaultValue,   // 引擎默认值
    String i18nKey,        // 标签的 i18n key, 如 "engine.cfr.decodeenumswitch"
    String helpKey,        // 帮助文本的 i18n key, 如 "engine.cfr.decodeenumswitch.help"
    Category category,     // COMMON / ADVANCED
    String[] range         // INTEGER: [min, max]; ENUM: 选项列表; 其他: null
) {
    public enum ParamType { BOOLEAN, INTEGER, STRING, ENUM }
    public enum Category { COMMON, ADVANCED }
}
```

### 3.2 引擎参数定义类

每个引擎一个静态类，提供 `List<DecompilerParameter> PARAMETERS` 常量：

| 文件 | 参数数量 |
|------|---------|
| `decompiler/CfrParameters.java` | ~77 项 |
| `decompiler/ProcyonParameters.java` | 21 项 |
| `decompiler/VineflowerParameters.java` | 47 项 |

### 3.3 CFR Tropolean 处理

CFR 有 TrooleanOption (DEFAULT/NEITHER/TRUE/FALSE)。简化策略：

- **UI 中统一为普通 CheckBox**：勾选 → 写入 `"true"`，不勾选 → 写入 `"false"`
- **DEFAULT 语义**：当参数值为 DEFAULT 时，完全不写入 `engineOptions` Map（等效于"让引擎自己决定"）
- **NEITHER 不支持**：用户极少使用，不暴露在 UI 中。如需设置，可在 JSON TextArea 中手动写入 `null`
- **初始加载**：Map 中无此 key → CheckBox 不勾选（使用引擎默认行为），Map 中有 `"true"` → 勾选，Map 中有 `"false"` → 不勾选

---

## 4. 参数详细列表

### 4.1 CFR 参数（~77 项）

#### 常用（20 项）
| 参数键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| decodeenumswitch | BOOLEAN | true | 将 switch 重构为 enum switch |
| sugarenums | BOOLEAN | true | 反糖化枚举 |
| decodestringswitch | BOOLEAN | true | 将 switch 重构为 string switch |
| decodelambdas | BOOLEAN | true | 反编译 lambda 表达式 |
| innerclasses | BOOLEAN | true | 反编译内部类 |
| tryresources | BOOLEAN | true | 反编译 try-with-resources |
| decodefinally | BOOLEAN | true | 反编译 finally 块 |
| removebadgenerics | BOOLEAN | true | 移除无法反编译的泛型 |
| sugarasserts | BOOLEAN | true | 反糖化 assert 语句 |
| sugarboxing | BOOLEAN | true | 反糖化自动装箱 |
| stringbuilder | BOOLEAN | true | 反糖化 StringBuilder |
| stringconcat | BOOLEAN | true | 反糖化字符串拼接 |
| recordtypes | BOOLEAN | true | 支持 record 类型 |
| sealedclasses | BOOLEAN | true | 支持 sealed class |
| switchexpression | BOOLEAN | true | 支持 switch 表达式 |
| instanceofpattern | BOOLEAN | true | 支持 instanceof 模式匹配 |
| previewfeatures | BOOLEAN | true | 支持预览特性 |
| showinferrable | BOOLEAN | true | 显示可推断的泛型参数 |
| override | BOOLEAN | true | 显示 @Override 注解 |
| eclipse | BOOLEAN | true | Eclipse 兼容模式 |

#### 高级（~57 项）
| 参数键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| showversion | BOOLEAN | false | 在注释中显示反编译器版本 |
| hideutf | BOOLEAN | false | 隐藏 UTF-8 字符 |
| hidelongstrings | BOOLEAN | false | 隐藏过长字符串 |
| removeboilerplate | BOOLEAN | false | 移除样板代码 |
| removeinnerclasssynthetics | BOOLEAN | false | 移除内部类合成成员 |
| relinkconst | BOOLEAN | false | 重新链接常量引用 |
| relinkconststring | BOOLEAN | false | 重新链接字符串常量 |
| liftconstructorinit | BOOLEAN | false | 提升构造函数初始化 |
| removedeadmethods | BOOLEAN | false | 移除死方法 |
| sugarretrolambda | BOOLEAN | false | 反糖化 RetroLambda |
| tidymonitors | BOOLEAN | false | 整理监视器块 |
| commentmonitors | BOOLEAN | false | 在监视器处添加注释 |
| lenient | BOOLEAN | true | 宽松模式（容错解析） |
| comments | BOOLEAN | false | 输出 CFR 内部注释 |
| antiobf | BOOLEAN | false | 反混淆增强 |
| obfcontrol | BOOLEAN | false | 混淆控制流检测 |
| obfattr | BOOLEAN | false | 混淆属性检测 |
| constobf | BOOLEAN | false | 常量混淆检测 |
| hidebridgemethods | BOOLEAN | false | 隐藏桥接方法 |
| ignoreexceptions | BOOLEAN | false | 忽略异常处理 |
| version | BOOLEAN | false | 显示版本信息 |
| labelledblocks | BOOLEAN | false | 标记块 |
| j14classobj | BOOLEAN | false | Java 1.4 Class 对象语法 |
| hidelangimports | BOOLEAN | false | 隐藏 java.lang 导入 |
| renamedupmembers | BOOLEAN | false | 重命名重复成员 |
| renamesmallmembers | INTEGER | 0 | 成员重命名最小长度阈值 |
| renameillegalidents | BOOLEAN | false | 重命名非法标识符 |
| renameenumidents | BOOLEAN | false | 重命名非法枚举常量名 |
| staticinitreturn | BOOLEAN | false | 静态初始化返回 |
| usenametable | BOOLEAN | false | 使用名称表 |
| pullcodecase | BOOLEAN | false | 拉取 case 代码 |
| elidescala | BOOLEAN | false | 省略 Scala 相关代码 |
| usesignatures | BOOLEAN | false | 使用签名属性 |
| arrayiter | BOOLEAN | false | 数组迭代器语法糖 |
| collectioniter | BOOLEAN | false | 集合迭代器语法糖 |
| forbidmethodscopedclasses | BOOLEAN | false | 禁止方法作用域类 |
| forbidanonymousclasses | BOOLEAN | false | 禁止匿名类 |
| skipbatchinnerclasses | BOOLEAN | false | 跳过批处理内部类 |
| forcetopsort | BOOLEAN | — | 强制拓扑排序 |
| forloopaggcapture | BOOLEAN | — | 循环捕获优化 |
| forcetopsortaggress | BOOLEAN | — | 激进拓扑排序 |
| forcetopsortnopull | BOOLEAN | — | 拓扑排序不拉取 |
| forcecondpropagate | BOOLEAN | — | 强制条件传播 |
| reducecondscope | BOOLEAN | — | 缩减条件作用域 |
| forcereturningifs | BOOLEAN | — | 强制合并返回 if |
| forceexceptionprune | BOOLEAN | — | 强制异常修剪 |
| aexagg | BOOLEAN | — | 激进异常修剪 |
| aexagg2 | BOOLEAN | — | 激进异常修剪 2 |
| recovertypeclash | BOOLEAN | — | 恢复类型冲突 |
| recovertypehints | BOOLEAN | — | 恢复类型提示 |
| removedeadconditionals | BOOLEAN | — | 移除死条件 |
| aggressivedoextension | BOOLEAN | — | 激进的 do 扩展 |
| aggressiveduff | BOOLEAN | — | 激进的 Duff 设备 |
| aggressivedocopy | INTEGER | 0 | 激进复制深度 |
| aggressivesizethreshold | INTEGER | 13000 | 激进大小阈值 |
| allowmalformedswitch | BOOLEAN | — | 允许畸形 switch |
| ignoreexceptionsalways | BOOLEAN | — | 始终忽略异常 |
| forceclassfilever | STRING | — | 强制 class 文件版本 |
| recover | BOOLEAN | true | 恢复模式 |

### 4.2 Procyon 参数（21 项）

#### 常用（10 项）
| 参数键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| unicodeOutputEnabled | BOOLEAN | true | Unicode 输出（保留标识符中的 Unicode 字符） |
| previewFeaturesEnabled | BOOLEAN | true | 预览特性支持 |
| showSyntheticMembers | BOOLEAN | false | 显示合成成员 |
| forceExplicitImports | BOOLEAN | true | 强制显式导入（不使用通配符） |
| forceExplicitTypeArguments | BOOLEAN | false | 强制显式类型参数 |
| flattenSwitchBlocks | BOOLEAN | false | 扁平化 switch 块 |
| retainRedundantCasts | BOOLEAN | false | 保留冗余强制转换 |
| includeLineNumbersInBytecode | BOOLEAN | true | 在字节码中保留行号 |
| showDebugLineNumbers | BOOLEAN | false | 显示调试行号注释 |
| simplifyMemberReferences | BOOLEAN | false | 简化成员引用 |

#### 高级（11 项）
| 参数键 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| alwaysGenerateExceptionVariableForCatchBlocks | BOOLEAN | true | Always generate exception variable for catch blocks |
| forceFullyQualifiedReferences | BOOLEAN | false | 强制完全限定引用 |
| excludeNestedTypes | BOOLEAN | false | 排除嵌套类型 |
| retainPointlessSwitches | BOOLEAN | false | 保留无意义 switch |
| includeErrorDiagnostics | BOOLEAN | true | 包含错误诊断信息 |
| mergeVariables | BOOLEAN | false | 合并变量 |
| disableForEachTransforms | BOOLEAN | false | 禁用 for-each 转换 |
| textBlockLineMinimum | INTEGER | 3 | 文本块最小行数 |
| forcedCompilerTarget | ENUM | — | 强制编译器目标版本 |
| bytecodeOutputOptions | ENUM | — | 字节码输出选项 |
| languageTarget | ENUM | — | 语言目标（默认 Java） |

### 4.3 Vineflower 参数（47 项）

#### 常用（20 项）
| 参数键 | VF 原生 key | 类型 | 默认值 | 说明 |
|--------|------------|------|--------|------|
| decompileInner | din | BOOLEAN | true | 反编译内部/匿名类 |
| decompileGenericSignatures | dgs | BOOLEAN | true | 反编译泛型签名 |
| decompileAssertions | das | BOOLEAN | true | 反编译 assert 语句 |
| removeBridge | rbr | BOOLEAN | true | 移除桥接方法 |
| removeSynthetic | rsy | BOOLEAN | true | 移除合成成员 |
| bytecodeToSource | bto | BOOLEAN | true | 字节码到源码优化映射 |
| newNumberRepresentation | nns | BOOLEAN | true | 新数字表示（十六进制浮点等） |
| useMethodParameters | ump | BOOLEAN | true | 使用方法参数名 |
| useDebugVarNames | udv | BOOLEAN | true | 使用调试变量名（LVT） |
| patternMatching | pam | BOOLEAN | true | 模式匹配支持 |
| switchExpressions | swi | BOOLEAN | true | switch 表达式支持 |
| decompilePreview | dec | BOOLEAN | true | 预览特性反编译 |
| decompileFinally | fdi | BOOLEAN | true | 反编译 finally 块 |
| removeEmptyRanges | rer | BOOLEAN | true | 移除空 try-catch 范围 |
| overrideAnnotation | ovr | BOOLEAN | true | 生成 @Override 注解 |
| inlineSimpleLambdas | isl | BOOLEAN | true | 内联简单 lambda |
| ternaryConditions | tco | BOOLEAN | true | 三元条件表达式 |
| hideEmptySuper | hes | BOOLEAN | true | 隐藏空 super 调用 |
| hideDefaultConstructor | hdc | BOOLEAN | true | 隐藏默认构造函数 |
| decompileEnums | den | BOOLEAN | true | 反编译枚举 |

#### 高级（27 项）
| 参数键 | VF 原生 key | 类型 | 默认值 | 说明 |
|--------|------------|------|--------|------|
| asciiStringCharacters | asc | BOOLEAN | false | ASCII 字符限制（转义 Unicode） |
| booleanTrueOne | bto | BOOLEAN | true | Boolean true = 1 表示 |
| syntheticNotSet | sns | BOOLEAN | false | 合成字段不赋值 |
| undefinedParamTypeObject | uto | BOOLEAN | true | 未定义参数类型设为 Object |
| incorporateReturns | ner | BOOLEAN | true | 合并返回语句 |
| ensureSynchronizedMonitors | esm | BOOLEAN | true | 确保同步监视器 |
| removeGetClassNew | rgn | BOOLEAN | true | 移除 getClass/new 调用 |
| keepLiterals | lit | BOOLEAN | false | 保持字面量不变 |
| decompileJava4 | dc4 | BOOLEAN | true | 反编译 Java 1.4 类特性 |
| lambdaToAnonymousClass | lac | BOOLEAN | false | Lambda 转为匿名类 |
| bytecodeSourceMapping | bsm | BOOLEAN | false | 字节码→源码行映射 |
| dumpCodeLines | dcl | BOOLEAN | false | 导出源码行调试信息 |
| ignoreInvalidBytecode | iib | BOOLEAN | false | 忽略无效字节码 |
| verifyAnonymousClasses | vac | BOOLEAN | false | 验证匿名类 |
| ternaryConstantSimplification | tcs | BOOLEAN | false | 三元常量简化 |
| tryLoopFix | tlf | BOOLEAN | true | 尝试循环修复 |
| showHiddenStatements | shs | BOOLEAN | false | 显示隐藏语句 |
| simplifyStack | sst | BOOLEAN | true | 简化栈二次遍历 |
| verifyMerges | vvm | BOOLEAN | false | 验证变量合并 |
| explicitGenerics | ega | BOOLEAN | false | 显式泛型参数 |
| skipExtraFiles | sef | BOOLEAN | false | 跳过额外生成文件 |
| warnInconsistentInnerAttributes | wia | BOOLEAN | true | 警告内部类不一致 |
| dumpBytecodeOnError | dbe | BOOLEAN | true | 错误时导出字节码 |
| dumpExceptionOnError | dee | BOOLEAN | true | 错误时导出异常堆栈 |
| decompilerComments | dec | BOOLEAN | false | 反编译器内部注释 |
| sourceFileComments | sfc | BOOLEAN | false | 源文件注释 |
| decompileComplexCondys | dcc | BOOLEAN | false | 反编译复杂常量动态 |
| forceJsrInline | fji | BOOLEAN | false | 强制 JSR 内联 |
| logLevel | log | ENUM | ERROR | 日志级别 (ERROR/WARN/INFO/DEBUG) |
| maxProcessingTime | mpm | INTEGER | 60 | 单方法最大处理时间（秒） |
| preferredLineLength | pll | INTEGER | 130 | 首选行长度 |
| indent | ind | STRING | "    " | 缩进字符串 |

### 4.4 直接映射到引擎原生 key

在 `engineOptions` 中，Vineflower 参数存为 VF 原生 key（如 `dgs`、`rbr`），面板上显示可读名称。`VineflowerParameters` 中维护可读名称 → 原生 key 的映射。CFR 和 Procyon 参数 key 与引擎原生 key 一致，无需映射。

---

## 5. UI 布局

### 5.1 反编译标签页结构

```
┌─────────────────────────────────────────────────────┐
│  settings.decompiler                                 │
│                                                      │
│  [默认引擎: VINEFLOWER ▼]        ← 现有控件，不变     │
│                                                      │
│  ┌──────────────────────────────────────────────┐    │
│  │  [CFR 参数] [Procyon 参数] [Vineflower 参数]  │    │  ← 子标签页 TabPane
│  ├──────────────────────────────────────────────┤    │
│  │                                              │    │
│  │  ▼ 常用参数                    ← 展开         │    │
│  │  ┌──────────────────────────────────────┐    │    │
│  │  │ [✓] decodeenumswitch                 │    │    │
│  │  │ [✓] sugarenums                       │    │    │
│  │  │ ...                                  │    │    │
│  │  └──────────────────────────────────────┘    │    │
│  │                                              │    │
│  │  ▶ 高级参数                    ← 折叠         │    │
│  │                                              │    │
│  └──────────────────────────────────────────────┘    │
│                                                      │
│  ▶ 引擎选项 JSON (高级)            ← 折叠的 TextArea │
└─────────────────────────────────────────────────────┘
```

### 5.2 参数控件

| ParamType | 控件 | 示例 |
|-----------|------|------|
| BOOLEAN | CheckBox | `[✓] decodeenumswitch` |
| INTEGER | Spinner (min/max 可控) | `[60 ▴▾]` |
| STRING | TextField | `[    ]` |
| ENUM | ComboBox | `[ERROR ▼]` |

每行：`[控件] 参数标签`，标签右侧可悬停显示帮助提示（Tooltip）。

### 5.3 布局辅助

- 参数列表放入 `ScrollPane`，支持大量参数滚动
- "常用"使用 `TitledPane` 默认展开
- "高级"使用 `TitledPane` 默认折叠
- 控件标签通过 `I18nUtil.getStringOrDefault()` 获取文本

---

## 6. JSON ↔ 面板双向同步

### 6.1 初始化

1. 从 `AppConfig.Decompiler.engineOptions` 读取当前引擎的参数 Map
2. 对于每个 `DecompilerParameter`，查 Map 中是否有值：有值→设为控件值，无值→设 `DecompilerParameter.defaultValue`
3. JSON TextArea：序列化当前 Map 为 pretty-print JSON

### 6.2 面板 → JSON（实时）

- 每次控件值变化时 → 更新内存中的参数 Map → 序列化到 JSON TextArea
- **例外**：如果用户正在 JSON 区域手动编辑（区域有焦点），跳过自动更新，避免干扰光标

### 6.3 JSON → 面板（失焦时）

- JSON TextArea 失去焦点时 → 尝试解析 JSON → 成功 → 回填面板控件
- 解析失败 → 面板保持当前值，TextArea 边框标红，但不弹错误框

### 6.4 冲突解决

- 以面板为准（面板是主要交互路径）
- 当面板和 JSON 不一致时，面板修改覆盖 JSON

### 6.5 默认值处理

- 面板初始加载时：配置中无值 → 使用 `DecompilerParameter.defaultValue`（引擎硬编码默认值）
- 用户修改后写入 `engineOptions` Map
- 值与默认值相同 → 不写入 Map（保持 Map 精简），面板仍显示默认值
- "恢复默认"按钮 → 清空当前引擎的 `engineOptions` 条目 + 重置面板所有控件为默认值 + 清空 JSON

---

## 7. i18n 策略

### 7.1 复用现有基础设施

使用 `i18n/` 目录下的三个类：`I18nContext`、`I18nUtil`、`I18nRefreshable`。

### 7.2 标签获取

```java
I18nUtil.getStringOrDefault(param.i18nKey(), fallbackEnglishLabel)
```

- 先在 `.properties` 文件中查找 `i18nKey`
- 命中 → 翻译文本
- 未命中 → 返回 `fallbackEnglishLabel`（从参数 key 自动生成可读英文标签）

### 7.3 Fallback 生成规则

```
decodeenumswitch  →  "Decode Enum Switch"
showSyntheticMembers  →  "Show Synthetic Members"
```

首字母大写，驼峰边界插入空格，缩写（如 `utf`）保持大写。

### 7.4 语言切换

反编译参数面板实现 `I18nRefreshable`：
- 在创建时调用 `I18nUtil.addRefreshable(this)`
- `refreshI18n()` 中遍历所有控件行，重新设置标签和提示文本
- 无需重建面板或控件

### 7.5 Key 命名约定

```
engine.cfr.<paramKey>        = 标签文本
engine.cfr.<paramKey>.help   = 帮助描述
engine.procyon.<paramKey>    = 标签文本
engine.procyon.<paramKey>.help = 帮助描述
engine.vineflower.<paramKey> = 标签文本
engine.vineflower.<paramKey>.help = 帮助描述
```

### 7.6 翻译策略

- 初始版本：全部 fallback 英文标签 + `getStringOrDefault()`，功能立即可用
- 后续：逐步将 fallback 标签写入两个 `.properties` 文件
- 帮助文本：优先从引擎源码注释提取英文描述，中文待后续补充

---

## 8. 文件清单

### 新增文件（4 个）

| 文件 | 职责 |
|------|------|
| `src/main/java/.../model/DecompilerParameter.java` | 参数定义 record |
| `src/main/java/.../decompiler/CfrParameters.java` | CFR 77 项参数列表 + 常用/高级分类 |
| `src/main/java/.../decompiler/ProcyonParameters.java` | Procyon 21 项参数列表 |
| `src/main/java/.../decompiler/VineflowerParameters.java` | Vineflower 47 项参数列表 + 可读名→原生key映射 |

### 修改文件（4 个）

| 文件 | 变更 |
|------|------|
| `ui/settings/SettingsDialog.java` | 反编译标签页重构：新增子标签 TabPane + 参数面板 + 双向同步 + I18nRefreshable |
| `config/AppConfig.java` | `normalize()` 中为 `engineOptions` 补充默认值初始化 |
| `resources/language/language_zh_CN.properties` | 增量添加参数标签/帮助的中文翻译 |
| `resources/language/language_en.properties` | 增量添加参数标签/帮助的英文翻译 |

### 不修改

- `Decompiler` 接口、四个 Decompiler 实现类（运行时选项合并逻辑已完备）
- `DecompilerOptions.forEngine()`（已正确从 `engineOptions` 提取选项）
- `DecompilerFactory`、`DecompilerContext`、`DecompilerRunner`、`ClassTabOpener`（选项传递链已完备）

---

## 9. 实施顺序

### 第 1 步：数据层
创建 `DecompilerParameter.java` + 三个 `*Parameters.java`，定义全部参数。

### 第 2 步：UI 基础设施
在 `SettingsDialog` 中构建参数面板工厂方法（`createBooleanRow`、`createIntegerRow`、`createEnumRow`、`createStringRow`）。

### 第 3 步：子标签页组装
为每个引擎构建 TitledPane 折叠面板，组装常用/高级参数列表。

### 第 4 步：双向同步
实现面板 ↔ engineOptions Map ↔ JSON TextArea 的读写逻辑。

### 第 5 步：i18n 集成
实现 `I18nRefreshable`，注册到 i18n 系统。在 `.properties` 文件中增量添加翻译。

### 第 6 步：默认值填充
更新 `AppConfig.normalize()`，确保新增参数有合理的初始值。

### 第 7 步：编译验证
`mvn compile -DskipTests` 确保编译通过。

---

## 10. 测试验证

1. **打开设置** → 反编译标签页 → 确认三个子标签存在，参数控件正确
2. **修改参数** → 确认 JSON TextArea 同步更新
3. **手写 JSON** → 失焦后确认面板回填
4. **无效 JSON** → 确认边框标红，面板不变
5. **点击应用** → 重启应用 → 确认参数持久化到 config.json
6. **实际反编译** → 切换参数（如关闭 `decodelambdas`）→ 反编译结果变化
7. **语言切换** → 确认参数标签跟随语言变化
8. **恢复默认** → 确认全部参数重置，JSON 清空
