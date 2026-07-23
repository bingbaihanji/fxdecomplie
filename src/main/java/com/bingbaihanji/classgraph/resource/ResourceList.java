/*
 * This file is part of ClassGraph.
 *
 * Author: Luke Hutchison (luke.hutch@gmail.com)
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

import com.bingbaihanji.classgraph.util.CollectionUtils;
import com.bingbaihanji.classgraph.util.LazyList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.Map.Entry;

/** 一个可自动关闭的 {@link Resource} 对象的可自动关闭列表 */
public class ResourceList extends LazyList<Resource> implements AutoCloseable {
    /** 一个不可修改的空的 {@link ResourceList} */
    public static final ResourceList EMPTY_LIST = new ResourceList();
    /** 序列化版本UID */
    static final long serialVersionUID = 1L;
    /** 如果 Resource 的路径以 ".class" 结尾，则返回 true */
    private static final ResourceFilter CLASSFILE_FILTER = new ResourceFilter() {
        @Override
        public boolean accept(final Resource resource) {
            final String path = resource.getPath();
            if (!path.endsWith(".class") || path.length() < 7) {
                return false;
            }
            // 检查文件名不是简单的 ".class"
            final char c = path.charAt(path.length() - 7);
            return c != '/' && c != '.';
        }
    };

    static {
        EMPTY_LIST.makeUnmodifiable();
    }

    /**
     * 创建一个新的可修改的空的 {@link Resource} 对象列表
     */
    public ResourceList() {
        super();
    }

    /**
     * 创建一个新的可修改的空的 {@link Resource} 对象列表，带有大小提示
     *
     * @param sizeHint
     *            大小提示
     */
    public ResourceList(final int sizeHint) {
        super(sizeHint);
    }

    /**
     * 创建一个新的可修改的空的 {@link ResourceList}，带有初始的 {@link Resource} 对象集合
     *
     * @param resourceCollection
     *            {@link Resource} 对象的集合
     */
    public ResourceList(final Collection<Resource> resourceCollection) {
        super(resourceCollection);
    }

