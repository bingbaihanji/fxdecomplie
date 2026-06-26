# 反编译引擎参数配置 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 CFR/Procyon/Vineflower 三个反编译引擎提供可视化参数配置面板，与 Recaf 参数范围对齐，支持常用/高级分组和 JSON 双向同步。

**Architecture:** 新增 `DecompilerParameter` record 作为参数定义模型，每个引擎一个静态 `*Parameters.java` 定义全部参数。在 `SettingsDialog` 反编译标签页内嵌子标签 TabPane，每个引擎的参数按常用/高级分布到两个 TitledPane 折叠面板。面板修改实时同步到 `engineOptions` Map 和 JSON TextArea。

**Tech Stack:** Java 25 + JavaFX 25.0.1 + SLF4J/Logback + Gson（复用现有栈）

---

## File Structure

| Action | File | Responsibility |
|--------|------|----------------|
| **Create** | `src/main/java/.../model/DecompilerParameter.java` | 参数定义 record：key/类型/默认值/i18n/分类 |
| **Create** | `src/main/java/.../decompiler/CfrParameters.java` | CFR 77 项参数静态列表 + 常用/高级分类 |
| **Create** | `src/main/java/.../decompiler/ProcyonParameters.java` | Procyon 21 项参数静态列表 |
| **Create** | `src/main/java/.../decompiler/VineflowerParameters.java` | Vineflower 47 项参数静态列表（含 VF 原生 key 映射） |
| **Modify** | `src/main/java/.../decompiler/ProcyonDecompiler.java:20-44` | `applyOptions()` 扩展覆盖全部 21 个参数 |
| **Modify** | `src/main/java/.../ui/settings/SettingsDialog.java:54-83` | 反编译标签页重构：子标签 + 参数面板 + JSON 同步 + I18nRefreshable |
| **Modify** | `src/main/java/.../config/AppConfig.java:390-391` | `normalize()` 默认值填充（已有空 Map 初始化，仅确认） |
| **Modify** | `src/main/resources/language/language_zh_CN.properties` | 增量添加 ~145 个参数 i18n key（中英文） |
| **Modify** | `src/main/resources/language/language_en.properties` | 增量添加 ~145 个参数 i18n key（英文） |

---

### Task 1: Create DecompilerParameter record

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/DecompilerParameter.java`

- [ ] **Step 1: Write the record**

```java
package com.bingbaihanji.fxdecomplie.model;

/**
 * 反编译引擎单个可配置参数的定义。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public record DecompilerParameter(
        String key,
        ParamType type,
        String defaultValue,
        String i18nKey,
        String helpKey,
        Category category,
        String[] enumValues) {

    public enum ParamType { BOOLEAN, INTEGER, STRING, ENUM }
    public enum Category { COMMON, ADVANCED }

    /** 返回 fallback 英文标签：将 camelCase key 转为可读文本 */
    public String fallbackLabel() {
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (Character.isUpperCase(c) && sb.length() > 0) {
                sb.append(' ');
            }
            if (i == 0) {
                c = Character.toUpperCase(c);
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/model/DecompilerParameter.java
git commit -m "feat: add DecompilerParameter record for engine config parameters"
```

---

### Task 2: Create CFR parameters definition

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrParameters.java`

- [ ] **Step 1: Write the complete parameter list**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * CFR 反编译引擎全部可配置参数定义（与 Recaf CfrConfig 对齐）。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class CfrParameters {

    private CfrParameters() { throw new AssertionError("constants"); }

    // ──── 常用 (COMMON) ────

    private static DecompilerParameter of(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.COMMON, null);
    }

    private static DecompilerParameter adv(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }

    private static DecompilerParameter advInt(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.INTEGER, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }

    private static DecompilerParameter advStr(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.STRING, defaultValue,
                "engine.cfr." + key, "engine.cfr." + key + ".help", Category.ADVANCED, null);
    }

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // ── 常用 20 项 ──
            of("decodeenumswitch", "true"),
            of("sugarenums", "true"),
            of("decodestringswitch", "true"),
            of("decodelambdas", "true"),
            of("innerclasses", "true"),
            of("tryresources", "true"),
            of("decodefinally", "true"),
            of("removebadgenerics", "true"),
            of("sugarasserts", "true"),
            of("sugarboxing", "true"),
            of("stringbuilder", "true"),
            of("stringconcat", "true"),
            of("recordtypes", "true"),
            of("sealedclasses", "true"),
            of("switchexpression", "true"),
            of("instanceofpattern", "true"),
            of("previewfeatures", "true"),
            of("showinferrable", "true"),
            of("override", "true"),
            of("eclipse", "true"),

            // ── 高级 57 项 ──
            adv("showversion", "false"),
            adv("hideutf", "false"),
            adv("hidelongstrings", "false"),
            adv("removeboilerplate", "false"),
            adv("removeinnerclasssynthetics", "false"),
            adv("relinkconst", "false"),
            adv("relinkconststring", "false"),
            adv("liftconstructorinit", "false"),
            adv("removedeadmethods", "false"),
            adv("sugarretrolambda", "false"),
            adv("tidymonitors", "false"),
            adv("commentmonitors", "false"),
            adv("lenient", "true"),
            adv("comments", "false"),
            adv("antiobf", "false"),
            adv("obfcontrol", "false"),
            adv("obfattr", "false"),
            adv("constobf", "false"),
            adv("hidebridgemethods", "false"),
            adv("ignoreexceptions", "false"),
            adv("version", "false"),
            adv("labelledblocks", "false"),
            adv("j14classobj", "false"),
            adv("hidelangimports", "false"),
            adv("renamedupmembers", "false"),
            adv("renameillegalidents", "false"),
            adv("renameenumidents", "false"),
            adv("staticinitreturn", "false"),
            adv("usenametable", "false"),
            adv("pullcodecase", "false"),
            adv("elidescala", "false"),
            adv("usesignatures", "false"),
            adv("arrayiter", "false"),
            adv("collectioniter", "false"),
            adv("forbidmethodscopedclasses", "false"),
            adv("forbidanonymousclasses", "false"),
            adv("skipbatchinnerclasses", "false"),
            adv("recover", "true"),
            adv("forcetopsort", ""),
            adv("forloopaggcapture", ""),
            adv("forcetopsortaggress", ""),
            adv("forcetopsortnopull", ""),
            adv("forcecondpropagate", ""),
            adv("reducecondscope", ""),
            adv("forcereturningifs", ""),
            adv("forceexceptionprune", ""),
            adv("aexagg", ""),
            adv("aexagg2", ""),
            adv("recovertypeclash", ""),
            adv("recovertypehints", ""),
            adv("removedeadconditionals", ""),
            adv("aggressivedoextension", ""),
            adv("aggressiveduff", ""),
            adv("allowmalformedswitch", ""),
            adv("ignoreexceptionsalways", ""),
            advInt("renamesmallmembers", "0"),
            advInt("aggressivedocopy", "0"),
            advInt("aggressivesizethreshold", "13000"),
            advStr("forceclassfilever", "")
    );
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/CfrParameters.java
git commit -m "feat: add CFR 77 parameter definitions aligned with Recaf"
```

---

### Task 3: Create Procyon parameters definition

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonParameters.java`

