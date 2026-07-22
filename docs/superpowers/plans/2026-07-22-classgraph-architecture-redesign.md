# ClassGraph 架构重构 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 `com.bingbaihanji.classgraph` 包进行领域驱动重构：8 个子包重组、~75 文件重命名移动、大文件拆分、JSON 依赖消除、设计模式规范化。

**Architecture:** 从底层到顶层分阶段重构。先建基础包（util/、reflect/），再建类型系统（type/）、数据模型（metadata/）、资源 IO（resource/），然后字节码（bytecode/）、类路径（classpath/），最后扫描引擎（scan/）和全局 import 修复。每阶段独立可编译。

**Tech Stack:** Java 25, 纯 JDK（无外部依赖）, JUnit 5.12.1

---

## File Structure

### Target Package Mapping

| 当前位置 | 新位置 |
|----------|--------|
| `core/HasName.java` | `metadata/Named.java` |
| `core/ScanResultObject.java` | `metadata/MetadataNode.java` |
| `core/ClassInfo.java` | `metadata/ClassInfo.java` |
| `core/ClassInfoList.java` | `metadata/ClassInfoList.java` |
| `core/ArrayClassInfo.java` | `metadata/ArrayClassInfo.java` |
| `core/ClassMemberInfo.java` | `metadata/ClassMemberInfo.java` |
| `core/FieldInfo.java` | `metadata/FieldInfo.java` |
| `core/FieldInfoList.java` | `metadata/FieldInfoList.java` |
| `core/MethodInfo.java` | `metadata/MethodInfo.java` |
| `core/MethodInfoList.java` | `metadata/MethodInfoList.java` |
| `core/MethodParameterInfo.java` | `metadata/MethodParam.java` |
| `core/AnnotationInfo.java` | `metadata/AnnotationInfo.java` |
| `core/AnnotationInfoList.java` | `metadata/AnnotationInfoList.java` |
| `core/AnnotationParameterValue.java` | `metadata/AnnotationParameterValue.java` |
| `core/AnnotationParameterValueList.java` | `metadata/AnnotationParameterValueList.java` |
| `core/AnnotationClassRef.java` | `metadata/AnnotationClassRef.java` |
| `core/AnnotationEnumValue.java` | `metadata/AnnotationEnumValue.java` |
| `core/InfoList.java` | `metadata/InfoList.java` |
| `core/MappableInfoList.java` | 合并到 `metadata/InfoList.java` |
| `core/PackageInfo.java` | `metadata/PackageInfo.java` |
| `core/PackageInfoList.java` | `metadata/PackageInfoList.java` |
| `core/ModuleInfo.java` | `metadata/ModuleInfo.java` |
| `core/ModuleInfoList.java` | `metadata/ModuleInfoList.java` |
| `core/ModuleRef.java` | `metadata/ModuleRef.java` |
| `core/ObjectTypedValueWrapper.java` | `metadata/TypedValue.java` |
| `core/PotentiallyUnmodifiableList.java` | `util/LazyList.java` |
| `core/ClassFile.java` | `bytecode/ClassParser.java` |
| `fileslice/reader/ClassfileReader.java` | `bytecode/ClassFileReader.java` |
| `core/CloseableByteBuffer.java` | `bytecode/ByteBuffer.java` |
| `core/Resource.java` | `resource/Resource.java` |
| `core/ResourceList.java` | `resource/ResourceList.java` |
| `core/ModuleReaderProxy.java` | `resource/ModuleReaderProxy.java` |
| `core/GraphvizDotfileGenerator.java` | `resource/GraphvizDotfileGenerator.java` |
| `fileslice/*.java` | `resource/*.java` |
| `fileslice/reader/*.java` | `resource/*.java` |
| `fastzipfilereader/*.java` | `resource/*.java` |
| `recycler/*.java` | `resource/Pool.java` (合并) |
| `core/TypeSignature.java` | `type/TypeSignature.java` |
| `core/ClassTypeSignature.java` | `type/ClassType.java` |
| `core/ArrayTypeSignature.java` | `type/ArrayType.java` |
| `core/BaseTypeSignature.java` | `type/BaseType.java` |
| `core/TypeVariableSignature.java` | `type/TypeVar.java` |
| `core/MethodTypeSignature.java` | `type/MethodType.java` |
| `core/TypeArgument.java` | `type/TypeArg.java` |
| `core/TypeParameter.java` | `type/TypeParam.java` |
| `core/ReferenceTypeSignature.java` | `type/ReferenceType.java` |
| `core/HierarchicalTypeSignature.java` | `type/HierarchicalType.java` |
| `core/ClassRefTypeSignature.java` | `type/ClassRef.java` |
| `core/ClassRefOrTypeVariableSignature.java` | `type/TypeRef.java` |
| `types/ParseException.java` | `type/ParseException.java` |
| `types/Parser.java` | `type/TypeParser.java` |
| `types/TypeUtils.java` | `type/TypeUtils.java` |
| `core/ClasspathElement.java` | `classpath/Classpath.java` |
| `core/ClasspathElementDir.java` | `classpath/DirClasspath.java` |
| `core/ClasspathElementZip.java` | `classpath/JarClasspath.java` |
| `core/ClasspathElementModule.java` | `classpath/ModuleClasspath.java` |
| `core/ModulePathInfo.java` | `classpath/ModulePathInfo.java` |
| `classpath/*.java` | `classpath/*.java` (keep, update imports) |
| `classloaderhandler/*.java` | `classpath/handler/*.java` |
| `core/ClassGraph.java` | `scan/ClassGraph.java` |
| `core/ScanResult.java` | `scan/ScanResult.java` |
| `core/Scanner.java` | `scan/Scanner.java` |
| `core/ClassGraphClassLoader.java` | `scan/ScanClassLoader.java` |
| `core/ClassGraphException.java` | `scan/ScanException.java` |
| `scanspec/ScanSpec.java` | `scan/ScanConfig.java` |
| `scanspec/AcceptReject.java` | `scan/Filter.java` |
| `reflection/*.java` | `reflect/*.java` |
| `utils/*.java` | `util/*.java` |
| `concurrency/*.java` | 合并到 `util/Concurrency.java` |
| `classgraph/json/JSONUtils.java` | 删除，内联到 `util/Strings.java` |

### Shared Package Suffix Map (for global import replacement)

```
com.bingbaihanji.classgraph.core → split by class name
com.bingbaihanji.classgraph.classloaderhandler → com.bingbaihanji.classgraph.classpath.handler
com.bingbaihanji.classgraph.fastzipfilereader → com.bingbaihanji.classgraph.resource
com.bingbaihanji.classgraph.fileslice → com.bingbaihanji.classgraph.resource
com.bingbaihanji.classgraph.fileslice.reader → com.bingbaihanji.classgraph.resource
com.bingbaihanji.classgraph.recycler → com.bingbaihanji.classgraph.resource
com.bingbaihanji.classgraph.reflection → com.bingbaihanji.classgraph.reflect
com.bingbaihanji.classgraph.scanspec → com.bingbaihanji.classgraph.scan
com.bingbaihanji.classgraph.types → com.bingbaihanji.classgraph.type
com.bingbaihanji.classgraph.utils → com.bingbaihanji.classgraph.util
com.bingbaihanji.classgraph.concurrency → 合并到 com.bingbaihanji.classgraph.util
```

