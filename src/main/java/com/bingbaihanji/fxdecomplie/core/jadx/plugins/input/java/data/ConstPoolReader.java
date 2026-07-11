package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.*;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.EncodedValue;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.CallSite;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.FieldRefHandle;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.impl.MethodRefHandle;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaClassReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.JavaAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types.JavaBootstrapMethodsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types.data.RawBootstrapMethod;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.DescriptorParser;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.JavaClassParseException;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.ModifiedUTF8Decoder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 常量池读取器
 * <p>
 * 依据 {@link ClassOffsets} 记录的各常量池项偏移，从 class 文件的字节数据中
 * 按索引读取常量池条目，包括类引用、字段引用、方法引用、调用点、方法句柄
 * 以及各类字面量常量，并将其转换为上层需要的数据结构
 */
public class ConstPoolReader {
    /** 所属的类读取器 */
    private final JavaClassReader clsReader;
    /** 所属的类数据 */
    private final JavaClassData clsData;
    /** 底层字节数据读取器 */
    private final DataReader data;
    /** 常量池各条目的偏移信息 */
    private final ClassOffsets offsets;

    /**
     * 构造常量池读取器
     *
     * @param clsReader     所属的类读取器
     * @param javaClassData 所属的类数据
     * @param data          底层字节数据读取器
     * @param offsets       常量池各条目的偏移信息
     */
    public ConstPoolReader(JavaClassReader clsReader, JavaClassData javaClassData, DataReader data, ClassOffsets offsets) {
        this.clsReader = clsReader;
        this.clsData = javaClassData;
        this.data = data;
        this.offsets = offsets;
    }

    /**
     * 读取指定索引处的类常量，返回其规范化后的类型描述符
     *
     * @param idx 常量池索引
     * @return 类型描述符，索引对应的名称为空时返回 {@code null}
     */
    @Nullable
    public String getClass(int idx) {
        jumpToData(idx);
        int nameIdx = data.readU2();
        return fixType(getUtf8(nameIdx));
    }

    /**
     * 读取指定索引处的字段引用
     *
     * @param idx 常量池索引
     * @return 字段引用数据
     */
    public IFieldRef getFieldRef(int idx) {
        jumpToData(idx);
        int clsIdx = data.readU2();
        int nameTypeIdx = data.readU2();
        jumpToData(nameTypeIdx);
        int nameIdx = data.readU2();
        int typeIdx = data.readU2();

        JavaFieldData fieldData = new JavaFieldData();
        fieldData.setParentClassType(getClass(clsIdx));
        fieldData.setName(getUtf8(nameIdx));
        fieldData.setType(getUtf8(typeIdx));
        return fieldData;
    }

    /**
     * 读取指定索引处字段引用的类型描述符
     *
     * @param idx 常量池索引
     * @return 字段类型描述符
     */
    public String getFieldType(int idx) {
        jumpToData(idx);
        data.skip(2);
        int nameTypeIdx = data.readU2();
        jumpToData(nameTypeIdx);
        data.skip(2);
        int typeIdx = data.readU2();
        return getUtf8(typeIdx);
    }

    /**
     * 读取指定索引处的方法引用
     *
     * @param idx 常量池索引
     * @return 方法引用数据
     */
    public IMethodRef getMethodRef(int idx) {
        jumpToData(idx);
        int clsIdx = data.readU2();
        int nameTypeIdx = data.readU2();
        jumpToData(nameTypeIdx);
        int nameIdx = data.readU2();
        int descIdx = data.readU2();

        JavaMethodRef mthRef = new JavaMethodRef();
        mthRef.initUniqId(clsReader, idx, true);
        mthRef.setParentClassType(getClass(clsIdx));
        mthRef.setName(getUtf8(nameIdx));
        mthRef.setDescr(getUtf8(descIdx));
        return mthRef;
    }

    /**
     * 读取指定索引处的调用点 (invokedynamic)
     *
     * @param idx 常量池索引
     * @return 调用点数据
     * @throws JavaClassParseException 当常量类型为字段调用点 (尚未实现)或非预期类型时抛出
     */
    public ICallSite getCallSite(int idx) {
        ConstantType constType = jumpToConst(idx);
        switch (constType) {
            case INVOKE_DYNAMIC:
                int bootstrapMthIdx = data.readU2();
                int nameAndTypeIdx = data.readU2();
                jumpToData(nameAndTypeIdx);
                int nameIdx = data.readU2();
                int descIdx = data.readU2();
                return resolveMethodCallSite(bootstrapMthIdx, nameIdx, descIdx);
            case DYNAMIC:
                throw new JavaClassParseException("Field call site not yet implemented");
            default:
                throw new JavaClassParseException("Unexpected tag type for call site: " + constType);
        }
    }

