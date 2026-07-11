package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Boolean} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedBooleanValueImpl extends ObjectValueBoxImpl<Boolean> {
    private static final Type TYPE = Type.getType(Boolean.class);

    public BoxedBooleanValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

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
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
