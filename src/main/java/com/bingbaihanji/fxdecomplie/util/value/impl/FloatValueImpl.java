package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.util.value.FloatValue;
import com.bingbaihanji.fxdecomplie.util.value.IllegalValueException;
import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;

import java.util.OptionalDouble;

/**
 * Float value holder implementation.
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class FloatValueImpl implements FloatValue {
    private final OptionalDouble value;

    public FloatValueImpl(float value) {
        this.value = OptionalDouble.of(value);
    }

    public FloatValueImpl(double value) {
        this.value = OptionalDouble.of(value);
    }

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
