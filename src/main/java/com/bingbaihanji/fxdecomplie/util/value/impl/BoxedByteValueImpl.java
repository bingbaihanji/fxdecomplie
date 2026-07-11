package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Byte} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedByteValueImpl extends ObjectValueBoxImpl<Byte> {
    private static final Type TYPE = Type.getType(Byte.class);

    public BoxedByteValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

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
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
