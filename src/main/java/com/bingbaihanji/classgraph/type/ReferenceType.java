package com.bingbaihanji.classgraph.type;

/**
 * 引用类型的类型签名子类包括 {@link TypeRef}
 * ({@link ClassRef} 或 {@link TypeVar})，以及 {@link ArrayType}
 */
public abstract class ReferenceType extends TypeSignature {
    /** 构造函数 */
    protected ReferenceType() {
        super();
    }

    /**
     * 解析一个引用类型签名
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的引用类型签名
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceType parseReferenceType(final TypeParser TypeParser, final String definingClassName)
            throws ParseException {
        final ClassRef classRef = ClassRef.parse(TypeParser, definingClassName);
        if (classRef != null) {
            return classRef;
        }
        final TypeVar typeVar = (TypeVar) TypeVar.parse(TypeParser, definingClassName);
        if (typeVar != null) {
            return typeVar;
        }
        final ArrayType arrayType = (ArrayType) ArrayType.parse(TypeParser, definingClassName);
        if (arrayType != null) {
            return arrayType;
        }
        return null;
    }

    /**
     * 解析一个类边界
     *
     * @param TypeParser
     *            解析器
     * @param definingClassName
     *            包含该类型描述符的类
     * @return 解析后的类边界
     * @throws ParseException
     *             如果类型签名无法解析
     */
    static ReferenceType parseClassBound(final TypeParser TypeParser, final String definingClassName)
            throws ParseException {
        TypeParser.expect(':');
        // 如果 ':' 之后没有签名，则可能返回 null(类边界签名可以为空)
        return parseReferenceType(TypeParser, definingClassName);
    }
}