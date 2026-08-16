# bdec 反编译引擎集成 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将自研 bdec 反编译引擎通过 Git subtree 集成到 FxDecompiler 作为第六个反编译引擎

**Architecture:** bdec 仓库维护 `main-src` 分支（仅含 src/main/java 源码），fxdecomplie 用 `git subtree pull` 拉取到 `src/main/java/com/bingbaihanji/bdec`。适配器 `BdecDecompiler` 桥接 fxdecomplie `Decompiler` 接口与 bdec `BdecEngine`。

**Tech Stack:** Java 25, Maven, Git subtree, bdec 1.0.0, JUnit 5, ASM 9.9

---

## 前置条件

- bdec 本地仓库: `D:\bingbaihanji\fxdecomplie\bdec\bdec`（已有 main 分支，remote = github.com/bingbaihanji/bdec）
- bdec 嵌入式 API 已确认:
  - `BdecEngine(BdecConfig config, DiagnosticListener diagnostics)`
  - `BdecEngine.decompile(String internalName, byte[] classBytes, DecompileContext ctx)` → `BdecResult`
  - `BdecConfig.builder().build()` → `BdecConfig`
  - `bdec.DecompileContext(BdecConfig, Function<String,byte[]> classByteLoader)`
  - `BdecResult(success, decompiledCode, cause, warnings, sourceLineToBytecodeOffset)`
  - `DiagnosticListener` 是 `@FunctionalInterface`（`diag -> {}` 即 noop）

---

## Task 1: 创建 bdec main-src 分支 + GitHub Actions 自动同步

**Files:**
- Create: `D:\bingbaihanji\fxdecomplie\bdec\bdec\.github\workflows\main-src.yml`

- [ ] **Step 1: 在 bdec 本地仓库创建 main-src 分支**

```bash
cd D:/bingbaihanji/fxdecomplie/bdec/bdec
git subtree split --prefix=src/main/java -b main-src
```

Expected: 创建 main-src 分支，根目录包含 bdec 的 main 源码（`com/bingbaihanji/bdec/**`）

- [ ] **Step 2: 创建 GitHub Actions workflow**

`D:\bingbaihanji\fxdecomplie\bdec\bdec\.github\workflows\main-src.yml`:

```yaml
name: Sync main-src branch

on:
  push:
    branches: [ main ]

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      - name: Rebuild main-src from src/main/java
        run: |
          git subtree split --prefix=src/main/java -b main-src
          git push -f origin main-src
```

- [ ] **Step 3: 提交 workflow 到 bdec 仓库**

```bash
cd D:/bingbaihanji/fxdecomplie/bdec/bdec
git add .github/workflows/main-src.yml
git commit -m "ci: 自动生成 main-src 分支 (供 FxDecompiler subtree 拉取)"
```

- [ ] **Step 4: 推送 main-src 分支和 workflow 到 GitHub**

```bash
cd D:/bingbaihanji/fxdecomplie/bdec/bdec
git push origin main
git push -f origin main-src
```

> 注意：如果 GitHub 凭据不可用，此步骤由用户手动执行。subtree 集成仍可用本地 main-src 分支完成（Task 2）。

---

## Task 2: subtree 集成 bdec 源码到 fxdecomplie

**Files:**
- (subtree 一次性操作，产生 `src/main/java/com/bingbaihanji/bdec/**`)

- [ ] **Step 1: 在 fxdecomplie 添加 bdec remote**

```bash
cd D:/bingbaihanji/fxdecomplie/fxdecomplie
git remote add bdec D:/bingbaihanji/fxdecomplie/bdec/bdec
```

- [ ] **Step 2: subtree add bdec main-src**

```bash
git subtree add --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src --squash
```

Expected: 成功添加提交，`src/main/java/com/bingbaihanji/bdec/` 下出现 bdec 的 main 源码（平铺，无嵌套）

- [ ] **Step 3: 验证源码存在**

```bash
ls src/main/java/com/bingbaihanji/bdec/
```

Expected: 包含 `App.java`、`BdecEngine.java`、`BdecConfig.java`、`BdecResult.java`、`ast/`、`bytecode/` 等

---

## Task 3: 反编译引擎类型注册

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerTypeEnum.java`

- [ ] **Step 1: 添加 BDEC 枚举值**

```java
    /** jadx 反编译器 */
    JADX,
    /** bdec 反编译器 (自研) */
    BDEC
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerTypeEnum.java
git commit -m "feat(decompiler): 注册 BDEC 反编译引擎类型"
```

---

## Task 4: BdecDecompiler 适配器

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/BdecDecompiler.java`

