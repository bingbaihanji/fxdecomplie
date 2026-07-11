package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.*;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.utils.Utils;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.JavaClassReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.AttributesReader;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.JavaAttrStorage;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.JavaAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.types.JavaAnnotationsAttr;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.utils.DisasmUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Java 类数据解析器。
 * <p>
 * 基于 {@link JavaClassReader} 读取到的原始字节，解析 {@code .class} 文件的结构（访问标志、类型、
 * 父类、接口、字段、方法及各类属性），并对外提供 {@link IClassData} 接口所需的访问能力。
 * 解析过程借助 {@link ClassOffsets} 记录的各段偏移量、{@link ConstPoolReader} 常量池读取器
 * 以及 {@link AttributesReader} 属性读取器完成。
 */
public class JavaClassData implements IClassData {
    /** 底层类字节读取器，提供原始字节及文件名等信息 */
    private final JavaClassReader clsReader;
    /** 类字节数据读取器，负责按偏移量读取具体字段 */
    private final DataReader data;
    /** 类文件各结构段的偏移量信息 */
    private final ClassOffsets offsets;
    /** 常量池读取器，用于解析常量池中的类、字符串、UTF8 等 */
    private final ConstPoolReader constPoolReader;
    /** 属性读取器，用于加载字段、方法及类级别的属性 */
    private final AttributesReader attributesReader;

    /**
     * 构造类数据解析器。
     *
     * @param clsReader 提供类原始字节及文件信息的类读取器
     */
    public JavaClassData(JavaClassReader clsReader) {
        this.clsReader = clsReader;
        this.data = new DataReader(clsReader.getData());
        this.offsets = new ClassOffsets(this.data);
        this.constPoolReader = new ConstPoolReader(clsReader, this, this.data.copy(), this.offsets);
        this.attributesReader = new AttributesReader(this, this.constPoolReader);
    }

    /**
     * 返回该类在输入文件中的偏移量（此处使用访问标志的偏移量）。
     */
    @Override
    public int getInputFileOffset() {
        return offsets.getAccessFlagsOffset();
    }

    /**
     * 返回当前实例自身作为副本（本实现为不可变解析器，无需真正复制）。
     */
    @Override
    public IClassData copy() {
        return this;
    }

    /**
     * 读取并返回类的访问标志。
     */
    @Override
    public int getAccessFlags() {
        return data.absPos(offsets.getAccessFlagsOffset()).readU2();
    }

    /**
     * 返回当前类的类型（全限定类型描述）。
     */
    @Override
    public String getType() {
        int idx = data.absPos(offsets.getClsTypeOffset()).readU2();
        return constPoolReader.getClass(idx);
    }

    /**
     * 返回父类的类型；若无父类（如 {@code java.lang.Object}）则返回 {@code null}。
     */
    @Override
    @Nullable
    public String getSuperType() {
        int idx = data.absPos(offsets.getSuperTypeOffset()).readU2();
        if (idx == 0) {
            return null;
        }
        return constPoolReader.getClass(idx);
    }

    /**
     * 返回当前类实现的所有接口类型列表。
     */
    @Override
    public List<String> getInterfacesTypes() {
        data.absPos(offsets.getInterfacesOffset());
        return data.readClassesList(constPoolReader);
    }

    /**
     * 返回该类所属输入文件的文件名。
     */
    @Override
    public String getInputFileName() {
        return this.clsReader.getFileName();
    }

    /**
     * 依次遍历类中的所有字段和方法，并将其提供给对应的消费者。
     *
     * @param fieldsConsumer 字段消费者
     * @param mthConsumer    方法消费者
     */
    @Override
    public void visitFieldsAndMethods(ISeqConsumer<IFieldData> fieldsConsumer, ISeqConsumer<IMethodData> mthConsumer) {
        int clsIdx = data.absPos(offsets.getClsTypeOffset()).readU2();
        String classType = constPoolReader.getClass(clsIdx);
        DataReader reader = data.absPos(offsets.getFieldsOffset()).copy();
        int fieldsCount = reader.readU2();
        fieldsConsumer.init(fieldsCount);
        if (fieldsCount != 0) {
            JavaFieldData field = new JavaFieldData();
            field.setParentClassType(classType);
            for (int i = 0; i < fieldsCount; i++) {
                parseField(reader, field);
                fieldsConsumer.accept(field);
            }
        }

        int methodsCount = reader.readU2();
        mthConsumer.init(methodsCount);
        if (methodsCount != 0) {
            JavaMethodRef methodRef = new JavaMethodRef();
            methodRef.setParentClassType(classType);
            JavaMethodData method = new JavaMethodData(this, methodRef);
            for (int i = 0; i < methodsCount; i++) {
                parseMethod(reader, method, i);
                mthConsumer.accept(method);
            }
        }
    }

