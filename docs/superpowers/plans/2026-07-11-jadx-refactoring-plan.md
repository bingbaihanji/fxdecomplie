# jadx 引擎适配性优化与工具类统一重构 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 jadx 引擎深度集成：统一状态管理、消除工具类重复、抽取通用代码、优化集合、清理冗余

**Architecture:** 五个阶段顺序执行：状态统一 → 工具类替换 → 通用抽取 → 集合优化 → 冗余清理。每阶段独立编译验证。

**Tech Stack:** JDK 25, JavaFX, Maven, jadx internals (ASM tree API)

**Spec:** `docs/superpowers/specs/2026-07-11-jadx-refactoring-design.md`

**Start commit:** `2214242` (本计划写入前的 HEAD)

---

## 阶段 1：状态管理与依赖统一（~5 文件）

### Task 1.1: 创建 `JadxOptionsBridge` — 键值映射到 JadxArgs

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/util/JadxOptionsBridge.java`

- [ ] **Step 1: 写入 JadxOptionsBridge 完整代码**

```java
package com.bingbaihanji.fxdecomplie.util;

import com.bingbaihanji.fxdecomplie.core.jadx.api.JadxArgs;
import java.util.Map;

/**
 * 将 {@code Map<String, String>} 选项映射到 {@link JadxArgs} setter 的桥接工具。
 * <p>
 * key 名称与 {@link com.bingbaihanji.fxdecomplie.decompiler.JadxParameters} 的定义保持一致。
 */
public final class JadxOptionsBridge {

    private JadxOptionsBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * 将选项 Map 中的值应用到指定的 JadxArgs 实例。
     * 未出现在 map 中的 key 保留 JadxArgs 默认值不变。
     */
    public static void apply(JadxArgs args, Map<String, String> options) {
        if (args == null || options == null || options.isEmpty()) {
            return;
        }
        BooleanValue v;

        v = bool(options, "showInconsistentCode");
        if (v.set) args.setShowInconsistentCode(v.value);
        v = bool(options, "useImports");
        if (v.set) args.setUseImports(v.value);
        v = bool(options, "debugInfo");
        if (v.set) args.setDebugInfo(v.value);
        v = bool(options, "extractFinally");
        if (v.set) args.setExtractFinally(v.value);
        v = bool(options, "inlineAnonymousClasses");
        if (v.set) args.setInlineAnonymousClasses(v.value);
        v = bool(options, "inlineMethods");
        if (v.set) args.setInlineMethods(v.value);
        v = bool(options, "moveInnerClasses");
        if (v.set) args.setMoveInnerClasses(v.value);
        v = bool(options, "deobfuscationOn");
        if (v.set) args.setDeobfuscationOn(v.value);
        v = bool(options, "restoreSwitchOverString");
        if (v.set) args.setRestoreSwitchOverString(v.value);
        v = bool(options, "replaceConsts");
        if (v.set) args.setReplaceConsts(v.value);
        v = bool(options, "skipResources");
        if (v.set) args.setSkipResources(v.value);
        v = bool(options, "skipSources");
        if (v.set) args.setSkipSources(v.value);
        v = bool(options, "insertDebugLines");
        if (v.set) args.setInsertDebugLines(v.value);
        v = bool(options, "allowInlineKotlinLambda");
        if (v.set) args.setAllowInlineKotlinLambda(v.value);
        v = bool(options, "escapeUnicode");
        if (v.set) args.setEscapeUnicode(v.value);
        v = bool(options, "respectBytecodeAccModifiers");
        if (v.set) args.setRespectBytecodeAccModifiers(v.value);
        v = bool(options, "skipXmlPrettyPrint");
        if (v.set) args.setSkipXmlPrettyPrint(v.value);

        intValue(options, "threadsCount").ifSet(args::setThreadsCount);
        intValue(options, "deobfuscationMinLength").ifSet(args::setDeobfuscationMinLength);
        intValue(options, "deobfuscationMaxLength").ifSet(args::setDeobfuscationMaxLength);
        intValue(options, "sourceNameRepeatLimit").ifSet(args::setSourceNameRepeatLimit);
        intValue(options, "typeUpdatesLimitCount").ifSet(args::setTypeUpdatesLimitCount);
    }

    // --- helpers ---

    private record BooleanValue(boolean set, boolean value) {}

