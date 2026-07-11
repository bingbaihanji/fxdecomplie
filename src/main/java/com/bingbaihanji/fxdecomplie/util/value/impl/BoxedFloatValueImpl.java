package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Float} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedFloatValueImpl extends ObjectValueBoxImpl<Float> {
    private static final Type TYPE = Type.getType(Float.class);

    public BoxedFloatValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    public BoxedFloatValueImpl(Float value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Float> wrap(Float value) {
        return new BoxedFloatValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Float> wrapUnknown(Nullness nullness) {
        return new BoxedFloatValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
