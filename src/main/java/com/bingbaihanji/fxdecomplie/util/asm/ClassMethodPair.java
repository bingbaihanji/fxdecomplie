package com.bingbaihanji.fxdecomplie.util.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Pair of a class and field.
 *
 * @param classNode
 * 		Wrapped class.
 * @param methodNode
 * 		Wrapped method.
 */
public record ClassMethodPair(ClassNode classNode,
                              MethodNode methodNode) {
}