- [ ] **Step 1: Write the complete parameter list**

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;

/**
 * Procyon 反编译引擎全部可配置参数定义（与 Recaf ProcyonConfig 对齐）。
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class ProcyonParameters {

    private ProcyonParameters() { throw new AssertionError("constants"); }

    private static DecompilerParameter of(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.procyon." + key, "engine.procyon." + key + ".help", Category.COMMON, null);
    }

    private static DecompilerParameter adv(String key, String defaultValue) {
        return new DecompilerParameter(key, ParamType.BOOLEAN, defaultValue,
                "engine.procyon." + key, "engine.procyon." + key + ".help", Category.ADVANCED, null);
    }

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // ── 常用 10 项 ──
            of("unicodeOutputEnabled", "true"),
            of("previewFeaturesEnabled", "true"),
            of("showSyntheticMembers", "false"),
            of("forceExplicitImports", "true"),
            of("forceExplicitTypeArguments", "false"),
            of("flattenSwitchBlocks", "false"),
            of("retainRedundantCasts", "false"),
            of("includeLineNumbersInBytecode", "true"),
            of("showDebugLineNumbers", "false"),
            of("simplifyMemberReferences", "false"),

            // ── 高级 11 项 ──
            adv("alwaysGenerateExceptionVariableForCatchBlocks", "true"),
            adv("forceFullyQualifiedReferences", "false"),
            adv("excludeNestedTypes", "false"),
            adv("retainPointlessSwitches", "false"),
            adv("includeErrorDiagnostics", "true"),
            adv("mergeVariables", "false"),
            adv("disableForEachTransforms", "false"),
            new DecompilerParameter("textBlockLineMinimum", ParamType.INTEGER, "3",
                    "engine.procyon.textBlockLineMinimum",
                    "engine.procyon.textBlockLineMinimum.help", Category.ADVANCED, null),
            new DecompilerParameter("forcedCompilerTarget", ParamType.ENUM, "",
                    "engine.procyon.forcedCompilerTarget",
                    "engine.procyon.forcedCompilerTarget.help", Category.ADVANCED,
                    new String[]{"", "JAVA_1", "JAVA_2", "JAVA_3", "JAVA_4", "JAVA_5",
                            "JAVA_6", "JAVA_7", "JAVA_8", "JAVA_9", "JAVA_10", "JAVA_11",
                            "JAVA_12", "JAVA_13", "JAVA_14", "JAVA_15", "JAVA_16", "JAVA_17"}),
            new DecompilerParameter("bytecodeOutputOptions", ParamType.ENUM, "",
                    "engine.procyon.bytecodeOutputOptions",
                    "engine.procyon.bytecodeOutputOptions.help", Category.ADVANCED,
                    new String[]{"", "PREFER_SOURCE", "PREFER_BYTECODE", "BYTECODE_ONLY"}),
            new DecompilerParameter("languageTarget", ParamType.ENUM, "",
                    "engine.procyon.languageTarget",
                    "engine.procyon.languageTarget.help", Category.ADVANCED,
                    new String[]{"", "JAVA", "SCALA", "KOTLIN", "GROOVY"})
    );
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonParameters.java
git commit -m "feat: add Procyon 21 parameter definitions aligned with Recaf"
```

---

### Task 4: Create Vineflower parameters definition

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerParameters.java`

- [ ] **Step 1: Write the complete parameter list**

Vineflower 参数使用 VF 原生缩写 key（如 "dgs"、"rbr"）存入 `engineOptions` Map，UI 上显示可读名称。

```java
package com.bingbaihanji.fxdecomplie.decompiler;

import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.Category;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;

import java.util.List;
import java.util.Map;

/**
 * Vineflower 反编译引擎全部可配置参数定义（与 Recaf VineflowerConfig 对齐）。
 * <p>
 * 参数 key 使用 VF 原生缩写（如 "dgs"、"rbr"），因为 Vineflower 引擎直接
 * 以这些 key 读取选项 Map。UI 显示标签通过 i18n 翻译为可读名称。
 * </p>
 *
 * @author bingbaihanji
 * @date 2026-06-26
 */
