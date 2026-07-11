package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Short} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedShortValueImpl extends ObjectValueBoxImpl<Short> {
    private static final Type TYPE = Type.getType(Short.class);

    public BoxedShortValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

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
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
