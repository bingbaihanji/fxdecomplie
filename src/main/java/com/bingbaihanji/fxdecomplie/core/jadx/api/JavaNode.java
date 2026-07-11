package com.bingbaihanji.fxdecomplie.core.jadx.api;

import java.util.List;

import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeAnnotation;
import com.bingbaihanji.fxdecomplie.core.jadx.api.metadata.ICodeNodeRef;

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