public final class VineflowerParameters {

    private VineflowerParameters() { throw new AssertionError("constants"); }

    private static DecompilerParameter of(String vfKey, String defaultValue) {
        return new DecompilerParameter(vfKey, ParamType.BOOLEAN, defaultValue,
                "engine.vineflower." + vfKey, "engine.vineflower." + vfKey + ".help",
                Category.COMMON, null);
    }

    private static DecompilerParameter adv(String vfKey, String defaultValue) {
        return new DecompilerParameter(vfKey, ParamType.BOOLEAN, defaultValue,
                "engine.vineflower." + vfKey, "engine.vineflower." + vfKey + ".help",
                Category.ADVANCED, null);
    }

    public static final List<DecompilerParameter> PARAMETERS = List.of(
            // ── 常用 20 项 ──
            of("din", "1"),           // decompile inner/anonymous classes
            of("dgs", "1"),           // decompile generic signatures
            of("das", "1"),           // decompile assertions
            of("rbr", "1"),           // remove bridge methods
            of("rsy", "1"),           // remove synthetic members
            of("bto", "1"),           // bytecode-to-source optimization
            of("nns", "1"),           // new number representation
            of("ump", "1"),           // use method parameter names
            of("udv", "1"),           // use debug variable names (LVT)
            of("pam", "1"),           // pattern matching
            of("swi", "1"),           // switch expressions
            of("dec", "1"),           // decompile preview features
            of("fdi", "1"),           // decompile finally
            of("rer", "1"),           // remove empty try-catch ranges
            of("ovr", "1"),           // override annotation
            of("isl", "1"),           // inline simple lambdas
            of("tco", "1"),           // ternary conditions
            of("hes", "1"),           // hide empty super
            of("hdc", "1"),           // hide default constructor
            of("den", "1"),           // decompile enums

            // ── 高级 27 项 ──
            adv("asc", "0"),          // ASCII string characters (disable = keep Unicode)
            adv("sns", "0"),          // synthetic members not set
            adv("uto", "0"),          // undefined param type → Object
            adv("ner", "1"),          // no exceptions return (incorporate returns)
            adv("esm", "1"),          // ensure synchronized monitors
            adv("rgn", "1"),          // remove getClass/new calls
            adv("lit", "0"),          // keep literals as-is
            adv("dc4", "1"),          // decompile Java 1.4 class features
            adv("lac", "0"),          // lambda to anonymous class
            adv("bsm", "0"),          // bytecode source mapping
            adv("dcl", "0"),          // dump code lines debug info
            adv("iib", "0"),          // ignore invalid bytecode
            adv("vac", "0"),          // verify anonymous classes
            adv("tcs", "0"),          // ternary constant simplification
            adv("tlf", "1"),          // try loop fix
            adv("shs", "0"),          // show hidden statements
            adv("sst", "1"),          // simplify stack second pass
            adv("vvm", "0"),          // verify variable merges
            adv("ega", "0"),          // explicit generic arguments
            adv("sef", "0"),          // skip extra files
            adv("wia", "1"),          // warn inconsistent inner class attributes
            adv("dbe", "1"),          // dump bytecode on error
            adv("dee", "1"),          // dump exception on error
            adv("dcc", "0"),          // decompiler comments
            adv("sfc", "0"),          // source file comments
            adv("ccd", "0"),          // decompile complex constant dynamics
            adv("fji", "0"),          // force JSR inline

            // Non-boolean params
            new DecompilerParameter("log", ParamType.ENUM, "ERROR",
                    "engine.vineflower.log", "engine.vineflower.log.help",
                    Category.ADVANCED, new String[]{"ERROR", "WARN", "INFO", "DEBUG"}),
            new DecompilerParameter("mpm", ParamType.INTEGER, "60",
                    "engine.vineflower.mpm", "engine.vineflower.mpm.help",
                    Category.ADVANCED, null),
            new DecompilerParameter("pll", ParamType.INTEGER, "130",
                    "engine.vineflower.pll", "engine.vineflower.pll.help",
                    Category.ADVANCED, null),
            new DecompilerParameter("ind", ParamType.STRING, "    ",
                    "engine.vineflower.ind", "engine.vineflower.ind.help",
                    Category.ADVANCED, null)
    );

