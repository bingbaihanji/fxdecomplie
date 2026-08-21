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
