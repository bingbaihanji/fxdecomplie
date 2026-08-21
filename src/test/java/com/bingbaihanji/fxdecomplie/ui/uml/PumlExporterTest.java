package com.bingbaihanji.fxdecomplie.ui.uml;

import com.bingbaihanji.fxdecomplie.model.WorkspaceIndex;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
        assertTrue(puml.contains("abstract class \"<<abstract>>\\n**AbstractService**"));
        assertTrue(puml.contains("-[#000082,plain]-^"));
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

    @Test
    void buildSource_markerClassAndEmptyDiagram() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.MissingBase", "package com.example;\npublic class MissingBase {}\n");
        sources.put("com.example.Child", "package com.example;\npublic class Child extends MissingBase {}\n");
        Map<String, byte[]> all = TestCompile.compile(sources);
        Map<String, byte[]> treeBytes = new HashMap<>(all);
        treeBytes.remove("com/example/MissingBase.class");
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(treeBytes));
        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Child", all.get("com/example/Child.class"), index);

        String puml = PumlExporter.buildSource(diagram, UmlTheme.LIGHT);
        // 标记框:单一柔和色 + 空成员体
        assertTrue(puml.contains("class \"**MissingBase**\" as ") && puml.contains(" #EDEDED {\n}"));

        // 空图
        assertEquals("@startuml\n@enduml", PumlExporter.buildSource(null, UmlTheme.LIGHT));
    }
}
