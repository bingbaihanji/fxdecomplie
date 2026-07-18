package com.bingbaihanji.fxdecomplie.ui.hex.analyzer;

import javafx.scene.paint.Color;

import java.util.List;

/**
 * 数据分析器接口,用于从二进制数据中提取特定结构信息 
 * <p>
 * 实现类应解析给定字节范围内的数据,生成一系列带标签和颜色的分析结果,
 * 以供 HexView 界面展示 
 * </p>
 *
 * @author BingBaiHanJi
 * @see com.bingbaihanji.fxdecomplie.ui.hex.HexViewController
 */
public interface DataAnalyzer {

    /**
     * 获取分析器的名称,用于 UI 标识 
     *
     * @return 分析器名称(例如 "ClassFile", "Elf" 等)
     */
    String getName();

    /**
     * 对指定数据片段执行分析 
     *
     * @param address 片段起始的绝对地址(用于定位)
     * @param data    完整的原始字节数组
     * @param offset  当前分析片段在 {@code data} 中的起始偏移
     * @param length  片段长度
     * @return 分析结果列表,每个结果包含标签、值和显示颜色
     */
    List<Result> analyze(long address, byte[] data, int offset, int length);

    /**
     * 表示一个分析结果项 
     *
     * @param label 字段名称或类型说明(例如 "Magic", "Version")
     * @param value 字段的十六进制或可读值
     * @param color 该字段在 HexView 中的高亮颜色
     */
    record Result(String label, String value, Color color) {
    }
}