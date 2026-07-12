package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Boolean} 值持有者实现
 *
 * @author Matt Coley
 */
public class BoxedBooleanValueImpl extends ObjectValueBoxImpl<Boolean> {
    private static final Type TYPE = Type.getType(Boolean.class);

    /**
     * @param nullness
     * 		值的空值状态
     */
    public BoxedBooleanValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值
     */
    public BoxedBooleanValueImpl(Boolean value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Boolean> wrap(Boolean value) {
        return new BoxedBooleanValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Boolean> wrapUnknown(Nullness nullness) {
        return new BoxedBooleanValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
