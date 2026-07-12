package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Integer} 值持有者实现。
 *
 * @author Matt Coley
 */
public class BoxedIntegerValueImpl extends ObjectValueBoxImpl<Integer> {
    private static final Type TYPE = Type.getType(Integer.class);

    /**
     * @param nullness
     * 		值的空值状态。
     */
    public BoxedIntegerValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值。
     */
    public BoxedIntegerValueImpl(Integer value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Integer> wrap(Integer value) {
        return new BoxedIntegerValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Integer> wrapUnknown(Nullness nullness) {
        return new BoxedIntegerValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
