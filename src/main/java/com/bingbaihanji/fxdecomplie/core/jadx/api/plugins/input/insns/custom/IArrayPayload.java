package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.insns.custom;

/**
 * 数组数据负载接口
 * <p>
 * 表示 fill-array-data 等指令所携带的数组填充数据，
 * 描述数组元素的数量 单个元素大小以及实际数据内容
 */
public interface IArrayPayload extends ICustomPayload {
    /**
     * 获取数组元素的数量
     *
     * @return 元素个数
     */
    int getSize();

    /**
     * 获取单个数组元素占用的字节数
     *
     * @return 单个元素的大小 (字节)
     */
    int getElementSize();

    /**
     * 获取数组的原始数据
     *
     * @return 数组数据对象
     */
    Object getData();
}
