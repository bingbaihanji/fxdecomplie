package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.PhiInsn;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.args.RegisterArg;

import java.util.ArrayList;
import java.util.List;

public class PhiListAttr implements IJadxAttribute {

    private final List<PhiInsn> list = new ArrayList<>();

    @Override
    public AType<PhiListAttr> getAttrType() {
        return AType.PHI_LIST;
    }

    public List<PhiInsn> getList() {
        return list;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("PHI:");
        for (PhiInsn phiInsn : list) {
            RegisterArg resArg = phiInsn.getResult();
            if (resArg != null) {
                sb.append(" r").append(resArg.getRegNum());
            }
        }
        for (PhiInsn phiInsn : list) {
            sb.append('\n').append("  ").append(phiInsn);
        }
        return sb.toString();
    }
}
