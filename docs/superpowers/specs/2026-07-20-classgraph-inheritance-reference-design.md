# ClassGraph 按需迁移与“继承引用”视图设计文档

## 1. 目标

将 ClassGraph 能力按项目需要迁移到 `com.bingbaihanji.fxdecomplie.core.classgraph`，并基于它升级底部“继承”工具窗口为“继承引用”视图。

核心交互目标：

1. `public class FileServiceImpl implements FileService {}` 中显示 `I FileService`，双击跳转接口。
2. 类头有 `@Service` 时显示 `T Service`，双击跳转注解类。
3. 打开接口 `FileService` 时显示实现类列表，可折叠，双击跳转。

## 2. 设计原则

- **ClassGraph 代码隔离**：所有 ClassGraph 相关代码限制在 `core/classgraph` 包，UI/service/model 不直接依赖完整 ClassGraph 扫描引擎。
- **不替代现有索引**：保留 `WorkspaceIndex` 的轻量索引，`ClassGraph` 仅用于“类关系/注解元数据增强”。
- **按需扫描**：大型 fat jar 不因为打开一个类就全量同步扫描；先展示当前类字节解析出的局部结果，后台补全全局关系。
- **无新 Maven 依赖**：不引入 `io.github.classgraph:classgraph` 依赖，仅迁移最小 API 表面并手动实现缺失方法。
- **生命周期绑定 Workspace**：引用索引随 `Workspace` 创建/关闭/刷新而创建/释放/失效，默认不使用磁盘缓存。

## 3. 总体架构

```
┌─────────────────────────────────────────────────────────────────┐
│ UI                                                              │
│  InheritanceReferencePane                                       │
│  WorkspaceTabManager (替换 InheritancePane)                      │
└───────────────────────┬─────────────────────────────────────────┘
                        │ InheritanceReferenceTree / Node / Group
┌───────────────────────▼─────────────────────────────────────────┐
│ service/reference                                               │
│  InheritanceReferenceService   ← 业务入口                        │
│  InheritanceReferenceIndex     ← 内存索引                        │
│  InheritanceReferenceIndexService ← 异步构建                     │
│  ClassGraphWorkspaceAdapter    ← Workspace → ScanResult          │
└───────────────────────┬─────────────────────────────────────────┘
                        │ ClassInfo / AnnotationInfo (最小子集)
┌───────────────────────▼─────────────────────────────────────────┐
│ core/classgraph                                                 │
│  ClassGraph / ScanResult / ClassInfo / ClassInfoList            │
│  AnnotationInfo / AnnotationInfoList / Resource                 │
└─────────────────────────────────────────────────────────────────┘
```

## 4. `core/classgraph` 最小 API 设计

基于 ClassGraph 4.8.184 的公开 API，仅保留本项目需要的部分，内部实现大幅简化。

### 4.1 包与类清单

| 类 | 来源/说明 |
|---|---|
| `com.bingbaihanji.fxdecomplie.core.classgraph.ClassGraph` | 入口类；配置扫描范围并启动扫描 |
| `com.bingbaihanji.fxdecomplie.core.classgraph.ScanResult` | 扫描结果；持有 `Map<String, ClassInfo>` |
| `com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo` | 类元数据：name、modifiers、superclass、interfaces、annotations、subclasses、implementations |
| `com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfoList` | `List<ClassInfo>` 包装，支持过滤与去重 |
| `com.bingbaihanji.fxdecomplie.core.classgraph.AnnotationInfo` | 注解元数据：name（参数值第二阶段扩展） |
| `com.bingbaihanji.fxdecomplie.core.classgraph.AnnotationInfoList` | 注解列表 |
| `com.bingbaihanji.fxdecomplie.core.classgraph.Resource` | 占位类，用于 `ScanResult.getResourcesWithPath` 等未来扩展 |

### 4.2 `ClassGraph`

```java
public final class ClassGraph {
    public ClassGraph acceptPackages(String... packages);
    public ClassGraph rejectPackages(String... packages);
    public ScanResult scan();
}
```

