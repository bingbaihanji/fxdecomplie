package com.bingbaihanji.fxdecomplie.util.lookup;

import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.*;
import com.bingbaihanji.fxdecomplie.util.value.impl.*;


import java.util.List;
import java.util.OptionalInt;

/**
 * Common utilities for lookup implementations to convert between JVM values and interpreter values.
 *
 * @author Matt Coley
 */
public class BasicLookupUtils {
    // TODO: These short-named methods are useful for code generation and should stay as-is,
    //       But we should also expose the conversion logic in a more explicit way for plugin developers
    //       to use when writing transformers or their own lookup implementations.

    @SuppressWarnings("all")
    protected static byte b(IntValue value) {
        return (byte) value.value().getAsInt();
    }

    @SuppressWarnings("all")
    protected static boolean z(IntValue value) {
        return value.isNotEqualTo(0);
    }

    @SuppressWarnings("all")
    protected static short s(IntValue value) {
        return (short) value.value().getAsInt();
    }

    @SuppressWarnings("all")
    protected static char c(IntValue value) {
        return (char) value.value().getAsInt();
    }

    @SuppressWarnings("all")
    protected static int i(IntValue value) {
        return value.value().getAsInt();
    }

    @SuppressWarnings("all")
    protected static long j(LongValue value) {
        return value.value().getAsLong();
    }

    @SuppressWarnings("all")
    protected static float f(FloatValue value) {
        return (float) value.value().getAsDouble();
    }

    @SuppressWarnings("all")
    protected static double d(DoubleValue value) {
        return value.value().getAsDouble();
    }

    @SuppressWarnings("all")
    protected static String str(StringValue value) {
        return value.getText().get();
    }

    protected static Object objl(ObjectValue value) {
        // Yield object type literally instead of auto-casting at call-site with T.
        return obj(value);
    }

    @SuppressWarnings("all")
    protected static <T> T obj(ObjectValue value) {
        // Map null to null
        if (value.isNull())
            return null;

        // Unwrap strings
        if (value instanceof StringValue string)
            return (T) str(string);

        // Unwrap boxed values
        if (value instanceof ObjectValueBoxImpl<?> box)
            return (T) box.unbox();

        throw new IllegalArgumentException("Unsupported object unwrap: " + value);
    }


    protected static IntValue z(boolean value) {
        return IntValue.of(value ? 1 : 0);
    }


    protected static IntValue b(byte value) {
        return IntValue.of(value);
    }


    protected static IntValue c(char value) {
        return IntValue.of(value);
    }


    protected static IntValue s(short value) {
        return IntValue.of(value);
    }


    protected static IntValue i(int value) {
        return IntValue.of(value);
    }


    protected static LongValue j(long value) {
        return LongValue.of(value);
    }


    protected static FloatValue f(float value) {
        return FloatValue.of(value);
    }


    protected static DoubleValue d(double value) {
        return DoubleValue.of(value);
    }


    protected static StringValue str(String value) {
        return ObjectValue.string(value);
    }


    protected static StringValue str(CharSequence value) {
        return str(value == null ? null : value.toString());
    }


    protected static ObjectValue obj(Object value) {
        // This isn't exactly perfect, as we lose type info with this conversion.
        if (value == null) {
            return ObjectValue.VAL_OBJECT_NULL;
        }

        // String-like
        if (value instanceof CharSequence string) {
            return str(string);
        }

        // Primitive boxes
        if (value instanceof Integer i) {
            return new BoxedIntegerValueImpl(i);
        }
        if (value instanceof Long l) {
            return new BoxedLongValueImpl(l);
        }
        if (value instanceof Byte b) {
            return new BoxedByteValueImpl(b);
        }
        if (value instanceof Float f) {
            return new BoxedFloatValueImpl(f);
        }
        if (value instanceof Double d) {
            return new BoxedDoubleValueImpl(d);
        }
        if (value instanceof Character c) {
            return new BoxedCharacterValueImpl(c);
        }
        if (value instanceof Short s) {
            return new BoxedShortValueImpl(s);
        }

        throw new IllegalArgumentException("Unsupported Object wrap: " + value);
    }

