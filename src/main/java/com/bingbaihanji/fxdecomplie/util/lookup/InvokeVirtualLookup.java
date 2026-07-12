package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;

import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

/**
 * 用于查找绑定上下文的方法返回值。
 *
 * @author Matt Coley
 */
public interface InvokeVirtualLookup {
    /**
     * @param method
     * 		方法引用。
     * @param context
     * 		方法所在的类上下文。
     * 		具有{@link ReValue#hasKnownValue() 已知值}。
     * @param values
     * 		方法的参数值。
     * 		所有项均{@link ReValue#hasKnownValue() 具有已知值}。
     *
     * @return 表示该方法返回值的值。
     */

    ReValue get(MethodInsnNode method, ReValue context, List<? extends ReValue> values);

    /**
     * @param method
     * 		方法引用。
     *
     * @return 当此查找能够通过 {@link #get(MethodInsnNode, ReValue, List)} 提供值时返回 {@code true}。
     */
    boolean hasLookup(MethodInsnNode method);
}