- 标准 `scan()` 返回空结果或抛出 `ClassGraphException`；本项目不通过 `ClassGraph` 直接扫描 classpath。
- 不实现 classpath/module/classloader 扫描逻辑；workspace 扫描由 `ClassGraphWorkspaceAdapter` 直接构造 `ScanResult`。

### 4.3 `ScanResult`

```java
public final class ScanResult implements Closeable {
    public ClassInfoList getAllClasses();
    public ClassInfo getClassInfo(String name);
    public ClassInfoList getClassesWithAnnotation(String annotationName);
    @Override public void close();
}
```

- 内部持有 `Map<String, ClassInfo>`。
- `close()` 释放对 `WorkspaceByteSource` 的引用。

### 4.4 `ClassInfo`

```java
public final class ClassInfo implements Comparable<ClassInfo> {
    public String getName();
    public String getPackageName();
    public boolean isInterface();
    public boolean isAnnotation();
    public boolean isAbstract();
    public boolean isPublic();
    public ClassInfo getSuperclass();
    public ClassInfoList getInterfaces();
    public ClassInfoList getSubclasses();
    public ClassInfoList getImplementingClasses(); // 若自身是接口
    public AnnotationInfoList getAnnotationInfo();
    public ClassInfoList getAnnotations();
}
```

- `getSubclasses()` / `getImplementingClasses()` 在扫描完成后由 `ClassGraphWorkspaceAdapter` 反向填充。
- 不实现字段/方法/类型签名等高级 API。

### 4.5 `AnnotationInfo`

```java
public final class AnnotationInfo {
    public String getName();
}
```

- 第一阶段仅返回注解类名；参数值与元注解在第二阶段扩展。

## 5. `service/reference` 适配与索引

### 5.1 `ClassGraphWorkspaceAdapter`

职责：将 `Workspace` / `WorkspaceIndex` 中的 class bytes 转换为 `ScanResult`。

输入：

- `Workspace`（提供 `WorkspaceIndex`、文件树）
- 可选 `Predicate<String> classFilter`（按包过滤）

处理流程：

1. 遍历 `WorkspaceIndex.classes()` 获取所有 `ClassIndexEntry`。
2. 对每个 entry 读取 bytes（优先 `node.readBytes()`，避免污染缓存）。
3. 使用 ASM `ClassReader` + 自定义 `ClassVisitor` 提取：
   - className、access、superName、interfaces
   - `RuntimeVisibleAnnotations` / `RuntimeInvisibleAnnotations` 中的注解名
4. 创建 `ClassInfo` 并放入 `ScanResult`。
5. 第二遍遍历建立反向关系：
   - superclass → subclasses
   - interface → implementing classes
   - annotation → annotated classes
6. 建立 `internalName → fullPath` 映射表，供后续跳转使用。

边界：

- 普通 jar：`fullPath = "com/example/Foo.class"`
- Spring Boot nested jar：`fullPath = "demo.jar:BOOT-INF/lib/nested.jar:com/example/Foo.class"`
- 目录：与 jar 一致，前缀为相对路径。

### 5.2 `InheritanceReferenceIndex`

内存索引结构：

```java
public final class InheritanceReferenceIndex {
    private final ScanResult scanResult;
    private final Map<String, String> internalNameToFullPath;
    private final Map<String, List<String>> interfaceToImplementations;
    private final Map<String, List<String>> superclassToSubclasses;
    private final Map<String, List<String>> annotationToAnnotatedClasses;
}
```

API（只读）：

```java
public ClassInfo classInfo(String internalName);
public List<String> implementationsOf(String interfaceName);
public List<String> subclassesOf(String superclassName);
public List<String> annotatedBy(String annotationName);
public String fullPathOf(String internalName);
public boolean containsClass(String internalName);
```

### 5.3 `InheritanceReferenceIndexService`

职责：按 `Workspace` 异步、幂等地构建/刷新引用索引。

实现：

```java
public final class InheritanceReferenceIndexService {
    private static final WeakHashMap<Workspace, IndexState> STATES = new WeakHashMap<>();

    public static InheritanceReferenceIndex getOrStart(Workspace workspace);
    public static InheritanceReferenceIndex getIfReady(Workspace workspace);
    public static void invalidate(Workspace workspace);
}
```

