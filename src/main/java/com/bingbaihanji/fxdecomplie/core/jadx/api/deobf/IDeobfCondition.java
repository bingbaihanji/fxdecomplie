package com.bingbaihanji.fxdecomplie.core.jadx.api.deobf;

import com.bingbaihanji.fxdecomplie.core.jadx.api.deobf.impl.CombineDeobfConditions;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.*;

/**
 * Utility interface to simplify merging several rename conditions to build {@link IRenameCondition}
 * instance with {@link CombineDeobfConditions#combine(IDeobfCondition...)}.
 */
public interface IDeobfCondition {

    void init(RootNode root);

    Action check(PackageNode pkg);

    Action check(ClassNode cls);

    Action check(FieldNode fld);

    Action check(MethodNode mth);

    enum Action {
        NO_ACTION,
        FORCE_RENAME,
        FORBID_RENAME,
    }
}
