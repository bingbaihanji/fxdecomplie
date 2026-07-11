package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.impl;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionFlag;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.options.OptionType;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 选项构建器接口，用于以流式方式配置插件选项
 * <p>
 * 所有选项必须提供描述 ({@link #description})、解析器 ({@link #parser})、
 * 格式化器 ({@link #formatter})和设置器 ({@link #setter})
 *
 * @param <T> 选项值的类型
 */
public interface OptionBuilder<T> {

    /**
     * 设置选项描述 (必填)
     *
     * @param desc 选项的描述文本
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> description(String desc);

    /**
     * 设置选项的默认值
     *
     * @param defValue 默认值
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> defaultValue(T defValue);

    /**
     * 设置将输入字符串解析为选项值的函数 (必填)
     *
     * @param parser 解析函数，接收字符串输入，返回解析后的选项值
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> parser(Function<String, T> parser);

    /**
     * 设置将选项值格式化为字符串的函数，用于生成帮助信息 (必填)
     *
     * @param formatter 格式化函数，接收选项值，返回字符串表示
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> formatter(Function<T, String> formatter);

    /**
     * 设置保存/应用已解析选项值的回调函数 (必填)
     *
     * @param setter 设置器回调，接收解析后的选项值并执行保存或应用操作
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> setter(Consumer<T> setter);

    /**
     * 设置选项的可选值列表
     *
     * @param values 可选的选项值列表
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> values(List<T> values);

    /**
     * 设置选项类型
     *
     * @param optionType 选项类型
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> type(OptionType optionType);

    /**
     * 设置选项标志位
     *
     * @param flags 一个或多个选项标志
     * @return 当前构建器实例，支持链式调用
     */
    OptionBuilder<T> flags(OptionFlag... flags);
}
