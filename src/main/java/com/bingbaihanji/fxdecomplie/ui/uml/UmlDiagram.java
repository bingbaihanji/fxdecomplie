package com.bingbaihanji.fxdecomplie.ui.uml;

import java.util.List;

/** 完整的 UML 类图 */
public record UmlDiagram(String target, List<UmlClass> classes, List<UmlRelation> relations) {
}
