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

import com.bingbaihanji.classgraph.metadata.*;
import com.bingbaihanji.classgraph.bytecode.*;
import com.bingbaihanji.classgraph.metadata.ModuleRef;

import com.bingbaihanji.classgraph.bytecode.ClassFileReader;
import com.bingbaihanji.classgraph.util.LogNode;
import com.bingbaihanji.classgraph.util.URLPathEncoder;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.zip.ZipEntry;

/**
 * 一个在类路径元素或模块中被接受/未被拒绝的包中找到的类路径或模块路径资源(即文件)
 */
public abstract class Resource implements Closeable, Comparable<Resource> {
    /** 此资源所来自的类路径元素 */
    private final Classpath Classpath;

    /** 输入流，或为 null */
    protected InputStream inputStream;

    /** 字节缓冲区，或为 null */
    protected ByteBuffer byteBuffer;

    /** 长度，未知时为 -1L */
    protected long length;
    /**
     * 用于记录在扫描类路径元素路径时发现资源的 {@link LogNode}对于被接受的类文件资源，
     * 在扫描类文件内容时会添加子日志条目
     */
    LogNode scanLog;
    /** toString() 的缓存结果 */
    private String toString;

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param Classpath
     *            此资源所来自的类路径元素
     * @param length
     *            资源的长度
     */
    public Resource(final Classpath Classpath, final long length) {
        this.Classpath = Classpath;
        this.length = length;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将 URI 转换为 URL，捕获 "jrt:" URI 为无效
     *
     * @param uri
     *            URI
     * @return URL
     * @throws IllegalArgumentException
     *             如果 URI 无法转换为 URL，或者 URI 具有 "jrt:" 协议
     */
    private static URL uriToURL(final URI uri) {
        try {
            return uri.toURL();
        } catch (final IllegalArgumentException | MalformedURLException e) {
            if ("jrt".equals(uri.getScheme())) {
                // 目前 URL 无法处理系统模块使用的 "jrt:" 协议
                throw new IllegalArgumentException("Could not create URL from URI with \"jrt:\" scheme "
                        + "(\"jrt:\" is not supported by the URL class without a custom URL protocol handler): "
                        + uri);
            } else {
                throw new IllegalArgumentException("Could not create URL from URI: " + uri + " -- " + e);
            }
        }
    }

    /**
     * 获取表示资源位置的 {@link URI}
     *
     * @return 表示资源位置的 {@link URI}
     * @throws IllegalArgumentException
     *             如果资源来自模块且该模块的位置 URI 为 null
     */
    public URI getURI() {
        final URI locationURI = getClasspathURI();
        final String locationURIStr = locationURI.toString();
        final String resourcePath = getPathRelativeToClasspath();
        // 检查这是否是一个基于目录的模块(位置 URI 将以 "/" 结尾)
        final boolean isDir = locationURIStr.endsWith("/");
        try {
            return new URI(
                    (isDir || locationURIStr.startsWith("jar:") || locationURIStr.startsWith("jrt:") ? "" : "jar:")
                            + locationURIStr + (isDir ? "" : locationURIStr.startsWith("jrt:") ? "/" : "!/")
                            + URLPathEncoder.encodePath(resourcePath));
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Could not form URL for classpath element: " + locationURIStr
                    + " ; path: " + resourcePath + " : " + e);
        }
    }

    /**
     * 获取表示资源位置的 {@link URL}如果资源可能来自系统模块，或者这是一个 jlink 运行时镜像，
     * 请改用 {@link #getURI()}，因为系统模块和 jlink 运行时镜像使用的 "jrt:" URI 协议不被 {@link URL} 支持，
     * 这将导致抛出 {@link IllegalArgumentException}
     *
     * @return 表示资源位置的 {@link URL}
     * @throws IllegalArgumentException
     *             如果资源来自具有 "jrt:" 位置 URI 的系统模块或 jlink 运行时镜像，
     *             或者资源来自模块且该模块的位置 URI 为 null
     */
    public URL getURL() {
        return uriToURL(getURI());
    }

    /**
     * 获取此资源所来自的类路径元素或模块的 {@link URI}
     *
     * @return 此资源所在类路径元素或模块的 {@link URL}
     * @throws IllegalArgumentException
     *             如果类路径元素没有有效的 URI(例如对于位置 URI 为 null 的模块)
     */
    public URI getClasspathURI() {
        return Classpath.getURI();
    }

