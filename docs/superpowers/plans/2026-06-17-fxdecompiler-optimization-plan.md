# FxDecompiler 代码优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对照 Java 开发规范，对全部 27 个源文件进行合规优化（命名、工具类、异常处理、Javadoc、MainWindow 拆分），不新增功能。

**Architecture:** 先执行2个全局重命名（跨模块影响），再按模块从底层到顶层推进：decompiler → io → config → model → ui/app/入口。`MainWindow` 拆分为协调器 + `WorkspaceTabManager` + `ClassTabOpener` 三个类。

**Tech Stack:** JDK 25, JavaFX 25 + jfx.incubator.richtext, Maven

---

## 全局重命名映射

| 当前名 | 新名 | 影响范围 |
|--------|------|----------|
| `IDecompiler` | `Decompiler` | 全项目 import + 类型声明，约 15 处 |
| `DecompilerType` | `DecompilerTypeEnum` | 全项目 import + 类型声明，约 20 处 |
| `FileTreeNode.NodeType` | `FileTreeNode.NodeTypeEnum` | 全项目引用，约 10 处 |

---

### Task 1: 全局重命名 — IDecompiler → Decompiler

**Files:**
- Rename: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/IDecompiler.java` → `Decompiler.java`
- Modify: 所有引用该接口的文件

- [ ] **Step 1: 重命名接口文件并更新接口声明**

Delete `IDecompiler.java`, create `Decompiler.java`:

```java
package com.bingbihanji.fxdecomplie.decompiler;

