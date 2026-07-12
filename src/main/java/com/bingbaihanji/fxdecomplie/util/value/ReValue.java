package com.bingbaihanji.fxdecomplie.util.value;


import com.bingbaihanji.fxdecomplie.model.Nullness;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.analysis.Value;


/**
 * 一类值的基类型，当所有控制流路径最终收敛于单一使用场景时，能够记录精确内容
 *
 * @author Matt Coley
 */
public sealed interface ReValue extends Value permits IntValue, FloatValue, DoubleValue, LongValue, ObjectValue, UninitializedValue {
    /**
     * @param value
     * 		LDC 指令中的 ASM 常量值
     *
     * @return 给定输入的 {@link ReValue} 包装
     *
     * @throws IllegalValueException
     * 		当该值无法映射为 {@link ReValue} 时
     * @see LdcInsnNode#cst 可能的取值
     */

    static ReValue ofConstant(Object value) throws IllegalValueException {
        return switch (value) {
            case Character c -> IntValue.of(c);
            case Byte b -> IntValue.of(b);
            case Short s -> IntValue.of(s);
            case Integer i -> IntValue.of(i);
            case Float f -> FloatValue.of(f);
            case Long l -> LongValue.of(l);
            case Double d -> DoubleValue.of(d);
            case String s -> ObjectValue.string(s);
            case Handle handle -> ObjectValue.VAL_METHOD_HANDLE;
            case Type type -> {
                int sort = type.getSort();
                if (sort == Type.OBJECT || sort == Type.ARRAY) {
                    yield ObjectValue.VAL_CLASS;
                }
                if (sort == Type.METHOD) {
                    yield ObjectValue.VAL_METHOD_TYPE;
                }
                throw new IllegalValueException("Illegal LDC value " + value);
            }
            case ConstantDynamic constantDynamic -> {
                Type dynamicType = Type.getType(constantDynamic.getDescriptor());
                ReValue dynamicValue = ofType(dynamicType, Nullness.NOT_NULL);
                if (dynamicValue == null) {
                    throw new IllegalValueException("Illegal LDC dynamic value descriptor " + dynamicType);
                }
                yield dynamicValue;
            }
            case null, default -> throw new IllegalValueException("Illegal LDC value " + value);
        };
    }

    /**
     * @param type
     * 		要为其创建新泛型值的类型
     * @param nullness
     * 		值的空值状态
     *
     * @return 给定类型的值
     *
     * @throws IllegalValueException
     * 		当该类型无法映射为 {@link ReValue} 时
     */

    static ReValue ofType(Type type, Nullness nullness) throws IllegalValueException {
        if (type == null) {
            return UninitializedValue.UNINITIALIZED_VALUE;
        }
        return switch (type.getSort()) {
            case Type.VOID -> null;
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> IntValue.UNKNOWN;
            case Type.FLOAT -> FloatValue.UNKNOWN;
            case Type.LONG -> LongValue.UNKNOWN;
            case Type.DOUBLE -> DoubleValue.UNKNOWN;
            case Type.ARRAY -> ArrayValue.of(type, nullness);
            case Type.OBJECT -> ObjectValue.object(type, nullness);
            default -> throw new IllegalValueException("Invalid type for new value: " + type);
        };
    }

    /**
     * @param type
     * 		要为其创建新泛型值的类型
     *
     * @return 给定类型的值，取默认值 <i>（基本类型为 {@code 0}，对象/数组为 {@code null}）</i>
     *
     * @throws IllegalValueException
     * 		当该类型无法映射为 {@link ReValue} 时
     */

    static ReValue ofTypeDefaultValue(Type type) throws IllegalValueException {
        return switch (type.getSort()) {
            case Type.VOID -> throw new IllegalValueException("Cannot create default value for 'void' type");
            case Type.BOOLEAN, Type.CHAR, Type.BYTE, Type.SHORT, Type.INT -> IntValue.VAL_0;
            case Type.FLOAT -> FloatValue.VAL_0;
            case Type.LONG -> LongValue.VAL_0;
            case Type.DOUBLE -> DoubleValue.VAL_0;
            case Type.ARRAY -> ArrayValue.of(type, Nullness.NULL);
            case Type.OBJECT -> ObjectValue.object(type, Nullness.NULL);
            default -> throw new IllegalValueException("Invalid type for new default value: " + type);
        };
    }

    /**
     * @param value
     * 		可能是基本类型的某个值
     * @param v
     * 		用于比较的基本类型值
     *
     * @return 当该值是基本类型，且其基本类型值等于给定字面量值时返回 {@code true}
     */
    static boolean isPrimitiveEqualTo(ReValue value, int v) {
        return switch (value) {
            case DoubleValue x -> x.isEqualTo(v);
            case FloatValue x -> x.isEqualTo(v);
            case IntValue x -> x.isEqualTo(v);
            case LongValue x -> x.isEqualTo(v);
            case ObjectValue _, UninitializedValue _ -> false;
        };
    }

    /**
     * @return 当精确内容已知时返回 {@code true}
     * 若此值为 {@link ObjectValue}，则 {@code null} 不计入其中，
     * 此时应使用 {@link ObjectValue#isNull()}
     */
    boolean hasKnownValue();

    /**
     * @return 值内容的类型
     */

    Type type();

    /**
     * 仅当我们值的类型比给定的另一个类型更宽泛时调用
     * 例如，若我们是 {@code ArrayList}，则可能将 {@code List} 视为 {@code other} 值，但反之绝不可能
     *
     * @param other
     * 		要合并的另一个值
     * 		应假定该值的类型与我们的 {@link #type()} 相同，或为其子类型
     *
     * @return 合并后的值
     *
     * @throws IllegalValueException
     * 		当给定值无法与此值合并时
     */

    ReValue mergeWith(ReValue other) throws IllegalValueException;
}
