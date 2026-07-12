package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Byte} 值持有者实现
 *
 * @author Matt Coley
 */
public class BoxedByteValueImpl extends ObjectValueBoxImpl<Byte> {
    private static final Type TYPE = Type.getType(Byte.class);

    /**
     * @param nullness
     * 		值的空值状态
     */
    public BoxedByteValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值
     */
    public BoxedByteValueImpl(Byte value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Byte> wrap(Byte value) {
        return new BoxedByteValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Byte> wrapUnknown(Nullness nullness) {
        return new BoxedByteValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
