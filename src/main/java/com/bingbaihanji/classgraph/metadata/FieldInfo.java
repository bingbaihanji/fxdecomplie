 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.bytecode.ClassParser.TypeAnnotationDecorator;
import com.bingbaihanji.classgraph.metadata.ClassHierarchy.RelType;
import com.bingbaihanji.classgraph.scan.ClassGraph;
import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.ParseException;
import com.bingbaihanji.classgraph.type.TypeSignature;
import com.bingbaihanji.classgraph.type.TypeUtils;
import com.bingbaihanji.classgraph.type.TypeUtils.ModifierType;
import com.bingbaihanji.classgraph.util.LogNode;

import java.lang.annotation.Repeatable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 保存扫描过程中遇到的类的字段元数据所有值均直接从类的 ClassParser 中获取
 */
public class FieldInfo extends ClassMemberInfo implements Comparable<FieldInfo> {
    /** 已解析的类型签名 */
    private transient TypeSignature typeSignature;

    /** 已解析的类型描述符 */
    private transient TypeSignature typeDescriptor;

    /** 字段的常量初始值(如果有的话) */
    // 这是 transient 的，因为常量初始值是 final 的，因此该值不需要序列化
    private TypedValue constantInitializerValue;

    /** 此字段的 {@link TypeSignature} 实例的类型注解装饰器 */
    private transient List<TypeAnnotationDecorator> typeAnnotationDecorators;

    // -------------------------------------------------------------------------------------------------------------

    /** 用于反序列化的默认构造函数 */
    FieldInfo() {
        super();
    }

