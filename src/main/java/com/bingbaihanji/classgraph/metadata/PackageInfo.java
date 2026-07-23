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
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.scan.ScanConfig;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.util.*;

/** 保存扫描过程中遇到的包的元数据 */
public class PackageInfo implements Comparable<PackageInfo>, Named {
    /** 包名称 */
    private String name;

    /**
     * 对 package-info.class 文件上的注解的唯 {@link AnnotationInfo} 对象集合(如果存在)，否则为 null
     */
    private Set<AnnotationInfo> annotationInfoSet;

    /** 对 package-info.class 文件上的注解的 {@link AnnotationInfo}(如果存在)，否则为 null */
    private AnnotationInfoList annotationInfo;

    /** 此包的父包 */
    private PackageInfo parent;

    /** 此包的子包 */
    private Set<PackageInfo> children;

    /** 包中的类集合 */
    private Map<String, ClassInfo> memberClassNameToClassInfo;

    // -------------------------------------------------------------------------------------------------------------

    /** 反序列化构造函数 */
    PackageInfo() {
        // 空
    }

    /**
     * 构造一个 PackageInfo 对象
     *
     * @param packageName
     *            包名称
     */
    PackageInfo(final String packageName) {
        this.name = packageName;
    }

    /**
     * 获取父包的名称，或命名类所属包的名称
     *
     * @param packageOrClassName
     *            包名或类名
     * @return 父包名称，或命名类所属包的名称，如果 packageOrClassName 是根包("")则返回 null
     */
    public static String getParentPackageName(final String packageOrClassName) {
        if (packageOrClassName.isEmpty()) {
            return null;
        }
        final int lastDotIdx = packageOrClassName.lastIndexOf('.');
        return lastDotIdx < 0 ? "" : packageOrClassName.substring(0, lastDotIdx);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取指定名称包的 {@link PackageInfo} 对象，如果不存在则创建它，同时为任何尚未创建的
     * 所需父包创建 {@link PackageInfo} 对象
     *
     * @param packageName
     *            包名称
     * @param packageNameToPackageInfo
     *            从包名到包信息的映射
     * @param ScanConfig
     *            ScanConfig 配置
     * @return 指定名称包的 {@link PackageInfo}
     */
    public static PackageInfo getOrCreatePackage(final String packageName,
                                                 final Map<String, PackageInfo> packageNameToPackageInfo, final ScanConfig ScanConfig) {
        // 获取或创建此包的 PackageInfo 对象
        PackageInfo packageInfo = packageNameToPackageInfo.get(packageName);
        if (packageInfo != null) {
            // 此包的 PackageInfo 对象已存在
            return packageInfo;
        }

        // 为此包创建新的 PackageInfo
        packageNameToPackageInfo.put(packageName, packageInfo = new PackageInfo(packageName));

        // 如果这不是根包("")
        if (!packageName.isEmpty()) {
            // 递归为父包创建 PackageInfo 对象(直到到达已存在或不被接受的父包)，
            // 并将每个祖先包连接到其父包
            final String parentPackageName = getParentPackageName(packageInfo.name);
            if (ScanConfig.packageAcceptReject.isAcceptedAndNotRejected(parentPackageName)
                    || ScanConfig.packagePrefixAcceptReject.isAcceptedAndNotRejected(parentPackageName)) {
                final PackageInfo parentPackageInfo = getOrCreatePackage(parentPackageName,
                        packageNameToPackageInfo, ScanConfig);
                if (parentPackageInfo != null) {
                    // 将包链接到父包
                    if (parentPackageInfo.children == null) {
                        parentPackageInfo.children = new HashSet<>();
                    }
                    parentPackageInfo.children.add(packageInfo);
                    packageInfo.parent = parentPackageInfo;
                }
            }
        }

        // 返回新创建的 PackageInfo 对象
        return packageInfo;
    }

    /**
     * 包名称(对于根包则为 "")
     *
     * @return 名称
     */
    @Override
    public String getName() {
        return name;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 添加在包描述符 ClassParser 中找到的注解
     *
     * @param packageAnnotations
     *            包注解
     */
    public void addAnnotations(final AnnotationInfoList packageAnnotations) {
        // 添加来自 package-info.class 文件的类注解
        if (packageAnnotations != null && !packageAnnotations.isEmpty()) {
            if (annotationInfoSet == null) {
                annotationInfoSet = new LinkedHashSet<>();
            }
            annotationInfoSet.addAll(packageAnnotations);
        }
    }

    /**
     * 将 package-info.class 文件的 {@link ClassInfo} 对象合并到此 PackageInfo 中
     * (同一个 package-info.class 文件可能存在于不同模块中同一个包的多个定义中)
     *
     * @param classInfo
     *            要添加到包中的 {@link ClassInfo} 对象
     */
    public void addClassInfo(final ClassInfo classInfo) {
        if (memberClassNameToClassInfo == null) {
            memberClassNameToClassInfo = new HashMap<>();
        }
        memberClassNameToClassInfo.put(classInfo.getName(), classInfo);
    }

    public void setScanResult(final ScanResult scanResult) {
        if (annotationInfoSet != null) {
            for (final AnnotationInfo ai : annotationInfoSet) {
                ai.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取此包上的某个注解，如果包没有该注解则返回 null
     *
     * @param annotation
     *            注解类
     * @return 表示此包上该注解的 {@link AnnotationInfo} 对象，如果包没有该注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    /**
     * 获取此包上的某个命名注解，如果包没有该命名注解则返回 null
     *
     * @param annotationName
     *            注解名称
     * @return 表示此包上该命名注解的 {@link AnnotationInfo} 对象，如果包没有该命名注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final String annotationName) {
        for (final AnnotationInfo ai : getAnnotationInfo()) {
            if (ai.getName().equals(annotationName)) {
                return ai;
            }
        }
        return null;
    }

    /**
     * 获取 {@code package-info.class} 文件上的所有注解
     *
     * @return {@code package-info.class} 文件上的所有注解
     */
    public AnnotationInfoList getAnnotationInfo() {
        if (annotationInfo == null) {
            if (annotationInfoSet == null) {
                annotationInfo = AnnotationInfoList.EMPTY_LIST;
            } else {
                annotationInfo = new AnnotationInfoList();
                annotationInfo.addAll(annotationInfoSet);
            }
        }
        return annotationInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 检查此包是否有该注解
     *
     * @param annotation
     *            注解类
     * @return 如果此包有该注解则返回 true
     */
    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    /**
     * 检查此包是否有该命名注解
     *
     * @param annotationName
     *            注解名称
     * @return 如果此包有该命名注解则返回 true
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotationInfo().containsName(annotationName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 此包的父包，如果是根包则返回 null
     *
     * @return 父包，如果是根包则返回 null
     */
    public PackageInfo getParent() {
        return parent;
    }

    /**
     * 此包的子包，如果没有则返回空列表
     *
     * @return 子包，如果没有则返回空列表
     */
    public PackageInfoList getChildren() {
        if (children == null) {
            return PackageInfoList.EMPTY_LIST;
        }
        final PackageInfoList childrenSorted = new PackageInfoList(children);
        // 确保子包已排序
        CollectionUtils.sortIfNotEmpty(childrenSorted, new Comparator<PackageInfo>() {
            @Override
            public int compare(final PackageInfo o1, final PackageInfo o2) {
                return o1.name.compareTo(o2.name);
            }
        });
        return childrenSorted;
    }

    /**
     * 获取此包中指定名称类的 {@link ClassInfo} 对象，如果在此包中未找到该类则返回 null
     *
     * @param className
     *            类名
     * @return 此包中指定名称类的 {@link ClassInfo} 对象，如果在此包中未找到该类则返回 null
     */
    public ClassInfo getClassInfo(final String className) {
        return memberClassNameToClassInfo == null ? null : memberClassNameToClassInfo.get(className);
    }

    /**
     * 获取此包中所有成员类的 {@link ClassInfo} 对象
     *
     * @return 此包中所有成员类的 {@link ClassInfo} 对象
     */
    public ClassInfoList getClassInfo() {
        return memberClassNameToClassInfo == null ? ClassInfoList.EMPTY_LIST
                : new ClassInfoList(new HashSet<>(memberClassNameToClassInfo.values()), /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 递归获取此包内的 {@link ClassInfo} 对象
     *
     * @param reachableClassInfo
     *            可到达的类信息集合
     */
    private void obtainClassInfoRecursive(final Set<ClassInfo> reachableClassInfo) {
        if (memberClassNameToClassInfo != null) {
            reachableClassInfo.addAll(memberClassNameToClassInfo.values());
        }
        for (final PackageInfo subPackageInfo : getChildren()) {
            subPackageInfo.obtainClassInfoRecursive(reachableClassInfo);
        }
    }

    /**
     * 获取此包或其子包中所有成员类的 {@link ClassInfo} 对象
     *
     * @return 此包或其子包中所有成员类的 {@link ClassInfo} 对象
     */
    public ClassInfoList getClassInfoRecursive() {
        final Set<ClassInfo> reachableClassInfo = new HashSet<>();
        obtainClassInfoRecursive(reachableClassInfo);
        return new ClassInfoList(reachableClassInfo, /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final PackageInfo o) {
        return this.name.compareTo(o.name);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return name.hashCode();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof PackageInfo)) {
            return false;
        }
        return this.name.equals(((PackageInfo) obj).name);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return name;
    }
}
