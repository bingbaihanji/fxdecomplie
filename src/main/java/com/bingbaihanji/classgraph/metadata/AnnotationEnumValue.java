 
package com.bingbaihanji.classgraph.metadata;

import java.lang.reflect.Field;

/**
 * 用于包装枚举常量值(拆分为类名和常量名)的类，作为注解参数值使用
 */
public class AnnotationEnumValue extends MetadataNode implements Comparable<AnnotationEnumValue> {
    /** 类名 */
    private String className;

    /** 值名称 */
    private String valueName;

    /** 用于反序列化的默认构造函数 */
    AnnotationEnumValue() {
        super();
    }

    /**
     * 构造函数
     *
     * @param className
     *            枚举类名
     * @param constValueName
     *            枚举常量值名称
     */
    public AnnotationEnumValue(final String className, final String constValueName) {
        super();
        this.className = className;
        this.valueName = constValueName;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类名
     *
     * @return 枚举类的名称
     */
    @Override
    public String getClassName() {
        return className;
    }

    /**
     * 获取值名称
     *
     * @return 枚举常量值的名称
     */
    public String getValueName() {
        return valueName;
    }

    /**
     * 获取名称
     *
     * @return 枚举常量值的完全限定名，即 ({@link #getClassName()} + {#getValueName()})
     */
    public String getName() {
        return className + "." + valueName;
    }

    /**
     * 加载枚举类，实例化该类的枚举常量，并返回此 {@link AnnotationEnumValue} 所代表的枚举常量值
     *
     * @param ignoreExceptions
     *            如果为 true，则忽略类加载异常，在失败时返回 null
     * @return 此 {@link AnnotationEnumValue} 所代表的枚举常量值
     * @throws IllegalArgumentException
     *             如果类无法加载且 ignoreExceptions 为 false，或者枚举常量无效
     */
    public Object loadClassAndReturnEnumValue(final boolean ignoreExceptions) throws IllegalArgumentException {
        final Class<?> classRef = super.loadClass(ignoreExceptions);
        if (classRef == null) {
            if (ignoreExceptions) {
                return null;
            } else {
                throw new IllegalArgumentException("Enum class " + className + " could not be loaded");
            }
        }
        if (!classRef.isEnum()) {
            throw new IllegalArgumentException("Class " + className + " is not an enum");
        }
        Field field;
        try {
            field = classRef.getDeclaredField(valueName);
        } catch (final ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException("Could not find enum constant " + this, e);
        }
        if (!field.isEnumConstant()) {
            throw new IllegalArgumentException("Field " + this + " is not an enum constant");
        }
        try {
            return field.get(null);
        } catch (final ReflectiveOperationException | SecurityException e) {
            throw new IllegalArgumentException("Field " + this + " is not accessible", e);
        }
    }

    /**
     * 加载枚举类，实例化该类的枚举常量，并返回此 {@link AnnotationEnumValue} 所代表的枚举常量值
     *
     * @return 此 {@link AnnotationEnumValue} 所代表的枚举常量值
     * @throws IllegalArgumentException
     *             如果类无法加载，或枚举常量无效
     */
    public Object loadClassAndReturnEnumValue() throws IllegalArgumentException {
        return loadClassAndReturnEnumValue(/* ignoreExceptions = */ false);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(final AnnotationEnumValue o) {
        final int diff = className.compareTo(o.className);
        return diff == 0 ? valueName.compareTo(o.valueName) : diff;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AnnotationEnumValue)) {
            return false;
        }
        return compareTo((AnnotationEnumValue) obj) == 0;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return className.hashCode() * 11 + valueName.hashCode();
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        buf.append(useSimpleNames ? ClassInfo.getSimpleName(className) : className);
        buf.append('.');
        buf.append(valueName);
    }
}
