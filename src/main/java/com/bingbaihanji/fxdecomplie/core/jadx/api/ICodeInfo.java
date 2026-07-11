package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.impl.SimpleCodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeMetadata;

/**
 * 代码信息接口，表示反编译后的代码结果及其元数据
 * <p>
 * 该接口封装了反编译输出的代码字符串以及可选的元数据信息（如定义位置、
 * 行号映射等）当无需元数据支持时，可使用 {@link #EMPTY} 常量作为空结果
 * </p>
 */
public interface ICodeInfo {

    /**
     * 空的代码信息常量，其代码字符串为空字符串且不包含元数据
     */
    ICodeInfo EMPTY = new SimpleCodeInfo("");

    /**
     * 获取反编译后的源代码字符串
     *
     * @return 反编译源代码字符串，不会返回 {@code null}
     */
    String getCodeStr();

    /**
     * 获取与此代码信息关联的元数据
     *
     * @return 代码元数据对象，可能为 {@code null}
     */
    ICodeMetadata getCodeMetadata();

    /**
     * 检查此代码信息是否包含元数据
     *
     * @return 如果包含元数据则返回 {@code true}，否则返回 {@code false}
     */
    boolean hasMetadata();
}
