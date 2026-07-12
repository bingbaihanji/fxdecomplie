package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Long} 值持有者实现。
 *
 * @author Matt Coley
 */
public class BoxedLongValueImpl extends ObjectValueBoxImpl<Long> {
    private static final Type TYPE = Type.getType(Long.class);

    /**
     * @param nullness
     * 		值的空值状态。
     */
    public BoxedLongValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值。
     */
    public BoxedLongValueImpl(Long value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Long> wrap(Long value) {
        return new BoxedLongValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Long> wrapUnknown(Nullness nullness) {
        return new BoxedLongValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
