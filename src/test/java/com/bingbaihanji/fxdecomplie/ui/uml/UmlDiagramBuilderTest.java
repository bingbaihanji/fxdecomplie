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
                + "    public Order find(String id) { return null; }\n"
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
        assertTrue(names.contains("com/example/Order"));
        assertFalse(names.contains("java/lang/Object"));
        assertFalse(names.contains("java/lang/String"));
        assertFalse(names.contains("java/util/List"));

        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/OrderServiceImpl", "com/example/AbstractOrderService",
                        RelationType.GENERALIZATION, null, null)));
        assertTrue(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/IOrderService",
                        RelationType.REALIZATION, null, null)));
        // 反向展开不应产生实线继承边指向接口
        assertFalse(diagram.relations().contains(
                new UmlRelation("com/example/AbstractOrderService", "com/example/IOrderService",
                        RelationType.GENERALIZATION, null, null)));
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
        StringBuilder hub = new StringBuilder("package com.example;\npublic class Hub {\n");
        for (int i = 0; i < total; i++) {
            sources.put("com.example.Dep" + i, "package com.example;\npublic class Dep" + i + " {}\n");
            hub.append("    private Dep").append(i).append(" d").append(i).append(";\n");
        }
        hub.append("}\n");
        sources.put("com.example.Hub", hub.toString());

        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Hub", bytes.get("com/example/Hub.class"), index);

        assertNotNull(diagram);
        // Hub 的 105 个字段类型全部入队,但节点上限截断为 MAX_CLASSES
        assertEquals(UmlDiagramBuilder.MAX_CLASSES, diagram.classes().size());
        assertTrue(diagram.classes().stream().anyMatch(c -> c.internalName().equals("com/example/Hub")));
    }

    @Test
    void build_stopsExpandingAtMaxDepth() {
        Map<String, String> sources = new LinkedHashMap<>();
        int total = 26; // A0..A25, 深度超过 MAX_DEPTH=20
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
