package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.value.IllegalValueException;
import com.bingbaihanji.fxdecomplie.util.value.ObjectValue;
import com.bingbaihanji.fxdecomplie.util.value.ReValue;
import com.bingbaihanji.fxdecomplie.util.value.UninitializedValue;
import org.objectweb.asm.Type;


import java.util.Objects;
import java.util.Optional;

/**
 * 装箱对象值持有者实现。
 *
 * @author Matt Coley
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public abstract class ObjectValueBoxImpl<T> extends ObjectValueImpl {
    private final Optional<T> value;

    /**
     * @param type
     * 		对象类型。
     * @param nullness
     * 		值的空值状态。
     */
    public ObjectValueBoxImpl(Type type, Nullness nullness) {
        super(type, nullness);
        value = Optional.empty();
    }

    /**
     * @param type
     * 		对象类型。
     * @param value
     * 		要持有的装箱值。
     */
    public ObjectValueBoxImpl(Type type, T value) {
        super(type, value == null ? Nullness.NULL : Nullness.NOT_NULL);
        this.value = Optional.ofNullable(value);
    }


    /**
     * @param value
     * 		要包装的值。
     *
     * @return 持有给定值的装箱值实例。
     */
    protected abstract ObjectValueBoxImpl<T> wrap(T value);


    /**
     * @param nullness
     * 		值的空值状态。
     *
     * @return 内容未知、具有给定空值状态的装箱值实例。
     */
    protected abstract ObjectValueBoxImpl<T> wrapUnknown(Nullness nullness);

    @Override
    public boolean hasKnownValue() {
        return nullness() == Nullness.NOT_NULL && value.isPresent();
    }


    /**
     * @return 拆箱后的值。
     *
     * @throws java.util.NoSuchElementException
     * 		当值未知时。
     */
    public T unbox() {
        return value().orElseThrow();
    }


    /**
     * @return 值的内容。若未知则为空。
     */
    public Optional<T> value() {
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

        ObjectValueBoxImpl<?> other = (ObjectValueBoxImpl<?>) o;

        return value().equals(other.value());
    }

    @Override
    public int hashCode() {
        int value = type().hashCode();
        value = 31 * value + value().hashCode();
        return value;
    }

    @Override
    public String toString() {
        return type().getInternalName() + ":" + (hasKnownValue() ? unbox() : "<?>");
    }


    @Override
    @SuppressWarnings("unchecked")
    public ReValue mergeWith(ReValue other) throws IllegalValueException {
        if (other == UninitializedValue.UNINITIALIZED_VALUE) {
            return other;
        } else if (other instanceof ObjectValueBoxImpl<?> otherBoxed) {
            if (type().equals(otherBoxed.type()) && value().isPresent() && otherBoxed.value().isPresent()) {
                T v = value().get();
                T otherV = (T) otherBoxed.value().get();
                if (Objects.equals(v, otherV)) {
                    return wrap(v);
                }
            }
            return wrapUnknown(nullness().mergeWith(otherBoxed.nullness()));
        } else if (other instanceof ObjectValue otherObject) {
            return ObjectValue.object(type(), nullness().mergeWith(otherObject.nullness()));
        }
        throw new IllegalValueException("Cannot merge with: " + other);
    }
}
