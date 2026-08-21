package com.bingbaihanji.fxdecomplie.ui.uml;

/** 两个类节点之间的有向关系 */
public record UmlRelation(String from, String to, RelationType type,
                          String fromCardinality, String toCardinality) {
}
