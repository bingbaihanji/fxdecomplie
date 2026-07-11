package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.typeinference;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.ArgType;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashSet;
import java.util.Set;

public class TypeInfo {
    private final Set<ITypeBound> bounds = new LinkedHashSet<>();
    private ArgType type = ArgType.UNKNOWN;

    @NotNull
    public ArgType getType() {
        return type;
    }

    public void setType(ArgType type) {
        this.type = type;
    }

    public Set<ITypeBound> getBounds() {
        return bounds;
    }

    @Override
    public String toString() {
        return "TypeInfo{type=" + type + ", bounds=" + bounds + '}';
    }
}
