package com.bingbaihanji.fxdecomplie.core.jadx.zip.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * 基于 {@link ByteBuffer} 的 {@link InputStream} 实现
 * <p>
 * 该流将 ByteBuffer 作为底层数据源，支持 mark/reset 操作，
 * 适用于需要以流式方式读取 NIO 缓冲区的场景
 * </p>
 */
public class ByteBufferBackedInputStream extends InputStream {
    /** 底层数据缓冲区 */
    private final ByteBuffer buf;
    /** mark 操作记录的位置 */
    private int markedPosition = 0;

    /**
     * 使用指定的 ByteBuffer 构造输入流
     *
     * @param buf 作为数据源的 ByteBuffer，其当前位置作为流的起始位置
     */
    public ByteBufferBackedInputStream(ByteBuffer buf) {
        this.buf = buf;
    }

    /**
     * 从缓冲区读取一个字节
     *
     * @return 读取到的字节（0-255），如果缓冲区已无剩余数据则返回 -1
     */
    @Override
    public int read() throws IOException {
        if (!buf.hasRemaining()) {
            return -1;
        }
        return buf.get() & 0xFF;
    }

    /**
     * 从缓冲区读取多个字节到指定数组
     *
     * @param bytes 目标字节数组
     * @param off   目标数组的起始偏移量
     * @param len   最大读取字节数
     * @return 实际读取的字节数，如果缓冲区已无剩余数据则返回 -1
     */
    @Override
    @SuppressWarnings("NullableProblems")
    public int read(byte[] bytes, int off, int len) throws IOException {
        if (!buf.hasRemaining()) {
            return -1;
        }
        int readLen = Math.min(len, buf.remaining());
        buf.get(bytes, off, readLen);
        return readLen;
    }

    /**
     * 标记流支持 mark/reset 操作
     *
     * @return 始终返回 {@code true}
     */
    @Override
    public boolean markSupported() {
        return true;
    }

    /**
     * 记录当前读取位置，供后续 {@link #reset()} 恢复
     *
     * @param unused 标记读取上限（此实现中未使用）
     */
    @Override
    public synchronized void mark(int unused) {
        markedPosition = buf.position();
    }

    /**
     * 将读取位置重置为上次 {@link #mark(int)} 记录的位置
     */
    @Override
    public synchronized void reset() {
        buf.position(markedPosition);
    }
}