    /**
     * 解析单个字段，将访问标志、名称、类型及属性写入给定的字段对象。
     *
     * @param reader 定位到字段起始位置的读取器
     * @param field  用于承载解析结果的字段对象（会被复用）
     */
    private void parseField(DataReader reader, JavaFieldData field) {
        int accessFlags = reader.readU2();
        int nameIdx = reader.readU2();
        int typeIdx = reader.readU2();
        JavaAttrStorage attributes = attributesReader.loadAll(reader);

        field.setAccessFlags(accessFlags);
        field.setName(constPoolReader.getUtf8(nameIdx));
        field.setType(constPoolReader.getUtf8(typeIdx));
        field.setAttributes(attributes);
    }

    /**
     * 解析单个方法，构建方法引用并将访问标志、属性写入给定的方法对象。
     *
     * @param reader 定位到方法起始位置的读取器
     * @param method 用于承载解析结果的方法对象（会被复用）
     * @param id     方法在类中的序号，用于生成唯一 id
     */
    private void parseMethod(DataReader reader, JavaMethodData method, int id) {
        int accessFlags = reader.readU2();
        int nameIdx = reader.readU2();
        int descriptorIdx = reader.readU2();
        JavaAttrStorage attributes = attributesReader.loadAll(reader);

        JavaMethodRef methodRef = method.getMethodRef();
        methodRef.reset();
        methodRef.initUniqId(clsReader, id, false);
        methodRef.setName(constPoolReader.getUtf8(nameIdx));
        methodRef.setDescr(constPoolReader.getUtf8(descriptorIdx));

        if ("<init>".equals(methodRef.getName())) {
            accessFlags |= AccessFlags.CONSTRUCTOR; // Java 字节码本身不使用该标志，这里手动补上构造方法标志
        }

        method.setData(accessFlags, attributes);
    }

    /**
     * 返回底层的类字节数据读取器。
     */
    public DataReader getData() {
        return data;
    }

    /**
     * 返回类级别的属性列表（如注解、内部类、源文件、签名等）。
     */
    @Override
    public List<IJadxAttribute> getAttributes() {
        data.absPos(offsets.getAttributesOffset());
        JavaAttrStorage attributes = attributesReader.loadAll(data);
        int size = attributes.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        List<IJadxAttribute> list = new ArrayList<>(size);
        Utils.addToList(list, JavaAnnotationsAttr.merge(attributes));
        Utils.addToList(list, attributes.get(JavaAttrType.INNER_CLASSES));
        Utils.addToList(list, attributes.get(JavaAttrType.SOURCE_FILE));
        Utils.addToList(list, attributes.get(JavaAttrType.SIGNATURE));
        return list;
    }

    /**
     * 从类属性段读取并加载指定类型的单个类级别属性。
     *
     * @param reader 读取器
     * @param type   要加载的属性类型
     * @param <T>    属性类型
     * @return 加载到的属性对象
     */
    public <T extends IJavaAttribute> T loadClassAttribute(DataReader reader, JavaAttrType<T> type) {
        reader.absPos(offsets.getAttributesOffset());
        return attributesReader.loadOne(reader, type);
    }

    /**
     * 返回该类的反汇编代码文本。
     */
    @Override
    public String getDisassembledCode() {
        return DisasmUtils.get(data.getBytes());
    }

    /**
     * 返回底层的类字节读取器。
     */
    public JavaClassReader getClsReader() {
        return clsReader;
    }

    /**
     * 返回类文件各结构段的偏移量信息。
     */
    public ClassOffsets getOffsets() {
        return offsets;
    }

    /**
     * 返回常量池读取器。
     */
    public ConstPoolReader getConstPoolReader() {
        return constPoolReader;
    }

    /**
     * 返回属性读取器。
     */
    public AttributesReader getAttributesReader() {
        return attributesReader;
    }

    @Override
    public String toString() {
        return getInputFileName();
    }
}
