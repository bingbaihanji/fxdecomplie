package com.bingbaihanji.fxdecomplie.core.jadx.api.usage;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.ClassNode;

public interface IUsageInfoData {

    void apply();

    void applyForClass(ClassNode cls);

    void visitUsageData(IUsageInfoVisitor visitor);
}
