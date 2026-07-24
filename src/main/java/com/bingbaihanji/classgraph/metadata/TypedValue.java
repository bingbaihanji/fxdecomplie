 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.ArrayType;
import com.bingbaihanji.classgraph.type.BaseType;
import com.bingbaihanji.classgraph.type.ClassRef;
import com.bingbaihanji.classgraph.type.TypeSignature;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 联合类型，用于类型安全的 JSON 序列化/反序列化任何时候仅设置一个字段 */
class TypedValue extends MetadataNode {
    // 参数值按类型拆分到不同的字段中，以便序列化和反序列化正常工作
    // (无法正确序列化 Object 类型的字段，因为具体类型未
    // TODO: 移除 JSON 序列化后删除此类
    // 存储在 JSON 中)
    /** 枚举值 */
    private AnnotationEnumValue annotationEnumValue;

    /** 类引用 */
    private AnnotationClassRef annotationClassRef;

    /** 注解信息 */
    private AnnotationInfo annotationInfo;

    /** 字符串值 */
    private String stringValue;

    /** 整数(Integer)值 */
    private Integer integerValue;

    /** 长整数(Long)值 */
    private Long longValue;

    /** 短整数(Short)值 */
    private Short shortValue;

    /** 布尔值 */
    private Boolean booleanValue;

    /** 字符值 */
    private Character characterValue;

    /** 单精度浮点值 */
    private Float floatValue;

    /** 双精度浮点值 */
    private Double doubleValue;

    /** 字节值 */
    private Byte byteValue;

    /** 字符串数组值 */
    private String[] stringArrayValue;

    /** int 数组值 */
    private int[] intArrayValue;

    /** long 数组值 */
    private long[] longArrayValue;

    /** short 数组值 */
    private short[] shortArrayValue;

    /** boolean 数组值 */
    private boolean[] booleanArrayValue;

    /** char 数组值 */
    private char[] charArrayValue;

    /** float 数组值 */
    private float[] floatArrayValue;

    /** double 数组值 */
    private double[] doubleArrayValue;

    /** byte 数组值 */
    private byte[] byteArrayValue;

    /** Object 数组值 */
    private TypedValue[] objectArrayValue;

    // -------------------------------------------------------------------------------------------------------------

