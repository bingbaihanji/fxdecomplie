package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.util.value.FloatValue;
import com.bingbaihanji.fxdecomplie.util.value.IllegalValueException;
import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;

import java.util.OptionalDouble;

/**
 * float 值持有者实现
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class FloatValueImpl implements FloatValue {
    private final OptionalDouble value;

    /**
     * @param value
     * 		要持有的 float 值
     */
    public FloatValueImpl(float value) {
        this.value = OptionalDouble.of(value);
    }

    /**
     * @param value
     * 		要持有的值(以 double 形式提供)
     */
    public FloatValueImpl(double value) {
        this.value = OptionalDouble.of(value);
    }

    /**
     * 创建一个内容未知的 float 值
     */
    public FloatValueImpl() {
        this.value = OptionalDouble.empty();
    }


    @Override
    public OptionalDouble value() {
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

        FloatValueImpl other = (FloatValueImpl) o;

        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return type().getInternalName() + ":" + (value.isPresent() ? value.getAsDouble() : "?");
    }


    @Override
    public ReValue mergeWith(ReValue other) throws IllegalValueException {
        if (other == UninitializedValue.UNINITIALIZED_VALUE) {
            return other;
        } else if (other instanceof FloatValue otherFloat) {
            if (value().isPresent() && otherFloat.value().isPresent()) {
                double f = value().getAsDouble();
                double otherF = otherFloat.value().getAsDouble();
                if (f == otherF) {
                    return FloatValue.of((float) f);
                }
            }
            return FloatValue.UNKNOWN;
        }
        throw new IllegalValueException("Cannot merge with: " + other);
    }
}
