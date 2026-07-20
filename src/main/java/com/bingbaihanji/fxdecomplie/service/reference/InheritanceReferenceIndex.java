package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.core.classgraph.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.classgraph.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InheritanceReferenceIndex {

    private final ScanResult scanResult;
    private final Map<String, String> internalNameToFullPath;
    private final Map<String, List<String>> interfaceToImplementations;
    private final Map<String, List<String>> superclassToSubclasses;
    private final Map<String, List<String>> annotationToAnnotatedClasses;

    public InheritanceReferenceIndex(ScanResult scanResult,
                                      Map<String, String> internalNameToFullPath) {
        this.scanResult = scanResult != null ? scanResult : new ScanResult(Map.of());
        this.internalNameToFullPath = internalNameToFullPath != null
                ? Map.copyOf(internalNameToFullPath) : Map.of();

        Map<String, List<String>> implMap = new LinkedHashMap<>();
        Map<String, List<String>> subMap = new LinkedHashMap<>();
        Map<String, List<String>> annMap = new LinkedHashMap<>();

        for (ClassInfo ci : this.scanResult.getAllClasses()) {
            String name = ci.getName();
            for (ClassInfo itf : ci.getInterfaces()) {
                implMap.computeIfAbsent(itf.getName(), k -> new ArrayList<>()).add(name);
            }
            ClassInfo sup = ci.getSuperclass();
            if (sup != null) {
                subMap.computeIfAbsent(sup.getName(), k -> new ArrayList<>()).add(name);
            }
            for (var ai : ci.getAnnotationInfo()) {
                annMap.computeIfAbsent(ai.getName(), k -> new ArrayList<>()).add(name);
            }
        }

        this.interfaceToImplementations = freeze(implMap);
        this.superclassToSubclasses = freeze(subMap);
        this.annotationToAnnotatedClasses = freeze(annMap);
    }

    private static Map<String, List<String>> freeze(Map<String, List<String>> map) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : map.entrySet()) {
            result.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    public ClassInfo classInfo(String internalName) {
        return scanResult.getClassInfo(internalName);
    }

    public List<String> implementationsOf(String interfaceName) {
        return interfaceToImplementations.getOrDefault(interfaceName, List.of());
    }

    public List<String> subclassesOf(String superclassName) {
        return superclassToSubclasses.getOrDefault(superclassName, List.of());
    }

    public List<String> annotatedBy(String annotationName) {
        return annotationToAnnotatedClasses.getOrDefault(annotationName, List.of());
    }

    public String fullPathOf(String internalName) {
        return internalNameToFullPath.get(internalName);
    }

    public boolean containsClass(String internalName) {
        return scanResult.getClassInfo(internalName) != null;
    }

    public ScanResult scanResult() {
        return scanResult;
    }
}