    /** 反序列化的默认构造函数 */
    public TypedValue() {
        super();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 构造函数
     *
     * @param annotationParamValue
     *            注解参数值
     */
    public TypedValue(final Object annotationParamValue) {
        super();
        if (annotationParamValue != null) {
            final Class<?> annotationParameterValueClass = annotationParamValue.getClass();
            if (annotationParameterValueClass.isArray()) {
                // 注解参数值需要支持一维基本类型数组和字符串数组
                if (annotationParameterValueClass == String[].class) {
                    stringArrayValue = (String[]) annotationParamValue;
                } else if (annotationParameterValueClass == int[].class) {
                    intArrayValue = (int[]) annotationParamValue;
                } else if (annotationParameterValueClass == long[].class) {
                    longArrayValue = (long[]) annotationParamValue;
                } else if (annotationParameterValueClass == short[].class) {
                    shortArrayValue = (short[]) annotationParamValue;
                } else if (annotationParameterValueClass == boolean[].class) {
                    booleanArrayValue = (boolean[]) annotationParamValue;
                } else if (annotationParameterValueClass == char[].class) {
                    charArrayValue = (char[]) annotationParamValue;
                } else if (annotationParameterValueClass == float[].class) {
                    floatArrayValue = (float[]) annotationParamValue;
                } else if (annotationParameterValueClass == double[].class) {
                    doubleArrayValue = (double[]) annotationParamValue;
                } else if (annotationParameterValueClass == byte[].class) {
                    byteArrayValue = (byte[]) annotationParamValue;
                } else {
                    // 对象数组类型 —— 包装每个单独的元素
                    final int n = Array.getLength(annotationParamValue);
                    objectArrayValue = new TypedValue[n];
                    for (int i = 0; i < n; i++) {
                        objectArrayValue[i] = new TypedValue(Array.get(annotationParamValue, i));
                    }
                }
            } else if (annotationParamValue instanceof AnnotationEnumValue) {
                annotationEnumValue = (AnnotationEnumValue) annotationParamValue;
            } else if (annotationParamValue instanceof AnnotationClassRef) {
                annotationClassRef = (AnnotationClassRef) annotationParamValue;
            } else if (annotationParamValue instanceof AnnotationInfo) {
                annotationInfo = (AnnotationInfo) annotationParamValue;
            } else if (annotationParamValue instanceof String) {
                stringValue = (String) annotationParamValue;
            } else if (annotationParamValue instanceof Integer) {
                integerValue = (Integer) annotationParamValue;
            } else if (annotationParamValue instanceof Long) {
                longValue = (Long) annotationParamValue;
            } else if (annotationParamValue instanceof Short) {
                shortValue = (Short) annotationParamValue;
            } else if (annotationParamValue instanceof Boolean) {
                booleanValue = (Boolean) annotationParamValue;
            } else if (annotationParamValue instanceof Character) {
                characterValue = (Character) annotationParamValue;
            } else if (annotationParamValue instanceof Float) {
                floatValue = (Float) annotationParamValue;
            } else if (annotationParamValue instanceof Double) {
                doubleValue = (Double) annotationParamValue;
            } else if (annotationParamValue instanceof Byte) {
                byteValue = (Byte) annotationParamValue;
            } else {
                throw new IllegalArgumentException(
                        "不支持的注解参数值类型: " + annotationParameterValueClass.getName());
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 实例化或获取包装的值
     *
     * @param annotationClassInfo
     *            如果非 null，则将此对象实例化为该注解类的参数值
     * @param paramName
     *            如果非 null，则将此对象实例化为该命名参数的值
     * @return 此包装类包装的值
     */
    Object instantiateOrGet(final ClassInfo annotationClassInfo, final String paramName) {
        final boolean instantiate = annotationClassInfo != null;
        if (annotationEnumValue != null) {
            return instantiate ? annotationEnumValue.loadClassAndReturnEnumValue() : annotationEnumValue;
        } else if (annotationClassRef != null) {
            return instantiate ? annotationClassRef.loadClass() : annotationClassRef;
        } else if (annotationInfo != null) {
            return instantiate ? annotationInfo.loadClassAndInstantiate() : annotationInfo;
        } else if (stringValue != null) {
            return stringValue;
        } else if (integerValue != null) {
            return integerValue;
        } else if (longValue != null) {
            return longValue;
        } else if (shortValue != null) {
            return shortValue;
        } else if (booleanValue != null) {
            return booleanValue;
        } else if (characterValue != null) {
            return characterValue;
        } else if (floatValue != null) {
            return floatValue;
        } else if (doubleValue != null) {
            return doubleValue;
        } else if (byteValue != null) {
            return byteValue;
        } else if (stringArrayValue != null) {
            return stringArrayValue;
        } else if (intArrayValue != null) {
            return intArrayValue;
        } else if (longArrayValue != null) {
            return longArrayValue;
        } else if (shortArrayValue != null) {
            return shortArrayValue;
        } else if (booleanArrayValue != null) {
            return booleanArrayValue;
        } else if (charArrayValue != null) {
            return charArrayValue;
        } else if (floatArrayValue != null) {
            return floatArrayValue;
        } else if (doubleArrayValue != null) {
            return doubleArrayValue;
        } else if (byteArrayValue != null) {
            return byteArrayValue;
        } else if (objectArrayValue != null) {
            // 获取数组的元素类型
            final Class<?> eltClass = instantiate
                    ? (Class<?>) getArrayValueClassOrName(annotationClassInfo, paramName, /* getClass = */ true)
                    : null;
            // 分配数组，如果无法确定元素类型则作为通用的 Object[] 数组，
            // 如果可以确定元素类型则作为特定元素类型的数组
            final Object annotationValueObjectArray = eltClass == null ? new Object[objectArrayValue.length]
                    : Array.newInstance(eltClass, objectArrayValue.length);
            // 填充数组实例
            for (int i = 0; i < objectArrayValue.length; i++) {
                if (objectArrayValue[i] != null) {
                    // 获取元素值(也可能导致元素被实例化)
                    final Object eltValue = objectArrayValue[i].instantiateOrGet(annotationClassInfo, paramName);
                    // 将可能已实例化的值存储到数组中
                    Array.set(annotationValueObjectArray, i, eltValue);
                }
            }
            return annotationValueObjectArray;
        } else {
            return null;
        }
    }

    /**
     * 获取此包装类包装的值
     *
     * @return 此包装类包装的值
     */
    public Object get() {
        return instantiateOrGet(null, null);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取数组元素的元素类型
     *
     * @param annotationClassInfo
     *            注解类
     * @param paramName
     *            参数名称
     * @param getClass
     *            如果为 true，则返回 {@code Class<?>} 引用，否则返回类名
     * @return 如果 getClass 为 true，则作为 {@code Class<?>} 引用返回数组值类型，否则作为
     *         字符串返回类名
     */
    private Object getArrayValueClassOrName(final ClassInfo annotationClassInfo, final String paramName,
                                            final boolean getClass) {
        // 在注解类中查找与注解参数同名的方法
        final MethodInfoList annotationMethodList = annotationClassInfo == null
                || annotationClassInfo.methodInfo == null ? null : annotationClassInfo.methodInfo.get(paramName);
        if (annotationClassInfo != null && annotationMethodList != null && !annotationMethodList.isEmpty()) {
            if (annotationMethodList.size() > 1) {
                // 在注解中，一个给定名称应该只有一个方法
                throw new IllegalArgumentException("注解类 " + annotationClassInfo.getName()
                        + " 中存在重复的注解参数方法 " + paramName + "()");
            }
            // 获取与注解参数同名的方法的结果类型
            final TypeSignature annotationMethodResultTypeSig = annotationMethodList.get(0)
                    .getTypeSignatureOrTypeDescriptor().getResultType();
            // 结果类型必须是数组类型
            if (!(annotationMethodResultTypeSig instanceof ArrayType)) {
                throw new IllegalArgumentException("注解类 " + annotationClassInfo.getName()
                        + " 中的注解参数 " + paramName
                        + " 包含数组，但没有数组类型签名");
            }
            final ArrayType arrayTypeSig = (ArrayType) annotationMethodResultTypeSig;
            if (arrayTypeSig.getNumDimensions() != 1) {
                throw new IllegalArgumentException("注解仅支持一维数组");
            }
            final TypeSignature elementTypeSig = arrayTypeSig.getElementTypeSignature();
            if (elementTypeSig instanceof ClassRef) {
                // 为非基本类型数组查找元素类型名称
                final ClassRef ClassRef = (ClassRef) elementTypeSig;
                return getClass ? ClassRef.loadClass() : ClassRef.getClassName();
            } else if (elementTypeSig instanceof BaseType) {
                // 为基本类型数组查找基本类型类名称
                final BaseType BaseType = (BaseType) elementTypeSig;
                return getClass ? BaseType.getType() : BaseType.getTypeStr();
            }
        } else {
            // 无法找到此名称的方法 —— 这是一个外部类
            // 在数组中查找第一个非 null 对象，并将其类型用作数组的元素类型
            for (final TypedValue elt : objectArrayValue) {
                if (elt != null) {
                    // 基本类型数组将被转换为包装类型的数组
                    return elt.integerValue != null ? (getClass ? Integer.class : "int")
                            : elt.longValue != null ? (getClass ? Long.class : "long")
                            : elt.shortValue != null ? (getClass ? Short.class : "short")
                            : elt.characterValue != null ? (getClass ? Character.class : "char")
                            : elt.byteValue != null ? (getClass ? Byte.class : "byte")
                            : elt.booleanValue != null
                            ? (getClass ? Boolean.class : "boolean")
                            : elt.doubleValue != null
                            ? (getClass ? Double.class : "double")
                            : elt.floatValue != null
                            ? (getClass ? Float.class
                            : "float")
                            : (getClass ? elt.getClass()
                            : elt.getClass()
                            .getName());
                }
            }
        }
        // 无法确定元素类型 —— 直接使用 Object
        return getClass ? Object.class : "java.lang.Object";
    }

    /**
     * 将包含包装类型的 Object[] 数组替换为基本类型数组
     *
     * @param annotationClassInfo
     *            注解类信息
     * @param paramName
     *            参数名称
     */
    void convertWrapperArraysToPrimitiveArrays(final ClassInfo annotationClassInfo, final String paramName) {
        if (annotationInfo != null) {
            // 递归转换嵌套注解中的基本类型数组
            annotationInfo.convertWrapperArraysToPrimitiveArrays();
        } else if (objectArrayValue != null) {
            for (final TypedValue elt : objectArrayValue) {
                if (elt.annotationInfo != null) {
                    // 递归
                    elt.annotationInfo.convertWrapperArraysToPrimitiveArrays();
                }
            }

            if (objectArrayValue.getClass().getComponentType().isArray()) {
                // 超过一个数组维度 —— 注解参数值不可能出现这种情况 => 跳过
                return;
            }

            // 在注解类中查找与注解参数同名的方法
            final String targetElementTypeName = (String) getArrayValueClassOrName(annotationClassInfo, paramName,
                    /* getClass = */ false);

            // 获取一维非基本类型数组的数组元素类型，并将其转换为基本类型数组
            switch (targetElementTypeName) {
                case "java.lang.String":
                    // 将包含 String 对象的 Object[] 数组转换为 String[] 数组
                    stringArrayValue = new String[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        stringArrayValue[j] = objectArrayValue[j].stringValue;
                    }
                    objectArrayValue = null;
                    break;
                case "int":
                    intArrayValue = new int[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        intArrayValue[j] = objectArrayValue[j].integerValue;
                    }
                    objectArrayValue = null;
                    break;
                case "long":
                    longArrayValue = new long[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        longArrayValue[j] = objectArrayValue[j].longValue;
                    }
                    objectArrayValue = null;
                    break;
                case "short":
                    shortArrayValue = new short[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        shortArrayValue[j] = objectArrayValue[j].shortValue;
                    }
                    objectArrayValue = null;
                    break;
                case "char":
                    charArrayValue = new char[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        charArrayValue[j] = objectArrayValue[j].characterValue;
                    }
                    objectArrayValue = null;
                    break;
                case "float":
                    floatArrayValue = new float[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        floatArrayValue[j] = objectArrayValue[j].floatValue;
                    }
                    objectArrayValue = null;
                    break;
                case "double":
                    doubleArrayValue = new double[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        doubleArrayValue[j] = objectArrayValue[j].doubleValue;
                    }
                    objectArrayValue = null;
                    break;
                case "boolean":
                    booleanArrayValue = new boolean[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        booleanArrayValue[j] = objectArrayValue[j].booleanValue;
                    }
                    objectArrayValue = null;
                    break;
                case "byte":
                    byteArrayValue = new byte[objectArrayValue.length];
                    for (int j = 0; j < objectArrayValue.length; j++) {
                        final TypedValue elt = objectArrayValue[j];
                        if (elt == null) {
                            throw new IllegalArgumentException("元素类型为 " + targetElementTypeName
                                    + " 的数组中存在非法 null 值，位于注解类 "
                                    + (annotationClassInfo == null ? "<class outside accept>"
                                    : annotationClassInfo.getName())
                                    + " 的参数 " + paramName + " 中");
                        }
                        byteArrayValue[j] = objectArrayValue[j].byteValue;
                    }
                    objectArrayValue = null;
                    break;
                default:
                    // 保持 objectArrayValue 不变
                    break;
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassName()
     */
    @Override
    public String getClassName() {
        // getClassInfo() 对此类型无效，因此 getClassName() 不需要实现
        throw new IllegalArgumentException("getClassName() 不能在此处调用");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#getClassInfo()
     */
    @Override
    public ClassInfo getClassInfo() {
        throw new IllegalArgumentException("getClassInfo() 不能在此处调用");
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (annotationEnumValue != null) {
            annotationEnumValue.setScanResult(scanResult);
        } else if (annotationClassRef != null) {
            annotationClassRef.setScanResult(scanResult);
        } else if (annotationInfo != null) {
            annotationInfo.setScanResult(scanResult);
        } else if (objectArrayValue != null) {
            for (final TypedValue anObjectArrayValue : objectArrayValue) {
                if (anObjectArrayValue != null) {
                    anObjectArrayValue.setScanResult(scanResult);
                }
            }
        }
    }

    /**
     * 获取注解参数中引用的所有类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息集合
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        if (annotationEnumValue != null) {
            annotationEnumValue.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        } else if (annotationClassRef != null) {
            final ClassInfo classInfo = annotationClassRef.getClassInfo();
            if (classInfo != null) {
                refdClassInfo.add(classInfo);
            }
        } else if (annotationInfo != null) {
            annotationInfo.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
        } else if (objectArrayValue != null) {
            for (final TypedValue item : objectArrayValue) {
                item.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        return Objects.hash(annotationEnumValue, annotationClassRef, annotationInfo, stringValue, integerValue,
                longValue, shortValue, booleanValue, characterValue, floatValue, doubleValue, byteValue,
                Arrays.hashCode(stringArrayValue), Arrays.hashCode(intArrayValue), Arrays.hashCode(longArrayValue),
                Arrays.hashCode(shortArrayValue), Arrays.hashCode(booleanArrayValue),
                Arrays.hashCode(charArrayValue), Arrays.hashCode(floatArrayValue),
                Arrays.hashCode(doubleArrayValue), Arrays.hashCode(byteArrayValue),
                Arrays.hashCode(objectArrayValue));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object other) {
        if (other == this) {
            return true;
        } else if (!(other instanceof TypedValue)) {
            return false;
        }
        final TypedValue o = (TypedValue) other;
        return Objects.equals(annotationEnumValue, o.annotationEnumValue)
                && Objects.equals(annotationClassRef, o.annotationClassRef)
                && Objects.equals(annotationInfo, o.annotationInfo) && Objects.equals(stringValue, o.stringValue)
                && Objects.equals(integerValue, o.integerValue) && Objects.equals(longValue, o.longValue)
                && Objects.equals(shortValue, o.shortValue) && Objects.equals(booleanValue, o.booleanValue)
                && Objects.equals(characterValue, o.characterValue) && Objects.equals(floatValue, o.floatValue)
                && Objects.equals(doubleValue, o.doubleValue) && Objects.equals(byteValue, o.byteValue)
                && Arrays.equals(stringArrayValue, o.stringArrayValue)
                && Arrays.equals(intArrayValue, o.intArrayValue) && Arrays.equals(longArrayValue, o.longArrayValue)
                && Arrays.equals(shortArrayValue, o.shortArrayValue)
                && Arrays.equals(floatArrayValue, o.floatArrayValue)
                && Arrays.equals(byteArrayValue, o.byteArrayValue)
                && Arrays.deepEquals(objectArrayValue, o.objectArrayValue);
    }

    // -------------------------------------------------------------------------------------------------------------

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        if (annotationEnumValue != null) {
            annotationEnumValue.toString(useSimpleNames, buf);
        } else if (annotationClassRef != null) {
            annotationClassRef.toString(useSimpleNames, buf);
        } else if (annotationInfo != null) {
            annotationInfo.toString(useSimpleNames, buf);
        } else if (stringValue != null) {
            buf.append(stringValue);
        } else if (integerValue != null) {
            buf.append(integerValue);
        } else if (longValue != null) {
            buf.append(longValue);
        } else if (shortValue != null) {
            buf.append(shortValue);
        } else if (booleanValue != null) {
            buf.append(booleanValue);
        } else if (characterValue != null) {
            buf.append(characterValue);
        } else if (floatValue != null) {
            buf.append(floatValue);
        } else if (doubleValue != null) {
            buf.append(doubleValue);
        } else if (byteValue != null) {
            buf.append(byteValue);
        } else if (stringArrayValue != null) {
            buf.append(Arrays.toString(stringArrayValue));
        } else if (intArrayValue != null) {
            buf.append(Arrays.toString(intArrayValue));
        } else if (longArrayValue != null) {
            buf.append(Arrays.toString(longArrayValue));
        } else if (shortArrayValue != null) {
            buf.append(Arrays.toString(shortArrayValue));
        } else if (booleanArrayValue != null) {
            buf.append(Arrays.toString(booleanArrayValue));
        } else if (charArrayValue != null) {
            buf.append(Arrays.toString(charArrayValue));
        } else if (floatArrayValue != null) {
            buf.append(Arrays.toString(floatArrayValue));
        } else if (doubleArrayValue != null) {
            buf.append(Arrays.toString(doubleArrayValue));
        } else if (byteArrayValue != null) {
            buf.append(Arrays.toString(byteArrayValue));
        } else if (objectArrayValue != null) {
            // TODO 这不处理嵌套数组，但此 toString() 方法仅用于调试
            buf.append(Arrays.toString(objectArrayValue));
        }
    }
}
