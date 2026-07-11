package com.bingbaihanji.fxdecomplie.core.jadx.zip.io;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 带读取大小限制的 {@link FilterInputStream} 包装流。
 * <p>
 * 该流对底层的 {@link InputStream} 进行包装，限制总共可读取的字节数。
 * 当累计读取的字节数超过预设的 {@code maxSize} 时，会抛出
 * {@link IllegalStateException}，用于防止 ZIP 炸弹等攻击场景中
 * 恶意构造的压缩数据导致的内存溢出。
 * </p>
 */
public class LimitedInputStream extends FilterInputStream {
    /** 允许读取的最大字节数 */
    private final long maxSize;

    /** 当前已读取的字节位置 */
    private long currentPos;
    /** mark 操作时记录的字节位置 */
    private long markPos;

    /**
     * 构造一个带读取限制的输入流。
     *
     * @param in      底层输入流
     * @param maxSize 允许读取的最大字节数
     */
    public LimitedInputStream(InputStream in, long maxSize) {
        super(in);
        this.maxSize = maxSize;
    }

    /**
     * 累加已读取的字节数并检查是否超出限制。
     *
     * @param count 本次读取的字节数
     * @throws IllegalStateException 如果累计读取量超过 {@code maxSize}
     */
    private void addAndCheckPos(long count) {
        currentPos += count;
        if (currentPos > maxSize) {
            throw new IllegalStateException("Read limit exceeded");
        }
    }

    /**
     * 读取单个字节，并更新已读取计数。
     *
     * @return 读取到的字节（0-255），如果流已结束则返回 -1
     */
    @Override
    public int read() throws IOException {
        int data = super.read();
        if (data != -1) {
            addAndCheckPos(1);
        }
        return data;
    }

    /**
     * 读取多个字节到指定数组，并更新已读取计数。
     *
     * @param b   目标字节数组
     * @param off 目标数组的起始偏移量
     * @param len 最大读取字节数
     * @return 实际读取的字节数，如果流已结束则返回 -1
     */
    @SuppressWarnings("NullableProblems")
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int count = super.read(b, off, len);
        if (count > 0) {
            addAndCheckPos(count);
        }
        return count;
    }

    /**
     * 跳过指定字节数，并更新已读取计数。
     *
     * @param n 要跳过的字节数
     * @return 实际跳过的字节数
     */
    @Override
    public long skip(long n) throws IOException {
        long skipped = super.skip(n);
        if (skipped > 0) {
            addAndCheckPos(skipped);
        }
        return skipped;
    }

    /**
     * 标记当前读取位置，同时记录当前的已读取计数。
     *
     * @param readLimit 标记读取上限
     */
    @Override
    public void mark(int readLimit) {
        super.mark(readLimit);
        markPos = currentPos;
    }

    /**
     * 重置到上次标记的位置，同时恢复已读取计数。
     */
    @Override
    public void reset() throws IOException {
        super.reset();
        currentPos = markPos;
    }
}