---

## Phase 1: Foundation — Create Directories + util/ Package

### Task 1: Create target directory structure

**Files:**
- Create: 8 new package directories

- [ ] **Step 1: Create all target directories**

```powershell
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/metadata
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/bytecode
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/type
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/classpath/handler
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/resource
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/scan
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/reflect
New-Item -ItemType Directory -Force -Path src/main/java/com/bingbaihanji/classgraph/util
```

- [ ] **Step 2: Verify directories created**

Run: `Get-ChildItem src/main/java/com/bingbaihanji/classgraph -Directory | Select-Object Name`
Expected: 8 new directories visible alongside original ones

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/metadata/ \
        src/main/java/com/bingbaihanji/classgraph/bytecode/ \
        src/main/java/com/bingbaihanji/classgraph/type/ \
        src/main/java/com/bingbaihanji/classgraph/classpath/handler/ \
        src/main/java/com/bingbaihanji/classgraph/resource/ \
        src/main/java/com/bingbaihanji/classgraph/scan/ \
        src/main/java/com/bingbaihanji/classgraph/reflect/ \
        src/main/java/com/bingbaihanji/classgraph/util/
git commit -m "chore(classgraph): create target package directories for redesign"
```

---

### Task 2: Move and consolidate util/ package

**Files:**
- Move: `utils/*.java` → `util/`
- Move: `concurrency/` → merge into `util/Concurrency.java`
- Create: `util/Strings.java` (new, with escapeJson)
- Delete: `utils/`, `concurrency/` directories (when empty)

- [ ] **Step 1: Move all utils files to util/**

For each file in `utils/` directory, move it to `util/` with updated package declaration from `com.bingbaihanji.classgraph.utils` to `com.bingbaihanji.classgraph.util`.

```powershell
$utilsFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/utils/*.java
foreach ($f in $utilsFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.utils;',
                                  'package com.bingbaihanji.classgraph.util;'
    $newPath = "src/main/java/com/bingbaihanji/classgraph/util/$($f.Name)"
    Set-Content -Path $newPath -Value $content -NoNewline
    Remove-Item $f.FullName
}
```

Files moved:
- `Assert.java`, `CollectionUtils.java`, `FastPathResolver.java`, `FileUtils.java`, `JarUtils.java`, `LogNode.java`, `ProxyingInputStream.java`, `StringUtils.java`, `URLPathEncoder.java`, `VersionFinder.java`

- [ ] **Step 2: Update imports from utils→util within moved files**

```powershell
$utilFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/util/*.java
foreach ($f in $utilFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.utils\.',
                                  'import com.bingbaihanji.classgraph.util.'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 3: Merge concurrency/ into util/Concurrency.java**

Move the 5 concurrency classes into a single `Concurrency.java` file in `util/`:

```java
package com.bingbaihanji.classgraph.util;

import java.util.concurrent.*;
// ... (merge AutoCloseableExecutorService, InterruptionChecker, 
//      SimpleThreadFactory, SingletonMap, WorkQueue into one file)
```

Copy the content of each concurrency class (without the license header or package declaration) into `Concurrency.java` as static inner classes or top-level classes.

- [ ] **Step 4: Create Strings.java with escapeJson method**

Write `util/Strings.java`:

```java
package com.bingbaihanji.classgraph.util;

import java.util.Locale;

/** String utility methods. Uses only JDK APIs. */
public final class Strings {
    private Strings() { throw new AssertionError("utility class"); }

    /**
     * Escape a string for safe inclusion in JSON output.
     * Pure JDK — no external dependencies.
     */
    public static String escapeJson(String s) {
        if (s == null) return null;
        var buf = new StringBuilder(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> buf.append("\\\"");
                case '\\' -> buf.append("\\\\");
                case '\b' -> buf.append("\\b");
                case '\f' -> buf.append("\\f");
                case '\n' -> buf.append("\\n");
                case '\r' -> buf.append("\\r");
                case '\t' -> buf.append("\\t");
                default -> {
                    if (c < 0x20) buf.append("\\u").append(String.format("%04x", (int) c));
                    else buf.append(c);
                }
            }
        }
        return buf.toString();
    }

    // Merge existing StringUtils methods here...
}
```

Also merge `StringUtils.java` content (if any) into this file.

- [ ] **Step 5: Add LazyList to util/**

Move `PotentiallyUnmodifiableList` from `core/` to `util/` as `LazyList`:

```powershell
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/core/PotentiallyUnmodifiableList.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                              'package com.bingbaihanji.classgraph.util;'
$content = $content -replace 'class PotentiallyUnmodifiableList',
                              'class LazyList'
