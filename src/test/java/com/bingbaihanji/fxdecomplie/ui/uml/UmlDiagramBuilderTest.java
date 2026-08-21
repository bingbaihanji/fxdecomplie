package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class UmlDiagramBuilderTest {

    @Test
    void build_expandsInheritanceAndImplementation() {
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
                + "    public Order find(String id) { return null; }\n"
                + "    public void save(Order o) { }\n"
                + "}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/OrderServiceImpl", bytes.get("com/example/OrderServiceImpl.class"), index);

        assertNotNull(diagram);
        assertEquals("com/example/OrderServiceImpl", diagram.target());

        var names = diagram.classes().stream().map(UmlClass::internalName).toList();
        assertTrue(names.contains("com/example/OrderServiceImpl"));
        assertTrue(names.contains("com/example/AbstractOrderService"));
        assertTrue(names.contains("com/example/IOrderService"));
        // 关联/依赖类型不再作为节点
        assertFalse(names.contains("com/example/Order"));
        assertFalse(names.contains("java/lang/Object"));
        assertFalse(names.contains("java/lang/String"));
        assertFalse(names.contains("java/util/List"));

        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/OrderServiceImpl", "com/example/AbstractOrderService",
                        RelationType.GENERALIZATION, null, null)));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/IOrderService",
                        RelationType.REALIZATION, null, null)));
        assertFalse(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/IOrderService",
                        RelationType.GENERALIZATION, null, null)));
    }

    @Test
    void build_includesDirectSubclassesAndImplementors() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.Base", "package com.example;\npublic class Base {}\n");
        sources.put("com.example.ChildA", "package com.example;\npublic class ChildA extends Base {}\n");
        sources.put("com.example.ChildB", "package com.example;\npublic class ChildB extends Base {}\n");
        sources.put("com.example.IFace", "package com.example;\npublic interface IFace {}\n");
        sources.put("com.example.Impl", "package com.example;\npublic class Impl implements IFace {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Base", bytes.get("com/example/Base.class"), index);

        assertNotNull(diagram);
        var names = diagram.classes().stream().map(UmlClass::internalName).toList();
        assertTrue(names.contains("com/example/Base"));
        assertTrue(names.contains("com/example/ChildA"));
        assertTrue(names.contains("com/example/ChildB"));
        assertFalse(names.contains("com/example/Impl"));
        assertFalse(names.contains("com/example/IFace"));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/ChildA", "com/example/Base", RelationType.GENERALIZATION, null, null)));
    }

    @Test
    void build_unresolvableSuperclassBecomesMarker() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.MissingBase", "package com.example;\npublic class MissingBase {}\n");
        sources.put("com.example.Child", "package com.example;\npublic class Child extends MissingBase {}\n");
        Map<String, byte[]> all = TestCompile.compile(sources);
        Map<String, byte[]> treeBytes = new HashMap<>(all);
        treeBytes.remove("com/example/MissingBase.class");
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(treeBytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Child", all.get("com/example/Child.class"), index);

        assertNotNull(diagram);
        UmlClass base = diagram.classes().stream()
                .filter(c -> c.internalName().equals("com/example/MissingBase"))
                .findFirst().orElseThrow();
        assertFalse(base.parsed());
        assertTrue(base.fields().isEmpty());
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/Child", "com/example/MissingBase",
                        RelationType.GENERALIZATION, null, null)));
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
            String name = "Child" + i;
            sources.put("com.example." + name, "package com.example;\npublic class " + name + " extends Base {}\n");
        }
        sources.put("com.example.Base", "package com.example;\npublic class Base {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Base", bytes.get("com/example/Base.class"), index);

        assertNotNull(diagram);
        // Base + 前 99 个子类 = 100(被 MAX_CLASSES 截断)
        assertEquals(UmlDiagramBuilder.MAX_CLASSES, diagram.classes().size());
        assertTrue(diagram.classes().stream().anyMatch(c -> c.internalName().equals("com/example/Base")));
    }

    @Test
    void build_stopsExpandingAtMaxDepth() {
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 26;
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
        // 深度 0..20 显示,共 21 个节点;A21 及更深的类不再展开
        assertEquals(21, diagram.classes().size());
        assertFalse(diagram.classes().stream().anyMatch(c -> c.internalName().equals("com/example/A21")));
    }
}
