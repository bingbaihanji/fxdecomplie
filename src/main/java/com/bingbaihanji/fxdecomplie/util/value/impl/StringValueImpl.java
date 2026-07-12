package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.StringValue;


import java.util.Optional;

/**
 * 字符串值持有者实现
 *
 * @author Matt Coley
 */
public class StringValueImpl extends ObjectValueBoxImpl<String> implements StringValue {
    /**
     * @param nullness
     * 		值的空值状态
     */
    public StringValueImpl(Nullness nullness) {
        super(Types.STRING_TYPE, nullness);
    }

    /**
     * @param value
     * 		要持有的字符串值
     */
    public StringValueImpl(String value) {
        super(Types.STRING_TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<String> wrap(String value) {
        return new StringValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<String> wrapUnknown(Nullness nullness) {
        return new StringValueImpl(nullness);
    }


    @Override
    public Optional<String> getText() {
        return value();
    }
}