    private static BooleanValue bool(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) return new BooleanValue(false, false);
        return new BooleanValue(true, "true".equalsIgnoreCase(v) || "1".equals(v));
    }

    private record IntValue(boolean set, int value) {
        void ifSet(java.util.function.IntConsumer c) { if (set) c.accept(value); }
    }

    private static IntValue intValue(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null || v.isEmpty()) return new IntValue(false, 0);
        try {
            return new IntValue(true, Integer.parseInt(v));
        } catch (NumberFormatException e) {
            return new IntValue(false, 0);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -pl . -am -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/util/JadxOptionsBridge.java
git commit -m "feat: add JadxOptionsBridge for mapping AppConfig options to JadxArgs

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.2: 创建 `JadxConsts` — JVM 类常量

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/util/JadxConsts.java`

- [ ] **Step 1: 写入 JadxConsts**

```java
package com.bingbaihanji.fxdecomplie.util;

/**
 * jadx 反编译引擎中的 JVM 标准类常量，可供所有反编译引擎复用。
 * <p>
 * 本类是一个纯常量容器，不可实例化。
 */
public final class JadxConsts {

    /** {@code java.lang.Object} */
    public static final String CLASS_OBJECT = "java.lang.Object";
    /** {@code java.lang.String} */
    public static final String CLASS_STRING = "java.lang.String";
    /** {@code java.lang.Class} */
    public static final String CLASS_CLASS = "java.lang.Class";
    /** {@code java.lang.Throwable} */
    public static final String CLASS_THROWABLE = "java.lang.Throwable";
    /** {@code java.lang.Error} */
    public static final String CLASS_ERROR = "java.lang.Error";
    /** {@code java.lang.Exception} */
    public static final String CLASS_EXCEPTION = "java.lang.Exception";
    /** {@code java.lang.RuntimeException} */
    public static final String CLASS_RUNTIME_EXCEPTION = "java.lang.RuntimeException";
    /** {@code java.lang.Enum} */
    public static final String CLASS_ENUM = "java.lang.Enum";
    /** {@code java.lang.StringBuilder} */
    public static final String CLASS_STRING_BUILDER = "java.lang.StringBuilder";
    /** {@code @Override} 注解的 JVM 类型描述符 */
    public static final String OVERRIDE_ANNOTATION = "Ljava/lang/Override;";
    /** 默认包名（用于无包名类） */
    public static final String DEFAULT_PACKAGE_NAME = "defpackage";
    /** 匿名类名称前缀 */
    public static final String ANONYMOUS_CLASS_PREFIX = "AnonymousClass";
    /** {@code toString()} 方法的签名 */
    public static final String MTH_TOSTRING_SIGNATURE = "toString()Ljava/lang/String;";

    private JadxConsts() {
        throw new AssertionError("utility class");
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
./mvnw compile -pl . -am -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/util/JadxConsts.java
git commit -m "feat: add JadxConsts with JVM standard class constants from jadx Consts

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.3: 修改 `JadxDecompiler` 适配器 — 从 DecompilerContext 读取选项

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/JadxDecompiler.java`

- [ ] **Step 1: Add imports**

在文件顶部 import 区域添加：

```java
import com.bingbaihanji.fxdecomplie.util.JadxOptionsBridge;
import java.util.Map;
```

- [ ] **Step 2: Replace `createJadxArgs()` and `decompileType()`**

将原来的 `createJadxArgs()` 方法（第 129–140 行）和 `decompileType()` 方法（第 56–111 行）中的调用替换为：

```java
    @Override
    public String decompileType(String typeName, byte[] classBytes, DecompilerContext context) {
        final DecompilerContext effectiveContext = context == null ? DecompilerContext.EMPTY : context;
        final String effectiveTypeName = DecompilerContext.normalizeInternalName(typeName);

        log.debug("jadx decompile: class={}", effectiveTypeName);
        long start = System.currentTimeMillis();

        try {
            List<JavaClassReader> classReaders = new ArrayList<>();
            classReaders.add(new JavaClassReader(0, effectiveTypeName + ".class", classBytes.clone()));

            ICodeLoader codeLoader = new JavaLoadResult(classReaders);

            // 从上下文读取引擎选项，不再硬编码
            JadxArgs args = createJadxArgs(effectiveContext.options());

            try (com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler jadx =
                         new com.bingbaihanji.fxdecomplie.core.jadx.api.JadxDecompiler(args)) {
                jadx.addCustomCodeLoader(codeLoader);
                jadx.load();

                List<JavaClass> classes = jadx.getClasses();
                if (classes.isEmpty()) {
                    log.warn("jadx decompile: no classes loaded for {}", effectiveTypeName);
                    return "// jadx decompile failed: no classes loaded\n// Class: " + effectiveTypeName;
                }

                JavaClass targetClass = findTargetClass(classes, effectiveTypeName);
                if (targetClass == null) {
                    targetClass = classes.get(0);
                }

                String decompiled = targetClass.getCode();
                long elapsed = System.currentTimeMillis() - start;

                if (decompiled == null || decompiled.isEmpty()) {
                    log.warn("jadx decompile returned empty: {} ({}ms)", effectiveTypeName, elapsed);
                    return "// jadx decompile failed\n// Class: " + effectiveTypeName;
                }

                log.debug("jadx decompile OK: {} ({}ms, {} chars)", effectiveTypeName, elapsed,
                        decompiled.length());
                return decompiled;
            }
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("jadx decompile exception: {} ({}ms): {}", effectiveTypeName, elapsed, e.getMessage());
            return "// jadx Error: " + e.getMessage();
        }
    }

    /**
     * 创建 jadx 反编译参数，先设置默认值，再通过 JadxOptionsBridge 应用用户配置覆盖。
     */
    private JadxArgs createJadxArgs(Map<String, String> engineOptions) {
        JadxArgs args = new JadxArgs();
        // 单类反编译模式默认值
        args.setSkipResources(true);
        args.setDebugInfo(true);
        args.setUseImports(true);
        args.setInlineMethods(true);
        args.setInlineAnonymousClasses(true);
        args.setExtractFinally(true);
        args.setDeobfuscationOn(false);
        args.setShowInconsistentCode(true);
        // 应用用户配置覆盖默认值
        JadxOptionsBridge.apply(args, engineOptions);
        return args;
    }
```

- [ ] **Step 3: 编译验证**

```bash
./mvnw compile -pl . -am -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/JadxDecompiler.java
git commit -m "refactor: JadxDecompiler reads options from DecompilerContext via JadxOptionsBridge

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.4: 更新 jadx 内部 Consts 引用指向 JadxConsts

**Files:**
- Modify: 所有引用 `Consts.CLASS_*` / `Consts.DEFAULT_PACKAGE_NAME` / `Consts.ANONYMOUS_CLASS_PREFIX` / `Consts.OVERRIDE_ANNOTATION` / `Consts.MTH_TOSTRING_SIGNATURE` 的 jadx 文件
- Delete: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/Consts.java`

- [ ] **Step 1: Find all references to Consts in jadx**

```bash
grep -rn "Consts\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "Consts.java" | grep -v "^Binary" | sed 's/:.*$//' | sort -u
```

- [ ] **Step 2: Replace import and usages**

For each file found above:
1. Replace `import com.bingbaihanji.fxdecomplie.core.jadx.core.Consts;` with `import com.bingbaihanji.fxdecomplie.util.JadxConsts;`
2. Replace all `Consts.` prefixes with `JadxConsts.`

Execute the replacement with sed (Bash):

```bash
# 获取所有引用 Consts 的 jadx 源文件（排除 Consts.java 自身）
FILES=$(grep -rn "Consts\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "Consts.java" | cut -d: -f1 | sort -u)

for f in $FILES; do
  # 替换 import
  sed -i 's|import com.bingbaihanji.fxdecomplie.core.jadx.core.Consts;|import com.bingbaihanji.fxdecomplie.util.JadxConsts;|g' "$f"
  # 替换引用前缀
  sed -i 's|Consts\.|JadxConsts.|g' "$f"
done
```

- [ ] **Step 3: Delete Consts.java**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/Consts.java
```

- [ ] **Step 4: 编译验证**

```bash
./mvnw compile -pl . -am -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`. 如果有编译错误，逐个手动修复剩余的引用。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/
git add src/main/java/com/bingbaihanji/fxdecomplie/util/JadxConsts.java
git commit -m "refactor: replace jadx Consts.java with project JadxConsts

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 1.5: 阶段 1 最终验证

- [ ] **Step 1: 完整编译**

```bash
./mvnw clean compile -DskipTests 2>&1 | tail -10
```
期望: `BUILD SUCCESS`

- [ ] **Step 2: 运行全部测试**

```bash
./mvnw test 2>&1 | tail -20
```
期望: `BUILD SUCCESS`，所有测试通过

- [ ] **Step 3: 阶段 1 标记提交**

```bash
git add -A
git commit -m "chore: phase 1 complete - jadx state management unified

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段 2：存量工具类复用（~120 文件）

### Task 2.1: 扩展 `ByteUtils` — 添加 MD5、单字节/整型 hex

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/util/ByteUtils.java`

- [ ] **Step 1: 在 ByteUtils 中新增方法**

在文件末尾的 `private static String format(...)` 方法之前插入：

```java
    private static final byte[] HEX_ARRAY = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    /**
     * 将单个字节值转换为补零的两位十六进制字符串。
     * @param value 字节值（仅取低 8 位）
     * @return 两位十六进制字符串
     */
    public static String byteToHex(int value) {
        int v = value & 0xFF;
        byte[] hexChars = {HEX_ARRAY[v >>> 4], HEX_ARRAY[v & 0x0F]};
        return new String(hexChars, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * 将 int 值转换为补零的 8 位十六进制字符串。
     * @param value 整数值
     * @return 8 位十六进制字符串
     */
    public static String intToHex(int value) {
        byte[] hexChars = new byte[8];
        int v = value;
        for (int i = 7; i >= 0; i--) {
            hexChars[i] = HEX_ARRAY[v & 0x0F];
            v >>>= 4;
        }
        return new String(hexChars, java.nio.charset.StandardCharsets.US_ASCII);
    }

    /**
     * 计算字节数组的 MD5 哈希值。
     * @param data 输入字节数组
     * @return 十六进制表示的 MD5 值
     */
    public static String md5Sum(byte[] data) {
        java.util.Objects.requireNonNull(data, "data 不能为 null");
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            md.update(data);
            return bytesToHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    /**
     * 计算字符串（UTF-8 编码）的 MD5 哈希值。
     * @param str 输入字符串
     * @return 十六进制表示的 MD5 值
     */
    public static String md5Sum(String str) {
        java.util.Objects.requireNonNull(str, "str 不能为 null");
        return md5Sum(str.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 通过文件魔数（PK\03\04）判断文件是否为 ZIP 格式。
     * @param file 待检测的文件
     * @return 是 ZIP 文件返回 true
     */
    public static boolean isZipFile(java.io.File file) {
        byte[] magic = {0x50, 0x4B, 0x03, 0x04};
        try (java.io.InputStream is = new java.io.FileInputStream(file)) {
            byte[] headers = new byte[magic.length];
            int read = is.read(headers);
            return read == magic.length && java.util.Arrays.equals(headers, magic);
        } catch (Exception e) {
            return false;
        }
    }
```

- [ ] **Step 2: 编译验证 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -5
# BUILD SUCCESS
git add src/main/java/com/bingbaihanji/fxdecomplie/util/ByteUtils.java
git commit -m "feat: extend ByteUtils with md5Sum, byteToHex, intToHex, isZipFile

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.2: 创建 `NamedThreadFactory`

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/util/NamedThreadFactory.java`

- [ ] **Step 1: 写入文件**

```java
package com.bingbaihanji.fxdecomplie.util;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 带命名前缀的简单线程工厂。
 * <p>
 * 线程名格式：{@code <prefix>-<poolId>-<threadId>}。
 * 配备统一的未捕获异常处理器，OOM 错误会强制中断线程。
 */
public final class NamedThreadFactory implements ThreadFactory {

    private static final Logger LOG = LoggerFactory.getLogger(NamedThreadFactory.class);
    private static final AtomicInteger POOL_ID = new AtomicInteger(0);

    private final String name;
    private final AtomicInteger number = new AtomicInteger(0);
    private final int poolId;

    public NamedThreadFactory(String name) {
        this.name = name;
        this.poolId = POOL_ID.incrementAndGet();
    }

    @Override
    public Thread newThread(@NotNull Runnable r) {
        Thread thread = new Thread(r, name + '-' + poolId + '-' + number.incrementAndGet());
        thread.setUncaughtExceptionHandler((t, e) -> {
            if (e instanceof OutOfMemoryError) {
                t.interrupt();
                LOG.error("OutOfMemoryError in thread {}, forcing interrupt", t.getName());
            } else {
                LOG.error("Uncaught thread exception, thread: {}", t.getName(), e);
            }
        });
        return thread;
    }
}
```

- [ ] **Step 2: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -3
git add src/main/java/com/bingbaihanji/fxdecomplie/util/NamedThreadFactory.java
git commit -m "feat: add NamedThreadFactory extracted from jadx Utils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.3: 创建 `SmaliNameUtils` — Smali/Java 名称互转

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/util/SmaliNameUtils.java`

- [ ] **Step 1: 写入 SmaliNameUtils**

```java
package com.bingbaihanji.fxdecomplie.util;

/**
 * Smali 格式与 Java 格式类型/类名互转工具。
 * <p>
 * 纯函数风格，不做状态缓存。
 */
public final class SmaliNameUtils {

    private SmaliNameUtils() {
        throw new AssertionError("utility class");
    }

    /** "Ljava/lang/String;" → "java.lang.String" */
    public static String smaliToJava(String obj) {
        if (obj == null || obj.isEmpty()) return obj;
        if (obj.charAt(0) == 'L') {
            int last = obj.length() - 1;
            if (obj.charAt(last) == ';') {
                return obj.substring(1, last).replace('/', '.');
            }
        }
        return obj;
    }

    /** "Ljava/lang/String;" → "java/lang/String" (去 L; 前后缀) */
    public static String smaliToInternal(String obj) {
        if (obj == null || obj.isEmpty()) return obj;
        if (obj.charAt(0) == 'L') {
            return obj.substring(1, obj.length() - 1);
        }
        return obj;
    }

    /** "java.lang.String" → "Ljava/lang/String;" */
    public static String javaToSmali(String obj) {
        if (obj == null || obj.isEmpty()) return obj;
        return 'L' + obj.replace('.', '/') + ';';
    }

    /** "I" → "int", "[[B" → "byte[][]", "Ljava/lang/String;" → "java.lang.String" */
    public static String descriptorToJava(String desc) {
        if (desc == null || desc.isEmpty()) return desc;
        return switch (desc.charAt(0)) {
            case 'V' -> "void";
            case 'Z' -> "boolean";
            case 'C' -> "char";
            case 'B' -> "byte";
            case 'S' -> "short";
            case 'I' -> "int";
            case 'F' -> "float";
            case 'J' -> "long";
            case 'D' -> "double";
            case 'L' -> smaliToJava(desc).replace('$', '.');
            case '[' -> descriptorToJava(desc.substring(1)) + "[]";
            default -> desc;
        };
    }

    /** "int" → "I", "java.lang.String" → "Ljava/lang/String;", "byte[][]" → "[[B" */
    public static String javaToDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) return desc;
        if (desc.endsWith("[]")) {
            return "[" + javaToDescriptor(desc.substring(0, desc.length() - 2));
        }
        return switch (desc) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "char" -> "C";
            case "byte" -> "B";
            case "short" -> "S";
            case "int" -> "I";
            case "float" -> "F";
            case "long" -> "J";
            case "double" -> "D";
            default -> javaToSmali(desc);
        };
    }

    /** 判断描述符是否代表基本类型 */
    public static boolean isPrimitiveDescriptor(String desc) {
        if (desc == null || desc.isEmpty()) return false;
        return switch (desc.charAt(0)) {
            case 'V', 'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> true;
            default -> false;
        };
    }
}
```

- [ ] **Step 2: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -3
git add src/main/java/com/bingbaihanji/fxdecomplie/util/SmaliNameUtils.java
git commit -m "feat: add SmaliNameUtils for smali/java type name conversion

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.4: 删除 jadx 侧的 `ListUtils.java`

**Files:**
- Delete: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/ListUtils.java`
- Modify: 所有引用 jadx `ListUtils` 的 ~62 处调用点

- [ ] **Step 1: 查找所有引用**

```bash
grep -rn "ListUtils\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "ListUtils.java" | cut -d: -f1 | sort -u
```

- [ ] **Step 2: 按方法逐文件替换**

通过 `sed` 批量执行以下替换模式（下表中每一行是一个 `sed` 正则替换），然后再逐个文件手动处理复杂内联：

| `sed` 命令 | 效果 |
|---|---|
| `sed -i 's|ListUtils\.map(\([^)]*\), \([^)]*\))|\\1.stream().map(\\2).toList()|g'` | `map()` → `stream().map().toList()` |
| `sed -i 's|ListUtils\.filter(\([^)]*\), \([^)]*\))|\\1.stream().filter(\\2).toList()|g'` | `filter()` → `stream().filter().toList()` |
| `sed -i 's|ListUtils\.firstOrNull(\([^)]*\))|com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.first(\\1)|g'` | `firstOrNull()` → `Utils.first()` |
| `sed -i 's|ListUtils\.first(\([^)]*\))|\\1.get(0)|g'` | `first()` → `get(0)` |
| `sed -i 's|ListUtils\.last(\([^)]*\))|com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.last(\\1)|g'` | `last()` → `Utils.last()` |
| `sed -i 's|ListUtils\.removeLast(\([^)]*\))|removeLastInline(\\1)|g'` | `removeLast()` (handled next step) |
| `sed -i 's|ListUtils\.distinctList(\([^)]*\))|new java.util.ArrayList<>(new java.util.LinkedHashSet<>(\\1))|g'` | `distinctList()` 内联 |
| `sed -i 's|ListUtils\.anyMatch(\([^)]*\), \([^)]*\))|\\1.stream().anyMatch(\\2)|g'` | `anyMatch()` → stream |
| `sed -i 's|ListUtils\.allMatch(\([^)]*\), \([^)]*\))|\\1.stream().allMatch(\\2)|g'` | `allMatch()` → stream |
| `sed -i 's|ListUtils\.noneMatch(\([^)]*\), \([^)]*\))|\\1.stream().noneMatch(\\2)|g'` | `noneMatch()` → stream |

执行脚本：

```bash
FILES=$(grep -rn "import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils" src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | cut -d: -f1 | sort -u)

for f in $FILES; do
  # 删除 import
  sed -i '/import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.ListUtils;/d' "$f"
  # 替换 .map()
  sed -i 's|ListUtils\.map(\([^,]*\), \([^)]*\))|\\1.stream().map(\\2).toList()|g' "$f"
  sed -i 's|ListUtils\.filter(\([^,]*\), \([^)]*\))|\\1.stream().filter(\\2).toList()|g' "$f"
  sed -i 's|ListUtils\.firstOrNull(\([^)]*\))|com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.first(\\1)|g' "$f"
  sed -i 's|ListUtils\.first(\([^)]*\))|\\1.get(0)|g' "$f"
  sed -i 's|ListUtils\.last(\([^)]*\))|com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils.last(\\1)|g' "$f"
  sed -i 's|ListUtils\.distinctList(\([^)]*\))|new java.util.ArrayList<>(new java.util.LinkedHashSet<>(\\1))|g' "$f"
  sed -i 's|ListUtils\.anyMatch(\([^,]*\), \([^)]*\))|\\1.stream().anyMatch(\\2)|g' "$f"
  sed -i 's|ListUtils\.allMatch(\([^,]*\), \([^)]*\))|\\1.stream().allMatch(\\2)|g' "$f"
  sed -i 's|ListUtils\.noneMatch(\([^,]*\), \([^)]*\))|\\1.stream().noneMatch(\\2)|g' "$f"
done
```

- [ ] **Step 3: 处理需要内联的方法**

对于 `ListUtils.removeLast()`、`safeReplace()`、`safeAdd()`、`safeRemove()`、`safeRemoveAndTrim()`、`mutableListOf()`、`orderedEquals()`、`unorderedEquals()`、`concat()` 等方法，直接在调用处内联：

```bash
# 查找剩余未替换的 ListUtils 引用
grep -rn "ListUtils\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java"
```

根据输出结果，逐个文件手动修改。每个方法的内联模板：

- `ListUtils.removeLast(list)` → 内联为：
```java
(list != null && !list.isEmpty()) ? list.remove(list.size() - 1) : null
```

- `ListUtils.safeReplace(list, oldObj, newObj)` → 内联为：
```java
if (list == null || list.isEmpty()) { list = new java.util.ArrayList<>(); list.add(newObj); return list; }
int idx = list.indexOf(oldObj);
if (idx != -1) list.set(idx, newObj); else list.add(newObj);
return list;
```

- `ListUtils.safeAdd(list, obj)` → 内联为：
```java
if (list == null || list.isEmpty()) { java.util.ArrayList<T> nl = new java.util.ArrayList<>(1); nl.add(obj); return nl; }
list.add(obj); return list;
```

- `ListUtils.mutableListOf(x)` → `new java.util.ArrayList<>(java.util.List.of(x))`
- `ListUtils.mutableListOf(x, y)` → `new java.util.ArrayList<>(java.util.List.of(x, y))`

- `ListUtils.concat(first, values)` → 内联为：
```java
java.util.List<T> list = new java.util.ArrayList<>(1 + values.length);
list.add(first);
list.addAll(java.util.Arrays.asList(values));
return list;
```

- `ListUtils.enumerationToList(enum)` → 内联为：
```java
if (enum == null || enum == java.util.Collections.emptyEnumeration()) return java.util.Collections.emptyList();
java.util.List<T> list = new java.util.ArrayList<>();
while (enum.hasMoreElements()) list.add(enum.nextElement());
return list;
```

- [ ] **Step 4: 删除 ListUtils.java 和编译验证**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/ListUtils.java
./mvnw compile -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`。若编译失败，按错误提示逐个修复剩余的引用。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "refactor: delete jadx ListUtils, redirect references to Stream API

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.5: 删除 jadx 侧的 `FileUtils.java`

**Files:**
- Delete: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/files/FileUtils.java`
- Modify: 所有引用 jadx `FileUtils` 的文件

- [ ] **Step 1: 查找所有引用**

```bash
grep -rn "FileUtils\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "FileUtils.java" | cut -d: -f1 | sort -u
```

- [ ] **Step 2: 执行映射替换**

| jadx `FileUtils.*` 方法 | 替换为 |
|---|---|
| `FileUtils.bytesToHex(bytes)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.bytesToHex(bytes)` |
| `FileUtils.byteToHex(v)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.byteToHex(v)` |
| `FileUtils.intToHex(v)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.intToHex(v)` |
| `FileUtils.md5Sum(data)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.md5Sum(data)` |
| `FileUtils.md5Sum(str)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.md5Sum(str)` |
| `FileUtils.copyStream(in, out)` | `in.transferTo(out)` |
| `FileUtils.streamToByteArray(in)` | `in.readAllBytes()` |
| `FileUtils.streamToString(in)` | `new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)` |
| `FileUtils.close(closeable)` | 用 try-with-resources 处理 |
| `FileUtils.makeDirs(file)` | `java.nio.file.Files.createDirectories(file.toPath())` |
| `FileUtils.makeDirsForFile(file)` | `java.nio.file.Files.createDirectories(file.toPath().getParent())` |
| `FileUtils.readFile(path)` | `java.nio.file.Files.readString(path)` |
| `FileUtils.writeFile(path, str)` | `java.nio.file.Files.writeString(path, str)` |
| `FileUtils.writeFile(path, bytes)` | `java.nio.file.Files.write(path, bytes)` |
| `FileUtils.writeFile(path, is)` | `java.nio.file.Files.copy(is, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)` |
| `FileUtils.isZipFile(file)` | `com.bingbaihanji.fxdecomplie.util.ByteUtils.isZipFile(file)` |
| `FileUtils.renameFile(src, tgt)` | `java.nio.file.Files.move(src, tgt, java.nio.file.StandardCopyOption.REPLACE_EXISTING)` |
| `FileUtils.hasExtension(path, ext)` | `path.getFileName().toString().toLowerCase().endsWith(ext)` |
| `FileUtils.getPathBaseName(path)` | 内联：`fileName.substring(0, fileName.lastIndexOf('.'))` |
| `FileUtils.listFiles(dir)` | `java.nio.file.Files.list(dir).toList()` |
| `FileUtils.listFiles(dir, filter)` | `java.nio.file.Files.list(dir).filter(filter).toList()` |
| `FileUtils.expandDirs(paths)` | 保留为 jadx 内部的精简 `expandInputPaths` |
| `FileUtils.deleteDir(dir)` | `Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.delete(p); } catch (Exception e) { ... } })` |
| `FileUtils.deleteDirIfExists(dir)` | `if (Files.exists(dir)) { /* deleteDir logic */ }` |
| `FileUtils.clearDir(dir)` | `/* deleteDir(dir, keepRoot=true) */` |
| `FileUtils.toPaths(files)` | `files.stream().map(File::toPath).toList()` |
| `FileUtils.toFiles(paths)` | `paths.stream().map(Path::toFile).toList()` |
| `FileUtils.toFile(path)` | `new File(path)` 或直接使用 Path |
| `FileUtils.toPathWithTrim(file)` | `Path.of(file.getPath().trim())` |
| `FileUtils.prepareFile(file)` | 内联 cutFileName + makeDirs 逻辑 |
| `FileUtils.buildInputsHash(paths)` | 抽取到 `util/ByteUtils.buildInputsHash()` 或在调用处内联 |

执行批量 sed 替换：

```bash
FILES=$(grep -rn "FileUtils\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "FileUtils.java" | cut -d: -f1 | sort -u)

for f in $FILES; do
  sed -i 's|FileUtils\.bytesToHex(|com.bingbaihanji.fxdecomplie.util.ByteUtils.bytesToHex(|g' "$f"
  sed -i 's|FileUtils\.md5Sum(|com.bingbaihanji.fxdecomplie.util.ByteUtils.md5Sum(|g' "$f"
  sed -i 's|FileUtils\.isZipFile(|com.bingbaihanji.fxdecomplie.util.ByteUtils.isZipFile(|g' "$f"
  sed -i 's|FileUtils\.streamToByteArray(\([^)]*\))|\\1.readAllBytes()|g' "$f"
  sed -i 's|FileUtils\.streamToString(\([^)]*\))|new String(\\1.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)|g' "$f"
  sed -i 's|FileUtils\.copyStream(\([^,]*\), \([^)]*\))|\\1.transferTo(\\2)|g' "$f"
  sed -i 's|FileUtils\.close(\([^)]*\))|/* closed via try-with-resources */|g' "$f"
  sed -i 's|FileUtils\.makeDirs(\([^)]*\.\)toFile())|java.nio.file.Files.createDirectories(\\1|g' "$f"
  sed -i 's|FileUtils\.readFile(\([^)]*\))|java.nio.file.Files.readString(\\1)|g' "$f"
  sed -i 's|FileUtils\.writeFile(\([^,]*\), \([^)]*\))|java.nio.file.Files.writeString(\\1, \\2)|g' "$f"
  # delete import line
  sed -i '/import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files.FileUtils;/d' "$f"
done
```

- [ ] **Step 3: 处理特殊方法**

对于 `deleteDir()`、`deleteDirIfExists()`、`clearDir()`、`clearTempRootDir()`、`updateTempRootDir()`、`expandDirs()`、`buildInputsHash()` 等复杂方法，在 jadx 内部创建精简替代版本：

创建 `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/files/IoUtils.java`：

```java
package com.bingbaihanji.fxdecomplie.core.jadx.core.utils.files;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/** 精简的 I/O 工具方法，供 jadx 内部使用。比原 FileUtils 更聚焦。 */
public final class IoUtils {

    private IoUtils() {}

    /** 递归删除目录，parallel deletion of files */
    public static void deleteDir(Path dir) {
        try {
            if (Files.isDirectory(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                }
            }
        } catch (IOException ignored) {}
    }

    public static void deleteDirIfExists(Path dir) {
        if (Files.exists(dir)) deleteDir(dir);
    }

    /** 展开目录为文件平列表 */
    public static List<Path> expandDirs(List<Path> paths) {
        List<Path> files = new ArrayList<>();
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    walk.filter(Files::isRegularFile).forEach(files::add);
                } catch (Exception ignored) {}
            } else {
                files.add(path);
            }
        }
        return files;
    }
}
```

将这些方法在 jadx 中原本的调用处替换为 `IoUtils.*`。

- [ ] **Step 4: 删除 FileUtils.java 和编译验证**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/files/FileUtils.java
./mvnw compile -DskipTests 2>&1 | tail -5
```
期望: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "refactor: delete jadx FileUtils, redirect to ByteUtils/JDK API/IoUtils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.6: 删除 jadx 侧的 `StringUtils.java`

