package com.bingbaihanji.classgraph.type;

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypePathNode;
import com.bingbaihanji.classgraph.metadata.AnnotationInfo;
import com.bingbaihanji.classgraph.metadata.AnnotationInfoList;
import com.bingbaihanji.classgraph.metadata.MetadataNode;
import com.bingbaihanji.classgraph.scan.ScanResult;

import java.util.List;

/**
 * Java 类型签名子类包括 ClassType、MethodType 和 TypeSignature
 */
public abstract class HierarchicalType extends MetadataNode {
    protected AnnotationInfoList typeAnnotationInfo;

    /** 一个层次化的类型签名 */
    public HierarchicalType() {
        super();
    }

    /**
     * 添加一个类型注解
     *
     * @param annotationInfo
     *            注解
     */
    protected void addTypeAnnotation(final AnnotationInfo annotationInfo) {
        if (typeAnnotationInfo == null) {
            typeAnnotationInfo = new AnnotationInfoList(1);
        }
        typeAnnotationInfo.add(annotationInfo);
    }

    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (typeAnnotationInfo != null) {
            for (final AnnotationInfo annotationInfo : typeAnnotationInfo) {
                annotationInfo.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取此类型上的所有类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     *
     * @return 此类型上的所有类型注解的 {@link AnnotationInfo} 对象列表，如果没有则返回 null
     */
    public AnnotationInfoList getTypeAnnotationInfo() {
        return typeAnnotationInfo;
    }

    /**
     * 添加一个类型注解
     *
     * @param typePath
     *            类型路径
     * @param annotationInfo
     *            注解
     */
    protected abstract void addTypeAnnotation(List<TypePathNode> typePath, AnnotationInfo annotationInfo);

    /**
     * 将类型签名渲染为字符串
     *
     * @param useSimpleNames
     *            是否使用类的简单名称
     * @param annotationsToExclude
     *            要排除的顶层注解，用于消除重复(顶层注解同时是类/字段/方法注解和类型注解)
     * @param buf
     *            要写入的 {@link StringBuilder}
     */
    public abstract void toStringInternal(final boolean useSimpleNames, AnnotationInfoList annotationsToExclude,
                                          StringBuilder buf);

    /**
     * 将类型签名渲染为字符串
     *
     * @param useSimpleNames
     *            是否使用类的简单名称
     * @param buf
     *            要写入的 {@link StringBuilder}
     */
    @Override
    public void toString(final boolean useSimpleNames, final StringBuilder buf) {
        toStringInternal(useSimpleNames, /* annotationsToExclude = */ null, buf);
    }
}