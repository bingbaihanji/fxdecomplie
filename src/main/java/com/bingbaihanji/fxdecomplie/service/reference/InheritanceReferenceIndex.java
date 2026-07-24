package com.bingbaihanji.fxdecomplie.service.reference;

import com.bingbaihanji.fxdecomplie.service.classscan.ClassMetadata;
import com.bingbaihanji.fxdecomplie.service.classscan.ClassScanResult;

import java.util.*;

public final class InheritanceReferenceIndex {

    private final ClassScanResult scanResult;
    private final Map<String, String> internalNameToFullPath;
    private final Map<String, List<String>> interfaceToImplementations;
    private final Map<String, List<String>> superclassToSubclasses;
    private final Map<String, List<String>> annotationToAnnotatedClasses;

    public InheritanceReferenceIndex(ClassScanResult scanResult,
                                     Map<String, String> internalNameToFullPath) {
        this.scanResult = scanResult != null ? scanResult : new ClassScanResult(Map.of());
        this.internalNameToFullPath = internalNameToFullPath != null
                ? Map.copyOf(internalNameToFullPath) : Map.of();

        Map<String, List<String>> implMap = new LinkedHashMap<>();
        Map<String, List<String>> subMap = new LinkedHashMap<>();
        Map<String, List<String>> annMap = new LinkedHashMap<>();

        for (ClassMetadata cm : this.scanResult.getAllClasses()) {
            String name = cm.name();
            for (String itf : cm.interfaceNames()) {
                implMap.computeIfAbsent(itf, k -> new ArrayList<>()).add(name);
            }
            String superName = cm.superclassName();
            if (superName != null) {
                subMap.computeIfAbsent(superName, k -> new ArrayList<>()).add(name);
            }
            for (var ann : cm.annotations()) {
                annMap.computeIfAbsent(ann.className(), k -> new ArrayList<>()).add(name);
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

    public ClassMetadata classInfo(String internalName) {
        return scanResult.getClass(internalName).orElse(null);
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
        return scanResult.containsClass(internalName);
    }

    public ClassScanResult scanResult() {
        return scanResult;
    }
}
