package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.LongValueImpl;
import org.objectweb.asm.Type;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * Value capable of recording exact long content.
 *
 * @author Matt Coley
 */
public non-sealed interface LongValue extends ReValue {
    LongValue UNKNOWN = new LongValueImpl();
    LongValue VAL_MAX = new LongValueImpl(Long.MAX_VALUE);
    LongValue VAL_MIN = new LongValueImpl(Long.MIN_VALUE);
    LongValue VAL_M1 = new LongValueImpl(-1);
    LongValue VAL_0 = new LongValueImpl(0);
    LongValue VAL_1 = new LongValueImpl(1);

    /**
     * @param value
     * 		Long value to hold.
     *
     * @return Long value holding the exact content.
     */

    static LongValue of(long value) {
        if (value == 0) {
            return VAL_0;
        } else if (value == 1) {
            return VAL_1;
        } else if (value == -1) {
            return VAL_M1;
        } else if (value == Long.MAX_VALUE) {
            return VAL_MAX;
        } else if (value == Long.MIN_VALUE) {
            return VAL_MIN;
        }
        return new LongValueImpl(value);
    }

    /**
     * @return Long content of value. Empty if {@link #hasKnownValue() not known}.
     */

    OptionalLong value();

    @Override
    default boolean hasKnownValue() {
        return value().isPresent();
    }


    @Override
    default Type type() {
        return Type.LONG_TYPE;
    }

    @Override
    default int getSize() {
        return 2;
    }

    /**
     * @param value
     * 		Value to check against.
     *
     * @return {@code true} when the known value is equal to the given value.
     */
    default boolean isEqualTo(long value) {
        return value().isPresent() && value().getAsLong() == value;
    }

    /**
     * @param value
     * 		Value to check against.
     *
     * @return {@code true} when the known value is less than the given value.
     */
    default boolean isLessThan(long value) {
        return value().isPresent() && value().getAsLong() < value;
    }

    /**
     * @param value
     * 		Value to check against.
     *
     * @return {@code true} when the known value is less than or equal to the given value.
     */
    default boolean isLessThanOrEqual(long value) {
        return value().isPresent() && value().getAsLong() <= value;
    }

    /**
     * @param value
     * 		Value to check against.
     *
     * @return {@code true} when the known value is greater than the given value.
     */
    default boolean isGreaterThan(long value) {
        return value().isPresent() && value().getAsLong() > value;
    }

    /**
     * @param value
     * 		Value to check against.
     *
     * @return {@code true} when the known value is greater than or equal to the given value.
     */
    default boolean isGreaterThanOrEqual(long value) {
        return value().isPresent() && value().getAsLong() >= value;
    }


    default LongValue add(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() + otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue sub(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() - otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue mul(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() * otherValue.getAsLong());
        }
        if (isEqualTo(0) || other.isEqualTo(0)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    default LongValue div(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            long otherLiteral = otherValue.getAsLong();
            if (otherLiteral == 0) {
                return UNKNOWN; // We'll just pretend this works
            }
            return of(value.getAsLong() / otherLiteral);
        }
        return UNKNOWN;
    }


    default LongValue and(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() & otherValue.getAsLong());
        }
        if (isEqualTo(0) || other.isEqualTo(0)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    default LongValue or(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() | otherValue.getAsLong());
        }
        if (isEqualTo(-1) || other.isEqualTo(-1)) {
            return VAL_M1;
        }
        return UNKNOWN;
    }


    default LongValue xor(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() ^ otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default IntValue cmp(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return IntValue.of(Long.compare(value.getAsLong(), otherValue.getAsLong()));
        }
        return IntValue.UNKNOWN;
    }


    default LongValue rem(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            long otherLiteral = otherValue.getAsLong();
            if (otherLiteral == 0) {
                return UNKNOWN; // We'll just pretend this works
            }
            return of(value.getAsLong() % otherLiteral);
        }
        if (other.isEqualTo(1)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    default LongValue shl(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() << otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue shl(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() << otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    default LongValue shr(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >> otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue shr(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    default LongValue ushr(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >>> otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue ushr(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >>> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    default LongValue negate() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return of(-value.getAsLong());
        }
        return UNKNOWN;
    }


    default LongValue add(long incr) {
        OptionalLong value = value();
        if (value.isPresent()) {
            return of(value.getAsLong() + incr);
        }
        return UNKNOWN;
    }


    default IntValue castInt() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return IntValue.of((int) value.getAsLong());
        }
        return IntValue.UNKNOWN;
    }


    default FloatValue castFloat() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return FloatValue.of(value.getAsLong());
        }
        return FloatValue.UNKNOWN;
    }


    default DoubleValue castDouble() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return DoubleValue.of(value.getAsLong());
        }
        return DoubleValue.UNKNOWN;
    }
}
