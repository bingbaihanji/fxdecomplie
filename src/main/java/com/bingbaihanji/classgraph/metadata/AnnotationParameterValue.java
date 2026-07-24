 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.reflect.Array;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 用于将注解参数名与注解参数值配对包装的包装器 */
public class AnnotationParameterValue extends MetadataNode
        implements Named, Comparable<AnnotationParameterValue> {
    /** 参数名 */
    private String name;

    /** 参数值 */
    private TypedValue value;

    /** 用于反序列化的默认构造函数 */
    AnnotationParameterValue() {
        super();
    }

    /**
     * 构造函数
     *
     * @param name
     *            注解参数名
     * @param value
     *            注解参数值
     */
    public AnnotationParameterValue(final String name, final Object value) {
        super();
        this.name = name;
        this.value = new TypedValue(value);
    }

    /**
     * 将注解参数值的字符串表示写入缓冲区
     *
     * @param val
     *            参数值
     * @param useSimpleNames
     *            是否使用简单名称
     * @param buf
     *            缓冲区
     */
    private static void toString(final Object val, final boolean useSimpleNames, final StringBuilder buf) {
        if (val == null) {
            buf.append("null");
        } else if (val instanceof MetadataNode) {
            ((MetadataNode) val).toString(useSimpleNames, buf);
        } else {
            buf.append(val);
        }
    }

    /**
     * 获取注解参数名
     *
     * @return 注解参数名
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * 获取注解参数值
     *
     * @return 注解参数值可能为以下类型之一：
     *         <ul>
     *         <li>String —— 字符串常量
     *         <li>String[] —— 字符串数组
     *         <li>包装类型，如 Integer 或 Character —— 基本类型常量
     *         <li>一维基本类型数组(即 int[]、long[]、short[]、char[]、byte[]、boolean[]、
     *         float[] 或 double[])—— 基本类型数组
     *         <li>一维 {@link Object}[] 数组 —— 数组类型(且数组元素类型可能为此列表中的类型之一)
     *         <li>{@link AnnotationEnumValue} —— 枚举常量(包装了枚举类及其常量的字符串名称)
     *         <li>{@link AnnotationClassRef} —— 注解中的 Class 引用(包装了被引用类的名称)
     *         <li>{@link AnnotationInfo} —— 嵌套注解
     *         </ul>
     */
    public Object getValue() {
        return value == null ? null : value.get();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 设置(更新)注解参数的值用于将包含包装类型的 Object[] 数组替换为基本类型数组
     *
     * @param newValue
     *            新的值
     */
    void setValue(final Object newValue) {
        this.value = new TypedValue(newValue);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    public String getClassName() {
        // getClassInfo() 对此类型无效，因此 getClassName() 无需实现
        throw new IllegalArgumentException("getClassName() cannot be called here");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassInfo()
     */
    @Override
    public ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() cannot be called here");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (value != null) {
            value.setScanResult(scanResult);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取注解参数中引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        if (value != null) {
            value.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        }
    }

    /**
     * 对于基本类型数组参数，将包含包装类型的 Object[] 数组替换为基本类型数组(需要检查注解类的每个方法
     * 的类型，以确定其是否为基本类型数组)
     *
     * @param annotationClassInfo
     *            注解类信息
     */
    void convertWrapperArraysToPrimitiveArrays(final ClassInfo annotationClassInfo) {
        if (value != null) {
            value.convertWrapperArraysToPrimitiveArrays(annotationClassInfo, name);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 实例化一个注解参数值
     *
     * @param annotationClassInfo
     *            注解类信息
     * @return 实例
     */
    Object instantiate(final ClassInfo annotationClassInfo) {
        return value.instantiateOrGet(annotationClassInfo, name);
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final AnnotationParameterValue other) {
        if (other == this) {
            return 0;
        }
        final int diff = name.compareTo(other.getName());
        if (diff != 0) {
            return diff;
        }
        if (value.equals(other.value)) {
            return 0;
        }
        // 使用 toString() 顺序(可能较慢)作为最后的比较手段 —— 仅当注解具有
        // 多个同名但不同值的参数时才发生
        final Object p0 = getValue();
        final Object p1 = other.getValue();
        return p0 == null || p1 == null ? (p0 == null ? 0 : 1) - (p1 == null ? 0 : 1)
                : toStringParamValueOnly().compareTo(other.toStringParamValueOnly());
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AnnotationParameterValue)) {
            return false;
        }
        final AnnotationParameterValue other = (AnnotationParameterValue) obj;
        return this.name.equals(other.name) && (value == null) == (other.value == null)
                && (value == null || value.equals(other.value));
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(name, value);
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        buf.append(name);
        buf.append("=");
        toStringParamValueOnly(useSimpleNames, buf);
    }

    /**
     * 转换为字符串，仅包含参数值
     *
     * @param buf
     *            缓冲区
     */
    void toStringParamValueOnly(final boolean useSimpleNames, final StringBuilder buf) {
        if (value == null) {
            buf.append("null");
        } else {
            final Object paramVal = value.get();
            final Class<?> valClass = paramVal.getClass();
            if (valClass.isArray()) {
                buf.append('{');
                for (int j = 0, n = Array.getLength(paramVal); j < n; j++) {
                    if (j > 0) {
                        buf.append(", ");
                    }
                    final Object elt = Array.get(paramVal, j);
                    toString(elt, useSimpleNames, buf);
                }
                buf.append('}');
            } else if (paramVal instanceof String) {
                buf.append('"');
                buf.append(paramVal.toString().replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"));
                buf.append('"');
            } else if (paramVal instanceof Character) {
                buf.append('\'');
                buf.append(paramVal.toString().replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r"));
                buf.append('\'');
            } else {
                toString(paramVal, useSimpleNames, buf);
            }
        }
    }

    /**
     * 转换为字符串，仅包含参数值
     *
     * @return 字符串
     */
    private String toStringParamValueOnly() {
        final StringBuilder buf = new StringBuilder();
        toStringParamValueOnly(false, buf);
        return buf.toString();
    }
}
