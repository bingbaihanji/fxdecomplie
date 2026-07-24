package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

public record FieldMetadata(
    String name,
    String typeDescriptor,
    String typeSignature,
    int modifiers,
    Object constantValue,
    List<AnnotationMetadata> annotations
) {
    public boolean isStatic() { return (modifiers & 0x0008) != 0; }
    public boolean isFinal() { return (modifiers & 0x0010) != 0; }
    public boolean isPublic() { return (modifiers & 0x0001) != 0; }
}
