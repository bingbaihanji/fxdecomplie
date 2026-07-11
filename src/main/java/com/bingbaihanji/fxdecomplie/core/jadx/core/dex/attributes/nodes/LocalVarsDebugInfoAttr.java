package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.ILocalVar;
import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

import java.util.List;

public class LocalVarsDebugInfoAttr implements IJadxAttribute {
    private final List<ILocalVar> localVars;

    public LocalVarsDebugInfoAttr(List<ILocalVar> localVars) {
        this.localVars = localVars;
    }

    public List<ILocalVar> getLocalVars() {
        return localVars;
    }

    @Override
    public AType<LocalVarsDebugInfoAttr> getAttrType() {
        return AType.LOCAL_VARS_DEBUG_INFO;
    }

    @Override
    public String toString() {
        return "Debug Info:\n  " + Utils.listToString(localVars, "\n  ");
    }
}
