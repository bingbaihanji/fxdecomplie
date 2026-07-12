package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.UninitializedValueImpl;
import org.objectweb.asm.Type;

/**
 * 表示尚未初始化或尚未使用的槽位的值。
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