    /** VF 原生 key → 可读名称映射（用于错误提示和 label 生成） */
    public static final Map<String, String> KEY_LABELS = Map.ofEntries(
            Map.entry("din", "Decompile Inner"),
            Map.entry("dgs", "Decompile Generic Signatures"),
            Map.entry("das", "Decompile Assertions"),
            Map.entry("rbr", "Remove Bridge"),
            Map.entry("rsy", "Remove Synthetic"),
            Map.entry("bto", "Bytecode-to-Source Optimization"),
            Map.entry("nns", "New Number Representation"),
            Map.entry("ump", "Use Method Parameters"),
            Map.entry("udv", "Use Debug Variable Names"),
            Map.entry("pam", "Pattern Matching"),
            Map.entry("swi", "Switch Expressions"),
            Map.entry("dec", "Decompile Preview"),
            Map.entry("fdi", "Decompile Finally"),
            Map.entry("rer", "Remove Empty Ranges"),
            Map.entry("ovr", "Override Annotation"),
            Map.entry("isl", "Inline Simple Lambdas"),
            Map.entry("tco", "Ternary Conditions"),
            Map.entry("hes", "Hide Empty Super"),
            Map.entry("hdc", "Hide Default Constructor"),
            Map.entry("den", "Decompile Enums"),
            Map.entry("asc", "ASCII String Characters"),
            Map.entry("sns", "Synthetic Not Set"),
            Map.entry("uto", "Undefined Param Type Object"),
            Map.entry("ner", "Incorporate Returns"),
            Map.entry("esm", "Ensure Synchronized Monitors"),
            Map.entry("rgn", "Remove GetClass/New"),
            Map.entry("lit", "Keep Literals"),
            Map.entry("dc4", "Decompile Java 1.4"),
            Map.entry("lac", "Lambda to Anonymous Class"),
            Map.entry("bsm", "Bytecode Source Mapping"),
            Map.entry("dcl", "Dump Code Lines"),
            Map.entry("iib", "Ignore Invalid Bytecode"),
            Map.entry("vac", "Verify Anonymous Classes"),
            Map.entry("tcs", "Ternary Constant Simplification"),
            Map.entry("tlf", "Try Loop Fix"),
            Map.entry("shs", "Show Hidden Statements"),
            Map.entry("sst", "Simplify Stack Second Pass"),
            Map.entry("vvm", "Verify Variable Merges"),
            Map.entry("ega", "Explicit Generic Arguments"),
            Map.entry("sef", "Skip Extra Files"),
            Map.entry("wia", "Warn Inconsistent Inner Attributes"),
            Map.entry("dbe", "Dump Bytecode On Error"),
            Map.entry("dee", "Dump Exception On Error"),
            Map.entry("sfc", "Source File Comments"),
            Map.entry("dcc", "Decompiler Comments"),
            Map.entry("ccd", "Decompile Complex Constant Dynamics"),
            Map.entry("fji", "Force JSR Inline"),
            Map.entry("log", "Log Level"),
            Map.entry("mpm", "Max Processing Time (s)"),
            Map.entry("pll", "Preferred Line Length"),
            Map.entry("ind", "Indent String")
    );
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/VineflowerParameters.java
git commit -m "feat: add Vineflower 47 parameter definitions aligned with Recaf"
```

---

### Task 5: Expand ProcyonDecompiler.applyOptions() for all 21 parameters

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonDecompiler.java`

- [ ] **Step 1: Expand the applyOptions switch to handle all 21 Procyon params**

Replace the existing `applyOptions()` method (lines 20-44):

```java
private static void applyOptions(DecompilerSettings settings, DecompilerContext context) {
    if (context == null || !context.hasOptions()) {
        return;
    }
    for (var entry : context.options().entrySet()) {
        String value = entry.getValue();
        switch (entry.getKey()) {
            // 常用
            case "unicodeOutput", "unicodeOutputEnabled" ->
                    settings.setUnicodeOutputEnabled(Boolean.parseBoolean(value));
            case "previewFeatures", "previewFeaturesEnabled" ->
                    settings.setPreviewFeaturesEnabled(Boolean.parseBoolean(value));
            case "showSyntheticMembers" -> settings.setShowSyntheticMembers(Boolean.parseBoolean(value));
            case "forceExplicitImports" -> settings.setForceExplicitImports(Boolean.parseBoolean(value));
            case "forceExplicitTypeArguments" ->
                    settings.setForceExplicitTypeArguments(Boolean.parseBoolean(value));
            case "flattenSwitchBlocks" -> settings.setFlattenSwitchBlocks(Boolean.parseBoolean(value));
            case "retainRedundantCasts" -> settings.setRetainRedundantCasts(Boolean.parseBoolean(value));
            case "includeLineNumbersInBytecode" ->
                    settings.setIncludeLineNumbersInBytecode(Boolean.parseBoolean(value));
            case "showDebugLineNumbers" -> settings.setShowDebugLineNumbers(Boolean.parseBoolean(value));
            case "simplifyMemberReferences" -> settings.setSimplifyMemberReferences(Boolean.parseBoolean(value));
            // 高级
            case "alwaysGenerateExceptionVariableForCatchBlocks" ->
                    settings.setAlwaysGenerateExceptionVariableForCatchBlocks(Boolean.parseBoolean(value));
            case "forceFullyQualifiedReferences" ->
                    settings.setForceFullyQualifiedReferences(Boolean.parseBoolean(value));
            case "excludeNestedTypes" -> settings.setExcludeNestedTypes(Boolean.parseBoolean(value));
            case "retainPointlessSwitches" -> settings.setRetainPointlessSwitches(Boolean.parseBoolean(value));
            case "includeErrorDiagnostics" -> settings.setIncludeErrorDiagnostics(Boolean.parseBoolean(value));
            case "mergeVariables" -> settings.setMergeVariables(Boolean.parseBoolean(value));
            case "disableForEachTransforms" -> settings.setDisableForEachTransforms(Boolean.parseBoolean(value));
            case "textBlockLineMinimum" -> {
                try { settings.setTextBlockLineMinimum(Integer.parseInt(value)); }
                catch (NumberFormatException ignored) {}
            }
            case "forcedCompilerTarget" -> {
                if (value != null && !value.isBlank()) {
                    try {
                        settings.setForcedCompilerTarget(
                                com.strobel.decompiler.CompilerTarget.valueOf(value));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            case "languageTarget" -> {
                if (value != null && !value.isBlank()) {
                    settings.setLanguage(switch (value) {
                        case "SCALA" -> com.strobel.decompiler.languages.Languages.scala();
                        case "KOTLIN" -> com.strobel.decompiler.languages.Languages.kotlin();
                        case "GROOVY" -> com.strobel.decompiler.languages.Languages.groovy();
                        default -> com.strobel.decompiler.languages.Languages.java();
                    });
                }
            }
            default -> {
                // Unknown Procyon option — silently ignored
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/decompiler/ProcyonDecompiler.java
git commit -m "feat: expand Procyon applyOptions to cover all 21 parameters"
```

---

### Task 6: Refactor SettingsDialog decompiler tab with sub-tabs and parameter panels

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java`

This is the largest task. We need to:
1. Add an inner sub-TabPane with CFR/Procyon/VF tabs
2. Build parameter panels using `createParameterPanel()` for each engine
3. Add JSON ↔ panel bidirectional sync
4. Implement `I18nRefreshable`

- [ ] **Step 1: Add imports and implement I18nRefreshable on SettingsDialog**

Add these imports to SettingsDialog.java:
```java
import com.bingbaihanji.fxdecomplie.decompiler.CfrParameters;
import com.bingbaihanji.fxdecomplie.decompiler.ProcyonParameters;
import com.bingbaihanji.fxdecomplie.decompiler.VineflowerParameters;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter;
import com.bingbaihanji.fxdecomplie.model.DecompilerParameter.ParamType;
import com.bingbaihanji.fxdecomplie.utils.I18nRefreshable;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.GridPane;
```

- [ ] **Step 2: Replace the decompiler tab content (lines 54-83) with the new structure**

Replace lines 54-83 (from `Tab decompilerTab = ...` to `engineOptionsArea));`) with the sub-tabbed structure:

```java
// ── 反编译器标签页 ──
Tab decompilerTab = new Tab(I18nUtil.getString("settings.decompiler"));
decompilerTab.setClosable(false);

