package com.bingbaihanji.fxdecomplie.util.asm;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * 类与方法的组合对。
 *
 * @param classNode
 * 		所包装的类。
 * @param methodNode
 * 		所包装的方法。
 */
public record ClassMethodPair(ClassNode classNode,
                              MethodNode methodNode) {
}