package com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.debuginfo;

import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.plugins.input.java.data.attributes.IJavaAttributeReader;

import java.util.HashMap;
import java.util.Map;

/**
 * LineNumberTable 属性
 * <p>
 * 表示 class 文件中的 LineNumberTable 属性，建立字节码偏移量 (offset)到
 * 源代码行号的映射关系，用于调试信息中的行号定位
 * </p>
 */
public class LineNumberTableAttr implements IJavaAttribute {

    /** 字节码偏移量到源代码行号的映射 */
    private final Map<Integer, Integer> lineMap;

    /**
     * 构造一个行号表属性实例
     *
     * @param sourceLineMap 偏移量到行号的映射
     */
    public LineNumberTableAttr(Map<Integer, Integer> sourceLineMap) {
        this.lineMap = sourceLineMap;
    }

    /**
     * 返回该属性的读取器工厂方法
     * <p>
     * 读取器从 class 数据中解析 LineNumberTable 属性结构：先读取表长度，
     * 然后逐条读取偏移量和行号对，最终构建 {@link LineNumberTableAttr} 实例
     * </p>
     *
     * @return 用于解析 LineNumberTable 属性的 {@link IJavaAttributeReader}
     */
    public static IJavaAttributeReader reader() {
        return (clsData, reader) -> {
            int len = reader.readU2();
            Map<Integer, Integer> map = new HashMap<>(len);
            for (int i = 0; i < len; i++) {
                int offset = reader.readU2();
                int line = reader.readU2();
                map.put(offset, line);
            }
            return new LineNumberTableAttr(map);
        };
    }

    /**
     * 获取偏移量到行号的映射表
     *
     * @return 字节码偏移量 (key)到源代码行号 (value)的映射
     */
    public Map<Integer, Integer> getLineMap() {
        return lineMap;
    }
}
