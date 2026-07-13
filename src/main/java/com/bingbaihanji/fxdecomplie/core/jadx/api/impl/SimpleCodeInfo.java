package com.bingbaihanji.fxdecomplie.core.jadx.api.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeMetadata;

/**
 * {@link ICodeInfo} 的简单实现，不包含元数据支持
 * <p>
 * 该类直接持有一个代码字符串，适用于不需要代码元数据 (如行号映射 注解等)的场景
 * 所有与元数据相关的方法均返回空或 false
 * </p>
 */
public class SimpleCodeInfo implements ICodeInfo {

    private final String code;

    /**
     * 使用指定的代码字符串构造 SimpleCodeInfo 实例
     *
     * @param code 反编译后的代码字符串
     */
    public SimpleCodeInfo(String code) {
        this.code = code;
    }

    /**
     * 获取代码字符串
     *
     * @return 反编译后的代码字符串
     */
    @Override
    public String getCodeStr() {
        return code;
    }

    /**
     * 获取代码元数据该简单实现始终返回空元数据
     *
     * @return 空的 {@link ICodeMetadata} 实例
     */
    @Override
    public ICodeMetadata getCodeMetadata() {
        return ICodeMetadata.EMPTY;
    }

    /**
     * 判断是否包含元数据该简单实现始终返回 false
     *
     * @return 始终返回 {@code false}
     */
    @Override
    public boolean hasMetadata() {
        return false;
    }

    /**
     * 返回代码字符串
     *
     * @return 反编译后的代码字符串
     */
    @Override
    public String toString() {
        return code;
    }
}
