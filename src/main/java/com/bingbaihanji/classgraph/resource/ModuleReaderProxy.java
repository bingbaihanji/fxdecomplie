/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison
 *
 * Hosted at: https://github.com/classgraph/classgraph
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Luke Hutchison
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.bingbaihanji.classgraph.resource;

import com.bingbaihanji.classgraph.metadata.ModuleRef;
import com.bingbaihanji.classgraph.reflect.ReflectionUtils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;

/** ModuleReader 代理，使用反射编写以保持与 JDK 7 和 8 的向后兼容性 */
public class ModuleReaderProxy implements Closeable {
    /** Class<Collector> collectorClass = Class.forName("java.util.stream.Collector"); */
    private static Class<?> collectorClass;
    /** Collector<Object, ?, List<Object>> collectorsToList = Collectors.toList(); */
    private static Object collectorsToList;
    /** 模块读取器 */
    private final AutoCloseable moduleReader;
    private ReflectionUtils reflectionUtils;

    /**
     * 构造函数
     *
     * @param moduleRef
     *            模块引用
     * @throws IOException
     *             如果发生 I/O 异常
     */
    public ModuleReaderProxy(final ModuleRef moduleRef) throws IOException {
        try {
            reflectionUtils = moduleRef.reflectionUtils;
            if (collectorClass == null || collectorsToList == null) {
                collectorClass = reflectionUtils.classForNameOrNull("java.util.stream.Collector");
                final Class<?> collectorsClass = reflectionUtils.classForNameOrNull("java.util.stream.Collectors");
                if (collectorsClass != null) {
                    collectorsToList = reflectionUtils.invokeStaticMethod(/* throwException = */ true,
                            collectorsClass, "toList");
                }
            }
            moduleReader = (AutoCloseable) reflectionUtils.invokeMethod(/* throwException = */ true,
                    moduleRef.getReference(), "open");
            if (moduleReader == null) {
                throw new IllegalArgumentException("moduleReference.open() 不应返回 null");
            }
        } catch (final SecurityException e) {
            throw new IOException("无法打开模块 " + moduleRef.getName(), e);
        }
    }

    /** 调用 ModuleReader#close() */
    @Override
    public void close() {
        try {
            moduleReader.close();
        } catch (final Exception e) {
            // 忽略
        }
    }

    /**
     * 获取 ModuleReader 可访问的资源列表
     *
     * 来自 ModuleReader#list() 的文档说明："元素的流是否包含与模块中目录相对应的名称取决于
     * 具体的模块读取器实现在惰性实现中，当使用流列出模块内容时可能会抛出 IOException
     * 如果发生这种情况，IOException 将被包装在 java.io.UncheckedIOException 中，
     * 并从导致访问尝试的方法中抛出当使用流列出模块内容且安全管理器拒绝访问时，
     * 也可能抛出 SecurityException"
     *
     * @return 模块中资源路径的列表
     * @throws SecurityException
     *             如果无法访问模块
     */
    public List<String> list() throws SecurityException {
        if (collectorsToList == null) {
            throw new IllegalArgumentException("无法调用 Collectors.toList()");
        }
        final Object /* Stream<String> */ resourcesStream = reflectionUtils
                .invokeMethod(/* throwException = */ true, moduleReader, "list");
        if (resourcesStream == null) {
            throw new IllegalArgumentException("无法调用 moduleReader.list()");
        }
        final Object resourcesList = reflectionUtils.invokeMethod(/* throwException = */ true, resourcesStream,
                "collect", collectorClass, collectorsToList);
        if (resourcesList == null) {
            throw new IllegalArgumentException("无法调用 moduleReader.list().collect(Collectors.toList())");
        }
        @SuppressWarnings("unchecked") final List<String> resourcesListTyped = (List<String>) resourcesList;
        return resourcesListTyped;
    }

    /**
     * 使用代理的 ModuleReader 以 InputStream 形式打开指定名称的资源
     *
     * @param path
     *            要打开的资源的路径
     *
     * @return 资源内容的 {@link InputStream}
     * @throws SecurityException
     *             如果无法访问模块
     * @throws IllegalArgumentException
     *             如果无法访问模块
     */
    public InputStream open(final String path) throws SecurityException {
        final Object /* Optional<InputStream> */ optionalInputStream = reflectionUtils
                .invokeMethod(/* throwException = */ true, moduleReader, "open", String.class, path);
        if (optionalInputStream == null) {
            throw new IllegalArgumentException("ModuleReader#open 对路径 " + path + " 返回了 null 结果");
        }
        final InputStream inputStream = (InputStream) reflectionUtils.invokeMethod(/* throwException = */ true,
                optionalInputStream, "get");
        if (inputStream == null) {
            throw new IllegalArgumentException("ModuleReader#open(String)#get() 返回了 null 结果");
        }
        return inputStream;
    }

    /**
     * 使用代理的 ModuleReader 以 ByteBuffer 形式打开指定名称的资源
     * 使用完 ByteBuffer 后请调用 {@link #release(ByteBuffer)}
     *
     * @param path
     *            要打开的资源的路径
     * @return 资源内容的 {@link ByteBuffer}
     * @throws SecurityException
     *             如果无法访问模块
     * @throws OutOfMemoryError
     *             如果资源大于 2GB(字节缓冲区的最大容量)
     */
    public ByteBuffer read(final String path) throws SecurityException, OutOfMemoryError {
        final Object /* Optional<ByteBuffer> */ optionalByteBuffer = reflectionUtils
                .invokeMethod(/* throwException = */ true, moduleReader, "read", String.class, path);
        if (optionalByteBuffer == null) {
            throw new IllegalArgumentException("ModuleReader#read(String) 返回了 null 结果");
        }
        final ByteBuffer byteBuffer = (ByteBuffer) reflectionUtils.invokeMethod(/* throwException = */ true,
                optionalByteBuffer, "get");
        if (byteBuffer == null) {
            throw new IllegalArgumentException("ModuleReader#read(String).get() 返回了 null 结果");
        }
        return byteBuffer;
    }

    /**
     * 释放通过调用 {@link #read(String)} 分配的 {@link ByteBuffer}
     *
     * @param byteBuffer
     *            要释放的 {@link ByteBuffer}
     */
    public void release(final ByteBuffer byteBuffer) {
        reflectionUtils.invokeMethod(/* throwException = */ true, moduleReader, "release", ByteBuffer.class,
                byteBuffer);
    }

    /**
     * 使用代理的 ModuleReader 以 URI 形式查找指定名称的资源
     *
     * @param path
     *            要打开的资源的路径
     * @return 资源的 {@link URI}
     * @throws SecurityException
     *             如果无法访问模块
     */
    public URI find(final String path) {
        final Object /* Optional<URI> */ optionalURI = reflectionUtils.invokeMethod(/* throwException = */ true,
                moduleReader, "find", String.class, path);
        if (optionalURI == null) {
            throw new IllegalArgumentException("ModuleReader#find(String) 返回了 null 结果");
        }
        final URI uri = (URI) reflectionUtils.invokeMethod(/* throwException = */ true, optionalURI, "get");
        if (uri == null) {
            throw new IllegalArgumentException("ModuleReader#find(String).get() 返回了 null 结果");
        }
        return uri;
    }
}
