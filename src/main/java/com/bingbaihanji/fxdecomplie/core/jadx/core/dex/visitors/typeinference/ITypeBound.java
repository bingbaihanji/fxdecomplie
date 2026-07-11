package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;
import org.jetbrains.annotations.Nullable;

/**
 * Information to restrict types by applying constraints (or boundaries)
 */
public interface ITypeBound {

    BoundEnum getBound();

    ArgType getType();

    @Nullable
    RegisterArg getArg();
}