- [ ] **Step 1: 编写适配器**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.bdec.BdecConfig;
import com.bingbaihanji.bdec.BdecEngine;
import com.bingbaihanji.bdec.BdecResult;

/**
 * bdec 反编译引擎适配器
 *
 * <p>桥接 fxdecomplie {@link Decompiler} 接口与 bdec 的 {@link BdecEngine}。
 * 依赖类字节码通过 fxdecomplie {@link DecompilerContext} 解析。</p>
 *
 * @author bingbaihanji
 * @date 2026-08-17
 */
public final class BdecDecompiler implements Decompiler {

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        return decompileType(typeName, classBytes, DecompilerContext.EMPTY);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes,
                            DecompilerContext context) {
        return decompileType(DecompilerContext.normalizeInternalName(classFilePath),
                classBytes, context);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes,
                                DecompilerContext context) {
        if (classBytes == null) {
            return failureOutput(typeName, "类字节码为空");
        }
        try {
            BdecConfig config = BdecConfig.builder().build();
            // 桥接依赖解析: fxdecomplie context → bdec classByteLoader
            var bdecCtx = new com.bingbaihanji.bdec.DecompileContext(
                    config, internalName -> context.getClassBytes(internalName));
            // noop 诊断监听器 (诊断仅记录,不打断反编译)
            BdecResult result = new BdecEngine(config, diag -> {})
                    .decompile(typeName, classBytes, bdecCtx);
            if (!result.success()) {
                Throwable cause = result.cause();
                String reason = cause != null && cause.getMessage() != null
                        ? cause.getMessage() : "反编译失败";
                return failureOutput(typeName, reason);
            }
            return result.decompiledCode();
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage()
                    : e.getClass().getSimpleName();
            return failureOutput(typeName, reason);
        }
    }

    private static String failureOutput(String typeName, String reason) {
        return "// Bdec Error: " + typeName + "\n// "
                + (reason == null || reason.isBlank() ? "未知错误" : reason);
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.BDEC;
    }

    @Override
    public String getName() {
        return "bdec";
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -DskipTests
```

Expected: BUILD SUCCESS（如 BdecConfig.builder() 不可用，先修正 bdec 调用）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/BdecDecompiler.java
git commit -m "feat(decompiler): BdecDecompiler 适配器 — 桥接 bdec BdecEngine"
```

---

## Task 5: DecompilerFactory + EngineParameters 注册

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactory.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/bdec/BdecParameters.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/EngineParameters.java`

- [ ] **Step 1: DecompilerFactory 加 BDEC case**

在 switch 中 JADX case 后添加:

```java
                case JADX -> new JadxDecompiler();
                case BDEC -> new BdecDecompiler();
```

- [ ] **Step 2: 创建 BdecParameters**

```java
package com.bingbaihanji.fxdecomplie.decompiler.bdec;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * bdec 反编译引擎可配置参数定义
 *
 * <p>从 bdec {@code BdecConfig} 的配置项映射,key 使用 BdecConfig builder 字段名。</p>
 *
 * @author bingbaihanji
 * @date 2026-08-17
 */
public final class BdecParameters {

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            param("indentSize", ParamType.INTEGER, "4", Category.COMMON),
            param("showLineNumbers", ParamType.BOOLEAN, "false", Category.COMMON),
            param("decodeEnums", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeLambdas", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeTernary", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeStringConcat", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeTryResource", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeForEach", ParamType.BOOLEAN, "true", Category.COMMON),
            param("decodeStringSwitch", ParamType.BOOLEAN, "true", Category.COMMON),
            param("collapseImports", ParamType.BOOLEAN, "true", Category.COMMON),
            param("ssaThreshold", ParamType.INTEGER, "20", Category.ADVANCED)
    );

    private BdecParameters() {
        throw new AssertionError("constants");
    }

    private static DecompilerParameter param(String key, ParamType type,
                                             String def, Category category) {
        return new DecompilerParameter(key, type, def,
                "engine.bdec." + key, "engine.bdec." + key + ".help", category, null);
    }
}
```

- [ ] **Step 3: EngineParameters 注册表加 BDEC**

```java
            DecompilerTypeEnum.JD, JdParameters.PARAMETERS,
            DecompilerTypeEnum.BDEC, BdecParameters.PARAMETERS);
