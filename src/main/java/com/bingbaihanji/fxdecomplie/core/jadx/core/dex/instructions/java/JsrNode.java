package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.java;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.InsnType;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.TargetInsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.InsnUtils;

public class JsrNode extends TargetInsnNode {

    protected final int target;

    public JsrNode(int target) {
        this(InsnType.JAVA_JSR, target, 0);
    }

    protected JsrNode(InsnType type, int target, int argsCount) {
        super(type, argsCount);
        this.target = target;
    }

    public int getTarget() {
        return target;
    }

    @Override
    public InsnNode copy() {
        return copyCommonParams(new JsrNode(target));
    }

    @Override
    public String toString() {
        return baseString() + " -> " + InsnUtils.formatOffset(target) + attributesString();
    }
}
