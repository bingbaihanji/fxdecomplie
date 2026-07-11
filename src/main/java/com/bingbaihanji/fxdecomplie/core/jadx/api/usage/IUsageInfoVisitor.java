package com.bingbaihanji.fxdecomplie.core.jadx.api.usage;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.MethodInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.FieldNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;

import java.util.List;

public interface IUsageInfoVisitor {

    void visitClassDeps(ClassNode cls, List<ClassNode> deps);

    void visitClassUsage(ClassNode cls, List<ClassNode> usage);

    void visitClassUseInMethods(ClassNode cls, List<MethodNode> methods);

    void visitFieldsUsage(FieldNode fld, List<MethodNode> methods);

    void visitMethodsUsage(MethodNode mth, List<MethodNode> methods);

    void visitMethodsUses(MethodNode mth, List<MethodNode> methods);

    void visitUnresolvedMethodsUsage(MethodNode mth, List<MethodInfo> methods);

    void visitIsSelfCall(MethodNode mth, boolean isSelfCall);

    void visitComplete();
}
