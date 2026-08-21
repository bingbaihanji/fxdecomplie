# UML 类图功能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 FxDecompiler 增加 IDEA 风格的 UML 类图视图：展示类的继承/实现关系、字段与方法成员，支持亮/暗主题与 PNG/.puml 导出。

**Architecture:** 纯 ASM 从字节码提取 UML 模型 → BFS 闭包构建 `UmlDiagram` → 纯 JavaFX 渲染分栏类框；PNG 走画布快照（javafx-swing `SwingFXUtils`），`.puml` 走文本导出器（移植参考项目 `GenerateUML` 逻辑）。无新增第三方依赖。

**Tech Stack:** Java 25、JavaFX 25、ASM 9.9、JUnit 5、Maven。

**Spec:** `docs/superpowers/specs/2026-08-21-uml-class-diagram-design.md`

---

## 文件结构

**新建（main）— `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/`：**

| 文件 | 职责 |
|---|---|
| `ClassKind.java` | 枚举：CLASS/INTERFACE/ENUM/ANNOTATION |
| `Visibility.java` | 枚举：PUBLIC/PROTECTED/PACKAGE/PRIVATE + 符号 |
| `RelationType.java` | 枚举：GENERALIZATION/REALIZATION/ASSOCIATION/DEPENDENCY |
| `UmlField.java` | record：字段成员 |
| `UmlMethod.java` | record：方法成员 |
| `UmlClass.java` | record：类节点（含 parsed 标记） |
| `UmlRelation.java` | record：有向关系（含多重性） |
| `UmlDiagram.java` | record：完整类图 |
| `UmlTypeNameUtil.java` | 类型名判定（移植 UMLHelper） |
| `UmlClassExtractor.java` | ASM 提取字节码 → UmlClass |
| `UmlDiagramBuilder.java` | BFS 闭包 → UmlDiagram |
| `UmlTheme.java` | 亮/暗色板 |
| `PumlExporter.java` | UmlDiagram → .puml 文本 |
| `UmlDiagramRenderer.java` | 纯 JavaFX 渲染 |
| `UmlClassDiagramDialog.java` | 弹窗壳 |

**新建（test）— `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/`：**

| 文件 | 职责 |
|---|---|
| `TestCompile.java` | 内存编译源码 → 字节映射 + FileTreeModel 构造 |
| `UmlTypeNameUtilTest.java` | 类型判定 |
| `UmlClassExtractorTest.java` | ASM 提取 |
| `UmlDiagramBuilderTest.java` | 闭包构建 |
| `PumlExporterTest.java` | PlantUML 导出 |

**修改：**

| 文件 | 改动 |
|---|---|
| `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeActionHandler.java` | 新增 `showUmlClassDiagram(CodeViewContext)` |
| `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeAreaContextMenu.java` | 新增「UML 类图」菜单项 |
| `src/main/java/com/bingbaihanji/fxdecomplie/controller/EngineController.java` | 新增 `showUmlClassDiagram` |
| `src/main/java/com/bingbaihanji/fxdecomplie/controller/MainWindow.java` | 实现 `showUmlClassDiagram` |
| `src/main/resources/language/language_zh_CN.properties` | 新增 i18n 键 |
| `src/main/resources/language/language_en.properties` | 新增 i18n 键 |
| `AGENTS.md` | 项目结构加 `ui/uml/` |

---

### Task 1: 模型类型（枚举 + records）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/ClassKind.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/Visibility.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/RelationType.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlField.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlMethod.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClass.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlRelation.java`
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagram.java`

- [ ] **Step 1: 创建 8 个模型文件**

纯数据声明，无业务逻辑，不写单测（测试重点在后续任务的算法）。

`ClassKind.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/** UML 图中的类型种类 */
public enum ClassKind {
    CLASS, INTERFACE, ENUM, ANNOTATION
}
```

`Visibility.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/** UML 可见性符号 */
public enum Visibility {
    PUBLIC('+'), PROTECTED('#'), PACKAGE('~'), PRIVATE('-');

    private final char symbol;

    Visibility(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }
}
```

`RelationType.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/** UML 关系类型 */
public enum RelationType {
    GENERALIZATION, REALIZATION, ASSOCIATION, DEPENDENCY
}
```

`UmlField.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/** 类的字段成员 */
public record UmlField(String name, String type, Visibility visibility, boolean isStatic) {
}
```

`UmlMethod.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/** 类的方法成员 */
public record UmlMethod(String name, List<String> params, String returnType,
                        Visibility visibility, boolean isStatic, boolean isAbstract,
                        boolean isConstructor) {
}
```

`UmlClass.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/**
 * 图中一个类型节点：类 / 接口 / 枚举 / 注解
 *
 * @param parsed 是否从字节码完整解析；false 表示「仅类名」标记框
 */
public record UmlClass(String internalName, String packageName, String simpleName,
                       ClassKind kind, boolean isAbstract, boolean parsed,
                       List<UmlField> fields, List<UmlMethod> methods,
                       String superName, List<String> interfaces,
                       List<String> annotationNames) {
}
```

`UmlRelation.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/** 两个类节点之间的有向关系 */
public record UmlRelation(String from, String to, RelationType type,
                          String fromCardinality, String toCardinality) {
}
```

`UmlDiagram.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/** 完整的 UML 类图 */
public record UmlDiagram(String target, List<UmlClass> classes, List<UmlRelation> relations) {
}
```

- [ ] **Step 2: 编译验证**

Run: `.\mvnw.cmd clean compile -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add -f src/main/java/com/bingbaihanji/fxdecomplie/ui/uml
git commit -m "feat(uml): UML 类图模型类型（ClassKind/Visibility/RelationType/records）"
```

> 注：`src/main` 不在 `.gitignore`，无需 `-f`；此处 `-f` 无害。后续提交不需要 `-f`。

---

### Task 2: UmlTypeNameUtil（TDD）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTypeNameUtil.java`
- Test: `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTypeNameUtilTest.java`

- [ ] **Step 1: 写失败测试**

`UmlTypeNameUtilTest.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UmlTypeNameUtilTest {

    @Test
    void getElementType_extractsArrayAndGenericElement() {
        assertEquals("Order", UmlTypeNameUtil.getElementType("Order[]"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("java.util.List<Order>"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("java.util.Map<String, List<Order>>"));
        assertEquals("X", UmlTypeNameUtil.getElementType("java.util.List<? extends X>"));
        assertEquals("Y", UmlTypeNameUtil.getElementType("java.util.List<? super Y>"));
        assertEquals("Order", UmlTypeNameUtil.getElementType("Order"));
    }

    @Test
    void isCollectionOrArray_detectsArrayAndGenerics() {
        assertTrue(UmlTypeNameUtil.isCollectionOrArray("Order[]"));
        assertTrue(UmlTypeNameUtil.isCollectionOrArray("java.util.List<Order>"));
        assertFalse(UmlTypeNameUtil.isCollectionOrArray("Order"));
    }

    @Test
    void isUMLClassType_filtersJdkAndCoreTypes() {
        assertTrue(UmlTypeNameUtil.isUMLClassType("com.example.Order"));
        assertTrue(UmlTypeNameUtil.isUMLClassType("java.util.List<com.example.Order>"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("String"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("java.util.List"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("java.lang.String"));
        assertFalse(UmlTypeNameUtil.isUMLClassType("int"));
    }

    @Test
    void isJdk_detectsInternalAndDottedJdkNames() {
        assertTrue(UmlTypeNameUtil.isJdk("java/lang/Object"));
        assertTrue(UmlTypeNameUtil.isJdk("java.util.ArrayList"));
        assertTrue(UmlTypeNameUtil.isJdk("javax.swing.JPanel"));
        assertTrue(UmlTypeNameUtil.isJdk("com/sun/Proxy"));
        assertFalse(UmlTypeNameUtil.isJdk("com/example/Order"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\mvnw.cmd test -Dtest="UmlTypeNameUtilTest"`
Expected: FAIL — 编译错误（类不存在）

- [ ] **Step 3: 实现**

`UmlTypeNameUtil.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 类型名判定工具（移植自 class-diagram-gen 的 {@code UMLHelper}）。
 * 输入为点分格式的可读类型名，例如 {@code java.util.List<com.example.Order>}。
 */
public final class UmlTypeNameUtil {

    private static final Set<String> JAVA_CORE = new HashSet<>(Arrays.asList(
            // 基本类型
            "boolean", "byte", "char", "short", "int", "long", "float", "double", "void",
            // java.lang
            "Object", "String", "Integer", "Long", "Double", "Float", "Short", "Byte",
            "Boolean", "Character", "Void", "Number", "Math", "StringBuilder", "StringBuffer",
            "Class", "Enum", "Comparable", "Iterable", "Runnable", "CharSequence", "Serializable",
            "Throwable", "Exception", "RuntimeException", "Error",
            // java.math
            "BigDecimal", "BigInteger",
            // java.time
            "Date", "LocalDate", "LocalDateTime", "LocalTime", "OffsetDateTime", "ZonedDateTime",
            "Duration", "Instant",
            // java.util / java.util.stream
            "List", "ArrayList", "LinkedList", "Set", "HashSet", "LinkedHashSet", "TreeSet",
            "Map", "HashMap", "LinkedHashMap", "TreeMap", "Collection", "Queue", "Deque",
            "Optional", "Stream", "Iterator", "Arrays", "Collections",
            "UUID", "Locale", "Calendar", "TimeUnit", "URL", "Timer", "TimeZone",
            // java.io / java.nio / java.sql
            "File", "Path", "URI", "InputStream", "OutputStream", "Reader", "Writer", "Timestamp",
            // java.util.function
            "Supplier", "Consumer", "Function", "Predicate", "BiConsumer", "BiFunction"));

    private UmlTypeNameUtil() {
        throw new AssertionError("utility class");
    }

    /**
     * 提取数组或泛型类型的元素类型。
     * {@code List<Order>} → {@code Order}；{@code Order[]} → {@code Order}；
     * {@code Map<String,List<Order>>} → {@code List<Order>}（保留最后一个顶层类型参数整体）。
     * 通配符边界（{@code ? extends X} / {@code ? super X}）归一化为 {@code X}。
     */
    public static String getElementType(String type) {
        if (type == null) {
            return "";
        }
        String t = type.trim();
        while (t.endsWith("[]")) {
            t = t.substring(0, t.length() - 2).trim();
        }
        int lt = t.indexOf('<');
        if (lt < 0) {
            return t;
        }
        int lastGt = t.lastIndexOf('>');
        if (lastGt <= lt) {
            return t.substring(0, lt).trim();
        }
        String inner = t.substring(lt + 1, lastGt).trim();
        String lastArg = splitLastTopLevelArg(inner).trim();
        if (lastArg.startsWith("? extends ")) {
            lastArg = lastArg.substring("? extends ".length()).trim();
        } else if (lastArg.startsWith("? super ")) {
            lastArg = lastArg.substring("? super ".length()).trim();
        } else if (lastArg.equals("?")) {
            lastArg = "";
        }
        return lastArg;
    }

    /** 返回泛型参数字符串中最后一个嵌套深度为 0 的逗号之后的部分；无顶层逗号则返回整个字符串 */
    private static String splitLastTopLevelArg(String inner) {
        int depth = 0;
        int lastComma = -1;
        for (int i = 0; i < inner.length(); i++) {
            char ch = inner.charAt(i);
            if (ch == '<') {
                depth++;
            } else if (ch == '>') {
                depth--;
            } else if (ch == ',' && depth == 0) {
                lastComma = i;
            }
        }
        return lastComma < 0 ? inner : inner.substring(lastComma + 1);
    }

    /** 当类型是数组或泛型（集合/映射）时返回 true */
    public static boolean isCollectionOrArray(String type) {
        if (type == null) {
            return false;
        }
        String t = type.trim();
        return t.endsWith("[]") || t.indexOf('<') >= 0;
    }

    /** 去掉包名前缀，保留元素类型的简单名称 */
    public static String getSimpleName(String type) {
        String base = getElementType(type);
        int dot = base.lastIndexOf('.');
        return dot >= 0 ? base.substring(dot + 1) : base;
    }

    /** 当元素类型是非 JDK 的类、值得建立关系时返回 true */
    public static boolean isUMLClassType(String type) {
        String simple = getSimpleName(type);
        if (simple.isEmpty()) {
            return false;
        }
        if (JAVA_CORE.contains(simple)) {
            return false;
        }
        String base = getElementType(type);
        return !isJdk(base);
    }

    /** 判定是否 JDK 内部类型；同时接受内部名（java/lang/Object）与点分名（java.util.ArrayList） */
    public static boolean isJdk(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String n = name.trim().replace('.', '/');
        return n.startsWith("java/") || n.startsWith("javax/")
                || n.startsWith("sun/") || n.startsWith("com/sun/");
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\mvnw.cmd test -Dtest="UmlTypeNameUtilTest"`
Expected: PASS（4 个测试）

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTypeNameUtil.java src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTypeNameUtilTest.java
git commit -m "feat(uml): 类型名判定工具 UmlTypeNameUtil（移植 UMLHelper）"
```

---

### Task 3: UmlClassExtractor + TestCompile（TDD）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassExtractor.java`
- Test: `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/TestCompile.java`
- Test: `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassExtractorTest.java`

