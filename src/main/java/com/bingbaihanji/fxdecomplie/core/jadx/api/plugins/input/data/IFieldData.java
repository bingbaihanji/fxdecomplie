package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;

import java.util.List;

/**
 * 字段数据接口
 * <p>
 * 表示从输入源解析出的单个字段的信息继承 {@link IFieldRef}，在字段引用 (所属类 
 * 字段名 字段类型)的基础上，额外提供访问标志与属性信息
 */
public interface IFieldData extends IFieldRef {

    /**
     * 获取字段的访问标志 (access flags)
     *
     * @return 访问标志位掩码
     */
    int getAccessFlags();

    /**
     * 获取该字段上附加的属性列表 (如注解 常量值 签名等)
     *
     * @return 字段属性列表
     */
    List<IJadxAttribute> getAttributes();
}
