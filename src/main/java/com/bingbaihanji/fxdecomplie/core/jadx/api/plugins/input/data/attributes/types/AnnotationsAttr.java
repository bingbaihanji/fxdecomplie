package com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.types;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.AnnotationVisibility;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.annotations.IAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.JadxAttrType;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AnnotationsAttr extends PinnedAttribute {

    private final Map<String, IAnnotation> map;

    public AnnotationsAttr(Map<String, IAnnotation> map) {
        this.map = map;
    }

    public static @Nullable AnnotationsAttr pack(List<IAnnotation> annotationList) {
        if (annotationList.isEmpty()) {
            return null;
        }
        Map<String, IAnnotation> annMap = new HashMap<>(annotationList.size());
        for (IAnnotation ann : annotationList) {
            if (ann.getVisibility() != AnnotationVisibility.SYSTEM) {
                annMap.put(ann.getAnnotationClass(), ann);
            }
        }
        if (annMap.isEmpty()) {
            return null;
        }
        return new AnnotationsAttr(annMap);
    }

    public @Nullable IAnnotation get(String className) {
        return map.get(className);
    }

    public Collection<IAnnotation> getAll() {
        return map.values();
    }

    public List<IAnnotation> getList() {
        return map.isEmpty() ? Collections.emptyList() : new ArrayList<>(map.values());
    }

    public int size() {
        return map.size();
    }

    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public JadxAttrType<AnnotationsAttr> getAttrType() {
        return JadxAttrType.ANNOTATION_LIST;
    }

    @Override
    public String toString() {
        return map.toString();
    }
}
