package com.bingbaihanji.fxdecomplie.ui.hex.highlight;

import com.bingbaihanji.fxdecomplie.ui.hex.HexDataProvider;
import com.bingbaihanji.fxdecomplie.ui.hex.model.PatternModel;

/**
 * 内置高亮器接口,用于识别特定格式的二进制数据并为其添加结构化的颜色标记 
 * <p>
 * 实现类通过 {@link #matches(HexDataProvider)} 判断数据是否匹配自身的格式特征,
 * 若匹配则通过 {@link #highlight(HexDataProvider, PatternModel)} 方法向模式模型
 * 添加区域({@link PatternModel.Region})来标记不同逻辑部分 
 * </p>
 * <p>
 * 例如：{@link JavaBytecodeHighlighter} 可识别 Java 类文件(魔数 0xCAFEBABE),
 * 并高亮 magic、版本、常量池、字段、方法等结构 
 * </p>
 *
 * @author BingBaiHanJi
 * @see PatternModel
 * @see HexDataProvider
 */
public interface BuiltinHighlighter {

    /**
     * 获取高亮器的名称,用于 UI 展示或日志 
     *
     * @return 高亮器名称,如 "Java Bytecode"
     */
    String getName();

    /**
     * 检测给定的数据提供者是否匹配此高亮器支持的数据格式 
     * <p>
     * 通常检查文件头部的魔数、签名等特征字节 
     * </p>
     *
     * @param provider 数据提供者,用于读取数据
     * @return 若数据格式匹配则返回 {@code true}
     */
    boolean matches(HexDataProvider provider);

    /**
     * 对匹配的数据执行高亮操作,向模式模型中添加区域(Region) 
     * <p>
     * 实现应解析数据结构,为每个逻辑段创建带颜色、名称和描述的
     * {@link PatternModel.Region} 对象,并添加到模型中 
     * </p>
     *
     * @param provider 数据提供者
     * @param model    目标模式模型(已有结构会被清除)
     */
    void highlight(HexDataProvider provider, PatternModel model);
}