 
package com.bingbaihanji.classgraph.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;

/**
 * 一个代理 {@link InputStream} 实现，可以为 JDK 7 编译，
 * 但通过反射支持 JDK 8 中添加的方法
 */
public class ProxyingInputStream extends InputStream {
    private static Method readAllBytes;
    private static Method readNBytes1;
    private static Method readNBytes3;
    private static Method skipNBytes;
    private static Method transferTo;

    static {
        // 通过反射使用 JDK 7 中不存在的 InputStream 方法
        // TODO 一旦要求 JDK 8，切换到直接方法调用，并加回缺失的 @Override 注解
        try {
            readAllBytes = InputStream.class.getDeclaredMethod("readAllBytes");
        } catch (NoSuchMethodException | SecurityException e1) {
            // 忽略
        }
        try {
            readNBytes1 = InputStream.class.getDeclaredMethod("readNBytes", int.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // 忽略
        }
        try {
            readNBytes3 = InputStream.class.getDeclaredMethod("readNBytes", byte[].class, int.class, int.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // 忽略
        }
        try {
            skipNBytes = InputStream.class.getDeclaredMethod("skipNBytes", long.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // 忽略
        }
        try {
            transferTo = InputStream.class.getDeclaredMethod("transferTo", OutputStream.class);
        } catch (NoSuchMethodException | SecurityException e1) {
            // 忽略
        }
    }

    private InputStream inputStream;

    /**
     * 一个代理 {@link InputStream} 实现，可以为 JDK 7 编译，
     * 但通过反射支持 JDK 8 中添加的方法
     *
     * @param inputStream
     *            要包装的 {@link InputStream}
     */
    public ProxyingInputStream(final InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return inputStream.read(b);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        return inputStream.read(b, off, len);
    }

    // 没有 @Override，因为 JDK 7 中不存在此方法
    @Override
    public byte[] readAllBytes() throws IOException {
        if (readAllBytes == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (byte[]) readAllBytes.invoke(inputStream);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // 没有 @Override，因为 JDK 7 中不存在此方法
    @Override
    public byte[] readNBytes(final int len) throws IOException {
        if (readNBytes1 == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (byte[]) readNBytes1.invoke(inputStream, len);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // 没有 @Override，因为 JDK 7 中不存在此方法
    @Override
    public int readNBytes(final byte[] b, final int off, final int len) throws IOException {
        if (readNBytes3 == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (int) readNBytes3.invoke(inputStream, b, off, len);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public boolean markSupported() {
        return inputStream.markSupported();
    }

    @Override
    public synchronized void mark(final int readlimit) {
        inputStream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
        inputStream.reset();
    }

    @Override
    public long skip(final long n) throws IOException {
        return inputStream.skip(n);
    }

    // 没有 @Override，因为 JDK 7 中不存在此方法
    @Override
    public void skipNBytes(final long n) throws IOException {
        if (skipNBytes == null) {
            throw new UnsupportedOperationException();
        }
        try {
            skipNBytes.invoke(inputStream, n);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    // 没有 @Override，因为 JDK 7 中不存在此方法
    @Override
    public long transferTo(final OutputStream out) throws IOException {
        if (transferTo == null) {
            throw new UnsupportedOperationException();
        }
        try {
            return (long) transferTo.invoke(inputStream, out);
        } catch (final Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    public String toString() {
        return inputStream.toString();
    }

    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            try {
                inputStream.close();
            } finally {
                inputStream = null;
            }
        }
    }
}