    /**
     * 获取此资源所来自的类路径元素或模块的 {@link URL}如果资源可能来自系统模块，或者这是一个 jlink 运行时镜像，
     * 请改用 {@link #getClasspathURI()}，因为系统模块和 jlink 运行时镜像使用的 "jrt:" URI 协议不被
     * {@link URL} 支持，这将导致抛出 {@link IllegalArgumentException}
     *
     * @return 此资源所在类路径元素或模块的 {@link URL}
     * @throws IllegalArgumentException
     *             如果资源来自具有 "jrt:" 位置 URI 的系统模块或 jlink 运行时镜像，
     *             或者资源来自模块且该模块的位置 URI 为 null
     */
    public URL getClasspathURL() {
        return uriToURL(getClasspathURI());
    }

    /**
     * 获取类路径元素的 {@link File}
     *
     * @return 此 {@link Resource} 所在类路径元素包根目录或 jar 的 {@link File}，
     *         如果此 {@link Resource} 位于由 "jrt:" URI 支持的模块中或位置未知的模块中，则返回 null
     *         如果类路径元素是 http/https URL，并且 jar 直接下载到 RAM 中而非磁盘上的临时文件
     *         (例如临时目录不可写)，也可能返回 null
     */
    public File getClasspathFile() {
        return Classpath.getFile();
    }

    /**
     * 获取此 {@link Resource} 所在模块的 {@link ModuleRef} 引用
     *
     * @return 此 {@link Resource} 所在模块的 {@link ModuleRef} 引用，
     *         如果此 {@link Resource} 位于类路径中的目录或 jar 中，则返回 null
     */
    public ModuleRef getModuleRef() {
        return Classpath instanceof ModuleClasspath
                ? ((ModuleClasspath) Classpath).moduleRef
                : null;
    }

