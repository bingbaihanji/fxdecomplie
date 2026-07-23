# ClassGraph 继承引用视图 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ClassGraph 最小 API 迁移到 `core/classgraph`，并新增“继承引用”服务、索引与 UI，替换现有 `InheritancePane`。

**Architecture:** 在 `core/classgraph` 提供简化版 `ClassGraph`/`ScanResult`/`ClassInfo`/`AnnotationInfo`；`service/reference` 通过 ASM 读取 `WorkspaceIndex` 中的 class bytes 构建索引；`model/reference` 提供 UI 模型；`ui/inheritance` 新建 `InheritanceReferencePane` 并接入 `WorkspaceTabManager`。

**Tech Stack:** Java 25, JavaFX 25, ASM 9.9, JUnit 5.12.1, Maven Shade

---

## File Structure

| File | Responsibility |
|---|---|
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraphException.java` | 异常基类 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/HasName.java` | 命名接口 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResultObject.java` | 扫描结果对象基类 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/Resource.java` | 资源占位 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfo.java` | 注解元数据 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfoList.java` | 注解列表 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfo.java` | 类元数据 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfoList.java` | 类列表 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraph.java` | 扫描入口 |
| `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResult.java` | 扫描结果 |
| `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/Kind.java` | 引用节点类型 |
| `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceNode.java` | 引用节点 |
| `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceGroup.java` | 引用分组 |
| `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceTree.java` | 引用树 |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java` | Workspace → ScanResult |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java` | 内存引用索引 |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java` | 异步索引构建/刷新 |
| `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java` | 业务入口 |
| `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceReferencePane.java` | 新继承引用面板 |
| `src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java` | 替换 InheritancePane |
| `src/main/resources/language/language_zh_CN.properties` | 中文语言资源 |
| `src/main/resources/language/language_en.properties` | 英文语言资源 |
| `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapterTest.java` | 适配器测试 |
| `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexTest.java` | 索引测试 |
| `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceServiceTest.java` | 服务测试 |
| `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/TestClassCompiler.java` | 测试样本编译辅助 |

---

## Task 1: core/classgraph 基础设施

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraphException.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/HasName.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResultObject.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/Resource.java`

- [ ] **Step 1: 创建异常与接口**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

public class ClassGraphException extends RuntimeException {
    public ClassGraphException(String message) { super(message); }
    public ClassGraphException(String message, Throwable cause) { super(message, cause); }
}
```

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

public interface HasName {
    String getName();
}
```

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult;

public abstract class ScanResultObject {
    protected ScanResult scanResult;

    void setScanResult(ScanResult scanResult) {
        this.scanResult = scanResult;
    }
}
```

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

public final class Resource {
    private final String path;