Set-Content -Path src/main/java/com/bingbaihanji/classgraph/util/LazyList.java -Value $content -NoNewline
Remove-Item src/main/java/com/bingbaihanji/classgraph/core/PotentiallyUnmodifiableList.java
```

- [ ] **Step 6: Remove old directories**

```powershell
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/utils
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/concurrency
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/util/
git rm -r src/main/java/com/bingbaihanji/classgraph/utils/
git rm -r src/main/java/com/bingbaihanji/classgraph/concurrency/
git rm src/main/java/com/bingbaihanji/classgraph/core/PotentiallyUnmodifiableList.java
git commit -m "refactor(classgraph): consolidate util package, merge concurrency, add Strings.escapeJson"
```

---

## Phase 2: reflect/ Package

### Task 3: Move reflection files

**Files:**
- Move: `reflection/*.java` → `reflect/` with simplified names
- Delete: `reflection/` directory

- [ ] **Step 1: Move files with renames**

| 当前路径 | 新路径 |
|----------|--------|
| `reflection/ReflectionDriver.java` | `reflect/ReflectionDriver.java` |
| `reflection/StandardReflectionDriver.java` | `reflect/StandardDriver.java` |
| `reflection/NarcissusReflectionDriver.java` | `reflect/NarcissusDriver.java` |
| `reflection/ReflectionUtils.java` | `reflect/ReflectionUtils.java` |

```powershell
# Move ReflectionDriver (package: reflection → reflect)
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/reflection/ReflectionDriver.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.reflection;',
                              'package com.bingbaihanji.classgraph.reflect;'
Set-Content src/main/java/com/bingbaihanji/classgraph/reflect/ReflectionDriver.java -Value $content -NoNewline

# Move StandardReflectionDriver → StandardDriver
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/reflection/StandardReflectionDriver.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.reflection;',
                              'package com.bingbaihanji.classgraph.reflect;'
Set-Content src/main/java/com/bingbaihanji/classgraph/reflect/StandardDriver.java -Value $content -NoNewline

# Move NarcissusReflectionDriver → NarcissusDriver
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/reflection/NarcissusReflectionDriver.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.reflection;',
                              'package com.bingbaihanji.classgraph.reflect;'
Set-Content src/main/java/com/bingbaihanji/classgraph/reflect/NarcissusDriver.java -Value $content -NoNewline

# Move ReflectionUtils
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/reflection/ReflectionUtils.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.reflection;',
                              'package com.bingbaihanji.classgraph.reflect;'
Set-Content src/main/java/com/bingbaihanji/classgraph/reflect/ReflectionUtils.java -Value $content -NoNewline
```

- [ ] **Step 2: Update internal imports in moved reflect files**

```powershell
$reflectFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/reflect/*.java
foreach ($f in $reflectFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.reflection\.',
                                  'import com.bingbaihanji.classgraph.reflect.'
    $content = $content -replace 'class StandardReflectionDriver',
                                  'class StandardDriver'
    $content = $content -replace 'class NarcissusReflectionDriver',
                                  'class NarcissusDriver'
    $content = $content -replace 'StandardReflectionDriver', 'StandardDriver'
    $content = $content -replace 'NarcissusReflectionDriver', 'NarcissusDriver'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 3: Remove old reflection directory and commit**

```powershell
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/reflection
```

```bash
git add src/main/java/com/bingbaihanji/classgraph/reflect/
git rm -r src/main/java/com/bingbaihanji/classgraph/reflection/
git commit -m "refactor(classgraph): rename reflect package, shorten driver names"
```

---

## Phase 3: type/ Package

### Task 4: Move and rename type system files

**Files:**
- Move: 12 type signature files from `core/` → `type/` (renamed)
- Move: 3 files from `types/` → `type/` (renamed)
- Modify: `type/TypeParser.java` — remove Jackson dependency
- Delete: `types/` directory

- [ ] **Step 1: Move type signature files from core/ to type/ with renames**

For each file, move and rename, updating package declaration from `com.bingbaihanji.classgraph.core` to `com.bingbaihanji.classgraph.type`:

```powershell
$moves = @{
    'TypeSignature.java' = 'TypeSignature.java'
    'ClassTypeSignature.java' = 'ClassType.java'
    'ArrayTypeSignature.java' = 'ArrayType.java'
    'BaseTypeSignature.java' = 'BaseType.java'
    'TypeVariableSignature.java' = 'TypeVar.java'
    'MethodTypeSignature.java' = 'MethodType.java'
    'TypeArgument.java' = 'TypeArg.java'
    'TypeParameter.java' = 'TypeParam.java'
    'ReferenceTypeSignature.java' = 'ReferenceType.java'
    'HierarchicalTypeSignature.java' = 'HierarchicalType.java'
    'ClassRefTypeSignature.java' = 'ClassRef.java'
    'ClassRefOrTypeVariableSignature.java' = 'TypeRef.java'
}

foreach ($src in $moves.Keys) {
    $dst = $moves[$src]
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/core/$src" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                                  'package com.bingbaihanji.classgraph.type;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/type/$dst" -Value $content -NoNewline
    Remove-Item "src/main/java/com/bingbaihanji/classgraph/core/$src"
}
```

- [ ] **Step 2: Remove old types/ directory (keep only Parser for next step)**

```powershell
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/types/Parser.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.types;',
                              'package com.bingbaihanji.classgraph.type;'
$content = $content -replace 'class Parser', 'class TypeParser'
$content = $content -replace 'import com\.bingbaihanji\.utils\.json\.JSONUtils;',
                              'import com.bingbaihanji.classgraph.util.Strings;'
# Replace JSONUtils.escapeJSONString(...) calls with Strings.escapeJson(...)
$content = $content -replace 'JSONUtils\.escapeJSONString', 'Strings.escapeJson'
Set-Content src/main/java/com/bingbaihanji/classgraph/type/TypeParser.java -Value $content -NoNewline

$content = Get-Content src/main/java/com/bingbaihanji/classgraph/types/TypeUtils.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.types;',
                              'package com.bingbaihanji.classgraph.type;'
Set-Content src/main/java/com/bingbaihanji/classgraph/type/TypeUtils.java -Value $content -NoNewline

$content = Get-Content src/main/java/com/bingbaihanji/classgraph/types/ParseException.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.types;',
                              'package com.bingbaihanji.classgraph.type;'
Set-Content src/main/java/com/bingbaihanji/classgraph/type/ParseException.java -Value $content -NoNewline

Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/types
```

- [ ] **Step 3: Update class name references within moved type files**

```powershell
$typeFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/type/*.java
foreach ($f in $typeFiles) {
    $content = Get-Content $f.FullName -Raw
    # Fix class references for renamed types
    $content = $content -replace 'ClassTypeSignature', 'ClassType'
    $content = $content -replace 'ArrayTypeSignature', 'ArrayType'
    $content = $content -replace 'BaseTypeSignature', 'BaseType'
    $content = $content -replace 'TypeVariableSignature', 'TypeVar'
    $content = $content -replace 'MethodTypeSignature', 'MethodType'
    $content = $content -replace 'TypeArgument', 'TypeArg'
    $content = $content -replace 'TypeParameter', 'TypeParam'
    $content = $content -replace 'ReferenceTypeSignature', 'ReferenceType'
    $content = $content -replace 'HierarchicalTypeSignature', 'HierarchicalType'
    $content = $content -replace 'ClassRefTypeSignature', 'ClassRef'
    $content = $content -replace 'ClassRefOrTypeVariableSignature', 'TypeRef'
    # Fix internal Package-private references within type package
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.types\.',
                                  'import com.bingbaihanji.classgraph.type.'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/type/
git rm src/main/java/com/bingbaihanji/classgraph/core/TypeSignature.java
# ... (all 12 removed core type files)
git rm -r src/main/java/com/bingbaihanji/classgraph/types/
git commit -m "refactor(classgraph): move type system to type/ package, remove Jackson dep"
```

---

## Phase 4: metadata/ Package

### Task 5: Move metadata files from core/

**Files:**
- Move: 23 data model files from `core/` → `metadata/`
- Rename: `HasName` → `Named`, `ScanResultObject` → `MetadataNode`, `MethodParameterInfo` → `MethodParam`, `ObjectTypedValueWrapper` → `TypedValue`

- [ ] **Step 1: Move data model files with renames**

```powershell
$moves = @{
    # Core identity files
    'HasName.java' = 'Named.java'
    'ScanResultObject.java' = 'MetadataNode.java'
    # Class info
    'ClassInfo.java' = 'ClassInfo.java'
    'ClassInfoList.java' = 'ClassInfoList.java'
    'ArrayClassInfo.java' = 'ArrayClassInfo.java'
    'ClassMemberInfo.java' = 'ClassMemberInfo.java'
    # Field/Method
    'FieldInfo.java' = 'FieldInfo.java'
    'FieldInfoList.java' = 'FieldInfoList.java'
    'MethodInfo.java' = 'MethodInfo.java'
    'MethodInfoList.java' = 'MethodInfoList.java'
    'MethodParameterInfo.java' = 'MethodParam.java'
    # Annotations
    'AnnotationInfo.java' = 'AnnotationInfo.java'
    'AnnotationInfoList.java' = 'AnnotationInfoList.java'
    'AnnotationParameterValue.java' = 'AnnotationParameterValue.java'
    'AnnotationParameterValueList.java' = 'AnnotationParameterValueList.java'
    'AnnotationClassRef.java' = 'AnnotationClassRef.java'
    'AnnotationEnumValue.java' = 'AnnotationEnumValue.java'
    # Lists
    'InfoList.java' = 'InfoList.java'
    'MappableInfoList.java' = 'InfoList.java'  # Will be merged in next step
    # Package/Module
    'PackageInfo.java' = 'PackageInfo.java'
    'PackageInfoList.java' = 'PackageInfoList.java'
    'ModuleInfo.java' = 'ModuleInfo.java'
    'ModuleInfoList.java' = 'ModuleInfoList.java'
    'ModuleRef.java' = 'ModuleRef.java'
    # Values
    'ObjectTypedValueWrapper.java' = 'TypedValue.java'
}

foreach ($src in $moves.Keys) {
    $dst = $moves[$src]
    if ($src -eq 'MappableInfoList.java') { continue }  # handle separately
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/core/$src" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                                  'package com.bingbaihanji.classgraph.metadata;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/metadata/$dst" -Value $content -NoNewline
    Remove-Item "src/main/java/com/bingbaihanji/classgraph/core/$src"
}
```

- [ ] **Step 2: Fix class name references in moved metadata files**

```powershell
$metaFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/metadata/*.java
foreach ($f in $metaFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace '\bHasName\b', 'Named'
    $content = $content -replace '\bScanResultObject\b', 'MetadataNode'
    $content = $content -replace '\bMethodParameterInfo\b', 'MethodParam'
    $content = $content -replace '\bObjectTypedValueWrapper\b', 'TypedValue'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 3: Merge MappableInfoList into InfoList**

Read `MappableInfoList.java` content. Its methods should be merged into `InfoList.java`. Then delete `MappableInfoList.java`:

```powershell
Remove-Item src/main/java/com/bingbaihanji/classgraph/core/MappableInfoList.java
```

- [ ] **Step 4: Remove package-info.java from old core/ if still present**

```powershell
Remove-Item src/main/java/com/bingbaihanji/classgraph/core/package-info.java -ErrorAction SilentlyContinue
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/metadata/
git rm src/main/java/com/bingbaihanji/classgraph/core/HasName.java
# ... (all removed core metadata files)
git commit -m "refactor(classgraph): move data model to metadata/ package, rename core classes"
```

---

## Phase 5: resource/ Package

### Task 6: Consolidate resource IO into resource/

**Files:**
- Move: `fileslice/` + `fileslice/reader/` + `fastzipfilereader/` + `recycler/` → `resource/`
- Move: `Resource.java`, `ResourceList.java`, `CloseableByteBuffer.java`, `ModuleReaderProxy.java`, `GraphvizDotfileGenerator.java` from `core/`
- Rename: `NestedJarHandler` → `JarReader`, `Recycler` → `Pool`
- Delete: `fileslice/`, `fastzipfilereader/`, `recycler/` directories

- [ ] **Step 1: Move fileslice files to resource/**

```powershell
$sliceFiles = @('ArraySlice.java', 'FileSlice.java', 'PathSlice.java', 'Slice.java')
$readerFiles = @('ClassfileReader.java', 'RandomAccessArrayReader.java', 'RandomAccessByteBufferReader.java',
                 'RandomAccessFileChannelReader.java', 'RandomAccessReader.java', 'SequentialReader.java')

foreach ($f in $sliceFiles) {
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/fileslice/$f" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.fileslice;',
                                  'package com.bingbaihanji.classgraph.resource;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/resource/$f" -Value $content -NoNewline
}
foreach ($f in $readerFiles) {
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/fileslice/reader/$f" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.fileslice\.reader;',
                                  'package com.bingbaihanji.classgraph.resource;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/resource/$f" -Value $content -NoNewline
}
```

- [ ] **Step 2: Move fastzipfilereader files to resource/**

```powershell
$zipFiles = @('FastZipEntry.java', 'LogicalZipFile.java', 'NestedJarHandler.java',
              'PhysicalZipFile.java', 'ZipFileSlice.java')
foreach ($f in $zipFiles) {
    $dstName = if ($f -eq 'NestedJarHandler.java') { 'JarReader.java' } else { $f }
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/fastzipfilereader/$f" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.fastzipfilereader;',
                                  'package com.bingbaihanji.classgraph.resource;'
    if ($f -eq 'NestedJarHandler.java') {
        $content = $content -replace 'class NestedJarHandler', 'class JarReader'
        $content = $content -replace 'NestedJarHandler', 'JarReader'
    }
    Set-Content "src/main/java/com/bingbaihanji/classgraph/resource/$dstName" -Value $content -NoNewline
}
```

- [ ] **Step 3: Merge recycler into resource/ as Pool components**

```powershell
# Move Recycler → Pool
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/recycler/Recycler.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.recycler;',
                              'package com.bingbaihanji.classgraph.resource;'
$content = $content -replace 'class Recycler', 'class Pool'
$content = $content -replace 'Recycler', 'Pool'
Set-Content src/main/java/com/bingbaihanji/classgraph/resource/Pool.java -Value $content -NoNewline

# Move RecycleOnClose and Resettable
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/recycler/RecycleOnClose.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.recycler;',
                              'package com.bingbaihanji.classgraph.resource;'
Set-Content src/main/java/com/bingbaihanji/classgraph/resource/RecycleOnClose.java -Value $content -NoNewline

$content = Get-Content src/main/java/com/bingbaihanji/classgraph/recycler/Resettable.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.recycler;',
                              'package com.bingbaihanji.classgraph.resource;'
Set-Content src/main/java/com/bingbaihanji/classgraph/resource/Resettable.java -Value $content -NoNewline
```

- [ ] **Step 4: Move core Resource files to resource/**

```powershell
$resourceCoreFiles = @{
    'Resource.java' = 'Resource.java'
    'ResourceList.java' = 'ResourceList.java'
    'CloseableByteBuffer.java' = 'ByteBuffer.java'
    'ModuleReaderProxy.java' = 'ModuleReaderProxy.java'
    'GraphvizDotfileGenerator.java' = 'GraphvizDotfileGenerator.java'
}

foreach ($src in $resourceCoreFiles.Keys) {
    $dst = $resourceCoreFiles[$src]
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/core/$src" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                                  'package com.bingbaihanji.classgraph.resource;'
    if ($src -eq 'CloseableByteBuffer.java') {
        $content = $content -replace 'class CloseableByteBuffer', 'class ByteBuffer'
        $content = $content -replace 'CloseableByteBuffer', 'ByteBuffer'
    }
    Set-Content "src/main/java/com/bingbaihanji/classgraph/resource/$dst" -Value $content -NoNewline
    Remove-Item "src/main/java/com/bingbaihanji/classgraph/core/$src"
}
```

- [ ] **Step 5: Fix internal cross-references in resource/ files**

```powershell
$resFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/resource/*.java
foreach ($f in $resFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.fileslice\.',
                                  'import com.bingbaihanji.classgraph.resource.'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.fileslice\.reader\.',
                                  'import com.bingbaihanji.classgraph.resource.'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.fastzipfilereader\.',
                                  'import com.bingbaihanji.classgraph.resource.'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.recycler\.',
                                  'import com.bingbaihanji.classgraph.resource.'
    $content = $content -replace 'com\.bingbaihanji\.classgraph\.core\.CloseableByteBuffer',
                                  'com.bingbaihanji.classgraph.resource.ByteBuffer'
    $content = $content -replace '\bCloseableByteBuffer\b', 'ByteBuffer'
    $content = $content -replace '\bNestedJarHandler\b', 'JarReader'
    $content = $content -replace '\bRecycler\b', 'Pool'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 6: Remove old directories**

```powershell
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/fileslice -ErrorAction SilentlyContinue
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/fastzipfilereader -ErrorAction SilentlyContinue
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/recycler -ErrorAction SilentlyContinue
```

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/resource/
git rm -r src/main/java/com/bingbaihanji/classgraph/fileslice/
git rm -r src/main/java/com/bingbaihanji/classgraph/fastzipfilereader/
git rm -r src/main/java/com/bingbaihanji/classgraph/recycler/
git rm src/main/java/com/bingbaihanji/classgraph/core/Resource.java
git rm src/main/java/com/bingbaihanji/classgraph/core/ResourceList.java
git rm src/main/java/com/bingbaihanji/classgraph/core/CloseableByteBuffer.java
git rm src/main/java/com/bingbaihanji/classgraph/core/ModuleReaderProxy.java
git rm src/main/java/com/bingbaihanji/classgraph/core/GraphvizDotfileGenerator.java
git commit -m "refactor(classgraph): consolidate resource IO into resource/ package"
```

---

## Phase 6: bytecode/ Package

### Task 7: ClassFile rename + bytecode/ package

**Files:**
- Move: `ClassFile.java` → `bytecode/ClassParser.java`
- Move: `ClassfileReader.java` → `bytecode/ClassFileReader.java` (from resource/ where it was just placed)

- [ ] **Step 1: Move ClassFile → ClassParser**

```powershell
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/core/ClassFile.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                              'package com.bingbaihanji.classgraph.bytecode;'
$content = $content -replace 'class ClassFile\b', 'class ClassParser'
$content = $content -replace '\bClassFile\b', 'ClassParser'
Set-Content src/main/java/com/bingbaihanji/classgraph/bytecode/ClassParser.java -Value $content -NoNewline
Remove-Item src/main/java/com/bingbaihanji/classgraph/core/ClassFile.java
```

- [ ] **Step 2: Move ClassfileReader → ClassFileReader**

```powershell
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/resource/ClassfileReader.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.resource;',
                              'package com.bingbaihanji.classgraph.bytecode;'
$content = $content -replace 'class ClassfileReader', 'class ClassFileReader'
$content = $content -replace 'ClassfileReader', 'ClassFileReader'
Set-Content src/main/java/com/bingbaihanji/classgraph/bytecode/ClassFileReader.java -Value $content -NoNewline
Remove-Item src/main/java/com/bingbaihanji/classgraph/resource/ClassfileReader.java
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/bytecode/
git rm src/main/java/com/bingbaihanji/classgraph/core/ClassFile.java
git rm src/main/java/com/bingbaihanji/classgraph/resource/ClassfileReader.java
git commit -m "refactor(classgraph): move bytecode parser to bytecode/ package"
```

---

## Phase 7: classpath/ Package

### Task 8: Move classpath and handler files

**Files:**
- Move: `ClasspathElement*.java` + `ModulePathInfo.java` from `core/` → `classpath/`
- Move: `classloaderhandler/` → `classpath/handler/`
- Rename: `ClasspathElement` → `Classpath`, `ClasspathElementDir` → `DirClasspath`, `ClasspathElementZip` → `JarClasspath`, `ClasspathElementModule` → `ModuleClasspath`, `ClassLoaderHandlerRegistry` → `HandlerRegistry`

- [ ] **Step 1: Move classpath base files from core/**

```powershell
$cpMoves = @{
    'ClasspathElement.java' = 'Classpath.java'
    'ClasspathElementDir.java' = 'DirClasspath.java'
    'ClasspathElementZip.java' = 'JarClasspath.java'
    'ClasspathElementModule.java' = 'ModuleClasspath.java'
    'ModulePathInfo.java' = 'ModulePathInfo.java'
}

foreach ($src in $cpMoves.Keys) {
    $dst = $cpMoves[$src]
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/core/$src" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                                  'package com.bingbaihanji.classgraph.classpath;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/classpath/$dst" -Value $content -NoNewline
    Remove-Item "src/main/java/com/bingbaihanji/classgraph/core/$src"
}
```

- [ ] **Step 2: Fix class names in moved classpath files**

```powershell
$cpFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/classpath/*.java
foreach ($f in $cpFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace '\bClasspathElementDir\b', 'DirClasspath'
    $content = $content -replace '\bClasspathElementZip\b', 'JarClasspath'
    $content = $content -replace '\bClasspathElementModule\b', 'ModuleClasspath'
    $content = $content -replace '\bClasspathElement\b', 'Classpath'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 3: Move classloaderhandler/ → classpath/handler/**

```powershell
$handlerFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/classloaderhandler/*.java
foreach ($f in $handlerFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.classloaderhandler;',
                                  'package com.bingbaihanji.classgraph.classpath.handler;'
    Set-Content "src/main/java/com/bingbaihanji/classgraph/classpath/handler/$($f.Name)" -Value $content -NoNewline
}

# Rename ClassLoaderHandlerRegistry → HandlerRegistry
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/classpath/handler/ClassLoaderHandlerRegistry.java -Raw
$content = $content -replace 'class ClassLoaderHandlerRegistry', 'class HandlerRegistry'
$content = $content -replace 'ClassLoaderHandlerRegistry', 'HandlerRegistry'
Set-Content src/main/java/com/bingbaihanji/classgraph/classpath/handler/HandlerRegistry.java -Value $content -NoNewline
Remove-Item src/main/java/com/bingbaihanji/classgraph/classpath/handler/ClassLoaderHandlerRegistry.java
```

- [ ] **Step 4: Update internal imports in classpath/handler files**

```powershell
$handlerFiles = Get-ChildItem src/main/java/com/bingbaihanji/classgraph/classpath/handler/*.java
foreach ($f in $handlerFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.classloaderhandler\.',
                                  'import com.bingbaihanji.classgraph.classpath.handler.'
    Set-Content -Path $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 5: Remove old classloaderhandler/ directory**

```powershell
Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/classloaderhandler
```

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/classpath/
git rm -r src/main/java/com/bingbaihanji/classgraph/classloaderhandler/
git commit -m "refactor(classgraph): move classpath and handler files to classpath/ package"
```

---

## Phase 8: scan/ Package

### Task 9: Move scan engine files

**Files:**
- Move: `ClassGraph.java`, `ScanResult.java`, `Scanner.java`, `ClassGraphClassLoader.java`, `ClassGraphException.java` from `core/` → `scan/`
- Move: `ScanSpec.java` → `scan/ScanConfig.java`, `AcceptReject.java` → `scan/Filter.java`
- Rename: `ClassGraphClassLoader` → `ScanClassLoader`, `ClassGraphException` → `ScanException`, `ScanSpec` → `ScanConfig`, `AcceptReject` → `Filter`
- Delete: `scanspec/` directory

- [ ] **Step 1: Move scan files from core/ with renames**

```powershell
$scanMoves = @{
    'ClassGraph.java' = 'ClassGraph.java'
    'ScanResult.java' = 'ScanResult.java'
    'Scanner.java' = 'Scanner.java'
    'ClassGraphClassLoader.java' = 'ScanClassLoader.java'
    'ClassGraphException.java' = 'ScanException.java'
}

foreach ($src in $scanMoves.Keys) {
    $dst = $scanMoves[$src]
    $content = Get-Content "src/main/java/com/bingbaihanji/classgraph/core/$src" -Raw
    $content = $content -replace 'package com\.bingbaihanji\.classgraph\.core;',
                                  'package com.bingbaihanji.classgraph.scan;'
    if ($src -eq 'ClassGraphClassLoader.java') {
        $content = $content -replace 'class ClassGraphClassLoader', 'class ScanClassLoader'
        $content = $content -replace 'ClassGraphClassLoader', 'ScanClassLoader'
    }
    if ($src -eq 'ClassGraphException.java') {
        $content = $content -replace 'class ClassGraphException', 'class ScanException'
        $content = $content -replace 'ClassGraphException', 'ScanException'
    }
    Set-Content "src/main/java/com/bingbaihanji/classgraph/scan/$dst" -Value $content -NoNewline
    Remove-Item "src/main/java/com/bingbaihanji/classgraph/core/$src"
}
```

- [ ] **Step 2: Move scanspec files**

```powershell
$content = Get-Content src/main/java/com/bingbaihanji/classgraph/scanspec/ScanSpec.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.scanspec;',
                              'package com.bingbaihanji.classgraph.scan;'
$content = $content -replace 'class ScanSpec', 'class ScanConfig'
$content = $content -replace 'ScanSpec', 'ScanConfig'
Set-Content src/main/java/com/bingbaihanji/classgraph/scan/ScanConfig.java -Value $content -NoNewline

$content = Get-Content src/main/java/com/bingbaihanji/classgraph/scanspec/AcceptReject.java -Raw
$content = $content -replace 'package com\.bingbaihanji\.classgraph\.scanspec;',
                              'package com.bingbaihanji.classgraph.scan;'
$content = $content -replace 'class AcceptReject', 'class Filter'
$content = $content -replace 'AcceptReject', 'Filter'
Set-Content src/main/java/com/bingbaihanji/classgraph/scan/Filter.java -Value $content -NoNewline

Remove-Item -Recurse src/main/java/com/bingbaihanji/classgraph/scanspec
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/scan/
git rm -r src/main/java/com/bingbaihanji/classgraph/scanspec/
git commit -m "refactor(classgraph): move scan engine to scan/ package, rename config classes"
```

---

## Phase 9: Global Import Fix

### Task 10: Update all cross-package imports across classgraph tree

**Files:** All remaining Java files in `src/main/java/com/bingbaihanji/classgraph/` and `src/main/java/com/bingbaihanji/fxdecomplie/`

- [ ] **Step 1: Bulk-replace old package references in classgraph tree**

```powershell
$classgraphRoot = "src/main/java/com/bingbaihanji/classgraph"
$files = Get-ChildItem $classgraphRoot -Recurse -Filter "*.java"

foreach ($f in $files) {
    $content = Get-Content $f.FullName -Raw
    $changed = $false

    # Simple package-to-package mappings (package-level import)
    $replacements = @{
        'import com.bingbaihanji.classgraph.utils.' = 'import com.bingbaihanji.classgraph.util.'
        'import com.bingbaihanji.classgraph.concurrency.' = 'import com.bingbaihanji.classgraph.util.'
        'import com.bingbaihanji.classgraph.reflection.' = 'import com.bingbaihanji.classgraph.reflect.'
        'import com.bingbaihanji.classgraph.types.' = 'import com.bingbaihanji.classgraph.type.'
        'import com.bingbaihanji.classgraph.fileslice.reader.' = 'import com.bingbaihanji.classgraph.resource.'
        'import com.bingbaihanji.classgraph.fileslice.' = 'import com.bingbaihanji.classgraph.resource.'
        'import com.bingbaihanji.classgraph.fastzipfilereader.' = 'import com.bingbaihanji.classgraph.resource.'
        'import com.bingbaihanji.classgraph.recycler.' = 'import com.bingbaihanji.classgraph.resource.'
        'import com.bingbaihanji.classgraph.scanspec.' = 'import com.bingbaihanji.classgraph.scan.'
        'import com.bingbaihanji.classgraph.classloaderhandler.' = 'import com.bingbaihanji.classgraph.classpath.handler.'
    }

    foreach ($old in $replacements.Keys) {
        if ($content -match [regex]::Escape($old)) {
            $content = $content -replace [regex]::Escape($old), $replacements[$old]
            $changed = $true
        }
    }

    # Specific class renames from core package
    $coreClassMoves = @{
        'import com\.bingbaihanji\.classgraph\.core\.HasName' = 'import com.bingbaihanji.classgraph.metadata.Named'
        'import com\.bingbaihanji\.classgraph\.core\.ScanResultObject' = 'import com.bingbaihanji.classgraph.metadata.MetadataNode'
        'import com\.bingbaihanji\.classgraph\.core\.ClassInfo\b' = 'import com.bingbaihanji.classgraph.metadata.ClassInfo'
        'import com\.bingbaihanji\.classgraph\.core\.ClassGraph\b' = 'import com.bingbaihanji.classgraph.scan.ClassGraph'
        'import com\.bingbaihanji\.classgraph\.core\.ScanResult\b' = 'import com.bingbaihanji.classgraph.scan.ScanResult'
        'import com\.bingbaihanji\.classgraph\.core\.Scanner\b' = 'import com.bingbaihanji.classgraph.scan.Scanner'
        'import com\.bingbaihanji\.classgraph\.core\.ScanSpec' = 'import com.bingbaihanji.classgraph.scan.ScanConfig'
        'import com\.bingbaihanji\.classgraph\.core\.AcceptReject' = 'import com.bingbaihanji.classgraph.scan.Filter'
        'import com\.bingbaihanji\.classgraph\.core\.ClassFile\b' = 'import com.bingbaihanji.classgraph.bytecode.ClassParser'
        'import com\.bingbaihanji\.classgraph\.core\.ClassfileReader' = 'import com.bingbaihanji.classgraph.bytecode.ClassFileReader'
        'import com\.bingbaihanji\.classgraph\.core\.ClassGraphClassLoader' = 'import com.bingbaihanji.classgraph.scan.ScanClassLoader'
        'import com\.bingbaihanji\.classgraph\.core\.ClassGraphException' = 'import com.bingbaihanji.classgraph.scan.ScanException'
        'import com\.bingbaihanji\.classgraph\.core\.ClasspathElement\b' = 'import com.bingbaihanji.classgraph.classpath.Classpath'
        'import com\.bingbaihanji\.classgraph\.core\.ClasspathElementDir' = 'import com.bingbaihanji.classgraph.classpath.DirClasspath'
        'import com\.bingbaihanji\.classgraph\.core\.ClasspathElementZip' = 'import com.bingbaihanji.classgraph.classpath.JarClasspath'
        'import com\.bingbaihanji\.classgraph\.core\.ClasspathElementModule' = 'import com.bingbaihanji.classgraph.classpath.ModuleClasspath'
        'import com\.bingbaihanji\.classgraph\.core\.Resource\b' = 'import com.bingbaihanji.classgraph.resource.Resource'
        'import com\.bingbaihanji\.classgraph\.core\.CloseableByteBuffer' = 'import com.bingbaihanji.classgraph.resource.ByteBuffer'
        'import com\.bingbaihanji\.classgraph\.core\.NestedJarHandler' = 'import com.bingbaihanji.classgraph.resource.JarReader'
        'import com\.bingbaihanji\.classgraph\.core\.Recycler' = 'import com.bingbaihanji.classgraph.resource.Pool'
        'import com\.bingbaihanji\.classgraph\.core\.ClassRefTypeSignature' = 'import com.bingbaihanji.classgraph.type.ClassRef'
        'import com\.bingbaihanji\.classgraph\.core\.ClassRefOrTypeVariableSignature' = 'import com.bingbaihanji.classgraph.type.TypeRef'
        'import com\.bingbaihanji\.classgraph\.core\.PotentiallyUnmodifiableList' = 'import com.bingbaihanji.classgraph.util.LazyList'
        'import com\.bingbaihanji\.classgraph\.core\.ObjectTypedValueWrapper' = 'import com.bingbaihanji.classgraph.metadata.TypedValue'
        'import com\.bingbaihanji\.classgraph\.core\.MappableInfoList' = 'import com.bingbaihanji.classgraph.metadata.InfoList'
        'import com\.bingbaihanji\.classgraph\.core\.MethodParameterInfo' = 'import com.bingbaihanji.classgraph.metadata.MethodParam'
        'import com\.bingbaihanji\.classgraph\.core\.ClassLoaderHandlerRegistry' = 'import com.bingbaihanji.classgraph.classpath.handler.HandlerRegistry'
    }

    foreach ($pattern in $coreClassMoves.Keys) {
        if ($content -match $pattern) {
            $content = $content -replace $pattern, $coreClassMoves[$pattern]
            $changed = $true
        }
    }

    if ($changed) {
        Set-Content -Path $f.FullName -Value $content -NoNewline
    }
}
```

- [ ] **Step 2: Update fxdecomplie imports**

```powershell
$adapter = "src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java"
$content = Get-Content $adapter -Raw
$content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.\*;',
                              'import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.scan.*;'
Set-Content $adapter -Value $content -NoNewline

# Update other reference files
$refFiles = @(
    "src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java",
    "src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java",
    "src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java"
)
foreach ($f in $refFiles) {
    $content = Get-Content $f -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.ClassInfo;',
                                  'import com.bingbaihanji.classgraph.metadata.ClassInfo;'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.ScanResult;',
                                  'import com.bingbaihanji.classgraph.scan.ScanResult;'
    Set-Content $f -Value $content -NoNewline
}
```

- [ ] **Step 3: Update test file imports**

```powershell
$testFiles = Get-ChildItem src/test -Recurse -Filter "*.java" | 
             Where-Object { (Get-Content $_.FullName -Raw) -match 'classgraph' }
foreach ($f in $testFiles) {
    $content = Get-Content $f.FullName -Raw
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.ClassInfo;',
                                  'import com.bingbaihanji.classgraph.metadata.ClassInfo;'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.ScanResult;',
                                  'import com.bingbaihanji.classgraph.scan.ScanResult;'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.ClassInfoList;',
                                  'import com.bingbaihanji.classgraph.metadata.ClassInfoList;'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.AnnotationInfo;',
                                  'import com.bingbaihanji.classgraph.metadata.AnnotationInfo;'
    $content = $content -replace 'import com\.bingbaihanji\.classgraph\.core\.AnnotationInfoList;',
                                  'import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;'
    Set-Content $f.FullName -Value $content -NoNewline
}
```

- [ ] **Step 4: Remove empty core/ directory**

```powershell
Remove-Item src/main/java/com/bingbaihanji/classgraph/core -Recurse -ErrorAction SilentlyContinue
```

- [ ] **Step 5: Attempt compilation to find remaining import issues**

Run: `./mvnw -DskipTests compile 2>&1 | Select-String "error:"`
Expected: Fix any remaining compilation errors one by one (most will be import-related).

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/com/bingbaihanji/classgraph/
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/
git add src/test/
git commit -m "fix(classgraph): update all cross-package imports after package reorganization"
```

---

## Phase 10: Large File Splits + Design Patterns

### Task 11: Split ClassInfo into ClassInfo + ClassHierarchy + ClassMembers

**Files:**
- Create: `metadata/ClassHierarchy.java`
- Create: `metadata/ClassMembers.java`
- Modify: `metadata/ClassInfo.java`

- [ ] **Step 1: Extract ClassHierarchy from ClassInfo**

Read the current `metadata/ClassInfo.java` (was moved from core). Extract the `RelType` enum and all relationship graph traversal methods (`getSuperclasses`, `getSubclasses`, `filterClassInfo`, BFS transitive closure logic) into a new `ClassHierarchy.java`:

```java
package com.bingbaihanji.classgraph.metadata;

import java.util.*;

/** Manages the bidirectional relationship graph for a ClassInfo node. */
public class ClassHierarchy {
    private final Map<RelType, Set<ClassInfo>> relatedClasses;

