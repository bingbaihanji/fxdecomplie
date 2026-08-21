# UML 类图功能设计

- 日期：2026-08-21
- 状态：已获用户批准，待实现

## 1. 背景与目标

FxDecompiler 是字节码反编译桌面工具。需要新增 IDEA 风格的 **UML 类图** 视图：查看类的继承/实现关系、类中属性字段与方法成员，支持暗色/亮色主题与导出。

参考项目：`D:\javaProject\github\uml-parser\class-diagram-gen`（基于 JavaParser + PlantUML 的 CLI 工具）。本项目**借鉴其 IDEA 风格视觉语言与关系判定启发式、以及 PlantUML 源码生成逻辑**，但数据源改为 **ASM 直接解析字节码**、渲染走 **纯 JavaFX**，与 FxDecompiler 既有架构保持一致。

### 关键约束（与参考项目的差异）

- 参考项目解析 Java 源码（JavaParser）+ 依赖 jar（Javassist）；本工具面向 **class 字节码**（JAR/class/目录），数据源为 `WorkspaceIndex`（按 internalName 取字节 + 父类/接口/成员元数据）。
- 渲染不引入 PlantUML 依赖（避免 ~3MB 依赖 + `--add-opens java.awt` 运行时要求 + 位图非交互展示），沿用项目"纯 JavaFX 渲染"的既有决策。
- 不新增任何第三方依赖（ASM、javafx-swing 已在 pom 中）。

## 2. 范围

### 图范围（用户已确认：继承闭包 + 关联类）

从目标类 BFS 展开：

- 父类链（extends）→ `GENERALIZATION`
- 实现的接口（implements）→ `REALIZATION`
- 字段元素类型引用的类 → `ASSOCIATION`（集合/数组带 `0..*` 多重性）
- 方法参数元素类型引用的类 → `DEPENDENCY`
- 子类（反向：通过 `WorkspaceIndex.subclassesByParent()` 向下找）

能解析的类显示完整成员；JDK 类型不建节点；解析不到的类渲染为「仅类名」标记框。

### 导出格式（用户已确认：PNG + PlantUML 源码）

- **PNG**：JavaFX 画布快照导出，所见即所得。
- **PlantUML 源码（.puml）**：生成文本，供用户用 PlantUML 工具二次渲染/编辑。应用内不渲染该格式。

### 主题（用户要求：暗色亮色）

- 内置亮/暗两套色板（颜色取自参考项目 skinparam）。
- 弹窗内 Light/Dark 切换，默认跟随应用主题（当前应用为暗色）。

## 3. 架构

### 3.1 新包 `ui/uml/`（镜像 `ui/inheritance/` 模式：服务 + UI 同包）

| 组件 | 职责 |
|---|---|
| `UmlClass` / `UmlField` / `UmlMethod` / `UmlRelation` / `UmlDiagram` | 不可变模型 records |
| `ClassKind` / `Visibility` / `RelationType` | 枚举 |
| `UmlClassExtractor` | ASM `ClassVisitor`：字节码 → `UmlClass` |
| `UmlDiagramBuilder` | BFS 闭包：目标类 → `UmlDiagram`（含节点/深度上限保护） |
| `UmlTypeNameUtil` | 类型名判定工具（移植 `UMLHelper`） |
| `UmlDiagramRenderer` | 纯 JavaFX 渲染结构化模型（分栏类框、箭头、缩放） |
| `UmlTheme` | 亮/暗色板 record |
| `PumlExporter` | `UmlDiagram` → `.puml` 文本（移植 `GenerateUML.buildSource`） |
| `UmlClassDiagramDialog` | 弹窗壳：加载态 / 主题切换 / 导出按钮 / 交互画布 |

### 3.2 数据流

