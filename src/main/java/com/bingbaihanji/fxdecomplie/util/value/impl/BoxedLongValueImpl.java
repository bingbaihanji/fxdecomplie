package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Long} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedLongValueImpl extends ObjectValueBoxImpl<Long> {
    private static final Type TYPE = Type.getType(Long.class);

    public BoxedLongValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

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
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