    /**
     * 根据引导方法及名称/描述符解析出方法调用点
     *
     * @param bootstrapMthIdx 引导方法索引
     * @param nameIdx         名称常量索引
     * @param descIdx         描述符常量索引
     * @return 解析出的调用点
     * @throws JavaClassParseException 当缺少 BootstrapMethods 属性时抛出
     */
    private CallSite resolveMethodCallSite(int bootstrapMthIdx, int nameIdx, int descIdx) {
        JavaBootstrapMethodsAttr bootstrapMethodsAttr = clsData.loadClassAttribute(data, JavaAttrType.BOOTSTRAP_METHODS);
        if (bootstrapMethodsAttr == null) {
            throw new JavaClassParseException("Unexpected missing BootstrapMethods attribute");
        }
        RawBootstrapMethod rawBootstrapMethod = bootstrapMethodsAttr.getList().get(bootstrapMthIdx);

        List<EncodedValue> values = new ArrayList<>(6);
        values.add(new EncodedValue(EncodedType.ENCODED_METHOD_HANDLE, getMethodHandle(rawBootstrapMethod.getMethodHandleIdx())));
        values.add(new EncodedValue(EncodedType.ENCODED_STRING, getUtf8(nameIdx)));
        values.add(new EncodedValue(EncodedType.ENCODED_METHOD_TYPE, DescriptorParser.parseToMethodProto(getUtf8(descIdx))));
        for (int argConstIdx : rawBootstrapMethod.getArgs()) {
            values.add(readAsEncodedValue(argConstIdx));
        }
        return new CallSite(values);
    }

    /**
     * 读取指定索引处的方法句柄
     *
     * @param idx 常量池索引
     * @return 方法句柄，字段类型句柄返回 {@link FieldRefHandle}，否则返回 {@link MethodRefHandle}
     */
    private IMethodHandle getMethodHandle(int idx) {
        jumpToData(idx);
        int kind = data.readU1();
        int refIdx = data.readU2();
        MethodHandleType handleType = convertMethodHandleKind(kind);
        if (handleType.isField()) {
            return new FieldRefHandle(handleType, getFieldRef(refIdx));
        }
        return new MethodRefHandle(handleType, getMethodRef(refIdx));
    }

    /**
     * 将 class 文件中的方法句柄种类 (reference_kind)转换为 {@link MethodHandleType}
     *
     * @param kind 方法句柄种类值 (1-9)
     * @return 对应的方法句柄类型
     * @throws IllegalArgumentException 当种类值未知时抛出
     */
    private MethodHandleType convertMethodHandleKind(int kind) {
        switch (kind) {
            case 1:
                return MethodHandleType.STATIC_PUT;
            case 2:
                return MethodHandleType.STATIC_GET;
            case 3:
                return MethodHandleType.INSTANCE_PUT;
            case 4:
                return MethodHandleType.INSTANCE_GET;
            case 5:
                return MethodHandleType.INVOKE_INSTANCE;
            case 6:
                return MethodHandleType.INVOKE_STATIC;
            case 7:
                return MethodHandleType.INVOKE_DIRECT;
            case 8:
                return MethodHandleType.INVOKE_CONSTRUCTOR;
            case 9:
                return MethodHandleType.INVOKE_INTERFACE;
            default:
                throw new IllegalArgumentException("Unknown method handle type: " + kind);
        }
    }

    /**
     * 读取指定索引处的 UTF-8 字符串常量
     *
     * @param idx 常量池索引，为 0 时表示无值
     * @return 字符串内容，索引为 0 时返回 {@code null}
     */
    public String getUtf8(int idx) {
        if (idx == 0) {
            return null;
        }
        jumpToData(idx);
        return readString();
    }

    /**
     * 跳转到指定索引的常量条目并读取其标签，返回常量类型
     *
     * @param idx 常量池索引
     * @return 该常量的类型
     */
    public ConstantType jumpToConst(int idx) {
        jumpToTag(idx);
        return ConstantType.getTypeByTag(data.readU1());
    }

    /**
     * 从当前位置读取一个带长度前缀的 Modified UTF-8 字符串
     *
     * @return 解析出的字符串
     */
    public String readString() {
        int len = data.readU2();
        byte[] bytes = data.readBytes(len);
        return parseString(bytes);
    }

