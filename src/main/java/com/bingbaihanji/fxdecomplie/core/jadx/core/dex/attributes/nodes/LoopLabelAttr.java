package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.nodes;

import com.bingbaihanji.fxdecomplie.core.jadx.api.plugins.input.data.attributes.IJadxAttribute;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.attributes.AType;

public class LoopLabelAttr implements IJadxAttribute {

    private final LoopInfo loop;

    public LoopLabelAttr(LoopInfo loop) {
        this.loop = loop;
    }

    public LoopInfo getLoop() {
        return loop;
    }

    @Override
    public AType<LoopLabelAttr> getAttrType() {
        return AType.LOOP_LABEL;
    }

    @Override
    public String toString() {
        return "LOOP_LABEL: " + loop;
    }
}