// 默认引擎选择（现有控件，保持不变）
ComboBox<String> engineCombo = new ComboBox<>();
engineCombo.getItems().addAll("PROCYON", "CFR", "VINEFLOWER", "JD");
engineCombo.setValue(config.decompiler().defaultEngine().name());

// 引擎选项 JSON 编辑器（移到独立 TitledPane 中，默认折叠）
Label engineOptionsLabel = new Label(I18nUtil.getString("settings.decompiler.engineOptions"));
engineOptionsLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");

TextArea engineOptionsArea = new TextArea();
engineOptionsArea.setPromptText(I18nUtil.getString("settings.decompiler.engineOptionsHint"));
engineOptionsArea.setPrefRowCount(6);
engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
        + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");

// 初始化 JSON
refreshEngineOptionsJson(config, engineOptionsArea);

// ── 引擎参数子标签页 ──
TabPane engineTabPane = new TabPane();

// CFR 面板
Tab cfrTab = new Tab("CFR");
cfrTab.setClosable(false);
cfrTab.setContent(buildEngineParameterPanel(config, "CFR", CfrParameters.PARAMETERS,
        engineOptionsArea));

// Procyon 面板
Tab procyonTab = new Tab("Procyon");
procyonTab.setClosable(false);
procyonTab.setContent(buildEngineParameterPanel(config, "PROCYON", ProcyonParameters.PARAMETERS,
        engineOptionsArea));

// Vineflower 面板
Tab vfTab = new Tab("Vineflower");
vfTab.setClosable(false);
vfTab.setContent(buildEngineParameterPanel(config, "VINEFLOWER", VineflowerParameters.PARAMETERS,
        engineOptionsArea));

engineTabPane.getTabs().addAll(cfrTab, procyonTab, vfTab);

