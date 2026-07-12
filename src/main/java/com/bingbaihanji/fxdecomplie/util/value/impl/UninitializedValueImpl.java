package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;

/**
 * 未初始化值实现
 *
 * @author Matt Coley
 */
public class UninitializedValueImpl implements UninitializedValue {
    public static final UninitializedValue UNINITIALIZED_VALUE = new UninitializedValueImpl();

    private UninitializedValueImpl() {
    }

    @Override
    public String toString() {
        return ".";
    }


    @Override
    public ReValue mergeWith(ReValue other) {
        return this;
    }
}
