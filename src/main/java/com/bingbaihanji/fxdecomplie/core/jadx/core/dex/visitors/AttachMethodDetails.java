package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors;

import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.instructions.BaseInvokeNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IMethodDetails;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.MethodNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.RootNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.utils.MethodUtils;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.visitors.blocks.BlockSplitter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.JadxException;

@JadxVisitor(
        name = "Attach Method Details",
        desc = "Attach method details for invoke instructions",
        runBefore = {
                BlockSplitter.class,
                MethodInvokeVisitor.class
        }
)
public class AttachMethodDetails extends AbstractVisitor {

    private MethodUtils methodUtils;

    @Override
    public void init(RootNode root) {
        methodUtils = root.getMethodUtils();
    }

    @Override
    public void visit(MethodNode mth) throws JadxException {
        if (mth.isNoCode()) {
            return;
        }
        for (InsnNode insn : mth.getInstructions()) {
            if (insn instanceof BaseInvokeNode) {
                attachMethodDetails((BaseInvokeNode) insn);
            }
        }
    }

    private void attachMethodDetails(BaseInvokeNode insn) {
        IMethodDetails methodDetails = methodUtils.getMethodDetails(insn.getCallMth());
        if (methodDetails != null) {
            insn.addAttr(methodDetails);
        }
    }
}