    /**
     * 从当前位置读取一个无符号 2 字节整数
     *
     * @return 读取到的值
     */
    public int readU2() {
        return data.readU2();
    }

    /**
     * 从当前位置读取一个无符号 4 字节整数
     *
     * @return 读取到的值
     */
    public int readU4() {
        return data.readU4();
    }

    /**
     * 从当前位置读取一个无符号 8 字节整数
     *
     * @return 读取到的值
     */
    public long readU8() {
        return data.readU8();
    }

    /**
     * 读取指定索引处的 Integer 常量
     *
     * @param idx 常量池索引
     * @return int 值
     */
    public int getInt(int idx) {
        jumpToData(idx);
        return data.readS4();
    }

    /**
     * 读取指定索引处的 Long 常量
     *
     * @param idx 常量池索引
     * @return long 值
     */
    public long getLong(int idx) {
        jumpToData(idx);
        return data.readS8();
    }

    /**
     * 读取指定索引处的 Double 常量
     *
     * @param idx 常量池索引
     * @return double 值
     */
    public double getDouble(int idx) {
        jumpToData(idx);
        return Double.longBitsToDouble(data.readU8());
    }

    /**
     * 读取指定索引处的 Float 常量
     *
     * @param idx 常量池索引
     * @return float 值
     */
    public float getFloat(int idx) {
        jumpToData(idx);
        return Float.intBitsToFloat(data.readU4());
    }

    /**
     * 将指定索引处的常量读取为编码值 ({@link EncodedValue})
     *
     * @param idx 常量池索引
     * @return 对应的编码值
     * @throws JavaClassParseException 当该常量类型无法编码为编码值时抛出
     */
    public EncodedValue readAsEncodedValue(int idx) {
        ConstantType constantType = jumpToConst(idx);
        switch (constantType) {
            case UTF8:
                return new EncodedValue(EncodedType.ENCODED_STRING, readString());
            case STRING:
                return new EncodedValue(EncodedType.ENCODED_STRING, getUtf8(readU2()));
            case INTEGER:
                return new EncodedValue(EncodedType.ENCODED_INT, data.readS4());
            case FLOAT:
                return new EncodedValue(EncodedType.ENCODED_FLOAT, Float.intBitsToFloat(data.readU4()));
            case LONG:
                return new EncodedValue(EncodedType.ENCODED_LONG, data.readS8());
            case DOUBLE:
                return new EncodedValue(EncodedType.ENCODED_DOUBLE, Double.longBitsToDouble(data.readU8()));
            case CLASS:
                return new EncodedValue(EncodedType.ENCODED_TYPE, getClass(idx));
            case METHOD_TYPE:
                return new EncodedValue(EncodedType.ENCODED_METHOD_TYPE, DescriptorParser.parseToMethodProto(getUtf8(readU2())));
            case METHOD_HANDLE:
                return new EncodedValue(EncodedType.ENCODED_METHOD_HANDLE, getMethodHandle(idx));

            default:
                throw new JavaClassParseException("Can't encode constant " + constantType + " as encoded value");
        }
    }

    /**
     * 将 Modified UTF-8 字节解码为字符串
     *
     * @param bytes 字节内容
     * @return 解码后的字符串
     */
    @NotNull
    private String parseString(byte[] bytes) {
        return ModifiedUTF8Decoder.decodeString(bytes);
    }

    /**
     * 规范化类名，使其成为合法的类型描述符
     * <p>
     * 数组类型 (以 {@code [} 开头)原样返回 已带 {@code L}/{@code T} 前缀且以
     * {@code ;} 结尾的原样返回 其余情况补全为 {@code L<类名>;} 形式
     *
     * @param clsName 原始类名
     * @return 规范化后的类型描述符
     */
    private String fixType(String clsName) {
        switch (clsName.charAt(0)) {
            case '[':
                return clsName;

            case 'L':
            case 'T':
                if (clsName.endsWith(";")) {
                    return clsName;
                }
                break;
        }
        return 'L' + clsName + ';';
    }

    /**
     * 将读取位置跳转到指定索引常量条目的数据起始处
     *
     * @param idx 常量池索引
     */
    private void jumpToData(int idx) {
        data.absPos(offsets.getOffsetOfConstEntry(idx));
    }

    /**
     * 将读取位置跳转到指定索引常量条目的标签处 (数据起始前 1 字节)
     *
     * @param idx 常量池索引
     */
    private void jumpToTag(int idx) {
        data.absPos(offsets.getOffsetOfConstEntry(idx) - 1);
    }
}
