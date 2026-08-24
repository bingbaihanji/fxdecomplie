package com.bingbaihanji.fxdecomplie.ui.uml;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        assertTrue(cls.methods().stream().anyMatch(m -> m.isConstructor() && m.name().equals("Person")));
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
        assertEquals("java/lang/Enum", enm.superName());
        assertTrue(enm.fields().stream().noneMatch(f -> f.name().equals("$VALUES")));
        assertTrue(enm.methods().stream().noneMatch(m -> m.name().equals("<clinit>")));
        assertTrue(enm.methods().stream().noneMatch(m -> m.name().equals("values") || m.name().equals("valueOf")));

        UmlClass ann = UmlClassExtractor.extract("com/example/MyAnn", bytes.get("com/example/MyAnn.class"));
        assertEquals(ClassKind.ANNOTATION, ann.kind());
    }

    @Test
    void extract_record() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.UserRecord",
                "package com.example;\npublic record UserRecord(String id, int age) {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);

        UmlClass record = UmlClassExtractor.extract("com/example/UserRecord", bytes.get("com/example/UserRecord.class"));
        assertNotNull(record);
        assertEquals(ClassKind.RECORD, record.kind());
        assertEquals("java/lang/Record", record.superName());
        assertTrue(record.fields().stream().anyMatch(f -> f.name().equals("id")
                && f.type().equals("java.lang.String")));
        assertTrue(record.methods().stream().anyMatch(m -> m.name().equals("id")
                && m.returnType().equals("java.lang.String")));
    }

    @Test
    void extract_genericTypeVariableMembers() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.Box",
                "package com.example;\npublic class Box<T> {\n"
                        + "    private T value;\n"
                        + "    public void set(T v) { this.value = v; }\n"
                        + "    public T get() { return value; }\n"
                        + "}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);

        UmlClass cls = UmlClassExtractor.extract("com/example/Box", bytes.get("com/example/Box.class"));
        assertNotNull(cls);
        assertTrue(cls.fields().stream().anyMatch(f -> f.name().equals("value") && f.type().equals("T")));
        assertTrue(cls.methods().stream().anyMatch(m -> m.name().equals("set")
                && m.params().equals(java.util.List.of("T"))));
        assertTrue(cls.methods().stream().anyMatch(m -> m.name().equals("get")
                && m.returnType().equals("T")));
    }

    @Test
    void extract_nullOrInvalidBytesReturnsNull() {
        assertNull(UmlClassExtractor.extract("com/example/X", null));
        assertNull(UmlClassExtractor.extract("com/example/X", new byte[0]));
        assertNull(UmlClassExtractor.extract("com/example/X", new byte[]{1, 2, 3}));
    }
}
