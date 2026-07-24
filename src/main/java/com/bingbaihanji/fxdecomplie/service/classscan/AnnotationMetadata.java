package com.bingbaihanji.fxdecomplie.service.classscan;

import java.util.Map;

public record AnnotationMetadata(
    String className,
    Map<String, Object> parameterValues
) {}
