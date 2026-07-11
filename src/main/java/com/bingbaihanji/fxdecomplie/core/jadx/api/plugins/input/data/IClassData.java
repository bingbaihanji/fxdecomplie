package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 类数据接口
 * <p>
 * 表示从输入源解析出的单个类的完整信息，包括类型、访问标志、父类、实现接口、
 * 字段、方法及其它属性等用于在反编译流程中承载类级别的元数据
 */
public interface IClassData {

    /**
     * 创建当前类数据的副本
     * <p>
     * 由于遍历过程中类数据实例可能被复用，需要长期持有数据时应先进行拷贝
     *
     * @return 当前类数据的副本
     */
    IClassData copy();

    /**
     * 获取该类所属的输入文件名 (如所在的 JAR/DEX 文件名)
     *
     * @return 输入文件名
     */
    String getInputFileName();

    /**
     * 获取类的类型描述符 (完整类型签名)
     *
     * @return 类的类型描述符
     */
    String getType();

    /**
     * 获取类的访问标志 (access flags)
     *
     * @return 访问标志位掩码
     */
    int getAccessFlags();

    /**
     * 获取该类在输入文件中的偏移量
     *
     * @return 输入文件内的偏移量
     */
    int getInputFileOffset();

    /**
     * 获取父类的类型描述符
     *
     * @return 父类类型描述符 若无父类 (如 {@code java.lang.Object})则返回 {@code null}
     */
    @Nullable
    String getSuperType();

    /**
     * 获取该类实现的所有接口的类型描述符列表
     *
     * @return 接口类型描述符列表
     */
    List<String> getInterfacesTypes();

    /**
     * 依次遍历该类的字段和方法，并分别交由对应的消费者处理
     *
     * @param fieldsConsumer 用于接收并处理每个 {@link IFieldData} 字段数据的消费者
     * @param mthConsumer    用于接收并处理每个 {@link IMethodData} 方法数据的消费者
     */
    void visitFieldsAndMethods(ISeqConsumer<IFieldData> fieldsConsumer, ISeqConsumer<IMethodData> mthConsumer);

    /**
     * 获取该类上附加的属性列表 (如注解、签名等)
     *
     * @return 类属性列表
     */
    List<IJadxAttribute> getAttributes();

    /**
     * 获取该类的反汇编代码文本
     *
     * @return 反汇编后的代码字符串
     */
    String getDisassembledCode();
}