// JSON 编辑器折叠面板
TitledPane jsonPane = new TitledPane();
jsonPane.setText(I18nUtil.getString("settings.decompiler.engineOptionsJson"));
jsonPane.setExpanded(false);
jsonPane.setContent(new VBox(5, engineOptionsLabel, engineOptionsArea));

VBox decompilerContent = new VBox(10,
        new Label(I18nUtil.getString("settings.defaultEngine")), engineCombo,
        engineTabPane,
        jsonPane);
decompilerTab.setContent(decompilerContent);
```

- [ ] **Step 3: Add buildEngineParameterPanel() helper method**

```java
/**
 * 为指定引擎构建参数配置面板。
 * 参数按常用/高级分组到两个 TitledPane 中。
 */
private static VBox buildEngineParameterPanel(AppConfig config, String engineName,
                                               List<DecompilerParameter> params,
                                               TextArea jsonArea) {
    Map<String, String> engineOpts = config.decompiler().engineOptions()
            .getOrDefault(engineName, new LinkedHashMap<>());

    // 分离常用和高级参数
    List<DecompilerParameter> common = new ArrayList<>();
    List<DecompilerParameter> advanced = new ArrayList<>();
    for (DecompilerParameter p : params) {
        if (p.category() == DecompilerParameter.Category.COMMON) {
            common.add(p);
        } else {
            advanced.add(p);
        }
    }

    var commonPane = buildTitledParameterPane("settings.engine.common", common, engineOpts, jsonArea);
    var advancedPane = buildTitledParameterPane("settings.engine.advanced", advanced, engineOpts, jsonArea);
    advancedPane.setExpanded(false);

    ScrollPane scroll = new ScrollPane(new VBox(5, commonPane, advancedPane));
    scroll.setFitToWidth(true);
    scroll.setStyle("-fx-background: #2b2b2b; -fx-background-color: #2b2b2b;");

    return new VBox(scroll);
}

/**
 * 为参数列表构建一个 TitledPane，内含 GridPane。
 * 每行：[控件] [标签]
 */
private static TitledPane buildTitledParameterPane(String titleKey,
                                                     List<DecompilerParameter> params,
                                                     Map<String, String> engineOpts,
                                                     TextArea jsonArea) {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(5);
    grid.setStyle("-fx-padding: 8;");

    for (int i = 0; i < params.size(); i++) {
        DecompilerParameter param = params.get(i);
        String labelText = I18nUtil.getStringOrDefault(param.i18nKey(), param.fallbackLabel());
        String helpText = I18nUtil.getStringOrDefault(param.helpKey(), "");

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 12px;");
        if (!helpText.isEmpty()) {
            lbl.setTooltip(new Tooltip(helpText));
        }

        String currentValue = engineOpts.getOrDefault(param.key(), param.defaultValue());
        javafx.scene.Node control = createParameterControl(param, currentValue, () -> {
            // 值变化回调: 更新 engineOpts Map → 刷新 JSON
            updateEngineOptionFromControl(param.key(), control, engineOpts);
            syncJsonFromMap(engineOpts, jsonArea);
        });

        grid.add(control, 0, i);
        grid.add(lbl, 1, i);
    }

    TitledPane pane = new TitledPane();
    pane.setText(I18nUtil.getStringOrDefault(titleKey, titleKey));
    pane.setContent(grid);
    return pane;
}

/**
 * 根据参数类型创建对应的控件。
 */
private static javafx.scene.Node createParameterControl(DecompilerParameter param,
                                                          String currentValue,
                                                          Runnable onChange) {
    return switch (param.type()) {
        case BOOLEAN -> {
            CheckBox cb = new CheckBox();
            cb.setSelected("true".equalsIgnoreCase(currentValue) || "1".equals(currentValue));
            cb.selectedProperty().addListener((obs, old, val) -> onChange.run());
            yield cb;
        }
        case INTEGER -> {
            Spinner<Integer> spinner = new Spinner<>(0, Integer.MAX_VALUE,
                    parseOrDefault(currentValue, 0), 1);
            spinner.setEditable(true);
            spinner.setPrefWidth(100);
            spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(
                    change -> change.getControlNewText().matches("\\d*") ? change : null));
            spinner.valueProperty().addListener((obs, old, val) -> onChange.run());
            yield spinner;
        }
        case STRING -> {
            TextField tf = new TextField(currentValue != null ? currentValue : "");
            tf.setPrefWidth(120);
            tf.textProperty().addListener((obs, old, val) -> onChange.run());
            yield tf;
        }
        case ENUM -> {
            ComboBox<String> combo = new ComboBox<>();
            if (param.enumValues() != null) {
                combo.getItems().addAll(param.enumValues());
            }
            if (currentValue != null && !currentValue.isEmpty()) {
                combo.setValue(currentValue);
            } else if (param.enumValues() != null && param.enumValues().length > 0) {
                combo.setValue(param.enumValues()[0]);
            }
            combo.valueProperty().addListener((obs, old, val) -> onChange.run());
            yield combo;
        }
    };
}

