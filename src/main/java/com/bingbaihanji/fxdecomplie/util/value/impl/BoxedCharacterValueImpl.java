package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Character} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedCharacterValueImpl extends ObjectValueBoxImpl<Character> {
    private static final Type TYPE = Type.getType(Character.class);

    public BoxedCharacterValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

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
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