    /**
     * 返回一个不可修改的空的 {@link ResourceList}
     *
     * @return 不可修改的空的 {@link ResourceList}
     */
    public static ResourceList emptyList() {
        return EMPTY_LIST;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 返回具有请求路径的所有资源的列表(可能有多个具有相同路径的资源，来自不同的类路径元素或模块，
     * 因此返回的是 {@link ResourceList} 而不是单个 {@link Resource})
     *
     * @param resourcePath
     *            资源的路径
     * @return 此列表中具有给定路径的 {@link Resource} 对象的 {@link ResourceList}
     *         (可能有多个具有相同路径的资源，来自不同的类路径元素或模块，
     *         因此返回的是 {@link ResourceList} 而不是单个 {@link Resource})
     *         如果未找到匹配路径的资源，则返回空列表
     */
    public ResourceList get(final String resourcePath) {
        ResourceList matchingResources = null;
        for (final Resource res : this) {
            if (res.getPath().equals(resourcePath)) {
                if (matchingResources == null) {
                    matchingResources = new ResourceList(2);
                }
                matchingResources.add(res);
            }
        }
        return matchingResources == null ? EMPTY_LIST : matchingResources;
    }

    /**
     * 获取此列表中所有资源相对于包根的路径
     *
     * @return 此列表中所有资源相对于包根的路径，通过对列表中的每个项调用 {@link Resource#getPath()} 获得
     */
    public List<String> getPaths() {
        final List<String> resourcePaths = new ArrayList<>(this.size());
        for (final Resource resource : this) {
            resourcePaths.add(resource.getPath());
        }
        return resourcePaths;
    }

    /**
     * 获取此列表中所有资源相对于类路径元素根目录的路径
     *
     * @return 此列表中所有资源相对于类路径元素根目录的路径，
     *         通过对列表中的每个项调用 {@link Resource#getPathRelativeToClasspath()} 获得
     */
    public List<String> getPathsRelativeToClasspath() {
        final List<String> resourcePaths = new ArrayList<>(this.size());
        for (final Resource resource : this) {
            resourcePaths.add(resource.getPathRelativeToClasspath());
        }
        return resourcePaths;
    }

    /**
     * 获取此列表中所有资源的 URL，通过对列表中的每个项调用 {@link Resource#getURL()} 获得
     * 注意，任何具有 {@code jrt:} URI 的资源(例如系统资源或来自 jlink 镜像的资源)
     * 将导致抛出 {@link IllegalArgumentException}，因为 {@link URL} 不支持此协议，
     * 因此强烈建议使用 {@link #getURIs()} 而非 {@link #getURLs()}
     *
     * @return 此列表中所有资源的 URL
     */
    public List<URL> getURLs() {
        final List<URL> resourceURLs = new ArrayList<>(this.size());
        for (final Resource resource : this) {
            resourceURLs.add(resource.getURL());
        }
        return resourceURLs;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此列表中所有资源的 URI，通过对列表中的每个项调用 {@link Resource#getURI()} 获得
     *
     * @return 此列表中所有资源的 URI
     */
    public List<URI> getURIs() {
        final List<URI> resourceURLs = new ArrayList<>(this.size());
        for (final Resource resource : this) {
            resourceURLs.add(resource.getURI());
        }
        return resourceURLs;
    }

    /**
     * 返回一个新的 {@link ResourceList}，仅包含文件扩展名为 ".class" 的资源
     *
     * @return 一个新的 {@link ResourceList}，仅包含文件扩展名为 ".class" 的资源
     */
    public ResourceList classFilesOnly() {
        return filter(CLASSFILE_FILTER);
    }

    /**
     * 返回一个新的 {@link ResourceList}，仅包含非类文件资源
     *
     * @return 一个新的 {@link ResourceList}，仅包含不具有 ".class" 文件扩展名的资源
     */
    public ResourceList nonClassFilesOnly() {
        return filter(new ResourceFilter() {
            @Override
            public boolean accept(final Resource resource) {
                return !CLASSFILE_FILTER.accept(resource);
            }
        });
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 将此 {@link ResourceList} 作为从资源路径(由 {@link Resource#getPath()} 获得)到
     * 具有该路径的 {@link Resource} 对象的 {@link ResourceList} 的映射返回
     *
     * @return 将此 {@link ResourceList} 作为从资源路径(由 {@link Resource#getPath()} 获得)到
     *         具有该路径的 {@link Resource} 对象的 {@link ResourceList} 的映射返回
     */
    public Map<String, ResourceList> asMap() {
        final Map<String, ResourceList> pathToResourceList = new HashMap<>();
        for (final Resource resource : this) {
            final String path = resource.getPath();
            ResourceList resourceList = pathToResourceList.get(path);
            if (resourceList == null) {
                resourceList = new ResourceList(1);
                pathToResourceList.put(path, resourceList);
            }
            resourceList.add(resource);
        }
        return pathToResourceList;
    }

    /**
     * 在此 {@link ResourceList} 中查找重复的资源路径
     *
     * @return 一个 {@link List}，包含类路径和/或模块路径中所有具有非唯一路径的资源
     *         (即至少有两个资源具有相同路径)的 {@link Entry} 对象
     *         每个返回的 {@link Entry} 的键是路径(由 {@link Resource#getPath()} 获得)，
     *         值是一个包含至少两个具有该路径的唯一 {@link Resource} 对象的 {@link ResourceList}
     */
    public List<Entry<String, ResourceList>> findDuplicatePaths() {
        final List<Entry<String, ResourceList>> duplicatePaths = new ArrayList<>();
        for (final Entry<String, ResourceList> pathAndResourceList : asMap().entrySet()) {
            // 查找包含两个或更多条目的 ResourceList
            if (pathAndResourceList.getValue().size() > 1) {
                duplicatePaths.add(new SimpleEntry<>(pathAndResourceList.getKey(), pathAndResourceList.getValue()));
            }
        }
        CollectionUtils.sortIfNotEmpty(duplicatePaths, new Comparator<Entry<String, ResourceList>>() {
            @Override
            public int compare(final Entry<String, ResourceList> o1, final Entry<String, ResourceList> o2) {
                // 按路径的字典序排序
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        return duplicatePaths;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 查找此列表中给定过滤谓词为真的 {@link Resource} 对象的子集
     *
     * @param filter
     *            要应用的 {@link ResourceFilter}
     * @return 此列表中给定过滤谓词为真的 {@link Resource} 对象的子集
     */
    public ResourceList filter(final ResourceFilter filter) {
        final ResourceList resourcesFiltered = new ResourceList();
        for (final Resource resource : this) {
            if (filter.accept(resource)) {
                resourcesFiltered.add(resource);
            }
        }
        return resourcesFiltered;
    }

    /**
     * 获取此 {@link ResourceList} 中每个 {@link Resource} 的内容并以字节数组形式返回，
     * 将字节数组传递给给定的 {@link ByteArrayConsumer}，
     * 然后通过调用 {@link Resource#close()} 关闭底层的 InputStream 或释放底层的 ByteBuffer
     *
     * @param byteArrayConsumer
     *            {@link ByteArrayConsumer}
     * @param ignoreIOExceptions
     *            如果为 true，则在尝试加载任何资源时抛出的任何 {@link IOException} 将被静默忽略
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，并且在尝试加载任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachByteArrayIgnoringIOException(ByteArrayConsumer)} 或
     *             {@link #forEachByteArrayThrowingIOException(ByteArrayConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachByteArray(final ByteArrayConsumer byteArrayConsumer, final boolean ignoreIOExceptions) {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                byteArrayConsumer.accept(resourceToClose, resourceToClose.load());
            } catch (final IOException e) {
                if (!ignoreIOExceptions) {
                    throw new IllegalArgumentException("Could not load resource " + resource, e);
                }
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取此 {@link ResourceList} 中每个 {@link Resource} 的内容并以字节数组形式返回，
     * 将字节数组传递给给定的 {@link ByteArrayConsumer}，
     * 然后通过调用 {@link Resource#close()} 关闭底层的 InputStream 或释放底层的 ByteBuffer
     *
     * @param byteArrayConsumer
     *            {@link ByteArrayConsumer}
     * @throws IllegalArgumentException
     *             如果在尝试加载任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachByteArrayThrowingIOException(ByteArrayConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachByteArray(final ByteArrayConsumer byteArrayConsumer) {
        forEachByteArray(byteArrayConsumer, false);
    }

    /**
     * 获取此 {@link ResourceList} 中每个 {@link Resource} 的内容并以字节数组形式返回，
     * 将字节数组传递给给定的 {@link ByteArrayConsumer}，
     * 然后通过调用每个 {@link Resource} 的 {@link Resource#close()} 关闭底层的 InputStream 或释放底层的 ByteBuffer
     * 如果在打开或读取任何资源时发生 {@link IOException}，则静默跳过该资源
     *
     * @param byteArrayConsumer
     *            {@link ByteArrayConsumer}
     */
    public void forEachByteArrayIgnoringIOException(final ByteArrayConsumer byteArrayConsumer) {
        for (final Resource resource : this) {
            try (Resource resourceToClose = resource) {
                byteArrayConsumer.accept(resourceToClose, resourceToClose.load());
            } catch (final IOException e) {
                // 忽略
            }
        }
    }

    /**
     * 获取此 {@link ResourceList} 中每个 {@link Resource} 的内容并以字节数组形式返回，
     * 将字节数组传递给给定的 {@link ByteArrayConsumer}，
     * 然后通过调用 {@link Resource#close()} 关闭底层的 InputStream 或释放底层的 ByteBuffer
     *
     * @param byteArrayConsumerThrowsIOException
     *            {@link ByteArrayConsumerThrowsIOException}
     * @throws IOException
     *             如果尝试加载任何资源导致抛出 {@link IOException}
     */
    public void forEachByteArrayThrowingIOException(
            final ByteArrayConsumerThrowsIOException byteArrayConsumerThrowsIOException) throws IOException {
        for (final Resource resource : this) {
            try (Resource resourceToClose = resource) {
                byteArrayConsumerThrowsIOException.accept(resourceToClose, resourceToClose.load());
            }
        }
    }

    /**
     * 为此 {@link ResourceList} 中的每个 {@link Resource} 获取一个 {@link InputStream}，
     * 将 {@link InputStream} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link InputStreamConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 关闭 {@link InputStream}
     *
     * @param inputStreamConsumer
     *            {@link InputStreamConsumer}
     * @param ignoreIOExceptions
     *            如果为 true，则在尝试加载任何资源时抛出的任何 {@link IOException} 将被静默忽略
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，并且在尝试打开任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachInputStreamIgnoringIOException(InputStreamConsumer)} 或
     *             {@link #forEachInputStreamThrowingIOException(InputStreamConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachInputStream(final InputStreamConsumer inputStreamConsumer,
                                   final boolean ignoreIOExceptions) {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                inputStreamConsumer.accept(resourceToClose, resourceToClose.open());
            } catch (final IOException e) {
                if (!ignoreIOExceptions) {
                    throw new IllegalArgumentException("Could not load resource " + resource, e);
                }
            }
        }
    }

    /**
     * 为此 {@link ResourceList} 中的每个 {@link Resource} 获取一个 {@link InputStream}，
     * 将 {@link InputStream} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link InputStreamConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 关闭 {@link InputStream}
     *
     * @param inputStreamConsumer
     *            {@link InputStreamConsumer}
     * @throws IllegalArgumentException
     *             在尝试打开任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachInputStreamThrowingIOException(InputStreamConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachInputStream(final InputStreamConsumer inputStreamConsumer) {
        forEachInputStream(inputStreamConsumer, false);
    }

    /**
     * 为此 {@link ResourceList} 中的每个 {@link Resource} 获取一个 {@link InputStream}，
     * 将 {@link InputStream} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link InputStreamConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 关闭 {@link InputStream}如果在打开或读取任何资源时发生 {@link IOException}，则静默跳过该资源
     *
     * @param inputStreamConsumer
     *            {@link InputStreamConsumer}
     */
    public void forEachInputStreamIgnoringIOException(final InputStreamConsumer inputStreamConsumer) {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                inputStreamConsumer.accept(resourceToClose, resourceToClose.open());
            } catch (final IOException e) {
                // 忽略
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 为此 {@link ResourceList} 中的每个 {@link Resource} 获取一个 {@link InputStream}，
     * 将 {@link InputStream} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link InputStreamConsumer} 返回后，通过调用 {@link Resource#close()} 关闭 {@link InputStream}
     *
     * @param inputStreamConsumerThrowsIOException
     *            {@link InputStreamConsumerThrowsIOException}
     * @throws IOException
     *             如果尝试打开或读取任何资源导致抛出 {@link IOException}
     */
    public void forEachInputStreamThrowingIOException(
            final InputStreamConsumerThrowsIOException inputStreamConsumerThrowsIOException) throws IOException {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                inputStreamConsumerThrowsIOException.accept(resourceToClose, resourceToClose.open());
            }
        }
    }

    /**
     * 将此 {@link ResourceList} 中的每个 {@link Resource} 读取为 {@link ByteBuffer}，
     * 将 {@link ByteBuffer} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link ByteBufferConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 释放 {@link ByteBuffer}
     *
     * @param byteBufferConsumer
     *            {@link ByteBufferConsumer}
     * @param ignoreIOExceptions
     *            如果为 true，则在尝试加载任何资源时抛出的任何 {@link IOException} 将被静默忽略
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false，并且在尝试加载任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachByteBufferIgnoringIOException(ByteBufferConsumer)} 或
     *             {@link #forEachByteBufferThrowingIOException(ByteBufferConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachByteBuffer(final ByteBufferConsumer byteBufferConsumer, final boolean ignoreIOExceptions) {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                byteBufferConsumer.accept(resourceToClose, resourceToClose.read());
            } catch (final IOException e) {
                if (!ignoreIOExceptions) {
                    throw new IllegalArgumentException("Could not load resource " + resource, e);
                }
            }
        }
    }

    /**
     * 将此 {@link ResourceList} 中的每个 {@link Resource} 读取为 {@link ByteBuffer}，
     * 将 {@link ByteBuffer} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link ByteBufferConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 释放 {@link ByteBuffer}
     *
     * @param byteBufferConsumer
     *            {@link ByteBufferConsumer}
     * @throws IllegalArgumentException
     *             如果在尝试加载任何资源时抛出 {@link IOException}
     * @deprecated 请改用 {@link #forEachByteBufferThrowingIOException(ByteBufferConsumerThrowsIOException)}
     */
    @Deprecated
    public void forEachByteBuffer(final ByteBufferConsumer byteBufferConsumer) {
        forEachByteBuffer(byteBufferConsumer, false);
    }

    /**
     * 将此 {@link ResourceList} 中的每个 {@link Resource} 读取为 {@link ByteBuffer}，
     * 将 {@link ByteBuffer} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link ByteBufferConsumer} 返回后，通过调用每个 {@link Resource} 的 {@link Resource#close()}
     * 释放 {@link ByteBuffer}如果在打开或读取任何资源时发生 {@link IOException}，则静默跳过该资源
     *
     * @param byteBufferConsumer
     *            {@link ByteBufferConsumer}
     */
    public void forEachByteBufferIgnoringIOException(final ByteBufferConsumer byteBufferConsumer) {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                byteBufferConsumer.accept(resourceToClose, resourceToClose.read());
            } catch (final IOException e) {
                // 忽略
            }
        }
    }

    /**
     * 将此 {@link ResourceList} 中的每个 {@link Resource} 读取为 {@link ByteBuffer}，
     * 将 {@link ByteBuffer} 传递给给定的 {@link InputStreamConsumer}，
     * 然后在 {@link ByteBufferConsumer} 返回后，通过调用 {@link Resource#close()} 释放 {@link ByteBuffer}
     *
     * @param byteBufferConsumerThrowsIOException
     *            {@link ByteBufferConsumerThrowsIOException}
     * @throws IOException
     *             如果尝试加载任何资源导致抛出 {@link IOException}
     */
    public void forEachByteBufferThrowingIOException(
            final ByteBufferConsumerThrowsIOException byteBufferConsumerThrowsIOException) throws IOException {
        for (final Resource resource : this) {
            try (final Resource resourceToClose = resource) {
                byteBufferConsumerThrowsIOException.accept(resourceToClose, resourceToClose.read());
            }
        }
    }

    /** 关闭此 {@link ResourceList} 中的所有 {@link Resource} 对象 */
    @Override
    public void close() {
        Throwable firstThrowable = null;
        for (final Resource resource : this) {
            try {
                resource.close();
            } catch (final Throwable t) {
                // 继续关闭其余资源，最后抛出第一个异常
                if (firstThrowable == null) {
                    firstThrowable = t;
                }
            }
        }
        if (firstThrowable != null) {
            if (firstThrowable instanceof RuntimeException) {
                throw (RuntimeException) firstThrowable;
            } else if (firstThrowable instanceof Error) {
                throw (Error) firstThrowable;
            } else {
                throw new RuntimeException(firstThrowable);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 使用一个将 {@link Resource} 对象映射到布尔值的谓词来过滤 {@link ResourceList}，
     * 生成一个新的 {@link ResourceList}，包含列表中所有谓词为真的项
     */
    @FunctionalInterface
    public interface ResourceFilter {
        /**
         * 是否允许一个 {@link Resource} 列表项通过过滤器
         *
         * @param resource
         *            要过滤的 {@link Resource} 项
         * @return 是否允许该项通过过滤器如果为 true，则该项被复制到输出列表；如果为 false，则被排除
         */
        boolean accept(Resource resource);
    }

    /** 一个用于消费 {@link Resource} 内容(字节数组形式)的 {@link FunctionalInterface} */
    @FunctionalInterface
    public interface ByteArrayConsumer {
        /**
         * 消费 {@link Resource} 的完整内容(字节数组形式)
         *
         * @param resource
         *            用于加载字节数组的 {@link Resource}
         * @param byteArray
         *            资源的完整内容
         */
        void accept(final Resource resource, final byte[] byteArray);
    }

    /**
     * 一个用于消费 {@link Resource} 内容(字节数组形式)的 {@link FunctionalInterface}，
     * 如果发生 IO 异常，则向调用者抛出 {@link IOException}
     */
    @FunctionalInterface
    public interface ByteArrayConsumerThrowsIOException {
        /**
         * 消费 {@link Resource} 的完整内容(字节数组形式)，可能抛出 {@link IOException}
         *
         * @param resource
         *            用于加载字节数组的 {@link Resource}
         * @param byteArray
         *            资源的完整内容
         * @throws IOException
         *             如果发生 IO 异常
         */
        void accept(final Resource resource, final byte[] byteArray) throws IOException;
    }

    /** 一个用于消费 {@link Resource} 内容({@link InputStream} 形式)的 {@link FunctionalInterface} */
    @FunctionalInterface
    public interface InputStreamConsumer {
        /**
         * 以 {@link InputStream} 形式消费 {@link Resource}
         *
         * @param resource
         *            用于打开 {@link InputStream} 的 {@link Resource}
         * @param inputStream
         *            在资源上打开的 {@link InputStream}
         */
        void accept(final Resource resource, final InputStream inputStream);
    }

    /**
     * 一个用于消费 {@link Resource} 内容({@link InputStream} 形式)的 {@link FunctionalInterface}，
     * 如果发生 IO 异常，则向调用者抛出 {@link IOException}
     */
    @FunctionalInterface
    public interface InputStreamConsumerThrowsIOException {
        /**
         * 消费 {@link Resource} 的完整内容(字节数组形式)，可能抛出 {@link IOException}
         *
         * @param resource
         *            用于加载字节数组的 {@link Resource}
         * @param inputStream
         *            在资源上打开的 {@link InputStream}
         * @throws IOException
         *             如果发生 IO 异常
         */
        void accept(final Resource resource, final InputStream inputStream) throws IOException;
    }

    /** 一个用于消费 {@link Resource} 内容({@link ByteBuffer} 形式)的 {@link FunctionalInterface} */
    @FunctionalInterface
    public interface ByteBufferConsumer {
        /**
         * 以 {@link ByteBuffer} 形式消费 {@link Resource}，可能抛出 {@link IOException}
         *
         * @param resource
         *            其内容反映在 {@link ByteBuffer} 中的 {@link Resource}
         * @param byteBuffer
         *            映射到资源的 {@link ByteBuffer}
         */
        void accept(final Resource resource, final ByteBuffer byteBuffer);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 一个用于消费 {@link Resource} 内容({@link ByteBuffer} 形式)的 {@link FunctionalInterface}，
     * 如果发生 IO 异常，则向调用者抛出 {@link IOException}
     */
    @FunctionalInterface
    public interface ByteBufferConsumerThrowsIOException {
        /**
         * 消费 {@link Resource} 的完整内容(字节数组形式)
         *
         * @param resource
         *            用于加载字节数组的 {@link Resource}，可能抛出 {@link IOException}
         * @param byteBuffer
         *            映射到资源的 {@link ByteBuffer}
         * @throws IOException
         *             如果发生 IO 异常
         */
        void accept(final Resource resource, final ByteBuffer byteBuffer) throws IOException;
    }
}
