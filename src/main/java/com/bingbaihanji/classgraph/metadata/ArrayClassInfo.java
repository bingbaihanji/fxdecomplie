 
package com.bingbaihanji.classgraph.metadata;

import com.bingbaihanji.classgraph.scan.ScanResult;
import com.bingbaihanji.classgraph.type.ArrayType;
import com.bingbaihanji.classgraph.type.BaseType;
import com.bingbaihanji.classgraph.type.TypeSignature;
import com.bingbaihanji.classgraph.util.LogNode;

import java.util.Map;
import java.util.Set;

/**
 * 保存数组类的元数据此类扩展了 {@link ClassInfo}，添加了与数组类相关的额外方法，特别是
 * {@link #getArrayType()}、{@link #getTypeSignatureStr()}、
 * {@link #getElementTypeSignature()}、{@link #getElementClassInfo()}、{@link #loadElementClass()} 和
 * {@link #getNumDimensions()}
 *
 * <p>
 * {@link ArrayClassInfo} 对象不会有任何方法、字段或注解
 * 对于 {@link ClassInfo} 的这个子类，{@link ClassInfo#isArrayClass()} 将返回 true
 */
public class ArrayClassInfo extends ClassInfo {
    /** 数组类型签名 */
    private ArrayType ArrayType;

    /** 元素类信息 */
    private ClassInfo elementClassInfo;

    /** 用于反序列化的默认构造函数 */
    public ArrayClassInfo() {
        super();
    }

    /**
     * 构造函数
     *
     * @param ArrayType
     *            数组类型签名
     */
    public ArrayClassInfo(final ArrayType ArrayType) {
        super(ArrayType.getClassName(), /* modifiers = */ 0, /* resource = */ null);
        this.ArrayType = ArrayType;
        // 预加载元素类型的字段
        getElementClassInfo();
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ClassInfo#setScanResult(com.bingbaihanji.classgraph.core.ScanResult)
     */
    @Override
    public void setScanResult(final ScanResult scanResult) {
        super.setScanResult(scanResult);
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取数组类的原始类型签名字符串，例如 "[[I" 表示 "int[][]"
     *
     * @return 数组类的原始类型签名字符串
     */
    @Override
    public String getTypeSignatureStr() {
        return ArrayType.getTypeSignatureStr();
    }

    /**
     * 返回 null，因为数组类没有 ClassType请改用 {@link #getArrayType()}
     *
     * @return null(始终)
     */
    @Override
    public com.bingbaihanji.classgraph.type.ClassType getTypeSignature() {
        return null;
    }

    /**
     * 获取类的类型签名
     *
     * @return 类的类型签名(如果可用)，否则返回 null
     */
    public ArrayType getArrayType() {
        return ArrayType;
    }

    /**
     * 获取数组元素的类型签名
     *
     * @return 数组元素的类型签名
     */
    public TypeSignature getElementTypeSignature() {
        return ArrayType.getElementTypeSignature();
    }

    /**
     * 获取数组的维度数
     *
     * @return 数组的维度数
     */
    public int getNumDimensions() {
        return ArrayType.getNumDimensions();
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取数组元素类型的 {@link ClassInfo} 实例
     *
     * @return 数组元素类型的 {@link ClassInfo} 实例如果在扫描期间未找到元素类型，则返回 null
     *         特别是，对于具有基本类型元素的数组将返回 null
     */
    public ClassInfo getElementClassInfo() {
        if (elementClassInfo == null) {
            final TypeSignature elementTypeSignature = ArrayType.getElementTypeSignature();
            if (!(elementTypeSignature instanceof BaseType)) {
                elementClassInfo = ArrayType.getElementTypeSignature().getClassInfo();
                if (elementClassInfo != null) {
                    // 从数组元素 ClassInfo 复制相关字段
                    this.Classpath = elementClassInfo.Classpath;
                    this.classfileResource = elementClassInfo.classfileResource;
                    this.classLoader = elementClassInfo.classLoader;
                    this.isScannedClass = elementClassInfo.isScannedClass;
                    this.isExternalClass = elementClassInfo.isExternalClass;
                    this.moduleInfo = elementClassInfo.moduleInfo;
                    this.packageInfo = elementClassInfo.packageInfo;
                }
            }
        }
        return elementClassInfo;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取数组元素类型的 {@code Class<?>} 引用如果元素类尚未加载，会导致 ClassLoader 加载它
     *
     * @param ignoreExceptions
     *            是否忽略异常
     * @return 数组元素类型的 {@code Class<?>} 引用对于基本类型元素的数组同样有效
     */
    public Class<?> loadElementClass(final boolean ignoreExceptions) {
        return ArrayType.loadElementClass(ignoreExceptions);
    }

    /**
     * 获取数组元素类型的 {@code Class<?>} 引用如果元素类尚未加载，会导致 ClassLoader 加载它
     *
     * @return 数组元素类型的 {@code Class<?>} 引用对于基本类型元素的数组同样有效
     */
    public Class<?> loadElementClass() {
        return ArrayType.loadElementClass();
    }

    /**
     * 获取此 {@link ArrayClassInfo} 对象所命名的数组类的 {@code Class<?>} 引用如果元素类尚未加载，
     * 会导致 ClassLoader 加载它
     *
     * @param ignoreExceptions
     *            是否忽略异常
     * @return 类引用，如果 ignoreExceptions 为 true 且在加载类时发生了异常或错误，则返回 null
     * @throws IllegalArgumentException
     *             如果 ignoreExceptions 为 false 且加载类时出现问题
     */
    @Override
    public Class<?> loadClass(final boolean ignoreExceptions) {
        if (classRef == null) {
            classRef = ArrayType.loadClass(ignoreExceptions);
        }
        return classRef;
    }

    /**
     * 获取此 {@link ArrayClassInfo} 对象所命名的数组类的 {@code Class<?>} 引用如果元素类尚未加载，
     * 会导致 ClassLoader 加载它
     *
     * @return 类引用
     * @throws IllegalArgumentException
     *             如果加载类时出现问题
     */
    @Override
    public Class<?> loadClass() {
        if (classRef == null) {
            classRef = ArrayType.loadClass();
        }
        return classRef;
    }

    // -------------------------------------------------------------------------------------------------------------

    /**
     * 获取类型描述符或类型签名中引用的任何类的 {@link ClassInfo} 对象
     *
     * @param classNameToClassInfo
     *            从类名到 {@link ClassInfo} 的映射
     * @param refdClassInfo
     *            被引用的类信息
     */
    @Override
    protected void findReferencedClassInfo(final Map<String, ClassInfo> classNameToClassInfo,
                                           final Set<ClassInfo> refdClassInfo, final LogNode log) {
        super.findReferencedClassInfo(classNameToClassInfo, refdClassInfo, log);
    }

    // -------------------------------------------------------------------------------------------------------------

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ClassInfo#equals(java.lang.Object)
     */
    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj);
    }

    /* (non-Javadoc)
     * @see com.bingbaihanji.classgraph.core.ClassInfo#hashCode()
     */
    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