/** 从控件读取当前值，更新 engineOpts Map */
private static void updateEngineOptionFromControl(String key, javafx.scene.Node control,
                                                    Map<String, String> engineOpts) {
    String value = switch (control) {
        case CheckBox cb -> cb.isSelected() ? "true" : "false";
        case Spinner<?> sp -> sp.getValue().toString();
        case TextField tf -> tf.getText();
        case ComboBox<?> combo -> {
            Object val = combo.getValue();
            yield val != null ? val.toString() : "";
        }
        default -> "";
    };
    engineOpts.put(key, value);
}

/** 将 engineOpts Map 序列化到 JSON TextArea */
private static void syncJsonFromMap(Map<String, String> engineOpts, TextArea jsonArea) {
    if (jsonArea.isFocused()) {
        return; // 用户正在手动编辑 JSON，跳过自动更新
    }
    try {
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(engineOpts);
        jsonArea.setText(("{}".equals(json)) ? "" : json);
    } catch (Exception ignored) {}
}

/** 刷新引擎选项 JSON（从 AppConfig 加载） */
private static void refreshEngineOptionsJson(AppConfig config, TextArea jsonArea) {
    try {
        String json = new GsonBuilder().setPrettyPrinting().create()
                .toJson(config.decompiler().engineOptions());
        jsonArea.setText(("{}".equals(json) || "null".equals(json)) ? "" : json);
    } catch (Exception ignored) {
        jsonArea.setText("");
    }
}

private static int parseOrDefault(String s, int def) {
    try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
}
```

- [ ] **Step 4: Add JSON TextArea focus-lost listener for JSON→panel sync**

Add after `refreshEngineOptionsJson` call in the decompiler tab setup. The `engineOptionsArea.focusedProperty()` listener should parse JSON and rebuild panels. Since rebuilding panels from JSON is complex (need to iterate all controls), we take a simpler approach: on focus lost, try to parse JSON and update AppConfig.engineOptions, then notify user to re-open settings to see changes.

Actually, a simpler approach: add a "Parse JSON" button next to the JSON area, or just handle it on apply. The spec says "JSON TextArea blurs → parse → backfill panel". But backfilling panels requires tracking all controls. Let me use a Map<String, javafx.scene.Node> to track controls by key, enabling backfill.

Add these fields at method level in `show()`:

```java
// 跟踪每个引擎的参数控件 (key → control)，用于 JSON → 面板回填
Map<String, Map<String, javafx.scene.Node>> engineControlMaps = new HashMap<>();
engineControlMaps.put("CFR", new LinkedHashMap<>());
engineControlMaps.put("PROCYON", new LinkedHashMap<>());
engineControlMaps.put("VINEFLOWER", new LinkedHashMap<>());
```

Modify `buildTitledParameterPane` to accept and populate the control map:

```java
private static TitledPane buildTitledParameterPane(String titleKey,
                                                     List<DecompilerParameter> params,
                                                     Map<String, String> engineOpts,
                                                     TextArea jsonArea,
                                                     Map<String, javafx.scene.Node> controlMap) {
    // ... same as before, but add after creating control:
    controlMap.put(param.key(), control);
    // ...
}
```

Add JSON area focus listener:

```java
// JSON 失焦 → 解析 → 回填面板控件
engineOptionsArea.focusedProperty().addListener((obs, old, focused) -> {
    if (!focused) { // 失去焦点
        String jsonText = engineOptionsArea.getText();
        if (jsonText == null || jsonText.isBlank()) return;
        try {
            Map<String, Map<String, String>> allOpts = new Gson().fromJson(jsonText,
                    new TypeToken<Map<String, Map<String, String>>>(){}.getType());
            // 回填当前可见引擎的面板
            String activeEngine = getActiveEngineName(engineTabPane);
            Map<String, String> engineOpts = allOpts.getOrDefault(activeEngine, Map.of());
            Map<String, javafx.scene.Node> controls = engineControlMaps.get(activeEngine);
            if (controls != null) {
                for (var entry : engineOpts.entrySet()) {
                    javafx.scene.Node ctrl = controls.get(entry.getKey());
                    if (ctrl != null) {
                        setControlValue(ctrl, entry.getValue());
                    }
                }
            }
            engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                    + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px;");
        } catch (Exception ex) {
            // 解析失败: 标红提示
            engineOptionsArea.setStyle("-fx-control-inner-background: #3c3c3c; -fx-text-fill: #cccccc; "
                    + "-fx-font-family: 'Consolas', monospace; -fx-font-size: 12px; "
                    + "-fx-border-color: #f44747; -fx-border-width: 1px;");
        }
    }
});
```

Helper methods:

```java
private static String getActiveEngineName(TabPane engineTabPane) {
    Tab selected = engineTabPane.getSelectionModel().getSelectedItem();
    if (selected == null) return "VINEFLOWER";
    return switch (selected.getText()) {
        case "CFR" -> "CFR";
        case "Procyon" -> "PROCYON";
        case "Vineflower" -> "VINEFLOWER";
        default -> "VINEFLOWER";
    };
}

