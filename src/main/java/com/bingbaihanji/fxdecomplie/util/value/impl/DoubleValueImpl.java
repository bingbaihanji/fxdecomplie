package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.util.value.DoubleValue;
import com.bingbaihanji.fxdecomplie.util.value.IllegalValueException;
import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;

import java.util.OptionalDouble;

/**
 * double 值持有者实现。
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class DoubleValueImpl implements DoubleValue {
    private final OptionalDouble value;

    /**
     * @param value
     * 		要持有的 double 值。
     */
    public DoubleValueImpl(double value) {
        this.value = OptionalDouble.of(value);
    }

    /**
     * 创建一个内容未知的 double 值。
     */
    public DoubleValueImpl() {
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

        DoubleValueImpl other = (DoubleValueImpl) o;

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
        } else if (other instanceof DoubleValue otherDouble) {
            if (value().isPresent() && otherDouble.value().isPresent()) {
                double d = value().getAsDouble();
                double otherD = otherDouble.value().getAsDouble();
                if (d == otherD) {
                    return DoubleValue.of(d);
                }
            }
            return DoubleValue.UNKNOWN;
        }
        throw new IllegalValueException("Cannot merge with: " + other);
    }
}
