package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.custom.IArrayPayload;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.LiteralArg;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.PrimitiveType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxRuntimeException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 表示 Dex 字节码中的 fill-array-data 指令
 * <p>
 * 该指令用于填充数组的初始数据，根据元素大小（1/2/4/8 字节）推断元素类型，
 * 并支持将原始字节数组数据转换为字面量参数列表
 * </p>
 */
public final class FillArrayData extends InsnNode {

    /** 单字节元素类型：byte 或 boolean */
    private static final ArgType ONE_BYTE_TYPE = ArgType.unknown(PrimitiveType.BYTE, PrimitiveType.BOOLEAN);
    /** 双字节元素类型：short 或 char */
    private static final ArgType TWO_BYTES_TYPE = ArgType.unknown(PrimitiveType.SHORT, PrimitiveType.CHAR);
    /** 四字节元素类型：int 或 float */
    private static final ArgType FOUR_BYTES_TYPE = ArgType.unknown(PrimitiveType.INT, PrimitiveType.FLOAT);
    /** 八字节元素类型：long 或 double */
    private static final ArgType EIGHT_BYTES_TYPE = ArgType.unknown(PrimitiveType.LONG, PrimitiveType.DOUBLE);

    /** 数组原始数据（byte[]/short[]/int[]/long[] 之一） */
    private final Object data;
    /** 数组元素个数 */
    private final int size;
    /** 单个元素的字节大小（1/2/4/8） */
    private final int elemSize;
    /** 推断出的元素类型 */
    private ArgType elemType;

    /**
     * 根据输入载荷构造填充数组数据指令
     *
     * @param payload 数组数据载荷
     */
    public FillArrayData(IArrayPayload payload) {
        this(payload.getData(), payload.getSize(), payload.getElementSize());
    }

    /**
     * 根据原始数据、元素个数和元素大小构造填充数组数据指令
     *
     * @param data    原始数组数据
     * @param size    元素个数
     * @param elemSize 单个元素的字节大小
     */
    private FillArrayData(Object data, int size, int elemSize) {
        super(InsnType.FILL_ARRAY_DATA, 0);
        this.data = data;
        this.size = size;
        this.elemSize = elemSize;
        this.elemType = getElementType(elemSize);
    }

    /**
     * 根据元素宽度（字节数）推断对应的元素类型
     *
     * @param elementWidthUnit 元素宽度（0/1/2/4/8 字节）
     * @return 推断出的 ArgType
     * @throws JadxRuntimeException 当元素宽度未知时抛出
     */
    private static ArgType getElementType(int elementWidthUnit) {
        switch (elementWidthUnit) {
            case 1:
            case 0:
                return ONE_BYTE_TYPE;
            case 2:
                return TWO_BYTES_TYPE;
            case 4:
                return FOUR_BYTES_TYPE;
            case 8:
                return EIGHT_BYTES_TYPE;
            default:
                throw new JadxRuntimeException("Unknown array element width: " + elementWidthUnit);
        }
    }

    /**
     * 获取数组原始数据对象
     *
     * @return 原始数据（byte[]/short[]/int[]/long[] 之一）
     */
    public Object getData() {
        return data;
    }

    /**
     * 获取数组元素个数
     *
     * @return 元素数量
     */
    public int getSize() {
        return size;
    }

    /**
     * 获取数组元素类型
     *
     * @return 元素 ArgType
     */
    public ArgType getElementType() {
        return elemType;
    }

    /**
     * 将数组原始数据转换为指定类型的字面量参数列表
     * <p>
     * 根据元素大小遍历底层数组，将每个元素包装为 LiteralArg
     * </p>
     *
     * @param type 目标类型
     * @return 字面量参数列表
     * @throws JadxRuntimeException 当元素大小未知时抛出
     */
    public List<LiteralArg> getLiteralArgs(ArgType type) {
        List<LiteralArg> list = new ArrayList<>(size);
        Object array = data;
        switch (elemSize) {
            case 1:
                for (byte b : (byte[]) array) {
                    list.add(InsnArg.lit(b, type));
                }
                break;
            case 2:
                for (short b : (short[]) array) {
                    list.add(InsnArg.lit(b, type));
                }
                break;
            case 4:
                for (int b : (int[]) array) {
                    list.add(InsnArg.lit(b, type));
                }
                break;
            case 8:
                for (long b : (long[]) array) {
                    list.add(InsnArg.lit(b, type));
                }
                break;
            default:
                throw new JadxRuntimeException("Unknown type: " + data.getClass() + ", expected: " + type);
        }
        return list;
    }

    @Override
    public boolean isSame(InsnNode obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof FillArrayData) || !super.isSame(obj)) {
            return false;
        }
        FillArrayData other = (FillArrayData) obj;
        return elemType.equals(other.elemType) && data == other.data;
    }

    @Override
    public InsnNode copy() {
        FillArrayData copy = new FillArrayData(data, size, elemSize);
        copy.elemType = this.elemType;
        return copyCommonParams(copy);
    }

    /**
     * 将数组数据转换为可读字符串
     *
     * @return 数组数据的字符串表示
     */
    public String dataToString() {
        switch (elemSize) {
            case 1:
                return Arrays.toString((byte[]) data);
            case 2:
                return Arrays.toString((short[]) data);
            case 4:
                return Arrays.toString((int[]) data);
            case 8:
                return Arrays.toString((long[]) data);
            default:
                return "?";
        }
    }

    @Override
    public String toString() {
        return super.toString() + ", data: " + dataToString();
    }
}
