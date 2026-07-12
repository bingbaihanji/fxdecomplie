package com.bingbaihanji.fxdecomplie.util.value;

import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.impl.ArrayValueImpl;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;


import java.util.Arrays;
import java.util.OptionalInt;

/**
 * 能够记录数组内容部分细节的值。
 *
 * @author Matt Coley
 */
public interface ArrayValue extends ObjectValue {
    ArrayValue VAL_BOOLEANS = new ArrayValueImpl(Type.getType("[Z"), Nullness.NOT_NULL);
    ArrayValue VAL_BOOLEANS_NULL = new ArrayValueImpl(Type.getType("[Z"), Nullness.NULL);
    ArrayValue VAL_CHARS = new ArrayValueImpl(Type.getType("[C"), Nullness.NOT_NULL);
    ArrayValue VAL_CHARS_NULL = new ArrayValueImpl(Type.getType("[C"), Nullness.NULL);
    ArrayValue VAL_BYTES = new ArrayValueImpl(Type.getType("[B"), Nullness.NOT_NULL);
    ArrayValue VAL_BYTES_NULL = new ArrayValueImpl(Type.getType("[B"), Nullness.NULL);
    ArrayValue VAL_SHORTS = new ArrayValueImpl(Type.getType("[S"), Nullness.NOT_NULL);
    ArrayValue VAL_SHORTS_NULL = new ArrayValueImpl(Type.getType("[S"), Nullness.NULL);
    ArrayValue VAL_INTS = new ArrayValueImpl(Type.getType("[I"), Nullness.NOT_NULL);
    ArrayValue VAL_INTS_NULL = new ArrayValueImpl(Type.getType("[I"), Nullness.NULL);
    ArrayValue VAL_FLOATS = new ArrayValueImpl(Type.getType("[F"), Nullness.NOT_NULL);
    ArrayValue VAL_FLOATS_NULL = new ArrayValueImpl(Type.getType("[F"), Nullness.NULL);
    ArrayValue VAL_DOUBLES = new ArrayValueImpl(Type.getType("[D"), Nullness.NOT_NULL);
    ArrayValue VAL_DOUBLES_NULL = new ArrayValueImpl(Type.getType("[D"), Nullness.NULL);
    ArrayValue VAL_LONGS = new ArrayValueImpl(Type.getType("[J"), Nullness.NOT_NULL);
    ArrayValue VAL_LONGS_NULL = new ArrayValueImpl(Type.getType("[J"), Nullness.NULL);
    ArrayValue VAL_OBJECTS = new ArrayValueImpl(Type.getType("[Ljava/lang/Object;"), Nullness.NOT_NULL);
    ArrayValue VAL_OBJECTS_NULL = new ArrayValueImpl(Type.getType("[Ljava/lang/Object;"), Nullness.NULL);
    ArrayValue VAL_STRINGS = new ArrayValueImpl(Type.getType("[Ljava/lang/String;"), Nullness.NOT_NULL);
    ArrayValue VAL_STRINGS_NULL = new ArrayValueImpl(Type.getType("[Ljava/lang/String;"), Nullness.NULL);

    /**
     * @param type
     * 		数组类型。
     * @param nullness
     * 		数组空值状态。
     *
     * @return 给定类型的数组值。
     */

