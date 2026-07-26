# FxDecompiler 架构边界规则

日期: 2026-07-26
版本: 1.0

## 1. 包依赖规则

### 1.1 允许的依赖方向

```
controller → ui, service, model, config, util, decompiler
ui → service, model, config, util, decompiler
service → model, util, decompiler, classgraph (via service/classscan/)
decompiler → model, util, core/jadx (via decompiler/jadx/ adapter)
core/jadx → util, model (轻量级,仅限 constants/io/text 等基础工具)
classgraph → 独立,不依赖主应用任何包
model → 不依赖任何项目包 (纯数据结构)
config → 不依赖 controller/ui (纯配置 POJO)
util → model, constants (无循环依赖)
```

### 1.2 禁止的依赖方向

| 规则编号 | 源包 | 禁止的目标包 | 说明 |
|----------|------|-------------|------|
| R1 | `service/**` | `controller/**` | 业务服务不能依赖控制器 |
| R2 | `service/**` | `ui/**` | 业务服务不能依赖 UI (已有少量例外见白名单) |
| R3 | `core/jadx/**` | `controller/**` | JADX 内核不能依赖控制器 |
| R4 | `core/jadx/**` | `ui/**` | JADX 内核不能依赖 UI |
| R5 | `core/jadx/**` | `service/**` | JADX 内核不能依赖业务服务 |
| R6 | `classgraph/**` | `com.bingbaihanji.fxdecomplie.*` | 扫描内核隔离,不能引用主应用 |
| R7 | `controller/**` | `classgraph/**` | UI 不能直接引用 ClassGraph,必须通过 service/classscan/ |
| R8 | `ui/**` | `classgraph/**` | UI 不能直接引用 ClassGraph,必须通过 service/classscan/ |
| R9 | `model/**` | `controller/**`, `ui/**`, `service/**` | model 不依赖重量级包,但允许 util/decompiler(枚举)/bytecode(元数据) |

## 2. Vendored 内核边界

### 2.1 ClassGraph (`com.bingbaihanji.classgraph`)

- **状态**: 内核维护区
- **访问入口**: 仅通过 `service/classscan/` 包的 DTO 访问
- **禁止**: UI/controller 直接 import classgraph 类型
- **变更要求**: 附带最小可复现测试

### 2.2 JADX (`com.bingbaihanji.fxdecomplie.core.jadx`)

- **状态**: 内核维护区
- **访问入口**: 仅通过 `decompiler/jadx/` 适配层访问
- **禁止**: UI/controller/service 直接 import core/jadx 内部类型
- **变更要求**: 附带最小可复现 class 测试样例

## 3. 白名单（现有违规,待后续阶段消除）

执行 `ArchitectureBoundaryTest` 首次扫描结果:

<!-- 运行架构测试后填充 -->
(待填充)

## 4. 变更流程

1. 新增依赖方向: 先更新本文件,再写代码
2. 白名单消减: 每阶段至少消除 2 条白名单中的违规
3. 架构测试失败: 不是 bug,是违规 — 要么修正 import,要么更新规则并解释原因
