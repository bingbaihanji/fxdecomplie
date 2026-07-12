package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * 装箱 {@link Character} 值持有者实现。
 *
 * @author Matt Coley
 */
public class BoxedCharacterValueImpl extends ObjectValueBoxImpl<Character> {
    private static final Type TYPE = Type.getType(Character.class);

    /**
     * @param nullness
     * 		值的空值状态。
     */
    public BoxedCharacterValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的装箱值。
     */
    public BoxedCharacterValueImpl(Character value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Character> wrap(Character value) {
        return new BoxedCharacterValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Character> wrapUnknown(Nullness nullness) {
        return new BoxedCharacterValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // 用于消解对象值接口与基本类型值接口之间的冲突
        return 1;
    }
}
