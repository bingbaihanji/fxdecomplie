package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.DoubleValueImpl;
import org.objectweb.asm.Type;

import java.util.OptionalDouble;

/**
 * 能够记录精确的双精度浮点内容的值。
 *
 * @author Matt Coley
 */
public non-sealed interface DoubleValue extends ReValue {
    DoubleValue UNKNOWN = new DoubleValueImpl();
    DoubleValue VAL_MAX = new DoubleValueImpl(Double.MAX_VALUE);
    DoubleValue VAL_MIN = new DoubleValueImpl(Double.MIN_VALUE);
    DoubleValue VAL_M1 = new DoubleValueImpl(-1);
    DoubleValue VAL_0 = new DoubleValueImpl(0);
    DoubleValue VAL_1 = new DoubleValueImpl(1);

    /**
     * @param value
     * 		要持有的 double 值。
     *
     * @return 持有该精确内容的 double 值。
     */

    static DoubleValue of(double value) {
        if (value == 0) {
            return VAL_0;
        } else if (value == 1) {
            return VAL_1;
        } else if (value == -1) {
            return VAL_M1;
        } else if (value == Double.MAX_VALUE) {
            return VAL_MAX;
        } else if (value == Double.MIN_VALUE) {
            return VAL_MIN;
        }
        return new DoubleValueImpl(value);
    }

    /**
     * @return 值的 double 内容。若 {@link #hasKnownValue() 未知} 则为空。
     */

    OptionalDouble value();

    @Override
    default boolean hasKnownValue() {
        return value().isPresent();
    }


    @Override
    default Type type() {
        return Type.DOUBLE_TYPE;
    }

    @Override
    default int getSize() {
        return 2;
    }

    /**
     * @param value
     * 		用于比较的值。
     *
     * @return 当已知值等于给定值时返回 {@code true}。
     */
    default boolean isEqualTo(double value) {
        return value().isPresent() && value().getAsDouble() == value;
    }

    /**
     * @param value
     * 		用于比较的值。
     *
     * @return 当已知值小于给定值时返回 {@code true}。
     */
    default boolean isLessThan(double value) {
        return value().isPresent() && value().getAsDouble() < value;
    }

    /**
     * @param value
     * 		用于比较的值。
     *
     * @return 当已知值小于或等于给定值时返回 {@code true}。
     */
    default boolean isLessThanOrEqual(double value) {
        return value().isPresent() && value().getAsDouble() <= value;
    }

    /**
     * @param value
     * 		用于比较的值。
     *
     * @return 当已知值大于给定值时返回 {@code true}。
     */
    default boolean isGreaterThan(double value) {
        return value().isPresent() && value().getAsDouble() > value;
    }

    /**
     * @param value
     * 		用于比较的值。
     *
     * @return 当已知值大于或等于给定值时返回 {@code true}。
     */
    default boolean isGreaterThanOrEqual(double value) {
        return value().isPresent() && value().getAsDouble() >= value;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 两值之和；若任一值未知则为 {@link #UNKNOWN}。
     */
    default DoubleValue add(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsDouble() + otherValue.getAsDouble());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 两值之差；若任一值未知则为 {@link #UNKNOWN}。
     */
    default DoubleValue sub(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsDouble() - otherValue.getAsDouble());
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 两值之积；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）。
     */
    default DoubleValue mul(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of(value.getAsDouble() * otherValue.getAsDouble());
        }
        if (isEqualTo(0) || other.isEqualTo(0)) {
            return VAL_0;
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 两值之商；若任一值未知或除数为 0 则为 {@link #UNKNOWN}。
     */
    default DoubleValue div(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            double otherLiteral = otherValue.getAsDouble();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of((value.getAsDouble() / otherLiteral));
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 比较结果（对应 {@code dcmpg} 指令，NaN 视为大于）；若任一值未知则为 {@link IntValue#UNKNOWN}。
     */
    default IntValue cmpg(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            double f1 = value.getAsDouble();
            double f2 = otherValue.getAsDouble();
            if (Double.isNaN(f1) || Double.isNaN(f2)) {
                return IntValue.VAL_1;
            }
            return IntValue.of(Double.compare(f1, f2));
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 比较结果（对应 {@code dcmpl} 指令，NaN 视为小于）；若任一值未知则为 {@link IntValue#UNKNOWN}。
     */
    default IntValue cmpl(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            double f1 = value.getAsDouble();
            double f2 = otherValue.getAsDouble();
            if (Double.isNaN(f1) || Double.isNaN(f2)) {
                return IntValue.VAL_M1;
            }
            return IntValue.of(Double.compare(f1, f2));
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值。
     *
     * @return 两值取余的结果；若任一值未知则为 {@link #UNKNOWN}。
     */
    default DoubleValue rem(DoubleValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of((float) (value.getAsDouble() % otherValue.getAsDouble()));
        }
        return UNKNOWN;
    }


    /**
     * @return 取负后的值；若值未知则为 {@link #UNKNOWN}。
     */
    default DoubleValue negate() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return of(-value.getAsDouble());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 int 后的值；若值未知则为 {@link IntValue#UNKNOWN}。
     */
    default IntValue castInt() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return IntValue.of((int) value.getAsDouble());
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @return 转换为 float 后的值；若值未知则为 {@link FloatValue#UNKNOWN}。
     */
    default FloatValue castFloat() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return FloatValue.of((float) value.getAsDouble());
        }
        return FloatValue.UNKNOWN;
    }


    /**
     * @return 转换为 long 后的值；若值未知则为 {@link LongValue#UNKNOWN}。
     */
    default LongValue castLong() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return LongValue.of((long) value.getAsDouble());
        }
        return LongValue.UNKNOWN;
    }
}
