package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Lookup for static field values.
 *
 * @author Matt Coley
 */
public interface GetStaticLookup {
    /**
     * @param field
     * 		Field reference.
     *
     * @return Value representing the field.
     */

    ReValue get(FieldInsnNode field);

    /**
     * @param field
     * 		Field reference.
     *
     * @return {@code true} when this lookup can provide a value via {@link #get(FieldInsnNode)}.
     */
    boolean hasLookup(FieldInsnNode field);
}