    protected static boolean[] arrz(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        boolean[] booleans = new boolean[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof IntValue iiv && iiv.hasKnownValue()) {
                booleans[i] = z(iiv);
            }
        }
        return booleans;
    }

    protected static byte[] arrb(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof IntValue iiv && iiv.hasKnownValue()) {
                bytes[i] = b(iiv);
            }
        }
        return bytes;
    }

    protected static short[] arrs(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        short[] shorts = new short[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof IntValue iiv && iiv.hasKnownValue()) {
                shorts[i] = s(iiv);
            }
        }
        return shorts;
    }

    protected static char[] arrc(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        char[] chars = new char[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof IntValue iiv && iiv.hasKnownValue()) {
                chars[i] = c(iiv);
            }
        }
        return chars;
    }

    protected static int[] arri(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        int[] ints = new int[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof IntValue iiv && iiv.hasKnownValue()) {
                ints[i] = i(iiv);
            }
        }
        return ints;
    }

    protected static float[] arrf(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        float[] floats = new float[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof FloatValue fv && fv.hasKnownValue()) {
                floats[i] = f(fv);
            }
        }
        return floats;
    }

    protected static double[] arrd(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        double[] doubles = new double[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof DoubleValue dv && dv.hasKnownValue()) {
                doubles[i] = d(dv);
            }
        }
        return doubles;
    }

    protected static long[] arrj(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        long[] longs = new long[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof LongValue lv && lv.hasKnownValue()) {
                longs[i] = j(lv);
            }
        }
        return longs;
    }

    protected static String[] arrstr(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        String[] strings = new String[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof StringValue sv && sv.hasKnownValue()) {
                strings[i] = str(sv);
            }
        }
        return strings;
    }

    protected static Object[] arrobj(ArrayValue value) {
        if (value.isNull()) {
            return null;
        }
        OptionalInt dimLength = value.getFirstDimensionLength();
        if (dimLength.isEmpty() || !value.hasKnownValue()) {
            throw new IllegalArgumentException();
        }
        int length = dimLength.getAsInt();
        Object[] objects = new Object[length];
        for (int i = 0; i < length; i++) {
            ReValue iv = value.getValue(i);
            if (iv instanceof ObjectValue sv && sv.hasKnownValue()) {
                objects[i] = obj(sv);
            }
        }
        return objects;
    }


    protected static ArrayValue arrz(boolean[] value) {
        if (value == null) {
            return ArrayValue.VAL_BOOLEANS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_BOOLEAN, Nullness.NOT_NULL, value.length, index -> IntValue.of(value[index] ? 1 : 0));
    }


    protected static ArrayValue arrb(byte[] value) {
        if (value == null) {
            return ArrayValue.VAL_BYTES_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_BYTE, Nullness.NOT_NULL, value.length, index -> IntValue.of(value[index]));
    }


    protected static ArrayValue arrs(short[] value) {
        if (value == null) {
            return ArrayValue.VAL_SHORTS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_SHORT, Nullness.NOT_NULL, value.length, index -> IntValue.of(value[index]));
    }


    protected static ArrayValue arrc(char[] value) {
        if (value == null) {
            return ArrayValue.VAL_CHARS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_CHAR, Nullness.NOT_NULL, value.length, index -> IntValue.of(value[index]));
    }


    protected static ArrayValue arri(int[] value) {
        if (value == null) {
            return ArrayValue.VAL_INTS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_INT, Nullness.NOT_NULL, value.length, index -> IntValue.of(value[index]));
    }


    protected static ArrayValue arrj(long[] value) {
        if (value == null) {
            return ArrayValue.VAL_LONGS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_LONG, Nullness.NOT_NULL, value.length, index -> LongValue.of(value[index]));
    }


    protected static ArrayValue arrf(float[] value) {
        if (value == null) {
            return ArrayValue.VAL_FLOATS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_FLOAT, Nullness.NOT_NULL, value.length, index -> FloatValue.of(value[index]));
    }


    protected static ArrayValue arrd(double[] value) {
        if (value == null) {
            return ArrayValue.VAL_DOUBLES_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_DOUBLE, Nullness.NOT_NULL, value.length, index -> DoubleValue.of(value[index]));
    }


    protected static ArrayValue arrstr(CharSequence[] value) {
        if (value == null) {
            return ArrayValue.VAL_STRINGS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_STRING, Nullness.NOT_NULL, value.length, index -> ObjectValue.string(String.valueOf(value[index])));
    }


    protected static ArrayValue arrstr(String[] value) {
        if (value == null) {
            return ArrayValue.VAL_STRINGS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_STRING, Nullness.NOT_NULL, value.length, index -> ObjectValue.string(value[index]));
    }


    protected static ArrayValue arrobj(Object[] value) {
        if (value == null) {
            return ArrayValue.VAL_OBJECTS_NULL;
        }
        return new ArrayValueImpl(Types.ARRAY_1D_OBJECT, Nullness.NOT_NULL, value.length, index -> obj(value[index]));
    }

    @SuppressWarnings("unchecked")
    protected interface Func_7<A extends ReValue, B extends ReValue, C extends ReValue, D extends ReValue, E extends ReValue, F extends ReValue, G extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1), (C) values.get(2), (D) values.get(3), (E) values.get(4), (F) values.get(5), (G) values.get(6));
        }


        ReValue apply(A a, B b, C c, D d, E e, F f, G g);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_6<A extends ReValue, B extends ReValue, C extends ReValue, D extends ReValue, E extends ReValue, F extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1), (C) values.get(2), (D) values.get(3), (E) values.get(4), (F) values.get(5));
        }


        ReValue apply(A a, B b, C c, D d, E e, F f);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_5<A extends ReValue, B extends ReValue, C extends ReValue, D extends ReValue, E extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1), (C) values.get(2), (D) values.get(3), (E) values.get(4));
        }


        ReValue apply(A a, B b, C c, D d, E e);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_4<A extends ReValue, B extends ReValue, C extends ReValue, D extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1), (C) values.get(2), (D) values.get(3));
        }


        ReValue apply(A a, B b, C c, D d);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_3<A extends ReValue, B extends ReValue, C extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1), (C) values.get(2));
        }


        ReValue apply(A a, B b, C c);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_2<A extends ReValue, B extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.get(0), (B) values.get(1));
        }


        ReValue apply(A a, B b);
    }

    @SuppressWarnings("unchecked")
    protected interface Func_1<A extends ReValue> extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply((A) values.getFirst());
        }


        ReValue apply(A a);
    }

    protected interface Func_0 extends Func {

        @Override
        default ReValue apply(List<? extends ReValue> values) {
            return apply();
        }


        ReValue apply();
    }

    protected interface Func {

        ReValue apply(List<? extends ReValue> values);
    }
}
