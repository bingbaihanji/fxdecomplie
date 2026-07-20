package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.io.Closeable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ScanResult implements Closeable {
    private final Map<String, ClassInfo> classNameToClassInfo;

    public ScanResult(Map<String, ClassInfo> classNameToClassInfo) {
        Map<String, ClassInfo> map = new LinkedHashMap<>();
        for (Map.Entry<String, ClassInfo> entry : classNameToClassInfo.entrySet()) {
            ClassInfo ci = entry.getValue();
            map.put(entry.getKey(), ci);
            ci.setScanResult(this);
        }
        this.classNameToClassInfo = Collections.unmodifiableMap(map);
    }

    public ClassInfoList getAllClasses() {
        return new ClassInfoList(classNameToClassInfo.values());
    }

    public ClassInfo getClassInfo(String name) {
        return classNameToClassInfo.get(name);
    }

    public ClassInfoList getClassesWithAnnotation(String annotationName) {
        ClassInfoList result = new ClassInfoList();
        for (ClassInfo ci : classNameToClassInfo.values()) {
            for (AnnotationInfo ai : ci.getAnnotationInfo()) {
                if (ai.getName().equals(annotationName)) {
                    result.add(ci);
                    break;
                }
            }
        }
        return result;
    }

    @Override
    public void close() {
        // no-op for in-memory scan result
    }
}