1. 代码编辑器右键菜单「UML 类图」→ `CodeActionHandler.showUmlClassDiagram(context)` → `EngineController.showUmlClassDiagram(CodeViewContext)`。
2. `UmlClassDiagramDialog.show()` 进入加载态。
3. 后台任务（`BackgroundTasks`）：取目标类字节 → `UmlDiagramBuilder.build()` 构建 `UmlDiagram`。
4. FX 线程：`UmlDiagramRenderer.render(diagram, theme)` → 显示画布，启用导出按钮。
5. 导出：PNG（画布快照）/ .puml（`PumlExporter`）→ `FileChooser` 保存。

## 4. 数据模型

```java
enum ClassKind { CLASS, INTERFACE, ENUM, ANNOTATION }
enum Visibility { PUBLIC('+'), PROTECTED('#'), PACKAGE('~'), PRIVATE('-') }
enum RelationType { GENERALIZATION, REALIZATION, ASSOCIATION, DEPENDENCY }

record UmlField(String name, String type, Visibility visibility, boolean isStatic) {}
record UmlMethod(String name, List<String> params, String returnType,
                 Visibility visibility, boolean isStatic, boolean isAbstract,
                 boolean isConstructor) {}
record UmlClass(String internalName, String packageName, String simpleName,
                ClassKind kind, boolean isAbstract, boolean parsed,
                List<UmlField> fields, List<UmlMethod> methods,
                String superName, List<String> interfaces,
                List<String> annotationNames) {}
record UmlRelation(String from, String to, RelationType type,
                   String fromCardinality, String toCardinality) {}
record UmlDiagram(String target, List<UmlClass> classes, List<UmlRelation> relations) {}
```

- `parsed=false` 表示解析不到的类 → 标记框（仅类名，无成员）。
- 多重性：关联字段为数组/集合时，`toCardinality = "0..*"`。

## 5. 提取与闭包

### 5.1 `UmlClassExtractor`

`ClassReader.accept(visitor)` 提取：

- 类类型 / abstract：access flags（`ACC_INTERFACE` / `ACC_ENUM` / `ACC_ANNOTATION` / `ACC_ABSTRACT`）。
- 字段 / 方法：名称、描述符 → 可读类型（优先 `signature` 泛型签名）；`ACC_PUBLIC/PROTECTED/PRIVATE/STATIC/ABSTRACT` → `Visibility` 与标记。
- `superName` / `interfaces` / 类注解（`RuntimeVisible` + `RuntimeInvisible`）。

### 5.2 `UmlDiagramBuilder.build(targetInternalName, bytes, WorkspaceIndex)`

1. 队列从目标类开始；每出队一个类：`UmlClassExtractor` 解析 → 加入 `classes`（按 internalName 去重）。
2. 收集关系：
   - superclass → `GENERALIZATION`
   - interfaces → `REALIZATION`
   - 字段元素类型（非 JDK、可解析）→ `ASSOCIATION`（集合/数组带 `0..*`）
   - 方法参数元素类型（非 JDK）→ `DEPENDENCY`
   - 子类（经 `WorkspaceIndex.subclassesByParent()`）→ `GENERALIZATION`（反向）
3. 关系目标处理：JDK 类型跳过不建节点；`WorkspaceIndex.getClassBytes()` 命中 → 入队展开完整成员；未命中 → 标记框。
4. 保护（沿用项目 counter + deadline 模式）：`MAX_CLASSES=100`、`MAX_DEPTH=20`，超限截断并提示。

### 5.3 `UmlTypeNameUtil`

移植 `UMLHelper`：

- `getElementType(type)`：剥 `[]`；取泛型最后一个顶层参数；`? extends X`/`? super X` 归一化。
- `isCollectionOrArray(type)`：`[]` 结尾或含 `<`。
- `isUMLClassType(type)`：JDK 核心类黑名单 + `java./javax./sun./com.sun.` 前缀判定。

## 6. 渲染与主题

### 6.1 `UmlDiagramRenderer`（纯 JavaFX）

复用 `DotGraphRenderer` 的分层布局思路，但独立实现（分栏类框尺寸估算不同）：

