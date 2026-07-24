 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.classpath.Classpath;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.util.Assert;
import com.bingbaihanji.classgraph.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/** 保存扫描过程中遇到的包的元数据 */
public class ModuleInfo implements Comparable<ModuleInfo>, Named {
    /** 模块名称 */
    private String name;

    /** 类路径元素 */
    private transient Classpath Classpath;

    /** {@link ModuleRef} 引用 */
    private transient ModuleRef moduleRef;

    /** 模块位置(URI 形式) */
    private transient URI locationURI;

    /**
     * 对 module-info.class 文件上的注解的唯 {@link AnnotationInfo} 对象集合(如果存在)，否则为 null
     */
    private Set<AnnotationInfo> annotationInfoSet;

    /** 对 module-info.class 文件上的注解的 {@link AnnotationInfo} 对象集合(如果存在)，否则为 null */
    private AnnotationInfoList annotationInfo;

    /** 在这个类中找到的包的 {@link PackageInfo} 对象集合(如果有)，否则为 null */
    private Set<PackageInfo> packageInfoSet;

    /** 模块中的类集合 */
    private Set<ClassInfo> classInfoSet;

    // -------------------------------------------------------------------------------------------------------------

    /** 反序列化构造函数 */
    public ModuleInfo() {
        // 空
    }

    /**
     * 构造一个 ModuleInfo 对象
     *
     * @param moduleRef
     *            模块引用
     * @param Classpath
     *            类路径元素
     */
    public ModuleInfo(final ModuleRef moduleRef, final Classpath Classpath) {
        this.moduleRef = moduleRef;
        this.Classpath = Classpath;
        this.name = Classpath.getModuleName();
    }

    /**
     * 模块名称，对于未命名模块则为 {@code ""}
     *
     * @return 模块名称，对于未命名模块则为 {@code ""}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 模块位置，对于位置未知的模块则为 null
     *
     * @return 模块位置，对于位置未知的模块则为 null
     */
    public URI getLocation() {
        if (locationURI == null) {
            locationURI = moduleRef != null ? moduleRef.getLocation() : null;
            if (locationURI == null) {
                locationURI = Classpath.getURI();
            }
        }
        return locationURI;
    }