- `IndexState` 持有 `CompletableFuture<InheritanceReferenceIndex>` 与构建任务 `Future<?>`。
- 多次调用 `getOrStart` 不重复提交任务；关闭 workspace 后 `WeakHashMap` 可释放。
- 后台任务使用 `BackgroundTasks.run(BackgroundTasks.PoolType.IO, ...)`，低优先级。

### 5.4 `InheritanceReferenceService`

业务入口：

```java
public final class InheritanceReferenceService {
    public InheritanceReferenceTree buildTree(Workspace workspace,
                                              String fullPath,
                                              byte[] currentClassBytes);
}
```

构建逻辑：

1. **解析当前类字节**：使用 `ClassFileParser` 获取 `internalName`、`superName`、`interfaces`、注解（需扩展解析）。
2. **立即输出局部结果**：
   - 当前类 `SELF`
   - 父类 `SUPER_CLASS`
   - 直接接口 `INTERFACE`
   - 直接注解 `ANNOTATION`
3. **查询全局索引**：
   - 若索引已就绪，补全 `IMPLEMENTATION`、`SUBCLASS`。
   - 若未就绪，标记 `partial = true`，后台启动 `InheritanceReferenceIndexService.getOrStart(workspace)`，完成后通知 UI 刷新。
4. **路径解析**：每个节点通过 `internalNameToFullPath` 或 `WorkspaceIndex.findClass()` 找到 `fullPath`；找不到则 `navigable = false`。

## 6. `model/reference` UI 模型

### 6.1 类型

```java
public enum Kind {
    SELF, SUPER_CLASS, INTERFACE, ANNOTATION, IMPLEMENTATION, SUBCLASS, UNRESOLVED
}
```

### 6.2 记录

```java
public record InheritanceReferenceNode(
        String className,
        String displayName,
        Kind kind,
        String fullPath,
        int depth,
        boolean navigable
) {}

public record InheritanceReferenceGroup(
        Kind kind,
        String title,
        List<InheritanceReferenceNode> children,
        boolean collapsible
) {}

public record InheritanceReferenceTree(
        InheritanceReferenceNode root,
        List<InheritanceReferenceGroup> groups,
        boolean partial,
        String statusMessage
) {}
```

### 6.3 显示前缀

| Kind | 前缀 |
|---|---|
| `SELF` | 无 |
| `SUPER_CLASS` | `S` |
| `INTERFACE` | `I` |
| `ANNOTATION` | `T` |
| `IMPLEMENTATION` | `↓` |
| `SUBCLASS` | `↓` |
| `UNRESOLVED` | 无（灰色） |

## 7. UI 改造

### 7.1 命名与语言资源

更新 `src/main/resources/language/*.properties`：

```properties
# zh_CN
tab.inheritance=继承引用
inheritance.title=继承引用
inheritance.status.partial=局部结果
inheritance.status.building=正在补全引用...
inheritance.status.ready=索引完成
inheritance.status.failed=加载失败，点击刷新重试
inheritance.group.self=当前类
inheritance.group.super=父类
inheritance.group.interfaces=接口
inheritance.group.annotations=注解
inheritance.group.implementations=实现类
inheritance.group.subclasses=子类
inheritance.group.overflow=还有 {0} 个结果，使用搜索查看全部
inheritance.refresh.tooltip=刷新继承引用索引
```

英文文件对应 `References` / `Inheritance References`。

### 7.2 `InheritanceReferencePane`

布局：

- 顶部标题栏：左侧 `inheritance.title`，右侧刷新按钮。
- 中间 `TreeView<InheritanceReferenceNode>`。
- 底部状态标签：展示 `partial` / `statusMessage`。

TreeView 结构：

- 根节点固定为当前类（`SELF`）。
- 子节点按 `InheritanceReferenceGroup` 分组：
  - 父类
  - 接口
  - 注解
  - 实现类
  - 子类
- 实现类/子类分组默认折叠；其余默认展开。

交互：

