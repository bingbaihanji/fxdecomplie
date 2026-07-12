package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.util.value.impl.FloatValueImpl;
import org.objectweb.asm.Type;

import java.util.OptionalDouble;

/**
 * 能够记录精确的浮点内容的值
 *
 * @author Matt Coley
 */
public non-sealed interface FloatValue extends ReValue {
    FloatValue UNKNOWN = new FloatValueImpl();
    FloatValue VAL_MAX = new FloatValueImpl(Float.MAX_VALUE);
    FloatValue VAL_MIN = new FloatValueImpl(Float.MIN_VALUE);
    FloatValue VAL_M1 = new FloatValueImpl(-1);
    FloatValue VAL_0 = new FloatValueImpl(0);
    FloatValue VAL_1 = new FloatValueImpl(1);
    FloatValue VAL_2 = new FloatValueImpl(2);

    /**
     * @param value
     * 		要持有的 float 值
     *
     * @return 持有该精确内容的 float 值
     */

    static FloatValue of(float value) {
        if (value == 0) {
            return VAL_0;
        } else if (value == 1) {
            return VAL_1;
        } else if (value == -1) {
            return VAL_M1;
        } else if (value == 2) {
            return VAL_2;
        } else if (value == Float.MAX_VALUE) {
            return VAL_MAX;
        } else if (value == Float.MIN_VALUE) {
            return VAL_MIN;
        }
        return new FloatValueImpl(value);
    }

    /**
     * @return 值的 float 内容若 {@link #hasKnownValue() 未知} 则为空
     *
     * @implNote Java 没有 {@code OptionalFloat}
     */

    OptionalDouble value();

    @Override
    default boolean hasKnownValue() {
        return value().isPresent();
    }


    @Override
    default Type type() {
        return Type.FLOAT_TYPE;
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
    default boolean isEqualTo(float value) {
        return value().isPresent() && value().getAsDouble() == value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于给定值时返回 {@code true}
     */
    default boolean isLessThan(float value) {
        return value().isPresent() && value().getAsDouble() < value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值小于或等于给定值时返回 {@code true}
     */
    default boolean isLessThanOrEqual(float value) {
        return value().isPresent() && value().getAsDouble() <= value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于给定值时返回 {@code true}
     */
    default boolean isGreaterThan(float value) {
        return value().isPresent() && value().getAsDouble() > value;
    }

    /**
     * @param value
     * 		用于比较的值
     *
     * @return 当已知值大于或等于给定值时返回 {@code true}
     */
    default boolean isGreaterThanOrEqual(float value) {
        return value().isPresent() && value().getAsDouble() >= value;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之和；若任一值未知则为 {@link #UNKNOWN}
     */
    default FloatValue add(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of((float) (value.getAsDouble() + otherValue.getAsDouble()));
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之差；若任一值未知则为 {@link #UNKNOWN}
     */
    default FloatValue sub(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of((float) (value.getAsDouble() - otherValue.getAsDouble()));
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值之积；若任一值未知则为 {@link #UNKNOWN}（任一操作数为 0 时结果为 0）
     */
    default FloatValue mul(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of((float) (value.getAsDouble() * otherValue.getAsDouble()));
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
    default FloatValue div(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            double otherLiteral = otherValue.getAsDouble();
            if (otherLiteral == 0) {
                return UNKNOWN; // 这里我们假装它能正常工作
            }
            return of((float) (value.getAsDouble() / otherLiteral));
        }
        return UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 比较结果（对应 {@code fcmpg} 指令，NaN 视为大于）；若任一值未知则为 {@link IntValue#UNKNOWN}
     */
    default IntValue cmpg(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            float f1 = (float) value.getAsDouble();
            float f2 = (float) otherValue.getAsDouble();
            if (Float.isNaN(f1) || Float.isNaN(f2)) {
                return IntValue.VAL_1;
            }
            return IntValue.of(Float.compare(f1, f2));
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 比较结果（对应 {@code fcmpl} 指令，NaN 视为小于）；若任一值未知则为 {@link IntValue#UNKNOWN}
     */
    default IntValue cmpl(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            float f1 = (float) value.getAsDouble();
            float f2 = (float) otherValue.getAsDouble();
            if (Float.isNaN(f1) || Float.isNaN(f2)) {
                return IntValue.VAL_M1;
            }
            return IntValue.of(Float.compare(f1, f2));
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @param other
     * 		另一个值
     *
     * @return 两值取余的结果；若任一值未知则为 {@link #UNKNOWN}
     */
    default FloatValue rem(FloatValue other) {
        OptionalDouble value = value();
        OptionalDouble otherValue = other.value();
        if (value.isPresent() && otherValue.isPresent()) {
            return of((float) (value.getAsDouble() % otherValue.getAsDouble()));
        }
        return UNKNOWN;
    }


    /**
     * @return 取负后的值；若值未知则为 {@link #UNKNOWN}
     */
    default FloatValue negate() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return of((float) -value.getAsDouble());
        }
        return UNKNOWN;
    }


    /**
     * @return 转换为 int 后的值；若值未知则为 {@link IntValue#UNKNOWN}
     */
    default IntValue castInt() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return IntValue.of((int) value.getAsDouble());
        }
        return IntValue.UNKNOWN;
    }


    /**
     * @return 转换为 double 后的值；若值未知则为 {@link DoubleValue#UNKNOWN}
     */
    default DoubleValue castDouble() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return DoubleValue.of(value.getAsDouble());
        }
        return DoubleValue.UNKNOWN;
    }


    /**
     * @return 转换为 long 后的值；若值未知则为 {@link LongValue#UNKNOWN}
     */
    default LongValue castLong() {
        OptionalDouble value = value();
        if (value.isPresent()) {
            return LongValue.of((long) value.getAsDouble());
        }
        return LongValue.UNKNOWN;
    }
}
