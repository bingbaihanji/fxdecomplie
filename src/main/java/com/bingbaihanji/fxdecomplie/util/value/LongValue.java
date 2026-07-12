package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.LongValueImpl;
import org.objectweb.asm.Type;

import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * 能够记录精确 long 内容的值
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
     * 		要持有的 long 值
     *
     * @return 持有该精确内容的 long 值
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
     * @return 值的 long 内容若 {@link #hasKnownValue() 未知} 则为空
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
     * 		用于比较的值
     *
     * @return 当已知值等于给定值时返回 {@code true}
     */
    default boolean isEqualTo(long value) {
        return value().isPresent() && value().getAsLong() == value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于给定值时返回 {@code true}
     */
    default boolean isLessThan(long value) {
        return value().isPresent() && value().getAsLong() < value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于或等于给定值时返回 {@code true}
     */
    default boolean isLessThanOrEqual(long value) {
        return value().isPresent() && value().getAsLong() <= value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于给定值时返回 {@code true}
     */
    default boolean isGreaterThan(long value) {
        return value().isPresent() && value().getAsLong() > value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于或等于给定值时返回 {@code true}
     */
    default boolean isGreaterThanOrEqual(long value) {
        return value().isPresent() && value().getAsLong() >= value;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之和；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue add(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() + otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之差；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue sub(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() - otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之积；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）
     */
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


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之商；若任一值未知或除数为 0 则为 {@link #UNKNOWN}
     */
    default LongValue div(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            long otherLiteral = otherValue.getAsLong();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of(value.getAsLong() / otherLiteral);
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位与的结果；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）
     */
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


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位或的结果；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 -1 时结果为 -1）
     */
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


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值按位异或的结果；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue xor(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() ^ otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 比较结果（对应 {@code lcmp} 指令）；若任一值未知则为 {@link IntValue#UNKNOWN}
     */
    default IntValue cmp(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return IntValue.of(Long.compare(value.getAsLong(), otherValue.getAsLong()));
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值取余的结果；若任一值未知或除数为 0 则为 {@link #UNKNOWN}
     */
    default LongValue rem(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            long otherLiteral = otherValue.getAsLong();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of(value.getAsLong() % otherLiteral);
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
    default LongValue shl(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() << otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 左移后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue shl(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() << otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 算术右移后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue shr(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >> otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 算术右移后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue shr(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 逻辑右移（无符号右移）后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue ushr(LongValue other) {
        OptionalLong value = value();
        OptionalLong otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >>> otherValue.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		移位位数
     *
     * @return 逻辑右移（无符号右移）后的值；若任一值未知则为 {@link #UNKNOWN}
     */
    default LongValue ushr(IntValue other) {
        OptionalLong value = value();
        OptionalInt otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsLong() >>> otherValue.getAsInt());
        }
        return UNKNOWN;
    }


    /**
     * @return 取负后的值；若值未知则为 {@link #UNKNOWN}
     */
    default LongValue negate() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return of(-value.getAsLong());
        }
        return UNKNOWN;
    }


    /**
     * @param incr
     * 		要加上的增量
     *
     * @return 加上增量后的值；若值未知则为 {@link #UNKNOWN}
     */
    default LongValue add(long incr) {
        OptionalLong value = value();
        if (value.isPresent()) {
            return of(value.getAsLong() + incr);
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 int 后的值；若值未知则为 {@link IntValue#UNKNOWN}
     */
    default IntValue castInt() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return IntValue.of((int) value.getAsLong());
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @return 转换为 float 后的值；若值未知则为 {@link FloatValue#UNKNOWN}
     */
    default FloatValue castFloat() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return FloatValue.of(value.getAsLong());
        }
        return FloatValue.UNKNOWN;
    }


    /**
     * @return 转换为 double 后的值；若值未知则为 {@link DoubleValue#UNKNOWN}
     */
    default DoubleValue castDouble() {
        OptionalLong value = value();
        if (value.isPresent()) {
            return DoubleValue.of(value.getAsLong());
        }
        return DoubleValue.UNKNOWN;
    }
}