    static ArrayValue of(Type type, Nullness nullness) {
        String descriptor = type.getDescriptor();
        return switch (descriptor) {
            case "[Z" -> nullness == Nullness.NULL ? VAL_BOOLEANS_NULL : VAL_BOOLEANS;
            case "[C" -> nullness == Nullness.NULL ? VAL_CHARS_NULL : VAL_CHARS;
            case "[B" -> nullness == Nullness.NULL ? VAL_BYTES_NULL : VAL_BYTES;
            case "[S" -> nullness == Nullness.NULL ? VAL_SHORTS_NULL : VAL_SHORTS;
            case "[I" -> nullness == Nullness.NULL ? VAL_INTS_NULL : VAL_INTS;
            case "[F" -> nullness == Nullness.NULL ? VAL_FLOATS_NULL : VAL_FLOATS;
            case "[D" -> nullness == Nullness.NULL ? VAL_DOUBLES_NULL : VAL_DOUBLES;
            case "[J" -> nullness == Nullness.NULL ? VAL_LONGS_NULL : VAL_LONGS;
            case "[Ljava/lang/String;" -> nullness == Nullness.NULL ? VAL_STRINGS_NULL : VAL_STRINGS;
            case "[Ljava/lang/Object;" -> nullness == Nullness.NULL ? VAL_OBJECTS_NULL : VAL_OBJECTS;
            default -> new ArrayValueImpl(type, nullness);
        };
    }

    /**
     * @param type
     * 		数组类型。
     * @param nullness
     * 		数组空值状态。
     * @param length
     * 		数组长度。
     *
     * @return 给定类型/长度的数组值。
     */

    static ArrayValue of(Type type, Nullness nullness, int length) {
        return new ArrayValueImpl(type, nullness, length);
    }

    /**
     * @param type
     * 		数组类型。
     * @param dimensions
     * 		要创建的数组各维度长度。
     *
     * @return 由 {@link Opcodes#MULTIANEWARRAY} 指令创建的数组值。
     */

    static ReValue multiANewArray(Type type, int[] dimensions) {
        int length = dimensions[0];
        if (dimensions.length == 1) {
            return of(type, Nullness.NOT_NULL, length);
        }
        return new ArrayValueImpl(type, Nullness.NOT_NULL, length,
                i -> multiANewArray(Types.undimension(type), Arrays.copyOfRange(dimensions, 1, dimensions.length))
        );
    }

    /**
     * @param index
     * 		要赋值的索引。
     * @param value
     * 		要赋的值。
     *
     * @return 在给定索引处赋予给定值后的新数组。
     */

    ArrayValue setValue(int index, ReValue value);

    /**
     * @param originalValue
     * 		某个值。
     * @param updatedValue
     * 		该值的某个更新版本。
     *
     * @return 将给定原始值替换为更新值后的新数组。
     */

    ArrayValue updatedCopyIfContained(ReValue originalValue, ReValue updatedValue);

    @Override
    default boolean hasKnownValue() {
        return false;
    }


    @Override
    Type type();

    /**
     * 元素类型是任意数组的基础类型。考虑以下情形：
     * <ul>
     *     <li>{@code int[]}</li>
     *     <li>{@code int[][]}</li>
     *     <li>{@code int[][][]}</li>
     * </ul>
     * 它们的元素类型都是 {@code int}。
     *
     * @return 数组的元素类型。
     */

    default Type elementType() {
        return type().getElementType();
    }

    /**
     * 考虑以下情形：
     * <ul>
     *     <li>1: {@code int[]}</li>
     *     <li>2: {@code int[][]}</li>
     *     <li>3: {@code int[][][]}</li>
     * </ul>
     *
     * @return 数组的维数。
     */
    default int dimensions() {
        return type().getDimensions();
    }

    /**
     * 考虑以下情形：
     * <table>
     *     <tr><th>长度</th><th>数组定义</th></tr>
     *     <tr><td>{@code 7}</td><td>{@code int[7]}</td></tr>
     *     <tr><td>{@code 7}</td><td>{@code int[7][9]}</td></tr>
     *     <tr><td>未知</td><td>{@code int[][9]}</td></tr>
     *     <tr><td>未知</td><td>{@code int[]}</td></tr>
     * </table>
     *
     * @return 第一维的长度。
     */

    OptionalInt getFirstDimensionLength();

    /**
     * @param index
     * 		{@link #getFirstDimensionLength()} 范围内的索引。
     *
     * @return 若已知则返回给定索引处的值；否则返回数组元素类型对应的 {@link ReValue}。
     */

    ReValue getValue(int index);
}
