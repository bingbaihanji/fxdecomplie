package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.IntValueImpl;
import org.objectweb.asm.Type;

import java.util.OptionalInt;

/**
 * 能够记录精确整数内容的值
 *
 * @author Matt Coley
 */
public non-sealed interface IntValue extends ReValue {
    IntValue UNKNOWN = new IntValueImpl();
    IntValue VAL_MAX = new IntValueImpl(Integer.MAX_VALUE);
    IntValue VAL_MIN = new IntValueImpl(Integer.MIN_VALUE);
    IntValue VAL_M1 = new IntValueImpl(-1);
    IntValue VAL_0 = new IntValueImpl(0);
    IntValue VAL_1 = new IntValueImpl(1);
    IntValue VAL_2 = new IntValueImpl(2);
    IntValue VAL_3 = new IntValueImpl(3);
    IntValue VAL_4 = new IntValueImpl(4);
    IntValue VAL_5 = new IntValueImpl(5);

    /**
     * @param value
     * 		要持有的整数值
     *
     * @return 持有该精确内容的整数值
     */

    static IntValue of(int value) {
        return switch (value) {
            case Integer.MAX_VALUE -> VAL_MAX;
            case Integer.MIN_VALUE -> VAL_MIN;
            case -1 -> VAL_M1;
            case 0 -> VAL_0;
            case 1 -> VAL_1;
            case 2 -> VAL_2;
            case 3 -> VAL_3;
            case 4 -> VAL_4;
            case 5 -> VAL_5;
            default -> new IntValueImpl(value);
        };
    }

    /**
     * @return 值的整数内容若 {@link #hasKnownValue() 未知} 则为空
     */

    OptionalInt value();

    @Override
    default boolean hasKnownValue() {
        return value().isPresent();
    }


    @Override
    default Type type() {
        return Type.INT_TYPE;
    }

    @Override
    default int getSize() {
        return 1;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值等于给定值时返回 {@code true}
     */
    default boolean isEqualTo(int value) {
        return value().isPresent() && value().getAsInt() == value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值等于给定值时返回 {@code true}
     */
    default boolean isEqualTo(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() == otherValue.value().getAsInt();
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值不等于给定值时返回 {@code true}
     */
    default boolean isNotEqualTo(int value) {
        return value().isPresent() && value().getAsInt() != value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值不等于给定值时返回 {@code true}
     */
    default boolean isNotEqualTo(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() != otherValue.value().getAsInt();
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于给定值时返回 {@code true}
     */
    default boolean isLessThan(int value) {
        return value().isPresent() && value().getAsInt() < value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值小于给定值时返回 {@code true}
     */
    default boolean isLessThan(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() < otherValue.value().getAsInt();
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于或等于给定值时返回 {@code true}
     */
    default boolean isLessThanOrEqual(int value) {
        return value().isPresent() && value().getAsInt() <= value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值小于或等于给定值时返回 {@code true}
     */
    default boolean isLessThanOrEqual(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() <= otherValue.value().getAsInt();
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于给定值时返回 {@code true}
     */
    default boolean isGreaterThan(int value) {
        return value().isPresent() && value().getAsInt() > value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值大于给定值时返回 {@code true}
     */
    default boolean isGreaterThan(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() > otherValue.value().getAsInt();
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于或等于给定值时返回 {@code true}
     */
    default boolean isGreaterThanOrEqual(int value) {
        return value().isPresent() && value().getAsInt() >= value;
    }

    /**
     * @param otherValue
     * 		用于比较的值
     *
     * @return 当已知值大于或等于给定值时返回 {@code true}
     */
    default boolean isGreaterThanOrEqual(IntValue otherValue) {
        return value().isPresent() && otherValue.value().isPresent()
                && value().getAsInt() >= otherValue.value().getAsInt();
    }


    /**
     * @param incr
     * 		要加上的增量
     *
     * @return 加上增量后的值；若值未知则为 {@link #UNKNOWN}
     */
    default IntValue add(int incr) {
        OptionalInt value = value();
        if (value.isPresent()) {
            return of(value.getAsInt() + incr);
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之和；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue add(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() + otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之差；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue sub(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() - otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之积；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）
     */
    default IntValue mul(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() * otherValue.getAsInt());
        }
        if (isEqualTo(0) || other.isEqualTo(0)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之商；若任一值未知或除数为 0 则为 {@link #UNKNOWN}
     */
    default IntValue div(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            int otherLiteral = otherValue.getAsInt();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of(value.getAsInt() / otherLiteral);
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位与的结果；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）
     */
    default IntValue and(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() & otherValue.getAsInt());
        }
        if (isEqualTo(0) || other.isEqualTo(0)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位或的结果；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 -1 时结果为 -1）
     */
    default IntValue or(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() | otherValue.getAsInt());
        }
        if (isEqualTo(-1) || other.isEqualTo(-1)) {
            return VAL_M1;
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位异或的结果；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue xor(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() ^ otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值取余的结果；若任一值未知或除数为 0 则为 {@link #UNKNOWN}
     */
    default IntValue rem(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            int otherLiteral = otherValue.getAsInt();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of(value.getAsInt() % otherLiteral);
        }
        if (other.isEqualTo(1)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 左移后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue shl(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() << otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 算术右移后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue shr(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() >> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 逻辑右移（无符号右移）后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default IntValue ushr(IntValue other) {
        OptionalInt value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsInt() >>> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 取负后的值；若值未知则为 {@link #UNKNOWN}
     */
    default IntValue negate() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return of(-value.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 byte 后的值；若值未知则为 {@link #UNKNOWN}
     */
    default IntValue castByte() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return of((byte) value.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 char 后的值；若值未知则为 {@link #UNKNOWN}
     */
    default IntValue castChar() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return of((char) value.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 short 后的值；若值未知则为 {@link #UNKNOWN}
     */
    default IntValue castShort() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return of((short) value.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 float 后的值；若值未知则为 {@link FloatValue#UNKNOWN}
     */
    default FloatValue castFloat() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return FloatValue.of(value.getAsInt());
        }
        return FloatValue.UNKNOWN;
    }


    /**
     * @return 转换为 double 后的值；若值未知则为 {@link DoubleValue#UNKNOWN}
     */
    default DoubleValue castDouble() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return DoubleValue.of(value.getAsInt());
        }
        return DoubleValue.UNKNOWN;
    }


    /**
     * @return 转换为 long 后的值；若值未知则为 {@link LongValue#UNKNOWN}
     */
    default LongValue castLong() {
        OptionalInt value = value();
        if (value.isPresent()) {
            return LongValue.of(value.getAsInt());
        }
        return LongValue.UNKNOWN;
    }
}