**Files:**
- Delete: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/StringUtils.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/codegen/utils/CodegenEscapeUtils.java` (只保留代码生成专用的 escape 方法)
- Modify: 所有引用 jadx `StringUtils` 的文件

- [ ] **Step 1: 从 StringUtils 提取代码生成 escape 方法到新文件**

将 `unescapeString()`, `unescapeChar()`, `escape()`, `escapeXML()`, `escapeResValue()`, `escapeResStrValue()`, `escapeWhiteSpaceChar()`, `escapeXmlChar()`, `visitCodePoints()` 等方法移入 `core/jadx/core/codegen/utils/CodegenEscapeUtils.java`，其余方法删除。

- [ ] **Step 2: 替换通用方法引用**

| jadx `StringUtils.*` 方法 | 替换为 |
|---|---|
| `StringUtils.isEmpty(str)` | `str == null \|\| str.isEmpty()` |
| `StringUtils.notEmpty(str)` | `str != null && !str.isEmpty()` |
| `StringUtils.notBlank(str)` | `str != null && !str.isBlank()` |
| `StringUtils.countMatches(str, sub)` | 内联 `str.split(sub, -1).length - 1` |
| `StringUtils.containsChar(str, ch)` | `str.indexOf(ch) >= 0` |
| `StringUtils.removeChar(str, ch)` | `str.replace(String.valueOf(ch), "")` |
| `StringUtils.getDateText()` | `java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))` |

执行批量替换脚本：

```bash
FILES=$(grep -rn "StringUtils\." src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java" | grep -v "StringUtils.java" | cut -d: -f1 | sort -u)

for f in $FILES; do
  sed -i 's|StringUtils\.isEmpty(\([^)]*\))|\\1 == null \\|\\| \\1.isEmpty()|g' "$f"
  sed -i 's|StringUtils\.notEmpty(\([^)]*\))|\\1 != null \\&\\& !\\1.isEmpty()|g' "$f"
  sed -i 's|StringUtils\.notBlank(\([^)]*\))|\\1 != null \\&\\& !\\1.isBlank()|g' "$f"
  sed -i 's|StringUtils\.containsChar(\([^,]*\), \([^)]*\))|\\1.indexOf(\\2) >= 0|g' "$f"
  sed -i '/import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.StringUtils;/d' "$f"
done
```

- [ ] **Step 3: 更新 escape 方法引用**

将剩余的 `StringUtils.escape*()` 和 `StringUtils.unescape*()` 引用的 import 替换为 `CodegenEscapeUtils`。

- [ ] **Step 4: 删除 StringUtils.java**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/StringUtils.java
```

- [ ] **Step 5: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -5
# BUILD SUCCESS
git add -A
git commit -m "refactor: delete jadx StringUtils, extract codegen escape to CodegenEscapeUtils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.7: 瘦身 jadx 侧 `Utils.java`

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/Utils.java`

- [ ] **Step 1: 删除以下方法**

从 Utils.java 中删除以下已在其他位置有替代的方法：

1. `cleanObjectName()` → `SmaliNameUtils.smaliToJava()`
2. `cutObject()` → `SmaliNameUtils.smaliToInternal()`
3. `makeQualifiedObjectName()` → `SmaliNameUtils.javaToSmali()`
4. `smaliNameToJavaName()` → `SmaliNameUtils.descriptorToJava()`
5. `javaNameToSmaliName()` → `SmaliNameUtils.javaToDescriptor()`
6. `strRepeat()` → `String.repeat()` (JDK 11)
7. `safeParseInt(String, int)` → `NumberUtil` 扩展
8. `safeParseInteger(String)` → `NumberUtil` 扩展
9. `isEmpty(Collection)`, `isEmpty(Map)`, `isEmpty(T[])` → 内联到调用处
10. `notEmpty(Collection)`, `notEmpty(T[])` → 内联到调用处
11. `collectionMap()` → 内联到 `stream().map().toList()`
12. `collectionMapNoNull()` → 内联到 `stream().map().filter(Objects::nonNull).toList()`
13. `listToString()` 系列 → `String.join()` / `Collectors.joining()`
14. `mergeLists()`, `mergeSets()`, `mergeMaps()` → `Stream.concat()` / 内联
15. `groupBy()` → `stream().collect(Collectors.toMap())`
16. `newConstStringMap()` → `Map.of()`
17. `getEnvVarBool()`, `getEnvVarInt()` → 直接删除（已 @Deprecated）
18. `simpleThreadFactory()` → 替换引用为 `NamedThreadFactory`
19. `arrayToStr()` → 内联为 `Arrays.toString()`
20. `concatStrings()` → `String.join("", list)`

- [ ] **Step 2: 更新 jadx 内部引用**

对所有引用了上述被删除方法的文件：
- `cleanObjectName()` → 替换为 `SmaliNameUtils.smaliToJava()`（添加 import）
- `strRepeat()` → 替换为 `str.repeat(count)`（已标记 `@SuppressWarnings("StringRepeatCanBeUsed")`）
- 其他依上表映射替换

- [ ] **Step 3: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -5
# BUILD SUCCESS
git add -A
git commit -m "refactor: slim down jadx Utils, redirect generic methods to util package

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2.8: 阶段 2 最终验证

- [ ] **Step 1: 完整编译**

```bash
./mvnw clean compile -DskipTests 2>&1 | tail -10
```
期望: `BUILD SUCCESS`

- [ ] **Step 2: 运行全部测试**

```bash
./mvnw test 2>&1 | tail -20
```
期望: `BUILD SUCCESS`

- [ ] **Step 3: 阶段 2 标记提交**

```bash
git add -A
git commit -m "chore: phase 2 complete - jadx tool classes replaced with util package

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段 3：通用能力抽取（~30 文件）

### Task 3.1: 验证 SmaliNameUtils 已创建

SmaliNameUtils 已在阶段 2 Task 2.3 中创建。此步验证所有预期方法存在：

- [ ] **Step 1: 确认接口完整性**

对照 `docs/superpowers/specs/2026-07-11-jadx-refactoring-design.md` 第 6.2 节，确认 `SmaliNameUtils` 包含所有 6 个公共方法。

### Task 3.2: 创建 `DescriptorUtils`

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/util/DescriptorUtils.java`

- [ ] **Step 1: 从 jadx `DescriptorParser.java` 提取通用方法**

```java
package com.bingbaihanji.fxdecomplie.util;

import java.util.ArrayList;
import java.util.List;

/**
 * JVM 方法/字段描述符解析工具。
 * <p>
 * 提供了从原始描述符字符串中逐步读取参数类型的能力，
 * 与 jadx 内部的 {@code DescriptorParser} 互补。
 */
public final class DescriptorUtils {

    private DescriptorUtils() {
        throw new AssertionError("utility class");
    }

    /**
     * 获取从 pos 位置开始的类型描述符在字符串中所占的长度。
     * <ul>
     *   <li>基本类型 (V, Z, C, B, S, I, F, J, D) → 1</li>
     *   <li>对象类型 (L...;) → 到下一个 ';' 的长度</li>
     *   <li>数组类型 ([[[...) → 每个 '[' 递归 1 + 下个元素的长度</li>
     * </ul>
     *
     * @param descriptor 方法/字段描述符
     * @param pos        起始位置
     * @return 该类型在 descriptor 中占据的字符数
     */
    public static int getTypeLength(String descriptor, int pos) {
        if (descriptor == null || pos >= descriptor.length()) {
            return 0;
        }
        char ch = descriptor.charAt(pos);
        if (ch == '[') {
            return 1 + getTypeLength(descriptor, pos + 1);
        }
        if (ch == 'L') {
            int semi = descriptor.indexOf(';', pos);
            if (semi == -1) {
                return descriptor.length() - pos;
            }
            return semi - pos + 1;
        }
        return 1; // primitive
    }

    /**
     * 将方法描述符拆解为参数类型列表和返回类型。
     *
     * @param descriptor 方法描述符，例如 "(II)Ljava/lang/String;"
     * @return [参数类型列表, 返回类型] — 元素 0..N-1 为参数，最后一个为返回类型；
     *         若 descriptor 无效则返回空列表
     */
    public static List<String> splitMethodDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty() || descriptor.charAt(0) != '(') {
            return List.of();
        }
        int closeParen = descriptor.indexOf(')');
        if (closeParen == -1) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int pos = 1;
        while (pos < closeParen) {
            int len = getTypeLength(descriptor, pos);
            parts.add(descriptor.substring(pos, pos + len));
            pos += len;
        }
        parts.add(descriptor.substring(closeParen + 1)); // return type
        return parts;
    }

    /**
     * 判断描述符字符是否为基本类型。
     */
    public static boolean isPrimitiveDescriptor(char ch) {
        return switch (ch) {
            case 'V', 'Z', 'C', 'B', 'S', 'I', 'F', 'J', 'D' -> true;
            default -> false;
        };
    }
}
```

- [ ] **Step 2: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -3
git add src/main/java/com/bingbaihanji/fxdecomplie/util/DescriptorUtils.java
git commit -m "feat: add DescriptorUtils for JVM method/field descriptor parsing

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3.3: 阶段 3 验证 + 提交

- [ ] **Step 1: 确认新增类都被正确引用（编译不报错）**

```bash
./mvnw clean compile -DskipTests 2>&1 | tail -10
# BUILD SUCCESS
```

- [ ] **Step 2: 提交**

```bash
git add -A
git commit -m "chore: phase 3 complete - extracted SmaliNameUtils, DescriptorUtils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段 4：数据结构与底层库高性能优化（~35 文件）

