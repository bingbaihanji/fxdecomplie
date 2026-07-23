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

import com.bingbaihanji.classgraph.resource.Slice;
import com.bingbaihanji.classgraph.scan.Filter.FilterLeafname;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** ZIP 文件切片(PhysicalZipFile 中的一个字节子范围) */
public class ZipFileSlice {
    /** 底层的物理 ZIP 文件 */
    protected final PhysicalZipFile physicalZipFile;
    /** 父切片，如果这是顶层切片(整个 ZIP 文件)则为 null */
    private final ZipFileSlice parentZipFileSlice;
    /** 对于顶层 ZIP 文件切片，表示 ZIP 文件路径；对于嵌套切片，表示 ZIP 文件条目的名称/路径 */
    private final String pathWithinParentZipFileSlice;
    /** 包含 ZIP 文件的 {@link Slice} */
    public Slice slice;

    /**
     * 创建一个包装顶层 {@link PhysicalZipFile} 的 ZipFileSlice
     *
     * @param physicalZipFile
     *            物理 ZIP 文件
     */
    ZipFileSlice(final PhysicalZipFile physicalZipFile) {
        this.parentZipFileSlice = null;
        this.physicalZipFile = physicalZipFile;
        this.slice = physicalZipFile.slice;
        this.pathWithinParentZipFileSlice = physicalZipFile.getPathStr();
    }

    /**
     * 创建一个 ZipFileSlice，包装从嵌套 JAR 解压或膨胀到内存或磁盘的 {@link PhysicalZipFile}
     *
     * @param physicalZipFile
     *            已解压到 RAM 的物理 ZIP 文件
     * @param zipEntry
     *            ZIP 条目
     */
    ZipFileSlice(final PhysicalZipFile physicalZipFile, final FastZipEntry zipEntry) {
        this.parentZipFileSlice = zipEntry.parentLogicalZipFile;
        this.physicalZipFile = physicalZipFile;
        this.slice = physicalZipFile.slice;
        this.pathWithinParentZipFileSlice = zipEntry.entryName;
    }

    /**
     * 创建一个 ZipFileSlice，包装单个已存储(未压缩)的 {@link FastZipEntry}
     *
     * @param zipEntry
     *            ZIP 条目
     * @throws IOException
     *             如果发生 I/O 异常
     * @throws InterruptedException
     *             如果线程被中断
     */
    ZipFileSlice(final FastZipEntry zipEntry) throws IOException, InterruptedException {
        this.parentZipFileSlice = zipEntry.parentLogicalZipFile;
        this.physicalZipFile = zipEntry.parentLogicalZipFile.physicalZipFile;
        this.slice = zipEntry.getSlice();
        this.pathWithinParentZipFileSlice = zipEntry.entryName;
    }

    /**
     * 克隆构造函数
     *
     * @param other
     *            要克隆的 {@link ZipFileSlice}
     */
    ZipFileSlice(final ZipFileSlice other) {
        this.parentZipFileSlice = other.parentZipFileSlice;
        this.physicalZipFile = other.physicalZipFile;
        this.slice = other.slice;
        this.pathWithinParentZipFileSlice = other.pathWithinParentZipFileSlice;
    }

    /**
     * 检查此 ZIP 文件切片及其所有父切片是否在 JAR 文件接受/拒绝条件中被接受且未被拒绝
     *
     * @param jarAcceptReject
     *            JAR 文件接受/拒绝条件
     * @return 如果此 ZIP 文件切片及其所有父切片在 JAR 文件接受/拒绝条件中被接受且未被拒绝，则返回 true
     */
    public boolean isAcceptedAndNotRejected(final FilterLeafname jarAcceptReject) {
        return jarAcceptReject.isAcceptedAndNotRejected(pathWithinParentZipFileSlice) //
                && (parentZipFileSlice == null || parentZipFileSlice.isAcceptedAndNotRejected(jarAcceptReject));
    }

    /**
     * 获取父 ZipFileSlice，如果这是顶层切片(即此切片包装整个物理 ZIP 文件)，则返回 null
     *
     * @return 父 ZipFileSlice，如果这是顶层切片则返回 null
     */
    public ZipFileSlice getParentZipFileSlice() {
        return parentZipFileSlice;
    }

    /**
     * 获取切片的名称(父 ZIP 文件切片中的条目名称/路径，或者如果此切片是顶层切片(即此切片包装整个物理 ZIP 文件)，
     * 则为物理 ZIP 文件的路径)
     *
     * @return 切片的名称
     */
    public String getPathWithinParentZipFileSlice() {
        return pathWithinParentZipFileSlice;
    }

    /**
     * 按照自上而下的祖先顺序递归追加路径
     *
     * @param buf
     *            要追加路径的 StringBuilder
     */
    private void appendPath(final StringBuilder buf) {
        if (parentZipFileSlice != null) {
            parentZipFileSlice.appendPath(buf);
            if (buf.length() > 0) {
                buf.append("!/");
            }
        }
        buf.append(pathWithinParentZipFileSlice);
    }

    /**
     * 获取此 ZIP 文件切片的路径，例如 "/path/to/jarfile.jar!/nestedjar1.jar"
     *
     * @return 此 ZIP 文件切片的路径
     */
    public String getPath() {
        final StringBuilder buf = new StringBuilder();
        appendPath(buf);
        return buf.toString();
    }

    /**
     * 获取此 ZipFileSlice 所属的物理 {@link File}
     *
     * @return 此 ZipFileSlice 所属的物理 {@link File}，如果此文件是从 URL 直接下载到 RAM 则返回 null
     */
    public File getPhysicalFile() {
        final Path path = physicalZipFile.getPath();
        if (path != null) {
            try {
                return path.toFile();
            } catch (final UnsupportedOperationException e) {
                // 文件系统支持 Path API 但不支持 File API
                return null;
            }
        } else {
            return physicalZipFile.getFile();
        }
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.fastzipfilereader.ZipFileSlice#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ZipFileSlice)) {
            return false;
        } else {
            final ZipFileSlice other = (ZipFileSlice) o;
            return Objects.equals(physicalZipFile, other.physicalZipFile) && Objects.equals(slice, other.slice)
                    && Objects.equals(pathWithinParentZipFileSlice, other.pathWithinParentZipFileSlice);
        }
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.fastzipfilereader.ZipFileSlice#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(physicalZipFile, slice, pathWithinParentZipFileSlice);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        final String path = getPath();
        String fileStr = physicalZipFile.getPath() == null ? null : physicalZipFile.getPath().toString();
        if (fileStr == null) {
            fileStr = physicalZipFile.getFile() == null ? null : physicalZipFile.getFile().toString();
        }
        return "[" + (fileStr != null && !fileStr.equals(path) ? path + " -> " + fileStr : path) + " ; byte range: "
                + slice.sliceStartPos + ".." + (slice.sliceStartPos + slice.sliceLength) + " / "
                + physicalZipFile.length() + "]";
    }
}