package com.bingbaihanji.fxdecomplie.core.jadx.core.deobf.conditions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.IDeobfCondition;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;

public abstract class AbstractDeobfCondition implements IDeobfCondition {

    @Override
    public void init(RootNode root) {
    }

    @Override
    public Action check(PackageNode pkg) {
        return Action.NO_ACTION;
    }

    @Override
    public Action check(ClassNode cls) {
        return Action.NO_ACTION;
    }

    @Override
    public Action check(FieldNode fld) {
        return Action.NO_ACTION;
    }

    @Override
    public Action check(MethodNode mth) {
        return Action.NO_ACTION;
    }
}