private static void setControlValue(javafx.scene.Node control, String value) {
    switch (control) {
        case CheckBox cb -> cb.setSelected("true".equalsIgnoreCase(value) || "1".equals(value));
        case Spinner<?> sp -> {
            try { ((Spinner<Integer>) sp).getValueFactory().setValue(Integer.parseInt(value)); }
            catch (NumberFormatException ignored) {}
        }
        case TextField tf -> tf.setText(value != null ? value : "");
        case ComboBox<?> combo -> {
            if (value != null && combo.getItems().contains(value)) {
                ((ComboBox<String>) combo).setValue(value);
            }
        }
        default -> {}
    }
}
```

- [ ] **Step 5: Update the apply button handler**

In the `dialog.showAndWait()` result handler, update the engineOptions saving (the existing lines 237-251):

Replace lines 237-251 with:

```java
// 保存引擎选项（已通过面板控件实时更新到 engineControlMaps + engineOptions Map）
// 直接使用 dialog 关闭前各面板的 engineOpts Map
try {
    // Flush all engine opts from control maps to config
    for (var engineEntry : engineControlMaps.entrySet()) {
        String engName = engineEntry.getKey();
        Map<String, String> engOpts = config.decompiler().engineOptions()
                .computeIfAbsent(engName, k -> new LinkedHashMap<>());
        for (var ctrlEntry : engineEntry.getValue().entrySet()) {
            String key = ctrlEntry.getKey();
            String val = readControlValue(ctrlEntry.getValue());
            if (val != null && !val.isEmpty()) {
                engOpts.put(key, val);
            }
        }
    }
    // Also merge JSON area if user manually edited it
    String json = engineOptionsArea.getText();
    if (json != null && !json.isBlank()) {
        try {
            Map<String, Map<String, String>> jsonOpts = new Gson().fromJson(json,
                    new TypeToken<Map<String, Map<String, String>>>(){}.getType());
            for (var entry : jsonOpts.entrySet()) {
                config.decompiler().engineOptions()
                        .computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>())
                        .putAll(entry.getValue());
            }
        } catch (Exception ignored) {}
    }
} catch (Exception ignored) {
    logger.warn("保存引擎选项失败", ignored);
}
```

And add:

```java
private static String readControlValue(javafx.scene.Node control) {
    return switch (control) {
        case CheckBox cb -> String.valueOf(cb.isSelected());
        case Spinner<?> sp -> sp.getValue().toString();
        case TextField tf -> tf.getText();
        case ComboBox<?> combo -> {
            Object val = combo.getValue();
            yield val != null ? val.toString() : "";
        }
        default -> "";
    };
}
```

- [ ] **Step 6: Verify compilation**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/settings/SettingsDialog.java
git commit -m "feat: add per-engine sub-tab parameter panels with JSON sync"
```

---

### Task 7: Add i18n keys for settings tabs

**Files:**
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: Add new i18n keys to both files**

Append to `language_zh_CN.properties`:
```properties
# 引擎参数面板
settings.engine.common=常用参数
settings.engine.advanced=高级参数
settings.decompiler.engineOptionsJson=引擎选项 JSON (高级)
engine.cfr=CFR 参数
engine.procyon=Procyon 参数
engine.vineflower=Vineflower 参数
```

Append to `language_en.properties`:
```properties
# Engine parameter panels
settings.engine.common=Common
settings.engine.advanced=Advanced
settings.decompiler.engineOptionsJson=Engine Options JSON (Advanced)
engine.cfr=CFR Parameters
engine.procyon=Procyon Parameters
engine.vineflower=Vineflower Parameters
```

Note: Individual parameter labels (145+ keys) will use `getStringOrDefault()` fallback mechanism — `DecompilerParameter.fallbackLabel()` generates readable English labels from camelCase keys automatically. Translation to Chinese can be added incrementally later.

- [ ] **Step 2: Verify the build still passes**

Run: `./mvnw compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
git commit -m "i18n: add engine parameter panel UI keys"
```

---

### Task 8: Verify AppConfig.normalize() handles engineOptions

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java`

- [ ] **Step 1: Confirm existing normalize() already initializes engineOptions**

Read `AppConfig.java` `normalize()` method to verify this line exists:
```java
if (decompiler.engineOptions == null) {
    decompiler.engineOptions = new LinkedHashMap<>();
}
```

If it does (already verified at line ~430-432 area), no changes needed. If not, add it.

- [ ] **Step 2: Commit (if changes needed)**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/config/AppConfig.java
git commit -m "fix: ensure engineOptions initialized in normalize()"
```

---

### Task 9: Full compilation and smoke test

- [ ] **Step 1: Full clean compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

Run: `./mvnw test`
Expected: All tests pass

- [ ] **Step 3: Run the application**

Run: `./mvnw exec:java`
Manual check:
1. Open 编辑 → 设置 → 反编译
2. Verify three sub-tabs (CFR / Procyon / Vineflower) appear
3. Verify common parameters are expanded, advanced are collapsed
4. Toggle a checkbox → verify JSON area updates
5. Edit JSON → lose focus → verify panel backfills
6. Enter invalid JSON → verify red border
7. Click Apply → close and reopen settings → verify settings persisted

- [ ] **Step 4: Commit any final fixes**

```bash
git add -A
git commit -m "chore: final adjustments after smoke test"
```
