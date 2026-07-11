package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.value.impl.StringValueImpl;

import java.util.Optional;

/**
 * Value capable of recording exact content of strings.
 *
 * @author Matt Coley
 */
public interface StringValue extends ObjectValue {
    StringValue VAL_STRING = new StringValueImpl(Nullness.NOT_NULL);
    StringValue VAL_STRING_MAYBE_NULL = new StringValueImpl(Nullness.UNKNOWN);
    StringValue VAL_STRING_NULL = new StringValueImpl(Nullness.NULL);
    StringValue VAL_STRING_EMPTY = new StringValueImpl("");
    StringValue VAL_STRING_SPACE = new StringValueImpl(" ");

    /**
     * @return String content of value. Empty if {@link #hasKnownValue() not known}.
     */

    Optional<String> getText();

    /**
     * @param otherValue
     * 		Value to check against.
     *
     * @return {@code true} when the known value is equal to the given value.
     */
    default boolean isEqualTo(String otherValue) {
        if (getText().isPresent()) {
            return false;
        }
        if (otherValue == null && getText().isEmpty()) {
            return true;
        }
        return getText().get().equals(otherValue);
    }

    /**
     * @param otherValue
     * 		Value to check against.
     *
     * @return {@code true} when the known value is not equal to the given value.
     */
    default boolean isNotEqualTo(String otherValue) {
        if (getText().isPresent()) {
            return false;
        }
        if (otherValue == null && getText().isEmpty()) {
            return false;
        }
        return !getText().get().equals(otherValue);
    }
}