```

同时添加 import:

```java
import com.bingbaihanji.fxdecomplie.decompiler.bdec.BdecParameters;
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw compile -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/DecompilerFactory.java
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/bdec/BdecParameters.java
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/EngineParameters.java
git commit -m "feat(decompiler): 注册 BDEC 到 Factory + EngineParameters"
```

---

## Task 6: BdecDecompiler 单元测试

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/decompiler/bdec/BdecDecompilerTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.bingbaihanji.fxdecomplie.decompiler.bdec;

import com.bingbaihanji.fxdecomplie.decompiler.BdecDecompiler;
import com.bingbaihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerContext;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbaihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BdecDecompiler")
class BdecDecompilerTest {

    /** 用 ASM 生成一个简单类的字节码 */
    private static byte[] simpleClassBytes(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, internalName,
                null, "java/lang/Object", null);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "hello", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("反编译简单类应产出非空源码")
    void shouldDecompileSimpleClass() {
        Decompiler bdec = new BdecDecompiler();
        byte[] bytes = simpleClassBytes("com/example/Simple");

        String source = bdec.decompileType("com.example.Simple", bytes,
                DecompilerContext.EMPTY);

        assertNotNull(source, "源码不应为 null");
        assertFalse(source.isBlank(), "源码不应为空白");
        assertTrue(source.contains("Simple"), "源码应包含类名 Simple");
    }

    @Test
    @DisplayName("空字节码应返回 Bdec Error 文本")
    void shouldReturnErrorOnNullBytes() {
        Decompiler bdec = new BdecDecompiler();

        String source = bdec.decompileType("com.example.Missing", null,
                DecompilerContext.EMPTY);

        assertTrue(source.startsWith("// Bdec Error:"), "应以 Bdec Error 开头");
    }

    @Test
    @DisplayName("工厂应能创建 BDEC 引擎")
    void shouldRegisterInFactory() {
        Decompiler engine = DecompilerFactory.getDecompiler(DecompilerTypeEnum.BDEC);
        assertNotNull(engine, "BDEC 引擎不应为 null");
        assertEquals(DecompilerTypeEnum.BDEC, engine.getType());
    }
}
```

- [ ] **Step 2: 运行测试**

```bash
./mvnw test -Dtest="BdecDecompilerTest"
```

Expected: 3 tests 通过

- [ ] **Step 3: 提交**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/decompiler/bdec/BdecDecompilerTest.java
git commit -m "test(decompiler): BdecDecompiler 单元测试 (3/3)"
```

---

## Task 7: 文档更新 + 全量验证

**Files:**
- Modify: `docs/architecture-boundaries.md`
- Modify: `docs/THIRD-PARTY-NOTICES.txt`
- Modify: `AGENTS.md`

- [ ] **Step 1: architecture-boundaries.md 标记 bdec 为内核维护区**

在 Vendored 内核边界节添加:

```markdown
### 2.3 bdec (`com.bingbaihanji.bdec`)

- **状态**: 内核维护区（subtree vendored, 来源 github.com/bingbaihanji/bdec）
- **访问入口**: 仅通过 `decompiler/BdecDecompiler.java` 适配层访问
- **禁止**: UI/controller 直接 import bdec 内部类型
- **同步方式**: `git subtree pull --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src`
```

- [ ] **Step 2: THIRD-PARTY-NOTICES.txt 加 bdec**

```text
16. bdec (自研引擎, vendored via subtree)
    许可证: Apache License 2.0
    仓库: https://github.com/bingbaihanji/bdec
    集成: src/main/java/com/bingbaihanji/bdec
```

- [ ] **Step 3: AGENTS.md 更新技术栈和结构**

在技术栈表的反编译引擎行加 `bdec 1.0.0`；在项目结构加 `decompiler/bdec/` 说明。

- [ ] **Step 4: 全量测试**

```bash
./mvnw test
```

Expected: 全部测试通过（现有 336 + bdec 3 = 339）

- [ ] **Step 5: 运行架构测试确认 bdec 不违反边界**

```bash
./mvnw test -Dtest="ArchitectureBoundaryTest"
```

Expected: 3/3 通过（bdec 包不匹配任何禁止规则）

- [ ] **Step 6: 提交**

```bash
git add docs/architecture-boundaries.md docs/THIRD-PARTY-NOTICES.txt AGENTS.md
git commit -m "docs: 标记 bdec 为内核维护区 + 更新第三方声明"
```

---

## 验收标准

1. `src/main/java/com/bingbaihanji/bdec/**` 存在（bdec 源码平铺）
2. `DecompilerFactory.getDecompiler(BDEC)` 可创建引擎
3. `BdecDecompilerTest` 3/3 通过
4. `mvn test` 全部通过
5. 后续更新: `git subtree pull --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src`

## 后续 bdec 更新流程 (给用户)

1. bdec main 更新 → GitHub Actions 自动重建 main-src 分支
2. fxdecomplie: `git subtree pull --prefix=src/main/java/com/bingbaihanji/bdec bdec main-src`
