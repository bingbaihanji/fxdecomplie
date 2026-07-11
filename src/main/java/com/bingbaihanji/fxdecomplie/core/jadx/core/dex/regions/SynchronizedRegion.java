package com.bingbaihanji.fxdecomplie.core.jadx.core.dex.regions;

import com.bingbaihanji.fxdecomplie.core.jadx.api.ICodeWriter;
import com.bingbaihanji.fxdecomplie.core.jadx.core.codegen.RegionGen;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IContainer;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.IRegion;
import com.bingbaihanji.fxdecomplie.core.jadx.core.dex.nodes.InsnNode;
import com.bingbaihanji.fxdecomplie.core.jadx.core.utils.exceptions.CodegenException;

import java.util.ArrayList;
import java.util.List;

public final class SynchronizedRegion extends AbstractRegion {

    private final InsnNode enterInsn;
    private final List<InsnNode> exitInsns = new ArrayList<>();
    private final Region region;

    public SynchronizedRegion(IRegion parent, InsnNode insn) {
        super(parent);
        this.enterInsn = insn;
        this.region = new Region(this);
    }

    public InsnNode getEnterInsn() {
        return enterInsn;
    }

    public List<InsnNode> getExitInsns() {
        return exitInsns;
    }

    public Region getRegion() {
        return region;
    }

    @Override
    public List<IContainer> getSubBlocks() {
        return region.getSubBlocks();
    }

    @Override
    public void generate(RegionGen regionGen, ICodeWriter code) throws CodegenException {
        regionGen.makeSynchronizedRegion(this, code);
    }

    @Override
    public String baseString() {
        return Integer.toHexString(enterInsn.getOffset());
    }

    @Override
    public String toString() {
        return "Synchronized:" + region;
    }
}
