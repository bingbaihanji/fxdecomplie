package com.bingbaihanji.fxdecomplie.util.value.impl;

import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.*;

import org.objectweb.asm.Type;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.IntFunction;

/**
 * Array value holder implementation.
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class ArrayValueImpl implements ArrayValue {
    private final Type type;
    private final Nullness nullness;
    private final OptionalInt length;
    private final List<ReValue> contents;

    /**
     * New array value where we don't know the exact length.
     *
     * @param type
     * 		Array type.
     * @param nullness
     * 		Array reference nullability.
     */
    public ArrayValueImpl(Type type, Nullness nullness) {
        if (type.getSort() != Type.ARRAY) {
            throw new IllegalStateException("Non-array type passed to array-value");
        }
        this.type = type;
        this.nullness = nullness;
        this.length = OptionalInt.empty();
        this.contents = null;
    }

    /**
     * New array value where we know the exact length.
     * <p>
     * Array contents are filled with default {@link ReValue} instances for the element type.
     *
     * @param type
     * 		Array type.
     * @param nullness
     * 		Array reference nullability.
     * @param length
     * 		Length of array.
     */
    public ArrayValueImpl(Type type, Nullness nullness, int length) {
        this(type, nullness, length, new ConstProvider(getSubTypedValue(type, ReValue::ofTypeDefaultValue)));
    }

    /**
     * New array where we know the exact length and want to pre-populate values.
     * <p>
     * Array contents are filled with what is provided by the index-value function.
     *
     * @param type
     * 		Array type.
     * @param nullness
     * 		Array reference nullability.
     * @param length
     * 		Length of array.
     * @param indexValueFunction
     * 		Array index to value function.
     */
    public ArrayValueImpl(Type type, Nullness nullness, int length, IntFunction<ReValue> indexValueFunction) {
        if (type.getSort() != Type.ARRAY) {
            throw new IllegalStateException("Non-array type passed to array-value");
        }
        this.type = type;
        this.nullness = nullness;
        if (length >= 0) {
            this.length = OptionalInt.of(length);
            this.contents = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                contents.add(indexValueFunction.apply(i));
            }
        } else {
            // Array length is negative. So we have two possibilities:
            // - We have a bug in our stack evaluation
            // - We are looking at obfuscated code intentionally trying to throw exceptions
            this.length = OptionalInt.empty();
            this.contents = null;
        }
    }

    private static ReValue getSubTypedValue(Type arrayType, UncheckedFunction<Type, ReValue> function) {
        try {
            if (arrayType.getSort() != Type.ARRAY) {
                throw new IllegalStateException("ArrayValue had non-array type");
            }
            return Objects.requireNonNull(function.apply(Types.undimension(arrayType)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed providing fallback value of array", ex);
        }
    }

    @Override
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    public ArrayValue setValue(int index, ReValue value) {
        if (hasKnownValue()) {
            ArrayValueImpl copy = new ArrayValueImpl(type, nullness, length.getAsInt());
            for (int i = 0; i < contents.size(); i++) {
                ReValue valueAtIndex = i == index ? value : contents.get(i);
                copy.contents.set(i, valueAtIndex);
            }
            return copy;
        }

        // Values not known, so no need to create a copy.
        return this;
    }

    @Override
    public ArrayValue updatedCopyIfContained(ReValue originalValue, ReValue updatedValue) {
        if (hasKnownValue()) {
            for (int i = 0; i < contents.size(); i++) {
                ReValue content = contents.get(i);

                // Case 1: The value is a direct entry in this array.
                if (content == originalValue) {
                    return setValue(i, updatedValue);
                }

                // Case 2: This array is multidimensional and the value is in a nested sub array.
                else if (content instanceof ArrayValue subArray) {
                    ArrayValue updatedSubArray = subArray.updatedCopyIfContained(originalValue, updatedValue);
                    if (subArray != updatedSubArray) {
                        return setValue(i, updatedSubArray);
                    }
                }
            }
        }

        // Not contained, no changes needed.
        return this;
    }

    @Override
    public Type type() {
        return type;
    }

    @Override
    public boolean hasKnownValue() {
        return nullness == Nullness.NOT_NULL
                && length.isPresent()
                && contents != null && contents.stream().allMatch(v -> v.hasKnownValue() || v instanceof ObjectValue ro && ro.isNull());
    }

    @Override
    public ReValue mergeWith(ReValue other) throws IllegalValueException {
        if (other == UninitializedValue.UNINITIALIZED_VALUE) {
            return other;
        } else if (other instanceof ArrayValue otherArray) {
            if (getFirstDimensionLength().isPresent() && otherArray.getFirstDimensionLength().isPresent()
                    && nullness() == otherArray.nullness()
                    && dimensions() == otherArray.dimensions()) {
                int length = getFirstDimensionLength().getAsInt();
                ArrayValueImpl merged = new ArrayValueImpl(type, nullness, length, i -> {
                    try {
                        ReValue value = Objects.requireNonNull(otherArray.getValue(i));
                        return contents.get(i).mergeWith(value);
                    } catch (IllegalValueException ex) {
                        throw new IllegalStateException("Failed merging array contents", ex);
                    }
                });
            }
            return ArrayValue.of(type, nullness.mergeWith(otherArray.nullness()));
        } else if (other instanceof ObjectValue otherObject) {
            return ObjectValue.VAL_OBJECT;
        }
        throw new IllegalValueException("Cannot merge with: " + other);
    }

    @Override
    public Nullness nullness() {
        return nullness;
    }

    @Override
    public OptionalInt getFirstDimensionLength() {
        return length;
    }


    @Override
    public ReValue getValue(int index) {
        if (index < 0 || index >= length.orElse(0)) {
            return getSubTypedValue(type, t -> ReValue.ofType(t, Nullness.UNKNOWN));
        }
        return contents.get(index);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ArrayValueImpl that)) {
            return false;
        }

        if (!type.equals(that.type)) {
            return false;
        }
        if (nullness != that.nullness) {
            return false;
        }
        if (!length.equals(that.length)) {
            return false;
        }
        return Objects.equals(contents, that.contents);
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + nullness.hashCode();
        result = 31 * result + length.hashCode();
        result = 31 * result + (contents != null ? contents.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return type().getInternalName();
    }

    private record ConstProvider(ReValue defaultValue) implements IntFunction<ReValue> {
        @Override
        public ReValue apply(int value) {
            return defaultValue;
        }
    }
}
