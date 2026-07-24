package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.List;

public record ClassMetadata(
    String name,
    int modifiers,
    String superclassName,
    List<String> interfaceNames,
    List<FieldMetadata> fields,
    List<MethodMetadata> methods,
    List<AnnotationMetadata> annotations,
    String fullPath,
    boolean isExternalClass
) {
    public boolean isInterface() { return (modifiers & 0x0200) != 0; }
    public boolean isAnnotation() { return (modifiers & 0x2000) != 0; }
    public boolean isEnum() { return (modifiers & 0x4000) != 0; }
    public boolean isRecord() { return (modifiers & 0x00000010) != 0; }
}
