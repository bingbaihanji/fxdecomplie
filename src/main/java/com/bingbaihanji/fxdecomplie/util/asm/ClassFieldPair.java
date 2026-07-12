package com.bingbaihanji.fxdecomplie.util.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * 类与字段的组合对
 *
 * @param classNode
 * 		所包装的类
 * @param fieldNode
 * 		所包装的字段
 */
public record ClassFieldPair(ClassNode classNode,
                             FieldNode fieldNode) {
}