### Task 4.1: 热路径 `HashMap` → `ArrayMap`

**目标文件**（反编译核心路径）：

| 文件 | 原因 | 替换方式 |
|---|---|---|
| `core/jadx/core/dex/visitors/typeinference/TypeUpdateInfo.java` | 类型推断 - 高频 | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/typeinference/TypeSearchState.java` | 类型搜索状态 - 高频 | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/finaly/MarkFinallyVisitor.java` | finally 处理 - 高频 (~2处) | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/trycatch/TryEdgeScopeGroupMap.java` | try-catch 映射 - 高频 (~4处) | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/blocks/BlockProcessor.java` | 控制流处理 (~2处) | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/blocks/BlockSplitter.java` | 块分割 (~2处) | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/typeinference/FinishTypeInference.java` | 类型推断收尾 | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/regions/maker/RegionMaker.java` | Region 构建 | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/ShadowFieldVisitor.java` | 字段阴影处理 (~3处) | `HashMap`→`ArrayMap` |
| `core/jadx/core/dex/visitors/finaly/traverser/state/TraverserGlobalCommonState.java` | finally 遍历全局状态 | `HashMap`→`ArrayMap` |

- [ ] **Step 1: 逐个文件替换**

对于上述每个文件：

```java
// 替换 import
- import java.util.HashMap;
+ import com.bingbaihanji.fxdecomplie.util.collection.ArrayMap;

// 替换声明
- Map<K, V> map = new HashMap<>();
+ Map<K, V> map = new ArrayMap<>();
```