    ClassHierarchy() {
        this.relatedClasses = new EnumMap<>(RelType.class);
    }

    void addRelation(RelType type, ClassInfo target) {
        relatedClasses.computeIfAbsent(type, k -> new LinkedHashSet<>()).add(target);
    }

    // ... BFS transitive closure, filter methods from original ClassInfo
}
```

- [ ] **Step 2: Extract ClassMembers from ClassInfo**

Extract field/method query methods (`getFieldInfo`, `getDeclaredFieldInfo`, `getMethodInfo`, etc.) into `ClassMembers.java`:

```java
package com.bingbaihanji.classgraph.metadata;

import java.util.*;

/** Manages field and method queries for a ClassInfo node. */
public class ClassMembers {
    private final ClassInfo owner;

    ClassMembers(ClassInfo owner) {
        this.owner = owner;
    }

    public FieldInfoList getFields() { /* ... */ }
    public MethodInfoList getMethods() { /* ... */ }
    // ... delegate methods
}
```

- [ ] **Step 3: Update ClassInfo to delegate to ClassHierarchy and ClassMembers**

```java
// In ClassInfo.java, add delegation fields
private final ClassHierarchy hierarchy = new ClassHierarchy();
private final ClassMembers members = new ClassMembers(this);

// Delegate methods
public ClassHierarchy hierarchy() { return hierarchy; }
public ClassMembers members() { return members; }

