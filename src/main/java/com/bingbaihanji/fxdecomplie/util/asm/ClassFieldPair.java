package com.bingbaihanji.fxdecomplie.util.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

/**
 * Pair of a class and field.
 *
 * @param classNode
 * 		Wrapped class.
 * @param fieldNode
 * 		Wrapped field.
 */
public record ClassFieldPair(ClassNode classNode,
                             FieldNode fieldNode) {
}