- [ ] **Step 1: 写失败测试（先建 TestCompile 编译助手）**

`TestCompile.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.FileTreeNode;
import com.bingbaihanji.fxdecomplie.model.FileTreeModel;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 测试助手：内存编译源码 → 字节映射，并构造 FileTreeModel */
final class TestCompile {

    private TestCompile() {
        throw new AssertionError("utility class");
    }

    /** 编译多段源码，返回「com/example/Test.class」→ 字节 映射。键用完整路径（含 .class）。 */
    static Map<String, byte[]> compile(Map<String, String> classNameToSource) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        MemFileManager fm = new MemFileManager(
                compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8));
        List<JavaFileObject> units = new ArrayList<>();
        for (Map.Entry<String, String> e : classNameToSource.entrySet()) {
            String className = e.getKey();
            units.add(new SimpleJavaFileObject(
                    URI.create("string:///" + className.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
                    JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return e.getValue();
                }
            });
        }
        boolean ok = compiler.getTask(null, fm, null, null, null, units).call();
        if (!ok) {
            throw new IllegalStateException("测试源码编译失败");
        }
        return fm.outputBytes;
    }

    /** 由编译产物构建文件树根（PACKAGE 根 + CLASS_FILE 子节点） */
    static FileTreeModel treeFromClasses(Map<String, byte[]> classBytes) {
        FileTreeModel root = new FileTreeModel(
                new FileTreeNode("root", "", FileTreeNode.NodeTypeEnum.PACKAGE));
        for (Map.Entry<String, byte[]> e : classBytes.entrySet()) {
            String path = e.getKey();
            int idx = path.lastIndexOf('/');
            String name = idx >= 0 ? path.substring(idx + 1) : path;
            FileTreeNode node = new FileTreeNode(name, path, FileTreeNode.NodeTypeEnum.CLASS_FILE);
            node.setCachedBytes(e.getValue());
            root.getChildren().add(new FileTreeModel(node));
        }
        return root;
    }

    /** 内存文件管理器：捕获编译输出的 class 字节 */
    private static final class MemFileManager extends ForwardingJavaFileManager<JavaFileManager> {
        final Map<String, byte[]> outputBytes = new HashMap<>();

        MemFileManager(JavaFileManager delegate) {
            super(delegate);
        }

        @Override
        public JavaFileObject getJavaFileForOutput(Location location, String className,
                                                   JavaFileObject.Kind kind, FileObject sibling) {
            return new SimpleJavaFileObject(
                    URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind) {
                @Override
                public OutputStream openOutputStream() {
                    return new ByteArrayOutputStream() {
                        @Override
                        public void close() throws IOException {
                            super.close();
                            outputBytes.put(className.replace('.', '/') + ".class", toByteArray());
                        }
                    };
                }
            };
        }
    }
}
```

`UmlClassExtractorTest.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UmlClassExtractorTest {

    @Test
    void extract_classWithMembersAndGenerics() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.Order", "package com.example;\npublic class Order {}\n");
        sources.put("com.example.Person",
                "package com.example;\n"
                + "import java.util.List;\n"
                + "public abstract class Person {\n"
                + "    private String name;\n"
                + "    protected static int count;\n"
                + "    public List<Order> orders;\n"
                + "    public abstract void run();\n"
                + "    public static String greet(String who) { return \"hi\"; }\n"
                + "}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);

        UmlClass cls = UmlClassExtractor.extract("com/example/Person", bytes.get("com/example/Person.class"));
        assertNotNull(cls);
        assertEquals(ClassKind.CLASS, cls.kind());
        assertTrue(cls.isAbstract());
        assertEquals("Person", cls.simpleName());
        assertEquals("com.example", cls.packageName());
        assertEquals("java/lang/Object", cls.superName());

        assertTrue(cls.fields().stream().anyMatch(f -> f.name().equals("name")
                && f.visibility() == Visibility.PRIVATE && !f.isStatic()));
        assertTrue(cls.fields().stream().anyMatch(f -> f.name().equals("count")
                && f.visibility() == Visibility.PROTECTED && f.isStatic()));
        assertTrue(cls.fields().stream().anyMatch(f -> f.name().equals("orders")
                && f.type().contains("java.util.List<com.example.Order>")));

        assertTrue(cls.methods().stream().anyMatch(m -> m.isConstructor()));
        assertTrue(cls.methods().stream().anyMatch(m -> m.name().equals("run")
                && m.isAbstract() && m.returnType().equals("void")));
        assertTrue(cls.methods().stream().anyMatch(m -> m.name().equals("greet")
                && m.isStatic() && m.returnType().equals("java.lang.String")
                && m.params().equals(java.util.List.of("java.lang.String"))));
    }

    @Test
    void extract_interfaceEnumAnnotation() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.IFace", "package com.example;\npublic interface IFace { int add(int a, int b); }\n");
        sources.put("com.example.MyEnum", "package com.example;\npublic enum MyEnum { A, B }\n");
        sources.put("com.example.MyAnn", "package com.example;\npublic @interface MyAnn { String value(); }\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);

        UmlClass iface = UmlClassExtractor.extract("com/example/IFace", bytes.get("com/example/IFace.class"));
        assertEquals(ClassKind.INTERFACE, iface.kind());

        UmlClass enm = UmlClassExtractor.extract("com/example/MyEnum", bytes.get("com/example/MyEnum.class"));
        assertEquals(ClassKind.ENUM, enm.kind());
        assertTrue(enm.interfaces().contains("java/lang/Enum") || enm.superName().equals("java/lang/Enum"));

        UmlClass ann = UmlClassExtractor.extract("com/example/MyAnn", bytes.get("com/example/MyAnn.class"));
        assertEquals(ClassKind.ANNOTATION, ann.kind());
    }

    @Test
    void extract_nullOrInvalidBytesReturnsNull() {
        assertNull(UmlClassExtractor.extract("com/example/X", null));
        assertNull(UmlClassExtractor.extract("com/example/X", new byte[0]));
        assertNull(UmlClassExtractor.extract("com/example/X", new byte[]{1, 2, 3}));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\mvnw.cmd test -Dtest="UmlClassExtractorTest"`
Expected: FAIL — 编译错误（UmlClassExtractor 不存在）

- [ ] **Step 3: 实现 UmlClassExtractor**

