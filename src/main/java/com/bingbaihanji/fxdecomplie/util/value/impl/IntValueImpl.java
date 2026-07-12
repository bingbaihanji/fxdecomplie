package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.util.value.IllegalValueException;
import com.bingbaihanji.fxdecomplie.util.value.IntValue;
import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;

import java.util.OptionalInt;

/**
 * 整数值持有者实现
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class IntValueImpl implements IntValue {
    private final OptionalInt value;

    /**
     * 创建一个内容未知的整数值
     */
    public IntValueImpl() {
        this.value = OptionalInt.empty();
    }

    /**
     * @param value
     * 		要持有的整数值
     */
    public IntValueImpl(int value) {
        this.value = OptionalInt.of(value);
    }


    @Override
    public OptionalInt value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        IntValueImpl other = (IntValueImpl) o;

        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return type().getInternalName() + ":" + (value.isPresent() ? value.getAsInt() : "?");
    }


    @Override
    public ReValue mergeWith(ReValue other) throws IllegalValueException {
        if (other == UninitializedValue.UNINITIALIZED_VALUE) {
            return other;
        } else if (other instanceof IntValue otherInt) {
            if (value().isPresent() && otherInt.value().isPresent()) {
                int i = value().getAsInt();
                int otherI = otherInt.value().getAsInt();
                if (i == otherI) {
                    return IntValue.of(i);
                }
            }
            return IntValue.UNKNOWN;
        }
        throw new IllegalValueException("Cannot merge with: " + other);
    }
}