    /**
     * 此模块的 {@link ModuleRef}如果此模块是从传统类路径上包含 {@code module-info.class} 文件的类路径元素
     * 获取的，则返回 null
     *
     * @return 此模块的 {@link ModuleRef}如果此模块是从传统类路径上包含 {@code module-info.class} 文件的
     *         类路径元素获取的，则返回 null
     */
    public ModuleRef getModuleRef() {
        return moduleRef;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 向此 {@link ModuleInfo} 添加一个 {@link ClassInfo} 对象
     *
     * @param classInfo
     *            要添加的 {@link ClassInfo} 对象
     */
    public void addClassInfo(final ClassInfo classInfo) {
        if (classInfoSet == null) {
            classInfoSet = new HashSet<>();
        }
        classInfoSet.add(classInfo);
    }

    /**
     * 获取此模块中指定名称类的 {@link ClassInfo} 对象，如果在此模块中未找到该类则返回 null
     *
     * @param className
     *            类名
     * @return 此模块中指定名称类的 {@link ClassInfo} 对象，如果在此模块中未找到该类则返回 null
     */
    public ClassInfo getClassInfo(final String className) {
        for (final ClassInfo ci : classInfoSet) {
            if (ci.getName().equals(className)) {
                return ci;
            }
        }
        return null;
    }

    /**
     * 获取此包中所有成员类的 {@link ClassInfo} 对象列表
     *
     * @return 此包中所有成员类的 {@link ClassInfo} 对象列表
     */
    public ClassInfoList getClassInfo() {
        return new ClassInfoList(classInfoSet, /* sortByName = */ true);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 向此 {@link ModuleInfo} 添加一个 {@link PackageInfo} 对象
     *
     * @param packageInfo
     *            要添加的 {@link PackageInfo} 对象
     */
    public void addPackageInfo(final PackageInfo packageInfo) {
        if (packageInfoSet == null) {
            packageInfoSet = new HashSet<>();
        }
        packageInfoSet.add(packageInfo);
    }

    /**
     * 获取此模块中指定名称包的 {@link PackageInfo} 对象，如果在此模块中未找到该包则返回 null
     *
     * @param packageName
     *            包名
     * @return 此模块中指定名称包的 {@link PackageInfo} 对象，如果在此模块中未找到该包则返回 null
     */
    public PackageInfo getPackageInfo(final String packageName) {
        if (packageInfoSet == null) {
            return null;
        }
        for (final PackageInfo pi : packageInfoSet) {
            if (pi.getName().equals(packageName)) {
                return pi;
            }
        }
        return null;
    }

    /**
     * 获取此模块中所有成员包的 {@link PackageInfo} 对象列表
     *
     * @return 此模块中所有成员包的 {@link PackageInfo} 对象列表
     */
    public PackageInfoList getPackageInfo() {
        if (packageInfoSet == null) {
            return new PackageInfoList(1);
        }
        final PackageInfoList packageInfoList = new PackageInfoList(packageInfoSet);
        CollectionUtils.sortIfNotEmpty(packageInfoList);
        return packageInfoList;
    }

    // -------------------------------------------------------------------------------------------------------------

    public void setScanResult(final ScanResult scanResult) {
        if (annotationInfoSet != null) {
            for (final AnnotationInfo ai : annotationInfoSet) {
                ai.setScanResult(scanResult);
            }
        }
    }

    /**
     * 添加在模块描述符 ClassParser 中找到的注解
     *
     * @param moduleAnnotations
     *            模块注解
     */
    public void addAnnotations(final AnnotationInfoList moduleAnnotations) {
        // 目前 module-info.class 文件中仅使用类注解
        if (moduleAnnotations != null && !moduleAnnotations.isEmpty()) {
            if (annotationInfoSet == null) {
                annotationInfoSet = new LinkedHashSet<>();
            }
            annotationInfoSet.addAll(moduleAnnotations);
        }
    }

    /**
     * 获取此模块上的某个注解，如果模块没有该注解则返回 null
     *
     * @param annotation
     *            注解类
     * @return 表示此模块上该注解的 {@link AnnotationInfo} 对象，如果模块没有该注解则返回 null
     */
    public AnnotationInfo getAnnotationInfo(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return getAnnotationInfo(annotation.getName());
    }

    /**
     * 获取此模块上的某个命名注解，如果模块没有该命名注解则返回 null
     *
     * @param annotationName
     *            注解名称
     * @return 表示此模块上该命名注解的 {@link AnnotationInfo} 对象，如果模块没有该命名注解则返回 null
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
     * @return {@code package-info.class} 文件上注解的 {@link AnnotationInfo} 对象列表
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

    /**
     * 检查此模块是否有该注解
     *
     * @param annotation
     *            注解类
     * @return 如果此模块有该注解则返回 true
     */
    public boolean hasAnnotation(final Class<? extends Annotation> annotation) {
        Assert.isAnnotation(annotation);
        return hasAnnotation(annotation.getName());
    }

    /**
     * 检查此模块是否有该命名注解
     *
     * @param annotationName
     *            注解名称
     * @return 如果此模块有该命名注解则返回 true
     */
    public boolean hasAnnotation(final String annotationName) {
        return getAnnotationInfo().containsName(annotationName);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final ModuleInfo other) {
        final int diff = this.name.compareTo(other.name);
        if (diff != 0) {
            return diff;
        }
        final URI thisLoc = this.getLocation();
        final URI otherLoc = other.getLocation();
        if (thisLoc != null && otherLoc != null) {
            return thisLoc.compareTo(otherLoc);
        }
        return 0;
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
        } else if (!(obj instanceof ModuleInfo)) {
            return false;
        }
        return this.compareTo((ModuleInfo) obj) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return name;
    }
}
