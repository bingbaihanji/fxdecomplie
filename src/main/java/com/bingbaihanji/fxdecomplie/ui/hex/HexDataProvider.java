package com.bingbaihanji.fxdecomplie.ui.hex;

/**
 * 十六进制视图的数据提供者接口 
 * <p>
 * 该接口定义了从某个数据源(如文件、内存块等)读取字节数据的方法,
 * 供 {@link HexView} 组件获取要显示和解析的原始二进制数据 
 * </p>
 * <p>
 * 实现类需负责管理底层数据源的读取逻辑,并保证线程安全(如适用) 
 * </p>
 *
 * @author BingBaiHanJi
 * @see HexView
 * @see HexViewController
 */
public interface HexDataProvider {

    /**
     * 从指定地址开始读取字节数据到目标数组中 
     *
     * @param address 起始地址(从 0 开始,对应于数据源的开头)
     * @param dst     目标字节数组,用于存放读取的数据
     * @param offset  {@code dst} 数组中的起始偏移量,从该位置开始写入
     * @param length  要读取的字节数
     * @return 实际读取的字节数,可能小于 {@code length}(若到达数据末尾),
     *         若地址超出数据范围则返回 0 或负数(由具体实现决定)
     * @throws IndexOutOfBoundsException 如果 offset 或 length 不合法(如超出 dst 范围)
     * @throws IllegalArgumentException  如果参数为 null 或长度非法
     */
    int read(long address, byte[] dst, int offset, int length);

    /**
     * 获取数据源的总字节大小 
     *
     * @return 数据源的总长度(字节数),若无法获取则返回 -1
     */
    long getSize();

    /**
     * 从指定地址开始读取字节数据到目标数组中(简化版本) 
     * <p>
     * 默认实现调用 {@link #read(long, byte[], int, int)},并从 {@code dst} 的起始位置(偏移 0)写入 
     * </p>
     *
     * @param address 起始地址
     * @param dst     目标字节数组
     * @param length  要读取的字节数
     * @return 实际读取的字节数
     * @see #read(long, byte[], int, int)
     */
    default int read(long address, byte[] dst, int length) {
        return read(address, dst, 0, length);
    }
}