`UmlClassExtractor.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 用 ASM 从 class 字节码提取 {@link UmlClass}。
 * 类型名优先取泛型签名（signature），否则回退描述符。
 */
public final class UmlClassExtractor {

    private static final Logger log = LoggerFactory.getLogger(UmlClassExtractor.class);

    private UmlClassExtractor() {
        throw new AssertionError("utility class");
    }

    /** 解析 class 字节为 UML 类；输入为空或解析失败返回 null */
    public static UmlClass extract(String internalName, byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classBytes);
            UmlClassVisitor visitor = new UmlClassVisitor(internalName);
            reader.accept(visitor, 0);
            return visitor.build();
        } catch (RuntimeException e) {
            log.debug("UML 类提取失败: {}", internalName, e);
            return null;
        }
    }

    // ======================== 访问器 ========================

    private static final class UmlClassVisitor extends ClassVisitor {

        private final String expectedName;
        private String internalName;
        private String signature;
        private int access;
        private String superName;
        private String[] interfaces;
        private final List<UmlField> fields = new ArrayList<>();
        private final List<UmlMethod> methods = new ArrayList<>();
        private final List<String> annotations = new ArrayList<>();

        UmlClassVisitor(String expectedName) {
            super(Opcodes.ASM9);
            this.expectedName = expectedName;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.access = access;
            this.signature = signature;
            this.superName = superName;
            this.interfaces = interfaces == null ? new String[0] : interfaces;
            this.internalName = name != null ? name : expectedName;
        }

        @Override
        public org.objectweb.asm.AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            if (descriptor != null && descriptor.length() > 2
                    && descriptor.startsWith("L") && descriptor.endsWith(";")) {
                annotations.add(descriptor.substring(1, descriptor.length() - 1));
            }
            return null;
        }

        @Override
        public org.objectweb.asm.FieldVisitor visitField(int access, String name,
                                                         String descriptor, String signature, Object value) {
            fields.add(new UmlField(
                    name,
                    readableType(signature != null ? signature : descriptor),
                    visibility(access),
                    (access & Opcodes.ACC_STATIC) != 0));
            return null;
        }

        @Override
        public org.objectweb.asm.MethodVisitor visitMethod(int access, String name,
                                                           String descriptor, String signature, String[] exceptions) {
            boolean isCtor = "<init>".equals(name);
            methods.add(new UmlMethod(
                    isCtor ? name : name,
                    methodParams(signature, descriptor),
                    isCtor ? "" : methodReturn(signature, descriptor),
                    visibility(access),
                    (access & Opcodes.ACC_STATIC) != 0,
                    (access & Opcodes.ACC_ABSTRACT) != 0,
                    isCtor));
            return null;
        }

        UmlClass build() {
            boolean isEnum = (access & Opcodes.ACC_ENUM) != 0;
            boolean isAnnotation = (access & Opcodes.ACC_ANNOTATION) != 0;
            boolean isInterface = (access & Opcodes.ACC_INTERFACE) != 0;
            ClassKind kind = isAnnotation ? ClassKind.ANNOTATION
                    : isEnum ? ClassKind.ENUM
                    : isInterface ? ClassKind.INTERFACE
                    : ClassKind.CLASS;
            String simple = internalName == null ? "" : simpleName(internalName);
            String pkg = internalName == null ? "" : packageName(internalName);
            return new UmlClass(internalName, pkg, simple, kind,
                    (access & Opcodes.ACC_ABSTRACT) != 0, true,
                    List.copyOf(fields), List.copyOf(methods),
                    superName, List.of(interfaces), List.copyOf(annotations));
        }
    }

    // ======================== 可见性 ========================

    private static Visibility visibility(int access) {
        if ((access & Opcodes.ACC_PUBLIC) != 0) {
            return Visibility.PUBLIC;
        }
        if ((access & Opcodes.ACC_PROTECTED) != 0) {
            return Visibility.PROTECTED;
        }
        if ((access & Opcodes.ACC_PRIVATE) != 0) {
            return Visibility.PRIVATE;
        }
        return Visibility.PACKAGE;
    }

    // ======================== 类型名渲染 ========================

    /** 字段类型：有泛型签名走签名渲染，否则描述符 */
    private static String readableType(String sigOrDesc) {
        try {
            if (sigOrDesc != null && sigOrDesc.indexOf('<') >= 0) {
                String rendered = renderTypeSignature(sigOrDesc);
                if (rendered != null && !rendered.isEmpty()) {
                    return rendered;
                }
            }
            if (sigOrDesc == null || sigOrDesc.isEmpty()) {
                return "";
            }
            return Type.getType(sigOrDesc).getClassName();
        } catch (RuntimeException e) {
            return sigOrDesc == null ? "" : sigOrDesc;
        }
    }

    private static List<String> methodParams(String signature, String descriptor) {
        List<String> fromDesc = new ArrayList<>();
        try {
            for (Type t : Type.getArgumentTypes(descriptor)) {
                fromDesc.add(t.getClassName());
            }
        } catch (RuntimeException e) {
            fromDesc.clear();
        }
        if (signature != null && signature.indexOf('<') >= 0) {
            try {
                MethodSigParser parser = new MethodSigParser();
                new SignatureReader(signature).accept(parser);
                if (!parser.params().isEmpty()) {
                    return List.copyOf(parser.params());
                }
            } catch (RuntimeException ignored) {
                // 签名解析失败，回退描述符
            }
        }
        return List.copyOf(fromDesc);
    }

    private static String methodReturn(String signature, String descriptor) {
        try {
            if (signature != null && signature.indexOf('<') >= 0) {
                MethodSigParser parser = new MethodSigParser();
                new SignatureReader(signature).accept(parser);
                String fromSig = parser.returnType();
                if (fromSig != null && !fromSig.isEmpty()) {
                    return fromSig;
                }
            }
            return Type.getReturnType(descriptor).getClassName();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 渲染纯类型签名（如 {@code Ljava/util/List<Lcom/Order;>;}）为可读点分文本 */
    private static String renderTypeSignature(String signature) {
        SigBuilder builder = new SigBuilder();
        new SignatureReader(signature).acceptType(builder);
        return builder.toString();
    }

    // ======================== 泛型签名渲染 ========================

    /** 无操作访问器，用于吞掉类级/方法级泛型声明（类型参数绑定） */
    private static final SignatureVisitor NO_OP = new SignatureVisitor(Opcodes.ASM9) {
    };

    /** 将一段类型签名渲染为 Java 风格文本；数组元素由独立子构建器负责并在 visitEnd 补 "[]" */
    private static final class SigBuilder extends SignatureVisitor {

        private static final class Frame {
            boolean angleOpened;
            int args;
        }

        private final StringBuilder sb = new StringBuilder();
        private final Deque<Frame> frames = new ArrayDeque<>();

        SigBuilder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            // 忽略类型参数声明
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return NO_OP;
        }

        @Override
        public void visitBaseType(char descriptor) {
            sb.append(baseTypeName(descriptor));
        }

        @Override
        public void visitTypeVariable(String name) {
            sb.append(name);
        }

        @Override
        public void visitClassType(String name) {
            sb.append(name.replace('/', '.'));
            frames.push(new Frame());
        }

        @Override
        public void visitInnerClassType(String name) {
            sb.append('$').append(name);
        }

        @Override
        public void visitTypeArgument() {
            typeArgStart();
            sb.append('?');
        }

        @Override
        public SignatureVisitor visitTypeArgument(char wildcard) {
            typeArgStart();
            if (wildcard == SignatureVisitor.EXTENDS) {
                sb.append("? extends ");
            } else if (wildcard == SignatureVisitor.SUPER) {
                sb.append("? super ");
            }
            return this;
        }

        private void typeArgStart() {
            Frame f = frames.peek();
            if (f == null) {
                return;
            }
            if (f.args > 0) {
                sb.append(", ");
            } else {
                sb.append('<');
                f.angleOpened = true;
            }
            f.args++;
        }

        @Override
        public void visitEnd() {
            Frame f = frames.peek();
            if (f != null) {
                if (f.angleOpened) {
                    sb.append('>');
                }
                frames.pop();
            }
        }

        @Override
        public SignatureVisitor visitArrayType() {
            return new SignatureVisitor(Opcodes.ASM9) {
                private final SigBuilder element = new SigBuilder();

                @Override
                public void visitBaseType(char d) {
                    element.visitBaseType(d);
                }

                @Override
                public void visitTypeVariable(String n) {
                    element.visitTypeVariable(n);
                }

                @Override
                public SignatureVisitor visitArrayType() {
                    return new SignatureVisitor(Opcodes.ASM9) {
                        private final SigBuilder inner = new SigBuilder();

                        @Override
                        public void visitBaseType(char d) {
                            inner.visitBaseType(d);
                        }

                        @Override
                        public void visitTypeVariable(String n) {
                            inner.visitTypeVariable(n);
                        }

                        @Override
                        public SignatureVisitor visitArrayType() {
                            return new SignatureVisitor(Opcodes.ASM9) {
                                private final SigBuilder inner2 = new SigBuilder();

                                @Override
                                public void visitBaseType(char d) {
                                    inner2.visitBaseType(d);
                                }

                                @Override
                                public void visitTypeVariable(String n) {
                                    inner2.visitTypeVariable(n);
                                }

                                @Override
                                public SignatureVisitor visitArrayType() {
                                    return this;
                                }

                                @Override
                                public void visitClassType(String n) {
                                    inner2.visitClassType(n);
                                }

                                @Override
                                public void visitInnerClassType(String n) {
                                    inner2.visitInnerClassType(n);
                                }

                                @Override
                                public void visitTypeArgument() {
                                    inner2.visitTypeArgument();
                                }

                                @Override
                                public SignatureVisitor visitTypeArgument(char w) {
                                    return inner2.visitTypeArgument(w);
                                }

                                @Override
                                public void visitEnd() {
                                    sb.append(inner2).append("[]");
                                }
                            };
                        }

                        @Override
                        public void visitClassType(String n) {
                            inner.visitClassType(n);
                        }

                        @Override
                        public void visitInnerClassType(String n) {
                            inner.visitInnerClassType(n);
                        }

                        @Override
                        public void visitTypeArgument() {
                            inner.visitTypeArgument();
                        }

                        @Override
                        public SignatureVisitor visitTypeArgument(char w) {
                            return inner.visitTypeArgument(w);
                        }

                        @Override
                        public void visitEnd() {
                            sb.append(inner).append("[]");
                        }
                    };
                }

                @Override
                public void visitClassType(String n) {
                    element.visitClassType(n);
                }

                @Override
                public void visitInnerClassType(String n) {
                    element.visitInnerClassType(n);
                }

                @Override
                public void visitTypeArgument() {
                    element.visitTypeArgument();
                }

                @Override
                public SignatureVisitor visitTypeArgument(char w) {
                    return element.visitTypeArgument(w);
                }

                @Override
                public void visitEnd() {
                    sb.append(element).append("[]");
                }
            };
        }

        @Override
        public String toString() {
            return sb.toString();
        }
    }

    /** 解析方法签名，逐个收集参数类型与返回类型 */
    private static final class MethodSigParser extends SignatureVisitor {

        private final List<String> params = new ArrayList<>();
        private String returnType = "";

        MethodSigParser() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visitFormalTypeParameter(String name) {
            // 忽略类型参数声明
        }

        @Override
        public SignatureVisitor visitClassBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitInterfaceBound() {
            return NO_OP;
        }

        @Override
        public SignatureVisitor visitParameterType() {
            return new SigBuilder() {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    params.add(toString());
                }
            };
        }

        @Override
        public SignatureVisitor visitReturnType() {
            return new SigBuilder() {
                @Override
                public void visitEnd() {
                    super.visitEnd();
                    returnType = toString();
                }
            };
        }

        @Override
        public void visitEnd() {
            // 方法签名整体结束，无额外处理
        }

        List<String> params() {
            return List.copyOf(params);
        }

        String returnType() {
            return returnType;
        }
    }

    private static String baseTypeName(char d) {
        return switch (d) {
            case 'B' -> "byte";
            case 'C' -> "char";
            case 'D' -> "double";
            case 'F' -> "float";
            case 'I' -> "int";
            case 'J' -> "long";
            case 'S' -> "short";
            case 'Z' -> "boolean";
            case 'V' -> "void";
            default -> String.valueOf(d);
        };
    }

    private static String simpleName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx >= 0 ? internalName.substring(idx + 1) : internalName;
    }

    private static String packageName(String internalName) {
        int idx = internalName.lastIndexOf('/');
        return idx < 0 ? "" : internalName.substring(0, idx).replace('/', '.');
    }
}
```

> 说明：`SignatureReader.acceptType(SigBuilder)` 只解析类型签名（type signature），不含 superclass/参数，规避了 `visitSuperclass`/`visitParameterType` 干扰。方法签名用 `accept(MethodSigParser)`。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\mvnw.cmd test -Dtest="UmlClassExtractorTest"`
Expected: PASS（3 个测试）

> 若 `Person` 泛型字段断言失败：请确认 `TestCompile` 编译产生的 class 确实带 signature 属性（`javac` 默认写泛型签名）。若本地 `ToolProvider` 返回 null（无 JDK 编译器），改用项目既有测试模式（参考 `WorkspaceIndexTest`，同样依赖 `ToolProvider`，项目测试环境应可用）。

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassExtractor.java src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/TestCompile.java src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassExtractorTest.java
git commit -m "feat(uml): ASM 字节码 → UmlClass 提取器（含泛型签名渲染）"
```

---

### Task 4: UmlDiagramBuilder（TDD）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramBuilder.java`
- Test: `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramBuilderTest.java`

- [ ] **Step 1: 写失败测试**

