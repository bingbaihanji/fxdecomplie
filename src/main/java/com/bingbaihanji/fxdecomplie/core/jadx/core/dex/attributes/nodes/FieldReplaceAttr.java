package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.PinnedAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.info.ClassInfo;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.InsnArg;

public class FieldReplaceAttr extends PinnedAttribute {

    private final ReplaceWith replaceType;
    private final Object replaceObj;

    public FieldReplaceAttr(ClassInfo cls) {
        this.replaceType = ReplaceWith.CLASS_INSTANCE;
        this.replaceObj = cls;
    }

    public FieldReplaceAttr(InsnArg reg) {
        this.replaceType = ReplaceWith.VAR;
        this.replaceObj = reg;
    }

    public ReplaceWith getReplaceType() {
        return replaceType;
    }

    public ClassInfo getClsRef() {
        return (ClassInfo) replaceObj;
    }

    public InsnArg getVarRef() {
        return (InsnArg) replaceObj;
    }

    @Override
    public AType<FieldReplaceAttr> getAttrType() {
        return AType.FIELD_REPLACE;
    }

    @Override
    public String toString() {
        return "REPLACE: " + replaceType + ' ' + replaceObj;
    }

    public enum ReplaceWith {
        CLASS_INSTANCE,
        VAR
    }
}
