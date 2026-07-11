package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.UninitializedValueImpl;
import org.objectweb.asm.Type;

/**
 * Value representing a slot that has not been initialized or used yet.
 *
 * @author Matt Coley
 */
public non-sealed interface UninitializedValue extends ReValue {
    UninitializedValue UNINITIALIZED_VALUE = UninitializedValueImpl.UNINITIALIZED_VALUE;

    @Override
    default boolean hasKnownValue() {
        return false;
    }


    @Override
    default Type type() {
        return null;
    }

    @Override
    default int getSize() {
        return 1;
    }
}