`UmlDiagramBuilderTest.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.FileTreeModel;
import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UmlDiagramBuilderTest {

    @Test
    void build_expandsInheritanceRealizationAssociationDependency() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.Order", "package com.example;\npublic class Order { public String id; }\n");
        sources.put("com.example.IOrderService",
                "package com.example;\npublic interface IOrderService { Order find(String id); }\n");
        sources.put("com.example.AbstractOrderService",
                "package com.example;\nimport java.util.List;\n"
                + "public abstract class AbstractOrderService implements IOrderService {\n"
                + "    private List<Order> orders;\n"
                + "}\n");
        sources.put("com.example.OrderServiceImpl",
                "package com.example;\npublic class OrderServiceImpl extends AbstractOrderService {\n"
                + "    private double total;\n"
                + "    public void save(Order o) { }\n"
                + "}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/OrderServiceImpl", bytes.get("com/example/OrderServiceImpl.class"), index);

        assertNotNull(diagram);
        assertEquals("com/example/OrderServiceImpl", diagram.target());

        // 类节点：自身 + 父类 + 接口 + 关联类
        var names = diagram.classes().stream().map(UmlClass::internalName).toList();
        assertTrue(names.contains("com/example/OrderServiceImpl"));
        assertTrue(names.contains("com/example/AbstractOrderService"));
        assertTrue(names.contains("com/example/IOrderService"));
        assertTrue(names.contains("com/example/Order"));
        // JDK 类不建节点
        assertFalse(names.contains("java/lang/Object"));
        assertFalse(names.contains("java/lang/String"));
        assertFalse(names.contains("java/util/List"));

        // 关系：继承 / 实现 / 关联(0..*) / 依赖
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/OrderServiceImpl", "com/example/AbstractOrderService",
                        RelationType.GENERALIZATION, null, null)));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/IOrderService",
                        RelationType.REALIZATION, null, null)));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/Order",
                        RelationType.ASSOCIATION, null, "0..*")));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/OrderServiceImpl", "com/example/Order",
                        RelationType.DEPENDENCY, null, null)));
    }

    @Test
    void build_unresolvableTypeBecomesMarker() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.External", "package com.example;\npublic class External {}\n");
        sources.put("com.example.UsesExternal",
                "package com.example;\npublic class UsesExternal { private External ext; }\n");
        Map<String, byte[]> all = TestCompile.compile(sources);
        // 从工作区中剔除 External，使其解析不到
        Map<String, byte[]> treeBytes = new HashMap<>(all);
        treeBytes.remove("com/example/External.class");
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(treeBytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/UsesExternal", all.get("com/example/UsesExternal.class"), index);

        assertNotNull(diagram);
        UmlClass external = diagram.classes().stream()
                .filter(c -> c.internalName().equals("com/example/External"))
                .findFirst().orElseThrow();
        assertFalse(external.parsed());
        assertTrue(external.fields().isEmpty());
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/UsesExternal", "com/example/External",
                        RelationType.ASSOCIATION, null, null)));
    }

    @Test
    void build_nullOrInvalidInput() {
        assertNull(UmlDiagramBuilder.build(null, new byte[]{1}, WorkspaceIndex.EMPTY));
        UmlDiagram noBytes = UmlDiagramBuilder.build("com/example/X", null, WorkspaceIndex.EMPTY);
        assertNull(noBytes);
        UmlDiagram emptyBytes = UmlDiagramBuilder.build("com/example/X", new byte[0], WorkspaceIndex.EMPTY);
        assertNull(emptyBytes);
    }

    @Test
    void build_respectsClassLimit() {
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 105;
        for (int i = 0; i < total; i++) {
            String name = "A" + i;
            String parent = i + 1 < total ? " A" + (i + 1) + " " : " Object ";
            sources.put("com.example." + name,
                    "package com.example;\npublic class " + name + " extends" + parent + "{}\n");
        }
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/A0", bytes.get("com/example/A0.class"), index);

        assertNotNull(diagram);
        assertTrue(diagram.classes().size() <= UmlDiagramBuilder.MAX_CLASSES);
        assertTrue(diagram.classes().stream().anyMatch(c -> c.internalName().equals("com/example/A0")));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\mvnw.cmd test -Dtest="UmlDiagramBuilderTest"`
Expected: FAIL — 编译错误（UmlDiagramBuilder 不存在）

- [ ] **Step 3: 实现 UmlDiagramBuilder**

`UmlDiagramBuilder.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从目标类出发 BFS 构建 UML 类图闭包。
 *
 * <p>展开方向：父类（GENERALIZATION）、接口（REALIZATION）、字段元素类型（ASSOCIATION，
 * 集合/数组带 0..* 多重性）、方法参数元素类型（DEPENDENCY）、子类（反向 GENERALIZATION）。
 * JDK 类型不建节点；解析不到的类建「仅类名」标记框。</p>
 */
public final class UmlDiagramBuilder {

    /** 类节点数量上限 */
    public static final int MAX_CLASSES = 100;
    /** 关系展开深度上限 */
    public static final int MAX_DEPTH = 20;

    private UmlDiagramBuilder() {
        throw new AssertionError("utility class");
    }

    /**
     * 构建 UML 类图。
     *
     * @param targetInternalName 目标类内部名称（如 com/example/OrderServiceImpl）
     * @param targetBytes        目标类字节码，优先于索引读取
     * @param index              工作区索引，可为 {@link WorkspaceIndex#EMPTY}
     * @return 类图；目标不可解析返回 null
     */
    public static UmlDiagram build(String targetInternalName, byte[] targetBytes, WorkspaceIndex index) {
        if (targetInternalName == null || targetInternalName.isBlank()) {
            return null;
        }
        String target = normalize(targetInternalName);
        byte[] rootBytes = (targetBytes == null || targetBytes.length == 0)
                ? (index == null ? null : index.getClassBytes(target))
                : targetBytes;
        if (rootBytes == null || rootBytes.length == 0) {
            return null;
        }

        Map<String, UmlClass> byName = new LinkedHashMap<>();
        List<UmlRelation> relations = new ArrayList<>();
        Set<String> seenRelations = new LinkedHashSet<>();
        Set<String> queued = new LinkedHashSet<>();
        Map<String, Integer> depth = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();

        queued.add(target);
        depth.put(target, 0);
        queue.add(target);

        while (!queue.isEmpty() && byName.size() < MAX_CLASSES) {
            String name = queue.removeFirst();
            if (byName.containsKey(name)) {
                continue;
            }
            int d = depth.getOrDefault(name, 0);

            byte[] bytes = name.equals(target) && rootBytes != null
                    ? rootBytes
                    : (index == null ? null : index.getClassBytes(name));
            UmlClass uml = UmlClassExtractor.extract(name, bytes);
            if (uml == null) {
                byName.put(name, marker(name));
                continue;
            }
            byName.put(name, uml);
            if (d >= MAX_DEPTH) {
                continue; // 仅显示，不再展开关系
            }

            // 继承：父类
            if (uml.superName() != null && !UmlTypeNameUtil.isJdk(uml.superName())) {
                addRelation(relations, seenRelations, name, uml.superName(), RelationType.GENERALIZATION, null, null);
                enqueue(uml.superName(), d + 1, queued, queue, depth);
            }
            // 实现：接口
            for (String itf : uml.interfaces()) {
                if (UmlTypeNameUtil.isJdk(itf)) {
                    continue;
                }
                addRelation(relations, seenRelations, name, itf, RelationType.REALIZATION, null, null);
                enqueue(itf, d + 1, queued, queue, depth);
            }
            // 关联：字段元素类型
            for (UmlField f : uml.fields()) {
                addTypeRelation(relations, seenRelations, queue, queued, depth, name, d,
                        f.type(), RelationType.ASSOCIATION, true);
            }
            // 依赖：方法参数元素类型
            for (UmlMethod m : uml.methods()) {
                for (String p : m.params()) {
                    addTypeRelation(relations, seenRelations, queue, queued, depth, name, d,
                            p, RelationType.DEPENDENCY, false);
                }
            }
            // 子类：反向继承
            if (index != null) {
                for (String child : index.subclassesByParent().getOrDefault(name, List.of())) {
                    if (UmlTypeNameUtil.isJdk(child) || child.equals(name)) {
                        continue;
                    }
                    addRelation(relations, seenRelations, child, name, RelationType.GENERALIZATION, null, null);
                    enqueue(child, d + 1, queued, queue, depth);
                }
            }
        }
        return new UmlDiagram(target, List.copyOf(byName.values()), List.copyOf(relations));
    }

    /** 按可读类型名建立 ASSOCIATION/DEPENDENCY 关系 */
    private static void addTypeRelation(List<UmlRelation> relations, Set<String> seenRelations,
                                        Deque<String> queue, Set<String> queued, Map<String, Integer> depth,
                                        String from, int fromDepth, String readableType,
                                        RelationType type, boolean isField) {
        String element = UmlTypeNameUtil.getElementType(readableType);
        if (!UmlTypeNameUtil.isUMLClassType(element)) {
            return;
        }
        String target = normalize(element);
        if (target.equals(from)) {
            return; // 自引用不建边
        }
        String cardinality = isField && UmlTypeNameUtil.isCollectionOrArray(readableType) ? "0..*" : null;
        addRelation(relations, seenRelations, from, target, type, null, cardinality);
        enqueue(target, fromDepth + 1, queued, queue, depth);
    }

    private static void addRelation(List<UmlRelation> relations, Set<String> seenRelations,
                                    String from, String to, RelationType type,
                                    String fromCardinality, String toCardinality) {
        String key = from + ' ' + to + ' ' + type + ' '
                + fromCardinality + ' ' + toCardinality;
        if (seenRelations.add(key)) {
            relations.add(new UmlRelation(from, to, type, fromCardinality, toCardinality));
        }
    }

    private static void enqueue(String name, int d, Set<String> queued,
                                Deque<String> queue, Map<String, Integer> depth) {
        if (queued.add(name)) {
            depth.put(name, d);
            queue.add(name);
        }
    }

    /** 解析不到的类：仅类名标记框 */
    private static UmlClass marker(String internalName) {
        String simple = internalName;
        int idx = internalName.lastIndexOf('/');
        if (idx >= 0) {
            simple = internalName.substring(idx + 1);
        }
        return new UmlClass(internalName, "", simple, ClassKind.CLASS, false, false,
                List.of(), List.of(), null, List.of(), List.of());
    }

    private static String normalize(String name) {
        return name.replace('\\', '/');
    }
}
```

> 说明：`targetBytes` 为 null 或空时返回 null；若传非法字节（如 `{1}`），`UmlClassExtractor.extract` 失败会降级为「仅类名」标记框而非返回 null —— 因此测试只用 null/空 bytes 断言 null。

- [ ] **Step 4: 运行测试确认通过**

Run: `.\mvnw.cmd test -Dtest="UmlDiagramBuilderTest"`
Expected: PASS（4 个测试）

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramBuilder.java src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramBuilderTest.java
git commit -m "feat(uml): BFS 闭包构建 UmlDiagram（继承/实现/关联/依赖 + 上限保护）"
```

---

### Task 5: UmlTheme（色板）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTheme.java`

- [ ] **Step 1: 实现色板**

纯数据 record + 亮/暗预设，无逻辑，不写单测。

`UmlTheme.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

/**
 * UML 类图亮/暗色板。
 * 颜色取自参考项目 class-diagram-gen 的 PlantUML skinparam。
 */
public record UmlTheme(
        boolean dark,
        String background,
        String nodeBackground,
        String nodeBorder,
        String titleText,
        String packageText,
        String memberText,
        String classHeader,
        String interfaceHeader,
        String enumHeader,
        String annotationHeader,
        String markerBackground,
        String generalizationColor,
        String realizationColor,
        String associationColor,
        String dependencyColor
) {

    public static final UmlTheme LIGHT = new UmlTheme(
            false,
            "#FFFFFF",
            "#FFFFFF",
            "#A0A7B3",
            "#1F2430",
            "#8C9BAC",
            "#2E3440",
            "#EDF2FA",
            "#E9F4EC",
            "#FDF3E0",
            "#F3E9FA",
            "#EDEDED",
            "#000082",
            "#008200",
            "#595959",
            "#595959");

    public static final UmlTheme DARK = new UmlTheme(
            true,
            "#1E1E1E",
            "#3C3F41",
            "#6A6F73",
            "#E6E6E6",
            "#8C8C8C",
            "#BBBBBB",
            "#4B5053",
            "#3D5A46",
            "#5A4A30",
            "#4A3A52",
            "#3A3D40",
            "#6C8CFF",
            "#4CAF50",
            "#9E9E9E",
            "#9E9E9E");
}
```

