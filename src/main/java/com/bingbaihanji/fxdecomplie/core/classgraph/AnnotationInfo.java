package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;

public final class AnnotationInfo implements Comparable<AnnotationInfo>, HasName {
    private final String name;

    public AnnotationInfo(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int compareTo(AnnotationInfo o) {
        return name.compareTo(o.name);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AnnotationInfo that && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }
}