    public Resource(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS (no test failures expected since no tests yet)

- [ ] **Step 4: 复制 ClassGraph LICENSE**

将本地 ClassGraph 的 LICENSE 文件复制到项目资源目录：

```bash
cp "D:/javaProject/github/classgraph/LICENSE-ClassGraph.txt" \
   "src/main/resources/META-INF/licenses/LICENSE-ClassGraph.txt"
```

若 `src/main/resources/META-INF/licenses/` 目录不存在则先创建。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraphException.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/HasName.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResultObject.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/Resource.java \
        src/main/resources/META-INF/licenses/LICENSE-ClassGraph.txt
git commit -m "feat(core/classgraph): add base exception, interfaces, placeholder and ClassGraph license"
```

---

## Task 2: core/classgraph 注解模型

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfo.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfoList.java`

- [ ] **Step 1: 创建 AnnotationInfo 与列表**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.HasName;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class AnnotationInfo implements Comparable<AnnotationInfo>, HasName {
    private final String name;

    public AnnotationInfo(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int compareTo(AnnotationInfo o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof AnnotationInfo that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
```

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public final class AnnotationInfoList extends ArrayList<AnnotationInfo> {
    public AnnotationInfoList() {
    }

    public AnnotationInfoList(Collection<? extends AnnotationInfo> c) {
        super(c);
    }

    public AnnotationInfoList filter(Predicate<AnnotationInfo> predicate) {
        AnnotationInfoList result = new AnnotationInfoList();
        for (AnnotationInfo info : this) {
            if (predicate.test(info)) {
                result.add(info);
            }
        }
        return result;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfo.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/AnnotationInfoList.java
git commit -m "feat(core/classgraph): add AnnotationInfo and AnnotationInfoList"
```

---

## Task 3: core/classgraph ClassInfo 与 ClassInfoList

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfo.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfoList.java`

- [ ] **Step 1: 创建 ClassInfo**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

public final class ClassInfo extends ScanResultObject implements Comparable<ClassInfo>, HasName {
    private final String name;
    private final int modifiers;
    private ClassInfo superclass;
    private ClassInfoList interfaces;
    private ClassInfoList subclasses;
    private ClassInfoList implementingClasses;
    private AnnotationInfoList annotationInfo;
    private String sourceFile;
    private boolean externalClass = true;
    private boolean scannedClass;
    private String fullPath;

    public ClassInfo(String name, int modifiers) {
        this.name = Objects.requireNonNull(name, "name");
        this.modifiers = modifiers;
        this.interfaces = new ClassInfoList();
        this.subclasses = new ClassInfoList();
        this.implementingClasses = new ClassInfoList();
        this.annotationInfo = new AnnotationInfoList();
    }

    @Override
    public String getName() {
        return name;
    }

    public String getPackageName() {
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(0, idx) : "";
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isPublic() {
        return java.lang.reflect.Modifier.isPublic(modifiers);
    }

    public boolean isAbstract() {
        return java.lang.reflect.Modifier.isAbstract(modifiers);
    }

    public boolean isInterface() {
        return java.lang.reflect.Modifier.isInterface(modifiers);
    }

    public boolean isAnnotation() {
        return (modifiers & 0x00002000) != 0;
    }

    public boolean isFinal() {
        return java.lang.reflect.Modifier.isFinal(modifiers);
    }

    public ClassInfo getSuperclass() {
        return superclass;
    }

    void setSuperclass(ClassInfo superclass) {
        this.superclass = superclass;
    }

    public ClassInfoList getInterfaces() {
        return interfaces;
    }

    void setInterfaces(ClassInfoList interfaces) {
        this.interfaces = interfaces != null ? interfaces : new ClassInfoList();
    }

    public ClassInfoList getSubclasses() {
        return subclasses;
    }

    void addSubclass(ClassInfo subclass) {
        if (subclass != null && !subclasses.contains(subclass)) {
            subclasses.add(subclass);
        }
    }

    public ClassInfoList getImplementingClasses() {
        return implementingClasses;
    }

    void addImplementingClass(ClassInfo impl) {
        if (impl != null && !implementingClasses.contains(impl)) {
            implementingClasses.add(impl);
        }
    }

    public AnnotationInfoList getAnnotationInfo() {
        return annotationInfo;
    }

    void setAnnotationInfo(AnnotationInfoList annotationInfo) {
        this.annotationInfo = annotationInfo != null ? annotationInfo : new AnnotationInfoList();
    }

    public ClassInfoList getAnnotations() {
        ClassInfoList list = new ClassInfoList();
        if (scanResult != null) {
            for (AnnotationInfo ai : annotationInfo) {
                ClassInfo ci = scanResult.getClassInfo(ai.getName());
                if (ci != null) {
                    list.add(ci);
                }
            }
        }
        return list;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public boolean isExternalClass() {
        return externalClass;
    }

    void setExternalClass(boolean externalClass) {
        this.externalClass = externalClass;
    }

    public boolean isScannedClass() {
        return scannedClass;
    }

    void setScannedClass(boolean scannedClass) {
        this.scannedClass = scannedClass;
    }

    public String getFullPath() {
        return fullPath;
    }

    void setFullPath(String fullPath) {
        this.fullPath = fullPath;
    }

    @Override
    public int compareTo(ClassInfo o) {
        return name.compareTo(o.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ClassInfo that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
```

- [ ] **Step 2: 创建 ClassInfoList**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public final class ClassInfoList extends ArrayList<ClassInfo> {
    public ClassInfoList() {
    }

    public ClassInfoList(Collection<? extends ClassInfo> c) {
        super(c);
    }

    public ClassInfoList filter(Predicate<ClassInfo> predicate) {
        ClassInfoList result = new ClassInfoList();
        for (ClassInfo info : this) {
            if (predicate.test(info)) {
                result.add(info);
            }
        }
        return result;
    }

    public ClassInfoList getInterfaces() {
        return filter(ClassInfo::isInterface);
    }

    public ClassInfoList getClasses() {
        return filter(ci -> !ci.isInterface() && !ci.isAnnotation());
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfo.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassInfoList.java
git commit -m "feat(core/classgraph): add ClassInfo and ClassInfoList"
```

---

## Task 4: core/classgraph ClassGraph 与 ScanResult

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraph.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResult.java`

- [ ] **Step 1: 创建 ClassGraph**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Simplified ClassGraph-compatible scan entry point for fxdecomplie.
 *
 * <p>Based on ClassGraph 4.8.184</p>
 * <p>Copyright Luke Hutchison</p>
 * <p>MIT License</p>
 */
public final class ClassGraph {
    private final List<String> acceptPackages = new ArrayList<>();
    private final List<String> rejectPackages = new ArrayList<>();

    public ClassGraph acceptPackages(String... packages) {
        acceptPackages.addAll(Arrays.asList(packages));
        return this;
    }

    public ClassGraph rejectPackages(String... packages) {
        rejectPackages.addAll(Arrays.asList(packages));
        return this;
    }

    public ScanResult scan() {
        return new ScanResult(java.util.Map.of());
    }

    List<String> getAcceptPackages() {
        return List.copyOf(acceptPackages);
    }

    List<String> getRejectPackages() {
        return List.copyOf(rejectPackages);
    }
}
```

- [ ] **Step 2: 创建 ScanResult**

```java
package com.bingbaihanji.fxdecomplie.core.classgraph;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfoList;

import java.io.Closeable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScanResult implements Closeable {
    private final Map<String, ClassInfo> classNameToClassInfo;

    public ScanResult(Map<String, ClassInfo> classNameToClassInfo) {
        Map<String, ClassInfo> map = new LinkedHashMap<>();
        for (Map.Entry<String, ClassInfo> entry : classNameToClassInfo.entrySet()) {
            ClassInfo ci = entry.getValue();
            map.put(entry.getKey(), ci);
            ci.setScanResult(this);
        }
        this.classNameToClassInfo = Collections.unmodifiableMap(map);
    }

    public ClassInfoList getAllClasses() {
        return new ClassInfoList(classNameToClassInfo.values());
    }

    public ClassInfo getClassInfo(String name) {
        return classNameToClassInfo.get(name);
    }

    public ClassInfoList getClassesWithAnnotation(String annotationName) {
        ClassInfoList result = new ClassInfoList();
        for (ClassInfo ci : classNameToClassInfo.values()) {
            for (AnnotationInfo ai : ci.getAnnotationInfo()) {
                if (ai.getName().equals(annotationName)) {
                    result.add(ci);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void close() {
        // no-op for in-memory scan result
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ClassGraph.java \
        src/main/java/com/bingbaihanji/fxdecomplie/core/classgraph/ScanResult.java
git commit -m "feat(core/classgraph): add ClassGraph entry and ScanResult"
```

---

## Task 5: model/reference UI 模型

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/Kind.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceNode.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceGroup.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/model/reference/InheritanceReferenceTree.java`

- [ ] **Step 1: 创建模型记录**

```java
package com.bingbaihanji.fxdecomplie.model.reference;

public enum Kind {
    SELF, SUPER_CLASS, INTERFACE, ANNOTATION, IMPLEMENTATION, SUBCLASS, UNRESOLVED
}
```

```java
package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.Objects;

public record InheritanceReferenceNode(
        String className,
        String displayName,
        Kind kind,
        String fullPath,
        int depth,
        boolean navigable
) {
    public InheritanceReferenceNode {
        Objects.requireNonNull(className, "className");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(kind, "kind");
    }
}
```

```java
package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.List;
import java.util.Objects;

public record InheritanceReferenceGroup(
        Kind kind,
        String title,
        List<InheritanceReferenceNode> children,
        boolean collapsible
) {
    public InheritanceReferenceGroup {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(children, "children");
        children = List.copyOf(children);
    }
}
```

```java
package com.bingbaihanji.fxdecomplie.model.reference;

import java.util.List;
import java.util.Objects;

public record InheritanceReferenceTree(
        InheritanceReferenceNode root,
        List<InheritanceReferenceGroup> groups,
        boolean partial,
        String statusMessage
) {
    public InheritanceReferenceTree {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(groups, "groups");
        groups = List.copyOf(groups);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/model/reference/
git commit -m "feat(model/reference): add inheritance reference UI models"
```

---

## Task 6: ClassGraphWorkspaceAdapter

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java`

- [ ] **Step 1: 实现适配器**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.AnnotationInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfoList;
import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfoList;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.*;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfoList;
import com.bingbaihanji.fxdecomplie.model.ClassIndexEntry;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class ClassGraphWorkspaceAdapter {

    private static final Logger log = LoggerFactory.getLogger(ClassGraphWorkspaceAdapter.class);

    private ClassGraphWorkspaceAdapter() {
        throw new AssertionError("utility class");
    }

    public static ScanResult scan(Workspace workspace) {
        return scan(workspace, null);
    }

    public static ScanResult scan(Workspace workspace, Predicate<String> classFilter) {
        if (workspace == null) {
            return new com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult(Map.of());
        }
        WorkspaceIndex index = workspace.getIndex();
        if (index == null || index == WorkspaceIndex.EMPTY) {
            return new ScanResult(Map.of());
        }

        Map<String, com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo> classInfoByName = new LinkedHashMap<>();
        Map<String, String> fullPathByName = new LinkedHashMap<>();
        List<ClassMetadata> metadatas = new ArrayList<>();

        for (ClassIndexEntry entry : index.classes()) {
            String fullPath = entry.fullPath();
            String internalName = entry.internalName();
            if (classFilter != null && !classFilter.test(internalName)) {
                continue;
            }
            byte[] bytes = readBytes(entry);
            if (bytes == null) {
                continue;
            }
            ClassMetadata metadata = parse(bytes, fullPath);
            if (metadata == null) {
                continue;
            }
            fullPathByName.put(metadata.name, fullPath);
            metadatas.add(metadata);
        }

        for (ClassMetadata metadata : metadatas) {
            com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo ci = toClassInfo(metadata, classInfoByName);
            ci.setScannedClass(true);
            ci.setExternalClass(false);
            ci.setFullPath(metadata.fullPath);
        }

        linkRelations(classInfoByName, metadatas);

        return new ScanResult(classInfoByName);
    }

    private static byte[] readBytes(ClassIndexEntry entry) {
        FileTreeNode node = entry.node();
        if (node != null) {
            try {
                return node.readBytes();
            } catch (IOException e) {
                log.debug("读取节点字节失败: {}", node.getFullPath(), e);
            }
        }
        return entry.bytes();
    }

    private static ClassMetadata parse(byte[] bytes, String fullPath) {
        try {
            ClassReader reader = new ClassReader(bytes);
            MetadataVisitor visitor = new MetadataVisitor();
            reader.accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return new ClassMetadata(visitor.name, visitor.access, visitor.superName,
                    List.copyOf(visitor.interfaces), List.copyOf(visitor.annotations), fullPath);
        } catch (Exception e) {
            log.debug("ASM 解析类失败: {}", fullPath, e);
            return null;
        }
    }

    private static com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo toClassInfo(ClassMetadata metadata,
                                                                                           Map<String, com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo> classInfoByName) {
        return classInfoByName.computeIfAbsent(metadata.name,
                name -> new com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo(name, metadata.access));
    }

    private static void linkRelations(Map<String, ClassInfo> classInfoByName,
                                      List<ClassMetadata> metadatas) {
        for (ClassMetadata metadata : metadatas) {
            com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo ci = classInfoByName.get(metadata.name);
            if (ci == null) {
                continue;
            }

            // superclass
            if (metadata.superName != null) {
                com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo superClass = classInfoByName.get(metadata.superName);
                if (superClass == null) {
                    superClass = new com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo(metadata.superName, 0);
                    superClass.setExternalClass(true);
                    classInfoByName.put(metadata.superName, superClass);
                }
                ci.setSuperclass(superClass);
                superClass.addSubclass(ci);
            }

            // interfaces
            ClassInfoList interfaces = new com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfoList();
            for (String itf : metadata.interfaces) {
                com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo itfInfo = classInfoByName.get(itf);
                if (itfInfo == null) {
                    itfInfo = new com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo(itf, Opcodes.ACC_INTERFACE);
                    itfInfo.setExternalClass(true);
                    classInfoByName.put(itf, itfInfo);
                }
                interfaces.add(itfInfo);
                itfInfo.addImplementingClass(ci);
            }
            ci.setInterfaces(interfaces);

            // annotations
            com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfoList annotations = new AnnotationInfoList();
            for (String ann : metadata.annotations) {
                annotations.add(new com.bingbaihanji.fxdecomplie.core.classgraph.core.AnnotationInfo(ann));
                ClassInfo annInfo = classInfoByName.get(ann);
                if (annInfo == null) {
                    annInfo = new ClassInfo(ann, Opcodes.ACC_INTERFACE | Opcodes.ACC_ABSTRACT | 0x00002000);
                    annInfo.setExternalClass(true);
                    classInfoByName.put(ann, annInfo);
                }
            }
            ci.setAnnotationInfo(annotations);
        }
    }

    private static final class MetadataVisitor extends ClassVisitor {
        String name;
        int access;
        String superName;
        final List<String> interfaces = new ArrayList<>();
        final List<String> annotations = new ArrayList<>();

        MetadataVisitor() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.name = name;
            this.access = access;
            this.superName = superName;
            if (interfaces != null) {
                this.interfaces.addAll(List.of(interfaces));
            }
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            Type type = Type.getType(descriptor);
            String internalName = type.getInternalName();
            if (internalName != null && !internalName.isBlank()) {
                annotations.add(internalName);
            }
            return null;
        }
    }

    private record ClassMetadata(String name, int access, String superName,
                                 List<String> interfaces, List<String> annotations,
                                 String fullPath) {
    }
}
```

**注意：** `ClassIndexEntry` 当前没有 `node()` 方法。需要在 Task 7 中给 `ClassIndexEntry` 添加 `node()` 返回 `FileTreeNode`，或者在适配器中退化使用 `entry.bytes()`。本计划选择**在 Task 7 中修改 `ClassIndexEntry`** 添加 `node()` 字段与 getter。

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: 编译失败，因为 `ClassIndexEntry.node()` 不存在；继续 Task 7 修复。

---

## Task 7: 为 ClassIndexEntry 添加 node 引用

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/model/ClassIndexEntry.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/model/WorkspaceIndex.java:123-125`

- [ ] **Step 1: 修改 ClassIndexEntry 添加 node 字段**

在现有 `ClassIndexEntry` 中新增 `node` 字段，并在现有构造函数中初始化它。

修改后记录形如：

```java
public final class ClassIndexEntry {
    private final String fullPath;
    private final String internalName;
    private final String simpleName;
    private final FileTreeNode.ByteLoader byteLoader;
    private final List<MemberIndexEntry> methods;
    private final List<MemberIndexEntry> fields;
    private final String superName;
    private final List<String> interfaces;
    private final FileTreeNode node;
    private volatile String bytecodeText;

    // 保留现有构造函数，并在 this(...) 链末端新增 node 参数
    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           byte[] bytes, List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this(fullPath, internalName, simpleName, () -> bytes, methods, fields, null, List.of(), null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields) {
        this(fullPath, internalName, simpleName, byteLoader, methods, fields, null, List.of(), null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields,
                           String superName,
                           List<String> interfaces) {
        this(fullPath, internalName, simpleName, byteLoader, methods, fields, superName, interfaces, null);
    }

    public ClassIndexEntry(String fullPath, String internalName, String simpleName,
                           FileTreeNode.ByteLoader byteLoader,
                           List<MemberIndexEntry> methods,
                           List<MemberIndexEntry> fields,
                           String superName,
                           List<String> interfaces,
                           FileTreeNode node) {
        this.fullPath = Objects.requireNonNull(fullPath, "fullPath");
        this.internalName = Objects.requireNonNull(internalName, "internalName");
        this.simpleName = Objects.requireNonNull(simpleName, "simpleName");
        this.byteLoader = byteLoader;
        this.methods = List.copyOf(methods == null ? List.of() : methods);
        this.fields = List.copyOf(fields == null ? List.of() : fields);
        this.superName = superName;
        this.interfaces = List.copyOf(interfaces == null ? List.of() : interfaces);
        this.node = node;
    }

    // ... 保留现有方法 ...

    /** @return 构造索引时对应的文件树节点，可能为 null */
    public FileTreeNode node() {
        return node;
    }
}
```

- [ ] **Step 2: 修改 WorkspaceIndex.indexClass 传入 node**

在 `WorkspaceIndex.indexClass` 中，构造 `ClassIndexEntry` 时增加 `node` 参数：

```java
return new ClassIndexEntry(node.getFullPath(), internalName, simpleName,
        node::resolveBytes, methods, fields, superName, interfaces, node);
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/model/ClassIndexEntry.java \
        src/main/java/com/bingbaihanji/fxdecomplie/model/WorkspaceIndex.java \
        src/main/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapter.java
git commit -m "feat(service/reference): add ClassGraphWorkspaceAdapter and ClassIndexEntry node ref"
```

---

## Task 8: InheritanceReferenceIndex

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java`

- [ ] **Step 1: 实现索引**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.ScanResult;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InheritanceReferenceIndex {

    private final com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult scanResult;
    private final Map<String, String> internalNameToFullPath;
    private final Map<String, List<String>> interfaceToImplementations;
    private final Map<String, List<String>> superclassToSubclasses;
    private final Map<String, List<String>> annotationToAnnotatedClasses;

    public InheritanceReferenceIndex(ScanResult scanResult,
                                     Map<String, String> internalNameToFullPath) {
        this.scanResult = scanResult != null ? scanResult : new ScanResult(Map.of());
        this.internalNameToFullPath = internalNameToFullPath != null
                ? Map.copyOf(internalNameToFullPath) : Map.of();

        Map<String, List<String>> implMap = new LinkedHashMap<>();
        Map<String, List<String>> subMap = new LinkedHashMap<>();
        Map<String, List<String>> annMap = new LinkedHashMap<>();

        for (ClassInfo ci : this.scanResult.getAllClasses()) {
            String name = ci.getName();
            for (ClassInfo itf : ci.getInterfaces()) {
                implMap.computeIfAbsent(itf.getName(), k -> new ArrayList<>()).add(name);
            }
            com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo sup = ci.getSuperclass();
            if (sup != null) {
                subMap.computeIfAbsent(sup.getName(), k -> new ArrayList<>()).add(name);
            }
            for (var ai : ci.getAnnotationInfo()) {
                annMap.computeIfAbsent(ai.getName(), k -> new ArrayList<>()).add(name);
            }
        }

        this.interfaceToImplementations = freeze(implMap);
        this.superclassToSubclasses = freeze(subMap);
        this.annotationToAnnotatedClasses = freeze(annMap);
    }

    private static Map<String, List<String>> freeze(Map<String, List<String>> map) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    public ClassInfo classInfo(String internalName) {
        return scanResult.getClassInfo(internalName);
    }

    public List<String> implementationsOf(String interfaceName) {
        return interfaceToImplementations.getOrDefault(interfaceName, List.of());
    }

    public List<String> subclassesOf(String superclassName) {
        return superclassToSubclasses.getOrDefault(superclassName, List.of());
    }

    public List<String> annotatedBy(String annotationName) {
        return annotationToAnnotatedClasses.getOrDefault(annotationName, List.of());
    }

    public String fullPathOf(String internalName) {
        return internalNameToFullPath.get(internalName);
    }

    public boolean containsClass(String internalName) {
        return scanResult.getClassInfo(internalName) != null;
    }

    public ScanResult scanResult() {
        return scanResult;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndex.java
git commit -m "feat(service/reference): add InheritanceReferenceIndex"
```

---

## Task 9: InheritanceReferenceIndexService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java`

- [ ] **Step 1: 实现异步索引服务**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public final class InheritanceReferenceIndexService {

    private static final Logger log = LoggerFactory.getLogger(InheritanceReferenceIndexService.class);
    private static final WeakHashMap<Workspace, IndexState> STATES = new WeakHashMap<>();

    private InheritanceReferenceIndexService() {
        throw new AssertionError("utility class");
    }

    public static synchronized InheritanceReferenceIndex getOrStart(Workspace workspace) {
        IndexState state = STATES.computeIfAbsent(workspace, w -> new IndexState());
        if (!state.started) {
            state.started = true;
            log.info("开始构建继承引用索引: {}", workspace.getName());
            long start = System.currentTimeMillis();
            Future<?> task = BackgroundTasks.run(BackgroundTasks.PoolType.IO,
                    "RefIndex-" + workspace.getName(), () -> {
                        try {
                            ScanResult scanResult = ClassGraphWorkspaceAdapter.scan(workspace);
                            InheritanceReferenceIndex index = new InheritanceReferenceIndex(
                                    scanResult, buildPathMap(scanResult));
                            long elapsed = System.currentTimeMillis() - start;
                            log.info("继承引用索引构建完成: {} ({} classes, {}ms)",
                                    workspace.getName(),
                                    scanResult.getAllClasses().size(), elapsed);
                            state.future.complete(index);
                        } catch (Exception ex) {
                            long elapsed = System.currentTimeMillis() - start;
                            log.error("继承引用索引构建失败: {} ({}ms)",
                                    workspace.getName(), elapsed, ex);
                            state.future.completeExceptionally(ex);
                        }
                    });
            state.task = task;
        }
        return state.future.isDone() && !state.future.isCompletedExceptionally()
                ? state.future.getNow(null) : null;
    }

    private static Map<String, String> buildPathMap(ScanResult scanResult) {
        java.util.LinkedHashMap<String, String> map = new java.util.LinkedHashMap<>();
        for (var ci : scanResult.getAllClasses()) {
            if (ci.getFullPath() != null) {
                map.put(ci.getName(), ci.getFullPath());
            }
        }
        return map;
    }

    public static synchronized InheritanceReferenceIndex getIfReady(Workspace workspace) {
        IndexState state = STATES.get(workspace);
        if (state == null) {
            return null;
        }
        return state.future.isDone() && !state.future.isCompletedExceptionally()
                ? state.future.getNow(null) : null;
    }

    public static synchronized CompletableFuture<InheritanceReferenceIndex> getFuture(Workspace workspace) {
        IndexState state = STATES.get(workspace);
        return state == null ? null : state.future;
    }

    public static synchronized void invalidate(Workspace workspace) {
        IndexState state = STATES.remove(workspace);
        if (state != null && state.task != null) {
            state.task.cancel(true);
        }
    }

    private static final class IndexState {
        volatile boolean started;
        volatile Future<?> task;
        final CompletableFuture<InheritanceReferenceIndex> future = new CompletableFuture<>();
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexService.java
git commit -m "feat(service/reference): add async inheritance reference index service"
```

---

## Task 10: InheritanceReferenceService

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java`

- [ ] **Step 1: 实现业务入口**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.bytecode.ClassFileMetadata;
import com.bingbaihanji.fxdecomplie.bytecode.ClassFileParser;
import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.fxdecomplie.util.jvm.ClassNameUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InheritanceReferenceService {

    private static final int MAX_OVERFLOW = 200;

    private InheritanceReferenceService() {
        throw new AssertionError("utility class");
    }

    public static InheritanceReferenceTree buildTree(Workspace workspace, String fullPath,
                                                     byte[] currentClassBytes) {
        if (fullPath == null || fullPath.isBlank()) {
            return emptyTree(I18nUtil.getString("inheritance.status.failed"));
        }
        String internalName = ClassNameUtil.normalizeInternalName(fullPath);
        byte[] bytes = currentClassBytes != null && currentClassBytes.length > 0
                ? currentClassBytes
                : readCurrentBytes(workspace, internalName);

        Optional<ClassFileMetadata> localMeta = bytes != null
                ? ClassFileParser.tryParse(bytes) : Optional.empty();

        if (localMeta.isEmpty()) {
            return emptyTree(I18nUtil.getString("inheritance.unavailable"));
        }

        ClassFileMetadata meta = localMeta.get();
        String displayName = simpleName(meta.internalName());
        InheritanceReferenceNode root = new InheritanceReferenceNode(
                meta.internalName(), displayName, Kind.SELF,
                resolveFullPath(workspace, meta.internalName()), 0, true);

        List<InheritanceReferenceGroup> groups = new ArrayList<>();

        // Super class
        if (meta.superName() != null && !"java/lang/Object".equals(meta.superName())) {
            groups.add(singleGroup(Kind.SUPER_CLASS,
                    I18nUtil.getString("inheritance.group.super"),
                    List.of(nodeFor(workspace, meta.superName(), Kind.SUPER_CLASS, 1)),
                    false));
        }

        // Interfaces
        List<InheritanceReferenceNode> interfaceNodes = new ArrayList<>();
        for (String itf : meta.interfaces()) {
            interfaceNodes.add(nodeFor(workspace, itf, Kind.INTERFACE, 1));
        }
        if (!interfaceNodes.isEmpty()) {
            groups.add(singleGroup(Kind.INTERFACE,
                    I18nUtil.getString("inheritance.group.interfaces"),
                    interfaceNodes, false));
        }

        // Annotations (need ClassFileParser enhancement or ASM parse)
        List<InheritanceReferenceNode> annotationNodes = parseAnnotations(bytes);
        if (!annotationNodes.isEmpty()) {
            groups.add(singleGroup(Kind.ANNOTATION,
                    I18nUtil.getString("inheritance.group.annotations"),
                    annotationNodes, false));
        }

        // Global relations
        InheritanceReferenceIndex index = InheritanceReferenceIndexService.getIfReady(workspace);
        boolean partial = false;
        String statusMessage = I18nUtil.getString("inheritance.status.ready");
        if (index == null) {
            partial = true;
            statusMessage = I18nUtil.getString("inheritance.status.building");
            InheritanceReferenceIndexService.getOrStart(workspace);
        } else {
            addImplementationGroup(workspace, index, meta.internalName(), groups);
            addSubclassGroup(workspace, index, meta.internalName(), groups);
        }

        return new InheritanceReferenceTree(root, groups, partial, statusMessage);
    }

    private static List<InheritanceReferenceNode> parseAnnotations(byte[] bytes) {
        List<InheritanceReferenceNode> result = new ArrayList<>();
        if (bytes == null) {
            return result;
        }
        try {
            org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(bytes);
            org.objectweb.asm.ClassVisitor visitor = new org.objectweb.asm.ClassVisitor(org.objectweb.asm.Opcodes.ASM9) {
                @Override
                public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                    String internal = org.objectweb.asm.Type.getType(descriptor).getInternalName();
                    result.add(new InheritanceReferenceNode(internal, simpleName(internal),
                            Kind.ANNOTATION, null, 1, false));
                    return null;
                }
            };
            reader.accept(visitor, org.objectweb.asm.ClassReader.SKIP_CODE
                    | org.objectweb.asm.ClassReader.SKIP_DEBUG
                    | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        } catch (Exception ignored) {
        }
        return result;
    }

    private static void addImplementationGroup(Workspace workspace, InheritanceReferenceIndex index,
                                               String internalName, List<InheritanceReferenceGroup> groups) {
        com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo ci = index.classInfo(internalName);
        if (ci == null || !ci.isInterface()) {
            return;
        }
        List<String> impls = index.implementationsOf(internalName);
        if (impls.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.IMPLEMENTATION,
                I18nUtil.getString("inheritance.group.implementations"),
                impls, 1, true));
    }

    private static void addSubclassGroup(Workspace workspace, InheritanceReferenceIndex index,
                                         String internalName, List<InheritanceReferenceGroup> groups) {
        com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo ci = index.classInfo(internalName);
        if (ci != null && ci.isInterface()) {
            return; // subclasses shown via implementations for interfaces
        }
        List<String> subs = index.subclassesOf(internalName);
        if (subs.isEmpty()) {
            return;
        }
        groups.add(buildOverflowGroup(workspace, Kind.SUBCLASS,
                I18nUtil.getString("inheritance.group.subclasses"),
                subs, 1, true));
    }

    private static InheritanceReferenceGroup buildOverflowGroup(Workspace workspace, Kind kind,
                                                                String title, List<String> names,
                                                                int depth, boolean collapsible) {
        List<InheritanceReferenceNode> children = new ArrayList<>();
        int limit = Math.min(names.size(), MAX_OVERFLOW);
        for (int i = 0; i < limit; i++) {
            children.add(nodeFor(workspace, names.get(i), kind, depth));
        }
        if (names.size() > MAX_OVERFLOW) {
            int overflow = names.size() - MAX_OVERFLOW;
            children.add(new InheritanceReferenceNode("",
                    I18nUtil.getString("inheritance.group.overflow", overflow),
                    Kind.UNRESOLVED, "", depth + 1, false));
        }
        return singleGroup(kind, title, children, collapsible);
    }

    private static InheritanceReferenceGroup singleGroup(Kind kind, String title,
                                                         List<InheritanceReferenceNode> children,
                                                         boolean collapsible) {
        return new InheritanceReferenceGroup(kind, title, children, collapsible);
    }

    private static InheritanceReferenceNode nodeFor(Workspace workspace, String internalName,
                                                    Kind kind, int depth) {
        String fullPath = resolveFullPath(workspace, internalName);
        return new InheritanceReferenceNode(internalName, simpleName(internalName), kind,
                fullPath, depth, fullPath != null && !fullPath.isBlank());
    }

    private static String resolveFullPath(Workspace workspace, String internalName) {
        if (workspace == null) {
            return null;
        }
        InheritanceReferenceIndex index = InheritanceReferenceIndexService.getIfReady(workspace);
        if (index != null) {
            String fp = index.fullPathOf(internalName);
            if (fp != null && !fp.isBlank()) {
                return fp;
            }
        }
        WorkspaceIndex wsIndex = workspace.getIndex();
        if (wsIndex != null && wsIndex != WorkspaceIndex.EMPTY) {
            var entry = wsIndex.findClass(internalName);
            if (entry != null) {
                return entry.fullPath();
            }
        }
        return null;
    }

    private static byte[] readCurrentBytes(Workspace workspace, String internalName) {
        if (workspace == null) {
            return null;
        }
        WorkspaceIndex index = workspace.getIndex();
        if (index != null && index != WorkspaceIndex.EMPTY) {
            return index.getClassBytes(internalName);
        }
        return null;
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private static InheritanceReferenceTree emptyTree(String message) {
        InheritanceReferenceNode root = new InheritanceReferenceNode("", "", Kind.SELF, "", 0, false);
        return new InheritanceReferenceTree(root, List.of(), false, message);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceService.java
git commit -m "feat(service/reference): add InheritanceReferenceService"
```

---

## Task 11: 语言资源

**Files:**
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: 更新中文资源**

在文件合适位置添加或替换以下键：

```properties
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

并保留旧键 `inheritance.indexing`、`inheritance.indexPending`、`inheritance.unavailable` 供兼容或统一改为新键。

- [ ] **Step 2: 更新英文资源**

```properties
tab.inheritance=References
inheritance.title=Inheritance References
inheritance.status.partial=Partial results
inheritance.status.building=Building references...
inheritance.status.ready=Index ready
inheritance.status.failed=Failed, click refresh to retry
inheritance.group.self=Current class
inheritance.group.super=Super class
inheritance.group.interfaces=Interfaces
inheritance.group.annotations=Annotations
inheritance.group.implementations=Implementations
inheritance.group.subclasses=Subclasses
inheritance.group.overflow={0} more results, use search to see all
inheritance.refresh.tooltip=Refresh inheritance reference index
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/language/language_zh_CN.properties \
        src/main/resources/language/language_en.properties
git commit -m "i18n: add inheritance reference labels"
```

---

## Task 12: InheritanceReferencePane

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceReferencePane.java`

- [ ] **Step 1: 实现新面板**

```java
package com.bingbaihanji.fxdecomplie.ui.inheritance;

import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceGroup;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import com.bingbaihanji.fxdecomplie.service.BackgroundTasks;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceIndexService;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceService;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.concurrent.atomic.AtomicLong;

public final class InheritanceReferencePane extends VBox {

    private final TreeView<InheritanceReferenceNode> treeView;
    private final Label statusLabel;
    private final Button refreshButton;
    private final AtomicLong loadGeneration = new AtomicLong();
    private OpenHandler openHandler;
    private Runnable refreshAction;

    public InheritanceReferencePane() {
        setPadding(new Insets(4));
        setSpacing(4);
        setStyle("-fx-background-color: #252526;");

        HBox titleBar = new HBox(8);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(I18nUtil.getString("inheritance.title"));
        title.setStyle("-fx-text-fill: #cccccc; -fx-font-weight: bold; -fx-padding: 2px 4px;");

        refreshButton = new Button("↻");
        refreshButton.setTooltip(new javafx.scene.control.Tooltip(
                I18nUtil.getString("inheritance.refresh.tooltip")));
        refreshButton.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc;");
        refreshButton.setOnAction(e -> {
            if (refreshAction != null) {
                refreshAction.run();
            }
        });

        HBox.setHgrow(title, Priority.ALWAYS);
        titleBar.getChildren().addAll(title, refreshButton);

        treeView = new TreeView<>();
        treeView.setStyle("-fx-background-color: #252526;");
        VBox.setVgrow(treeView, Priority.ALWAYS);

        treeView.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(InheritanceReferenceNode item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                String prefix = switch (item.kind()) {
                    case SELF -> "";
                    case SUPER_CLASS -> "S ";
                    case INTERFACE -> "I ";
                    case ANNOTATION -> "T ";
                    case IMPLEMENTATION, SUBCLASS -> "↓ ";
                    case UNRESOLVED -> "";
                };
                setText(prefix + item.displayName());
                Color c = switch (item.kind()) {
                    case SELF -> Color.web("#dcdcaa");
                    case SUPER_CLASS -> Color.web("#c586c0");
                    case INTERFACE -> Color.web("#4ec9b0");
                    case ANNOTATION -> Color.web("#4fc1ff");
                    case IMPLEMENTATION, SUBCLASS -> Color.web("#9cdcfe");
                    case UNRESOLVED -> Color.web("#808080");
                };
                setTextFill(c);
                setStyle("-fx-background-color: transparent; -fx-font-family: 'Consolas', monospace;");
            }
        });

        treeView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && openHandler != null) {
                TreeItem<InheritanceReferenceNode> item = treeView.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue() != null
                        && item.getValue().kind() != Kind.SELF
                        && item.getValue().navigable()) {
                    openHandler.open(item.getValue());
                }
            }
        });

        statusLabel = new Label();
        statusLabel.setStyle("-fx-text-fill: #808080; -fx-font-size: 11px; -fx-padding: 2px 4px;");

        getChildren().addAll(titleBar, treeView, statusLabel);
    }

    public void load(com.bingbaihanji.fxdecomplie.model.Workspace workspace,
                     String fullPath, byte[] classBytes) {
        showStatus(I18nUtil.getString("inheritance.status.building"));
        long gen = loadGeneration.incrementAndGet();
        BackgroundTasks.run("RefViewBuild", () -> {
            InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                    workspace, fullPath, classBytes);
            Platform.runLater(() -> {
                if (loadGeneration.get() != gen) {
                    return;
                }
                render(tree);
            });
        });
    }

    private void render(InheritanceReferenceTree tree) {
        TreeItem<InheritanceReferenceNode> root = new TreeItem<>(tree.root());
        root.setExpanded(true);
        for (InheritanceReferenceGroup group : tree.groups()) {
            TreeItem<InheritanceReferenceNode> groupItem = new TreeItem<>(
                    new InheritanceReferenceNode("", group.title(), group.kind(), "",
                            tree.root().depth() + 1, false));
            for (InheritanceReferenceNode child : group.children()) {
                groupItem.getChildren().add(new TreeItem<>(child));
            }
            groupItem.setExpanded(!group.collapsible());
            root.getChildren().add(groupItem);
        }
        treeView.setRoot(root);
        showStatus(tree.statusMessage());
    }

    public void clear() {
        treeView.setRoot(null);
        statusLabel.setText("");
    }

    public void showStatus(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    public void setOpenHandler(OpenHandler handler) {
        this.openHandler = handler;
    }

    public void setRefreshAction(Runnable action) {
        this.refreshAction = action;
    }

    @FunctionalInterface
    public interface OpenHandler {
        void open(InheritanceReferenceNode node);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/inheritance/InheritanceReferencePane.java
git commit -m "feat(ui/inheritance): add InheritanceReferencePane"
```

---

## Task 13: WorkspaceTabManager 替换 InheritancePane

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java`

- [ ] **Step 1: 替换导入与字段**

将 `import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritancePane;` 替换为：

```java
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceNode;
import com.bingbaihanji.fxdecomplie.service.reference.InheritanceReferenceIndexService;
import com.bingbaihanji.fxdecomplie.ui.inheritance.InheritanceReferencePane;
```

- [ ] **Step 2: 修改 addWorkspaceTab 中面板创建**

将：

```java
InheritancePane inheritancePane = new InheritancePane();
```

改为：

```java
InheritanceReferencePane inheritancePane = new InheritanceReferencePane();
```

- [ ] **Step 3: 修改 refreshInheritancePane**

替换现有 `refreshInheritancePane` 方法为：

```java
private static void refreshInheritancePane(Workspace workspace, TabPane codeTabPane,
                                           InheritanceReferencePane inheritancePane,
                                           CodeEditorTab codeTab,
                                           boolean startIndexIfNeeded) {
    if (workspace == null || inheritancePane == null || codeTab == null
            || codeTab.getOpenFile() == null) {
        return;
    }
    String selectedPath = codeTab.getOpenFile().fullPath();
    byte[] classBytes = codeTab.getClassBytes();
    inheritancePane.load(workspace, selectedPath, classBytes);
    if (!workspace.isIndexReady() && startIndexIfNeeded) {
        WorkspaceIndexService.ensureIndexingStarted(workspace);
    }
    if (startIndexIfNeeded) {
        InheritanceReferenceIndexService.getOrStart(workspace);
        var future = InheritanceReferenceIndexService.getFuture(workspace);
        if (future != null) {
            future.whenComplete((index, error) -> Platform.runLater(() -> {
                if (error != null) {
                    return;
                }
                Tab selected = codeTabPane.getSelectionModel().getSelectedItem();
                if (selected instanceof CodeEditorTab selectedCodeTab
                        && selectedPath.equals(selectedCodeTab.getOpenFile().fullPath())) {
                    inheritancePane.load(workspace, selectedPath, selectedCodeTab.getClassBytes());
                }
            }));
        }
    }
}
```

- [ ] **Step 4: 修改 refreshToolWindowsForTab 签名**

将 `refreshToolWindowsForTab` 与 `refreshInheritanceForCurrentSelection` 中的 `InheritancePane` 类型参数改为 `InheritanceReferencePane`。

- [ ] **Step 5: 修改打开处理器**

将原来的 `inheritancePane.setOpenHandler(className -> { ... })` 替换为：

```java
inheritancePane.setOpenHandler(node -> {
    FileTreeNode target = workspace.findNodeByPath(node.fullPath());
    if (target == null && workspace.getIndex() != null) {
        var entry = workspace.getIndex().findClass(node.className());
        if (entry != null) {
            target = workspace.findNodeByPath(entry.fullPath());
        }
    }
    if (target == null) {
        target = findClassNode(treeView.getRoot(), node.className());
    }
    if (target != null) {
        onClassClick.accept(target, codeTabPane);
    } else {
        statusBar.setFilePath(I18nUtil.getString("status.locateFailed", node.className()));
    }
});
```

- [ ] **Step 6: 修改 refreshAction**

在绑定打开处理器后添加：

```java
inheritancePane.setRefreshAction(() -> {
    InheritanceReferenceIndexService.invalidate(workspace);
    refreshInheritanceForCurrentSelection(workspace, codeTabPane, inheritancePane, true);
});
```

- [ ] **Step 7: 编译验证**

Run: `mvn -DskipTests compile`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/WorkspaceTabManager.java
git commit -m "feat(ui): wire InheritanceReferencePane into WorkspaceTabManager"
```

---

## Task 14: 测试辅助 TestClassCompiler + WorkspaceBuilder

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/TestClassCompiler.java`
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapterTestHelper.java`

- [ ] **Step 1: 实现编译辅助**

参考 `InheritanceServiceTest` 的运行时编译逻辑，生成类文件字节并可选补丁 major version 到 69。

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class TestClassCompiler {

    private TestClassCompiler() {}

    public static Map<String, byte[]> compile(Map<String, String> sources) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        InMemoryFileManager fileManager = new InMemoryFileManager(
                compiler.getStandardFileManager(diagnostics, null, null));

        Iterable<? extends JavaFileObject> units = sources.entrySet().stream()
                .map(e -> new SourceJavaFileObject(e.getKey(), e.getValue()))
                .toList();

        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics,
                null, null, units);
        boolean ok = task.call();
        if (!ok) {
            throw new RuntimeException("编译失败: " + diagnostics.getDiagnostics());
        }
        Map<String, byte[]> result = fileManager.getOutputs();
        result.replaceAll((k, v) -> patchMajorVersion(v, 69));
        return result;
    }

    public static byte[] patchMajorVersion(byte[] bytes, int major) {
        if (bytes == null || bytes.length < 8) {
            return bytes;
        }
        byte[] copy = bytes.clone();
        copy[6] = (byte) ((major >> 8) & 0xFF);
        copy[7] = (byte) (major & 0xFF);
        return copy;
    }

    public static byte[] buildJar(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                zos.putNextEntry(entry);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static final class SourceJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        SourceJavaFileObject(String name, String source) {
            super(URI.create("string:///" + name), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }

    private static final class InMemoryFileManager extends javax.tools.ForwardingJavaFileManager<StandardJavaFileManager> {
        private final Map<String, byte[]> outputs = new LinkedHashMap<>();

        InMemoryFileManager(StandardJavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                    JavaFileObject.Kind kind, javax.tools.FileObject sibling) {
            return new ClassJavaFileObject(className, outputs);
        }

        Map<String, byte[]> getOutputs() {
            return outputs;
        }
    }

    private static final class ClassJavaFileObject extends SimpleJavaFileObject {
        private final String className;
        private final Map<String, byte[]> outputs;

        ClassJavaFileObject(String className, Map<String, byte[]> outputs) {
            super(URI.create("mem:///" + className.replace('.', '/') + ".class"), Kind.CLASS);
            this.className = className;
            this.outputs = outputs;
        }

        @Override
        public OutputStream openOutputStream() {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    super.close();
                    outputs.put(className.replace('.', '/') + ".class", toByteArray());
                }
            };
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(outputs.getOrDefault(className, new byte[0]));
        }
    }
}
```

- [ ] **Step 2: 实现测试 Workspace 构建辅助**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;

final class ClassGraphWorkspaceAdapterTestHelper {
    private ClassGraphWorkspaceAdapterTestHelper() {}

    static Workspace buildWorkspace(File dir, Map<String, String> sources) throws Exception {
        Map<String, byte[]> compiled = TestClassCompiler.compile(sources);
        for (Map.Entry<String, byte[]> e : compiled.entrySet()) {
            File f = new File(dir, e.getKey());
            f.getParentFile().mkdirs();
            Files.write(f.toPath(), e.getValue());
        }

        FileTreeNode rootNode = new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE);
        FileTreeModel root = new FileTreeModel(rootNode);
        for (Map.Entry<String, byte[]> e : compiled.entrySet()) {
            FileTreeNode node = new FileTreeNode(
                    e.getKey().substring(e.getKey().lastIndexOf('/') + 1),
                    e.getKey(), FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setByteLoader(() -> e.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }

        Workspace workspace = new Workspace("test", dir, root, false);
        workspace.setIndex(WorkspaceIndex.build(root));
        return workspace;
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -DskipTests test-compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/reference/TestClassCompiler.java \
        src/test/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapterTestHelper.java
git commit -m "test(reference): add test compiler and workspace builder helper"
```

---

## Task 15: ClassGraphWorkspaceAdapterTest

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapterTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.core.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.core.ScanResult;
import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClassGraphWorkspaceAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void scansInterfaceAndImplementation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; public class FileServiceImpl implements FileService {}");

        Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
        ScanResult result = ClassGraphWorkspaceAdapter.scan(workspace);

        ClassInfo fileService = result.getClassInfo("com/example/FileService");
        assertNotNull(fileService);
        assertTrue(fileService.isInterface());

        ClassInfo impl = result.getClassInfo("com/example/FileServiceImpl");
        assertNotNull(impl);
        assertFalse(impl.isInterface());
        assertEquals(fileService, impl.getInterfaces().get(0));
        assertEquals(1, fileService.getImplementingClasses().size());
        assertEquals(impl, fileService.getImplementingClasses().get(0));
    }

    @Test
    void scansAnnotation() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/Service.java",
                "package com.example; import java.lang.annotation.*; " +
                        "@Retention(RetentionPolicy.RUNTIME) @interface Service {}");
        sources.put("com/example/UserService.java",
                "package com.example; @Service public class UserService {}");

        Workspace workspace = ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
        ScanResult result = ClassGraphWorkspaceAdapter.scan(workspace);

        ClassInfo ann = result.getClassInfo("com/example/Service");
        assertNotNull(ann);
        assertTrue(ann.isAnnotation());

        ClassInfo userService = result.getClassInfo("com/example/UserService");
        assertNotNull(userService);
        assertEquals(1, userService.getAnnotationInfo().size());
        assertEquals("com/example/Service", userService.getAnnotationInfo().get(0).getName());
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -Dtest=ClassGraphWorkspaceAdapterTest`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/reference/ClassGraphWorkspaceAdapterTest.java
git commit -m "test(reference): add ClassGraphWorkspaceAdapterTest"
```

---

## Task 16: InheritanceReferenceIndexTest

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceReferenceIndexTest {

    @TempDir
    Path tempDir;

    @Test
    void indexProvidesImplementations() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertTrue(index.implementationsOf("com/example/FileService").contains("com/example/FileServiceImpl"));
    }

    @Test
    void indexProvidesAnnotatedClasses() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertTrue(index.annotatedBy("com/example/Service").contains("com/example/UserService"));
    }

    @Test
    void fullPathMappingWorks() throws Exception {
        Workspace workspace = sampleWorkspace();
        InheritanceReferenceIndex index = buildIndex(workspace);
        assertNotNull(index.fullPathOf("com/example/FileService"));
        assertTrue(index.fullPathOf("com/example/FileService").endsWith("FileService.class"));
    }

    private Workspace sampleWorkspace() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/Service.java",
                "package com.example; import java.lang.annotation.*; " +
                "@Retention(RetentionPolicy.RUNTIME) @interface Service {}");
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; @Service public class FileServiceImpl implements FileService {}");
        sources.put("com/example/UserService.java",
                "package com.example; @Service public class UserService {}");
        return ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
    }

    private InheritanceReferenceIndex buildIndex(Workspace workspace) {
        return new InheritanceReferenceIndex(
                ClassGraphWorkspaceAdapter.scan(workspace), Map.of());
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -Dtest=InheritanceReferenceIndexTest`
Expected: 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceIndexTest.java
git commit -m "test(reference): add InheritanceReferenceIndexTest"
```

---

## Task 17: InheritanceReferenceServiceTest

**Files:**
- Create: `src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceServiceTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.model.Workspace;
import com.bingbaihanji.fxdecomplie.model.reference.InheritanceReferenceTree;
import com.bingbaihanji.fxdecomplie.model.reference.Kind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InheritanceReferenceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void partialResultWithoutIndex() throws Exception {
        Workspace workspace = sampleWorkspace();
        byte[] implBytes = readBytes(workspace, "com/example/FileServiceImpl.class");
        InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                workspace, "com/example/FileServiceImpl.class", implBytes);
        assertEquals("FileServiceImpl", tree.root().displayName());
        assertTrue(tree.partial() || tree.statusMessage().contains("正在") || tree.statusMessage().contains("Building"));
        assertTrue(hasKind(tree, Kind.INTERFACE));
    }

    @Test
    void fullResultWithIndex() throws Exception {
        Workspace workspace = sampleWorkspace();
        workspace.setIndex(com.bingbaihanji.fxdecomplie.model.WorkspaceIndex.build(workspace.getTreeRoot()));
        InheritanceReferenceIndexService.getOrStart(workspace);
        var future = InheritanceReferenceIndexService.getFuture(workspace);
        if (future != null) {
            future.join();
        }
        byte[] bytes = readBytes(workspace, "com/example/FileService.class");
        InheritanceReferenceTree tree = InheritanceReferenceService.buildTree(
                workspace, "com/example/FileService.class", bytes);
        assertTrue(hasKind(tree, Kind.IMPLEMENTATION));
    }

    private Workspace sampleWorkspace() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com/example/FileService.java",
                "package com.example; public interface FileService {}");
        sources.put("com/example/FileServiceImpl.java",
                "package com.example; public class FileServiceImpl implements FileService {}");
        return ClassGraphWorkspaceAdapterTestHelper.buildWorkspace(tempDir.toFile(), sources);
    }

    private byte[] readBytes(Workspace workspace, String fullPath) throws Exception {
        var entry = workspace.getIndex().findClass(fullPath);
        return entry != null ? entry.bytes() : null;
    }

    private boolean hasKind(InheritanceReferenceTree tree, Kind kind) {
        for (var g : tree.groups()) {
            if (g.kind() == kind && !g.children().isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 2: 运行测试**

Run: `mvn test -Dtest=InheritanceReferenceServiceTest`
Expected: 2 tests PASS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/com/bingbaihanji/fxdecomplie/service/reference/InheritanceReferenceServiceTest.java
git commit -m "test(reference): add InheritanceReferenceServiceTest"
```

---

## Task 18: 全量测试与打包

- [ ] **Step 1: 运行引用相关测试**

Run: `mvn test -Dtest=ClassGraphWorkspaceAdapterTest,InheritanceReferenceIndexTest,InheritanceReferenceServiceTest`
Expected: ALL PASS

- [ ] **Step 2: 运行全部测试**

Run: `mvn test`
Expected: ALL PASS (或仅已有失败保持原状)

- [ ] **Step 3: 打包验证**

Run: `mvn -DskipTests package`
Expected: BUILD SUCCESS，输出 `bin/fxdecomplie.jar`

- [ ] **Step 4: Commit 最终结果**

```bash
git add -f docs/superpowers/plans/2026-07-20-classgraph-inheritance-reference-plan.md
git commit -m "docs: add ClassGraph inheritance reference implementation plan"
```

---

## Self-Review

### Spec Coverage

| Spec 要求 | 对应 Task |
|---|---|
| `core/classgraph` 隔离 ClassGraph 代码 | Task 1–4 |
| `service/reference` 适配层与索引 | Task 6–9 |
| `model/reference` UI 模型 | Task 5 |
| 当前类/父类/接口/注解即时展示 | Task 10 |
| 接口实现类/子类后台补全 | Task 9–10 |
| 刷新按钮与缓存失效 | Task 9 (invalidate) + Task 13 (refreshAction) |
| 双击跳转 | Task 13 |
| 语言资源更新 | Task 11 |
| 测试覆盖 | Task 14–17 |
| License / 版权说明 | 已在设计文档提交信息中体现；实施无需额外文件 |

### Placeholder Scan

- 无 “TBD/TODO” 未填内容。
- 代码块包含完整可编译代码。
- `ClassIndexEntry.node()` 的修改已在 Task 7 明确。

### Type Consistency

- `ClassInfoList` / `AnnotationInfoList` 均继承 `ArrayList`，支持过滤。
- `InheritanceReferenceService.buildTree` 签名与 design doc 一致。
- `InheritanceReferencePane.OpenHandler` 接受 `InheritanceReferenceNode` 而非 `String`。

### 潜在gap

- `BytecodeText.toText` 在 `ClassIndexEntry.bytecodeText()` 中若不存在，需使用实际项目中的字节码文本方法；请根据现有 `ClassIndexEntry` 实际代码调整。
- `ClassNameUtil.normalizeInternalName` 行为需保证 `"com/example/Foo.class"` → `"com/example/Foo"`。
- `I18nUtil.getString(String, Object...)` 需支持参数化消息；若不支持，需改用 `String.format` 包装。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-20-classgraph-inheritance-reference-plan.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** - Execute tasks in this session using `superpowers:executing-plans`, batch execution with checkpoints.

**Which approach?**
