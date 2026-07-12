package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.value.impl.StringValueImpl;

import java.util.Optional;

/**
 * 能够记录字符串精确内容的值。
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
     * @return 值的字符串内容。若 {@link #hasKnownValue() 未知} 则为空。
     */

    Optional<String> getText();

    /**
     * @param otherValue
     * 		用于比较的值。
     *
     * @return 当已知值等于给定值时返回 {@code true}。
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
     * 		用于比较的值。
     *
     * @return 当已知值不等于给定值时返回 {@code true}。
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
