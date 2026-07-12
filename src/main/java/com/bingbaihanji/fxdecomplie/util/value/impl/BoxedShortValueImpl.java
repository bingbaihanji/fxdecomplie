package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Short} 值持有者实现
 *
 * @author Matt Coley
 */
public class BoxedShortValueImpl extends ObjectValueBoxImpl<Short> {
    private static final Type TYPE = Type.getType(Short.class);

    /**
     * @param nullness
     * 		值的空值状态
     */
    public BoxedShortValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值
     */
    public BoxedShortValueImpl(Short value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Short> wrap(Short value) {
        return new BoxedShortValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Short> wrapUnknown(Nullness nullness) {
        return new BoxedShortValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