- [ ] **Step 2: 编译 + 测试验证**

```bash
./mvnw compile -DskipTests 2>&1 | tail -5
./mvnw test 2>&1 | tail -10
```

- [ ] **Step 3: 提交**

```bash
git add -A
git commit -m "perf: replace HashMap with ArrayMap in jadx decompilation hot paths

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4.2: 热路径 `HashSet` → `ArraySet`

**目标文件**：

| 文件 | 原因 |
|---|---|
| `core/jadx/core/dex/visitors/ProcessAnonymous.java` | 匿名类处理 (~2处) |
| `core/jadx/core/dex/visitors/ExtractFieldInit.java` | 字段初始化提取 |
| `core/jadx/core/clsp/ClspGraph.java` | 类层次图 (~4处) |
| `core/jadx/core/dex/visitors/regions/TernaryMod.java` | 三元表达式处理 |
| `core/jadx/core/dex/visitors/FixSwitchOverEnum.java` | Switch→enum 修复 |
| `core/jadx/core/deobf/DeobfPresets.java` | 反混淆预设 (~4处) |

- [ ] **Step 1: 逐个文件替换**

```java
// 替换 import
- import java.util.HashSet;
+ import com.bingbaihanji.fxdecomplie.util.collection.ArraySet;

// 替换声明
- Set<T> set = new HashSet<>();
+ Set<T> set = new ArraySet<>();
```

- [ ] **Step 2: 编译 + 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -5
git add -A
git commit -m "perf: replace HashSet with ArraySet in jadx decompilation hot paths

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4.3: 阶段 4 验证 + 提交

- [ ] **Step 1: 完整测试**

```bash
./mvnw clean test 2>&1 | tail -20
```
期望: `BUILD SUCCESS`

- [ ] **Step 2: 提交**

```bash
git add -A
git commit -m "chore: phase 4 complete - collection optimization in jadx hot paths

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 阶段 5：冗余代码清理（~15 文件）

