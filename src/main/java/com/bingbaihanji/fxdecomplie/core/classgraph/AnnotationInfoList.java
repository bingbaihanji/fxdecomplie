package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public final class AnnotationInfoList extends ArrayList<AnnotationInfo> {
    public AnnotationInfoList() {}

    public AnnotationInfoList(Collection<? extends AnnotationInfo> c) {
        super(c);
    }

    public AnnotationInfoList filter(Predicate<AnnotationInfo> predicate) {
        AnnotationInfoList result = new AnnotationInfoList();
        for (AnnotationInfo info : this) {
            if (predicate.test(info)) {
                result.add(info);
            }
        }
        return result;
    }
}