- 每个 `UmlClass` 渲染为**分栏类框**：顶部标题栏（类名加粗 + 灰色包名）、字段栏、方法栏（`--` 分隔）。
  - 可见性前缀 `+ - # ~`；static 成员下划线；abstract 方法斜体；构造器不显示返回类型。
  - 类/接口/枚举/注解头部不同底色；`parsed=false` 标记框单一柔和色、无成员栏。
- 布局：BFS 层级（按关系图距离目标类），`rankdir=BT`（继承向上）；连线截断到类框边框。
- 箭头按关系类型区分：继承=实线+空心三角；实现=虚线+空心三角；关联=实线+多重性标签；依赖=虚线箭头。
- 交互：Ctrl+滚轮缩放（0.35–2.5×），ScrollPane 平移。
- 节点尺寸按分栏行数估算（标题+字段+方法），长标签折行，防止超高。

### 6.2 `UmlTheme`

亮/暗两套色板（颜色取自参考项目 skinparam）：

- `LIGHT`：背景 `#FFFFFF`、边框 `#A0A7B3`、头部 `#EDF2FA`（类）/`#E9F4EC`（接口）/`#FDF3E0`（枚举）/`#F3E9FA`（注解）、箭头 `#44546A`。
- `DARK`：背景 `#1E1E1E`（对齐项目 VS Code 暗色）、头部 `#4B5053` 系、箭头 `#9A9A9A`。
- 弹窗内 Light/Dark 切换，默认跟随应用主题。

## 7. 导出

### 7.1 PNG

`canvas.snapshot(SnapshotParameters, WritableImage)` → `SwingFXUtils.fromFXImage` → `ImageIO.write(..., "png", file)`（javafx-swing 已在依赖中）。全画布 1:1 捕获。

### 7.2 PlantUML 源码

`PumlExporter.buildSource(diagram, theme)`：移植 `GenerateUML.buildSource`：

- `@startuml` + `top to bottom direction` + 亮/暗 `skinparam`。
- 类/接口/枚举/注解关键字；`**类名**\n<size:10>包名` 标题；头部配色（`#back:...;header:...`）。
- 字段/方法行：可见性符号、`{static}`/`{abstract}`、`--` 分隔。
- 四类关系箭头（继承实线 `[#000082,plain]` 等）+ 多重性；注解黄点线。
- 未解析标记框用单一柔和色。

## 8. 集成入口

- **代码编辑器右键菜单**（`CodeAreaContextMenu`）：在「继承图」下方新增「UML 类图」项 → `CodeActionHandler.showUmlClassDiagram(context)`（接口新增方法）→ `EngineController.showUmlClassDiagram(CodeViewContext)`。
- 控制器流程镜像 `showInheritanceGraph`：`dialog.show()` 加载态 → `BackgroundTasks.run` 取类字节（`classBytesForContext`）+ 构建 `UmlDiagram` → `Platform.runLater` 渲染 + 开启导出。
- 新 UI 字符串同步进 `language_zh_CN.properties` / `language_en.properties`。

## 9. 错误处理

- 目标类字节为空 → 弹窗错误提示（复用 `showGraphFailed` 模式）。
- 无任何类/关系 → 「无图形数据」占位。
- 单类解析失败 → 降级为标记框，不中断整体。
- 导出失败 → 弹窗状态栏提示。

## 10. 测试

- `UmlTypeNameUtilTest`：移植 `UMLHelper` 测试（元素类型提取、集合检测、JDK 判定）。
- `UmlClassExtractorTest`：ASM 生成的样本类（含接口/枚举/注解/泛型字段）→ 校验字段/方法/可见性/static/abstract。
- `UmlDiagramBuilderTest`：继承链 + 接口实现 + 集合关联（`0..*`）+ 方法参数依赖 + JDK 跳过 + 未解析标记框 + 节点上限。
- `PumlExporterTest`：小图 → 校验 skinparam、类声明、关系行。
- 渲染器不写 FX 线程测试，仅保证模型可纯逻辑构建。