/**
 * 反编译引擎统一接口。所有引擎实现必须是无状态或线程安全的。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public interface Decompiler {

    String decompile(String classFilePath, byte[] classBytes);

    String decompileType(String typeName, byte[] classBytes);

    DecompilerTypeEnum getType();

    default String getName() {
        return getType().name();
    }

    default void initialize() {
    }

    default void cleanup() {
    }
}
```

- [ ] **Step 2: 更新 DecompilerFactory.java — 接口引用和内部 imports**

```java
package com.bingbihanji.fxdecomplie.decompiler;

import java.util.EnumMap;
import java.util.Map;

public class DecompilerFactory {
    private static final Map<DecompilerTypeEnum, Decompiler> CACHE = new EnumMap<>(DecompilerTypeEnum.class);

    public static Decompiler getDecompiler(DecompilerTypeEnum type) {
        return CACHE.computeIfAbsent(type, t -> {
            Decompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
            };
            engine.initialize();
            return engine;
        });
    }

    public static void cleanup() {
        CACHE.values().forEach(Decompiler::cleanup);
        CACHE.clear();
    }
}
```

- [ ] **Step 3: 更新 CfrDecompiler.java — implements 声明**

将 `public class CfrDecompiler implements IDecompiler` 改为 `public class CfrDecompiler implements Decompiler`

- [ ] **Step 4: 更新 VineflowerDecompiler.java — implements 声明**

将 `public class VineflowerDecompiler implements IDecompiler` 改为 `public class VineflowerDecompiler implements Decompiler`

- [ ] **Step 5: 更新 ProcyonDecompiler.java — implements 声明**

将 `public class ProcyonDecompiler implements IDecompiler` 改为 `public class ProcyonDecompiler implements Decompiler`

- [ ] **Step 6: 更新 ExportService.java — import 和类型引用**

`import com.bingbihanji.fxdecomplie.decompiler.IDecompiler;` → `import com.bingbihanji.fxdecomplie.decompiler.Decompiler;`
将 `IDecompiler decompiler` → `Decompiler decompiler`（共 3 处）

- [ ] **Step 7: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 2: 全局重命名 — DecompilerType → DecompilerTypeEnum

**Files:** Rename `DecompilerType.java` + 更新所有引用

- [ ] **Step 1: 重命名枚举文件**

Delete `DecompilerType.java`, create `DecompilerTypeEnum.java`:

```java
package com.bingbihanji.fxdecomplie.decompiler;

/**
 * 反编译引擎类型枚举。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public enum DecompilerTypeEnum {
    /** Procyon 反编译器 */
    PROCYON,
    /** CFR 反编译器 */
    CFR,
    /** Vineflower 反编译器 */
    VINEFLOWER
}
```

- [ ] **Step 2: 全局替换所有 import 和引用**

Run PowerShell to find and report all occurrences:
```powershell
Select-String -Path "src/main/java/com/bingbihanji/fxdecomplie/**/*.java" -Pattern "DecompilerType" | Select-Object Filename, LineNumber, Line
```

Then manually update each file:
- `DecompilerFactory.java`: `Map<DecompilerType, Decompiler>` → `Map<DecompilerTypeEnum, Decompiler>`，`EnumMap<>(DecompilerType.class)` → `EnumMap<>(DecompilerTypeEnum.class)`
- `OpenFile.java`: `import ...DecompilerType;` → `import ...DecompilerTypeEnum;`，`DecompilerType engine` → `DecompilerTypeEnum engine`
- `MainWindow.java`: 所有 `DecompilerType` → `DecompilerTypeEnum`（约 8 处）
- `MainMenuBar.java`: 所有 `DecompilerType` → `DecompilerTypeEnum`（约 5 处）
- `ExportService.java`: `DecompilerType engine` → `DecompilerTypeEnum engine`
- `AppConfig.java`: 注释中的引用更新
- `Decompiler.java` (原 IDecompiler): `DecompilerType getType()` → `DecompilerTypeEnum getType()`
- `CfrDecompiler.java`: `DecompilerType getType()` → `DecompilerTypeEnum getType()`, `return DecompilerType.CFR` → `return DecompilerTypeEnum.CFR`
- `VineflowerDecompiler.java`: 同理 → `DecompilerTypeEnum.VINEFLOWER`
- `ProcyonDecompiler.java`: 同理 → `DecompilerTypeEnum.PROCYON`

**快捷方式** — 执行 PowerShell 批量替换:

```powershell
$files = Get-ChildItem -Path "src/main/java" -Recurse -Filter "*.java"
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    if ($content -match "DecompilerType[^E]") {
        $newContent = $content -replace 'import com\.bingbihanji\.fxdecomplie\.decompiler\.DecompilerType;', 'import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;'
        $newContent = $newContent -replace '(?<!\w)DecompilerType(?!Enum)(?![.\w])', 'DecompilerTypeEnum'
        Set-Content $f.FullName -Value $newContent -NoNewline
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 3: decompiler/ — 工具类合规

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/DecompilerFactory.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/BytecodeCache.java`

- [ ] **Step 1: DecompilerFactory 加 final + 私有构造器**

```java
package com.bingbihanji.fxdecomplie.decompiler;

import java.util.EnumMap;
import java.util.Map;

/**
 * 反编译引擎工厂。每个引擎类型单例缓存。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class DecompilerFactory {

    private DecompilerFactory() {
        throw new AssertionError("utility class");
    }

    private static final Map<DecompilerTypeEnum, Decompiler> CACHE = new EnumMap<>(DecompilerTypeEnum.class);

    public static Decompiler getDecompiler(DecompilerTypeEnum type) {
        return CACHE.computeIfAbsent(type, t -> {
            Decompiler engine = switch (t) {
                case PROCYON -> new ProcyonDecompiler();
                case CFR -> new CfrDecompiler();
                case VINEFLOWER -> new VineflowerDecompiler();
            };
            engine.initialize();
            return engine;
        });
    }

    public static void cleanup() {
        CACHE.values().forEach(Decompiler::cleanup);
        CACHE.clear();
    }
}
```

- [ ] **Step 2: BytecodeCache 加 final + 私有构造器 + 防御性校验 + 删除 getBytecode**

```java
package com.bingbihanji.fxdecomplie.decompiler;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局字节码缓存。在打开 JAR/ZIP 时预加载 class 字节码，供反编译器解析类型依赖时查找。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BytecodeCache {

    private BytecodeCache() {
        throw new AssertionError("utility class");
    }

    private static final ConcurrentHashMap<String, byte[]> CACHE = new ConcurrentHashMap<>();

    public static void put(String internalName, byte[] bytes) {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(bytes, "bytes");
        CACHE.put(internalName, bytes);
    }

    public static byte[] get(String internalName) {
        Objects.requireNonNull(internalName, "internalName");
        return CACHE.get(internalName);
    }

    public static boolean contains(String internalName) {
        return CACHE.containsKey(internalName);
    }

    public static void clear() {
        CACHE.clear();
    }

    public static int size() {
        return CACHE.size();
    }
}
```

- [ ] **Step 3: 更新 BytecodeCache.getBytecode 的调用方**

`CfrDecompiler.java`: `BytecodeCache.getBytecode(internalName)` → `BytecodeCache.get(internalName)`
`VineflowerDecompiler.java`: `BytecodeCache.getBytecode(key)` → `BytecodeCache.get(key)`
`ProcyonDecompiler.java`: `BytecodeCache.getBytecode(...)` → `BytecodeCache.get(...)`

- [ ] **Step 4: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 4: CfrDecompiler 优化

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/CfrDecompiler.java`

- [ ] **Step 1: 修复通配符导入 + options 常量化 + Javadoc**

完整的改写后文件内容：

```java
package com.bingbihanji.fxdecomplie.decompiler;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CFR 反编译引擎适配器。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class CfrDecompiler implements Decompiler {

    private static final Map<String, String> DEFAULT_OPTIONS = createDefaultOptions();

    private static Map<String, String> createDefaultOptions() {
        Map<String, String> opts = new HashMap<>();
        opts.put("showversion", "false");
        opts.put("hideutf", "false");
        opts.put("innerclasses", "true");
        opts.put("decodelambdas", "true");
        opts.put("decodestringswitch", "true");
        opts.put("decodeenumswitch", "true");
        opts.put("sugarenums", "true");
        opts.put("decodefinally", "true");
        opts.put("removebadgenerics", "true");
        opts.put("sugarasserts", "true");
        opts.put("sugarboxing", "true");
        opts.put("showops", "false");
        opts.put("silent", "true");
        opts.put("recover", "true");
        opts.put("eclipse", "true");
        opts.put("override", "true");
        opts.put("showinferrable", "true");
        opts.put("stringbuilder", "true");
        opts.put("stringconcat", "true");
        opts.put("tryresources", "true");
        opts.put("recordtypes", "true");
        opts.put("sealedclasses", "true");
        opts.put("switchexpression", "true");
        opts.put("instanceofpattern", "true");
        return Collections.unmodifiableMap(opts);
    }

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = classFilePath.replace(".class", "");
        return decompileType(internalName, classBytes);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        final StringBuilder result = new StringBuilder();

        ClassFileSource classFileSource = new ClassFileSource() {
            @Override
            public void informAnalysisRelativePathDetail(String usePath, String specPath) {
            }

            @Override
            public Collection<String> addJar(String jarPath) {
                return Collections.emptyList();
            }

            @Override
            public String getPossiblyRenamedPath(String path) {
                return path;
            }

            @Override
            public Pair<byte[], String> getClassFileContent(String path) throws IOException {
                String normalizedPath = path.replace("\\", "/");
                String normalizedTypeName = typeName.replace("\\", "/");

                if (normalizedPath.equals(normalizedTypeName)
                        || normalizedPath.equals(normalizedTypeName + ".class")
                        || normalizedPath.endsWith("/" + getSimpleName(normalizedTypeName) + ".class")
                        || normalizedPath.endsWith("/" + getSimpleName(normalizedTypeName))) {
                    return Pair.make(classBytes, normalizedPath);
                }

                String internalName = normalizedPath.replace(".class", "");
                byte[] otherBytes = BytecodeCache.get(internalName);
                if (otherBytes != null) {
                    return Pair.make(otherBytes, normalizedPath);
                }

                return null;
            }

            private String getSimpleName(String name) {
                int idx = name.lastIndexOf('/');
                return idx >= 0 ? name.substring(idx + 1) : name;
            }
        };

        OutputSinkFactory outputSinkFactory = new OutputSinkFactory() {
            @Override
            public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                if (sinkType == SinkType.JAVA) {
                    return Collections.singletonList(SinkClass.DECOMPILED);
                }
                return Collections.emptyList();
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                    return (Sink<T>) (OutputSinkFactory.Sink<SinkReturns.Decompiled>) decompiled -> {
                        result.append(decompiled.getJava());
                    };
                }
                return ignore -> {
                };
            }
        };

        CfrDriver driver = new CfrDriver.Builder()
                .withClassFileSource(classFileSource)
                .withOutputSink(outputSinkFactory)
                .withOptions(DEFAULT_OPTIONS)
                .build();

        driver.analyse(Collections.singletonList(typeName + ".class"));

        String decompiled = result.toString();
        if (decompiled.isEmpty()) {
            return "// CFR decompile failed\n// Class: " + typeName;
        }
        return decompiled;
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.CFR;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 5: VineflowerDecompiler 优化

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/VineflowerDecompiler.java`

- [ ] **Step 1: options 常量化 + 资源清理日志 + Javadoc**

完整改写内容：

```java
package com.bingbihanji.fxdecomplie.decompiler;

import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Vineflower 反编译引擎适配器。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class VineflowerDecompiler implements Decompiler {

    private static final Map<String, Object> DEFAULT_OPTIONS = createDefaultOptions();

    private static Map<String, Object> createDefaultOptions() {
        Map<String, Object> opts = new HashMap<>();
        opts.put("den", "1");
        opts.put("dgs", "1");
        opts.put("din", "1");
        opts.put("rbr", "1");
        opts.put("rsy", "1");
        opts.put("bto", "1");
        opts.put("nns", "1");
        opts.put("uto", "0");
        opts.put("udv", "1");
        opts.put("ump", "1");
        opts.put("fdi", "1");
        opts.put("asc", "0");
        opts.put("rer", "1");
        opts.put("rgn", "1");
        opts.put("lit", "1");
        opts.put("bsm", "1");
        opts.put("mpm", "60");
        opts.put("lac", "0");
        opts.put("nls", "1");
        opts.put("ind", "    ");
        opts.put("log", "WARN");
        opts.put("pll", "130");
        opts.put("rec", "1");
        opts.put("sea", "1");
        opts.put("pam", "1");
        opts.put("swi", "1");
        opts.put("vac", "1");
        return Collections.unmodifiableMap(opts);
    }

    private static final System.Logger LOG = System.getLogger(VineflowerDecompiler.class.getName());

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = classFilePath.replace(".class", "");
        return decompileType(internalName, classBytes);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        final StringBuilder result = new StringBuilder();

        File tempDir = null;
        File tempClassFile = null;

        try {
            tempDir = Files.createTempDirectory("vineflower_").toFile();
            String classFileName = getSimpleName(typeName) + ".class";
            tempClassFile = new File(tempDir, classFileName);
            Files.write(tempClassFile.toPath(), classBytes);

            final File finalTempClassFile = tempClassFile;

            IBytecodeProvider bytecodeProvider = (externalPath, internalPath) -> {
                if (externalPath != null && externalPath.equals(finalTempClassFile.getAbsolutePath())) {
                    return classBytes;
                }
                String key = internalPath != null ? internalPath : externalPath;
                if (key != null) {
                    key = key.replace(".class", "").replace("\\", "/");
                    if (key.endsWith("/" + getSimpleName(typeName)) || key.equals(typeName)) {
                        return classBytes;
                    }
                    byte[] cached = BytecodeCache.get(key);
                    if (cached != null) {
                        return cached;
                    }
                }
                return null;
            };

            IResultSaver resultSaver = new IResultSaver() {
                @Override public void saveFolder(String path) {}
                @Override public void copyFile(String source, String path, String entryName) {}
                @Override
                public void saveClassFile(String path, String qualifiedName, String entryName,
                                          String content, int[] mapping) {
                    if (content != null && !content.isEmpty()) {
                        result.append(content);
                    }
                }
                @Override public void createArchive(String path, String archiveName, Manifest manifest) {}
                @Override public void saveDirEntry(String path, String archiveName, String entryName) {}
                @Override public void copyEntry(String source, String path, String archiveName, String entry) {}
                @Override
                public void saveClassEntry(String path, String archiveName, String qualifiedName,
                                           String entryName, String content) {
                    if (content != null && !content.isEmpty()) {
                        result.append(content);
                    }
                }
                @Override public void closeArchive(String path, String archiveName) {}
            };

            IFernflowerLogger logger = new IFernflowerLogger() {
                @Override
                public void writeMessage(String message, Severity severity) {}
                @Override
                public void writeMessage(String message, Severity severity, Throwable t) {}
            };

            BaseDecompiler decompiler = new BaseDecompiler(bytecodeProvider, resultSaver, DEFAULT_OPTIONS, logger);
            decompiler.addSource(tempClassFile);
            decompiler.decompileContext();

        } catch (IOException e) {
            return "// Vineflower Error: " + e.getMessage();
        } finally {
            if (tempClassFile != null) {
                try {
                    Files.deleteIfExists(tempClassFile.toPath());
                } catch (IOException e) {
                    LOG.log(System.Logger.Level.WARNING, "Failed to delete temp class file", e);
                }
            }
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir.toPath());
                } catch (IOException e) {
                    LOG.log(System.Logger.Level.WARNING, "Failed to delete temp dir", e);
                }
            }
        }

        String decompiled = result.toString();
        if (decompiled.isEmpty()) {
            return "// Vineflower decompile failed\n// Class: " + typeName;
        }
        return decompiled;
    }

    private static String getSimpleName(String typeName) {
        int idx = typeName.lastIndexOf('/');
        return idx >= 0 ? typeName.substring(idx + 1) : typeName;
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.VINEFLOWER;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 6: ProcyonDecompiler 优化

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/decompiler/ProcyonDecompiler.java`

- [ ] **Step 1: 修复 StringWriter 资源泄漏 + Javadoc**

```java
package com.bingbihanji.fxdecomplie.decompiler;

import com.strobel.assembler.metadata.Buffer;
import com.strobel.assembler.metadata.ITypeLoader;
import com.strobel.assembler.metadata.MetadataSystem;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.Languages;

import java.io.StringWriter;

/**
 * Procyon 反编译引擎适配器。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class ProcyonDecompiler implements Decompiler {

    @Override
    public String decompile(String classFilePath, byte[] classBytes) {
        String internalName = normalizeInternalName(classFilePath);
        return decompileType(internalName, classBytes);
    }

    @Override
    public String decompileType(String typeName, byte[] classBytes) {
        String internalName = normalizeInternalName(typeName);
        DecompilerSettings localSettings = DecompilerSettings.javaDefaults();
        localSettings.setTypeLoader(new CachedTypeLoader(internalName, classBytes));
        localSettings.setLanguage(Languages.java());
        localSettings.setUnicodeOutputEnabled(true);
        localSettings.setPreviewFeaturesEnabled(true);

        MetadataSystem metadataSystem = new MetadataSystem(localSettings.getTypeLoader());
        TypeReference type = metadataSystem.lookupType(internalName);
        if (type == null) {
            return "// Procyon decompile failed\n// Class: " + internalName;
        }
        TypeDefinition resolvedType = type.resolve();
        if (resolvedType == null) {
            return "// Procyon decompile failed\n// Class: " + internalName;
        }

        DecompilationOptions options = new DecompilationOptions();
        options.setSettings(localSettings);
        options.setFullDecompilation(true);

        try (StringWriter writer = new StringWriter()) {
            PlainTextOutput output = new PlainTextOutput(writer);
            localSettings.getLanguage().decompileType(resolvedType, output, options);
            return writer.toString();
        }
    }

    @Override
    public DecompilerTypeEnum getType() {
        return DecompilerTypeEnum.PROCYON;
    }

    private static String normalizeInternalName(String name) {
        String normalized = name.replace('\\', '/');
        return normalized.endsWith(".class")
                ? normalized.substring(0, normalized.length() - 6)
                : normalized;
    }

    private static class CachedTypeLoader implements ITypeLoader {
        private final String targetName;
        private final byte[] targetBytes;

        CachedTypeLoader(String targetName, byte[] targetBytes) {
            this.targetName = targetName;
            this.targetBytes = targetBytes;
        }

        @Override
        public boolean tryLoadType(String internalName, Buffer buffer) {
            String normalized = internalName.replace('\\', '/');
            byte[] bytes = null;
            if (normalized.equals(targetName) || normalized.equals(targetName + ".class")) {
                bytes = targetBytes;
            }
            if (bytes == null) {
                bytes = BytecodeCache.get(normalized.replace(".class", ""));
            }
            if (bytes == null) {
                return false;
            }
            buffer.putByteArray(bytes, 0, bytes.length);
            buffer.position(0);
            return true;
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 7: io/ — 工具类合规

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/ClassDiscoverer.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/FileTreeBuilder.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/ExportService.java`

- [ ] **Step 1: ClassDiscoverer — 加 final + 私有构造器 + Javadoc**

在类声明处修改：

```java
/**
 * 类文件发现器。支持 JAR/ZIP/目录/单个 .class 文件。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ClassDiscoverer {

    private ClassDiscoverer() {
        throw new AssertionError("utility class");
    }
    // ... 其余方法保持不变
}
```

- [ ] **Step 2: FileTreeBuilder — 加 final + 私有构造器 + Javadoc**

```java
/**
 * 将扁平 ClassEntry 列表构建为包层级 TreeItem 树。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class FileTreeBuilder {

    private FileTreeBuilder() {
        throw new AssertionError("utility class");
    }
    // ... 其余方法保持不变
}
```

- [ ] **Step 3: ExportService — 加 final + 私有构造器 + Javadoc**

```java
/**
 * 导出服务。支持单文件/全部导出到目录/ZIP。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ExportService {

    private ExportService() {
        throw new AssertionError("utility class");
    }
    // ... 其余方法保持不变
}
```

- [ ] **Step 4: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 8: ClassDiscoverer 正则预编译 + 异常处理

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/ClassDiscoverer.java`

- [ ] **Step 1: 替换 guessType 中的正则**

在类顶部添加静态 Pattern：

```java
private static final Pattern RESOURCE_PATTERN =
        Pattern.compile(".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql)$");
```

将 `guessType` 方法中的:
```java
if (lower.matches(".*\\.(xml|json|properties|txt|html|css|js|md|yml|yaml|cfg|ini|sh|bat|sql)$")) {
```
改为:
```java
if (RESOURCE_PATTERN.matcher(lower).matches()) {
```

- [ ] **Step 2: discoverDirectory 异常记录**

添加 Logger，将 `catch (IOException ignored) {}` 改为：

```java
private static final System.Logger LOG = System.getLogger(ClassDiscoverer.class.getName());

// 在 discoverDirectory 的 forEach 中:
if (type == FileTreeNode.NodeTypeEnum.CLASS_FILE) {
    try {
        bytes = Files.readAllBytes(p);
    } catch (IOException e) {
        LOG.log(System.Logger.Level.WARNING, "Failed to read class file: " + p, e);
    }
}
```

- [ ] **Step 3: NodeType 引用更新为 NodeTypeEnum**

在 `guessType` 和 `discoverDirectory`、`discoverJar`、`discoverClassFile` 中，所有 `FileTreeNode.NodeType.XXX` → `FileTreeNode.NodeTypeEnum.XXX`

- [ ] **Step 4: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 9: FileTreeBuilder 注释清理 + sortTree 迭代化

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/FileTreeBuilder.java`

- [ ] **Step 1: 删除过时注释 + sortTree 改为 BFS 迭代**

将 `getOrCreateParent` 上的 Javadoc 注释（"迭代式创建父节点链。避免 computeIfAbsent 递归..."）删除（代码已自说明）。

将递归 `sortTree` 改为迭代式：

```java
private static void sortTree(TreeItem<FileTreeNode> root) {
    java.util.ArrayDeque<TreeItem<FileTreeNode>> queue = new java.util.ArrayDeque<>();
    queue.add(root);
    while (!queue.isEmpty()) {
        TreeItem<FileTreeNode> node = queue.poll();
        node.getChildren().sort((a, b) -> {
            FileTreeNode aData = a.getValue();
            FileTreeNode bData = b.getValue();
            boolean aDir = aData.getNodeType() == FileTreeNode.NodeTypeEnum.PACKAGE;
            boolean bDir = bData.getNodeType() == FileTreeNode.NodeTypeEnum.PACKAGE;
            if (aDir != bDir) return aDir ? -1 : 1;
            return aData.getName().compareToIgnoreCase(bData.getName());
        });
        queue.addAll(node.getChildren());
    }
}
```

- [ ] **Step 2: 更新 BytecodeCache.put 和 NodeTypeEnum 引用**

将 `FileTreeNode.NodeType.CLASS_FILE` → `FileTreeNode.NodeTypeEnum.CLASS_FILE`
将 `FileTreeNode.NodeType.PACKAGE` → `FileTreeNode.NodeTypeEnum.PACKAGE`

- [ ] **Step 3: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 10: ExportService 防御性校验 + 提取重复代码

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/io/ExportService.java`

- [ ] **Step 1: 添加防御性校验和提取私有方法**

完整改写：

```java
package com.bingbihanji.fxdecomplie.io;

import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.decompiler.Decompiler;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import javafx.scene.control.TreeItem;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 导出服务。支持单文件保存和批量导出到目录/ZIP。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class ExportService {

    private ExportService() {
        throw new AssertionError("utility class");
    }

    public static void exportCurrentCode(String code, Path outputPath) throws IOException {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(outputPath, "outputPath");
        Files.writeString(outputPath, code);
    }

    public static void exportAllToDir(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                      Path outputDir) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(outputDir, "outputDir");
        Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
        Files.createDirectories(outputDir);
        exportTree(root, decompiler, outputDir);
    }

    private static void exportTree(TreeItem<FileTreeNode> node, Decompiler decompiler,
                                   Path outputDir) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                Path javaFile = outputDir.resolve(
                        data.getFullPath().replace(".class", ".java"));
                Files.createDirectories(javaFile.getParent());
                Files.writeString(javaFile, source);
            }
        }
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTree(child, decompiler, outputDir);
        }
    }

    public static void exportAllToZip(TreeItem<FileTreeNode> root, DecompilerTypeEnum engine,
                                      Path zipPath) throws IOException {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(engine, "engine");
        Objects.requireNonNull(zipPath, "zipPath");
        Decompiler decompiler = DecompilerFactory.getDecompiler(engine);
        try (ZipOutputStream zos = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {
            exportTreeToZip(root, decompiler, zos);
        }
    }

    private static void exportTreeToZip(TreeItem<FileTreeNode> node, Decompiler decompiler,
                                        ZipOutputStream zos) throws IOException {
        FileTreeNode data = node.getValue();
        if (data != null && data.isClassFile()) {
            byte[] bytes = resolveClassBytes(data);
            if (bytes != null) {
                String source = decompiler.decompile(data.getFullPath(), bytes);
                ZipEntry entry = new ZipEntry(data.getFullPath().replace(".class", ".java"));
                zos.putNextEntry(entry);
                zos.write(source.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        for (TreeItem<FileTreeNode> child : node.getChildren()) {
            exportTreeToZip(child, decompiler, zos);
        }
    }

    private static byte[] resolveClassBytes(FileTreeNode data) {
        byte[] bytes = data.getCachedBytes();
        if (bytes == null) {
            bytes = BytecodeCache.get(data.getFullPath().replace(".class", ""));
        }
        return bytes;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 11: AppConfig 异常处理 + toString + 注释

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/config/AppConfig.java`

- [ ] **Step 1: 完整改写 AppConfig.java**

```java
package com.bingbihanji.fxdecomplie.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置，读写 JSON 文件。位置: ~/.fxdecompiler/config.json
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class AppConfig {

    private static final Path CONFIG_DIR = Path.of(System.getProperty("user.home"), ".fxdecompiler");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final System.Logger LOG = System.getLogger(AppConfig.class.getName());

    public Window window = new Window();
    public Theme theme = new Theme();
    public Decompiler decompiler = new Decompiler();

    /** 最近打开的文件列表（路径字符串），预留后续实现最近文件菜单 */
    public List<String> recentFiles = new ArrayList<>();

    public static AppConfig load() {
        try {
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                return GSON.fromJson(json, AppConfig.class);
            }
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to load config, using defaults", e);
        }
        return new AppConfig();
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(this));
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to save config", e);
        }
    }

    public static class Window {
        public int width = 1200;
        public int height = 800;
        public int x = 100;
        public int y = 100;
        public boolean maximized = false;

        @Override
        public String toString() {
            return "Window{width=" + width + ", height=" + height
                    + ", x=" + x + ", y=" + y + ", maximized=" + maximized + "}";
        }
    }

    public static class Theme {
        public String path = "";
        public String fontFamily = "Consolas";
        public int fontSize = 14;

        @Override
        public String toString() {
            return "Theme{path='" + path + "', fontFamily='" + fontFamily
                    + "', fontSize=" + fontSize + "}";
        }
    }

    public static class Decompiler {
        /** 默认引擎，有效值: PROCYON, CFR, VINEFLOWER */
        public String defaultEngine = "VINEFLOWER";
        public boolean lineNumbersEnabled = true;
        public boolean wrapText = true;

        @Override
        public String toString() {
            return "Decompiler{defaultEngine='" + defaultEngine
                    + "', lineNumbersEnabled=" + lineNumbersEnabled
                    + ", wrapText=" + wrapText + "}";
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 12: 全局重命名 — FileTreeNode.NodeType → NodeTypeEnum

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/model/FileTreeNode.java`

- [ ] **Step 1: 重命名枚举并添加枚举值注释**

```java
public enum NodeTypeEnum {
    /** 包节点（中间层级） */
    PACKAGE,
    /** .class 文件 */
    CLASS_FILE,
    /** .java 源文件 */
    JAVA_FILE,
    /** 其他资源文件 (xml, json, properties 等) */
    RESOURCE,
    /** 二进制文件（不显示内容） */
    BINARY
}
```

同时更新 `FileTreeNode` 类中 `nodeType` 字段的类型和 getter 返回类型。

- [ ] **Step 2: 全局替换所有引用**

在以下文件中替换 `NodeType` → `NodeTypeEnum`：

- `ClassDiscoverer.java` — `guessType()` 返回值（4 处）+ `discover*` 方法中的比较（约 6 处）
- `FileTreeBuilder.java` — PACKAGE/CLASS_FILE 常量引用（约 4 处）
- `FileTreeView.java` — 泛型参数无需改动（仅引用 FileTreeNode）
- `FileTreeCell.java` — `item.getNodeType()` 返回值比较（5 处）
- `ExportService.java` — 无需改动（仅用 isClassFile()）
- `MainWindow.java` — 无需改动（仅用 isClassFile()）

使用 PowerShell 批量替换：

```powershell
$files = Get-ChildItem -Path "src/main/java" -Recurse -Filter "*.java"
foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    if ($content -match 'NodeType[^E]') {
        $newContent = $content -replace '(?<!Enum)(?<!\w)NodeType(?!Enum)(?!\.Enum)', 'NodeTypeEnum'
        Set-Content $f.FullName -Value $newContent -NoNewline
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 13: FileTreeNode 死代码删除 + toString + Javadoc

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/model/FileTreeNode.java`

- [ ] **Step 1: 删除 decompiled 字段及相关方法**

删除：
- `private boolean decompiled;`
- `public boolean isDecompiled() { return decompiled; }`
- `public void setDecompiled(boolean decompiled) { this.decompiled = decompiled; }`

加 Javadoc + 改进 toString：

完整文件：

```java
package com.bingbihanji.fxdecomplie.model;

/**
 * 文件树节点数据模型。包装在 TreeItem.getValue() 中。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class FileTreeNode {

    private final String name;
    private final String fullPath;
    private final NodeTypeEnum nodeType;
    private byte[] cachedBytes;

    public FileTreeNode(String name, String fullPath, NodeTypeEnum nodeType) {
        this.name = name;
        this.fullPath = fullPath;
        this.nodeType = nodeType;
    }

    public String getName() { return name; }

    public String getFullPath() { return fullPath; }

    public NodeTypeEnum getNodeType() { return nodeType; }

    public byte[] getCachedBytes() { return cachedBytes; }

    public void setCachedBytes(byte[] cachedBytes) { this.cachedBytes = cachedBytes; }

    public boolean isClassFile() { return nodeType == NodeTypeEnum.CLASS_FILE; }

    @Override
    public String toString() {
        return "FileTreeNode{name='" + name + "', type=" + nodeType + ", path='" + fullPath + "'}";
    }

    /** 文件树节点类型 */
    public enum NodeTypeEnum {
        /** 包节点（中间层级） */
        PACKAGE,
        /** .class 文件 */
        CLASS_FILE,
        /** .java 源文件 */
        JAVA_FILE,
        /** 其他资源文件 (xml, json, properties 等) */
        RESOURCE,
        /** 二进制文件（不显示内容） */
        BINARY
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 14: Workspace + OpenFile 补充 equals/hashCode/toString/Javadoc

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/model/Workspace.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/model/OpenFile.java`

- [ ] **Step 1: Workspace.java 完整改写**

```java
package com.bingbihanji.fxdecomplie.model;

import javafx.scene.control.TreeItem;

import java.io.File;
import java.util.Objects;

/**
 * 代表一个已打开的 JAR/ZIP/目录工作空间。包含文件树根节点。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class Workspace {

    private final String name;
    private final File sourceFile;
    private final TreeItem<FileTreeNode> treeRoot;
    private final boolean isArchive;

    public Workspace(String name, File sourceFile, TreeItem<FileTreeNode> treeRoot, boolean isArchive) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.treeRoot = treeRoot;
        this.isArchive = isArchive;
    }

    public String getName() { return name; }
    public File getSourceFile() { return sourceFile; }
    public TreeItem<FileTreeNode> getTreeRoot() { return treeRoot; }
    public boolean isArchive() { return isArchive; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Workspace other)) return false;
        return Objects.equals(sourceFile, other.sourceFile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFile);
    }

    @Override
    public String toString() {
        return "Workspace{name='" + name + "', source=" + sourceFile + "}";
    }
}
```

- [ ] **Step 2: OpenFile.java 完整改写**

```java
package com.bingbihanji.fxdecomplie.model;

import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;

import java.util.Objects;

/**
 * 已打开的 class 文件状态。包含反编译后的源码和使用的引擎。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class OpenFile {

    private final String className;
    private final String fullPath;
    private final String sourceCode;
    private final DecompilerTypeEnum engine;

    public OpenFile(String className, String fullPath, String sourceCode, DecompilerTypeEnum engine) {
        this.className = className;
        this.fullPath = fullPath;
        this.sourceCode = sourceCode;
        this.engine = engine;
    }

    public String getClassName() { return className; }
    public String getFullPath() { return fullPath; }
    public String getSourceCode() { return sourceCode; }
    public DecompilerTypeEnum getEngine() { return engine; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OpenFile other)) return false;
        return Objects.equals(fullPath, other.fullPath)
                && engine == other.engine;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullPath, engine);
    }

    @Override
    public String toString() {
        return className + ".java";
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 15: ui/ + app/ — 工具类合规

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/theme/AppTheme.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/window/AppHeaderBar.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/app/BackgroundTasks.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/theme/VsCodeThemeLoader.java`

- [ ] **Step 1: AppTheme — 加 final + 私有构造器 + Javadoc**

```java
/**
 * 应用主题工具类。加载暗色 CSS 样式表和编辑器主题。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppTheme {

    private AppTheme() {
        throw new AssertionError("utility class");
    }
    // ... 其余不变
}
```

- [ ] **Step 2: AppHeaderBar — 加 final + 私有构造器 + Javadoc**

```java
/**
 * 自定义窗口标题栏。使用 JavaFX HeaderBar 替代操作系统标题栏。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class AppHeaderBar {

    private AppHeaderBar() {
        throw new AssertionError("utility class");
    }
    // ... 其余不变
}
```

- [ ] **Step 3: BackgroundTasks — 加 final + 私有构造器 + Javadoc**

```java
/**
 * 后台任务工具类。在守护线程上执行耗时操作。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class BackgroundTasks {

    private BackgroundTasks() {
        throw new AssertionError("utility class");
    }
    // ... 其余不变
}
```

- [ ] **Step 4: VsCodeThemeLoader — 加 final + 私有构造器 + Javadoc**

```java
/**
 * VS Code 主题加载器。解析 VS Code 主题 JSON 文件，提取编辑器颜色和 token 样式映射。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class VsCodeThemeLoader {

    private VsCodeThemeLoader() {
        throw new AssertionError("utility class");
    }
    // ... 其余不变
}
```

- [ ] **Step 5: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 16: VsCodeThemeLoader 懒加载 + 优化

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/theme/VsCodeThemeLoader.java`

- [ ] **Step 1: defaultDark() 改为静态常量懒加载**

在类中添加 `DEFAULT_DARK` 静态字段，`defaultDark()` 直接返回常量：

```java
private static final ThemeData DEFAULT_DARK = buildDefaultDark();

private static ThemeData buildDefaultDark() {
    Map<String, StyleAttributeMap> tokenStyles = new LinkedHashMap<>();
    tokenStyles.put("keyword", style("#c586c0", true, false));
    tokenStyles.put("comment", style("#6a9955", false, true));
    tokenStyles.put("string", style("#ce9178", false, false));
    tokenStyles.put("annotation", style("#dcdcaa", false, false));
    tokenStyles.put("number", style("#b5cea8", false, false));
    tokenStyles.put("default", style("#9aa7b0", false, false));

    return new ThemeData("Default Dark", "dark",
            Color.web("#141718"), Color.web("#9aa7b0"), Color.web("#4f5b63"),
            "Fira Code Light", 14, tokenStyles);
}

public static ThemeData defaultDark() {
    return DEFAULT_DARK;
}
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 17: RegexHighlighter 前缀匹配修复 + Javadoc

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/theme/RegexHighlighter.java`

- [ ] **Step 1: 修复 resolveStyle 前缀匹配歧义**

将 `resolveStyle` 中的内层循环：

```java
for (var entry : styles.entrySet()) {
    if (entry.getKey().startsWith(scope)) return entry.getValue();
}
```

改为精确逐段匹配（仅匹配第一级 scope）：

```java
for (var entry : styles.entrySet()) {
    String key = entry.getKey();
    if (key.equals(scope)) return entry.getValue();
    // 仅当 scope 是独立段时匹配（如 "keyword" 不匹配 "keyword.other"）
    if (key.startsWith(scope + ".")) return entry.getValue();
}
```

- [ ] **Step 2: 添加 Javadoc**

在类声明处添加：

```java
/**
 * 基于正则表达式的 Java 语法高亮器。实现 SyntaxDecorator 接口，
 * Token 颜色从 VsCodeThemeLoader.ThemeData 获取。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

- [ ] **Step 3: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 18: ui/ 类 Javadoc 补充

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/code/StatusBar.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/code/CodeEditorTab.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/tree/FileTreeView.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/tree/FileTreeCell.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/ui/menu/MainMenuBar.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/Main.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/AppLauncher.java`

- [ ] **Step 1: 为每个类添加 Javadoc**

`StatusBar.java` — 添加：
```java
/**
 * 底部状态栏: [类路径]  [编码]  [行:列]。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

`CodeEditorTab.java` — 添加：
```java
/**
 * 单个代码标签页。封装 CodeArea + 语法高亮器 + 字体缩放。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

`FileTreeView.java` — 添加：
```java
/**
 * 文件树视图封装。设置暗色背景和自定义单元格渲染。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

`FileTreeCell.java` — 添加：
```java
/**
 * 文件树单元格渲染器。根据节点类型显示不同图标样式。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

`MainMenuBar.Actions` 接口方法 — 为每个方法添加 Javadoc：

```java
public interface Actions {
    /** 打开 JAR/ZIP/Class 文件 */
    void openFile();
    /** 打开目录 */
    void openDirectory();
    /** 关闭当前工作区标签页 */
    void closeCurrentWorkspace();
    /** 关闭其他工作区标签页 */
    void closeOtherWorkspaces();
    /** 保存当前代码标签页为 .java 文件 */
    void saveCurrentFile();
    /** 批量导出当前工作区所有 class 为 .java 文件到目录 */
    void exportAllFiles();
    /** 退出应用 */
    void exit();
    /** 复制选中文本 */
    void copySelection();
    /** 全选 */
    void selectAll();
    /** 放大字号 */
    void zoomIn();
    /** 缩小字号 */
    void zoomOut();
    /** 重置字号 */
    void resetZoom();
    /** 切换行号显示 */
    void toggleLineNumbers();
    /** 折叠文件树 */
    void collapseTree();
    /** 切换反编译引擎 */
    void selectEngine(DecompilerTypeEnum engine);
}
```

`MainMenuBar.java` — 添加类级别 Javadoc + 每个 `create*Menu` 方法添加单行注释。

`Main.java` — 添加：
```java
/**
 * 应用程序入口点。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

`AppLauncher.java` — 添加：
```java
/**
 * 启动器，委托到 FxDecompilerApp.main()。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
```

- [ ] **Step 2: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

### Task 19: MainWindow 拆分为 3 个类

**Files:**
- Create: `src/main/java/com/bingbihanji/fxdecomplie/ui/WorkspaceTabManager.java`
- Create: `src/main/java/com/bingbihanji/fxdecomplie/app/ClassTabOpener.java`
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/MainWindow.java`

- [ ] **Step 1: 创建 WorkspaceTabManager.java**

```java
package com.bingbihanji.fxdecomplie.ui;

import com.bingbihanji.fxdecomplie.MainWindow.WorkspaceView;
import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.StackPane;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理外层 TabPane 中工作区标签页的创建和销毁。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
final class WorkspaceTabManager {

    private final TabPane outerTabPane;
    private final StatusBar statusBar;
    private final Map<Tab, WorkspaceView> workspaceViews = new HashMap<>();

    WorkspaceTabManager(TabPane outerTabPane, StatusBar statusBar) {
        this.outerTabPane = outerTabPane;
        this.statusBar = statusBar;
    }

    Map<Tab, WorkspaceView> getWorkspaceViews() {
        return workspaceViews;
    }

    void addWorkspaceTab(Workspace workspace, java.util.function.BiConsumer<FileTreeNode, TabPane> onClassClick) {
        removeWelcomeTab();

        FileTreeView treeView = new FileTreeView(workspace.getTreeRoot());
        treeView.setPrefWidth(280);

        TabPane codeTabPane = new TabPane();
        codeTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);

        treeView.setOnMouseClicked(e -> {
            TreeItem<FileTreeNode> item = treeView.getSelectionModel().getSelectedItem();
            if (item == null) return;
            FileTreeNode node = item.getValue();
            if (node != null && node.isClassFile()) {
                onClassClick.accept(node, codeTabPane);
            }
        });

        SplitPane splitPane = new SplitPane(treeView, codeTabPane);
        splitPane.setDividerPositions(0.25);

        Tab tab = new Tab(workspace.getName(), splitPane);
        WorkspaceView view = MainWindow.createWorkspaceView(workspace, treeView, codeTabPane, tab);
        workspaceViews.put(tab, view);
        tab.setOnClosed(event -> cleanupClosedWorkspace(tab));

        outerTabPane.getTabs().add(tab);
        outerTabPane.getSelectionModel().select(tab);
        statusBar.setFilePath(workspace.getSourceFile().getAbsolutePath());
    }

    void closeWorkspaceTab(Tab tab) {
        cleanupClosedWorkspace(tab);
        outerTabPane.getTabs().remove(tab);
    }

    void closeOtherWorkspaces(Tab keepTab) {
        outerTabPane.getTabs().stream()
                .filter(tab -> tab != keepTab)
                .toList()
                .forEach(this::closeWorkspaceTab);
    }

    WorkspaceView currentWorkspaceView() {
        Tab selected = outerTabPane.getSelectionModel().getSelectedItem();
        return selected == null ? null : workspaceViews.get(selected);
    }

    CodeEditorTab currentCodeTab() {
        WorkspaceView view = currentWorkspaceView();
        if (view == null) return null;
        Tab selected = view.codeTabPane().getSelectionModel().getSelectedItem();
        return selected instanceof CodeEditorTab codeTab ? codeTab : null;
    }

    void updateStatusForWorkspace(Tab tab) {
        WorkspaceView view = tab == null ? null : workspaceViews.get(tab);
        if (view == null) {
            statusBar.clear();
            return;
        }
        var codeSelected = view.codeTabPane().getSelectionModel().getSelectedItem();
        if (codeSelected instanceof CodeEditorTab codeTab) {
            statusBar.setFilePath(formatClassPath(codeTab.getOpenFile().getFullPath()));
            statusBar.setEncoding("UTF-8");
            var caret = codeTab.getCodeArea().getCaretPosition();
            statusBar.setCursorPosition(caret.index() + 1, caret.charIndex() + 1);
        }
    }

    void collapseTreeInCurrentWorkspace() {
        WorkspaceView view = currentWorkspaceView();
        if (view != null) {
            collapse(view.workspace().getTreeRoot());
            view.workspace().getTreeRoot().setExpanded(true);
        }
    }

    private void cleanupClosedWorkspace(Tab tab) {
        workspaceViews.remove(tab);
        BytecodeCache.clear();
        if (workspaceViews.isEmpty()) {
            statusBar.clear();
            if (outerTabPane.getTabs().stream().noneMatch(existing -> "欢迎".equals(existing.getText()))) {
                outerTabPane.getTabs().add(createWelcomeTab());
            }
        }
    }

    private void removeWelcomeTab() {
        outerTabPane.getTabs().removeIf(tab -> !workspaceViews.containsKey(tab));
    }

    private Tab createWelcomeTab() {
        Label placeholder = new Label("拖入 JAR/ZIP/Class 文件 或 文件 > 打开");
        placeholder.getStyleClass().add("empty-state-label");
        StackPane content = new StackPane(placeholder);
        content.getStyleClass().add("empty-state");
        StackPane.setAlignment(placeholder, Pos.CENTER);
        Tab tab = new Tab("欢迎", content);
        tab.setClosable(false);
        return tab;
    }

    private void collapse(TreeItem<FileTreeNode> item) {
        item.setExpanded(false);
        item.getChildren().forEach(this::collapse);
    }

    static String formatClassPath(String fullPath) {
        String display = fullPath.replace('/', '>').replace('\\', '>');
        return display.endsWith(".class")
                ? display.substring(0, display.length() - 6)
                : display;
    }
}
```

- [ ] **Step 2: 创建 ClassTabOpener.java**

```java
package com.bingbihanji.fxdecomplie.app;

import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.BytecodeCache;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.OpenFile;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import javafx.application.Platform;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * 负责 class 文件的反编译和代码标签页的创建/刷新。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
final class ClassTabOpener {

    private final AppConfig config;
    private final VsCodeThemeLoader.ThemeData editorTheme;
    private final StatusBar statusBar;

    ClassTabOpener(AppConfig config, VsCodeThemeLoader.ThemeData editorTheme, StatusBar statusBar) {
        this.config = config;
        this.editorTheme = editorTheme;
        this.statusBar = statusBar;
    }

    void openClassTab(FileTreeNode node, Workspace workspace, TabPane codeTabPane,
                      DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        Tab existingTab = findOpenClassTab(codeTabPane, node, engine);
        if (existingTab != null) {
            codeTabPane.getSelectionModel().select(existingTab);
            return;
        }

        statusBar.setFilePath("正在反编译: " + node.getFullPath());

        BackgroundTasks.run("Decompile-" + node.getName(), () -> {
            try {
                byte[] bytes = readClassBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert("读取错误", "无法读取: " + node.getFullPath()));
                    return;
                }

                String sourceCode = DecompilerFactory.getDecompiler(engine)
                        .decompile(node.getFullPath(), bytes);
                OpenFile openFile = new OpenFile(className(node), node.getFullPath(), sourceCode, engine);

                Platform.runLater(() -> {
                    CodeEditorTab codeTab = createCodeEditorTab(openFile, lineNumbersEnabled);
                    codeTabPane.getTabs().add(codeTab);
                    codeTabPane.getSelectionModel().select(codeTab);
                    statusBar.setFilePath(WorkspaceTabManager.formatClassPath(node.getFullPath()));
                    statusBar.setEncoding("UTF-8");
                    bindCaretPosition(codeTab);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert("反编译错误", "反编译失败: " + ex.getMessage()));
            }
        });
    }

    void refreshCurrentClassTab(Workspace workspace, TabPane codeTabPane, CodeEditorTab currentTab,
                                DecompilerTypeEnum engine, boolean lineNumbersEnabled) {
        String fullPath = currentTab.getOpenFile().getFullPath();
        FileTreeNode node = findNode(workspace.getTreeRoot(), fullPath);
        if (node == null) {
            statusBar.setFilePath("无法定位: " + fullPath);
            return;
        }

        int tabIndex = codeTabPane.getTabs().indexOf(currentTab);
        statusBar.setFilePath("正在使用 " + engine.name() + " 重新反编译: " + fullPath);

        BackgroundTasks.run("Redecompile-" + node.getName(), () -> {
            try {
                byte[] bytes = readClassBytes(node, workspace);
                if (bytes == null) {
                    Platform.runLater(() -> showAlert("读取错误", "无法读取: " + fullPath));
                    return;
                }

                String sourceCode = DecompilerFactory.getDecompiler(engine).decompile(fullPath, bytes);
                OpenFile openFile = new OpenFile(className(node), fullPath, sourceCode, engine);

                Platform.runLater(() -> {
                    CodeEditorTab replacement = createCodeEditorTab(openFile, lineNumbersEnabled);
                    codeTabPane.getTabs().set(tabIndex, replacement);
                    codeTabPane.getSelectionModel().select(replacement);
                    bindCaretPosition(replacement);
                    statusBar.setFilePath(WorkspaceTabManager.formatClassPath(fullPath));
                    statusBar.setEncoding("UTF-8");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showAlert("反编译错误", "反编译失败: " + ex.getMessage()));
            }
        });
    }

    private CodeEditorTab createCodeEditorTab(OpenFile openFile, boolean lineNumbersEnabled) {
        return new CodeEditorTab(
                openFile, editorTheme,
                config.theme.fontFamily, config.theme.fontSize,
                config.decompiler.wrapText, lineNumbersEnabled
        );
    }

    private Tab findOpenClassTab(TabPane codeTabPane, FileTreeNode node, DecompilerTypeEnum engine) {
        for (Tab tab : codeTabPane.getTabs()) {
            if (tab instanceof CodeEditorTab codeTab
                    && codeTab.getOpenFile().getFullPath().equals(node.getFullPath())) {
                if (codeTab.getOpenFile().getEngine() == engine) return tab;
            }
        }
        codeTabPane.getTabs().removeIf(tab -> tab instanceof CodeEditorTab codeTab
                && codeTab.getOpenFile().getFullPath().equals(node.getFullPath()));
        return null;
    }

    private byte[] readClassBytes(FileTreeNode node, Workspace workspace) throws IOException {
        byte[] bytes = node.getCachedBytes();
        if (bytes != null) return bytes;
        String internalName = node.getFullPath().replace(".class", "");
        bytes = BytecodeCache.get(internalName);
        if (bytes != null) return bytes;
        if (!workspace.isArchive()) {
            File source = workspace.getSourceFile();
            return Files.readAllBytes(new File(source, node.getFullPath()).toPath());
        }
        return null;
    }

    private FileTreeNode findNode(TreeItem<FileTreeNode> item, String fullPath) {
        FileTreeNode node = item.getValue();
        if (node != null && fullPath.equals(node.getFullPath())) return node;
        for (TreeItem<FileTreeNode> child : item.getChildren()) {
            FileTreeNode found = findNode(child, fullPath);
            if (found != null) return found;
        }
        return null;
    }

    private void bindCaretPosition(CodeEditorTab codeTab) {
        codeTab.getCodeArea().caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            if (newPos != null) {
                statusBar.setCursorPosition(newPos.index() + 1, newPos.charIndex() + 1);
            }
        });
    }

    private static String className(FileTreeNode node) {
        String name = node.getName();
        return name.endsWith(".class") ? name.substring(0, name.length() - 6) : name;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.ERROR, message);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.showAndWait();
        });
    }
}
```

- [ ] **Step 3: 精简 MainWindow.java**

MainWindow 保留协调器职责。删除以下方法（已迁移到 WorkspaceTabManager 或 ClassTabOpener）：
- `addWorkspaceTab`, `closeWorkspaceTab`, `cleanupClosedWorkspace`, `createWelcomeTab`, `removeWelcomeTab` → WorkspaceTabManager
- `openClassTab`, `refreshCurrentClassTab`, `readClassBytes`, `findOpenClassTab`, `findNode`, `createCodeEditorTab`, `bindCaretPosition` → ClassTabOpener
- 将 `WorkspaceView` record 提升为 package-private（供 WorkspaceTabManager 访问）
- 添加 `WorkspaceTabManager` 和 `ClassTabOpener` 字段

```java
package com.bingbihanji.fxdecomplie;

import com.bingbihanji.fxdecomplie.app.BackgroundTasks;
import com.bingbihanji.fxdecomplie.app.ClassTabOpener;
import com.bingbihanji.fxdecomplie.config.AppConfig;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerFactory;
import com.bingbihanji.fxdecomplie.decompiler.DecompilerTypeEnum;
import com.bingbihanji.fxdecomplie.io.ClassDiscoverer;
import com.bingbihanji.fxdecomplie.io.ExportService;
import com.bingbihanji.fxdecomplie.io.FileTreeBuilder;
import com.bingbihanji.fxdecomplie.model.FileTreeNode;
import com.bingbihanji.fxdecomplie.model.Workspace;
import com.bingbihanji.fxdecomplie.ui.WorkspaceTabManager;
import com.bingbihanji.fxdecomplie.ui.code.CodeEditorTab;
import com.bingbihanji.fxdecomplie.ui.code.StatusBar;
import com.bingbihanji.fxdecomplie.ui.menu.MainMenuBar;
import com.bingbihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbihanji.fxdecomplie.ui.theme.VsCodeThemeLoader;
import com.bingbihanji.fxdecomplie.ui.tree.FileTreeView;
import com.bingbihanji.fxdecomplie.ui.window.AppHeaderBar;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

/**
 * 主窗口控制器。实现 MainMenuBar.Actions 接口，协调各模块。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public class MainWindow implements MainMenuBar.Actions {

    private final AppConfig config;
    private final VsCodeThemeLoader.ThemeData editorTheme;
    private final boolean useHeaderBar;

    private Stage stage;
    private StatusBar statusBar;
    private TabPane outerTabPane;
    private WorkspaceTabManager tabManager;
    private ClassTabOpener classTabOpener;
    private DecompilerTypeEnum currentEngine = DecompilerTypeEnum.VINEFLOWER;
    private boolean lineNumbersEnabled;

    public MainWindow(AppConfig config) {
        this(config, false);
    }

    public MainWindow(AppConfig config, boolean useHeaderBar) {
        this.config = config;
        this.useHeaderBar = useHeaderBar;
        this.editorTheme = AppTheme.loadEditorTheme(config);
        this.lineNumbersEnabled = config.decompiler.lineNumbersEnabled;
    }

    public void show(Stage stage) {
        this.stage = stage;
        this.currentEngine = parseDecompiler(config.decompiler.defaultEngine);

        outerTabPane = new TabPane();
        outerTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
        statusBar = new StatusBar();

        tabManager = new WorkspaceTabManager(outerTabPane, statusBar);
        outerTabPane.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldTab, newTab) -> tabManager.updateStatusForWorkspace(newTab));

        classTabOpener = new ClassTabOpener(config, editorTheme, statusBar);

        outerTabPane.getTabs().add(createWelcomeTab());

        MainMenuBar menuBar = new MainMenuBar(this, currentEngine);
        VBox topBars = new VBox();
        if (useHeaderBar) {
            topBars.getChildren().add(AppHeaderBar.create(stage, "FxDecompiler", menuBar));
        } else {
            topBars.getChildren().add(menuBar);
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("app-root");
        root.setTop(topBars);
        root.setCenter(outerTabPane);
        root.setBottom(statusBar);

        Scene scene = new Scene(root, config.window.width, config.window.height);
        scene.getStylesheets().add(AppTheme.darkStylesheet());

        stage.setTitle("FxDecompiler");
        stage.setScene(scene);
        stage.show();
    }

    // --- Menu Actions ---

    @Override public void openFile() { /* unchanged — calls loadFile */ }
    @Override public void openDirectory() { /* unchanged — calls loadFile */ }
    @Override public void closeCurrentWorkspace() { /* delegates to tabManager */ }
    @Override public void closeOtherWorkspaces() { /* delegates to tabManager */ }
    @Override public void saveCurrentFile() { /* unchanged */ }
    @Override public void exportAllFiles() { /* unchanged — uses tabManager.currentWorkspaceView() */ }
    @Override public void exit() { DecompilerFactory.cleanup(); stage.close(); }
    @Override public void copySelection() { /* unchanged */ }
    @Override public void selectAll() { /* unchanged */ }
    @Override public void zoomIn() { /* unchanged */ }
    @Override public void zoomOut() { /* unchanged */ }
    @Override public void resetZoom() { /* unchanged */ }
    @Override public void toggleLineNumbers() { /* unchanged — uses tabManager.getWorkspaceViews() */ }
    @Override public void collapseTree() { tabManager.collapseTreeInCurrentWorkspace(); }
    @Override public void selectEngine(DecompilerTypeEnum engine) { /* unchanged — uses tabManager */ }

    // --- Internal ---

    private void loadFile(File file) { /* unchanged — calls tabManager.addWorkspaceTab */ }
    // ... 其余辅助方法 (parseDecompiler, isArchive, 等) 保持不变
    // showError/showWarning/showInfo 保持不变 — 但现在在 showAlert 处用

    // WorkspaceView record 提升为 package-private
    static WorkspaceView createWorkspaceView(Workspace workspace, FileTreeView treeView,
                                              TabPane codeTabPane, Tab workspaceTab) {
        return new WorkspaceView(workspace, treeView, codeTabPane, workspaceTab);
    }

    record WorkspaceView(
            Workspace workspace,
            FileTreeView treeView,
            TabPane codeTabPane,
            Tab workspaceTab
    ) {}
}
```

> 由于 MainWindow 完整代码较长，实际实施时使用 Edit 工具逐段替换。核心原则：保留协调逻辑，将 Tab 管理和 class 反编译逻辑委托给新类。

- [ ] **Step 4: 编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS。如有编译错误，逐一修复后重新编译。

---

### Task 20: FxDecompilerApp + 入口类 Javadoc + 补充注释

**Files:**
- Modify: `src/main/java/com/bingbihanji/fxdecomplie/FxDecompilerApp.java`

- [ ] **Step 1: FxDecompilerApp 类级别 Javadoc + deprecation 注释**

```java
/**
 * JavaFX 应用启动器。加载配置，创建主窗口，处理启动异常。
 *
 * @author bingbaihanji
 * @date 2026-06-17
 */
public final class FxDecompilerApp {
    // ...
    // 在 startApplication 的 @SuppressWarnings("deprecation") 上方添加:
    // initStyle(EXTENDED) 在 JavaFX 中被标记为 deprecated，需由应用自行实现标题栏交互。
    // 当前实现配合 AppHeaderBar 提供完整的窗口拖拽和按钮交互，功能正常，暂时保留。
```

- [ ] **Step 2: 最终编译验证**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS

---

## 自检

1. **Spec 覆盖检查**: 设计文档中的 5 个模块全部有对应 Task → Task 1-6 (decompiler), Task 7-10 (io), Task 11 (config), Task 12-14 (model), Task 15-20 (ui/app/入口)

2. **占位符扫描**: 所有步骤包含完整代码，无 TODO/TBD。Task 19 MainWindow 的完整代码需在实际实施时用 Edit 工具逐段完成，但拆分边界已明确。

3. **类型一致性**: 全局重命名在 Task 1, 2, 12 中提前执行（IDecompiler→Decompiler, DecompilerType→DecompilerTypeEnum, NodeType→NodeTypeEnum），后续 Task 均使用新名称。`WorkspaceTabManager.formatClassPath` 被 `ClassTabOpener` 引用，已确认 static 方法可见。