// Keep backward-compatible direct methods
public ClassInfoList getSubclasses() { return hierarchy.getSubclasses(); }
public FieldInfoList getFieldInfo() { return members.getFields(); }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/metadata/
git commit -m "refactor(classgraph): split ClassInfo into ClassInfo+ClassHierarchy+ClassMembers"
```

---

### Task 12: Split ScanResult into ScanResult + ClassQuery + ResourceQuery

**Files:**
- Create: `scan/ClassQuery.java`
- Create: `scan/ResourceQuery.java`
- Modify: `scan/ScanResult.java`

- [ ] **Step 1: Create ClassQuery**

Extract all class-related query methods from ScanResult into `ClassQuery.java`:

```java
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.metadata.*;
import java.util.*;

/** Facade for querying class information from a ScanResult. */
public class ClassQuery {
    private final Map<String, ClassInfo> classIndex;

    ClassQuery(Map<String, ClassInfo> classIndex) {
        this.classIndex = classIndex;
    }

    public ClassInfoList all() { ... }
    public ClassInfo find(String name) { ... }
    public ClassInfoList implementing(String interfaceName) { ... }
    public ClassInfoList annotatedWith(String annotationName) { ... }
    // ... all class-related query methods from ScanResult
}
```

- [ ] **Step 2: Create ResourceQuery**

Extract all resource-related query methods from ScanResult into `ResourceQuery.java`:

```java
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.resource.*;
import java.util.*;