- 双击非根节点触发 `OpenHandler.open(node)`，携带 `fullPath`。
- 刷新按钮调用 `InheritanceReferenceIndexService.invalidate(workspace)` 并重新加载当前类。
- 切换 tab 时丢弃过期异步结果（使用 `AtomicLong loadGeneration`）。

### 7.3 `WorkspaceTabManager` 改动

- 将 `InheritancePane` 替换为 `InheritanceReferencePane`。
- 更新 `refreshToolWindowsForTab` 与 `refreshInheritancePane` 调用新 pane 的方法。
- 打开处理器使用 `workspace.findNodeByPath(fullPath)` → `WorkspaceIndex.findClass(className)` → 后缀匹配 的降级逻辑。
- 找不到时状态栏提示，不弹阻断对话框。

## 8. 跳转逻辑

可跳转节点携带 `className` 与 `fullPath`。

跳转顺序：

1. `workspace.findNodeByPath(fullPath)`（精确匹配 nested jar 路径）。
2. `workspace.getIndex().findClass(className)`（按 internalName 或别名）。
3. 后缀/简名匹配（当前类所在模块/嵌套 jar 优先，其次主应用类，最后外部依赖）。
4. 找不到时：`statusBar.setFilePath(I18nUtil.getString("status.locateFailed", className))`，不弹窗。

## 9. 测试策略

### 9.1 测试类

- `ClassGraphWorkspaceAdapterTest`
  - 普通 jar 场景：类、接口、注解正确解析。
  - Spring Boot nested jar 场景：`fullPath` 能正确回溯。
  - 目录输入场景。
- `InheritanceReferenceIndexTest`
  - 类实现接口 / 接口反查实现类。
  - 类注解 / 注解反查被注解类。
  - 同名类冲突时优先当前模块。
- `InheritanceReferenceServiceTest`
  - 无索引时输出局部结果。
  - 索引完成后输出完整结果。
  - 刷新后索引重建。

### 9.2 测试样本

使用运行时编译生成样本类（参考现有 `InheritanceServiceTest`），补丁 major version 到 69（Java 25）。

```java
// FileService.java
package com.example;
public interface FileService {}

// FileServiceImpl.java
package com.example;
import org.springframework.stereotype.Service;
@Service
public class FileServiceImpl implements FileService {}
```

对 `@Service` 使用自定义占位注解类，无需真实 Spring 依赖。

## 10. 风险与缓解

| 风险 | 缓解 |
|---|---|
| ClassGraph 子集迁移后 API 不完整 | 仅实现视图需要的 getter；缺失方法手动抛 `UnsupportedOperationException` 并在调用处规避 |
| Annotation 解析覆盖不全 | 第一阶段仅解析类头直接注解；元注解后续扩展 |
| 大型 workspace 索引慢 | 后台低优先级任务 + 超时保护 + 即时局部结果 |
| 同名类在不同 nested jar 中 | 跳转始终携带 `fullPath`，优先精确匹配 |
| 与现有 `WorkspaceIndex` 冲突 | `InheritanceReferenceIndex` 作为独立增强索引，不修改 `WorkspaceIndex` |

## 11. 推荐实施顺序

1. 创建 `core/classgraph` 最小模型类并确保编译。
2. 实现 `ClassGraphWorkspaceAdapter` 与测试。
3. 实现 `InheritanceReferenceIndex` / `InheritanceReferenceIndexService` 与测试。
4. 实现 `InheritanceReferenceService` 与测试。
5. 创建 `InheritanceReferencePane` 并替换 `WorkspaceTabManager` 中的旧 pane。
6. 更新语言资源。
7. 跑 `mvn test` 与 `mvn -DskipTests package` 验收。

## 12. 完成标准

- `FileServiceImpl implements FileService` 场景下显示 `I FileService`，双击跳转成功。
- `@Service` 场景下显示 `T Service`，双击跳转成功。
- 打开 `FileService` 接口时显示 `FileServiceImpl` 实现类列表，可折叠，双击跳转成功。
- 大型 Spring Boot fat jar 中视图首屏不阻塞全量索引。
- 全量引用索引只构建一次，刷新按钮可重建。
- 所有新增逻辑有服务层单测覆盖，项目可编译打包。
