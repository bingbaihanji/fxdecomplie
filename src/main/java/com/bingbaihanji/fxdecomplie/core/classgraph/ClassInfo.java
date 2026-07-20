package com.bingbaihanji.fxdecomplie.core.classgraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Predicate;

public final class ClassInfo extends ScanResultObject implements Comparable<ClassInfo>, HasName {
    private final String name;
    private final int modifiers;
    private ClassInfo superclass;
    private ClassInfoList interfaces;
    private ClassInfoList subclasses;
    private ClassInfoList implementingClasses;
    private AnnotationInfoList annotationInfo;
    private String sourceFile;
    private boolean externalClass = true;
    private boolean scannedClass;
    private String fullPath;

    public ClassInfo(String name, int modifiers) {
        this.name = Objects.requireNonNull(name, "name");
        this.modifiers = modifiers;
        this.interfaces = new ClassInfoList();
        this.subclasses = new ClassInfoList();
        this.implementingClasses = new ClassInfoList();
        this.annotationInfo = new AnnotationInfoList();
    }

    @Override
    public String getName() { return name; }

    public String getPackageName() {
        int idx = name.lastIndexOf('/');
        return idx >= 0 ? name.substring(0, idx) : "";
    }

    public int getModifiers() { return modifiers; }
    public boolean isPublic() { return java.lang.reflect.Modifier.isPublic(modifiers); }
    public boolean isAbstract() { return java.lang.reflect.Modifier.isAbstract(modifiers); }
    public boolean isInterface() { return java.lang.reflect.Modifier.isInterface(modifiers); }
    public boolean isAnnotation() { return (modifiers & 0x00002000) != 0; }
    public boolean isFinal() { return java.lang.reflect.Modifier.isFinal(modifiers); }

    public ClassInfo getSuperclass() { return superclass; }
    public void setSuperclass(ClassInfo superclass) { this.superclass = superclass; }

    public ClassInfoList getInterfaces() { return interfaces; }
    public void setInterfaces(ClassInfoList interfaces) { this.interfaces = interfaces != null ? interfaces : new ClassInfoList(); }

    public ClassInfoList getSubclasses() { return subclasses; }
    public void addSubclass(ClassInfo subclass) {
        if (subclass != null && !subclasses.contains(subclass)) {
            subclasses.add(subclass);
        }
    }

    public ClassInfoList getImplementingClasses() { return implementingClasses; }
    public void addImplementingClass(ClassInfo impl) {
        if (impl != null && !implementingClasses.contains(impl)) {
            implementingClasses.add(impl);
        }
    }

    public AnnotationInfoList getAnnotationInfo() { return annotationInfo; }
    public void setAnnotationInfo(AnnotationInfoList annotationInfo) { this.annotationInfo = annotationInfo != null ? annotationInfo : new AnnotationInfoList(); }

    public ClassInfoList getAnnotations() {
        ClassInfoList list = new ClassInfoList();
        if (scanResult != null) {
            for (AnnotationInfo ai : annotationInfo) {
                ClassInfo ci = scanResult.getClassInfo(ai.getName());
                if (ci != null) {
                    list.add(ci);
                }
            }
        }
        return list;
    }

    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }

    public boolean isExternalClass() { return externalClass; }
    public void setExternalClass(boolean externalClass) { this.externalClass = externalClass; }

    public boolean isScannedClass() { return scannedClass; }
    public void setScannedClass(boolean scannedClass) { this.scannedClass = scannedClass; }

    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }

    @Override
    public int compareTo(ClassInfo o) { return name.compareTo(o.name); }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof ClassInfo that && name.equals(that.name);
    }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public String toString() { return name; }
}