### Task 5.1: 删除 `ImmutableList` — 替换为 `List.of()` / `List.copyOf()`

- [ ] **Step 1: 查找所有引用**

```bash
grep -rn "ImmutableList" src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java"
```

- [ ] **Step 2: 逐处替换**

```java
// new ImmutableList<>(list)  →  List.copyOf(list)
// new ImmutableList<>(singleElem)  →  List.of(singleElem)
```

- [ ] **Step 3: 删除文件**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/ImmutableList.java
```

- [ ] **Step 4: 提交**

```bash
./mvnw compile -DskipTests 2>&1 | tail -3
git add -A
git commit -m "refactor: replace jadx ImmutableList with List.of()/List.copyOf()

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5.2: 删除 jadx 侧 `Pair.java` + `GsonUtils.java` + `CacheStorage.java`

- [ ] **Step 1: 替换 jadx `Pair` → `util/collection/Pair`**

```bash
# 查找引用
grep -rn "import.*jadx.*\.Pair" src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java"
```
逐一替换 import 和 `new Pair<>(a, b)` → `Pair.of(a, b)`。

- [ ] **Step 2: 删除 GsonUtils.java**

确认 jadx 中 GsonUtils 的使用场景。如果只是构建 Gson 实例，改为内联 `new GsonBuilder().create()`。

