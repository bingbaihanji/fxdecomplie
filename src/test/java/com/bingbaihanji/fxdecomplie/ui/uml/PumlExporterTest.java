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
        assertTrue(puml.contains("abstract class \"**AbstractService**"));
        // 泛化:ServiceImpl(具体类) → AbstractService(抽象类),实线空心三角
        assertTrue(puml.contains("-[#000082,plain]-^"));
        // 关联:AbstractService → Order 集合字段 0..*（PlantUML 多重性前有一个空格）
        assertTrue(puml.contains("-[#595959,plain]- \"0..*\""));
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
        sources.put("com.example.External", "package com.example;\npublic class External {}\n");
        sources.put("com.example.UsesExternal",
                "package com.example;\npublic class UsesExternal { private External ext; }\n");
        Map<String, byte[]> all = TestCompile.compile(sources);
        Map<String, byte[]> treeBytes = new HashMap<>(all);
        treeBytes.remove("com/example/External.class");
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(treeBytes));
        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/UsesExternal", all.get("com/example/UsesExternal.class"), index);

        String puml = PumlExporter.buildSource(diagram, UmlTheme.LIGHT);
        // 标记框:单一柔和色 + 空成员体
        assertTrue(puml.contains("class \"**External**\" as ") && puml.contains(" #EDEDED {\n}"));

        // 空图
        assertEquals("@startuml\n@enduml", PumlExporter.buildSource(null, UmlTheme.LIGHT));
    }

    @Test
    void buildSource_rendersAnnotationEdge() {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("com.example.MyAnn", "package com.example;\npublic @interface MyAnn {}\n");
        sources.put("com.example.Annotated",
                "package com.example;\n@MyAnn\npublic class Annotated {}\n");
        Map<String, byte[]> bytes = TestCompile.compile(sources);
        WorkspaceIndex index = WorkspaceIndex.build(TestCompile.treeFromClasses(bytes));

        UmlDiagram diagram = UmlDiagramBuilder.build(
                "com/example/Annotated", bytes.get("com/example/Annotated.class"), index);
        String puml = PumlExporter.buildSource(diagram, UmlTheme.LIGHT);

        assertTrue(puml.contains("annotation \"**MyAnn**"));
        assertTrue(puml.contains("-[#999900,dotted]-"));
    }
}
