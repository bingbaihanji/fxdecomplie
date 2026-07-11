package com.bingbaihanji.fxdecomplie.core.jadx.api;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;

import java.util.List;

public interface JavaNode {

    ICodeNodeRef getCodeNodeRef();

    String getName();

    String getFullName();

    JavaClass getDeclaringClass();

    JavaClass getTopParentClass();

    int getDefPos();

    List<JavaNode> getUseIn();

    void removeAlias();

    boolean isOwnCodeAnnotation(ICodeAnnotation ann);
}
