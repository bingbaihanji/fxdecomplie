package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.Predicate;

public final class ClassInfoList extends ArrayList<ClassInfo> {
    public ClassInfoList() {}

    public ClassInfoList(Collection<? extends ClassInfo> c) {
        super(c);
    }

    public ClassInfoList filter(Predicate<ClassInfo> predicate) {
        ClassInfoList result = new ClassInfoList();
        for (ClassInfo info : this) {
            if (predicate.test(info)) {
                result.add(info);
            }
        }
        return result;
    }

    public ClassInfoList getInterfaces() {
        return filter(ClassInfo::isInterface);
    }

    public ClassInfoList getClasses() {
        return filter(ci -> !ci.isInterface() && !ci.isAnnotation());
    }
}
