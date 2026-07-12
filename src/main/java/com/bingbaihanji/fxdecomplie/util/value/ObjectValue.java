package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import com.bingbaihanji.fxdecomplie.util.reflect.asm.Types;
import com.bingbaihanji.fxdecomplie.util.value.impl.ObjectValueImpl;
import com.bingbaihanji.fxdecomplie.util.value.impl.StringValueImpl;
import org.objectweb.asm.Type;

/**
 * 能够记录特定对象类型精确内容的值。
 * <table>
 *     <tr><th>内容</th><th>使用的值</th></tr>
 *     <tr><td>{@code null}</td><td>{@link #VAL_OBJECT_NULL}</td></tr>
 *     <tr><td>{@code Any.class}</td><td>{@link #VAL_CLASS}</td></tr>
 *     <tr><td>{@code Method::reference}</td><td>{@link #VAL_METHOD_HANDLE}</td></tr>
 *     <tr><td>{@code "strings"}</td><td>{@link #string(String)} 或 {@link #string(Nullness)}</td></tr>
 * </table>
 *
 * @author Matt Coley
 */
public non-sealed interface ObjectValue extends ReValue {
    ObjectValue VAL_OBJECT = new ObjectValueImpl(Types.OBJECT_TYPE, Nullness.NOT_NULL);
    ObjectValue VAL_OBJECT_NULL = new ObjectValueImpl(Types.OBJECT_TYPE, Nullness.NULL);
    ObjectValue VAL_OBJECT_MAYBE_NULL = new ObjectValueImpl(Types.OBJECT_TYPE, Nullness.UNKNOWN);
    ObjectValue VAL_CLASS = new ObjectValueImpl(Types.CLASS_TYPE, Nullness.NOT_NULL);
    ObjectValue VAL_CLASS_NULL = new ObjectValueImpl(Types.CLASS_TYPE, Nullness.NULL);
    ObjectValue VAL_CLASS_MAYBE_NULL = new ObjectValueImpl(Types.CLASS_TYPE, Nullness.NULL);
    ObjectValue VAL_METHOD_TYPE = new ObjectValueImpl(Type.getObjectType("java/lang/invoke/MethodType"), Nullness.NOT_NULL);
    ObjectValue VAL_METHOD_HANDLE = new ObjectValueImpl(Type.getObjectType("java/lang/invoke/MethodType"), Nullness.NOT_NULL);
    ObjectValue VAL_JSR = new ObjectValueImpl(Type.VOID_TYPE, Nullness.NOT_NULL);

    /**
     * @param text
     * 		精确的字符串内容。
     *
     * @return 持有该精确内容的字符串值。
     */

    static StringValue string(String text) {
        if (text == null) {
            return StringValue.VAL_STRING_NULL;
        }
        if (text.isEmpty()) {
            return StringValue.VAL_STRING_EMPTY;
        }
        if (" ".equals(text)) {
            return StringValue.VAL_STRING_SPACE;
        }
        return new StringValueImpl(text);
    }

    /**
     * @param nullness
     * 		该 {@link Class} 的空值状态。
     *
     * @return 给定空值状态的类字面量对象值。
     */

    static ObjectValue clazz(Nullness nullness) {
        return switch (nullness) {
            case NULL -> VAL_CLASS_NULL;
            case NOT_NULL -> VAL_CLASS;
            case UNKNOWN -> VAL_CLASS_MAYBE_NULL;
        };
    }

    /**
     * @param nullness
     * 		字符串的空值状态。
     *
     * @return 给定空值状态的字符串值。
     */

    static StringValue string(Nullness nullness) {
        return switch (nullness) {
            case NULL -> StringValue.VAL_STRING_NULL;
            case NOT_NULL -> StringValue.VAL_STRING;
            case UNKNOWN -> StringValue.VAL_STRING_MAYBE_NULL;
        };
    }

    /**
     * @param nullness
     * 		字符串的空值状态。
     *
     * @return 给定空值状态、类型为 {@link Object} 的对象值。
     */

    static ObjectValue object(Nullness nullness) {
        return switch (nullness) {
            case NULL -> VAL_OBJECT_NULL;
            case NOT_NULL -> VAL_OBJECT;
            case UNKNOWN -> VAL_OBJECT_MAYBE_NULL;
        };
    }


    /**
     * @param type
     * 		对象类型。
     * @param nullness
     * 		对象的空值状态。
     *
     * @return 给定类型与空值状态的对象值。
     */
    static ObjectValue object(Type type, Nullness nullness) {
        if (Types.OBJECT_TYPE.equals(type)) {
            return object(nullness);
        }
        if (Types.STRING_TYPE.equals(type)) {
            return string(nullness);
        }
        if (Types.CLASS_TYPE.equals(type)) {
            return clazz(nullness);
        }
        return new ObjectValueImpl(type, nullness);
    }


    @Override
    Type type();

    /**
     * @return 此值的空值状态。
     */

    Nullness nullness();

    /**
     * @return 当此值确定为 {@code null} 时返回 {@code true}。
     */
    default boolean isNull() {
        return nullness() == Nullness.NULL;
    }

    /**
     * @return 当此值确定<b>不</b>为 {@code null} 时返回 {@code true}。
     */
    default boolean isNotNull() {
        return nullness() == Nullness.NOT_NULL;
    }

    @Override
    default int getSize() {
        return 1;
    }
}
