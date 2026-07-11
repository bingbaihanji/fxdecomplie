package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.util.value.ReValue;

import org.objectweb.asm.tree.FieldInsnNode;

/**
 * Lookup for context-bound field values.
 *
 * @author Matt Coley
 */
public interface GetFieldLookup {
    /**
     * @param field
     * 		Field reference.
     * @param context
     * 		Class context the field resides within.
     * 		Has a {@link ReValue#hasKnownValue() known value}.
     *
     * @return Value representing the field.
     */

    ReValue get(FieldInsnNode field, ReValue context);

    /**
     * @param field
     * 		Field reference.
     *
     * @return {@code true} when this lookup can provide a value via {@link #get(FieldInsnNode, ReValue)}.
     */
    boolean hasLookup(FieldInsnNode field);
}
