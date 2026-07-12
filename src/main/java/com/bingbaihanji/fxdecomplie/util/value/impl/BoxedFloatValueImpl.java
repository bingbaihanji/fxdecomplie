package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Float} 值持有者实现。
 *
 * @author Matt Coley
 */
public class BoxedFloatValueImpl extends ObjectValueBoxImpl<Float> {
    private static final Type TYPE = Type.getType(Float.class);

    /**
     * @param nullness
     * 		值的空值状态。
     */
    public BoxedFloatValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值。
     */
    public BoxedFloatValueImpl(Float value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Float> wrap(Float value) {
        return new BoxedFloatValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Float> wrapUnknown(Nullness nullness) {
        return new BoxedFloatValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
