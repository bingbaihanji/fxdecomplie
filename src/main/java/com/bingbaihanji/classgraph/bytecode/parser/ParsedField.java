package com.bingbaihanji.classgraph.bytecode.parser;

import java.util.Collections;
import java.util.List;

/**
 * 解析后的字段 DTO。
 *
 * @param name           字段名称
 * @param descriptor     类型描述符（如 "I", "Ljava/lang/String;"）
 * @param signature      泛型签名（可为 null）
 * @param accessFlags    访问标志
 * @param constantValue  常量初始值（仅 static final 字段，可为 null）
 * @param annotations    字段上的注解列表
 */
public record ParsedField(
    String name,
    String descriptor,
    String signature,
    int accessFlags,
    Object constantValue,
    List<ParsedAnnotation> annotations
) {
    public ParsedField {
        annotations = annotations == null ? List.of()
            : Collections.unmodifiableList(annotations);
    }

    public boolean isStatic() { return (accessFlags & 0x0008) != 0; }
    public boolean isFinal() { return (accessFlags & 0x0010) != 0; }
    public boolean isPublic() { return (accessFlags & 0x0001) != 0; }
    public boolean isPrivate() { return (accessFlags & 0x0002) != 0; }
    public boolean isProtected() { return (accessFlags & 0x0004) != 0; }
    public boolean isTransient() { return (accessFlags & 0x0080) != 0; }
    public boolean isVolatile() { return (accessFlags & 0x0040) != 0; }
    public boolean isSynthetic() { return (accessFlags & 0x1000) != 0; }
}