- [ ] **Step 2: 编译验证**

Run: `.\mvnw.cmd clean compile -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlTheme.java
git commit -m "feat(uml): 亮/暗色板 UmlTheme"
```

---

### Task 6: PumlExporter（TDD）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/PumlExporter.java`
- Test: `src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/PumlExporterTest.java`

- [ ] **Step 1: 写失败测试**

`PumlExporterTest.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PumlExporterTest {

    @Test
    void buildSource_containsSkinClassAndRelations() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.Order", "package com.example;\npublic class Order {}\n");
        sources.put("com.example.AbstractService",
                "package com.example;\nimport java.util.List;\n"
                + "public abstract class AbstractService {\n    private List<Order> orders;\n}\n");
        sources.put("com.example.ServiceImpl",
                "package com.example;\npublic class ServiceImpl extends AbstractService {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/ServiceImpl", bytes.get("com/example/ServiceImpl.class"), index);
        String puml = PumlExporter.buildSource(diagram, UmlTheme.LIGHT);

        assertTrue(puml.startsWith("@startuml"));
        assertTrue(puml.contains("top to bottom direction"));
        assertTrue(puml.contains("skinparam"));
        assertTrue(puml.contains("class \"**ServiceImpl**"));
        assertTrue(puml.contains("abstract class \"**AbstractService**"));
        assertTrue(puml.contains("0..*"));
        assertTrue(puml.contains("^"));
        assertTrue(puml.endsWith("@enduml"));
    }

    @Test
    void buildSource_darkThemeUsesDarkSkin() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.A", "package com.example;\npublic class A {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/A", bytes.get("com/example/A.class"),
                WorkspaceIndex.build(TestCompile.treeFromClasses(bytes)));

        String light = PumlExporter.buildSource(diagram, UmlTheme.LIGHT);
        String dark = PumlExporter.buildSource(diagram, UmlTheme.DARK);
        assertNotEquals(light, dark);
        assertTrue(dark.contains("#2B2B2B"));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `.\mvnw.cmd test -Dtest="PumlExporterTest"`
Expected: FAIL — 编译错误（PumlExporter 不存在）

- [ ] **Step 3: 实现 PumlExporter**

`PumlExporter.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将 {@link UmlDiagram} 生成 PlantUML 源码（.puml 文本），
 * 移植自 class-diagram-gen 的 {@code GenerateUML.buildSource}。
 * 应用内不渲染 .puml，仅供用户用 PlantUML 工具二次生成/编辑。
 */
public final class PumlExporter {

    private static final String LIGHT_SKIN =
            "skinparam {\n"
            + "  linetype ortho\n"
            + "  shadowing false\n"
            + "  roundCorner 10\n"
            + "  backgroundColor #FFFFFF\n"
            + "  defaultFontName \"Segoe UI\"\n"
            + "  defaultFontSize 12\n"
            + "  classAttributeIconSize 0\n"
            + "  classFontSize 13\n"
            + "  classFontStyle bold\n"
            + "  classFontColor #1F2430\n"
            + "  classHeaderFontSize 14\n"
            + "  classHeaderFontStyle bold\n"
            + "  classHeaderFontColor #1F2430\n"
            + "  classHeaderBackgroundColor #EDF2FA\n"
            + "  classBackgroundColor #FFFFFF\n"
            + "  classBorderColor #A0A7B3\n"
            + "  classAttributeFontSize 11\n"
            + "  classAttributeFontStyle normal\n"
            + "  classAttributeFontColor #2E3440\n"
            + "  classStereotypeFontSize 11\n"
            + "  classStereotypeFontColor #5A6572\n"
            + "  classArrowColor #44546A\n"
            + "}\n";

    private static final String DARK_SKIN =
            "skinparam {\n"
            + "  linetype ortho\n"
            + "  shadowing false\n"
            + "  roundCorner 10\n"
            + "  backgroundColor #2B2B2B\n"
            + "  defaultFontName \"Segoe UI\"\n"
            + "  defaultFontSize 12\n"
            + "  classAttributeIconSize 0\n"
            + "  classFontSize 13\n"
            + "  classFontStyle bold\n"
            + "  classFontColor #E6E6E6\n"
            + "  classHeaderFontSize 14\n"
            + "  classHeaderFontStyle bold\n"
            + "  classHeaderFontColor #FFFFFF\n"
            + "  classHeaderBackgroundColor #4B5053\n"
            + "  classBackgroundColor #3C3F41\n"
            + "  classBorderColor #6A6F73\n"
            + "  classAttributeFontSize 11\n"
            + "  classAttributeFontStyle normal\n"
            + "  classAttributeFontColor #BBBBBB\n"
            + "  classStereotypeFontSize 11\n"
            + "  classStereotypeFontColor #9A9A9A\n"
            + "  classArrowColor #9A9A9A\n"
            + "}\n";

    private PumlExporter() {
        throw new AssertionError("utility class");
    }