    /**
     * 以字符串形式获取此 {@link Resource} 内容的便捷方法假定为 UTF8 编码(完成后调用 {@link #close()})
     *
     * @return 此 {@link Resource} 的内容，以字符串形式返回
     * @throws IOException
     *             如果发生 I/O 异常
     */
    public String getContentAsString() throws IOException {
        final String content = new String(load(), StandardCharsets.UTF_8);
        close();
        return content;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此类路径资源相对于包根的路径
     *
     * @return 此类路径资源相对于包根的路径例如，对于资源路径
     *         {@code "BOOT-INF/classes/com/xyz/resource.xml"} 和包根 {@code "BOOT-INF/classes/"}，
     *         返回 {@code "com/xyz/resource.xml"}对于多版本 jar，也会删除版本前缀，
     *         例如在 JDK 9+ 上运行时，对于资源路径 {@code "META-INF/versions/11/com/xyz/resource.xml"}，
     *         返回 {@code "com/xyz/resource.xml"}
     */
    public abstract String getPath();

    /**
     * 获取此类路径资源相对于类路径元素根目录的完整路径
     *
     * @return 此类路径资源在类路径元素中的完整路径例如，将返回
     *         {@code "BOOT-INF/classes/com/xyz/resource.xml"} 或
     *         {@code "META-INF/versions/11/com/xyz/resource.xml"} 的完整路径，
     *         而不是 {@code "com/xyz/resource.xml"}
     */
    public String getPathRelativeToClasspath() {
        // 仅对 jar 重写
        return getPath();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 为类路径资源打开一个 {@link InputStream}当你使用完 {@link InputStream} 后，确保调用
     * {@link Resource#close()}，以便关闭 {@link InputStream}
     *
     * @return 打开的 {@link InputStream}
     * @throws IOException
     *             如果 {@link InputStream} 无法打开
     */
    public abstract InputStream open() throws IOException;

    /**
     * 为类路径资源打开一个 {@link ByteBuffer}当你使用完 {@link ByteBuffer} 后，确保调用
     * {@link Resource#close()}，以便释放或取消映射 {@link ByteBuffer}另请参阅 {@link #readCloseable()}
     *
     * @return 为资源文件内容分配或映射的 {@link ByteBuffer}
     * @throws IOException
     *             如果资源无法读取
     */
    public abstract ByteBuffer read() throws IOException;

    /**
     * 为类路径资源打开一个 {@link ByteBuffer}，并将其包装在一个 {@link ByteBuffer} 实例中，
     * 该实例实现了 {@link Closeable#close()} 方法，在调用 {@link ByteBuffer#close()} 时
     * 通过自动调用 {@link Resource#close()} 来释放底层的 {@link ByteBuffer}
     *
     * <p>
     * 调用返回实例的 {@link ByteBuffer#getByteBuffer()} 方法来访问底层的 {@link ByteBuffer}
     *
     * @return 为资源文件内容分配或映射的 {@link ByteBuffer}
     * @throws IOException
     *             如果资源无法读取
     */
    public ByteBuffer readCloseable() throws IOException {
        return new ByteBuffer(read(), new Runnable() {
            @Override
            public void run() {
                close();
            }
        });
    }

    /**
     * 加载类路径资源并以字节数组形式返回其内容在加载字节数组后、返回之前自动调用
     * {@link Resource#close()}，以便关闭底层的 InputStream 或释放/取消映射底层的 ByteBuffer
     *
     * @return 资源文件的内容
     * @throws IOException
     *             如果文件内容无法完整加载
     */
    public abstract byte[] load() throws IOException;

    /**
     * 在资源上打开一个 {@link ClassFileReader}(用于读取类文件)
     *
     * @return {@link ClassFileReader}
     * @throws IOException
     *             如果发生 I/O 异常
     */
    abstract ClassFileReader openClassfile() throws IOException;

    /**
     * 获取资源的长度
     *
     * @return 资源的长度这仅在调用 {@link #open()}、{@link #read()} 或 {@link #load()} 之后
     *         才可靠地返回有效值(对于 {@link #open()}，仅当底层 jar 文件包含相应 {@link ZipEntry} 的
     *         长度信息时——某些 jar 文件的 zip 条目可能没有长度信息)如果长度未知，则返回 -1L
     */
    public long getLength() {
        return length;
    }

    /**
     * 获取资源的最后修改时间，以自纪元以来的毫秒数表示如果此资源是磁盘上的文件，此时间来自目录条目；
     * 如果此资源是 zip 文件条目，则来自 zip 文件中央目录对于从系统模块或 jlink 模块获取的资源，
     * 时间戳不可用
     *
     * <p>
     * 注意：ZIP 格式没有时区概念，因此只有知道时间戳是在哪个时区创建的，时间戳才有意义
     * 我们任意假设 zip 文件时间戳处于 UTC 时区这可能是一个错误的假设，
     * 因此如果您知道 zip 文件创建者使用的时区，可能需要应用时区校正
     *
     * @return 表示此文件资源最后修改日期/时间的自纪元以来的毫秒数如果最后修改日期未知，则返回 0L
     */
    public abstract long getLastModified();

    /**
     * 获取资源的 POSIX 文件权限如果此资源是磁盘上的文件，POSIX 文件权限来自目录条目；
     * 如果此资源是 zip 文件条目，则来自 zip 文件中央目录对于从系统模块或 jlink 模块获取的资源，
     * POSIX 文件权限不可用，并且在不符合 POSIX 标准的操作系统或非 POSIX 文件系统上也可能不可用
     *
     * @return 资源的 {@link PosixFilePermission} 权限标志集合，如果未知则返回 null
     */
    public abstract Set<PosixFilePermission> getPosixFilePermissions();

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取资源位置的字符串表示(以 URL 字符串形式)
     *
     * @return 资源位置，以 URL 字符串形式返回
     */
    @Override
    public String toString() {
        if (toString != null) {
            return toString;
        } else {
            return toString = getURI().toString();
        }
    }

    /**
     * 哈希码
     *
     * @return 哈希码整数
     */
    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    /**
     * 相等性比较
     *
     * @param obj
     *            要比较的对象
     * @return 如果相等则返回 true
     */
    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof Resource)) {
            return false;
        }
        return this.toString().equals(obj.toString());
    }

    /**
     * 比较
     *
     * @param o
     *            要比较的对象
     * @return 比较结果整数
     */
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final Resource o) {
        return toString().compareTo(o.toString());
    }

    // -------------------------------------------------------------------------------------------------------------

    /** 关闭底层的 InputStream，或释放/取消映射底层的 ByteBuffer */
    @Override
    public void close() {
        // 在子类中重写，并调用 super.close()，然后最后调用 markAsClosed()
        if (inputStream != null) {
            try {
                inputStream.close();
            } catch (final IOException e) {
                // 忽略
            }
            inputStream = null;
        }
    }
}
