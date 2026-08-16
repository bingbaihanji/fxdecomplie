# bdec 反编译引擎集成 — 设计文档

日期: 2026-08-17
来源: 用户自研 bdec 引擎开源仓库 github.com/bingbaihanji/bdec
状态: 已确认

## 1. 目标

将自研 bdec 反编译引擎集成到 FxDecompiler 作为第六个反编译引擎，支持通过 Git subtree 一键拉取 bdec 最新更新。

## 2. bdec 项目概况

- 独立 Maven 项目: `com.bingbaihanji.bdec:bdec:1.0.0`，Java 25，Apache 2.0
- 361 个 Java 文件，包路径 `com.bingbaihanji.bdec`
- 主代码零外部依赖（仅 test 依赖 junit）
- 嵌入式 API:
  - `BdecEngine implements com.bingbaihanji.bdec.decompiler.Decompiler`
  - `BdecEngine.decompile(String internalName, byte[] bytes, DecompileContext ctx)` → `BdecResult`
  - `BdecConfig.builder()` 类型化配置
  - `DecompileContext(BdecConfig, Function<String,byte[]> classByteLoader)` 依赖解析

## 3. 集成架构

```
bdec 仓库 (github.com/bingbaihanji/bdec)
  ├── main 分支 (完整项目: pom/test/README)
  └── main-src 分支 (仅 src/main/java 源码, GitHub Actions 自动生成)
              │  git subtree pull
              ▼
fxdecomplie 仓库
  ├── src/main/java/com/bingbaihanji/bdec/     ← bdec 源码 (vendored, 平铺无嵌套)
  └── decompiler/
      ├── DecompilerTypeEnum    ← 加 BDEC
      ├── BdecDecompiler.java    ← 适配器 (包装 bdec BdecEngine)
      └── bdec/BdecParameters.java ← 引擎参数定义
```

核心映射:

```
fxdecomplie Decompiler.decompileType(name, bytes, ctx)
  → 构造 bdec.DecompileContext(classByteLoader = ctx::getClassBytes)
  → bdec.BdecEngine.decompile(name, bytes, bdecCtx)
  → 返回 BdecResult.decompiledCode()
```

## 4. Subtree 同步机制

### 4.1 一次性设置 (bdec 仓库)

```bash
git subtree split --prefix=src/main/java -b main-src
git push origin main-src
```

### 4.2 GitHub Actions (bdec 仓库，每次 main 更新自动重建)

```yaml
# .github/workflows/main-src.yml
on: { push: { branches: [main] } }
jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - run: |
          git subtree split --prefix=src/main/java -b main-src
          git push -f origin main-src
```

### 4.3 首次集成 (fxdecomplie)

```bash
git remote add bdec https://github.com/bingbaihanji/bdec.git
git subtree add --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src --squash
```

### 4.4 后续更新

```bash
git subtree pull --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src
```

## 5. 适配层

### 5.1 BdecDecompiler 适配器

文件: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/BdecDecompiler.java`

- implements fxdecomplie `Decompiler`
- `decompileType(typeName, bytes, ctx)`:
  1. 构建 `BdecConfig.builder().build()`
  2. 构造 bdec `DecompileContext(config, internalName -> ctx.getClassBytes(internalName))`
  3. 调用 `new BdecEngine(config, DiagnosticListener.noop()).decompile(typeName, bytes, bdecCtx)`
  4. `BdecResult.success()` 为 true → 返回 `decompiledCode()`；否则返回 `// Bdec Error: ...` 文本
- `getType()` → `DecompilerTypeEnum.BDEC`
- `getName()` → `"bdec"`

### 5.2 BdecParameters

文件: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/bdec/BdecParameters.java`

从 `BdecConfig` 配置项映射参数:
- indentSize, lineSeparator, showLineNumbers, showBytecodeOffsets
- decodeEnums, decodeLambdas, decodeTernary, decodeStringConcat
- decodeTryResource, decodeForEach, decodeStringSwitch, collapseImports
- ssaThreshold

### 5.3 注册点修改

| 文件 | 修改 |
|------|------|
| `DecompilerTypeEnum` | 加 `BDEC` 枚举值 |
| `DecompilerFactory.getDecompiler()` | switch 加 `case BDEC -> new BdecDecompiler()` |
| `EngineParameters.REGISTRY` | 加 `BDEC -> BdecParameters.PARAMETERS` |
| `architecture-boundaries.md` | 标记 `com.bingbaihanji.bdec` 为「内核维护区」 |
| `THIRD-PARTY-NOTICES.txt` | 加 bdec (Apache 2.0) |
| `AGENTS.md` | 更新技术栈表 + 项目结构 |

## 6. 重名冲突处理

bdec 自带 `com.bingbaihanji.bdec.decompiler.Decompiler`，与 fxdecomplie 的 `Decompiler` 重名:
- 适配器 import fxdecomplie 的 `Decompiler`（正常使用）
- bdec 的 `Decompiler` 用全限定名

## 7. 测试

- `decompiler/bdec/BdecDecompilerTest.java`:
  - 反编译简单 class → 源码非空且含类名
  - 非法字节 → 返回 `// Bdec Error:` 文本
  - `DecompilerFactory.getDecompiler(BDEC)` 非空

## 8. 不做的事

1. 不修改 bdec 内核源码（只在适配层解决集成需求）
2. 不把 bdec 的 test 源码带入 fxdecomplie（由 bdec 仓库自行维护）
3. 不为 bdec 新增独立 UI 入口（作为引擎自然出现在引擎列表）
