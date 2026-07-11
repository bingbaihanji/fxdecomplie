package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.StringValue;


import java.util.Optional;

/**
 * String value holder implementation.
 *
 * @author Matt Coley
 */
public class StringValueImpl extends ObjectValueBoxImpl<String> implements StringValue {
    public StringValueImpl(Nullness nullness) {
        super(Types.STRING_TYPE, nullness);
    }

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