- [ ] **Step 3: 内联 CacheStorage**

`CacheStorage` 是 `Set<String>` 的包装。删除该类，将引用改为直接使用 `Set<String>`。

- [ ] **Step 4: 删除文件 + 提交**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/Pair.java
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/GsonUtils.java
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/core/utils/CacheStorage.java
./mvnw compile -DskipTests 2>&1 | tail -3
git add -A
git commit -m "refactor: delete jadx Pair, GsonUtils, CacheStorage; use util equivalents

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5.3: 删除 jadx `api/plugins/utils/Utils.java`

- [ ] **Step 1: 将剩余引用迁移到 `core/utils/Utils.java` 或相关工具**

```bash
grep -rn "import.*jadx.*plugins.*utils\.Utils" src/main/java/com/bingbaihanji/fxdecomplie/core/jadx --include="*.java"
```

方法迁移：
- `Utils.addToList()` → 内联
- `Utils.concat()` → `Stream.concat()`
- `Utils.listToStr()` → `String.join()` / `Collectors.joining()`
- `Utils.formatOffset()` → 统一使用 `core/utils/InsnUtils.formatOffset()`

- [ ] **Step 2: 删除文件 + 编译 + 提交**

```bash
rm src/main/java/com/bingbaihanji/fxdecomplie/core/jadx/api/plugins/utils/Utils.java
./mvnw compile -DskipTests 2>&1 | tail -3
git add -A
git commit -m "refactor: delete jadx api/plugins/utils/Utils, merge into core/utils

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5.4: 阶段 5 最终验证

- [ ] **Step 1: 完整编译 + 全部测试**

```bash
./mvnw clean test 2>&1 | tail -20
```
期望: `BUILD SUCCESS`，所有测试通过

- [ ] **Step 2: 最终提交**

```bash
git add -A
git commit -m "chore: phase 5 complete - redundancy cleanup finished

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## 全局验证检查表

- [ ] 每个阶段编译通过：`./mvnw clean compile -DskipTests`
- [ ] 全部测试通过：`./mvnw clean test`
- [ ] jadx 反混淆开关从 SettingsDialog 可控制 jadx 引擎行为
- [ ] 使用 Vineflower 和 jadx 分别反编译标准 Java 类库，结果一致
- [ ] 无 `import` 指向已删除的类（`Consts`, `ListUtils`, `FileUtils`, `StringUtils`, `ImmutableList`, `Pair`, `GsonUtils`, `CacheStorage`）
- [ ] jadx 内部不再有 `HashMap`/`HashSet` 的 import（热路径文件除外——温/冷路径可能保留）

## 回滚策略

每个提交都是独立可回滚的。如需完全撤回所有变更：

```bash
git revert 2214242..HEAD --no-commit
git commit -m "revert: rollback all jadx refactoring changes"
```