    public static String buildSource(UmlDiagram diagram, UmlTheme theme) {
        if (diagram == null || diagram.classes().isEmpty()) {
            return "@startuml\n@enduml";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        sb.append("top to bottom direction\n");
        sb.append(theme.dark() ? DARK_SKIN : LIGHT_SKIN);

        Map<String, String> alias = new LinkedHashMap<>();
        int i = 0;
        for (UmlClass c : diagram.classes()) {
            alias.put(c.internalName(), "C" + i);
            i++;
        }

        i = 0;
        for (UmlClass c : diagram.classes()) {
            String a = "C" + i;
            sb.append(kindKeyword(c)).append(' ')
              .append(title(c, theme)).append(" as ").append(a)
              .append(nodeColor(c, theme)).append(" {\n");
            appendMembers(c, sb);
            sb.append("}\n\n");
            i++;
        }

        for (UmlRelation r : diagram.relations()) {
            String child = alias.get(r.from());
            String parent = alias.get(r.to());
            if (child == null || parent == null) {
                continue;
            }
            sb.append(relationshipLine(r, child, parent, theme));
        }

        // 注解应用边（黄点线）
        String annotationSpec = theme.dark() ? "[#C9B45A,dotted]" : "[#999900,dotted]";
        for (UmlClass c : diagram.classes()) {
            for (String ann : c.annotationNames()) {
                String annAlias = alias.get(ann);
                if (annAlias != null && !annAlias.equals(alias.get(c.internalName()))) {
                    sb.append(alias.get(c.internalName()))
                      .append(" -").append(annotationSpec).append("- ")
                      .append(annAlias).append('\n');
                }
            }
        }
        sb.append("@enduml");
        return sb.toString();
    }

    private static String kindKeyword(UmlClass c) {
        return switch (c.kind()) {
            case INTERFACE -> "interface";
            case ENUM -> "enum";
            case ANNOTATION -> "annotation";
            case CLASS -> c.isAbstract() ? "abstract class" : "class";
        };
    }

    private static String title(UmlClass c, UmlTheme theme) {
        String pkgColor = theme.dark() ? "#8C8C8C" : "#8C9BAC";
        String title = "**" + c.simpleName() + "**";
        if (c.packageName() != null && !c.packageName().isEmpty()) {
            title += "\\n<size:10><color:" + pkgColor + ">" + c.packageName() + "</color></size>";
        }
        return "\"" + title + "\"";
    }

    private static String nodeColor(UmlClass c, UmlTheme theme) {
        if (!c.parsed()) {
            return theme.dark() ? " #3A3D40" : " #EDEDED";
        }
        if (theme.dark()) {
            return switch (c.kind()) {
                case INTERFACE -> " #back:3C3F41;header:3D5A46";
                case ENUM -> " #back:3C3F41;header:5A4A30";
                case ANNOTATION -> " #back:3C3F41;header:4A3A52";
                case CLASS -> " #back:3C3F41;header:4B5053";
            };
        }
        return switch (c.kind()) {
            case INTERFACE -> " #back:FFFFFF;header:E9F4EC";
            case ENUM -> " #back:FFFFFF;header:FDF3E0";
            case ANNOTATION -> " #back:FFFFFF;header:F3E9FA";
            case CLASS -> " #back:FFFFFF;header:EDF2FA";
        };
    }

    private static void appendMembers(UmlClass c, StringBuilder sb) {
        if (!c.parsed()) {
            return;
        }
        List<UmlField> fields = c.fields();
        List<UmlMethod> methods = c.methods();
        for (UmlField f : fields) {
            sb.append("  ").append(fieldLine(f)).append('\n');
        }
        if (!fields.isEmpty() && !methods.isEmpty()) {
            sb.append("  --\n");
        }
        for (UmlMethod m : methods) {
            sb.append("  ").append(methodLine(m)).append('\n');
        }
    }

    private static String fieldLine(UmlField f) {
        StringBuilder s = new StringBuilder();
        s.append(f.visibility().symbol());
        if (f.isStatic()) {
            s.append(" {static}");
        }
        s.append(f.name()).append(": ").append(f.type());
        return s.toString();
    }

    private static String methodLine(UmlMethod m) {
        StringBuilder s = new StringBuilder();
        s.append(m.visibility().symbol());
        if (m.isStatic()) {
            s.append(" {static}");
        }
        if (m.isAbstract()) {
            s.append(" {abstract}");
        }
        s.append(m.name()).append('(').append(String.join(", ", m.params())).append(')');
        if (!m.isConstructor()) {
            s.append(": ").append(m.returnType());
        }
        return s.toString();
    }

    private static String relationshipLine(UmlRelation r, String child, String parent, UmlTheme theme) {
        String spec = arrowSpec(r.type(), theme);
        String head = switch (r.type()) {
            case GENERALIZATION, REALIZATION -> "^";
            case DEPENDENCY -> ">";
            case ASSOCIATION -> "";
        };
        String fromCard = r.fromCardinality() == null ? "" : "\"" + r.fromCardinality() + "\" ";
        String toCard = r.toCardinality() == null ? "" : " \"" + r.toCardinality() + "\"";
        return child + " " + fromCard + "-" + spec + "-" + head + toCard + " " + parent + "\n";
    }

    private static String arrowSpec(RelationType type, UmlTheme theme) {
        if (theme.dark()) {
            return switch (type) {
                case GENERALIZATION -> "[#6C8CFF,plain]";
                case REALIZATION -> "[#4CAF50,dashed]";
                case ASSOCIATION -> "[#9E9E9E,plain]";
                case DEPENDENCY -> "[#9E9E9E,dashed]";
            };
        }
        return switch (type) {
            case GENERALIZATION -> "[#000082,plain]";
            case REALIZATION -> "[#008200,dashed]";
            case ASSOCIATION -> "[#595959,plain]";
            case DEPENDENCY -> "[#595959,dashed]";
        };
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `.\mvnw.cmd test -Dtest="PumlExporterTest"`
Expected: PASS（2 个测试）

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/PumlExporter.java src/test/java/com/bingbaihanji/fxdecomplie/ui/uml/PumlExporterTest.java
git commit -m "feat(uml): PlantUML 源码导出 PumlExporter"
```

---

### Task 7: UmlDiagramRenderer（纯 JavaFX 渲染）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramRenderer.java`

渲染器依赖 JavaFX 线程，不写单测；以编译 + 手动运行验证。

- [ ] **Step 1: 实现渲染器**

`UmlDiagramRenderer.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.text.TextAlignment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 纯 JavaFX 渲染 {@link UmlDiagram}：分栏类框（标题 + 包名 + 字段 + 方法）、
 * 按关系类型区分的箭头、Ctrl+滚轮缩放。层级采用入度拓扑分层，继承根在上、子类在下。
 */
public final class UmlDiagramRenderer {

    private static final double MARGIN = 48;
    private static final double LEVEL_GAP = 60;
    private static final double ROW_GAP = 40;
    private static final double MIN_BOX_W = 150;
    private static final double MIN_BOX_H = 60;
    private static final double CHAR_W = 7.0;
    private static final double LINE_H = 16.0;
    private static final double NODE_PAD_W = 36;
    private static final double NODE_PAD_H = 22;
    private static final double WRAP_THRESHOLD = 40;
    private static final double MIN_ZOOM = 0.35;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 0.1;
    private static final double EDGE_ARROW = 9;

    private UmlDiagramRenderer() {
        throw new AssertionError("utility class");
    }

    /** 渲染结果：可交互 ScrollPane + 画布 Pane（用于快照导出） */
    public record UmlView(ScrollPane scrollPane, Pane canvas) {
    }

    public static UmlView create(UmlDiagram diagram, UmlTheme theme) {
        if (diagram == null || diagram.classes().isEmpty()) {
            Pane canvas = new Pane();
            canvas.setStyle("-fx-background-color:" + theme.background() + ";");
            Label label = new Label("无 UML 数据");
            label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
            StackPane center = new StackPane(label);
            center.setStyle("-fx-background-color:" + theme.background() + ";");
            ScrollPane scroll = new ScrollPane(center);
            scroll.setPannable(true);
            return new UmlView(scroll, canvas);
        }

        Map<String, LayoutBox> layout = layout(diagram, theme);
        Bounds bounds = computeBounds(layout);

        Pane edgeLayer = new Pane();
        edgeLayer.setMouseTransparent(true);
        edgeLayer.setPickOnBounds(false);

        Pane nodeLayer = new Pane();
        nodeLayer.setPickOnBounds(false);

        for (UmlRelation r : diagram.relations()) {
            LayoutBox from = layout.get(r.from());
            LayoutBox to = layout.get(r.to());
            if (from == null || to == null || from == to) {
                continue;
            }
            drawRelation(edgeLayer, from, to, r, theme);
        }
        for (LayoutBox lb : layout.values()) {
            nodeLayer.getChildren().add(lb.box());
        }

        Group group = new Group(edgeLayer, nodeLayer);
        Pane canvas = new Pane(group);
        canvas.setStyle("-fx-background-color:" + theme.background() + ";");
        canvas.setMinSize(bounds.width(), bounds.height());
        canvas.setPrefSize(bounds.width(), bounds.height());

        ScrollPane scroll = new ScrollPane(canvas);
        scroll.setPannable(true);
        scroll.setFitToWidth(false);
        scroll.setFitToHeight(false);
        scroll.setStyle("-fx-background:" + theme.background() + ";-fx-background-color:" + theme.background() + ";");
        installZoom(scroll, group, canvas, bounds);
        return new UmlView(scroll, canvas);
    }

    // ======================== 布局 ========================

    private static Map<String, LayoutBox> layout(UmlDiagram diagram, UmlTheme theme) {
        Map<String, Integer> levels = computeLevels(diagram);
        Map<Integer, List<UmlClass>> byLevel = new LinkedHashMap<>();
        for (UmlClass c : diagram.classes()) {
            byLevel.computeIfAbsent(levels.getOrDefault(c.internalName(), 0),
                    key -> new ArrayList<>()).add(c);
        }
        double maxW = 0;
        double maxH = 0;
        for (UmlClass c : diagram.classes()) {
            maxW = Math.max(maxW, boxWidth(c));
            maxH = Math.max(maxH, boxHeight(c));
        }
        double levelStep = maxH + LEVEL_GAP;
        double rowStep = maxW + ROW_GAP;
        int maxLevel = byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        Map<String, LayoutBox> result = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<UmlClass>> entry : byLevel.entrySet()) {
            int level = entry.getKey();
            List<UmlClass> levelClasses = entry.getValue();
            int mid = levelClasses.size() / 2;
            for (int i = 0; i < levelClasses.size(); i++) {
                UmlClass c = levelClasses.get(i);
                double w = boxWidth(c);
                double h = boxHeight(c);
                // 层级垂直向下，同层水平居中排列
                double x = MARGIN + (i - mid) * rowStep + (maxW - w) / 2.0;
                double y = MARGIN + (maxLevel - level) * levelStep;
                Node box = createClassBox(c, theme);
                box.setLayoutX(x);
                box.setLayoutY(y);
                if (box instanceof Region region) {
                    region.setPrefSize(w, h);
                    region.setMinSize(w, h);
                    region.setMaxSize(w, h);
                }
                result.put(c.internalName(), new LayoutBox(box, x, y, w, h));
            }
        }
        return result;
    }

    /** 入度拓扑分层：入度为 0（继承链顶端）在 level 0（画布顶部） */
    private static Map<String, Integer> computeLevels(UmlDiagram diagram) {
        Map<String, Integer> indegree = new HashMap<>();
        Map<String, List<String>> outgoing = new HashMap<>();
        for (UmlClass c : diagram.classes()) {
            indegree.put(c.internalName(), 0);
            outgoing.put(c.internalName(), new ArrayList<>());
        }
        for (UmlRelation r : diagram.relations()) {
            if (!indegree.containsKey(r.from()) || !indegree.containsKey(r.to())) {
                continue;
            }
            outgoing.get(r.from()).add(r.to());
            indegree.compute(r.to(), (k, v) -> v == null ? 1 : v + 1);
        }
        Map<String, Integer> levels = new LinkedHashMap<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        Set<String> queued = new HashSet<>();
        Set<String> processed = new HashSet<>();
        for (UmlClass c : diagram.classes()) {
            if (indegree.getOrDefault(c.internalName(), 0) == 0) {
                queue.add(c.internalName());
                queued.add(c.internalName());
                levels.put(c.internalName(), 0);
            }
        }
        if (queue.isEmpty() && !diagram.classes().isEmpty()) {
            String first = diagram.classes().getFirst().internalName();
            queue.add(first);
            queued.add(first);
            levels.put(first, 0);
        }
        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            if (!processed.add(current)) {
                continue;
            }
            int nextLevel = levels.getOrDefault(current, 0) + 1;
            for (String next : outgoing.getOrDefault(current, List.of())) {
                if (processed.contains(next)) {
                    continue;
                }
                if (nextLevel > levels.getOrDefault(next, -1)) {
                    levels.put(next, nextLevel);
                }
                int degree = indegree.computeIfPresent(next, (k, v) -> Math.max(0, v - 1));
                if (degree == 0 && queued.add(next)) {
                    queue.add(next);
                }
            }
        }
        int fallback = 0;
        for (UmlClass c : diagram.classes()) {
            levels.putIfAbsent(c.internalName(), fallback++ / 8);
        }
        return levels;
    }

    // ======================== 类框渲染 ========================

    private static Node createClassBox(UmlClass cls, UmlTheme theme) {
        VBox box = new VBox();
        box.setStyle("-fx-background-color:" + theme.nodeBackground() + ";"
                + "-fx-border-color:" + theme.nodeBorder() + ";"
                + "-fx-border-width:1;-fx-background-radius:6;-fx-border-radius:6;");

        Label title = new Label(cls.simpleName());
        title.setStyle("-fx-text-fill:" + theme.titleText() + ";-fx-font-size:13px;-fx-font-weight:bold;");
        title.setMaxWidth(Double.MAX_VALUE);
        title.setAlignment(Pos.CENTER);

        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        if (cls.packageName() != null && !cls.packageName().isEmpty()) {
            Label pkg = new Label(cls.packageName());
            pkg.setStyle("-fx-text-fill:" + theme.packageText() + ";-fx-font-size:10px;");
            pkg.setMaxWidth(Double.MAX_VALUE);
            pkg.setAlignment(Pos.CENTER);
            header.getChildren().add(pkg);
        }
        header.setStyle("-fx-background-color:" + headerColor(cls, theme) + ";"
                + "-fx-background-radius:6 6 0 0;-fx-padding:5 8 5 8;");
        box.getChildren().add(header);

        if (cls.parsed()) {
            VBox members = new VBox();
            members.setStyle("-fx-padding:4 8 6 8;");
            boolean hasFields = false;
            for (UmlField f : cls.fields()) {
                members.getChildren().add(fieldLabel(f, theme));
                hasFields = true;
            }
            if (hasFields && !cls.methods().isEmpty()) {
                Region sep = new Region();
                sep.setMaxWidth(Double.MAX_VALUE);
                sep.setStyle("-fx-background-color:" + theme.nodeBorder()
                        + ";-fx-pref-height:1;-fx-min-height:1;-fx-max-height:1;");
                members.getChildren().add(sep);
            }
            for (UmlMethod m : cls.methods()) {
                members.getChildren().add(methodLabel(m, theme));
            }
            box.getChildren().add(members);
        }
        return box;
    }

    private static String headerColor(UmlClass cls, UmlTheme theme) {
        return switch (cls.kind()) {
            case CLASS -> theme.classHeader();
            case INTERFACE -> theme.interfaceHeader();
            case ENUM -> theme.enumHeader();
            case ANNOTATION -> theme.annotationHeader();
        };
    }

    private static Label fieldLabel(UmlField f, UmlTheme theme) {
        String text = f.visibility().symbol() + " " + f.name() + ": " + f.type();
        Label label = new Label(text);
        String style = "-fx-text-fill:" + theme.memberText()
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (f.isStatic()) {
            style += "-fx-underline:true;";
        }
        label.setStyle(style);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    private static Label methodLabel(UmlMethod m, UmlTheme theme) {
        String ret = m.isConstructor() ? "" : ": " + m.returnType();
        String text = m.visibility().symbol() + " " + m.name() + "("
                + String.join(", ", m.params()) + ")" + ret;
        Label label = new Label(text);
        String style = "-fx-text-fill:" + theme.memberText()
                + ";-fx-font-size:11px;-fx-font-family:'Consolas',monospace;";
        if (m.isAbstract()) {
            style += "-fx-font-style:italic;";
        }
        if (m.isStatic()) {
            style += "-fx-underline:true;";
        }
        label.setStyle(style);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setTextAlignment(TextAlignment.LEFT);
        return label;
    }

    // ======================== 尺寸估算 ========================

    private static double boxWidth(UmlClass cls) {
        int longest = 0;
        for (String line : boxTextLines(cls)) {
            longest = Math.max(longest, line.length());
        }
        return Math.max(longest * CHAR_W + NODE_PAD_W, MIN_BOX_W);
    }

    private static double boxHeight(UmlClass cls) {
        int visualLines = 0;
        for (String line : boxTextLines(cls)) {
            visualLines += Math.max(1, (int) Math.ceil(line.length() / (double) WRAP_THRESHOLD));
        }
        return Math.max(visualLines * LINE_H + NODE_PAD_H, MIN_BOX_H);
    }

    private static List<String> boxTextLines(UmlClass cls) {
        List<String> lines = new ArrayList<>();
        lines.add(cls.simpleName());
        if (cls.packageName() != null && !cls.packageName().isEmpty()) {
            lines.add(cls.packageName());
        }
        if (cls.parsed()) {
            for (UmlField f : cls.fields()) {
                lines.add(f.visibility().symbol() + " " + f.name() + ": " + f.type());
            }
            if (!cls.fields().isEmpty() && !cls.methods().isEmpty()) {
                lines.add("-");
            }
            for (UmlMethod m : cls.methods()) {
                lines.add(m.visibility().symbol() + " " + m.name() + "("
                        + String.join(", ", m.params()) + ")"
                        + (m.isConstructor() ? "" : ": " + m.returnType()));
            }
        }
        return lines;
    }

    // ======================== 关系渲染 ========================

    private static void drawRelation(Pane pane, LayoutBox from, LayoutBox to,
                                     UmlRelation r, UmlTheme theme) {
        Point2D fromCenter = from.center();
        Point2D toCenter = to.center();
        Point2D start = borderPoint(from, toCenter);
        Point2D end = borderPoint(to, fromCenter);

        String color = switch (r.type()) {
            case GENERALIZATION -> theme.generalizationColor();
            case REALIZATION -> theme.realizationColor();
            case ASSOCIATION -> theme.associationColor();
            case DEPENDENCY -> theme.dependencyColor();
        };
        boolean dashed = r.type() == RelationType.REALIZATION || r.type() == RelationType.DEPENDENCY;

        Line line = new Line(start.getX(), start.getY(), end.getX(), end.getY());
        line.setStroke(Color.web(color));
        line.setStrokeWidth(1.2);
        if (dashed) {
            line.getStrokeDashArray().addAll(8.0, 4.0);
        }
        pane.getChildren().add(line);

        if (r.type() == RelationType.GENERALIZATION || r.type() == RelationType.REALIZATION) {
            hollowArrow(pane, start, end, color);
        } else if (r.type() == RelationType.DEPENDENCY) {
            filledArrow(pane, start, end, color);
        }

        if (r.toCardinality() != null && !r.toCardinality().isBlank()) {
            double midX = (start.getX() + end.getX()) / 2.0;
            double midY = (start.getY() + end.getY()) / 2.0;
            Label card = new Label(r.toCardinality());
            card.setStyle("-fx-text-fill:" + color + ";-fx-font-size:10px;");
            card.setLayoutX(midX + 4);
            card.setLayoutY(midY - 10);
            pane.getChildren().add(card);
        }
    }

    /** 空心三角（继承/实现箭头） */
    private static void hollowArrow(Pane pane, Point2D start, Point2D end, String color) {
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double arrow = 11;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Polygon tri = new Polygon(
                end.getX(), end.getY(),
                end.getX() - arrow * cos + arrow * 0.5 * sin,
                end.getY() - arrow * sin - arrow * 0.5 * cos,
                end.getX() - arrow * cos - arrow * 0.5 * sin,
                end.getY() - arrow * sin + arrow * 0.5 * cos);
        tri.setFill(Color.TRANSPARENT);
        tri.setStroke(Color.web(color));
        tri.setStrokeWidth(1.2);
        pane.getChildren().add(tri);
    }

    /** 实心小三角（依赖箭头） */
    private static void filledArrow(Pane pane, Point2D start, Point2D end, String color) {
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Polygon tri = new Polygon(
                end.getX(), end.getY(),
                end.getX() - EDGE_ARROW * cos + EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin - EDGE_ARROW * 0.45 * cos,
                end.getX() - EDGE_ARROW * cos - EDGE_ARROW * 0.45 * sin,
                end.getY() - EDGE_ARROW * sin + EDGE_ARROW * 0.45 * cos);
        tri.setFill(Color.web(color));
        pane.getChildren().add(tri);
    }

    /** 射线与节点矩形边框的交点（连线端点截断） */
    private static Point2D borderPoint(LayoutBox node, Point2D target) {
        Point2D center = node.center();
        double dx = target.getX() - center.getX();
        double dy = target.getY() - center.getY();
        if (Math.abs(dx) < 0.0001 && Math.abs(dy) < 0.0001) {
            return center;
        }
        double scaleX = Math.abs(dx) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.width() / 2.0) / Math.abs(dx);
        double scaleY = Math.abs(dy) < 0.0001
                ? Double.POSITIVE_INFINITY : (node.height() / 2.0) / Math.abs(dy);
        double scale = Math.min(scaleX, scaleY);
        return new Point2D(center.getX() + dx * scale, center.getY() + dy * scale);
    }

    // ======================== 交互 ========================

    private static void installZoom(ScrollPane scroll, Group group, Pane canvas, Bounds bounds) {
        final double[] zoom = {1.0};
        scroll.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (!event.isControlDown() && !event.isMetaDown()) {
                return;
            }
            double next = Math.clamp(
                    zoom[0] + (event.getDeltaY() > 0 ? ZOOM_STEP : -ZOOM_STEP),
                    MIN_ZOOM, MAX_ZOOM);
            if (Math.abs(next - zoom[0]) < 0.0001) {
                event.consume();
                return;
            }
            zoom[0] = next;
            group.setScaleX(next);
            group.setScaleY(next);
            canvas.setPrefSize(bounds.width() * next, bounds.height() * next);
            canvas.setMinSize(bounds.width() * next, bounds.height() * next);
            event.consume();
        });
    }

    private static Bounds computeBounds(Map<String, LayoutBox> layout) {
        double maxX = MARGIN * 2;
        double maxY = MARGIN * 2;
        for (LayoutBox lb : layout.values()) {
            maxX = Math.max(maxX, lb.x() + lb.width() + MARGIN);
            maxY = Math.max(maxY, lb.y() + lb.height() + MARGIN);
        }
        return new Bounds(maxX, maxY);
    }

    private record LayoutBox(Node box, double x, double y, double width, double height) {
        Point2D center() {
            return new Point2D(x + width / 2.0, y + height / 2.0);
        }
    }

    private record Bounds(double width, double height) {
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\mvnw.cmd clean compile -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlDiagramRenderer.java
git commit -m "feat(uml): 纯 JavaFX 渲染器 UmlDiagramRenderer（分栏类框 + 分层布局 + 箭头 + 缩放）"
```

---

### Task 8: UmlClassDiagramDialog（弹窗壳）

**Files:**
- Create: `src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassDiagramDialog.java`

- [ ] **Step 1: 实现弹窗**

`UmlClassDiagramDialog.java`：
```java
package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.ui.IconHelper;
import com.bingbaihanji.fxdecomplie.ui.theme.AppTheme;
import com.bingbaihanji.fxdecomplie.util.i18n.I18nUtil;
import com.bingbaihanji.windows.jfx.DefaultWindowTheme;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * UML 类图弹窗：加载态 → 渲染画布，底部工具栏提供亮/暗主题切换、PNG 导出、PlantUML 导出。
 */
public class UmlClassDiagramDialog extends Dialog<Void> {

    private static final Logger log = LoggerFactory.getLogger(UmlClassDiagramDialog.class);

    private static final int DEFAULT_WIDTH = 880;
    private static final int DEFAULT_HEIGHT = 660;

    private final StackPane contentStack = new StackPane();
    private final Label statusLabel = new Label();
    private final ToggleGroup themeGroup = new ToggleGroup();
    private final javafx.stage.Window ownerWindow;

    private UmlDiagram currentDiagram;
    private UmlTheme currentTheme = UmlTheme.DARK;
    private Pane diagramCanvas;

    public UmlClassDiagramDialog(javafx.stage.Window owner, String title) {
        this.ownerWindow = owner;
        if (owner != null) {
            initOwner(owner);
        }
        setTitle(title);
        initModality(Modality.NONE);
        setResizable(true);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().add(ButtonType.CLOSE);
        pane.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        try {
            pane.getStylesheets().add(AppTheme.darkStylesheet());
        } catch (RuntimeException ignored) {
            // 样式加载失败不影响功能
        }

        contentStack.setStyle("-fx-background-color:#1e1e1e;");
        VBox.setVgrow(contentStack, Priority.ALWAYS);

        RadioButton light = new RadioButton(I18nUtil.getString("uml.theme.light"));
        light.setToggleGroup(themeGroup);
        light.setUserData(UmlTheme.LIGHT);
        RadioButton dark = new RadioButton(I18nUtil.getString("uml.theme.dark"));
        dark.setToggleGroup(themeGroup);
        dark.setUserData(UmlTheme.DARK);
        dark.setSelected(true);
        themeGroup.selectedToggleProperty().addListener((obs, oldV, newV) -> {
            if (newV != null && newV.getUserData() instanceof UmlTheme theme) {
                currentTheme = theme;
                render();
            }
        });

        Button exportPng = new Button(I18nUtil.getString("uml.exportPng"));
        exportPng.setOnAction(e -> exportPng());
        Button exportPuml = new Button(I18nUtil.getString("uml.exportPuml"));
        exportPuml.setOnAction(e -> exportPuml());

        statusLabel.setStyle("-fx-text-fill:#808080;-fx-font-size:11px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(6, light, dark, new Separator(), exportPng, exportPuml, spacer, statusLabel);
        bar.setStyle("-fx-padding:4px 8px;-fx-background-color:#252526;");
        bar.setMinHeight(30);
        bar.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(contentStack, bar);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        pane.setContent(root);

        setOnShown(e -> {
            var w = pane.getScene().getWindow();
            if (w != null) {
                DefaultWindowTheme.applyWindowDarkMode(w);
            }
            if (w instanceof javafx.stage.Stage s) {
                IconHelper.setStageIcon(s);
            }
        });

        showLoading(I18nUtil.getString("uml.loading"));
    }

    /** 显示已构建的类图（FX 线程） */
    public void showDiagram(UmlDiagram diagram) {
        runOnFx(() -> {
            currentDiagram = diagram;
            render();
        });
    }

    /** 显示纯文本消息（FX 线程） */
    public void showMessage(String msg) {
        runOnFx(() -> showMsg(msg));
    }

    private void render() {
        if (currentDiagram == null) {
            showMsg(I18nUtil.getString("uml.noData"));
            return;
        }
        try {
            UmlDiagramRenderer.UmlView view = UmlDiagramRenderer.create(currentDiagram, currentTheme);
            diagramCanvas = view.canvas();
            contentStack.getChildren().setAll(view.scrollPane());
            statusLabel.setText("");
        } catch (RuntimeException e) {
            log.warn("UML 类图渲染失败", e);
            showMsg(I18nUtil.getString("uml.renderFailed"));
        }
    }

    private void showLoading(String msg) {
        ProgressIndicator indicator = new ProgressIndicator(36);
        Label label = new Label(msg);
        label.setStyle("-fx-text-fill:#969696;-fx-font-size:13px;");
        VBox box = new VBox(12, indicator, label);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color:#1e1e1e;");
        contentStack.getChildren().setAll(box);
        statusLabel.setText("");
    }

    private void showMsg(String msg) {
        TextArea area = new TextArea(msg == null ? "" : msg);
        area.setEditable(false);
        area.setWrapText(true);
        contentStack.getChildren().setAll(area);
        statusLabel.setText("");
    }

    private void exportPng() {
        if (diagramCanvas == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("uml.exportPng"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG 图片", "*.png"));
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) {
            return;
        }
        try {
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.web(currentTheme.background()));
            WritableImage image = diagramCanvas.snapshot(params, null);
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);
            statusLabel.setText(I18nUtil.getString("uml.export.done", file.getName()));
        } catch (Exception e) {
            log.error("导出 PNG 失败", e);
            statusLabel.setText(I18nUtil.getString("uml.export.failed", e.getMessage()));
        }
    }

    private void exportPuml() {
        if (currentDiagram == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(I18nUtil.getString("uml.exportPuml"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PlantUML 源码", "*.puml"));
        File file = chooser.showSaveDialog(ownerWindow);
        if (file == null) {
            return;
        }
        try {
            Files.writeString(file.toPath(),
                    PumlExporter.buildSource(currentDiagram, currentTheme), StandardCharsets.UTF_8);
            statusLabel.setText(I18nUtil.getString("uml.export.done", file.getName()));
        } catch (Exception e) {
            log.error("导出 .puml 失败", e);
            statusLabel.setText(I18nUtil.getString("uml.export.failed", e.getMessage()));
        }
    }

    private static void runOnFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `.\mvnw.cmd clean compile -DskipTests`
Expected: `BUILD SUCCESS`（若 `uml.export.done` 等 i18n 键缺失仅影响运行时文本，编译不受影响）

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/uml/UmlClassDiagramDialog.java
git commit -m "feat(uml): UML 类图弹窗（主题切换 + PNG/.puml 导出）"
```

---

### Task 9: 集成（菜单 / 控制器 / i18n）

**Files:**
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeActionHandler.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeAreaContextMenu.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/controller/EngineController.java`
- Modify: `src/main/java/com/bingbaihanji/fxdecomplie/controller/MainWindow.java`
- Modify: `src/main/resources/language/language_zh_CN.properties`
- Modify: `src/main/resources/language/language_en.properties`

- [ ] **Step 1: 接口新增方法**

`CodeActionHandler.java` —— 在 `showInheritanceGraph` 声明后新增：
```java
    /** 展示当前类的 UML 类图 */
    void showUmlClassDiagram(CodeViewContext context);
```

- [ ] **Step 2: 代码区右键菜单新增「UML 类图」项**

`CodeAreaContextMenu.java`：

a) 字段区新增（`inheritanceGraphItem` 声明后）：
```java
    private final MenuItem umlDiagramItem;
```

b) 构造器初始化（`inheritanceGraphItem.setOnAction(...)` 后）：
```java
        umlDiagramItem = new MenuItem(I18nUtil.getString("context.umlDiagram"));
        umlDiagramItem.setOnAction(e -> onShowUmlDiagram());
```

c) `getItems().addAll(...)` 中，`inheritanceGraphItem` 后加 `umlDiagramItem`：
```java
        getItems().addAll(
                gotoDeclarationItem,
                inheritanceGraphItem,
                umlDiagramItem,
                methodGraphItem,
                ...
```

d) `refreshState()` 中加禁用逻辑：
```java
        umlDiagramItem.setDisable(!hasClassContext);
```

e) 新增处理器方法（放在 `onShowInheritanceGraph` 后）：
```java
    /** 显示当前类的 UML 类图 */
    private void onShowUmlDiagram() {
        if (actionHandler == null || context == null) {
            return;
        }
        log.info("代码区右键菜单触发查看 UML 类图: {}", context.classInternalName());
        actionHandler.showUmlClassDiagram(context);
    }
```

- [ ] **Step 3: EngineController 新增 showUmlClassDiagram**

`EngineController.java`：

a) imports 新增：
```java
import com.bingbaihanji.fxdecomplie.ui.uml.UmlClassDiagramDialog;
import com.bingbaihanji.fxdecomplie.ui.uml.UmlDiagram;
import com.bingbaihanji.fxdecomplie.ui.uml.UmlDiagramBuilder;
```

b) 在 `showInheritanceGraph` 方法后新增：
```java
    /** 显示当前类的 UML 类图 */
    public void showUmlClassDiagram(CodeViewContext context) {
        if (context == null) {
            return;
        }
        String fullPath = context.classInternalName();
        if (fullPath == null || fullPath.isBlank()) {
            showGraphFailed(fullPath, null);
            return;
        }
        Workspace workspace = context.workspace();
        WorkspaceIndex index = workspace != null && workspace.isIndexReady()
                ? workspace.getIndex()
                : context.workspaceIndex();
        owner.statusBar().setTask(I18nUtil.getString("task.loading"));
        owner.statusBar().setFilePath(I18nUtil.getString("graph.building", fullPath));
        log.info("请求查看 UML 类图: {}", fullPath);

        UmlClassDiagramDialog dialog = new UmlClassDiagramDialog(owner.stage(),
                I18nUtil.getString("context.umlDiagram") + " - " + fullPath);
        dialog.show();
        BackgroundTasks.run("UmlClassDiagram-" + fullPath, () -> {
            try {
                byte[] classBytes = owner.classBytesForContext(context);
                WorkspaceIndex graphIndex = workspace != null
                        ? owner.searchController().awaitWorkspaceIndex(workspace)
                        : index;
                if (graphIndex == WorkspaceIndex.EMPTY && index != null) {
                    graphIndex = index;
                }
                UmlDiagram diagram = UmlDiagramBuilder.build(fullPath, classBytes, graphIndex);
                if (diagram == null || diagram.classes().isEmpty()) {
                    Platform.runLater(() -> {
                        owner.statusBar().clearTask();
                        dialog.showMessage(I18nUtil.getString("uml.noData"));
                    });
                    return;
                }
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    dialog.showDiagram(diagram);
                });
            } catch (Exception e) {
                log.error("UML 类图构建失败: {}", fullPath, e);
                Platform.runLater(() -> {
                    owner.statusBar().clearTask();
                    dialog.showMessage(I18nUtil.getString("uml.renderFailed"));
                });
            }
        });
    }
```

> 说明：`owner.stage()`、`owner.classBytesForContext`、`owner.searchController().awaitWorkspaceIndex` 与 `showInheritanceGraph` 使用方式一致，均已在 MainWindow 提供。

- [ ] **Step 4: MainWindow 实现接口方法**

`MainWindow.java` —— 在 `showInheritanceGraph` override 后新增：
```java
    @Override
    public void showUmlClassDiagram(CodeViewContext context) {
        engineController.showUmlClassDiagram(context);
    }
```

- [ ] **Step 5: 新增 i18n 键**

`language_zh_CN.properties` 末尾追加：
```properties
context.umlDiagram=查看 UML 类图
uml.loading=正在生成 UML 类图...
uml.noData=无 UML 数据
uml.renderFailed=UML 类图渲染失败
uml.exportPng=导出 PNG
uml.exportPuml=导出 PlantUML 源码
uml.export.failed=导出失败: {0}
uml.export.done=已导出: {0}
uml.theme.light=亮色
uml.theme.dark=暗色
```

`language_en.properties` 末尾追加：
```properties
context.umlDiagram=View UML Class Diagram
uml.loading=Building UML class diagram...
uml.noData=No UML data
uml.renderFailed=Failed to render UML class diagram
uml.exportPng=Export PNG
uml.exportPuml=Export PlantUML Source
uml.export.failed=Export failed: {0}
uml.export.done=Exported: {0}
uml.theme.light=Light
uml.theme.dark=Dark
```

- [ ] **Step 6: 编译验证**

Run: `.\mvnw.cmd clean compile -DskipTests`
Expected: `BUILD SUCCESS`

- [ ] **Step 7: 手动运行冒烟**

Run: `.\mvnw.cmd exec:java --open <某个 class 或 jar>`，打开任意类后，在代码区右键 → 「UML 类图」：
- 弹窗加载 → 显示分栏类图，类框含包名/字段/方法
- 亮/暗切换即时生效
- 「导出 PNG」保存成功且图片内容与画布一致
- 「导出 PlantUML 源码」保存 `.puml` 文本

- [ ] **Step 8: Commit**

```bash
git add src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeActionHandler.java src/main/java/com/bingbaihanji/fxdecomplie/ui/code/CodeAreaContextMenu.java src/main/java/com/bingbaihanji/fxdecomplie/controller/EngineController.java src/main/java/com/bingbaihanji/fxdecomplie/controller/MainWindow.java src/main/resources/language/language_zh_CN.properties src/main/resources/language/language_en.properties
git commit -m "feat(uml): 集成代码区右键菜单 + EngineController + i18n"
```

---

### Task 10: 全量测试 + 文档收尾

**Files:**
- Modify: `AGENTS.md`

- [ ] **Step 1: 运行全量测试**

Run: `.\mvnw.cmd test`
Expected: 全部 PASS（原 339 + 新增 13 = 352 个左右）

> 若架构测试（import 扫描）报 `ui/uml/` 违规：确认规则是否禁止 ui 依赖 model/util；`ui/uml/` 仅依赖 `model.WorkspaceIndex`、`util.*`、`bytecode`（无）、ASM、JavaFX，应与既有 `ui/inheritance/` 同级，不应触发。若误报，参照 `docs/architecture-boundaries.md` 调整规则。

- [ ] **Step 2: 更新 AGENTS.md**

在项目结构 `ui/` 列表的 `tree/` 行后新增：
```
│   │   ├── tree/                          # 文件树视图
│   │   ├── uml/                           # UML 类图（ASM 提取 + JavaFX 渲染 + PlantUML 导出）
```

- [ ] **Step 3: Commit**

```bash
git add -f AGENTS.md
git commit -m "docs: AGENTS.md 项目结构补充 ui/uml"
```

---

## Self-Review（对照 Spec）

**Spec 覆盖核对：**

| Spec 章节 | 对应任务 |
|---|---|
| 4. 数据模型 | Task 1 |
| 5.3 UmlTypeNameUtil | Task 2 |
| 5.1 UmlClassExtractor | Task 3 |
| 5.2 UmlDiagramBuilder（BFS 闭包 + 上限） | Task 4 |
| 6.2 UmlTheme | Task 5 |
| 7.2 PlantUML 导出 | Task 6 |
| 6.1 UmlDiagramRenderer | Task 7 |
| 7.1 PNG 导出 / 弹窗 / 主题切换 | Task 8 |
| 8. 集成入口 / 9. 错误处理 | Task 9 |
| 10. 测试 / AGENTS.md | Task 2-6 + 10 |

**占位符扫描：** 无 TBD/TODO；所有代码步骤含完整代码。

**类型一致性：** 模型 records（Task 1）被 Task 3-8 引用，字段名一致（`internalName/simpleName/packageName/kind/isAbstract/parsed/fields/methods/superName/interfaces/annotationNames`）；`UmlDiagram(target, classes, relations)` 一致；`UmlRelation(from, to, type, fromCardinality, toCardinality)` 一致；`UmlTheme` 16 字段在 Task 5/7/8 中一致；`UmlDiagramRenderer.UmlView(scrollPane, canvas)` 在 Task 7/8 中一致。
