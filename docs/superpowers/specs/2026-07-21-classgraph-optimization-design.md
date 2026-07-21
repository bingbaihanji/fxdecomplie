# ClassGraph 移植代码完善优化 — 设计文档

> 创建日期: 2026-07-21
> 状态: 已批准

## 目标

完善优化 `com.bingbaihanji.classgraph` 包（ClassGraph 4.8.184 移植代码），聚焦代码质量和性能健壮性。

## 背景

`com.bingbaihanji.classgraph` 是从 ClassGraph 4.8.184 完整移植的 Java 类路径扫描库，包含 131 个 Java 文件、约 60,000+ 行代码。它被 `com.bingbaihanji.fxdecomplie.service.reference` 包使用，为继承引用分析系统提供 `ScanResult`、`ClassInfo` 等核心数据结构。

## 不改动范围

- **许可证头** — 保持每文件 28 行 MIT 许可证原文不变
- **代码风格** — 保持原 ClassGraph 库的风格（`final` 修饰符、格式等），利于未来合并上游更新
- **编译器警告** — 不主动消除（多为上游遗留，非新增问题）
- **无用 handler 移除** — 不删除服务器级 ClassLoaderHandler

## 改动项

### 1. ASM 解析合并（性能优化）

**现状：** 类字节码被 ASM `ClassReader.accept()` 解析两次：
- `ClassGraphWorkspaceAdapter.MetadataVisitor` — 类名、父类、接口、注解
- `InheritanceReferenceService.parseAnnotations()` — 注解（匿名 ClassVisitor，重复！）

**方案：** 扩展 `MetadataVisitor` 同时收集方法级信息（方法名、描述符、访问标志），通过 `ClassInfo.getMethodInfo()` 暴露。`InheritanceReferenceService` 改为从 `InheritanceReferenceIndex` 读取已有数据，删除重复的 ASM 解析。

**影响文件：**
- `ClassGraphWorkspaceAdapter.java` — MetadataVisitor 增加方法收集 + sourceFile
- `InheritanceReferenceService.java` — parseAnnotations() 改为索引查找，删除匿名 ClassVisitor

**预期效果：** 大型工作区（数千类）减少约 50% 的 ASM 解析开销。

### 2. ClassLoaderHandler 接口重构（健壮性）

**现状：**
```java
public interface ClassLoaderHandler {}  // 空标记接口
```
所有 handler 的 `canHandle`、`findClassLoaderOrder`、`findClasspathOrder` 为静态方法，`ClassLoaderHandlerRegistry` 通过反射（`getDeclaredMethod().invoke()`）调用。

**问题：** 无编译期类型检查；添加新 handler 易出错；反射有性能开销。

**方案：** 接口添加 3 个 `default` 方法：
```java
public interface ClassLoaderHandler {
    default boolean canHandle(Class<?> classLoaderClass, LogNode log) { return false; }
    default void findClassLoaderOrder(ClassLoader cl, ClassLoaderOrder order, LogNode log) {}
    default void findClasspathOrder(ClassLoader cl, ClasspathOrder order, ScanSpec spec, LogNode log) {}
}
```

18 个 handler 实现类改为 `@Override` 实例方法（逻辑不变）。`ClassLoaderHandlerRegistry.ClassLoaderHandlerRegistryEntry` 从反射改为直接实例方法调用（通过 `ClassLoaderHandler` 实例）。

**影响文件：**
- `ClassLoaderHandler.java` — 添加 3 个 default 方法
- 18 个 handler 实现类 — 静态方法改为实例方法，加 `@Override`
- `ClassLoaderHandlerRegistry.java` — 构造函数改为接收 `ClassLoaderHandler` 实例，消除反射

### 3. 异常处理与日志增强（健壮性）

**现状问题位置：**

| 位置 | 当前 | 问题 |
|------|------|------|
| `ClassGraphWorkspaceAdapter.readBytes()` | `log.debug(...)` | IOException 被静默跳过 |
| `ClassGraphWorkspaceAdapter.parse()` | `log.debug(...)` | ASM 解析失败静默返回 null |
| `InheritanceReferenceService.parseAnnotations()` | `catch (Exception ignored) {}` | 空 catch，异常完全丢失 |

**方案：**
- `readBytes()` IOException → `log.warn("Failed to read class bytes: {}", fullPath, e)`
- `parse()` 异常 → `log.warn("ASM parse failed for: {}", fullPath, e)`
- `parseAnnotations()` 空 catch → `log.debug("Annotation parse skipped", e)`
- `ScanResult` 新增 `getSkippedFileCount()` 方法，累加跳过的文件数，供调用方感知

**影响文件：**
- `ClassGraphWorkspaceAdapter.java` — 日志级别提升
- `InheritanceReferenceService.java` — 空 catch 增加日志
- `ScanResult.java` — 新增 skippedFileCount 字段和 getter

### 4. 代码风格微调（质量）

- `ClassLoaderHandlerRegistry` 移除不必要的 `@SuppressWarnings("null")` 注解
- 其他保持原库风格不变

## 测试验证

- 运行 `mvn test -Dtest=ClassGraphWorkspaceAdapterTest,InheritanceReferenceIndexTest,InheritanceReferenceServiceTest` 确保现有测试通过
- 运行 `mvn test` 确保无回归
- 运行 `mvn -DskipTests package` 确保打包成功

## 自检清单

- [x] 无 TBD/TODO 未填内容
- [x] 所有改动项有明确的文件和方案描述
- [x] 不改动范围明确
- [x] 测试验证步骤清晰
- [x] 不与现有 CLAUDE.md 规范冲突
