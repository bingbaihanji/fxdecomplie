package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.Collections;
import java.util.List;

/**
 * 解析后的方法 DTO。
 *
 * @param name              方法名称
 * @param descriptor        方法描述符（如 "(I)V", "()Ljava/lang/String;"）
 * @param signature          泛型签名（可为 null）
 * @param accessFlags        访问标志
 * @param exceptionTypeNames 声明的异常类型列表
 * @param annotations        方法上的注解列表
 * @param parameterAnnotations 参数注解（可为 null，按参数索引排序）
 */
public record ParsedMethod(
    String name,
    String descriptor,
    String signature,
    int accessFlags,
    List<String> exceptionTypeNames,
    List<ParsedAnnotation> annotations,
    List<List<ParsedAnnotation>> parameterAnnotations
) {
    public ParsedMethod {
        exceptionTypeNames = exceptionTypeNames == null ? List.of()
            : Collections.unmodifiableList(exceptionTypeNames);
        annotations = annotations == null ? List.of()
            : Collections.unmodifiableList(annotations);
        parameterAnnotations = parameterAnnotations == null ? List.of()
            : Collections.unmodifiableList(parameterAnnotations);
    }

    public boolean isStatic() { return (accessFlags & 0x0008) != 0; }
    public boolean isPublic() { return (accessFlags & 0x0001) != 0; }
    public boolean isPrivate() { return (accessFlags & 0x0002) != 0; }
    public boolean isProtected() { return (accessFlags & 0x0004) != 0; }
    public boolean isFinal() { return (accessFlags & 0x0010) != 0; }
    public boolean isSynchronized() { return (accessFlags & 0x0020) != 0; }
    public boolean isBridge() { return (accessFlags & 0x0040) != 0; }
    public boolean isVarArgs() { return (accessFlags & 0x0080) != 0; }
    public boolean isNative() { return (accessFlags & 0x0100) != 0; }
    public boolean isAbstract() { return (accessFlags & 0x0400) != 0; }
    public boolean isStrict() { return (accessFlags & 0x0800) != 0; }
    public boolean isSynthetic() { return (accessFlags & 0x1000) != 0; }
    public boolean isConstructor() { return "<init>".equals(name); }
    public boolean isClassInitializer() { return "<clinit>".equals(name); }
}
