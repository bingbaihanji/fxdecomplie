package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations;

import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface IAnnotation {
    String getAnnotationClass();

    AnnotationVisibility getVisibility();

    Map<String, EncodedValue> getValues();

    default @Nullable EncodedValue getDefaultValue() {
        return getValues().get("value");
    }
}
