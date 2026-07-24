package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

public record MethodMetadata(
    String name,
    String descriptor,
    String signature,
    int modifiers,
    List<String> parameterTypes,
    String returnType,
    List<AnnotationMetadata> annotations
) {
    public boolean isStatic() { return (modifiers & 0x0008) != 0; }
    public boolean isPublic() { return (modifiers & 0x0001) != 0; }
    public boolean isPrivate() { return (modifiers & 0x0002) != 0; }
    public boolean isProtected() { return (modifiers & 0x0004) != 0; }
}