    /**
     * 构造函数
     *
     * @param definingClassName
     *            定义该字段的类名
     * @param fieldName
     *            字段名称
     * @param modifiers
     *            字段修饰符
     * @param typeDescriptorStr
     *            字段类型描述符
     * @param typeSignatureStr
     *            字段类型签名
     * @param constantInitializerValue
     *            字段初始化时的静态常量值(如果有的话)
     * @param annotationInfo
     *            字段上任何注解的 {@link AnnotationInfo}
     */
    public FieldInfo(final String definingClassName, final String fieldName, final int modifiers,
                     final String typeDescriptorStr, final String typeSignatureStr, final Object constantInitializerValue,
                     final AnnotationInfoList annotationInfo, final List<TypeAnnotationDecorator> typeAnnotationDecorators) {
        super(definingClassName, fieldName, modifiers, typeDescriptorStr, typeSignatureStr, annotationInfo);
        if (fieldName == null) {
            throw new IllegalArgumentException("fieldName must not be null");
        }
        this.constantInitializerValue = constantInitializerValue == null ? null
                : new TypedValue(constantInitializerValue);
        this.typeAnnotationDecorators = typeAnnotationDecorators;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 已弃用 -- 请改用 {@link #getModifiersStr()}
     *
     * @deprecated 请改用 {@link #getModifiersStr()}
     * @return 字段修饰符字符串
     */
    @Deprecated
    public String getModifierStr() {
        return getModifiersStr();
    }

    /**
     * 获取字段修饰符字符串，例如 "public static final"如需获取修饰符位值，请调用 getModifiers()
     *
     * @return 字段修饰符字符串
     */
    @Override
    public String getModifiersStr() {
        final StringBuilder buf = new StringBuilder();
        TypeUtils.modifiersToString(modifiers, ModifierType.FIELD, /* ignored */ false, buf);
        return buf.toString();
    }

    /**
     * 返回此字段是否为 transient 字段
     *
     * @return 如果字段是 transient 的，则返回 true
     */
    public boolean isTransient() {
        return Modifier.isTransient(modifiers);
    }

    /**
     * 返回此字段是否为枚举常量
     *
     * @return 如果字段是枚举常量，则返回 true
     */
    public boolean isEnum() {
        return (modifiers & 0x4000) != 0;
    }

    /**
     * 返回字段的已解析类型描述符，该描述符不包含类型参数如果需要泛型类型参数，请改用 {@link #getTypeSignature()}
     *
     * @return 字段的已解析类型描述符字符串
     */
    @Override
    public TypeSignature getTypeDescriptor() {
        synchronized (this) {
            if (typeDescriptorStr == null) {
                return null;
            }
            if (typeDescriptor == null) {
                try {
                    typeDescriptor = TypeSignature.parse(typeDescriptorStr, declaringClassName);
                    typeDescriptor.setScanResult(scanResult);
                    decorateType(typeDescriptor);
                } catch (final ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            return typeDescriptor;
        }
    }

    /**
     * 对给定的已解析字段类型运行类型注解装饰器任何无法匹配到该类型的单独类型注解
     * (例如不可解析的嵌套类型，或编译器错误)会被跳过，而非导致整个字段类型
     * 解析失败(尽力而为)(#897)
     *
     * @param fieldType
     *            要装饰的已解析字段类型签名或描述符
     */
    private void decorateType(final TypeSignature fieldType) {
        if (typeAnnotationDecorators != null) {
            for (final TypeAnnotationDecorator decorator : typeAnnotationDecorators) {
                try {
                    decorator.decorate(fieldType);
                } catch (final IllegalArgumentException e) {
                    // 跳过无法匹配到字段类型的类型注解，而非导致整个字段类型
                    // 解析失败(尽力而为)(#897)
                }
            }
        }
    }

    /**
     * 返回字段的已解析类型签名，可能包含类型参数如果返回 null，
     * 表示此字段没有可用的类型签名信息，请改用 {@link #getTypeDescriptor()}
     *
     * @return 字段的已解析类型签名，如果不可用则返回 null
     * @throws IllegalArgumentException
     *             如果字段类型签名无法解析(这应该只在 ClassParser 损坏或编译器错误导致向 ClassParser
     *             写入了无效的类型签名时抛出)
     */
    @Override
    public TypeSignature getTypeSignature() {
        synchronized (this) {
            if (typeSignatureStr == null) {
                return null;
            }
            if (typeSignature == null) {
                try {
                    typeSignature = TypeSignature.parse(typeSignatureStr, declaringClassName);
                    typeSignature.setScanResult(scanResult);
                    decorateType(typeSignature);
                } catch (final ParseException e) {
                    throw new IllegalArgumentException(
                            "Invalid type signature for field " + getClassName() + "." + getName()
                                    + (getClassInfo() != null
                                    ? " in classpath element " + getClassInfo().getClasspathURI()
                                    : "")
                                    + " : " + typeSignatureStr,
                            e);
                }
            }
            return typeSignature;
        }
    }

    /**
     * 返回字段的类型签名，可能包含类型参数如果类型签名为 null，表示此字段没有可用的类型签名信息，
     * 则返回类型描述符
     *
     * @return 字段的已解析类型签名，如果不可用则返回字段的已解析类型描述符
     */
    @Override
    public TypeSignature getTypeSignatureOrTypeDescriptor() {
        TypeSignature typeSig = null;
        try {
            typeSig = getTypeSignature();
            if (typeSig != null) {
                return typeSig;
            }
        } catch (final Exception e) {
            // 忽略
        }
        return getTypeDescriptor();
    }

    /**
     * 返回字段的常量初始值需要先调用 {@link ClassGraph#withConstantFieldValues()}
     * 仅对于具有常量初始化器的字段才返回非 null 值，通常这仅限于基本类型字段或 String 常量
     * 另请注意，常量值字段是否在字段定义本身中作为常量赋值，还是在静态或非静态类初始化块或构造函数中
     * 手动赋值，这取决于编译器 -- 因此在提取常量初始值方面效果可能因人而异
     *
     * @return 如果此字段具有常量初始值，则返回初始值，否则返回 null
     */
    public Object getConstantInitializerValue() {
        if (!scanResult.ScanConfig.enableStaticFinalFieldConstantInitializerValues) {
            throw new IllegalArgumentException(
                    "Please call ClassGraph#enableStaticFinalFieldConstantInitializerValues() " + "before #scan()");
        }
        return constantInitializerValue == null ? null : constantInitializerValue.get();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 加载此字段关联的类，并获取此字段的 {@link Field} 引用
     *
     * @return 此字段的 {@link Field} 引用
     * @throws IllegalArgumentException
     *             如果类无法加载或字段不存在
     */
    public Field loadClassAndGetField() throws IllegalArgumentException {
        try {
            return loadClass().getField(getName());
        } catch (final NoSuchFieldException e1) {
            try {
                return loadClass().getDeclaredField(getName());
            } catch (final NoSuchFieldException e2) {
                throw new IllegalArgumentException("No such field: " + getClassName() + "." + getName());
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 处理 {@link Repeatable} 注解
     *
     * @param allRepeatableAnnotationNames
     *            所有可重复注解的名称集合
     */
    void handleRepeatableAnnotations(final Set<String> allRepeatableAnnotationNames) {
        if (annotationInfo != null) {
            annotationInfo.handleRepeatableAnnotations(allRepeatableAnnotationNames, getClassInfo(),
                    RelType.FIELD_ANNOTATIONS, RelType.CLASSES_WITH_FIELD_ANNOTATION,
                    RelType.CLASSES_WITH_NONPRIVATE_FIELD_ANNOTATION);
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.metadata.MetadataNode#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
        if (this.typeSignature != null) {
            this.typeSignature.setScanResult(scanResult);
        }
        if (this.typeDescriptor != null) {
            this.typeDescriptor.setScanResult(scanResult);
        }
        if (this.annotationInfo != null) {
            for (final AnnotationInfo ai : this.annotationInfo) {
                ai.setScanResult(scanResult);
            }
        }
    }

    /**
     * 获取类型描述符或类型签名中引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            引用的类信息集合
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        try {
            final TypeSignature fieldSig = getTypeSignature();
            if (fieldSig != null) {
                fieldSig.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("Illegal type signature for field " + getClassName() + "." + getName() + ": "
                        + getTypeSignatureStr());
            }
        }
        try {
            final TypeSignature fieldDesc = getTypeDescriptor();
            if (fieldDesc != null) {
                fieldDesc.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        } catch (final IllegalArgumentException e) {
            if (log != null) {
                log.log("Illegal type descriptor for field " + getClassName() + "." + getName() + ": "
                        + getTypeDescriptorStr());
            }
        }
        if (annotationInfo != null) {
            for (final AnnotationInfo ai : annotationInfo) {
                ai.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
            }
        }
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 使用类名和字段名进行 equals() 比较
     *
     * @param obj
     *            要比较的对象
     * @return 如果相等则返回 true
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof FieldInfo)) {
            return false;
        }
        final FieldInfo other = (FieldInfo) obj;
        return declaringClassName.equals(other.declaringClassName) && name.equals(other.name);
    }

    /**
     * 使用类名和字段名的哈希码
     *
     * @return 哈希码
     */
    @Override
    public int hashCode() {
        return name.hashCode() + declaringClassName.hashCode() * 11;
    }

    /**
     * 按类名然后按字段名排序
     *
     * @param other
     *            要比较的另一个 FieldInfo 对象
     * @return 比较结果
     */
    @Override
    public int compareTo(final FieldInfo other) {
        final int diff = declaringClassName.compareTo(other.declaringClassName);
        if (diff != 0) {
            return diff;
        }
        return name.compareTo(other.name);
    }

    // -------------------------------------------------------------------------------------------------------------

    void toString(final boolean includeModifiers, final boolean useSimpleNames, final StringBuilder buf) {
        if (annotationInfo != null) {
            for (final AnnotationInfo annotation : annotationInfo) {
                // 如果此字段是 record 参数，前一个位置可能有括号
                if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' '
                        && buf.charAt(buf.length() - 1) != '(') {
                    buf.append(' ');
                }
                annotation.toString(useSimpleNames, buf);
            }
        }

        if (modifiers != 0 && includeModifiers) {
            if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' ' && buf.charAt(buf.length() - 1) != '(') {
                buf.append(' ');
            }
            TypeUtils.modifiersToString(modifiers, ModifierType.FIELD, /* ignored */ false, buf);
        }

        if (buf.length() > 0 && buf.charAt(buf.length() - 1) != ' ' && buf.charAt(buf.length() - 1) != '(') {
            buf.append(' ');
        }
        final TypeSignature typeSig = getTypeSignatureOrTypeDescriptor();
        typeSig.toStringInternal(useSimpleNames, /* annotationsToExclude = */ annotationInfo, buf);

        buf.append(' ');
        buf.append(name);

        if (constantInitializerValue != null) {
            final Object val = constantInitializerValue.get();
            buf.append(" = ");
            if (val instanceof String) {
                buf.append('"').append(((String) val).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            } else if (val instanceof Character) {
                buf.append('\'').append(((Character) val).toString().replace("\\", "\\\\").replaceAll("'", "\\'"))
                        .append('\'');
            } else {
                buf.append(val == null ? "null" : val.toString());
            }
        }
    }

    @Override
    protected void toString(final boolean useSimpleNames, final StringBuilder buf) {
        toString(true, useSimpleNames, buf);
    }
}
