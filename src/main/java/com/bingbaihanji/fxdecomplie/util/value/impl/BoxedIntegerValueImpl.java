package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Integer} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedIntegerValueImpl extends ObjectValueBoxImpl<Integer> {
    private static final Type TYPE = Type.getType(Integer.class);

    public BoxedIntegerValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    public BoxedIntegerValueImpl(Integer value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Integer> wrap(Integer value) {
        return new BoxedIntegerValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Integer> wrapUnknown(Nullness nullness) {
        return new BoxedIntegerValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
