package com.bingbaihanji.fxdecomplie.util.value.impl;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.Type;


/**
 * Boxed {@link Double} value holder implementation.
 *
 * @author Matt Coley
 */
public class BoxedDoubleValueImpl extends ObjectValueBoxImpl<Double> {
    private static final Type TYPE = Type.getType(Double.class);

    public BoxedDoubleValueImpl(Nullness nullness) {
        super(TYPE, nullness);
    }

    public BoxedDoubleValueImpl(Double value) {
        super(TYPE, value);
    }


    @Override
    protected ObjectValueBoxImpl<Double> wrap(Double value) {
        return new BoxedDoubleValueImpl(value);
    }


    @Override
    protected ObjectValueBoxImpl<Double> wrapUnknown(Nullness nullness) {
        return new BoxedDoubleValueImpl(nullness);
    }

    @Override
    public int getSize() {
        // Deconflict between object-v-primitive value interfaces
        return 1;
    }
}