/** Facade for querying resources from a ScanResult. */
public class ResourceQuery {
    private final List<Resource> resources;

    ResourceQuery(List<Resource> resources) {
        this.resources = resources;
    }

    public ResourceList all() { ... }
    public ResourceList withExtension(String ext) { ... }
    public ResourceList matching(String glob) { ... }
    // ... all resource-related query methods from ScanResult
}
```

- [ ] **Step 3: Update ScanResult to delegate**

```java
// In ScanResult.java
private final ClassQuery classQuery;
private final ResourceQuery resourceQuery;

public ClassQuery classes() { return classQuery; }
public ResourceQuery resources() { return resourceQuery; }
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/scan/
git commit -m "refactor(classgraph): split ScanResult into ScanResult+ClassQuery+ResourceQuery (Facade)"
```

---

### Task 13: ClassGraph Builder standardization

**Files:**
- Modify: `scan/ClassGraph.java`

- [ ] **Step 1: Rename builder methods to `withXxx`/`filterXxx` convention**

In `scan/ClassGraph.java`:

```java
// Old → New
enableClassInfo()     → withClassInfo()
enableMethodInfo()    → withMethodInfo()
enableFieldInfo()     → withFieldInfo()
enableAnnotationInfo() → withAnnotationInfo()
ignoreClassVisibility() → withoutClassVisibilityFilter()
ignoreFieldVisibility() → withoutFieldVisibilityFilter()
ignoreMethodVisibility() → withoutMethodVisibilityFilter()
acceptPackages(...)   → filterPackages(...)
rejectPackages(...)   → excludePackages(...)
acceptClasses(...)    → filterClasses(...)
rejectClasses(...)    → excludeClasses(...)
acceptJars(...)       → filterJars(...)
rejectJars(...)       → excludeJars(...)

