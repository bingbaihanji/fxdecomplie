package com.bingbaihanji.fxdecomplie.ui.graph;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DotGraphParserTest {

    @Test
    void parsesQuotedInheritanceGraph() {
        String dot = """
                digraph InheritanceGraph {
                    rankdir=BT;
                    node [shape=box, style=filled, fontname="Consolas"];
                    "com/example/Child" [label="Child", fillcolor="#6a9955"];
                    "com/example/Parent" [label="Parent", fillcolor="#569cd6"];
                    "com/example/Child" -> "com/example/Parent";
                }
                """;

        DotGraphParser.DotGraph graph = DotGraphParser.parse(dot);

        assertEquals("BT", graph.rankDir());
        assertEquals(2, graph.nodes().size());
        assertEquals("Child", graph.nodes().get(0).label());
        assertEquals("#6a9955", graph.nodes().get(0).fillColor());
        assertEquals("com/example/Child", graph.edges().get(0).from());
        assertEquals("com/example/Parent", graph.edges().get(0).to());
    }

    @Test
    void parsesMethodGraphWithBareIds() {
        String dot = """
                digraph MethodGraph {
                    rankdir=LR;
                    N0 [label="a()"];
                    N1 [label="b(I)"];
                    N0 -> N1;
                }
                """;

        DotGraphParser.DotGraph graph = DotGraphParser.parse(dot);

        assertEquals("LR", graph.rankDir());
        assertEquals(2, graph.nodes().size());
        assertEquals("a()", graph.nodes().get(0).label());
        assertEquals("N0", graph.edges().get(0).from());
        assertEquals("N1", graph.edges().get(0).to());
    }

    @Test
    void parsesSingleLineGraphLabel() {
        DotGraphParser.DotGraph graph = DotGraphParser.parse("digraph G { label=\"无方法调用数据\" }");

        assertEquals(0, graph.nodes().size());
        assertEquals("无方法调用数据", graph.graphLabel());
    }
}
