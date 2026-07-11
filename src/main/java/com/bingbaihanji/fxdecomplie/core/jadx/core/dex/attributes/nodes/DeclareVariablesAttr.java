package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.CodeVar;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * List of variables to be declared at region start.
 */
public class DeclareVariablesAttr implements IJadxAttribute {

    private final List<CodeVar> vars = new ArrayList<>();

    public Iterable<CodeVar> getVars() {
        return vars;
    }

    public void addVar(CodeVar arg) {
        vars.add(arg);
    }

    @Override
    public AType<DeclareVariablesAttr> getAttrType() {
        return AType.DECLARE_VARIABLES;
    }

    @Override
    public String toString() {
        return "DECL_VAR: " + Utils.listToString(vars);
    }
}
