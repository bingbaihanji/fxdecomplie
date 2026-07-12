package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Double} 值持有者实现。
 *
 * @author Matt Coley
 */
public class BoxedDoubleValueImpl extends ObjectValueBoxImpl<Double> {
    private static final Type TYPE = Type.getType(Double.class);

    /**
     * @param nullness
     * 		值的空值状态。
     */
    public BoxedDoubleValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值。
     */
    public BoxedDoubleValueImpl(Double value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Double> wrap(Double value) {
        return new BoxedDoubleValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Double> wrapUnknown(Nullness nullness) {
        return new BoxedDoubleValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
