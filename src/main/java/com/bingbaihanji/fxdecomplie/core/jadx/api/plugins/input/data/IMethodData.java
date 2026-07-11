package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 方法数据接口
 * <p>
 * 表示从输入源解析出的单个方法的信息，包括方法引用、访问标志、字节码读取器及属性等
 * 用于在反编译流程中承载方法级别的元数据
 */
public interface IMethodData {

    /**
     * 获取该方法的引用信息（包含所属类、方法名和方法签名）
     *
     * @return 方法引用 {@link IMethodRef}
     */
    IMethodRef getMethodRef();

    /**
     * 获取方法的访问标志（access flags）
     *
     * @return 访问标志位掩码
     */
    int getAccessFlags();

    /**
     * 获取该方法的字节码读取器，用于读取方法体的指令数据
     *
     * @return 字节码读取器 {@link ICodeReader} 若方法无代码体（如抽象方法或本地方法）则返回 {@code null}
     */
    @Nullable
    ICodeReader getCodeReader();

    /**
     * 对该方法进行反汇编，返回其反汇编代码文本
     *
     * @return 反汇编后的方法代码字符串
     */
    String disassembleMethod();

    /**
     * 获取该方法上附加的属性列表（如注解、异常表等）
     *
     * @return 方法属性列表
     */
    List<IJadxAttribute> getAttributes();
}