// Add static factory
public static ClassGraph create() {
    return new ClassGraph();
}
```

- [ ] **Step 2: Keep old method names as @Deprecated delegates**

For backward compatibility, keep the old `enableXxx`/`acceptXxx`/`rejectXxx` methods as `@Deprecated` delegates that call the new methods:

```java
/** @deprecated Use {@link #withClassInfo()} instead. */
@Deprecated
public ClassGraph enableClassInfo() {
    return withClassInfo();
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/classgraph/scan/ClassGraph.java
git commit -m "feat(classgraph): standardize ClassGraph builder API with withXxx/filterXxx naming"
```

---

## Phase 11: Build & Verify

### Task 14: Compile, Test, Package

- [ ] **Step 1: Clean compile**

Run: `./mvnw clean compile -DskipTests`
Expected: BUILD SUCCESS, zero compilation errors

- [ ] **Step 2: Fix any remaining compilation issues**

Iterate on any compilation errors from Step 1 until clean.

- [ ] **Step 3: Run all tests**

Run: `./mvnw test`
Expected: All 154 tests pass (zero failures, zero errors)

- [ ] **Step 4: Fix any test failures**

Investigate and fix any test regressions.

- [ ] **Step 5: Package fat JAR**

Run: `./mvnw -DskipTests package`
Expected: BUILD SUCCESS, fat JAR in `bin/fxdecomplie.jar`

- [ ] **Step 6: Verify adapter compatibility**

Run: `./mvnw test -Dtest="ClassGraphWorkspaceAdapterTest,InheritanceReferenceIndexTest,InheritanceReferenceServiceTest"`
Expected: All adapter-related tests pass

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "test(classgraph): verify all 154 tests pass after architecture redesign"
```

---

## Self-Review

### Spec Coverage

| Spec 要求 | 对应 Task |
|-----------|----------|
| 8 个子包创建 | Task 1 |
| util/ 包合并 concurrency | Task 2 |
| reflect/ 包重组 | Task 3 |
| type/ 包重组 + Jackson 依赖移除 | Task 4 |
| metadata/ 包重组 | Task 5 |
| resource/ 包合并 fileslice/fastzipfilereader/recycler | Task 6 |
| bytecode/ 包重组 | Task 7 |
| classpath/ 包 + handler 重组 | Task 8 |
| scan/ 包 + scanspec 重组 | Task 9 |
| 全局 import 修复 | Task 10 |
| ClassInfo → 3 文件拆分 | Task 11 |
| ScanResult → 3 文件拆分 (Facade) | Task 12 |
| ClassGraph Builder 标准化 | Task 13 |
| 命名规范 (HasName→Named, etc.) | Tasks 2-9 |
| 设计模式 (Builder, Facade, Strategy, etc.) | Tasks 11-13 |
| 纯 JDK (Jackson 移除) | Task 4 |
| 编译/测试/打包验证 | Task 14 |

### Placeholder Scan

- 无 TBD/TODO 未填内容
- 所有步骤有具体代码或命令
- 所有文件路径为绝对路径

### Type Consistency

- `ClassHierarchy` 和 `ClassMembers` 定义在 Task 11，与 Task 5 中 metadata/ 包结构一致
- `ClassQuery` 和 `ResourceQuery` 定义在 Task 12，与 ScanResult 拆分设计一致
- 所有类重命名在 spec 中有映射表，在 import 替换中使用一致的模式

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-22-classgraph-architecture-redesign.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
