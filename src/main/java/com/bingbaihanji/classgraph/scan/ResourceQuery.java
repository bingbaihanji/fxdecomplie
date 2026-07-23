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
package com.bingbaihanji.classgraph.scan;

import com.bingbaihanji.classgraph.classpath.Classpath;
import com.bingbaihanji.classgraph.resource.Resource;
import com.bingbaihanji.classgraph.resource.ResourceList;
import com.bingbaihanji.classgraph.util.FileUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Facade for resource queries on a ScanResult.
 */
public class ResourceQuery {
    private final List<Classpath> classpathOrder;
    private final AtomicInteger getResourcesWithPathCallCount;
    private final AtomicBoolean closed;
    private ResourceList allAcceptedResourcesCached;
    private Map<String, ResourceList> pathToAcceptedResourcesCached;

    ResourceQuery(final List<Classpath> classpathOrder,
                  final AtomicInteger getResourcesWithPathCallCount,
                  final AtomicBoolean closed) {
        this.classpathOrder = classpathOrder;
        this.getResourcesWithPathCallCount = getResourcesWithPathCallCount;
        this.closed = closed;
    }

    // -------------------------------------------------------------------------------------------------------------
    // 资源

    /**
     * 获取所有资源的列表
     *
     * @return 在被接受的包中找到的所有资源(包括类文件和非类文件)的列表
     */
    public ResourceList getAllResources() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        synchronized (this) {
            if (allAcceptedResourcesCached == null) {
                // 按路径索引 Resource 对象
                final ResourceList acceptedResourcesList = new ResourceList();
                for (final Classpath classpathElt : classpathOrder) {
                    acceptedResourcesList.addAll(classpathElt.acceptedResources);
                }
                // 原子性设置以确保线程安全
                allAcceptedResourcesCached = acceptedResourcesList;
            }
            return allAcceptedResourcesCached;
        }
    }

    /**
     * 获取从资源路径到 {@link Resource} 的映射，包含在被接受的包中找到的所有资源(包括类文件和非类文件)
     *
     * @return 从资源路径到 {@link Resource} 的映射，包含在被接受的包中找到的所有资源(包括类文件和非类文件)
     */
    public Map<String, ResourceList> getAllResourcesAsMap() {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        synchronized (this) {
            if (pathToAcceptedResourcesCached == null) {
                final Map<String, ResourceList> pathToAcceptedResourceListMap = new HashMap<>();
                for (final Resource res : getAllResources()) {
                    ResourceList resList = pathToAcceptedResourceListMap.get(res.getPath());
                    if (resList == null) {
                        pathToAcceptedResourceListMap.put(res.getPath(), resList = new ResourceList());
                    }
                    resList.add(res);
                }
                // 原子性设置以确保线程安全
                pathToAcceptedResourcesCached = pathToAcceptedResourceListMap;
            }
            return pathToAcceptedResourcesCached;
        }
    }

    /**
     * 获取在被接受的包中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表
     * 可能匹配多个资源，每个类路径元素最多一个
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在被接受的包中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表
     *         可能匹配多个资源，每个类路径元素最多一个
     */
    public ResourceList getResourcesWithPath(final String resourcePath) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final String path = FileUtils.sanitizeEntryPath(resourcePath, /* removeInitialSlash = */ true,
                /* removeFinalSlash = */ true);
        ResourceList matchingResources = null;
        if (getResourcesWithPathCallCount.incrementAndGet() > 3) {
            // 如果进行了多次调用，则生成并缓存一个 HashMap 以实现 O(1) 访问时间
            matchingResources = getAllResourcesAsMap().get(path);
        } else {
            // 如果只进行了少量调用，则直接搜索具有请求路径的资源
            for (final Classpath classpathElt : classpathOrder) {
                for (final Resource res : classpathElt.acceptedResources) {
                    if (res.getPath().equals(path)) {
                        if (matchingResources == null) {
                            matchingResources = new ResourceList();
                        }
                        matchingResources.add(res);
                    }
                }
            }
        }
        return matchingResources == null ? ResourceList.EMPTY_LIST : matchingResources;
    }

    /**
     * 获取在任何类路径元素中找到的具有给定路径(相对于类路径元素的包根)的所有资源的列表，
     * <i>无论是否在被接受的包中(只要资源未被拒绝)</i>
     * 可能匹配多个资源，每个类路径元素最多一个注意，这可能不会返回未被接受的资源，
     * 特别是在扫描目录类路径元素时，因为一旦给定目录下不再可能有被接受的资源，递归扫描就会终止
     * 但是，可以使用此方法找到被接受目录的祖先目录中的资源
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在任何类路径元素中找到的具有给定路径的所有资源的列表，
     *         <i>无论是否在被接受的包中(只要资源未被拒绝)</i>，
     *         相对于类路径元素的包根可能匹配多个资源，每个类路径元素最多一个
     */
    public ResourceList getResourcesWithPathIgnoringAccept(final String resourcePath) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final String path = FileUtils.sanitizeEntryPath(resourcePath, /* removeInitialSlash = */ true,
                /* removeFinalSlash = */ true);
        final ResourceList matchingResources = new ResourceList();
        for (final Classpath classpathElt : classpathOrder) {
            final Resource matchingResource = classpathElt.getResource(path);
            if (matchingResource != null) {
                matchingResources.add(matchingResource);
            }
        }
        return matchingResources;
    }

    /**
     * 请改用 {@link #getResourcesWithPathIgnoringAccept(String)}
     *
     * @param resourcePath
     *            完整的资源路径，相对于类路径条目的包根
     * @return 在任何类路径元素中找到的具有给定路径的所有资源的列表，
     *         <i>无论是否在被接受的包中(只要资源未被拒绝)</i>，
     *         相对于类路径元素的包根可能匹配多个资源，每个类路径元素最多一个
     * @deprecated 请改用 {@link #getResourcesWithPathIgnoringAccept(String)}
     */
    @Deprecated
    public ResourceList getResourcesWithPathIgnoringWhitelist(final String resourcePath) {
        return getResourcesWithPathIgnoringAccept(resourcePath);
    }

    /**
     * 获取在被接受的包中找到的具有请求的叶子名称的所有资源的列表
     *
     * @param leafName
     *            资源叶子文件名
     * @return 在被接受的包中找到的具有请求的叶子名称的所有资源的列表
     */
    public ResourceList getResourcesWithLeafName(final String leafName) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                final int lastSlashIdx = relativePath.lastIndexOf('/');
                if (relativePath.substring(lastSlashIdx + 1).equals(leafName)) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的具有请求的文件扩展名的所有资源的列表
     *
     * @param extension
     *            文件扩展名，例如 "xml" 可匹配所有以 ".xml" 结尾的资源
     * @return 在被接受的包中找到的具有请求的文件扩展名的所有资源的列表
     */
    public ResourceList getResourcesWithExtension(final String extension) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            String bareExtension = extension;
            while (bareExtension.startsWith(".")) {
                bareExtension = bareExtension.substring(1);
            }
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                final int lastSlashIdx = relativePath.lastIndexOf('/');
                final int lastDotIdx = relativePath.lastIndexOf('.');
                if (lastDotIdx > lastSlashIdx
                        && relativePath.substring(lastDotIdx + 1).equalsIgnoreCase(bareExtension)) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的路径与请求的正则表达式模式匹配的所有资源的列表
     * 另请参阅 {@link #getResourcesMatchingWildcard(String)}
     *
     * @param pattern
     *            用于匹配 {@link Resource} 路径的模式
     * @return 在被接受的包中找到的路径与请求的模式匹配的所有资源的列表
     */
    public ResourceList getResourcesMatchingPattern(final Pattern pattern) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        final ResourceList allAcceptedResources = getAllResources();
        if (allAcceptedResources.isEmpty()) {
            return ResourceList.EMPTY_LIST;
        } else {
            final ResourceList filteredResources = new ResourceList();
            for (final Resource classpathResource : allAcceptedResources) {
                final String relativePath = classpathResource.getPath();
                if (pattern.matcher(relativePath).matches()) {
                    filteredResources.add(classpathResource);
                }
            }
            return filteredResources;
        }
    }

    /**
     * 获取在被接受的包中找到的路径与请求的通配符字符串匹配的所有资源的列表
     *
     * <p>
     * 通配符字符串可以包含：
     * <ul>
     * <li>单个星号，匹配零个或多个非 '/' 字符</li>
     * <li>双星号，匹配零个或多个任意字符</li>
     * <li>问号，匹配一个字符</li>
     * <li>任何其他正则表达式风格的语法，例如字符集(用方括号表示)——表达式的其余部分
     * 在转义点字符后传递给 Java 正则表达式解析器</li>
     * </ul>
     *
     * <p>
     * 通配符字符串以简化的方式转换为正则表达式如果你需要更复杂的模式匹配，
     * 请直接通过 {@link #getResourcesMatchingPattern(Pattern)} 使用正则表达式
     *
     * @param wildcardString
     *            用于匹配 {@link Resource} 路径的通配符(glob)模式
     * @return 在被接受的包中找到的路径与请求的通配符字符串匹配的所有资源的列表
     */
    public ResourceList getResourcesMatchingWildcard(final String wildcardString) {
        if (closed.get()) {
            throw new IllegalArgumentException("Cannot use a ScanResult after it has been closed");
        }
        return getResourcesMatchingPattern(Filter.globToPattern(wildcardString, /* simpleGlob = */ false));
    }

    // -------------------------------------------------------------------------------------------------------------
    // 清理

    /**
     * 清除缓存的资源数据
     */
    void close() {
        if (allAcceptedResourcesCached != null) {
            for (final Resource classpathResource : allAcceptedResourcesCached) {
                classpathResource.close();
            }
            allAcceptedResourcesCached.clear();
            allAcceptedResourcesCached = null;
        }
        if (pathToAcceptedResourcesCached != null) {
            pathToAcceptedResourcesCached.clear();
            pathToAcceptedResourcesCached = null;
        }
    }
}
