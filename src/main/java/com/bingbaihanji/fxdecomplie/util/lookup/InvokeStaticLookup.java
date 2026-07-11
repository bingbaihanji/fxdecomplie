package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;

import org.objectweb.asm.tree.MethodInsnNode;

import java.util.List;

/**
 * Lookup for static method return values.
 *
 * @author Matt Coley
 */
public interface InvokeStaticLookup {
    /**
     * @param method
     * 		Method reference.
     * @param values
     * 		Argument values to the method.
     * 		All items {@link ReValue#hasKnownValue() have known values}.
     *
     * @return Value representing the return value of the method.
     */

    ReValue get(MethodInsnNode method, List<? extends ReValue> values);

    /**
     * @param method
     * 		Method reference.
     *
     * @return {@code true} when this lookup can provide a value via {@link #get(MethodInsnNode, List)}.
     */
    boolean hasLookup(MethodInsnNode method);
}
